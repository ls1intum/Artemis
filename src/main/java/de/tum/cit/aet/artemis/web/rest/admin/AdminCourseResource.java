package de.tum.cit.aet.artemis.web.rest.admin;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
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

import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.domain.Course;
import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.domain.enumeration.DefaultChannelType;
import de.tum.cit.aet.artemis.domain.metis.conversation.Channel;
import de.tum.cit.aet.artemis.repository.CourseRepository;
import de.tum.cit.aet.artemis.repository.UserRepository;
import de.tum.cit.aet.artemis.service.CourseService;
import de.tum.cit.aet.artemis.service.FilePathService;
import de.tum.cit.aet.artemis.service.FileService;
import de.tum.cit.aet.artemis.service.OnlineCourseConfigurationService;
import de.tum.cit.aet.artemis.service.metis.conversation.ChannelService;
import de.tum.cit.aet.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.cit.aet.artemis.web.rest.util.HeaderUtil;

/**
 * REST controller for managing Course.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/admin/")
public class AdminCourseResource {

    private static final Logger log = LoggerFactory.getLogger(AdminCourseResource.class);

    private static final int MAX_TITLE_LENGTH = 255;

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final UserRepository userRepository;

    private final CourseService courseService;

    private final ChannelService channelService;

    private final CourseRepository courseRepository;

    private final AuditEventRepository auditEventRepository;

    private final FileService fileService;

    private final Optional<OnlineCourseConfigurationService> onlineCourseConfigurationService;

    public AdminCourseResource(UserRepository userRepository, CourseService courseService, CourseRepository courseRepository, AuditEventRepository auditEventRepository,
            FileService fileService, Optional<OnlineCourseConfigurationService> onlineCourseConfigurationService, ChannelService channelService) {
        this.courseService = courseService;
        this.courseRepository = courseRepository;
        this.auditEventRepository = auditEventRepository;
        this.userRepository = userRepository;
        this.fileService = fileService;
        this.onlineCourseConfigurationService = onlineCourseConfigurationService;
        this.channelService = channelService;
    }

    /**
     * GET /courses/groups : get all groups for all courses for administration purposes.
     *
     * @return the list of groups (the user has access to)
     */
    @GetMapping("courses/groups")
    @EnforceAdmin
    public ResponseEntity<Set<String>> getAllGroupsForAllCourses() {
        log.debug("REST request to get all Groups for all Courses");
        List<Course> courses = courseRepository.findAll();
        Set<String> groups = new LinkedHashSet<>();
        for (Course course : courses) {
            groups.add(course.getInstructorGroupName());
            groups.add(course.getEditorGroupName());
            groups.add(course.getTeachingAssistantGroupName());
            groups.add(course.getStudentGroupName());
        }
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
    @EnforceAdmin
    public ResponseEntity<Course> createCourse(@RequestPart Course course, @RequestPart(required = false) MultipartFile file) throws URISyntaxException {
        log.debug("REST request to save Course : {}", course);
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

        if (course.isOnlineCourse() && onlineCourseConfigurationService.isPresent()) {
            onlineCourseConfigurationService.get().createOnlineCourseConfiguration(course);
        }

        courseService.setDefaultGroupsIfNotSet(course);

        Course createdCourse = courseRepository.save(course);

        if (file != null) {
            Path basePath = FilePathService.getCourseIconFilePath();
            Path savePath = fileService.saveFile(file, basePath, false);
            createdCourse.setCourseIcon(FilePathService.publicPathForActualPathOrThrow(savePath, createdCourse.getId()).toString());
            createdCourse = courseRepository.save(createdCourse);
        }

        Course finalCreatedCourse = createdCourse;
        Arrays.stream(DefaultChannelType.values()).forEach(channelType -> createDefaultChannel(finalCreatedCourse, channelType));

        return ResponseEntity.created(new URI("/api/courses/" + createdCourse.getId())).body(createdCourse);
    }

    /**
     * DELETE /courses/:courseId : delete the "id" course.
     *
     * @param courseId the id of the course to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("courses/{courseId}")
    @EnforceAdmin
    public ResponseEntity<Void> deleteCourse(@PathVariable long courseId) {
        log.info("REST request to delete Course : {}", courseId);
        Course course = courseRepository.findByIdWithExercisesAndLecturesAndLectureUnitsAndCompetenciesElseThrow(courseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        var auditEvent = new AuditEvent(user.getLogin(), Constants.DELETE_COURSE, "course=" + course.getTitle());
        auditEventRepository.add(auditEvent);
        log.info("User {} has requested to delete the course {}", user.getLogin(), course.getTitle());

        courseService.delete(course);
        if (course.getCourseIcon() != null) {
            fileService.schedulePathForDeletion(FilePathService.actualPathForPublicPathOrThrow(URI.create(course.getCourseIcon())), 0);
        }
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, Course.ENTITY_NAME, course.getTitle())).build();
    }

    /**
     * Creates a default channel with the given name and adds all students, tutors and instructors as participants.
     *
     * @param course      the course, where the channel should be created
     * @param channelType the default channel type
     */
    private void createDefaultChannel(Course course, DefaultChannelType channelType) {
        Channel channelToCreate = new Channel();
        channelToCreate.setName(channelType.getName());
        channelToCreate.setIsPublic(true);
        channelToCreate.setIsCourseWide(true);
        channelToCreate.setIsAnnouncementChannel(channelType.equals(DefaultChannelType.ANNOUNCEMENT));
        channelToCreate.setIsArchived(false);
        channelToCreate.setDescription(null);
        channelService.createChannel(course, channelToCreate, Optional.empty());
    }
}
