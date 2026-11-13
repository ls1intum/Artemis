package de.tum.cit.aet.artemis.hyperion.repository;

import java.time.ZonedDateTime;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionPoolLectureUnit;

/**
 * Spring Data repository for what the quiz question pool records per lecture unit.
 */
@Lazy
@Repository
@Conditional(HyperionEnabled.class)
public interface QuizQuestionPoolLectureUnitRepository extends ArtemisJpaRepository<QuizQuestionPoolLectureUnit, Long> {

    @Query("""
            SELECT unit
            FROM QuizQuestionPoolLectureUnit unit
            WHERE unit.course.id = :courseId
            ORDER BY unit.lectureUnitId
            """)
    List<QuizQuestionPoolLectureUnit> findAllByCourseId(@Param("courseId") long courseId);

    /**
     * Stores what a build saw of a lecture unit, leaving whether the unit is excluded untouched.
     *
     * @param courseId      the course
     * @param lectureUnitId the lecture unit
     * @param fingerprint   the fingerprint of the unit's material, or {@code null} when the unit has no readable material
     * @param builtAt       when the build reconciled the unit
     * @return the number of updated rows
     */
    @Modifying
    @Transactional // ok because of the update
    @Query("""
            UPDATE QuizQuestionPoolLectureUnit unit
            SET unit.fingerprint = :fingerprint, unit.lastBuiltAt = :builtAt, unit.updatedAt = :builtAt
            WHERE unit.course.id = :courseId AND unit.lectureUnitId = :lectureUnitId
            """)
    int updateBuildState(@Param("courseId") long courseId, @Param("lectureUnitId") long lectureUnitId, @Param("fingerprint") @Nullable String fingerprint,
            @Param("builtAt") ZonedDateTime builtAt);

    /**
     * Stores whether a lecture unit is excluded from generation, leaving what builds recorded about it untouched.
     *
     * @param courseId      the course
     * @param lectureUnitId the lecture unit
     * @param excluded      whether the unit is excluded
     * @param updatedAt     when the instructor changed it
     * @return the number of updated rows
     */
    @Modifying
    @Transactional // ok because of the update
    @Query("""
            UPDATE QuizQuestionPoolLectureUnit unit
            SET unit.excluded = :excluded, unit.updatedAt = :updatedAt
            WHERE unit.course.id = :courseId AND unit.lectureUnitId = :lectureUnitId
            """)
    int updateExcluded(@Param("courseId") long courseId, @Param("lectureUnitId") long lectureUnitId, @Param("excluded") boolean excluded,
            @Param("updatedAt") ZonedDateTime updatedAt);
}
