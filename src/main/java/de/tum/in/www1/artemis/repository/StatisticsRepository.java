package de.tum.in.www1.artemis.repository;

import java.time.*;
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

/**
 * Spring Data JPA repository for the user statistics
 */
@Repository
public interface StatisticsRepository extends JpaRepository<User, Long> {

    @Query("""
            select s.submissionDate as day, count(s.id) as amount
            from Submission s
            where s.submissionDate >= :#{#startDate} and s.submissionDate <= :#{#endDate} and (s.participation.exercise.exerciseGroup IS NOT NULL or exists (select c from Course c where s.participation.exercise.course.testCourse = false))
            group by s.submissionDate
            order by s.submissionDate asc
            """)
    List<Map<String, Object>> getTotalSubmissions(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate);

    @Query("""
            select s.submissionDate as day, count(s.id) as amount
            from Submission s
            where s.submissionDate >= :#{#startDate} and s.submissionDate <= :#{#endDate} and s.participation.exercise.id in :exerciseIds
            group by s.submissionDate
            order by s.submissionDate asc
            """)
    List<Map<String, Object>> getTotalSubmissionsForCourse(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate,
            @Param("exerciseIds") List<Long> exerciseIds);

    @Query("""
            select s.submissionDate as day, u.login as username
            from User u, Submission s, StudentParticipation p
            where s.participation.id = p.id and p.student.id = u.id and s.submissionDate >= :#{#startDate} and s.submissionDate <= :#{#endDate} and u.login not like '%test%'
            and (s.participation.exercise.exerciseGroup IS NOT NULL or exists (select c from Course c where s.participation.exercise.course.testCourse = false))
            order by s.submissionDate asc
            """)
    List<Map<String, Object>> getActiveUsers(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate);

    @Query("""
            select s.submissionDate as day, u.login as username
            from User u, Submission s, StudentParticipation p
            where s.participation.id = p.id and p.student.id = u.id and s.submissionDate >= :#{#startDate} and s.submissionDate <= :#{#endDate} and u.login not like '%test%'
            and p.exercise.id in :exerciseIds
            order by s.submissionDate asc
            """)
    List<Map<String, Object>> getActiveUsersForCourse(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate,
            @Param("exerciseIds") List<Long> exerciseIds);

    @Query("""
            select e.releaseDate as day, count(e.id) as amount
            from Exercise e
            where e.releaseDate >= :#{#startDate} and e.releaseDate <= :#{#endDate} and e.course.testCourse = false
            group by e.releaseDate
            order by e.releaseDate asc
            """)
    List<Map<String, Object>> getReleasedExercises(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate);

    @Query("""
            select e.releaseDate as day, count(e.id) as amount
            from Exercise e
            where e.releaseDate >= :#{#startDate} and e.releaseDate <= :#{#endDate} and e.id in :exerciseIds
            group by e.releaseDate
            order by e.releaseDate asc
            """)
    List<Map<String, Object>> getReleasedExercisesForCourse(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate,
            @Param("exerciseIds") List<Long> exerciseIds);

    @Query("""
            select e.dueDate as day, count(e.id) as amount
            from Exercise e
            where e.dueDate >= :#{#startDate} and e.dueDate <= :#{#endDate} and e.course.testCourse = false
            group by e.dueDate
            order by e.dueDate asc
            """)
    List<Map<String, Object>> getExercisesDue(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate);

    @Query("""
            select e.dueDate as day, count(e.id) as amount
            from Exercise e
            where e.dueDate >= :#{#startDate} and e.dueDate <= :#{#endDate} and e.id in :exerciseIds
            group by e.dueDate
            order by e.dueDate asc
            """)
    List<Map<String, Object>> getExercisesDueForCourse(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate,
            @Param("exerciseIds") List<Long> exerciseIds);

    @Query("""
            select p.auditEventDate as day, u.login as username
            from User u, PersistentAuditEvent p
            where u.login = p.principal and p.auditEventType = 'AUTHENTICATION_SUCCESS' and u.login not like '%test%' and p.auditEventDate >= :#{#startDate} and p.auditEventDate <= :#{#endDate}
            order by p.auditEventDate asc
            """)
    List<Map<String, Object>> getLoggedInUsers(@Param("startDate") Instant startDate, @Param("endDate") Instant endDate);

