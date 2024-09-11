package de.tum.cit.aet.artemis.web.rest;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.service.util.TimeLogUtil.formatDurationFrom;
import static java.time.ZonedDateTime.now;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.security.Principal;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BadRequestException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import de.tum.cit.aet.artemis.assessment.domain.TutorParticipation;
import de.tum.cit.aet.artemis.assessment.repository.TutorParticipationRepository;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.CustomAuditEventRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.domain.SuspiciousSessionsAnalysisOptions;
import de.tum.cit.aet.artemis.exam.repository.ExamRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.service.AssessmentDashboardService;
import de.tum.cit.aet.artemis.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.service.SubmissionService;
import de.tum.cit.aet.artemis.service.dto.StudentDTO;
import de.tum.cit.aet.artemis.service.exam.ExamAccessService;
import de.tum.cit.aet.artemis.service.exam.ExamDateService;
import de.tum.cit.aet.artemis.service.exam.ExamDeletionService;
import de.tum.cit.aet.artemis.service.exam.ExamImportService;
import de.tum.cit.aet.artemis.service.exam.ExamLiveEventsService;
import de.tum.cit.aet.artemis.service.exam.ExamRegistrationService;
import de.tum.cit.aet.artemis.service.exam.ExamService;
import de.tum.cit.aet.artemis.service.exam.ExamSessionService;
import de.tum.cit.aet.artemis.service.exam.ExamUserService;
import de.tum.cit.aet.artemis.service.exam.StudentExamService;
import de.tum.cit.aet.artemis.service.feature.Feature;
import de.tum.cit.aet.artemis.service.feature.FeatureToggle;
import de.tum.cit.aet.artemis.service.messaging.InstanceMessageSendService;
import de.tum.cit.aet.artemis.service.metis.conversation.ChannelService;
import de.tum.cit.aet.artemis.service.util.TimeLogUtil;
import de.tum.cit.aet.artemis.web.rest.dto.CourseWithIdDTO;
import de.tum.cit.aet.artemis.web.rest.dto.ExamChecklistDTO;
import de.tum.cit.aet.artemis.web.rest.dto.ExamInformationDTO;
import de.tum.cit.aet.artemis.web.rest.dto.ExamScoresDTO;
import de.tum.cit.aet.artemis.web.rest.dto.ExamUserDTO;
import de.tum.cit.aet.artemis.web.rest.dto.ExamWithIdAndCourseDTO;
import de.tum.cit.aet.artemis.web.rest.dto.ExerciseForPlagiarismCasesOverviewDTO;
import de.tum.cit.aet.artemis.web.rest.dto.ExerciseGroupWithIdAndExamDTO;
import de.tum.cit.aet.artemis.web.rest.dto.SearchResultPageDTO;
import de.tum.cit.aet.artemis.web.rest.dto.StatsForDashboardDTO;
import de.tum.cit.aet.artemis.web.rest.dto.SuspiciousExamSessionsDTO;
import de.tum.cit.aet.artemis.web.rest.dto.examevent.ExamWideAnnouncementEventDTO;
import de.tum.cit.aet.artemis.web.rest.dto.pageablesearch.SearchTermPageableSearchDTO;
import de.tum.cit.aet.artemis.web.rest.errors.AccessForbiddenAlertException;
import de.tum.cit.aet.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.cit.aet.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.cit.aet.artemis.web.rest.errors.ConflictException;
import de.tum.cit.aet.artemis.web.rest.errors.EntityNotFoundException;
import de.tum.cit.aet.artemis.web.rest.util.HeaderUtil;
import io.swagger.annotations.ApiParam;
import tech.jhipster.web.util.PaginationUtil;

