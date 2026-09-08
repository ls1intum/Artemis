package de.tum.cit.aet.artemis.core.util;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.jspecify.annotations.NonNull;
import org.springframework.web.util.UriUtils;

import de.tum.cit.aet.artemis.core.FilePathType;

/**
 * The single owner of the REST URLs under which stored files are served.
 * <p>
 * Every served file type is one permitted record here, and each record carries exactly the metadata its URL needs. The metadata is therefore required by the type system: a
 * lecture attachment URL cannot be asked for without a lecture id, and a drag item URL cannot be asked for without both the question id and the drag item id. There is no
 * parsing and no reuse of the shape of a stored value; the template is hardcoded next to the metadata it consumes.
 * <p>
 * <b>Only real, servable URLs live here.</b> {@link FilePathType#TEMPORARY} has no record, because a temporary file is not served over REST at all: asking for its URL is a
 * compile error rather than a string that merely looks like a URL. The ids are primitives for the same reason. A caller that does not have the id yet, because the owning row has
 * not been inserted, has no URL to hand out and must not invent one; {@link ServedFileUrl} answers such a caller with the bare filename.
 * <p>
 * The URLs are relative to the {@code api/core/} request mapping of {@code de.tum.cit.aet.artemis.core.web.FileResource}, so they include the {@code files/} segment. The value
 * the JSON carries is one segment narrower, because the client's {@code addPublicFilePrefix} prepends {@code api/core/files/}; see {@link #clientPath()}.
 * <p>
 * Callers pass the filename as it is stored. It is percent-encoded as a single path segment on the way into the URL, so a filename can neither forge an extra path segment
 * nor make the URL unbuildable, whatever it contains; see {@link #uri(String, String)}. A filename written by this release has been through
 * {@link FileUtil#sanitizeFilename} and needs no encoding at all, so for every value this application writes the encoding is a no-op.
 *
 * @see FileSystemLocation for the file system location of the same files, which is independent of these URLs
 */
public sealed interface PublicFileUrl {

    /**
     * The first segment of every served file URL, relative to the {@code api/core/} request mapping that serves it.
     */
    String FILES_PREFIX = "files/";

    /**
     * Sub-path under which the images of a drag and drop question are served. It is question-scoped because a drag item id is only unique within its question, so the owning
     * question id has to be part of the path for the request to be authorizable at all.
     */
    String DRAG_AND_DROP_QUESTION_SUBPATH = "drag-and-drop/questions/";

    /**
     * @return the URL under which the file is served, relative to the {@code api/core/} request mapping of {@code FileResource}
     */
    @NonNull
    URI url();

    /**
     * The same URL without its leading {@code files/} segment, which is the form the JSON served to clients still carries.
     * <p>
     * A client appends what it receives to {@code api/core/files/} (see {@code addPublicFilePrefix} in {@code app.constants.ts}), so the value in the JSON has to be one segment
     * narrower than {@link #url()}. This is the only reason the two differ; both render the same hardcoded template. Once every client stops prepending anything, this method
     * goes and callers use {@link #url()}.
     * <p>
     * That is a later release's change rather than this one's, and not because of the web client: sending the whole URL changes the value of a JSON field, and unlike a path a
     * field has no room for a deprecated alias, so the mobile apps and the VS Code extension would double the prefix the moment the server started doing it. The reasoning is
     * written out once, at {@code addPublicFilePrefix}, next to the code that would have to change first. It is the same constraint that keeps {@code CoreLegacyFileRestPaths}
     * populated.
     *
     * @return the URL relative to the {@code api/core/files/} prefix the client prepends
     */
    @NonNull
    default String clientPath() {
        return url().toString().substring(FILES_PREFIX.length());
    }

    /**
     * The file type this URL belongs to. It names the type; it does not imply that this record also carries everything the file system path of the same file needs, which is
     * true for most types but not for {@link Slide}.
     *
     * @return the file path type served under this URL
     */
    @NonNull
    FilePathType filePathType();

    /**
     * A course icon.
     *
     * @param courseId the id of the course the icon belongs to
     * @param filename the sanitized filename of the icon
     */
    record CourseIcon(long courseId, @NonNull String filename) implements PublicFileUrl {

