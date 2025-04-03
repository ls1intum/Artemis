package de.tum.cit.aet.artemis.lecture.repository;

import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.domain.Slide;

/**
 * Spring Data JPA repository for the Attachment Unit entity.
 */
@Conditional(LectureEnabled.class)
@Repository
public interface SlideRepository extends ArtemisJpaRepository<Slide, Long> {

    Slide findSlideByAttachmentUnitIdAndSlideNumber(Long attachmentUnitId, Integer slideNumber);

}
