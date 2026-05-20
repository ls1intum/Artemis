package de.tum.cit.aet.artemis.communication.domain;

/**
 * Enum representing the types of global notifications a user can receive.
 * Each constant corresponds to a specific security-related event or status change
 * for which the user may opt in or out of receiving email alerts.
 *
 * <ul>
 * <li>{@link #NEW_LOGIN} - Triggered when a new login to the account is detected.</li>
 * <li>{@link #NEW_PASSKEY_ADDED} - Triggered when a new passkey is added to the user account.</li>
 * <li>{@link #VCS_TOKEN_EXPIRED} - Triggered when the user's version control system (VCS) access token has expired.</li>
 * <li>{@link #SSH_KEY_EXPIRED} - Triggered when a previously registered SSH key has expired.</li>
 * <li>{@link #MAINTENANCE} - Triggered when a planned maintenance notification is created by an admin.</li>
 * </ul>
 *
 * These notification types are used in user settings to control which email alerts the system should send.
 */
public enum GlobalNotificationType {
    NEW_LOGIN, NEW_PASSKEY_ADDED, VCS_TOKEN_EXPIRED, SSH_KEY_EXPIRED, MAINTENANCE
}
