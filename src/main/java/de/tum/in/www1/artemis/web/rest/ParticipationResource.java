package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.domain.enumeration.InitializationState.INITIALIZED;
import static java.time.ZonedDateTime.now;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.Principal;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJacksonValue;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.config.GuidedTourConfiguration;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.participation.*;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.domain.quiz.QuizSubmission;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService;
import de.tum.in.www1.artemis.service.feature.Feature;
import de.tum.in.www1.artemis.service.feature.FeatureToggle;
import de.tum.in.www1.artemis.service.feature.FeatureToggleService;
import de.tum.in.www1.artemis.service.messaging.InstanceMessageSendService;
import de.tum.in.www1.artemis.service.notifications.GroupNotificationService;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseParticipationService;
import de.tum.in.www1.artemis.service.scheduled.cache.quiz.QuizScheduleService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

/**
 * REST controller for managing Participation.
 */
@RestController
@RequestMapping("api/")
public class ParticipationResource {

    private final Logger log = LoggerFactory.getLogger(ParticipationResource.class);

    private static final String ENTITY_NAME = "participation";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final ParticipationService participationService;

    private final ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    private final QuizExerciseRepository quizExerciseRepository;

    private final ExerciseRepository exerciseRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authCheckService;

    private final Optional<ContinuousIntegrationService> continuousIntegrationService;

    private final UserRepository userRepository;

    private final AuditEventRepository auditEventRepository;

    private final GuidedTourConfiguration guidedTourConfiguration;

    private final TeamRepository teamRepository;

    private final FeatureToggleService featureToggleService;

    private final StudentParticipationRepository studentParticipationRepository;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final SubmissionRepository submissionRepository;

    private final ResultRepository resultRepository;

    private final ExerciseDateService exerciseDateService;

    private final InstanceMessageSendService instanceMessageSendService;

    private final QuizBatchService quizBatchService;

    private final QuizScheduleService quizScheduleService;

    private final SubmittedAnswerRepository submittedAnswerRepository;

    private final GroupNotificationService groupNotificationService;

    public ParticipationResource(ParticipationService participationService, ProgrammingExerciseParticipationService programmingExerciseParticipationService,
            CourseRepository courseRepository, QuizExerciseRepository quizExerciseRepository, ExerciseRepository exerciseRepository,
            ProgrammingExerciseRepository programmingExerciseRepository, AuthorizationCheckService authCheckService,
            Optional<ContinuousIntegrationService> continuousIntegrationService, UserRepository userRepository, StudentParticipationRepository studentParticipationRepository,
            AuditEventRepository auditEventRepository, GuidedTourConfiguration guidedTourConfiguration, TeamRepository teamRepository, FeatureToggleService featureToggleService,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, SubmissionRepository submissionRepository,
            ResultRepository resultRepository, ExerciseDateService exerciseDateService, InstanceMessageSendService instanceMessageSendService, QuizBatchService quizBatchService,
            QuizScheduleService quizScheduleService, SubmittedAnswerRepository submittedAnswerRepository, GroupNotificationService groupNotificationService) {
        this.participationService = participationService;
        this.programmingExerciseParticipationService = programmingExerciseParticipationService;
        this.quizExerciseRepository = quizExerciseRepository;
        this.courseRepository = courseRepository;
        this.exerciseRepository = exerciseRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.authCheckService = authCheckService;
        this.continuousIntegrationService = continuousIntegrationService;
        this.userRepository = userRepository;
        this.auditEventRepository = auditEventRepository;
        this.guidedTourConfiguration = guidedTourConfiguration;
        this.teamRepository = teamRepository;
        this.featureToggleService = featureToggleService;
        this.studentParticipationRepository = studentParticipationRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.submissionRepository = submissionRepository;
        this.resultRepository = resultRepository;
        this.exerciseDateService = exerciseDateService;
        this.instanceMessageSendService = instanceMessageSendService;
        this.quizBatchService = quizBatchService;
        this.quizScheduleService = quizScheduleService;
        this.submittedAnswerRepository = submittedAnswerRepository;
        this.groupNotificationService = groupNotificationService;
    }

    /**
     * POST /exercises/:exerciseId/participations : start the "participationId" exercise for the current user.
     *
     * @param exerciseId the participationId of the exercise for which to init a participation
     * @return the ResponseEntity with status 201 (Created) and the participation within the body, or with status 404 (Not Found)
     * @throws URISyntaxException If the URI for the created participation could not be created
     */
    @PostMapping("exercises/{exerciseId}/participations")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Participation> startParticipation(@PathVariable Long exerciseId) throws URISyntaxException {
        log.debug("REST request to start Exercise : {}", exerciseId);
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, exercise, user);

