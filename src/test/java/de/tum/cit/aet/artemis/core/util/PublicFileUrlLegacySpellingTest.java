package de.tum.cit.aet.artemis.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.FilePathType;
import de.tum.cit.aet.artemis.core.config.Constants;

/**
 * The difference table between the URL {@link PublicFileUrl} defines and the string {@link FilePathConverter#externalUriForFileSystemPath} writes into the database today.
 * <p>
 * The two are not the same, and that is deliberate. {@code externalUriForFileSystemPath} emits the older spelling of each path, and it has to keep doing so: the stored value is
 * still the key the storage layer parses and still sized by the width of the column it lives in, so switching it to the canonical spelling would lengthen stored values and
 * force a schema change. {@link PublicFileUrl} is the canonical spelling that {@code FileResource} also answers to, which is what the stored value will eventually stop needing
 * to be at all.
 * <p>
 * Pinning both halves side by side is the point of this class: as long as the two spellings coexist, a change to either one is visible here. It is scaffolding for exactly that
 * period, and the phase that stops storing a URL should delete it together with the emission it pins.
 * <p>
 * The paths handed to {@code externalUriForFileSystemPath} are bare and relative on purpose. That method reads only the tail of the path it is given, so this test needs no file
 * upload root, and it therefore does not touch the process-wide root that {@link FilePathConverter#setFileUploadPath} sets.
 */
class PublicFileUrlLegacySpellingTest {

    /**
     * @param path         the file system path of the stored file, of which only the tail is read
     * @param filePathType the type of the stored file
     * @param entityId     the id the emitted string embeds
     * @return what is stored today, expressed the way the client resolves it, so it can be compared with a canonical URL
     */
    private static String legacyUrl(Path path, FilePathType filePathType, Long entityId) {
        // A stored value is relative to api/core/files/, which the client prepends. A canonical URL is relative to api/core/ and starts with files/.
        return PublicFileUrl.FILES_PREFIX + FilePathConverter.externalUriForFileSystemPath(path, filePathType, entityId);
    }

    @Test
    void courseIcon() {
        assertThat(new PublicFileUrl.CourseIcon(3L, "icon.png").url()).hasToString("files/courses/3/icons/icon.png");
        assertThat(legacyUrl(Path.of("icon.png"), FilePathType.COURSE_ICON, 3L)).isEqualTo("files/course/icons/3/icon.png");
    }

    @Test
    void profilePicture() {
        assertThat(new PublicFileUrl.ProfilePicture(7L, "avatar.jpg").url()).hasToString("files/users/7/profile-pictures/avatar.jpg");
        assertThat(legacyUrl(Path.of("avatar.jpg"), FilePathType.PROFILE_PICTURE, 7L)).isEqualTo("files/user/profile-pictures/7/avatar.jpg");
    }

    @Test
    void examUserSignature() {
        assertThat(new PublicFileUrl.ExamUserSignature(8L, "sign.png").url()).hasToString("files/exam-users/8/signatures/sign.png");
        assertThat(legacyUrl(Path.of("sign.png"), FilePathType.EXAM_USER_SIGNATURE, 8L)).isEqualTo("files/exam-user/signatures/8/sign.png");
    }

    @Test
    void examUserImage() {
        assertThat(new PublicFileUrl.ExamUserImage(9L, "photo.jpg").url()).hasToString("files/exam-users/9/photo.jpg");
        assertThat(legacyUrl(Path.of("photo.jpg"), FilePathType.EXAM_USER_IMAGE, 9L)).isEqualTo("files/exam-user/9/photo.jpg");
    }

    @Test
    void lectureAttachment() {
        assertThat(new PublicFileUrl.LectureAttachment(4L, "slides.pdf").url()).hasToString("files/attachments/lectures/4/slides.pdf");
        assertThat(legacyUrl(Path.of("slides.pdf"), FilePathType.LECTURE_ATTACHMENT, 4L)).isEqualTo("files/attachments/lecture/4/slides.pdf");
    }

    @Test
    void attachmentVideoUnitFile() {
        assertThat(new PublicFileUrl.AttachmentVideoUnitFile(4L, "file.pdf").url()).hasToString("files/attachments/attachment-video-units/4/file.pdf");
        assertThat(legacyUrl(Path.of("file.pdf"), FilePathType.ATTACHMENT_UNIT, 4L)).isEqualTo("files/attachments/attachment-unit/4/file.pdf");
    }

