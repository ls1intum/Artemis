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
import java.util.stream.Collectors;

import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ParticipantScoreRepository;
import de.tum.cit.aet.artemis.assessment.repository.StudentScoreRepository;
import de.tum.cit.aet.artemis.assessment.repository.TeamScoreRepository;
import de.tum.cit.aet.artemis.assessment.service.ResultService;
import de.tum.cit.aet.artemis.atlas.service.competency.CompetencyProgressService;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.ContinuousIntegrationException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.exception.VersionControlException;
import de.tum.cit.aet.artemis.core.service.ProfileService;
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
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.VcsRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.build.BuildPlanType;
import de.tum.cit.aet.artemis.programming.repository.BuildLogStatisticsEntryRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.hestia.CoverageReportRepository;
import de.tum.cit.aet.artemis.programming.service.BuildLogEntryService;
import de.tum.cit.aet.artemis.programming.service.GitService;
import de.tum.cit.aet.artemis.programming.service.ParticipationVcsAccessTokenService;
import de.tum.cit.aet.artemis.programming.service.UriService;
import de.tum.cit.aet.artemis.programming.service.ci.ContinuousIntegrationService;
import de.tum.cit.aet.artemis.programming.service.localci.SharedQueueManagementService;
import de.tum.cit.aet.artemis.programming.service.vcs.VersionControlService;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;

/**
 * Service Implementation for managing Participation.
 */
@Profile(PROFILE_CORE)
@Service
public class ParticipationService {

    private static final Logger log = LoggerFactory.getLogger(ParticipationService.class);

    private final GitService gitService;

    private final Optional<ContinuousIntegrationService> continuousIntegrationService;

    private final Optional<VersionControlService> versionControlService;

    private final BuildLogEntryService buildLogEntryService;

    private final ParticipationRepository participationRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final SubmissionRepository submissionRepository;

    private final TeamRepository teamRepository;

    private final UriService uriService;

    private final ResultService resultService;

    private final CoverageReportRepository coverageReportRepository;

    private final BuildLogStatisticsEntryRepository buildLogStatisticsEntryRepository;

    private final ParticipantScoreRepository participantScoreRepository;

    private final StudentScoreRepository studentScoreRepository;

    private final TeamScoreRepository teamScoreRepository;

    private final Optional<SharedQueueManagementService> localCISharedBuildJobQueueService;

    private final ProfileService profileService;

    private final ParticipationVcsAccessTokenService participationVCSAccessTokenService;

    private final CompetencyProgressService competencyProgressService;