        // Don't allow student to start before the start and release date
        ZonedDateTime releaseOrStartDate = exercise.getParticipationStartDate();
        if (releaseOrStartDate != null && releaseOrStartDate.isAfter(now())) {
            if (authCheckService.isOnlyStudentInCourse(exercise.getCourseViaExerciseGroupOrCourseMember(), user)) {
                throw new AccessForbiddenException("Students cannot start an exercise before the release date");
            }
        }

        // Also don't allow participations if the feature is disabled
        if (exercise instanceof ProgrammingExercise) {
            // fetch additional objects needed for the startExercise method below
            var programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exercise.getId());
            if (!featureToggleService.isFeatureEnabled(Feature.ProgrammingExercises) || !isAllowedToParticipateInProgrammingExercise(programmingExercise, null)) {
                throw new AccessForbiddenException("Not allowed");
            }
            exercise = programmingExercise;
        }

        // if this is a team-based exercise, set the participant to the team that the user belongs to
        Participant participant = user;
        if (exercise.isTeamMode()) {
            participant = teamRepository.findOneByExerciseIdAndUserId(exercise.getId(), user.getId())
                    .orElseThrow(() -> new BadRequestAlertException("Team exercise cannot be started without assigned team.", "participation", "cannotStart"));
        }
        StudentParticipation participation = participationService.startExercise(exercise, participant, true);

        if (exercise.isExamExercise() && exercise instanceof ProgrammingExercise) {
            // TODO: this programming exercise was started during an exam (the instructor did not invoke "prepare exercise start" before the exam or it failed in this case)
            // 1) check that now is between exam start and individual exam end
            // 2) create a scheduled lock operation (see ProgrammingExerciseScheduleService)
            // var task = programmingExerciseScheduleService.lockStudentRepository(participation);
            // 3) add the task to the schedule service
            // scheduleService.scheduleTask(exercise, ExerciseLifecycle.DUE, task);
        }

        // remove sensitive information before sending participation to the client
        participation.getExercise().filterSensitiveInformation();
        return ResponseEntity.created(new URI("/api/participations/" + participation.getId())).body(participation);
    }

    /**
     * POST /exercises/:exerciseId/participations : start the "participationId" exercise for the current user.
     *
     * @param exerciseId the participationId of the exercise for which to init a participation
     * @param useGradedParticipation a flag that indicates that the student wants to use their graded participation as baseline for the new repo
     * @return the ResponseEntity with status 201 (Created) and the participation within the body, or with status 404 (Not Found)
     * @throws URISyntaxException If the URI for the created participation could not be created
     */
    @PostMapping("exercises/{exerciseId}/participations/practice")
    @PreAuthorize("hasRole('USER')")
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<Participation> startPracticeParticipation(@PathVariable Long exerciseId,
            @RequestParam(value = "useGradedParticipation", defaultValue = "false") boolean useGradedParticipation) throws URISyntaxException {
        log.debug("REST request to practice Exercise : {}", exerciseId);
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        Optional<StudentParticipation> optionalGradedStudentParticipation = participationService.findOneByExerciseAndParticipantAnyStateAndTestRun(exercise, user, false);

        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, exercise, user);
        if (exercise.isExamExercise()) {
            throw new BadRequestAlertException("The practice mode cannot be used in an exam", ENTITY_NAME, "noPracticeModeInExam");
        }
        if (exercise.isTeamMode()) {
            throw new BadRequestAlertException("The practice mode is not yet supported for team exercises", ENTITY_NAME, "noPracticeModeForTeams");
        }
        if (!(exercise instanceof ProgrammingExercise)) {
            throw new BadRequestAlertException("The practice can only be used for programming exercises", ENTITY_NAME, "practiceModeOnlyForProgramming");
        }
        if (exercise.getDueDate() == null || now().isBefore(exercise.getDueDate())
                || (optionalGradedStudentParticipation.isPresent() && exerciseDateService.isBeforeDueDate(optionalGradedStudentParticipation.get()))) {
            throw new AccessForbiddenException("The practice mode can only be started after the due date");
        }
        if (useGradedParticipation && optionalGradedStudentParticipation.isEmpty()) {
            throw new BadRequestAlertException("Tried to start the practice mode based on the graded participation, but there is no graded participation", ENTITY_NAME,
                    "practiceModeNoGradedParticipation");
        }

        StudentParticipation participation = participationService.startPracticeMode(exercise, user, optionalGradedStudentParticipation, useGradedParticipation);

        // remove sensitive information before sending participation to the client
        participation.getExercise().filterSensitiveInformation();
        return ResponseEntity.created(new URI("/api/participations/" + participation.getId())).body(participation);
    }

    /**
     * PUT exercises/:exerciseId/resume-programming-participation: resume the participation of the current user in the given programming exercise
     *
     * @param exerciseId of the exercise for which to resume participation
     * @param participationId of the participation that should be resumed
     * @param principal  current user principal
     * @return ResponseEntity with status 200 (OK) and with updated participation as a body, or with status 500 (Internal Server Error)
     */
    @PutMapping("exercises/{exerciseId}/resume-programming-participation/{participationId}")
    @PreAuthorize("hasRole('USER')")
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<ProgrammingExerciseStudentParticipation> resumeParticipation(@PathVariable Long exerciseId, @PathVariable Long participationId, Principal principal) {
        log.debug("REST request to resume Exercise : {}", exerciseId);
        var programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exerciseId);
        var participation = programmingExerciseStudentParticipationRepository.findByIdElseThrow(participationId);

        if (!participation.isOwnedBy(principal.getName())) {
            throw new AccessForbiddenException("You are not the user of this participation");
        }

        // explicitly set the exercise here to make sure that the templateParticipation and solutionParticipation are initialized in case they should be used again
        participation.setProgrammingExercise(programmingExercise);

        User user = userRepository.getUserWithGroupsAndAuthorities();
        checkAccessPermissionOwner(participation, user);
        if (!isAllowedToParticipateInProgrammingExercise(programmingExercise, participation)) {
            throw new AccessForbiddenException("You are not allowed to resume that participation.");
        }

        participation = participationService.resumeProgrammingExercise(participation);
        // Note: in this case we might need an empty commit to make sure the build plan works correctly for subsequent student commits
        continuousIntegrationService.get().performEmptySetupCommit(participation);
        addLatestResultToParticipation(participation);
        participation.getExercise().filterSensitiveInformation();
        return ResponseEntity.ok().body(participation);
    }

    /**
     * PUT exercises/:exerciseId/request-feedback: Requests manual feedback for the latest participation
     *
     * @param exerciseId of the exercise for which to resume participation
     * @param principal  current user principal
     * @return ResponseEntity with status 200 (OK)
     */
    @PutMapping("exercises/{exerciseId}/request-feedback")
    @PreAuthorize("hasRole('USER')")
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<ProgrammingExerciseStudentParticipation> requestFeedback(@PathVariable Long exerciseId, Principal principal) {
        log.debug("REST request for feedback request: {}", exerciseId);
        var programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exerciseId);
        var participation = programmingExerciseParticipationService.findStudentParticipationByExerciseAndStudentId(programmingExercise, principal.getName());
        User user = userRepository.getUserWithGroupsAndAuthorities();

        checkAccessPermissionOwner(participation, user);
        programmingExercise.validateManualFeedbackSettings();

        var studentParticipation = studentParticipationRepository.findByIdWithResultsElseThrow(participation.getId());
        var result = studentParticipation.findLatestLegalResult();
        if (Objects.isNull(result) || result.getScore() < 100) {
            throw new BadRequestAlertException("User has not reached the conditions to submit a feedback request", "participation", "preconditions not met");
        }

        var currentDate = now();
        var participationIndividualDueDate = participation.getIndividualDueDate();
        if (Objects.nonNull(participationIndividualDueDate) && currentDate.isAfter(participationIndividualDueDate)) {
            throw new BadRequestAlertException("Request has already been sent", "participation", "already sent");
        }

        // The participations due date is a flag showing that a feedback request is sent
        participation.setIndividualDueDate(currentDate);

        participation = programmingExerciseStudentParticipationRepository.save(participation);
        programmingExerciseParticipationService.lockStudentRepository(programmingExercise, participation);

        // Set all past results to automatic to reset earlier feedback request assessments
        var participationResults = studentParticipation.getResults();
        participationResults.forEach(participationResult -> {
            participationResult.setAssessmentType(AssessmentType.AUTOMATIC);
            participationResult.setAssessor(null);
            participationResult.setRated(false);
        });
        resultRepository.saveAll(participationResults);

        groupNotificationService.notifyTutorGroupAboutNewFeedbackRequest(programmingExercise);

        return ResponseEntity.ok().body(participation);
    }

    /**
     * Checks if the student is currently allowed to participate in the course exercise using this participation
     * @param programmingExercise the exercise where the user wants to participate
     * @param participation       the participation, may be null in case there is none
     * @return a boolean indicating if the user may participate
     */
    private boolean isAllowedToParticipateInProgrammingExercise(ProgrammingExercise programmingExercise, @Nullable StudentParticipation participation) {
        if (participation != null) {
            // only regular participation before the due date; only practice run afterwards
            return participation.isTestRun() == exerciseDateService.isAfterDueDate(participation);
        }
        else {
            return programmingExercise.getDueDate() == null || now().isBefore(programmingExercise.getDueDate());
        }
    }

    /**
     * This makes sure the client can display the latest result immediately after loading this participation
     *
     * @param participation The participation to which the latest result should get added
     */
    private void addLatestResultToParticipation(StudentParticipation participation) {
        // Load results of participation as they are not contained in the current object
        participation = studentParticipationRepository.findByIdWithResultsElseThrow(participation.getId());

        Result result = participation.findLatestLegalResult();
        if (result != null) {
            participation.setResults(Set.of(result));
        }
    }

    /**
     * PUT /participations : Updates an existing participation.
     *
     * @param exerciseId    the id of the exercise, the participation belongs to
     * @param participation the participation to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated participation, or with status 400 (Bad Request) if the participation is not valid, or with status
     * 500 (Internal Server Error) if the participation couldn't be updated
     */
    @PutMapping("exercises/{exerciseId}/participations")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<Participation> updateParticipation(@PathVariable long exerciseId, @RequestBody StudentParticipation participation) {
        log.debug("REST request to update Participation : {}", participation);
        if (participation.getId() == null) {
            throw new BadRequestAlertException("The participation object needs to have an id to be changed", ENTITY_NAME, "idmissing");
        }
        if (participation.getExercise() == null || participation.getExercise().getId() == null) {
            throw new BadRequestAlertException("The participation needs to be connected to an exercise", ENTITY_NAME, "exerciseidmissing");
        }
        if (participation.getExercise().getId() != exerciseId) {
            throw new ConflictException("The exercise of the participation does not match the exercise id in the URL", ENTITY_NAME, "noidmatch");
        }
        var originalParticipation = studentParticipationRepository.findByIdElseThrow(participation.getId());
        var user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, originalParticipation.getExercise(), null);
        if (participation.getPresentationScore() == null || participation.getPresentationScore() < 0) {
            participation.setPresentationScore(0);
        }
        if (participation.getPresentationScore() > 1) {
            participation.setPresentationScore(1);
        }
        StudentParticipation currentParticipation = studentParticipationRepository.findByIdElseThrow(participation.getId());
        if (currentParticipation.getPresentationScore() != null && currentParticipation.getPresentationScore() > participation.getPresentationScore()) {
            log.info("{} removed the presentation score of {} for exercise with participationId {}", user.getLogin(), originalParticipation.getParticipantIdentifier(),
                    originalParticipation.getExercise().getId());
        }

        Participation updatedParticipation = studentParticipationRepository.saveAndFlush(participation);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, participation.getParticipant().getName()))
                .body(updatedParticipation);
    }

    /**
     * PUT /participations/update-individual-due-date : Updates the individual due dates for the given already existing participations.
     * <p>
     * If the exercise is a programming exercise, also triggers a scheduling
     * update for the participations where the individual due date has changed.
     *
     * @param exerciseId     of the exercise the participations belong to.
     * @param participations for which the individual due date should be updated.
     * @return all participations where the individual due date actually changed.
     */
    @PutMapping("exercises/{exerciseId}/participations/update-individual-due-date")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<List<StudentParticipation>> updateParticipationDueDates(@PathVariable long exerciseId, @RequestBody List<StudentParticipation> participations) {
        final boolean anyInvalidExerciseId = participations.stream()
                .anyMatch(participation -> participation.getExercise() == null || participation.getExercise().getId() == null || exerciseId != participation.getExercise().getId());
        if (anyInvalidExerciseId) {
            throw new BadRequestAlertException("The participation needs to be connected to an exercise", ENTITY_NAME, "exerciseidmissing");
        }

        final Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, exercise, null);

        if (exercise.isExamExercise()) {
            throw new BadRequestAlertException("Cannot set individual due dates for exam exercises", ENTITY_NAME, "examexercise");
        }
        if (exercise instanceof QuizExercise) {
            throw new BadRequestAlertException("Cannot set individual due dates for quiz exercises", ENTITY_NAME, "quizexercise");
        }

        final List<StudentParticipation> changedParticipations = participationService.updateIndividualDueDates(exercise, participations);
        final List<StudentParticipation> updatedParticipations = studentParticipationRepository.saveAllAndFlush(changedParticipations);

        if (!updatedParticipations.isEmpty() && exercise instanceof ProgrammingExercise programmingExercise) {
            log.info("Updating scheduling for exercise {} (id {}) due to changed individual due dates.", exercise.getTitle(), exercise.getId());
            instanceMessageSendService.sendProgrammingExerciseSchedule(programmingExercise.getId());

            // when changing the individual due date after the regular due date, the repository might already have been locked
            updatedParticipations.stream().filter(exerciseDateService::isBeforeDueDate).forEach(
                    participation -> programmingExerciseParticipationService.unlockStudentRepository(programmingExercise, (ProgrammingExerciseStudentParticipation) participation));
            // the new due date may be in the past, students should no longer be able to make any changes
            updatedParticipations.stream().filter(exerciseDateService::isAfterDueDate).forEach(
                    participation -> programmingExerciseParticipationService.lockStudentRepository(programmingExercise, (ProgrammingExerciseStudentParticipation) participation));
        }

        return ResponseEntity.ok().body(updatedParticipations);
    }

    /**
     * GET /exercises/:exerciseId/participations : get all the participations for an exercise
     *
     * @param exerciseId       The participationId of the exercise
     * @param withLatestResult Whether the {@link Result results} for the participations should also be fetched
     * @return A list of all participations for the exercise
     */
    @GetMapping("exercises/{exerciseId}/participations")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<Set<StudentParticipation>> getAllParticipationsForExercise(@PathVariable Long exerciseId,
            @RequestParam(defaultValue = "false") boolean withLatestResult) {
        log.debug("REST request to get all Participations for Exercise {}", exerciseId);
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, exercise, null);
        Set<StudentParticipation> participations;
        if (withLatestResult) {
            participations = studentParticipationRepository.findByExerciseIdWithLatestResult(exerciseId);
        }
        else {
            participations = studentParticipationRepository.findByExerciseId(exerciseId);
        }
        participations = participations.stream().filter(participation -> participation.getParticipant() != null).peek(participation -> {
            // remove unnecessary data to reduce response size
            participation.setExercise(null);
        }).collect(Collectors.toSet());

        Map<Long, Integer> submissionCountMap = studentParticipationRepository.countSubmissionsPerParticipationByExerciseIdAsMap(exerciseId);
        participations.forEach(participation -> participation.setSubmissionCount(submissionCountMap.get(participation.getId())));

        return ResponseEntity.ok(participations);
    }

    /**
     * GET /courses/:courseId/participations : get all the participations for a course
     *
     * @param courseId The participationId of the course
     * @return A list of all participations for the given course
     */
    @GetMapping("courses/{courseId}/participations")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<List<StudentParticipation>> getAllParticipationsForCourse(@PathVariable Long courseId) {
        long start = System.currentTimeMillis();
        log.debug("REST request to get all Participations for Course {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        List<StudentParticipation> participations = studentParticipationRepository.findByCourseIdWithRelevantResult(courseId);
        int resultCount = 0;
        for (StudentParticipation participation : participations) {
            // make sure the registration number is explicitly shown in the client
            participation.getStudents().forEach(student -> student.setVisibleRegistrationNumber(student.getRegistrationNumber()));
            // we only need participationId, title, dates and max points
            // remove unnecessary elements
            Exercise exercise = participation.getExercise();
            exercise.setCourse(null);
            exercise.setStudentParticipations(null);
            exercise.setTutorParticipations(null);
            exercise.setExampleSubmissions(null);
            exercise.setAttachments(null);
            exercise.setCategories(null);
            exercise.setProblemStatement(null);
            exercise.setPosts(null);
            exercise.setGradingInstructions(null);
            exercise.setDifficulty(null);
            exercise.setMode(null);
            if (exercise instanceof ProgrammingExercise programmingExercise) {
                programmingExercise.setSolutionParticipation(null);
                programmingExercise.setTemplateParticipation(null);
                programmingExercise.setTestRepositoryUrl(null);
                programmingExercise.setShortName(null);
                programmingExercise.setPublishBuildPlanUrl(null);
                programmingExercise.setProgrammingLanguage(null);
                programmingExercise.setPackageName(null);
                programmingExercise.setAllowOnlineEditor(null);
            }
            else if (exercise instanceof QuizExercise quizExercise) {
                quizExercise.setQuizQuestions(null);
                quizExercise.setQuizPointStatistic(null);
            }
            else if (exercise instanceof TextExercise textExercise) {
                textExercise.setExampleSolution(null);
            }
            else if (exercise instanceof ModelingExercise modelingExercise) {
                modelingExercise.setExampleSolutionModel(null);
                modelingExercise.setExampleSolutionExplanation(null);
            }
            resultCount += participation.getResults().size();
        }
        long end = System.currentTimeMillis();
        log.info("Found {} participations with {} results in {}ms", participations.size(), resultCount, end - start);
        return ResponseEntity.ok().body(participations);
    }

    /**
     * GET /participations/:participationId : get the participation for the given "participationId" including its latest result.
     *
     * @param participationId the participationId of the participation to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the participation, or with status 404 (Not Found)
     */
    @GetMapping("participations/{participationId}/withLatestResult")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<StudentParticipation> getParticipationWithLatestResult(@PathVariable Long participationId) {
        log.debug("REST request to get Participation : {}", participationId);
        StudentParticipation participation = studentParticipationRepository.findByIdWithResultsElseThrow(participationId);
        authCheckService.canAccessParticipation(participation);
        Result result = participation.getExercise().findLatestResultWithCompletionDate(participation);
        Set<Result> results = new HashSet<>();
        if (result != null) {
            results.add(result);
        }
        participation.setResults(results);
        return new ResponseEntity<>(participation, HttpStatus.OK);
    }

    /**
     * GET /participations/:participationId : get the participation for the given "participationId".
     *
     * @param participationId the participationId of the participation to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the participation, or with status 404 (Not Found)
     */
    @GetMapping("participations/{participationId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<StudentParticipation> getParticipationForCurrentUser(@PathVariable Long participationId) {
        log.debug("REST request to get participation : {}", participationId);
        StudentParticipation participation = studentParticipationRepository.findByIdElseThrow(participationId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        checkAccessPermissionOwner(participation, user);
        return new ResponseEntity<>(participation, HttpStatus.OK);
    }

    /**
     * Retrieves the latest build artifact of a given programming exercise participation
     *
     * @param participationId The participationId of the participation
     * @return The latest build artifact (JAR/WAR) for the participation
     */
    @GetMapping("participations/{participationId}/buildArtifact")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<byte[]> getParticipationBuildArtifact(@PathVariable Long participationId) {
        log.debug("REST request to get Participation build artifact: {}", participationId);
        ProgrammingExerciseStudentParticipation participation = programmingExerciseStudentParticipationRepository.findByIdElseThrow(participationId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        checkAccessPermissionOwner(participation, user);

        return continuousIntegrationService.get().retrieveLatestArtifact(participation);
    }

    /**
     * GET /exercises/:exerciseId/participation: get the user's participation for a specific exercise. Please note: 'courseId' is only included in the call for
     * API consistency, it is not actually used
     *
     * @param exerciseId the participationId of the exercise for which to retrieve the participation
     * @param principal  The principal in form of the user's identity
     * @return the ResponseEntity with status 200 (OK) and with body the participation, or with status 404 (Not Found)
     */
    @GetMapping("exercises/{exerciseId}/participation")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<MappingJacksonValue> getParticipationForCurrentUser(@PathVariable Long exerciseId, Principal principal) {
        log.debug("REST request to get Participation for Exercise : {}", exerciseId);
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        // if exercise is not yet released to the students they should not have any access to it
        if (!authCheckService.isAllowedToSeeExercise(exercise, user)) {
            throw new AccessForbiddenException();
        }
        MappingJacksonValue response;
        if (exercise instanceof QuizExercise quizExercise) {
            response = participationForQuizExercise(quizExercise, user);
        }
        else {
            Optional<StudentParticipation> optionalParticipation = participationService.findOneByExerciseAndStudentLoginAnyStateWithEagerResults(exercise, principal.getName());
            if (optionalParticipation.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.FAILED_DEPENDENCY, "No participation found for " + principal.getName() + " in exercise " + exerciseId);
            }
            Participation participation = optionalParticipation.get();
            response = new MappingJacksonValue(participation);
        }
        if (response == null) {
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        }
        else {
            return new ResponseEntity<>(response, HttpStatus.OK);
        }
    }

    @Nullable
    private MappingJacksonValue participationForQuizExercise(QuizExercise quizExercise, User user) {
        // 1st case the quiz has already ended
        if (quizExercise.isQuizEnded()) {
            // When the quiz has ended, students reload the page (or navigate again into it), but the participation (+ submission + result) has not yet been stored in the database
            // (because for 1500 students this can take up to 60s), we would get a lot of errors here -> wait until all submissions are process and available in the DB
            // TODO: Handle this case here properly and show a message to the user: please wait while the quiz results are being processed (show a progress animation in the client)
            // Edge case: if students got their participation via before all processing was done and the reload their results will be gone until processing is finished
            if (!quizScheduleService.finishedProcessing(quizExercise.getId())) {
                return null;
            }

            // quiz has ended => get participation from database and add full quizExercise
            quizExercise = quizExerciseRepository.findByIdWithQuestionsElseThrow(quizExercise.getId());
            StudentParticipation participation = participationForQuizWithResult(quizExercise, user.getLogin());
            if (participation == null) {
                return null;
            }
            // avoid problems due to bidirectional associations between submission and result during serialization
            for (Result result : participation.getResults()) {
                if (result.getSubmission() != null) {
                    result.getSubmission().setResults(null);
                    result.getSubmission().setParticipation(null);
                }
            }
            return new MappingJacksonValue(participation);
        }
        quizExercise.setQuizBatches(null); // not available here
        var quizBatch = quizBatchService.getQuizBatchForStudentByLogin(quizExercise, user.getLogin());

        if (quizBatch.isPresent() && quizBatch.get().isSubmissionAllowed()) {
            // Quiz is active => construct Participation from
            // filtered quizExercise and submission from HashMap
            quizExercise = quizExerciseRepository.findByIdWithQuestionsElseThrow(quizExercise.getId());
            quizExercise.setQuizBatches(quizBatch.stream().collect(Collectors.toSet()));
            quizExercise.filterForStudentsDuringQuiz();
            StudentParticipation participation = participationForQuizWithResult(quizExercise, user.getLogin());
            // set view
            var view = quizExercise.viewForStudentsInQuizExercise(quizBatch.get());
            MappingJacksonValue value = new MappingJacksonValue(participation);
            value.setSerializationView(view);
            return value;
        }
        else {
            // Quiz hasn't started yet => no Result, only quizExercise without questions
            // OR: the quiz batch is already done and the submission has not yet been processed =>
            // TODO: handle this case, but the issue will go away on its own by just waiting
            quizExercise.filterSensitiveInformation();
            quizExercise.setQuizBatches(quizBatch.stream().collect(Collectors.toSet()));
            if (quizExercise.getAllowedNumberOfAttempts() != null) {
                var attempts = submissionRepository.countByExerciseIdAndStudentLogin(quizExercise.getId(), user.getLogin());
                quizExercise.setRemainingNumberOfAttempts(quizExercise.getAllowedNumberOfAttempts() - attempts);
            }
            StudentParticipation participation = new StudentParticipation().exercise(quizExercise);
            return new MappingJacksonValue(participation);
        }

    }

    /**
     * DELETE /participations/:participationId : delete the "participationId" participation. This only works for student participations - other participations should not be deleted here!
     *
     * @param participationId  the participationId of the participation to delete
     * @param deleteBuildPlan  True, if the build plan should also get deleted
     * @param deleteRepository True, if the repository should also get deleted
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("participations/{participationId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> deleteParticipation(@PathVariable Long participationId, @RequestParam(defaultValue = "false") boolean deleteBuildPlan,
            @RequestParam(defaultValue = "false") boolean deleteRepository) {
        StudentParticipation participation = studentParticipationRepository.findByIdElseThrow(participationId);
        if (participation instanceof ProgrammingExerciseParticipation && !featureToggleService.isFeatureEnabled(Feature.ProgrammingExercises)) {
            throw new AccessForbiddenException("Programming Exercise Feature is disabled.");
        }
        User user = userRepository.getUserWithGroupsAndAuthorities();
        checkAccessPermissionAtLeastInstructor(participation, user);
        return deleteParticipation(participation, deleteBuildPlan, deleteRepository, user);
    }

    /**
     * DELETE guided-tour/participations/:participationId : delete the "participationId" participation of student participations for guided tutorials (e.g. when restarting a tutorial)
     * Please note: all users can delete their own participation when it belongs to a guided tutorial
     *
     * @param participationId  the participationId of the participation to delete
     * @param deleteBuildPlan  True, if the build plan should also get deleted
     * @param deleteRepository True, if the repository should also get deleted
     * @return the ResponseEntity with status 200 (OK) or 403 (FORBIDDEN)
     */
    @DeleteMapping("guided-tour/participations/{participationId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> deleteParticipationForGuidedTour(@PathVariable Long participationId, @RequestParam(defaultValue = "false") boolean deleteBuildPlan,
            @RequestParam(defaultValue = "false") boolean deleteRepository) {
        StudentParticipation participation = studentParticipationRepository.findByIdElseThrow(participationId);
        if (participation instanceof ProgrammingExerciseParticipation && !featureToggleService.isFeatureEnabled(Feature.ProgrammingExercises)) {
            throw new AccessForbiddenException("Programming Exercise Feature is disabled.");
        }

        User user = userRepository.getUserWithGroupsAndAuthorities();

        // Allow all users to delete their own StudentParticipations if it's for a tutorial
        if (participation.isOwnedBy(user)) {
            checkAccessPermissionAtLeastStudent(participation, user);
            guidedTourConfiguration.checkExerciseForTutorialElseThrow(participation.getExercise());
        }
        else {
            throw new AccessForbiddenException("Users are not allowed to delete their own participation.");
        }

        return deleteParticipation(participation, deleteBuildPlan, deleteRepository, user);
    }

    /**
     * delete the participation, potentially including build plan and repository and log the event in the database audit
     *
     * @param participation    the participation to be deleted
     * @param deleteBuildPlan  whether the build plan should be deleted as well, only relevant for programming exercises
     * @param deleteRepository whether the repository should be deleted as well, only relevant for programming exercises
     * @param user             the currently logged-in user who initiated the delete operation
     * @return the response to the client
     */
    @NotNull
    private ResponseEntity<Void> deleteParticipation(StudentParticipation participation, boolean deleteBuildPlan, boolean deleteRepository, User user) {
        String name = participation.getParticipantName();
        var logMessage = "Delete Participation " + participation.getId() + " of exercise " + participation.getExercise().getTitle() + " for " + name + ", deleteBuildPlan: "
                + deleteBuildPlan + ", deleteRepository: " + deleteRepository + " by " + user.getLogin();
        var auditEvent = new AuditEvent(user.getLogin(), Constants.DELETE_PARTICIPATION, logMessage);
        auditEventRepository.add(auditEvent);
        log.info(logMessage);
        participationService.delete(participation.getId(), deleteBuildPlan, deleteRepository, true);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, "participation", name)).build();
    }

    /**
     * DELETE /participations/:participationId : remove the build plan of the ProgrammingExerciseStudentParticipation of the "participationId".
     * This only works for programming exercises.
     *
     * @param participationId the participationId of the ProgrammingExerciseStudentParticipation for which the build plan should be removed
     * @param principal       The identity of the user accessing this resource
     * @return the ResponseEntity with status 200 (OK)
     */
    @PutMapping("participations/{participationId}/cleanupBuildPlan")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<Participation> cleanupBuildPlan(@PathVariable Long participationId, Principal principal) {
        ProgrammingExerciseStudentParticipation participation = (ProgrammingExerciseStudentParticipation) studentParticipationRepository.findByIdElseThrow(participationId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        checkAccessPermissionAtLeastInstructor(participation, user);
        log.info("Clean up participation with build plan {} by {}", participation.getBuildPlanId(), principal.getName());
        participationService.cleanupBuildPlan(participation);
        return ResponseEntity.ok().body(participation);
    }

    private void checkAccessPermissionAtLeastStudent(StudentParticipation participation, User user) {
        Course course = findCourseFromParticipation(participation);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);
    }

    private void checkAccessPermissionAtLeastInstructor(StudentParticipation participation, User user) {
        Course course = findCourseFromParticipation(participation);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, user);
    }

    private void checkAccessPermissionOwner(StudentParticipation participation, User user) {
        if (!authCheckService.isOwnerOfParticipation(participation)) {
            Course course = findCourseFromParticipation(participation);
            authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, user);
        }
    }

    private Course findCourseFromParticipation(StudentParticipation participation) {
        if (participation.getExercise() != null && participation.getExercise().getCourseViaExerciseGroupOrCourseMember() != null) {
            return participation.getExercise().getCourseViaExerciseGroupOrCourseMember();
        }

        return studentParticipationRepository.findByIdElseThrow(participation.getId()).getExercise().getCourseViaExerciseGroupOrCourseMember();
    }

    /**
     * fetches all submissions of a specific participation
     *
     * @param participationId the id of the participation
     * @return all submissions that belong to the participation
     */
    @GetMapping("participations/{participationId}/submissions")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<List<Submission>> getSubmissionsOfParticipation(@PathVariable Long participationId) {
        StudentParticipation participation = studentParticipationRepository.findByIdElseThrow(participationId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        checkAccessPermissionAtLeastInstructor(participation, user);
        List<Submission> submissions = submissionRepository.findAllWithResultsAndAssessorByParticipationId(participationId);
        return ResponseEntity.ok(submissions);
    }

    /**
     * Get a participation for the given quiz and username.
     * If the quiz hasn't ended, participation is constructed from cached submission.
     * If the quiz has ended, we first look in the database for the participation and construct one if none was found
     *
     * @param quizExercise the quiz exercise to attach to the participation
     * @param username     the username of the user that the participation belongs to
     * @return the found or created participation with a result
     */
    // TODO: we should move this method (and others related to quizzes) into a QuizParticipationService (or similar) to make this resource independent of specific quiz exercise
    // functionality
    private StudentParticipation participationForQuizWithResult(QuizExercise quizExercise, String username) {
        if (quizExercise.isQuizEnded()) {
            // try getting participation from database
            Optional<StudentParticipation> optionalParticipation = participationService.findOneByExerciseAndStudentLoginAnyState(quizExercise, username);

            if (optionalParticipation.isEmpty()) {
                log.error("Participation in quiz {} not found for user {}", quizExercise.getTitle(), username);
                // TODO properly handle this case
                return null;
            }
            StudentParticipation participation = optionalParticipation.get();
            // add exercise
            participation.setExercise(quizExercise);
            participation.setResults(new HashSet<>());

            // add the appropriate result
            Result result = resultRepository.findFirstByParticipationIdAndRatedOrderByCompletionDateDesc(participation.getId(), true).orElse(null);
            if (result != null) {
                // find the submitted answers (they are NOT loaded eagerly anymore)
                var quizSubmission = (QuizSubmission) result.getSubmission();
                var submittedAnswers = submittedAnswerRepository.findBySubmission(quizSubmission);
                quizSubmission.setSubmittedAnswers(submittedAnswers);
                participation.addResult(result);
            }
            return participation;
        }

        // Look for Participation in ParticipationHashMap first
        StudentParticipation participation = quizScheduleService.getParticipation(quizExercise.getId(), username);
        if (participation != null) {
            return participation;
        }

        // get submission from HashMap
        QuizSubmission quizSubmission = quizScheduleService.getQuizSubmission(quizExercise.getId(), username);

        // construct result
        Result result = new Result().submission(quizSubmission);

        // construct participation
        participation = new StudentParticipation();
        participation.setInitializationState(INITIALIZED);
        participation.setExercise(quizExercise);
        participation.addResult(result);
        return participation;
    }
}
