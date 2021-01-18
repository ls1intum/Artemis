package de.tum.in.www1.artemis.repository;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.User;

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
            select s.submissionDate as day, u.login as username
            from User u, Submission s, StudentParticipation p
            where s.participation.id = p.id and p.student.id = u.id and s.submissionDate >= :#{#startDate} and s.submissionDate <= :#{#endDate} and u.login not like '%test%'
            and (s.participation.exercise.exerciseGroup IS NOT NULL or exists (select c from Course c where s.participation.exercise.course.testCourse = false))
            order by s.submissionDate asc
            """)
    List<Map<String, Object>> getActiveUsers(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate);

    @Query("""
            select e.releaseDate as day, count(e.id) as amount
            from Exercise e
            where e.releaseDate >= :#{#startDate} and e.releaseDate <= :#{#endDate} and e.course.testCourse = false
            group by e.releaseDate
            order by e.releaseDate asc
            """)
    List<Map<String, Object>> getReleasedExercises(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate);

    @Query("""
            select e.dueDate as day, count(e.id) as amount
            from Exercise e
            where e.dueDate >= :#{#startDate} and e.dueDate <= :#{#endDate} and e.course.testCourse = false
            group by e.dueDate
            order by e.dueDate asc
            """)
    List<Map<String, Object>> getExercisesDue(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate);

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
            select e.endDate as day, count(se.id) as amount
            from StudentExam se, Exam e
            where se.submitted = true and se.exam = e and e.endDate >= :#{#startDate} and e.endDate <= :#{#endDate} and e.course.testCourse = false
            group by e.endDate
            order by e.endDate asc
            """)
    List<Map<String, Object>> getExamParticipations(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate);

    @Query("""
            select e.endDate as day, sum(size(e.registeredUsers)) as amount
            from Exam e
            where e.endDate >= :#{#startDate} and e.endDate <= :#{#endDate} and e.course.testCourse = false
            group by e.endDate
            order by e.endDate asc
            """)
    List<Map<String, Object>> getExamRegistrations(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate);

    @Query("""
            select r.completionDate as day, r.assessor.login as username
            from Result r
            where (r.assessmentType = 'MANUAL' or r.assessmentType = 'SEMI_AUTOMATIC') and r.completionDate >= :#{#startDate} and r.completionDate <= :#{#endDate} and r.assessor.login not like '%test%'
            and (r.participation.exercise.exerciseGroup IS NOT NULL or exists (select c from Course c where r.participation.exercise.course.testCourse = false))
            """)
    List<Map<String, Object>> getActiveTutors(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate);

    @Query("""
            select r.completionDate as day, count(r.id) as amount
            from Result r
            where r.completionDate >= :#{#startDate} and r.completionDate <= :#{#endDate} and (r.participation.exercise.exerciseGroup IS NOT NULL or exists (select c from Course c where r.participation.exercise.course.testCourse = false))
            group by r.completionDate
            order by r.completionDate
            """)
    List<Map<String, Object>> getCreatedResults(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate);

    @Query("""
            select r.completionDate as day, sum(size(r.feedbacks)) as amount
            from Result r
            where r.completionDate >= :#{#startDate} and r.completionDate <= :#{#endDate} and (r.participation.exercise.exerciseGroup IS NOT NULL or exists (select c from Course c where r.participation.exercise.course.testCourse = false))
            group by r.completionDate
            order by r.completionDate
            """)
    List<Map<String, Object>> getResultFeedbacks(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate);
}
