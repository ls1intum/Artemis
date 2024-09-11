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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.core.service.connectors.localci.dto.DockerImageBuild;
import de.tum.cit.aet.artemis.core.service.connectors.localci.dto.ResultBuildJob;
import de.tum.cit.aet.artemis.programming.domain.BuildJob;
import de.tum.cit.aet.artemis.programming.domain.BuildStatus;
import de.tum.cit.aet.artemis.service.dto.BuildJobResultCountDTO;

@Profile(PROFILE_CORE)
@Repository
public interface BuildJobRepository extends ArtemisJpaRepository<BuildJob, Long>, JpaSpecificationExecutor<BuildJob> {

    Optional<BuildJob> findFirstByParticipationIdOrderByBuildStartDateDesc(Long participationId);

    Optional<BuildJob> findBuildJobByResult(Result result);

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
            SELECT new de.tum.cit.aet.artemis.core.service.connectors.localci.dto.DockerImageBuild(
                b.dockerImage,
                MAX(b.buildStartDate)
            )
            FROM BuildJob b
            GROUP BY b.dockerImage
            """)
    Set<DockerImageBuild> findAllLastBuildDatesForDockerImages();

    @Query("""
            SELECT b.id
            FROM BuildJob b
            WHERE b.courseId = :courseId
            """)
    List<Long> findIdsByCourseId(@Param("courseId") long courseId, Pageable pageable);

    long countBuildJobByCourseId(long courseId);

    /**
     * Retrieves a paginated list of all {@link BuildJob} entities that have a given course id.
     *
     * @param courseId the course id.
     * @param pageable the pagination information.
     * @return a paginated list of {@link BuildJob} entities. If no entities are found, returns an empty page.
     */
    default Page<BuildJob> findAllWithDataByCourseId(long courseId, Pageable pageable) {
        List<Long> ids = findIdsByCourseId(courseId, pageable);
        if (ids.isEmpty()) {
            return Page.empty(pageable);
        }
        List<BuildJob> result = findWithDataByIdIn(ids);
        return new PageImpl<>(result, pageable, countBuildJobByCourseId(courseId));
    }

    @Query("""
             SELECT new de.tum.cit.aet.artemis.core.service.connectors.localci.dto.ResultBuildJob(
                 b.result.id,
                 b.buildJobId
             )
             FROM BuildJob b
             WHERE b.result.id IN :resultIds
            """)
    Set<ResultBuildJob> findBuildJobIdsForResultIds(@Param("resultIds") List<Long> resultIds);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.service.dto.BuildJobResultCountDTO(
                b.buildStatus,
                COUNT(b.buildStatus)
            )
            FROM BuildJob b
            WHERE b.buildStartDate >= :fromDateTime
                AND (:courseId IS NULL OR b.courseId = :courseId)
            GROUP BY b.buildStatus
            """)
    List<BuildJobResultCountDTO> getBuildJobsResultsStatistics(@Param("fromDateTime") ZonedDateTime fromDateTime, @Param("courseId") Long courseId);

}
