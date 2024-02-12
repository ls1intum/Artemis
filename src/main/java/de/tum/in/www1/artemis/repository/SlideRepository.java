package de.tum.in.www1.artemis.repository;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.lecture.Slide;

/**
 * Spring Data JPA repository for the Attachment Unit entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface SlideRepository extends JpaRepository<Slide, Long> {

    List<Slide> findAllByAttachmentUnitId(Long attachmentUnitId);

    Slide findSlideByAttachmentUnitIdAndSlideNumber(Long attachmentUnitId, Integer slideNumber);

}
