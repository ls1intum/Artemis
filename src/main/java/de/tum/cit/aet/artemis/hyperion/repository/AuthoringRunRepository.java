package de.tum.cit.aet.artemis.hyperion.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.hyperion.domain.AuthoringRun;

/** Version links are write-once and completion is first-wins, independently for each immutable job id. */
@Lazy
@Repository
@Profile(PROFILE_CORE)
public interface AuthoringRunRepository extends ArtemisJpaRepository<AuthoringRun, Long> {

    Optional<AuthoringRun> findByJobId(String jobId);

    List<AuthoringRun> findByOwnerIdAndJobIdIn(long ownerId, Collection<String> jobIds);

    @Query("""
            SELECT run FROM AuthoringRun run
            WHERE run.ownerId = :ownerId AND (:beforeId IS NULL OR run.id < :beforeId)
            ORDER BY run.id DESC
            """)
    List<AuthoringRun> findOwnedBefore(@Param("ownerId") long ownerId, @Param("beforeId") Long beforeId, Pageable pageable);

    @Query("""
            SELECT run FROM AuthoringRun run
            WHERE run.exerciseId = :exerciseId AND run.mutationStartedAt IS NOT NULL AND run.revertedAt IS NULL
              AND (run.liveExerciseChanged IS NULL OR run.liveExerciseChanged = true)
            ORDER BY run.id DESC
            """)
    List<AuthoringRun> findLatestMutation(@Param("exerciseId") long exerciseId, Pageable pageable);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE AuthoringRun run SET run.beforeVersionId = :versionId, run.repositoryBranch = :branch, run.mutationStartedAt = :startedAt
            WHERE run.jobId = :jobId AND run.mutationStartedAt IS NULL AND run.finishedAt IS NULL
            """)
    int linkBeforeVersion(@Param("jobId") String jobId, @Param("versionId") long versionId, @Param("branch") String branch, @Param("startedAt") Instant startedAt);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE AuthoringRun run SET run.afterVersionId = :versionId
            WHERE run.jobId = :jobId AND run.beforeVersionId IS NOT NULL AND run.afterVersionId IS NULL AND run.finishedAt IS NULL
            """)
    int linkAfterVersion(@Param("jobId") String jobId, @Param("versionId") long versionId);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE AuthoringRun run SET run.status = :status, run.finishedAt = :finishedAt, run.liveExerciseChanged = :changed
            WHERE run.jobId = :jobId AND run.finishedAt IS NULL
            """)
    int complete(@Param("jobId") String jobId, @Param("status") AuthoringRun.Status status, @Param("finishedAt") Instant finishedAt, @Param("changed") Boolean changed);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE AuthoringRun run SET run.revertedAt = :revertedAt
            WHERE run.jobId = :jobId AND run.afterVersionId = :expectedVersionId AND run.revertedAt IS NULL
            """)
    int markReverted(@Param("jobId") String jobId, @Param("expectedVersionId") long expectedVersionId, @Param("revertedAt") Instant revertedAt);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE AuthoringRun run SET run.restoreStartedAt = COALESCE(run.restoreStartedAt, :startedAt)
            WHERE run.jobId = :jobId AND run.afterVersionId = :expectedVersionId AND run.revertedAt IS NULL
            """)
    int markRestoreStarted(@Param("jobId") String jobId, @Param("expectedVersionId") long expectedVersionId, @Param("startedAt") Instant startedAt);

    @Query("""
            SELECT DISTINCT run.exerciseId FROM AuthoringRun run
            WHERE run.restoreStartedAt IS NOT NULL AND run.revertedAt IS NULL AND run.exerciseId > :afterExerciseId
            ORDER BY run.exerciseId
            """)
    List<Long> findPendingRestoreExercises(@Param("afterExerciseId") long afterExerciseId, Pageable pageable);

    boolean existsByExerciseIdAndRestoreStartedAtIsNotNullAndRevertedAtIsNull(long exerciseId);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE AuthoringRun run SET run.restoreStartedAt = NULL WHERE run.exerciseId = :exerciseId AND run.revertedAt IS NULL")
    int reconcileRestore(@Param("exerciseId") long exerciseId);

}
