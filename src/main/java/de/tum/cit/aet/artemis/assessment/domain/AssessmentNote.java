package de.tum.cit.aet.artemis.assessment.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.domain.User;

/**
 * Represents an internal assessment note.
 */
@Entity
@Table(name = "assessment_note")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@EntityListeners(AuditingEntityListener.class)
public class AssessmentNote extends DomainObject {

    @OneToOne
    @JoinColumn(name = "creator_id", referencedColumnName = "id")
    private User creator;

    @CreatedDate
    @Column(name = "created_date", updatable = false)
    @JsonIgnore
    private Instant createdDate;

    @LastModifiedDate
    @Column(name = "last_modified_date")
    @JsonIgnore
    private Instant lastModifiedDate;

    @Column(name = "note")
    private String note;

    public void setCreator(final User user) {
        this.creator = user;
    }

    public void setNote(final String note) {
        this.note = note;
    }

    public String getNote() {
        return this.note;
    }

    public User getCreator() {
        return this.creator;
    }

    public Instant getCreatedDate() {
        return this.createdDate;
    }

    public void setLastModifiedDate(Instant lastModifiedDate) {
        this.lastModifiedDate = lastModifiedDate;
    }

    public Instant getLastModifiedDate() {
        return this.lastModifiedDate;
    }
}
