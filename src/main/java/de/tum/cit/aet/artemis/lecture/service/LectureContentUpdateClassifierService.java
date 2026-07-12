package de.tum.cit.aet.artemis.lecture.service;

import java.util.Objects;
import java.util.Set;

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
     * Classifies the highest-priority Pyris update dimension touched by a lecture unit change.
     *
     * @param before           the previous lecture unit snapshot
     * @param after            the current lecture unit snapshot, or null if the unit was deleted
     * @param fileUpdateResult the attachment file update result
     * @return the highest-priority update kind, or {@link LectureContentUpdateKind#NONE}
     */
    public LectureContentUpdateKind classify(LectureContentUpdateSnapshot before, LectureContentUpdateSnapshot after, AttachmentFileUpdateResult fileUpdateResult) {
        Set<LectureContentUpdateKind> updateKinds = classifyAll(before, after, fileUpdateResult);
        if (updateKinds.contains(LectureContentUpdateKind.DELETE)) {
            return LectureContentUpdateKind.DELETE;
        }
        if (updateKinds.contains(LectureContentUpdateKind.CONTENT)) {
            return LectureContentUpdateKind.CONTENT;
        }
        if (updateKinds.contains(LectureContentUpdateKind.VISIBILITY)) {
            return LectureContentUpdateKind.VISIBILITY;
        }
        if (updateKinds.contains(LectureContentUpdateKind.METADATA)) {
            return LectureContentUpdateKind.METADATA;
        }
        return LectureContentUpdateKind.NONE;
    }

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
        var updateKinds = java.util.EnumSet.noneOf(LectureContentUpdateKind.class);
        if (isContentUpdate(before, after, fileUpdateResult)) {
            updateKinds.add(LectureContentUpdateKind.CONTENT);
        }
        if (isVisibilityUpdate(before, after)) {
            updateKinds.add(LectureContentUpdateKind.VISIBILITY);
        }
        if (isMetadataUpdate(before, after)) {
            updateKinds.add(LectureContentUpdateKind.METADATA);
        }
        return updateKinds;
    }

    private static boolean isContentUpdate(LectureContentUpdateSnapshot before, LectureContentUpdateSnapshot after, AttachmentFileUpdateResult fileUpdateResult) {
        return hasAttachmentFileContentUpdate(fileUpdateResult) || !Objects.equals(before.attachmentVersion(), after.attachmentVersion())
                || !Objects.equals(before.attachmentLink(), after.attachmentLink()) || !Objects.equals(before.videoSource(), after.videoSource());
    }

    private static boolean hasAttachmentFileContentUpdate(AttachmentFileUpdateResult fileUpdateResult) {
        return fileUpdateResult != null && (fileUpdateResult.fileBytesChanged() || fileUpdateResult.attachmentAdded() || fileUpdateResult.attachmentRemoved());
    }

    private static boolean isVisibilityUpdate(LectureContentUpdateSnapshot before, LectureContentUpdateSnapshot after) {
        return !Objects.equals(before.releaseDate(), after.releaseDate()) || !Objects.equals(before.slideHiddenUntilBySlideNumber(), after.slideHiddenUntilBySlideNumber());
    }

    private static boolean isMetadataUpdate(LectureContentUpdateSnapshot before, LectureContentUpdateSnapshot after) {
        return !Objects.equals(before.lectureUnitName(), after.lectureUnitName()) || !Objects.equals(before.lectureName(), after.lectureName())
                || !Objects.equals(before.courseName(), after.courseName()) || !Objects.equals(before.courseDescription(), after.courseDescription());
    }
}
