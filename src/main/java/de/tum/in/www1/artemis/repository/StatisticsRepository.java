package de.tum.in.www1.artemis.repository;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalField;
import java.time.temporal.WeekFields;
import java.util.*;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.GraphType;
import de.tum.in.www1.artemis.domain.enumeration.SpanType;
import de.tum.in.www1.artemis.domain.enumeration.StatisticsView;
import de.tum.in.www1.artemis.domain.statistics.CourseStatisticsAverageScore;
import de.tum.in.www1.artemis.domain.statistics.StatisticsEntry;

/**
 * Spring Data JPA repository for the statistics pages
 */
@Repository
public interface StatisticsRepository extends JpaRepository<User, Long> {

    @Query("""
            select
            new de.tum.in.www1.artemis.domain.statistics.StatisticsEntry(
                s.submissionDate,
                count(s.id)
                )
            from Submission s
            where s.submissionDate >= :#{#startDate} and s.submissionDate <= :#{#endDate} and (s.participation.exercise.exerciseGroup IS NOT NULL or exists (select c from Course c where s.participation.exercise.course.testCourse = false))
            group by s.submissionDate
            order by s.submissionDate asc
            """)
    List<StatisticsEntry> getTotalSubmissions(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate);

    @Query("""
            select
            new de.tum.in.www1.artemis.domain.statistics.StatisticsEntry(
                s.submissionDate,
                count(s.id)
                )
            from Submission s
            where s.submissionDate >= :#{#startDate} and s.submissionDate <= :#{#endDate} and s.participation.exercise.id in :exerciseIds
            group by s.submissionDate
            order by s.submissionDate asc
            """)
    List<StatisticsEntry> getTotalSubmissionsForCourse(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate,
            @Param("exerciseIds") List<Long> exerciseIds);

    @Query("""
            select
            new de.tum.in.www1.artemis.domain.statistics.StatisticsEntry(
                s.submissionDate,
                count(s.id)
                )
            from Submission s
            where s.submissionDate >= :#{#startDate} and s.submissionDate <= :#{#endDate} and s.participation.exercise.id = :exerciseId
            group by s.submissionDate
            order by s.submissionDate asc
            """)
    List<StatisticsEntry> getTotalSubmissionsForExercise(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate,
            @Param("exerciseId") Long exerciseId);

    @Query("""
            select
            new de.tum.in.www1.artemis.domain.statistics.StatisticsEntry(
                s.submissionDate, u.login
                )
            from User u, Submission s, StudentParticipation p
            where s.participation.id = p.id and p.student.id = u.id and s.submissionDate >= :#{#startDate} and s.submissionDate <= :#{#endDate} and u.login not like '%test%'
            and (s.participation.exercise.exerciseGroup IS NOT NULL or exists (select c from Course c where s.participation.exercise.course.testCourse = false))
            order by s.submissionDate asc
            """)
    List<StatisticsEntry> getActiveUsers(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate);

    @Query("""
            select
            new de.tum.in.www1.artemis.domain.statistics.StatisticsEntry(
                s.submissionDate, u.login
                )
            from User u, Submission s, StudentParticipation p
            where s.participation.id = p.id and p.student.id = u.id and s.submissionDate >= :#{#startDate} and s.submissionDate <= :#{#endDate} and u.login not like '%test%'
            and p.exercise.id in :exerciseIds
            order by s.submissionDate asc
            """)
    List<StatisticsEntry> getActiveUsersForCourse(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate,
            @Param("exerciseIds") List<Long> exerciseIds);

    @Query("""
            select
            new de.tum.in.www1.artemis.domain.statistics.StatisticsEntry(
                s.submissionDate, u.login
                )
            from User u, Submission s, StudentParticipation p
            where s.participation.id = p.id and p.student.id = u.id and s.submissionDate >= :#{#startDate} and s.submissionDate <= :#{#endDate} and u.login not like '%test%'
            and p.exercise.id = :exerciseId
            order by s.submissionDate asc
            """)
    List<StatisticsEntry> getActiveUsersForExercise(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate, @Param("exerciseId") Long exerciseId);

