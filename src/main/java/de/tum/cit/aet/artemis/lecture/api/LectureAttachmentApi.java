package de.tum.cit.aet.artemis.lecture.api;

import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentType;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentUnit;
import de.tum.cit.aet.artemis.lecture.domain.Slide;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentRepository;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentUnitRepository;
import de.tum.cit.aet.artemis.lecture.repository.SlideRepository;

/**
 * API for managing lecture attachments.
 */
@Conditional(LectureEnabled.class)
@Controller
public class LectureAttachmentApi extends AbstractLectureApi {

    private final AttachmentRepository attachmentRepository;

    private final AttachmentUnitRepository attachmentUnitRepository;

    private final SlideRepository slideRepository;

    public LectureAttachmentApi(AttachmentRepository attachmentRepository, AttachmentUnitRepository attachmentUnitRepository, SlideRepository slideRepository) {
        this.attachmentRepository = attachmentRepository;
        this.attachmentUnitRepository = attachmentUnitRepository;
        this.slideRepository = slideRepository;
    }

    public AttachmentUnit findAttachmentUnitByIdElseThrow(long id) {
        return attachmentUnitRepository.findByIdElseThrow(id);
    }

    public Attachment findAttachmentByIdElseThrow(long id) {
        return attachmentRepository.findByIdElseThrow(id);
    }

    public Slide findSlideByAttachmentUnitIdAndSlideNumber(Long attachmentUnitId, Integer slideNumber) {
        return slideRepository.findSlideByAttachmentUnitIdAndSlideNumber(attachmentUnitId, slideNumber);
    }

    public List<AttachmentUnit> findAllByLectureIdAndAttachmentTypeElseThrow(long lectureId, AttachmentType type) {
        return attachmentUnitRepository.findAllByLectureIdAndAttachmentTypeElseThrow(lectureId, type);
    }

    public List<Attachment> findAllByLectureId(long lectureId) {
        return attachmentRepository.findAllByLectureId(lectureId);
    }
}
