package de.tum.cit.aet.artemis.lecture.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentType;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentUnit;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentRepository;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentUnitRepository;

/**
 * API for managing lecture attachments.
 */
@Profile(PROFILE_CORE)
@Controller
public class LectureAttachmentApi extends AbstractLectureApi {

    private final AttachmentRepository attachmentRepository;

    private final AttachmentUnitRepository attachmentUnitRepository;

    public LectureAttachmentApi(AttachmentRepository attachmentRepository, AttachmentUnitRepository attachmentUnitRepository) {
        this.attachmentRepository = attachmentRepository;
        this.attachmentUnitRepository = attachmentUnitRepository;
    }

    public AttachmentUnit findAttachmentUnitByIdElseThrow(long id) {
        return attachmentUnitRepository.findByIdElseThrow(id);
    }

    public Attachment findAttachmentByIdElseThrow(long id) {
        return attachmentRepository.findByIdElseThrow(id);
    }

    public List<AttachmentUnit> findAllByLectureIdAndAttachmentTypeElseThrow(long lectureId, AttachmentType type) {
        return attachmentUnitRepository.findAllByLectureIdAndAttachmentTypeElseThrow(lectureId, type);
    }

    public List<Attachment> findAllByLectureId(long lectureId) {
        return attachmentRepository.findAllByLectureId(lectureId);
    }
}
