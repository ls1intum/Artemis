package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.domain.enumeration.InitializationState.*;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.*;
import de.tum.in.www1.artemis.domain.participation.*;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.exception.ContinuousIntegrationException;
import de.tum.in.www1.artemis.exception.VersionControlException;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.repository.hestia.CoverageReportRepository;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.connectors.VersionControlService;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Service Implementation for managing Participation.
 */
@Service
public class ParticipationService {

    private final Logger log = LoggerFactory.getLogger(ParticipationService.class);

    private final GitService gitService;

    private final Optional<ContinuousIntegrationService> continuousIntegrationService;

    private final Optional<VersionControlService> versionControlService;

    private final ParticipationRepository participationRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final SubmissionRepository submissionRepository;

    private final TeamRepository teamRepository;

    private final UrlService urlService;

    private final ResultService resultService;

    private final CoverageReportRepository coverageReportRepository;

    private final BuildLogStatisticsEntryRepository buildLogStatisticsEntryRepository;

    private final ParticipantScoreRepository participantScoreRepository;

    private final StudentScoreRepository studentScoreRepository;

    private final TeamScoreRepository teamScoreRepository;

    public ParticipationService(GitService gitService, Optional<ContinuousIntegrationService> continuousIntegrationService, Optional<VersionControlService> versionControlService,
            ParticipationRepository participationRepository, StudentParticipationRepository studentParticipationRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            SubmissionRepository submissionRepository, TeamRepository teamRepository, UrlService urlService, ResultService resultService,
            CoverageReportRepository coverageReportRepository, BuildLogStatisticsEntryRepository buildLogStatisticsEntryRepository,
            ParticipantScoreRepository participantScoreRepository, StudentScoreRepository studentScoreRepository, TeamScoreRepository teamScoreRepository) {
        this.gitService = gitService;
        this.continuousIntegrationService = continuousIntegrationService;
        this.versionControlService = versionControlService;
        this.participationRepository = participationRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.submissionRepository = submissionRepository;
        this.teamRepository = teamRepository;
        this.urlService = urlService;
        this.resultService = resultService;
        this.coverageReportRepository = coverageReportRepository;
        this.buildLogStatisticsEntryRepository = buildLogStatisticsEntryRepository;
        this.participantScoreRepository = participantScoreRepository;
        this.studentScoreRepository = studentScoreRepository;
        this.teamScoreRepository = teamScoreRepository;
    }

    /**
     * This method is triggered when a student starts an exercise. It creates a Participation which connects the corresponding student and exercise. Additionally, it configures
     * repository / build plan related stuff for programming exercises. In the case of modeling or text exercises, it also initializes and stores the corresponding submission.
     *
     * @param exercise                the exercise which is started, a programming exercise needs to have the template and solution participation eagerly loaded
     * @param participant             the user or team who starts the exercise
     * @param createInitialSubmission whether an initial empty submission should be created for text, modeling, quiz, file-upload or not
     * @return the participation connecting the given exercise and user
     */
    public StudentParticipation startExercise(Exercise exercise, Participant participant, boolean createInitialSubmission) {
        return startExerciseWithInitializationDate(exercise, participant, createInitialSubmission, null);
    }

