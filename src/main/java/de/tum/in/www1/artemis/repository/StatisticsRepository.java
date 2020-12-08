package de.tum.in.www1.artemis.repository;

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
            where s.submissionDate > :#{#startDate} and s.submissionDate <= :#{#endDate}
            group by s.submissionDate
            order by s.submissionDate asc
            """)
    List<Map<String, Object>> getTotalSubmissionsDay(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate);

    @Query("""
            select date(s.submissionDate) as day, count(s.id) as amount
            from Submission s
            where s.submissionDate > :#{#startDate} and s.submissionDate <= :#{#endDate}
            group by date(s.submissionDate)
            order by date(s.submissionDate) asc
            """)
    List<Map<String, Object>> getTotalSubmissions(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate);

    @Query("""
            select date(s.submissionDate) as day, count(distinct u.login) as amount
            from User u, Submission s, StudentParticipation p
            where s.participation.id = p.id and p.student.id = u.id and s.submissionDate >= :#{#startDate} and s.submissionDate <= :#{#endDate} and u.login not like '%test%'
            group by date(s.submissionDate)
            order by date(s.submissionDate) asc
            """)
    List<Map<String, Object>> getActiveUsers(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate);

    @Query("""
            select s.submissionDate as day, count(distinct u.login) as amount
            from User u, Submission s, StudentParticipation p
            where s.participation.id = p.id and p.student.id = u.id and s.submissionDate >= :#{#startDate} and s.submissionDate <= :#{#endDate} and u.login not like '%test%'
            group by s.submissionDate
            order by s.submissionDate asc
            """)
    List<Map<String, Object>> getActiveUsersDay(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate);
}
