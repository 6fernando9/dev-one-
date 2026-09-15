package io.onedev.server.web.page.admin.auditlog;

import static io.onedev.server.web.translation.Translation._T;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.inject.Inject;

import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.extensions.markup.html.repeater.data.grid.ICellPopulator;
import org.apache.wicket.extensions.markup.html.repeater.data.table.AbstractColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.DataTable;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.model.AbstractReadOnlyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.jspecify.annotations.Nullable;

import io.onedev.server.model.AuditEvent;
import io.onedev.server.model.Project;
import io.onedev.server.model.support.AuditEventSeverity;
import io.onedev.server.service.AuditEventService;
import io.onedev.server.util.DateUtils;
import io.onedev.server.web.WebConstants;
import io.onedev.server.web.behavior.OnTypingDoneBehavior;
import io.onedev.server.web.component.datatable.DefaultDataTable;
import io.onedev.server.web.component.floating.FloatingPanel;
import io.onedev.server.web.component.menu.MenuItem;
import io.onedev.server.web.component.menu.MenuLink;
import io.onedev.server.web.component.modal.ModalPanel;
import io.onedev.server.web.component.user.UserAvatar;
import io.onedev.server.web.util.LoadableDetachableDataProvider;

public class AuditEventListPanel extends Panel {

	private final Project project;

	private final boolean showProjectColumn;

	@Inject
	private AuditEventService auditEventService;

	private String filterText = "";

	private AuditEventSeverity filterSeverity;

	private Boolean filterScope;

	private DataTable<AuditEvent, Void> table;

	private MenuLink severityFilterLink;

	private MenuLink scopeFilterLink;

	public AuditEventListPanel(String id, @Nullable Project project, boolean showProjectColumn) {
		super(id);
		this.project = project;
		this.showProjectColumn = showProjectColumn;
	}

