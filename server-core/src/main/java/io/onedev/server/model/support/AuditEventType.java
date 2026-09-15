package io.onedev.server.model.support;

import static io.onedev.server.model.support.AuditEventSeverity.CRITICAL;
import static io.onedev.server.model.support.AuditEventSeverity.INFO;
import static io.onedev.server.model.support.AuditEventSeverity.WARNING;

public enum AuditEventType {
	ROLE_CREATED(INFO),
	ROLE_UPDATED(WARNING),
	ROLE_DELETED(WARNING),
	USER_CREATED(INFO),
	USER_UPDATED(WARNING),
	USER_DISABLED(WARNING),
	USER_DELETED(CRITICAL),
	GROUP_CREATED(INFO),
	GROUP_UPDATED(WARNING),
	GROUP_DELETED(WARNING),
	MEMBERSHIP_CREATED(INFO),
	MEMBERSHIP_DELETED(WARNING),
	PROJECT_CREATED(INFO),
	PROJECT_DELETED(CRITICAL),
	PROJECT_SETTING_UPDATED(WARNING),
	SYSTEM_SETTING_UPDATED(WARNING),
	WEBHOOK_CREATED(INFO),
	WEBHOOK_UPDATED(INFO),
	WEBHOOK_DELETED(WARNING),
	LOGIN_SUCCEEDED(INFO),
	LOGIN_FAILED(CRITICAL),
	LOGOUT(INFO),
	PASSWORD_CHANGED(WARNING),
	PULL_REQUEST_MERGED(INFO),
	ISSUE_STATE_CHANGED(INFO),
	BUILD_FINISHED(INFO);

	private final AuditEventSeverity severity;

	AuditEventType(AuditEventSeverity severity) {
		this.severity = severity;
	}

	public AuditEventSeverity getSeverity() {
		return severity;
	}
}
