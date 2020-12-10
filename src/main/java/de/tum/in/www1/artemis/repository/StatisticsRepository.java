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
            select count(distinct u.login)
            from User u, PersistentAuditEvent p
            where u.login like p.principal and p.auditEventType = 'AUTHENTICATION_SUCCESS' and u.login not like '%test%' and p.auditEventDate >= :#{#span}
            """)
    Integer getLoggedInUsers(@Param("span") Instant span);

    @Query("""
            select count(distinct u.login)
            from User u, Submission s, StudentParticipation p
            where s.participation.id = p.id and p.student.id = u.id and s.submissionDate >= :#{#span} and u.login not like '%test%'
            """)
    Integer getActiveUsers(@Param("span") ZonedDateTime span);

    @Query("""
            select count(e.id)
            from Exercise e
            where e.releaseDate >= :#{#span} and e.releaseDate <= :#{#now}
            """)
    Integer getReleasedExercises(@Param("span") ZonedDateTime span, @Param("now") ZonedDateTime now);

    @Query("""
            select count(e.id)
            from Exercise e
            where e.dueDate >= :#{#span} and e.dueDate <= :#{#now}
            """)
    Integer getExerciseDeadlines(@Param("span") ZonedDateTime span, @Param("now") ZonedDateTime now);

    @Query("""
            select count(e.id)
            from Exam e
            where e.endDate >= :#{#span} and e.endDate <= :#{#now}
            """)
    Integer getConductedExams(@Param("span") ZonedDateTime span, @Param("now") ZonedDateTime now);

    @Query("""
            select count(se.id)
            from StudentExam se, Exam e
            where se.submitted = true and se.exam = e and e.endDate >= :#{#span}
            """)
    Integer getExamParticipations(@Param("span") ZonedDateTime span);

    @Query("""
            select sum(size(e.registeredUsers))
            from Exam e
            where e.endDate >= :#{#span} and e.endDate <= :#{#now}
            """)
    Integer getExamRegistrations(@Param("span") ZonedDateTime span, @Param("now") ZonedDateTime now);

    @Query("""
            select count(distinct r.assessor.id)
            from Result r
            where (r.assessmentType = 'MANUAL' or r.assessmentType = 'SEMI-AUTOMATIC') and r.completionDate >= :#{#span}
            """)
    Integer getActiveTutors(@Param("span") ZonedDateTime span);

    @Query("""
            select count(r.id)
            from Result r
            where r.completionDate >= :#{#span}
            """)
    Integer getCreatedResults(@Param("span") ZonedDateTime span);

    @Query("""
            select sum(size(r.feedbacks))
            from Result r
            where r.completionDate >= :#{#span}
            """)
    Integer getResultFeedbacks(@Param("span") ZonedDateTime span);

    @Query("""
            select s.submissionDate as day, count(s.id) as amount
            from Submission s
            where s.submissionDate > :#{#startDate}
            group by s.submissionDate
            order by s.submissionDate asc
            """)
    List<Map<String, Object>> getTotalSubmissions(ZonedDateTime startDate);

    @Query("""
            select s.submissionDate as day, count(s.id) as amount
            from Submission s
            where s.submissionDate > :#{#startDate} and s.submissionDate < :#{#endDate}
            group by s.submissionDate
            order by s.submissionDate asc
            """)
    List<Map<String, Object>> getTotalSubmissionsYesterday(ZonedDateTime startDate, ZonedDateTime endDate);

}
