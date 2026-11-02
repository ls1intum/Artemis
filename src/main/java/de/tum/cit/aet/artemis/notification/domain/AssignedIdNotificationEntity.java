package de.tum.cit.aet.artemis.notification.domain;

import java.io.Serializable;

import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;

import org.springframework.data.domain.Persistable;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Tracks whether an assigned-key notification row needs an insert rather than a merge. This avoids loading each
 * recipient status or parameter before its first insert, while loaded rows still follow the update path.
 *
 * @param <ID> the concrete notification row's composite identifier
 */
@MappedSuperclass
public abstract class AssignedIdNotificationEntity<ID> implements Serializable, Persistable<ID> {

    @Transient
    private boolean isNew = true;

    @Override
    @JsonIgnore
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        isNew = false;
    }
}
