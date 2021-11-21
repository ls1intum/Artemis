package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.service.util.TimeLogUtil.formatDurationFrom;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.*;
import static java.time.ZonedDateTime.now;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.domain.participation.TutorParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.dto.StudentDTO;
import de.tum.in.www1.artemis.service.exam.*;
import de.tum.in.www1.artemis.service.messaging.InstanceMessageSendService;
import de.tum.in.www1.artemis.web.rest.dto.*;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
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

    @Value("${artemis.course-archives-path}")
    private String examArchivesDirPath;

    private final UserRepository userRepository;

    private final CourseRepository courseRepository;

    private final ExamService examService;

    private final ExamDateService examDateService;

    private final ExamRegistrationService examRegistrationService;

    private final ExamRepository examRepository;

    private final ExamAccessService examAccessService;

    private final SubmissionService submissionService;

    private final InstanceMessageSendService instanceMessageSendService;

    private final AuthorizationCheckService authCheckService;

    private final TutorParticipationRepository tutorParticipationRepository;

    private final AssessmentDashboardService assessmentDashboardService;

    private final StudentExamRepository studentExamRepository;

    public ExamResource(UserRepository userRepository, CourseRepository courseRepository, ExamService examService, ExamAccessService examAccessService,
            InstanceMessageSendService instanceMessageSendService, ExamRepository examRepository, SubmissionService submissionService, AuthorizationCheckService authCheckService,
            ExamDateService examDateService, TutorParticipationRepository tutorParticipationRepository, AssessmentDashboardService assessmentDashboardService,
            ExamRegistrationService examRegistrationService, StudentExamRepository studentExamRepository) {
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.examService = examService;
        this.submissionService = submissionService;
        this.examDateService = examDateService;
        this.examRegistrationService = examRegistrationService;
        this.examRepository = examRepository;
        this.examAccessService = examAccessService;
        this.instanceMessageSendService = instanceMessageSendService;
        this.authCheckService = authCheckService;
        this.tutorParticipationRepository = tutorParticipationRepository;
        this.assessmentDashboardService = assessmentDashboardService;
        this.studentExamRepository = studentExamRepository;
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
    @PreAuthorize("hasRole('INSTRUCTOR')")
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

        if (exam.getMaxPoints() <= 0) {
            return conflict();
        }

        Optional<ResponseEntity<Exam>> courseAccessFailure = examAccessService.checkCourseAccessForInstructor(courseId);
        if (courseAccessFailure.isPresent()) {
            return courseAccessFailure.get();
        }

        Exam result = examRepository.save(exam);
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
    @PreAuthorize("hasRole('INSTRUCTOR')")
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

        if (updatedExam.getMaxPoints() <= 0) {
            return conflict();
        }

        Optional<ResponseEntity<Exam>> courseAndExamAccessFailure = examAccessService.checkCourseAndExamAccessForInstructor(courseId, updatedExam.getId());
        if (courseAndExamAccessFailure.isPresent()) {
            return courseAndExamAccessFailure.get();
        }

        // Make sure that the original references are preserved.
        Exam originalExam = examRepository.findByIdElseThrow(updatedExam.getId());

        // NOTE: Make sure that all references are preserved here
        updatedExam.setExerciseGroups(originalExam.getExerciseGroups());
        updatedExam.setStudentExams(originalExam.getStudentExams());
        updatedExam.setRegisteredUsers(originalExam.getRegisteredUsers());

        Exam result = examRepository.save(updatedExam);

        // We can't test dates for equality as the dates retrieved from the database lose precision. Also use instant to take timezones into account
        Comparator<ZonedDateTime> comparator = Comparator.comparing(date -> date.truncatedTo(ChronoUnit.SECONDS).toInstant());
        if (comparator.compare(originalExam.getVisibleDate(), updatedExam.getVisibleDate()) != 0
                || comparator.compare(originalExam.getStartDate(), updatedExam.getStartDate()) != 0) {
            // get all exercises
            Exam examWithExercises = examService.findByIdWithExerciseGroupsAndExercisesElseThrow(result.getId());
            // for all programming exercises in the exam, send their ids for scheduling
            examWithExercises.getExerciseGroups().stream().flatMap(group -> group.getExercises().stream()).filter(exercise -> exercise instanceof ProgrammingExercise)
                    .map(Exercise::getId).forEach(instanceMessageSendService::sendProgrammingExerciseSchedule);
        }

        if (comparator.compare(originalExam.getEndDate(), updatedExam.getEndDate()) != 0) {
            // get all exercises
            Exam examWithExercises = examService.findByIdWithExerciseGroupsAndExercisesElseThrow(result.getId());
            examService.scheduleModelingExercises(examWithExercises);
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
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<Exam> getExam(@PathVariable Long courseId, @PathVariable Long examId, @RequestParam(defaultValue = "false") boolean withStudents,
            @RequestParam(defaultValue = "false") boolean withExerciseGroups) {
        log.debug("REST request to get exam : {}", examId);
        Optional<ResponseEntity<Exam>> courseAndExamAccessFailure;
        if (withStudents) {
            courseAndExamAccessFailure = examAccessService.checkCourseAndExamAccessForInstructor(courseId, examId);
        }
        else {
            courseAndExamAccessFailure = examAccessService.checkCourseAndExamAccessForEditor(courseId, examId);
        }

        if (courseAndExamAccessFailure.isPresent()) {
            return courseAndExamAccessFailure.get();
        }
        if (!withStudents && !withExerciseGroups) {
            return ResponseEntity.ok(examRepository.findByIdElseThrow(examId));
        }
        if (withExerciseGroups) {
            Exam exam;
            if (withStudents) {
                exam = examRepository.findByIdWithRegisteredUsersExerciseGroupsAndExercisesElseThrow(examId);
            }
            else {
                exam = examService.findByIdWithExerciseGroupsAndExercisesElseThrow(examId);
            }
            examService.setExamProperties(exam);
            return ResponseEntity.ok(exam);
        }
        Exam exam = examRepository.findByIdWithRegisteredUsersElseThrow(examId);
        exam.getRegisteredUsers().forEach(user -> user.setVisibleRegistrationNumber(user.getRegistrationNumber()));
        return ResponseEntity.ok(exam);
    }

    /**
     * GET /exams/{examId}/title : Returns the title of the exam with the given id
     *
     * @param examId the id of the exam
     * @return the title of the exam wrapped in an ResponseEntity or 404 Not Found if no exam with that id exists
     */
    @GetMapping(value = "/exams/{examId}/title")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<String> getExamTitle(@PathVariable Long examId) {
        final var title = examRepository.getExamTitle(examId);
        return title == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(title);
    }

    /**
     * GET /courses/{courseId}/exams/{examId} : Find an exam by id.
     *
     * @param courseId              the course to which the exam belongs
     * @param examId                the exam to find
     * @return the ResponseEntity with status 200 (OK) and with the found exam as body
     */
    @GetMapping("/courses/{courseId}/exams/{examId}/statistics")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<ExamChecklistDTO> getExamStatistics(@PathVariable Long courseId, @PathVariable Long examId) {
        log.debug("REST request to get exam statistics: {}", examId);
        Optional<ResponseEntity<ExamChecklistDTO>> courseAndExamAccessFailure = examAccessService.checkCourseAndExamAccessForInstructor(courseId, examId);
        if (courseAndExamAccessFailure.isPresent()) {
            return courseAndExamAccessFailure.get();
        }
        Exam exam = examRepository.findByIdWithRegisteredUsersExerciseGroupsAndExercisesElseThrow(examId);
        ExamChecklistDTO examChecklistDTO = examService.getStatsForChecklist(exam);

        return ResponseEntity.ok(examChecklistDTO);
    }

    /**
     * GET /courses/{courseId}/exams/{examId}/scores : Find scores for an exam by id.
     *
     * @param courseId              the course to which the exam belongs
     * @param examId                the exam to find
     * @return the ResponseEntity with status 200 (OK) and with the found ExamScoreDTO as body
     */
    @GetMapping("/courses/{courseId}/exams/{examId}/scores")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<ExamScoresDTO> getExamScore(@PathVariable Long courseId, @PathVariable Long examId) {
        long start = System.currentTimeMillis();
        log.debug("REST request to get score for exam : {}", examId);
        Optional<ResponseEntity<ExamScoresDTO>> courseAndExamAccessFailure = examAccessService.checkCourseAndExamAccessForInstructor(courseId, examId);
        if (courseAndExamAccessFailure.isPresent()) {
            return courseAndExamAccessFailure.get();
        }
        ExamScoresDTO examScoresDTO = examService.calculateExamScores(examId);
        log.info("get scores for exam {} took {}ms", examId, System.currentTimeMillis() - start);
        return ResponseEntity.ok(examScoresDTO);
    }

    /**
     * GET /courses/:courseId/exams/:examId/exam-for-assessment-dashboard
     *
     * @param courseId the id of the course to retrieve
     * @param examId the id of the exam that contains the exercises
     * @return data about a course including all exercises, plus some data for the tutor as tutor status for assessment
     */
    @GetMapping("/courses/{courseId}/exams/{examId}/exam-for-assessment-dashboard")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<Exam> getExamForAssessmentDashboard(@PathVariable long courseId, @PathVariable long examId) {
        log.debug("REST request /courses/{courseId}/exams/{examId}/exam-for-assessment-dashboard");

        Exam exam = examService.findByIdWithExerciseGroupsAndExercisesElseThrow(examId);
        Course course = exam.getCourse();
        if (!course.getId().equals(courseId)) {
            return conflict();
        }

        User user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, user);

        if (ZonedDateTime.now().isBefore(exam.getEndDate()) && authCheckService.isTeachingAssistantInCourse(course, user)) {
            // tutors cannot access the exercises before the exam ends
            return forbidden();
        }

        Set<Exercise> exercises = new HashSet<>();
        // extract all exercises for all the exam
        for (ExerciseGroup exerciseGroup : exam.getExerciseGroups()) {
            exerciseGroup.setExercises(courseRepository.getInterestingExercisesForAssessmentDashboards(exerciseGroup.getExercises()));
            exercises.addAll(exerciseGroup.getExercises());
        }
        List<TutorParticipation> tutorParticipations = tutorParticipationRepository.findAllByAssessedExercise_ExerciseGroup_Exam_IdAndTutor_Id(examId, user.getId());

        assessmentDashboardService.generateStatisticsForExercisesForAssessmentDashboard(exercises, tutorParticipations, true);

        return ResponseEntity.ok(exam);
    }

    /**
     * GET /courses/:courseId/exams/:examId/exam-for-test-run-assessment-dashboard
     *
     * @param courseId the id of the course to retrieve
     * @param examId the id of the exam that contains the exercises
     * @return data about a exam test run including all exercises, plus some data for the tutor as tutor status for assessment
     */
    @GetMapping("/courses/{courseId}/exams/{examId}/exam-for-test-run-assessment-dashboard")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Exam> getExamForTestRunAssessmentDashboard(@PathVariable long courseId, @PathVariable long examId) {
        log.debug("REST request /courses/{courseId}/exams/{examId}/exam-for-test-run-assessment-dashboard");

        Exam exam = examService.findByIdWithExerciseGroupsAndExercisesElseThrow(examId);
        Course course = exam.getCourse();
        if (!course.getId().equals(courseId)) {
            return conflict();
        }

        User user = userRepository.getUserWithGroupsAndAuthorities();

        if (!authCheckService.isAtLeastInstructorInCourse(course, user)) {
            return forbidden();
        }

        for (ExerciseGroup exerciseGroup : exam.getExerciseGroups()) {
            exerciseGroup.setExercises(courseRepository.getInterestingExercisesForAssessmentDashboards(exerciseGroup.getExercises()));
        }

        return ResponseEntity.ok(exam);
    }

    /**
     * GET /courses/:courseId/exams/:examId/stats-for-exam-assessment-dashboard A collection of useful statistics for the tutor course dashboard,
     * including: - number of submissions to the course - number of assessments - number of assessments assessed by the tutor - number of complaints
     *
     * @param courseId - the id of the course
     * @param examId   - the id of the exam to retrieve stats from
     * @return data about a course including all exercises, plus some data for the tutor as tutor status for assessment
     */
    @GetMapping("/courses/{courseId}/exams/{examId}/stats-for-exam-assessment-dashboard")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<StatsForDashboardDTO> getStatsForExamAssessmentDashboard(@PathVariable Long courseId, @PathVariable Long examId) {
        log.debug("REST request /courses/{courseId}/stats-for-exam-assessment-dashboard");

        Course course = courseRepository.findByIdElseThrow(courseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isAtLeastTeachingAssistantInCourse(course, user)) {
            return forbidden();
        }
        return ResponseEntity.ok(examService.getStatsForExamAssessmentDashboard(course, examId));
    }

    /**
     * GET /courses/{courseId}/exams : Find all exams for the given course.
     *
     * @param courseId the course to which the exam belongs
     * @return the ResponseEntity with status 200 (OK) and a list of exams. The list can be empty
     */
    @GetMapping("/courses/{courseId}/exams")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<List<Exam>> getExamsForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all exams for Course : {}", courseId);
        Optional<ResponseEntity<List<Exam>>> courseAccessFailure = examAccessService.checkCourseAccessForTeachingAssistant(courseId);
        return courseAccessFailure.orElseGet(() -> {
            List<Exam> exams = examRepository.findByCourseId(courseId);
            examRepository.setNumberOfRegisteredUsersForExams(exams);
            return ResponseEntity.ok(exams);
        });
    }

    /**
     * GET /courses/{courseId}/exams-for-user : Find all exams the user is allowed to access (Is at least Instructor)
     *
     * @param courseId the course to which the exam belongs
     * @return the ResponseEntity with status 200 (OK) and a list of exams. The list can be empty
     */
    @GetMapping("/courses/{courseId}/exams-for-user")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<List<Exam>> getExamsForUser(@PathVariable Long courseId) {
        User user = userRepository.getUserWithGroupsAndAuthorities();
        if (authCheckService.isAdmin(user)) {
            return ResponseEntity.ok(examRepository.findAllWithQuizExercisesWithEagerExerciseGroupsAndExercises());
        }
        else {
            Course course = courseRepository.findByIdElseThrow(courseId);
            if (!authCheckService.isAtLeastInstructorInCourse(course, user)) {
                return forbidden();
            }
            var userGroups = new ArrayList<>(user.getGroups());
            return ResponseEntity.ok(examRepository.getExamsWithQuizExercisesForWhichUserHasInstructorAccess(userGroups));
        }
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

        List<Exam> upcomingExams = examRepository.findAllCurrentAndUpcomingExams();
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
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> deleteExam(@PathVariable Long courseId, @PathVariable Long examId) {
        log.info("REST request to delete exam : {}", examId);
        var exam = examRepository.findByIdElseThrow(examId);
        Optional<ResponseEntity<Void>> courseAndExamAccessFailure = examAccessService.checkCourseAndExamAccessForInstructor(courseId, examId);
        if (courseAndExamAccessFailure.isPresent()) {
            return courseAndExamAccessFailure.get();
        }

        examService.delete(examId);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, exam.getTitle())).build();
    }

    /**
     * DELETE /courses/{courseId}/exams/{examId}/reset : Reset the exam with the given id.
     * The reset operation deletes all studentExams, participations, submissions and feedback.
     *
     * @param courseId  the course to which the exam belongs
     * @param examId    the id of the exam to reset
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/courses/{courseId}/exams/{examId}/reset")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Exam> resetExam(@PathVariable Long courseId, @PathVariable Long examId) {
        log.info("REST request to reset exam : {}", examId);
        var exam = examRepository.findByIdElseThrow(examId);
        Optional<ResponseEntity<Exam>> courseAndExamAccessFailure = examAccessService.checkCourseAndExamAccessForInstructor(courseId, examId);
        if (courseAndExamAccessFailure.isPresent()) {
            return courseAndExamAccessFailure.get();
        }

        examService.reset(exam.getId());
        Exam returnExam = examService.findByIdWithExerciseGroupsAndExercisesElseThrow(examId);
        examService.setExamProperties(returnExam);
        return ResponseEntity.ok(returnExam);
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
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<StudentDTO> addStudentToExam(@PathVariable Long courseId, @PathVariable Long examId, @PathVariable String studentLogin) {
        log.debug("REST request to add {} as student to exam : {}", studentLogin, examId);

        Optional<ResponseEntity<StudentDTO>> courseAndExamAccessFailure = examAccessService.checkCourseAndExamAccessForInstructor(courseId, examId);
        if (courseAndExamAccessFailure.isPresent()) {
            return courseAndExamAccessFailure.get();
        }

        var course = courseRepository.findByIdElseThrow(courseId);
        var exam = examRepository.findByIdWithRegisteredUsersElseThrow(examId);

        var student = userRepository.findOneWithGroupsAndAuthoritiesByLogin(studentLogin)
                .orElseThrow(() -> new EntityNotFoundException("User with login: \"" + studentLogin + "\" does not exist"));

        if (student.getGroups().contains(exam.getCourse().getInstructorGroupName()) || authCheckService.isAdmin(student)) {
            return forbidden("exam", "cannotRegisterInstructor", "You cannot register instructors or administrators to exams.");
        }

        examRegistrationService.registerStudentToExam(course, exam, student);
        var studentDto = new StudentDTO();
        studentDto.setRegistrationNumber(student.getRegistrationNumber());
        studentDto.setFirstName(student.getFirstName());
        studentDto.setLastName(student.getLastName());
        studentDto.setLogin(student.getLogin());
        return ResponseEntity.ok().body(studentDto);
    }

    /**
     * POST /courses/:courseId/exams/:examId/generate-student-exams : Generates the student exams randomly based on the exam configuration and the exercise groups
     *
     * @param courseId      the id of the course
     * @param examId        the id of the exam
     * @return the list of student exams with their corresponding users
     */
    @PostMapping(value = "/courses/{courseId}/exams/{examId}/generate-student-exams")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<List<StudentExam>> generateStudentExams(@PathVariable Long courseId, @PathVariable Long examId) {
        long start = System.nanoTime();
        log.info("REST request to generate student exams for exam {}", examId);
        final Exam exam = examRepository.findByIdWithRegisteredUsersExerciseGroupsAndExercisesElseThrow(examId);

        Optional<ResponseEntity<List<StudentExam>>> courseAndExamAccessFailure = examAccessService.checkCourseAndExamAccessForInstructor(courseId, exam);
        if (courseAndExamAccessFailure.isPresent()) {
            return courseAndExamAccessFailure.get();
        }

        // Validate settings of the exam
        examService.validateForStudentExamGeneration(exam);

        examService.combineTemplateCommitsOfAllProgrammingExercisesInExam(exam);

        List<StudentExam> studentExams = studentExamRepository.generateStudentExams(exam);

        // we need to break a cycle for the serialization
        for (StudentExam studentExam : studentExams) {
            studentExam.getExam().setRegisteredUsers(null);
            studentExam.getExam().setExerciseGroups(null);
            studentExam.getExam().setStudentExams(null);
        }
        log.info("Generated {} student exams in {} for exam {}", studentExams.size(), formatDurationFrom(start), examId);
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
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<List<StudentExam>> generateMissingStudentExams(@PathVariable Long courseId, @PathVariable Long examId) {
        log.info("REST request to generate missing student exams for exam {}", examId);

        final Exam exam = examRepository.findByIdWithRegisteredUsersExerciseGroupsAndExercisesElseThrow(examId);

        Optional<ResponseEntity<List<StudentExam>>> courseAndExamAccessFailure = examAccessService.checkCourseAndExamAccessForInstructor(courseId, examId);
        if (courseAndExamAccessFailure.isPresent()) {
            return courseAndExamAccessFailure.get();
        }

        // Validate settings of the exam
        examService.validateForStudentExamGeneration(exam);

        List<StudentExam> studentExams = studentExamRepository.generateMissingStudentExams(exam);

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
     * POST /courses/{courseId}/exams/{examId}/student-exams/evaluate-quiz-exercises : Evaluate the quiz exercises of the exam
     *
     * @param courseId the course to which the exam belongs to
     * @param examId   the id of the exam
     * @return ResponseEntity the number of evaluated quiz exercises
     */
    @PostMapping(value = "/courses/{courseId}/exams/{examId}/student-exams/evaluate-quiz-exercises")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Integer> evaluateQuizExercises(@PathVariable Long courseId, @PathVariable Long examId) {
        log.info("REST request to evaluate quiz exercises of exam {}", examId);

        Optional<ResponseEntity<Integer>> courseAndExamAccessFailure = examAccessService.checkCourseAndExamAccessForInstructor(courseId, examId);
        if (courseAndExamAccessFailure.isPresent())
            return courseAndExamAccessFailure.get();

        if (examDateService.getLatestIndividualExamEndDate(examId).isAfter(ZonedDateTime.now())) {
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
    @PreAuthorize("hasRole('INSTRUCTOR')")
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
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Integer> lockAllRepositories(@PathVariable Long courseId, @PathVariable Long examId) {
        log.info("REST request to lock all repositories of exam {}", examId);

        Optional<ResponseEntity<Integer>> courseAndExamAccessFailure = examAccessService.checkCourseAndExamAccessForInstructor(courseId, examId);
        if (courseAndExamAccessFailure.isPresent()) {
            return courseAndExamAccessFailure.get();
        }
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
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<List<StudentDTO>> addStudentsToExam(@PathVariable Long courseId, @PathVariable Long examId, @RequestBody List<StudentDTO> studentDtos) {
        log.debug("REST request to add {} as students to exam {}", studentDtos, examId);

        Optional<ResponseEntity<List<StudentDTO>>> courseAndExamAccessFailure = examAccessService.checkCourseAndExamAccessForInstructor(courseId, examId);
        if (courseAndExamAccessFailure.isPresent()) {
            return courseAndExamAccessFailure.get();
        }
        List<StudentDTO> notFoundStudentsDtos = examRegistrationService.registerStudentsForExam(courseId, examId, studentDtos);
        return ResponseEntity.ok().body(notFoundStudentsDtos);
    }

    /**
     * POST /courses/:courseId/exams/:examId/register-course-students : Add all users which are enrolled in the course to the exam so that the student can access the exam
     *
     * @param courseId     the id of the course
     * @param examId       the id of the exam
     * @return empty ResponseEntity with status 200 (OK) or with status 404 (Not Found)
    */
    @PostMapping(value = "/courses/{courseId}/exams/{examId}/register-course-students")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> registerCourseStudents(@PathVariable Long courseId, @PathVariable Long examId) {
        // get all students enrolled in the course
        log.debug("REST request to add all students to exam {} with courseId {}", examId, courseId);

        Optional<ResponseEntity<Void>> courseAndExamAccessFailure = examAccessService.checkCourseAndExamAccessForInstructor(courseId, examId);
        if (courseAndExamAccessFailure.isPresent())
            return courseAndExamAccessFailure.get();

        examRegistrationService.addAllStudentsOfCourseToExam(courseId, examId);
        return ResponseEntity.ok().body(null);
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
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> removeStudentFromExam(@PathVariable Long courseId, @PathVariable Long examId, @PathVariable String studentLogin,
            @RequestParam(defaultValue = "false") boolean withParticipationsAndSubmission) {
        log.debug("REST request to remove {} as student from exam : {}", studentLogin, examId);

        Optional<ResponseEntity<Void>> courseAndExamAccessFailure = examAccessService.checkCourseAndExamAccessForInstructor(courseId, examId);
        if (courseAndExamAccessFailure.isPresent()) {
            return courseAndExamAccessFailure.get();
        }

        Optional<User> optionalStudent = userRepository.findOneWithGroupsAndAuthoritiesByLogin(studentLogin);
        if (optionalStudent.isEmpty()) {
            return notFound();
        }

        examRegistrationService.unregisterStudentFromExam(examId, withParticipationsAndSubmission, optionalStudent.get());
        return ResponseEntity.ok().body(null);
    }

    /**
     * DELETE /courses/{courseId}/exams/{examId}/allstudents :
     * Remove all students of the exam so that they cannot access the exam any more.
     * Optionally, also deletes participations and submissions of all students in their student exams.
     *
     * @param courseId     the id of the course
     * @param examId       the id of the exam
     * @param withParticipationsAndSubmission request param deciding whether participations and submissions should also be deleted
     *
     * @return empty ResponseEntity with status 200 (OK) or with status 404 (Not Found)
     */
    @DeleteMapping(value = "/courses/{courseId}/exams/{examId}/students")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> removeAllStudentsFromExam(@PathVariable Long courseId, @PathVariable Long examId,
            @RequestParam(defaultValue = "false") boolean withParticipationsAndSubmission) {
        log.debug("REST request to remove all students from exam {} with courseId {}", examId, courseId);

        Optional<ResponseEntity<Void>> courseAndExamAccessFailure = examAccessService.checkCourseAndExamAccessForInstructor(courseId, examId);
        if (courseAndExamAccessFailure.isPresent()) {
            return courseAndExamAccessFailure.get();
        }

        examRegistrationService.unregisterAllStudentFromExam(examId, withParticipationsAndSubmission);
        return ResponseEntity.ok().body(null);
    }

    /**
     * GET /courses/{courseId}/exams/{examId}/start : Get an exam for the exam start.
     *
     * @param courseId  the id of the course
     * @param examId    the id of the exam
     * @return the ResponseEntity with status 200 (OK) and with the found student exam (without exercises) as body
     */
    @GetMapping("/courses/{courseId}/exams/{examId}/start")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<StudentExam> getStudentExamForStart(@PathVariable Long courseId, @PathVariable Long examId) {
        log.debug("REST request to get exam {} for conduction", examId);
        return examAccessService.checkAndGetCourseAndExamAccessForConduction(courseId, examId);
    }

    /**
     * PUT /courses/:courseId/exams/:examId/exercise-groups-order : Update the order of exercise groups. If the received
     * exercise groups do not belong to the exam the operation is aborted.
     *
     * @param courseId              the id of the course
     * @param examId                the id of the exam
     * @param orderedExerciseGroups the exercise groups of the exam in the desired order.
     * @return the list of exercise groups
     */
    @PutMapping("/courses/{courseId}/exams/{examId}/exercise-groups-order")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<List<ExerciseGroup>> updateOrderOfExerciseGroups(@PathVariable Long courseId, @PathVariable Long examId,
            @RequestBody List<ExerciseGroup> orderedExerciseGroups) {
        log.debug("REST request to update the order of exercise groups of exam : {}", examId);

        Optional<ResponseEntity<List<ExerciseGroup>>> courseAndExamAccessFailure = examAccessService.checkCourseAndExamAccessForEditor(courseId, examId);
        if (courseAndExamAccessFailure.isPresent()) {
            return courseAndExamAccessFailure.get();
        }

        Exam exam = examRepository.findByIdWithExerciseGroupsElseThrow(examId);

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
        examRepository.save(exam);

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
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<ExamInformationDTO> getLatestIndividualEndDateOfExam(@PathVariable Long courseId, @PathVariable Long examId) {
        log.debug("REST request to get latest individual end date of exam : {}", examId);
        Optional<ResponseEntity<ExamInformationDTO>> courseAndExamAccessFailure = examAccessService.checkCourseAndExamAccessForTeachingAssistant(courseId, examId);
        var examInformation = new ExamInformationDTO(examDateService.getLatestIndividualExamEndDate(examId));
        return courseAndExamAccessFailure.orElseGet(() -> ResponseEntity.ok().body(examInformation));
    }

    /**
     * GET /courses/:courseId/exams/:examId/lockedSubmissions Get locked submissions for exam for user
     *
     * @param courseId  - the id of the course
     * @param examId    - the id of the exam
     * @return the ResponseEntity with status 200 (OK) and with body the course, or with status 404 (Not Found)
     */
    @GetMapping("/courses/{courseId}/exams/{examId}/lockedSubmissions")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<List<Submission>> getLockedSubmissionsForExam(@PathVariable Long courseId, @PathVariable Long examId) {
        log.debug("REST request to get all locked submissions for course : {}", courseId);
        long start = System.currentTimeMillis();
        Course course = courseRepository.findWithEagerExercisesById(courseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, user);

        List<Submission> submissions = submissionService.getLockedSubmissions(examId, user);

        long end = System.currentTimeMillis();
        log.debug("Finished /courses/{}/submissions call in {}ms", courseId, end - start);
        return ResponseEntity.ok(submissions);
    }

    /**
     * PUT /courses/{courseId}/exams/{examId}/archive : archive an existing exam asynchronously.
     *
     * This method starts the process of archiving all exam exercises and submissions.
     * It immediately returns and runs this task asynchronously. When the task is done, the exam is marked as archived, which means the zip file can be downloaded.
     *
     * @param courseId the id of the course
     * @param examId the id of the exam to archive
     * @return empty
     */
    @PutMapping("/courses/{courseId}/exams/{examId}/archive")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> archiveExam(@PathVariable Long courseId, @PathVariable Long examId) {
        log.info("REST request to archive exam : {}", examId);

        final Exam exam = examRepository.findOneWithEagerExercisesGroupsAndStudentExams(examId);
        if (exam == null) {
            return notFound();
        }

        Optional<ResponseEntity<Exam>> courseAndExamAccessFailure = examAccessService.checkCourseAndExamAccessForInstructor(courseId, examId);
        if (courseAndExamAccessFailure.isPresent()) {
            return forbidden();
        }

        // Archiving an exam is only possible after the exam is over
        if (now().isBefore(exam.getEndDate())) {
            throw new BadRequestAlertException("You cannot archive an exam that is not over.", ENTITY_NAME, "examNotOver", true);
        }

        examService.archiveExam(exam);
        return ResponseEntity.ok().build();
    }

    /**
     * Downloads the zip file of the archived exam if it exists. Throws a 404 if the exam doesn't exist.
     *
     * @param courseId The course id of the course
     * @param examId The id of the archived exam
     * @return ResponseEntity with status
     */
    @GetMapping("/courses/{courseId}/exams/{examId}/download-archive")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Resource> downloadExamArchive(@PathVariable Long courseId, @PathVariable Long examId) throws FileNotFoundException, EntityNotFoundException {
        log.info("REST request to download archive of exam : {}", examId);
        final Exam exam = examRepository.findByIdElseThrow(examId);

        Optional<ResponseEntity<Exam>> courseAndExamAccessFailure = examAccessService.checkCourseAndExamAccessForInstructor(courseId, examId);
        if (courseAndExamAccessFailure.isPresent()) {
            return forbidden();
        }

        if (!exam.hasExamArchive()) {
            return notFound();
        }

        // The path is stored in the exam table
        Path archive = Path.of(examArchivesDirPath, exam.getExamArchivePath());

        File zipFile = archive.toFile();
        InputStreamResource resource = new InputStreamResource(new FileInputStream(zipFile));
        return ResponseEntity.ok().contentLength(zipFile.length()).contentType(MediaType.APPLICATION_OCTET_STREAM).header("filename", zipFile.getName()).body(resource);
    }

}
