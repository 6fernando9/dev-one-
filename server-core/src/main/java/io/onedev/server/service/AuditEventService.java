package io.onedev.server.service;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import io.onedev.server.model.AuditEvent;
import io.onedev.server.model.Project;
import io.onedev.server.model.User;
import io.onedev.server.model.support.AuditEventSeverity;
import io.onedev.server.model.support.AuditEventType;

public interface AuditEventService extends EntityService<AuditEvent> {

	void record(AuditEventType type, @Nullable User actor, @Nullable String actorName,
			@Nullable String ipAddress, @Nullable Project project, String summary,
			@Nullable String details, @Nullable String refType, @Nullable Long refId);

	void recordLoginSucceeded(User actor, String via);

	void recordLoginFailed(@Nullable String userName, String via);

	void recordLogout(User actor);

	List<AuditEvent> query(@Nullable Project project, @Nullable AuditEventType type,
			@Nullable AuditEventSeverity severity, @Nullable Boolean projectScoped,
			@Nullable Date from, @Nullable Date to, @Nullable String searchTerm,
			int firstResult, int maxResults);

	int count(@Nullable Project project, @Nullable AuditEventType type,
			@Nullable AuditEventSeverity severity, @Nullable Boolean projectScoped,
			@Nullable Date from, @Nullable Date to, @Nullable String searchTerm);

	Map<LocalDate, Long> countByDay(@Nullable Project project, @Nullable AuditEventType type,
			@Nullable AuditEventSeverity severity, @Nullable Boolean projectScoped,
			@Nullable Date from, @Nullable Date to);

	void purgeBefore(Date date);

}
