package de.tum.cit.aet.artemis.core;

import de.tum.cit.aet.artemis.core.util.FilePathConverter;

/**
 * <p>
 * <b>Enum representing the different types of file paths/URIs used in the system.</b>
 * </p>
 * <p>
 * Each type corresponds to a specific path prefix in the file path/URI.
 * </p>
 *
 * <ul>
 * <li>Make sure that the enum value reflects the actual path prefix, so it's easy to find in the file system.</li>
 * <li>Ensure related items in the file system are grouped together.</li>
 * </ul>
 *
 * @see FilePathConverter FilePathService for the actual path generation.
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

    /** <strong> Dnd Quiz file paths </strong> **/
    DRAG_AND_DROP_BACKGROUND,
    DRAG_ITEM,

    /** <strong> Exam participant information file paths </strong> **/
    EXAM_USER_SIGNATURE,
    EXAM_USER_IMAGE,

    /** <strong> Course file paths </strong> **/
    COURSE_ICON,

    /** <strong> User file paths </strong> **/
    PROFILE_PICTURE,

    /** <strong> Exercise file paths </strong> **/
    FILE_UPLOAD_SUBMISSION,

    /** <strong> Other file paths </strong> **/
    TEMPORARY
}
