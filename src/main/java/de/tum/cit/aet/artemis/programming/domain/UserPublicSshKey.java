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
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.programming.service.vcs.VcsTokenRenewalService;

/**
 * A public SSH key of a user.
 */
@Entity
@Table(name = "user_public_ssh_key")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class UserPublicSshKey extends DomainObject {

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
     * The expiry date of the VCS access token.
     * This is used for checking if a access token needs to be renewed.
     *
     * @see VcsTokenRenewalService
     * @see UserRepository#getUsersWithAccessTokenExpirationDateBefore
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

    @Nullable
    public ZonedDateTime getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(@Nullable ZonedDateTime expiryDate) {
        this.expiryDate = expiryDate;
    }
}
