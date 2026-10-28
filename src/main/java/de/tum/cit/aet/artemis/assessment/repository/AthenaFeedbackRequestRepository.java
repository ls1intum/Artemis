package de.tum.cit.aet.artemis.assessment.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import jakarta.persistence.LockModeType;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;

/** Persistence operations dedicated to the lifecycle of student-requested Athena feedback. */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface AthenaFeedbackRequestRepository extends ArtemisJpaRepository<Result, Long>, AthenaFeedbackRequestWriteRepository {

    /**
     * Serializes feedback request lifecycle writes for a participation.
     *
     * @param participationId the participation whose request state is changing
     * @return the locked participation
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM StudentParticipation p WHERE p.id = :participationId")
    StudentParticipation lockParticipationForAthenaFeedback(@Param("participationId") long participationId);

    /**
     * Loads Athena request records for duplicate, lease and quota checks while the participation is locked.
     *
     * @param participationId the participation whose requests are considered
     * @return its Athena results with their submissions
     */
    @Query("""
            SELECT r FROM Result r JOIN FETCH r.submission s
            WHERE s.participation.id = :participationId
                AND r.assessmentType = de.tum.cit.aet.artemis.assessment.domain.AssessmentType.AUTOMATIC_ATHENA
            """)
    List<Result> findAthenaFeedbackRequests(@Param("participationId") long participationId);
}