    @Query("""
            select
            new de.tum.in.www1.artemis.domain.statistics.StatisticsEntry(
                e.releaseDate, count(e.id)
                )
            from Exercise e
            where e.releaseDate >= :#{#startDate} and e.releaseDate <= :#{#endDate} and e.course.testCourse = false
            group by e.releaseDate
            order by e.releaseDate asc
            """)
    List<StatisticsEntry> getReleasedExercises(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate);

    @Query("""
            select
            new de.tum.in.www1.artemis.domain.statistics.StatisticsEntry(
                e.releaseDate, count(e.id)
                )
            from Exercise e
            where e.releaseDate >= :#{#startDate} and e.releaseDate <= :#{#endDate} and e.id in :exerciseIds
            group by e.releaseDate
            order by e.releaseDate asc
            """)
    List<StatisticsEntry> getReleasedExercisesForCourse(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate,
            @Param("exerciseIds") List<Long> exerciseIds);

    @Query("""
            select
            new de.tum.in.www1.artemis.domain.statistics.StatisticsEntry(
                e.dueDate, count(e.id)
                )
            from Exercise e
            where e.dueDate >= :#{#startDate} and e.dueDate <= :#{#endDate} and e.course.testCourse = false
            group by e.dueDate
            order by e.dueDate asc
            """)
    List<StatisticsEntry> getExercisesDue(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate);

    @Query("""
            select
            new de.tum.in.www1.artemis.domain.statistics.StatisticsEntry(
                e.dueDate, count(e.id)
                )
            from Exercise e
            where e.dueDate >= :#{#startDate} and e.dueDate <= :#{#endDate} and e.id in :exerciseIds
            group by e.dueDate
            order by e.dueDate asc
            """)
    List<StatisticsEntry> getExercisesDueForCourse(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate,
            @Param("exerciseIds") List<Long> exerciseIds);

    @Query("""
            select
            new de.tum.in.www1.artemis.domain.statistics.StatisticsEntry(
                p.auditEventDate, u.login
                )
            from User u, PersistentAuditEvent p
            where u.login = p.principal and p.auditEventType = 'AUTHENTICATION_SUCCESS' and u.login not like '%test%' and p.auditEventDate >= :#{#startDate} and p.auditEventDate <= :#{#endDate}
            order by p.auditEventDate asc
            """)
    List<StatisticsEntry> getLoggedInUsers(@Param("startDate") Instant startDate, @Param("endDate") Instant endDate);

    @Query("""
            select
            new de.tum.in.www1.artemis.domain.statistics.StatisticsEntry(
                e.endDate, count(e.id)
                )
            from Exam e
            where e.endDate >= :#{#startDate} and e.endDate <= :#{#endDate} and e.course.testCourse = false
            group by e.endDate
            order by e.endDate asc
            """)
    List<StatisticsEntry> getConductedExams(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate);

    @Query("""
            select
            new de.tum.in.www1.artemis.domain.statistics.StatisticsEntry(
                e.endDate, count(e.id)
                )
            from Exam e
            where e.endDate >= :#{#startDate} and e.endDate <= :#{#endDate} and e.course.id = :#{#courseId}
            group by e.endDate
            order by e.endDate asc
            """)
    List<StatisticsEntry> getConductedExamsForCourse(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate, @Param("courseId") Long courseId);

    @Query("""
            select
            new de.tum.in.www1.artemis.domain.statistics.StatisticsEntry(
                e.endDate, count(se.id)
                )
            from StudentExam se, Exam e
            where se.submitted = true and se.exam = e and e.endDate >= :#{#startDate} and e.endDate <= :#{#endDate} and e.course.testCourse = false
            group by e.endDate
            order by e.endDate asc
            """)
    List<StatisticsEntry> getExamParticipations(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate);

    @Query("""
            select
            new de.tum.in.www1.artemis.domain.statistics.StatisticsEntry(
                e.endDate, count(se.id)
                )
            from StudentExam se, Exam e
            where se.submitted = true and se.exam = e and e.endDate >= :#{#startDate} and e.endDate <= :#{#endDate} and e.course.id = :#{#courseId}
            group by e.endDate
            order by e.endDate asc
            """)
    List<StatisticsEntry> getExamParticipationsForCourse(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate, @Param("courseId") Long courseId);

