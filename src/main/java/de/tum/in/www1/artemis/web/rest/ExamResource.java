package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.service.util.TimeLogUtil.formatDurationFrom;
import static java.time.ZonedDateTime.now;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.audit.AuditEvent;
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
import de.tum.in.www1.artemis.service.AssessmentDashboardService;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.SubmissionService;
import de.tum.in.www1.artemis.service.dto.StudentDTO;
import de.tum.in.www1.artemis.service.exam.*;
import de.tum.in.www1.artemis.service.messaging.InstanceMessageSendService;
import de.tum.in.www1.artemis.service.scheduled.cache.monitoring.ExamMonitoringScheduleService;
import de.tum.in.www1.artemis.web.rest.dto.*;
import de.tum.in.www1.artemis.web.rest.errors.*;
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

    private final ExamImportService examImportService;

    private final ExamMonitoringScheduleService examMonitoringScheduleService;

    private final CustomAuditEventRepository auditEventRepository;

    public ExamResource(UserRepository userRepository, CourseRepository courseRepository, ExamService examService, ExamAccessService examAccessService,
            InstanceMessageSendService instanceMessageSendService, ExamRepository examRepository, SubmissionService submissionService, AuthorizationCheckService authCheckService,
            ExamDateService examDateService, TutorParticipationRepository tutorParticipationRepository, AssessmentDashboardService assessmentDashboardService,
            ExamRegistrationService examRegistrationService, StudentExamRepository studentExamRepository, ExamImportService examImportService,
            ExamMonitoringScheduleService examMonitoringScheduleService, CustomAuditEventRepository auditEventRepository) {
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
        this.examImportService = examImportService;
        this.examMonitoringScheduleService = examMonitoringScheduleService;
        this.auditEventRepository = auditEventRepository;
    }

    /**
     * POST /courses/{courseId}/exams : Create a new exam.
     *
     * @param courseId the course to which the exam belongs
     * @param exam     the exam to create
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

        checkForExamConflictsElseThrow(courseId, exam);

        // Check that exerciseGroups are not set to prevent manipulation of associated exerciseGroups
        if (!exam.getExerciseGroups().isEmpty()) {
            throw new ConflictException("A new exam cannot have exercise groups yet", ENTITY_NAME, "groupsExist");
        }

        examAccessService.checkCourseAccessForInstructorElseThrow(courseId);

        Exam result = examRepository.save(exam);

        if (result.isMonitoring()) {
            instanceMessageSendService.sendExamMonitoringSchedule(result.getId());
        }

        return ResponseEntity.created(new URI("/api/courses/" + courseId + "/exams/" + result.getId())).body(result);
    }

    /**
     * PUT /courses/{courseId}/exams : Updates an existing exam.
     * This route does not save changes to the exercise groups. This should be done via the ExerciseGroupResource.
     *
     * @param courseId    the course to which the exam belongs
     * @param updatedExam the exam to update
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

        checkForExamConflictsElseThrow(courseId, updatedExam);

        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, updatedExam.getId());

        // Make sure that the original references are preserved.
        Exam originalExam = examRepository.findByIdElseThrow(updatedExam.getId());

        // The Exam Mode cannot be changed after creation -> Compare request with version in the database
        if (updatedExam.isTestExam() != originalExam.isTestExam()) {
            throw new ConflictException("The Exam Mode cannot be changed after creation", ENTITY_NAME, "examModeMismatch");
        }

        // NOTE: Make sure that all references are preserved here
        updatedExam.setExerciseGroups(originalExam.getExerciseGroups());
        updatedExam.setStudentExams(originalExam.getStudentExams());
        updatedExam.setRegisteredUsers(originalExam.getRegisteredUsers());

        Exam result = examRepository.save(updatedExam);

        if (updatedExam.isMonitoring()) {
            instanceMessageSendService.sendExamMonitoringSchedule(result.getId());
        }
        else {
            instanceMessageSendService.sendExamMonitoringScheduleCancel(result.getId());
        }
        examMonitoringScheduleService.notifyMonitoringUpdate(result.getId(), updatedExam.isMonitoring());

        // We can't test dates for equality as the dates retrieved from the database lose precision. Also use instant to take timezones into account
        Comparator<ZonedDateTime> comparator = Comparator.comparing(date -> date.truncatedTo(ChronoUnit.SECONDS).toInstant());
        if (comparator.compare(originalExam.getVisibleDate(), updatedExam.getVisibleDate()) != 0
                || comparator.compare(originalExam.getStartDate(), updatedExam.getStartDate()) != 0) {
            // get all exercises
            Exam examWithExercises = examService.findByIdWithExerciseGroupsAndExercisesElseThrow(result.getId());
            // for all programming exercises in the exam, send their ids for scheduling
            examWithExercises.getExerciseGroups().stream().flatMap(group -> group.getExercises().stream()).filter(ProgrammingExercise.class::isInstance).map(Exercise::getId)
                    .forEach(instanceMessageSendService::sendProgrammingExerciseSchedule);
        }

        if (comparator.compare(originalExam.getEndDate(), updatedExam.getEndDate()) != 0) {
            // get all exercises
            Exam examWithExercises = examService.findByIdWithExerciseGroupsAndExercisesElseThrow(result.getId());
            examService.scheduleModelingExercises(examWithExercises);
        }

        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, result.getTitle())).body(result);
    }

    /**
     * POST /courses/{courseId}/exam-import : Imports a new exam with exercises.
     *
     * @param courseId         the course to which the exam belongs
     * @param examToBeImported the exam to import / create
     * @return the ResponseEntity with status 201 (Created) and with body the newly imported exam, or with status 400 (Bad Request) if the exam has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/courses/{courseId}/exam-import")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Exam> importExamWithExercises(@PathVariable Long courseId, @RequestBody Exam examToBeImported) throws URISyntaxException {
        log.debug("REST request to import an exam : {}", examToBeImported);

        // Step 1: Check if Exam has an ID
        if (examToBeImported.getId() != null) {
            throw new BadRequestAlertException("A imported exam cannot already have an ID", ENTITY_NAME, "idexists");
        }

        examAccessService.checkCourseAccessForInstructorElseThrow(courseId);

        // Step 3: Validate the Exam dates
        checkForExamConflictsElseThrow(courseId, examToBeImported);

        // Step 4: Import Exam with Exercises
        Exam examCopied = examImportService.importExamWithExercises(examToBeImported, courseId);

        // Step 5: Set Exam Monitoring
        if (examCopied.isMonitoring()) {
            instanceMessageSendService.sendExamMonitoringSchedule(examCopied.getId());
        }

        return ResponseEntity.created(new URI("/api/courses/" + courseId + "/exams/" + examCopied.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, examCopied.getTitle())).body(examCopied);
    }

    /**
     * Checks if the input values are set correctly. More details in the corresponding methods
     *
     * @param courseId the exam should belong to.
     * @param exam     which should be checked.
     */
    private void checkForExamConflictsElseThrow(Long courseId, Exam exam) {

        checkExamCourseIdElseThrow(courseId, exam);

        checkExamForDatesConflictsElseThrow(exam);

        checkExamForWorkingTimeConflictsElseThrow(exam);

        checkExamPointsAndCorrectionRoundsElseThrow(exam);
    }

    /**
     * Checks that the correct course is present and set
     *
     * @param courseId the course to which the exam should be linked
     * @param exam     the exam to be checked
     */
    private void checkExamCourseIdElseThrow(Long courseId, Exam exam) {
        if (exam.getCourse() == null) {
            throw new BadRequestAlertException("An exam has to belong to a course.", ENTITY_NAME, "noCourse");
        }

        if (!exam.getCourse().getId().equals(courseId)) {
            throw new BadRequestAlertException("The course id does not match the id of the course connected to the exam.", ENTITY_NAME, "wrongCourseId");
        }
    }

    /**
     * Checks that the visible/start/end-dates are present and in the correct order.
     * For real exams: visibleDate < startDate < endDate
     * For test exams: visibleDate <= startDate < endDate
     *
     * @param exam the exam to be checked
     */
    private void checkExamForDatesConflictsElseThrow(Exam exam) {
        if (exam.getVisibleDate() == null || exam.getStartDate() == null || exam.getEndDate() == null) {
            throw new BadRequestAlertException("An exam has to have times when it becomes visible, starts, and ends as well as a working time.", ENTITY_NAME, "examTimes");
        }

        if (exam.isTestExam()) {
            if (!(exam.getVisibleDate().isBefore(exam.getStartDate()) || exam.getVisibleDate().isEqual(exam.getStartDate())) || !exam.getStartDate().isBefore(exam.getEndDate())) {
                throw new BadRequestAlertException("For TestExams, the visible Date has to be before or equal to the start Date and the start Date has to be before the End Date",
                        ENTITY_NAME, "examTimes");
            }
        }
        else if (!exam.getVisibleDate().isBefore(exam.getStartDate()) || !exam.getStartDate().isBefore(exam.getEndDate())) {
            throw new BadRequestAlertException("For RealExams, the visible Date has to be before the start Date and the start Date has to be before the end Date", ENTITY_NAME,
                    "examTimes");
        }
    }

    /**
     * Validates the working time, which should be equal (real exams) or smaller / equal (test exam) to the
     * difference between start- and endDate.
     *
     * @param exam the exam to be checked
     */
    private void checkExamForWorkingTimeConflictsElseThrow(Exam exam) {
        int differenceStartEndDate = Math.toIntExact(Duration.between(exam.getStartDate(), exam.getEndDate()).toSeconds());

        if (exam.isTestExam()) {
            if (exam.getWorkingTime() > differenceStartEndDate || exam.getWorkingTime() < 1) {
                throw new BadRequestAlertException("For TestExams, the working time must be at least 1 and at most the duration of the working window.", ENTITY_NAME, "examTimes");
            }
        }
        else if (exam.getWorkingTime() != differenceStartEndDate) {
            /*
             * Set the working time to the time difference for real exams, if not done by the client. This can be an issue if the working time calculation in the client is not
             * performed (e.g. for Cypress-2E2-Tests). However, since the working time currently depends on the start- and end-date, we can do a server-side assignment
             */
            exam.setWorkingTime(differenceStartEndDate);
        }
    }

    /**
     * Checks if the exam has at least one point achievable
     *
     * @param exam the exam to be checked
     */
    private void checkExamPointsAndCorrectionRoundsElseThrow(Exam exam) {
        if (exam.getMaxPoints() <= 0) {
            throw new BadRequestAlertException("An exam cannot have negative points.", ENTITY_NAME, "negativePoints");
        }

        if (exam.isTestExam() && exam.getNumberOfCorrectionRoundsInExam() != 0) {
            throw new BadRequestAlertException("A testExam has to have 0 correction rounds", ENTITY_NAME, "correctionRoundViolation");
        }

        if (!exam.isTestExam() && (exam.getNumberOfCorrectionRoundsInExam() <= 0 || exam.getNumberOfCorrectionRoundsInExam() > 2)) {
            throw new BadRequestAlertException("A realExam has to have either 1 or 2 correction rounds", ENTITY_NAME, "correctionRoundViolation");
        }
    }

    /**
     * GET /exams : Find all exams the user is allowed to access
     *
     * @param withExercises if only exams with at least one exercise Groups should be considered
     * @param search Pagable with all relevant information
     * @return the ResponseEntity with status 200 (OK) and a list of exams. The list can be empty
     */
    @GetMapping("/exams")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<SearchResultPageDTO<Exam>> getAllExamsOnPage(@RequestParam(defaultValue = "false") boolean withExercises, PageableSearchDTO<String> search) {
        final var user = userRepository.getUserWithGroupsAndAuthorities();
        return ResponseEntity.ok(examService.getAllOnPageWithSize(search, user, withExercises));
    }

    /**
     * GET /exams/{examId} : Find an exam by id with exercises for the exam import
     *
     * @param examId the exam to find
     * @return the ResponseEntity with status 200 (OK) and with the found exam as body
     */
    @GetMapping("/exams/{examId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Exam> getExamForImportWithExercises(@PathVariable Long examId) {
        log.debug("REST request to get exam : {} for import with exercises", examId);

        Exam exam = examService.findByIdWithExerciseGroupsAndExercisesElseThrow(examId);
        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(exam.getCourse().getId(), examId);

        return ResponseEntity.ok(exam);
    }

    /**
     * GET /courses/{courseId}/exams/{examId} : Find an exam by id.
     *
     * @param courseId           the course to which the exam belongs
     * @param examId             the exam to find
     * @param withStudents       boolean flag whether to include all students registered for the exam
     * @param withExerciseGroups boolean flag whether to include all exercise groups of the exam
     * @return the ResponseEntity with status 200 (OK) and with the found exam as body
     */
    @GetMapping("/courses/{courseId}/exams/{examId}")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<Exam> checkAccessAndLog(@PathVariable Long courseId, @PathVariable Long examId, @RequestParam(defaultValue = "false") boolean withStudents,
            @RequestParam(defaultValue = "false") boolean withExerciseGroups) {
        log.debug("REST request to get exam : {}", examId);

        if (withStudents) {
            examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);
        }
        else {
            examAccessService.checkCourseAndExamAccessForEditorElseThrow(courseId, examId);
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
     * @param courseId the course to which the exam belongs
     * @param examId   the exam to find
     * @return the ResponseEntity with status 200 (OK) and with the found exam as body
     */
    @GetMapping("/courses/{courseId}/exams/{examId}/statistics")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<ExamChecklistDTO> getExamStatistics(@PathVariable Long courseId, @PathVariable Long examId) {
        log.debug("REST request to get exam statistics: {}", examId);

        examAccessService.checkCourseAndExamAccessForTeachingAssistantElseThrow(courseId, examId);

        var course = courseRepository.findByIdElseThrow(courseId);
        var isInstructorInCourse = authCheckService.isAtLeastInstructorInCourse(course, null);

        Exam exam = examRepository.findByIdWithRegisteredUsersExerciseGroupsAndExercisesElseThrow(examId);
        ExamChecklistDTO examChecklistDTO = examService.getStatsForChecklist(exam, isInstructorInCourse);

        return ResponseEntity.ok(examChecklistDTO);
    }

    /**
     * GET /courses/{courseId}/exams/{examId}/scores : Find scores for an exam by id.
     *
     * @param courseId the course to which the exam belongs
     * @param examId   the exam to find
     * @return the ResponseEntity with status 200 (OK) and with the found ExamScoreDTO as body
     */
    @GetMapping("/courses/{courseId}/exams/{examId}/scores")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<ExamScoresDTO> getExamScore(@PathVariable Long courseId, @PathVariable Long examId) {
        long start = System.currentTimeMillis();
        log.debug("REST request to get score for exam : {}", examId);
        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);
        ExamScoresDTO examScoresDTO = examService.calculateExamScores(examId);
        log.info("get scores for exam {} took {}ms", examId, System.currentTimeMillis() - start);
        return ResponseEntity.ok(examScoresDTO);
    }

    /**
     * GET /courses/:courseId/exams/:examId/exam-for-assessment-dashboard
     *
     * @param courseId the id of the course to retrieve
     * @param examId   the id of the exam that contains the exercises
     * @return data about a course including all exercises, plus some data for the tutor as tutor status for assessment
     */
    @GetMapping("/courses/{courseId}/exams/{examId}/exam-for-assessment-dashboard")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<Exam> getExamForAssessmentDashboard(@PathVariable long courseId, @PathVariable long examId) {
        log.debug("REST request /courses/{courseId}/exams/{examId}/exam-for-assessment-dashboard");

        Exam exam = examService.findByIdWithExerciseGroupsAndExercisesElseThrow(examId);
        Course course = exam.getCourse();
        checkExamCourseIdElseThrow(courseId, exam);

        User user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, user);

        if (ZonedDateTime.now().isBefore(exam.getEndDate()) && authCheckService.isTeachingAssistantInCourse(course, user)) {
            // tutors cannot access the exercises before the exam ends
            throw new AccessForbiddenException("exam", examId);
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
     * @param examId   the id of the exam that contains the exercises
     * @return data about an exam test run including all exercises, plus some data for the tutor as tutor status for assessment
     */
    @GetMapping("/courses/{courseId}/exams/{examId}/exam-for-test-run-assessment-dashboard")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Exam> getExamForTestRunAssessmentDashboard(@PathVariable long courseId, @PathVariable long examId) {
        log.debug("REST request /courses/{courseId}/exams/{examId}/exam-for-test-run-assessment-dashboard");

        Exam exam = examService.findByIdWithExerciseGroupsAndExercisesElseThrow(examId);
        Course course = exam.getCourse();
        checkExamCourseIdElseThrow(courseId, exam);

        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);

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
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, null);

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

        examAccessService.checkCourseAccessForTeachingAssistantElseThrow(courseId);
        // We need the exercise groups and exercises for the exam status now
        List<Exam> exams = examRepository.findByCourseIdWithExerciseGroupsAndExercises(courseId);
        examRepository.setNumberOfRegisteredUsersForExams(exams);
        return ResponseEntity.ok(exams);
    }

    /**
     * GET /courses/{courseId}/exams-for-user : Find all exams with quiz-questions the user is allowed to access (Is at least Instructor)
     *
     * @param courseId the course to which the exam belongs
     * @return the ResponseEntity with status 200 (OK) and a list of exams. The list can be empty
     */
    @GetMapping("/courses/{courseId}/exams-for-user")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<List<Exam>> getExamsWithQuizExercisesForUser(@PathVariable Long courseId) {
        User user = userRepository.getUserWithGroupsAndAuthorities();
        if (authCheckService.isAdmin(user)) {
            return ResponseEntity.ok(examRepository.findAllWithQuizExercisesWithEagerExerciseGroupsAndExercises());
        }
        else {
            Course course = courseRepository.findByIdElseThrow(courseId);
            authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, user);
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
            throw new AccessForbiddenException("Only admins are allowed to access all exams!");
        }

        List<Exam> upcomingExams = examRepository.findAllCurrentAndUpcomingExams();
        return ResponseEntity.ok(upcomingExams);
    }

    /**
     * DELETE /courses/{courseId}/exams/{examId} : Delete the exam with the given id.
     * The delete operation cascades to all student exams, exercise group, exercises and their participations.
     *
     * @param courseId the course to which the exam belongs
     * @param examId   the id of the exam to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/courses/{courseId}/exams/{examId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> deleteExam(@PathVariable Long courseId, @PathVariable Long examId) {
        log.info("REST request to delete exam : {}", examId);

        var exam = examRepository.findByIdElseThrow(examId);
        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);

        if (exam.isMonitoring()) {
            // Cancel schedule of exam monitoring
            instanceMessageSendService.sendExamMonitoringScheduleCancel(examId);
        }

        examService.delete(examId);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, exam.getTitle())).build();
    }

    /**
     * DELETE /courses/{courseId}/exams/{examId}/reset : Reset the exam with the given id.
     * The reset operation deletes all studentExams, participations, submissions and feedback.
     *
     * @param courseId the course to which the exam belongs
     * @param examId   the id of the exam to reset
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/courses/{courseId}/exams/{examId}/reset")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Exam> resetExam(@PathVariable Long courseId, @PathVariable Long examId) {
        log.info("REST request to reset exam : {}", examId);

        var exam = examRepository.findByIdElseThrow(examId);
        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);

        if (exam.isMonitoring()) {
            // Cancel schedule of exam monitoring
            instanceMessageSendService.sendExamMonitoringScheduleCancel(examId);
        }

        examService.reset(exam.getId());
        Exam returnExam = examService.findByIdWithExerciseGroupsAndExercisesElseThrow(examId);
        examService.setExamProperties(returnExam);

        if (returnExam.isMonitoring()) {
            // Schedule exam monitoring
            instanceMessageSendService.sendExamMonitoringSchedule(examId);
        }

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

        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);

        var course = courseRepository.findByIdElseThrow(courseId);
        var exam = examRepository.findByIdWithRegisteredUsersElseThrow(examId);

        if (exam.isTestExam()) {
            throw new AccessForbiddenException("Registration is only allowed for RealExams");
        }

        var student = userRepository.findOneWithGroupsAndAuthoritiesByLogin(studentLogin)
                .orElseThrow(() -> new EntityNotFoundException("User with login: \"" + studentLogin + "\" does not exist"));

        if (student.getGroups().contains(exam.getCourse().getInstructorGroupName()) || authCheckService.isAdmin(student)) {
            throw new AccessForbiddenAlertException("You cannot register instructors or administrators to exams.", ENTITY_NAME, "cannotRegisterInstructor");
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
     * @param courseId the id of the course
     * @param examId   the id of the exam
     * @return the list of student exams with their corresponding users
     */
    @PostMapping(value = "/courses/{courseId}/exams/{examId}/generate-student-exams")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<List<StudentExam>> generateStudentExams(@PathVariable Long courseId, @PathVariable Long examId) {
        long start = System.nanoTime();
        log.info("REST request to generate student exams for exam {}", examId);

        final var exam = checkAccessAndLog(courseId, examId, Constants.GENERATE_STUDENT_EXAMS);
        examService.combineTemplateCommitsOfAllProgrammingExercisesInExam(exam);

        // Reset existing student exams & participations
        examService.deleteStudentExamsAndExistingParticipationsForExam(exam.getId());

        List<StudentExam> studentExams = studentExamRepository.generateStudentExams(exam);

        // we need to break a cycle for the serialization
        breakCyclesForSerialization(studentExams);

        // Reschedule after creation (possible longer working time)
        instanceMessageSendService.sendExamMonitoringSchedule(examId);
        log.info("Generated {} student exams in {} for exam {}", studentExams.size(), formatDurationFrom(start), examId);
        return ResponseEntity.ok().body(studentExams);
    }

    @NotNull
    private Exam checkAccessAndLog(Long courseId, Long examId, String auditEventAction) {
        final Exam exam = examRepository.findByIdWithRegisteredUsersExerciseGroupsAndExercisesElseThrow(examId);

        if (exam.isTestExam()) {
            throw new AccessForbiddenException("Registration is only allowed for RealExams");
        }

        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, exam);

        // Validate settings of the exam
        examService.validateForStudentExamGeneration(exam);

        User instructor = userRepository.getUser();
        AuditEvent auditEvent = new AuditEvent(instructor.getLogin(), auditEventAction, "examId=" + examId, "user=" + instructor.getLogin());
        auditEventRepository.add(auditEvent);
        return exam;
    }

    /**
     * POST /courses/:courseId/exams/:examId/generate-missing-student-exams:
     * Generates exams for students, who don't have an individual exam yet.
     * They are created randomly based on the exam configuration and the exercise groups.
     *
     * @param courseId the id of the course
     * @param examId   the id of the exam
     * @return the list of student exams with their corresponding users
     */
    @PostMapping(value = "/courses/{courseId}/exams/{examId}/generate-missing-student-exams")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<List<StudentExam>> generateMissingStudentExams(@PathVariable Long courseId, @PathVariable Long examId) {
        long start = System.nanoTime();
        log.info("REST request to generate missing student exams for exam {}", examId);

        final var exam = checkAccessAndLog(courseId, examId, Constants.GENERATE_MISSING_STUDENT_EXAMS);
        List<StudentExam> studentExams = studentExamRepository.generateMissingStudentExams(exam);

        // we need to break a cycle for the serialization
        breakCyclesForSerialization(studentExams);

        // Reschedule after creation (possible longer working time)
        instanceMessageSendService.sendExamMonitoringSchedule(examId);
        log.info("Generated {} missing student exams in {} for exam {}", studentExams.size(), formatDurationFrom(start), examId);
        return ResponseEntity.ok().body(studentExams);
    }

    private static void breakCyclesForSerialization(List<StudentExam> studentExams) {
        for (StudentExam studentExam : studentExams) {
            studentExam.getExam().setRegisteredUsers(null);
            studentExam.getExam().setExerciseGroups(null);
            studentExam.getExam().setStudentExams(null);
        }
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

        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);

        if (examDateService.getLatestIndividualExamEndDate(examId).isAfter(ZonedDateTime.now())) {
            throw new AccessForbiddenException("There are still exams running, quizzes can only be evaluated once all exams are finished.");
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

        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);

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

        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);

        Integer numOfLockedExercises = examService.lockAllRepositories(examId);

        log.info("Locked {} programming exercises of exam {}", numOfLockedExercises, examId);

        return ResponseEntity.ok().body(numOfLockedExercises);
    }

    /**
     * POST /courses/:courseId/exams/:examId/students : Add multiple users to the students of the exam so that they can access the exam
     * The passed list of UserDTOs must include the registration number (the other entries are currently ignored and can be left out)
     * Note: registration based on other user attributes (e.g. email, name, login) is currently NOT supported
     * <p>
     * This method first tries to find the student in the internal Artemis user database (because the user is most probably already using Artemis).
     * In case the user cannot be found, we additionally search the (TUM) LDAP in case it is configured properly.
     *
     * @param courseId    the id of the course
     * @param examId      the id of the exam
     * @param studentDtos the list of students (with at least registration number) who should get access to the exam
     * @return the list of students who could not be registered for the exam, because they could NOT be found in the Artemis database and could NOT be found in the TUM LDAP
     */
    @PostMapping(value = "/courses/{courseId}/exams/{examId}/students")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<List<StudentDTO>> addStudentsToExam(@PathVariable Long courseId, @PathVariable Long examId, @RequestBody List<StudentDTO> studentDtos) {
        log.debug("REST request to add {} as students to exam {}", studentDtos, examId);

        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);

        List<StudentDTO> notFoundStudentsDtos = examRegistrationService.registerStudentsForExam(courseId, examId, studentDtos);
        return ResponseEntity.ok().body(notFoundStudentsDtos);
    }

    /**
     * POST /courses/:courseId/exams/:examId/register-course-students : Add all users which are enrolled in the course to the exam so that the student can access the exam
     *
     * @param courseId the id of the course
     * @param examId   the id of the exam
     * @return empty ResponseEntity with status 200 (OK) or with status 404 (Not Found)
     */
    @PostMapping(value = "/courses/{courseId}/exams/{examId}/register-course-students")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> registerCourseStudents(@PathVariable Long courseId, @PathVariable Long examId) {
        // get all students enrolled in the course
        log.debug("REST request to add all students to exam {} with courseId {}", examId, courseId);

        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);

        examRegistrationService.addAllStudentsOfCourseToExam(courseId, examId);
        return ResponseEntity.ok().body(null);
    }

    /**
     * DELETE /courses/:courseId/exams/:examId/students/:studentLogin :
     * Remove one single given user (based on the login) from the students of the exam so that the student cannot access the exam anymore.
     * Optionally, also deletes participations and submissions of the student in the student exam.
     *
     * @param courseId                        the id of the course
     * @param examId                          the id of the exam
     * @param studentLogin                    the login of the user who should lose student access
     * @param withParticipationsAndSubmission request param deciding whether participations and submissions should also be deleted
     * @return empty ResponseEntity with status 200 (OK) or with status 404 (Not Found)
     */
    @DeleteMapping(value = "/courses/{courseId}/exams/{examId}/students/{studentLogin:" + Constants.LOGIN_REGEX + "}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> removeStudentFromExam(@PathVariable Long courseId, @PathVariable Long examId, @PathVariable String studentLogin,
            @RequestParam(defaultValue = "false") boolean withParticipationsAndSubmission) {
        log.debug("REST request to remove {} as student from exam : {}", studentLogin, examId);

        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);

        Optional<User> optionalStudent = userRepository.findOneWithGroupsAndAuthoritiesByLogin(studentLogin);
        if (optionalStudent.isEmpty()) {
            throw new EntityNotFoundException("user", studentLogin);
        }

        examRegistrationService.unregisterStudentFromExam(examId, withParticipationsAndSubmission, optionalStudent.get());
        return ResponseEntity.ok().body(null);
    }

    /**
     * DELETE /courses/{courseId}/exams/{examId}/allstudents :
     * Remove all students of the exam so that they cannot access the exam anymore.
     * Optionally, also deletes participations and submissions of all students in their student exams.
     *
     * @param courseId                        the id of the course
     * @param examId                          the id of the exam
     * @param withParticipationsAndSubmission request param deciding whether participations and submissions should also be deleted
     * @return empty ResponseEntity with status 200 (OK) or with status 404 (Not Found)
     */
    @DeleteMapping(value = "/courses/{courseId}/exams/{examId}/students")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> removeAllStudentsFromExam(@PathVariable Long courseId, @PathVariable Long examId,
            @RequestParam(defaultValue = "false") boolean withParticipationsAndSubmission) {
        log.debug("REST request to remove all students from exam {} with courseId {}", examId, courseId);

        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);

        examRegistrationService.unregisterAllStudentFromExam(examId, withParticipationsAndSubmission);
        return ResponseEntity.ok().body(null);
    }

    /**
     * GET /courses/{courseId}/exams/{examId}/start : Get an exam for the exam start.
     * Real Exams: StudentExam needs to be generated by an instructor
     * Test Exam: StudentExam can be self-created by the user
     * Note: The Access control is performed in the {@link ExamAccessService#getExamInCourseElseThrow(Long, Long)} to limit the DB-calls
     *
     * @param courseId the id of the course
     * @param examId   the id of the exam
     * @return the ResponseEntity with status 200 (OK) and with the found student exam (without exercises) as body
     */
    @GetMapping("/courses/{courseId}/exams/{examId}/start")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<StudentExam> getStudentExamForStart(@PathVariable Long courseId, @PathVariable Long examId) {
        log.debug("REST request to get exam {} for conduction", examId);

        StudentExam exam = examAccessService.getExamInCourseElseThrow(courseId, examId);
        return ResponseEntity.ok(exam);
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

        examAccessService.checkCourseAndExamAccessForEditorElseThrow(courseId, examId);

        Exam exam = examRepository.findByIdWithExerciseGroupsElseThrow(examId);

        // Ensure that exactly as many exercise groups have been received as are currently related to the exam
        if (orderedExerciseGroups.size() != exam.getExerciseGroups().size()) {
            throw new AccessForbiddenException("exam", examId);
        }

        // Ensure that all received exercise groups are already related to the exam
        for (ExerciseGroup exerciseGroup : orderedExerciseGroups) {
            if (!exam.getExerciseGroups().contains(exerciseGroup)) {
                throw new AccessForbiddenException("exam", examId);
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

        examAccessService.checkCourseAndExamAccessForTeachingAssistantElseThrow(courseId, examId);

        var examInformation = new ExamInformationDTO(examDateService.getLatestIndividualExamEndDate(examId));
        return ResponseEntity.ok().body(examInformation);
    }

    /**
     * GET /courses/:courseId/exams/:examId/lockedSubmissions Get locked submissions for exam for user
     *
     * @param courseId - the id of the course
     * @param examId   - the id of the exam
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
     * <p>
     * This method starts the process of archiving all exam exercises and submissions.
     * It immediately returns and runs this task asynchronously. When the task is done, the exam is marked as archived, which means the zip file can be downloaded.
     *
     * @param courseId the id of the course
     * @param examId   the id of the exam to archive
     * @return empty
     */
    @PutMapping("/courses/{courseId}/exams/{examId}/archive")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> archiveExam(@PathVariable Long courseId, @PathVariable Long examId) {
        log.info("REST request to archive exam : {}", examId);

        final Exam exam = examRepository.findOneWithEagerExercisesGroupsAndStudentExams(examId);
        if (exam == null) {
            throw new EntityNotFoundException("exam", examId);
        }

        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);

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
     * @param examId   The id of the archived exam
     * @return ResponseEntity with status
     */
    @GetMapping("/courses/{courseId}/exams/{examId}/download-archive")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Resource> downloadExamArchive(@PathVariable Long courseId, @PathVariable Long examId) throws FileNotFoundException {
        log.info("REST request to download archive of exam : {}", examId);
        final Exam exam = examRepository.findByIdElseThrow(examId);

        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);

        if (!exam.hasExamArchive()) {
            throw new EntityNotFoundException("exam", examId);
        }

        // The path is stored in the exam table
        Path archive = Path.of(examArchivesDirPath, exam.getExamArchivePath());

        File zipFile = archive.toFile();
        InputStreamResource resource = new InputStreamResource(new FileInputStream(zipFile));
        return ResponseEntity.ok().contentLength(zipFile.length()).contentType(MediaType.APPLICATION_OCTET_STREAM).header("filename", zipFile.getName()).body(resource);
    }

}
