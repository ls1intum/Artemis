package de.tum.cit.aet.artemis.exercise.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static java.time.ZonedDateTime.now;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.Principal;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import org.apache.velocity.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJacksonValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.GradingScale;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.assessment.service.GradingScaleService;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.config.GuidedTourConfiguration;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastStudentInExercise;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggle;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import de.tum.cit.aet.artemis.core.service.messaging.InstanceMessageSendService;
import de.tum.cit.aet.artemis.core.util.HeaderUtil;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participant;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDateService;
import de.tum.cit.aet.artemis.exercise.service.ParticipationAuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.service.ParticipationService;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseCodeReviewFeedbackService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseParticipationService;
import de.tum.cit.aet.artemis.programming.service.ci.ContinuousIntegrationService;
import de.tum.cit.aet.artemis.quiz.domain.QuizBatch;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository;
import de.tum.cit.aet.artemis.quiz.repository.SubmittedAnswerRepository;
import de.tum.cit.aet.artemis.quiz.service.QuizBatchService;
import de.tum.cit.aet.artemis.quiz.service.QuizSubmissionService;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.service.TextExerciseFeedbackService;

/**
 * REST controller for managing Participation.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class ParticipationResource {

    private static final Logger log = LoggerFactory.getLogger(ParticipationResource.class);

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

    private final ParticipationAuthorizationCheckService participationAuthCheckService;

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

    private final SubmittedAnswerRepository submittedAnswerRepository;

    private final QuizSubmissionService quizSubmissionService;

    private final GradingScaleService gradingScaleService;

    private final ProgrammingExerciseCodeReviewFeedbackService programmingExerciseCodeReviewFeedbackService;

    private final TextExerciseFeedbackService textExerciseFeedbackService;

    private final ModelingExerciseFeedbackService modelingExerciseFeedbackService;

    public ParticipationResource(ParticipationService participationService, ProgrammingExerciseParticipationService programmingExerciseParticipationService,
            CourseRepository courseRepository, QuizExerciseRepository quizExerciseRepository, ExerciseRepository exerciseRepository,
            ProgrammingExerciseRepository programmingExerciseRepository, AuthorizationCheckService authCheckService,
            ParticipationAuthorizationCheckService participationAuthCheckService, Optional<ContinuousIntegrationService> continuousIntegrationService,
            UserRepository userRepository, StudentParticipationRepository studentParticipationRepository, AuditEventRepository auditEventRepository,
            GuidedTourConfiguration guidedTourConfiguration, TeamRepository teamRepository, FeatureToggleService featureToggleService,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, SubmissionRepository submissionRepository,
            ResultRepository resultRepository, ExerciseDateService exerciseDateService, InstanceMessageSendService instanceMessageSendService, QuizBatchService quizBatchService,
            SubmittedAnswerRepository submittedAnswerRepository, QuizSubmissionService quizSubmissionService, GradingScaleService gradingScaleService,
            ProgrammingExerciseCodeReviewFeedbackService programmingExerciseCodeReviewFeedbackService, TextExerciseFeedbackService textExerciseFeedbackService,
            ModelingExerciseFeedbackService modelingExerciseFeedbackService) {
        this.participationService = participationService;
        this.programmingExerciseParticipationService = programmingExerciseParticipationService;
        this.quizExerciseRepository = quizExerciseRepository;
        this.courseRepository = courseRepository;
        this.exerciseRepository = exerciseRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.authCheckService = authCheckService;
        this.participationAuthCheckService = participationAuthCheckService;
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
        this.submittedAnswerRepository = submittedAnswerRepository;
        this.quizSubmissionService = quizSubmissionService;
        this.gradingScaleService = gradingScaleService;
        this.programmingExerciseCodeReviewFeedbackService = programmingExerciseCodeReviewFeedbackService;
        this.textExerciseFeedbackService = textExerciseFeedbackService;
        this.modelingExerciseFeedbackService = modelingExerciseFeedbackService;
    }

    /**
     * POST /exercises/:exerciseId/participations : start the "participationId" exercise for the current user.
     *
     * @param exerciseId the participationId of the exercise for which to init a participation
     * @return the ResponseEntity with status 201 (Created) and the participation within the body, or with status 404 (Not Found)
     * @throws URISyntaxException If the URI for the created participation could not be created
     */
    @PostMapping("exercises/{exerciseId}/participations")
    @EnforceAtLeastStudentInExercise
    public ResponseEntity<Participation> startParticipation(@PathVariable Long exerciseId) throws URISyntaxException {
        log.debug("REST request to start Exercise : {}", exerciseId);
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();

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
            // only editors and instructors have permission to trigger participation after due date passed
            if (!featureToggleService.isFeatureEnabled(Feature.ProgrammingExercises)
                    || (!authCheckService.isAtLeastEditorForExercise(exercise, user) && !isAllowedToParticipateInProgrammingExercise(programmingExercise, null))) {
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
     * @param exerciseId             the participationId of the exercise for which to init a participation
     * @param useGradedParticipation a flag that indicates that the student wants to use their graded participation as baseline for the new repo
     * @return the ResponseEntity with status 201 (Created) and the participation within the body, or with status 404 (Not Found)
     * @throws URISyntaxException If the URI for the created participation could not be created
     */
    @PostMapping("exercises/{exerciseId}/participations/practice")
    @EnforceAtLeastStudent
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
     * @param exerciseId      of the exercise for which to resume participation
     * @param participationId of the participation that should be resumed
     * @return ResponseEntity with status 200 (OK) and with updated participation as a body, or with status 500 (Internal Server Error)
     */
    @PutMapping("exercises/{exerciseId}/resume-programming-participation/{participationId}")
    @EnforceAtLeastStudent
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<ProgrammingExerciseStudentParticipation> resumeParticipation(@PathVariable Long exerciseId, @PathVariable Long participationId) {
        log.debug("REST request to resume Exercise : {}", exerciseId);
        var programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exerciseId);
        var participation = programmingExerciseStudentParticipationRepository.findWithTeamStudentsByIdElseThrow(participationId);
        // explicitly set the exercise here to make sure that the templateParticipation and solutionParticipation are initialized in case they should be used again
        participation.setProgrammingExercise(programmingExercise);

        User user = userRepository.getUserWithGroupsAndAuthorities();
        checkAccessPermissionOwner(participation, user);
        if (!isAllowedToParticipateInProgrammingExercise(programmingExercise, participation)) {
            throw new AccessForbiddenException("You are not allowed to resume that participation.");
        }

        // There is a second participation of that student in the exercise that is inactive/finished now
        Optional<StudentParticipation> optionalOtherStudentParticipation = participationService.findOneByExerciseAndParticipantAnyStateAndTestRun(programmingExercise, user,
                !participation.isPracticeMode());
        if (optionalOtherStudentParticipation.isPresent()) {
            StudentParticipation otherParticipation = optionalOtherStudentParticipation.get();
            if (participation.getInitializationState() == InitializationState.INACTIVE) {
                otherParticipation.setInitializationState(InitializationState.FINISHED);
            }
            else {
                otherParticipation.setInitializationState(InitializationState.INACTIVE);
            }
            studentParticipationRepository.saveAndFlush(otherParticipation);
        }

        participation = participationService.resumeProgrammingExercise(participation);
        addLatestResultToParticipation(participation);
        participation.getExercise().filterSensitiveInformation();
        return ResponseEntity.ok().body(participation);
    }

    /**
     * PUT exercises/:exerciseId/request-feedback: Requests feedback for the latest participation
     *
     * @param exerciseId of the exercise for which to resume participation
     * @param principal  current user principal
     * @return ResponseEntity with status 200 (OK)
     */
    @PutMapping("exercises/{exerciseId}/request-feedback")
    @EnforceAtLeastStudent
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<StudentParticipation> requestFeedback(@PathVariable Long exerciseId, Principal principal) {
        log.debug("REST request for feedback request: {}", exerciseId);

        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);

        if (!(exercise instanceof TextExercise) && !(exercise instanceof ProgrammingExercise) && !(exercise instanceof ModelingExercise)) {
            throw new BadRequestAlertException("Unsupported exercise type", "participation", "unsupported type");
        }

        return handleExerciseFeedbackRequest(exercise, principal);
    }

    private ResponseEntity<StudentParticipation> handleExerciseFeedbackRequest(Exercise exercise, Principal principal) {

        // Validate exercise and timing
        if (exercise.isExamExercise()) {
            throw new BadRequestAlertException("Not intended for the use in exams", "participation", "preconditions not met");
        }
        if (exercise.getDueDate() != null && now().isAfter(exercise.getDueDate())) {
            throw new BadRequestAlertException("The due date is over", "participation", "preconditions not met");
        }
        if (exercise instanceof ProgrammingExercise) {
            ((ProgrammingExercise) exercise).validateSettingsForFeedbackRequest();
        }

        // Get and validate participation
        User user = userRepository.getUserWithGroupsAndAuthorities();
        StudentParticipation participation = (exercise instanceof ProgrammingExercise)
                ? programmingExerciseParticipationService.findStudentParticipationByExerciseAndStudentId(exercise, principal.getName())
                : studentParticipationRepository.findByExerciseIdAndStudentLogin(exercise.getId(), principal.getName())
                        .orElseThrow(() -> new ResourceNotFoundException("Participation not found"));

        checkAccessPermissionOwner(participation, user);
        participation = studentParticipationRepository.findByIdWithResultsElseThrow(participation.getId());

        // Check submission requirements
        if (exercise instanceof TextExercise || exercise instanceof ModelingExercise) {
            if (submissionRepository.findAllByParticipationId(participation.getId()).isEmpty()) {
                throw new BadRequestAlertException("You need to submit at least once", "participation", "preconditions not met");
            }
        }
        else if (exercise instanceof ProgrammingExercise) {
            if (participation.findLatestLegalResult() == null) {
                throw new BadRequestAlertException("User has not reached the conditions to submit a feedback request", "participation", "preconditions not met");
            }
        }

        // Check if feedback has already been requested
        var currentDate = now();
        var participationIndividualDueDate = participation.getIndividualDueDate();
        if (participationIndividualDueDate != null && currentDate.isAfter(participationIndividualDueDate)) {
            throw new BadRequestAlertException("Request has already been sent", "participation", "already sent");
        }

        // Process feedback request
        StudentParticipation updatedParticipation;
        if (exercise instanceof TextExercise) {
            updatedParticipation = textExerciseFeedbackService.handleNonGradedFeedbackRequest(exercise.getId(), participation, (TextExercise) exercise);
        }
        else if (exercise instanceof ModelingExercise) {
            updatedParticipation = modelingExerciseFeedbackService.handleNonGradedFeedbackRequest(exercise.getId(), participation, (ModelingExercise) exercise);
        }
        else {
            updatedParticipation = programmingExerciseCodeReviewFeedbackService.handleNonGradedFeedbackRequest(exercise.getId(),
                    (ProgrammingExerciseStudentParticipation) participation, (ProgrammingExercise) exercise);
        }

        return ResponseEntity.ok().body(updatedParticipation);
    }

    /**
     * Checks if the student is currently allowed to participate in the course exercise using this participation
     *
     * @param programmingExercise the exercise where the user wants to participate
     * @param participation       the participation, may be null in case there is none
     * @return a boolean indicating if the user may participate
     */
    private boolean isAllowedToParticipateInProgrammingExercise(ProgrammingExercise programmingExercise, @Nullable StudentParticipation participation) {
        if (participation != null) {
            // only regular participation before the due date; only practice run afterwards
            return participation.isPracticeMode() == exerciseDateService.isAfterDueDate(participation);
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
     *         500 (Internal Server Error) if the participation couldn't be updated
     */
    @PutMapping("exercises/{exerciseId}/participations")
    @EnforceAtLeastTutor
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

        Course course = findCourseFromParticipation(participation);
        if (participation.getPresentationScore() != null && participation.getExercise().getPresentationScoreEnabled() != null
                && participation.getExercise().getPresentationScoreEnabled()) {
            Optional<GradingScale> gradingScale = gradingScaleService.findGradingScaleByCourseId(participation.getExercise().getCourseViaExerciseGroupOrCourseMember().getId());

            // Presentation Score is only valid for non practice participations
            if (participation.isPracticeMode()) {
                throw new BadRequestAlertException("Presentation score is not allowed for practice participations", ENTITY_NAME, "presentationScoreInvalid");
            }

            // Validity of presentationScore for basic presentations
            if (course.getPresentationScore() != null && course.getPresentationScore() > 0) {
                if (participation.getPresentationScore() >= 1.) {
                    participation.setPresentationScore(1.);
                }
                else {
                    participation.setPresentationScore(null);
                }
            }
            // Validity of presentationScore for graded presentations
            if (gradingScale.isPresent() && gradingScale.get().getPresentationsNumber() != null) {
                if ((participation.getPresentationScore() > 100. || participation.getPresentationScore() < 0.)) {
                    throw new BadRequestAlertException("The presentation grade must be between 0 and 100", ENTITY_NAME, "presentationGradeInvalid");
                }

                long presentationCountForParticipant = studentParticipationRepository
                        .findByCourseIdAndStudentIdWithRelevantResult(course.getId(), participation.getParticipant().getId()).stream()
                        .filter(studentParticipation -> studentParticipation.getPresentationScore() != null && !Objects.equals(studentParticipation.getId(), participation.getId()))
                        .count();
                if (presentationCountForParticipant >= gradingScale.get().getPresentationsNumber()) {
                    throw new BadRequestAlertException("Participant already gave the maximum number of presentations", ENTITY_NAME,
                            "invalid.presentations.maxNumberOfPresentationsExceeded",
                            Map.of("name", participation.getParticipant().getName(), "presentationsNumber", gradingScale.get().getPresentationsNumber()));
                }
            }
        }
        // Validity of presentationScore for no presentations
        else {
            participation.setPresentationScore(null);
        }

        StudentParticipation currentParticipation = studentParticipationRepository.findByIdElseThrow(participation.getId());
        if (currentParticipation.getPresentationScore() != null && participation.getPresentationScore() == null || course.getPresentationScore() != null
                && currentParticipation.getPresentationScore() != null && currentParticipation.getPresentationScore() > participation.getPresentationScore()) {
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
    @EnforceAtLeastInstructor
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
            List<StudentParticipation> participationsBeforeDueDate = updatedParticipations.stream().filter(exerciseDateService::isBeforeDueDate).toList();
            List<StudentParticipation> participationsAfterDueDate = updatedParticipations.stream().filter(exerciseDateService::isAfterDueDate).toList();

            if (exercise.isTeamMode()) {
                participationService.initializeTeamParticipations(participationsBeforeDueDate);
                participationService.initializeTeamParticipations(participationsAfterDueDate);
            }
            // when changing the individual due date after the regular due date, the repository might already have been locked
            participationsBeforeDueDate.forEach(
                    participation -> programmingExerciseParticipationService.unlockStudentRepositoryAndParticipation((ProgrammingExerciseStudentParticipation) participation));
            // the new due date may be in the past, students should no longer be able to make any changes
            participationsAfterDueDate.forEach(participation -> programmingExerciseParticipationService.lockStudentRepositoryAndParticipation(programmingExercise,
                    (ProgrammingExerciseStudentParticipation) participation));
        }

        return ResponseEntity.ok().body(updatedParticipations);
    }

    private Set<StudentParticipation> findParticipationWithLatestResults(Exercise exercise) {
        if (exercise.getExerciseType() == ExerciseType.QUIZ) {
            return studentParticipationRepository.findByExerciseIdWithLatestAndManualRatedResultsAndAssessmentNote(exercise.getId());
        }
        if (exercise.isTeamMode()) {
            // For team exercises the students need to be eagerly fetched
            return studentParticipationRepository.findByExerciseIdWithLatestAndManualResultsWithTeamInformation(exercise.getId());
        }
        return studentParticipationRepository.findByExerciseIdWithLatestAndManualResultsAndAssessmentNote(exercise.getId());
    }

    /**
     * GET /exercises/:exerciseId/participations : get all the participations for an exercise
     *
     * @param exerciseId        The participationId of the exercise
     * @param withLatestResults Whether the manual and latest {@link Result results} for the participations should also be fetched
     * @return A list of all participations for the exercise
     */
    @GetMapping("exercises/{exerciseId}/participations")
    @EnforceAtLeastTutor
    public ResponseEntity<Set<StudentParticipation>> getAllParticipationsForExercise(@PathVariable Long exerciseId,
            @RequestParam(defaultValue = "false") boolean withLatestResults) {
        log.debug("REST request to get all Participations for Exercise {}", exerciseId);
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, exercise, null);
        Set<StudentParticipation> participations;
        if (withLatestResults) {
            participations = findParticipationWithLatestResults(exercise);
            participations.forEach(participation -> {
                participation.setSubmissionCount(participation.getSubmissions().size());
                if (participation.getResults() != null && !participation.getResults().isEmpty()
                        && !(participation.getResults().stream().allMatch(result -> AssessmentType.AUTOMATIC_ATHENA.equals(result.getAssessmentType())))) {
                    participation.setSubmissions(null);
                }
                else if (participation.getSubmissions() != null && !participation.getSubmissions().isEmpty()) {
                    var lastLegalSubmission = participation.getSubmissions().stream().filter(submission -> submission.getType() != SubmissionType.ILLEGAL)
                            .max(Comparator.naturalOrder());
                    participation.setSubmissions(lastLegalSubmission.map(Set::of).orElse(Collections.emptySet()));
                }
            });
        }
        else {
            participations = studentParticipationRepository.findByExerciseId(exerciseId);

            Map<Long, Integer> submissionCountMap = studentParticipationRepository.countSubmissionsPerParticipationByExerciseIdAsMap(exerciseId);
            participations.forEach(participation -> participation.setSubmissionCount(submissionCountMap.get(participation.getId())));
        }
        participations = participations.stream().filter(participation -> participation.getParticipant() != null).peek(participation -> {
            // remove unnecessary data to reduce response size
            participation.setExercise(null);
        }).collect(Collectors.toSet());

        return ResponseEntity.ok(participations);
    }

    /**
     * GET /courses/:courseId/participations : get all the participations for a course
     *
     * @param courseId The participationId of the course
     * @return A list of all participations for the given course
     */
    @GetMapping("courses/{courseId}/participations")
    @EnforceAtLeastInstructor
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
            switch (exercise) {
                case ProgrammingExercise programmingExercise -> {
                    programmingExercise.setSolutionParticipation(null);
                    programmingExercise.setTemplateParticipation(null);
                    programmingExercise.setTestRepositoryUri(null);
                    programmingExercise.setShortName(null);
                    programmingExercise.setProgrammingLanguage(null);
                    programmingExercise.setPackageName(null);
                    programmingExercise.setAllowOnlineEditor(null);
                }
                case QuizExercise quizExercise -> {
                    quizExercise.setQuizQuestions(null);
                    quizExercise.setQuizPointStatistic(null);
                }
                case TextExercise textExercise -> textExercise.setExampleSolution(null);
                case ModelingExercise modelingExercise -> {
                    modelingExercise.setExampleSolutionModel(null);
                    modelingExercise.setExampleSolutionExplanation(null);
                }
                default -> {
                }
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
    @EnforceAtLeastStudent
    public ResponseEntity<StudentParticipation> getParticipationWithLatestResult(@PathVariable Long participationId) {
        log.debug("REST request to get Participation : {}", participationId);
        StudentParticipation participation = studentParticipationRepository.findByIdWithResultsElseThrow(participationId);
        participationAuthCheckService.checkCanAccessParticipationElseThrow(participation);

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
    @EnforceAtLeastStudent
    public ResponseEntity<StudentParticipation> getParticipationForCurrentUser(@PathVariable Long participationId) {
        log.debug("REST request to get participation : {}", participationId);
        StudentParticipation participation = studentParticipationRepository.findByIdWithEagerTeamStudentsElseThrow(participationId);
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
    @EnforceAtLeastStudent
    public ResponseEntity<byte[]> getParticipationBuildArtifact(@PathVariable Long participationId) {
        log.debug("REST request to get Participation build artifact: {}", participationId);
        ProgrammingExerciseStudentParticipation participation = programmingExerciseStudentParticipationRepository.findByIdElseThrow(participationId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        checkAccessPermissionOwner(participation, user);

        return continuousIntegrationService.orElseThrow().retrieveLatestArtifact(participation);
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
    @EnforceAtLeastStudent
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
            // quiz has ended => get participation from database and add full quizExercise
            quizExercise = quizExerciseRepository.findByIdWithQuestionsElseThrow(quizExercise.getId());
            StudentParticipation participation = participationForQuizWithResult(quizExercise, user.getLogin(), null);
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

        if (quizBatch.isPresent() && quizBatch.get().isStarted()) {
            // Quiz is active => construct Participation from
            // filtered quizExercise and submission from HashMap
            quizExercise = quizExerciseRepository.findByIdWithQuestionsElseThrow(quizExercise.getId());
            quizExercise.setQuizBatches(quizBatch.stream().collect(Collectors.toSet()));
            quizExercise.filterForStudentsDuringQuiz();
            StudentParticipation participation = participationForQuizWithResult(quizExercise, user.getLogin(), quizBatch.get());
            // set view
            var view = quizExercise.viewForStudentsInQuizExercise(quizBatch.get());
            MappingJacksonValue value = new MappingJacksonValue(participation);
            value.setSerializationView(view);
            return value;
        }
        else {
            // Quiz hasn't started yet => no Result, only quizExercise without questions
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
     * DELETE /participations/:participationId : delete the "participationId" participation. This only works for student participations - other participations should not be deleted
     * here!
     *
     * @param participationId  the participationId of the participation to delete
     * @param deleteBuildPlan  True, if the build plan should also get deleted
     * @param deleteRepository True, if the repository should also get deleted
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("participations/{participationId}")
    @EnforceAtLeastInstructor
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
     * DELETE guided-tour/participations/:participationId : delete the "participationId" participation of student participations for guided tutorials (e.g. when restarting a
     * tutorial)
     * Please note: all users can delete their own participation when it belongs to a guided tutorial
     *
     * @param participationId  the participationId of the participation to delete
     * @param deleteBuildPlan  True, if the build plan should also get deleted
     * @param deleteRepository True, if the repository should also get deleted
     * @return the ResponseEntity with status 200 (OK) or 403 (FORBIDDEN)
     */
    @DeleteMapping("guided-tour/participations/{participationId}")
    @EnforceAtLeastStudent
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
    @EnforceAtLeastInstructor
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
    @EnforceAtLeastInstructor
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
     * @param quizBatch    the quiz batch of quiz exercise which user participated in
     * @return the found or created participation with a result
     */
    // TODO: we should move this method (and others related to quizzes) into a QuizParticipationService (or similar) to make this resource independent of specific quiz exercise
    // functionality
    private StudentParticipation participationForQuizWithResult(QuizExercise quizExercise, String username, QuizBatch quizBatch) {
        // try getting participation from database
        Optional<StudentParticipation> optionalParticipation = participationService.findOneByExerciseAndStudentLoginAnyState(quizExercise, username);

        if (quizExercise.isQuizEnded() || quizSubmissionService.hasUserSubmitted(quizBatch, username)) {

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
            Result result = resultRepository.findFirstByParticipationIdAndRatedWithSubmissionOrderByCompletionDateDesc(participation.getId(), true).orElse(null);
            if (result != null) {
                // find the submitted answers (they are NOT loaded eagerly anymore)
                var quizSubmission = (QuizSubmission) result.getSubmission();
                var submittedAnswers = submittedAnswerRepository.findBySubmission(quizSubmission);
                quizSubmission.setSubmittedAnswers(submittedAnswers);
                participation.addResult(result);
            }
            return participation;
        }
        else if (optionalParticipation.isEmpty()) {
            return null;
        }

        StudentParticipation participation = optionalParticipation.get();
        Optional<Submission> optionalSubmission = submissionRepository.findByParticipationIdOrderBySubmissionDateDesc(participation.getId());
        if (optionalSubmission.isPresent()) {
            Result result = new Result().submission(optionalSubmission.get());
            participation.setResults(Set.of(result));
        }

        return participation;
    }
}
