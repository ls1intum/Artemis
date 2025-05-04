package de.tum.cit.aet.artemis.core;

import de.tum.cit.aet.artemis.core.service.FilePathService;

/**
 * Enum representing the different types of file paths/URIs used in the system.
 * Each type corresponds to a specific path prefix in the file path/URI.
 * see {@link FilePathService} for the actual path generation.
 */
public enum FilePathType {
    LECTURE_ATTACHMENT,
    /** Path for slide images when attachments are split into single slides **/
    SLIDE, TEMPORARY, DRAG_AND_DROP_BACKGROUND, DRAG_ITEM, PROFILE_PICTURE, COURSE_ICON, EXAM_USER_SIGNATURE, EXAM_ATTENDANCE_CHECK_STUDENT_IMAGE,
    /** Path for the student version of slides. This is a version of the actual slides with hidden slides published to students **/
    STUDENT_VERSION_SLIDES, FILE_UPLOAD_SUBMISSION, ATTACHMENT_UNIT
}