    @Query("""
            select e.endDate as day, count(e.id) as amount
            from Exam e
            where e.endDate >= :#{#startDate} and e.endDate <= :#{#endDate} and e.course.testCourse = false
            group by e.endDate
            order by e.endDate asc
            """)
    List<Map<String, Object>> getConductedExams(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate);

    @Query("""
            select e.endDate as day, count(e.id) as amount
            from Exam e
            where e.endDate >= :#{#startDate} and e.endDate <= :#{#endDate} and e.course.id = :#{#courseId}
            group by e.endDate
            order by e.endDate asc
            """)
    List<Map<String, Object>> getConductedExamsForCourse(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate, @Param("courseId") Long courseId);

    @Query("""
            select e.endDate as day, count(se.id) as amount
            from StudentExam se, Exam e
            where se.submitted = true and se.exam = e and e.endDate >= :#{#startDate} and e.endDate <= :#{#endDate} and e.course.testCourse = false
            group by e.endDate
            order by e.endDate asc
            """)
    List<Map<String, Object>> getExamParticipations(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate);

    @Query("""
            select e.endDate as day, count(se.id) as amount
            from StudentExam se, Exam e
            where se.submitted = true and se.exam = e and e.endDate >= :#{#startDate} and e.endDate <= :#{#endDate} and e.course.id = :#{#courseId}
            group by e.endDate
            order by e.endDate asc
            """)
    List<Map<String, Object>> getExamParticipationsForCourse(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate,
            @Param("courseId") Long courseId);

    @Query("""
            select e.endDate as day, sum(size(e.registeredUsers)) as amount
            from Exam e
            where e.endDate >= :#{#startDate} and e.endDate <= :#{#endDate} and e.course.testCourse = false
            group by e.endDate
            order by e.endDate asc
            """)
    List<Map<String, Object>> getExamRegistrations(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate);

    @Query("""
            select e.endDate as day, sum(size(e.registeredUsers)) as amount
            from Exam e
            where e.endDate >= :#{#startDate} and e.endDate <= :#{#endDate} and e.course.id = :#{#courseId}
            group by e.endDate
            order by e.endDate asc
            """)
    List<Map<String, Object>> getExamRegistrationsForCourse(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate, @Param("courseId") Long courseId);

    @Query("""
            select r.completionDate as day, r.assessor.login as username
            from Result r
            where (r.assessmentType = 'MANUAL' or r.assessmentType = 'SEMI_AUTOMATIC') and r.completionDate >= :#{#startDate} and r.completionDate <= :#{#endDate} and r.assessor.login not like '%test%'
            and (r.participation.exercise.exerciseGroup IS NOT NULL or exists (select c from Course c where r.participation.exercise.course.testCourse = false))
            """)
    List<Map<String, Object>> getActiveTutors(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate);

    @Query("""
            select r.completionDate as day, r.assessor.login as username
            from Result r
            where (r.assessmentType = 'MANUAL' or r.assessmentType = 'SEMI_AUTOMATIC') and r.completionDate >= :#{#startDate} and r.completionDate <= :#{#endDate} and r.assessor.login not like '%test%'
            and r.participation.exercise.id in :exerciseIds
            """)
    List<Map<String, Object>> getActiveTutorsForCourse(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate,
            @Param("exerciseIds") List<Long> exerciseIds);

    @Query("""
            select r.completionDate as day, count(r.id) as amount
            from Result r
            where r.completionDate >= :#{#startDate} and r.completionDate <= :#{#endDate} and (r.participation.exercise.exerciseGroup IS NOT NULL or exists (select c from Course c where r.participation.exercise.course.testCourse = false))
            group by r.completionDate
            order by r.completionDate
            """)
    List<Map<String, Object>> getCreatedResults(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate);

    @Query("""
            select r.completionDate as day, count(r.id) as amount
            from Result r
            where r.completionDate >= :#{#startDate} and r.completionDate <= :#{#endDate} and r.participation.exercise.id in :exerciseIds
            group by r.completionDate
            order by r.completionDate
            """)
    List<Map<String, Object>> getCreatedResultsForCourse(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate,
            @Param("exerciseIds") List<Long> exerciseIds);

