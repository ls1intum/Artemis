package de.tum.cit.aet.artemis.atlas.repository;

import java.util.Collection;
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
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyProgress;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.export.CompetencyProgressExportDTO;
import de.tum.cit.aet.artemis.core.dto.export.UserCompetencyProgressExportDTO;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

@Conditional(AtlasEnabled.class)
@Lazy
@Repository
public interface CompetencyProgressRepository extends ArtemisJpaRepository<CompetencyProgress, Long> {

    @Transactional // ok because of delete
    @Modifying
    @Query("""
            DELETE FROM CompetencyProgress cp
            WHERE cp.competency.id = :competencyId
            """)
    void deleteAllByCompetencyId(@Param("competencyId") long competencyId);

    List<CompetencyProgress> findAllByCompetencyId(long competencyId);

    @Query("""
            SELECT cp
            FROM CompetencyProgress cp
            WHERE cp.competency.id = :competencyId
                AND cp.user.id = :userId
            """)
    Optional<CompetencyProgress> findByCompetencyIdAndUserId(@Param("competencyId") long competencyId, @Param("userId") long userId);

    @Query("""
            SELECT cp
            FROM CompetencyProgress cp
                LEFT JOIN cp.competency
            WHERE cp.competency IN :competencies
                AND cp.user.id = :userId
            """)
    Set<CompetencyProgress> findByCompetenciesAndUser(@Param("competencies") Collection<? extends CourseCompetency> competencies, @Param("userId") long userId);

    @Query("""
            SELECT cp
            FROM CompetencyProgress cp
                LEFT JOIN FETCH cp.user
                LEFT JOIN FETCH cp.competency
            WHERE cp.competency.id = :competencyId
                AND cp.user.id = :userId
            """)
    Optional<CompetencyProgress> findEagerByCompetencyIdAndUserId(@Param("competencyId") long competencyId, @Param("userId") long userId);

    @Query("""
            SELECT cp
            FROM CompetencyProgress cp
            WHERE cp.competency.id IN :competencyIds
                AND cp.user.id = :userId
            """)
    Set<CompetencyProgress> findAllByCompetencyIdsAndUserId(@Param("competencyIds") Set<Long> competencyIds, @Param("userId") long userId);

    @Query("""
            SELECT COUNT(cp)
            FROM CompetencyProgress cp
            WHERE cp.competency.id = :competencyId
            """)
    long countByCompetency(@Param("competencyId") long competencyId);

    @Query("""
            SELECT COUNT(cp)
            FROM CompetencyProgress cp
            WHERE cp.competency.id = :competencyId
                AND cp.progress * cp.confidence >= :masteryThreshold
            """)
    long countByCompetencyAndMastered(@Param("competencyId") long competencyId, @Param("masteryThreshold") int masteryThreshold);

    @Query("""
            SELECT cp
            FROM CourseCompetency c
                LEFT JOIN CompetencyRelation cr ON cr.tailCompetency = c
                LEFT JOIN CourseCompetency priorC ON priorC = cr.headCompetency
                LEFT JOIN FETCH CompetencyProgress cp ON cp.competency = priorC
            WHERE cr.type <> de.tum.cit.aet.artemis.atlas.domain.competency.RelationType.MATCHES
                AND cp.user = :user
                AND c = :competency
            """)
    Set<CompetencyProgress> findAllPriorByCompetencyId(@Param("competency") CourseCompetency competency, @Param("user") User userId);

    @Query("""
            SELECT COALESCE(GREATEST(0.0, LEAST(1.0, AVG(cp.progress * cp.confidence / com.masteryThreshold))), 0.0)
            FROM CompetencyProgress cp
                LEFT JOIN cp.competency com
                LEFT JOIN com.course c
                LEFT JOIN cp.user u
            WHERE com.id = :competencyId
                AND cp.progress > 0
                AND c.studentGroupName MEMBER OF u.groups
            """)
    double findAverageOfAllNonZeroStudentProgressByCompetencyId(@Param("competencyId") long competencyId);

    /**
     * Find all competency progress records for a course for export.
     *
     * @param courseId the id of the course
     * @return list of competency progress export DTOs
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.core.dto.export.CompetencyProgressExportDTO(
                cp.competency.id, cp.competency.title, cp.user.login, cp.progress, cp.confidence, cp.lastModifiedDate)
            FROM CompetencyProgress cp
            WHERE cp.competency.course.id = :courseId
            """)
    List<CompetencyProgressExportDTO> findAllForExportByCourseId(@Param("courseId") long courseId);

    /**
     * Count the number of competency progress records for a course.
     *
     * @param courseId the id of the course
     * @return the count of progress records
     */
    @Query("""
            SELECT COUNT(cp)
            FROM CompetencyProgress cp
            WHERE cp.competency.course.id = :courseId
            """)
    long countByCourseId(@Param("courseId") long courseId);

    /**
     * Find all competency progress records for a user for GDPR data export.
     *
     * @param userId the id of the user
     * @return list of user competency progress export DTOs with course information
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.core.dto.export.UserCompetencyProgressExportDTO(
                cp.competency.course.id, cp.competency.course.title, cp.competency.id, cp.competency.title,
                cp.progress, cp.confidence, cp.lastModifiedDate)
            FROM CompetencyProgress cp
            WHERE cp.user.id = :userId
            ORDER BY cp.competency.course.title, cp.competency.title
            """)
    List<UserCompetencyProgressExportDTO> findAllForExportByUserId(@Param("userId") long userId);
}
