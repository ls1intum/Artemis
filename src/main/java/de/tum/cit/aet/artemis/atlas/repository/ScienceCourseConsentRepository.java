package de.tum.cit.aet.artemis.atlas.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.science.ScienceCourseConsent;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

@Conditional(AtlasEnabled.class)
@Lazy
@Repository
public interface ScienceCourseConsentRepository extends ArtemisJpaRepository<ScienceCourseConsent, Long> {

    Optional<ScienceCourseConsent> findByUserIdAndCourseId(long userId, long courseId);

    @Query("""
            SELECT consent
            FROM ScienceCourseConsent consent
                JOIN FETCH consent.course
            WHERE consent.user.id = :userId
                AND consent.course.id IN :courseIds
            """)
    List<ScienceCourseConsent> findAllByUserIdAndCourseIdIn(@Param("userId") long userId, @Param("courseIds") Set<Long> courseIds);

    List<ScienceCourseConsent> findAllByUserIdOrderByLastModifiedDateDesc(long userId);

    boolean existsByUserIdAndCourseIdAndActiveTrue(long userId, long courseId);
}
