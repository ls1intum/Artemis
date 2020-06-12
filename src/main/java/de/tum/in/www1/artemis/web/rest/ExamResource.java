package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.notFound;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.CourseService;
import de.tum.in.www1.artemis.service.ExamService;
import de.tum.in.www1.artemis.service.UserService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

/**
 * REST controller for managing Exam.
 */
@RestController
@RequestMapping("/api")
public class ExamResource {

    private final Logger log = LoggerFactory.getLogger(ExamResource.class);

    private static final String ENTITY_NAME = "exam";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final UserService userService;

    private final CourseService courseService;

    private final ExamService examService;

    private final ExamRepository examRepository;

    private final AuthorizationCheckService authCheckService;

    private final AuditEventRepository auditEventRepository;

    public ExamResource(UserService userService, CourseService courseService, ExamService examService, ExamRepository examRepository, AuthorizationCheckService authCheckService,
            AuditEventRepository auditEventRepository) {
        this.userService = userService;
        this.courseService = courseService;
        this.examService = examService;
        this.examRepository = examRepository;
        this.authCheckService = authCheckService;
        this.auditEventRepository = auditEventRepository;
    }

    /**
     * POST /courses/{courseId}/exams : Create a new exam.
     *
     * @param courseId  the course to which the exam belongs
     * @param exam      the exam to create
     * @return the ResponseEntity with status 201 (Created) and with body the new exam, or with status 400 (Bad Request) if the exam has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/courses/{courseId}/exams")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<Exam> createExam(@PathVariable Long courseId, @RequestBody Exam exam) throws URISyntaxException {
        log.debug("REST request to create an exam : {}", exam);
        if (exam.getId() != null) {
            throw new BadRequestAlertException("A new exam cannot already have an ID", ENTITY_NAME, "idexists");
        }

        User user = userService.getUserWithGroupsAndAuthorities();
        Course course = courseService.findOne(courseId);
        if (!authCheckService.isAtLeastInstructorInCourse(course, user)) {
            return forbidden();
        }

        Exam result = examService.save(exam);
        return ResponseEntity.created(new URI("/api/courses/" + courseId + "/exams/" + result.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.getTitle())).body(result);
    }

    /**
     * PUT /courses/{courseId}/exams : Updates an existing exam.
     *
     * @param courseId      the course to which the exam belongs
     * @param updatedExam   the exam to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated exam
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/courses/{courseId}/exams")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<Exam> updateExam(@PathVariable Long courseId, @RequestBody Exam updatedExam) throws URISyntaxException {
        log.debug("REST request to update an exam : {}", updatedExam);
        if (updatedExam.getId() == null) {
            return createExam(courseId, updatedExam);
        }

        User user = userService.getUserWithGroupsAndAuthorities();
        Course course = courseService.findOne(courseId);
        if (!authCheckService.isAtLeastInstructorInCourse(course, user)) {
            return forbidden();
        }

        Optional<Exam> existingExam = examRepository.findById(updatedExam.getId());
        if (existingExam.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Exam result = examService.save(updatedExam);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, result.getTitle())).body(result);
    }

    /**
     * GET /courses/{courseId}/exams : Get all exams for a course.
     *
     * @param courseId      the courseId to which the exam belongs
     * @return the ResponseEntity with status 200 (OK) and with body exam or status 403 (FORBIDDEN) if the user does not have admin or instructor rights
     */
    @GetMapping("/courses/{courseId}/exams")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<Set<Exam>> getExams(@PathVariable Long courseId) {
        log.debug("REST request to get all exams for course with id: {}", courseId);

        User user = userService.getUserWithGroupsAndAuthorities();
        Course course = courseService.findOne(courseId);
        if (!authCheckService.isAtLeastInstructorInCourse(course, user)) {
            return forbidden();
        }

        Set<Exam> result = examService.findAllByCourseId(courseId);
        return ResponseEntity.ok().body(result);
    }

    /**
     * DELETE /courses/:courseId/exams/:examId : delete an exam of a course.
     *
     * @param examId the id of the course to delete
     * @param courseId the id of the course to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/courses/{courseId}/exams/{examId}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Void> deleteCourse(@PathVariable long examId, @PathVariable String courseId) {
        log.info("REST request to delete exam : {}", examId);
        User user = userService.getUserWithGroupsAndAuthorities();
        Exam exam = examService.findOne(examId);
        if (exam == null) {
            return notFound();
        }
        var auditEvent = new AuditEvent(user.getLogin(), Constants.DELETE_EXAM, "exam=" + exam.getTitle());
        auditEventRepository.add(auditEvent);
        log.info("User " + user.getLogin() + " has requested to delete the exam {}", exam.getTitle());
        examService.delete(examId);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, exam.getTitle())).build();
    }
}
