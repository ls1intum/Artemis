package de.tum.cit.aet.artemis.core.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalField;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.annotation.Nullable;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.dto.CourseStatisticsAverageScore;
import de.tum.cit.aet.artemis.core.dto.StatisticsEntry;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.domain.Exercise;
import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.domain.enumeration.GraphType;
import de.tum.cit.aet.artemis.domain.enumeration.SpanType;
import de.tum.cit.aet.artemis.domain.enumeration.StatisticsView;

/**
 * Spring Data JPA repository for the statistics pages
 */
@Profile(PROFILE_CORE)
@Repository
public interface StatisticsRepository extends ArtemisJpaRepository<User, Long> {

    @Query("""
            SELECT new de.tum.cit.aet.artemis.core.dto.StatisticsEntry(
                s.submissionDate,
                count(s.id)
            )
            FROM Submission s
            WHERE s.submissionDate >= :startDate
                AND s.submissionDate <= :endDate
                AND (
                    s.participation.exercise.exerciseGroup IS NOT NULL
                    OR EXISTS (SELECT c FROM Course c WHERE s.participation.exercise.course.testCourse = FALSE)
                )
            GROUP BY s.submissionDate
            ORDER BY s.submissionDate ASC
            """)
    List<StatisticsEntry> getTotalSubmissions(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.core.dto.StatisticsEntry(
                s.submissionDate,
                count(s.id)
            )
            FROM Submission s
            WHERE s.submissionDate >= :startDate
                AND s.submissionDate <= :endDate
                AND s.participation.exercise.id IN :exerciseIds
            GROUP BY s.submissionDate
            ORDER BY s.submissionDate ASC
            """)
    List<StatisticsEntry> getTotalSubmissionsForCourse(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate,
            @Param("exerciseIds") List<Long> exerciseIds);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.core.dto.StatisticsEntry(
                s.submissionDate,
                count(s.id)
            )
            FROM Submission s
            WHERE s.submissionDate >= :startDate
                AND s.submissionDate <= :endDate
                AND s.participation.exercise.id = :exerciseId
            GROUP BY s.submissionDate
            ORDER BY s.submissionDate ASC
            """)
    List<StatisticsEntry> getTotalSubmissionsForExercise(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate,
            @Param("exerciseId") long exerciseId);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.core.dto.StatisticsEntry(
                submission.submissionDate,
                p.student.login
            )
            FROM StudentParticipation p
                LEFT JOIN p.submissions submission
            WHERE submission.submissionDate >= :startDate
                AND submission.submissionDate <= :endDate
                AND p.student.login NOT LIKE '%test%'
                AND (submission.participation.exercise.exerciseGroup IS NOT NULL
                    OR EXISTS (SELECT c FROM Course c WHERE submission.participation.exercise.course.testCourse = FALSE)
                )
            ORDER BY submission.submissionDate ASC
            """)
    List<StatisticsEntry> getActiveUsers(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate);

    @Query("""
            SELECT DISTINCT p.student.login
            FROM StudentParticipation p
                LEFT JOIN p.submissions submission
            WHERE submission.submissionDate >= :startDate
                AND submission.submissionDate <= :endDate
                AND p.student.login NOT LIKE '%test%'
            """)
    List<String> getActiveUserNames(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate);

    /**
     * Count users that were active within the given date range.
     * Users are considered as active if they created a submission within the given date range
     *
     * @param startDate the minimum submission date
     * @param endDate   the maximum submission date
     * @return a list of active users
     */
    @Query("""
            SELECT COUNT(DISTINCT p.student.id)
            FROM StudentParticipation p
                LEFT JOIN p.submissions submission
            WHERE submission.submissionDate >= :startDate
                AND submission.submissionDate <= :endDate
                AND p.student.login NOT LIKE '%test%'
                AND (
                    p.exercise.exerciseGroup IS NOT NULL
                    OR p.exercise.course.testCourse = FALSE
                )
            """)
    Long countActiveUsers(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.core.dto.StatisticsEntry(
                submission.submissionDate,
                p.student.login
            )
            FROM StudentParticipation p
                LEFT JOIN p.submissions submission
            WHERE submission.submissionDate >= :startDate
                AND submission.submissionDate <= :endDate
                AND p.student.login NOT LIKE '%test%'
                AND p.exercise.id IN :exerciseIds
            ORDER BY submission.submissionDate ASC
            """)
    List<StatisticsEntry> getActiveUsersForCourse(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate,
            @Param("exerciseIds") List<Long> exerciseIds);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.core.dto.StatisticsEntry(
                submission.submissionDate,
                p.student.login
            )
            FROM StudentParticipation p
                LEFT JOIN p.submissions submission
            WHERE submission.submissionDate >= :startDate
                AND submission.submissionDate <= :endDate
                AND p.student.login NOT LIKE '%test%'
            AND p.exercise.id = :exerciseId
            ORDER BY submission.submissionDate ASC
            """)
    List<StatisticsEntry> getActiveUsersForExercise(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate, @Param("exerciseId") long exerciseId);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.core.dto.StatisticsEntry(
                e.releaseDate, COUNT(e.id)
            )
            FROM Exercise e
            WHERE e.releaseDate >= :startDate
                AND e.releaseDate <= :endDate
                AND e.course.testCourse = FALSE
            GROUP BY e.releaseDate
            ORDER BY e.releaseDate ASC
            """)
    List<StatisticsEntry> getReleasedExercises(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.core.dto.StatisticsEntry(
                e.releaseDate, COUNT(e.id)
            )
            FROM Exercise e
            WHERE e.releaseDate >= :startDate
                AND e.releaseDate <= :endDate
                AND e.id IN :exerciseIds
            GROUP BY e.releaseDate
            ORDER BY e.releaseDate ASC
            """)
    List<StatisticsEntry> getReleasedExercisesForCourse(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate,
            @Param("exerciseIds") List<Long> exerciseIds);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.core.dto.StatisticsEntry(
                e.dueDate, COUNT(e.id)
            )
            FROM Exercise e
            WHERE e.dueDate >= :startDate
                AND e.dueDate <= :endDate
                AND e.course.testCourse = FALSE
            GROUP BY e.dueDate
            ORDER BY e.dueDate ASC
            """)
    List<StatisticsEntry> getExercisesDue(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.core.dto.StatisticsEntry(
                e.dueDate, count(e.id)
            )
            FROM Exercise e
            WHERE e.dueDate >= :startDate
                AND e.dueDate <= :endDate
                AND e.id IN :exerciseIds
            GROUP BY e.dueDate
            ORDER BY e.dueDate ASC
            """)
    List<StatisticsEntry> getExercisesDueForCourse(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate,
            @Param("exerciseIds") List<Long> exerciseIds);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.core.dto.StatisticsEntry(
                p.auditEventDate, u.login
            )
            FROM User u
                LEFT JOIN PersistentAuditEvent p ON u.login = p.principal
            WHERE p.auditEventType = 'AUTHENTICATION_SUCCESS'
                AND u.login NOT LIKE '%test%'
                AND p.auditEventDate >= :startDate AND p.auditEventDate <= :endDate
            ORDER BY p.auditEventDate ASC
            """)
    List<StatisticsEntry> getLoggedInUsers(@Param("startDate") Instant startDate, @Param("endDate") Instant endDate);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.core.dto.StatisticsEntry(
                e.endDate, COUNT(e.id)
            )
            FROM Exam e
            WHERE e.endDate >= :startDate
                AND e.endDate <= :endDate
                AND e.course.testCourse = FALSE
            GROUP BY e.endDate
            ORDER BY e.endDate ASC
            """)
    List<StatisticsEntry> getConductedExams(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.core.dto.StatisticsEntry(
                e.endDate, COUNT(e.id)
            )
            FROM Exam e
            WHERE e.endDate >= :startDate
                AND e.endDate <= :endDate
                AND e.course.id = :courseId
            GROUP BY e.endDate
            ORDER BY e.endDate ASC
            """)
    List<StatisticsEntry> getConductedExamsForCourse(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate, @Param("courseId") long courseId);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.core.dto.StatisticsEntry(
                e.endDate, COUNT(e.id)
            )
            FROM Exam e
                LEFT JOIN e.studentExams se
            WHERE e.endDate >= :startDate
                AND e.endDate <= :endDate
                AND se.submitted = TRUE
                AND e.course.testCourse = FALSE
            GROUP BY e.endDate
            ORDER BY e.endDate ASC
            """)
    List<StatisticsEntry> getExamParticipations(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.core.dto.StatisticsEntry(
                e.endDate, COUNT(e.id)
            )
            FROM Exam e
                LEFT JOIN e.studentExams se
            WHERE e.endDate >= :startDate
                AND e.endDate <= :endDate
                AND se.submitted = TRUE
                AND e.course.id = :courseId
            GROUP BY e.endDate
            ORDER BY e.endDate ASC
            """)
    List<StatisticsEntry> getExamParticipationsForCourse(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate, @Param("courseId") long courseId);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.core.dto.StatisticsEntry(
                e.endDate, SUM(SIZE(e.examUsers))
            )
            FROM Exam e
            WHERE e.endDate >= :startDate
                AND e.endDate <= :endDate
                AND e.course.testCourse = FALSE
            GROUP BY e.endDate
            ORDER BY e.endDate ASC
            """)
    List<StatisticsEntry> getExamRegistrations(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.core.dto.StatisticsEntry(
                e.endDate, SUM(SIZE(e.examUsers))
            )
            FROM Exam e
            WHERE e.endDate >= :startDate
                AND e.endDate <= :endDate
                AND e.course.id = :courseId
            GROUP BY e.endDate
            ORDER BY e.endDate ASC
            """)
    List<StatisticsEntry> getExamRegistrationsForCourse(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate, @Param("courseId") long courseId);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.core.dto.StatisticsEntry(
                r.completionDate,
                r.assessor.login
            )
            FROM Result r
            WHERE r.completionDate >= :startDate
                AND r.completionDate <= :endDate
                AND (
                    r.assessmentType = de.tum.cit.aet.artemis.domain.enumeration.AssessmentType.MANUAL
                    OR r.assessmentType = de.tum.cit.aet.artemis.domain.enumeration.AssessmentType.SEMI_AUTOMATIC
                ) AND r.assessor.login NOT LIKE '%test%'
                AND (
                    r.participation.exercise.exerciseGroup IS NOT NULL
                    OR EXISTS (SELECT c FROM Course c WHERE r.participation.exercise.course.testCourse = FALSE)
                )
            """)
    List<StatisticsEntry> getActiveTutors(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.core.dto.StatisticsEntry(
                r.completionDate,
                r.assessor.login
            )
            FROM Result r
            WHERE r.completionDate >= :startDate
                AND r.completionDate <= :endDate
                AND (
                    r.assessmentType = de.tum.cit.aet.artemis.domain.enumeration.AssessmentType.MANUAL
                    OR r.assessmentType = de.tum.cit.aet.artemis.domain.enumeration.AssessmentType.SEMI_AUTOMATIC
                ) AND r.assessor.login NOT LIKE '%test%'
                AND r.participation.exercise.id IN :exerciseIds
            """)
    List<StatisticsEntry> getActiveTutorsForCourse(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate,
            @Param("exerciseIds") List<Long> exerciseIds);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.core.dto.StatisticsEntry(
                r.completionDate,
                r.assessor.login
            )
            FROM Result r
            WHERE r.completionDate >= :startDate
                AND r.completionDate <= :endDate
                AND (
                    r.assessmentType = de.tum.cit.aet.artemis.domain.enumeration.AssessmentType.MANUAL
                    OR r.assessmentType = de.tum.cit.aet.artemis.domain.enumeration.AssessmentType.SEMI_AUTOMATIC
                ) AND r.assessor.login NOT LIKE '%test%'
                AND r.participation.exercise.id = :exerciseId
            """)
    List<StatisticsEntry> getActiveTutorsForExercise(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate, @Param("exerciseId") long exerciseId);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.core.dto.StatisticsEntry(
                r.completionDate, COUNT(r.id)
            )
            FROM Result r
            WHERE r.completionDate >= :startDate
                AND r.completionDate <= :endDate
                AND (
                    r.participation.exercise.exerciseGroup IS NOT NULL
                    OR EXISTS (SELECT c FROM Course c WHERE r.participation.exercise.course.testCourse = FALSE)
                )
            GROUP BY r.completionDate
            ORDER BY r.completionDate
            """)
    List<StatisticsEntry> getCreatedResults(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.core.dto.StatisticsEntry(
                r.completionDate, COUNT(r.id)
            )
            FROM Result r
            WHERE r.completionDate >= :startDate
                AND r.completionDate <= :endDate
                AND r.participation.exercise.id IN :exerciseIds
            GROUP BY r.completionDate
            ORDER BY r.completionDate
            """)
    List<StatisticsEntry> getCreatedResultsForCourse(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate,
            @Param("exerciseIds") List<Long> exerciseIds);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.core.dto.StatisticsEntry(
                r.completionDate, COUNT(r.id)
            )
            FROM Result r
            WHERE r.completionDate >= :startDate
                AND r.completionDate <= :endDate
                AND r.participation.exercise.id = :exerciseId
            GROUP BY r.completionDate
            ORDER BY r.completionDate
            """)
    List<StatisticsEntry> getCreatedResultsForExercise(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate, @Param("exerciseId") long exerciseId);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.core.dto.StatisticsEntry(
                r.completionDate, SUM(SIZE(r.feedbacks))
            )
            FROM Result r
            WHERE r.completionDate >= :startDate
                AND r.completionDate <= :endDate
                AND (
                    r.participation.exercise.exerciseGroup IS NOT NULL
                    OR EXISTS(SELECT c FROM Course c WHERE r.participation.exercise.course.testCourse = FALSE ))
            GROUP BY r.completionDate
            ORDER BY r.completionDate
            """)
    List<StatisticsEntry> getResultFeedbacks(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.core.dto.StatisticsEntry(
                r.completionDate, SUM(SIZE(r.feedbacks))
            )
            FROM Result r
            WHERE r.completionDate >= :startDate
                AND r.completionDate <= :endDate
                AND r.participation.exercise.id IN :exerciseIds
            GROUP BY r.completionDate
            ORDER BY r.completionDate
            """)
    List<StatisticsEntry> getResultFeedbacksForCourse(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate,
            @Param("exerciseIds") List<Long> exerciseIds);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.core.dto.StatisticsEntry(
                r.completionDate, SUM(SIZE(r.feedbacks))
            )
            FROM Result r
            WHERE r.completionDate >= :startDate
                AND r.completionDate <= :endDate
                AND r.participation.exercise.id = :exerciseId
            GROUP BY r.completionDate
            ORDER BY r.completionDate
            """)
    List<StatisticsEntry> getResultFeedbacksForExercise(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate, @Param("exerciseId") long exerciseId);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.core.dto.StatisticsEntry(
                post.creationDate, COUNT(post.id)
            )
            FROM Post post
                LEFT JOIN TREAT (post.conversation AS Channel) channel
            WHERE post.creationDate >= :startDate
                AND post.creationDate <= :endDate
                AND channel.course.id = :courseId
                AND channel.isCourseWide = TRUE
            GROUP BY post.creationDate
            ORDER BY post.creationDate ASC
            """)
    List<StatisticsEntry> getPostsForCourseInDateRange(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate, @Param("courseId") long courseId);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.core.dto.StatisticsEntry(
                post.creationDate, COUNT(post.id)
            )
            FROM Post post
                LEFT JOIN TREAT (post.conversation AS Channel) channel
            WHERE post.creationDate >= :startDate
                AND post.creationDate <= :endDate
                AND channel.exercise.id = :exerciseId
            GROUP BY post.creationDate
            ORDER BY post.creationDate ASC
            """)
    List<StatisticsEntry> getPostsForExerciseInDateRange(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate,
            @Param("exerciseId") long exerciseId);

    @Query("""
            SELECT COUNT(post)
            FROM Post post
                LEFT JOIN TREAT (post.conversation AS Channel) channel
            WHERE channel.exercise.id = :exerciseId
            """)
    long getNumberOfExercisePosts(@Param("exerciseId") long exerciseId);

    @Query("""
            SELECT COUNT(DISTINCT post.id)
            FROM AnswerPost answer
                LEFT JOIN answer.post post
                LEFT JOIN TREAT (post.conversation AS Channel) channel
            WHERE channel.exercise.id = :exerciseId
                AND answer.resolvesPost = TRUE
            """)
    long getNumberOfResolvedExercisePosts(@Param("exerciseId") long exerciseId);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.core.dto.StatisticsEntry(
                answer.creationDate, COUNT(answer.id)
            )
            FROM AnswerPost answer
                LEFT JOIN answer.post post
                LEFT JOIN TREAT (post.conversation AS Channel) channel
            WHERE answer.creationDate >= :startDate
                AND answer.creationDate <= :endDate
                AND answer.resolvesPost = TRUE
                AND channel.course.id = :courseId
                AND channel.isCourseWide = TRUE
            GROUP BY answer.creationDate
            ORDER BY answer.creationDate ASC
            """)
    List<StatisticsEntry> getResolvedCoursePostsInDateRange(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate, @Param("courseId") long courseId);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.core.dto.StatisticsEntry(
                answer.creationDate, COUNT(answer.id)
            )
            FROM AnswerPost answer
                LEFT JOIN answer.post post
                LEFT JOIN TREAT (post.conversation AS Channel) channel
            WHERE answer.creationDate >= :startDate
                AND answer.creationDate <= :endDate
                AND answer.resolvesPost = TRUE
                AND channel.exercise.id = :exerciseId
            GROUP BY answer.creationDate
            ORDER BY answer.creationDate ASC
            """)
    List<StatisticsEntry> getResolvedExercisePostsInDateRange(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate,
            @Param("exerciseId") long exerciseId);

    @Query("""
            SELECT e.id
            FROM Exercise e
            WHERE e.course.id = :courseId
            """)
    List<Long> findExerciseIdsByCourseId(@Param("courseId") long courseId);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.domain.statistics.CourseStatisticsAverageScore(
                p.exercise.id,
                p.exercise.title,
                p.exercise.releaseDate,
                AVG(p.lastScore)
            )
            FROM ParticipantScore p
            WHERE p.exercise IN :exercises
            GROUP BY p.exercise.id, p.exercise.title, p.exercise.releaseDate
            """)
    List<CourseStatisticsAverageScore> findAvgPointsForExercises(@Param("exercises") Set<Exercise> exercises);

    /**
     * Gets the number of entries for the specific graphType and the span. First we distribute into the different types of graphs.
     * After that, we distinguish between the views in which this graph can be shown and call the corresponding method
     *
     * @param graphType the type of graph the data should be fetched for (see GraphType.java)
     * @param span      the spanType for which the call is executed
     * @param startDate The startDate of which the data should be fetched
     * @param endDate   The endDate of which the data should be fetched
     * @param view      the view in which the data will be displayed (Artemis, Course, Exercise)
     * @param entityId  the entityId which is null for a user statistics call and contains the id for the other statistics pages
     * @return the return value of the processed database call which returns a list of entries
     */
    default List<StatisticsEntry> getNumberOfEntriesPerTimeSlot(GraphType graphType, SpanType span, ZonedDateTime startDate, ZonedDateTime endDate, StatisticsView view,
            @Nullable Long entityId) {
        var exerciseIds = view == StatisticsView.COURSE && entityId != null ? findExerciseIdsByCourseId(entityId) : null;
        switch (graphType) {
            case SUBMISSIONS -> {
                return switch (view) {
                    case ARTEMIS -> getTotalSubmissions(startDate, endDate);
                    case COURSE -> getTotalSubmissionsForCourse(startDate, endDate, exerciseIds);
                    case EXERCISE -> getTotalSubmissionsForExercise(startDate, endDate, entityId);
                };
            }
            case ACTIVE_USERS -> {
                List<StatisticsEntry> result = switch (view) {
                    case ARTEMIS -> getActiveUsers(startDate, endDate);
                    case COURSE -> getActiveUsersForCourse(startDate, endDate, exerciseIds);
                    case EXERCISE -> getActiveUsersForExercise(startDate, endDate, entityId);
                };
                return filterDuplicatedUsers(span, result, startDate, graphType);
            }
            case LOGGED_IN_USERS -> {
                Instant startDateInstant = startDate.toInstant();
                Instant endDateInstant = endDate.toInstant();
                List<StatisticsEntry> result = getLoggedInUsers(startDateInstant, endDateInstant);
                return filterDuplicatedUsers(span, result, startDate, graphType);
            }
            case RELEASED_EXERCISES -> {
                return switch (view) {
                    case ARTEMIS -> getReleasedExercises(startDate, endDate);
                    case COURSE -> getReleasedExercisesForCourse(startDate, endDate, exerciseIds);
                    case EXERCISE -> throw new UnsupportedOperationException("Unsupported view: " + view);
                };
            }
            case EXERCISES_DUE -> {
                return switch (view) {
                    case ARTEMIS -> getExercisesDue(startDate, endDate);
                    case COURSE -> getExercisesDueForCourse(startDate, endDate, exerciseIds);
                    case EXERCISE -> throw new UnsupportedOperationException("Unsupported view: " + view);
                };
            }
            case CONDUCTED_EXAMS -> {
                return switch (view) {
                    case ARTEMIS -> getConductedExams(startDate, endDate);
                    case COURSE -> getConductedExamsForCourse(startDate, endDate, entityId);
                    case EXERCISE -> throw new UnsupportedOperationException("Unsupported view: " + view);
                };
            }
            case EXAM_PARTICIPATIONS -> {
                return switch (view) {
                    case ARTEMIS -> getExamParticipations(startDate, endDate);
                    case COURSE -> getExamParticipationsForCourse(startDate, endDate, entityId);
                    case EXERCISE -> throw new UnsupportedOperationException("Unsupported view: " + view);
                };
            }
            case EXAM_REGISTRATIONS -> {
                return switch (view) {
                    case ARTEMIS -> getExamRegistrations(startDate, endDate);
                    case COURSE -> getExamRegistrationsForCourse(startDate, endDate, entityId);
                    case EXERCISE -> throw new UnsupportedOperationException("Unsupported view: " + view);
                };
            }
            case ACTIVE_TUTORS -> {
                List<StatisticsEntry> result = switch (view) {
                    case ARTEMIS -> getActiveTutors(startDate, endDate);
                    case COURSE -> getActiveTutorsForCourse(startDate, endDate, exerciseIds);
                    case EXERCISE -> getActiveTutorsForExercise(startDate, endDate, entityId);
                };
                return filterDuplicatedUsers(span, result, startDate, graphType);
            }
            case CREATED_RESULTS -> {
                return switch (view) {
                    case ARTEMIS -> getCreatedResults(startDate, endDate);
                    case COURSE -> getCreatedResultsForCourse(startDate, endDate, exerciseIds);
                    case EXERCISE -> getCreatedResultsForExercise(startDate, endDate, entityId);
                };
            }
            case CREATED_FEEDBACKS -> {
                return switch (view) {
                    case ARTEMIS -> getResultFeedbacks(startDate, endDate);
                    case COURSE -> getResultFeedbacksForCourse(startDate, endDate, exerciseIds);
                    case EXERCISE -> getResultFeedbacksForExercise(startDate, endDate, entityId);
                };
            }
            case POSTS -> {
                return switch (view) {
                    case COURSE -> getPostsForCourseInDateRange(startDate, endDate, entityId);
                    case EXERCISE -> getPostsForExerciseInDateRange(startDate, endDate, entityId);
                    case ARTEMIS -> throw new UnsupportedOperationException("Unsupported view: " + view);
                };
            }
            case RESOLVED_POSTS -> {
                return switch (view) {
                    case COURSE -> getResolvedCoursePostsInDateRange(startDate, endDate, entityId);
                    case EXERCISE -> getResolvedExercisePostsInDateRange(startDate, endDate, entityId);
                    case ARTEMIS -> throw new UnsupportedOperationException("Unsupported view: " + view);
                };
            }
            default -> throw new UnsupportedOperationException("Unsupported graph type: " + graphType);
        }
    }

    /**
     * This method handles the duplicity of usernames. It gets a List<StatisticsData> with set day values and set username values.
     * It then filters out all duplicated user entries per timeslot (depending on spanType) and return a list of entries
     * containing the amount of distinct users per timeslot
     *
     * @param span      DAY,WEEK,MONTH or YEAR
     * @param result    the result given by the Repository call
     * @param startDate the startDate of the period
     * @param graphType the graphType for which the List should be converted
     * @return A List<StatisticsData> with only distinct users per timeslot
     */
    private List<StatisticsEntry> filterDuplicatedUsers(SpanType span, List<StatisticsEntry> result, ZonedDateTime startDate, GraphType graphType) {
        Map<Integer, Set<String>> users = new HashMap<>();
        for (StatisticsEntry listElement : result) {
            ZonedDateTime date;
            if (graphType == GraphType.LOGGED_IN_USERS) {
                Instant instant = (Instant) listElement.getDay();
                date = instant.atZone(startDate.getZone());
            }
            else {
                date = (ZonedDateTime) listElement.getDay();
            }
            Integer index = switch (span) {
                case DAY -> date.getHour();
                case WEEK, MONTH -> Math.toIntExact(ChronoUnit.DAYS.between(startDate, date));
                case QUARTER -> getWeekOfDate(date);
                case YEAR -> date.getMonth().getValue();
            };
            addUserToTimeslot(users, listElement, index);
        }
        return mergeUsersPerTimeslotIntoList(users, span, startDate);
    }

    /**
     * This method is normally invoked in a for each loop and adds a user based on the set element in case it does not yet exist in the users map
     *
     * @param users              the map of existing users
     * @param userStatisticEntry the statistic entry which contains a username and a potentially new user
     * @param index              the index of the map which should be considered, can be a date or an integer
     */
    default void addUserToTimeslot(Map<Integer, Set<String>> users, StatisticsEntry userStatisticEntry, Integer index) {
        String username = userStatisticEntry.getUsername();
        // if this index is not yet existing in users
        // if the value of the map for this index does not contain this username
        users.computeIfAbsent(index, k -> new HashSet<>(Collections.singletonList(username))).add(username);
    }

    /**
     * Helper class for the filterDuplicatedUsers method, which takes the users in the same timeslot as well as some parameters needed
     * for calculation to convert these into a List<StatisticsData> which is then returned
     *
     * @param users     a Map where a date gets mapped onto a list of users with entries on this date
     * @param span      the spanType for which we created the users List
     * @param startDate the startDate which we need for mapping into timeslots
     * @return A List<StatisticsData> with no duplicated user per timeslot
     */
    private List<StatisticsEntry> mergeUsersPerTimeslotIntoList(Map<Integer, Set<String>> users, SpanType span, ZonedDateTime startDate) {
        List<StatisticsEntry> returnList = new ArrayList<>();
        users.forEach((timeIndex, userSet) -> {
            ZonedDateTime start = switch (span) {
                case DAY -> startDate.withHour(timeIndex);
                case WEEK, MONTH -> startDate.plusDays(timeIndex);
                case QUARTER -> {
                    int year = timeIndex < getWeekOfDate(startDate) ? startDate.getYear() + 1 : startDate.getYear();
                    ZonedDateTime firstDateOfYear = ZonedDateTime.of(year, 1, 1, 0, 0, 0, 0, startDate.getZone());
                    yield getWeekOfDate(firstDateOfYear) == 1 ? firstDateOfYear.plusWeeks(timeIndex - 1) : firstDateOfYear.plusWeeks(timeIndex);
                }
                case YEAR -> startDate.withMonth(timeIndex);
            };
            StatisticsEntry listElement = new StatisticsEntry(start, userSet.size());
            returnList.add(listElement);
        });
        return returnList;
    }

    /**
     * Gets the week of the given date
     *
     * @param date the date to get the week for
     * @return the calendar week of the given date
     */
    default Integer getWeekOfDate(ZonedDateTime date) {
        LocalDate localDate = date.toLocalDate();
        TemporalField weekOfYear = WeekFields.of(DayOfWeek.MONDAY, 4).weekOfWeekBasedYear();
        return localDate.get(weekOfYear);
    }

    /**
     * Gets a List<StatisticsData> each containing a date and an amount of entries. We take the amount of entries and
     * map it into the results list based on the date of the entry. This method handles the spanType DAY
     * **Note**: The length of the result list must be correct, all values must be initialized with 0
     *
     * @param outcome A List<StatisticsData>, each StatisticsData containing a date and the amount of entries for one timeslot
     * @param result  the list in which the converted outcome should be inserted
     */
    default void sortDataIntoHours(List<StatisticsEntry> outcome, List<Integer> result) {
        for (StatisticsEntry entry : outcome) {
            int hourIndex = ((ZonedDateTime) entry.getDay()).getHour();
            int amount = Math.toIntExact(entry.getAmount());
            int currentValue = result.get(hourIndex);
            result.set(hourIndex, currentValue + amount);
        }
    }

    /**
     * Gets a List<StatisticsData> each containing a date and an amount of entries. We take the amount of entries and
     * map it into the results list based on the date of the entry. This method handles the spanType WEEK and MONTH
     * **Note**: The length of the result list must be correct, all values must be initialized with 0
     *
     * @param outcome   A List<StatisticsData>, each StatisticsData containing a date and the amount of entries for one timeslot
     * @param result    the list in which the converted outcome should be inserted
     * @param startDate the startDate of the result list
     */
    default void sortDataIntoDays(List<StatisticsEntry> outcome, List<Integer> result, ZonedDateTime startDate) {
        for (StatisticsEntry entry : outcome) {
            ZonedDateTime date = (ZonedDateTime) entry.getDay();
            int amount = Math.toIntExact(entry.getAmount());
            int dayIndex = Math.toIntExact(ChronoUnit.DAYS.between(startDate, date));
            int currentValue = result.get(dayIndex);
            result.set(dayIndex, currentValue + amount);
        }
    }

    /**
     * Gets a List<StatisticsData> each containing a date and an amount of entries. We take the amount of entries and
     * map it into the results list based on the date of the entry. This method sorts the data into weeks.
     * **Note**: The length of the result list must be correct, all values must be initialized with 0
     *
     * @param outcome   A List<StatisticsData>, each StatisticsData containing a date and the amount of entries for one timeslot
     * @param result    the list in which the converted outcome should be inserted, should be initialized with enough values
     * @param startDate the startDate of the result list
     */
    default void sortDataIntoWeeks(List<StatisticsEntry> outcome, List<Integer> result, ZonedDateTime startDate) {
        for (StatisticsEntry entry : outcome) {
            ZonedDateTime date = (ZonedDateTime) entry.getDay();
            int amount = Math.toIntExact(entry.getAmount());
            int dateWeek = getWeekOfDate(date);
            int startDateWeek = getWeekOfDate(startDate);
            int weeksInYear = Math.toIntExact(IsoFields.WEEK_OF_WEEK_BASED_YEAR.rangeRefinedBy(startDate).getMaximum());    // either 52 or 53
            int weekIndex = (dateWeek - startDateWeek + weeksInYear) % weeksInYear;     // make sure to have a positive value in the range [0, 52 or 53]
            int currentValue = result.get(weekIndex);
            result.set(weekIndex, currentValue + amount);
        }
    }

    /**
     * Gets a List<StatisticsData> each containing a date and an amount of entries. We take the amount of entries and
     * map it into the results list based on the date of the entry. This method handles the spanType YEAR
     * **Note**: The length of the result list must be correct, all values must be initialized with 0
     *
     * @param outcome   A List<StatisticsData>, each StatisticsData containing a date and the amount of entries for one timeslot
     * @param result    the list in which the converted outcome should be inserted
     * @param startDate the startDate of the result list
     */
    default void sortDataIntoMonths(List<StatisticsEntry> outcome, List<Integer> result, ZonedDateTime startDate) {
        for (StatisticsEntry entry : outcome) {
            ZonedDateTime date = (ZonedDateTime) entry.getDay();
            int amount = (int) entry.getAmount();
            int monthOfDate = date.getMonth().getValue();
            int monthOfStartDate = startDate.getMonth().getValue();
            int monthsPerYear = 12;
            int monthIndex = (monthOfDate - monthOfStartDate + monthsPerYear) % monthsPerYear; // make sure to have a positive value in the range [0, 12]
            int currentValue = result.get(monthIndex);
            result.set(monthIndex, currentValue + amount);
        }
    }
}
