package io.onedev.server.web.page.admin.auditlog;

import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.core.request.handler.IPartialPageRequestHandler;

import io.onedev.server.model.Project;
import io.onedev.server.model.User;
import io.onedev.server.web.component.modal.ModalPanel;

/**
 * Modal que muestra un {@link ActorSelectorPanel} paginado con búsqueda.
 * Al seleccionar un usuario se cierra el modal y se invoca el callback.
 */
public abstract class ActorSelectorModalPanel extends ModalPanel {

	public ActorSelectorModalPanel(IPartialPageRequestHandler target, Project project) {
		super(target);
	}

	@Override
	protected Component newContent(String id) {
		return new ActorSelectorPanel(id, getProject()) {

			@Override
			protected void onSelect(AjaxRequestTarget target, User user) {
				close();
				ActorSelectorModalPanel.this.onSelect(target, user);
			}

		};
	}

	@Override
	protected String getCssClass() {
		return "modal-sm";
	}

	protected Project getProject() {
		return null;
	}

	protected abstract void onSelect(AjaxRequestTarget target, User user);

}
