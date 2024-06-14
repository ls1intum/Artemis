package de.tum.in.www1.artemis.repository;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
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

    @Query("""
            SELECT b.id
            FROM BuildJob b
            """)
    List<Long> findAllIds(Pageable pageable);

    @Query("""
            SELECT b
            FROM BuildJob b
            LEFT JOIN FETCH b.result r
            LEFT JOIN FETCH r.participation p
            LEFT JOIN FETCH p.exercise e
            LEFT JOIN FETCH r.submission s
            WHERE b.id IN :ids
            """)
    List<BuildJob> findByIdsWithAssociations(@Param("ids") List<Long> ids);

    default Page<BuildJob> findAll(Pageable pageable) {
        List<Long> ids = findAllIds(pageable);
        if (ids.isEmpty()) {
            return Page.empty(pageable);
        }
        List<BuildJob> result = findByIdsWithAssociations(ids);
        return new PageImpl<>(result, pageable, countAllBuildJobs());
    }

    @Query("""
            SELECT COUNT(b)
            FROM BuildJob b
            """)
    long countAllBuildJobs();

    @Query("""
            SELECT new de.tum.in.www1.artemis.service.connectors.localci.dto.DockerImageBuild(
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
            WHERE b.course.id = :courseId
            """)
    List<Long> findIdsByCourseId(@Param("courseId") long courseId, Pageable pageable);

    @Query("""
            SELECT COUNT(b)
            FROM BuildJob b
            WHERE b.course.id = :courseId
            """)
    long countBuildJobsByCourseId(@Param("courseId") long courseId);

    default Page<BuildJob> findAllByCourseId(long courseId, Pageable pageable) {
        List<Long> ids = findIdsByCourseId(courseId, pageable);
        if (ids.isEmpty()) {
            return Page.empty(pageable);
        }
        List<BuildJob> result = findByIdsWithAssociations(ids);
        return new PageImpl<>(result, pageable, countBuildJobsByCourseId(courseId));
    }

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
