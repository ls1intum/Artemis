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

    @Query(value = """
            SELECT COUNT(b.id)
            FROM BuildJob b
            """, nativeQuery = true)
    long countAllBuildJobs();

    @Query(value = """
            SELECT * FROM (
                SELECT
                    b.id AS build_job_id,
                    b.*,
                    r.*,
                    p.*,
                    e.*,
                    s.*,
                    ROW_NUMBER() OVER (ORDER BY b.id) AS rn
                FROM BuildJob b
                LEFT JOIN Result r ON b.result_id = r.id
                LEFT JOIN Participation p ON r.participation_id = p.id
                LEFT JOIN Exercise e ON p.exercise_id = e.id
                LEFT JOIN Submission s ON r.submission_id = s.id
            ) sub
            WHERE sub.rn BETWEEN :from AND :to
            """, nativeQuery = true)
    List<BuildJob> findAllWithRowBounds(@Param("from") int from, @Param("to") int to);

    default Page<BuildJob> findAll(Pageable pageable) {

        int pageNumber = pageable.getPageNumber();
        int pageSize = pageable.getPageSize();
        int from = pageNumber * pageSize + 1;
        int to = (pageNumber + 1) * pageSize;

        List<BuildJob> buildJobs = findAllWithRowBounds(from, to);

        long count = countAllBuildJobs();

        return new PageImpl<>(buildJobs, pageable, count);
    }

    @Query("""
            SELECT new de.tum.in.www1.artemis.service.connectors.localci.dto.DockerImageBuild(
                b.dockerImage,
                MAX(b.buildStartDate)
            )
            FROM BuildJob b
            GROUP BY b.dockerImage
            """)
    Set<DockerImageBuild> findAllLastBuildDatesForDockerImages();

    @Query(value = """
            SELECT COUNT(b.id)
            FROM BuildJob b
            WHERE b.course_id = :courseId
            """, nativeQuery = true)
    long countAllByCourseId(@Param("courseId") long courseId);

    @Query(value = """
            SELECT * FROM (
                SELECT
                    b.id AS build_job_id,
                    b.*,
                    r.*,
                    p.*,
                    e.*,
                    s.*,
                    ROW_NUMBER() OVER (ORDER BY b.id) AS rn
                FROM BuildJob b
                LEFT JOIN Result r ON b.result_id = r.id
                LEFT JOIN Participation p ON r.participation_id = p.id
                LEFT JOIN Exercise e ON p.exercise_id = e.id
                LEFT JOIN Submission s ON r.submission_id = s.id
                WHERE b.course_id = :courseId
            ) sub
            WHERE sub.rn BETWEEN :from AND :to
            """, nativeQuery = true)
    List<BuildJob> findAllByCourseIdWithRowBounds(@Param("courseId") long courseId, @Param("from") int from, @Param("to") int to);

    default Page<BuildJob> findAllByCourseId(long courseId, Pageable pageable) {

        int pageNumber = pageable.getPageNumber();
        int pageSize = pageable.getPageSize();
        int from = pageNumber * pageSize + 1;
        int to = (pageNumber + 1) * pageSize;

        List<BuildJob> buildJobs = findAllByCourseIdWithRowBounds(courseId, from, to);

        long count = countAllByCourseId(courseId);

        return new PageImpl<>(buildJobs, pageable, count);
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
