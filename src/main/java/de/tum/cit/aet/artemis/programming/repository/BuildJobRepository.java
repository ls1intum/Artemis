package de.tum.cit.aet.artemis.programming.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.buildagent.dto.BuildJobResultCountDTO;
import de.tum.cit.aet.artemis.buildagent.dto.DockerImageBuild;
import de.tum.cit.aet.artemis.buildagent.dto.ResultBuildJob;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.programming.domain.build.BuildJob;
import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;

@Profile(PROFILE_CORE)
@Repository
public interface BuildJobRepository extends ArtemisJpaRepository<BuildJob, Long>, JpaSpecificationExecutor<BuildJob> {

    @EntityGraph(type = LOAD, attributePaths = { "result", "result.participation", "result.participation.exercise", "result.submission" })
    List<BuildJob> findWithDataByIdIn(List<Long> ids);

    // Cast to string is necessary. Otherwise, the query will fail on PostgreSQL.
    @Query("""
            SELECT b.id
            FROM BuildJob b
                LEFT JOIN Course c ON b.courseId = c.id
            WHERE (:buildStatus IS NULL OR b.buildStatus = :buildStatus)
                AND (:buildAgentAddress IS NULL OR b.buildAgentAddress = :buildAgentAddress)
                AND (CAST(:startDate AS string) IS NULL OR b.buildStartDate >= :startDate)
                AND (CAST(:endDate AS string) IS NULL OR b.buildStartDate <= :endDate)
                AND (:searchTerm IS NULL OR (b.repositoryName LIKE %:searchTerm% OR c.title LIKE %:searchTerm%))
                AND (:courseId IS NULL OR b.courseId = :courseId)
                AND (:durationLower IS NULL OR (b.buildCompletionDate - b.buildStartDate) >= :durationLower)
                AND (:durationUpper IS NULL OR (b.buildCompletionDate - b.buildStartDate) <= :durationUpper)

            """)
    Page<Long> findIdsByFilterCriteria(@Param("buildStatus") BuildStatus buildStatus, @Param("buildAgentAddress") String buildAgentAddress,
            @Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate, @Param("searchTerm") String searchTerm, @Param("courseId") Long courseId,
            @Param("durationLower") Duration durationLower, @Param("durationUpper") Duration durationUpper, Pageable pageable);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.buildagent.dto.DockerImageBuild(
                b.dockerImage,
                MAX(b.buildStartDate)
            )
            FROM BuildJob b
            GROUP BY b.dockerImage
            """)
    Set<DockerImageBuild> findAllLastBuildDatesForDockerImages();

    @Query("""
             SELECT new de.tum.cit.aet.artemis.buildagent.dto.ResultBuildJob(
                 b.result.id,
                 b.buildJobId
             )
             FROM BuildJob b
             WHERE b.result.id IN :resultIds
            """)
    Set<ResultBuildJob> findBuildJobIdsForResultIds(@Param("resultIds") List<Long> resultIds);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.buildagent.dto.BuildJobResultCountDTO(
                b.buildStatus,
                COUNT(b.buildStatus)
            )
            FROM BuildJob b
            WHERE b.buildStartDate >= :fromDateTime
                AND (:courseId IS NULL OR b.courseId = :courseId)
            GROUP BY b.buildStatus
            """)
    List<BuildJobResultCountDTO> getBuildJobsResultsStatistics(@Param("fromDateTime") ZonedDateTime fromDateTime, @Param("courseId") Long courseId);

    Optional<BuildJob> findByBuildJobId(String buildJobId);

    default BuildJob findByBuildJobIdElseThrow(String buildJobId) {
        return getValueElseThrow(findByBuildJobId(buildJobId));
    }

    /**
     * Get the number of build jobs for a list of exercise ids.
     *
     * @param exerciseIds the list of exercise ids
     * @return the number of build jobs
     */
    @Query("""
            SELECT COUNT(b)
            FROM BuildJob b
                LEFT JOIN Result r ON b.result.id = r.id
                LEFT JOIN Participation p ON r.participation.id = p.id
                LEFT JOIN Exercise e ON p.exercise.id = e.id
            WHERE e.id IN :exerciseIds
            """)
    long countBuildJobsByExerciseIds(@Param("exerciseIds") List<Long> exerciseIds);
}
