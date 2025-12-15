package de.tum.cit.aet.artemis.core.web.admin;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
import de.tum.cit.aet.artemis.core.dto.CourseDeletionSummaryDTO;
import de.tum.cit.aet.artemis.core.dto.CourseGroupsDTO;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.core.service.course.CourseAccessService;
import de.tum.cit.aet.artemis.core.service.course.CourseAdminService;
import de.tum.cit.aet.artemis.core.service.course.CourseDeletionService;
import de.tum.cit.aet.artemis.core.service.course.CourseLoadService;
import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.core.util.FileUtil;
import de.tum.cit.aet.artemis.core.util.HeaderUtil;
import de.tum.cit.aet.artemis.lti.api.LtiApi;

/**
 * REST controller for managing Course.
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

    private final CourseLoadService courseLoadService;

    private final UserRepository userRepository;

    private final CourseAdminService courseAdminService;

    private final ChannelService channelService;

    private final CourseRepository courseRepository;

    private final AuditEventRepository auditEventRepository;

    private final FileService fileService;

    private final Optional<LtiApi> ltiApi;

    private final CourseDeletionService courseDeletionService;

    public AdminCourseResource(UserRepository userRepository, CourseAdminService courseAdminService, CourseRepository courseRepository, AuditEventRepository auditEventRepository,
            FileService fileService, Optional<LtiApi> ltiApi, ChannelService channelService, CourseDeletionService courseDeletionService, CourseAccessService courseAccessService,
            CourseLoadService courseLoadService) {
        this.courseAdminService = courseAdminService;
        this.courseRepository = courseRepository;
        this.auditEventRepository = auditEventRepository;
        this.userRepository = userRepository;
        this.fileService = fileService;
        this.ltiApi = ltiApi;
        this.channelService = channelService;
        this.courseDeletionService = courseDeletionService;
        this.courseAccessService = courseAccessService;
        this.courseLoadService = courseLoadService;
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
     * POST /courses : create a new course.
     *
     * @param course the course to create
     * @param file   the optional course icon file
     * @return the ResponseEntity with status 201 (Created) and with body the new course, or with status 400 (Bad Request) if the course has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping(value = "courses", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    // TODO: use a DTO and do not save on an entity serialized from the client
    public ResponseEntity<Course> createCourse(@RequestPart Course course, @RequestPart(required = false) MultipartFile file) throws URISyntaxException {
        log.debug("REST request to save Course : {}", course);

        // Validate file size
        FileUtil.validateFileSize(file, Constants.MAX_FILE_SIZE);

        if (course.getId() != null) {
            throw new BadRequestAlertException("A new course cannot already have an ID", Course.ENTITY_NAME, "idExists");
        }

        if (course.getTitle().length() > MAX_TITLE_LENGTH) {
            throw new BadRequestAlertException("The course title is too long", Course.ENTITY_NAME, "courseTitleTooLong");
        }

        course.validateShortName();

        List<Course> coursesWithSameShortName = courseRepository.findAllByShortName(course.getShortName());
        if (!coursesWithSameShortName.isEmpty()) {
            return ResponseEntity.badRequest().headers(
                    HeaderUtil.createAlert(applicationName, "A course with the same short name already exists. Please choose a different short name.", "shortnameAlreadyExists"))
                    .body(null);
        }

        course.validateEnrollmentConfirmationMessage();
        course.validateComplaintsAndRequestMoreFeedbackConfig();
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
     * DELETE /courses/:courseId : delete the "id" course.
     *
     * @param courseId the id of the course to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("courses/{courseId}")
    public ResponseEntity<Void> deleteCourse(@PathVariable long courseId) {
        log.info("REST request to delete Course : {}", courseId);
        Course course = courseLoadService.loadCourseWithExercisesLecturesLectureUnitsCompetenciesAndPrerequisites(courseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        var auditEvent = new AuditEvent(user.getLogin(), Constants.DELETE_COURSE, "course=" + course.getTitle());
        auditEventRepository.add(auditEvent);
        log.info("User {} has requested to delete the course {}", user.getLogin(), course.getTitle());

        courseDeletionService.delete(course);
        if (course.getCourseIcon() != null) {
            fileService.schedulePathForDeletion(FilePathConverter.fileSystemPathForExternalUri(URI.create(course.getCourseIcon()), FilePathType.COURSE_ICON), 0);
        }
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, Course.ENTITY_NAME, course.getTitle())).build();
    }

    /**
     * GET /courses/:courseId/deletion-summary : get the deletion summary for the course with the given id.
     *
     * @param courseId the id of the course
     * @return the ResponseEntity with status 200 (OK) and the deletion summary in the body
     */
    @GetMapping("courses/{courseId}/deletion-summary")
    public ResponseEntity<CourseDeletionSummaryDTO> getDeletionSummary(@PathVariable long courseId) {
        log.debug("REST request to get deletion summary course: {}", courseId);
        return ResponseEntity.ok().body(courseAdminService.getDeletionSummary(courseId));
    }
}
