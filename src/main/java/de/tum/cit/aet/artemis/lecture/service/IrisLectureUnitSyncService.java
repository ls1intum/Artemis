package de.tum.cit.aet.artemis.lecture.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.util.HexFormat;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.repository.IrisLectureUnitSyncStateRepository;

@Conditional(LectureEnabled.class)
@Lazy
@Service
public class IrisLectureUnitSyncService {

    private static final String SHA_256 = "SHA-256";

    private final IrisLectureUnitSyncStateRepository repository;

    private final ApplicationEventPublisher eventPublisher;

    private final TransactionAfterCommitService transactionAfterCommitService;

    public IrisLectureUnitSyncService(IrisLectureUnitSyncStateRepository repository, ApplicationEventPublisher eventPublisher,
            TransactionAfterCommitService transactionAfterCommitService) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.transactionAfterCommitService = transactionAfterCommitService;
    }

    /**
     * Persists a pending metadata synchronization and dispatches it after the surrounding transaction commits.
     *
     * @param snapshot the current lecture unit snapshot
     */
    public void markMetadataDirtyAfterCommit(LectureContentUpdateSnapshot snapshot) {
        repository.markDirty(snapshot.lectureUnitId(), metadataHash(snapshot), null, ZonedDateTime.now());
        transactionAfterCommitService.execute(() -> eventPublisher.publishEvent(new IrisLectureUnitMetadataDirtyEvent(snapshot.lectureUnitId())));
    }

    /**
     * Persists a pending visibility synchronization and dispatches it after the surrounding transaction commits.
     *
     * @param snapshot the current lecture unit snapshot
     */
    public void markVisibilityDirtyAfterCommit(LectureContentUpdateSnapshot snapshot) {
        repository.markDirty(snapshot.lectureUnitId(), null, visibilityHash(snapshot), ZonedDateTime.now());
        transactionAfterCommitService
                .execute(() -> eventPublisher.publishEvent(new IrisLectureUnitVisibilityDirtyEvent(snapshot.lectureUnitId(), snapshot.slideHiddenUntilBySlideNumber())));
    }

    private static String metadataHash(LectureContentUpdateSnapshot snapshot) {
        MessageDigest digest = createMessageDigest();

        appendField(digest, "lectureUnitId", snapshot.lectureUnitId());
        appendField(digest, "lectureUnitName", snapshot.lectureUnitName());
        appendField(digest, "lectureName", snapshot.lectureName());
        appendField(digest, "courseName", snapshot.courseName());
        appendField(digest, "courseDescription", snapshot.courseDescription());
        return HexFormat.of().formatHex(digest.digest());
    }

    static String visibilityHash(LectureContentUpdateSnapshot snapshot) {
        MessageDigest digest = createMessageDigest();

        appendField(digest, "lectureUnitId", snapshot.lectureUnitId());
        appendField(digest, "releaseDate", instantString(snapshot.releaseDate()));
        snapshot.slideHiddenUntilBySlideNumber().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> appendSlideHiddenUntil(digest, entry));
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void appendSlideHiddenUntil(MessageDigest digest, Map.Entry<Integer, ZonedDateTime> slideHiddenUntil) {
        appendField(digest, "slideNumber", slideHiddenUntil.getKey());
        appendField(digest, "slideHiddenUntil", instantString(slideHiddenUntil.getValue()));
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

    public record IrisLectureUnitMetadataDirtyEvent(Long lectureUnitId) {
    }

    public record IrisLectureUnitVisibilityDirtyEvent(Long lectureUnitId, Map<Integer, ZonedDateTime> slideHiddenUntilBySlideNumber) {
    }
}
