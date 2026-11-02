package de.tum.cit.aet.artemis.lecture.service;

import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentVideoUnitRepository;
import de.tum.cit.aet.artemis.lecture.repository.SlideRepository;

/**
 * Marks the Iris visibility of attachment video units as dirty from their saved slide state.
 * <p>
 * Used where the visibility Iris was last told about may differ from what is saved: after an exercise due date moved the hidden date of linked slides, and after an
 * asynchronous slide split finished or failed. The snapshot is built from the current deck of the unit, so superseded slides of an earlier file never reach Iris.
 */
@Conditional(LectureEnabled.class)
@Lazy
@Service
public class LectureUnitVisibilitySyncService {

    private static final Logger log = LoggerFactory.getLogger(LectureUnitVisibilitySyncService.class);

    private final SlideRepository slideRepository;

    private final AttachmentVideoUnitRepository attachmentVideoUnitRepository;

    private final IrisLectureUnitSyncService irisLectureUnitSyncService;

    public LectureUnitVisibilitySyncService(SlideRepository slideRepository, AttachmentVideoUnitRepository attachmentVideoUnitRepository,
            IrisLectureUnitSyncService irisLectureUnitSyncService) {
        this.slideRepository = slideRepository;
        this.attachmentVideoUnitRepository = attachmentVideoUnitRepository;
        this.irisLectureUnitSyncService = irisLectureUnitSyncService;
    }

    /**
     * Marks the visibility of each given attachment video unit as dirty. A failure for one unit is logged and does not stop the others.
     *
     * @param attachmentVideoUnitIds the ids of the attachment video units whose slide visibility changed
     */
    public void markVisibilityDirty(Collection<Long> attachmentVideoUnitIds) {
        attachmentVideoUnitIds.forEach(this::markVisibilityDirty);
    }

    /**
     * Marks the visibility of an attachment video unit as dirty, based on its saved current slide deck. A failure is logged and not rethrown, because callers run this after
     * their own changes are already saved.
     *
     * @param attachmentVideoUnitId the id of the attachment video unit
     */
    public void markVisibilityDirty(long attachmentVideoUnitId) {
        try {
            attachmentVideoUnitRepository.findWithLectureAndCourseAndAttachmentById(attachmentVideoUnitId).map(this::buildVisibilitySnapshot)
                    .ifPresent(irisLectureUnitSyncService::markVisibilityDirty);
        }
        catch (RuntimeException exception) {
            log.error("Failed to mark the Iris visibility of attachment video unit {} as dirty: {}", attachmentVideoUnitId, exception.getMessage(), exception);
        }
    }

    private LectureContentUpdateSnapshot buildVisibilitySnapshot(AttachmentVideoUnit unit) {
        return new LectureContentUpdateSnapshot(unit.getId(), null, null, null, null, null, null, null, unit.resolveReleaseDate(),
                SlideVisibilitySnapshotHelper.toSortedHiddenUntilBySlideNumber(slideRepository.findAllByAttachmentVideoUnitId(unit.getId())));
    }
}
