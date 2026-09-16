package io.onedev.server.web.page.project.traceability;

import static io.onedev.server.web.translation.Translation._T;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.apache.wicket.AttributeModifier;
import org.apache.wicket.Component;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.request.mapper.parameter.PageParameters;

import io.onedev.server.OneDev;
import io.onedev.server.model.Project;
import io.onedev.server.security.SecurityUtils;
import io.onedev.server.traceability.ConfigItem;
import io.onedev.server.traceability.ConfigItemType;
import io.onedev.server.traceability.matrix.TraceabilityMatrix;
import io.onedev.server.traceability.matrix.TraceabilityMatrixService;
import io.onedev.server.traceability.matrix.TraceabilityRow;
import io.onedev.server.traceability.matrix.TraceabilitySyncStatus;
import io.onedev.server.web.component.link.ViewStateAwarePageLink;
import io.onedev.server.web.page.project.ProjectPage;
import io.onedev.server.web.page.project.overview.ProjectOverviewPage;

/**
 * Vista web de la Matriz de Trazabilidad Dinámica y Bidireccional para el proyecto en OneDev (RF4).
 */
public class ProjectTraceabilityPage extends ProjectPage {

    public ProjectTraceabilityPage(PageParameters params) {
        super(params);
    }

    public static PageParameters paramsOf(Project project) {
        return ProjectPage.paramsOf(project);
    }

    @Override
    protected Component newProjectTitle(String componentId) {
        return new Label(componentId, "<span class='text-truncate'>" + _T("Traceability") + "</span>").setEscapeModelStrings(false);
    }

    @Override
    protected BookmarkablePageLink<Void> navToProject(String componentId, Project project) {
        if (project.isCodeManagement() && SecurityUtils.canReadCode(project)) {
            return new ViewStateAwarePageLink<Void>(componentId, ProjectTraceabilityPage.class, ProjectTraceabilityPage.paramsOf(project));
        } else {
            return new ViewStateAwarePageLink<Void>(componentId, ProjectOverviewPage.class, ProjectOverviewPage.paramsOf(project.getId()));
        }
    }

    @Override
    protected void onInitialize() {
        super.onInitialize();

        IModel<TraceabilityMatrix> matrixModel = new LoadableDetachableModel<TraceabilityMatrix>() {
            @Override
            protected TraceabilityMatrix load() {
                TraceabilityMatrixService service = OneDev.getInstance(TraceabilityMatrixService.class);
                return service.buildMatrix(getProject(), null);
            }
        };

        TraceabilityMatrix matrix = matrixModel.getObject();

        // Tarjetas de métricas
        add(new Label("totalReqs", String.valueOf(matrix.getTotalRequirements())));
        add(new Label("coverage", String.format(Locale.US, "%.1f%%", matrix.getCoveragePercentage())));
        add(new Label("driftCount", String.valueOf(matrix.getDriftCount())));
        add(new Label("totalLinks", String.valueOf(matrix.getTotalLinks())));

        // Enlaces de descarga / exportación
        WebMarkupContainer csvBtn = new WebMarkupContainer("csvBtn");
        csvBtn.add(AttributeModifier.replace("href", "/api/traceability/export/" + getProject().getId() + "?format=csv"));
        add(csvBtn);

        WebMarkupContainer mdBtn = new WebMarkupContainer("mdBtn");
        mdBtn.add(AttributeModifier.replace("href", "/api/traceability/export/" + getProject().getId() + "?format=md"));
        add(mdBtn);

        // Tabla de filas de requisitos
        add(new ListView<TraceabilityRow>("reqRows", new LoadableDetachableModel<List<TraceabilityRow>>() {
            @Override
            protected List<TraceabilityRow> load() {
                return matrixModel.getObject().getRows().stream()
                    .filter(r -> r.getPrimaryItem().getType() == ConfigItemType.REQUIREMENT)
                    .collect(Collectors.toList());
            }
        }) {
            @Override
            protected void populateItem(ListItem<TraceabilityRow> item) {
                TraceabilityRow row = item.getModelObject();
                item.add(new Label("id", row.getPrimaryItem().getIdentifier()));
                item.add(new Label("title", row.getPrimaryItem().getTitle()));

                Label statusLabel = new Label("status", row.getStatus().getDisplayName());
                String badgeClass = row.getStatus() == TraceabilitySyncStatus.SYNCHRONIZED ? "badge badge-success" :
                    (row.getStatus() == TraceabilitySyncStatus.PARTIAL ? "badge badge-warning" : "badge badge-danger");
                statusLabel.add(AttributeModifier.replace("class", badgeClass));
                item.add(statusLabel);

                String tasksText = row.getTasks().isEmpty() ? "-" :
                    row.getTasks().stream().map(ConfigItem::getIdentifier).collect(Collectors.joining(", "));
                item.add(new Label("tasks", tasksText));

                String sourcesText = row.getSourceFiles().isEmpty() ? "-" :
                    row.getSourceFiles().stream().map(ConfigItem::getPath).collect(Collectors.joining("<br/>"));
                item.add(new Label("sources", sourcesText).setEscapeModelStrings(false));

                String adrsText = row.getAdrs().isEmpty() ? "-" :
                    row.getAdrs().stream().map(ConfigItem::getIdentifier).collect(Collectors.joining(", "));
                item.add(new Label("adrs", adrsText));

                String modelsText = (row.getDataModels().isEmpty() && row.getArchitectureDocs().isEmpty()) ? "-" :
                    (row.getDataModels().stream().map(ConfigItem::getPath).collect(Collectors.joining("<br/>")) +
                     (row.getArchitectureDocs().isEmpty() ? "" : "<br/>" + row.getArchitectureDocs().stream().map(ConfigItem::getPath).collect(Collectors.joining("<br/>")))).trim();
                item.add(new Label("models", modelsText).setEscapeModelStrings(false));

                item.add(new Label("notes", row.getNotes()));
            }
        });

        // Tabla de código huérfano (desfase)
        WebMarkupContainer orphanSection = new WebMarkupContainer("orphanSection") {
            @Override
            protected void onConfigure() {
                super.onConfigure();
                setVisible(matrixModel.getObject().getOrphanCount() > 0);
            }
        };
        orphanSection.add(new Label("orphanCount", String.valueOf(matrix.getOrphanCount())));
        orphanSection.add(new ListView<TraceabilityRow>("orphanRows", new LoadableDetachableModel<List<TraceabilityRow>>() {
            @Override
            protected List<TraceabilityRow> load() {
                return matrixModel.getObject().getRows().stream()
                    .filter(r -> r.getPrimaryItem().getType() != ConfigItemType.REQUIREMENT)
                    .collect(Collectors.toList());
            }
        }) {
            @Override
            protected void populateItem(ListItem<TraceabilityRow> item) {
                TraceabilityRow row = item.getModelObject();
                item.add(new Label("path", row.getPrimaryItem().getPath()));
                item.add(new Label("notes", row.getNotes()));
            }
        });
        add(orphanSection);
    }

    @Override
    protected String getPageTitle() {
        return _T("Traceability") + " - " + getProject().getPath();
    }
}