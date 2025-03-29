package de.tum.cit.aet.artemis.core.domain;

/**
 * Transport types of {@link PasskeyCredentials}
 * <br>
 * <b>Important: The enum types must match the database table definition passkey_credentials.transports <i>(case-sensitive)</i></b>
 */
public enum Transports {
    USB, NFC, BLE, INTERNAL, HYBRID
}
