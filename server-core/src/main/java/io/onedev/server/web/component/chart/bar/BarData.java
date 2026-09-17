package io.onedev.server.web.component.chart.bar;

import java.io.Serializable;
import java.util.List;

public class BarData implements Serializable {

	private static final long serialVersionUID = 1L;

	private final String seriesName;

	private final List<String> xAxisValues;

	private final List<Long> yAxisValues;

	private final List<String> seriesNames;

	private final List<List<Long>> yAxisValuesList;

	public BarData(String seriesName, List<String> xAxisValues, List<Long> yAxisValues) {
		this.seriesName = seriesName;
		this.xAxisValues = xAxisValues;
		this.yAxisValues = yAxisValues;
		this.seriesNames = null;
		this.yAxisValuesList = null;
	}

	public BarData(List<String> seriesNames, List<String> xAxisValues, List<List<Long>> yAxisValuesList) {
		this.seriesName = null;
		this.xAxisValues = xAxisValues;
		this.yAxisValues = null;
		this.seriesNames = seriesNames;
		this.yAxisValuesList = yAxisValuesList;
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

	public List<String> getSeriesNames() {
		return seriesNames;
	}

	public List<List<Long>> getYAxisValuesList() {
		return yAxisValuesList;
	}

}
