package de.tum.cit.aet.artemis.lecture.service;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.domain.LectureContentUpdateKind;

@Conditional(LectureEnabled.class)
@Lazy
@Service
public class LectureContentUpdateClassifierService {

    /**
     * Classifies all Pyris update dimensions touched by a lecture unit snapshot change.
     *
     * @param before           the previous detached lecture unit snapshot
     * @param after            the current detached lecture unit snapshot, or null if the unit was deleted
     * @param fileUpdateResult the attachment file update result, if an attachment update was attempted
     * @return all required Pyris update kinds, or an empty set if no update is required
     */
    public Set<LectureContentUpdateKind> classifyAll(LectureContentUpdateSnapshot before, LectureContentUpdateSnapshot after, AttachmentFileUpdateResult fileUpdateResult) {
        if (after == null) {
            return Set.of(LectureContentUpdateKind.DELETE);
        }
        Objects.requireNonNull(before, "before");
        Map<LectureContentUpdateKind, Boolean> changes = new EnumMap<>(LectureContentUpdateKind.class);
        changes.put(LectureContentUpdateKind.CONTENT, isContentUpdate(before, after, fileUpdateResult));
        changes.put(LectureContentUpdateKind.VISIBILITY, isVisibilityUpdate(before, after));
        changes.put(LectureContentUpdateKind.METADATA, isMetadataUpdate(before, after));
        return changes.entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey).collect(Collectors.toSet());
    }

    private static boolean isContentUpdate(LectureContentUpdateSnapshot before, LectureContentUpdateSnapshot after, AttachmentFileUpdateResult fileUpdateResult) {
        boolean hasAttachmentFileContentUpdate = Optional.ofNullable(fileUpdateResult)
                .map(result -> List.of(result.fileBytesChanged(), result.attachmentAdded(), result.attachmentRemoved()).contains(true)).orElse(false);
        return hasAttachmentFileContentUpdate || !Arrays.asList(before.attachmentVersion(), before.attachmentLink(), before.videoSource())
                .equals(Arrays.asList(after.attachmentVersion(), after.attachmentLink(), after.videoSource()));
    }

    private static boolean isVisibilityUpdate(LectureContentUpdateSnapshot before, LectureContentUpdateSnapshot after) {
        return !visibilitySignature(before).equals(visibilitySignature(after));
    }

    private static List<Object> visibilitySignature(LectureContentUpdateSnapshot snapshot) {
        Map<Integer, Optional<java.time.Instant>> slideVisibility = snapshot.slideHiddenUntilBySlideNumber().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> Optional.ofNullable(entry.getValue()).map(ZonedDateTime::toInstant)));
        return List.of(Optional.ofNullable(snapshot.releaseDate()).map(ZonedDateTime::toInstant), slideVisibility);
    }

    private static boolean isMetadataUpdate(LectureContentUpdateSnapshot before, LectureContentUpdateSnapshot after) {
        return !Arrays.asList(before.lectureUnitName(), before.lectureName(), before.courseName(), before.courseDescription())
                .equals(Arrays.asList(after.lectureUnitName(), after.lectureName(), after.courseName(), after.courseDescription()));
    }
}
