package de.tum.cit.aet.artemis.lecture.service;

import java.util.Objects;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.domain.LectureContentUpdateKind;
import de.tum.cit.aet.artemis.lecture.dto.AttachmentFileUpdateResult;
import de.tum.cit.aet.artemis.lecture.dto.LectureContentUpdateSnapshot;

@Conditional(LectureEnabled.class)
@Lazy
@Service
public class LectureContentUpdateClassifier {

    public LectureContentUpdateKind classify(LectureContentUpdateSnapshot before, LectureContentUpdateSnapshot after, AttachmentFileUpdateResult fileUpdateResult) {
        if (isContentUpdate(before, after, fileUpdateResult)) {
            return LectureContentUpdateKind.CONTENT;
        }
        if (isVisibilityUpdate(before, after)) {
            return LectureContentUpdateKind.VISIBILITY;
        }
        if (isMetadataUpdate(before, after)) {
            return LectureContentUpdateKind.METADATA;
        }
        return LectureContentUpdateKind.NONE;
    }

    private static boolean isContentUpdate(LectureContentUpdateSnapshot before, LectureContentUpdateSnapshot after, AttachmentFileUpdateResult fileUpdateResult) {
        return hasAttachmentFileContentUpdate(fileUpdateResult) || !Objects.equals(before.attachmentVersion(), after.attachmentVersion())
                || !Objects.equals(before.attachmentLink(), after.attachmentLink()) || !Objects.equals(before.videoSource(), after.videoSource());
    }

    private static boolean hasAttachmentFileContentUpdate(AttachmentFileUpdateResult fileUpdateResult) {
        return fileUpdateResult != null && (fileUpdateResult.fileBytesChanged() || fileUpdateResult.attachmentAdded() || fileUpdateResult.attachmentRemoved());
    }

    private static boolean isVisibilityUpdate(LectureContentUpdateSnapshot before, LectureContentUpdateSnapshot after) {
        return !Objects.equals(before.releaseDate(), after.releaseDate())
                || !Objects.equals(before.slideHiddenUntilBySlideNumber(), after.slideHiddenUntilBySlideNumber());
    }

    private static boolean isMetadataUpdate(LectureContentUpdateSnapshot before, LectureContentUpdateSnapshot after) {
        return !Objects.equals(before.lectureUnitName(), after.lectureUnitName()) || !Objects.equals(before.lectureName(), after.lectureName())
                || !Objects.equals(before.courseName(), after.courseName()) || !Objects.equals(before.courseDescription(), after.courseDescription());
    }
}
