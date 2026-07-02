package de.tum.cit.aet.artemis.lecture.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.HexFormat;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.IrisLectureUnitSyncState;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.Slide;
import de.tum.cit.aet.artemis.lecture.repository.IrisLectureUnitSyncStateRepository;

@Conditional(LectureEnabled.class)
@Lazy
@Service
public class IrisLectureUnitSyncService {

    private static final String SHA_256 = "SHA-256";

    private final IrisLectureUnitSyncStateRepository repository;

    private final ApplicationEventPublisher eventPublisher;

    public IrisLectureUnitSyncService(IrisLectureUnitSyncStateRepository repository, ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    public void markMetadataDirtyAfterCommit(AttachmentVideoUnit unit) {
        IrisLectureUnitSyncState state = stateFor(unit.getId());
        state.setMetadataHash(metadataHash(unit));
        markDirty(state);
        repository.save(state);
        eventPublisher.publishEvent(new IrisLectureUnitMetadataDirtyEvent(unit.getId()));
    }

    public void markVisibilityDirtyAfterCommit(AttachmentVideoUnit unit) {
        IrisLectureUnitSyncState state = stateFor(unit.getId());
        state.setVisibilityHash(visibilityHash(unit));
        markDirty(state);
        repository.save(state);
        eventPublisher.publishEvent(new IrisLectureUnitVisibilityDirtyEvent(unit.getId()));
    }

    private IrisLectureUnitSyncState stateFor(Long lectureUnitId) {
        return repository.findByLectureUnitId(lectureUnitId).orElseGet(() -> {
            IrisLectureUnitSyncState state = new IrisLectureUnitSyncState();
            state.setLectureUnitId(lectureUnitId);
            state.setStatus(IrisLectureUnitSyncState.STATUS_CLEAN);
            return state;
        });
    }

    private static void markDirty(IrisLectureUnitSyncState state) {
        state.setStatus(IrisLectureUnitSyncState.STATUS_DIRTY);
        state.setNextRetryAt(ZonedDateTime.now());
    }

    private static String metadataHash(AttachmentVideoUnit unit) {
        MessageDigest digest = createMessageDigest();
        Lecture lecture = unit.getLecture();
        Course course = lecture != null ? lecture.getCourse() : null;

        appendField(digest, "lectureUnitId", unit.getId());
        appendField(digest, "lectureUnitName", unit.getName());
        appendField(digest, "lectureTitle", lecture != null ? lecture.getTitle() : null);
        appendField(digest, "courseTitle", course != null ? course.getTitle() : null);
        appendField(digest, "courseDescription", course != null ? course.getDescription() : null);
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String visibilityHash(AttachmentVideoUnit unit) {
        MessageDigest digest = createMessageDigest();

        appendField(digest, "lectureUnitId", unit.getId());
        appendField(digest, "releaseDate", instantString(unit.getReleaseDate()));
        unit.getSlides().stream().sorted(slideComparator()).forEach(slide -> appendSlide(digest, slide));
        return HexFormat.of().formatHex(digest.digest());
    }

    private static Comparator<Slide> slideComparator() {
        return Comparator.comparingInt(Slide::getSlideNumber).thenComparing(slide -> nullSafeString(slide.getId())).thenComparing(slide -> instantString(slide.getHidden()))
                .thenComparing(slide -> nullSafeString(slide.getExercise() != null ? slide.getExercise().getId() : null));
    }

    private static void appendSlide(MessageDigest digest, Slide slide) {
        appendField(digest, "slideNumber", slide.getSlideNumber());
        appendField(digest, "slideHidden", instantString(slide.getHidden()));
        appendField(digest, "slideExerciseId", slide.getExercise() != null ? slide.getExercise().getId() : null);
    }

    private static MessageDigest createMessageDigest() {
        try {
            return MessageDigest.getInstance(SHA_256);
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Could not initialize SHA-256 digest", e);
        }
    }

    private static void appendField(MessageDigest digest, String label, Object value) {
        digest.update(label.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) ':');
        if (value == null) {
            digest.update("-1".getBytes(StandardCharsets.UTF_8));
            digest.update((byte) ':');
            digest.update((byte) '\n');
            return;
        }
        String stringValue = value.toString();
        digest.update(Integer.toString(stringValue.length()).getBytes(StandardCharsets.UTF_8));
        digest.update((byte) ':');
        digest.update(stringValue.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) '\n');
    }

    private static String instantString(ZonedDateTime value) {
        return value != null ? value.toInstant().toString() : null;
    }

    private static String nullSafeString(Long value) {
        return value != null ? value.toString() : "";
    }

    public record IrisLectureUnitMetadataDirtyEvent(Long lectureUnitId) {
    }

    public record IrisLectureUnitVisibilityDirtyEvent(Long lectureUnitId) {
    }
}
