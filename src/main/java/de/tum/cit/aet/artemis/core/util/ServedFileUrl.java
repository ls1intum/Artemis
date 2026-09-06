package de.tum.cit.aet.artemis.core.util;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Turns a stored file reference into the value the client is served for it.
 * <p>
 * A column stores nothing but a filename. Everything else the client needs in order to request the file, the owning entity already knows, so the URL is assembled here from the
 * hardcoded template of {@link PublicFileUrl} plus the id of that entity. Nothing is parsed out of the stored value.
 * <p>
 * Every method is null-safe in both arguments and, because it reduces the stored value with {@link FileSystemLocation#filenameOf}, idempotent: feeding a value that is already a
 * URL back in yields the same URL. That is what makes a client round-trip harmless, and it is what lets a row written by a node on the previous release be read correctly by a
 * node on this one.
 * <p>
 * When the owning id is missing the stored filename is returned unchanged rather than a URL with a hole in it. This happens only before the owning row is inserted, where there is
 * no URL to hand out yet.
 *
 * @see PublicFileUrl for the templates themselves
 * @see FileSystemLocation for where the same files live on disk
 */
public final class ServedFileUrl {

    private ServedFileUrl() {
    }

    /**
     * @param courseId    the id of the course the icon belongs to
     * @param storedValue the stored icon reference
     * @return the path the icon is served under, relative to {@code api/core/files/}
     */
    @Nullable
    public static String courseIcon(@Nullable Long courseId, @Nullable String storedValue) {
        return served(storedValue, courseId, (id, filename) -> new PublicFileUrl.CourseIcon(id, filename));
    }

    /**
     * @param userId      the id of the user the picture belongs to
     * @param storedValue the stored picture reference
     * @return the path the profile picture is served under, relative to {@code api/core/files/}
     */
    @Nullable
    public static String profilePicture(@Nullable Long userId, @Nullable String storedValue) {
        return served(storedValue, userId, (id, filename) -> new PublicFileUrl.ProfilePicture(id, filename));
    }

    /**
     * @param examUserId  the id of the exam user the signature belongs to
     * @param storedValue the stored signature reference
     * @return the path the signature is served under, relative to {@code api/core/files/}
     */
    @Nullable
    public static String examUserSignature(@Nullable Long examUserId, @Nullable String storedValue) {
        return served(storedValue, examUserId, (id, filename) -> new PublicFileUrl.ExamUserSignature(id, filename));
    }

    /**
     * @param examUserId  the id of the exam user the photo belongs to
     * @param storedValue the stored photo reference
     * @return the path the identification photo is served under, relative to {@code api/core/files/}
     */
    @Nullable
    public static String examUserImage(@Nullable Long examUserId, @Nullable String storedValue) {
        return served(storedValue, examUserId, (id, filename) -> new PublicFileUrl.ExamUserImage(id, filename));
    }

    /**
     * @param lectureId   the id of the lecture the attachment belongs to
     * @param storedValue the stored attachment reference
     * @return the path the attachment is served under, relative to {@code api/core/files/}
     */
    @Nullable
    public static String lectureAttachment(@Nullable Long lectureId, @Nullable String storedValue) {
        return served(storedValue, lectureId, (id, filename) -> new PublicFileUrl.LectureAttachment(id, filename));
    }

    /**
     * @param attachmentVideoUnitId the id of the attachment video unit the file belongs to
     * @param storedValue           the stored attachment reference
     * @return the path the attachment is served under, relative to {@code api/core/files/}
     */
    @Nullable
    public static String attachmentVideoUnitFile(@Nullable Long attachmentVideoUnitId, @Nullable String storedValue) {
        return served(storedValue, attachmentVideoUnitId, (id, filename) -> new PublicFileUrl.AttachmentVideoUnitFile(id, filename));
    }

    /**
     * @param attachmentVideoUnitId the id of the attachment video unit the student version belongs to
     * @param storedValue           the stored student version reference
     * @return the path the student version is served under, relative to {@code api/core/files/}
     */
    @Nullable
    public static String studentVersionSlides(@Nullable Long attachmentVideoUnitId, @Nullable String storedValue) {
        return served(storedValue, attachmentVideoUnitId, (id, filename) -> new PublicFileUrl.StudentVersionSlides(id, filename));
    }

    /**
     * @param questionId  the id of the drag and drop question the background belongs to
     * @param storedValue the stored background reference
     * @return the path the background image is served under, relative to {@code api/core/files/}
     */
    @Nullable
    public static String dragAndDropBackground(@Nullable Long questionId, @Nullable String storedValue) {
        return served(storedValue, questionId, (id, filename) -> new PublicFileUrl.DragAndDropBackground(id, filename));
    }

    /**
     * A drag item picture needs both ids, because a drag item id is only unique within its question.
     *
     * @param questionId  the id of the owning drag and drop question
     * @param dragItemId  the question-scoped id of the drag item
     * @param storedValue the stored picture reference
     * @return the path the picture is served under, relative to {@code api/core/files/}
     */
    @Nullable
    public static String dragItem(@Nullable Long questionId, @Nullable Long dragItemId, @Nullable String storedValue) {
        if (dragItemId == null) {
            return filenameOrNull(storedValue);
        }
        return served(storedValue, questionId, (id, filename) -> new PublicFileUrl.DragItem(id, dragItemId, filename));
    }

    /**
     * Builds the served path of a stored file, or returns the bare filename when the owning entity has no id yet.
     *
     * @param storedValue the value as it comes out of the database, which may still be an entire URL
     * @param ownerId     the id the template needs, or null while the owning row has not been inserted
     * @param template    builds the URL from the owning id and the filename
     * @return the served path, or the filename when there is no id to build one with
     */
    @Nullable
    private static String served(@Nullable String storedValue, @Nullable Long ownerId, @NonNull UrlTemplate template) {
        if (ownerId == null || !FileSystemLocation.refersToStoredFile(storedValue)) {
            return filenameOrNull(storedValue);
        }
        return template.of(ownerId, FileSystemLocation.filenameOf(storedValue)).clientPath();
    }

    /**
     * @param storedValue the stored value, which may still be an entire URL
     * @return its filename, or the value unchanged when it names nothing this application stores
     */
    @Nullable
    private static String filenameOrNull(@Nullable String storedValue) {
        return FileSystemLocation.refersToStoredFile(storedValue) ? FileSystemLocation.filenameOf(storedValue) : storedValue;
    }

    /**
     * One of the URL templates, seen as a function of the owning id and the filename.
     */
    @FunctionalInterface
    private interface UrlTemplate {

        @NonNull
        PublicFileUrl of(long ownerId, @NonNull String filename);
    }
}
