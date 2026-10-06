package de.tum.cit.aet.artemis.lecture.api;

import java.util.List;
import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentType;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.dto.AttachmentFileLocationDTO;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentRepository;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentVideoUnitRepository;
import de.tum.cit.aet.artemis.lecture.repository.SlideRepository;

/**
 * API for managing lecture attachments.
 */
@Conditional(LectureEnabled.class)
@Controller
@Lazy
public class LectureAttachmentApi extends AbstractLectureApi {

    private final AttachmentRepository attachmentRepository;

    private final AttachmentVideoUnitRepository attachmentVideoUnitRepository;

    private final SlideRepository slideRepository;

    public LectureAttachmentApi(AttachmentRepository attachmentRepository, AttachmentVideoUnitRepository attachmentVideoUnitRepository, SlideRepository slideRepository) {
        this.attachmentRepository = attachmentRepository;
        this.attachmentVideoUnitRepository = attachmentVideoUnitRepository;
        this.slideRepository = slideRepository;
    }

    public AttachmentVideoUnit findAttachmentVideoUnitByIdElseThrow(long id) {
        return attachmentVideoUnitRepository.findByIdElseThrow(id);
    }

    public List<AttachmentVideoUnit> findAllByLectureIdAndAttachmentTypeElseThrow(long lectureId, AttachmentType type) {
        return attachmentVideoUnitRepository.findAllByLectureIdAndAttachmentTypeElseThrow(lectureId, type);
    }

    public List<Attachment> findAllInLecture(long lectureId) {
        return attachmentRepository.findAllInLecture(lectureId);
    }

    public List<AttachmentFileLocationDTO> findAttachmentFileLocationsAfter(long minimumAttachmentId, int limit) {
        return attachmentRepository.findAttachmentFileLocationsAfter(minimumAttachmentId, Pageable.ofSize(limit));
    }

    /**
     * Checks whether the current slide deck of an attachment video unit hides any slide.
     *
     * @param attachmentVideoUnitId the id of the attachment video unit
     * @return whether at least one current slide is hidden
     */
    public boolean hasHiddenSlides(long attachmentVideoUnitId) {
        return slideRepository.existsByAttachmentVideoUnitIdAndHiddenNotNullAndSupersededIsFalse(attachmentVideoUnitId);
    }

    /**
     * Finds which of the given attachment video units hide at least one slide of their current deck.
     *
     * @param attachmentVideoUnitIds the ids of the attachment video units to check
     * @return the ids of the units with at least one current hidden slide
     */
    public Set<Long> findAttachmentVideoUnitIdsWithHiddenSlides(Set<Long> attachmentVideoUnitIds) {
        if (attachmentVideoUnitIds.isEmpty()) {
            return Set.of();
        }
        return slideRepository.findAttachmentVideoUnitIdsWithHiddenSlides(attachmentVideoUnitIds);
    }
}
