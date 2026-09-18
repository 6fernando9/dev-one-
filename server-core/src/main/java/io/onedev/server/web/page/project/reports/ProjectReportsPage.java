package io.onedev.server.web.page.project.reports;

import static io.onedev.server.web.translation.Translation._T;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import org.apache.shiro.authz.UnauthorizedException;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.markup.head.CssHeaderItem;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.request.mapper.parameter.PageParameters;

import io.onedev.server.OneDev;
import io.onedev.server.model.Project;
import io.onedev.server.report.ReportDomain;
import io.onedev.server.report.ReportRequest;
import io.onedev.server.report.ReportResult;
import io.onedev.server.report.ReportService;
import io.onedev.server.security.SecurityUtils;
import io.onedev.server.web.component.link.ViewStateAwarePageLink;
import io.onedev.server.web.page.project.ProjectPage;

/**
 * Página de Informes Dinámicos con Lenguaje Natural (RF6).
 * Soporte para Modo Claro, Modo Oscuro y cambio dinámico de idioma (_T / wicket:t).
 */
public class ProjectReportsPage extends ProjectPage {

    private static final long serialVersionUID = 1L;

    private final IModel<String> queryModel = Model.of("");
    private final IModel<ReportResult> resultModel = Model.of((ReportResult) null);

    public ProjectReportsPage(PageParameters params) {
        super(params);
    }

    public static PageParameters paramsOf(Project project) {
        return ProjectPage.paramsOf(project);
    }

    public static PageParameters paramsOf(Long projectId) {
        return ProjectPage.paramsOf(projectId);
    }

    public static ViewStateAwarePageLink<Void> link(String componentId, Project project) {
        return new ViewStateAwarePageLink<Void>(componentId, ProjectReportsPage.class, paramsOf(project));
    }

    @Override
    protected Component newProjectTitle(String componentId) {
        return new Label(componentId, "<span class='text-truncate'>" + _T("Reports") + "</span>").setEscapeModelStrings(false);
    }

    @Override
    protected BookmarkablePageLink<Void> navToProject(String componentId, Project project) {
        return new ViewStateAwarePageLink<Void>(componentId, ProjectReportsPage.class, paramsOf(project));
    }