	@Override
	protected void onInitialize() {
		super.onInitialize();

		TextField<String> searchField = new TextField<>("filterText", new IModel<String>() {
			@Override
			public void detach() {
			}
			@Override
			public String getObject() {
				return filterText;
			}
			@Override
			public void setObject(String object) {
				filterText = object;
				var target = getRequestCycle().find(AjaxRequestTarget.class);
				if (target != null) {
					table.setCurrentPage(0);
					target.add(table);
				}
			}
		});
		searchField.add(new OnTypingDoneBehavior(200) {
			@Override
			protected void onTypingDone(AjaxRequestTarget target) {
			}
		});
		add(searchField);

		add(severityFilterLink = new MenuLink("filterSeverity") {
			@Override
			protected void onInitialize() {
				super.onInitialize();
				add(new Label("label", new AbstractReadOnlyModel<String>() {
					@Override
					public String getObject() {
						return filterSeverity != null ? filterSeverity.name() : _T("All Severities");
					}
				}));
			}

			@Override
			protected List<MenuItem> getMenuItems(FloatingPanel dropdown) {
				List<MenuItem> items = new ArrayList<>();
				items.add(newSeverityItem(dropdown, null));
				for (AuditEventSeverity severity : AuditEventSeverity.values())
					items.add(newSeverityItem(dropdown, severity));
				return items;
			}
		});

		add(scopeFilterLink = new MenuLink("filterScope") {
			@Override
			protected void onInitialize() {
				super.onInitialize();
				add(new Label("label", new AbstractReadOnlyModel<String>() {
					@Override
					public String getObject() {
						if (filterScope == null)
							return _T("All Scopes");
						else if (filterScope)
							return _T("Project");
						else
							return _T("System");
					}
				}));
			}

			@Override
			protected List<MenuItem> getMenuItems(FloatingPanel dropdown) {
				List<MenuItem> items = new ArrayList<>();
				items.add(newScopeItem(dropdown, null));
				items.add(newScopeItem(dropdown, Boolean.TRUE));
				items.add(newScopeItem(dropdown, Boolean.FALSE));
				return items;
			}

			@Override
			protected void onConfigure() {
				super.onConfigure();
				setVisible(showProjectColumn);
			}
		});

		List<IColumn<AuditEvent, Void>> columns = new ArrayList<>();

		columns.add(new AbstractColumn<AuditEvent, Void>(Model.of(_T("Date"))) {
			@Override
			public void populateItem(Item<ICellPopulator<AuditEvent>> cellItem, String componentId,
					IModel<AuditEvent> rowModel) {
				cellItem.add(new Label(componentId,
						DateUtils.formatDateTime(rowModel.getObject().getDate())));
			}
		});

		columns.add(new AbstractColumn<AuditEvent, Void>(Model.of(_T("Severity"))) {
			@Override
			public void populateItem(Item<ICellPopulator<AuditEvent>> cellItem, String componentId,
					IModel<AuditEvent> rowModel) {
				Fragment fragment = new Fragment(componentId, "severityFrag", AuditEventListPanel.this);
				String css;
				switch (rowModel.getObject().getEventSeverity()) {
					case CRITICAL: css = "badge badge-danger badge-sm"; break;
					case WARNING: css = "badge badge-warning badge-sm"; break;
					default: css = "badge badge-info badge-sm"; break;
				}
				fragment.add(new Label("badge", rowModel.getObject().getEventSeverity().name())
						.add(AttributeAppender.append("class", css)));
				cellItem.add(fragment);
			}
		});

		columns.add(new AbstractColumn<AuditEvent, Void>(Model.of(_T("Action"))) {
			@Override
			public void populateItem(Item<ICellPopulator<AuditEvent>> cellItem, String componentId,
					IModel<AuditEvent> rowModel) {
				cellItem.add(new Label(componentId, rowModel.getObject().getEventType().name())
						.add(AttributeAppender.append("class", "text-monospace")));
			}
		});

		columns.add(new AbstractColumn<AuditEvent, Void>(Model.of(_T("Actor"))) {
			@Override
			public void populateItem(Item<ICellPopulator<AuditEvent>> cellItem, String componentId,
					IModel<AuditEvent> rowModel) {
				Fragment fragment = new Fragment(componentId, "actorFrag", AuditEventListPanel.this);
				AuditEvent event = rowModel.getObject();
				if (event.getActor() != null)
					fragment.add(new UserAvatar("avatar", event.getActor()));
				else
					fragment.add(new Label("avatar", initialsOf(AuditEventLinks.getActorDisplay(event)))
							.add(AttributeAppender.append("class", "avatar avatar-fallback")));
				fragment.add(AuditEventLinks.newActorRef("actor", event));
				String ip = event.getIpAddress() != null ? "(" + event.getIpAddress() + ")" : "";
				fragment.add(new Label("ip", ip));
				cellItem.add(fragment);
			}
		});

		if (showProjectColumn) {
			columns.add(new AbstractColumn<AuditEvent, Void>(Model.of(_T("Project"))) {
				@Override
				public void populateItem(Item<ICellPopulator<AuditEvent>> cellItem, String componentId,
						IModel<AuditEvent> rowModel) {
					cellItem.add(AuditEventLinks.newProjectRef(componentId, rowModel.getObject()));
				}
			});
		}

		columns.add(new AbstractColumn<AuditEvent, Void>(Model.of(_T("Summary"))) {
			@Override
			public void populateItem(Item<ICellPopulator<AuditEvent>> cellItem, String componentId,
					IModel<AuditEvent> rowModel) {
				Fragment fragment = new Fragment(componentId, "summaryFrag", AuditEventListPanel.this);
				AuditEvent event = rowModel.getObject();
				fragment.add(new Label("summary", event.getSummary()));
				fragment.add(AuditEventLinks.newTargetRef("target", event));
				cellItem.add(fragment);
			}
		});

		columns.add(new AbstractColumn<AuditEvent, Void>(Model.of("")) {
			@Override
			public void populateItem(Item<ICellPopulator<AuditEvent>> cellItem, String componentId,
					IModel<AuditEvent> rowModel) {
				Fragment fragment = new Fragment(componentId, "detailFrag", AuditEventListPanel.this);
				fragment.add(new AjaxLink<Void>("show") {
					@Override
					public void onClick(AjaxRequestTarget target) {
						new ModalPanel(target) {
							@Override
							protected Component newContent(String id) {
								return new AuditEventDetailPanel(id, rowModel.getObject(), this);
							}
						};
					}
				});
				cellItem.add(fragment);
			}

			@Override
			public String getCssClass() {
				return "actions";
			}
		});

		var dataProvider = new LoadableDetachableDataProvider<AuditEvent, Void>() {
			@Override
			public Iterator<? extends AuditEvent> iterator(long first, long count) {
				return auditEventService.query(project, null, filterSeverity,
						project != null ? null : filterScope,
						filterText, (int) first, (int) count).iterator();
			}

			@Override
			public long calcSize() {
				return auditEventService.count(project, null, filterSeverity,
						project != null ? null : filterScope, filterText);
			}

			@Override
			public IModel<AuditEvent> model(AuditEvent object) {
				return Model.of(object);
			}
		};

		add(table = new DefaultDataTable<>("events", columns, dataProvider,
				WebConstants.PAGE_SIZE, null));
		table.setOutputMarkupId(true);
	}

	private String initialsOf(String displayName) {
		if (displayName == null || displayName.isBlank())
			return "?";
		var parts = displayName.trim().split("\\s+");
		if (parts.length == 1)
			return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
		return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
	}

	private MenuItem newSeverityItem(FloatingPanel dropdown, AuditEventSeverity severity) {
		return new MenuItem() {
			@Override
			public String getLabel() {
				return severity != null ? severity.name() : _T("All Severities");
			}

			@Override
			public boolean isSelected() {
				return severity == null ? filterSeverity == null : severity == filterSeverity;
			}

			@Override
			public WebMarkupContainer newLink(String id) {
				return new AjaxLink<Void>(id) {
					@Override
					public void onClick(AjaxRequestTarget target) {
						dropdown.close();
						filterSeverity = severity;
						table.setCurrentPage(0);
						target.add(severityFilterLink);
						target.add(table);
					}
				};
			}
		};
	}

	private MenuItem newScopeItem(FloatingPanel dropdown, Boolean scope) {
		return new MenuItem() {
			@Override
			public String getLabel() {
				if (scope == null)
					return _T("All Scopes");
				else if (scope)
					return _T("Project");
				else
					return _T("System");
			}

			@Override
			public boolean isSelected() {
				return scope == null ? filterScope == null : scope.equals(filterScope);
			}

			@Override
			public WebMarkupContainer newLink(String id) {
				return new AjaxLink<Void>(id) {
					@Override
					public void onClick(AjaxRequestTarget target) {
						dropdown.close();
						filterScope = scope;
						table.setCurrentPage(0);
						target.add(scopeFilterLink);
						target.add(table);
					}
				};
			}
		};
	}

}