    @Test
    void studentVersionSlides() {
        assertThat(new PublicFileUrl.StudentVersionSlides(4L, "notes.pdf").url()).hasToString("files/attachments/attachment-video-units/4/student/notes.pdf");
        assertThat(legacyUrl(Path.of("notes.pdf"), FilePathType.STUDENT_VERSION_SLIDES, 4L)).isEqualTo("files/attachments/attachment-unit/4/student/notes.pdf");
    }

    @Test
    void dragAndDropBackground() {
        assertThat(new PublicFileUrl.DragAndDropBackground(42L, "bg.png").url()).hasToString("files/drag-and-drop/questions/42/backgrounds/bg.png");
        assertThat(legacyUrl(Path.of("bg.png"), FilePathType.DRAG_AND_DROP_BACKGROUND, 42L)).isEqualTo("files/drag-and-drop/backgrounds/42/bg.png");
    }

    /**
     * The only file family whose stored spelling already is the canonical one, because a drag item picture was introduced with the question-scoped path.
     */
    @Test
    void dragItemStoresTheCanonicalSpellingAlready() {
        String canonical = new PublicFileUrl.DragItem(7L, 2L, "item.png").url().toString();

        assertThat(canonical).isEqualTo("files/drag-and-drop/questions/7/drag-items/2/item.png");
        assertThat(PublicFileUrl.FILES_PREFIX + FilePathConverter.externalUriForDragItemFileSystemPath(Path.of("item.png"), 7L, 2L)).isEqualTo(canonical);
    }

    /**
     * A file upload submission is the second family whose stored spelling already is the canonical one.
     */
    @Test
    void fileUploadSubmissionStoresTheCanonicalSpellingAlready() {
        String canonical = new PublicFileUrl.FileUploadSubmission(7L, 9L, "solution.txt").url().toString();

        assertThat(canonical).isEqualTo("files/file-upload-exercises/7/submissions/9/solution.txt");
        assertThat(legacyUrl(Path.of("7", "9", "solution.txt"), FilePathType.FILE_UPLOAD_SUBMISSION, 9L)).isEqualTo(canonical);
    }

    /**
     * A slide is the one family where the two are not variants of one template. What is stored is a storage key: it names the unit, the slide number and the filename, and no
     * mapping on {@code FileResource} answers to it, because both slide mappings stop before the filename. The URL a client can actually fetch addresses the slide by its id,
     * and that is what {@link PublicFileUrl.Slide} produces.
     */
    @Test
    void slideStoresAStorageKeyRatherThanAUrl() {
        assertThat(new PublicFileUrl.Slide(11L).url()).hasToString("files/slides/11");
        assertThat(legacyUrl(Path.of("4", "slide", "1", "slide1.png"), FilePathType.SLIDE, 1L)).isEqualTo("files/attachments/attachment-unit/4/slide/1/slide1.png");
    }

    /**
     * A temporary file has no URL at all. {@code externalUriForFileSystemPath} still returns a string for it, which the quiz creation flow uses as a lookup key for an uploaded
     * file rather than as a URL, and {@link PublicFileUrl} deliberately cannot express it.
     */
    @Test
    void temporaryFileHasNoUrl() {
        assertThat(FilePathConverter.externalUriForFileSystemPath(Path.of("file.tmp"), FilePathType.TEMPORARY, 1L)).isEqualTo(URI.create("temp/file.tmp"));
    }

    /**
     * While a drag and drop question has no id yet, the stored value carries a placeholder that {@code DragAndDropQuestion.afterCreate()} patches once the id exists. A URL
     * carrying that placeholder is never served, so {@link PublicFileUrl} takes a primitive question id and cannot express it. The quiz write paths that pass no id can only be
     * routed through the canonical owner once the stored value has stopped being a URL.
     */
    @Test
    void anUnpersistedQuestionStoresAPlaceholderThatIsNotAUrl() {
        assertThat(legacyUrl(Path.of("bg.png"), FilePathType.DRAG_AND_DROP_BACKGROUND, null))
                .isEqualTo("files/drag-and-drop/backgrounds/" + Constants.FILEPATH_ID_PLACEHOLDER + "/bg.png");
    }
}
