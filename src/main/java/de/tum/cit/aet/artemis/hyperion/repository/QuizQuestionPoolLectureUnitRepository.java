package de.tum.cit.aet.artemis.hyperion.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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

    @Query("""
            SELECT unit
            FROM QuizQuestionPoolLectureUnit unit
            WHERE unit.course.id = :courseId AND unit.lectureUnitId = :lectureUnitId
            """)
    Optional<QuizQuestionPoolLectureUnit> findByCourseIdAndLectureUnitId(@Param("courseId") long courseId, @Param("lectureUnitId") long lectureUnitId);
}
