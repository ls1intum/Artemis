package de.tum.cit.aet.artemis.programming.domain;

import java.time.ZonedDateTime;

import jakarta.annotation.Nullable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

/**
 * A public SSH key of a user.
 */
@Entity
@Table(name = "user_public_ssh_key")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class UserSshPublicKey extends DomainObject {

    /**
     * The user who is owner of the public key
     */
    @NotNull
    @Column(name = "user_id")
    private Long userId;

    /**
     * The label of the SSH key shwon in the UI
     */
    @Size(max = 50)
    @Column(name = "label", length = 50)
    private String label;

    /**
     * The actual full public ssh key of a user used to authenticate git clone and git push operations if available
     */
    @NotNull
    @Column(name = "public_key")
    private String publicKey;

    /**
     * A hash of the public ssh key for fast comparison in the database (with an index)
     */
    @Nullable
    @Size(max = 100)
    @Column(name = "key_hash")
    private String keyHash;

    /**
     * The creation date of the public SSH key
     */
    @Column(name = "creation_date")
    private ZonedDateTime creationDate = null;

    /**
     * The last used date of the public SSH key
     */
    @Nullable
    @Column(name = "last_used_date")
    private ZonedDateTime lastUsedDate = null;

    /**
     * The expiry date of the public SSH key
     */
    @Nullable
    @Column(name = "expiry_date")
    private ZonedDateTime expiryDate = null;

    public @NotNull Long getUserId() {
        return userId;
    }

    public void setUserId(@NotNull Long userId) {
        this.userId = userId;
    }

    public @Size(max = 50) String getLabel() {
        return label;
    }

    public void setLabel(@Size(max = 50) String label) {
        this.label = label;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    @Nullable
    public @Size(max = 100) String getKeyHash() {
        return keyHash;
    }

    public void setKeyHash(@Nullable @Size(max = 100) String keyHash) {
        this.keyHash = keyHash;
    }

    public ZonedDateTime getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(ZonedDateTime creationDate) {
        this.creationDate = creationDate;
    }

    @Nullable
    public ZonedDateTime getLastUsedDate() {
        return lastUsedDate;
    }

    public void setLastUsedDate(@Nullable ZonedDateTime lastUsedDate) {
        this.lastUsedDate = lastUsedDate;
    }

    @Nullable
    public ZonedDateTime getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(@Nullable ZonedDateTime expiryDate) {
        this.expiryDate = expiryDate;
    }
}
