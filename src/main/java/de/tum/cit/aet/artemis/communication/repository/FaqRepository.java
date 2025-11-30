package de.tum.cit.aet.artemis.communication.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.communication.domain.Faq;
import de.tum.cit.aet.artemis.communication.domain.FaqState;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Spring Data repository for the Faq entity.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface FaqRepository extends ArtemisJpaRepository<Faq, Long> {

    @EntityGraph(type = LOAD, attributePaths = "categories")
    List<Faq> findAllByCourseIdOrderByCreatedDateDesc(Long courseId);

    @Query("""
            SELECT DISTINCT faq.categories
            FROM Faq faq
            WHERE faq.course.id = :courseId
            """)
    Set<String> findAllCategoriesByCourseId(@Param("courseId") Long courseId);

    @Query("""
            SELECT DISTINCT faq.categories
            FROM Faq faq
            WHERE faq.course.id = :courseId AND faq.faqState = :faqState
            """)
    Set<String> findAllCategoriesByCourseIdAndState(@Param("courseId") Long courseId, @Param("faqState") FaqState faqState);

    @EntityGraph(type = LOAD, attributePaths = "categories")
    List<Faq> findAllByCourseIdAndFaqStateOrderByCreatedDateDesc(Long courseId, FaqState faqState);

    @Transactional // ok because of delete
    @Modifying
    void deleteAllByCourseId(Long courseId);
}
