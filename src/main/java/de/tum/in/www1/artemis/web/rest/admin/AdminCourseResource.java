package de.tum.in.www1.artemis.web.rest.admin;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.OAuth2JWKSService;
import de.tum.in.www1.artemis.security.annotations.EnforceAdmin;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

/**
 * REST controller for managing Course.
 */
@RestController
@RequestMapping("api/admin/")
public class AdminCourseResource {

    private final Logger log = LoggerFactory.getLogger(AdminCourseResource.class);

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final UserRepository userRepository;

    private final CourseService courseService;

    private final CourseRepository courseRepository;

    private final AuditEventRepository auditEventRepository;

    private final FileService fileService;

    private final OAuth2JWKSService oAuth2JWKSService;

    public AdminCourseResource(UserRepository userRepository, CourseService courseService, CourseRepository courseRepository, AuditEventRepository auditEventRepository,
            FileService fileService, OAuth2JWKSService oAuth2JWKSService) {
        this.courseService = courseService;
        this.courseRepository = courseRepository;
        this.auditEventRepository = auditEventRepository;
        this.userRepository = userRepository;
        this.fileService = fileService;
        this.oAuth2JWKSService = oAuth2JWKSService;
    }

    /**
     * POST /courses : create a new course.
     *
     * @param course the course to create
     * @param file the optional course icon file
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

        course.validateShortName();

        List<Course> coursesWithSameShortName = courseRepository.findAllByShortName(course.getShortName());
        if (!coursesWithSameShortName.isEmpty()) {
            return ResponseEntity.badRequest().headers(
                    HeaderUtil.createAlert(applicationName, "A course with the same short name already exists. Please choose a different short name.", "shortnameAlreadyExists"))
                    .body(null);
        }

        course.validateRegistrationConfirmationMessage();
        course.validateComplaintsAndRequestMoreFeedbackConfig();
        course.validateOnlineCourseAndRegistrationEnabled();
        course.validateOnlineCourseConfiguration();
        course.validateAccuracyOfScores();
        if (!course.isValidStartAndEndDate()) {
            throw new BadRequestAlertException("For Courses, the start date has to be before the end date", Course.ENTITY_NAME, "invalidCourseStartDate", true);
        }

        courseService.createOrValidateGroups(course);

        if (file != null) {
            String pathString = fileService.handleSaveFile(file, false, false);
            course.setCourseIcon(pathString);
        }

        Course result = courseRepository.save(course);
        if (course.isOnlineCourse()) {
            oAuth2JWKSService.updateKey(course.getOnlineCourseConfiguration().getRegistrationId());
        }
        return ResponseEntity.created(new URI("/api/courses/" + result.getId())).body(result);
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
        Course course = courseRepository.findByIdWithExercisesAndLecturesAndLectureUnitsAndLearningGoalsElseThrow(courseId);
        if (course == null) {
            throw new EntityNotFoundException("Course", courseId);
        }
        User user = userRepository.getUserWithGroupsAndAuthorities();
        var auditEvent = new AuditEvent(user.getLogin(), Constants.DELETE_COURSE, "course=" + course.getTitle());
        auditEventRepository.add(auditEvent);
        log.info("User {} has requested to delete the course {}", user.getLogin(), course.getTitle());

        courseService.delete(course);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, Course.ENTITY_NAME, course.getTitle())).build();
    }
}
