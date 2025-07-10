package de.tum.cit.aet.artemis.lecture.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentType;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentRepository;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentVideoUnitRepository;

/**
 * API for managing lecture attachments.
 */
@Profile(PROFILE_CORE)
@Controller
@Lazy
public class LectureAttachmentApi extends AbstractLectureApi {

    private final AttachmentRepository attachmentRepository;

    private final AttachmentVideoUnitRepository attachmentVideoUnitRepository;

    public LectureAttachmentApi(AttachmentRepository attachmentRepository, AttachmentVideoUnitRepository attachmentVideoUnitRepository) {
        this.attachmentRepository = attachmentRepository;
        this.attachmentVideoUnitRepository = attachmentVideoUnitRepository;
    }

    public AttachmentVideoUnit findAttachmentVideoUnitByIdElseThrow(long id) {
        return attachmentVideoUnitRepository.findByIdElseThrow(id);
    }

    public Attachment findAttachmentByIdElseThrow(long id) {
        return attachmentRepository.findByIdElseThrow(id);
    }

    public List<AttachmentVideoUnit> findAllByLectureIdAndAttachmentTypeElseThrow(long lectureId, AttachmentType type) {
        return attachmentVideoUnitRepository.findAllByLectureIdAndAttachmentTypeElseThrow(lectureId, type);
    }

    public List<Attachment> findAllByLectureId(long lectureId) {
        return attachmentRepository.findAllByLectureId(lectureId);
    }
}
