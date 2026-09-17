package io.onedev.server.web.page.admin.auditlog;

import static io.onedev.server.web.translation.Translation._T;

import org.apache.wicket.Component;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.request.mapper.parameter.PageParameters;

import io.onedev.server.web.page.admin.AdministrationPage;

public class GlobalAuditLogPage extends AdministrationPage {

	public GlobalAuditLogPage(PageParameters params) {
		super(params);
	}

	@Override
	protected void onInitialize() {
		super.onInitialize();
		add(new AuditEventListPanel("auditEvents", null, true));
	}

	@Override
	protected Component newTopbarTitle(String componentId) {
		return new Label(componentId, _T("Audit Log"));
	}

}
