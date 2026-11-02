package de.tum.cit.aet.artemis.lecture.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_SCHEDULING;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentVideoUnitRepository;
import de.tum.cit.aet.artemis.lecture.repository.SlideRepository;

/**
 * Generates missing student versions again.
 * <p>
 * Downloads of an attachment whose current deck hides slides fail closed while it has no student version. A regeneration that failed, for example on a temporary write
 * error, leaves exactly that state behind, and nothing else would retry it until the next visibility change. The pending work therefore needs no record of its own: it is
 * every such attachment, found by a query. Each run generates their student versions. A student version stored in the meantime, for example one an instructor submitted with a
 * new file, is never replaced. An attachment whose generation fails in {@link #MAX_ATTEMPTS} runs in a row is reported once and left alone until it drops out of the query or
 * the next restart, so a broken file cannot fill the log.
 */
@Conditional(LectureEnabled.class)
@Profile(PROFILE_SCHEDULING)
@Lazy
@Component
public class StudentVersionRepairScheduler {

    private static final Logger log = LoggerFactory.getLogger(StudentVersionRepairScheduler.class);

    static final int MAX_ATTEMPTS = 3;

    private final SlideRepository slideRepository;

    private final AttachmentVideoUnitRepository attachmentVideoUnitRepository;

    private final AttachmentService attachmentService;

    /** Failed runs per attachment video unit; a successful run removes its entry. */
    private final Map<Long, Integer> attemptsByAttachmentVideoUnitId = new ConcurrentHashMap<>();

    public StudentVersionRepairScheduler(SlideRepository slideRepository, AttachmentVideoUnitRepository attachmentVideoUnitRepository, AttachmentService attachmentService) {
        this.slideRepository = slideRepository;
        this.attachmentVideoUnitRepository = attachmentVideoUnitRepository;
        this.attachmentService = attachmentService;
    }

    /**
     * Regenerates the student version of every attachment that hides slides but has none.
     */
    @Scheduled(initialDelayString = "${artemis.lecture.student-version-repair.initial-delay:PT5M}", fixedDelayString = "${artemis.lecture.student-version-repair.interval:PT1H}")
    public void repairMissingStudentVersions() {
        List<Long> attachmentVideoUnitIds = slideRepository.findAttachmentVideoUnitIdsWithHiddenSlidesAndWithoutStudentVersion();
        // Units that got a student version, or no longer hide slides, start from zero should they need one again.
        attemptsByAttachmentVideoUnitId.keySet().retainAll(new HashSet<>(attachmentVideoUnitIds));
        for (long attachmentVideoUnitId : attachmentVideoUnitIds) {
            int failedAttempts = attemptsByAttachmentVideoUnitId.getOrDefault(attachmentVideoUnitId, 0);
            if (failedAttempts >= MAX_ATTEMPTS) {
                continue;
            }
            if (repair(attachmentVideoUnitId, failedAttempts + 1)) {
                attemptsByAttachmentVideoUnitId.remove(attachmentVideoUnitId);
            }
            else {
                attemptsByAttachmentVideoUnitId.put(attachmentVideoUnitId, failedAttempts + 1);
            }
        }
    }

    /**
     * @return whether the unit needs nothing more: it has a student version now, no longer needs one, or cannot get one because its file is not stored here
     */
    private boolean repair(long attachmentVideoUnitId, int attempt) {
        try {
            Attachment attachment = attachmentVideoUnitRepository.findWithLectureAndCourseAndAttachmentById(attachmentVideoUnitId).map(AttachmentVideoUnit::getAttachment)
                    .orElse(null);
            if (attachment == null) {
                return true;
            }
            attachmentService.generateMissingStudentVersion(attachment);
            if (attachment.getStudentVersion() != null) {
                log.info("Generated the missing student version of attachment {}", attachment.getId());
            }
            return true;
        }
        catch (RuntimeException exception) {
            if (attempt >= MAX_ATTEMPTS) {
                log.error("Could not generate the missing student version of attachment video unit {} in {} attempts; its downloads stay closed until its slides change: {}",
                        attachmentVideoUnitId, attempt, exception.getMessage(), exception);
            }
            else {
                log.warn("Could not generate the missing student version of attachment video unit {} (attempt {}): {}", attachmentVideoUnitId, attempt, exception.getMessage());
            }
            return false;
        }
    }
}
