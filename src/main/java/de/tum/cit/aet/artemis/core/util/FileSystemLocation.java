package de.tum.cit.aet.artemis.core.util;

import java.nio.file.Path;
import java.util.Optional;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.core.FilePathType;

/**
 * The single owner of where a stored file lives on disk.
 * <p>
 * This is the read-direction counterpart of {@link PublicFileUrl} and is built the same way: every file type is one
 * permitted record, and each record carries exactly the metadata its location needs. The metadata is therefore required
 * by the type system rather than recovered from the shape of a string: a lecture attachment cannot be located without a
 * lecture id, and a slide image cannot be located without the id of its attachment video unit and its slide number. No
 * segment of any value is ever read as an entity id.
 * <p>
 * The two sides do not carry the same components, because a URL and a directory layout answer different questions:
 * <ul>
 * <li>Where the layout puts every file of a type in one directory, the record takes only the filename, even though the
 * URL of the same file is scoped by an id. A course icon is served from {@code files/courses/{courseId}/icons/} but is
 * stored in one flat {@code images/course/icons/} directory, so a caller that has to delete one does not have to know
 * the course. The same holds for a profile picture, an exam user signature, a drag and drop background and a drag item
 * picture.</li>
 * <li>{@link Slide} takes the id of the attachment video unit and the slide number, while {@link PublicFileUrl.Slide}
 * takes the slide id, because the two address the same image in genuinely different ways. Going from the URL to the
 * location needs the slide row, which is why {@link #of(PublicFileUrl)} cannot answer for a slide.</li>
 * <li>{@link Temporary} has no counterpart at all: a temporary file is never served, so it has a location but no
 * URL.</li>
 * </ul>
 * <p>
 * <b>The filename component tolerates a legacy stored value.</b> Until the columns hold a bare filename, values such as
 * {@code attachments/lecture/4/slides.pdf} still come out of the database, out of post markdown that no migration
 * reaches, and out of client-side caches. Every record therefore reduces its filename to the last path segment, which is
 * the one part of such a value that is not a restatement of metadata the entity already holds. Once the columns store
 * only a filename, that reduction becomes a no-op and {@link #filenameOf} can go.
 *
 * @see PublicFileUrl for the REST URL of the same files, which is independent of these locations
 * @see FilePathConverter for the fixed directory of each file type
 */
public sealed interface FileSystemLocation {

    /**
     * @return the absolute location of the file on disk
     */
    @NonNull
    Path path();

    /**
     * @return the file path type stored at this location
     */
    @NonNull
    FilePathType filePathType();

    /**
     * A file that has been uploaded but not yet claimed by an entity.
     *
     * @param filename the filename of the temporary file
     */
    record Temporary(@NonNull String filename) implements FileSystemLocation {

        @Override
        public Path path() {
            return resolve(FilePathConverter.getTempFilePath(), filename);
        }

        @Override
        public FilePathType filePathType() {
            return FilePathType.TEMPORARY;
        }
    }

    /**
     * A course icon. Every icon lives in one directory, so the course id is not part of the location.
     *
     * @param filename the filename of the icon
     */
    record CourseIcon(@NonNull String filename) implements FileSystemLocation {

        @Override
        public Path path() {
            return resolve(FilePathConverter.getCourseIconFilePath(), filename);
        }

        @Override
        public FilePathType filePathType() {
            return FilePathType.COURSE_ICON;
        }
    }

    /**
     * A user's profile picture. Every picture lives in one directory, so the user id is not part of the location.
     *
     * @param filename the filename of the picture
     */
    record ProfilePicture(@NonNull String filename) implements FileSystemLocation {

        @Override
        public Path path() {
            return resolve(FilePathConverter.getProfilePictureFilePath(), filename);
        }

        @Override
        public FilePathType filePathType() {
            return FilePathType.PROFILE_PICTURE;
        }
    }

    /**
     * The signature an exam participant gave on the exam cover page. Every signature lives in one directory, so the exam user id is not part of the location.
     *
     * @param filename the filename of the signature image
     */
    record ExamUserSignature(@NonNull String filename) implements FileSystemLocation {

        @Override
        public Path path() {
            return resolve(FilePathConverter.getExamUserSignatureFilePath(), filename);
        }

        @Override
        public FilePathType filePathType() {
            return FilePathType.EXAM_USER_SIGNATURE;
        }
    }

    /**
     * The identification photo of an exam participant.
     *
     * @param examUserId the id of the exam user the photo belongs to, which names the directory it is stored in
     * @param filename   the filename of the photo
     */
    record ExamUserImage(long examUserId, @NonNull String filename) implements FileSystemLocation {

        @Override
        public Path path() {
            return resolve(FilePathConverter.getStudentImageFilePath().resolve(String.valueOf(examUserId)), filename);
        }

