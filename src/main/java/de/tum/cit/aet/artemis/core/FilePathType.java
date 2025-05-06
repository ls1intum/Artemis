package de.tum.cit.aet.artemis.core;

import de.tum.cit.aet.artemis.core.service.FilePathService;

/**
 * Enum representing the different types of file paths/URIs used in the system.
 * Each type corresponds to a specific path prefix in the file path/URI.
 * see {@link FilePathService} for the actual path generation.
 * Make sure that the enum value reflects the actual path prefix, so it's easy to find in the file system.
 * Make sure related items in the file system are grouped together.
 *
 */
public enum FilePathType {
    // @formatter:off

    /** Lecture file paths **/
    LECTURE_ATTACHMENT,
    ATTACHMENT_UNIT,
    /** Path for slide images when attachments are split into single slides **/
    SLIDE,
    /** Path for the student version of slides. This is a version of the actual slides with hidden slides published to students **/
    STUDENT_VERSION_SLIDES,

    /** Dnd Quiz file paths **/
    DRAG_AND_DROP_BACKGROUND,
    DRAG_ITEM,

    /** Exam participant information file paths **/
    EXAM_USER_SIGNATURE,
    EXAM_USER_IMAGE,

    /** Course file paths **/
    COURSE_ICON,

    /** User file paths **/
    PROFILE_PICTURE,

    /** Exercise file paths **/
    FILE_UPLOAD_SUBMISSION,

    /** Other file paths **/
    TEMPORARY
}
