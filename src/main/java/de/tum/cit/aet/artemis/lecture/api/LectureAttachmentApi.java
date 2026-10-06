package de.tum.cit.aet.artemis.lecture.api;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
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
     * Fails closed for an attachment video unit without a student version: if its current deck hides any slide, the full file must not be served to students. This is the
     * case while a student version is being regenerated or after its regeneration failed.
     *
     * @param attachmentVideoUnitId the id of the attachment video unit whose attachment has no student version
     * @throws EntityNotFoundException if the current deck of the unit hides at least one slide
     */
    public void ensureStudentVersionAvailable(long attachmentVideoUnitId) {
        if (slideRepository.existsByAttachmentVideoUnitIdAndHiddenNotNullAndSupersededIsFalse(attachmentVideoUnitId)) {
            throw new EntityNotFoundException("Student version", attachmentVideoUnitId);
        }
    }

    /**
     * Fails closed for a set of attachment video units: if any of them has no student version but hides slides of its current deck, none of their files may be merged for
     * students. See {@link #ensureStudentVersionAvailable(long)}.
     *
     * @param attachmentVideoUnits the attachment video units whose files are about to be served
     * @throws EntityNotFoundException if a unit without student version hides at least one slide of its current deck
     */
    public void ensureStudentVersionsAvailable(Collection<AttachmentVideoUnit> attachmentVideoUnits) {
        Set<Long> unitIdsWithoutStudentVersion = attachmentVideoUnits.stream().filter(unit -> unit.getAttachment() != null && unit.getAttachment().getStudentVersion() == null)
                .map(AttachmentVideoUnit::getId).collect(Collectors.toSet());
        if (unitIdsWithoutStudentVersion.isEmpty()) {
            return;
        }
        slideRepository.findAttachmentVideoUnitIdsWithHiddenSlides(unitIdsWithoutStudentVersion).stream().findFirst().ifPresent(unitId -> {
            throw new EntityNotFoundException("Student version", unitId);
        });
    }
}
