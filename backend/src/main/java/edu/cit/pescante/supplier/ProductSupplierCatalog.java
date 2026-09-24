package edu.cit.pescante.supplier;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Package-private catalog mapping our product IDs to LegacySupply's catalog entries.
 *
 * This is the ONLY place in the application that knows about supplier SKU codes
 * and pack sizes. Everything outside this module uses productId only.
 *
 * Catalog entries confirmed against GET /api/v1/catalog:
 *   ZHF-8111 = WIRELESS MOUSE 2.4GHZ, PackSize 6
 *   ZHF-1964 = KEYBOARD MECH TKL,     PackSize 6
 *   ZHF-1746 = USB HUB 4-PORT,        PackSize 24
 */
class ProductSupplierCatalog {

    static final class CatalogEntry {
        final String supplierSku;
        final int packSize;
        final String description;

        CatalogEntry(String supplierSku, int packSize, String description) {
            this.supplierSku = supplierSku;
            this.packSize = packSize;
            this.description = description;
        }

        /** Translate unit count to whole cases (rounded up). */
        int unitsToCases(int units) {
            return (units + packSize - 1) / packSize;
        }

        /** Total units that will be delivered for the given number of cases. */
        int casesToUnits(int cases) {
            return cases * packSize;
        }
    }

    private static final Map<String, CatalogEntry> ENTRIES = new HashMap<>();

    static {
        ENTRIES.put("P100", new CatalogEntry("ZHF-8111", 6,  "WIRELESS MOUSE 2.4GHZ"));
        ENTRIES.put("P200", new CatalogEntry("ZHF-1964", 6,  "KEYBOARD MECH TKL"));
        ENTRIES.put("P300", new CatalogEntry("ZHF-1746", 24, "USB HUB 4-PORT"));
    }

    static Optional<CatalogEntry> forProduct(String productId) {
        return Optional.ofNullable(ENTRIES.get(productId));
    }
}
