onedev.server.barChart = {
	onDomReady: function(containerId, barData, darkMode) {
		var $chart = $("#" + containerId + ">.bar-chart");

		if (barData && barData.xAxisValues.length != 0) {
			var chart = echarts.init($chart[0]);
			chart.setOption({
				color: [darkMode?'#4A7DFF':'#2F5FEB'],
				xAxis: {
					type: 'category',
					data: barData.xAxisValues,
					axisLabel: {
						color: darkMode?'#cdcdde':'#3F4254'
					}
				},
			    yAxis: {
			        type: 'value',
					minInterval: 1,
			        splitLine: {
			            lineStyle: {
			                color: darkMode?'#535370':'#E4E6EF'
			            }
			        },
			    	axisLine: {
			    		show: false
			    	},
			    	axisLabel: {
						color: darkMode?'#cdcdde':'#3F4254'
			    	}
			    },
				tooltip: {
					trigger: 'axis',
					textStyle: {
						color: darkMode? 'white': '#535370'
					},
					borderColor: darkMode? '#36364F': 'white',
					backgroundColor: darkMode? '#36364F': 'white'
				},
				legend: {
					show: true,
					x: "right",
					data: [barData.seriesName],
					textStyle: {
						color: darkMode?'#cdcdde':'#3F4254'
					}
				},
				series: [{
					name: barData.seriesName,
					data: barData.yAxisValues,
					type: 'bar',
					barMaxWidth: 48,
					animation: false
				}]
			});

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
