package de.tum.cit.aet.artemis.lecture.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZonedDateTime;
import java.util.HashMap;
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
    void classifiesReleaseDateChangeAsVisibilityUpdate() {
        var before = snapshot();
        var after = snapshot("Exercise slides", "Lecture 1", "Course", "Description", 7, "attachments/unit.pdf", "https://video.example/source", RELEASE_DATE.plusDays(1),
                Map.of(1, HIDDEN_UNTIL));

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
    void classifiesAttachmentAddedAsContentUpdate() {
        var snapshot = snapshot();

        var updateKind = classifier.classify(snapshot, snapshot, new AttachmentFileUpdateResult(false, true, false, null, 1));

        assertThat(updateKind).isEqualTo(LectureContentUpdateKind.CONTENT);
    }

    @Test
    void classifiesAttachmentRemovedAsContentUpdate() {
        var snapshot = snapshot();

        var updateKind = classifier.classify(snapshot, snapshot, new AttachmentFileUpdateResult(false, false, true, 7, null));

        assertThat(updateKind).isEqualTo(LectureContentUpdateKind.CONTENT);
    }

    @Test
    void classifiesAttachmentVersionOnlyChangeAsContentUpdate() {
        var before = snapshot("Exercise slides", "Lecture 1", "Course", "Description", 7, "attachments/unit.pdf", "https://video.example/source", RELEASE_DATE,
                Map.of(1, HIDDEN_UNTIL));
        var after = snapshot("Exercise slides", "Lecture 1", "Course", "Description", 8, "attachments/unit.pdf", "https://video.example/source", RELEASE_DATE,
                Map.of(1, HIDDEN_UNTIL));

        var updateKind = classifier.classify(before, after, AttachmentFileUpdateResult.unchanged(7));

        assertThat(updateKind).isEqualTo(LectureContentUpdateKind.CONTENT);
    }

    @Test
    void classifiesAttachmentLinkChangeAsContentUpdate() {
        var before = snapshot();
        var after = snapshot("Exercise slides", "Lecture 1", "Course", "Description", 7, "attachments/unit-v2.pdf", "https://video.example/source", RELEASE_DATE,
                Map.of(1, HIDDEN_UNTIL));

        var updateKind = classifier.classify(before, after, AttachmentFileUpdateResult.unchanged(7));

        assertThat(updateKind).isEqualTo(LectureContentUpdateKind.CONTENT);
    }

    @Test
    void classifiesVideoUrlChangeAsContentUpdate() {
        var before = snapshot();
        var after = snapshot("Exercise slides", "Lecture 1", "Course", "Description", 7, "attachments/unit.pdf", "https://video.example/updated", RELEASE_DATE,
                Map.of(1, HIDDEN_UNTIL));

        var updateKind = classifier.classify(before, after, AttachmentFileUpdateResult.unchanged(7));

        assertThat(updateKind).isEqualTo(LectureContentUpdateKind.CONTENT);
    }

    @Test
    void classifiesUnchangedSnapshotsAsNoUpdate() {
        var snapshot = snapshot();

        var updateKind = classifier.classify(snapshot, snapshot, AttachmentFileUpdateResult.unchanged(7));

        assertThat(updateKind).isEqualTo(LectureContentUpdateKind.NONE);
    }

    @Test
    void classifiesMissingAfterSnapshotAsDeleteUpdate() {
        var updateKind = classifier.classify(snapshot(), null, AttachmentFileUpdateResult.unchanged(7));

        assertThat(updateKind).isEqualTo(LectureContentUpdateKind.DELETE);
    }

    @Test
    void classifiesContentUpdateWhenContentMetadataAndVisibilityChanged() {
        var before = snapshot("Exercise slides", "Lecture 1", "Course", "Description", 7, "attachments/unit.pdf", "https://video.example/old", RELEASE_DATE,
                Map.of(1, HIDDEN_UNTIL));
        var after = snapshot("Updated exercise slides", "Lecture 2", "Updated Course", "Updated description", 8, "attachments/unit-v2.pdf", "https://video.example/new",
                RELEASE_DATE.plusDays(1), Map.of(1, HIDDEN_UNTIL.plusHours(2)));

        var updateKind = classifier.classify(before, after, AttachmentFileUpdateResult.changed(7, 8));

        assertThat(updateKind).isEqualTo(LectureContentUpdateKind.CONTENT);
    }

    @Test
    void snapshotDefensivelyCopiesSlideHiddenMapAndNormalizesNullToEmptyMap() {
        var source = new HashMap<Integer, ZonedDateTime>();
        source.put(1, HIDDEN_UNTIL);

        var snapshot = snapshot(source);
        source.put(2, HIDDEN_UNTIL.plusDays(1));

        assertThat(snapshot.slideHiddenUntilBySlideNumber()).containsOnly(Map.entry(1, HIDDEN_UNTIL));
        assertThatThrownBy(() -> snapshot.slideHiddenUntilBySlideNumber().put(3, HIDDEN_UNTIL.plusDays(2))).isInstanceOf(UnsupportedOperationException.class);

        var snapshotWithNullSlides = new LectureContentUpdateSnapshot(42L, "Exercise slides", "Lecture 1", "Course", "Description", 7, "attachments/unit.pdf",
                "https://video.example/source", RELEASE_DATE, null);
        assertThat(snapshotWithNullSlides.slideHiddenUntilBySlideNumber()).isEmpty();
    }

    @Test
    void snapshotSupportsVisibleSlidesWithNullHiddenUntil() {
        var source = new HashMap<Integer, ZonedDateTime>();
        source.put(1, null);

        var snapshot = snapshot(source);
        source.put(2, HIDDEN_UNTIL);

        assertThat(snapshot.slideHiddenUntilBySlideNumber()).containsOnlyKeys(1).containsEntry(1, null);
        assertThatThrownBy(() -> snapshot.slideHiddenUntilBySlideNumber().put(3, HIDDEN_UNTIL)).isInstanceOf(UnsupportedOperationException.class);
    }

    private static LectureContentUpdateSnapshot snapshot() {
        return snapshot(Map.of(1, HIDDEN_UNTIL));
    }

    private static LectureContentUpdateSnapshot snapshot(Map<Integer, ZonedDateTime> hiddenUntilBySlideNumber) {
        return snapshot("Exercise slides", "Lecture 1", "Course", "Description", 7, "attachments/unit.pdf", "https://video.example/source", RELEASE_DATE, hiddenUntilBySlideNumber);
    }

    private static LectureContentUpdateSnapshot snapshot(String lectureUnitName, String lectureName, String courseName, String courseDescription) {
        return snapshot(lectureUnitName, lectureName, courseName, courseDescription, 7, "attachments/unit.pdf", "https://video.example/source", RELEASE_DATE,
                Map.of(1, HIDDEN_UNTIL));
    }

    private static LectureContentUpdateSnapshot snapshot(String lectureUnitName, String lectureName, String courseName, String courseDescription, Integer attachmentVersion,
            String attachmentLink, String videoSource, ZonedDateTime releaseDate, Map<Integer, ZonedDateTime> hiddenUntilBySlideNumber) {
        return new LectureContentUpdateSnapshot(42L, lectureUnitName, lectureName, courseName, courseDescription, attachmentVersion, attachmentLink, videoSource, releaseDate,
                hiddenUntilBySlideNumber);
    }
}
