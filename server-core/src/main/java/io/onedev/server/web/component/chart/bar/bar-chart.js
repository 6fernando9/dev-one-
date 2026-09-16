onedev.server.barChart = {
	colors: ['#5470C6','#91CC75','#FAC858','#EE6666','#73C0DE','#3BA272','#FC8452','#9A60B4','#EA7CCC','#5AB1EF','#D87C7C','#8D98B3','#98B44C','#E5CF0D','#95706D','#DC69AA','#07A2A4','#9A7FD1','#588DD5','#F5994E','#C05050','#59678C','#C9AB00','#7EB77A','#F47F6E','#E5C243','#D1B66C','#7EB77A','#F47F6E','#E5C243','#D1B66C','#79678C'],
	onDomReady: function(containerId, barData, darkMode) {
		var $chart = $("#" + containerId + ">.bar-chart");

		if (barData && barData.xAxisValues.length != 0) {
			var isMulti = barData.seriesNames && barData.yAxisValuesList;

			var series = [];
			var legendData = [];

			if (isMulti) {
				for (var i = 0; i < barData.seriesNames.length; i++) {
					legendData.push(barData.seriesNames[i]);
					series.push({
						name: barData.seriesNames[i],
						data: barData.yAxisValuesList[i],
						type: 'bar',
						barMaxWidth: 32,
						animation: false,
						itemStyle: {
							color: onedev.server.barChart.colors[i % onedev.server.barChart.colors.length]
						}
					});
				}
			} else {
				series.push({
					name: barData.seriesName,
					data: barData.yAxisValues,
					type: 'bar',
					barMaxWidth: 48,
					animation: false,
					itemStyle: {
						color: darkMode ? '#4A7DFF' : '#2F5FEB'
					}
				});
				legendData.push(barData.seriesName);
			}

			var option = {
				xAxis: {
					type: 'category',
					data: barData.xAxisValues,
					axisLabel: {
						color: darkMode ? '#cdcdde' : '#3F4254'
					}
				},
				yAxis: {
					type: 'value',
					minInterval: 1,
					splitLine: {
						lineStyle: {
							color: darkMode ? '#535370' : '#E4E6EF'
						}
					},
					axisLine: {
						show: false
					},
					axisLabel: {
						color: darkMode ? '#cdcdde' : '#3F4254'
					}
				},
				tooltip: {
					trigger: 'axis',
					textStyle: {
						color: darkMode ? 'white' : '#535370'
					},
					borderColor: darkMode ? '#36364F' : 'white',
					backgroundColor: darkMode ? '#36364F' : 'white'
				},
				legend: {
					show: isMulti && legendData.length > 1,
					type: 'scroll',
					x: 'center',
					bottom: 0,
					textStyle: {
						color: darkMode ? '#cdcdde' : '#3F4254',
						fontSize: 11
					},
					pageTextStyle: {
						color: darkMode ? '#cdcdde' : '#3F4254'
					},
					pageIconColor: darkMode ? '#cdcdde' : '#3F4254',
					pageIconInactiveColor: darkMode ? '#555' : '#ccc'
				},
				grid: {
					left: '3%',
					right: '3%',
					containLabel: true,
					bottom: isMulti && legendData.length > 1 ? 60 : 10
				},
				series: series
			};

			var chart = echarts.init($chart[0]);
			chart.setOption(option);

			$chart.on("resized", function() {
				setTimeout(function() {
					chart.resize();
				});
			});
		} else {
			$chart.append("No Data").addClass("d-flex align-items-center h1 text-muted justify-content-center");
		}
	}
}
