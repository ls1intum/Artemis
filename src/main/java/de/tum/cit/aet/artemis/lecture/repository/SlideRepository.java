package de.tum.cit.aet.artemis.lecture.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

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

}
