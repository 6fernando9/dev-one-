package io.onedev.server.ai;

import java.io.Serializable;
import java.util.List;

import org.apache.wicket.Page;

/**
 * Contributes {@link ChatTool}s for a given Wicket page. Implementations inspect the page
 * (type, state, permissions) and return an empty list when they have nothing to offer.
 */
public interface ChatToolsContribution extends Serializable {

	List<ChatTool> getChatTools(Page page);

}
