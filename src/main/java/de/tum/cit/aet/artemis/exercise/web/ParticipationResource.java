package de.tum.cit.aet.artemis.exercise.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_ATHENA;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static java.time.ZonedDateTime.now;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.ZonedDateTime;
import java.util.Optional;

import org.eclipse.jgit.errors.LargeObjectException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenAlertException;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorException;
import de.tum.cit.aet.artemis.core.exception.NotImplementedAlertException;
import de.tum.cit.aet.artemis.core.exception.ServiceUnavailableAlertException;
import de.tum.cit.aet.artemis.core.exception.VersionControlException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.allowedTools.AllowedTools;
import de.tum.cit.aet.artemis.core.security.allowedTools.ToolTokenType;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastStudentInExercise;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.ProfileService;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggle;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import de.tum.cit.aet.artemis.exam.api.StudentExamApi;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participant;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDateService;
import de.tum.cit.aet.artemis.exercise.service.FeedbackRequestService;
import de.tum.cit.aet.artemis.exercise.service.ParticipationAuthorizationService;
import de.tum.cit.aet.artemis.exercise.service.ParticipationService;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/**
 * REST controller for starting or resuming participations, as well as requesting feedback for one.
 */
@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/exercise/")
public class ParticipationResource {

    private static final Logger log = LoggerFactory.getLogger(ParticipationResource.class);

    private static final String ENTITY_NAME = "participation";

    private final ParticipationService participationService;

    private final AuthorizationCheckService authCheckService;

    private final FeatureToggleService featureToggleService;

    private final ExerciseDateService exerciseDateService;

    private final ParticipationAuthorizationService participationAuthorizationService;

    private final Optional<StudentExamApi> studentExamApi;

    private final ExerciseRepository exerciseRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final UserRepository userRepository;

    private final TeamRepository teamRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final SubmissionRepository submissionRepository;

    private final ProfileService profileService;

    private final FeedbackRequestService feedbackRequestService;

