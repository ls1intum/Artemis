package de.tum.cit.aet.artemis.exercise.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

/** Scalar counts and bounded candidates for Home; no content or participation graphs are hydrated. */
@Repository
@Lazy
@Profile(PROFILE_CORE)
public interface ExerciseHomeRepository extends ArtemisJpaRepository<Exercise, Long> {

    String FROM = """
             FROM Exercise e LEFT JOIN e.exerciseVariantGroup g
             LEFT JOIN QuizExercise q ON q.id = e.id
            """;

    String VISIBLE = """
             WHERE e.course.id = :courseId AND e.exerciseGroup IS NULL
             AND (e.releaseDate IS NULL OR e.releaseDate <= :asOf)
             AND (COALESCE(e.course.onlineCourse, FALSE) = FALSE OR EXISTS (
                 SELECT l.id FROM LtiResourceLaunch l WHERE l.exercise = e AND l.user.id = :userId))
             AND (e.mode = de.tum.cit.aet.artemis.exercise.domain.ExerciseMode.INDIVIDUAL OR EXISTS (
                 SELECT t.id FROM Team t JOIN t.students member WHERE t.exercise = e AND member.id = :userId))
            """;

    String OWNED = """
             ((p.student.id = :userId AND e.mode = de.tum.cit.aet.artemis.exercise.domain.ExerciseMode.INDIVIDUAL)
               OR EXISTS (SELECT t.id FROM Team t JOIN t.students m WHERE t = p.team AND m.id = :userId))
            """;

    String DUE = """
            COALESCE((SELECT MAX(p.individualDueDate) FROM StudentParticipation p
                WHERE p.exercise = e AND p.testRun = FALSE AND
            """ + OWNED + "), e.dueDate)";

    String PUBLICATION = """
            CASE WHEN e.releaseDate IS NULL THEN e.createdDate
                 WHEN e.createdDate IS NULL OR e.releaseDate > e.createdDate THEN e.releaseDate ELSE e.createdDate END
            """;

    String SUBMITTED = """
            EXISTS (SELECT s.id FROM Submission s JOIN TREAT(s.participation AS StudentParticipation) p
                WHERE s.submitted = TRUE AND p.testRun = FALSE
                  AND (p.exercise = e OR (g.id IS NOT NULL AND p.exercise.exerciseVariantGroup.id = g.id))
                  AND
            """ + OWNED + ")";

    String ACTIVITY = """
            (SELECT MAX(COALESCE(s.submissionDate, p.initializationDate)) FROM StudentParticipation p LEFT JOIN p.submissions s
                WHERE p.exercise = e AND p.testRun = FALSE AND
            """ + OWNED + ")";

    String OPEN = " AND (e.startDate IS NULL OR e.startDate <= :asOf) AND (" + DUE + " IS NULL OR " + DUE + " > :asOf) " + """
             AND (q.id IS NULL OR q.quizMode = de.tum.cit.aet.artemis.quiz.domain.QuizMode.INDIVIDUAL OR EXISTS (
                 SELECT b.id FROM QuizBatch b WHERE b.quizExercise = q AND b.startTime <= :asOf
                 AND FUNCTION('timestampadd', SECOND, q.duration, b.startTime) > :asOf
                 AND (q.quizMode = de.tum.cit.aet.artemis.quiz.domain.QuizMode.SYNCHRONIZED OR EXISTS (
                     SELECT qs.id FROM QuizSubmission qs JOIN TREAT(qs.participation AS StudentParticipation) qp
                     WHERE qs.quizBatch = b.id AND qp.student.id = :userId))))
            """;

    String FEEDBACK = """
            (SELECT MAX(CASE WHEN TYPE(e) = ProgrammingExercise AND r.assessmentType = de.tum.cit.aet.artemis.assessment.domain.AssessmentType.AUTOMATIC THEN r.completionDate
                 WHEN e.assessmentDueDate > r.completionDate THEN e.assessmentDueDate ELSE r.completionDate END)
             FROM Result r JOIN r.submission s JOIN TREAT(s.participation AS StudentParticipation) p
             WHERE p.exercise = e AND p.testRun = FALSE AND r.rated = TRUE AND r.completionDate IS NOT NULL AND r.completionDate <= :asOf
               AND (TYPE(e) <> QuizExercise OR e.dueDate <= :asOf)
               AND ((TYPE(e) = ProgrammingExercise AND r.assessmentType = de.tum.cit.aet.artemis.assessment.domain.AssessmentType.AUTOMATIC)
                    OR e.assessmentDueDate IS NULL OR e.assessmentDueDate <= :asOf)
               AND
            """ + OWNED + ")";

