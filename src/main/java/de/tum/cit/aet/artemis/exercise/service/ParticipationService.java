package de.tum.cit.aet.artemis.exercise.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.dto.UserNameAndLoginDTO;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.SortingOrder;
import de.tum.cit.aet.artemis.core.exception.ContinuousIntegrationException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participant;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.dto.ParticipationDueDateUpdateDTO;
import de.tum.cit.aet.artemis.exercise.dto.ParticipationManagementDTO;
import de.tum.cit.aet.artemis.exercise.dto.ParticipationNameExportDTO;
import de.tum.cit.aet.artemis.exercise.dto.ParticipationScoreDTO;
import de.tum.cit.aet.artemis.exercise.dto.ParticipationScoreSearchDTO;
import de.tum.cit.aet.artemis.exercise.dto.ParticipationSearchDTO;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.build.BuildPlanType;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.cit.aet.artemis.programming.service.ParticipationVcsAccessTokenService;
import de.tum.cit.aet.artemis.programming.service.UriService;
import de.tum.cit.aet.artemis.programming.service.ci.ContinuousIntegrationService;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.service.vcs.VersionControlService;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/**
 * Service Implementation for managing Participation.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class ParticipationService {

    @Value("${artemis.version-control.default-branch:main}")
    protected String defaultBranch;

    private final Optional<ContinuousIntegrationService> continuousIntegrationService;

    private final Optional<VersionControlService> versionControlService;

    private final ParticipationRepository participationRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final SubmissionRepository submissionRepository;

    private final TeamRepository teamRepository;

    private final UriService uriService;

    private final ParticipationVcsAccessTokenService participationVCSAccessTokenService;

    private final ResultRepository resultRepository;

    public ParticipationService(Optional<ContinuousIntegrationService> continuousIntegrationService, Optional<VersionControlService> versionControlService,
            ParticipationRepository participationRepository, StudentParticipationRepository studentParticipationRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            SubmissionRepository submissionRepository, TeamRepository teamRepository, UriService uriService, ParticipationVcsAccessTokenService participationVCSAccessTokenService,
            ResultRepository resultRepository) {
        this.continuousIntegrationService = continuousIntegrationService;
        this.versionControlService = versionControlService;
        this.participationRepository = participationRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.submissionRepository = submissionRepository;
        this.teamRepository = teamRepository;
        this.uriService = uriService;
        this.participationVCSAccessTokenService = participationVCSAccessTokenService;
        this.resultRepository = resultRepository;
    }

    /**
     * This method is triggered when a student or participant starts an exercise. It creates a `StudentParticipation` which connects the corresponding participant
     * (either a user or team) and exercise. Additionally, for programming exercises, it configures related repository and build plan setup.
     * For other exercise types such as modeling, text, quiz, or file-upload exercises, it also initializes and stores the corresponding submission if necessary.
     * <p>
     * The method handles different scenarios based on whether the exercise is part of a test exam, course exercise, or a regular exam.
     * In the case of a test exam, previous participations are marked as finished, and a new participation is created. For regular exercises,
     * the method ensures that either a new participation is created or an existing one is reused.
     * <p>
     * For programming exercises, additional steps like repository setup are handled by the `startProgrammingExercise` method.
     * For other exercises (e.g., modeling, text, file-upload, or quiz), the participation is initialized accordingly, and, if required,
     * an initial submission is created.
     *
     * @param exercise                the exercise that is being started. For programming exercises, template and solution participations should be eagerly loaded.
     * @param participant             the user or team starting the exercise
     * @param createInitialSubmission whether an initial empty submission should be created for non-programming exercises such as text, modeling, quiz, or file-upload
     * @return the `StudentParticipation` connecting the given exercise and participant
     */
    public StudentParticipation startExercise(Exercise exercise, Participant participant, boolean createInitialSubmission) {

        StudentParticipation participation;
        Optional<StudentParticipation> optionalStudentParticipation = Optional.empty();

        // In case of a test exam we don't try to find an existing participation, because students can participate multiple times
        // Instead, all previous participations are marked as finished and a new one is created
        if (exercise.isTestExamExercise()) {
            List<StudentParticipation> participations = studentParticipationRepository.findByExerciseIdAndStudentId(exercise.getId(), participant.getId());
            participations.forEach(studentParticipation -> studentParticipation.setInitializationState(InitializationState.FINISHED));
            participation = createNewParticipation(exercise, participant);
            participation.setAttempt(participations.size());
            participations.add(participation);
            studentParticipationRepository.saveAll(participations);
        }

        // All other cases, i.e. normal exercises, and regular exam exercises
        else {
            optionalStudentParticipation = findOneGradedByExerciseAndParticipant(exercise, participant);
            if (optionalStudentParticipation.isPresent() && optionalStudentParticipation.get().isPracticeMode() && exercise.isCourseExercise()) {
                // In case there is already a practice participation, set it to inactive
                optionalStudentParticipation.get().setInitializationState(InitializationState.INACTIVE);
                studentParticipationRepository.saveAndFlush(optionalStudentParticipation.get());

                optionalStudentParticipation = findOneGradedByExerciseAndParticipant(exercise, participant);
            }
            // Check if participation already exists
            if (optionalStudentParticipation.isEmpty()) {
                participation = createNewParticipation(exercise, participant);
            }
            else {
                // make sure participation and exercise are connected
                participation = optionalStudentParticipation.get();
                participation.setExercise(exercise);
            }
        }

        if (exercise instanceof ProgrammingExercise) {
            // we need to fetch the programming exercise again to get the template participation because we need the repository uri for the copy operation
            var programmingExercise = programmingExerciseRepository.findByIdWithTemplateParticipationElseThrow(exercise.getId());
            participation = startProgrammingExercise(programmingExercise, (ProgrammingExerciseStudentParticipation) participation);
        }
        // for all other exercises: QuizExercise, ModelingExercise, TextExercise, FileUploadExercise
        else {
            if (participation.getInitializationState() == null || participation.getInitializationState() == InitializationState.UNINITIALIZED
                    || participation.getInitializationState() == InitializationState.FINISHED && !(exercise instanceof QuizExercise)) {
                // in case the participation was finished before, we set it to initialized again so that the user sees the correct button "Open modeling editor" on the client side.
                // Only for quiz exercises, the participation status FINISHED should not be overwritten since the user must not change their submission once submitted
                participation.setInitializationState(InitializationState.INITIALIZED);
            }
            // TODO: load submission with exercise for exam edge case:
            // clients creates missing participation for exercise, call on server succeeds, but response to client is lost
            // -> client tries to create participation again. In this case the submission is not loaded from db -> client errors
            if (optionalStudentParticipation.isEmpty() || !submissionRepository.existsByParticipationId(participation.getId())) {
                // initialize a modeling, text, file upload or quiz submission
                if (createInitialSubmission) {
                    submissionRepository.initializeSubmission(participation, exercise, null);
                }
            }
        }
        if (Optional.ofNullable(participation.getInitializationDate()).isEmpty()) {
            participation.setInitializationDate(ZonedDateTime.now());
        }
        return studentParticipationRepository.saveAndFlush(participation);
    }

    /**
     * Helper Method to create a new Participation for the
     *
     * @param exercise    the exercise for which a participation should be created
     * @param participant the participant for the participation
     * @return a StudentParticipation for the exercise and participant with an optional specified initializationDate
     */
    private StudentParticipation createNewParticipation(Exercise exercise, Participant participant) {
        StudentParticipation participation;
        // create a new participation only if no participation can be found
        if (exercise instanceof ProgrammingExercise) {
            participation = new ProgrammingExerciseStudentParticipation(defaultBranch);
        }
        else {
            participation = new StudentParticipation();
        }
        participation.setInitializationState(InitializationState.UNINITIALIZED);
        participation.setExercise(exercise);
        participation.setParticipant(participant);

        participation = studentParticipationRepository.saveAndFlush(participation);

        if (exercise instanceof ProgrammingExercise && participant instanceof User user) {
            participationVCSAccessTokenService.createParticipationVCSAccessToken(user, participation);
        }

        return participation;
    }

    /**
     * Start a programming exercise participation (which does not exist yet) by creating and configuring a student git repository (step 1) and a student build plan (step 2)
     * based on the templates in the given programming exercise
     *
     * @param exercise      the programming exercise that the currently active user (student) wants to start
     * @param participation inactive participation
     * @return started participation
     */
    private StudentParticipation startProgrammingExercise(ProgrammingExercise exercise, ProgrammingExerciseStudentParticipation participation) {
        // Step 1a) create the student repository (based on the template repository)
        participation = copyRepository(exercise, exercise.getVcsTemplateRepositoryUri(), participation);

        return startProgrammingParticipation(participation);
    }

    /**
     * Start a programming exercise participation (which does not exist yet) by creating and configuring a student git repository (step 1) and a student build plan (step 2)
     * based on the templates in the given programming exercise
     *
     * @param exercise                           the programming exercise that the currently active user (student) wants to start
     * @param participation                      inactive participation
     * @param optionalGradedStudentParticipation the graded participation of that student, if present
     * @param useGradedParticipation             flag if the graded student participation should be used as baseline for the new repository
     * @return started participation
     */
    private StudentParticipation startPracticeMode(ProgrammingExercise exercise, ProgrammingExerciseStudentParticipation participation,
            Optional<StudentParticipation> optionalGradedStudentParticipation, boolean useGradedParticipation) {
        // Step 1a) create the student repository (based on the template repository or graded participation)
        if (useGradedParticipation && optionalGradedStudentParticipation.isPresent()
                && optionalGradedStudentParticipation.get() instanceof ProgrammingExerciseStudentParticipation programmingExerciseStudentParticipation) {
            participation = copyRepository(exercise, programmingExerciseStudentParticipation.getVcsRepositoryUri(), participation);
        }
        else {
            participation = copyRepository(exercise, exercise.getVcsTemplateRepositoryUri(), participation);
        }

        // For practice mode 1 is always set. For more information see Participation.class
        participation.setAttempt(1);

        return startProgrammingParticipation(participation);
    }

    private StudentParticipation startProgrammingParticipation(ProgrammingExerciseStudentParticipation participation) {
        // Step 1c) configure the student repository (e.g. access right, etc.)
        participation = configureRepository(participation);
        // Step 2a) create the build plan (based on the BASE build plan)
        participation = copyBuildPlan(participation);
        // Step 2b) configure the build plan (e.g. access right, hooks, etc.)
        participation = configureBuildPlan(participation);
        // Step 3a) Set the InitializationState to initialized to indicate, the programming exercise is ready
        participation.setInitializationState(InitializationState.INITIALIZED);
        // after saving, we need to make sure the object that is used after the if statement is the right one
        return participation;
    }

    /**
     * This method is triggered when a student starts the practice mode of an exercise. It creates or reuses a separate StudentParticipation (marked as practiceMode=true)
     * connecting the corresponding participant and exercise. For programming exercises, it additionally configures a new repository and build plan (copied from template or
     * graded).
     * For quiz exercises, it performs simple initialization without VCS or build setup, allowing multiple submissions (no state lock to FINISHED).
     * Note: This method finishes any provided graded participation and sets a fixed attempt=1 for practice.
     *
     * @param exercise                           the exercise to start in practice mode; for programming, template and solution participations should be eagerly loaded
     * @param participant                        the user or team starting practice
     * @param optionalGradedStudentParticipation the optional graded (live) participation to finish before starting practice
     * @param useGradedParticipation             flag if the graded participation's repository should be used as baseline (programming only)
     * @return the practice participation connecting the given exercise and participant
     */
    public StudentParticipation startPracticeMode(Exercise exercise, Participant participant, Optional<StudentParticipation> optionalGradedStudentParticipation,
            boolean useGradedParticipation) {
        if (exercise instanceof FileUploadExercise) {
            throw new IllegalStateException("File upload exercises do not support practice mode.");
        }
        optionalGradedStudentParticipation.ifPresent(participation -> {
            participation.setInitializationState(InitializationState.FINISHED);
            participationRepository.save(participation);
        });
        Optional<StudentParticipation> optionalStudentParticipation = findOnePracticeByExerciseAndParticipant(exercise, participant);
        StudentParticipation participation;
        if (optionalStudentParticipation.isEmpty()) {
            // create a new participation only if no participation can be found
            if (exercise instanceof ProgrammingExercise) {
                participation = new ProgrammingExerciseStudentParticipation(defaultBranch);
            }
            else {
                participation = new StudentParticipation();
            }
            participation.setInitializationState(InitializationState.UNINITIALIZED);
            participation.setExercise(exercise);
            participation.setParticipant(participant);
            participation.setPracticeMode(true);
            participation = studentParticipationRepository.saveAndFlush(participation);
            if (participant instanceof User user && exercise instanceof ProgrammingExercise) {
                participationVCSAccessTokenService.createParticipationVCSAccessToken(user, participation);
            }
        }
        else {
            // make sure participation and exercise are connected
            participation = optionalStudentParticipation.get();
            participation.setExercise(exercise);
        }

        if (exercise instanceof ProgrammingExercise) {
            ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exercise.getId());
            participation = startPracticeMode(programmingExercise, (ProgrammingExerciseStudentParticipation) participation, optionalGradedStudentParticipation,
                    useGradedParticipation);
        }
        else {
            if (participation.getInitializationState() != InitializationState.INITIALIZED) {
                participation.setInitializationState(InitializationState.INITIALIZED);
            }
            participation.setAttempt(1);
            if (participation.getInitializationDate() == null) {
                participation.setInitializationDate(ZonedDateTime.now());
            }

            boolean isTextOrModelingExercise = exercise instanceof TextExercise || exercise instanceof ModelingExercise;
            if (isTextOrModelingExercise && !submissionRepository.existsByParticipationId(participation.getId())) {
                submissionRepository.initializeSubmission(participation, exercise, null);
            }
        }

        return studentParticipationRepository.saveAndFlush(participation);
    }

    /**
     * This method checks whether a participation exists for a given exercise and user. If not, it creates such a participation with initialization state FINISHED.
     * If the participation had to be newly created or there were no submissions yet for the existing participation, a new submission is created with the given submission type.
     * For external submissions, the submission is assumed to be submitted immediately upon creation.
     *
     * @param exercise       the exercise for which to create a participation and submission
     * @param participant    the user/team for which to create a participation and submission
     * @param submissionType the type of submission to create if none exist yet
     * @return the participation connecting the given exercise and user
     */
    public StudentParticipation createParticipationWithEmptySubmissionIfNotExisting(Exercise exercise, Participant participant, SubmissionType submissionType) {
        Optional<StudentParticipation> optionalStudentParticipation = findOneGradedByExerciseAndParticipant(exercise, participant);
        StudentParticipation participation;
        if (optionalStudentParticipation.isEmpty()) {
            // create a new participation only if no participation can be found
            if (exercise instanceof ProgrammingExercise) {
                participation = new ProgrammingExerciseStudentParticipation(defaultBranch);
            }
            else {
                participation = new StudentParticipation();
            }
            participation.setInitializationState(InitializationState.UNINITIALIZED);
            participation.setInitializationDate(ZonedDateTime.now());
            participation.setExercise(exercise);
            participation.setParticipant(participant);

            participation = studentParticipationRepository.saveAndFlush(participation);
        }
        else {
            participation = optionalStudentParticipation.get();
        }

        // setup repository in case of programming exercise
        if (exercise instanceof ProgrammingExercise) {
            // fetch again to get additional objects
            ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exercise.getId());
            ProgrammingExerciseStudentParticipation programmingParticipation = (ProgrammingExerciseStudentParticipation) participation;
            // Note: we make sure to use the correct programming exercises here to avoid org.hibernate.LazyInitializationException later
            programmingParticipation.setProgrammingExercise(programmingExercise);
            // Note: we need a repository, otherwise the student would not be possible to click resume (in case they want to further participate after the due date)
            programmingParticipation = copyRepository(programmingExercise, programmingExercise.getVcsTemplateRepositoryUri(), programmingParticipation);
            programmingParticipation = configureRepository(programmingParticipation);
            participation = programmingParticipation;
        }

        participation.setInitializationState(InitializationState.FINISHED);
        participation = studentParticipationRepository.saveAndFlush(participation);

        // Take the latest submission or initialize a new empty submission
        var studentParticipation = studentParticipationRepository.findByIdWithSubmissionsElseThrow(participation.getId());
        var submission = studentParticipation.findLatestSubmission().orElseGet(() -> submissionRepository.initializeSubmission(studentParticipation, exercise, submissionType));

        // If the submission has not yet been submitted, submit it now
        if (!submission.isSubmitted()) {
            submission.setSubmitted(true);
            submission.setSubmissionDate(ZonedDateTime.now());
            submissionRepository.save(submission);
        }

        return studentParticipation;
    }

    /**
     * Resume an inactive programming exercise participation (with previously deleted build plan) by creating and configuring a student build plan (step 2)
     * based on the template (BASE) in the corresponding programming exercise, also compare {@link #startProgrammingExercise}
     *
     * @param participation inactive participation
     * @return resumed participation
     */
    public ProgrammingExerciseStudentParticipation resumeProgrammingExercise(ProgrammingExerciseStudentParticipation participation) {
        // this method assumes that the student git repository already exists (compare startProgrammingExercise) so steps 1, 2 and 5 are not necessary
        // Step 2a) create the build plan (based on the BASE build plan)
        participation = copyBuildPlan(participation);
        // Step 2b) configure the build plan (e.g. access right, hooks, etc.)
        participation = configureBuildPlan(participation);
        // Note: the repository webhook (step 1c) already exists, so we don't need to set it up again, the empty commit hook (step 2c) is also not necessary here
        // and must be handled by the calling method in case it would be necessary

        // If a graded participation gets reset after the due date set the state back to finished. Otherwise, the participation is initialized
        var dueDate = ExerciseDateService.getDueDate(participation);
        if (!participation.isPracticeMode() && dueDate.isPresent() && ZonedDateTime.now().isAfter(dueDate.get())) {
            participation.setInitializationState(InitializationState.FINISHED);
        }
        else {
            participation.setInitializationState(InitializationState.INITIALIZED);
        }
        participation = programmingExerciseStudentParticipationRepository.saveAndFlush(participation);
        if (participation.getInitializationDate() == null) {
            // only set the date if it was not set before (which should NOT be the case)
            participation.setInitializationDate(ZonedDateTime.now());
        }
        return programmingExerciseStudentParticipationRepository.saveAndFlush(participation);
    }

    private ProgrammingExerciseStudentParticipation copyRepository(ProgrammingExercise programmingExercise, LocalVCRepositoryUri sourceUri,
            ProgrammingExerciseStudentParticipation participation) {
        // only execute this step if it has not yet been completed yet or if the repository uri is missing for some reason
        if (!participation.getInitializationState().hasCompletedState(InitializationState.REPO_COPIED) || participation.getVcsRepositoryUri() == null) {
            final var projectKey = programmingExercise.getProjectKey();
            final var repoName = participation.addPracticePrefixIfTestRun(participation.getParticipantIdentifier());
            // NOTE: we have to get the repository slug of the template participation here, because not all exercises (in particular old ones) follow the naming conventions
            final var templateRepoName = uriService.getRepositorySlugFromRepositoryUri(sourceUri);
            VersionControlService vcs = versionControlService.orElseThrow();
            String templateBranch = programmingExerciseRepository.findBranchByExerciseId(programmingExercise.getId());
            // the next action includes recovery, which means if the repository has already been copied, we simply retrieve the repository uri and do not copy it again
            var newRepoUri = vcs.copyRepositoryWithoutHistory(projectKey, templateRepoName, templateBranch, projectKey, repoName, participation.getAttempt());
            // add the userInfo part to the repoUri only if the participation belongs to a single student (and not a team of students)
            if (participation.getStudent().isPresent()) {
                newRepoUri = newRepoUri.withUser(participation.getParticipantIdentifier());
            }
            // After copying the repository, the new participation uses the default branch
            participation.setBranch(defaultBranch);
            participation.setRepositoryUri(newRepoUri.toString());
            participation.setInitializationState(InitializationState.REPO_COPIED);

            return programmingExerciseStudentParticipationRepository.saveAndFlush(participation);
        }
        else {
            return participation;
        }
    }

    private ProgrammingExerciseStudentParticipation configureRepository(ProgrammingExerciseStudentParticipation participation) {
        if (!participation.getInitializationState().hasCompletedState(InitializationState.REPO_CONFIGURED)) {
            // do not allow the student to access the repository if this is an exam exercise that has not started yet
            if (participation.getParticipant() instanceof Team team && !Hibernate.isInitialized(team.getStudents())) {
                // eager load the team with students so their information can be used for the repository configuration
                participation.setParticipant(teamRepository.findWithStudentsByIdElseThrow(team.getId()));
            }
            participation.setInitializationState(InitializationState.REPO_CONFIGURED);
            return programmingExerciseStudentParticipationRepository.saveAndFlush(participation);
        }
        else {
            return participation;
        }
    }

    private ProgrammingExerciseStudentParticipation copyBuildPlan(ProgrammingExerciseStudentParticipation participation) {
        // only execute this step if it has not yet been completed yet or if the build plan id is missing for some reason
        if (!participation.getInitializationState().hasCompletedState(InitializationState.BUILD_PLAN_COPIED) || participation.getBuildPlanId() == null) {
            final var exercise = participation.getProgrammingExercise();
            final var planName = BuildPlanType.TEMPLATE.getName();
            final var username = participation.getParticipantIdentifier();
            final var buildProjectName = participation.getExercise().getCourseViaExerciseGroupOrCourseMember().getShortName().toUpperCase() + " "
                    + participation.getExercise().getTitle();
            final var targetPlanName = participation.addPracticePrefixIfTestRun(username.toUpperCase());
            // the next action includes recovery, which means if the build plan has already been copied, we simply retrieve the build plan id and do not copy it again
            final var buildPlanId = continuousIntegrationService.orElseThrow().copyBuildPlan(exercise, planName, exercise, buildProjectName, targetPlanName, true);
            participation.setBuildPlanId(buildPlanId);
            participation.setInitializationState(InitializationState.BUILD_PLAN_COPIED);
            return programmingExerciseStudentParticipationRepository.saveAndFlush(participation);
        }
        else {
            return participation;
        }
    }

    private ProgrammingExerciseStudentParticipation configureBuildPlan(ProgrammingExerciseStudentParticipation participation) {
        if (!participation.getInitializationState().hasCompletedState(InitializationState.BUILD_PLAN_CONFIGURED)) {
            try {
                continuousIntegrationService.orElseThrow().configureBuildPlan(participation);
            }
            catch (ContinuousIntegrationException ex) {
                // this means something with the configuration of the build plan is wrong.
                // we try to recover from typical edge cases by setting the initialization state back, so that the previous action (copy build plan) is tried again, when
                // the user again clicks on the start / resume exercise button.
                participation.setInitializationState(InitializationState.REPO_CONFIGURED);
                programmingExerciseStudentParticipationRepository.saveAndFlush(participation);
                // rethrow
                throw ex;
            }
            participation.setInitializationState(InitializationState.BUILD_PLAN_CONFIGURED);
            return programmingExerciseStudentParticipationRepository.saveAndFlush(participation);
        }
        else {
            return participation;
        }
    }

    /**
     * Ensures that all team students of a list of team participations are loaded from the database. If not, one database call for all participations is made to load the students.
     *
     * @param participations the team participations to load the students for
     */
    public void initializeTeamParticipations(List<StudentParticipation> participations) {
        List<Long> teamIds = new ArrayList<>();
        participations.forEach(participation -> {
            if (participation.getParticipant() instanceof Team team && !Hibernate.isInitialized(team.getStudents())) {
                teamIds.add(team.getId());
            }
        });
        if (teamIds.isEmpty()) {
            return;
        }
        Map<Long, Team> teamMap = teamRepository.findAllWithStudentsByIdIn(teamIds).stream().collect(Collectors.toMap(Team::getId, team -> team));
        participations.forEach(participation -> {
            if (participation.getParticipant() instanceof Team team) {
                team.setStudents(teamMap.get(team.getId()).getStudents());
            }
        });
    }

    /**
     * Get one participation (in any state) by its student and exercise.
     *
     * @param exercise the exercise for which to find a participation
     * @param username the username of the student
     * @return the participation of the given student and exercise in any state
     */
    public Optional<StudentParticipation> findOneByExerciseAndStudentLoginAnyState(Exercise exercise, String username) {
        if (exercise.isTeamMode()) {
            Optional<Team> optionalTeam = teamRepository.findOneByExerciseIdAndUserLogin(exercise.getId(), username);
            return optionalTeam.flatMap(team -> studentParticipationRepository.findOneByExerciseIdAndTeamId(exercise.getId(), team.getId()));
        }

        if (exercise.isTestExamExercise()) {
            return studentParticipationRepository.findFirstByExerciseIdAndStudentLoginOrderByIdDesc(exercise.getId(), username);
        }

        return studentParticipationRepository.findByExerciseIdAndStudentLogin(exercise.getId(), username);
    }

    /**
     * Get one graded participation (in any state) by its participant and exercise.
     *
     * @param exercise    the exercise for which to find a participation
     * @param participant the participant for which to find a participation
     * @return the graded participation of the given participant and exercise in any state
     */
    public Optional<StudentParticipation> findOneGradedByExerciseAndParticipant(Exercise exercise, Participant participant) {
        if (participant instanceof User user) {
            return studentParticipationRepository.findWithEagerSubmissionsByExerciseIdAndStudentLoginAndTestRun(exercise.getId(), user.getLogin(), false);
        }
        else if (participant instanceof Team team) {
            return studentParticipationRepository.findWithEagerSubmissionsAndTeamStudentsByExerciseIdAndTeamId(exercise.getId(), team.getId());
        }
        else {
            throw new Error("Unknown Participant type");
        }
    }

    /**
     * Get one practice participation (in any state) by its participant and exercise.
     *
     * @param exercise    the exercise for which to find a participation
     * @param participant the participant for which to find a participation
     * @return the practice participation of the given participant and exercise in any state
     */
    public Optional<StudentParticipation> findOnePracticeByExerciseAndParticipant(Exercise exercise, Participant participant) {
        if (participant instanceof User user) {
            return studentParticipationRepository.findWithEagerSubmissionsByExerciseIdAndStudentLoginAndTestRun(exercise.getId(), user.getLogin(), true);
        }
        else if (participant instanceof Team team) {
            return studentParticipationRepository.findWithEagerSubmissionsAndTeamStudentsByExerciseIdAndTeamId(exercise.getId(), team.getId());
        }
        else {
            throw new Error("Unknown Participant type");
        }
    }

    /**
     * Get one participation (in any state) by its student and exercise with all its results.
     *
     * @param exercise the exercise for which to find a participation
     * @param username the username of the student
     * @return the participation of the given student and exercise in any state
     */
    public Optional<StudentParticipation> findOneByExerciseAndStudentLoginAnyStateWithEagerResults(Exercise exercise, String username) {
        if (exercise.isTeamMode()) {
            Optional<Team> optionalTeam = teamRepository.findOneByExerciseIdAndUserLogin(exercise.getId(), username);
            return optionalTeam.flatMap(team -> studentParticipationRepository.findWithEagerResultsByExerciseIdAndTeamId(exercise.getId(), team.getId()));
        }
        return studentParticipationRepository.findWithEagerResultsByExerciseIdAndStudentLoginAndTestRun(exercise.getId(), username, false);
    }

    public StudentParticipation findOneByExerciseAndStudentLoginAnyStateWithEagerResultsElseThrow(Exercise exercise, String username) {
        return findOneByExerciseAndStudentLoginAnyStateWithEagerResults(exercise, username)
                .orElseThrow(() -> new EntityNotFoundException("Could not find a participation to exercise " + exercise.getId() + " and username " + username + "!"));
    }

    /**
     * Get one participation (in any state) by its student and exercise with eager submissions.
     *
     * @param exercise the exercise for which to find a participation
     * @param username the username of the student
     * @return the participation of the given student and exercise with eager submissions in any state
     */
    public Optional<StudentParticipation> findOneByExerciseAndStudentLoginWithEagerSubmissionsAnyState(Exercise exercise, String username) {
        if (exercise.isTeamMode()) {
            Optional<Team> optionalTeam = teamRepository.findOneByExerciseIdAndUserLogin(exercise.getId(), username);
            return optionalTeam.flatMap(team -> studentParticipationRepository.findWithEagerSubmissionsAndTeamStudentsByExerciseIdAndTeamId(exercise.getId(), team.getId()));
        }
        // If exercise is a test exam exercise we load the last participation, since there are multiple participations
        if (exercise.isTestExamExercise()) {
            return studentParticipationRepository.findLatestWithEagerSubmissionsByExerciseIdAndStudentLogin(exercise.getId(), username);
        }
        // After the effective due date (respecting individual extensions), prefer the practice participation
        Optional<StudentParticipation> gradedParticipation = studentParticipationRepository.findWithEagerSubmissionsByExerciseIdAndStudentLoginAndTestRun(exercise.getId(),
                username, false);
        ZonedDateTime effectiveDueDate = gradedParticipation.map(p -> p.getIndividualDueDate() != null ? p.getIndividualDueDate() : exercise.getDueDate())
                .orElse(exercise.getDueDate());
        if (effectiveDueDate != null && ZonedDateTime.now().isAfter(effectiveDueDate)) {
            Optional<StudentParticipation> practiceParticipation = studentParticipationRepository.findWithEagerSubmissionsByExerciseIdAndStudentLoginAndTestRun(exercise.getId(),
                    username, true);
            if (practiceParticipation.isPresent()) {
                return practiceParticipation;
            }
        }
        // For regular exam exercises (not test exams), if no graded participation was found,
        // fall back to looking for a test run participation. This handles the case where an
        // instructor performs a test run on a regular exam — their participation has testRun=true
        // but the exercise is not a test exam exercise, so it isn't caught by the check above.
        // Without this fallback, submissions during test runs fail with a "no participation found" error.
        // We use findLatest... to deterministically return the most recent participation when
        // multiple test runs exist for the same exercise.
        if (gradedParticipation.isEmpty() && exercise.isExamExercise() && !exercise.isTestExamExercise()) {
            return studentParticipationRepository.findLatestWithEagerSubmissionsByExerciseIdAndStudentLogin(exercise.getId(), username);
        }
        return gradedParticipation;
    }

    /**
     * Get all exercise participations belonging to exercise and student.
     *
     * @param exercise  the exercise
     * @param studentId the id of student
     * @return the list of exercise participations belonging to exercise and student
     */
    public List<StudentParticipation> findByExerciseAndStudentIdWithSubmissionsAndResults(Exercise exercise, Long studentId) {
        if (exercise.isTeamMode()) {
            return studentParticipationRepository.findAllWithTeamStudentsByExerciseIdAndTeamStudentIdWithSubmissionsAndResults(exercise.getId(), studentId);
        }
        return studentParticipationRepository.findByExerciseIdAndStudentIdWithEagerResultsAndSubmissions(exercise.getId(), studentId);
    }

    /**
     * Get all exercise participations belonging to exercise and student with eager submissions.
     *
     * @param exercise  the exercise
     * @param studentId the id of student
     * @return the list of exercise participations belonging to exercise and student
     */
    public List<StudentParticipation> findByExerciseAndStudentIdWithEagerSubmissions(Exercise exercise, Long studentId) {
        if (exercise.isTeamMode()) {
            Optional<Team> optionalTeam = teamRepository.findOneByExerciseIdAndUserId(exercise.getId(), studentId);
            return optionalTeam.map(team -> studentParticipationRepository.findByExerciseIdAndTeamIdWithEagerSubmissions(exercise.getId(), team.getId())).orElse(List.of());
        }
        return studentParticipationRepository.findByExerciseIdAndStudentIdWithEagerSubmissions(exercise.getId(), studentId);
    }

    /**
     * Retrieves a StudentParticipation with its latest Submission and associated Result.
     *
     * @param participationId The unique identifier of the participation to retrieve.
     * @return A StudentParticipation object containing the latest submission and result.
     * @throws EntityNotFoundException If no StudentParticipation is found with the given ID.
     */
    public StudentParticipation findExerciseParticipationWithLatestSubmissionAndResultElseThrow(Long participationId) throws EntityNotFoundException {
        Optional<Participation> participation = participationRepository.findByIdWithLatestSubmissionAndResult(participationId);
        if (participation.isEmpty() || !(participation.get() instanceof StudentParticipation studentParticipation)) {
            throw new EntityNotFoundException("No exercise participation found with id " + participationId);
        }
        return studentParticipation;
    }

    /**
     * Updates the individual due date for each given participation.
     * <p>
     * Only sets individual due dates if the exercise has a due date and the
     * individual due date is after this regular due date.
     *
     * @param exercise       the {@code participations} belong to.
     * @param participations for which the individual due date should be updated.
     * @return all participations where the individual due date actually changed.
     */
    public List<StudentParticipation> updateIndividualDueDates(final Exercise exercise, final List<StudentParticipation> participations) {
        final List<StudentParticipation> changedParticipations = new ArrayList<>();

        for (final StudentParticipation toBeUpdated : participations) {
            final Optional<StudentParticipation> optionalOriginalParticipation = studentParticipationRepository.findById(toBeUpdated.getId());
            if (optionalOriginalParticipation.isEmpty()) {
                continue;
            }
            final StudentParticipation originalParticipation = optionalOriginalParticipation.get();

            // individual due dates can only exist if the exercise has a due date
            // they also have to be after the exercise due date
            final ZonedDateTime newIndividualDueDate;
            if (exercise.getDueDate() == null || (toBeUpdated.getIndividualDueDate() != null && toBeUpdated.getIndividualDueDate().isBefore(exercise.getDueDate()))) {
                newIndividualDueDate = null;
            }
            else {
                newIndividualDueDate = toBeUpdated.getIndividualDueDate();
            }

            if (!Objects.equals(originalParticipation.getIndividualDueDate(), newIndividualDueDate)) {
                originalParticipation.setIndividualDueDate(newIndividualDueDate);
                changedParticipations.add(originalParticipation);
            }
        }

        return changedParticipations;
    }

    /**
     * Updates individual due dates for participations based on DTOs.
     * Similar to updateIndividualDueDates but accepts DTOs instead of entities.
     *
     * @param exercise       the exercise the participations belong to.
     * @param dueDateUpdates the DTOs containing participation IDs and new individual due dates.
     * @return all participations where the individual due date actually changed.
     */
    public List<StudentParticipation> updateIndividualDueDatesFromDTOs(final Exercise exercise, final List<ParticipationDueDateUpdateDTO> dueDateUpdates) {
        final List<StudentParticipation> changedParticipations = new ArrayList<>();

        for (final ParticipationDueDateUpdateDTO dto : dueDateUpdates) {
            final Optional<StudentParticipation> optionalOriginalParticipation = studentParticipationRepository.findById(dto.id());
            if (optionalOriginalParticipation.isEmpty()) {
                continue;
            }
            final StudentParticipation originalParticipation = optionalOriginalParticipation.get();

            final ZonedDateTime newIndividualDueDate;
            if (exercise.getDueDate() == null || (dto.individualDueDate() != null && dto.individualDueDate().isBefore(exercise.getDueDate()))) {
                newIndividualDueDate = null;
            }
            else {
                newIndividualDueDate = dto.individualDueDate();
            }

            if (!Objects.equals(originalParticipation.getIndividualDueDate(), newIndividualDueDate)) {
                originalParticipation.setIndividualDueDate(newIndividualDueDate);
                changedParticipations.add(originalParticipation);
            }
        }

        return changedParticipations;
    }

    /**
     * Finds all student participations for a given exercise with the latest submission and result, including the assessment note.
     *
     * @param exerciseId   the id of the exercise
     * @param teamExercise true if the exercise is a team exercise, false otherwise
     * @return a set of student participations with the latest submission and result, including the assessment note
     */
    public Set<StudentParticipation> findByExerciseIdWithLatestSubmissionResultAndAssessmentNote(long exerciseId, boolean teamExercise) {
        Set<StudentParticipation> participations = teamExercise ? studentParticipationRepository.findByExerciseIdWithLatestSubmissionWithTeamInformation(exerciseId)
                : studentParticipationRepository.findByExerciseIdWithLatestSubmission(exerciseId);
        Set<Long> submissionIds = participations.stream().flatMap(p -> p.getSubmissions().stream()).map(Submission::getId).filter(Objects::nonNull).collect(Collectors.toSet());

        if (submissionIds.isEmpty()) {
            return participations;
        }
        Set<Result> results = resultRepository.findLatestResultsWithAssessmentNoteBySubmissionIds(submissionIds);
        Map<Long, Result> resultBySubmissionId = results.stream().collect(Collectors.toMap(result -> result.getSubmission().getId(), Function.identity()));
        for (StudentParticipation participation : participations) {
            if (!participation.getSubmissions().isEmpty()) {
                Submission latestSubmission = participation.getSubmissions().iterator().next();
                Result latest = resultBySubmissionId.get(latestSubmission.getId());
                if (latest != null) {
                    latestSubmission.setResults(List.of(latest));
                }
                else {
                    latestSubmission.setResults(Collections.emptyList());
                }
            }
        }

        return participations;
    }

    /**
     * Returns a paginated list of {@link ParticipationManagementDTO} for the participation management view.
     * Uses a 3-step approach: paginated ID query → full entity load → DTO mapping.
     *
     * @param exercise the exercise to query
     * @param search   search parameters including pagination, sorting, search term, and filter
     * @return a page of ParticipationManagementDTO
     */
    public Page<ParticipationManagementDTO> findParticipationsForExercise(Exercise exercise, ParticipationSearchDTO search) {
        SortingOrder sortOrder = search.sortingOrder() != null ? search.sortingOrder() : SortingOrder.ASCENDING;
        Pageable pageable = PageRequest.of(search.page(), search.pageSize());
        boolean teamMode = exercise.isTeamMode();

        ZonedDateTime stuckBuildCutoff = null;
        if ("Failed".equals(search.filterProp()) && exercise instanceof ProgrammingExercise) {
            int timeoutSeconds = programmingExerciseRepository.findBuildTimeoutSecondsByExerciseId(exercise.getId()).filter(t -> t > 0).orElse(120);
            stuckBuildCutoff = ZonedDateTime.now().minusSeconds(timeoutSeconds);
        }

        Page<Long> idPage = studentParticipationRepository.findParticipationIdsForManagement(exercise.getId(), teamMode, search.searchTerm(), search.filterProp(), stuckBuildCutoff,
                pageable, sortOrder, search.sortedColumn());

        List<Long> ids = idPage.getContent();
        if (ids.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), pageable, idPage.getTotalElements());
        }

        List<StudentParticipation> participations = teamMode ? studentParticipationRepository.findByIdsWithLatestSubmissionWithTeamInformation(ids)
                : studentParticipationRepository.findByIdsWithLatestSubmission(ids);

        Map<Long, Integer> submissionCountMap = studentParticipationRepository.countSubmissionsPerParticipationByIdsAsMap(ids);

        Map<Long, StudentParticipation> participationById = participations.stream().collect(Collectors.toMap(p -> p.getId(), Function.identity()));
        List<ParticipationManagementDTO> dtos = ids.stream().map(participationById::get).filter(Objects::nonNull).map(p -> mapToManagementDTO(p, submissionCountMap)).toList();

        return new PageImpl<>(dtos, pageable, idPage.getTotalElements());
    }

    private ParticipationManagementDTO mapToManagementDTO(StudentParticipation participation, Map<Long, Integer> submissionCountMap) {
        String participantName;
        String participantIdentifier;
        Long studentId = null;
        String studentLogin = null;
        Long teamId = null;
        List<UserNameAndLoginDTO> teamStudents = null;

        if (participation.getStudent().isPresent()) {
            User student = participation.getStudent().get();
            participantName = student.getName();
            participantIdentifier = student.getLogin();
            studentId = student.getId();
            studentLogin = student.getLogin();
        }
        else if (participation.getTeam().isPresent()) {
            Team team = participation.getTeam().get();
            participantName = team.getName();
            participantIdentifier = team.getShortName();
            teamId = team.getId();
            teamStudents = team.getStudents().stream().map(s -> new UserNameAndLoginDTO(s.getName(), s.getLogin())).toList();
        }
        else {
            participantName = null;
            participantIdentifier = null;
        }

        Submission latestSubmission = participation.getSubmissions().isEmpty() ? null : participation.getSubmissions().iterator().next();
        Boolean buildFailed = null;
        if (latestSubmission instanceof ProgrammingSubmission progSubmission) {
            buildFailed = progSubmission.isBuildFailed();
        }

        Boolean lastResultIsManual = null;
        if (latestSubmission != null && !latestSubmission.getResults().isEmpty()) {
            Result latestResult = latestSubmission.getResults().stream().filter(r -> r.getId() != null).max((r1, r2) -> Long.compare(r1.getId(), r2.getId())).orElse(null);
            if (latestResult != null && latestResult.getAssessmentType() != null) {
                lastResultIsManual = latestResult.getAssessmentType() != AssessmentType.AUTOMATIC && latestResult.getAssessmentType() != AssessmentType.AUTOMATIC_ATHENA;
            }
        }

        String buildPlanId = null;
        String repositoryUri = null;
        if (participation instanceof ProgrammingExerciseStudentParticipation progParticipation) {
            buildPlanId = progParticipation.getBuildPlanId();
            repositoryUri = progParticipation.getRepositoryUri();
        }

        int submissionCount = submissionCountMap.getOrDefault(participation.getId(), 0);
        boolean testRun = Boolean.TRUE.equals(participation.isTestRun());

        return new ParticipationManagementDTO(participation.getId(), participation.getInitializationState(), participation.getInitializationDate(), submissionCount,
                participantName, participantIdentifier, studentId, studentLogin, teamId, teamStudents, testRun, participation.getPresentationScore(),
                participation.getIndividualDueDate(), buildPlanId, repositoryUri, buildFailed, lastResultIsManual);
    }

    /**
     * Finds participation scores for a given exercise using server-side pagination and filtering.
     * <p>
     * This method performs a three-step query:
     * 1. ID query — paginated, filtered participation IDs via Criteria API (no expensive LEFT JOINs)
     * 2. Data query — loads full entity data for the page-sized set of IDs (FETCH JOINs, bounded)
     * 3. DTO mapping — maps entities to flat DTOs for the REST response
     *
     * @param exercise the exercise to query
     * @param search   the search parameters including pagination, sorting, search term, filter, and score range
     * @return a page of ParticipationScoreDTO
     */
    public Page<ParticipationScoreDTO> findParticipationScoresForExercise(Exercise exercise, ParticipationScoreSearchDTO search) {
        SortingOrder sortOrder = search.sortingOrder() != null ? search.sortingOrder() : SortingOrder.ASCENDING;
        Pageable pageable = PageRequest.of(search.page(), search.pageSize());
        boolean teamMode = exercise.isTeamMode();

        // Step 1: Get paginated participation IDs with filters
        Page<Long> idPage = studentParticipationRepository.findParticipationIdsForScores(exercise.getId(), teamMode, search.searchTerm(), search.filterProp(),
                search.scoreRangeLower(), search.scoreRangeUpper(), pageable, sortOrder, search.sortedColumn());
        List<Long> ids = idPage.getContent();
        if (ids.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), pageable, idPage.getTotalElements());
        }

        // Step 2: Load full entity data for those IDs
        List<StudentParticipation> participations = teamMode ? studentParticipationRepository.findByIdsWithLatestSubmissionWithTeamInformation(ids)
                : studentParticipationRepository.findByIdsWithLatestSubmission(ids);

        // Load latest results with assessment notes
        Set<Long> submissionIds = participations.stream().flatMap(p -> p.getSubmissions().stream()).map(Submission::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Result> resultBySubmissionId = Map.of();
        if (!submissionIds.isEmpty()) {
            Set<Result> results = resultRepository.findLatestResultsWithAssessmentNoteBySubmissionIds(submissionIds);
            resultBySubmissionId = results.stream().collect(Collectors.toMap(result -> result.getSubmission().getId(), Function.identity()));
        }

        // Load submission counts for these IDs
        Map<Long, Integer> submissionCountMap = studentParticipationRepository.countSubmissionsPerParticipationByIdsAsMap(ids);

        // Step 3: Map to DTOs, preserving the ID query order
        Map<Long, StudentParticipation> participationById = participations.stream().collect(Collectors.toMap(p -> p.getId(), Function.identity()));
        final Map<Long, Result> finalResultMap = resultBySubmissionId;
        List<ParticipationScoreDTO> dtos = ids.stream().map(participationById::get).filter(Objects::nonNull).map(p -> mapToDTO(p, submissionCountMap, finalResultMap)).toList();

        return new PageImpl<>(dtos, pageable, idPage.getTotalElements());
    }

    private ParticipationScoreDTO mapToDTO(StudentParticipation participation, Map<Long, Integer> submissionCountMap, Map<Long, Result> resultBySubmissionId) {
        // Participant info
        String participantName;
        String participantIdentifier;
        Long studentId = null;
        Long teamId = null;

        if (participation.getStudent().isPresent()) {
            User student = participation.getStudent().get();
            participantName = student.getName();
            participantIdentifier = student.getLogin();
            studentId = student.getId();
        }
        else if (participation.getTeam().isPresent()) {
            Team team = participation.getTeam().get();
            participantName = team.getName();
            participantIdentifier = team.getShortName();
            teamId = team.getId();
        }
        else {
            participantName = null;
            participantIdentifier = null;
        }

        // Latest submission & result
        Submission latestSubmission = participation.getSubmissions().isEmpty() ? null : participation.getSubmissions().iterator().next();
        Result latestResult = (latestSubmission != null && latestSubmission.getId() != null) ? resultBySubmissionId.get(latestSubmission.getId()) : null;

        Long resultId = latestResult != null ? latestResult.getId() : null;
        Double score = latestResult != null ? latestResult.getScore() : null;
        Boolean successful = latestResult != null ? latestResult.isSuccessful() : null;
        ZonedDateTime completionDate = latestResult != null ? latestResult.getCompletionDate() : null;
        AssessmentType assessmentType = latestResult != null ? latestResult.getAssessmentType() : null;
        String assessmentNote = (latestResult != null && latestResult.getAssessmentNote() != null) ? latestResult.getAssessmentNote().getNote() : null;

        // Duration in seconds
        long durationInSeconds = 0;
        if (completionDate != null && participation.getInitializationDate() != null) {
            durationInSeconds = Duration.between(participation.getInitializationDate(), completionDate).getSeconds();
        }

        // Submission info
        Long submissionId = latestSubmission != null ? latestSubmission.getId() : null;
        Boolean buildFailed = null;
        if (latestSubmission instanceof ProgrammingSubmission progSubmission) {
            buildFailed = progSubmission.isBuildFailed();
        }

        // Programming participation info
        String buildPlanId = null;
        String repositoryUri = null;
        if (participation instanceof ProgrammingExerciseStudentParticipation progParticipation) {
            buildPlanId = progParticipation.getBuildPlanId();
            repositoryUri = progParticipation.getRepositoryUri();
        }

        boolean testRun = Boolean.TRUE.equals(participation.isTestRun());
        int submissionCount = submissionCountMap.getOrDefault(participation.getId(), 0);

        return new ParticipationScoreDTO(participation.getId(), participation.getInitializationDate(), submissionCount, participantName, participantIdentifier, studentId, teamId,
                resultId, score, successful, completionDate, assessmentType, assessmentNote, durationInSeconds, submissionId, buildFailed, buildPlanId, repositoryUri, testRun);
    }

    /**
     * Returns participant identity data for all participations in the given exercise.
     *
     * @param exercise the exercise to query
     * @return list of {@link ParticipationNameExportDTO}, one per participation
     */
    public List<ParticipationNameExportDTO> getParticipationNamesForExport(Exercise exercise) {
        if (exercise.isTeamMode()) {
            return studentParticipationRepository.findWithTeamInformationByExerciseId(exercise.getId()).stream().map(p -> {
                Team team = p.getTeam().orElse(null);
                if (team == null) {
                    return null;
                }
                List<String> studentNames = team.getStudents() != null ? team.getStudents().stream().map(User::getName).filter(Objects::nonNull).sorted().toList() : List.of();
                return new ParticipationNameExportDTO(team.getName(), team.getShortName(), studentNames);
            }).filter(Objects::nonNull).toList();
        }
        else {
            return studentParticipationRepository.findWithStudentByExerciseId(exercise.getId()).stream().map(p -> {
                User student = p.getStudent().orElse(null);
                if (student == null) {
                    return null;
                }
                return new ParticipationNameExportDTO(student.getName(), student.getLogin(), null);
            }).filter(Objects::nonNull).toList();
        }
    }
}
