package io.onedev.server.web.page.admin.auditlog;

import org.apache.wicket.Page;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.jspecify.annotations.Nullable;

/**
 * Referencia trazable: renderiza un link a la entidad (usuario, proyecto, PR...)
 * cuando se puede resolver, o texto plano cuando no (p.ej. entidad eliminada o
 * dato mock sin destino real). Misma lógica servirá cuando el backend sea real.
 */
public class AuditEventRefPanel extends Panel {

	public AuditEventRefPanel(String id, String text,
			@Nullable Class<? extends Page> pageClass, @Nullable PageParameters params) {
		super(id);

		if (pageClass != null) {
			var link = new BookmarkablePageLink<Void>("link", pageClass, params != null ? params : new PageParameters());
			link.add(new Label("label", text));
			add(link);
		} else {
			add(new WebMarkupContainer("link").setVisible(false));
		}

		add(new Label("text", text).setVisible(pageClass == null));
	}

}
