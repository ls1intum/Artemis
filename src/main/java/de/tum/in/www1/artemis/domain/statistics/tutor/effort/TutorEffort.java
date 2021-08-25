package de.tum.in.www1.artemis.domain.statistics.tutor.effort;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.User;

/**
 * A Rating.
 */
@Entity
@Table(name = "tutor_effort_statistics")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TutorEffort extends DomainObject {

    @ManyToOne(cascade = CascadeType.REMOVE)
    @JoinColumn(name = "tutor_id")
    private User tutor;

    @Column(name = "submission")
    private Submission submission;

    @Column(name = "total_time_spent_grading")
    private Long totalTimeSpentGrading;

}
