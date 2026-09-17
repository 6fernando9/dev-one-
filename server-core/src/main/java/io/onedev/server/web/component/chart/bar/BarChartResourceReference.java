package io.onedev.server.web.component.chart.bar;

import java.util.List;

import org.apache.wicket.markup.head.CssHeaderItem;
import org.apache.wicket.markup.head.HeaderItem;
import org.apache.wicket.markup.head.JavaScriptHeaderItem;

import io.onedev.server.web.asset.echarts.EChartsResourceReference;
import io.onedev.server.web.page.base.BaseDependentCssResourceReference;
import io.onedev.server.web.page.base.BaseDependentResourceReference;

public class BarChartResourceReference extends BaseDependentResourceReference {

	private static final long serialVersionUID = 1L;

	public BarChartResourceReference() {
		super(BarChartResourceReference.class, "bar-chart.js");
	}

	@Override
	public List<HeaderItem> getDependencies() {
		List<HeaderItem> dependencies = super.getDependencies();
		dependencies.add(JavaScriptHeaderItem.forReference(new EChartsResourceReference()));
		dependencies.add(CssHeaderItem.forReference(new BaseDependentCssResourceReference(
				BarChartResourceReference.class, "bar-chart.css")));
		return dependencies;
	}

}
