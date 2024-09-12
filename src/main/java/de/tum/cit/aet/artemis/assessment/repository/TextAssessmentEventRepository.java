package de.tum.cit.aet.artemis.assessment.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static java.util.stream.Collectors.toMap;

import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.text.domain.TextAssessmentEvent;

/**
 * Spring Data repository for the TextAssessmentEvent entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface TextAssessmentEventRepository extends ArtemisJpaRepository<TextAssessmentEvent, Long> {

    List<TextAssessmentEvent> findAllByCourseId(long courseId);

    @Query("""
            SELECT COUNT(DISTINCT textAssessmentEvent.userId)
            FROM TextAssessmentEvent textAssessmentEvent
            WHERE textAssessmentEvent.courseId = :courseId
                AND textAssessmentEvent.textExerciseId = :exerciseId
            """)
    Integer getNumberOfTutorsInvolvedInAssessingByExerciseAndCourseId(@Param("courseId") long courseId, @Param("exerciseId") long exerciseId);

    /**
     * Query and find all events which do not have the respective fields empty. These fields are specifically needed non-empty
     * for the tutor effort estimation process
     *
     * @param courseId       the id of the course to check for
     * @param textExerciseId the id of the text exercise to check for
     * @return a list of text assessment events
     */
    @Query("""
            SELECT textAssessmentEvent
            FROM TextAssessmentEvent textAssessmentEvent
            WHERE textAssessmentEvent.userId IS NOT NULL
                AND textAssessmentEvent.submissionId IS NOT NULL
                AND textAssessmentEvent.participationId IS NOT NULL
                AND textAssessmentEvent.timestamp IS NOT NULL
                AND textAssessmentEvent.courseId = :courseId
                AND textAssessmentEvent.textExerciseId = :textExerciseId
            """)
    List<TextAssessmentEvent> findAllNonEmptyEvents(@Param("courseId") long courseId, @Param("textExerciseId") long textExerciseId);

    /**
     * Finds the number of submissions assessed for each tutor listed in the assessment event list
     *
     * @param courseId       the id of the course to check for
     * @param textExerciseId the id of the text exercise to check for
     * @return a TutorAssessedSubmissionsCount interface representing user id and number of submissions involved
     */
    @Query("""
            SELECT textAssessmentEvent.userId AS tutorId,
                COUNT(DISTINCT textAssessmentEvent.submissionId) AS submissionsInvolved
            FROM TextAssessmentEvent textAssessmentEvent
            WHERE textAssessmentEvent.userId IS NOT NULL
                AND textAssessmentEvent.submissionId IS NOT NULL
                AND textAssessmentEvent.participationId IS NOT NULL
                AND textAssessmentEvent.courseId = :courseId
                AND textAssessmentEvent.textExerciseId = :textExerciseId
            GROUP BY textAssessmentEvent.userId
            """)
    List<TutorAssessedSubmissionsCount> findNumberOfSubmissionsAssessedForTutor(@Param("courseId") long courseId, @Param("textExerciseId") long textExerciseId);

    /**
     * An interface representing an intermediate form fitting the JPA query syntax.
     * It is used to make converting to a Map easier through the `getAssessedSubmissionCountPerTutor` function
     */
    interface TutorAssessedSubmissionsCount {

        long getTutorId();

        int getSubmissionsInvolved();
    }

    /**
     * Calculates number of submissions each tutor is involved with
     *
     * @param courseId       course to check
     * @param textExerciseId text exercise id to check
     * @return Map containing user id and respective number of submissions affected
     */
    default Map<Long, Integer> getAssessedSubmissionCountPerTutor(long courseId, long textExerciseId) {
        return findNumberOfSubmissionsAssessedForTutor(courseId, textExerciseId).stream()
                .collect(toMap(TutorAssessedSubmissionsCount::getTutorId, TutorAssessedSubmissionsCount::getSubmissionsInvolved));
    }
}
