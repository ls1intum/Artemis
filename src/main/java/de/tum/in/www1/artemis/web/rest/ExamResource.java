package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.notFound;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;

import javax.validation.constraints.NotNull;

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

        Optional<ResponseEntity<Exam>> courseAccessFailure = checkCourseAccess(courseId);
        if (courseAccessFailure.isPresent()) {
            return courseAccessFailure.get();
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

        Optional<ResponseEntity<Exam>> courseAccessFailure = checkCourseAccess(courseId);
        if (courseAccessFailure.isPresent()) {
            return courseAccessFailure.get();
        }

        Optional<Exam> existingExam = examRepository.findById(updatedExam.getId());
        if (existingExam.isEmpty()) {
            return notFound();
        }

        Exam result = examService.save(updatedExam);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, result.getTitle())).body(result);
    }

    /**
     * GET /courses/{courseId}/exams/{examId} : Find an exam by id.
     *
     * @param courseId  the course to which the exam belongs
     * @param examId    the exam to find
     * @return the ResponseEntity with status 200 (OK) and with the found exam as body
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @GetMapping("/courses/{courseId}/exams/{examId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<Exam> getExam(@PathVariable Long courseId, @PathVariable Long examId) throws URISyntaxException {
        log.debug("REST request to get Exam : {}", examId);
        Optional<Exam> exam = examRepository.findById(examId);
        Optional<ResponseEntity<Exam>> courseAndExamAccessFailure = checkCourseAndExamAccess(courseId, exam);
        return courseAndExamAccessFailure.orElseGet(() -> ResponseEntity.ok(exam.get()));
    }

    /**
     * GET /courses/{courseId}/exams : Find all exams for the given course.
     *
     * @param courseId  the course to which the exam belongs
     * @return the ResponseEntity with status 200 (OK) and a list of exams. The list can be empty
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @GetMapping("/courses/{courseId}/exams")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<List<Exam>> getExamsForCourse(@PathVariable Long courseId) throws URISyntaxException {
        log.debug("REST request to get all exams for Course : {}", courseId);
        Optional<ResponseEntity<List<Exam>>> courseAccessFailure = checkCourseAccess(courseId);
        return courseAccessFailure.orElseGet(() -> ResponseEntity.ok(examService.findAllByCourseId(courseId)));
    }

    /**
     * DELETE /courses/{courseId}/exams/{examId} : Delete the exam with the given id.
     *
     * @param courseId the course to which the exam belongs
     * @return the ResponseEntity with status 200 (OK)
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @DeleteMapping("/courses/{courseId}/exams/{examId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<Void> deleteExam(@PathVariable Long courseId, @PathVariable Long examId) throws URISyntaxException {
        log.info("REST request to delete Exam : {}", examId);

        Optional<Exam> exam = examRepository.findById(examId);

        Optional<ResponseEntity<Void>> courseAndExamAccessFailure = checkCourseAndExamAccess(courseId, exam);
        if (courseAndExamAccessFailure.isPresent()) {
            return courseAndExamAccessFailure.get();
        }

        User user = userService.getUser();
        AuditEvent auditEvent = new AuditEvent(user.getLogin(), Constants.DELETE_EXAM, "exam=" + exam.get().getTitle());
        auditEventRepository.add(auditEvent);
        log.info("User " + user.getLogin() + " has requested to delete the exam {}", exam.get().getTitle());

        examService.delete(examId);

        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, exam.get().getTitle())).build();
    }

    // TODO: move to a service as public method so it can be reused in other resources
    public <X> Optional<ResponseEntity<X>> checkCourseAccess(Long courseId) {
        Course course = courseService.findOne(courseId);
        if (!authCheckService.isAtLeastInstructorInCourse(course, null)) {
            return Optional.of(forbidden());
        }
        return Optional.empty();
    }

    // TODO: move to a service as public method so it can be reused in other resources
    private <X> Optional<ResponseEntity<X>> checkCourseAndExamAccess(Long courseId, @NotNull Optional<Exam> exam) {
        Optional<ResponseEntity<X>> courseAccessFailure = checkCourseAccess(courseId);
        if (courseAccessFailure.isPresent()) {
            return courseAccessFailure;
        }
        if (exam.isEmpty()) {
            return Optional.of(notFound());
        }
        if (!exam.get().getCourse().getId().equals(courseId)) {
            return Optional.of(forbidden());
        }
        return Optional.empty();
    }
}