    @Query("""
            select r.completionDate as day, sum(size(r.feedbacks)) as amount
            from Result r
            where r.completionDate >= :#{#startDate} and r.completionDate <= :#{#endDate} and (r.participation.exercise.exerciseGroup IS NOT NULL or exists (select c from Course c where r.participation.exercise.course.testCourse = false))
            group by r.completionDate
            order by r.completionDate
            """)
    List<Map<String, Object>> getResultFeedbacks(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate);

    @Query("""
            select r.completionDate as day, sum(size(r.feedbacks)) as amount
            from Result r
            where r.completionDate >= :#{#startDate} and r.completionDate <= :#{#endDate} and r.participation.exercise.id in :exerciseIds
            group by r.completionDate
            order by r.completionDate
            """)
    List<Map<String, Object>> getResultFeedbacksForCourse(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate,
            @Param("exerciseIds") List<Long> exerciseIds);

    @Query("""
            select sq.creationDate as day, count(sq.id) as amount
            from StudentQuestion sq left join sq.lecture lectures left join sq.exercise exercises
            where sq.creationDate >= :#{#startDate} and sq.creationDate <= :#{#endDate} and (lectures.course.id = :#{#courseId} or exercises.course.id = :#{#courseId})
            group by sq.creationDate
            order by sq.creationDate asc
            """)
    List<Map<String, Object>> getQuestionsAskedForCourse(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate, @Param("courseId") Long courseId);

