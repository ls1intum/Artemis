package de.tum.cit.aet.artemis.programming.domain;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * The personal VCS access token of a user, with which they authenticate against the embedded git server instead of
 * with their password.
 * <p>
 * Stored here rather than on the user row: fewer than one percent of accounts have one, and the participation- and
 * repository-scoped tokens already live in tables of their own. The absence of a row means the account has no personal
 * token, which is what a null column meant before.
 * <p>
 * The identifier is the user id itself, so the one-to-one is expressed by the schema and needs no separate unique
 * index. Deliberately holds the raw id rather than a {@code User} association: nothing on {@code User} points back
 * here, so loading a user can never pull this table in, and there is no lazy association to initialise outside a
 * session.
 */
@Entity
@Table(name = "user_vcs_access_token")
public class UserVCSAccessToken {

    @Id
    @Column(name = "user_id")
    private long userId;

    @Nullable
    @JsonIgnore
    @Column(name = "token", length = 50)
    private String token = null;

    @Nullable
    @JsonIgnore
    @Column(name = "expiry_date")
    private ZonedDateTime expiryDate = null;

    public UserVCSAccessToken() {
        // needed by Hibernate
    }

    public UserVCSAccessToken(long userId, @Nullable String token, @Nullable ZonedDateTime expiryDate) {
        this.userId = userId;
        this.token = token;
        this.expiryDate = expiryDate;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    @Nullable
    public String getToken() {
        return token;
    }

    public void setToken(@Nullable String token) {
        this.token = token;
    }

    @Nullable
    public ZonedDateTime getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(@Nullable ZonedDateTime expiryDate) {
        this.expiryDate = expiryDate;
    }

    /**
     * Whether this token may still be used to authenticate, which requires both a token and an expiry date that has not
     * passed. A token without an expiry date is treated as unusable rather than as never expiring.
     *
     * @return true if the token is present and not expired
     */
    public boolean isUsable() {
        return token != null && expiryDate != null && expiryDate.isAfter(ZonedDateTime.now());
    }
}
