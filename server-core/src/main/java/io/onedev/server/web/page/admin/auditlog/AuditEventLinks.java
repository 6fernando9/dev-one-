package io.onedev.server.web.page.admin.auditlog;

import org.apache.wicket.Component;
import org.apache.wicket.Page;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.jspecify.annotations.Nullable;

import io.onedev.server.OneDev;
import io.onedev.server.model.AuditEvent;
import io.onedev.server.model.Project;
import io.onedev.server.persistence.SessionService;
import io.onedev.server.service.BuildService;
import io.onedev.server.service.IssueService;
import io.onedev.server.service.ProjectService;
import io.onedev.server.service.PullRequestService;
import io.onedev.server.service.UserService;
import io.onedev.server.web.page.project.builds.detail.BuildDetailPage;
import io.onedev.server.web.page.project.issues.detail.IssueDetailPage;
import io.onedev.server.web.page.project.overview.ProjectOverviewPage;
import io.onedev.server.web.page.project.pullrequests.detail.PullRequestDetailPage;
import io.onedev.server.web.page.user.UserPage;
import io.onedev.server.web.page.user.profile.UserProfilePage;

/**
 * Resuelve referencias de un evento de auditoría a páginas reales de OneDev.
 * Si la entidad existe genera link, si fue eliminada devuelve texto plano
 * a partir de los datos congelados del evento (actorName, projectPath).
 */
public class AuditEventLinks {

	@Nullable
	private static Project findProject(String path) {
		if (path == null)
			return null;
		try {
			return OneDev.getInstance(ProjectService.class).findByPath(path);
		} catch (Exception e) {
			return null;
		}
	}

	public static String getActorDisplay(AuditEvent event) {
		if (event.getActorName() != null)
			return event.getActorName();
		else if (event.getActor() != null)
			return event.getActor().getDisplayName();
		else
			return "system";
	}

	public static Component newActorRef(String id, AuditEvent event) {
		var actor = event.getActor();
		if (actor != null) {
			return new AuditEventRefPanel(id, getActorDisplay(event),
					UserProfilePage.class, UserPage.paramsOf(actor));
		}
		return new AuditEventRefPanel(id, getActorDisplay(event), null, null);
	}

	public static Component newProjectRef(String id, AuditEvent event) {
		var project = event.getProject();
		if (project != null) {
			return new AuditEventRefPanel(id, event.getProjectPath(),
					ProjectOverviewPage.class, ProjectOverviewPage.paramsOf(project.getId()));
		}
		if (event.getProjectPath() != null) {
			var found = findProject(event.getProjectPath());
			if (found != null) {
				return new AuditEventRefPanel(id, event.getProjectPath(),
						ProjectOverviewPage.class, ProjectOverviewPage.paramsOf(found.getId()));
			}
			return new AuditEventRefPanel(id, event.getProjectPath(), null, null);
		}
		return new AuditEventRefPanel(id, "-", null, null);
	}

	public static Component newTargetRef(String id, AuditEvent event) {
		if (event.getRefType() == null || event.getRefId() == null)
			return new AuditEventRefPanel(id, "-", null, null);
		try {
			var params = OneDev.getInstance(SessionService.class).call(() -> paramsOfTarget(event));
			if (params != null) {
				Class<? extends Page> pageClass = pageClassOf(event.getRefType());
				return new AuditEventRefPanel(id, labelOf(event), pageClass, params);
			}
		} catch (Exception e) {
			// entidad eliminada o inaccesible: texto plano
		}
		return new AuditEventRefPanel(id, labelOf(event), null, null);
	}

	@Nullable
	private static PageParameters paramsOfTarget(AuditEvent event) {
		switch (event.getRefType()) {
			case "PullRequest": {
				var request = OneDev.getInstance(PullRequestService.class).get(event.getRefId());
				if (request != null)
					return PullRequestDetailPage.paramsOf(request.getProject(), request.getNumber());
				break;
			}
			case "Issue": {
				var issue = OneDev.getInstance(IssueService.class).get(event.getRefId());
				if (issue != null)
					return IssueDetailPage.paramsOf(issue.getProject(), issue.getNumber());
				break;
			}
			case "Build": {
				var build = OneDev.getInstance(BuildService.class).get(event.getRefId());
				if (build != null)
					return BuildDetailPage.paramsOf(build.getProject(), build.getNumber());
				break;
			}
			case "User": {
				var user = OneDev.getInstance(UserService.class).get(event.getRefId());
				if (user != null)
					return UserPage.paramsOf(user);
				break;
			}
			case "Project": {
				var project = OneDev.getInstance(ProjectService.class).get(event.getRefId());
				if (project != null)
					return ProjectOverviewPage.paramsOf(project.getId());
				break;
			}
			default:
				break;
		}
		return null;
	}

	@Nullable
	@SuppressWarnings("unchecked")
	private static Class<? extends Page> pageClassOf(String refType) {
		switch (refType) {
			case "PullRequest":
				return PullRequestDetailPage.class;
			case "Issue":
				return IssueDetailPage.class;
			case "Build":
				return BuildDetailPage.class;
			case "User":
				return UserProfilePage.class;
			case "Project":
				return ProjectOverviewPage.class;
			default:
				return null;
		}
	}

	private static String labelOf(AuditEvent event) {
		switch (event.getRefType()) {
			case "PullRequest":
				return "PR #" + event.getRefId();
			case "Issue":
				return "Issue #" + event.getRefId();
			case "Build":
				return "Build #" + event.getRefId();
			default:
				return event.getRefType() + " #" + event.getRefId();
		}
	}

}
