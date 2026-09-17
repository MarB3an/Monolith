package edu.cit.pescante;

import edu.cit.pescante.inventory.InventoryService;
import edu.cit.pescante.shop.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchitectureBoundaryTest {

    @Test
    @DisplayName("Verify InventoryServiceImpl is package-private (NOT public)")
    void verifyInventoryServiceImplIsPackagePrivate() throws ClassNotFoundException {
        Class<?> implClass = Class.forName("edu.cit.pescante.inventory.InventoryServiceImpl");
        int modifiers = implClass.getModifiers();

        assertFalse(Modifier.isPublic(modifiers),
                "InventoryServiceImpl MUST be package-private to enforce architectural boundary!");
        assertFalse(Modifier.isPrivate(modifiers),
                "InventoryServiceImpl should not be private");
        assertFalse(Modifier.isProtected(modifiers),
                "InventoryServiceImpl should not be protected");
        assertTrue(InventoryService.class.isAssignableFrom(implClass),
                "InventoryServiceImpl must implement InventoryService interface");
    }

    @Test
    @DisplayName("Verify InventoryService interface is public")
    void verifyInventoryServiceInterfaceIsPublic() {
        int modifiers = InventoryService.class.getModifiers();
        assertTrue(Modifier.isPublic(modifiers),
                "InventoryService interface must be public for client modules to depend upon");
        assertTrue(InventoryService.class.isInterface(),
                "InventoryService must be an interface");
    }

    @Test
    @DisplayName("Verify OrderService depends ONLY on InventoryService interface via constructor injection")
    void verifyOrderServiceConstructorInjection() {
        Constructor<?>[] constructors = OrderService.class.getConstructors();
        assertTrue(constructors.length > 0, "OrderService must have a public constructor");

        boolean hasInventoryServiceParam = Arrays.stream(constructors)
                .anyMatch(c -> Arrays.asList(c.getParameterTypes()).contains(InventoryService.class));

        assertTrue(hasInventoryServiceParam,
                "OrderService must take InventoryService interface in its constructor for dependency injection");
    }

    @Test
    @DisplayName("Verify Notification module does NOT depend on OrderService or InventoryService")
    void verifyNotificationModuleDoesNotDependOnServices() throws ClassNotFoundException {
        Class<?> listenerClass = Class.forName("edu.cit.pescante.notification.NotificationEventListener");
        Constructor<?>[] constructors = listenerClass.getConstructors();

        for (Constructor<?> constructor : constructors) {
            for (Class<?> param : constructor.getParameterTypes()) {
                assertFalse(param.getName().contains("OrderService"),
                        "Notification module must never depend on OrderService!");
                assertFalse(param.getName().contains("InventoryService"),
                        "Notification module must never depend on InventoryService!");
            }
        }
    }

    @Test
    @DisplayName("Verify Order module does NOT depend on Notification module")
    void verifyOrderModuleDoesNotDependOnNotification() {
        Constructor<?>[] constructors = OrderService.class.getConstructors();

        for (Constructor<?> constructor : constructors) {
            for (Class<?> param : constructor.getParameterTypes()) {
                assertFalse(param.getName().startsWith("edu.cit.pescante.notification"),
                        "OrderService must never depend on the Notification module!");
            }
        }
    }
}
