package de.tum.cit.aet.artemis.localci.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.buildagent.dto.BuildJobResultCountDTO;
import de.tum.cit.aet.artemis.buildagent.dto.DockerImageBuild;
import de.tum.cit.aet.artemis.buildagent.dto.ResultBuildJob;
import de.tum.cit.aet.artemis.core.domain.DomainObject_;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.localci.domain.BuildJob;
import de.tum.cit.aet.artemis.localci.dto.BuildJobStatisticsDTO;
import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;

@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface BuildJobRepository extends ArtemisJpaRepository<BuildJob, Long>, JpaSpecificationExecutor<BuildJob> {

    @EntityGraph(type = LOAD, attributePaths = { "result", "result.submission", "result.submission.participation", "result.submission.participation.exercise" })
    List<BuildJob> findWithDataByIdIn(List<Long> ids);

    /**
     * The ids of the results the jobs of a build group have merged into, oldest first. The containers of one build are
     * scheduled as separate jobs that share a build group (see {@code BuildJobQueueItem#buildGroupId}) and all merge into
     * one result, so the first id is the group's aggregated result.
     *
     * @param buildGroupId the id of the build group
     * @param pageable     limits the query, typically to the first linked result
     * @return the ids of the results linked to the group's jobs, oldest first
     */
    @Query("""
            SELECT b.result.id
            FROM BuildJob b
            WHERE b.buildGroupId = :buildGroupId
                AND b.result IS NOT NULL
            ORDER BY b.id ASC
            """)
    List<Long> findResultIdsOfBuildGroup(@Param("buildGroupId") String buildGroupId, Pageable pageable);

    /**
     * The build groups whose jobs have all finished while their aggregated result is still in progress: groups whose last
     * container's finalization did not go through, see {@code LocalCIResultProcessingService#finalizeCompletedBuildGroups}.
     * A group with a job that is still queued, building or missing is not complete and is left alone, and so is a group
     * whose jobs finished after the given date, so that a merge under way is not raced.
     *
     * @param finishedStatuses the statuses in which a job counts as finished
     * @param completedBefore  only groups whose jobs finished before this date
     * @param pageable         limits the number of groups
     * @return the ids of the complete build groups whose aggregated result has no completion date
     */
    @Query("""
            SELECT DISTINCT b.buildGroupId
            FROM BuildJob b
            WHERE b.buildGroupId IS NOT NULL
                AND b.result IS NOT NULL
                AND b.result.completionDate IS NULL
                AND b.buildCompletionDate < :completedBefore
                AND NOT EXISTS (
                    SELECT o
                    FROM BuildJob o
                    WHERE o.buildGroupId = b.buildGroupId
                        AND o.buildStatus NOT IN :finishedStatuses)
            """)
    List<String> findCompletedBuildGroupsWithResultInProgress(@Param("finishedStatuses") Collection<BuildStatus> finishedStatuses,
            @Param("completedBefore") ZonedDateTime completedBefore, Pageable pageable);

    /**
     * Checks whether the aggregated result a build group's jobs link to is still in progress. Written as a query rather
     * than derived, so that a job without a result does not count: the join to the result has to be an inner one.
     *
     * @param buildGroupId the id of the build group
     * @return true if a job of the group links to a result without a completion date
     */
    @Query("""
            SELECT COUNT(b) > 0
            FROM BuildJob b
            WHERE b.buildGroupId = :buildGroupId
                AND b.result IS NOT NULL
                AND b.result.completionDate IS NULL
            """)
    boolean existsResultInProgressOfBuildGroup(@Param("buildGroupId") String buildGroupId);

    /**
     * The jobs of a build group, one per container of a multi-container build. The result processing reads the group's
     * completion, its outcome and its dates off this list, rather than asking the database one question at a time.
     *
     * @param buildGroupId the id of the build group
     * @return the group's jobs
     */
    List<BuildJob> findAllByBuildGroupId(String buildGroupId);

    /**
     * Retrieves all build job ids that were submitted before the given date.
     *
     * @param date the date before which build jobs should be deleted
     * @return a set of ids of build jobs submitted before the date
     */
    @Query("""
            SELECT b.id
            FROM BuildJob b
            WHERE b.buildSubmissionDate < :date
            """)
    Set<Long> findAllIdsBeforeDate(@Param("date") ZonedDateTime date);

    /**
     * Finds the ids of finished build jobs matching the given optional filters, ordered and paged according to {@code pageable}.
     * <p>
     * Built with the Criteria API rather than JPQL on purpose. The previous implementation expressed every optional filter as {@code (:param IS NULL OR column = :param)} in one
     * statement; MySQL cannot fold those branches away, so it could not estimate the selectivity of any filter and instead satisfied the {@code ORDER BY} by scanning
     * {@code idx_build_job_build_submission_date} in reverse over the whole table, so a course-scoped call examined most of it only to return nothing.
     * With {@link BuildJobSpecs} an absent filter contributes no SQL at all, so the optimizer sees a real {@code course_id = ?} and can use {@code idx_build_job_course_id}.
     * <p>
     * Uses {@code findBy(...).slice(...)} rather than {@code findAll(spec, pageable)} because the latter returns a {@code Page} and issues an additional {@code COUNT(*)} over
     * the filtered set, which on a 1.8 M-row table would be a new slow query. Only the id is projected; callers load the entities via {@link #findWithDataByIdIn(List)}.
     *
     * @param buildStatus       filter by build status, or null
     * @param buildAgentAddress filter by build agent address, or null
     * @param startDate         earliest build submission date, or null
     * @param endDate           latest build submission date, or null
     * @param searchTerm        free-text match on repository name or course title, or null
     * @param courseId          filter by course, or null
     * @param durationLower     minimum build duration, or null
     * @param durationUpper     maximum build duration, or null
     * @param pageable          paging and sorting information
     * @return a slice of matching build job ids
     */
    default Slice<Long> findFinishedIdsByFilterCriteria(@Nullable BuildStatus buildStatus, @Nullable String buildAgentAddress, @Nullable ZonedDateTime startDate,
            @Nullable ZonedDateTime endDate, @Nullable String searchTerm, @Nullable Long courseId, @Nullable Duration durationLower, @Nullable Duration durationUpper,
            Pageable pageable) {
        // Specification.allOf rejects null elements, so absent filters are filtered out here rather than contributing an always-true predicate.
        List<Specification<BuildJob>> specifications = Stream
                .of(BuildJobSpecs.isFinished(), BuildJobSpecs.hasBuildStatus(buildStatus), BuildJobSpecs.hasBuildAgentAddress(buildAgentAddress),
                        BuildJobSpecs.submittedFrom(startDate), BuildJobSpecs.submittedUntil(endDate), BuildJobSpecs.inCourse(courseId),
                        BuildJobSpecs.matchesSearchTerm(searchTerm), BuildJobSpecs.durationAtLeast(durationLower), BuildJobSpecs.durationAtMost(durationUpper))
                .filter(Objects::nonNull).toList();
        return findBy(Specification.allOf(specifications), query -> query.project(DomainObject_.ID).slice(pageable)).map(BuildJob::getId);
    }

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
                b.exerciseId,
                b.buildJobId
            )
            FROM BuildJob b
            WHERE b.participationId = :participationId
                AND b.result.id IS NOT NULL
            """)
    Set<ResultBuildJob> findBuildJobIdsWithResultForParticipationId(@Param("participationId") long participationId);

    /**
     * Counts the build jobs submitted since the given date per build status, across all courses.
     * <p>
     * Deliberately a separate method from {@link #getBuildJobsResultsStatisticsForCourse}: expressing both in one
     * statement requires a {@code (:courseId IS NULL OR b.courseId = :courseId)} guard, which MySQL cannot fold away,
     * so it can estimate neither predicate - the same defect described on {@link #findFinishedIdsByFilterCriteria}.
     *
     * @param fromDateTime earliest build submission date to count
     * @return the number of build jobs per build status
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.buildagent.dto.BuildJobResultCountDTO(
                b.buildStatus,
                COUNT(b.buildStatus)
            )
            FROM BuildJob b
            WHERE b.buildSubmissionDate >= :fromDateTime
            GROUP BY b.buildStatus
            """)
    List<BuildJobResultCountDTO> getBuildJobsResultsStatistics(@Param("fromDateTime") ZonedDateTime fromDateTime);

    /**
     * Counts the build jobs of one course submitted since the given date per build status.
     *
     * @param fromDateTime earliest build submission date to count
     * @param courseId     the course whose build jobs are counted
     * @return the number of build jobs per build status
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.buildagent.dto.BuildJobResultCountDTO(
                b.buildStatus,
                COUNT(b.buildStatus)
            )
            FROM BuildJob b
            WHERE b.buildSubmissionDate >= :fromDateTime
                AND b.courseId = :courseId
            GROUP BY b.buildStatus
            """)
    List<BuildJobResultCountDTO> getBuildJobsResultsStatisticsForCourse(@Param("fromDateTime") ZonedDateTime fromDateTime, @Param("courseId") long courseId);

    Optional<BuildJob> findByBuildJobId(String buildJobId);

    /**
     * Finds a build job by its build job ID with all related data eagerly fetched.
     * This includes the result, submission, participation, and exercise relationships
     * needed to convert the entity to a DTO without lazy initialization issues.
     *
     * @param buildJobId the unique build job identifier
     * @return an Optional containing the build job with all related data, or empty if not found
     */
    @EntityGraph(type = LOAD, attributePaths = { "result", "result.submission", "result.submission.participation", "result.submission.participation.exercise" })
    Optional<BuildJob> findWithDataByBuildJobId(String buildJobId);

    default BuildJob findByBuildJobIdElseThrow(String buildJobId) {
        return getValueElseThrow(findByBuildJobId(buildJobId));
    }

    /**
     * Get the number of build jobs for a list of exercise ids.
     *
     * @param courseId the id of the course
     * @return the number of build jobs
     */
    @Query("""
            SELECT COUNT(b)
            FROM BuildJob b
            WHERE b.courseId = :courseId
            """)
    long countBuildJobsByCourseId(@Param("courseId") long courseId);

    /**
     * Get the number of build jobs for a list of exercise ids (used for exams).
     *
     * @param exerciseIds the list of exercise ids
     * @return the number of build jobs
     */
    @Query("""
            SELECT COUNT(b)
            FROM BuildJob b
            WHERE b.exerciseId IN :exerciseIds
            """)
    long countBuildJobsByExerciseIds(@Param("exerciseIds") Collection<Long> exerciseIds);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.localci.dto.BuildJobStatisticsDTO(
                ROUND(AVG((b.buildCompletionDate - b.buildStartDate) BY SECOND)),
                COUNT(b),
                b.exerciseId
            )
            FROM BuildJob b
            WHERE b.exerciseId = :exerciseId
                AND b.buildStatus = de.tum.cit.aet.artemis.programming.domain.build.BuildStatus.SUCCESSFUL
            GROUP BY b.exerciseId
            """)
    BuildJobStatisticsDTO findBuildJobStatisticsByExerciseId(@Param("exerciseId") Long exerciseId);

    @Transactional // ok because of modifying query
    @Modifying
    @Query("""
            UPDATE BuildJob b
            SET b.buildStatus = :newStatus
            WHERE b.buildJobId = :buildJobId
            """)
    void updateBuildJobStatus(@Param("buildJobId") String buildJobId, @Param("newStatus") BuildStatus newStatus);

    /**
     * Update the build job status and set the build start date if it is not set yet. The buildStartDate is required to calculate the statistics and the correctly display in the
     * build overview.
     * This is used to update missing jobs that do not have a build start date yet.
     * <p>
     * A job that has already finished is left alone: the processing-map event that reports a job as building is delivered
     * asynchronously and can arrive after the job's result has been processed, and reopening the finished job would make
     * its build group look incomplete forever.
     *
     * @param buildJobId     the build job id
     * @param newStatus      the new build status
     * @param buildStartDate the build start date
     */
    @Transactional // ok because of modifying query
    @Modifying
    @Query("""
            UPDATE BuildJob b
            SET b.buildStatus = :newStatus,
                b.buildStartDate = CASE WHEN b.buildStartDate IS NULL THEN :buildStartDate ELSE b.buildStartDate END
            WHERE b.buildJobId = :buildJobId
                AND b.buildStatus NOT IN (
                    de.tum.cit.aet.artemis.programming.domain.build.BuildStatus.SUCCESSFUL,
                    de.tum.cit.aet.artemis.programming.domain.build.BuildStatus.FAILED,
                    de.tum.cit.aet.artemis.programming.domain.build.BuildStatus.ERROR,
                    de.tum.cit.aet.artemis.programming.domain.build.BuildStatus.CANCELLED,
                    de.tum.cit.aet.artemis.programming.domain.build.BuildStatus.TIMEOUT
                )
            """)
    void updateBuildJobStatusWithBuildStartDate(@Param("buildJobId") String buildJobId, @Param("newStatus") BuildStatus newStatus,
            @Param("buildStartDate") ZonedDateTime buildStartDate);

    /**
     * Find all build jobs with the given build status.
     *
     * @param statuses the list of build statuses
     * @return the list of build jobs
     */
    List<BuildJob> findAllByBuildStatusIn(List<BuildStatus> statuses);

    /**
     * Returns a slice of missing build jobs submitted within the given time range for whose participation no newer job exists, ordered by submission date descending.
     *
     * @param startTime earliest build submission time
     * @param endTime   latest build submission time
     * @param pageable  pagination information
     * @return slice of matching build jobs
     */
    @Query("""
            SELECT b
            FROM BuildJob b
            WHERE b.buildStatus = de.tum.cit.aet.artemis.programming.domain.build.BuildStatus.MISSING
              AND b.buildSubmissionDate >= :startTime
              AND b.buildSubmissionDate <= :endTime
              AND NOT EXISTS (
                  SELECT 1
                  FROM BuildJob b2
                  WHERE b2.participationId = b.participationId
                    AND b2.buildSubmissionDate > b.buildSubmissionDate
              )
            ORDER BY b.buildSubmissionDate DESC
            """)
    Slice<BuildJob> findMissingJobsToRetryInTimeRange(@Param("startTime") ZonedDateTime startTime, @Param("endTime") ZonedDateTime endTime, Pageable pageable);

    /**
     * Increment the retry count of a build job by 1
     *
     * @param buildJobId the ID of the build job
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE BuildJob b
            SET b.retryCount = b.retryCount + 1
            WHERE b.buildJobId = :buildJobId
            """)
    void incrementRetryCount(@Param("buildJobId") String buildJobId);
}