        @Override
        public FilePathType filePathType() {
            return FilePathType.EXAM_USER_IMAGE;
        }
    }

    /**
     * The background image of a drag and drop question. Every background lives in one directory, so the question id is not part of the location.
     *
     * @param filename the filename of the background image
     */
    record DragAndDropBackground(@NonNull String filename) implements FileSystemLocation {

        @Override
        public Path path() {
            return resolve(FilePathConverter.getDragAndDropBackgroundFilePath(), filename);
        }

        @Override
        public FilePathType filePathType() {
            return FilePathType.DRAG_AND_DROP_BACKGROUND;
        }
    }

    /**
     * The picture of a single drag item. Every picture lives in one directory, so neither the question id nor the drag item id is part of the location, although the URL needs
     * both.
     *
     * @param filename the filename of the picture
     */
    record DragItem(@NonNull String filename) implements FileSystemLocation {

        @Override
        public Path path() {
            return resolve(FilePathConverter.getDragItemFilePath(), filename);
        }

        @Override
        public FilePathType filePathType() {
            return FilePathType.DRAG_ITEM;
        }
    }

    /**
     * An attachment that hangs directly off a lecture.
     *
     * @param lectureId the id of the lecture the attachment belongs to, which names the directory it is stored in
     * @param filename  the filename of the attachment
     */
    record LectureAttachment(long lectureId, @NonNull String filename) implements FileSystemLocation {

        @Override
        public Path path() {
            return resolve(FilePathConverter.getLectureAttachmentFileSystemPath().resolve(String.valueOf(lectureId)), filename);
        }

        @Override
        public FilePathType filePathType() {
            return FilePathType.LECTURE_ATTACHMENT;
        }
    }

    /**
     * The attachment of an attachment video unit.
     *
     * @param attachmentVideoUnitId the id of the attachment video unit, which names the directory the file is stored in
     * @param filename              the filename of the attachment
     */
    record AttachmentVideoUnitFile(long attachmentVideoUnitId, @NonNull String filename) implements FileSystemLocation {

        @Override
        public Path path() {
            return resolve(FilePathConverter.getAttachmentVideoUnitFileSystemPath().resolve(String.valueOf(attachmentVideoUnitId)), filename);
        }

        @Override
        public FilePathType filePathType() {
            return FilePathType.ATTACHMENT_UNIT;
        }
    }

    /**
     * The student version of an attachment video unit's slides, which is the same document with the hidden slides removed.
     *
     * @param attachmentVideoUnitId the id of the attachment video unit, which names the directory the file is stored under
     * @param filename              the filename of the student version
     */
    record StudentVersionSlides(long attachmentVideoUnitId, @NonNull String filename) implements FileSystemLocation {

        @Override
        public Path path() {
            return resolve(FilePathConverter.getAttachmentVideoUnitFileSystemPath().resolve(String.valueOf(attachmentVideoUnitId)).resolve("student"), filename);
        }

        @Override
        public FilePathType filePathType() {
            return FilePathType.STUDENT_VERSION_SLIDES;
        }
    }

    /**
     * The rendered image of a single slide.
     * <p>
     * The directory is named by the slide's position in the document, not by its id: the image is written to
     * {@code attachment-unit/{attachmentVideoUnitId}/slide/{slideNumber}/} at the moment the slide is given that number, and it is rewritten whenever the number changes. A
     * caller that renumbers a slide therefore has to build this location from the number the slide had while the file was written, not from the one it is being given.
     *
     * @param attachmentVideoUnitId the id of the attachment video unit the slide belongs to
     * @param slideNumber           the one-based position of the slide in the document at the time the image was written
     * @param filename              the filename of the slide image
     */
    record Slide(long attachmentVideoUnitId, int slideNumber, @NonNull String filename) implements FileSystemLocation {

        @Override
        public Path path() {
            Path directory = FilePathConverter.getAttachmentVideoUnitFileSystemPath().resolve(String.valueOf(attachmentVideoUnitId)).resolve("slide")
                    .resolve(String.valueOf(slideNumber));
            return resolve(directory, filename);
        }

        @Override
        public FilePathType filePathType() {
            return FilePathType.SLIDE;
        }
    }

    /**
     * A file a student submitted for a file upload exercise.
     *
     * @param exerciseId   the id of the file upload exercise
     * @param submissionId the id of the submission the file belongs to
     * @param filename     the filename of the submitted file
     */
    record FileUploadSubmission(long exerciseId, long submissionId, @NonNull String filename) implements FileSystemLocation {

        @Override
        public Path path() {
            return resolve(FilePathConverter.buildFileUploadSubmissionPath(exerciseId, submissionId), filename);
        }

        @Override
        public FilePathType filePathType() {
            return FilePathType.FILE_UPLOAD_SUBMISSION;
        }
    }