    public ParticipationResource(ParticipationService participationService, ExerciseRepository exerciseRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            AuthorizationCheckService authCheckService, UserRepository userRepository, StudentParticipationRepository studentParticipationRepository, TeamRepository teamRepository,
            FeatureToggleService featureToggleService, ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository,
            SubmissionRepository submissionRepository, ExerciseDateService exerciseDateService, ParticipationAuthorizationService participationAuthorizationService,
            Optional<StudentExamApi> studentExamApi, ProfileService profileService, FeedbackRequestService feedbackRequestService) {
        this.participationService = participationService;
        this.exerciseRepository = exerciseRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.authCheckService = authCheckService;
        this.userRepository = userRepository;
        this.teamRepository = teamRepository;
        this.featureToggleService = featureToggleService;
        this.studentParticipationRepository = studentParticipationRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.submissionRepository = submissionRepository;
        this.exerciseDateService = exerciseDateService;
        this.participationAuthorizationService = participationAuthorizationService;
        this.studentExamApi = studentExamApi;
        this.profileService = profileService;
        this.feedbackRequestService = feedbackRequestService;
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
    @AllowedTools({ ToolTokenType.SCORPIO, ToolTokenType.ARTEMIS_EXTENSION })
    public ResponseEntity<Participation> startParticipation(@PathVariable Long exerciseId) throws URISyntaxException {
        log.debug("REST request to start Exercise : {}", exerciseId);
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        boolean triesToCreateAssignmentRepoForExamExercise = exercise.isExamExercise() && exercise instanceof ProgrammingExercise
                && authCheckService.isAtLeastTeachingAssistantForExercise(exercise, user);
        if (triesToCreateAssignmentRepoForExamExercise) {
            throw new AccessForbiddenAlertException("Assignment repositories are not allowed for exam exercises. Please use the Test Run feature instead.", ENTITY_NAME,
                    "assignmentRepositoryNotAllowed");
        }
        checkIfParticipationCanBeStartedElseThrow(exercise, user);

        // if this is a team-based exercise, set the participant to the team that the user belongs to
        Participant participant = user;
        if (exercise.isTeamMode()) {
            participant = teamRepository.findOneByExerciseIdAndUserId(exercise.getId(), user.getId())
                    .orElseThrow(() -> new BadRequestAlertException("Team exercise cannot be started without assigned team.", "participation", "teamExercise.cannotStart"));
        }
        StudentParticipation participation;
        try {
            participation = participationService.startExercise(exercise, participant, true);
        }
        catch (Exception e) {
            if (e instanceof VersionControlException && e.getCause() instanceof LargeObjectException) {
                throw new InternalServerErrorException("Failed to start exercise because repository contains files that are too large. Please contact your instructor.");
            }
            else {
                log.error("Failed to start exercise participation for exercise {} and user {}", exerciseId, user.getLogin(), e);
                throw new InternalServerErrorException("Failed to start exercise participation.");
            }
        }

        // remove sensitive information before sending participation to the client
        participation.getExercise().filterSensitiveInformation();
        return ResponseEntity.created(new URI("/api/exercise/participations/" + participation.getId())).body(participation);
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
    public ResponseEntity<Participation> startPracticeParticipation(@PathVariable Long exerciseId,
            @RequestParam(value = "useGradedParticipation", defaultValue = "false") boolean useGradedParticipation) throws URISyntaxException {
        log.debug("REST request to practice Exercise : {}", exerciseId);
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        Optional<StudentParticipation> optionalGradedStudentParticipation = participationService.findOneGradedByExerciseAndParticipant(exercise, user);

        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, exercise, user);
        if (exercise.isExamExercise()) {
            throw new BadRequestAlertException("The practice mode cannot be used in an exam", ENTITY_NAME, "dueDateOver.notAvailableInExam");
        }
        if (exercise instanceof FileUploadExercise) {
            throw new NotImplementedAlertException("Unsupported exercise type", "participation", "dueDateOver.unsupportedExerciseType");
        }
        if (exercise.isTeamMode()) {
            throw new NotImplementedAlertException("The practice mode is not yet supported for team exercises", ENTITY_NAME, "dueDateOver.notSupportedForTeams");
        }
        if (exercise instanceof ProgrammingExercise && !featureToggleService.isFeatureEnabled(Feature.ProgrammingExercises)) {
            throw new ServiceUnavailableAlertException("The feature for programming exercises is disabled", ENTITY_NAME, "dueDateOver.programmingExercisesDisabled");
        }
        if ((exercise instanceof TextExercise || exercise instanceof ModelingExercise) && !profileService.isProfileActive(PROFILE_ATHENA)) {
            throw new ServiceUnavailableAlertException("Practice mode for text and modeling exercises requires the Athena profile", ENTITY_NAME,
                    "dueDateOver.athenaProfileRequired");
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
        participationAuthorizationService.checkAccessPermissionOwner(participation, user);
        if (!isAllowedToParticipateInProgrammingExercise(programmingExercise, participation)) {
            throw new AccessForbiddenException("You are not allowed to resume that participation.");
        }

        // There is a second participation of that student in the exercise that is inactive/finished now
        Optional<StudentParticipation> optionalOtherStudentParticipation = participation.isPracticeMode()
                ? participationService.findOneGradedByExerciseAndParticipant(programmingExercise, user)
                : participationService.findOnePracticeByExerciseAndParticipant(programmingExercise, user);
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
        participation.getExercise().filterSensitiveInformation();
        return ResponseEntity.ok().body(participation);
    }

    /**
     * PUT exercises/:exerciseId/participations/:participationId/request-feedback: Requests AI feedback for the given participation
     *
     * @param exerciseId      of the exercise for which to request feedback
     * @param participationId of the participation for which feedback is requested
     * @return ResponseEntity with status 200 (OK) and the updated participation as body
     */
    @PutMapping("exercises/{exerciseId}/participations/{participationId}/request-feedback")
    @EnforceAtLeastStudent
    public ResponseEntity<StudentParticipation> requestFeedback(@PathVariable Long exerciseId, @PathVariable Long participationId) {
        log.debug("REST request to request feedback for exercise {} and participation {}", exerciseId, participationId);
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        var participation = studentParticipationRepository.findByIdWithResultsElseThrow(participationId);
        if (!participation.getExercise().getId().equals(exerciseId)) {
            throw new BadRequestAlertException("Participation does not belong to the exercise", ENTITY_NAME, "participationExerciseMismatch");
        }
        participationAuthorizationService.checkAccessPermissionOwner(participation, user);

        if (exercise.isExamExercise()) {
            throw new BadRequestAlertException("Not intended for the use in exams", ENTITY_NAME, "feedbackRequest.notAvailableInExam");
        }

        if (exercise instanceof QuizExercise || exercise instanceof FileUploadExercise) {
            throw new BadRequestAlertException("Unsupported exercise type", ENTITY_NAME, "feedbackRequest.unsupportedExerciseType");
        }

        if (exercise instanceof ProgrammingExercise && !featureToggleService.isFeatureEnabled(Feature.ProgrammingExercises)) {
            throw new ServiceUnavailableAlertException("The feature for programming exercises is disabled", ENTITY_NAME, "feedbackRequest.programmingExercisesDisabled");
        }

        if ((exercise instanceof TextExercise || exercise instanceof ModelingExercise) && !profileService.isProfileActive(PROFILE_ATHENA)) {
            throw new ServiceUnavailableAlertException("Feedback requests for text and modeling exercises require the Athena profile", ENTITY_NAME,
                    "feedbackRequest.athenaProfileRequired");
        }

        if (exercise.getDueDate() != null && now().isAfter(exercise.getDueDate()) && !participation.isPracticeMode()) {
            throw new BadRequestAlertException("Feedback requests are not allowed after the due date for graded participations", ENTITY_NAME,
                    "dueDateOver.feedbackRequestAfterDueDate");
        }

        if (exercise instanceof ProgrammingExercise) {
            ((ProgrammingExercise) exercise).validateSettingsForFeedbackRequest();
        }

        if (exercise instanceof TextExercise || exercise instanceof ModelingExercise) {
            var submissions = submissionRepository.findAllByParticipationId(participation.getId());
            // Only count real submitted entries, not auto-created placeholders
            boolean hasSubmittedOnce = submissions.stream().anyMatch(Submission::isSubmitted);
            if (!hasSubmittedOnce) {
                throw new BadRequestAlertException("You need to submit at least once", "participation", "noSubmissionExists", true);
            }
        }
        else if (exercise instanceof ProgrammingExercise) {
            if (participation.findLatestResult() == null) {
                throw new BadRequestAlertException("You need to submit at least once and have the build results", "participation", "noSubmissionExists", true);
            }
        }

        var latestResult = participation.findLatestResult();
        boolean hasFeedbackBeenRequested = latestResult != null && latestResult.getAssessmentType() == AssessmentType.AUTOMATIC_ATHENA && latestResult.getCompletionDate() != null
                && latestResult.getCompletionDate().isAfter(now());
        if (hasFeedbackBeenRequested) {
            throw new BadRequestAlertException("Request has already been sent", "participation", "feedbackRequestAlreadySent", true);
        }

        StudentParticipation updatedParticipation = feedbackRequestService.processFeedbackRequest(exercise, participation);
        return ResponseEntity.ok().body(updatedParticipation);
    }

    /**
     * <p>
     * Checks if a participation can be started for the given exercise and user.
     * </p>
     * This method verifies if the participation can be started based on the due date.
     * <ul>
     * <li>Checks if the due date has passed (allows starting participations for non-programming exercises if the user might have an individual working time)</li>
     * <li>Additionally, for programming exercises, checks if the programming exercise feature is enabled</li>
     * </ul>
     *
     * @param exercise for which the participation is to be started
     * @param user     attempting to start the participation
     * @throws AccessForbiddenAlertException if the participation cannot be started due to feature restrictions or due date constraints
     */
    private void checkIfParticipationCanBeStartedElseThrow(Exercise exercise, User user) {
        // 1) Don't allow student to start before the start and release date
        ZonedDateTime releaseOrStartDate = exercise.getParticipationStartDate();
        if (releaseOrStartDate != null && releaseOrStartDate.isAfter(now())) {
            if (authCheckService.isOnlyStudentInCourse(exercise.getCourseViaExerciseGroupOrCourseMember(), user)) {
                throw new AccessForbiddenException("Students cannot start an exercise before the release date");
            }
        }
        // 2) Don't allow participations if the feature is disabled
        if (exercise instanceof ProgrammingExercise && !featureToggleService.isFeatureEnabled(Feature.ProgrammingExercises)) {
            throw new AccessForbiddenException("Programming Exercise Feature is disabled.");
        }
        // 3) Don't allow to start after the (individual) end date
        ZonedDateTime exerciseDueDate = exercise.getDueDate();
        // NOTE: course exercises can only have an individual due date when they already have started
        if (exercise.isExamExercise()) {
            // NOTE: this is an absolute edge case because exam participations are generated before the exam starts and should not be started by the user
            exerciseDueDate = exercise.getExam().getEndDate();
            var studentExam = studentExamApi.orElseThrow().findByExamIdAndUserId(exercise.getExam().getId(), user.getId());
            if (studentExam.isPresent() && studentExam.get().getIndividualEndDate() != null) {
                exerciseDueDate = studentExam.get().getIndividualEndDate();
            }
        }
        boolean isDueDateInPast = exerciseDueDate != null && now().isAfter(exerciseDueDate);
        if (isDueDateInPast) {
            if (exercise instanceof FileUploadExercise) {
                // File upload exercises do not support practice mode
                throw new AccessForbiddenAlertException("The exercise due date is already over, you can no longer participate in this exercise.", ENTITY_NAME,
                        "dueDateOver.noParticipationPossible");
            }
            else {
                // Programming, text, modeling, and quiz exercises offer a dedicated practice mode
                throw new AccessForbiddenAlertException("Not allowed", ENTITY_NAME, "dueDateOver.participationInPracticeMode");
            }
        }

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

}