    @Query("""
            select
            new de.tum.in.www1.artemis.domain.statistics.StatisticsEntry(
                e.endDate, sum(size(e.registeredUsers))
                )
            from Exam e
            where e.endDate >= :#{#startDate} and e.endDate <= :#{#endDate} and e.course.testCourse = false
            group by e.endDate
            order by e.endDate asc
            """)
    List<StatisticsEntry> getExamRegistrations(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate);

    @Query("""
            select
            new de.tum.in.www1.artemis.domain.statistics.StatisticsEntry(
                e.endDate, sum(size(e.registeredUsers))
                )
            from Exam e
            where e.endDate >= :#{#startDate} and e.endDate <= :#{#endDate} and e.course.id = :#{#courseId}
            group by e.endDate
            order by e.endDate asc
            """)
    List<StatisticsEntry> getExamRegistrationsForCourse(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate, @Param("courseId") Long courseId);

    @Query("""
            select new de.tum.in.www1.artemis.domain.statistics.StatisticsEntry(
                r.completionDate, r.assessor.login
                )
            from Result r
            where (r.assessmentType = 'MANUAL' or r.assessmentType = 'SEMI_AUTOMATIC') and r.completionDate >= :#{#startDate} and r.completionDate <= :#{#endDate} and r.assessor.login not like '%test%'
            and (r.participation.exercise.exerciseGroup IS NOT NULL or exists (select c from Course c where r.participation.exercise.course.testCourse = false))
            """)
    List<StatisticsEntry> getActiveTutors(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate);

    @Query("""
            select
            new de.tum.in.www1.artemis.domain.statistics.StatisticsEntry(
                r.completionDate, r.assessor.login
                )
            from Result r
            where (r.assessmentType = 'MANUAL' or r.assessmentType = 'SEMI_AUTOMATIC') and r.completionDate >= :#{#startDate} and r.completionDate <= :#{#endDate} and r.assessor.login not like '%test%'
            and r.participation.exercise.id in :exerciseIds
            """)
    List<StatisticsEntry> getActiveTutorsForCourse(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate,
            @Param("exerciseIds") List<Long> exerciseIds);

    @Query("""
            select
            new de.tum.in.www1.artemis.domain.statistics.StatisticsEntry(
                r.completionDate, r.assessor.login
                )
            from Result r
            where (r.assessmentType = 'MANUAL' or r.assessmentType = 'SEMI_AUTOMATIC') and r.completionDate >= :#{#startDate} and r.completionDate <= :#{#endDate} and r.assessor.login not like '%test%'
            and r.participation.exercise.id = :exerciseId
            """)
    List<StatisticsEntry> getActiveTutorsForExercise(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate, @Param("exerciseId") Long exerciseId);

    @Query("""
            select
            new de.tum.in.www1.artemis.domain.statistics.StatisticsEntry(
                r.completionDate, count(r.id)
                )
            from Result r
            where r.completionDate >= :#{#startDate} and r.completionDate <= :#{#endDate} and (r.participation.exercise.exerciseGroup IS NOT NULL or exists (select c from Course c where r.participation.exercise.course.testCourse = false))
            group by r.completionDate
            order by r.completionDate
            """)
    List<StatisticsEntry> getCreatedResults(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate);

    @Query("""
            select
            new de.tum.in.www1.artemis.domain.statistics.StatisticsEntry(
                r.completionDate, count(r.id)
                )
            from Result r
            where r.completionDate >= :#{#startDate} and r.completionDate <= :#{#endDate} and r.participation.exercise.id in :exerciseIds
            group by r.completionDate
            order by r.completionDate
            """)
    List<StatisticsEntry> getCreatedResultsForCourse(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate,
            @Param("exerciseIds") List<Long> exerciseIds);

    @Query("""
            select
            new de.tum.in.www1.artemis.domain.statistics.StatisticsEntry(
                r.completionDate, count(r.id)
                )
            from Result r
            where r.completionDate >= :#{#startDate} and r.completionDate <= :#{#endDate} and r.participation.exercise.id = :exerciseId
            group by r.completionDate
            order by r.completionDate
            """)
    List<StatisticsEntry> getCreatedResultsForExercise(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate, @Param("exerciseId") Long exerciseId);

