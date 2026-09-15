package io.onedev.server.web.component.chart.bar;

import java.io.Serializable;
import java.util.List;

public class BarData implements Serializable {

	private static final long serialVersionUID = 1L;

	private final String seriesName;

	private final List<String> xAxisValues;

	private final List<Long> yAxisValues;

	public BarData(String seriesName, List<String> xAxisValues, List<Long> yAxisValues) {
		this.seriesName = seriesName;
		this.xAxisValues = xAxisValues;
		this.yAxisValues = yAxisValues;
	}

	public String getSeriesName() {
		return seriesName;
	}

	public List<String> getXAxisValues() {
		return xAxisValues;
	}

	public List<Long> getYAxisValues() {
		return yAxisValues;
	}

}