    String SELECT = "SELECT e.id AS id, e.title AS title, " + DUE + " AS dueDate, " + PUBLICATION + " AS publicationDate, ";

    interface Counts {

        long getReleased();

        long getSubmitted();

        long getDue();

        long getDueSubmitted();
    }

    interface Item {

        long getId();

        String getTitle();

        @Nullable
        ZonedDateTime getDueDate();

        @Nullable
        ZonedDateTime getPublicationDate();

        @Nullable
        ZonedDateTime getActivityAt();
    }

    @Query("SELECT COUNT(DISTINCT COALESCE(g.id, -e.id)) AS released, COUNT(DISTINCT CASE WHEN " + SUBMITTED + " THEN COALESCE(g.id, -e.id) END) AS submitted, "
            + "COUNT(DISTINCT CASE WHEN " + DUE + " > :asOf AND " + DUE + " <= :dueSoon THEN COALESCE(g.id, -e.id) END) AS due, " + "COUNT(DISTINCT CASE WHEN " + DUE
            + " > :asOf AND " + DUE + " <= :dueSoon AND " + SUBMITTED + " THEN COALESCE(g.id, -e.id) END) AS dueSubmitted " + FROM + VISIBLE)
    Counts countForHome(@Param("courseId") long courseId, @Param("userId") long userId, @Param("asOf") ZonedDateTime asOf, @Param("dueSoon") ZonedDateTime dueSoon);

    @Query(SELECT + PUBLICATION + " AS activityAt " + FROM + VISIBLE + OPEN + " AND NOT " + SUBMITTED + " AND " + DUE + " <= :dueSoon ORDER BY " + DUE + ", e.id")
    List<Item> findDeadline(@Param("courseId") long courseId, @Param("userId") long userId, @Param("asOf") ZonedDateTime asOf, @Param("dueSoon") ZonedDateTime dueSoon,
            Pageable pageable);

    @Query(SELECT + ACTIVITY + " AS activityAt " + FROM + VISIBLE + OPEN + " AND NOT " + SUBMITTED + " AND " + ACTIVITY + " IS NOT NULL ORDER BY " + ACTIVITY + " DESC, e.id")
    List<Item> findInProgress(@Param("courseId") long courseId, @Param("userId") long userId, @Param("asOf") ZonedDateTime asOf, Pageable pageable);

    @Query(SELECT + FEEDBACK + " AS activityAt " + FROM + VISIBLE + " AND " + FEEDBACK + " > :since ORDER BY " + FEEDBACK + " DESC, e.id")
    List<Item> findFeedback(@Param("courseId") long courseId, @Param("userId") long userId, @Param("asOf") ZonedDateTime asOf, @Param("since") ZonedDateTime since,
            Pageable pageable);

    @Query(SELECT + PUBLICATION + " AS activityAt " + FROM + VISIBLE + " AND " + PUBLICATION + " > :since AND " + PUBLICATION + " <= :asOf ORDER BY " + PUBLICATION + " DESC, e.id")
    List<Item> findUpdates(@Param("courseId") long courseId, @Param("userId") long userId, @Param("asOf") ZonedDateTime asOf, @Param("since") ZonedDateTime since,
            Pageable pageable);

    @Query(SELECT + PUBLICATION + " AS activityAt " + FROM + VISIBLE + OPEN + " AND NOT " + SUBMITTED + " AND " + PUBLICATION + " > :since AND " + PUBLICATION
            + " <= :asOf ORDER BY " + PUBLICATION + " DESC, e.id")
    List<Item> findNewAction(@Param("courseId") long courseId, @Param("userId") long userId, @Param("asOf") ZonedDateTime asOf, @Param("since") ZonedDateTime since,
            Pageable pageable);

    @Query(SELECT + PUBLICATION + " AS activityAt " + FROM + VISIBLE + OPEN + " AND NOT " + SUBMITTED + " ORDER BY " + DUE + " NULLS LAST, e.id")
    List<Item> findAvailableAction(@Param("courseId") long courseId, @Param("userId") long userId, @Param("asOf") ZonedDateTime asOf, Pageable pageable);
}