    @Query("""
            select
            new de.tum.in.www1.artemis.domain.statistics.StatisticsEntry(
                r.completionDate, sum(size(r.feedbacks))
                )
            from Result r
            where r.completionDate >= :#{#startDate} and r.completionDate <= :#{#endDate} and (r.participation.exercise.exerciseGroup IS NOT NULL or exists (select c from Course c where r.participation.exercise.course.testCourse = false))
            group by r.completionDate
            order by r.completionDate
            """)
    List<StatisticsEntry> getResultFeedbacks(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate);

    @Query("""
            select
            new de.tum.in.www1.artemis.domain.statistics.StatisticsEntry(
                r.completionDate, sum(size(r.feedbacks))
                )
            from Result r
            where r.completionDate >= :#{#startDate} and r.completionDate <= :#{#endDate} and r.participation.exercise.id in :exerciseIds
            group by r.completionDate
            order by r.completionDate
            """)
    List<StatisticsEntry> getResultFeedbacksForCourse(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate,
            @Param("exerciseIds") List<Long> exerciseIds);

    @Query("""
            select
            new de.tum.in.www1.artemis.domain.statistics.StatisticsEntry(
                r.completionDate, sum(size(r.feedbacks))
                )
            from Result r
            where r.completionDate >= :#{#startDate} and r.completionDate <= :#{#endDate} and r.participation.exercise.id = :exerciseId
            group by r.completionDate
            order by r.completionDate
            """)
    List<StatisticsEntry> getResultFeedbacksForExercise(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate, @Param("exerciseId") Long exerciseId);

    @Query("""
            select
            new de.tum.in.www1.artemis.domain.statistics.StatisticsEntry(
                post.creationDate, count(post.id)
                )
            from Post post left join post.lecture lectures left join post.exercise exercises
            where post.creationDate >= :#{#startDate} and post.creationDate <= :#{#endDate} and (lectures.course.id = :#{#courseId} or exercises.course.id = :#{#courseId})
            group by post.creationDate
            order by post.creationDate asc
            """)
    List<StatisticsEntry> getQuestionsAskedForCourse(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate, @Param("courseId") Long courseId);

    @Query("""
            select
            new de.tum.in.www1.artemis.domain.statistics.StatisticsEntry(
                post.creationDate, count(post.id)
                )
            from Post post left join post.exercise exercise
            where post.creationDate >= :#{#startDate} and post.creationDate <= :#{#endDate} and exercise.id = :#{#exerciseId}
            group by post.creationDate
            order by post.creationDate asc
            """)
    List<StatisticsEntry> getQuestionsAskedForExercise(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate, @Param("exerciseId") Long exerciseId);

    @Query("""
            select count(post)
            from Post post left join post.exercise exercise
            where exercise.id = :#{#exerciseId}
            """)
    long getNumberOfQuestionsAskedForExercise(@Param("exerciseId") Long exerciseId);

    @Query("""
            select count(distinct post.id)
            from Post post left join post.exercise exercise left join post.answers answers
            where exercise.id = :#{#exerciseId} and answers.resolvesPost = true
            """)
    long getNumberOfQuestionsAnsweredForExercise(@Param("exerciseId") Long exerciseId);

    @Query("""
            select
            new de.tum.in.www1.artemis.domain.statistics.StatisticsEntry(
                answer.creationDate, count(answer.id)
                )
            from AnswerPost answer left join answer.post post left join post.lecture lectures left join post.exercise exercises
            where answer.creationDate >= :#{#startDate} and answer.creationDate <= :#{#endDate} and (lectures.course.id = :#{#courseId} or exercises.course.id = :#{#courseId})
            group by answer.creationDate
            order by answer.creationDate asc
            """)
    List<StatisticsEntry> getQuestionsAnsweredForCourse(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate, @Param("courseId") Long courseId);

