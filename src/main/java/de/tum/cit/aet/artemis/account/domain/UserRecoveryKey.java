package de.tum.cit.aet.artemis.account.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;

import de.tum.cit.aet.artemis.core.domain.Parent;

/**
 * The key an account currently has outstanding to activate itself or to complete a password reset, and the e-mail change it is
 * waiting to have confirmed.
 * <p>
 * These are short-lived - issued, redeemed, cleared - and set on a small minority of accounts, so they live here rather
 * than on the user row. They share a row because an account is never meaningfully mid-activation and mid-reset at once, and
 * because everything that ends an intrusion, such as a deactivation or a deletion, has to clear all of them together. The
 * absence of a row means nothing is outstanding.
 */
@Entity
@Table(name = "user_recovery_key")
public class UserRecoveryKey {

    @Id
    @Column(name = "user_id")
    @Parent
    private long userId;

    /**
     * One-time key a user redeems through {@code GET /activate} to activate their own account. Only ever set while
     * {@link User#activated} is false on an internal account and self-registration is enabled - see that field for why an
     * externally managed account must never be given one, and for how the key's presence tells an account awaiting
     * activation apart from one an admin deactivated.
     */
    @Nullable
    @JsonIgnore
    @Column(name = "activation_key", length = 20)
    private String activationKey = null;

    /**
     * Identifier used to link a password reset procedure to a user account.
     */
    @Column(name = "reset_key_id", length = 20, unique = true)
    @JsonIgnore
    private String resetKeyId = null;

    /**
     * Hash of the one-time key a user redeems to set a new password, issued by a password reset request and cleared once used.
     */
    @Nullable
    @JsonIgnore
    @Column(name = "reset_key_hash", length = 255)
    private String resetKeyHash = null;

    /**
     * When the outstanding reset key was issued, against which its expiry is checked.
     */
    @Nullable
    @Column(name = "reset_date")
    private Instant resetDate = null;

    /**
     * The e-mail change this account has requested and that still needs both addresses to confirm it, or null when none is
     * pending. All its columns are null in that case, which is how Hibernate recognises its absence.
     */
    @Nullable
    @JsonIgnore
    @Embedded
    private PendingEmailChange pendingEmailChange = null;

    public UserRecoveryKey() {
        // needed by Hibernate
    }

    public UserRecoveryKey(long userId) {
        this.userId = userId;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    @Nullable
    public String getActivationKey() {
        return activationKey;
    }

    public void setActivationKey(@Nullable String activationKey) {
        this.activationKey = activationKey;
    }

    @Nullable
    public String getResetKeyId() {
        return resetKeyId;
    }

    public void setResetKeyId(@Nullable String resetKeyId) {
        this.resetKeyId = resetKeyId;
    }

    @Nullable
    public String getResetKeyHash() {
        return resetKeyHash;
    }

    public void setResetKeyHash(@Nullable String resetKeyHash) {
        this.resetKeyHash = resetKeyHash;
    }

    @Nullable
    public Instant getResetDate() {
        return resetDate;
    }

    public void setResetDate(@Nullable Instant resetDate) {
        this.resetDate = resetDate;
    }

    @Nullable
    public PendingEmailChange getPendingEmailChange() {
        return pendingEmailChange;
    }

    public void setPendingEmailChange(@Nullable PendingEmailChange pendingEmailChange) {
        this.pendingEmailChange = pendingEmailChange;
    }

    /**
     * Whether nothing is outstanding, in which case the row can be removed rather than left as a row of nulls.
     *
     * @return true if neither key, reset date nor pending e-mail change is set
     */
    public boolean isEmpty() {
        return activationKey == null && resetKeyId == null && resetKeyHash == null && resetDate == null && pendingEmailChange == null;
    }
}