    /**
     * This method is called when an StudentExam for a test exam is set up for conduction.
     * It creates a Participation which connects the corresponding student and exercise. The test exam is linked with the initializationDate = startedDate (StudentExam)
     * Additionally, it configures repository / build plan related stuff for programming exercises.
     * In the case of modeling or text exercises, it also initializes and stores the corresponding submission.
     *
     * @param exercise                - the exercise for which a new participation is to be created
     * @param participant             - the user for which the new participation is to be created
     * @param createInitialSubmission - whether an initial empty submission should be created for text, modeling, quiz, file-upload or not
     * @param initializationDate      - the date which should be set as the initializationDate of the Participation. Links studentExam <-> participation
     * @return a new participation for the given exercise and user
     */
    // TODO: Stephan Krusche: offer this method again like above "startExercise" without initializationDate which is not really necessary at the moment, because we only support on
    // test exam per exam/student
    public StudentParticipation startExerciseWithInitializationDate(Exercise exercise, Participant participant, boolean createInitialSubmission, ZonedDateTime initializationDate) {
        // common for all exercises
        // Check if participation already exists
        Optional<StudentParticipation> optionalStudentParticipation = findOneByExerciseAndParticipantAnyState(exercise, participant);
        StudentParticipation participation;
        if (optionalStudentParticipation.isEmpty()) {
            participation = createNewParticipationWithInitializationDate(exercise, participant, initializationDate);
        }
        else {
            // make sure participation and exercise are connected
            participation = optionalStudentParticipation.get();
            participation.setExercise(exercise);
        }

        if (exercise instanceof ProgrammingExercise) {
            // fetch again to get additional objects
            var programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exercise.getId());
            participation = startProgrammingExercise(programmingExercise, (ProgrammingExerciseStudentParticipation) participation, initializationDate == null);
        }
        else {// for all other exercises: QuizExercise, ModelingExercise, TextExercise, FileUploadExercise
            if (participation.getInitializationState() == null || participation.getInitializationState() == UNINITIALIZED
                    || participation.getInitializationState() == FINISHED && !(exercise instanceof QuizExercise)) {
                // in case the participation was finished before, we set it to initialized again so that the user sees the correct button "Open modeling editor" on the client side.
                // Only for quiz exercises, the participation status FINISHED should not be overwritten since the user must not change his submission once submitted
                participation.setInitializationState(INITIALIZED);
            }

            if (Optional.ofNullable(participation.getInitializationDate()).isEmpty()) {
                participation.setInitializationDate(ZonedDateTime.now());
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
        return studentParticipationRepository.saveAndFlush(participation);
    }

    /**
     * Helper Method to create a new Participation for the
     *
     * @param exercise           the exercise for which a participation should be created
     * @param participant        the participant for the participation
     * @param initializationDate (optional) Value for the initializationDate of the Participation
     * @return a StudentParticipation for the exercise and participant with an optional specified initializationDate
     */
    private StudentParticipation createNewParticipationWithInitializationDate(Exercise exercise, Participant participant, ZonedDateTime initializationDate) {
        StudentParticipation participation;
        // create a new participation only if no participation can be found
        if (exercise instanceof ProgrammingExercise) {
            participation = new ProgrammingExerciseStudentParticipation(versionControlService.get().getDefaultBranchOfArtemis());
        }
        else {
            participation = new StudentParticipation();
        }
        participation.setInitializationState(UNINITIALIZED);
        participation.setExercise(exercise);
        participation.setParticipant(participant);
        // StartedDate is used to link a Participation to a test exam exercise
        if (initializationDate != null) {
            participation.setInitializationDate(initializationDate);
        }
        return studentParticipationRepository.saveAndFlush(participation);
    }

    /**
     * Start a programming exercise participation (which does not exist yet) by creating and configuring a student git repository (step 1) and a student build plan (step 2)
     * based on the templates in the given programming exercise
     *
     * @param exercise              the programming exercise that the currently active user (student) wants to start
     * @param participation         inactive participation
     * @param setInitializationDate flag if the InitializationDate should be set to the current time
     * @return started participation
     */
    private StudentParticipation startProgrammingExercise(ProgrammingExercise exercise, ProgrammingExerciseStudentParticipation participation, boolean setInitializationDate) {
        // Step 1a) create the student repository (based on the template repository)
        participation = copyRepository(exercise, participation);
        // Step 1b) configure the student repository (e.g. access right, etc.)
        participation = configureRepository(exercise, participation);
        // Step 2a) create the build plan (based on the BASE build plan)
        participation = copyBuildPlan(participation);
        // Step 2b) configure the build plan (e.g. access right, hooks, etc.)
        participation = configureBuildPlan(participation);
        // Step 2c) we might need to perform an empty commit (as a workaround, depending on the CI system) here, because it should not trigger a new programming submission
        // (when the web hook was already initialized, see below)
        continuousIntegrationService.get().performEmptySetupCommit(participation);
        // Note: we configure the repository webhook last, so that the potential empty commit does not trigger a new programming submission (see empty-commit-necessary)
        // Step 3) configure the web hook of the student repository
        participation = configureRepositoryWebHook(participation);
        // Step 4a) Set the InitializationState to initialized to indicate, the programming exercise is ready
        participation.setInitializationState(INITIALIZED);
        // Step 4b) Set the InitializationDate to the current time
        if (setInitializationDate) {
            // Note: For test exams, the InitializationDate is set to the StudentExam: startedDate in {#link #startExerciseWithInitializationDate}
            participation.setInitializationDate(ZonedDateTime.now());
        }
        // after saving, we need to make sure the object that is used after the if statement is the right one
        return participation;
    }

    /**
     * This method is triggered when a student starts the practice mode of a programming exercise. It creates a Participation which connects the corresponding student and exercise. Additionally, it configures
     * repository / build plan related stuff.
     *
     * @param exercise the exercise which is started, a programming exercise needs to have the template and solution participation eagerly loaded
     * @param participant the user or team who starts the exercise
     * @param optionalGradedStudentParticipation the optional graded participation before the deadline
     * @return the participation connecting the given exercise and user
     */
    public StudentParticipation startPracticeMode(Exercise exercise, Participant participant, Optional<StudentParticipation> optionalGradedStudentParticipation) {
        if (!(exercise instanceof ProgrammingExercise programmingExercise)) {
            throw new IllegalStateException("Only programming exercises support the practice mode at the moment");
        }

        optionalGradedStudentParticipation.ifPresent(participation -> {
            participation.setInitializationState(FINISHED);
            participationRepository.save(participation);
        });
        Optional<StudentParticipation> optionalStudentParticipation = findOneByExerciseAndParticipantAnyStateAndTestRun(exercise, participant, true);
        StudentParticipation participation;
        if (optionalStudentParticipation.isEmpty()) {
            // create a new participation only if no participation can be found
            participation = new ProgrammingExerciseStudentParticipation(versionControlService.get().getDefaultBranchOfArtemis());
            participation.setInitializationState(UNINITIALIZED);
            participation.setExercise(exercise);
            participation.setParticipant(participant);
            participation.setTestRun(true);
            participation = studentParticipationRepository.saveAndFlush(participation);
        }
        else {
            // make sure participation and exercise are connected
            participation = optionalStudentParticipation.get();
            participation.setExercise(exercise);
        }

        programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exercise.getId());
        participation = startProgrammingExercise(programmingExercise, (ProgrammingExerciseStudentParticipation) participation, true);

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
                participation = new ProgrammingExerciseStudentParticipation(versionControlService.get().getDefaultBranchOfArtemis());
            }
            else {
                participation = new StudentParticipation();
            }
            participation.setInitializationState(UNINITIALIZED);
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
            // Note: we need a repository, otherwise the student would not be possible to click resume (in case he wants to further participate after the deadline)
            programmingParticipation = copyRepository(programmingExercise, programmingParticipation);
            programmingParticipation = configureRepository(programmingExercise, programmingParticipation);
            programmingParticipation = configureRepositoryWebHook(programmingParticipation);
            participation = programmingParticipation;
            if (programmingExercise.getBuildAndTestStudentSubmissionsAfterDueDate() != null || programmingExercise.getAssessmentType() != AssessmentType.AUTOMATIC
                    || programmingExercise.getAllowComplaintsForAutomaticAssessments()) {
                // restrict access for the student
                try {
                    versionControlService.get().setRepositoryPermissionsToReadOnly(programmingParticipation.getVcsRepositoryUrl(), programmingExercise.getProjectKey(),
                            programmingParticipation.getStudents());
                }
                catch (VersionControlException e) {
                    log.error("Removing write permissions failed for programming exercise with id {} for student repository with participation id {}: {}",
                            programmingExercise.getId(), programmingParticipation.getId(), e.getMessage());
                }
            }
        }

        participation.setInitializationState(FINISHED);
        participation = studentParticipationRepository.saveAndFlush(participation);

        // Take the latest submission or initialize a new empty submission
        var studentParticipation = studentParticipationRepository.findByIdWithLegalSubmissionsElseThrow(participation.getId());
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
        participation.setInitializationState(INITIALIZED);
        participation = programmingExerciseStudentParticipationRepository.saveAndFlush(participation);
        if (participation.getInitializationDate() == null) {
            // only set the date if it was not set before (which should NOT be the case)
            participation.setInitializationDate(ZonedDateTime.now());
        }
        return programmingExerciseStudentParticipationRepository.saveAndFlush(participation);
    }

    private ProgrammingExerciseStudentParticipation copyRepository(ProgrammingExercise programmingExercise, ProgrammingExerciseStudentParticipation participation) {
        // only execute this step if it has not yet been completed yet or if the repository url is missing for some reason
        if (!participation.getInitializationState().hasCompletedState(InitializationState.REPO_COPIED) || participation.getVcsRepositoryUrl() == null) {
            final var projectKey = programmingExercise.getProjectKey();
            final var repoName = participation.addPotentialPracticePrefix(participation.getParticipantIdentifier());
            // NOTE: we have to get the repository slug of the template participation here, because not all exercises (in particular old ones) follow the naming conventions
            final var templateRepoName = urlService.getRepositorySlugFromRepositoryUrl(programmingExercise.getTemplateParticipation().getVcsRepositoryUrl());
            String templateBranch = versionControlService.get().getOrRetrieveBranchOfExercise(programmingExercise);
            // the next action includes recovery, which means if the repository has already been copied, we simply retrieve the repository url and do not copy it again
            var newRepoUrl = versionControlService.get().copyRepository(projectKey, templateRepoName, templateBranch, projectKey, repoName);
            // add the userInfo part to the repoURL only if the participation belongs to a single student (and not a team of students)
            if (participation.getStudent().isPresent()) {
                newRepoUrl = newRepoUrl.withUser(participation.getParticipantIdentifier());
            }
            participation.setRepositoryUrl(newRepoUrl.toString());
            participation.setInitializationState(REPO_COPIED);

            return programmingExerciseStudentParticipationRepository.saveAndFlush(participation);
        }
        else {
            return participation;
        }
    }

    private ProgrammingExerciseStudentParticipation configureRepository(ProgrammingExercise exercise, ProgrammingExerciseStudentParticipation participation) {
        if (!participation.getInitializationState().hasCompletedState(InitializationState.REPO_CONFIGURED)) {
            // do not allow the student to access the repository if this is an exam exercise that has not started yet
            boolean allowAccess = !exercise.isExamExercise() || ZonedDateTime.now().isAfter(exercise.getIndividualReleaseDate());
            versionControlService.get().configureRepository(exercise, participation, allowAccess);
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
            final var projectKey = participation.getProgrammingExercise().getProjectKey();
            final var planName = BuildPlanType.TEMPLATE.getName();
            final var username = participation.getParticipantIdentifier();
            final var buildProjectName = participation.getExercise().getCourseViaExerciseGroupOrCourseMember().getShortName().toUpperCase() + " "
                    + participation.getExercise().getTitle();
            final var targetPlanName = participation.addPotentialPracticePrefix(username.toUpperCase());
            // the next action includes recovery, which means if the build plan has already been copied, we simply retrieve the build plan id and do not copy it again
            final var buildPlanId = continuousIntegrationService.get().copyBuildPlan(projectKey, planName, projectKey, buildProjectName, targetPlanName, true);
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
                String branch = versionControlService.get().getOrRetrieveBranchOfStudentParticipation(participation);
                continuousIntegrationService.get().configureBuildPlan(participation, branch);
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

    private ProgrammingExerciseStudentParticipation configureRepositoryWebHook(ProgrammingExerciseStudentParticipation participation) {
        if (!participation.getInitializationState().hasCompletedState(InitializationState.INITIALIZED)) {
            versionControlService.get().addWebHookForParticipation(participation);
        }
        return participation;
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
        return studentParticipationRepository.findByExerciseIdAndStudentLogin(exercise.getId(), username);
    }

    /**
     * Get one participation (in any state) by its participant and exercise.
     *
     * @param exercise the exercise for which to find a participation
     * @param participant the participant for which to find a participation
     * @return the participation of the given participant and exercise in any state
     */
    public Optional<StudentParticipation> findOneByExerciseAndParticipantAnyState(Exercise exercise, Participant participant) {
        if (participant instanceof User user) {
            return studentParticipationRepository.findWithEagerLegalSubmissionsByExerciseIdAndStudentLogin(exercise.getId(), user.getLogin());
        }
        else if (participant instanceof Team team) {
            return studentParticipationRepository.findWithEagerLegalSubmissionsByExerciseIdAndTeamId(exercise.getId(), team.getId());
        }
        else {
            throw new Error("Unknown Participant type");
        }
    }

    /**
     * Get one participation (in any state) by its participant and exercise.
     *
     * @param exercise the exercise for which to find a participation
     * @param participant the short name of the team
     * @param testRun the indicator if it should be a testRun participation
     * @return the participation of the given team and exercise in any state
     */
    public Optional<StudentParticipation> findOneByExerciseAndParticipantAnyStateAndTestRun(Exercise exercise, Participant participant, boolean testRun) {
        if (participant instanceof User user) {
            return studentParticipationRepository.findWithEagerLegalSubmissionsByExerciseIdAndStudentLoginAndTestRun(exercise.getId(), user.getLogin(), testRun);
        }
        else if (participant instanceof Team team) {
            return studentParticipationRepository.findWithEagerLegalSubmissionsByExerciseIdAndTeamId(exercise.getId(), team.getId());
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
            return optionalTeam.flatMap(team -> studentParticipationRepository.findWithEagerLegalSubmissionsByExerciseIdAndTeamId(exercise.getId(), team.getId()));
        }
        return studentParticipationRepository.findWithEagerLegalSubmissionsByExerciseIdAndStudentLogin(exercise.getId(), username);
    }

    /**
     * Get all exercise participations belonging to exercise and student.
     *
     * @param exercise  the exercise
     * @param studentId the id of student
     * @return the list of exercise participations belonging to exercise and student
     */
    public List<StudentParticipation> findByExerciseAndStudentId(Exercise exercise, Long studentId) {
        if (exercise.isTeamMode()) {
            Optional<Team> optionalTeam = teamRepository.findOneByExerciseIdAndUserId(exercise.getId(), studentId);
            return optionalTeam.map(team -> studentParticipationRepository.findAllByExerciseIdAndTeamId(exercise.getId(), team.getId())).orElse(List.of());
        }
        return studentParticipationRepository.findByExerciseIdAndStudentId(exercise.getId(), studentId);
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
            return optionalTeam.map(team -> studentParticipationRepository.findByExerciseIdAndTeamIdWithEagerLegalSubmissions(exercise.getId(), team.getId())).orElse(List.of());
        }
        return studentParticipationRepository.findByExerciseIdAndStudentIdWithEagerLegalSubmissions(exercise.getId(), studentId);
    }

    /**
     * Get all programming exercise participations belonging to exercise and student with eager results and submissions.
     *
     * @param exercise  the exercise
     * @param studentId the id of student
     * @return the list of programming exercise participations belonging to exercise and student
     */
    public List<StudentParticipation> findByExerciseAndStudentIdWithEagerResultsAndSubmissions(Exercise exercise, Long studentId) {
        if (exercise.isTeamMode()) {
            Optional<Team> optionalTeam = teamRepository.findOneByExerciseIdAndUserId(exercise.getId(), studentId);
            return optionalTeam.map(team -> studentParticipationRepository.findByExerciseIdAndTeamIdWithEagerResultsAndLegalSubmissions(exercise.getId(), team.getId()))
                    .orElse(List.of());
        }
        return studentParticipationRepository.findByExerciseIdAndStudentIdWithEagerResultsAndLegalSubmissions(exercise.getId(), studentId);
    }

    /**
     * Deletes the build plan on the continuous integration server and sets the initialization state of the participation to inactive.
     * This means the participation can be resumed in the future
     *
     * @param participation that will be set to inactive
     */
    public void cleanupBuildPlan(ProgrammingExerciseStudentParticipation participation) {
        // ignore participations without build plan id
        if (participation.getBuildPlanId() != null) {
            final var projectKey = ((ProgrammingExercise) participation.getExercise()).getProjectKey();
            continuousIntegrationService.get().deleteBuildPlan(projectKey, participation.getBuildPlanId());
            participation.setInitializationState(INACTIVE);
            participation.setBuildPlanId(null);
            programmingExerciseStudentParticipationRepository.saveAndFlush(participation);
        }
    }

    /**
     * NOTICE: be careful with this method because it deletes the students code on the version control server Deletes the repository on the version control server and sets the
     * initialization state of the participation to finished. This means the participation cannot be resumed in the future and would need to be restarted
     *
     * @param participation to be stopped
     */
    public void cleanupRepository(ProgrammingExerciseStudentParticipation participation) {
        // ignore participations without repository URL
        if (participation.getRepositoryUrl() != null) {
            versionControlService.get().deleteRepository(participation.getVcsRepositoryUrl());
            gitService.deleteLocalRepository(participation.getVcsRepositoryUrl());
            participation.setRepositoryUrl(null);
            participation.setInitializationState(InitializationState.FINISHED);
            programmingExerciseStudentParticipationRepository.saveAndFlush(participation);
        }
    }

    /**
     * Updates the individual due date for each given participation.
     *
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
            final Optional<StudentParticipation> originalParticipation = studentParticipationRepository.findById(toBeUpdated.getId());
            if (originalParticipation.isEmpty()) {
                continue;
            }

            // individual due dates can only exist if the exercise has a due date
            // they also have to be after the exercise due date
            final ZonedDateTime newIndividualDueDate;
            if (exercise.getDueDate() == null || (toBeUpdated.getIndividualDueDate() != null && toBeUpdated.getIndividualDueDate().isBefore(exercise.getDueDate()))) {
                newIndividualDueDate = null;
            }
            else {
                newIndividualDueDate = toBeUpdated.getIndividualDueDate();
            }

            if (!Objects.equals(originalParticipation.get().getIndividualDueDate(), newIndividualDueDate)) {
                originalParticipation.get().setIndividualDueDate(newIndividualDueDate);
                changedParticipations.add(originalParticipation.get());
            }
        }

        return changedParticipations;
    }

    /**
     * Delete the participation by participationId.
     *
     * @param participationId  the participationId of the entity
     * @param deleteBuildPlan  determines whether the corresponding build plan should be deleted as well
     * @param deleteRepository determines whether the corresponding repository should be deleted as well
     * @param deleteParticipantScores false if the participant scores have already been bulk deleted, true by default otherwise
     */
    public void delete(long participationId, boolean deleteBuildPlan, boolean deleteRepository, boolean deleteParticipantScores) {
        StudentParticipation participation = studentParticipationRepository.findByIdElseThrow(participationId);
        log.info("Request to delete Participation : {}", participation);

        if (participation instanceof ProgrammingExerciseStudentParticipation programmingExerciseParticipation) {
            var repositoryUrl = programmingExerciseParticipation.getVcsRepositoryUrl();
            String buildPlanId = programmingExerciseParticipation.getBuildPlanId();

            if (deleteBuildPlan && buildPlanId != null) {
                final var projectKey = programmingExerciseParticipation.getProgrammingExercise().getProjectKey();
                continuousIntegrationService.get().deleteBuildPlan(projectKey, buildPlanId);
            }
            if (deleteRepository && programmingExerciseParticipation.getRepositoryUrl() != null) {
                try {
                    versionControlService.get().deleteRepository(repositoryUrl);
                }
                catch (Exception ex) {
                    log.error("Could not delete repository: {}", ex.getMessage());
                }
            }
            // delete local repository cache
            gitService.deleteLocalRepository(repositoryUrl);
        }

        deleteResultsAndSubmissionsOfParticipation(participationId, deleteParticipantScores);
        studentParticipationRepository.delete(participation);
    }

    /**
     * Remove all results and submissions of the given participation. Will do nothing if invoked with a participation without results/submissions.
     *
     * @param participationId the id of the participation to delete results/submissions from.
     * @param deleteParticipantScores false if the participant scores have already been bulk deleted, true by default otherwise
     */
    public void deleteResultsAndSubmissionsOfParticipation(Long participationId, boolean deleteParticipantScores) {
        log.debug("Request to delete all results and submissions of participation with id : {}", participationId);
        var participation = participationRepository.findByIdWithResultsAndSubmissionsResults(participationId)
                .orElseThrow(() -> new EntityNotFoundException("Participation", participationId));

        // delete the participant score with the combination (exerciseId, studentId) or (exerciseId, teamId)
        if (deleteParticipantScores && participation instanceof StudentParticipation studentParticipation) {
            if (studentParticipation.getStudent().isPresent()) {
                studentScoreRepository.deleteByExerciseAndUser(participation.getExercise(), studentParticipation.getStudent().get());
            }
            if (studentParticipation.getTeam().isPresent()) {
                teamScoreRepository.deleteByExerciseAndTeam(participation.getExercise(), studentParticipation.getTeam().get());
            }
        }

        Set<Submission> submissions = participation.getSubmissions();
        // Delete all results for this participation
        Set<Result> resultsToBeDeleted = submissions.stream().flatMap(submission -> submission.getResults().stream()).collect(Collectors.toSet());
        resultsToBeDeleted.addAll(participation.getResults());
        // By removing the participation, the ResultListener will ignore this result instead of scheduling a participant score update
        // This is okay here, because we delete the whole participation (no older results will exist for the score)
        resultsToBeDeleted.forEach(participation::removeResult);
        resultsToBeDeleted.forEach(result -> resultService.deleteResult(result, false));
        // Delete all submissions for this participation
        submissions.forEach(submission -> {
            if (submission instanceof ProgrammingSubmission) {
                coverageReportRepository.deleteBySubmissionId(submission.getId());
                buildLogStatisticsEntryRepository.deleteByProgrammingSubmissionId(submission.getId());
            }
            submissionRepository.deleteById(submission.getId());
        });
    }

    /**
     * Delete all participations belonging to the given exercise
     *
     * @param exerciseId       the id of the exercise
     * @param deleteBuildPlan  specify if build plan should be deleted
     * @param deleteRepository specify if repository should be deleted
     */
    public void deleteAllByExerciseId(long exerciseId, boolean deleteBuildPlan, boolean deleteRepository) {
        var participationsToDelete = studentParticipationRepository.findByExerciseId(exerciseId);
        log.info("Request to delete all {} participations of exercise with id : {}", participationsToDelete.size(), exerciseId);

        // First remove all participant scores, as we are deleting all participations for the exercise
        participantScoreRepository.deleteAllByExerciseId(exerciseId);

        for (StudentParticipation participation : participationsToDelete) {
            delete(participation.getId(), deleteBuildPlan, deleteRepository, false);
        }
    }

    /**
     * Delete all participations belonging to the given team
     *
     * @param teamId           the id of the team
     * @param deleteBuildPlan  specify if build plan should be deleted
     * @param deleteRepository specify if repository should be deleted
     */
    public void deleteAllByTeamId(Long teamId, boolean deleteBuildPlan, boolean deleteRepository) {
        log.info("Request to delete all participations of Team with id : {}", teamId);

        // First remove all participant scores, as we are deleting all participations for the team
        teamScoreRepository.deleteAllByTeamId(teamId);

        List<StudentParticipation> participationsToDelete = studentParticipationRepository.findByTeamId(teamId);
        for (StudentParticipation participation : participationsToDelete) {
            delete(participation.getId(), deleteBuildPlan, deleteRepository, false);
        }
    }
}
