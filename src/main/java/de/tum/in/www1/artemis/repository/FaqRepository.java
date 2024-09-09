package de.tum.in.www1.artemis.repository;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.Faq;
import de.tum.in.www1.artemis.repository.base.ArtemisJpaRepository;

/**
 * Spring Data repository for the Faq entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface FaqRepository extends ArtemisJpaRepository<Faq, Long> {

    @Query("""
            SELECT faq
            FROM Faq faq
            WHERE faq.course.id = :courseId
            """)
    Set<Faq> findAllByCourseId(@Param("courseId") Long courseId);

    @Query("""
            SELECT distinct faq.categories
            FROM Faq faq
            WHERE faq.course.id = :courseId
            """)
    Set<String> findAllCategoriesByCourseId(@Param("courseId") Long courseId);

    @Transactional
    @Modifying
    @Query("""
            DELETE
            FROM Faq faq
            WHERE faq.course.id = :courseId
            """)
    void deleteAllByCourseId(@Param("courseId") Long courseId);

}
