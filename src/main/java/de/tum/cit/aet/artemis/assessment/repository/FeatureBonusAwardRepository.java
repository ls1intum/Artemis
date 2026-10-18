package de.tum.cit.aet.artemis.assessment.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.assessment.domain.FeatureBonusAward;
import de.tum.cit.aet.artemis.assessment.dto.FeatureBonusAwardDTO;
import de.tum.cit.aet.artemis.assessment.dto.FeatureBonusPointsSumDTO;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for the {@link FeatureBonusAward} entity.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface FeatureBonusAwardRepository extends ArtemisJpaRepository<FeatureBonusAward, Long> {

    /**
     * @param participationId the id of the participation
     * @return the awards of the participation, oldest first
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.assessment.dto.FeatureBonusAwardDTO(award.id, award.title, award.justification, award.points, award.createdBy, award.createdDate)
            FROM FeatureBonusAward award
            WHERE award.participation.id = :participationId
            ORDER BY award.id
            """)
    List<FeatureBonusAwardDTO> findAllByParticipationId(@Param("participationId") long participationId);

    /**
     * @param exerciseId the id of the exercise
     * @return the sum of the awarded points per participation of the exercise; participations without awards are left out
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.assessment.dto.FeatureBonusPointsSumDTO(award.participation.id, SUM(award.points))
            FROM FeatureBonusAward award
            WHERE award.participation.exercise.id = :exerciseId
            GROUP BY award.participation.id
            """)
    List<FeatureBonusPointsSumDTO> sumPointsPerParticipationByExerciseId(@Param("exerciseId") long exerciseId);

    /**
     * @param courseId the id of the course
     * @return the sum of the points awarded to individual participations per student over the exercises of the course
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.assessment.dto.FeatureBonusPointsSumDTO(student.id, SUM(award.points))
            FROM FeatureBonusAward award
                JOIN award.participation participation
                JOIN participation.student student
            WHERE participation.exercise.course.id = :courseId
            GROUP BY student.id
            """)
    List<FeatureBonusPointsSumDTO> sumIndividualPointsPerStudentByCourseId(@Param("courseId") long courseId);

    /**
     * @param courseId the id of the course
     * @return the sum of the points awarded to team participations per team member over the exercises of the course
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.assessment.dto.FeatureBonusPointsSumDTO(student.id, SUM(award.points))
            FROM FeatureBonusAward award
                JOIN award.participation participation
                JOIN participation.team team
                JOIN team.students student
            WHERE participation.exercise.course.id = :courseId
            GROUP BY student.id
            """)
    List<FeatureBonusPointsSumDTO> sumTeamPointsPerStudentByCourseId(@Param("courseId") long courseId);
}