        @Override
        public URI url() {
            return uri(FILES_PREFIX + "courses/" + courseId + "/icons/", filename);
        }

        @Override
        public FilePathType filePathType() {
            return FilePathType.COURSE_ICON;
        }
    }

    /**
     * A user's profile picture.
     *
     * @param userId   the id of the user the picture belongs to
     * @param filename the sanitized filename of the picture
     */
    record ProfilePicture(long userId, @NonNull String filename) implements PublicFileUrl {

        @Override
        public URI url() {
            return uri(FILES_PREFIX + "users/" + userId + "/profile-pictures/", filename);
        }

        @Override
        public FilePathType filePathType() {
            return FilePathType.PROFILE_PICTURE;
        }
    }

    /**
     * The signature an exam participant gave on the exam cover page.
     *
     * @param examUserId the id of the exam user the signature belongs to
     * @param filename   the sanitized filename of the signature image
     */
    record ExamUserSignature(long examUserId, @NonNull String filename) implements PublicFileUrl {

        @Override
        public URI url() {
            return uri(FILES_PREFIX + "exam-users/" + examUserId + "/signatures/", filename);
        }

        @Override
        public FilePathType filePathType() {
            return FilePathType.EXAM_USER_SIGNATURE;
        }
    }

    /**
     * The identification photo of an exam participant.
     *
     * @param examUserId the id of the exam user the photo belongs to
     * @param filename   the sanitized filename of the photo
     */
    record ExamUserImage(long examUserId, @NonNull String filename) implements PublicFileUrl {

        @Override
        public URI url() {
            return uri(FILES_PREFIX + "exam-users/" + examUserId + "/", filename);
        }

        @Override
        public FilePathType filePathType() {
            return FilePathType.EXAM_USER_IMAGE;
        }
    }

    /**
     * The background image of a drag and drop question.
     *
     * @param questionId the id of the question the background belongs to
     * @param filename   the sanitized filename of the background image
     */
    record DragAndDropBackground(long questionId, @NonNull String filename) implements PublicFileUrl {

        @Override
        public URI url() {
            return uri(FILES_PREFIX + DRAG_AND_DROP_QUESTION_SUBPATH + questionId + "/backgrounds/", filename);
        }

        @Override
        public FilePathType filePathType() {
            return FilePathType.DRAG_AND_DROP_BACKGROUND;
        }
    }

    /**
     * The picture of a single drag item.
     * <p>
     * A drag item is not an entity of its own: it lives inside its question's JSON content and its id is only unique within that question. The URL therefore carries both ids,
     * which is also what lets {@code FileResource} authorize the request through the owning question.
     *
     * @param questionId the id of the owning drag and drop question
     * @param dragItemId the question-scoped id of the drag item
     * @param filename   the sanitized filename of the picture
     */
    record DragItem(long questionId, long dragItemId, @NonNull String filename) implements PublicFileUrl {

        @Override
        public URI url() {
            return uri(FILES_PREFIX + DRAG_AND_DROP_QUESTION_SUBPATH + questionId + "/drag-items/" + dragItemId + "/", filename);
        }

        @Override
        public FilePathType filePathType() {
            return FilePathType.DRAG_ITEM;
        }
    }

    /**
     * An attachment that hangs directly off a lecture.
     *
     * @param lectureId the id of the lecture the attachment belongs to
     * @param filename  the sanitized filename of the attachment
     */
    record LectureAttachment(long lectureId, @NonNull String filename) implements PublicFileUrl {

        @Override
        public URI url() {
            return uri(FILES_PREFIX + "attachments/lectures/" + lectureId + "/", filename);
        }

        @Override
        public FilePathType filePathType() {
            return FilePathType.LECTURE_ATTACHMENT;
        }
    }

    /**
     * The attachment of an attachment video unit.
     *
     * @param attachmentVideoUnitId the id of the attachment video unit the file belongs to
     * @param filename              the sanitized filename of the attachment
     */
    record AttachmentVideoUnitFile(long attachmentVideoUnitId, @NonNull String filename) implements PublicFileUrl {

        @Override
        public URI url() {
            return uri(FILES_PREFIX + "attachments/attachment-video-units/" + attachmentVideoUnitId + "/", filename);
        }

