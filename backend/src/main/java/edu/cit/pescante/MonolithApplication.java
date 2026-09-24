package edu.cit.pescante;

import edu.cit.pescante.inventory.InventoryItem;
import edu.cit.pescante.inventory.InventoryRepository;
import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * Root Spring Boot Application class in parent package edu.cit.pescante.
 * Automatically scans both submodules:
 * - edu.cit.pescante.shop
 * - edu.cit.pescante.inventory
 */
@SpringBootApplication
public class MonolithApplication {

    private static final Logger log = LoggerFactory.getLogger(MonolithApplication.class);

    public static void main(String[] args) {
        // Load environment variables from .env if present (checking current or parent directory)
        try {
            Dotenv dotenv;
            if (new java.io.File(".env").exists()) {
                dotenv = Dotenv.configure().load();
            } else if (new java.io.File("../.env").exists()) {
                dotenv = Dotenv.configure().directory("..").load();
            } else {
                dotenv = Dotenv.configure().ignoreIfMissing().load();
            }

            dotenv.entries().forEach(entry -> {
                if (System.getProperty(entry.getKey()) == null && System.getenv(entry.getKey()) == null) {
                    System.setProperty(entry.getKey(), entry.getValue());
                }
            });
        } catch (Exception e) {
            log.warn("Could not load .env file: {}", e.getMessage());
        }

        // Check if Supabase connection details were provided
        String dbUrl = System.getenv("SPRING_DATASOURCE_URL");
        if (dbUrl == null || dbUrl.isBlank()) {
            dbUrl = System.getProperty("SPRING_DATASOURCE_URL");
        }

        if (dbUrl == null || dbUrl.isBlank() || dbUrl.contains("localhost:5432")) {
            log.warn("==========================================================================");
            log.warn("No Supabase credentials detected in environment variables or .env.");
            log.warn("Using in-memory PostgreSQL-mode H2 database for local demonstration.");
            log.warn("To connect to Supabase: copy .env.example to .env and set your credentials.");
            log.warn("==========================================================================");
            System.setProperty("spring.datasource.url", "jdbc:h2:mem:monolithdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
            System.setProperty("spring.datasource.driver-class-name", "org.h2.Driver");
            System.setProperty("spring.datasource.username", "sa");
            System.setProperty("spring.datasource.password", "");
            System.setProperty("spring.jpa.database-platform", "org.hibernate.dialect.H2Dialect");
            // Auto-create all JPA entity tables in H2 (supplier_orders, inventory, orders, etc.)
            System.setProperty("spring.jpa.hibernate.ddl-auto", "create");
        }

        // Log supplier API key status (never print the actual key)
        String lsKey = System.getenv("LS_API_KEY");
        if (lsKey == null || lsKey.isBlank()) lsKey = System.getProperty("LS_API_KEY");
        if (lsKey == null || lsKey.isBlank()) {
            log.warn("[SUPPLIER] LS_API_KEY not set — supplier reorder will be skipped (set in .env)");
        } else {
            log.info("[SUPPLIER] LS_API_KEY configured ({}***)", lsKey.substring(0, Math.min(6, lsKey.length())));
        }

        SpringApplication.run(MonolithApplication.class, args);
    }

    /**
     * Seeds initial inventory products specified in the activity requirements:
     * - P100 Wireless Mouse (25)
     * - P200 Mechanical Keyboard (10)
     * - P300 USB-C Hub (0)
     */
    @Bean
    public CommandLineRunner seedInventoryDatabase(InventoryRepository inventoryRepository) {
        return args -> {
            List<InventoryItem> initialItems = List.of(
                    new InventoryItem("P100", "Wireless Mouse", 25),
                    new InventoryItem("P200", "Mechanical Keyboard", 10),
                    new InventoryItem("P300", "USB-C Hub", 0)
            );

            for (InventoryItem item : initialItems) {
                if (!inventoryRepository.existsById(item.getProductId())) {
                    inventoryRepository.save(item);
                    log.info("Seeded inventory item: {} - {} (Stock: {})",
                            item.getProductId(), item.getName(), item.getStock());
                }
            }
        };
    }
}
