package io.onedev.server.web.page.admin.auditlog;

import static io.onedev.server.web.translation.Translation._T;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.behavior.AttributeAppender;
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
		setOutputMarkupId(true);
	}

	private static String humanize(String enumName) {
		var builder = new StringBuilder();
		for (var word : enumName.toLowerCase().split("_")) {
			if (builder.length() != 0)
				builder.append(' ');
			builder.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
		}
		return builder.toString();
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
		add(new Label("severity", event.getEventSeverity().name())
				.add(AttributeAppender.append("class", AuditEventLinks.severityBadgeClass(event) + " badge-sm")));
		add(new Label("action", humanize(event.getEventType().name()))
				.add(AttributeAppender.append("class", AuditEventLinks.actionBadgeClass(event))));
		add(AuditEventLinks.newActorRef("actor", event));
		add(AuditEventLinks.newProjectRef("project", event));
		add(AuditEventLinks.newTargetRef("target", event));
		add(new Label("summary", event.getSummary()));
		add(new Label("details", Model.of(event.getDetails() != null ? event.getDetails() : "")));
		add(new Label("ip", event.getIpAddress() != null ? event.getIpAddress() : "-"));
	}

}
