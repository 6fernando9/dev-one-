package io.onedev.server.web.page.admin.auditlog;

import static io.onedev.server.web.translation.Translation._T;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.markup.repeater.RepeatingView;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.Model;
import org.jspecify.annotations.Nullable;

import io.onedev.server.OneDev;
import io.onedev.server.model.Project;
import io.onedev.server.model.User;
import io.onedev.server.persistence.SessionService;
import io.onedev.server.service.AuditEventService;
import io.onedev.server.service.ProjectService;
import io.onedev.server.service.UserService;
import io.onedev.server.util.Similarities;
import io.onedev.server.web.WebConstants;
import io.onedev.server.web.behavior.OnTypingDoneBehavior;
import io.onedev.server.web.behavior.infinitescroll.InfiniteScrollBehavior;
import io.onedev.server.web.component.link.PreventDefaultAjaxLink;
import io.onedev.server.web.component.user.UserAvatar;

/**
 * Panel con búsqueda y scroll infinito para seleccionar un actor del audit log.
 *
 * <p>En modo proyecto solo muestra actores con eventos en el proyecto más los
 * miembros con acceso y el usuario OneDev. En modo admin muestra todos los
 * usuarios del sistema.</p>
 *
 * <p>Siempre incluye una opción "All Actors" al inicio que selecciona
 * {@code null} para indicar que no hay filtro de actor.</p>
 */
public abstract class ActorSelectorPanel extends Panel {

	private static final int FETCH_LIMIT = 10000;

	private final IModel<List<User>> actorsModel = new LoadableDetachableModel<>() {

		@Override
		protected List<User> load() {
			return loadActors();
		}

	};

	private final IModel<List<User>> similarActorsModel = new LoadableDetachableModel<>() {

		@Override
		protected List<User> load() {
			return new Similarities<>(actorsModel.getObject()) {
				@Override
				protected double getSimilarScore(User item) {
					return Similarities.getSimilarScore(item.getDisplayName(), searchInput);
				}
			};
		}

	};

	private RepeatingView actorsView;

	private TextField<String> searchField;

	private String searchInput;

	private final Project project;

	public ActorSelectorPanel(String id, @Nullable Project project) {
		super(id);
		this.project = project;
	}

	@Override
	protected void onInitialize() {
		super.onInitialize();

		WebMarkupContainer actorsContainer = new WebMarkupContainer("actors") {

			@Override
			protected void onBeforeRender() {
				actorsView = new RepeatingView("actors");

				// Siempre agregar "All Actors" al inicio
				actorsView.add(newAllActorsItem(actorsView.newChildId()));

				int index = 0;
				for (User user : similarActorsModel.getObject()) {
					Component item = newItem(actorsView.newChildId(), user);
					if (index == 0)
						item.add(AttributeAppender.append("class", "active"));
					actorsView.add(item);
					if (++index >= WebConstants.PAGE_SIZE)
						break;
				}
				addOrReplace(actorsView);

				super.onBeforeRender();
			}

			@Override
			protected void onConfigure() {
				super.onConfigure();
				setVisible(true);
			}

		};

		actorsContainer.add(new InfiniteScrollBehavior(WebConstants.PAGE_SIZE) {

			@Override
			protected void appendMore(AjaxRequestTarget target, int offset, int count) {
				// El offset empieza después del item "All Actors" (+1)
				var actors = similarActorsModel.getObject();
				for (int i = offset; i < offset + count; i++) {
					if (i >= actors.size())
						break;
					User user = actors.get(i);

					Component item = newItem(actorsView.newChildId(), user);
					actorsView.add(item);
					String script = String.format("$('#%s').append('<li id=\"%s\"></li>');",
							actorsContainer.getMarkupId(), item.getMarkupId());
					target.prependJavaScript(script);
					target.add(item);
				}
			}

		});

		actorsContainer.setOutputMarkupPlaceholderTag(true);
		add(actorsContainer);

		WebMarkupContainer noActorsContainer = new WebMarkupContainer("noActors") {

			@Override
			protected void onConfigure() {
				super.onConfigure();
				setVisible(similarActorsModel.getObject().isEmpty());
			}

		};
		noActorsContainer.setOutputMarkupPlaceholderTag(true);
		add(noActorsContainer);

		searchField = new TextField<>("search", Model.of(""));
		add(searchField);
		searchField.add(new OnTypingDoneBehavior(100) {

			@Override
			protected void onTypingDone(AjaxRequestTarget target) {
				searchInput = searchField.getInput();

				target.add(actorsContainer);
				target.add(noActorsContainer);
			}

		});

		setOutputMarkupId(true);
	}

	private Component newAllActorsItem(String componentId) {
		WebMarkupContainer item = new WebMarkupContainer(componentId);

		AjaxLink<Void> link = new PreventDefaultAjaxLink<Void>("link") {

			@Override
			public void onClick(AjaxRequestTarget target) {
				onSelect(target, null);
			}

		};
		link.add(AttributeAppender.append("class", "all-actors"));
		// Avatar placeholder oculto para satisfacer el template
		var avatarPlaceholder = new WebMarkupContainer("avatar");
		avatarPlaceholder.setVisible(false);
		link.add(avatarPlaceholder);
		link.add(new Label("name", _T("All Actors")));
		item.add(link);

		return item;
	}

	private Component newItem(String componentId, User user) {
		WebMarkupContainer item = new WebMarkupContainer(componentId);

		AjaxLink<Void> link = new PreventDefaultAjaxLink<Void>("link") {

			@Override
			public void onClick(AjaxRequestTarget target) {
				User loaded = OneDev.getInstance(UserService.class).load(user.getId());
				onSelect(target, loaded);
			}

		};
		link.add(new UserAvatar("avatar", user));
		link.add(new Label("name", user.getDisplayName()));
		item.add(link);

		return item;
	}

	/**
	 * Carga todos los actores candidatos dentro de una sesión Hibernate.
	 * Se recarga el proyecto desde la BD para que las colecciones lazy
	 * (userAuthorizations) estén asociadas a la sesión activa.
	 */
	private List<User> loadActors() {
		return OneDev.getInstance(SessionService.class).call(() -> {
			var candidates = new LinkedHashSet<User>();

			if (project != null) {
				// Recargar proyecto desde la BD dentro de la sesión activa
				var freshProject = OneDev.getInstance(ProjectService.class)
						.load(project.getId());

				var auditActors = OneDev.getInstance(AuditEventService.class)
						.queryActors(freshProject, null, 0, FETCH_LIMIT);
				candidates.addAll(auditActors);

				for (var auth : freshProject.getUserAuthorizations())
					candidates.add(auth.getUser());

				var systemUser = OneDev.getInstance(UserService.class).getSystem();
				if (systemUser != null)
					candidates.add(systemUser);
			} else {
				List<User> allUsers = OneDev.getInstance(UserService.class)
						.query((String) null, 0, FETCH_LIMIT);
				candidates.addAll(allUsers);

				var systemUser = OneDev.getInstance(UserService.class).getSystem();
				if (systemUser != null)
					candidates.add(systemUser);
			}

			return new ArrayList<>(candidates);
		});
	}

	@Override
	protected void onDetach() {
		actorsModel.detach();
		similarActorsModel.detach();

		super.onDetach();
	}

	/**
	 * Se invoca cuando el usuario selecciona un actor.
	 * @param user el usuario seleccionado, o {@code null} para "All Actors"
	 */
	protected abstract void onSelect(AjaxRequestTarget target, @Nullable User user);

}