    @Query("""
            select
            new de.tum.in.www1.artemis.domain.statistics.StatisticsEntry(
                answer.creationDate, count(answer.id)
                )
            from AnswerPost answer left join answer.post post left join post.exercise exercise
            where answer.creationDate >= :#{#startDate} and answer.creationDate <= :#{#endDate} and exercise.course.id = :#{#exerciseId}
            group by answer.creationDate
            order by answer.creationDate asc
            """)
    List<StatisticsEntry> getQuestionsAnsweredForExercise(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate,
            @Param("exerciseId") Long exerciseId);

    @Query("""
            select e.id
            from Exercise e
            where e.course.id = :courseId
            """)
    List<Long> findExerciseIdsByCourseId(@Param("courseId") Long courseId);

    @Query("""
            select e
            from Exercise e
            where e.course.id = :courseId
            """)
    Set<Exercise> findExercisesByCourseId(@Param("courseId") Long courseId);

    @Query("""
            select
            new de.tum.in.www1.artemis.domain.statistics.CourseStatisticsAverageScore(
                p.exercise.id,
                p.exercise.title,
                p.exercise.releaseDate,
                avg(p.lastScore)
                )
            from ParticipantScore p
            where p.exercise IN :exercises
            group by p.exercise.id
            """)
    List<CourseStatisticsAverageScore> findAvgPointsForExercises(@Param("exercises") Set<Exercise> exercises);

