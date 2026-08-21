package de.tum.cit.aet.artemis.account.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.jspecify.annotations.Nullable;

/**
 * When an account was last used, and whether it has been warned that it is about to be deleted for inactivity.
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
}
