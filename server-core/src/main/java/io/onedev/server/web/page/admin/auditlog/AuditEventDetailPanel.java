package io.onedev.server.web.page.admin.auditlog;

import static io.onedev.server.web.translation.Translation._T;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.Model;

import io.onedev.server.model.AuditEvent;
import io.onedev.server.util.DateUtils;
import io.onedev.server.web.component.modal.ModalPanel;

public class AuditEventDetailPanel extends Panel {

	private final AuditEvent event;

	private final ModalPanel modal;

	public AuditEventDetailPanel(String id, AuditEvent event, ModalPanel modal) {
		super(id);
		this.event = event;
		this.modal = modal;
	}

	@Override
	protected void onInitialize() {
		super.onInitialize();

		add(new AjaxLink<Void>("close") {
			@Override
			public void onClick(AjaxRequestTarget target) {
				modal.close();
			}
		});

		add(new Label("date", DateUtils.formatDateTime(event.getDate())));
		add(new Label("scope", event.getProject() != null || event.getProjectPath() != null
				? _T("Project") : _T("System")));
		add(new Label("severity", event.getEventSeverity().name()));
		add(new Label("action", event.getEventType().name()));
		add(AuditEventLinks.newActorRef("actor", event));
		add(AuditEventLinks.newProjectRef("project", event));
		add(AuditEventLinks.newTargetRef("target", event));
		add(new Label("summary", event.getSummary()));
		add(new Label("details", Model.of(event.getDetails() != null ? event.getDetails() : "")));
	}

}