    /**
     * The location of the file a served URL points at, so that a caller holding a {@link PublicFileUrl} does not have to restate its metadata.
     * <p>
     * Every record of {@link PublicFileUrl} carries at least what the corresponding location needs, with one exception: {@link PublicFileUrl.Slide} identifies a slide by its id,
     * while the image is stored under the id of its attachment video unit and its slide number. Resolving that one needs the slide row, so it is answered with an empty result
     * rather than with a guess.
     *
     * @param url the URL a file is served under
     * @return the location of that file, or empty for a slide URL, which has to be resolved through the slide itself
     */
    static Optional<FileSystemLocation> of(@NonNull PublicFileUrl url) {
        return switch (url) {
            case PublicFileUrl.CourseIcon(long courseId, String filename) -> Optional.of(new CourseIcon(filename));
            case PublicFileUrl.ProfilePicture(long userId, String filename) -> Optional.of(new ProfilePicture(filename));
            case PublicFileUrl.ExamUserSignature(long examUserId, String filename) -> Optional.of(new ExamUserSignature(filename));
            case PublicFileUrl.ExamUserImage(long examUserId, String filename) -> Optional.of(new ExamUserImage(examUserId, filename));
            case PublicFileUrl.DragAndDropBackground(long questionId, String filename) -> Optional.of(new DragAndDropBackground(filename));
            case PublicFileUrl.DragItem(long questionId, long dragItemId, String filename) -> Optional.of(new DragItem(filename));
            case PublicFileUrl.LectureAttachment(long lectureId, String filename) -> Optional.of(new LectureAttachment(lectureId, filename));
            case PublicFileUrl.AttachmentVideoUnitFile(long unitId, String filename) -> Optional.of(new AttachmentVideoUnitFile(unitId, filename));
            case PublicFileUrl.StudentVersionSlides(long unitId, String filename) -> Optional.of(new StudentVersionSlides(unitId, filename));
            case PublicFileUrl.FileUploadSubmission(long exerciseId, long submissionId, String filename) ->
                Optional.of(new FileUploadSubmission(exerciseId, submissionId, filename));
            case PublicFileUrl.Slide slide -> Optional.empty();
        };
    }

    /**
     * The filename of a value that a column still stores in its URL-shaped legacy form, which is its last path segment.
     * <p>
     * This is the only thing a stored value is still read for. Everything else such a value encodes, the entity already knows, which is why nothing here looks at a segment
     * position. Once the columns store a bare filename this returns its argument unchanged, and the call can be dropped.
     *
     * @param storedValue the value as it comes out of the database, out of post markdown or out of a client-side cache
     * @return the filename, without any leading path segments
     */
    @NonNull
    static String filenameOf(@NonNull String storedValue) {
        return storedValue.substring(storedValue.lastIndexOf('/') + 1);
    }

    /**
     * The value a column stores for a file reference: the filename, and nothing else.
     * <p>
     * Every setter of such a field runs its argument through this, which is what makes storing an entire URL impossible rather than merely unlikely. The client is served a URL
     * and sends it back untouched in the next update of the same entity, so without this the column would fill up with URLs again through the ordinary edit path. Feeding a bare
     * filename in returns it unchanged, so the reduction is idempotent and a value written by a node on the previous release needs no migration to be read correctly.
     * <p>
     * A value that does not name a file this application stores is left alone, see {@link #refersToStoredFile}.
     *
     * @param value the value a caller wants to store, which may be a filename, an entire URL, or null
     * @return the filename to store, or the value unchanged when it names nothing this application stores
     */
    @Nullable
    static String storedFilename(@Nullable String value) {
        return refersToStoredFile(value) ? filenameOf(value) : value;
    }

    /**
     * Whether a value names a file this application stores, as opposed to something outside it.
     * <p>
     * Two of these fields accept a reference to somewhere else and have to keep it verbatim in both directions: an attachment may point at a document hosted elsewhere
     * ({@code https://example.org/lecture-notes.pdf}), and the Iris bot's profile picture is a static asset shipped with the client ({@code /public/images/iris/...}). Neither is
     * ever resolved against an upload directory or rebuilt into a served URL, so neither may be reduced to a filename. Everything a client can send back after being served one
     * of these fields is relative and unschemed, so the two cases do not overlap.
     *
     * @param value the value to classify
     * @return true if the value names a stored file, false for null, blank, an absolute path or anything carrying a URI scheme
     */
    static boolean refersToStoredFile(@Nullable String value) {
        return value != null && !value.isBlank() && !value.startsWith("/") && !value.contains("://");
    }

    /**
     * Resolves a filename against the directory its file type stores it in.
     *
     * @param directory the directory the file type stores its files in
     * @param filename  the filename, possibly still carrying the leading segments of a legacy stored value
     * @return the location of the file
     */
    @NonNull
    private static Path resolve(@NonNull Path directory, @NonNull String filename) {
        return directory.resolve(filenameOf(filename));
    }
}
