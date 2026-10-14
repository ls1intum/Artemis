package de.tum.cit.aet.artemis.programming.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;

import java.time.Instant;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/** Durable write ownership kept outside entity mappings so ordinary saves cannot replace it. */
@Lazy
@Profile({ PROFILE_CORE, PROFILE_LOCALVC })
public interface ProgrammingExerciseWriteOwnershipRepository extends Repository<ProgrammingExercise, Long> {

    /**
     * Atomically claims an unreserved programming exercise without an expiry or owner takeover.
     *
     * @param exerciseId protected exercise
     * @param token      unique operation token
     * @param owner      process incarnation
     * @param purpose    operation kind
     * @param startedAt  claim time
     * @return one for acquisition, otherwise zero
     */
    @Transactional
    @Modifying
    @Query(value = """
            UPDATE exercise SET write_owner_token = :token, write_owner = :owner,
                write_owner_purpose = :purpose, write_owner_started_at = :startedAt, write_owner_tests_commit = NULL
            WHERE id = :exerciseId AND discriminator = 'P' AND write_owner_token IS NULL
            """, nativeQuery = true)
    int claim(@Param("exerciseId") long exerciseId, @Param("token") String token, @Param("owner") String owner, @Param("purpose") String purpose,
            @Param("startedAt") Instant startedAt);

    /**
     * Releases only the exact reservation; a delayed caller cannot clear a replacement owner.
     *
     * @param exerciseId protected exercise
     * @param token      expected operation token
     * @return one if released, otherwise zero
     */
    @Transactional
    @Modifying
    @Query(value = """
            UPDATE exercise SET write_owner_token = NULL, write_owner = NULL,
                write_owner_purpose = NULL, write_owner_started_at = NULL, write_owner_tests_commit = NULL
            WHERE id = :exerciseId AND write_owner_token = :token
            """, nativeQuery = true)
    int release(@Param("exerciseId") long exerciseId, @Param("token") String token);

    /**
     * Changes a retained reservation without releasing its exclusive ownership.
     *
     * @param exerciseId protected exercise
     * @param token      expected operation token
     * @param purpose    recovery operation kind
     * @return one if retained, otherwise zero
     */
    @Transactional
    @Modifying
    @Query(value = "UPDATE exercise SET write_owner_purpose = :purpose, write_owner_tests_commit = NULL WHERE id = :exerciseId AND write_owner_token = :token", nativeQuery = true)
    int retain(@Param("exerciseId") long exerciseId, @Param("token") String token, @Param("purpose") String purpose);

    /**
     * Transfers a reconciled recovery reservation atomically, with no unreserved interval.
     *
     * @param exerciseId      protected exercise
     * @param expectedToken   reconciled operation token
     * @param expectedPurpose expected recovery kind
     * @param newToken        retry operation token
     * @param newPurpose      retry operation kind
     * @param owner           retry process incarnation
     * @param startedAt       retry time
     * @return one if transferred, otherwise zero
     */
    @Transactional
    @Modifying
    @Query(value = """
            UPDATE exercise SET write_owner_token = :newToken, write_owner = :owner,
                write_owner_purpose = :newPurpose, write_owner_started_at = :startedAt, write_owner_tests_commit = NULL
            WHERE id = :exerciseId AND write_owner_token = :expectedToken AND write_owner_purpose = :expectedPurpose
            """, nativeQuery = true)
    int replaceRecovery(@Param("exerciseId") long exerciseId, @Param("expectedToken") String expectedToken, @Param("expectedPurpose") String expectedPurpose,
            @Param("newToken") String newToken, @Param("newPurpose") String newPurpose, @Param("owner") String owner, @Param("startedAt") Instant startedAt);

    /**
     * Authorizes only one test-repository commit to synchronize grading under the current write owner.
     *
     * @param exerciseId protected exercise
     * @param token      expected operation token
     * @param commitHash authorized tests repository commit
     * @return one if updated, otherwise zero
     */
    @Transactional
    @Modifying
    @Query(value = "UPDATE exercise SET write_owner_tests_commit = :commitHash WHERE id = :exerciseId AND write_owner_token = :token", nativeQuery = true)
    int authorizeTestsBuild(@Param("exerciseId") long exerciseId, @Param("token") String token, @Param("commitHash") String commitHash);

    /**
     * Reads authoritative ownership without loading or changing the exercise entity.
     *
     * @param exerciseId protected exercise
     * @return current reservation, if present
     */
    @Query(value = """
            SELECT write_owner_token AS token, write_owner AS owner, write_owner_purpose AS purpose,
                write_owner_started_at AS startedAt, write_owner_tests_commit AS expectedTestsCommitHash
            FROM exercise WHERE id = :exerciseId AND write_owner_token IS NOT NULL
            """, nativeQuery = true)
    Optional<OwnershipProjection> findOwnership(@Param("exerciseId") long exerciseId);

    /** Database-only projection; it is not part of an exercise response. */
    interface OwnershipProjection {

        String getToken();

        String getOwner();

        String getPurpose();

        Instant getStartedAt();

        @Nullable
        String getExpectedTestsCommitHash();
    }
}