    /**
     * Gets the number of entries for the specific graphType and the span. First we distribute into the different types of graphs.
     * After that, we distinguish between the views in which this graph can be shown and call the corresponding method
     *
     * @param span the spanType for which the call is executed
     * @param startDate The startDate of which the data should be fetched
     * @param endDate The endDate of which the data should be fetched
     * @param graphType the type of graph the data should be fetched for (see GraphType.java)
     * @param view the view in which the data will be displayed (Artemis, Course, Exercise)
     * @param entityId the entityId which is null for a user statistics call and contains the Id for the other statistics pages
     * @return the return value of the processed database call which returns a list of entries
     */
    default List<StatisticsEntry> getNumberOfEntriesPerTimeSlot(SpanType span, ZonedDateTime startDate, ZonedDateTime endDate, GraphType graphType, StatisticsView view,
            Long entityId) {
        var exerciseIds = view == StatisticsView.COURSE ? findExerciseIdsByCourseId(entityId) : null;
        switch (graphType) {
            case SUBMISSIONS -> {
                switch (view) {
                    case ARTEMIS -> {
                        return getTotalSubmissions(startDate, endDate);
                    }
                    case COURSE -> {
                        return getTotalSubmissionsForCourse(startDate, endDate, exerciseIds);
                    }
                    case EXERCISE -> {
                        return getTotalSubmissionsForExercise(startDate, endDate, entityId);
                    }
                    default -> throw new UnsupportedOperationException("Unsupported view: " + view);
                }
            }
            case ACTIVE_USERS -> {
                List<StatisticsEntry> result;
                switch (view) {
                    case ARTEMIS -> result = getActiveUsers(startDate, endDate);
                    case COURSE -> result = getActiveUsersForCourse(startDate, endDate, exerciseIds);
                    case EXERCISE -> result = getActiveUsersForExercise(startDate, endDate, entityId);
                    default -> throw new UnsupportedOperationException("Unsupported view: " + view);
                }
                return filterDuplicatedUsers(span, result, startDate, graphType);
            }
            case LOGGED_IN_USERS -> {
                Instant startDateInstant = startDate.toInstant();
                Instant endDateInstant = endDate.toInstant();
                List<StatisticsEntry> result = getLoggedInUsers(startDateInstant, endDateInstant);
                return filterDuplicatedUsers(span, result, startDate, graphType);
            }
            case RELEASED_EXERCISES -> {
                switch (view) {
                    case ARTEMIS -> {
                        return getReleasedExercises(startDate, endDate);
                    }
                    case COURSE -> {
                        return getReleasedExercisesForCourse(startDate, endDate, exerciseIds);
                    }
                    default -> throw new UnsupportedOperationException("Unsupported view: " + view);
                }
            }
            case EXERCISES_DUE -> {
                switch (view) {
                    case ARTEMIS -> {
                        return getExercisesDue(startDate, endDate);
                    }
                    case COURSE -> {
                        return getExercisesDueForCourse(startDate, endDate, exerciseIds);
                    }
                    default -> throw new UnsupportedOperationException("Unsupported view: " + view);
                }
            }
            case CONDUCTED_EXAMS -> {
                switch (view) {
                    case ARTEMIS -> {
                        return getConductedExams(startDate, endDate);
                    }
                    case COURSE -> {
                        return getConductedExamsForCourse(startDate, endDate, entityId);
                    }
                    default -> throw new UnsupportedOperationException("Unsupported view: " + view);
                }
            }
            case EXAM_PARTICIPATIONS -> {
                switch (view) {
                    case ARTEMIS -> {
                        return getExamParticipations(startDate, endDate);
                    }
                    case COURSE -> {
                        return getExamParticipationsForCourse(startDate, endDate, entityId);
                    }
                    default -> throw new UnsupportedOperationException("Unsupported view: " + view);
                }
            }
            case EXAM_REGISTRATIONS -> {
                switch (view) {
                    case ARTEMIS -> {
                        return getExamRegistrations(startDate, endDate);
                    }
                    case COURSE -> {
                        return getExamRegistrationsForCourse(startDate, endDate, entityId);
                    }
                    default -> throw new UnsupportedOperationException("Unsupported view: " + view);
                }
            }
            case ACTIVE_TUTORS -> {
                List<StatisticsEntry> result;
                switch (view) {
                    case ARTEMIS -> result = getActiveTutors(startDate, endDate);
                    case COURSE -> result = getActiveTutorsForCourse(startDate, endDate, exerciseIds);
                    case EXERCISE -> result = getActiveTutorsForExercise(startDate, endDate, entityId);
                    default -> throw new UnsupportedOperationException("Unsupported view: " + view);
                }
                return filterDuplicatedUsers(span, result, startDate, graphType);
            }
            case CREATED_RESULTS -> {
                switch (view) {
                    case ARTEMIS -> {
                        return getCreatedResults(startDate, endDate);
                    }
                    case COURSE -> {
                        return getCreatedResultsForCourse(startDate, endDate, exerciseIds);
                    }
                    case EXERCISE -> {
                        return getCreatedResultsForExercise(startDate, endDate, entityId);
                    }
                    default -> throw new UnsupportedOperationException("Unsupported view: " + view);
                }
            }
            case CREATED_FEEDBACKS -> {
                switch (view) {
                    case ARTEMIS -> {
                        return getResultFeedbacks(startDate, endDate);
                    }
                    case COURSE -> {
                        return getResultFeedbacksForCourse(startDate, endDate, exerciseIds);
                    }
                    case EXERCISE -> {
                        return getResultFeedbacksForExercise(startDate, endDate, entityId);
                    }
                    default -> throw new UnsupportedOperationException("Unsupported view: " + view);
                }
            }
            case POSTS -> {
                switch (view) {
                    case COURSE -> {
                        return getQuestionsAskedForCourse(startDate, endDate, entityId);
                    }
                    case EXERCISE -> {
                        return getQuestionsAskedForExercise(startDate, endDate, entityId);
                    }
                    default -> throw new UnsupportedOperationException("Unsupported view: " + view);
                }
            }
            case ANSWERED_POSTS -> {
                switch (view) {
                    case COURSE -> {
                        return getQuestionsAnsweredForCourse(startDate, endDate, entityId);
                    }
                    case EXERCISE -> {
                        return getQuestionsAnsweredForExercise(startDate, endDate, entityId);
                    }
                    default -> throw new UnsupportedOperationException("Unsupported view: " + view);
                }
            }
            default -> {
                return new ArrayList<>();
            }
        }
    }

