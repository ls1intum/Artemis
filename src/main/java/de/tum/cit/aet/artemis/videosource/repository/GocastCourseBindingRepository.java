package de.tum.cit.aet.artemis.videosource.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Instant;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.videosource.domain.GocastCourseBinding;

@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface GocastCourseBindingRepository extends ArtemisJpaRepository<GocastCourseBinding, Long> {

    @Query("SELECT b FROM GocastCourseBinding b WHERE b.courseId = :courseId AND b.status <> de.tum.cit.aet.artemis.videosource.domain.GocastBindingStatus.PENDING")
    Optional<GocastCourseBinding> findByCourseId(@Param("courseId") long courseId);

    @Query("SELECT b FROM GocastCourseBinding b WHERE b.courseId = :courseId")
    Optional<GocastCourseBinding> findConnectionRowByCourseId(@Param("courseId") long courseId);

    @Query("SELECT b FROM GocastCourseBinding b WHERE b.courseId = :courseId AND b.status = de.tum.cit.aet.artemis.videosource.domain.GocastBindingStatus.PENDING")
    Optional<GocastCourseBinding> findPendingByCourseId(@Param("courseId") long courseId);

    @Query("SELECT b FROM GocastCourseBinding b WHERE b.stateHash = :stateHash AND b.status = de.tum.cit.aet.artemis.videosource.domain.GocastBindingStatus.PENDING")
    Optional<GocastCourseBinding> findPendingByStateHash(@Param("stateHash") String stateHash);

    Optional<GocastCourseBinding> findByGocastCourseId(long gocastCourseId);

    /**
     * Replaces a pending or revoked connection atomically, never an active connection.
     *
     * @param courseId      the Artemis course
     * @param stateHash     the new state hash
     * @param integrationId the authenticated integration
     * @param expiresAt     the approval expiry
     * @return the number of replaced rows
     */
    @Transactional // ok because of modifying query
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE GocastCourseBinding b
            SET b.status = de.tum.cit.aet.artemis.videosource.domain.GocastBindingStatus.PENDING,
                b.stateHash = :stateHash, b.expiresAt = :expiresAt, b.integrationId = :integrationId,
                b.gocastCourseId = NULL, b.gocastGrantId = NULL, b.courseSlug = NULL, b.courseName = NULL,
                b.visibility = NULL, b.version = b.version + 1
            WHERE b.courseId = :courseId AND b.status <> de.tum.cit.aet.artemis.videosource.domain.GocastBindingStatus.ACTIVE
            """)
    int replacePending(@Param("courseId") long courseId, @Param("stateHash") String stateHash, @Param("integrationId") long integrationId, @Param("expiresAt") Instant expiresAt);

    /**
     * Completes only the exact usable approval. The unique remote-course constraint prevents duplicate connections.
     *
     * @param stateHash      the state hash
     * @param integrationId  the verified integration
     * @param now            the current time
     * @param remoteCourseId the verified course
     * @param grantId        the verified grant
     * @param slug           the course slug
     * @param name           the course name
     * @param visibility     the course visibility
     * @return the number of completed rows
     */
    @Transactional // ok because of modifying query
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE GocastCourseBinding b
            SET b.status = de.tum.cit.aet.artemis.videosource.domain.GocastBindingStatus.ACTIVE,
                b.gocastCourseId = :remoteCourseId, b.gocastGrantId = :grantId,
                b.courseSlug = :slug, b.courseName = :name, b.visibility = :visibility,
                b.stateHash = NULL, b.expiresAt = NULL, b.version = b.version + 1
            WHERE b.status = de.tum.cit.aet.artemis.videosource.domain.GocastBindingStatus.PENDING
                AND b.stateHash = :stateHash AND b.integrationId = :integrationId AND b.expiresAt > :now
            """)
    int completePending(@Param("stateHash") String stateHash, @Param("integrationId") long integrationId, @Param("now") Instant now, @Param("remoteCourseId") long remoteCourseId,
            @Param("grantId") long grantId, @Param("slug") String slug, @Param("name") String name, @Param("visibility") String visibility);

    @Transactional // ok because of delete
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM GocastCourseBinding b WHERE b.courseId = :courseId AND b.status = de.tum.cit.aet.artemis.videosource.domain.GocastBindingStatus.PENDING")
    int deletePendingByCourseId(@Param("courseId") long courseId);

    @Transactional // ok because of delete
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM GocastCourseBinding b WHERE b.stateHash = :stateHash AND b.status = de.tum.cit.aet.artemis.videosource.domain.GocastBindingStatus.PENDING")
    int deletePendingByStateHash(@Param("stateHash") String stateHash);

    @Transactional // ok because of delete
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM GocastCourseBinding b
            WHERE b.courseId = :courseId AND b.integrationId = :integrationId AND b.gocastCourseId = :remoteCourseId AND b.gocastGrantId = :grantId
            """)
    int deleteExactGrant(@Param("courseId") long courseId, @Param("integrationId") long integrationId, @Param("remoteCourseId") long remoteCourseId,
            @Param("grantId") long grantId);

    @Transactional // ok because of modifying query
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE GocastCourseBinding b SET b.courseSlug = :slug, b.courseName = :name, b.visibility = :visibility, b.version = b.version + 1
            WHERE b.courseId = :courseId AND b.integrationId = :integrationId AND b.gocastCourseId = :remoteCourseId AND b.gocastGrantId = :grantId
                AND b.version = :version AND b.status = de.tum.cit.aet.artemis.videosource.domain.GocastBindingStatus.ACTIVE
            """)
    int updateExactGrantMetadata(@Param("courseId") long courseId, @Param("integrationId") long integrationId, @Param("remoteCourseId") long remoteCourseId,
            @Param("grantId") long grantId, @Param("version") long version, @Param("slug") String slug, @Param("name") String name, @Param("visibility") String visibility);

    @Transactional // ok because of modifying query
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE GocastCourseBinding b SET b.status = de.tum.cit.aet.artemis.videosource.domain.GocastBindingStatus.REVOKED, b.version = b.version + 1
            WHERE b.courseId = :courseId AND b.integrationId = :integrationId AND b.gocastCourseId = :remoteCourseId AND b.gocastGrantId = :grantId AND b.version = :version
            """)
    int markExactGrantRevoked(@Param("courseId") long courseId, @Param("integrationId") long integrationId, @Param("remoteCourseId") long remoteCourseId,
            @Param("grantId") long grantId, @Param("version") long version);
}
