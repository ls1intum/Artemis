package de.tum.cit.aet.artemis.core.config;

/**
 * The legacy spellings of the file serving paths, kept alongside their canonical successors in {@code FileResource}.
 * <p>
 * Unlike the other {@code *LegacyRestPaths} classes these are not class-level prefixes but whole method-level paths, so they carry no {@code Deprecation} header: the generic
 * {@code LegacyApiPathDeprecationInterceptor} tags a response only when the request came in under a legacy class-level prefix, and every path here shares the canonical
 * {@code api/core/} prefix of {@code FileResource}.
 * <p>
 * <b>Why every one of them still exists.</b> Until this release the server stored the URL a file is served under and handed that stored string to the client, which appended it
 * to {@code api/core/files/}. The spellings below are exactly what the previous release emits, one per file type, straight out of its
 * {@code FilePathConverter#externalUriForFileSystemPath}. The server now builds a canonical URL from a hardcoded template plus the owning entity, so it no longer emits any of
 * them, but that does not make them unreachable:
 * <ul>
 * <li>Four are permanent. A post embeds a fragment of the attachment link it references and the client re-expands that fragment against {@code api/core/files/attachments/}, so
 * a post written before this release resolves to the legacy spelling forever. That content is user-authored prose in the database and is deliberately not migrated. See
 * {@code lecture-attachment-reference.action.ts}, which writes the fragment, and {@code posting-content.components.ts}, which re-expands it.</li>
 * <li>The rest are reachable for as long as a client still holds a URL it was handed before the upgrade: a browser tab opened before the deployment, a cached mobile response,
 * or a node still running the previous release during a rolling deployment. The REST guideline is explicit that a path the mobile apps and the VS Code extension consume is
 * never removed outright, and every one of these was handed to all of them.</li>
 * </ul>
 * Retiring one is therefore a later release's change, not this one's: this release is the one that stops emitting them, and the clients have to stop asking for them first.
 * <p>
 * The constants are {@link Deprecated @Deprecated(forRemoval = true)} on purpose, so that every use site carries a compile-time warning and the eventual cleanup is a mechanical
 * "remove every reference, then delete the constant" job.
 * <p>
 * Not listed here because it is not a legacy alias: {@code files/templates/{language}} is the form
 * {@code FileService#getTemplateFile} requests whenever the project type is undefined, and it is the client that builds it rather than receives it. It stays for as long as that
 * call site does.
 */
public final class CoreLegacyFileRestPaths {

    /**
     * Was emitted as {@code drag-and-drop/backgrounds/{questionId}/{filename}}. Successor:
     * {@code files/drag-and-drop/questions/{questionId}/backgrounds/*}.
     */
    @Deprecated(forRemoval = true, since = "10.0")
    public static final String DRAG_AND_DROP_BACKGROUND = "files/drag-and-drop/backgrounds/{questionId}/*";

    /**
     * Was emitted as {@code course/icons/{courseId}/{filename}}. Successor: {@code files/courses/{courseId}/icons/*}.
     */
    @Deprecated(forRemoval = true, since = "10.0")
    public static final String COURSE_ICON = "files/course/icons/{courseId}/*";

    /**
     * Was emitted as {@code user/profile-pictures/{userId}/{filename}}. Successor: {@code files/users/{userId}/profile-pictures/*}.
     * <p>
     * This is the rename that made the case for the whole decoupling: moving the id one segment to the right lengthened every stored value by one character and had to widen
     * {@code jhi_user.image_url}.
     */
    @Deprecated(forRemoval = true, since = "10.0")
    public static final String PROFILE_PICTURE = "files/user/profile-pictures/{userId}/*";

    /**
     * Was emitted as {@code exam-user/signatures/{examUserId}/{filename}}. Successor: {@code files/exam-users/{examUserId}/signatures/*}.
     */
    @Deprecated(forRemoval = true, since = "10.0")
    public static final String EXAM_USER_SIGNATURE = "files/exam-user/signatures/{examUserId}/*";

    /**
     * Was emitted as {@code exam-user/{examUserId}/{filename}}. Successor: {@code files/exam-users/{examUserId}/*}.
     */
    @Deprecated(forRemoval = true, since = "10.0")
    public static final String EXAM_USER_IMAGE = "files/exam-user/{examUserId}/*";

    /**
     * Was emitted as {@code attachments/lecture/{lectureId}/{filename}}. Successor: {@code files/attachments/lectures/{lectureId}/{attachmentName}}.
     * <p>
     * Permanent: this is the spelling a post written before this release carries, because the editor stored everything after {@code attachments/} of the link it was serving.
     */
    @Deprecated(forRemoval = true, since = "10.0")
    public static final String LECTURE_ATTACHMENT = "files/attachments/lecture/{lectureId}/{attachmentName}";

    /**
     * The merged PDF of every attachment of a lecture, under the old singular spelling. Successor: {@code files/attachments/lectures/{lectureId}/merge-pdf}.
     * <p>
     * The only path here that was never emitted: a client builds it. The web client already builds the canonical one, so this is held open by the mobile clients alone.
     */
    @Deprecated(forRemoval = true, since = "10.0")
    public static final String LECTURE_ATTACHMENTS_MERGED = "files/attachments/lecture/{lectureId}/merge-pdf";

    /**
     * Was emitted as {@code attachments/attachment-unit/{attachmentVideoUnitId}/{filename}}. Successor:
     * {@code files/attachments/attachment-video-units/{attachmentVideoUnitId}/*}.
     * <p>
     * Permanent, for the same reason as {@link #LECTURE_ATTACHMENT}.
     */
    @Deprecated(forRemoval = true, since = "10.0")
    public static final String ATTACHMENT_VIDEO_UNIT_FILE = "files/attachments/attachment-unit/{attachmentVideoUnitId}/*";

    /**
     * Successor: {@code files/attachments/attachment-video-units/{attachmentVideoUnitId}/slide/{slideNumber}}.
     * <p>
     * Permanent: a slide reference written before the editor moved to {@code (#slideId)} carries {@code attachment-unit/{id}/slide/{n}} in the post.
     */
    @Deprecated(forRemoval = true, since = "10.0")
    public static final String ATTACHMENT_VIDEO_UNIT_SLIDE = "files/attachments/attachment-unit/{attachmentVideoUnitId}/slide/{slideNumber}";

    /**
     * Was emitted as {@code attachments/attachment-unit/{attachmentVideoUnitId}/student/{filename}}. Successor:
     * {@code files/attachments/attachment-video-units/{attachmentVideoUnitId}/student/*}.
     * <p>
     * Permanent: a lecture unit reference in a post carries the student version link, so this is the spelling those posts hold.
     */
    @Deprecated(forRemoval = true, since = "10.0")
    public static final String ATTACHMENT_VIDEO_UNIT_STUDENT_VERSION = "files/attachments/attachment-unit/{attachmentVideoUnitId}/student/*";

    private CoreLegacyFileRestPaths() {
        // utility class
    }
}
