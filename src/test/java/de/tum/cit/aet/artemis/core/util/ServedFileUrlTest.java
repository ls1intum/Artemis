package de.tum.cit.aet.artemis.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Pins the path each file type is served under, given the filename its column stores and the id of the owning entity.
 * <p>
 * Two properties matter beyond the literal strings and are asserted for every type. The conversion is <b>idempotent</b>, because a client sends the value it was served straight
 * back in the next update of the same entity, and it is <b>total</b>: a missing id or a reference to something this application does not store never produces a URL with a hole in
 * it.
 */
class ServedFileUrlTest {

    private static final String FILENAME = "Attachment_2026-01-01T10-00-00-000_a1b2c3.pdf";

    @Test
    void buildsThePathOfEveryFileType() {
        assertThat(ServedFileUrl.courseIcon(3L, "icon.png")).isEqualTo("courses/3/icons/icon.png");
        assertThat(ServedFileUrl.profilePicture(7L, "picture.png")).isEqualTo("users/7/profile-pictures/picture.png");
        assertThat(ServedFileUrl.examUserSignature(9L, "signature.png")).isEqualTo("exam-users/9/signatures/signature.png");
        assertThat(ServedFileUrl.examUserImage(9L, "photo.png")).isEqualTo("exam-users/9/photo.png");
        assertThat(ServedFileUrl.lectureAttachment(4L, FILENAME)).isEqualTo("attachments/lectures/4/" + FILENAME);
        assertThat(ServedFileUrl.attachmentVideoUnitFile(8L, FILENAME)).isEqualTo("attachments/attachment-video-units/8/" + FILENAME);
        assertThat(ServedFileUrl.studentVersionSlides(8L, FILENAME)).isEqualTo("attachments/attachment-video-units/8/student/" + FILENAME);
        assertThat(ServedFileUrl.dragAndDropBackground(5L, "background.jpg")).isEqualTo("drag-and-drop/questions/5/backgrounds/background.jpg");
        assertThat(ServedFileUrl.dragItem(5L, 2L, "item.png")).isEqualTo("drag-and-drop/questions/5/drag-items/2/item.png");
    }

    /**
     * A client is served the path and sends it back untouched, so feeding it in again has to produce the same path rather than nest one inside another.
     */
    @Test
    void isIdempotent() {
        assertThat(ServedFileUrl.courseIcon(3L, ServedFileUrl.courseIcon(3L, "icon.png"))).isEqualTo("courses/3/icons/icon.png");
        assertThat(ServedFileUrl.lectureAttachment(4L, ServedFileUrl.lectureAttachment(4L, FILENAME))).isEqualTo("attachments/lectures/4/" + FILENAME);
        assertThat(ServedFileUrl.dragItem(5L, 2L, ServedFileUrl.dragItem(5L, 2L, "item.png"))).isEqualTo("drag-and-drop/questions/5/drag-items/2/item.png");
    }

    /**
     * A row written before the columns held a filename still comes out of the database, and so does a fragment of such a value that was written into post markdown years ago.
     */
    @Test
    void readsAValueThatStillCarriesTheWholeOldPath() {
        assertThat(ServedFileUrl.lectureAttachment(4L, "attachments/lecture/4/slides.pdf")).isEqualTo("attachments/lectures/4/slides.pdf");
        assertThat(ServedFileUrl.courseIcon(3L, "course/icons/3/icon.png")).isEqualTo("courses/3/icons/icon.png");
        assertThat(ServedFileUrl.profilePicture(7L, "user/profile-pictures/7/picture.png")).isEqualTo("users/7/profile-pictures/picture.png");
        assertThat(ServedFileUrl.examUserSignature(9L, "exam-user/signatures/9/signature.png")).isEqualTo("exam-users/9/signatures/signature.png");
        assertThat(ServedFileUrl.examUserImage(9L, "exam-user/9/photo.png")).isEqualTo("exam-users/9/photo.png");
        assertThat(ServedFileUrl.attachmentVideoUnitFile(8L, "attachments/attachment-unit/8/slides.pdf")).isEqualTo("attachments/attachment-video-units/8/slides.pdf");
        assertThat(ServedFileUrl.studentVersionSlides(8L, "attachments/attachment-unit/8/student/slides.pdf")).isEqualTo("attachments/attachment-video-units/8/student/slides.pdf");
        assertThat(ServedFileUrl.dragAndDropBackground(5L, "drag-and-drop/backgrounds/5/background.jpg")).isEqualTo("drag-and-drop/questions/5/backgrounds/background.jpg");
        assertThat(ServedFileUrl.dragItem(5L, 2L, "drag-and-drop/drag-items/2/item.png")).isEqualTo("drag-and-drop/questions/5/drag-items/2/item.png");
    }

    @Test
    void hasNothingToBuildWithoutAValue() {
        assertThat(ServedFileUrl.courseIcon(3L, null)).isNull();
        assertThat(ServedFileUrl.lectureAttachment(4L, "")).isEmpty();
        assertThat(ServedFileUrl.dragItem(5L, 2L, null)).isNull();
    }

    /**
     * Before the owning row is inserted there is no id to scope the path with, and inventing one would produce a URL that serves nothing. The filename is handed out instead.
     */
    @Test
    void fallsBackToTheFilenameWhileTheOwnerHasNoId() {
        assertThat(ServedFileUrl.courseIcon(null, "icon.png")).isEqualTo("icon.png");
        assertThat(ServedFileUrl.dragAndDropBackground(null, "background.jpg")).isEqualTo("background.jpg");
        assertThat(ServedFileUrl.dragItem(null, 2L, "item.png")).isEqualTo("item.png");
        assertThat(ServedFileUrl.dragItem(5L, null, "item.png")).isEqualTo("item.png");
        // even then, a value that still carries the whole old path is reduced
        assertThat(ServedFileUrl.courseIcon(null, "course/icons/3/icon.png")).isEqualTo("icon.png");
    }

    /**
     * An attachment may point at a document hosted elsewhere and the Iris bot's picture is a static asset of the client. Neither is stored here, so neither may be reduced or
     * rebuilt.
     */
    @Test
    void leavesAReferenceToSomethingItDoesNotStoreAlone() {
        assertThat(ServedFileUrl.lectureAttachment(4L, "https://example.org/lecture-notes.pdf")).isEqualTo("https://example.org/lecture-notes.pdf");
        assertThat(ServedFileUrl.attachmentVideoUnitFile(8L, "https://example.org/lecture-notes.pdf")).isEqualTo("https://example.org/lecture-notes.pdf");
        assertThat(ServedFileUrl.profilePicture(7L, "/public/images/iris/iris-logo-small.png")).isEqualTo("/public/images/iris/iris-logo-small.png");
    }
}
