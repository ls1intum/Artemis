package de.tum.cit.aet.artemis.exercise.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.exercise.dto.ExercisePeerMetadataDTO;

/** Scalar statistics projections inherited by {@link ExerciseRepository}, without a separate repository bean. */
@Profile(PROFILE_CORE)
@Lazy
@Repository
@NoRepositoryBean
public interface ExerciseStatisticsRepository {

    /**
     * Projects released course exercises that the requesting student can access, without fetching their content.
     *
     * @param courseId         the authorized course
     * @param now              the release-date cutoff
     * @param requireLtiLaunch whether an online-course exercise requires a recorded LTI launch
     * @param userLogin        the requesting student's login for the LTI visibility check
     * @return scalar exercise metadata for the on-demand comparison
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.exercise.dto.ExercisePeerMetadataDTO(
                exercise.id, exercise.title, TYPE(exercise), exercise.releaseDate, exercise.dueDate, exercise.assessmentDueDate, exercise.mode, exercise.includedInOverallScore)
            FROM Exercise exercise
            WHERE exercise.course.id = :courseId
                AND (exercise.releaseDate IS NULL OR exercise.releaseDate <= :now)
                AND (:requireLtiLaunch = FALSE OR EXISTS (SELECT launch.id FROM LtiResourceLaunch launch WHERE launch.exercise = exercise AND launch.user.login = :userLogin))
            """)
    List<ExercisePeerMetadataDTO> findMetadataForPeerComparison(@Param("courseId") long courseId, @Param("now") ZonedDateTime now,
            @Param("requireLtiLaunch") boolean requireLtiLaunch, @Param("userLogin") String userLogin);
}
