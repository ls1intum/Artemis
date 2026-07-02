package de.tum.cit.aet.artemis.lecture.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.lecture.domain.LectureContentUpdateKind;
import de.tum.cit.aet.artemis.lecture.dto.AttachmentFileUpdateResult;
import de.tum.cit.aet.artemis.lecture.dto.LectureContentUpdateSnapshot;

class LectureContentUpdateClassifierTest {

    private static final ZonedDateTime RELEASE_DATE = ZonedDateTime.parse("2026-07-02T12:00:00Z");

    private static final ZonedDateTime HIDDEN_UNTIL = ZonedDateTime.parse("2026-07-03T12:00:00Z");

    private final LectureContentUpdateClassifier classifier = new LectureContentUpdateClassifier();

    @Test
    void classifiesHiddenSlideChangeAsVisibilityUpdate() {
        var before = snapshot(Map.of(1, HIDDEN_UNTIL));
        var after = snapshot(Map.of(1, HIDDEN_UNTIL.plusHours(2)));

        var updateKind = classifier.classify(before, after, AttachmentFileUpdateResult.unchanged(7));

        assertThat(updateKind).isEqualTo(LectureContentUpdateKind.VISIBILITY);
    }

    @Test
    void classifiesLectureUnitNameChangeAsMetadataUpdate() {
        var before = snapshot("Exercise slides", "Lecture 1", "Course", "Description");
        var after = snapshot("Updated exercise slides", "Lecture 1", "Course", "Description");

        var updateKind = classifier.classify(before, after, AttachmentFileUpdateResult.unchanged(7));

        assertThat(updateKind).isEqualTo(LectureContentUpdateKind.METADATA);
    }

    @Test
    void classifiesChangedFileBytesAsContentUpdate() {
        var snapshot = snapshot();

        var updateKind = classifier.classify(snapshot, snapshot, AttachmentFileUpdateResult.changed(7, 8));

        assertThat(updateKind).isEqualTo(LectureContentUpdateKind.CONTENT);
    }

    @Test
    void classifiesUnchangedSnapshotsAsNoUpdate() {
        var snapshot = snapshot();

        var updateKind = classifier.classify(snapshot, snapshot, AttachmentFileUpdateResult.unchanged(7));

        assertThat(updateKind).isEqualTo(LectureContentUpdateKind.NONE);
    }

    @Test
    void classifiesContentUpdateWhenContentMetadataAndVisibilityChanged() {
        var before = snapshot("Exercise slides", "Lecture 1", "Course", "Description", "attachments/unit.pdf", "https://video.example/old", RELEASE_DATE,
                Map.of(1, HIDDEN_UNTIL));
        var after = snapshot("Updated exercise slides", "Lecture 2", "Updated Course", "Updated description", "attachments/unit-v2.pdf", "https://video.example/new",
                RELEASE_DATE.plusDays(1), Map.of(1, HIDDEN_UNTIL.plusHours(2)));

        var updateKind = classifier.classify(before, after, AttachmentFileUpdateResult.changed(7, 8));

        assertThat(updateKind).isEqualTo(LectureContentUpdateKind.CONTENT);
    }

    private static LectureContentUpdateSnapshot snapshot() {
        return snapshot(Map.of(1, HIDDEN_UNTIL));
    }

    private static LectureContentUpdateSnapshot snapshot(Map<Integer, ZonedDateTime> hiddenUntilBySlideNumber) {
        return snapshot("Exercise slides", "Lecture 1", "Course", "Description", "attachments/unit.pdf", "https://video.example/source", RELEASE_DATE,
                hiddenUntilBySlideNumber);
    }

    private static LectureContentUpdateSnapshot snapshot(String lectureUnitName, String lectureName, String courseName, String courseDescription) {
        return snapshot(lectureUnitName, lectureName, courseName, courseDescription, "attachments/unit.pdf", "https://video.example/source", RELEASE_DATE,
                Map.of(1, HIDDEN_UNTIL));
    }

    private static LectureContentUpdateSnapshot snapshot(String lectureUnitName, String lectureName, String courseName, String courseDescription, String attachmentLink,
            String videoSource, ZonedDateTime releaseDate, Map<Integer, ZonedDateTime> hiddenUntilBySlideNumber) {
        return new LectureContentUpdateSnapshot(42L, lectureUnitName, lectureName, courseName, courseDescription, 7, attachmentLink, videoSource, releaseDate,
                hiddenUntilBySlideNumber);
    }
}
