package de.tum.in.www1.artemis.repository;

import static java.util.stream.Collectors.toMap;

import java.util.List;
import java.util.Map;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.TextBlockType;
import de.tum.in.www1.artemis.domain.analytics.TextAssessmentEvent;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;

/**
 * Spring Data repository for the TextAssessmentEvent entity.
 */
@SuppressWarnings("unused")
@Repository
public interface TextAssessmentEventRepository extends JpaRepository<TextAssessmentEvent, Long> {

    List<TextAssessmentEvent> findAllByFeedbackType(FeedbackType type);

    List<TextAssessmentEvent> findAllByEventType(TextAssessmentEvent type);

    List<TextAssessmentEvent> findAllBySegmentType(TextBlockType type);

    List<TextAssessmentEvent> findAllByCourseId(Long courseId);

    @Query("""
                SELECT textAssessmentEvent.userId AS tutorId, COUNT(DISTINCT textAssessmentEvent.submissionId) AS submissionsInvolved
                FROM TextAssessmentEvent textAssessmentEvent
                WHERE textAssessmentEvent.userId > 0 AND
                 textAssessmentEvent.submissionId > 0 AND
                 textAssessmentEvent.participationId > 0 AND
                 textAssessmentEvent.courseId = :#{#courseId} AND
                 textAssessmentEvent.textExerciseId = :#{#textExerciseId}
                GROUP BY textAssessmentEvent.userId
            """)
    List<TutorAssessedSubmissionsCount> findNumberOfSubmissionsAssessedForTutor(Long courseId, Long textExerciseId);

    interface TutorAssessedSubmissionsCount {

        Long getTutorId();

        Integer getSubmissionsInvolved();
    }

    /**
     * Calculates number of submissions each tutor is involved with
     * @param courseId course to check
     * @param textExerciseId text exercise id to check
     * @return Map containing user id and respective number of submissions affected
     */
    default Map<Long, Integer> getAssessedSubmissionCountPerTutor(Long courseId, Long textExerciseId) {
        return findNumberOfSubmissionsAssessedForTutor(courseId, textExerciseId).stream()
                .collect(toMap(TutorAssessedSubmissionsCount::getTutorId, TutorAssessedSubmissionsCount::getSubmissionsInvolved));
    }
}