/**
 * REST controller for managing Exam.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class ExamResource {

    private static final Logger log = LoggerFactory.getLogger(ExamResource.class);

    private static final String ENTITY_NAME = "exam";

    private final ChannelRepository channelRepository;

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    @Value("${artemis.course-archives-path}")
    private String examArchivesDirPath;

    private final UserRepository userRepository;

    private final CourseRepository courseRepository;

    private final ExamService examService;

    private final ExamDeletionService examDeletionService;

    private final ExamDateService examDateService;

    private final ExamRegistrationService examRegistrationService;

    private final ExamRepository examRepository;

    private final ExamAccessService examAccessService;

    private final SubmissionService submissionService;

    private final InstanceMessageSendService instanceMessageSendService;

    private final AuthorizationCheckService authCheckService;

    private final TutorParticipationRepository tutorParticipationRepository;

    private final AssessmentDashboardService assessmentDashboardService;

    private final ExamImportService examImportService;

    private final CustomAuditEventRepository auditEventRepository;

    private final ChannelService channelService;

    private final ExerciseRepository exerciseRepository;

    private final ExamSessionService examSessionService;

    private final ExamLiveEventsService examLiveEventsService;

    private final StudentExamService studentExamService;

    private final ExamUserService examUserService;

    public ExamResource(UserRepository userRepository, CourseRepository courseRepository, ExamService examService, ExamDeletionService examDeletionService,
            ExamAccessService examAccessService, InstanceMessageSendService instanceMessageSendService, ExamRepository examRepository, SubmissionService submissionService,
            AuthorizationCheckService authCheckService, ExamDateService examDateService, TutorParticipationRepository tutorParticipationRepository,
            AssessmentDashboardService assessmentDashboardService, ExamRegistrationService examRegistrationService, ExamImportService examImportService,
            CustomAuditEventRepository auditEventRepository, ChannelService channelService, ChannelRepository channelRepository, ExerciseRepository exerciseRepository,
            ExamSessionService examSessionRepository, ExamLiveEventsService examLiveEventsService, StudentExamService studentExamService, ExamUserService examUserService) {
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.examService = examService;
        this.examDeletionService = examDeletionService;
        this.submissionService = submissionService;
        this.examDateService = examDateService;
        this.examRegistrationService = examRegistrationService;
        this.examRepository = examRepository;
        this.examAccessService = examAccessService;
        this.instanceMessageSendService = instanceMessageSendService;
        this.authCheckService = authCheckService;
        this.tutorParticipationRepository = tutorParticipationRepository;
        this.assessmentDashboardService = assessmentDashboardService;
        this.examImportService = examImportService;
        this.auditEventRepository = auditEventRepository;
        this.channelService = channelService;
        this.channelRepository = channelRepository;
        this.exerciseRepository = exerciseRepository;
        this.examSessionService = examSessionRepository;
        this.examLiveEventsService = examLiveEventsService;
        this.studentExamService = studentExamService;
        this.examUserService = examUserService;
    }

    /**
     * POST /courses/{courseId}/exams : Create a new exam.
     *
     * @param courseId the course to which the exam belongs
     * @param exam     the exam to create
     * @return the ResponseEntity with status 201 (Created) and with body the new exam, or with status 400 (Bad Request) if the exam has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("courses/{courseId}/exams")
    @EnforceAtLeastInstructor
    public ResponseEntity<Exam> createExam(@PathVariable Long courseId, @RequestBody Exam exam) throws URISyntaxException {
        log.debug("REST request to create an exam : {}", exam);
        if (exam.getId() != null) {
            throw new BadRequestAlertException("A new exam cannot already have an ID", ENTITY_NAME, "idExists");
        }

        checkForExamConflictsElseThrow(courseId, exam);

        // Check that exerciseGroups are not set to prevent manipulation of associated exerciseGroups
        if (!exam.getExerciseGroups().isEmpty()) {
            throw new ConflictException("A new exam cannot have exercise groups yet", ENTITY_NAME, "groupsExist");
        }

        examAccessService.checkCourseAccessForInstructorElseThrow(courseId);
        Exam savedExam = examRepository.save(exam);
        channelService.createExamChannel(savedExam, Optional.ofNullable(exam.getChannelName()));
        return ResponseEntity.created(new URI("/api/courses/" + courseId + "/exams/" + savedExam.getId())).body(savedExam);
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
    @PutMapping("courses/{courseId}/exams")
    @EnforceAtLeastInstructor
    public ResponseEntity<Exam> updateExam(@PathVariable Long courseId, @RequestBody Exam updatedExam) throws URISyntaxException {
        log.debug("REST request to update an exam : {}", updatedExam);

        if (updatedExam.getId() == null) {
            return createExam(courseId, updatedExam);
        }

        checkForExamConflictsElseThrow(courseId, updatedExam);

        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, updatedExam.getId());

        // Make sure that the original references are preserved.
        Exam originalExam = examRepository.findByIdElseThrow(updatedExam.getId());
        var originalExamDuration = originalExam.getDuration();

        // The Exam Mode cannot be changed after creation -> Compare request with version in the database
        if (updatedExam.isTestExam() != originalExam.isTestExam()) {
            throw new ConflictException("The Exam Mode cannot be changed after creation", ENTITY_NAME, "examModeMismatch");
        }

        // NOTE: Make sure that all references are preserved here
        updatedExam.setExerciseGroups(originalExam.getExerciseGroups());
        updatedExam.setStudentExams(originalExam.getStudentExams());
        updatedExam.setExamUsers(originalExam.getExamUsers());

        Channel updatedChannel = channelService.updateExamChannel(originalExam, updatedExam);

        Exam savedExam = examRepository.save(updatedExam);

        // NOTE: We have to get exercises and groups as we need them for re-scheduling
        Exam examWithExercises = examService.findByIdWithExerciseGroupsAndExercisesElseThrow(savedExam.getId(), false);

        // We can't test dates for equality as the dates retrieved from the database lose precision. Also use instant to take timezones into account
        Comparator<ZonedDateTime> comparator = Comparator.comparing(date -> date.truncatedTo(ChronoUnit.SECONDS).toInstant());
        if (comparator.compare(originalExam.getVisibleDate(), updatedExam.getVisibleDate()) != 0
                || comparator.compare(originalExam.getStartDate(), updatedExam.getStartDate()) != 0) {
            // for all programming exercises in the exam, send their ids for scheduling
            examWithExercises.getExerciseGroups().stream().flatMap(group -> group.getExercises().stream()).filter(ProgrammingExercise.class::isInstance).map(Exercise::getId)
                    .forEach(instanceMessageSendService::sendProgrammingExerciseSchedule);
        }

        // NOTE: if the end date was changed, we need to update student exams and re-schedule exercises
        if (comparator.compare(originalExam.getEndDate(), savedExam.getEndDate()) != 0) {
            int workingTimeChange = savedExam.getDuration() - originalExamDuration;
            Exam examWithStudentExams = examRepository.findOneWithEagerExercisesGroupsAndStudentExams(savedExam.getId());
            examService.updateStudentExamsAndRescheduleExercises(examWithStudentExams, originalExamDuration, workingTimeChange);
        }

        if (updatedChannel != null) {
            savedExam.setChannelName(updatedExam.getChannelName());
        }

        return ResponseEntity.ok(savedExam);
    }

    /**
     * PATCH /courses/{courseId}/exams/{examId}/working-time : Update the working time of all exams
     *
     * @param courseId          the course ID to which the exam belong to
     * @param examId            the exam ID the working time should be extended for
     * @param workingTimeChange the working time change in seconds (can be positive or negative, but must not be 0)
     * @return the ResponseEntity with status 200 (OK) and with the updated exam as body
     */
    @PatchMapping("courses/{courseId}/exams/{examId}/working-time")
    @EnforceAtLeastInstructor
    public ResponseEntity<Exam> updateExamWorkingTime(@PathVariable Long courseId, @PathVariable Long examId, @RequestBody Integer workingTimeChange) {
        log.debug("REST request to update the working time of exam with id {}", examId);

        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);

        if (workingTimeChange == 0) {
            throw new BadRequestException();
        }

        // NOTE: We have to get exercise groups as `scheduleModelingExercises` needs them.
        // We also need all student exams for updateStudentExamsAndRescheduleExercises.
        Exam exam = examRepository.findOneWithEagerExercisesGroupsAndStudentExams(examId);
        var originalExamDuration = exam.getDuration();

        // 1. Update the end date & working time of the exam
        exam.setEndDate(exam.getEndDate().plusSeconds(workingTimeChange));
        exam.setWorkingTime(exam.getWorkingTime() + workingTimeChange);
        examRepository.save(exam);

        // 2. Re-calculate the working times of all student exams
        examService.updateStudentExamsAndRescheduleExercises(exam, originalExamDuration, workingTimeChange);

        return ResponseEntity.ok(exam);
    }

    /**
     * POST /courses/{courseId}/exams/{examId}/announcements : Create a new announcement for the exam.
     *
     * @param courseId the course to which the exam belongs
     * @param examId   the exam to which the announcement belongs
     * @param message  the message of the announcement
     * @return the ResponseEntity with status 200 (OK) and with the new announcement as body
     */
    @PostMapping("courses/{courseId}/exams/{examId}/announcements")
    @EnforceAtLeastInstructor
    public ResponseEntity<ExamWideAnnouncementEventDTO> createExamAnnouncement(@PathVariable Long courseId, @PathVariable Long examId, @RequestBody String message) {
        long start = System.nanoTime();
        log.debug("REST request to create an announcement for exam with id {}", examId);
        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);
        var exam = examRepository.findByIdElseThrow(examId);

        if (!exam.isVisibleToStudents()) {
            throw new BadRequestAlertException("Exam is not visible to students", "exam", "examNotVisible");
        }

        var event = examLiveEventsService.createAndDistributeExamAnnouncementEvent(exam, message, userRepository.getUser());
        log.debug("createExamAnnouncement took {} for exam {}", TimeLogUtil.formatDurationFrom(start), examId);
        return ResponseEntity.ok(event.asDTO());
    }

    /**
     * POST /courses/{courseId}/exam-import : Imports a new exam with exercises.
     *
     * @param courseId         the course to which the exam belongs
     * @param examToBeImported the exam to import / create
     * @return the ResponseEntity with status 201 (Created) and with body the newly imported exam, or with status 400 (Bad Request) if the exam has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("courses/{courseId}/exam-import")
    @EnforceAtLeastInstructor
    public ResponseEntity<Exam> importExamWithExercises(@PathVariable Long courseId, @RequestBody Exam examToBeImported) throws URISyntaxException, IOException {
        log.debug("REST request to import an exam : {}", examToBeImported);

        // Step 1: Check if Exam has an ID
        if (examToBeImported.getId() != null) {
            throw new BadRequestAlertException("A imported exam cannot already have an ID", ENTITY_NAME, "idexists");
        }

        examAccessService.checkCourseAccessForInstructorElseThrow(courseId);

        // Step 3: Validate the Exam dates
        checkForExamConflictsElseThrow(courseId, examToBeImported);

        // Step 4: Import Exam with Exercises and create a channel for the exam
        Exam examCopied = examImportService.importExamWithExercises(examToBeImported, courseId);

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

        checkExamAttendanceCheckSettings(exam);
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
                throw new BadRequestAlertException("For test exams, the visible date has to be before or equal to the start date and the start date has to be before the end date",
                        ENTITY_NAME, "examTimes");
            }
        }
        else if (!exam.getVisibleDate().isBefore(exam.getStartDate()) || !exam.getStartDate().isBefore(exam.getEndDate())) {
            throw new BadRequestAlertException("For real exams, the visible date has to be before the start date and the start date has to be before the end date", ENTITY_NAME,
                    "examTimes");
        }

        if (exam.getExampleSolutionPublicationDate() != null && exam.getExampleSolutionPublicationDate().isBefore(exam.getEndDate())) {
            throw new BadRequestAlertException("Example solutions cannot be published before the end date of an exam.", ENTITY_NAME, "examTimes");
        }
    }

    /**
     * Validates the working time, which should be equal (real exams) or smaller / equal (test exam) to the
     * difference between start- and endDate.
     *
     * @param exam the exam to be checked
     */
    private void checkExamForWorkingTimeConflictsElseThrow(Exam exam) {
        var examDuration = exam.getDuration();

        if (exam.isTestExam()) {
            if (exam.getWorkingTime() > examDuration || exam.getWorkingTime() < 1) {
                throw new BadRequestAlertException("For TestExams, the working time must be at least 1 and at most the duration of the working window.", ENTITY_NAME, "examTimes");
            }
        }
        else if (exam.getWorkingTime() != examDuration) {
            /*
             * Set the working time to the time difference for real exams, if not done by the client. This can be an issue if the working time calculation in the client is not
             * performed (e.g. for Cypress-2E2-Tests). However, since the working time currently depends on the start- and end-date, we can do a server-side assignment
             */
            exam.setWorkingTime(examDuration);
        }
    }

    /**
     * Checks if the exam has at least one point achievable
     *
     * @param exam the exam to be checked
     */
    private void checkExamPointsAndCorrectionRoundsElseThrow(Exam exam) {
        if (exam.getExamMaxPoints() <= 0) {
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
     * Checks if the test exam doesn't have attendance check turned on
     *
     * @param exam the exam to be checked
     */
    private void checkExamAttendanceCheckSettings(Exam exam) {
        if (exam.isTestExam() && exam.isExamWithAttendanceCheck()) {
            throw new BadRequestAlertException("A test exam cannot have attendance check turned on", ENTITY_NAME, "attendanceCheckViolation");
        }
    }

    /**
     * GET /exams/active : Find all active exams the user is allowed to access.
     * Exams that are active have visibilityDate for the previous and upcoming seven days.
     *
     * @param pageable pageable parameters
     * @return the ResponseEntity with status 200 (OK) and a list of exams. The list can be empty
     */
    @GetMapping("exams/active")
    @EnforceAtLeastInstructor
    public ResponseEntity<List<Exam>> getAllActiveExams(@ApiParam Pageable pageable) {
        final var user = userRepository.getUserWithGroupsAndAuthorities();
        Page<Exam> page = examService.getAllActiveExams(pageable, user);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    /**
     * GET /exams : Find all exams the user is allowed to access
     *
     * @param withExercises if only exams with at least one exercise Groups should be considered
     * @param search        Pageable with all relevant information
     * @return the ResponseEntity with status 200 (OK) and a list of exams. The list can be empty
     */
    @GetMapping("exams")
    @EnforceAtLeastEditor
    public ResponseEntity<SearchResultPageDTO<Exam>> getAllExamsOnPage(@RequestParam(defaultValue = "false") boolean withExercises, SearchTermPageableSearchDTO<String> search) {
        final var user = userRepository.getUserWithGroupsAndAuthorities();
        return ResponseEntity.ok(examService.getAllOnPageWithSize(search, user, withExercises));
    }

    /**
     * GET /exams/{examId} : Find an exam by id with exercises for the exam import
     *
     * @param examId the exam to find
     * @return the ResponseEntity with status 200 (OK) and with the found exam as body
     */
    @GetMapping("exams/{examId}")
    @EnforceAtLeastInstructor
    public ResponseEntity<Exam> getExamForImportWithExercises(@PathVariable Long examId) {
        log.debug("REST request to get exam : {} for import with exercises", examId);

        Exam exam = examService.findByIdWithExerciseGroupsAndExercisesElseThrow(examId, true);
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
    @GetMapping("courses/{courseId}/exams/{examId}")
    @EnforceAtLeastEditor
    public ResponseEntity<Exam> getExam(@PathVariable Long courseId, @PathVariable Long examId, @RequestParam(defaultValue = "false") boolean withStudents,
            @RequestParam(defaultValue = "false") boolean withExerciseGroups) {
        log.debug("REST request to get exam : {}", examId);

        if (withStudents) {
            examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);
        }
        else {
            examAccessService.checkCourseAndExamAccessForEditorElseThrow(courseId, examId);
        }

        if (!withStudents && !withExerciseGroups) {
            Exam exam = examRepository.findByIdElseThrow(examId);
            Channel channel = channelRepository.findChannelByExamId(exam.getId());
            if (channel != null) {
                exam.setChannelName(channel.getName());
            }
            examService.setQuizExamProperties(exam);
            return ResponseEntity.ok(exam);
        }

        if (withExerciseGroups) {
            Exam exam;
            if (withStudents) {
                exam = examRepository.findByIdWithExamUsersExerciseGroupsAndExercisesElseThrow(examId);
            }
            else {
                exam = examService.findByIdWithExerciseGroupsAndExercisesElseThrow(examId, true);
            }
            examService.setExamProperties(exam);
            examService.setQuizExamProperties(exam);
            return ResponseEntity.ok(exam);
        }

        Exam exam = examRepository.findByIdWithExamUsersElseThrow(examId);
        exam.getExamUsers().forEach(examUser -> examUser.getUser().setVisibleRegistrationNumber(examUser.getUser().getRegistrationNumber()));

        examService.setQuizExamProperties(exam);
        return ResponseEntity.ok(exam);
    }

    /**
     * GET /exams/{examId}/title : Returns the title of the exam with the given id
     *
     * @param examId the id of the exam
     * @return the title of the exam wrapped in an ResponseEntity or 404 Not Found if no exam with that id exists
     */
    @GetMapping("exams/{examId}/title")
    @EnforceAtLeastStudent
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
    @GetMapping("courses/{courseId}/exams/{examId}/statistics")
    @EnforceAtLeastTutor
    public ResponseEntity<ExamChecklistDTO> getExamStatistics(@PathVariable Long courseId, @PathVariable Long examId) {
        log.debug("REST request to get exam statistics: {}", examId);

        examAccessService.checkCourseAndExamAccessForTeachingAssistantElseThrow(courseId, examId);

        var course = courseRepository.findByIdElseThrow(courseId);
        var isInstructorInCourse = authCheckService.isAtLeastInstructorInCourse(course, null);

        Exam exam = examRepository.findByIdWithExamUsersExerciseGroupsAndExercisesElseThrow(examId);
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
    @GetMapping("courses/{courseId}/exams/{examId}/scores")
    @EnforceAtLeastInstructor
    public ResponseEntity<ExamScoresDTO> getExamScore(@PathVariable Long courseId, @PathVariable Long examId) {
        long start = System.currentTimeMillis();
        log.info("REST request to get score for exam : {}", examId);
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
    @GetMapping("courses/{courseId}/exams/{examId}/exam-for-assessment-dashboard")
    @EnforceAtLeastTutor
    public ResponseEntity<Exam> getExamForAssessmentDashboard(@PathVariable long courseId, @PathVariable long examId) {
        log.debug("REST request /courses/{courseId}/exams/{examId}/exam-for-assessment-dashboard");

        Exam exam = examService.findByIdWithExerciseGroupsAndExercisesElseThrow(examId, false);
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
            exerciseGroup.setExercises(courseRepository.filterInterestingExercisesForAssessmentDashboards(exerciseGroup.getExercises()));
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
    @GetMapping("courses/{courseId}/exams/{examId}/exam-for-test-run-assessment-dashboard")
    @EnforceAtLeastInstructor
    public ResponseEntity<Exam> getExamForTestRunAssessmentDashboard(@PathVariable long courseId, @PathVariable long examId) {
        log.debug("REST request /courses/{courseId}/exams/{examId}/exam-for-test-run-assessment-dashboard");

        Exam exam = examService.findByIdWithExerciseGroupsAndExercisesElseThrow(examId, false);
        Course course = exam.getCourse();
        checkExamCourseIdElseThrow(courseId, exam);

        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);

        for (ExerciseGroup exerciseGroup : exam.getExerciseGroups()) {
            exerciseGroup.setExercises(courseRepository.filterInterestingExercisesForAssessmentDashboards(exerciseGroup.getExercises()));
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
    @GetMapping("courses/{courseId}/exams/{examId}/stats-for-exam-assessment-dashboard")
    @EnforceAtLeastTutor
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
    @GetMapping("courses/{courseId}/exams")
    @EnforceAtLeastTutor
    public ResponseEntity<List<Exam>> getExamsForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all exams for Course : {}", courseId);

        examAccessService.checkCourseAccessForTeachingAssistantElseThrow(courseId);
        // We need the exercise groups and exercises for the exam status now
        List<Exam> exams = examRepository.findByCourseIdWithExerciseGroupsAndExercises(courseId);
        examRepository.setNumberOfExamUsersForExams(exams);
        return ResponseEntity.ok(exams);
    }

    /**
     * GET /courses/{courseId}/exams-for-user : Find all exams with quiz-questions the user is allowed to access (Is at least Instructor)
     *
     * @param courseId the course to which the exam belongs
     * @return the ResponseEntity with status 200 (OK) and a list of exams. The list can be empty
     */
    @GetMapping("courses/{courseId}/exams-for-user")
    @EnforceAtLeastInstructor
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
     * DELETE /courses/{courseId}/exams/{examId}/cleanup : Cleans up an exam by deleting all student submissions.
     *
     * @param courseId  id of the course which in includes the exam
     * @param examId    id of the exam to clean up
     * @param principal the user that wants to cleanup the exam
     * @return ResponseEntity with status
     */
    @DeleteMapping("courses/{courseId}/exams/{examId}/cleanup")
    @EnforceAtLeastInstructor
    public ResponseEntity<Resource> cleanup(@PathVariable Long courseId, @PathVariable Long examId, Principal principal) {
        log.info("REST request to cleanup the exam : {}", examId);
        final Exam exam = examRepository.findByIdElseThrow(examId);
        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);
        // Forbid cleaning the course if no archive has been created
        if (!exam.hasExamArchive()) {
            throw new BadRequestAlertException("Failed to clean up exam " + examId + " because it needs to be archived first.", ENTITY_NAME, "archivenonexistant");
        }
        examService.cleanupExam(examId, principal);
        return ResponseEntity.ok().build();
    }

    /**
     * DELETE /courses/{courseId}/exams/{examId} : Delete the exam with the given id.
     * The delete operation cascades to all student exams, exercise group, exercises and their participations.
     *
     * @param courseId the course to which the exam belongs
     * @param examId   the id of the exam to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("courses/{courseId}/exams/{examId}")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> deleteExam(@PathVariable Long courseId, @PathVariable Long examId) {
        log.info("REST request to delete exam : {}", examId);

        var exam = examRepository.findByIdWithExamUsersElseThrow(examId);
        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);

        examDeletionService.delete(examId);

        // delete all exam user signatures and images
        exam.getExamUsers().forEach(examUserService::deleteAvailableExamUserImages);

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
    @DeleteMapping("courses/{courseId}/exams/{examId}/reset")
    @EnforceAtLeastInstructor
    public ResponseEntity<Exam> resetExam(@PathVariable Long courseId, @PathVariable Long examId) {
        log.info("REST request to reset exam : {}", examId);

        var exam = examRepository.findByIdElseThrow(examId);
        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);

        examDeletionService.reset(exam.getId());
        Exam returnExam = examService.findByIdWithExerciseGroupsAndExercisesElseThrow(examId, false);
        examService.setExamProperties(returnExam);

        return ResponseEntity.ok(returnExam);
    }

    /**
     * POST /courses/:courseId/exams/:examId/students/:studentLogin : Add one single given user (based on the login) to the students of the exam so that the student can access the
     * exam
     *
     * @param courseId     the id of the course
     * @param examId       the id of the exam
     * @param studentLogin the login of the user who should get student access
     * @return empty ResponseEntity with status 200 (OK) or with status 404 (Not Found)
     */
    @PostMapping("courses/{courseId}/exams/{examId}/students/{studentLogin:" + Constants.LOGIN_REGEX + "}")
    @EnforceAtLeastInstructor
    public ResponseEntity<StudentDTO> addStudentToExam(@PathVariable Long courseId, @PathVariable Long examId, @PathVariable String studentLogin) {
        log.debug("REST request to add {} as student to exam : {}", studentLogin, examId);

        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);

        var course = courseRepository.findByIdElseThrow(courseId);
        var exam = examRepository.findByIdWithExamUsersElseThrow(examId);

        if (exam.isTestExam()) {
            throw new BadRequestAlertException("Add student to exam is only allowed for real exams", ENTITY_NAME, "addStudentOnlyForRealExams");
        }

        var student = userRepository.findOneWithGroupsAndAuthoritiesByLogin(studentLogin)
                .orElseThrow(() -> new EntityNotFoundException("User with login: \"" + studentLogin + "\" does not exist"));

        if (student.getGroups().contains(exam.getCourse().getInstructorGroupName()) || authCheckService.isAdmin(student)) {
            throw new AccessForbiddenAlertException("You cannot register instructors or administrators to exams.", ENTITY_NAME, "cannotRegisterInstructor");
        }

        examRegistrationService.registerStudentToExam(course, exam, student);
        return ResponseEntity.ok().body(new StudentDTO(student));
    }

    /**
     * POST /courses/:courseId/exams/:examId/generate-student-exams : Generates the student exams randomly based on the exam configuration and the exercise groups
     *
     * @param courseId the id of the course
     * @param examId   the id of the exam
     * @return the list of student exams with their corresponding users
     */
    @PostMapping("courses/{courseId}/exams/{examId}/generate-student-exams")
    @EnforceAtLeastInstructor
    public ResponseEntity<List<StudentExam>> generateStudentExams(@PathVariable Long courseId, @PathVariable Long examId) {
        long start = System.nanoTime();
        log.info("REST request to generate student exams for exam {}", examId);

        final var exam = checkAccessForStudentExamGenerationAndLogAuditEvent(courseId, examId, Constants.GENERATE_STUDENT_EXAMS);
        // Reset existing student exams & participations in case they already exist
        examDeletionService.deleteStudentExamsAndExistingParticipationsForExam(exam.getId());

        List<StudentExam> studentExams = studentExamService.generateStudentExams(exam);
        // we need to break a cycle for the serialization
        breakCyclesForSerialization(studentExams);
        log.info("Generated {} student exams in {} for exam {}", studentExams.size(), formatDurationFrom(start), examId);
        return ResponseEntity.ok().body(studentExams);
    }

    @NotNull
    private Exam checkAccessForStudentExamGenerationAndLogAuditEvent(Long courseId, Long examId, String auditEventAction) {
        final Exam exam = examRepository.findByIdWithExamUsersExerciseGroupsAndExercisesElseThrow(examId);

        if (exam.isTestExam()) {
            throw new BadRequestAlertException("Generate student exams is only allowed for real exams", ENTITY_NAME, "generateStudentExamsOnlyForRealExams");
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
    @PostMapping("courses/{courseId}/exams/{examId}/generate-missing-student-exams")
    @EnforceAtLeastInstructor
    public ResponseEntity<List<StudentExam>> generateMissingStudentExams(@PathVariable Long courseId, @PathVariable Long examId) {
        long start = System.nanoTime();
        log.info("REST request to generate missing student exams for exam {}", examId);

        final var exam = checkAccessForStudentExamGenerationAndLogAuditEvent(courseId, examId, Constants.GENERATE_MISSING_STUDENT_EXAMS);
        List<StudentExam> studentExams = studentExamService.generateMissingStudentExams(exam);

        // we need to break a cycle for the serialization
        breakCyclesForSerialization(studentExams);

        log.info("Generated {} missing student exams in {} for exam {}", studentExams.size(), formatDurationFrom(start), examId);
        return ResponseEntity.ok().body(studentExams);
    }

    private static void breakCyclesForSerialization(List<StudentExam> studentExams) {
        for (StudentExam studentExam : studentExams) {
            studentExam.getExam().setExamUsers(null);
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
    @PostMapping("courses/{courseId}/exams/{examId}/student-exams/evaluate-quiz-exercises")
    @EnforceAtLeastInstructor
    public ResponseEntity<Integer> evaluateQuizExercises(@PathVariable Long courseId, @PathVariable Long examId) {
        log.info("REST request to evaluate quiz exercises of exam {}", examId);
        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);

        if (examDateService.getLatestIndividualExamEndDate(examId).isAfter(ZonedDateTime.now())) {
            throw new BadRequestAlertException("There are still exams running, quizzes can only be evaluated once all exams are finished.", ENTITY_NAME,
                    "evaluateQuizExercisesTooEarly");
        }
        var exam = examRepository.findWithExerciseGroupsAndExercisesByIdOrElseThrow(examId);
        if (exam.isTestExam()) {
            throw new BadRequestAlertException("Evaluate quiz exercises is only allowed for real exams", ENTITY_NAME, "evaluateQuizExercisesOnlyForRealExams");
        }

        Integer numOfEvaluatedExercises = examService.evaluateQuizExercises(exam);
        log.info("Evaluated {} quiz exercises of exam {}", numOfEvaluatedExercises, examId);
        return ResponseEntity.ok().body(numOfEvaluatedExercises);
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
    @PostMapping("courses/{courseId}/exams/{examId}/students")
    @EnforceAtLeastInstructor
    public ResponseEntity<List<ExamUserDTO>> addStudentsToExam(@PathVariable Long courseId, @PathVariable Long examId, @RequestBody List<ExamUserDTO> studentDtos) {
        log.debug("REST request to add {} as students to exam {}", studentDtos, examId);

        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);

        List<ExamUserDTO> notFoundStudentsDtos = examRegistrationService.registerStudentsForExam(courseId, examId, studentDtos);
        return ResponseEntity.ok().body(notFoundStudentsDtos);
    }

    /**
     * POST /courses/:courseId/exams/:examId/register-course-students : Add all users which are enrolled in the course to the exam so that the student can access the exam
     *
     * @param courseId the id of the course
     * @param examId   the id of the exam
     * @return empty ResponseEntity with status 200 (OK) or with status 404 (Not Found)
     */
    @PostMapping("courses/{courseId}/exams/{examId}/register-course-students")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> registerCourseStudents(@PathVariable Long courseId, @PathVariable Long examId) {
        // get all students enrolled in the course
        log.debug("REST request to add all students to exam {} with courseId {}", examId, courseId);
        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);
        var exam = examRepository.findByIdWithExamUsersElseThrow(examId);

        if (exam.isTestExam()) {
            throw new BadRequestAlertException("Registration of course students is only allowed for real exams", ENTITY_NAME, "AddCourseStudentsOnlyForRealExams");
        }

        examRegistrationService.addAllStudentsOfCourseToExam(courseId, exam);
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
    @DeleteMapping("courses/{courseId}/exams/{examId}/students/{studentLogin:" + Constants.LOGIN_REGEX + "}")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> removeStudentFromExam(@PathVariable Long courseId, @PathVariable Long examId, @PathVariable String studentLogin,
            @RequestParam(defaultValue = "false") boolean withParticipationsAndSubmission) {
        log.debug("REST request to remove {} as student from exam : {}", studentLogin, examId);

        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);

        Optional<User> optionalStudent = userRepository.findOneWithGroupsAndAuthoritiesByLogin(studentLogin);
        if (optionalStudent.isEmpty()) {
            throw new EntityNotFoundException("user", studentLogin);
        }

        var exam = examRepository.findWithExamUsersById(examId).orElseThrow(() -> new EntityNotFoundException("Exam", examId));

        if (exam.isTestExam()) {
            throw new BadRequestAlertException("Deletion of users is only allowed for real exams", ENTITY_NAME, "unregisterStudentsOnlyForRealExams");
        }

        examRegistrationService.unregisterStudentFromExam(exam, withParticipationsAndSubmission, optionalStudent.get());
        return ResponseEntity.ok().body(null);
    }

    /**
     * DELETE /courses/{courseId}/exams/{examId}/students :
     * Remove all students of the exam so that they cannot access the exam anymore.
     * Optionally, also deletes participations and submissions of all students in their student exams.
     *
     * @param courseId                        the id of the course
     * @param examId                          the id of the exam
     * @param withParticipationsAndSubmission request param deciding whether participations and submissions should also be deleted
     * @return empty ResponseEntity with status 200 (OK) or with status 404 (Not Found)
     */
    @DeleteMapping("courses/{courseId}/exams/{examId}/students")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> removeAllStudentsFromExam(@PathVariable Long courseId, @PathVariable Long examId,
            @RequestParam(defaultValue = "false") boolean withParticipationsAndSubmission) {
        log.debug("REST request to remove all students from exam {} with courseId {}", examId, courseId);

        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);

        var exam = examRepository.findWithExamUsersById(examId).orElseThrow(() -> new EntityNotFoundException("Exam", examId));

        if (exam.isTestExam()) {
            throw new BadRequestAlertException("Deregister students is only allowed for real exams", ENTITY_NAME, "unregisterAllOnlyForRealExams");
        }

        examRegistrationService.unregisterAllStudentFromExam(exam, withParticipationsAndSubmission);
        return ResponseEntity.ok().body(null);
    }

    /**
     * GET /courses/{courseId}/exams/{examId}/own-student-exam: Get the own student exam for the exam
     * Real Exams: StudentExam needs to be generated by an instructor
     * Test Exam: StudentExam can be self-created by the user
     * Note: The Access control is performed in the {@link ExamAccessService#getExamInCourseElseThrow(Long, Long)} to limit the DB-calls
     *
     * @param courseId the id of the course
     * @param examId   the id of the exam
     * @return the ResponseEntity with status 200 (OK) and with the found student exam (without exercises) as body
     */
    @GetMapping("courses/{courseId}/exams/{examId}/own-student-exam")
    @EnforceAtLeastStudent
    public ResponseEntity<StudentExam> getOwnStudentExam(@PathVariable Long courseId, @PathVariable Long examId) {
        log.debug("REST request to get exam {} for conduction", examId);
        StudentExam exam = examAccessService.getExamInCourseElseThrow(courseId, examId);
        exam.getUser().setVisibleRegistrationNumber();
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
    @PutMapping("courses/{courseId}/exams/{examId}/exercise-groups-order")
    @EnforceAtLeastEditor
    public ResponseEntity<List<ExerciseGroup>> updateOrderOfExerciseGroups(@PathVariable Long courseId, @PathVariable Long examId,
            @RequestBody List<ExerciseGroup> orderedExerciseGroups) {
        log.debug("REST request to update the order of exercise groups of exam : {}", examId);

        examAccessService.checkCourseAndExamAccessForEditorElseThrow(courseId, examId);

        Exam exam = examRepository.findByIdWithExerciseGroupsElseThrow(examId);

        // Ensure that exactly as many exercise groups have been received as are currently related to the exam
        if (orderedExerciseGroups.size() != exam.getExerciseGroups().size()) {
            throw new BadRequestAlertException("The number of exercise groups changed", ENTITY_NAME, "numberExerciseGroupsChanged");
        }

        // Ensure that all received exercise groups are already related to the exam
        for (ExerciseGroup exerciseGroup : orderedExerciseGroups) {
            if (!exam.getExerciseGroups().contains(exerciseGroup)) {
                throw new BadRequestAlertException("The exercise group is not related to the exam", ENTITY_NAME, "exerciseGroupNotRelatedToExam");
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
     * @return the ResponseEntity with status 200 (OK) and with the found exam as body or NotFound if it could not be
     *         determined
     */
    @GetMapping("courses/{courseId}/exams/{examId}/latest-end-date")
    @EnforceAtLeastTutor
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
    @GetMapping("courses/{courseId}/exams/{examId}/lockedSubmissions")
    @EnforceAtLeastInstructor
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
    @PutMapping("courses/{courseId}/exams/{examId}/archive")
    @EnforceAtLeastInstructor
    @FeatureToggle(Feature.Exports)
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
    @GetMapping("courses/{courseId}/exams/{examId}/download-archive")
    @EnforceAtLeastInstructor
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
        ContentDisposition contentDisposition = ContentDisposition.builder("attachment").filename(zipFile.getName()).build();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(contentDisposition);
        InputStreamResource resource = new InputStreamResource(new FileInputStream(zipFile));
        return ResponseEntity.ok().headers(headers).contentLength(zipFile.length()).contentType(MediaType.APPLICATION_OCTET_STREAM).header("filename", zipFile.getName())
                .body(resource);
    }

    /**
     * GET /courses/{courseId}/exams/{examId}/exercises-with-potential-plagiarism : Get all exercises with potential plagiarism for exam.
     * An exercise has potential plagiarism if Artemis supports plagiarism detection for it.
     * This applies to the exercise types TEXT, MODELING and PROGRAMMING.
     *
     * @param courseId the id of the course the exam belongs to
     * @param examId   the id of the exam for which to find exercises with potential plagiarism
     * @return the ResponseEntity with status 200 (OK) and with body the list of exercises with potential plagiarism
     */
    @GetMapping("courses/{courseId}/exams/{examId}/exercises-with-potential-plagiarism")
    @EnforceAtLeastInstructor
    public ResponseEntity<List<ExerciseForPlagiarismCasesOverviewDTO>> getAllExercisesWithPotentialPlagiarismForExam(@PathVariable long courseId, @PathVariable long examId) {
        log.debug("REST request to get all exercises with potential plagiarism cases for exam : {}", examId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        Set<Exercise> exercises = exerciseRepository.findAllExercisesWithPotentialPlagiarismByExamId(examId);
        return ResponseEntity.ok(exercises.stream().map(this::convertToDto).toList());
    }

    private ExerciseForPlagiarismCasesOverviewDTO convertToDto(Exercise exercise) {
        var courseDTO = new CourseWithIdDTO(exercise.getExerciseGroup().getExam().getCourse().getId());
        var examDTO = new ExamWithIdAndCourseDTO(exercise.getExerciseGroup().getExam().getId(), courseDTO);
        var exerciseGroupDTO = new ExerciseGroupWithIdAndExamDTO(exercise.getExerciseGroup().getId(), examDTO);
        return new ExerciseForPlagiarismCasesOverviewDTO(exercise.getId(), exercise.getTitle(), exercise.getType(), exerciseGroupDTO);
    }

    /**
     * GET /courses/{courseId}/exams/{examId}/suspicious-sessions : Get all exam sessions that are suspicious for exam.
     * For an explanation when a session is suspicious, see {@link ExamSessionService#retrieveAllSuspiciousExamSessionsByExamId(long, SuspiciousSessionsAnalysisOptions, Optional)}
     *
     * @param courseId                                                             the id of the course
     * @param examId                                                               the id of the exam
     * @param analyzeSessionsWithTheSameIp                                         whether to analyze for sessions with the same IP address that belong to different student exams
     * @param analyzeSessionsWithTheSameBrowserFingerprint                         whether to analyze sessions with the same browser fingerprint that belong to different student
     *                                                                                 exams
     * @param analyzeSessionsForTheSameStudentExamWithDifferentIpAddresses         whether to analyze sessions with different IP addresses that belong to the same student exam
     * @param analyzeSessionsForTheSameStudentExamWithDifferentBrowserFingerprints whether to analyze sessions with different browser fingerprints that belong to the same student
     *                                                                                 exam
     * @param analyzeSessionsIpOutsideOfRange                                      whether to analyze sessions with IP addresses outside a given subnet
     *                                                                                 If this is true, the subnet needs to be provided as a request parameter
     * @param ipSubnet                                                             the subnet to use for analyzing sessions with IP addresses outside the subnet (optional)
     * @return the ResponseEntity with status 200 (OK) and with body a set containing all tuples of exam sessions that are suspicious.
     */
    @GetMapping("courses/{courseId}/exams/{examId}/suspicious-sessions")
    @EnforceAtLeastInstructor
    public ResponseEntity<Set<SuspiciousExamSessionsDTO>> getAllSuspiciousExamSessions(@PathVariable long courseId, @PathVariable long examId,
            @RequestParam("differentStudentExamsSameIPAddress") boolean analyzeSessionsWithTheSameIp,
            @RequestParam("differentStudentExamsSameBrowserFingerprint") boolean analyzeSessionsWithTheSameBrowserFingerprint,
            @RequestParam("sameStudentExamDifferentIPAddresses") boolean analyzeSessionsForTheSameStudentExamWithDifferentIpAddresses,
            @RequestParam("sameStudentExamDifferentBrowserFingerprints") boolean analyzeSessionsForTheSameStudentExamWithDifferentBrowserFingerprints,
            @RequestParam("ipOutsideOfRange") boolean analyzeSessionsIpOutsideOfRange, @RequestParam(required = false) String ipSubnet) {
        log.debug("REST request to get all exam sessions that are suspicious for exam : {}", examId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        if (analyzeSessionsIpOutsideOfRange) {
            if (ipSubnet == null) {
                throw new BadRequestAlertException("If you want to analyze sessions with IP outside of range, you need to provide a subnet", ENTITY_NAME,
                        "missingLowerOrUpperBoundIp");
            }
        }
        SuspiciousSessionsAnalysisOptions options = new SuspiciousSessionsAnalysisOptions(analyzeSessionsWithTheSameIp, analyzeSessionsWithTheSameBrowserFingerprint,
                analyzeSessionsForTheSameStudentExamWithDifferentIpAddresses, analyzeSessionsForTheSameStudentExamWithDifferentBrowserFingerprints,
                analyzeSessionsIpOutsideOfRange);
        return ResponseEntity.ok(examSessionService.retrieveAllSuspiciousExamSessionsByExamId(examId, options, Optional.ofNullable(ipSubnet)));
    }
}
