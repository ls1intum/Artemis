package de.tum.cit.aet.artemis.lecture.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.springframework.context.annotation.Profile;
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
     * Find all slides that have a non-null hidden timestamp
     *
     * @return List of all slides with a hidden timestamp
     */
    List<Slide> findAllByHiddenNotNull();

    /**
     * Find slides for a specific attachment unit where the hidden field is not null
     * (these are the hidden slides)
     *
     * @param attachmentUnitId The ID of the attachment unit
     * @return List of hidden slides for the attachment unit
     */
    List<Slide> findByAttachmentUnitIdAndHiddenNotNull(Long attachmentUnitId);
}
