package edu.cit.pescante.supplier;

/**
 * Package-private exception wrapping all LegacySupply communication errors.
 * Never escapes the supplier module boundary.
 */
class LegacySupplyException extends Exception {

    LegacySupplyException(String message) {
        super(message);
    }

    LegacySupplyException(String message, Throwable cause) {
        super(message, cause);
    }
}
