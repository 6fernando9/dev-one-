package io.onedev.server.web.page.admin.auditlog;

import static io.onedev.server.web.translation.Translation._T;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
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
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.Model;
import org.jspecify.annotations.Nullable;

import io.onedev.server.model.AuditEvent;
import io.onedev.server.model.Project;
import io.onedev.server.model.support.AuditEventSeverity;
import io.onedev.server.model.support.AuditEventType;
import io.onedev.server.service.AuditEventService;
import io.onedev.server.util.DateRange;
import io.onedev.server.util.DateUtils;
import io.onedev.server.web.WebConstants;
import io.onedev.server.web.behavior.OnTypingDoneBehavior;
import io.onedev.server.web.component.chart.bar.BarChartPanel;
import io.onedev.server.web.component.chart.bar.BarData;
import io.onedev.server.web.component.datatable.DefaultDataTable;
import io.onedev.server.web.component.datepicker.DateRangePicker;
import io.onedev.server.web.component.floating.FloatingPanel;
import io.onedev.server.web.component.menu.MenuItem;
import io.onedev.server.web.component.menu.MenuLink;
import io.onedev.server.web.component.modal.ModalPanel;
import io.onedev.server.web.component.user.UserAvatar;
import io.onedev.server.web.util.LoadableDetachableDataProvider;

public class AuditEventListPanel extends Panel {

	private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("MMM d");

	private final Project project;

	private final boolean showProjectColumn;

	@Inject
	private AuditEventService auditEventService;

	private String filterText = "";

	private AuditEventSeverity filterSeverity;

	private AuditEventType filterType;

	private Boolean filterScope;

	private Date filterFrom;

	private Date filterTo;

	private String activePreset = "Last 7 days";

	private DataTable<AuditEvent, Void> table;

	private WebMarkupContainer chartContainer;

	private Label rangeLabel;

	private MenuLink severityFilterLink;

	private MenuLink actionFilterLink;

	private MenuLink scopeFilterLink;

	private MenuLink dateRangeLink;

	public AuditEventListPanel(String id, @Nullable Project project, boolean showProjectColumn) {
		super(id);
		this.project = project;
		this.showProjectColumn = showProjectColumn;

		var zone = ZoneId.systemDefault();
		var today = LocalDate.now();
		filterFrom = Date.from(today.minusDays(6).atStartOfDay(zone).toInstant());
		filterTo = new Date();
	}

	private ZoneId getZone() {
		return ZoneId.systemDefault();
	}

	private void applyPreset(String preset) {
		var now = new Date();
		var today = LocalDate.now();
		var zone = getZone();
		activePreset = preset;
		switch (preset) {
			case "Today":
				filterFrom = Date.from(today.atStartOfDay(zone).toInstant());
				filterTo = now;
				break;
			case "Yesterday":
				filterFrom = Date.from(today.minusDays(1).atStartOfDay(zone).toInstant());
				filterTo = Date.from(today.atStartOfDay(zone).toInstant());
				break;
			case "Last 24 hours":
				filterFrom = new Date(now.getTime() - 24L * 3600 * 1000);
				filterTo = now;
				break;
			case "Last 30 days":
				filterFrom = Date.from(today.minusDays(29).atStartOfDay(zone).toInstant());
				filterTo = now;
				break;
			case "Last 60 days":
				filterFrom = Date.from(today.minusDays(59).atStartOfDay(zone).toInstant());
				filterTo = now;
				break;
			case "Last 90 days":
				filterFrom = Date.from(today.minusDays(89).atStartOfDay(zone).toInstant());
				filterTo = now;
				break;
			default: // Last 7 days
				activePreset = "Last 7 days";
				filterFrom = Date.from(today.minusDays(6).atStartOfDay(zone).toInstant());
				filterTo = now;
				break;
		}
	}

	private void applyCustomRange(DateRange range) {
		var zone = getZone();
		activePreset = "Custom";
		filterFrom = Date.from(range.getFrom().atStartOfDay(zone).toInstant());
		filterTo = Date.from(range.getTo().atTime(LocalTime.MAX).atZone(zone).toInstant());
	}

	private void refresh(AjaxRequestTarget target) {
		table.setCurrentPage(0);
		target.add(table);
		target.add(chartContainer);
		target.add(rangeLabel);
		target.add(dateRangeLink);
	}

