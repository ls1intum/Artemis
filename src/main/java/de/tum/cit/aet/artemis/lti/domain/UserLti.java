package de.tum.cit.aet.artemis.lti.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * What the lti module knows about an account: that a launch provisioned it, and whether that account has already
 * completed the one-time initialisation a launch-provisioned account goes through.
 * <p>
 * State the lti module owns, so it lives here rather than as columns on the account module's central table, where only
 * three of 34,354 accounts set them. A row exists only for an account the launch created, so the absence of a row means
 * "not launch-created".
 */
@Entity
@Table(name = "user_lti")
public class UserLti {

    @Id
    @Column(name = "user_id")
    private long userId;

    @Column(name = "created_by_launch", nullable = false)
    private boolean createdByLaunch = false;

    /**
     * Whether the account has completed the initialisation that follows its first launch, in which it is given the
     * password it authenticates with afterwards.
     * <p>
     * Kept separate from {@code jhi_user.activated}, which an administrator also uses to disable an account: a single flag
     * cannot distinguish an account that has never been initialised from one that has been deactivated, and an
     * initialisation endpoint deciding on it would activate the latter again. This marker only ever goes from false to
     * true, so initialisation happens once and a later deactivation cannot be undone through it.
     */
    @Column(name = "initialized", nullable = false)
    private boolean initialized = false;

    public UserLti() {
        // needed by Hibernate
    }

    public UserLti(long userId, boolean createdByLaunch) {
        this.userId = userId;
        this.createdByLaunch = createdByLaunch;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public boolean isCreatedByLaunch() {
        return createdByLaunch;
    }

    public void setCreatedByLaunch(boolean createdByLaunch) {
        this.createdByLaunch = createdByLaunch;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }
}
