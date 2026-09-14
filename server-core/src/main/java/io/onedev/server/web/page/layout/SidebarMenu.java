package io.onedev.server.web.page.layout;

import java.io.Serializable;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import org.apache.wicket.Component;

import io.onedev.server.web.component.floating.FloatingPanel;

public class SidebarMenu implements Serializable {

	// 1. SET DE OPCIONES ENTERPRISE (EE)
    private static final Set<String> BLOCKED_EE_ITEMS = Set.of(
        "Audit Log",
        "Pull Request",
        "Issue",
        "Build",
        "High Availability & Scalability",
        "State Transitions"
    );

    // 2. SET DE OTRAS OPCIONES COMUNITARIAS/BASE QUE DESEAS OCULTAR
    // Puedes poner el nombre exacto o la ruta "Padre > Hijo"
    private static final Set<String> BLOCKED_CUSTOM_ITEMS = Set.of(
        // "Subscription Management",
        "Discord Notifications",
        "Ntfy.sh Notifications"
        // "Administration > Audit Log" // Ejemplo con ruta específica
    );

	private static final long serialVersionUID = 1L;

	private final Header menuHeader;
	
	private final List<SidebarMenuItem> menuItems;
	
	public SidebarMenu(@Nullable Header menuHeader, List<SidebarMenuItem> menuItems) {
		this.menuHeader = menuHeader;
		this.menuItems = menuItems;
		sanitizarMenuItems(this.menuItems);
	}

	@Nullable
	public Header getMenuHeader() {
		return menuHeader;
	}

	public List<SidebarMenuItem> getMenuItems() {
		return menuItems;
	}
	
	@Nullable
	protected Component newMenuHeader() {
		return null;
	}

	public void insertMenuItem(SidebarMenuItem menuItem) {
       
        insertMenuItem(menuItems, menuItem);
    }
	

	
	private void insertMenuItem(List<SidebarMenuItem> menuItems, SidebarMenuItem menuItem) {
        if (menuItem == null) return;

        // String currentPath = construirRuta(parentPath, menuItem.getLabel());

        // 1. Si el ítem actual debe bloquearse, se ignora
        if (debeBloquearse(menuItem)) {
            return;
        }

        // 2. Procesamiento de submenús e hijos
        if (menuItem instanceof SidebarMenuItem.SubMenu) {
            var subMenu = (SidebarMenuItem.SubMenu) menuItem;

            // Filtra los hijos del submenú entrante
            sanitizarMenuItems(subMenu.getMenuItems());

            if (subMenu.getMenuItems().isEmpty()) {
                return;
            }

            for (var existingMenuItem: menuItems) {
                if (existingMenuItem instanceof SidebarMenuItem.SubMenu) {
                    var existingSubMenu = (SidebarMenuItem.SubMenu) existingMenuItem;
                    if (existingSubMenu.getLabel().equals(subMenu.getLabel())) {
                        for (var childMenuItem: subMenu.getMenuItems()) 
                            insertMenuItem(existingSubMenu.getMenuItems(), childMenuItem);
                        return;
                    }
                }
            }
        } 

        menuItems.add(menuItem);
    }

	private void sanitizarMenuItems(List<SidebarMenuItem> items) {
        if (items == null || items.isEmpty()) return;

        items.removeIf(this::debeBloquearse);

        for (SidebarMenuItem item : items) {
            if (item instanceof SidebarMenuItem.SubMenu) {
                SidebarMenuItem.SubMenu subMenu = (SidebarMenuItem.SubMenu) item;
                sanitizarMenuItems(subMenu.getMenuItems());
            }
        }
    }

    // Evaluación principal contra los 2 Sets y la clase SubscriptionRequired
    private boolean debeBloquearse(SidebarMenuItem item) {
        if (item == null) return true;

        String label = item.getLabel() != null ? item.getLabel().trim() : "";
        String className = item.getClass().getName();

        // REGLA A: Bloqueo automático por tipo/clase de suscripción
        if (item instanceof SidebarMenuItem.SubscriptionRequired || className.contains("SubscriptionRequired")) {
            return true;
        }

        // REGLA B: Coincidencia con el SET de opciones EE
        if (esCoincidencia(label, BLOCKED_EE_ITEMS)) {
            return true;
        }

        // REGLA C: Coincidencia con el SET de opciones personalizadas
        if (esCoincidencia(label, BLOCKED_CUSTOM_ITEMS)) {
            return true;
        }

        return false;
    }
    // Verifica si coincide el nombre simple o la ruta "Padre > Hijo"
    private boolean esCoincidencia(String label, Set<String> blockedSet) {
        for (String blocked : blockedSet) {
            if (blocked.equalsIgnoreCase(label)) {
                return true;
            }
        }
        return false;
    }

   

	private void imprimirInformacionMenuItem(SidebarMenuItem item, String tipo) {
		if (item == null) return;
		
		String label = item.getLabel() != null ? item.getLabel() : "SIN_LABEL";
		String className = item.getClass().getName();
		
		// Verificamos si es una instancia o subclase de SubscriptionRequired
		boolean esSuscripcion = item instanceof SidebarMenuItem.SubscriptionRequired 
				|| className.contains("SubscriptionRequired");

		System.out.println("--------------------------------------------------");
		System.out.println("TIPO:            " + tipo);
		System.out.println("LABEL:           " + label);
		System.out.println("CLASS:           " + className);
		System.out.println("ES SUSCRIPCIÓN?: " + (esSuscripcion ? "SÍ (EE)" : "NO (Gratuito/Base)"));
		System.out.println("--------------------------------------------------");
	}
	public void cleanup() {
		for (var it = menuItems.iterator(); it.hasNext();) {
			var menuItem = it.next();
			if (menuItem instanceof SidebarMenuItem.SubMenu) {
				var subMenu = (SidebarMenuItem.SubMenu) menuItem;
				subMenu.cleanup();
				if (subMenu.getMenuItems().isEmpty())
					it.remove();
			}
		}
	}
	
	public static class Header implements Serializable {
		
		private static final long serialVersionUID = 1L;

		private final String imageUrl;
		
		private final String label;
		
		public Header(String imageUrl, String label) {
			this.imageUrl = imageUrl;
			this.label = label;
		}

		public String getImageUrl() {
			return imageUrl;
		}

		public String getLabel() {
			return label;
		}

		protected boolean hasMoreInfo() {
			return false;
		}

		@Nullable
		protected Component newMoreInfo(String componentId, FloatingPanel dropdown) {
			return null;
		}
		
	}
	
}
