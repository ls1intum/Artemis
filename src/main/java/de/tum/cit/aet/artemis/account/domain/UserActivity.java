package de.tum.cit.aet.artemis.account.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.jspecify.annotations.Nullable;

/**
 * The account's lifecycle timestamps: when it was last used, whether it has been warned that it is about to be deleted
 * for inactivity, and when its credentials last changed.
 * <p>
 * Unlike the other extracted clusters this one is not sparse - a last login is recorded for every account. It earns its
 * own table by keeping a write that happens on every single authentication off the wide user row.
 * <p>
 * An account without a row behaves exactly as null columns did: it has never logged in and has never been warned.
 */
@Entity
@Table(name = "user_activity")
public class UserActivity {

    @Id
    @Column(name = "user_id")
    private long userId;

    @Nullable
    @Column(name = "last_login_date")
    private Instant lastLoginDate;

    @Nullable
    @Column(name = "deletion_warning_sent_date")
    private Instant deletionWarningSentDate;

    /**
     * When the account's credentials last changed - a password change, a completed reset, an administrative password
     * change, or a deactivation. A session established before this moment is not extended any further.
     * <p>
     * Read in exactly one place, when deciding whether to extend a passkey session. As a column on the user row it was
     * read as part of every user load instead.
     */
    @Nullable
    @Column(name = "credentials_changed_date")
    private Instant credentialsChangedDate;

    public UserActivity() {
        // needed by Hibernate
    }

    public UserActivity(long userId) {
        this.userId = userId;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    @Nullable
    public Instant getLastLoginDate() {
        return lastLoginDate;
    }

    public void setLastLoginDate(@Nullable Instant lastLoginDate) {
        this.lastLoginDate = lastLoginDate;
    }

    @Nullable
    public Instant getDeletionWarningSentDate() {
        return deletionWarningSentDate;
    }

    public void setDeletionWarningSentDate(@Nullable Instant deletionWarningSentDate) {
        this.deletionWarningSentDate = deletionWarningSentDate;
    }

    @Nullable
    public Instant getCredentialsChangedDate() {
        return credentialsChangedDate;
    }

    public void setCredentialsChangedDate(@Nullable Instant credentialsChangedDate) {
        this.credentialsChangedDate = credentialsChangedDate;
    }
}
