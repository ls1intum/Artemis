package de.tum.cit.aet.artemis.core.web.admin;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.core.FilePathType;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.CourseCreateDTO;
import de.tum.cit.aet.artemis.core.dto.CourseGroupsDTO;
import de.tum.cit.aet.artemis.core.dto.CourseSummaryDTO;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.core.service.course.CourseAccessService;
import de.tum.cit.aet.artemis.core.service.course.CourseAdminService;
import de.tum.cit.aet.artemis.core.service.course.CourseDeletionService;
import de.tum.cit.aet.artemis.core.service.course.CourseResetService;
import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.core.util.FileUtil;
import de.tum.cit.aet.artemis.core.util.HeaderUtil;
import de.tum.cit.aet.artemis.lti.api.LtiApi;

/**
 * REST controller for administrative course management operations.
 * <p>
 * This controller provides endpoints for admin-only operations on courses, including:
 * <ul>
 * <li>Creating new courses with optional course icons</li>
 * <li>Deleting courses and all associated data</li>
 * <li>Resetting courses to remove student data while preserving structure</li>
 * <li>Retrieving summaries of what will be affected by delete/reset operations</li>
 * </ul>
 * <p>
 * All endpoints in this controller require admin privileges (enforced by {@link EnforceAdmin}).
 * Operations are logged via Spring's audit event repository for compliance tracking.
 *
 * @see CourseDeletionService for course deletion logic
 * @see CourseResetService for course reset logic
 * @see CourseAdminService for summary calculations
 */
@Profile(PROFILE_CORE)
@EnforceAdmin
@Lazy
@RestController
@RequestMapping("api/core/admin/")
public class AdminCourseResource {

    private static final Logger log = LoggerFactory.getLogger(AdminCourseResource.class);

    private static final int MAX_TITLE_LENGTH = 255;

    private final CourseAccessService courseAccessService;

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final UserRepository userRepository;

    private final CourseAdminService courseAdminService;

    private final ChannelService channelService;

    private final CourseRepository courseRepository;

    private final AuditEventRepository auditEventRepository;

    private final FileService fileService;

    private final Optional<LtiApi> ltiApi;

    private final CourseDeletionService courseDeletionService;

    private final CourseResetService courseResetService;

    public AdminCourseResource(UserRepository userRepository, CourseAdminService courseAdminService, CourseRepository courseRepository, AuditEventRepository auditEventRepository,
            FileService fileService, Optional<LtiApi> ltiApi, ChannelService channelService, CourseDeletionService courseDeletionService, CourseAccessService courseAccessService,
            CourseResetService courseResetService) {
        this.courseAdminService = courseAdminService;
        this.courseRepository = courseRepository;
        this.auditEventRepository = auditEventRepository;
        this.userRepository = userRepository;
        this.fileService = fileService;
        this.ltiApi = ltiApi;
        this.channelService = channelService;
        this.courseDeletionService = courseDeletionService;
        this.courseAccessService = courseAccessService;
        this.courseResetService = courseResetService;
    }

    /**
     * GET /courses/groups : get all groups for all courses for administration purposes.
     *
     * @return the list of groups (the user has access to)
     */
    @GetMapping("courses/groups")
    public ResponseEntity<Set<String>> getAllGroupsForAllCourses() {
        log.debug("REST request to get all Groups for all Courses");
        Set<CourseGroupsDTO> courseGroups = courseRepository.findAllCourseGroups();
        Set<String> groups = new HashSet<>();
        courseGroups.forEach(courseGroup -> {
            groups.add(courseGroup.instructorGroupName());
            groups.add(courseGroup.editorGroupName());
            groups.add(courseGroup.teachingAssistantGroupName());
            groups.add(courseGroup.studentGroupName());
        });
        groups.remove(null); // remove a potential null group
        return ResponseEntity.ok().body(groups);
    }

