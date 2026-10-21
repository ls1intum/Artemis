package de.tum.cit.aet.artemis.communication.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.communication.domain.Faq;
import de.tum.cit.aet.artemis.communication.domain.FaqState;
import de.tum.cit.aet.artemis.communication.dto.FaqFingerprintInputDTO;
import de.tum.cit.aet.artemis.communication.dto.FaqImportCandidateDTO;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Spring Data repository for the Faq entity.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface FaqRepository extends ArtemisJpaRepository<Faq, Long> {

    /**
     * Searches FAQs outside the target course using the same visibility as the FAQ read endpoints.
     * Published FAQs require course membership; unpublished FAQs require at least teaching assistant access.
     *
     * @param targetCourseId the course to exclude
     * @param userId         the current user
     * @param isAdmin        whether administrator elevation is active for this request
     * @param searchTerm     a literal substring of the question, answer, or course name
     * @param pageable       bounded page and stable sort
     * @return the visible FAQs and their course names
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.communication.dto.FaqImportCandidateDTO(f.id, f.questionTitle, f.questionAnswer, f.faqState, c.id, c.title, c.semester)
            FROM Faq f JOIN f.course c
            WHERE c.id <> :targetCourseId
                AND (:isAdmin = TRUE OR EXISTS (
                    SELECT ucr FROM UserCourseRole ucr
                    WHERE ucr.user.id = :userId AND ucr.course.id = c.id
                        AND (f.faqState = de.tum.cit.aet.artemis.communication.domain.FaqState.ACCEPTED
                            OR ucr.role IN (de.tum.cit.aet.artemis.core.domain.CourseRole.TEACHING_ASSISTANT,
                                de.tum.cit.aet.artemis.core.domain.CourseRole.EDITOR, de.tum.cit.aet.artemis.core.domain.CourseRole.INSTRUCTOR))))
                AND (:searchTerm = '' OR LOCATE(LOWER(:searchTerm), LOWER(f.questionTitle)) > 0
                    OR LOCATE(LOWER(:searchTerm), LOWER(f.questionAnswer)) > 0
                    OR LOCATE(LOWER(:searchTerm), LOWER(c.title)) > 0)
            """)
    Page<FaqImportCandidateDTO> findForImport(@Param("targetCourseId") long targetCourseId, @Param("userId") long userId, @Param("isAdmin") boolean isAdmin,
            @Param("searchTerm") String searchTerm, Pageable pageable);

    @EntityGraph(type = LOAD, attributePaths = "categories")
    List<Faq> findAllByCourseIdOrderByCreatedDateDesc(Long courseId);

    List<Faq> findAllByCourseId(Long courseId);

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

    long countByCourseId(Long courseId);

    long countByCourseIdAndFaqState(Long courseId, FaqState faqState);

    /**
     * The fields of every FAQ in the given state that decide what Iris must hold for it, without loading the entities and their categories.
     *
     * @param faqState the state, ACCEPTED for the FAQs Iris may answer with
     * @return one entry per FAQ in that state
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.communication.dto.FaqFingerprintInputDTO(faq.id, faq.course.id, faq.questionTitle, faq.questionAnswer)
            FROM Faq faq
            WHERE faq.faqState = :faqState
            """)
    List<FaqFingerprintInputDTO> findFingerprintInputsByFaqState(@Param("faqState") FaqState faqState);
}
