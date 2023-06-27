package de.tum.in.www1.artemis.domain;

import java.time.Instant;

import javax.persistence.*;

import org.springframework.data.annotation.CreatedDate;

import com.fasterxml.jackson.annotation.JsonInclude;

@Entity
@Table(name = "review_note")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ReviewNote extends DomainObject {

    @OneToOne
    @JoinColumn(name = "creator_id", referencedColumnName = "id")
    private User creatorId;

    @CreatedDate
    @Column(name = "created_date", updatable = false)
    private Instant createdDate = Instant.now();

    @Column(name = "note")
    private String note;

    @OneToOne(mappedBy = "review_note", fetch = FetchType.LAZY)
    private Result result;
}
