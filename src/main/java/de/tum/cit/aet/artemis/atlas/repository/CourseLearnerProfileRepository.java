package de.tum.cit.aet.artemis.atlas.repository;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.profile.CourseLearnerProfile;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

@Conditional(AtlasEnabled.class)
@Lazy
@Repository
public interface CourseLearnerProfileRepository extends ArtemisJpaRepository<CourseLearnerProfile, Long> {

    @Transactional // ok because of delete
    @Modifying
    @Query("""
            DELETE FROM CourseLearnerProfile clp
            WHERE clp.course = :course AND clp.learnerProfile.user = :user
            """)
    void deleteByCourseAndUser(@Param("course") Course course, @Param("user") User user);

    @Transactional // ok because of delete
    @Modifying
    void deleteAllByCourse(Course course);

    @Query("""
            SELECT clp
            FROM CourseLearnerProfile clp
            LEFT JOIN FETCH clp.course
            WHERE clp.learnerProfile.user.login = :login
                        AND (clp.course.startDate <= :now OR clp.course.startDate IS NULL)
                        AND (clp.course.endDate >= :now OR clp.course.endDate IS NULL)
            """)
    Set<CourseLearnerProfile> findAllByLoginAndCourseActive(@Param("login") String login, @Param("now") ZonedDateTime now);

    @Query("""
                SELECT clp
                FROM CourseLearnerProfile clp
                LEFT JOIN FETCH clp.course
                WHERE clp.learnerProfile.user.login = :login AND clp.course = :course
            """)
    Optional<CourseLearnerProfile> findByLoginAndCourse(@Param("login") String login, @Param("course") Course course);

    @Query("""
            SELECT clp
            FROM CourseLearnerProfile clp
                LEFT JOIN FETCH clp.course
            WHERE clp.learnerProfile.user.login = :login
                AND clp.id = :courseLearnerProfileId
            """)
    Optional<CourseLearnerProfile> findByLoginAndId(@Param("login") String login, @Param("courseLearnerProfileId") long courseLearnerProfileId);
}