    public ParticipationService(GitService gitService, Optional<ContinuousIntegrationService> continuousIntegrationService, Optional<VersionControlService> versionControlService,
            BuildLogEntryService buildLogEntryService, ParticipationRepository participationRepository, StudentParticipationRepository studentParticipationRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            SubmissionRepository submissionRepository, TeamRepository teamRepository, UriService uriService, ResultService resultService,
            CoverageReportRepository coverageReportRepository, BuildLogStatisticsEntryRepository buildLogStatisticsEntryRepository,
            ParticipantScoreRepository participantScoreRepository, StudentScoreRepository studentScoreRepository, TeamScoreRepository teamScoreRepository,
            Optional<SharedQueueManagementService> localCISharedBuildJobQueueService, ProfileService profileService,
            ParticipationVcsAccessTokenService participationVCSAccessTokenService, CompetencyProgressService competencyProgressService) {
        this.gitService = gitService;
        this.continuousIntegrationService = continuousIntegrationService;
        this.versionControlService = versionControlService;
        this.buildLogEntryService = buildLogEntryService;
        this.participationRepository = participationRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.submissionRepository = submissionRepository;
        this.teamRepository = teamRepository;
        this.uriService = uriService;
        this.resultService = resultService;
        this.coverageReportRepository = coverageReportRepository;
        this.buildLogStatisticsEntryRepository = buildLogStatisticsEntryRepository;
        this.participantScoreRepository = participantScoreRepository;
        this.studentScoreRepository = studentScoreRepository;
        this.teamScoreRepository = teamScoreRepository;
        this.localCISharedBuildJobQueueService = localCISharedBuildJobQueueService;
        this.profileService = profileService;
        this.participationVCSAccessTokenService = participationVCSAccessTokenService;
        this.competencyProgressService = competencyProgressService;
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
        Optional<StudentParticipation> optionalStudentParticipation = findOneByExerciseAndParticipantAnyState(exercise, participant);
        if (optionalStudentParticipation.isPresent() && optionalStudentParticipation.get().isPracticeMode() && exercise.isCourseExercise()) {
            // In case there is already a practice participation, set it to inactive
            optionalStudentParticipation.get().setInitializationState(InitializationState.INACTIVE);
            studentParticipationRepository.saveAndFlush(optionalStudentParticipation.get());

            optionalStudentParticipation = findOneByExerciseAndParticipantAnyStateAndTestRun(exercise, participant, false);
        }

        // Check if participation already exists
        StudentParticipation participation;
        if (optionalStudentParticipation.isEmpty()) {
            participation = createNewParticipationWithInitializationDate(exercise, participant, initializationDate);
        }
        else {
            // make sure participation and exercise are connected
            participation = optionalStudentParticipation.get();
            participation.setExercise(exercise);
        }

        if (exercise instanceof ProgrammingExercise programmingExercise) {
            // fetch again to get additional objects
            participation = startProgrammingExercise(programmingExercise, (ProgrammingExerciseStudentParticipation) participation, initializationDate == null);
        }
        else {// for all other exercises: QuizExercise, ModelingExercise, TextExercise, FileUploadExercise
            if (participation.getInitializationState() == null || participation.getInitializationState() == InitializationState.UNINITIALIZED
                    || participation.getInitializationState() == InitializationState.FINISHED && !(exercise instanceof QuizExercise)) {
                // in case the participation was finished before, we set it to initialized again so that the user sees the correct button "Open modeling editor" on the client side.
                // Only for quiz exercises, the participation status FINISHED should not be overwritten since the user must not change his submission once submitted
                participation.setInitializationState(InitializationState.INITIALIZED);
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
            participation = new ProgrammingExerciseStudentParticipation(versionControlService.orElseThrow().getDefaultBranchOfArtemis());
        }
        else {
            participation = new StudentParticipation();
        }
        participation.setInitializationState(InitializationState.UNINITIALIZED);
        participation.setExercise(exercise);
        participation.setParticipant(participant);

        // StartedDate is used to link a Participation to a test exam exercise
        if (initializationDate != null) {
            participation.setInitializationDate(initializationDate);
        }
        participation = studentParticipationRepository.saveAndFlush(participation);

        if (exercise instanceof ProgrammingExercise && participant instanceof User user && profileService.isLocalVcsActive()) {
            participationVCSAccessTokenService.createParticipationVCSAccessToken(user, participation);
        }

        return participation;
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
        participation = copyRepository(exercise, exercise.getVcsTemplateRepositoryUri(), participation);

        return startProgrammingParticipation(exercise, participation, setInitializationDate);
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

        return startProgrammingParticipation(exercise, participation, true);
    }

    private StudentParticipation startProgrammingParticipation(ProgrammingExercise exercise, ProgrammingExerciseStudentParticipation participation, boolean setInitializationDate) {
        // Step 1c) configure the student repository (e.g. access right, etc.)
        participation = configureRepository(exercise, participation);
        // Step 2a) create the build plan (based on the BASE build plan)
        participation = copyBuildPlan(participation);
        // Step 2b) configure the build plan (e.g. access right, hooks, etc.)
        participation = configureBuildPlan(participation);
        // Step 3) configure the web hook of the student repository
        configureRepositoryWebHook(participation);
        // Step 4a) Set the InitializationState to initialized to indicate, the programming exercise is ready
        participation.setInitializationState(InitializationState.INITIALIZED);
        // Step 4b) Set the InitializationDate to the current time
        if (setInitializationDate) {
            // Note: For test exams, the InitializationDate is set to the StudentExam: startedDate in {#link #startExerciseWithInitializationDate}
            participation.setInitializationDate(ZonedDateTime.now());
        }
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
            participation = new ProgrammingExerciseStudentParticipation(versionControlService.orElseThrow().getDefaultBranchOfArtemis());
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
                participation = new ProgrammingExerciseStudentParticipation(versionControlService.orElseThrow().getDefaultBranchOfArtemis());
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
            programmingParticipation = configureRepository(programmingExercise, programmingParticipation);
            configureRepositoryWebHook(programmingParticipation);
            participation = programmingParticipation;
            if (programmingExercise.getBuildAndTestStudentSubmissionsAfterDueDate() != null || programmingExercise.getAssessmentType() != AssessmentType.AUTOMATIC
                    || programmingExercise.getAllowComplaintsForAutomaticAssessments()) {
                // restrict access for the student
                try {
                    versionControlService.orElseThrow().setRepositoryPermissionsToReadOnly(programmingParticipation.getVcsRepositoryUri(), programmingExercise.getProjectKey(),
                            programmingParticipation.getStudents());
                }
                catch (VersionControlException e) {
                    log.error("Removing write permissions failed for programming exercise with id {} for student repository with participation id {}: {}",
                            programmingExercise.getId(), programmingParticipation.getId(), e.getMessage());
                }
            }
        }

        participation.setInitializationState(InitializationState.FINISHED);
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

    private ProgrammingExerciseStudentParticipation copyRepository(ProgrammingExercise programmingExercise, VcsRepositoryUri sourceURL,
            ProgrammingExerciseStudentParticipation participation) {
        // only execute this step if it has not yet been completed yet or if the repository uri is missing for some reason
        if (!participation.getInitializationState().hasCompletedState(InitializationState.REPO_COPIED) || participation.getVcsRepositoryUri() == null) {
            final var projectKey = programmingExercise.getProjectKey();
            final var repoName = participation.addPracticePrefixIfTestRun(participation.getParticipantIdentifier());
            // NOTE: we have to get the repository slug of the template participation here, because not all exercises (in particular old ones) follow the naming conventions
            final var templateRepoName = uriService.getRepositorySlugFromRepositoryUri(sourceURL);
            VersionControlService vcs = versionControlService.orElseThrow();
            String templateBranch = vcs.getOrRetrieveBranchOfExercise(programmingExercise);
            // the next action includes recovery, which means if the repository has already been copied, we simply retrieve the repository uri and do not copy it again
            var newRepoUri = vcs.copyRepository(projectKey, templateRepoName, templateBranch, projectKey, repoName);
            // add the userInfo part to the repoUri only if the participation belongs to a single student (and not a team of students)
            if (participation.getStudent().isPresent()) {
                newRepoUri = newRepoUri.withUser(participation.getParticipantIdentifier());
            }
            participation.setRepositoryUri(newRepoUri.toString());
            participation.setInitializationState(InitializationState.REPO_COPIED);

            return programmingExerciseStudentParticipationRepository.saveAndFlush(participation);
        }
        else {
            return participation;
        }
    }

    private ProgrammingExerciseStudentParticipation configureRepository(ProgrammingExercise exercise, ProgrammingExerciseStudentParticipation participation) {
        if (!participation.getInitializationState().hasCompletedState(InitializationState.REPO_CONFIGURED)) {
            // do not allow the student to access the repository if this is an exam exercise that has not started yet
            boolean allowAccess = !exercise.isExamExercise() || ZonedDateTime.now().isAfter(exercise.getParticipationStartDate());
            if (participation.getParticipant() instanceof Team team && !Hibernate.isInitialized(team.getStudents())) {
                // eager load the team with students so their information can be used for the repository configuration
                participation.setParticipant(teamRepository.findWithStudentsByIdElseThrow(team.getId()));
            }
            versionControlService.orElseThrow().configureRepository(exercise, participation, allowAccess);
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
                String branch = versionControlService.orElseThrow().getOrRetrieveBranchOfStudentParticipation(participation);
                continuousIntegrationService.orElseThrow().configureBuildPlan(participation, branch);
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

    private void configureRepositoryWebHook(ProgrammingExerciseStudentParticipation participation) {
        if (!participation.getInitializationState().hasCompletedState(InitializationState.INITIALIZED)) {
            versionControlService.orElseThrow().addWebHookForParticipation(participation);
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
            return studentParticipationRepository.findWithEagerLegalSubmissionsByExerciseIdAndStudentLogin(exercise.getId(), user.getLogin());
        }
        else if (participant instanceof Team team) {
            return studentParticipationRepository.findWithEagerLegalSubmissionsAndTeamStudentsByExerciseIdAndTeamId(exercise.getId(), team.getId());
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
            return studentParticipationRepository.findWithEagerLegalSubmissionsByExerciseIdAndStudentLoginAndTestRun(exercise.getId(), user.getLogin(), testRun);
        }
        else if (participant instanceof Team team) {
            return studentParticipationRepository.findWithEagerLegalSubmissionsAndTeamStudentsByExerciseIdAndTeamId(exercise.getId(), team.getId());
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
            return optionalTeam.flatMap(team -> studentParticipationRepository.findWithEagerLegalSubmissionsAndTeamStudentsByExerciseIdAndTeamId(exercise.getId(), team.getId()));
        }
        return studentParticipationRepository.findWithEagerLegalSubmissionsByExerciseIdAndStudentLogin(exercise.getId(), username);
    }

    /**
     * Get one participation (in any state) by its student and exercise with eager submissions else throw exception.
     *
     * @param exercise the exercise for which to find a participation
     * @param username the username of the student
     * @return the participation of the given student and exercise with eager submissions in any state
     */
    public StudentParticipation findOneByExerciseAndStudentLoginWithEagerSubmissionsAnyStateElseThrow(Exercise exercise, String username) {
        Optional<StudentParticipation> optionalParticipation = findOneByExerciseAndStudentLoginWithEagerSubmissionsAnyState(exercise, username);
        if (optionalParticipation.isEmpty()) {
            throw new EntityNotFoundException("No participation found in exercise with id " + exercise.getId() + " for user " + username);
        }
        return optionalParticipation.get();
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
            return studentParticipationRepository.findAllWithTeamStudentsByExerciseIdAndTeamStudentId(exercise.getId(), studentId);
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
     * Get all programming exercise participations belonging to exercise and student with eager results and submissions.
     *
     * @param exercise  the exercise
     * @param studentId the id of student
     * @return the list of programming exercise participations belonging to exercise and student
     */
    public List<StudentParticipation> findByExerciseAndStudentIdWithEagerResultsAndSubmissions(Exercise exercise, Long studentId) {
        // TODO: do we really need to fetch all this information here?
        if (exercise.isTeamMode()) {
            Optional<Team> optionalTeam = teamRepository.findOneByExerciseIdAndUserId(exercise.getId(), studentId);
            return optionalTeam
                    .map(team -> studentParticipationRepository.findByExerciseIdAndTeamIdWithEagerResultsAndLegalSubmissionsAndTeamStudents(exercise.getId(), team.getId()))
                    .orElse(List.of());
        }
        return studentParticipationRepository.findByExerciseIdAndStudentIdWithEagerResultsAndSubmissions(exercise.getId(), studentId);
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
            continuousIntegrationService.orElseThrow().deleteBuildPlan(projectKey, participation.getBuildPlanId());

            // If a graded participation gets cleaned up after the due date set the state back to finished. Otherwise, the participation is initialized
            var dueDate = ExerciseDateService.getDueDate(participation);
            if (!participation.isPracticeMode() && dueDate.isPresent() && ZonedDateTime.now().isAfter(dueDate.get())) {
                participation.setInitializationState(InitializationState.FINISHED);
            }
            else {
                participation.setInitializationState(InitializationState.INACTIVE);
            }
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
        // ignore participations without repository URI
        if (participation.getRepositoryUri() != null) {
            versionControlService.orElseThrow().deleteRepository(participation.getVcsRepositoryUri());
            gitService.deleteLocalRepository(participation.getVcsRepositoryUri());
            participation.setRepositoryUri(null);
            participation.setInitializationState(InitializationState.FINISHED);
            programmingExerciseStudentParticipationRepository.saveAndFlush(participation);
        }
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
     * Delete the participation by participationId.
     *
     * @param participationId         the participationId of the entity
     * @param deleteBuildPlan         determines whether the corresponding build plan should be deleted as well
     * @param deleteRepository        determines whether the corresponding repository should be deleted as well
     * @param deleteParticipantScores false if the participant scores have already been bulk deleted, true by default otherwise
     */
    public void delete(long participationId, boolean deleteBuildPlan, boolean deleteRepository, boolean deleteParticipantScores) {
        StudentParticipation participation = studentParticipationRepository.findByIdElseThrow(participationId);
        log.info("Request to delete Participation : {}", participation);

        if (participation instanceof ProgrammingExerciseStudentParticipation programmingExerciseParticipation) {
            var repositoryUri = programmingExerciseParticipation.getVcsRepositoryUri();
            String buildPlanId = programmingExerciseParticipation.getBuildPlanId();

            // If LocalVC is active the flag deleteBuildPlan is ignored and build plans are always deleted
            if ((deleteBuildPlan || profileService.isLocalVcsActive()) && buildPlanId != null) {
                final var projectKey = programmingExerciseParticipation.getProgrammingExercise().getProjectKey();
                continuousIntegrationService.orElseThrow().deleteBuildPlan(projectKey, buildPlanId);
            }
            // If LocalVC is active the flag deleteRepository is ignored and repositories are always deleted
            if ((deleteRepository || profileService.isLocalVcsActive()) && programmingExerciseParticipation.getRepositoryUri() != null) {
                try {
                    versionControlService.orElseThrow().deleteRepository(repositoryUri);
                }
                catch (Exception ex) {
                    log.error("Could not delete repository: {}", ex.getMessage());
                }
            }
            // delete local repository cache
            gitService.deleteLocalRepository(repositoryUri);

            participationVCSAccessTokenService.deleteByParticipationId(participationId);
        }

        // If local CI is active, remove all queued jobs for participation
        localCISharedBuildJobQueueService.ifPresent(service -> service.cancelAllJobsForParticipation(participationId));

        deleteResultsAndSubmissionsOfParticipation(participationId, deleteParticipantScores);
        studentParticipationRepository.delete(participation);
    }

    /**
     * Remove all results and submissions of the given participation. Will do nothing if invoked with a participation without results/submissions.
     *
     * @param participationId         the id of the participation to delete results/submissions from.
     * @param deleteParticipantScores false if the participant scores have already been bulk deleted, true by default otherwise
     */
    public void deleteResultsAndSubmissionsOfParticipation(Long participationId, boolean deleteParticipantScores) {
        log.debug("Request to delete all results and submissions of participation with id : {}", participationId);
        var participation = participationRepository.findByIdWithResultsAndSubmissionsResults(participationId)
                .orElseThrow(() -> new EntityNotFoundException("Participation", participationId));

        // delete the participant score with the combination (exerciseId, studentId) or (exerciseId, teamId)
        if (deleteParticipantScores && participation instanceof StudentParticipation studentParticipation) {
            studentParticipation.getStudent().ifPresent(student -> studentScoreRepository.deleteByExerciseAndUser(participation.getExercise(), student));
            studentParticipation.getTeam().ifPresent(team -> teamScoreRepository.deleteByExerciseAndTeam(participation.getExercise(), team));
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
            // We have to set the results to an empty list because otherwise clearing the build log entries does not work correctly
            submission.setResults(Collections.emptyList());
            if (submission instanceof ProgrammingSubmission programmingSubmission) {
                coverageReportRepository.deleteBySubmissionId(submission.getId());
                buildLogEntryService.deleteBuildLogEntriesForProgrammingSubmission(programmingSubmission);
                buildLogStatisticsEntryRepository.deleteByProgrammingSubmissionId(submission.getId());
            }
            submissionRepository.deleteById(submission.getId());
        });
    }

    /**
     * Delete all participations belonging to the given exercise
     *
     * @param exercise                      the exercise
     * @param deleteBuildPlan               specify if build plan should be deleted
     * @param deleteRepository              specify if repository should be deleted
     * @param recalculateCompetencyProgress specify if the competency progress should be recalculated
     */
    public void deleteAllByExercise(Exercise exercise, boolean deleteBuildPlan, boolean deleteRepository, boolean recalculateCompetencyProgress) {
        var participationsToDelete = studentParticipationRepository.findByExerciseId(exercise.getId());
        log.info("Request to delete all {} participations of exercise with id : {}", participationsToDelete.size(), exercise.getId());

        // First remove all participant scores, as we are deleting all participations for the exercise
        participantScoreRepository.deleteAllByExerciseId(exercise.getId());

        for (StudentParticipation participation : participationsToDelete) {
            delete(participation.getId(), deleteBuildPlan, deleteRepository, false);
        }

        if (recalculateCompetencyProgress) {
            competencyProgressService.updateProgressByLearningObjectAsync(exercise);
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
