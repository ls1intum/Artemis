package de.tum.cit.aet.artemis.account.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * An e-mail change an internal account has requested and that is waiting for both addresses to confirm it.
 * <p>
 * Applying the new address on request would let anyone holding a session change it, and then use the password reset that goes
 * to it to take the account over. The address therefore changes only after the owner of the <em>current</em> address and the
 * owner of the <em>new</em> address have each followed a link that was mailed to them. Each link carries its own key, so
 * following one proves control of exactly one mailbox.
 * <p>
 * Stored on the row of {@link UserRecoveryKey}: like an activation or reset key it is short-lived, set on few accounts, and has
 * to disappear with every event that ends an intrusion, which is what that row already does. The secrets are kept hashed, like
 * a reset key's.
 */
@Embeddable
public class PendingEmailChange {

    /** The address the account will have once both addresses have confirmed. */
    @JsonIgnore
    @Column(name = "email_change_new_email", length = 100)
    private String newEmail;

    /** Identifies the key sent to the current address. */
    @JsonIgnore
    @Column(name = "email_change_current_key_id", length = 20, unique = true)
    private String currentKeyId;

    /** Hash of the secret sent to the current address. */
    @JsonIgnore
    @Column(name = "email_change_current_key_hash", length = 255)
    private String currentKeyHash;

    /** Identifies the key sent to the new address. */
    @JsonIgnore
    @Column(name = "email_change_new_key_id", length = 20, unique = true)
    private String newKeyId;

    /** Hash of the secret sent to the new address. */
    @JsonIgnore
    @Column(name = "email_change_new_key_hash", length = 255)
    private String newKeyHash;

    /** When the request was made, against which both its validity and the pause before a new request are measured. */
    @JsonIgnore
    @Column(name = "email_change_requested_at")
    private Instant requestedAt;

    /** When the current address was confirmed. Set from the start for an account that has no address yet. */
    @Nullable
    @JsonIgnore
    @Column(name = "email_change_current_confirmed_at")
    private Instant currentConfirmedAt;

    /** When the new address was confirmed. */
    @Nullable
    @JsonIgnore
    @Column(name = "email_change_new_confirmed_at")
    private Instant newConfirmedAt;

    public PendingEmailChange() {
        // needed by Hibernate
    }

    public PendingEmailChange(String newEmail, String currentKeyId, String currentKeyHash, String newKeyId, String newKeyHash, Instant requestedAt,
            @Nullable Instant currentConfirmedAt) {
        this.newEmail = newEmail;
        this.currentKeyId = currentKeyId;
        this.currentKeyHash = currentKeyHash;
        this.newKeyId = newKeyId;
        this.newKeyHash = newKeyHash;
        this.requestedAt = requestedAt;
        this.currentConfirmedAt = currentConfirmedAt;
    }

    public String getNewEmail() {
        return newEmail;
    }

    public String getCurrentKeyId() {
        return currentKeyId;
    }

    public String getCurrentKeyHash() {
        return currentKeyHash;
    }

    public String getNewKeyId() {
        return newKeyId;
    }

    public String getNewKeyHash() {
        return newKeyHash;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    @Nullable
    public Instant getCurrentConfirmedAt() {
        return currentConfirmedAt;
    }

    public void setCurrentConfirmedAt(@Nullable Instant currentConfirmedAt) {
        this.currentConfirmedAt = currentConfirmedAt;
    }

    @Nullable
    public Instant getNewConfirmedAt() {
        return newConfirmedAt;
    }

    public void setNewConfirmedAt(@Nullable Instant newConfirmedAt) {
        this.newConfirmedAt = newConfirmedAt;
    }

    /**
     * Whether the owners of both addresses have confirmed.
     *
     * @return true if the address may now be applied
     */
    public boolean isFullyConfirmed() {
        return currentConfirmedAt != null && newConfirmedAt != null;
    }
}
