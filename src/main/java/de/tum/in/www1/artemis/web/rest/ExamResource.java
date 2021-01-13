package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.service.util.TimeLogUtil.formatDurationFrom;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

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
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.participation.TutorParticipation;
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.dto.StudentDTO;
import de.tum.in.www1.artemis.service.messaging.InstanceMessageSendService;
import de.tum.in.www1.artemis.web.rest.dto.ExamInformationDTO;
import de.tum.in.www1.artemis.web.rest.dto.ExamScoresDTO;
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

    private final ExamRepository examRepository;

    private final ExamService examService;

    private final StudentExamService studentExamService;

    private final ExamAccessService examAccessService;

    private final ExerciseService exerciseService;

    private final ParticipationService participationService;

    private final AuditEventRepository auditEventRepository;

    private final InstanceMessageSendService instanceMessageSendService;

    private final AuthorizationCheckService authCheckService;

    private final TutorParticipationService tutorParticipationService;

    private final AssessmentDashboardService assessmentDashboardService;

    public ExamResource(UserService userService, CourseService courseService, ExamRepository examRepository, ExamService examService, ExamAccessService examAccessService,
            ExerciseService exerciseService, AuditEventRepository auditEventRepository, InstanceMessageSendService instanceMessageSendService,
            StudentExamService studentExamService, ParticipationService participationService, AuthorizationCheckService authCheckService,
            TutorParticipationService tutorParticipationService, AssessmentDashboardService assessmentDashboardService) {
        this.userService = userService;
        this.courseService = courseService;
        this.examRepository = examRepository;
        this.examService = examService;
        this.examAccessService = examAccessService;
        this.exerciseService = exerciseService;
        this.auditEventRepository = auditEventRepository;
        this.instanceMessageSendService = instanceMessageSendService;
        this.studentExamService = studentExamService;
        this.participationService = participationService;
        this.authCheckService = authCheckService;
        this.tutorParticipationService = tutorParticipationService;
        this.assessmentDashboardService = assessmentDashboardService;
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

        if (exam.getCourse() == null) {
            return conflict();
        }

        if (!exam.getCourse().getId().equals(courseId)) {
            return conflict();
        }

        if (exam.getVisibleDate() == null || exam.getStartDate() == null || exam.getEndDate() == null || !exam.getVisibleDate().isBefore(exam.getStartDate())
                || !exam.getStartDate().isBefore(exam.getEndDate())) {
            return conflict();
        }

        // Check that exerciseGroups are not set to prevent manipulation of associated exerciseGroups
        if (!exam.getExerciseGroups().isEmpty()) {
            return forbidden();
        }

        Optional<ResponseEntity<Exam>> courseAccessFailure = examAccessService.checkCourseAccessForInstructor(courseId);
        if (courseAccessFailure.isPresent()) {
            return courseAccessFailure.get();
        }

        Exam result = examService.save(exam);
        return ResponseEntity.created(new URI("/api/courses/" + courseId + "/exams/" + result.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.getTitle())).body(result);
    }

    /**
     * PUT /courses/{courseId}/exams : Updates an existing exam.
     * This route does not save changes to the exercise groups. This should be done via the ExerciseGroupResource.
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

        if (updatedExam.getCourse() == null) {
            return conflict();
        }

        if (!updatedExam.getCourse().getId().equals(courseId)) {
            return conflict();
        }

        if (updatedExam.getVisibleDate() == null || updatedExam.getStartDate() == null || updatedExam.getEndDate() == null
                || !updatedExam.getVisibleDate().isBefore(updatedExam.getStartDate()) || !updatedExam.getStartDate().isBefore(updatedExam.getEndDate())) {
            return conflict();
        }

        Optional<ResponseEntity<Exam>> courseAndExamAccessFailure = examAccessService.checkCourseAndExamAccessForInstructor(courseId, updatedExam.getId());
        if (courseAndExamAccessFailure.isPresent()) {
            return courseAndExamAccessFailure.get();
        }

        // Make sure that the original references are preserved.
        Exam originalExam = examService.findOne(updatedExam.getId());

        // NOTE: Make sure that all references are preserved here
        updatedExam.setExerciseGroups(originalExam.getExerciseGroups());
        updatedExam.setStudentExams(originalExam.getStudentExams());
        updatedExam.setRegisteredUsers(originalExam.getRegisteredUsers());

        Exam result = examService.save(updatedExam);

        // We can't test dates for equality as the dates retrieved from the database lose precision. Also use instant to take timezones into account
        Comparator<ZonedDateTime> comparator = Comparator.comparing(date -> date.truncatedTo(ChronoUnit.SECONDS).toInstant());
        if (comparator.compare(originalExam.getVisibleDate(), updatedExam.getVisibleDate()) != 0
                || comparator.compare(originalExam.getStartDate(), updatedExam.getStartDate()) != 0) {
            // get all exercises
            Exam examWithExercises = examService.findOneWithExerciseGroupsAndExercises(result.getId());
            // for all programming exercises in the exam, send their ids for scheduling
            examWithExercises.getExerciseGroups().stream().flatMap(group -> group.getExercises().stream()).filter(exercise -> exercise instanceof ProgrammingExercise)
                    .map(Exercise::getId).forEach(instanceMessageSendService::sendProgrammingExerciseSchedule);
        }

        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, result.getTitle())).body(result);
    }

    /**
     * GET /courses/{courseId}/exams/{examId} : Find an exam by id.
     *
     * @param courseId              the course to which the exam belongs
     * @param examId                the exam to find
     * @param withStudents          boolean flag whether to include all students registered for the exam
     * @param withExerciseGroups    boolean flag whether to include all exercise groups of the exam
     * @return the ResponseEntity with status 200 (OK) and with the found exam as body
     */
    @GetMapping("/courses/{courseId}/exams/{examId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<Exam> getExam(@PathVariable Long courseId, @PathVariable Long examId, @RequestParam(defaultValue = "false") boolean withStudents,
            @RequestParam(defaultValue = "false") boolean withExerciseGroups) {
        log.debug("REST request to get exam : {}", examId);
        Optional<ResponseEntity<Exam>> courseAndExamAccessFailure = examAccessService.checkCourseAndExamAccessForInstructor(courseId, examId);
        if (courseAndExamAccessFailure.isPresent()) {
            return courseAndExamAccessFailure.get();
        }
        if (!withStudents && !withExerciseGroups) {
            return ResponseEntity.ok(examService.findOne(examId));
        }
        if (withStudents && withExerciseGroups) {
            return ResponseEntity.ok(examService.findOneWithRegisteredUsersAndExerciseGroupsAndExercises(examId));
        }
        if (withExerciseGroups) {
            return ResponseEntity.ok(examService.findOneWithExerciseGroupsAndExercises(examId));
        }
        Exam exam = examService.findOneWithRegisteredUsers(examId);
        exam.getRegisteredUsers().forEach(user -> user.setVisibleRegistrationNumber(user.getRegistrationNumber()));
        return ResponseEntity.ok(exam);
    }

    /**
     * GET /courses/{courseId}/exams/{examId}/scores : Find scores for an exam by id.
     *
     * @param courseId              the course to which the exam belongs
     * @param examId                the exam to find
     * @return the ResponseEntity with status 200 (OK) and with the found ExamScoreDTO as body
     */
    @GetMapping("/courses/{courseId}/exams/{examId}/scores")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'TA')")
    public ResponseEntity<ExamScoresDTO> getExamScore(@PathVariable Long courseId, @PathVariable Long examId) {
        long start = System.currentTimeMillis();
        log.debug("REST request to get score for exam : {}", examId);
        Optional<ResponseEntity<ExamScoresDTO>> courseAndExamAccessFailure = examAccessService.checkCourseAndExamAccessForTeachingAssistant(courseId, examId);
        if (courseAndExamAccessFailure.isPresent()) {
            return courseAndExamAccessFailure.get();
        }
        ExamScoresDTO examScoresDTO = examService.getExamScore(examId);
        log.info("get scores for exam " + examId + " took " + (System.currentTimeMillis() - start) + "ms");
        return ResponseEntity.ok(examScoresDTO);
    }

    /**
     * GET /courses/:courseId/exams/:examId:for-exam-tutor-dashboard
     *
     * @param courseId the id of the course to retrieve
     * @param examId the id of the exam that contains the exercises
     * @return data about a course including all exercises, plus some data for the tutor as tutor status for assessment
     */
    @GetMapping("/courses/{courseId}/exams/{examId}/for-exam-tutor-dashboard")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Exam> getExamForAssessmentDashboard(@PathVariable long courseId, @PathVariable long examId) {
        log.debug("REST request /courses/{courseId}/exams/{examId}/for-exam-tutor-dashboard");

        Exam exam = examService.findOneWithExerciseGroupsAndExercises(examId);
        Course course = exam.getCourse();
        if (!course.getId().equals(courseId)) {
            return conflict();
        }

        User user = userService.getUserWithGroupsAndAuthorities();

        if (!authCheckService.isAtLeastTeachingAssistantInCourse(course, user)) {
            return forbidden();
        }

        if (ZonedDateTime.now().isBefore(exam.getEndDate()) && authCheckService.isTeachingAssistantInCourse(course, user)) {
            // tutors cannot access the exercises before the exam ends
            return forbidden();
        }

        Set<Exercise> exercises = new HashSet<>();
        // extract all exercises for all the exam
        for (ExerciseGroup exerciseGroup : exam.getExerciseGroups()) {
            exerciseGroup.setExercises(courseService.getInterestingExercisesForAssessmentDashboards(exerciseGroup.getExercises()));
            exercises.addAll(exerciseGroup.getExercises());
        }

        List<TutorParticipation> tutorParticipations = tutorParticipationService.findAllByCourseAndTutor(course, user);
        assessmentDashboardService.prepareExercisesForAssessmentDashboard(exercises, tutorParticipations, true);

        return ResponseEntity.ok(exam);
    }

    /**
     * GET /courses/:courseId/exams/:examId:for-exam-tutor-test-run-dashboard
     *
     * @param courseId the id of the course to retrieve
     * @param examId the id of the exam that contains the exercises
     * @return data about a exam test run including all exercises, plus some data for the tutor as tutor status for assessment
     */
    @GetMapping("/courses/{courseId}/exams/{examId}/for-exam-tutor-test-run-dashboard")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Exam> getExamForTutorTestRunDashboard(@PathVariable long courseId, @PathVariable long examId) {
        log.debug("REST request /courses/{courseId}/exams/{examId}/for-exam-tutor-test-run-dashboard");

        Exam exam = examService.findOneWithExerciseGroupsAndExercises(examId);
        Course course = exam.getCourse();
        if (!course.getId().equals(courseId)) {
            return conflict();
        }

        User user = userService.getUserWithGroupsAndAuthorities();

        if (!authCheckService.isAtLeastInstructorInCourse(course, user)) {
            return forbidden();
        }

        for (ExerciseGroup exerciseGroup : exam.getExerciseGroups()) {
            exerciseGroup.setExercises(courseService.getInterestingExercisesForAssessmentDashboards(exerciseGroup.getExercises()));
        }

        return ResponseEntity.ok(exam);
    }

    /**
     * GET /courses/{courseId}/exams : Find all exams for the given course.
     *
     * @param courseId the course to which the exam belongs
     * @return the ResponseEntity with status 200 (OK) and a list of exams. The list can be empty
     */
    @GetMapping("/courses/{courseId}/exams")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'TA')")
    public ResponseEntity<List<Exam>> getExamsForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all exams for Course : {}", courseId);
        Optional<ResponseEntity<List<Exam>>> courseAccessFailure = examAccessService.checkCourseAccessForTeachingAssistant(courseId);
        return courseAccessFailure.orElseGet(() -> {
            List<Exam> exams = examService.findAllByCourseId(courseId);
            examService.setNumberOfRegisteredUsersForExams(exams);
            return ResponseEntity.ok(exams);
        });
    }

    /**
     * GET /exams/upcoming : Find all current and upcoming exams.
     *
     * @return the ResponseEntity with status 200 (OK) and a list of exams.
     */
    @GetMapping("/courses/upcoming-exams")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Exam>> getCurrentAndUpcomingExams() {
        log.debug("REST request to get all upcoming exams");

        if (!authCheckService.isAdmin()) {
            return forbidden();
        }

        List<Exam> upcomingExams = examService.findAllCurrentAndUpcomingExams();
        return ResponseEntity.ok(upcomingExams);
    }

    /**
     * DELETE /courses/{courseId}/exams/{examId} : Delete the exam with the given id.
     * The delete operation cascades to all student exams, exercise group, exercises and their participations.
     *
     * @param courseId  the course to which the exam belongs
     * @param examId    the id of the exam to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/courses/{courseId}/exams/{examId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<Void> deleteExam(@PathVariable Long courseId, @PathVariable Long examId) {
        log.info("REST request to delete exam : {}", examId);
        Optional<ResponseEntity<Void>> courseAndExamAccessFailure = examAccessService.checkCourseAndExamAccessForInstructor(courseId, examId);
        if (courseAndExamAccessFailure.isPresent()) {
            return courseAndExamAccessFailure.get();
        }
        User user = userService.getUser();
        Exam exam = examService.findOneWithExercisesGroupsAndStudentExamsByExamId(examId);
        log.info("User " + user.getLogin() + " has requested to delete the exam {}", exam.getTitle());
        AuditEvent auditEvent = new AuditEvent(user.getLogin(), Constants.DELETE_EXAM, "exam=" + exam.getTitle());
        auditEventRepository.add(auditEvent);
        examService.delete(exam);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, exam.getTitle())).build();
    }

    /**
     * POST /courses/:courseId/exams/:examId/students/:studentLogin : Add one single given user (based on the login) to the students of the exam so that the student can access the exam
     *
     * @param courseId     the id of the course
     * @param examId       the id of the exam
     * @param studentLogin the login of the user who should get student access
     * @return empty ResponseEntity with status 200 (OK) or with status 404 (Not Found)
     */
    @PostMapping(value = "/courses/{courseId}/exams/{examId}/students/{studentLogin:" + Constants.LOGIN_REGEX + "}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> addStudentToExam(@PathVariable Long courseId, @PathVariable Long examId, @PathVariable String studentLogin) {
        log.debug("REST request to add {} as student to exam : {}", studentLogin, examId);

        Optional<ResponseEntity<Void>> courseAndExamAccessFailure = examAccessService.checkCourseAndExamAccessForInstructor(courseId, examId);
        if (courseAndExamAccessFailure.isPresent()) {
            return courseAndExamAccessFailure.get();
        }

        var course = courseService.findOne(courseId);
        var exam = examService.findOneWithRegisteredUsers(examId);

        Optional<User> student = userService.getUserWithGroupsAndAuthoritiesByLogin(studentLogin);
        if (student.isEmpty()) {
            return notFound();
        }
        if (student.get().getGroups().contains(exam.getCourse().getInstructorGroupName()) || authCheckService.isAdmin(student.get())) {
            return forbidden("exam", "cannotRegisterInstructor", "You cannot register instructors or administrators to exams.");
        }
        exam.addRegisteredUser(student.get());
        // NOTE: we intentionally add the user to the course group, because the user only has access to the exam of a course, if the student also
        // has access to the course of the exam.
        // we only need to add the user to the course group, if the student is not yet part of it, otherwise the student cannot access the exam (within the course)
        if (!student.get().getGroups().contains(course.getStudentGroupName())) {
            userService.addUserToGroup(student.get(), course.getStudentGroupName());
        }
        examRepository.save(exam);

        User currentUser = userService.getUserWithGroupsAndAuthorities();
        AuditEvent auditEvent = new AuditEvent(currentUser.getLogin(), Constants.ADD_USER_TO_EXAM, "exam=" + exam.getTitle(), "user=" + studentLogin);
        auditEventRepository.add(auditEvent);
        log.info("User " + currentUser.getLogin() + " has added user " + studentLogin + " to the exam " + exam.getTitle() + " with id " + exam.getId());

        return ResponseEntity.ok().body(null);
    }

    /**
     * POST /courses/:courseId/exams/:examId/generate-student-exams : Generates the student exams randomly based on the exam configuration and the exercise groups
     *
     * @param courseId      the id of the course
     * @param examId        the id of the exam
     * @return the list of student exams with their corresponding users
     */
    @PostMapping(value = "/courses/{courseId}/exams/{examId}/generate-student-exams")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<StudentExam>> generateStudentExams(@PathVariable Long courseId, @PathVariable Long examId) {
        log.info("REST request to generate student exams for exam {}", examId);

        final var exam = examService.findOneWithRegisteredUsersAndExerciseGroupsAndExercises(examId);

        Optional<ResponseEntity<List<StudentExam>>> courseAndExamAccessFailure = examAccessService.checkCourseAndExamAccessForInstructor(courseId, exam);
        if (courseAndExamAccessFailure.isPresent()) {
            return courseAndExamAccessFailure.get();
        }

        // Validate settings of the exam
        examService.validateForStudentExamGeneration(exam);

        List<StudentExam> studentExams = examService.generateStudentExams(exam);

        // we need to break a cycle for the serialization
        for (StudentExam studentExam : studentExams) {
            studentExam.getExam().setRegisteredUsers(null);
            studentExam.getExam().setExerciseGroups(null);
            studentExam.getExam().setStudentExams(null);
        }

        log.info("Generated {} student exams for exam {}", studentExams.size(), examId);
        return ResponseEntity.ok().body(studentExams);
    }

    /**
     * POST /courses/:courseId/exams/:examId/generate-missing-student-exams:
     * Generates exams for students, who don't have an individual exam yet.
     * They are created randomly based on the exam configuration and the exercise groups.
     *
     * @param courseId      the id of the course
     * @param examId        the id of the exam
     * @return the list of student exams with their corresponding users
     */
    @PostMapping(value = "/courses/{courseId}/exams/{examId}/generate-missing-student-exams")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<StudentExam>> generateMissingStudentExams(@PathVariable Long courseId, @PathVariable Long examId) {
        log.info("REST request to generate missing student exams for exam {}", examId);

        final var exam = examService.findOneWithRegisteredUsersAndExerciseGroupsAndExercises(examId);

        Optional<ResponseEntity<List<StudentExam>>> courseAndExamAccessFailure = examAccessService.checkCourseAndExamAccessForInstructor(courseId, examId);
        if (courseAndExamAccessFailure.isPresent()) {
            return courseAndExamAccessFailure.get();
        }

        // Validate settings of the exam
        examService.validateForStudentExamGeneration(exam);

        List<StudentExam> studentExams = examService.generateMissingStudentExams(exam);

        // we need to break a cycle for the serialization
        for (StudentExam studentExam : studentExams) {
            studentExam.getExam().setRegisteredUsers(null);
            studentExam.getExam().setExerciseGroups(null);
            studentExam.getExam().setStudentExams(null);
        }

        log.info("Generated {} missing student exams for exam {}", studentExams.size(), examId);
        return ResponseEntity.ok().body(studentExams);
    }

    /**
     * POST /courses/{courseId}/exams/{examId}/student-exams/start-exercises : Generate the participation objects
     * for all the student exams belonging to the exam
     *
     * @param courseId the course to which the exam belongs to
     * @param examId   the exam to which the student exam belongs to
     * @return ResponsEntity containing the list of generated participations
     */
    @PostMapping(value = "/courses/{courseId}/exams/{examId}/student-exams/start-exercises")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Integer> startExercises(@PathVariable Long courseId, @PathVariable Long examId) {
        long start = System.nanoTime();
        log.info("REST request to start exercises for student exams of exam {}", examId);

        Optional<ResponseEntity<Integer>> courseAndExamAccessFailure = examAccessService.checkCourseAndExamAccessForInstructor(courseId, examId);
        if (courseAndExamAccessFailure.isPresent())
            return courseAndExamAccessFailure.get();

        Integer numberOfGeneratedParticipations = examService.startExercises(examId);

        log.info("Generated {} participations in {} for student exams of exam {}", numberOfGeneratedParticipations, formatDurationFrom(start), examId);

        return ResponseEntity.ok().body(numberOfGeneratedParticipations);
    }

    /**
     * POST /courses/{courseId}/exams/{examId}/student-exams/evaluate-quiz-exercises : Evaluate the quiz exercises of the exam
     *
     * @param courseId the course to which the exam belongs to
     * @param examId   the id of the exam
     * @return ResponseEntity the number of evaluated quiz exercises
     */
    @PostMapping(value = "/courses/{courseId}/exams/{examId}/student-exams/evaluate-quiz-exercises")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Integer> evaluateQuizExercises(@PathVariable Long courseId, @PathVariable Long examId) {
        log.info("REST request to evaluate quiz exercises of exam {}", examId);

        Optional<ResponseEntity<Integer>> courseAndExamAccessFailure = examAccessService.checkCourseAndExamAccessForInstructor(courseId, examId);
        if (courseAndExamAccessFailure.isPresent())
            return courseAndExamAccessFailure.get();

        if (examService.getLatestIndividualExamEndDate(examId).isAfter(ZonedDateTime.now())) {
            // Quizzes should only be evaluated if no exams are running
            return forbidden(applicationName, ENTITY_NAME, "quizevaluationPendingExams",
                    "There are still exams running, quizzes can only be evaluated once all exams are finished.");
        }

        Integer numOfEvaluatedExercises = examService.evaluateQuizExercises(examId);

        log.info("Evaluated {} quiz exercises of exam {}", numOfEvaluatedExercises, examId);

        return ResponseEntity.ok().body(numOfEvaluatedExercises);
    }

    /**
     * POST /courses/{courseId}/exams/{examId}/student-exams/unlock-all-repositories : Unlock all repositories of the exam
     *
     * @param courseId the course to which the exam belongs to
     * @param examId   the id of the exam
     * @return the number of unlocked exercises
     */
    @PostMapping(value = "/courses/{courseId}/exams/{examId}/student-exams/unlock-all-repositories")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Integer> unlockAllRepositories(@PathVariable Long courseId, @PathVariable Long examId) {
        log.info("REST request to unlock all repositories of exam {}", examId);

        Optional<ResponseEntity<Integer>> courseAndExamAccessFailure = examAccessService.checkCourseAndExamAccessForInstructor(courseId, examId);
        if (courseAndExamAccessFailure.isPresent())
            return courseAndExamAccessFailure.get();

        Integer numOfUnlockedExercises = examService.unlockAllRepositories(examId);

        log.info("Unlocked {} programming exercises of exam {}", numOfUnlockedExercises, examId);

        return ResponseEntity.ok().body(numOfUnlockedExercises);
    }

    /**
     * POST /courses/{courseId}/exams/{examId}/student-exams/lock-all-repositories : Lock all repositories of the exam
     *
     * @param courseId the course to which the exam belongs to
     * @param examId   the id of the exam
     * @return the number of locked exercises
     */
    @PostMapping(value = "/courses/{courseId}/exams/{examId}/student-exams/lock-all-repositories")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Integer> lockAllRepositories(@PathVariable Long courseId, @PathVariable Long examId) {
        log.info("REST request to lock all repositories of exam {}", examId);

        Optional<ResponseEntity<Integer>> courseAndExamAccessFailure = examAccessService.checkCourseAndExamAccessForInstructor(courseId, examId);
        if (courseAndExamAccessFailure.isPresent())
            return courseAndExamAccessFailure.get();

        Integer numOfLockedExercises = examService.lockAllRepositories(examId);

        log.info("Locked {} programming exercises of exam {}", numOfLockedExercises, examId);

        return ResponseEntity.ok().body(numOfLockedExercises);
    }

    /**
     * POST /courses/:courseId/exams/:examId/students : Add multiple users to the students of the exam so that they can access the exam
     * The passed list of UserDTOs must include the registration number (the other entries are currently ignored and can be left out)
     * Note: registration based on other user attributes (e.g. email, name, login) is currently NOT supported
     *
     * This method first tries to find the student in the internal Artemis user database (because the user is most probably already using Artemis).
     * In case the user cannot be found, we additionally search the (TUM) LDAP in case it is configured properly.
     *
     * @param courseId      the id of the course
     * @param examId        the id of the exam
     * @param studentDtos   the list of students (with at least registration number) who should get access to the exam
     * @return the list of students who could not be registered for the exam, because they could NOT be found in the Artemis database and could NOT be found in the TUM LDAP
     */
    @PostMapping(value = "/courses/{courseId}/exams/{examId}/students")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<StudentDTO>> addStudentsToExam(@PathVariable Long courseId, @PathVariable Long examId, @RequestBody List<StudentDTO> studentDtos) {
        log.debug("REST request to add {} as students to exam {}", studentDtos, examId);

        Optional<ResponseEntity<List<StudentDTO>>> courseAndExamAccessFailure = examAccessService.checkCourseAndExamAccessForInstructor(courseId, examId);
        if (courseAndExamAccessFailure.isPresent()) {
            return courseAndExamAccessFailure.get();
        }

        List<StudentDTO> notFoundStudentsDtos = examService.registerStudentsForExam(courseId, examId, studentDtos);
        return ResponseEntity.ok().body(notFoundStudentsDtos);
    }

    /**
     * DELETE /courses/:courseId/exams/:examId/students/:studentLogin :
     * Remove one single given user (based on the login) from the students of the exam so that the student cannot access the exam any more.
     * Optionally, also deletes participations and submissions of the student in the student exam.
     *
     * @param courseId     the id of the course
     * @param examId       the id of the exam
     * @param studentLogin the login of the user who should lose student access
     * @param withParticipationsAndSubmission request param deciding whether participations and submissions should also be deleted
     * @return empty ResponseEntity with status 200 (OK) or with status 404 (Not Found)
     */
    @DeleteMapping(value = "/courses/{courseId}/exams/{examId}/students/{studentLogin:" + Constants.LOGIN_REGEX + "}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> removeStudentFromExam(@PathVariable Long courseId, @PathVariable Long examId, @PathVariable String studentLogin,
            @RequestParam(defaultValue = "false") boolean withParticipationsAndSubmission) {
        log.debug("REST request to remove {} as student from exam : {}", studentLogin, examId);

        Optional<ResponseEntity<Void>> courseAndExamAccessFailure = examAccessService.checkCourseAndExamAccessForInstructor(courseId, examId);
        if (courseAndExamAccessFailure.isPresent()) {
            return courseAndExamAccessFailure.get();
        }

        Optional<User> optionalStudent = userService.getUserWithGroupsAndAuthoritiesByLogin(studentLogin);
        if (optionalStudent.isEmpty()) {
            return notFound();
        }
        var exam = examService.findOneWithRegisteredUsers(examId);
        User student = optionalStudent.get();
        exam.removeRegisteredUser(student);

        // Note: we intentionally do not remove the user from the course, because the student might just have "deregistered" from the exam, but should
        // still have access to the course.
        examRepository.save(exam);

        // The student exam might not be generated yet
        Optional<StudentExam> optionalStudentExam = studentExamService.findOneWithExercisesByUserIdAndExamIdOptional(student.getId(), exam.getId());
        if (optionalStudentExam.isPresent()) {
            StudentExam studentExam = optionalStudentExam.get();

            // Optionally delete participations and submissions
            if (withParticipationsAndSubmission) {
                List<StudentParticipation> participations = participationService.findByStudentIdAndIndividualExercisesWithEagerSubmissionsResult(student.getId(),
                        studentExam.getExercises());
                for (var participation : participations) {
                    participationService.delete(participation.getId(), true, true);
                }
            }

            // Delete the student exam
            studentExamService.deleteStudentExam(studentExam.getId());
        }

        User currentUser = userService.getUserWithGroupsAndAuthorities();
        AuditEvent auditEvent = new AuditEvent(currentUser.getLogin(), Constants.REMOVE_USER_FROM_EXAM, "exam=" + exam.getTitle(), "user=" + studentLogin);
        auditEventRepository.add(auditEvent);
        log.info("User " + currentUser.getLogin() + " has removed user " + studentLogin + " from the exam " + exam.getTitle() + " with id " + exam.getId()
                + ". This also deleted a potentially existing student exam with all its participations and submissions.");

        return ResponseEntity.ok().body(null);
    }

    /**
     * GET /courses/{courseId}/exams/{examId}/conduction : Get an exam for conduction.
     *
     * @param courseId  the id of the course
     * @param examId    the id of the exam
     * @return the ResponseEntity with status 200 (OK) and with the found student exam (without exercises) as body
     */
    @GetMapping("/courses/{courseId}/exams/{examId}/conduction")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<StudentExam> getStudentExamForConduction(@PathVariable Long courseId, @PathVariable Long examId) {
        log.debug("REST request to get exam {} for conduction", examId);
        return examAccessService.checkAndGetCourseAndExamAccessForConduction(courseId, examId);
    }

    /**
     * PUT /courses/:courseId/exams/:examId/exerciseGroupsOrder : Update the order of exercise groups. If the received
     * exercise groups do not belong to the exam the operation is aborted.
     *
     * @param courseId              the id of the course
     * @param examId                the id of the exam
     * @param orderedExerciseGroups the exercise groups of the exam in the desired order.
     * @return the list of exercise groups
     */
    @PutMapping("/courses/{courseId}/exams/{examId}/exerciseGroupsOrder")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<List<ExerciseGroup>> updateOrderOfExerciseGroups(@PathVariable Long courseId, @PathVariable Long examId,
            @RequestBody List<ExerciseGroup> orderedExerciseGroups) {
        log.debug("REST request to update the order of exercise groups of exam : {}", examId);

        Optional<ResponseEntity<List<ExerciseGroup>>> courseAndExamAccessFailure = examAccessService.checkCourseAndExamAccessForInstructor(courseId, examId);
        if (courseAndExamAccessFailure.isPresent()) {
            return courseAndExamAccessFailure.get();
        }

        Exam exam = examService.findOneWithExerciseGroups(examId);

        // Ensure that exactly as many exercise groups have been received as are currently related to the exam
        if (orderedExerciseGroups.size() != exam.getExerciseGroups().size()) {
            return forbidden();
        }

        // Ensure that all received exercise groups are already related to the exam
        for (ExerciseGroup exerciseGroup : orderedExerciseGroups) {
            if (!exam.getExerciseGroups().contains(exerciseGroup)) {
                return forbidden();
            }
            // Set the exam manually as it won't be included in orderedExerciseGroups
            exerciseGroup.setExam(exam);
        }

        exam.setExerciseGroups(orderedExerciseGroups);
        examService.save(exam);

        // Return the original request body as it might contain exercise details (e.g. quiz questions), which would be lost otherwise
        return ResponseEntity.ok(orderedExerciseGroups);
    }

    /**
     * GET /courses/{courseId}/exams/{examId}/latest-end-date : Get an exam for conduction.
     *
     * @param courseId the id of the course
     * @param examId   the id of the exam
     * @return the ResponseEntity with status 200 (OK) and with the found exam as body or NotFound if it culd not be
     * determined
     */
    @GetMapping("/courses/{courseId}/exams/{examId}/latest-end-date")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ExamInformationDTO> getLatestIndividualEndDateOfExam(@PathVariable Long courseId, @PathVariable Long examId) {
        log.debug("REST request to get latest individual end date of exam : {}", examId);

        Optional<ResponseEntity<ExamInformationDTO>> courseAndExamAccessFailure = examAccessService.checkCourseAndExamAccessForTeachingAssistant(courseId, examId);

        if (courseAndExamAccessFailure.isPresent()) {
            return courseAndExamAccessFailure.get();
        }

        ZonedDateTime latestIndividualEndDateOfExam = examService.getLatestIndividualExamEndDate(examId);

        if (latestIndividualEndDateOfExam == null) {
            return ResponseEntity.notFound().build();
        }
        else {
            ExamInformationDTO examInformationDTO = new ExamInformationDTO();
            examInformationDTO.latestIndividualEndDate = latestIndividualEndDateOfExam;
            return ResponseEntity.ok().body(examInformationDTO);
        }
    }

}
