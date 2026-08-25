package de.tum.cit.aet.artemis.account.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * The key an account currently has outstanding to activate itself or to complete a password reset.
 * <p>
 * Both are short-lived - issued, redeemed, cleared - and set on a small minority of accounts, so they live here rather
 * than on the user row. They share a row because an account is never meaningfully mid-activation and mid-reset at once.
 * The absence of a row means no key is outstanding.
 */
@Entity
@Table(name = "user_recovery_key")
public class UserRecoveryKey {

    @Id
    @Column(name = "user_id")
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
     * One-time key a user redeems to set a new password, issued by a password reset request and cleared once used.
     */
    @Nullable
    @JsonIgnore
    @Column(name = "reset_key", length = 20)
    private String resetKey = null;

    /**
     * When the outstanding reset key was issued, against which its expiry is checked.
     */
    @Nullable
    @Column(name = "reset_date")
    private Instant resetDate = null;

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
    public String getResetKey() {
        return resetKey;
    }

    public void setResetKey(@Nullable String resetKey) {
        this.resetKey = resetKey;
    }

    @Nullable
    public Instant getResetDate() {
        return resetDate;
    }

    public void setResetDate(@Nullable Instant resetDate) {
        this.resetDate = resetDate;
    }

    /**
     * Whether nothing is outstanding, in which case the row can be removed rather than left as a row of nulls.
     *
     * @return true if neither key nor reset date is set
     */
    public boolean isEmpty() {
        return activationKey == null && resetKey == null && resetDate == null;
    }
}
