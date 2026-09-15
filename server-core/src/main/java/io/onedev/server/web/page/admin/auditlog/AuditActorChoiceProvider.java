package io.onedev.server.web.page.admin.auditlog;

import java.util.List;

import org.jspecify.annotations.Nullable;

import io.onedev.server.OneDev;
import io.onedev.server.model.Project;
import io.onedev.server.model.User;
import io.onedev.server.service.AuditEventService;
import io.onedev.server.service.UserService;
import io.onedev.server.web.WebConstants;
import io.onedev.server.web.component.select2.Response;
import io.onedev.server.web.component.user.choice.AbstractUserChoiceProvider;

/**
 * Selector paginado de actores del audit log. En vista global ofrece todos los
 * usuarios; en vista de proyecto solo los usuarios con eventos en ese proyecto.
 */
public class AuditActorChoiceProvider extends AbstractUserChoiceProvider {

	private static final long serialVersionUID = 1L;

	private final Project project;

	public AuditActorChoiceProvider(@Nullable Project project) {
		this.project = project;
	}

	@Override
	public void query(String term, int page, Response<User> response) {
		var firstResult = page * WebConstants.PAGE_SIZE;
		List<User> users;
		if (project != null) {
			users = OneDev.getInstance(AuditEventService.class)
					.queryActors(project, term, firstResult, WebConstants.PAGE_SIZE + 1);
		} else {
			users = OneDev.getInstance(UserService.class)
					.query(term, firstResult, WebConstants.PAGE_SIZE + 1);
		}
		if (users.size() > WebConstants.PAGE_SIZE) {
			response.setHasMore(true);
			users = users.subList(0, WebConstants.PAGE_SIZE);
		} else {
			response.setHasMore(false);
		}
		response.addAll(users);
	}

}
