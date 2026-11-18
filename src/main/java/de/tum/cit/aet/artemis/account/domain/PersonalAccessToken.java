package de.tum.cit.aet.artemis.account.domain;

import java.io.Serial;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.domain.Parent;

/**
 * A personal access token with which a user lets a program, typically a coding agent, call the REST API on their behalf.
 * <p>
 * Only a SHA-256 hash of the secret is stored, and the secret itself is shown once, when the token is created. The token
 * holds the user's id rather than a {@code User} association, as {@link de.tum.cit.aet.artemis.programming.domain.UserVCSAccessToken}
 * does: nothing on {@code User} points back here, so loading a user never pulls this table in.
 */
@Entity
@Table(name = "personal_access_token")
public class PersonalAccessToken extends DomainObject {

    @Serial
    private static final long serialVersionUID = 1L;

    @Parent
    @Column(name = "user_id", nullable = false)
    private long userId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** The first characters of the secret, so that a user can tell their tokens apart in a list. */
    @Column(name = "token_prefix", nullable = false, length = 16)
    private String tokenPrefix;

    @JsonIgnore
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    /** The scope names, comma separated. */
    @Column(name = "scopes", nullable = false, length = 512)
    private String scopes;

    @Column(name = "created_date", nullable = false)
    private Instant createdDate;

    @Column(name = "expiry_date", nullable = false)
    private Instant expiryDate;

    @Nullable
    @Column(name = "last_used_date")
    private Instant lastUsedDate;

    @Nullable
    @Column(name = "revoked_date")
    private Instant revokedDate;

    public PersonalAccessToken() {
        // needed by Hibernate
    }

    public PersonalAccessToken(long userId, String name, String tokenPrefix, String tokenHash, String scopes, Instant createdDate, Instant expiryDate) {
        this.userId = userId;
        this.name = name;
        this.tokenPrefix = tokenPrefix;
        this.tokenHash = tokenHash;
        this.scopes = scopes;
        this.createdDate = createdDate;
        this.expiryDate = expiryDate;
    }

    public long getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getTokenPrefix() {
        return tokenPrefix;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public String getScopes() {
        return scopes;
    }

    public Instant getCreatedDate() {
        return createdDate;
    }

    public Instant getExpiryDate() {
        return expiryDate;
    }

    @Nullable
    public Instant getLastUsedDate() {
        return lastUsedDate;
    }

    @Nullable
    public Instant getRevokedDate() {
        return revokedDate;
    }

    /**
     * Whether this token may authenticate a request: it has not been revoked and has not expired.
     *
     * @param now the time to check against
     * @return true if the token is usable at that time
     */
    public boolean isUsableAt(Instant now) {
        return revokedDate == null && expiryDate.isAfter(now);
    }
}
