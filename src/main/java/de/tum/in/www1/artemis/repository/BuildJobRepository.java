package de.tum.in.www1.artemis.repository;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

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
import de.tum.in.www1.artemis.service.connectors.localci.dto.DockerImageBuild;
import de.tum.in.www1.artemis.service.connectors.localci.dto.ResultBuildJob;

@Profile(PROFILE_CORE)
@Repository
public interface BuildJobRepository extends JpaRepository<BuildJob, Long>, JpaSpecificationExecutor<BuildJob> {

    Optional<BuildJob> findFirstByParticipationIdOrderByBuildStartDateDesc(Long participationId);

    Optional<BuildJob> findBuildJobByResult(Result result);

    @EntityGraph(attributePaths = { "result", "result.feedbacks", "result.feedbacks.testCase", "result.participation", "result.submission" })
    @Query("SELECT b FROM BuildJob b")
    Page<BuildJob> findAllWithEagerResults(Pageable pageable);

    @Query("""
            SELECT new de.tum.in.www1.artemis.service.connectors.localci.dto.DockerImageBuild(
                b.dockerImage,
                MAX(b.buildStartDate)
            )
            FROM BuildJob b
            GROUP BY b.dockerImage
            """)
    Set<DockerImageBuild> findAllLastBuildDatesForDockerImages();

    @EntityGraph(attributePaths = { "result", "result.feedbacks", "result.feedbacks.testCase", "result.participation", "result.submission" })
    @Query("""
            SELECT b
            FROM BuildJob b
                LEFT JOIN FETCH b.result r
            WHERE b.courseId = :courseId
            """)
    Page<BuildJob> findAllWithEagerResultsByCourseId(long courseId, Pageable pageable);

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