    /**
     * POST /courses : Create a new course.
     * <p>
     * Creates a new course using the provided DTO, which ensures a clean, server-controlled
     * entity state. The course goes through several validation steps:
     * <ul>
     * <li>Title length validation (max 255 characters)</li>
     * <li>Short name format and uniqueness validation</li>
     * <li>Enrollment and complaint configuration validation</li>
     * <li>Date range validation (start date before end date)</li>
     * </ul>
     * <p>
     * If no group names are provided, default groups are created using the ARTEMIS_GROUP_DEFAULT_PREFIX.
     * For online courses with LTI enabled, an online course configuration is automatically created.
     * Default channels (announcements, general, etc.) are created for the course.
     *
     * @param courseDTO the DTO containing the course data to create (multipart form part "course")
     * @param file      the optional course icon file (PNG/JPG image)
     * @return the ResponseEntity with status 201 (Created) and the new course in the body,
     *         or status 400 (Bad Request) if validation fails
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping(value = "courses", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Course> createCourse(@RequestPart("course") @Valid CourseCreateDTO courseDTO, @RequestPart(required = false) MultipartFile file)
            throws URISyntaxException {
        log.debug("REST request to save Course : {}", courseDTO.title());

        if (courseDTO.title().length() > MAX_TITLE_LENGTH) {
            throw new BadRequestAlertException("The course title is too long", Course.ENTITY_NAME, "courseTitleTooLong");
        }

        // Convert DTO to entity - this ensures a clean, server-controlled entity state
        Course course = courseDTO.toCourse();

        course.validateShortName();

        List<Course> coursesWithSameShortName = courseRepository.findAllByShortName(course.getShortName());
        if (!coursesWithSameShortName.isEmpty()) {
            return ResponseEntity.badRequest().headers(
                    HeaderUtil.createAlert(applicationName, "A course with the same short name already exists. Please choose a different short name.", "shortnameAlreadyExists"))
                    .body(null);
        }

        if (course.getEnrollmentConfiguration() != null) {
            course.getEnrollmentConfiguration().validateEnrollmentConfirmationMessage();
        }
        if (course.getComplaintConfiguration() != null) {
            course.getComplaintConfiguration().validateComplaintsAndRequestMoreFeedbackConfig();
        }
        course.validateOnlineCourseAndEnrollmentEnabled();
        course.validateAccuracyOfScores();
        course.validateStartAndEndDate();

        if (course.isOnlineCourse() && ltiApi.isPresent()) {
            ltiApi.get().createOnlineCourseConfiguration(course);
        }

        courseAccessService.setDefaultGroupsIfNotSet(course);

        Course createdCourse = courseRepository.save(course);

        if (file != null) {
            Path basePath = FilePathConverter.getCourseIconFilePath();
            Path savePath = FileUtil.saveFile(file, basePath, FilePathType.COURSE_ICON, false);
            createdCourse.setCourseIcon(FilePathConverter.externalUriForFileSystemPath(savePath, FilePathType.COURSE_ICON, createdCourse.getId()).toString());
            createdCourse = courseRepository.save(createdCourse);
        }

        channelService.createDefaultChannels(createdCourse);

        return ResponseEntity.created(new URI("/api/core/courses/" + createdCourse.getId())).body(createdCourse);
    }

    /**
     * DELETE /courses/:courseId : Delete a course and all associated data.
     * <p>
     * Permanently deletes the course and all associated elements including:
     * <ul>
     * <li>All exercises with their participations, submissions, and results</li>
     * <li>All programming exercise repositories and build plans</li>
     * <li>All lectures with their attachments</li>
     * <li>All exams with student exam data</li>
     * <li>All conversations, posts, and messages</li>
     * <li>All competencies and student progress</li>
     * <li>All Iris chat sessions and LLM usage traces</li>
     * <li>Default user groups created by Artemis</li>
     * </ul>
     * <p>
     * The operation is logged as an audit event for compliance purposes.
     * This action cannot be undone.
     *
     * @param courseId the ID of the course to delete
     * @return the ResponseEntity with status 200 (OK) on success,
     *         or status 404 (Not Found) if the course does not exist
     */
    @DeleteMapping("courses/{courseId}")
    public ResponseEntity<Void> deleteCourse(@PathVariable long courseId) {
        log.info("REST request to delete Course : {}", courseId);

        // Load only minimal data needed for audit logging and cleanup
        String courseTitle = courseRepository.getCourseTitle(courseId);
        if (courseTitle == null) {
            throw new EntityNotFoundException("Course", courseId);
        }
        String courseIcon = courseRepository.getCourseIconById(courseId);

        User user = userRepository.getUserWithGroupsAndAuthorities();
        var auditEvent = new AuditEvent(user.getLogin(), Constants.DELETE_COURSE, "course=" + courseTitle);
        auditEventRepository.add(auditEvent);
        log.info("User {} has requested to delete the course {}", user.getLogin(), courseTitle);

        courseDeletionService.delete(courseId);

        if (courseIcon != null) {
            fileService.schedulePathForDeletion(FilePathConverter.fileSystemPathForExternalUri(URI.create(courseIcon), FilePathType.COURSE_ICON), 0);
        }
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, Course.ENTITY_NAME, courseTitle)).build();
    }

    /**
     * GET /courses/:courseId/summary : Get a comprehensive summary of all course data.
     * <p>
     * Returns counts of all entities in the course, including users, exercises, exams,
     * posts, competencies, and AI data. This unified endpoint is used by both the
     * deletion and reset confirmation dialogs to display impact information.
     * <p>
     * The client determines which fields to display based on the operation type:
     * <ul>
     * <li>For deletion: All data is shown (everything will be permanently removed)</li>
     * <li>For reset: Only student data is shown (course structure is preserved)</li>
     * </ul>
     *
     * @param courseId the ID of the course to get the summary for
     * @return the ResponseEntity with status 200 (OK) and the course summary in the body
     */
    @GetMapping("courses/{courseId}/summary")
    public ResponseEntity<CourseSummaryDTO> getCourseSummary(@PathVariable long courseId) {
        log.debug("REST request to get summary for course: {}", courseId);
        return ResponseEntity.ok().body(courseAdminService.getCourseSummary(courseId));
    }

    /**
     * POST /courses/:courseId/reset : Reset a course by removing all student data.
     * <p>
     * Resets the course by deleting all student-generated data while preserving the course structure.
     * This is useful for data privacy compliance (e.g., GDPR) or reusing course templates for new semesters.
     * <p>
     * <b>Preserved data:</b>
     * <ul>
     * <li>Course configuration and settings</li>
     * <li>Exercise, exam, and lecture definitions</li>
     * <li>Competency definitions</li>
     * <li>Conversation/channel structure</li>
     * <li>Tutor, editor, and instructor group memberships</li>
     * </ul>
     * <p>
     * <b>Deleted data:</b>
     * <ul>
     * <li>All participations, submissions, and results</li>
     * <li>All student exam data</li>
     * <li>All posts and messages in conversations</li>
     * <li>All competency progress records</li>
     * <li>All learner profiles</li>
     * <li>All Iris chat sessions and LLM usage traces</li>
     * <li>Student enrollments (removed from student group)</li>
     * </ul>
     * <p>
     * The operation is logged as an audit event for compliance purposes.
     * It is recommended to archive the course before resetting to preserve student data.
     * This action cannot be undone.
     *
     * @param courseId the ID of the course to reset
     * @return the ResponseEntity with status 200 (OK) on success,
     *         or status 404 (Not Found) if the course does not exist
     * @see CourseResetService#resetStudentData(long)
     */
    @PostMapping("courses/{courseId}/reset")
    public ResponseEntity<Void> resetCourse(@PathVariable long courseId) {
        log.info("REST request to reset Course : {}", courseId);

        String courseTitle = courseRepository.getCourseTitle(courseId);
        if (courseTitle == null) {
            throw new EntityNotFoundException("Course", courseId);
        }

        User user = userRepository.getUserWithGroupsAndAuthorities();
        var auditEvent = new AuditEvent(user.getLogin(), Constants.RESET_COURSE, "course=" + courseTitle);
        auditEventRepository.add(auditEvent);
        log.info("User {} has requested to reset the course {}", user.getLogin(), courseTitle);

        courseResetService.resetStudentData(courseId);

        return ResponseEntity.ok().headers(HeaderUtil.createAlert(applicationName, "artemisApp.course.reset.success", courseTitle)).build();
    }
}
