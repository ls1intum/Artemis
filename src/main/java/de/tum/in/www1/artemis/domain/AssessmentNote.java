package de.tum.in.www1.artemis.domain;

import java.time.Instant;

import javax.persistence.*;

import org.springframework.data.annotation.CreatedDate;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

@Entity
@Table(name = "assessment_note")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class AssessmentNote extends DomainObject {

    @OneToOne
    @JoinColumn(name = "creator_id", referencedColumnName = "id")
    @JsonIgnore
    private User creator;

    @CreatedDate
    @JsonIgnore
    @Column(name = "created_date")
    private Instant createdDate = Instant.now();

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
}
