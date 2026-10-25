package de.tum.cit.aet.artemis.course.repository;

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
import de.tum.cit.aet.artemis.course.domain.CourseHomeVisit;

/** Atomic visit updates are safe across application instances and browser tabs. */
@Repository
@Lazy
@Profile(PROFILE_CORE)
public interface CourseHomeVisitRepository extends ArtemisJpaRepository<CourseHomeVisit, Long> {

    Optional<CourseHomeVisit> findByCourseIdAndUserId(long courseId, long userId);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE CourseHomeVisit v
            SET v.lastSeenAt = CASE WHEN v.lastSeenAt < :snapshot THEN :snapshot ELSE v.lastSeenAt END,
                v.firstSeenAt = CASE WHEN v.firstSeenAt > :snapshot THEN :snapshot ELSE v.firstSeenAt END,
                v.variantCursor = CASE WHEN v.variantCursor = :observed THEN v.variantCursor + 1 ELSE v.variantCursor END
            WHERE v.courseId = :courseId AND v.userId = :userId
            """)
    int acknowledge(@Param("courseId") long courseId, @Param("userId") long userId, @Param("snapshot") Instant snapshot, @Param("observed") long observed);

    @Transactional
    @Modifying
    @Query("DELETE FROM CourseHomeVisit v WHERE v.courseId = :courseId")
    int deleteByCourseId(@Param("courseId") long courseId);
}