        @Override
        public FilePathType filePathType() {
            return FilePathType.ATTACHMENT_UNIT;
        }
    }

    /**
     * The student version of an attachment video unit's slides, which is the same document with the hidden slides removed.
     *
     * @param attachmentVideoUnitId the id of the attachment video unit the file belongs to
     * @param filename              the sanitized filename of the student version
     */
    record StudentVersionSlides(long attachmentVideoUnitId, @NonNull String filename) implements PublicFileUrl {

        @Override
        public URI url() {
            return uri(FILES_PREFIX + "attachments/attachment-video-units/" + attachmentVideoUnitId + "/student/", filename);
        }

        @Override
        public FilePathType filePathType() {
            return FilePathType.STUDENT_VERSION_SLIDES;
        }
    }

    /**
     * The rendered image of a single slide.
     * <p>
     * This is the one served file URL that carries no filename. {@code FileResource} looks the slide up by its id and reads the filename off the slide itself, so the id is the
     * whole URL. The client already asks for slides this way. The other slide mapping,
     * {@code files/attachments/attachment-video-units/{attachmentVideoUnitId}/slide/{slideNumber}}, addresses a slide by its position and equally carries no filename; it has no
     * caller, so it is not modelled here.
     *
     * @param slideId the id of the slide
     */
    record Slide(long slideId) implements PublicFileUrl {

        @Override
        public URI url() {
            return uri(FILES_PREFIX + "slides/" + slideId);
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
     * @param filename     the sanitized filename of the submitted file
     */
    record FileUploadSubmission(long exerciseId, long submissionId, @NonNull String filename) implements PublicFileUrl {

        @Override
        public URI url() {
            return uri(FILES_PREFIX + "file-upload-exercises/" + exerciseId + "/submissions/" + submissionId + "/", filename);
        }

        @Override
        public FilePathType filePathType() {
            return FilePathType.FILE_UPLOAD_SUBMISSION;
        }
    }

    /**
     * The one place where a template and a filename become a {@link URI}, so that any change to how a filename is encoded happens here and nowhere else.
     * <p>
     * <b>Building a URL for a stored file is total: there is no filename this can refuse.</b> {@link URI#create} would refuse several, and every one of them can be sitting in
     * the database today. {@link FileUtil#sanitizeFilename} reduces a filename to {@code [A-Za-z0-9._-]} before it is written, but it has only done so since 2021, and a value
     * written before that keeps the name the uploader gave the file. A single space in such a name is enough for {@link URI#create} to throw {@link IllegalArgumentException},
     * and this method is now on the read path of every entity that carries a file, so one such row would take down the whole response rather than one image.
     * <p>
     * The filename is therefore percent-encoded as a single path segment, which is total by construction: a space becomes {@code %20}, a {@code #} becomes {@code %23} instead
     * of starting a fragment, a non-ASCII character becomes its UTF-8 escape, and a {@code /} becomes {@code %2F} rather than a second segment. The fixed part of the template
     * is not touched, because it is written in this file and is already valid. What the client requests is decoded again by the servlet container, so the request reaches
     * {@code FileResource} carrying the filename that is on disk.
     * <p>
     * The inverse lives in {@link FileSystemLocation#storedFilename}, which decodes before it stores, so a client that sends back a URL it was served stores the filename it
     * started with rather than an escaped copy of it. {@code PublicFileUrlTest} pins each of the four cases above and the round trip.
     *
     * @param template the fixed part of the URL, relative to the {@code api/core/} request mapping, ending in a slash
     * @param filename the filename to append as the last path segment
     * @return the URL as a URI
     */
    private static URI uri(@NonNull String template, @NonNull String filename) {
        return URI.create(template + UriUtils.encodePathSegment(filename, StandardCharsets.UTF_8));
    }

    /**
     * The variant for the one URL that carries no filename, {@link Slide}. Its template is entirely fixed, so there is nothing to encode.
     *
     * @param url the assembled URL, relative to the {@code api/core/} request mapping
     * @return the URL as a URI
     */
    private static URI uri(@NonNull String url) {
        return URI.create(url);
    }
}
