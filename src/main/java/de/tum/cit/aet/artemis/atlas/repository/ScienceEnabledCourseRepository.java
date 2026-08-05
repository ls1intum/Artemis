package de.tum.cit.aet.artemis.atlas.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.science.ScienceEnabledCourse;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

@Conditional(AtlasEnabled.class)
@Lazy
@Repository
public interface ScienceEnabledCourseRepository extends ArtemisJpaRepository<ScienceEnabledCourse, Long> {

    List<ScienceEnabledCourse> findAllByOrderByLastModifiedDateDesc();

    @Query("""
            SELECT enabledCourse
            FROM ScienceEnabledCourse enabledCourse
                JOIN FETCH enabledCourse.course
            WHERE enabledCourse.course.id = :courseId
            """)
    Optional<ScienceEnabledCourse> findByCourseId(@Param("courseId") long courseId);

    boolean existsByCourseIdAndActiveTrue(long courseId);
}