    @Override
    protected void onInitialize() {
        super.onInitialize();

        if (!SecurityUtils.canAccessProject(getProject())) {
            throw new UnauthorizedException();
        }

        // Formulario de consulta en lenguaje natural
        Form<?> queryForm = new Form<Void>("queryForm");
        TextField<String> queryInput = new TextField<>("queryInput", queryModel);
        queryInput.setOutputMarkupId(true);
        queryInput.add(AttributeModifier.replace("placeholder", _T("Search or ask for a report in natural language...")));
        queryForm.add(queryInput);

        // Contenedor principal de resultados del reporte
        WebMarkupContainer reportContainer = new WebMarkupContainer("reportContainer");
        reportContainer.setOutputMarkupId(true);
        add(reportContainer);

        // Botón de submit AJAX
        queryForm.add(new AjaxButton("submitBtn") {
            private static final long serialVersionUID = 1L;

            @Override
            protected void onSubmit(AjaxRequestTarget target, Form<?> form) {
                executeReport(queryModel.getObject());
                target.add(reportContainer);
            }
        });
        add(queryForm);

        // Sugerencias rápidas clickeables con soporte de internacionalización
        addSuggestionLink("suggestBranches", _T("List all project branches"), queryInput, reportContainer);
        addSuggestionLink("suggestCommits", _T("Recent commits from last week"), queryInput, reportContainer);
        addSuggestionLink("suggestCis", _T("Configuration item inventory"), queryInput, reportContainer);
        addSuggestionLink("suggestRtm", _T("Requirements traceability matrix"), queryInput, reportContainer);
        addSuggestionLink("suggestIssues", _T("Open project issues and tasks"), queryInput, reportContainer);

        // Estado inicial vacío
        WebMarkupContainer emptyState = new WebMarkupContainer("emptyState") {
            private static final long serialVersionUID = 1L;

            @Override
            protected void onConfigure() {
                super.onConfigure();
                setVisible(resultModel.getObject() == null);
            }
        };
        reportContainer.add(emptyState);

        // Sección con datos del reporte
        WebMarkupContainer resultSection = new WebMarkupContainer("resultSection") {
            private static final long serialVersionUID = 1L;

            @Override
            protected void onConfigure() {
                super.onConfigure();
                setVisible(resultModel.getObject() != null);
            }
        };

        // Encabezado del reporte
        resultSection.add(new Label("reportTitle", new LoadableDetachableModel<String>() {
            private static final long serialVersionUID = 1L;

            @Override
            protected String load() {
                ReportResult r = resultModel.getObject();
                return r != null ? r.getTitle() : "";
            }
        }));

        resultSection.add(new Label("reportDomainBadge", new LoadableDetachableModel<String>() {
            private static final long serialVersionUID = 1L;

            @Override
            protected String load() {
                ReportResult r = resultModel.getObject();
                return r != null && r.getDomain() != null ? r.getDomain().getDisplayName() : "";
            }
        }));

        resultSection.add(new Label("reportCountBadge", new LoadableDetachableModel<String>() {
            private static final long serialVersionUID = 1L;

            @Override
            protected String load() {
                ReportResult r = resultModel.getObject();
                return r != null ? r.getRowCount() + " " + _T("records") : "0 " + _T("records");
            }
        }));

        resultSection.add(new Label("reportDescription", new LoadableDetachableModel<String>() {
            private static final long serialVersionUID = 1L;

            @Override
            protected String load() {
                ReportResult r = resultModel.getObject();
                return r != null && r.getDescription() != null ? r.getDescription() : "";
            }
        }));

        // Enlaces de descarga (CSV y PDF)
        WebMarkupContainer downloadCsvBtn = new WebMarkupContainer("downloadCsvBtn") {
            private static final long serialVersionUID = 1L;

            @Override
            protected void onConfigure() {
                super.onConfigure();
                String url = buildExportUrl("csv");
                add(AttributeModifier.replace("href", url));
            }
        };
        resultSection.add(downloadCsvBtn);

        WebMarkupContainer downloadPdfBtn = new WebMarkupContainer("downloadPdfBtn") {
            private static final long serialVersionUID = 1L;

            @Override
            protected void onConfigure() {
                super.onConfigure();
                String url = buildExportUrl("pdf");
                add(AttributeModifier.replace("href", url));
            }
        };
        resultSection.add(downloadPdfBtn);

        // Mensaje de error si la consulta falló
        WebMarkupContainer errorAlert = new WebMarkupContainer("errorAlert") {
            private static final long serialVersionUID = 1L;

            @Override
            protected void onConfigure() {
                super.onConfigure();
                ReportResult r = resultModel.getObject();
                setVisible(r != null && r.getErrorMessage() != null);
            }
        };
        errorAlert.add(new Label("errorMessage", new LoadableDetachableModel<String>() {
            private static final long serialVersionUID = 1L;

            @Override
            protected String load() {
                ReportResult r = resultModel.getObject();
                return r != null && r.getErrorMessage() != null ? r.getErrorMessage() : "";
            }
        }));
        resultSection.add(errorAlert);

        // Tabla de datos
        WebMarkupContainer dataTable = new WebMarkupContainer("dataTable") {
            private static final long serialVersionUID = 1L;

            @Override
            protected void onConfigure() {
                super.onConfigure();
                ReportResult r = resultModel.getObject();
                setVisible(r != null && r.getErrorMessage() == null);
            }
        };

        // Encabezados de columnas
        dataTable.add(new ListView<String>("headerList", new LoadableDetachableModel<List<String>>() {
            private static final long serialVersionUID = 1L;

            @Override
            protected List<String> load() {
                ReportResult r = resultModel.getObject();
                return r != null ? r.getHeaders() : Collections.emptyList();
            }
        }) {
            private static final long serialVersionUID = 1L;

            @Override
            protected void populateItem(ListItem<String> item) {
                item.add(new Label("headerCell", item.getModelObject()));
            }
        });

        // Filas de datos
        dataTable.add(new ListView<List<String>>("rowList", new LoadableDetachableModel<List<List<String>>>() {
            private static final long serialVersionUID = 1L;

            @Override
            protected List<List<String>> load() {
                ReportResult r = resultModel.getObject();
                return r != null ? r.getRows() : Collections.emptyList();
            }
        }) {
            private static final long serialVersionUID = 1L;

            @Override
            protected void populateItem(ListItem<List<String>> rowItem) {
                rowItem.add(new ListView<String>("cellList", rowItem.getModelObject()) {
                    private static final long serialVersionUID = 1L;

                    @Override
                    protected void populateItem(ListItem<String> cellItem) {
                        cellItem.add(new Label("cellValue", cellItem.getModelObject()));
                    }
                });
            }
        });

        resultSection.add(dataTable);
        reportContainer.add(resultSection);
    }

    private void addSuggestionLink(String id, String queryText, TextField<String> queryInput, WebMarkupContainer reportContainer) {
        add(new AjaxLink<Void>(id) {
            private static final long serialVersionUID = 1L;

            @Override
            public void onClick(AjaxRequestTarget target) {
                queryModel.setObject(queryText);
                executeReport(queryText);
                target.add(queryInput);
                target.add(reportContainer);
            }
        });
    }

    private void executeReport(String query) {
        if (query == null || query.trim().isEmpty()) {
            query = _T("List all project branches");
        }
        try {
            ReportService reportService = OneDev.getInstance(ReportService.class);
            ReportRequest request = reportService.translateQuery(getProject(), query);
            ReportResult result = reportService.collectData(getProject(), request);
            resultModel.setObject(result);
        } catch (Exception e) {
            resultModel.setObject(new ReportResult(_T("Execution error"), ReportDomain.COMMITS, e.getMessage()));
        }
    }

    private String buildExportUrl(String format) {
        String q = queryModel.getObject();
        if (q == null || q.trim().isEmpty()) {
            q = _T("List all project branches");
        }
        try {
            String encoded = URLEncoder.encode(q, StandardCharsets.UTF_8.name());
            return "/api/report/export/" + getProject().getId() + "?query=" + encoded + "&format=" + format;
        } catch (UnsupportedEncodingException e) {
            return "/api/report/export/" + getProject().getId() + "?format=" + format;
        }
    }

    @Override
    public void renderHead(IHeaderResponse response) {
        super.renderHead(response);
        response.render(CssHeaderItem.forReference(new ProjectReportsCssResourceReference()));
    }

    @Override
    protected String getPageTitle() {
        return _T("Reports") + " - " + getProject().getPath();
    }
}
