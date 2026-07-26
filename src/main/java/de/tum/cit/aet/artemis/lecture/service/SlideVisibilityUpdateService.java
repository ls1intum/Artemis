package de.tum.cit.aet.artemis.lecture.service;

import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.dto.HiddenPageInfoDTO;

/**
 * Applies slide visibility and the derived student PDF as one transaction.
 */
@Conditional(LectureEnabled.class)
@Lazy
@Service
public class SlideVisibilityUpdateService {

    private final SlideSplitterService slideSplitterService;

    private final AttachmentService attachmentService;

    public SlideVisibilityUpdateService(SlideSplitterService slideSplitterService, AttachmentService attachmentService) {
        this.slideSplitterService = slideSplitterService;
        this.attachmentService = attachmentService;
    }

    /**
     * Updates persisted slide visibility and regenerates the matching student PDF atomically.
     *
     * @param attachmentVideoUnit the attachment video unit to update
     * @param hiddenPages         the complete hidden-page metadata
     */
    @Transactional
    public void updateVisibilityAndStudentVersion(AttachmentVideoUnit attachmentVideoUnit, List<HiddenPageInfoDTO> hiddenPages) {
        slideSplitterService.updateSlideVisibility(attachmentVideoUnit, hiddenPages);
        attachmentService.regenerateStudentVersion(attachmentVideoUnit.getAttachment());
    }
}
