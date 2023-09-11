package de.tum.in.www1.artemis.domain;

import java.time.Instant;

import javax.persistence.*;

import org.springframework.data.annotation.CreatedDate;

import com.fasterxml.jackson.annotation.JsonInclude;

@Entity
@Table(name = "assessment_note")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class AssessmentNote extends DomainObject {

    @OneToOne
    @JoinColumn(name = "creator_id", referencedColumnName = "id")
    private User creator;

    @CreatedDate
    @Column(name = "created_date", updatable = false)
    private Instant createdDate = Instant.now();

    @Column(name = "last_updated_date")
    private Instant lastUpdatedDate = Instant.now();

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
