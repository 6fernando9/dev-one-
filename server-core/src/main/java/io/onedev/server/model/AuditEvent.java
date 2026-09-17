package io.onedev.server.model;

import static io.onedev.server.model.AuditEvent.PROP_ACTOR;
import static io.onedev.server.model.AuditEvent.PROP_DATE;
import static io.onedev.server.model.AuditEvent.PROP_PROJECT;
import static io.onedev.server.model.AuditEvent.PROP_TYPE;

import java.util.Date;

import org.jspecify.annotations.Nullable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import io.onedev.server.model.support.AuditEventSeverity;
import io.onedev.server.model.support.AuditEventType;

@Entity
@Table(indexes={
		@Index(columnList="o_project_id"), @Index(columnList="o_actor_id"),
		@Index(columnList=PROP_DATE), @Index(columnList=PROP_TYPE)})
public class AuditEvent extends AbstractEntity {

	private static final long serialVersionUID = 1L;

	private static final int MAX_SUMMARY_LEN = 1024;

	private static final int MAX_DETAILS_LEN = 1048576;

	public static final String PROP_PROJECT = "project";

	public static final String PROP_ACTOR = "actor";

	public static final String PROP_TYPE = "type";

	public static final String PROP_SEVERITY = "severity";

	public static final String PROP_DATE = "date";

	public static final String PROP_SUMMARY = "summary";

	@ManyToOne(fetch=FetchType.LAZY)
	@JoinColumn
	private Project project;

	@Column(length=512)
	private String projectPath;

	@ManyToOne(fetch=FetchType.LAZY)
	@JoinColumn(name="o_actor_id")
	private User actor;

	@Column(length=256)
	private String actorName;

	@Column(length=64)
	private String ipAddress;

	@Column(nullable=false, length=64)
	private String type;

	@Column(nullable=false, length=16)
	private String severity;

	@Column(nullable=false)
	private Date date = new Date();

	@Column(nullable=false, length=MAX_SUMMARY_LEN)
	private String summary;

	@Lob
	@Column(length=MAX_DETAILS_LEN)
	private String details;

	@Column(length=64)
	private String refType;

	private Long refId;

	@Nullable
	public Project getProject() {
		return project;
	}

	public void setProject(@Nullable Project project) {
		this.project = project;
	}

	@Nullable
	public String getProjectPath() {
		return projectPath;
	}

	public void setProjectPath(@Nullable String projectPath) {
		this.projectPath = projectPath;
	}

	@Nullable
	public User getActor() {
		return actor;
	}

	public void setActor(@Nullable User actor) {
		this.actor = actor;
	}

	@Nullable
	public String getActorName() {
		return actorName;
	}

	public void setActorName(@Nullable String actorName) {
		this.actorName = actorName;
	}

	@Nullable
	public String getIpAddress() {
		return ipAddress;
	}

	public void setIpAddress(@Nullable String ipAddress) {
		this.ipAddress = ipAddress;
	}

	public AuditEventType getEventType() {
		return AuditEventType.valueOf(type);
	}

	public void setEventType(AuditEventType eventType) {
		this.type = eventType.name();
		this.severity = eventType.getSeverity().name();
	}

	public AuditEventSeverity getEventSeverity() {
		return AuditEventSeverity.valueOf(severity);
	}

	public Date getDate() {
		return date;
	}

	public void setDate(Date date) {
		this.date = date;
	}

	public String getSummary() {
		return summary;
	}

	public void setSummary(String summary) {
		if (summary.length() > MAX_SUMMARY_LEN)
			throw new IllegalArgumentException("Audit event summary is too long: " + summary);
		this.summary = summary;
	}

	@Nullable
	public String getDetails() {
		return details;
	}

	public void setDetails(@Nullable String details) {
		if (details != null && details.length() > MAX_DETAILS_LEN)
			throw new IllegalArgumentException("Audit event details are too long");
		this.details = details;
	}

	@Nullable
	public String getRefType() {
		return refType;
	}

	public void setRefType(@Nullable String refType) {
		this.refType = refType;
	}

	@Nullable
	public Long getRefId() {
		return refId;
	}

	public void setRefId(@Nullable Long refId) {
		this.refId = refId;
	}

}
