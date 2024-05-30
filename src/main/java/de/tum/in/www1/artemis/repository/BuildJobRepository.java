package de.tum.in.www1.artemis.repository;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.BuildJob;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.enumeration.BuildStatus;
import de.tum.in.www1.artemis.service.connectors.localci.dto.DockerImageBuild;
import de.tum.in.www1.artemis.service.connectors.localci.dto.ResultBuildJob;

@Profile(PROFILE_CORE)
@Repository
public interface BuildJobRepository extends JpaRepository<BuildJob, Long>, JpaSpecificationExecutor<BuildJob> {

    Optional<BuildJob> findFirstByParticipationIdOrderByBuildStartDateDesc(Long participationId);

    Optional<BuildJob> findBuildJobByResult(Result result);

    @EntityGraph(attributePaths = { "result", "result.participation", "result.participation.exercise", "result.submission" })
    Page<BuildJob> findAll(Pageable pageable);

    @Query("""
            SELECT b
            FROM BuildJob b JOIN Course c ON b.courseId = c.id
            WHERE (:buildStatus IS null OR b.buildStatus = :buildStatus)
            AND (:buildAgentAddress IS null OR b.buildAgentAddress = :buildAgentAddress)
            AND (:startDate IS null OR b.buildStartDate >= :startDate)
            AND (:endDate IS null OR b.buildStartDate <= :endDate)
            AND (:searchTerm IS null OR (b.repositoryName LIKE %:searchTerm% OR c.title LIKE %:searchTerm%))
            AND (:courseId IS null OR b.courseId = :courseId)
            """)
    @EntityGraph(attributePaths = { "result", "result.participation", "result.participation.exercise", "result.submission" })
    Page<BuildJob> findAllByFilterCriteria(@Param("buildStatus") BuildStatus buildStatus, @Param("buildAgentAddress") String buildAgentAddress,
            @Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate, @Param("searchTerm") String searchTerm, @Param("courseId") Long courseId,
            Pageable pageable);

    @Query("""
            SELECT new de.tum.in.www1.artemis.service.connectors.localci.dto.DockerImageBuild(
                b.dockerImage,
                MAX(b.buildStartDate)
            )
            FROM BuildJob b
            GROUP BY b.dockerImage
            """)
    Set<DockerImageBuild> findAllLastBuildDatesForDockerImages();

    @EntityGraph(attributePaths = { "result", "result.participation", "result.participation.exercise", "result.submission" })
    Page<BuildJob> findAllByCourseId(long courseId, Pageable pageable);

    @Query("""
             SELECT new de.tum.in.www1.artemis.service.connectors.localci.dto.ResultBuildJob(
                 b.result.id,
                 b.buildJobId
             )
             FROM BuildJob b
             WHERE b.result.id IN :resultIds
            """)
    Set<ResultBuildJob> findBuildJobIdsForResultIds(@Param("resultIds") List<Long> resultIds);

}