    /**
     * This method handles the duplicity of usernames. It gets a List<StatisticsData> with set day values and set username values.
     * It then filters out all duplicated user entries per timeslot (depending on spanType) and return a list of entries
     * containing the amount of distinct users per timeslot
     *
     * @param span DAY,WEEK,MONTH or YEAR
     * @param result the result given by the Repository call
     * @param startDate the startDate of the period
     * @param graphType the graphType for which the List should be converted
     * @return A List<StatisticsData> with only distinct users per timeslot
     */
    private List<StatisticsEntry> filterDuplicatedUsers(SpanType span, List<StatisticsEntry> result, ZonedDateTime startDate, GraphType graphType) {
        Map<Object, List<String>> users = new HashMap<>();
        for (StatisticsEntry listElement : result) {
            Object index;
            ZonedDateTime date;
            if (graphType == GraphType.LOGGED_IN_USERS) {
                Instant instant = (Instant) listElement.getDay();
                date = instant.atZone(startDate.getZone());
            }
            else {
                date = (ZonedDateTime) listElement.getDay();
            }
            if (span == SpanType.DAY) {
                index = date.getHour();
            }
            else if (span == SpanType.WEEK || span == SpanType.MONTH) {
                index = date.withHour(0).withMinute(0).withSecond(0).withNano(0);
            }
            else if (span == SpanType.QUARTER) {
                index = getWeekOfDate(date);
            }
            else {
                index = date.getMonth();
            }
            String username = listElement.getUsername();
            List<String> usersInSameSlot = users.get(index);
            // if this index is not yet existing in users
            if (usersInSameSlot == null) {
                usersInSameSlot = new ArrayList<>();
                usersInSameSlot.add(username);
                users.put(index, usersInSameSlot);
            }   // if the value of the map for this index does not contain this username
            else if (!usersInSameSlot.contains(username)) {
                usersInSameSlot.add(username);
            }
        }
        return mergeUsersPerTimeslotIntoList(users, span, startDate);
    }

    /**
     * Helper class for the filterDuplicatedUsers method, which takes the users in the same timeslot as well as some parameters needed
     * for calculation to convert these into a List<StatisticsData> which is then returned
     *
     * @param users a Map where a date gets mapped onto a list of users with entries on this date
     * @param span the spanType for which we created the users List
     * @param startDate the startDate which we need for mapping into timeslots
     * @return A List<StatisticsData> with no duplicated user per timeslot
     */
    private List<StatisticsEntry> mergeUsersPerTimeslotIntoList(Map<Object, List<String>> users, SpanType span, ZonedDateTime startDate) {
        List<StatisticsEntry> returnList = new ArrayList<>();
        users.forEach((timeslot, userList) -> {
            ZonedDateTime start;
            if (span == SpanType.DAY) {
                start = startDate.withHour((Integer) timeslot);
            }
            else if (span == SpanType.WEEK || span == SpanType.MONTH) {
                start = (ZonedDateTime) timeslot;
            }
            else if (span == SpanType.QUARTER) {
                int year = (Integer) timeslot < getWeekOfDate(startDate) ? startDate.getYear() + 1 : startDate.getYear();
                ZonedDateTime firstDateOfYear = ZonedDateTime.of(year, 1, 1, 0, 0, 0, 0, startDate.getZone());
                start = getWeekOfDate(firstDateOfYear) == 1 ? firstDateOfYear.plusWeeks(((Integer) timeslot) - 1) : firstDateOfYear.plusWeeks((Integer) timeslot);
            }
            else {
                start = startDate.withMonth(((Month) timeslot).getValue());
            }
            StatisticsEntry listElement = new StatisticsEntry(start, userList.size());
            returnList.add(listElement);
        });
        return returnList;
    }

    default Integer getWeekOfDate(ZonedDateTime date) {
        LocalDate localDate = date.toLocalDate();
        TemporalField weekOfYear = WeekFields.of(DayOfWeek.MONDAY, 4).weekOfWeekBasedYear();
        return localDate.get(weekOfYear);
    }

    /**
     * Gets a List<StatisticsData> each containing a date and an amount of entries. We take the amount of entries and
     * map it into a the results array based on the date of the entry. This method handles the spanType DAY
     *
     * @param outcome A List<StatisticsData>, each StatisticsData containing a date and the amount of entries for one timeslot
     * @param result the array in which the converted outcome should be inserted
     * @return an array, containing the values for each bar in the graph
     */
    default Integer[] mergeResultsIntoArrayForDay(List<StatisticsEntry> outcome, Integer[] result) {
        for (StatisticsEntry entry : outcome) {
            int hour = ((ZonedDateTime) entry.getDay()).getHour();
            int amount = (int) entry.getAmount();
            result[hour] += amount;
        }
        return result;
    }

