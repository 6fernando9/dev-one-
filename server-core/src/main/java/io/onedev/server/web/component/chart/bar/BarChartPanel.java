package io.onedev.server.web.component.chart.bar;

import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptHeaderItem;
import org.apache.wicket.markup.head.OnDomReadyHeaderItem;
import org.apache.wicket.markup.html.panel.GenericPanel;
import org.apache.wicket.model.IModel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.onedev.server.OneDev;
import io.onedev.server.web.page.base.BasePage;

public class BarChartPanel extends GenericPanel<BarData> {

	public BarChartPanel(String id, IModel<BarData> model) {
		super(id, model);
	}

	@Override
	public void renderHead(IHeaderResponse response) {
		super.renderHead(response);

		response.render(JavaScriptHeaderItem.forReference(new BarChartResourceReference()));

		try {
			ObjectMapper mapper = OneDev.getInstance(ObjectMapper.class);
			BasePage page = (BasePage) getPage();
			String script = String.format("onedev.server.barChart.onDomReady('%s', %s, %b);",
					getMarkupId(true), mapper.writeValueAsString(getModelObject()),
					page.isDarkMode());
			response.render(OnDomReadyHeaderItem.forScript(script));
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

}
