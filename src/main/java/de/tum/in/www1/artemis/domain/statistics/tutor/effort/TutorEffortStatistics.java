package de.tum.in.www1.artemis.domain.statistics.tutor.effort;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.User;

/**
 * A Rating.
 */
@Entity
@Table(name = "tutor_effort")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TutorEffortStatistics extends DomainObject {

    @ManyToOne(cascade = CascadeType.REMOVE)
    @JoinColumn(name = "tutor_id")
    private User tutor;

    @ManyToOne(cascade = CascadeType.REMOVE)
    @JoinColumn(name = "tutor_effort")
    private TutorEffort tutorEffort;

    @Column(name = "number_of_submissions_assessed")
    private Long numberOfSubmissionsAssessed;

    @Column(name = "totalTime")
    private Long totalTimeSpentMinutes;

    @Transient
    @Column(name = "average_time_per_submission")
    private Long averageTimePerSubmission;

}
