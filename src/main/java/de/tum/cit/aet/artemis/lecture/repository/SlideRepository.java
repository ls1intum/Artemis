package de.tum.cit.aet.artemis.lecture.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.lecture.domain.Slide;

/**
 * Spring Data JPA repository for the Attachment Unit entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface SlideRepository extends ArtemisJpaRepository<Slide, Long> {

    Slide findSlideByAttachmentUnitIdAndSlideNumber(Long attachmentUnitId, Integer slideNumber);

    /**
     * Finds the list of hidden slide IDs for a specific attachment unit ID.
     *
     * @param attachmentUnitId The ID of the attachment unit.
     * @return List of hidden slide IDs.
     */
    @Query("""
            SELECT s.slideNumber
            FROM Slide s
            WHERE s.attachmentUnit.id = :attachmentUnitId AND s.hidden IS NOT NULL
            """)
    List<Integer> findHiddenSlideNumbersByAttachmentUnitId(@Param("attachmentUnitId") Long attachmentUnitId);

}
