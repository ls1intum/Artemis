package de.tum.cit.aet.artemis.atlas.repository;

import java.time.ZonedDateTime;
import java.util.List;
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
import de.tum.cit.aet.artemis.core.dto.export.LearnerProfileExportDTO;
import de.tum.cit.aet.artemis.core.dto.export.UserLearnerProfileExportDTO;
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

    @Transactional // ok because of delete
    @Modifying
    @Query("DELETE FROM CourseLearnerProfile clp WHERE clp.course.id = :courseId")
    void deleteAllByCourseId(@Param("courseId") long courseId);

    /**
     * Count the number of course learner profiles for a given course.
     *
     * @param courseId the id of the course
     * @return the number of course learner profiles in the course
     */
    @Query("SELECT COUNT(clp) FROM CourseLearnerProfile clp WHERE clp.course.id = :courseId")
    long countByCourseId(@Param("courseId") long courseId);

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

    /**
     * Find all course learner profiles for a course for export.
     *
     * @param courseId the id of the course
     * @return list of learner profile export DTOs
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.core.dto.export.LearnerProfileExportDTO(
                clp.learnerProfile.user.login, clp.aimForGradeOrBonus, clp.timeInvestment, clp.repetitionIntensity)
            FROM CourseLearnerProfile clp
            WHERE clp.course.id = :courseId
            """)
    List<LearnerProfileExportDTO> findAllForExportByCourseId(@Param("courseId") long courseId);

    /**
     * Find all course learner profiles for a user for GDPR data export.
     *
     * @param userId the id of the user
     * @return list of user learner profile export DTOs with course information
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.core.dto.export.UserLearnerProfileExportDTO(
                clp.course.id, clp.course.title, clp.aimForGradeOrBonus, clp.timeInvestment, clp.repetitionIntensity)
            FROM CourseLearnerProfile clp
            WHERE clp.learnerProfile.user.id = :userId
            ORDER BY clp.course.title
            """)
    List<UserLearnerProfileExportDTO> findAllForExportByUserId(@Param("userId") long userId);
}
