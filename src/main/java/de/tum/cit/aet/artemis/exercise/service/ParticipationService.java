package de.tum.cit.aet.artemis.exercise.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

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
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participant;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.cit.aet.artemis.programming.service.ParticipationVcsAccessTokenService;
import de.tum.cit.aet.artemis.programming.service.UriService;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.service.vcs.VersionControlService;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;

/**
 * Service Implementation for managing Participation.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class ParticipationService {

    @Value("${artemis.version-control.default-branch:main}")
    protected String defaultBranch;

    // private final Optional<ContinuousIntegrationService> continuousIntegrationService;

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

    public ParticipationService(Optional<VersionControlService> versionControlService, ParticipationRepository participationRepository,
            StudentParticipationRepository studentParticipationRepository, ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository,
            ProgrammingExerciseRepository programmingExerciseRepository, SubmissionRepository submissionRepository, TeamRepository teamRepository, UriService uriService,
            ParticipationVcsAccessTokenService participationVCSAccessTokenService, ResultRepository resultRepository) {
        // this.continuousIntegrationService = continuousIntegrationService;
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
            optionalStudentParticipation = findOneByExerciseAndParticipantAnyState(exercise, participant);
            if (optionalStudentParticipation.isPresent() && optionalStudentParticipation.get().isPracticeMode() && exercise.isCourseExercise()) {
                // In case there is already a practice participation, set it to inactive
                optionalStudentParticipation.get().setInitializationState(InitializationState.INACTIVE);
                studentParticipationRepository.saveAndFlush(optionalStudentParticipation.get());

                optionalStudentParticipation = findOneByExerciseAndParticipantAnyStateAndTestRun(exercise, participant, false);
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
                // Only for quiz exercises, the participation status FINISHED should not be overwritten since the user must not change his submission once submitted
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
        // participation = copyBuildPlan(participation);
        // Step 2b) configure the build plan (e.g. access right, hooks, etc.)
        // participation = configureBuildPlan(participation);
        // Step 3a) Set the InitializationState to initialized to indicate, the programming exercise is ready
        participation.setInitializationState(InitializationState.INITIALIZED);
        // after saving, we need to make sure the object that is used after the if statement is the right one
        return participation;
    }

    /**
     * This method is triggered when a student starts the practice mode of a programming exercise. It creates a Participation which connects the corresponding student and exercise.
     * Additionally, it configures repository / build plan related stuff.
     *
     * @param exercise                           the exercise which is started, a programming exercise needs to have the template and solution participation eagerly loaded
     * @param participant                        the user or team who starts the exercise
     * @param optionalGradedStudentParticipation the optional graded participation before the due date
     * @param useGradedParticipation             flag if the graded student participation should be used as baseline for the new repository
     * @return the participation connecting the given exercise and user
     */
    public StudentParticipation startPracticeMode(Exercise exercise, Participant participant, Optional<StudentParticipation> optionalGradedStudentParticipation,
            boolean useGradedParticipation) {
        if (!(exercise instanceof ProgrammingExercise)) {
            throw new IllegalStateException("Only programming exercises support the practice mode at the moment");
        }

        optionalGradedStudentParticipation.ifPresent(participation -> {
            participation.setInitializationState(InitializationState.FINISHED);
            participationRepository.save(participation);
        });
        Optional<StudentParticipation> optionalStudentParticipation = findOneByExerciseAndParticipantAnyStateAndTestRun(exercise, participant, true);
        StudentParticipation participation;
        if (optionalStudentParticipation.isEmpty()) {
            // create a new participation only if no participation can be found
            participation = new ProgrammingExerciseStudentParticipation(defaultBranch);
            participation.setInitializationState(InitializationState.UNINITIALIZED);
            participation.setExercise(exercise);
            participation.setParticipant(participant);
            participation.setPracticeMode(true);
            participation = studentParticipationRepository.saveAndFlush(participation);
            if (participant instanceof User user) {
                participationVCSAccessTokenService.createParticipationVCSAccessToken(user, participation);
            }
        }
        else {
            // make sure participation and exercise are connected
            participation = optionalStudentParticipation.get();
            participation.setExercise(exercise);
        }

        ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exercise.getId());
        participation = startPracticeMode(programmingExercise, (ProgrammingExerciseStudentParticipation) participation, optionalGradedStudentParticipation, useGradedParticipation);

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
        Optional<StudentParticipation> optionalStudentParticipation = findOneByExerciseAndParticipantAnyState(exercise, participant);
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
            // Note: we need a repository, otherwise the student would not be possible to click resume (in case he wants to further participate after the due date)
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
        // participation = copyBuildPlan(participation);
        // Step 2b) configure the build plan (e.g. access right, hooks, etc.)
        // participation = configureBuildPlan(participation);
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

    // private ProgrammingExerciseStudentParticipation copyBuildPlan(ProgrammingExerciseStudentParticipation participation) {
    // // only execute this step if it has not yet been completed yet or if the build plan id is missing for some reason
    // if (!participation.getInitializationState().hasCompletedState(InitializationState.BUILD_PLAN_COPIED) || participation.getBuildPlanId() == null) {
    // final var exercise = participation.getProgrammingExercise();
    // final var planName = BuildPlanType.TEMPLATE.getName();
    // final var username = participation.getParticipantIdentifier();
    // final var buildProjectName = participation.getExercise().getCourseViaExerciseGroupOrCourseMember().getShortName().toUpperCase() + " "
    // + participation.getExercise().getTitle();
    // final var targetPlanName = participation.addPracticePrefixIfTestRun(username.toUpperCase());
    // // the next action includes recovery, which means if the build plan has already been copied, we simply retrieve the build plan id and do not copy it again
    // final var buildPlanId = continuousIntegrationService.orElseThrow().copyBuildPlan(exercise, planName, exercise, buildProjectName, targetPlanName, true);
    // participation.setBuildPlanId(buildPlanId);
    // participation.setInitializationState(InitializationState.BUILD_PLAN_COPIED);
    // return programmingExerciseStudentParticipationRepository.saveAndFlush(participation);
    // }
    // else {
    // return participation;
    // }
    // }
    //
    // private ProgrammingExerciseStudentParticipation configureBuildPlan(ProgrammingExerciseStudentParticipation participation) {
    // if (!participation.getInitializationState().hasCompletedState(InitializationState.BUILD_PLAN_CONFIGURED)) {
    // try {
    // continuousIntegrationService.orElseThrow().configureBuildPlan(participation);
    // }
    // catch (ContinuousIntegrationException ex) {
    // // this means something with the configuration of the build plan is wrong.
    // // we try to recover from typical edge cases by setting the initialization state back, so that the previous action (copy build plan) is tried again, when
    // // the user again clicks on the start / resume exercise button.
    // participation.setInitializationState(InitializationState.REPO_CONFIGURED);
    // programmingExerciseStudentParticipationRepository.saveAndFlush(participation);
    // // rethrow
    // throw ex;
    // }
    // participation.setInitializationState(InitializationState.BUILD_PLAN_CONFIGURED);
    // return programmingExerciseStudentParticipationRepository.saveAndFlush(participation);
    // }
    // else {
    // return participation;
    // }
    // }

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
     * Get one participation (in any state) by its participant and exercise.
     *
     * @param exercise    the exercise for which to find a participation
     * @param participant the participant for which to find a participation
     * @return the participation of the given participant and exercise in any state
     */
    public Optional<StudentParticipation> findOneByExerciseAndParticipantAnyState(Exercise exercise, Participant participant) {
        if (participant instanceof User user) {
            return studentParticipationRepository.findWithEagerSubmissionsByExerciseIdAndStudentLogin(exercise.getId(), user.getLogin());
        }
        else if (participant instanceof Team team) {
            return studentParticipationRepository.findWithEagerSubmissionsAndTeamStudentsByExerciseIdAndTeamId(exercise.getId(), team.getId());
        }
        else {
            throw new Error("Unknown Participant type");
        }
    }

    /**
     * Get one participation (in any state) by its participant and exercise.
     *
     * @param exercise    the exercise for which to find a participation
     * @param participant the short name of the team
     * @param testRun     the indicator if it should be a testRun participation
     * @return the participation of the given team and exercise in any state
     */
    public Optional<StudentParticipation> findOneByExerciseAndParticipantAnyStateAndTestRun(Exercise exercise, Participant participant, boolean testRun) {
        if (participant instanceof User user) {
            return studentParticipationRepository.findWithEagerSubmissionsByExerciseIdAndStudentLoginAndTestRun(exercise.getId(), user.getLogin(), testRun);
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
        return studentParticipationRepository.findWithEagerSubmissionsByExerciseIdAndStudentLogin(exercise.getId(), username);
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

}