    /**
     * Gets a List<StatisticsData> each containing a date and an amount of entries. We take the amount of entries and
     * map it into a the results array based on the date of the entry. This method handles the spanType WEEK
     *
     * @param outcome A List<StatisticsData>, each StatisticsData containing a date and the amount of entries for one timeslot
     * @param result the array in which the converted outcome should be inserted
     * @param startDate the startDate of the result array
     * @return an array, containing the values for each bar in the graph
     */
    default Integer[] mergeResultsIntoArrayForWeek(List<StatisticsEntry> outcome, Integer[] result, ZonedDateTime startDate) {
        for (StatisticsEntry entry : outcome) {
            ZonedDateTime date = (ZonedDateTime) entry.getDay();
            int amount = (int) entry.getAmount();
            int dayDifference = (int) ChronoUnit.DAYS.between(startDate, date);
            result[dayDifference] += amount;
        }
        return result;
    }

    /**
     * Gets a List<StatisticsData> each containing a date and an amount of entries. We take the amount of entries and
     * map it into a the results array based on the date of the entry. This method handles the spanType MONTH
     *
     * @param outcome A List<StatisticsData>, each StatisticsData containing a date and the amount of entries for one timeslot
     * @param result the array in which the converted outcome should be inserted
     * @param startDate the startDate of the result array
     * @return an array, containing the values for each bar in the graph
     */
    default Integer[] mergeResultsIntoArrayForMonth(List<StatisticsEntry> outcome, Integer[] result, ZonedDateTime startDate) {
        for (StatisticsEntry map : outcome) {
            ZonedDateTime date = (ZonedDateTime) map.getDay();
            int amount = (int) map.getAmount();
            int dayDifference = (int) ChronoUnit.DAYS.between(startDate, date);
            result[dayDifference] += amount;
        }
        return result;
    }

    /**
     * Gets a List<StatisticsData> each containing a date and an amount of entries. We take the amount of entries and
     * map it into a the results array based on the date of the entry. This method handles the spanType Quarter
     *
     * @param outcome A List<StatisticsData>, each StatisticsData containing a date and the amount of entries for one timeslot
     * @param result the array in which the converted outcome should be inserted
     * @param startDate the startDate of the result array
     * @return an array, containing the values for each bar in the graph
     */
    default Integer[] mergeResultsIntoArrayForQuarter(List<StatisticsEntry> outcome, Integer[] result, ZonedDateTime startDate) {
        for (StatisticsEntry map : outcome) {
            ZonedDateTime date = (ZonedDateTime) map.getDay();
            int amount = (int) map.getAmount();
            int dateWeek = getWeekOfDate(date);
            int startDateWeek = getWeekOfDate(startDate);
            int weeksDifference;
            // if the graph contains two different years
            weeksDifference = dateWeek < startDateWeek ? dateWeek + 53 - startDateWeek : dateWeek - startDateWeek;
            result[weeksDifference] += amount;

        }
        return result;
    }

    /**
     * Gets a List<StatisticsData> each containing a date and an amount of entries. We take the amount of entries and
     * map it into a the results array based on the date of the entry. This method handles the spanType YEAR
     *
     * @param outcome A List<StatisticsData>, each StatisticsData containing a date and the amount of entries for one timeslot
     * @param result the array in which the converted outcome should be inserted
     * @param startDate the startDate of the result array
     * @return an array, containing the values for each bar in the graph
     */
    default Integer[] mergeResultsIntoArrayForYear(List<StatisticsEntry> outcome, Integer[] result, ZonedDateTime startDate) {
        for (StatisticsEntry map : outcome) {
            ZonedDateTime date = (ZonedDateTime) map.getDay();
            int amount = (int) map.getAmount();
            int monthOfDate = date.getMonth().getValue();
            int monthOfStartDate = startDate.getMonth().getValue();
            if (monthOfDate >= monthOfStartDate) {
                // Date is in same year as startDate
                result[monthOfDate - monthOfStartDate] += amount;
            }
            else {
                // Date is in different year as startDate
                result[12 - monthOfStartDate + monthOfDate] += amount;
            }
        }
        return result;
    }

}
