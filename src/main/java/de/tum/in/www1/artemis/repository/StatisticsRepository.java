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
                sq.creationDate, count(sq.id)
                )
            from StudentQuestion sq left join sq.lecture lectures left join sq.exercise exercises
            where sq.creationDate >= :#{#startDate} and sq.creationDate <= :#{#endDate} and (lectures.course.id = :#{#courseId} or exercises.course.id = :#{#courseId})
            group by sq.creationDate
            order by sq.creationDate asc
            """)
    List<StatisticsEntry> getQuestionsAskedForCourse(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate, @Param("courseId") Long courseId);

    @Query("""
            select
            new de.tum.in.www1.artemis.domain.statistics.StatisticsEntry(
                a.answerDate, count(a.id)
                )
            from StudentQuestionAnswer a left join a.question question left join question.lecture lectures left join question.exercise exercises
            where a.answerDate >= :#{#startDate} and a.answerDate <= :#{#endDate} and (lectures.course.id = :#{#courseId} or exercises.course.id = :#{#courseId})
            group by a.answerDate
            order by a.answerDate asc
            """)
    List<StatisticsEntry> getQuestionsAnsweredForCourse(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate, @Param("courseId") Long courseId);

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
                avg(p.lastScore)
                )
            from ParticipantScore p
            where p.exercise IN :exercises
            group by p.exercise.id
            """)
    List<CourseStatisticsAverageScore> findAvgPointsForExercises(@Param("exercises") Set<Exercise> exercises);

    /**
     * Gets the number of entries for the specific graphType and the span
     *
     * @param span the spanType for which the call is executed
     * @param startDate The startDate of which the data should be fetched
     * @param endDate The endDate of which the data should be fetched
     * @param graphType the type of graph the data should be fetched for (see GraphType.java)
     * @param courseId the courseId which is null for a user statistics call and contains the courseId for the course statistics
     * @return the return value of the processed database call which returns a list of entries
     */
    default List<StatisticsEntry> getNumberOfEntriesPerTimeSlot(SpanType span, ZonedDateTime startDate, ZonedDateTime endDate, GraphType graphType, Long courseId) {
        var exerciseIds = courseId != null ? findExerciseIdsByCourseId(courseId) : null;
        switch (graphType) {
            case SUBMISSIONS -> {
                return courseId == null ? getTotalSubmissions(startDate, endDate) : getTotalSubmissionsForCourse(startDate, endDate, exerciseIds);
            }
            case ACTIVE_USERS -> {
                List<StatisticsEntry> result = courseId == null ? getActiveUsers(startDate, endDate) : getActiveUsersForCourse(startDate, endDate, exerciseIds);
                return filterDuplicatedUsers(span, result, startDate, graphType);
            }
            case LOGGED_IN_USERS -> {
                Instant startDateInstant = startDate.toInstant();
                Instant endDateInstant = endDate.toInstant();
                List<StatisticsEntry> result = getLoggedInUsers(startDateInstant, endDateInstant);
                return filterDuplicatedUsers(span, result, startDate, graphType);
            }
            case RELEASED_EXERCISES -> {
                return courseId == null ? getReleasedExercises(startDate, endDate) : getReleasedExercisesForCourse(startDate, endDate, exerciseIds);
            }
            case EXERCISES_DUE -> {
                return courseId == null ? getExercisesDue(startDate, endDate) : getExercisesDueForCourse(startDate, endDate, exerciseIds);
            }
            case CONDUCTED_EXAMS -> {
                return courseId == null ? getConductedExams(startDate, endDate) : getConductedExamsForCourse(startDate, endDate, courseId);
            }
            case EXAM_PARTICIPATIONS -> {
                return courseId == null ? getExamParticipations(startDate, endDate) : getExamParticipationsForCourse(startDate, endDate, courseId);
            }
            case EXAM_REGISTRATIONS -> {
                return courseId == null ? getExamRegistrations(startDate, endDate) : getExamRegistrationsForCourse(startDate, endDate, courseId);
            }
            case ACTIVE_TUTORS -> {
                List<StatisticsEntry> result = courseId == null ? getActiveTutors(startDate, endDate) : getActiveTutorsForCourse(startDate, endDate, exerciseIds);
                return filterDuplicatedUsers(span, result, startDate, graphType);
            }
            case CREATED_RESULTS -> {
                return courseId == null ? getCreatedResults(startDate, endDate) : getCreatedResultsForCourse(startDate, endDate, exerciseIds);
            }
            case CREATED_FEEDBACKS -> {
                return courseId == null ? getResultFeedbacks(startDate, endDate) : getResultFeedbacksForCourse(startDate, endDate, exerciseIds);
            }
            case QUESTIONS_ASKED -> {
                return courseId != null ? getQuestionsAskedForCourse(startDate, endDate, courseId) : new ArrayList<>();
            }
            case QUESTIONS_ANSWERED -> {
                return courseId != null ? getQuestionsAnsweredForCourse(startDate, endDate, courseId) : new ArrayList<>();
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
        return setNumberOfUsers(users, span, startDate);
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
    private List<StatisticsEntry> setNumberOfUsers(Map<Object, List<String>> users, SpanType span, ZonedDateTime startDate) {
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
            result[(monthOfDate + monthOfStartDate + 2) % 12] += amount;
        }
        return result;
    }

}