    @Query("""
            select a.answerDate as day, count(a.id) as amount
            from StudentQuestionAnswer a left join a.question question left join question.lecture lectures left join question.exercise exercises
            where a.answerDate >= :#{#startDate} and a.answerDate <= :#{#endDate} and (lectures.course.id = :#{#courseId} or exercises.course.id = :#{#courseId})
            group by a.answerDate
            order by a.answerDate asc
            """)
    List<Map<String, Object>> getQuestionsAnsweredForCourse(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate, @Param("courseId") Long courseId);

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
     * Handles the Repository calls depending on the graphType
     *
     * @param span the spanType for which the call is executed
     * @param startDate The startDate of which the data should be fetched
     * @param endDate The endDate of which the data should be fetched
     * @param graphType the type of graph the data should be fetched for (see GraphType.java)
     * @param courseId the courseId which is null for a user statistics call and contains the courseId for the course statistics
     * @return the return value of the database call
     */
    default List<Map<String, Object>> getDataFromDatabase(SpanType span, ZonedDateTime startDate, ZonedDateTime endDate, GraphType graphType, Long courseId) {
        var exerciseIds = courseId != null ? findExerciseIdsByCourseId(courseId) : null;
        switch (graphType) {
            case SUBMISSIONS -> {
                return courseId == null ? getTotalSubmissions(startDate, endDate) : getTotalSubmissionsForCourse(startDate, endDate, exerciseIds);
            }
            case ACTIVE_USERS -> {
                List<Map<String, Object>> result = courseId == null ? getActiveUsers(startDate, endDate) : getActiveUsersForCourse(startDate, endDate, exerciseIds);
                return convertMapList(span, result, startDate, graphType);
            }
            case LOGGED_IN_USERS -> {
                Instant startDateInstant = startDate.toInstant();
                Instant endDateInstant = endDate.toInstant();
                List<Map<String, Object>> result = getLoggedInUsers(startDateInstant, endDateInstant);
                return convertMapList(span, result, startDate, graphType);
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
                List<Map<String, Object>> result = courseId == null ? getActiveTutors(startDate, endDate) : getActiveTutorsForCourse(startDate, endDate, exerciseIds);
                return convertMapList(span, result, startDate, graphType);
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
     * This method handles the duplicity of usernames. It gets a List<Map<String, Object>> analogue to previous methods, but instead of numbers in an amount key,
     * it contains a username key with the actual username as value. It then handles all the usernames and returns a List<Map<String, Object>>, but now with the the key "amount"
     * and value the number of users in this interval
     *
     * @param span DAY,WEEK,MONTH or YEAR
     * @param result the result given by the Repository call
     * @param startDate the startDate of the period
     * @param graphType the graphType for which the List should be converted
     * @return A List<Map<String, Object>> analogue to other database calls
     */
    private List<Map<String, Object>> convertMapList(SpanType span, List<Map<String, Object>> result, ZonedDateTime startDate, GraphType graphType) {
        Map<Object, List<String>> users = new HashMap<>();
        for (Map<String, Object> listElement : result) {
            Object index;
            ZonedDateTime date;
            if (graphType == GraphType.LOGGED_IN_USERS) {
                Instant instant = (Instant) listElement.get("day");
                date = instant.atZone(startDate.getZone());
            }
            else {
                date = (ZonedDateTime) listElement.get("day");
            }
            if (span == SpanType.DAY) {
                index = date.getHour();
            }
            else if (span == SpanType.WEEK || span == SpanType.MONTH) {
                index = date.getDayOfMonth();
            }
            else if (span == SpanType.QUARTER) {
                index = getWeekOfDate(date);
            }
            else {
                index = date.getMonth();
            }
            String username = listElement.get("username").toString();
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
        return fillMapList(users, span, startDate);
    }

    /**
     * Helper class for the ConvertMapList method, which takes the users in the same timeslot as well as some attributed needed
     * for calculation to convert these into a Map List which is then returned
     *
     * @param users the List of Maps each containing the date and a list of users
     * @param span the spanType for which we created the users List
     * @param startDate the startDate which we need for mapping into timeslots
     * @return A List<Map<String, Object>> with no duplicated user per timeslot
     */
    private List<Map<String, Object>> fillMapList(Map<Object, List<String>> users, SpanType span, ZonedDateTime startDate) {
        List<Map<String, Object>> returnList = new ArrayList<>();
        users.forEach((k, v) -> {
            Object start;
            if (span == SpanType.DAY) {
                start = startDate.withHour((Integer) k);
            }
            else if (span == SpanType.WEEK || span == SpanType.MONTH) {
                start = startDate.withDayOfMonth((Integer) k);
            }
            else if (span == SpanType.QUARTER) {
                int year = (Integer) k < getWeekOfDate(startDate) ? startDate.getYear() + 1 : startDate.getYear();
                ZonedDateTime firstDateOfYear = ZonedDateTime.of(year, 1, 1, 0, 0, 0, 0, startDate.getZone());
                start = getWeekOfDate(firstDateOfYear) == 1 ? firstDateOfYear.plusWeeks(((Integer) k) - 1) : firstDateOfYear.plusWeeks((Integer) k);
            }
            else {
                start = startDate.withMonth(getMonthIndex((Month) k));
            }
            Map<String, Object> listElement = new HashMap<>();
            listElement.put("day", start);
            listElement.put("amount", (long) v.size());
            returnList.add(listElement);
        });
        return returnList;
    }

    private Integer getMonthIndex(Month month) {
        return switch (month) {
            case JANUARY -> 1;
            case FEBRUARY -> 2;
            case MARCH -> 3;
            case APRIL -> 4;
            case MAY -> 5;
            case JUNE -> 6;
            case JULY -> 7;
            case AUGUST -> 8;
            case SEPTEMBER -> 9;
            case OCTOBER -> 10;
            case NOVEMBER -> 11;
            case DECEMBER -> 12;
        };
    }

    default Integer getWeekOfDate(ZonedDateTime date) {
        LocalDate localDate = date.toLocalDate();
        TemporalField woy = WeekFields.of(DayOfWeek.MONDAY, 4).weekOfWeekBasedYear();
        return localDate.get(woy);
    }

    /**
     * Gets a list of maps, each map describing an entry in the database. The map has the two keys "day" and "amount",
     * which map to the date and the amount of the findings. This method handles the spanType DAY
     *
     * @param outcome A List<Map<String, Object>>, containing the content which should be refactored into an array
     * @param result the array in which the converted outcome should be inserted
     * @param endDate the endDate
     * @return an array, containing the values for each bar in the graph
     */
    default Integer[] createResultArrayForDay(List<Map<String, Object>> outcome, Integer[] result, ZonedDateTime endDate) {
        for (Map<String, Object> map : outcome) {
            int hour = ((ZonedDateTime) map.get("day")).getHour();
            Integer amount = map.get("amount") != null ? ((Long) map.get("amount")).intValue() : 0;
            for (int i = 0; i < 24; i++) {
                if (hour == endDate.minusHours(i).getHour()) {
                    result[endDate.getHour() - i] += amount;
                }
            }
        }
        return result;
    }

    /**
     * Gets a list of maps, each map describing an entry in the database. The map has the two keys "day" and "amount",
     * which map to the date and the amount of the findings. This method handles the spanType WEEK
     *
     * @param outcome A List<Map<String, Object>>, containing the content which should be refactored into an array
     * @param result the array in which the converted outcome should be inserted
     * @param endDate the endDate
     * @return an array, containing the values for each bar in the graph
     */
    default Integer[] createResultArrayForWeek(List<Map<String, Object>> outcome, Integer[] result, ZonedDateTime endDate) {
        for (Map<String, Object> map : outcome) {
            ZonedDateTime date = (ZonedDateTime) map.get("day");
            Integer amount = map.get("amount") != null ? ((Long) map.get("amount")).intValue() : 0;
            for (int i = 0; i < 7; i++) {
                if (date.getDayOfMonth() == endDate.minusDays(i).getDayOfMonth()) {
                    result[6 - i] += amount;
                }
            }
        }
        return result;
    }

    /**
     * Gets a list of maps, each map describing an entry in the database. The map has the two keys "day" and "amount",
     * which map to the date and the amount of the findings. This method handles the spanType MONTH
     *
     * @param outcome A List<Map<String, Object>>, containing the content which should be refactored into an array
     * @param result the array in which the converted outcome should be inserted
     * @param endDate the endDate
     * @return an array, containing the values for each bar in the graph
     */
    default Integer[] createResultArrayForMonth(List<Map<String, Object>> outcome, Integer[] result, ZonedDateTime endDate) {
        for (Map<String, Object> map : outcome) {
            ZonedDateTime date = (ZonedDateTime) map.get("day");
            Integer amount = map.get("amount") != null ? ((Long) map.get("amount")).intValue() : 0;
            for (int i = 0; i < result.length; i++) {
                if (date.getDayOfMonth() == endDate.minusDays(i).getDayOfMonth()) {
                    result[result.length - 1 - i] += amount;
                }
            }
        }
        return result;
    }

    /**
     * Gets a list of maps, each map describing an entry in the database. The map has the two keys "day" and "amount",
     * which map to the date and the amount of the findings. This method handles the spanType WEEKS_ORDERED
     *
     * @param outcome A List<Map<String, Object>>, containing the content which should be refactored into an array
     * @param result the array in which the converted outcome should be inserted
     * @param endDate the endDate
     * @return an array, containing the values for each bar in the graph
     */
    default Integer[] createResultArrayForQuarter(List<Map<String, Object>> outcome, Integer[] result, ZonedDateTime endDate) {
        int week;
        for (Map<String, Object> map : outcome) {
            ZonedDateTime date = (ZonedDateTime) map.get("day");
            Integer amount = map.get("amount") != null ? ((Long) map.get("amount")).intValue() : 0;
            week = getWeekOfDate(date);
            for (int i = 0; i < result.length; i++) {
                if (week == getWeekOfDate(endDate.minusWeeks(i))) {
                    result[result.length - 1 - i] += amount;
                }
            }
        }
        return result;
    }

    /**
     * Gets a list of maps, each map describing an entry in the database. The map has the two keys "day" and "amount",
     * which map to the date and the amount of the findings. This method handles the spanType YEAR
     *
     * @param outcome A List<Map<String, Object>>, containing the content which should be refactored into an array
     * @param result the array in which the converted outcome should be inserted
     * @param endDate the endDate
     * @return an array, containing the values for each bar in the graph
     */
    default Integer[] createResultArrayForYear(List<Map<String, Object>> outcome, Integer[] result, ZonedDateTime endDate) {
        for (Map<String, Object> map : outcome) {
            ZonedDateTime date = (ZonedDateTime) map.get("day");
            Integer amount = map.get("amount") != null ? ((Long) map.get("amount")).intValue() : 0;
            for (int i = 0; i < 12; i++) {
                if (date.getMonth() == endDate.minusMonths(i).getMonth()) {
                    result[11 - i] += amount;
                }
            }
        }
        return result;
    }

}
