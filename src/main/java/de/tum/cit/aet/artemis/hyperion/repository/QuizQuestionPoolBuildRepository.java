package de.tum.cit.aet.artemis.hyperion.repository;

import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionPoolBuild;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolLastCompletedBuildDTO;

/**
 * Spring Data repository for quiz question pool builds.
 */
@Lazy
@Repository
@Conditional(HyperionEnabled.class)
public interface QuizQuestionPoolBuildRepository extends ArtemisJpaRepository<QuizQuestionPoolBuild, Long> {

    @Query("""
            SELECT build
            FROM QuizQuestionPoolBuild build
            WHERE build.course.id = :courseId
            ORDER BY build.startedAt DESC, build.id DESC
            """)
    List<QuizQuestionPoolBuild> findAllByCourseIdNewestFirst(@Param("courseId") long courseId, Pageable pageable);

    /**
     * Finds, per course, when its most recent build that ran to its end finished. Builds that recorded a failure, including those that
     * stopped at the end of the overnight window, and builds still running are left out.
     *
     * @return one entry per course with at least one such build
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolLastCompletedBuildDTO(build.course.id, MAX(build.finishedAt))
            FROM QuizQuestionPoolBuild build
            WHERE build.finishedAt IS NOT NULL
                AND build.failure IS NULL
            GROUP BY build.course.id
            """)
    List<QuizQuestionPoolLastCompletedBuildDTO> findLastCompletedBuildPerCourse();
}
