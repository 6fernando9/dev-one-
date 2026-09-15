package io.onedev.server.service.impl;

import static io.onedev.server.model.AuditEvent.PROP_ACTOR;
import static io.onedev.server.model.AuditEvent.PROP_DATE;
import static io.onedev.server.model.AuditEvent.PROP_PROJECT;
import static io.onedev.server.model.AuditEvent.PROP_SEVERITY;
import static io.onedev.server.model.AuditEvent.PROP_SUMMARY;
import static io.onedev.server.model.AuditEvent.PROP_TYPE;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;

import org.apache.wicket.request.cycle.RequestCycle;
import org.hibernate.Hibernate;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;
import org.jspecify.annotations.Nullable;
import org.quartz.CronScheduleBuilder;
import org.quartz.ScheduleBuilder;

import io.onedev.server.event.Listen;
import io.onedev.server.event.entity.EntityPersisted;
import io.onedev.server.event.entity.EntityRemoved;
import io.onedev.server.event.project.build.BuildFinished;
import io.onedev.server.event.project.issue.IssueChanged;
import io.onedev.server.event.project.pullrequest.PullRequestChanged;
import io.onedev.server.event.system.SystemStarted;
import io.onedev.server.event.system.SystemStopping;
import io.onedev.server.model.AuditEvent;
import io.onedev.server.model.Group;
import io.onedev.server.model.Membership;
import io.onedev.server.model.Project;
import io.onedev.server.model.Role;
import io.onedev.server.model.Setting;
import io.onedev.server.model.User;
import io.onedev.server.model.support.AuditEventSeverity;
import io.onedev.server.model.support.AuditEventType;
import io.onedev.server.model.support.issue.changedata.IssueStateChangeData;
import io.onedev.server.model.support.pullrequest.changedata.PullRequestMergeData;
import io.onedev.server.persistence.annotation.Sessional;
import io.onedev.server.persistence.annotation.Transactional;
import io.onedev.server.security.SecurityUtils;
import io.onedev.server.service.AuditEventService;
import io.onedev.server.service.SettingService;
import io.onedev.server.taskschedule.SchedulableTask;
import io.onedev.server.taskschedule.TaskScheduler;

