package io.onedev.server.web.page.project.auditlog;

import static io.onedev.server.web.translation.Translation._T;

import org.apache.wicket.Component;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.request.mapper.parameter.PageParameters;

import io.onedev.server.model.Project;
import io.onedev.server.web.component.link.ViewStateAwarePageLink;

import io.onedev.server.web.mapper.ProjectMapperUtils;
import io.onedev.server.web.page.admin.auditlog.AuditEventListPanel;
import io.onedev.server.web.page.project.ProjectPage;

public class ProjectAuditLogPage extends ProjectPage {

	public ProjectAuditLogPage(PageParameters params) {
		super(params);
	}

	@Override
	protected void onInitialize() {
		super.onInitialize();
		add(new AuditEventListPanel("auditEvents", getProject(), false));
	}

	@Override
	protected BookmarkablePageLink<Void> navToProject(String componentId, Project project) {
		return new ViewStateAwarePageLink<Void>(componentId, ProjectAuditLogPage.class,
				ProjectAuditLogPage.paramsOf(project.getPath()));
	}

	@Override
	protected Component newProjectTitle(String componentId) {
		return new Label(componentId, _T("Audit Log"));
	}

	@Override
	protected Component newTopbarTitle(String componentId) {
		return new Label(componentId, _T("Audit Log"));
	}

	public static PageParameters paramsOf(String projectPath) {
		PageParameters params = new PageParameters();
		params.add(ProjectMapperUtils.PARAM_PROJECT, projectPath);
		return params;
	}

}
