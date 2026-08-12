package de.tum.cit.aet.artemis.lecture.service;

import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.Slide;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentVideoUnitRepository;
import de.tum.cit.aet.artemis.lecture.repository.SlideRepository;

@Conditional(LectureEnabled.class)
@Lazy
@Service
public class LectureUnitVisibilitySyncService {

    private final SlideRepository slideRepository;

    private final AttachmentVideoUnitRepository attachmentVideoUnitRepository;

    private final IrisLectureUnitSyncService irisLectureUnitSyncService;

    private final TransactionTemplate requiresNewTransactionTemplate;

    public LectureUnitVisibilitySyncService(SlideRepository slideRepository, AttachmentVideoUnitRepository attachmentVideoUnitRepository,
            IrisLectureUnitSyncService irisLectureUnitSyncService, PlatformTransactionManager transactionManager) {
        this.slideRepository = slideRepository;
        this.attachmentVideoUnitRepository = attachmentVideoUnitRepository;
        this.irisLectureUnitSyncService = irisLectureUnitSyncService;
        this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Marks an attachment video unit as visibility-dirty using its committed slide state.
     *
     * @param attachmentVideoUnitId the attachment video unit id
     */
    public void markVisibilityDirtyForAttachmentVideoUnit(long attachmentVideoUnitId) {
        requiresNewTransactionTemplate.executeWithoutResult(status -> attachmentVideoUnitRepository.findWithLectureAndCourseAndAttachmentById(attachmentVideoUnitId)
                .map(this::buildSnapshot).ifPresent(irisLectureUnitSyncService::markVisibilityDirtyAfterCommit));
    }

    /**
     * Locks the affected attachment video units in a stable order before slide rows are updated. Hidden-page edits already lock the unit before its slides; using the same order
     * for exercise-driven visibility updates prevents deadlocks with concurrent edits.
     *
     * @param relatedSlides slides whose units must be locked
     */
    void lockAffectedAttachmentVideoUnits(List<Slide> relatedSlides) {
        relatedSlides.stream().map(Slide::getAttachmentVideoUnit).filter(java.util.Objects::nonNull).map(AttachmentVideoUnit::getId).filter(java.util.Objects::nonNull).distinct()
                .sorted().forEach(attachmentVideoUnitRepository::findByIdForUpdate);
    }

    void markVisibilityDirtyForSlides(List<Slide> relatedSlides) {
        Map<Long, AttachmentVideoUnit> affectedUnitsById = new LinkedHashMap<>();
        relatedSlides.forEach(slide -> {
            AttachmentVideoUnit unit = slide.getAttachmentVideoUnit();
            if (unit != null && unit.getId() != null) {
                affectedUnitsById.putIfAbsent(unit.getId(), unit);
            }
        });

        affectedUnitsById.values().stream().map(this::buildSnapshot).forEach(irisLectureUnitSyncService::markVisibilityDirtyAfterCommit);
    }

    private LectureContentUpdateSnapshot buildSnapshot(AttachmentVideoUnit unit) {
        Lecture lecture = unit.getLecture();
        Course course = lecture != null ? lecture.getCourse() : null;
        Attachment attachment = unit.getAttachment();

        return new LectureContentUpdateSnapshot(unit.getId(), unit.getName(), lecture != null ? lecture.getTitle() : null, course != null ? course.getTitle() : null,
                course != null ? course.getDescription() : null, attachment != null ? attachment.getVersion() : null, attachment != null ? attachment.getLink() : null,
                unit.getVideoSource(), resolveReleaseDate(unit, attachment), buildSlideHiddenUntilBySlideNumber(unit.getId()));
    }

    private Map<Integer, ZonedDateTime> buildSlideHiddenUntilBySlideNumber(Long attachmentVideoUnitId) {
        var slideHiddenUntilBySlideNumber = new LinkedHashMap<Integer, ZonedDateTime>();
        slideRepository.findAllByAttachmentVideoUnitId(attachmentVideoUnitId).stream().sorted(Comparator.comparingInt(Slide::getSlideNumber))
                .forEach(slide -> slideHiddenUntilBySlideNumber.put(slide.getSlideNumber(), slide.getHidden()));
        return slideHiddenUntilBySlideNumber;
    }

    private static ZonedDateTime resolveReleaseDate(AttachmentVideoUnit unit, Attachment attachment) {
        if (unit.getReleaseDate() != null) {
            return unit.getReleaseDate();
        }
        return attachment != null ? attachment.getReleaseDate() : null;
    }
}
