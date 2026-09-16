package io.onedev.server.web.page.admin.auditlog;

import java.util.ArrayList;
import java.util.LinkedHashSet;
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
 * Selector paginado de actores del audit log.
 *
 * <p><b>Vista global:</b> todos los usuarios del sistema más el usuario
 * sintético OneDev.</p>
 *
 * <p><b>Vista de proyecto:</b> el creador/creadores del proyecto (detectados
 * por sus eventos de auditoría), todos los miembros con acceso directo al
 * proyecto ({@code UserAuthorization}), y el usuario OneDev.</p>
 */
public class AuditActorChoiceProvider extends AbstractUserChoiceProvider {

	private static final long serialVersionUID = 1L;

	private static final int FETCH_LIMIT = 10000;

	private final Project project;

	public AuditActorChoiceProvider(@Nullable Project project) {
		this.project = project;
	}

	@Override
	public void query(String term, int page, Response<User> response) {
		var users = collectActors(term);
		var start = page * WebConstants.PAGE_SIZE;
		var end = Math.min(start + WebConstants.PAGE_SIZE, users.size());
		response.setHasMore(end < users.size());
		if (start < users.size())
			response.addAll(users.subList(start, end));
	}

	/**
	 * Recopila todos los actores candidatos aplicando filtrado por término.
	 * El resultado está ordenado y sin duplicados.
	 */
	private ArrayList<User> collectActors(@Nullable String term) {
		var candidates = new LinkedHashSet<User>();

		if (project != null) {
			// 1. Actores que ya tienen eventos de auditoría en el proyecto
			var auditActors = OneDev.getInstance(AuditEventService.class)
					.queryActors(project, null, 0, FETCH_LIMIT);
			candidates.addAll(auditActors);

			// 2. Miembros con acceso directo al proyecto (UserAuthorization)
			for (var auth : project.getUserAuthorizations())
				candidates.add(auth.getUser());

			// 3. Usuario del sistema (OneDev)
			var systemUser = OneDev.getInstance(UserService.class).getSystem();
			if (systemUser != null)
				candidates.add(systemUser);
		} else {
			// Vista global: todos los usuarios del sistema
			List<User> allUsers = OneDev.getInstance(UserService.class)
					.query(null, 0, FETCH_LIMIT);
			candidates.addAll(allUsers);

			// Asegurar que el usuario del sistema esté incluido
			var systemUser = OneDev.getInstance(UserService.class).getSystem();
			if (systemUser != null)
				candidates.add(systemUser);
		}

		// Filtrar por término de búsqueda
		if (term != null && !term.isBlank()) {
			var lowerTerm = term.toLowerCase();
			return candidates.stream()
					.filter(u -> u.getName().toLowerCase().contains(lowerTerm)
							|| u.getDisplayName().toLowerCase().contains(lowerTerm))
					.collect(java.util.stream.Collectors.toCollection(ArrayList::new));
		}
		return new ArrayList<>(candidates);
	}

}