@Singleton
public class DefaultAuditEventService extends BaseEntityService<AuditEvent>
		implements AuditEventService, SchedulableTask {

	@Inject
	private SettingService settingService;

	@Inject
	private TaskScheduler taskScheduler;

	private String taskId;

	@Transactional
	@Override
	public void record(AuditEventType type, @Nullable User actor, @Nullable String actorName,
			@Nullable String ipAddress, @Nullable Project project, String summary,
			@Nullable String details, @Nullable String refType, @Nullable Long refId) {
		var event = new AuditEvent();
		event.setEventType(type);
		event.setActor(actor);
		if (actorName != null)
			event.setActorName(actorName);
		else if (actor != null)
			event.setActorName(actor.getDisplayName());
		event.setIpAddress(ipAddress);
		event.setProject(project);
		if (project != null)
			event.setProjectPath(project.getPath());
		event.setDate(new Date());
		event.setSummary(summary);
		event.setDetails(details);
		event.setRefType(refType);
		event.setRefId(refId);
		dao.persist(event);
	}

	@Transactional
	@Override
	public void recordLoginSucceeded(User actor, String via) {
		record(AuditEventType.LOGIN_SUCCEEDED, actor, null, getCurrentIpAddress(), null,
				actor.getDisplayName() + " logged in via " + via, null, "User", actor.getId());
	}

	@Transactional
	@Override
	public void recordLoginFailed(@Nullable String userName, String via) {
		var summary = userName != null
				? "failed login attempt for '" + userName + "' via " + via
				: "failed login attempt via " + via;
		record(AuditEventType.LOGIN_FAILED, null, userName, getCurrentIpAddress(), null,
				summary, null, null, null);
	}

	@Transactional
	@Override
	public void recordLogout(User actor) {
		record(AuditEventType.LOGOUT, actor, null, getCurrentIpAddress(), null,
				actor.getDisplayName() + " logged out", null, "User", actor.getId());
	}

	@Transactional
	@Listen
	public void on(PullRequestChanged event) {
		if (event.getChange().getData() instanceof PullRequestMergeData) {
			var request = event.getRequest();
			var user = event.getUser();
			record(AuditEventType.PULL_REQUEST_MERGED, user, null, getCurrentIpAddress(),
					event.getProject(),
					getActorDisplay(user) + " merged pull request #" + request.getNumber()
							+ " (" + request.getTitle() + ")",
					null, "PullRequest", request.getId());
		}
	}

	@Transactional
	@Listen
	public void on(IssueChanged event) {
		if (event.getChange().getData() instanceof IssueStateChangeData) {
			var data = (IssueStateChangeData) event.getChange().getData();
			var issue = event.getIssue();
			var user = event.getUser();
			record(AuditEventType.ISSUE_STATE_CHANGED, user, null, getCurrentIpAddress(),
					event.getProject(),
					getActorDisplay(user) + " changed state of issue #" + issue.getNumber()
							+ " from '" + data.getOldState() + "' to '" + data.getNewState() + "'",
					null, "Issue", issue.getId());
		}
	}

	@Transactional
	@Listen
	public void on(BuildFinished event) {
		var build = event.getBuild();
		var actor = build.getCanceller() != null ? build.getCanceller() : build.getSubmitter();
		record(AuditEventType.BUILD_FINISHED, actor, null, getCurrentIpAddress(),
				build.getProject(),
				getActorDisplay(actor) + " finished build #" + build.getNumber()
						+ " (" + build.getJobName() + ") with status " + build.getStatus(),
				null, "Build", build.getId());
	}

	@Transactional
	@Listen
	public void on(EntityPersisted event) {
		var entity = event.getEntity();
		if (entity instanceof AuditEvent)
			return;
		if (entity instanceof Role)
			recordRole((Role) entity, event.isNewEntity());
		else if (entity instanceof User)
			recordUser((User) entity, event.isNewEntity());
		else if (entity instanceof Group)
			recordGroup((Group) entity, event.isNewEntity());
		else if (entity instanceof Membership)
			recordMembership((Membership) entity, event.isNewEntity());
		else if (entity instanceof Project)
			recordProject((Project) entity, event.isNewEntity());
		else if (entity instanceof Setting)
			recordSetting((Setting) entity);
	}

	@Transactional
	@Listen
	public void on(EntityRemoved event) {
		var entity = event.getEntity();
		if (entity instanceof AuditEvent)
			return;
		var actor = getCurrentActor();
		var ipAddress = getCurrentIpAddress();
		var actorDisplay = getActorDisplay(actor);
		if (entity instanceof Role) {
			var role = (Role) entity;
			record(AuditEventType.ROLE_DELETED, actor, null, ipAddress, null,
					actorDisplay + " deleted role '" + role.getName() + "'", null, "Role", role.getId());
		} else if (entity instanceof User) {
			var user = (User) entity;
			record(AuditEventType.USER_DELETED, actor, null, ipAddress, null,
					actorDisplay + " deleted user '" + user.getName() + "'", null, "User", user.getId());
		} else if (entity instanceof Group) {
			var group = (Group) entity;
			record(AuditEventType.GROUP_DELETED, actor, null, ipAddress, null,
					actorDisplay + " deleted group '" + group.getName() + "'", null, "Group", group.getId());
		} else if (entity instanceof Membership) {
			var membership = (Membership) entity;
			record(AuditEventType.MEMBERSHIP_DELETED, actor, null, ipAddress, null,
					actorDisplay + " removed '" + membership.getUser().getDisplayName()
							+ "' from group '" + membership.getGroup().getName() + "'",
					null, null, null);
		} else if (entity instanceof Project) {
			var project = (Project) entity;
			var deleted = new AuditEvent();
			deleted.setEventType(AuditEventType.PROJECT_DELETED);
			deleted.setActor(actor);
			deleted.setActorName(actor != null ? actor.getDisplayName() : actorDisplay);
			deleted.setIpAddress(ipAddress);
			deleted.setDate(new Date());
			deleted.setProjectPath(project.getPath());
			deleted.setSummary(actorDisplay + " deleted project '" + project.getPath() + "'");
			deleted.setRefType("Project");
			deleted.setRefId(project.getId());
			dao.persist(deleted);
		}
	}

	private void recordRole(Role role, boolean isNew) {
		var actor = getCurrentActor();
		var actorDisplay = getActorDisplay(actor);
		var details = "permissions: " + String.join(", ", getEnabledPermissions(role));
		if (isNew) {
			record(AuditEventType.ROLE_CREATED, actor, null, getCurrentIpAddress(), null,
					actorDisplay + " created role '" + role.getName() + "'", details, "Role", role.getId());
		} else {
			record(AuditEventType.ROLE_UPDATED, actor, null, getCurrentIpAddress(), null,
					actorDisplay + " updated role '" + role.getName() + "'", details, "Role", role.getId());
		}
	}

	private void recordUser(User user, boolean isNew) {
		var actor = getCurrentActor();
		var actorDisplay = getActorDisplay(actor);
		if (isNew) {
			record(AuditEventType.USER_CREATED, actor, null, getCurrentIpAddress(), null,
					actorDisplay + " created user '" + user.getName() + "'", null, "User", user.getId());
		} else if (user.isDisabled()) {
			record(AuditEventType.USER_DISABLED, actor, null, getCurrentIpAddress(), null,
					actorDisplay + " disabled user '" + user.getName() + "'", null, "User", user.getId());
		} else {
			record(AuditEventType.USER_UPDATED, actor, null, getCurrentIpAddress(), null,
					actorDisplay + " updated user '" + user.getName() + "'", null, "User", user.getId());
		}
	}

	private void recordGroup(Group group, boolean isNew) {
		var actor = getCurrentActor();
		var actorDisplay = getActorDisplay(actor);
		if (isNew) {
			record(AuditEventType.GROUP_CREATED, actor, null, getCurrentIpAddress(), null,
					actorDisplay + " created group '" + group.getName() + "'", null, "Group", group.getId());
		} else {
			record(AuditEventType.GROUP_UPDATED, actor, null, getCurrentIpAddress(), null,
					actorDisplay + " updated group '" + group.getName() + "'", null, "Group", group.getId());
		}
	}

	private void recordMembership(Membership membership, boolean isNew) {
		var actor = getCurrentActor();
		var actorDisplay = getActorDisplay(actor);
		var summary = actorDisplay + " added '" + membership.getUser().getDisplayName()
				+ "' to group '" + membership.getGroup().getName() + "'";
		if (!isNew)
			summary = actorDisplay + " updated membership of '" + membership.getUser().getDisplayName()
					+ "' in group '" + membership.getGroup().getName() + "'";
		record(AuditEventType.MEMBERSHIP_CREATED, actor, null, getCurrentIpAddress(), null,
				summary, null, null, null);
	}

	private void recordProject(Project project, boolean isNew) {
		var actor = getCurrentActor();
		var actorDisplay = getActorDisplay(actor);
		if (isNew) {
			var created = new AuditEvent();
			created.setEventType(AuditEventType.PROJECT_CREATED);
			created.setActor(actor);
			created.setActorName(actor != null ? actor.getDisplayName() : actorDisplay);
			created.setIpAddress(getCurrentIpAddress());
			created.setDate(new Date());
			created.setProject(project);
			created.setProjectPath(project.getPath());
			created.setSummary(actorDisplay + " created project '" + project.getPath() + "'");
			created.setRefType("Project");
			created.setRefId(project.getId());
			dao.persist(created);
		} else {
			var updated = new AuditEvent();
			updated.setEventType(AuditEventType.PROJECT_SETTING_UPDATED);
			updated.setActor(actor);
			updated.setActorName(actor != null ? actor.getDisplayName() : actorDisplay);
			updated.setIpAddress(getCurrentIpAddress());
			updated.setDate(new Date());
			updated.setProject(project);
			updated.setProjectPath(project.getPath());
			updated.setSummary(actorDisplay + " updated settings of project '" + project.getPath() + "'");
			updated.setRefType("Project");
			updated.setRefId(project.getId());
			dao.persist(updated);
		}
	}

	private void recordSetting(Setting setting) {
		var actor = getCurrentActor();
		var actorDisplay = getActorDisplay(actor);
		record(AuditEventType.SYSTEM_SETTING_UPDATED, actor, null, getCurrentIpAddress(), null,
				actorDisplay + " changed system setting '" + setting.getKey().name() + "'",
				null, null, null);
	}

	@Nullable
	private User getCurrentActor() {
		try {
			return SecurityUtils.getUser();
		} catch (Exception e) {
			return null;
		}
	}

	private String getActorDisplay(@Nullable User actor) {
		if (actor != null)
			return actor.getDisplayName();
		else
			return "system";
	}

	@Nullable
	private String getCurrentIpAddress() {
		try {
			var requestCycle = RequestCycle.get();
			if (requestCycle != null) {
				var containerRequest = requestCycle.getRequest().getContainerRequest();
				if (containerRequest instanceof HttpServletRequest) {
					var httpRequest = (HttpServletRequest) containerRequest;
					var forwardedFor = httpRequest.getHeader("X-Forwarded-For");
					if (forwardedFor != null && !forwardedFor.isBlank())
						return forwardedFor.split(",")[0].trim();
					return httpRequest.getRemoteAddr();
				}
			}
			var session = SecurityUtils.getSubject().getSession(false);
			if (session != null && session.getHost() != null)
				return session.getHost().toString();
		} catch (Exception e) {
			// no request context (background threads, system tasks): no IP available
		}
		return null;
	}

	private List<String> getEnabledPermissions(Role role) {
		var permissions = new ArrayList<String>();
		if (role.isManageProject())
			permissions.add("manageProject");
		if (role.isCreateChildren())
			permissions.add("createChildren");
		if (role.isManagePullRequests())
			permissions.add("managePullRequests");
		if (role.isManageCodeComments())
			permissions.add("manageCodeComments");
		if (role.isManageIssues())
			permissions.add("manageIssues");
		if (role.isAccessConfidentialIssues())
			permissions.add("accessConfidentialIssues");
		if (role.isAccessTimeTracking())
			permissions.add("accessTimeTracking");
		if (role.isScheduleIssues())
			permissions.add("scheduleIssues");
		if (role.isCanEditFieldsOfOtherIssues())
			permissions.add("canEditFieldsOfOtherIssues");
		if (role.isManageBuilds())
			permissions.add("manageBuilds");
		if (role.isUploadCache())
			permissions.add("uploadCache");
		if (role.isManageWorkspaces())
			permissions.add("manageWorkspaces");
		if (role.isCreateWorkspaces())
			permissions.add("createWorkspaces");
		return permissions;
	}

	@Sessional
	@Override
	public List<AuditEvent> query(@Nullable Project project, @Nullable AuditEventType type,
			@Nullable AuditEventSeverity severity, @Nullable Boolean projectScoped,
			@Nullable String searchTerm, int firstResult, int maxResults) {
		var criteria = newCriteria(project, type, severity, projectScoped, searchTerm);
		criteria.addOrder(Order.desc(PROP_DATE));
		var events = dao.query(criteria, firstResult, maxResults);
		for (var event : events) {
			Hibernate.initialize(event.getActor());
			Hibernate.initialize(event.getProject());
		}
		return events;
	}

	@Override
	public int count(@Nullable Project project, @Nullable AuditEventType type,
			@Nullable AuditEventSeverity severity, @Nullable Boolean projectScoped,
			@Nullable String searchTerm) {
		return dao.count(newCriteria(project, type, severity, projectScoped, searchTerm));
	}

	@Transactional
	@Override
	public void purgeBefore(Date date) {
		var query = getSession().createQuery("delete from AuditEvent where date < :date");
		query.setParameter("date", date);
		query.executeUpdate();
	}

	private io.onedev.server.persistence.dao.EntityCriteria<AuditEvent> newCriteria(
			@Nullable Project project, @Nullable AuditEventType type,
			@Nullable AuditEventSeverity severity, @Nullable Boolean projectScoped,
			@Nullable String searchTerm) {
		var criteria = newCriteria();
		if (project != null)
			criteria.add(Restrictions.eq(PROP_PROJECT, project));
		else if (projectScoped != null) {
			if (projectScoped)
				criteria.add(Restrictions.isNotNull(PROP_PROJECT));
			else
				criteria.add(Restrictions.isNull(PROP_PROJECT));
		}
		if (type != null)
			criteria.add(Restrictions.eq(PROP_TYPE, type.name()));
		if (severity != null)
			criteria.add(Restrictions.eq(PROP_SEVERITY, severity.name()));
		if (searchTerm != null && !searchTerm.isBlank()) {
			var disjunction = Restrictions.disjunction();
			disjunction.add(Restrictions.ilike(PROP_SUMMARY, "%" + searchTerm + "%"));
			disjunction.add(Restrictions.ilike(PROP_ACTOR + ".name", "%" + searchTerm + "%"));
			criteria.add(disjunction);
		}
		return criteria;
	}

	@Transactional
	@Override
	public void execute() {
		var preserveDays = settingService.getAuditSetting().getPreserveDays();
		purgeBefore(new Date(System.currentTimeMillis() - (long) preserveDays * 24 * 3600 * 1000));
	}

	@Override
	public ScheduleBuilder<?> getScheduleBuilder() {
		return CronScheduleBuilder.dailyAtHourAndMinute(0, 0);
	}

	@Listen
	public void on(SystemStarted event) {
		taskId = taskScheduler.schedule(this);
	}

	@Listen
	public void on(SystemStopping event) {
		if (taskId != null)
			taskScheduler.unschedule(taskId);
	}

}
