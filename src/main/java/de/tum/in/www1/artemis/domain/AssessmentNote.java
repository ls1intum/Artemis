package de.tum.in.www1.artemis.domain;

import java.time.Instant;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.persistence.Table;

import org.springframework.data.annotation.CreatedDate;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents an internal assessment note.
 */
@Entity
@Table(name = "assessment_note")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class AssessmentNote extends DomainObject {

    @OneToOne
    @JoinColumn(name = "creator_id", referencedColumnName = "id")
    private User creator;

    @CreatedDate
    @Column(name = "created_date", updatable = false)
    private final Instant createdDate = Instant.now();

    @Column(name = "last_updated_date")
    private final Instant lastUpdatedDate = Instant.now();

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

    public Instant getLastUpdatedDate() {
        return this.lastUpdatedDate;
    }
}