	@Override
	protected void onInitialize() {
		super.onInitialize();

		add(rangeLabel = new Label("rangeLabel", new AbstractReadOnlyModel<String>() {
			@Override
			public String getObject() {
				var zone = getZone();
				var from = filterFrom.toInstant().atZone(zone).toLocalDate().format(DAY_FORMATTER);
				var to = filterTo.toInstant().atZone(zone).toLocalDate().format(DAY_FORMATTER);
				return from + " - " + to;
			}
		}));
		rangeLabel.setOutputMarkupId(true);

		add(dateRangeLink = new MenuLink("dateRange") {
			@Override
			protected void onInitialize() {
				super.onInitialize();
				add(new Label("label", new AbstractReadOnlyModel<String>() {
					@Override
					public String getObject() {
						return activePreset;
					}
				}));
			}

			@Override
			protected List<MenuItem> getMenuItems(FloatingPanel dropdown) {
				List<MenuItem> items = new ArrayList<>();
				for (String preset : List.of("Today", "Yesterday", "Last 24 hours",
						"Last 7 days", "Last 30 days", "Last 60 days", "Last 90 days")) {
					items.add(new MenuItem() {
						@Override
						public String getLabel() {
							return preset;
						}

						@Override
						public boolean isSelected() {
							return preset.equals(activePreset);
						}

						@Override
						public WebMarkupContainer newLink(String id) {
							return new AjaxLink<Void>(id) {
								@Override
								public void onClick(AjaxRequestTarget target) {
									dropdown.close();
									applyPreset(preset);
									refresh(target);
								}
							};
						}
					});
				}
				return items;
			}
		});
		dateRangeLink.setOutputMarkupId(true);

		var dateRangeModel = new IModel<DateRange>() {
			@Override
			public void detach() {
			}

			@Override
			public DateRange getObject() {
				var zone = getZone();
				return new DateRange(
						filterFrom.toInstant().atZone(zone).toLocalDate(),
						filterTo.toInstant().atZone(zone).toLocalDate());
			}

			@Override
			public void setObject(DateRange object) {
				if (object != null)
					applyCustomRange(object);
			}
		};
		var dateRangePicker = new DateRangePicker("dateRangePicker", dateRangeModel);
		dateRangePicker.add(new AjaxFormComponentUpdatingBehavior("change") {
			@Override
			protected void onUpdate(AjaxRequestTarget target) {
				refresh(target);
			}
		});
		add(dateRangePicker);

		add(chartContainer = new WebMarkupContainer("chartContainer"));
		chartContainer.setOutputMarkupId(true);
		chartContainer.add(new BarChartPanel("chart", new LoadableDetachableModel<BarData>() {
			@Override
			protected BarData load() {
				Map<LocalDate, Long> counts = auditEventService.countByDay(project, filterType,
						filterSeverity, project != null ? null : filterScope, filterFrom, filterTo);
				List<String> labels = new ArrayList<>();
				List<Long> values = new ArrayList<>();
				for (var entry : counts.entrySet()) {
					labels.add(entry.getKey().format(DAY_FORMATTER));
					values.add(entry.getValue());
				}
				return new BarData(_T("Number of changes"), labels, values);
			}
		}));

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
				if (target != null)
					refresh(target);
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

		add(actionFilterLink = new MenuLink("filterAction") {
			@Override
			protected void onInitialize() {
				super.onInitialize();
				add(new Label("label", new AbstractReadOnlyModel<String>() {
					@Override
					public String getObject() {
						return filterType != null ? filterType.name() : _T("All Actions");
					}
				}));
			}

			@Override
			protected List<MenuItem> getMenuItems(FloatingPanel dropdown) {
				List<MenuItem> items = new ArrayList<>();
				items.add(newActionItem(dropdown, null));
				for (AuditEventType type : AuditEventType.values())
					items.add(newActionItem(dropdown, type));
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

		columns.add(new AbstractColumn<AuditEvent, Void>(Model.of(_T("Date & Time"))) {
			@Override
			public void populateItem(Item<ICellPopulator<AuditEvent>> cellItem, String componentId,
					IModel<AuditEvent> rowModel) {
				Fragment fragment = new Fragment(componentId, "dateFrag", AuditEventListPanel.this);
				AuditEvent event = rowModel.getObject();
				fragment.add(new Label("time", DateUtils.formatDateTime(event.getDate())));
				fragment.add(new Label("day", relativeDay(event.getDate())));
				cellItem.add(fragment);
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

		columns.add(new AbstractColumn<AuditEvent, Void>(Model.of(_T("Changed By"))) {
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
				cellItem.add(fragment);
			}
		});

		columns.add(new AbstractColumn<AuditEvent, Void>(Model.of(_T("IP Address"))) {
			@Override
			public void populateItem(Item<ICellPopulator<AuditEvent>> cellItem, String componentId,
					IModel<AuditEvent> rowModel) {
				String ip = rowModel.getObject().getIpAddress();
				cellItem.add(new Label(componentId, ip != null ? ip : "-")
						.add(AttributeAppender.append("class", "text-truncate d-inline-block mw-100")));
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

		columns.add(new AbstractColumn<AuditEvent, Void>(Model.of(_T("Change Details"))) {
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
				return auditEventService.query(project, filterType, filterSeverity,
						project != null ? null : filterScope, filterFrom, filterTo,
						filterText, (int) first, (int) count).iterator();
			}

			@Override
			public long calcSize() {
				return auditEventService.count(project, filterType, filterSeverity,
						project != null ? null : filterScope, filterFrom, filterTo, filterText);
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

	private String relativeDay(Date date) {
		var zone = getZone();
		var day = date.toInstant().atZone(zone).toLocalDate();
		var today = LocalDate.now();
		if (day.equals(today))
			return _T("Today");
		else if (day.equals(today.minusDays(1)))
			return _T("Yesterday");
		else
			return day.format(DAY_FORMATTER);
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
						refresh(target);
						target.add(severityFilterLink);
					}
				};
			}
		};
	}

	private MenuItem newActionItem(FloatingPanel dropdown, AuditEventType type) {
		return new MenuItem() {
			@Override
			public String getLabel() {
				return type != null ? type.name() : _T("All Actions");
			}

			@Override
			public boolean isSelected() {
				return type == null ? filterType == null : type == filterType;
			}

			@Override
			public WebMarkupContainer newLink(String id) {
				return new AjaxLink<Void>(id) {
					@Override
					public void onClick(AjaxRequestTarget target) {
						dropdown.close();
						filterType = type;
						refresh(target);
						target.add(actionFilterLink);
					}
				};
			}
		};
	}

	private MenuItem newScopeItem(FloatingPanel dropdown, Boolean scope) {		return new MenuItem() {
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
						refresh(target);
						target.add(scopeFilterLink);
					}
				};
			}
		};
	}

}
