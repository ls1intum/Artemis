package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.domain.enumeration.InitializationState.*;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.BuildPlanType;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.*;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.domain.quiz.QuizSubmission;
import de.tum.in.www1.artemis.exception.ContinuousIntegrationException;
import de.tum.in.www1.artemis.exception.VersionControlException;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.connectors.VersionControlService;
import de.tum.in.www1.artemis.service.scheduled.quiz.QuizScheduleService;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Service Implementation for managing Participation.
 */
@Service
// TODO: this class is way to large. We need to split it into multiple smaller classes with fewer dependencies
public class ParticipationService {

    private final Logger log = LoggerFactory.getLogger(ParticipationService.class);

    private final UserRepository userRepository;

    private final GitService gitService;

    private final Optional<ContinuousIntegrationService> continuousIntegrationService;

    private final Optional<VersionControlService> versionControlService;

    private final AuthorizationCheckService authCheckService;

    private final QuizScheduleService quizScheduleService;

    private final UrlService urlService;

    private final ParticipationRepository participationRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    private final SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    private final ExerciseRepository exerciseRepository;

    private final ResultRepository resultRepository;

    private final SubmissionRepository submissionRepository;

    private final ComplaintResponseRepository complaintResponseRepository;

    private final ComplaintRepository complaintRepository;

    private final RatingRepository ratingRepository;

    private final TeamRepository teamRepository;

    private final StudentExamRepository studentExamRepository;

    public ParticipationService(ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository,
            TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository,
            SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository, ParticipationRepository participationRepository,
            StudentParticipationRepository studentParticipationRepository, ExerciseRepository exerciseRepository, ResultRepository resultRepository,
            SubmissionRepository submissionRepository, ComplaintResponseRepository complaintResponseRepository, ComplaintRepository complaintRepository,
            TeamRepository teamRepository, StudentExamRepository studentExamRepository, UserRepository userRepository, GitService gitService,
            Optional<ContinuousIntegrationService> continuousIntegrationService, Optional<VersionControlService> versionControlService, AuthorizationCheckService authCheckService,
            @Lazy QuizScheduleService quizScheduleService, RatingRepository ratingRepository, UrlService urlService) {
        this.participationRepository = participationRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.templateProgrammingExerciseParticipationRepository = templateProgrammingExerciseParticipationRepository;
        this.solutionProgrammingExerciseParticipationRepository = solutionProgrammingExerciseParticipationRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.exerciseRepository = exerciseRepository;
        this.resultRepository = resultRepository;
        this.submissionRepository = submissionRepository;
        this.complaintResponseRepository = complaintResponseRepository;
        this.complaintRepository = complaintRepository;
        this.teamRepository = teamRepository;
        this.studentExamRepository = studentExamRepository;
        this.userRepository = userRepository;
        this.gitService = gitService;
        this.continuousIntegrationService = continuousIntegrationService;
        this.versionControlService = versionControlService;
        this.authCheckService = authCheckService;
        this.quizScheduleService = quizScheduleService;
        this.ratingRepository = ratingRepository;
        this.urlService = urlService;
    }

    /**
     * Save any type of participation without knowing the explicit subtype
     *
     * @param participation the entity to save
     * @return the persisted entity
     */
    public Participation save(ParticipationInterface participation) {
        // Note: unfortunately polymorphism does not always work in Hibernate due to reflective method invocation.
        // Therefore we provide a convenience method here that finds out the concrete participation type and delegates the call to the right method
        if (participation instanceof ProgrammingExerciseStudentParticipation) {
            return save((ProgrammingExerciseStudentParticipation) participation);
        }
        else if (participation instanceof StudentParticipation) {
            return save((StudentParticipation) participation);
        }
        else if (participation instanceof TemplateProgrammingExerciseParticipation) {
            return save((TemplateProgrammingExerciseParticipation) participation);
        }
        else if (participation instanceof SolutionProgrammingExerciseParticipation) {
            return save((SolutionProgrammingExerciseParticipation) participation);
        }
        else {
            throw new RuntimeException("Participation type not known. Cannot save!");
        }
    }

    /**
     * Save a ProgrammingExerciseStudentParticipation.
     *
     * @param participation the entity to save
     * @return the persisted entity
     */
    public ProgrammingExerciseStudentParticipation save(ProgrammingExerciseStudentParticipation participation) {
        log.debug("Request to save ProgrammingExerciseStudentParticipation : {}", participation);
        return studentParticipationRepository.saveAndFlush(participation);
    }

    /**
     * Save a StudentParticipation.
     *
     * @param participation the entity to save
     * @return the persisted entity
     */
    public StudentParticipation save(StudentParticipation participation) {
        log.debug("Request to save StudentParticipation : {}", participation);
        return studentParticipationRepository.saveAndFlush(participation);
    }

    /**
     * Save a TemplateProgrammingExerciseParticipation.
     *
     * @param participation the entity to save
     * @return the persisted entity
     */
    public TemplateProgrammingExerciseParticipation save(TemplateProgrammingExerciseParticipation participation) {
        log.debug("Request to save TemplateProgrammingExerciseParticipation : {}", participation);
        return templateProgrammingExerciseParticipationRepository.saveAndFlush(participation);
    }

    /**
     * Save a SolutionProgrammingExerciseParticipation.
     *
     * @param participation the entity to save
     * @return the persisted entity
     */
    public SolutionProgrammingExerciseParticipation save(SolutionProgrammingExerciseParticipation participation) {
        log.debug("Request to save SolutionProgrammingExerciseParticipation : {}", participation);
        return solutionProgrammingExerciseParticipationRepository.saveAndFlush(participation);
    }

    /**
     * This method is triggered when a student starts an exercise. It creates a Participation which connects the corresponding student and exercise. Additionally, it configures
     * repository / build plan related stuff for programming exercises. In the case of modeling or text exercises, it also initializes and stores the corresponding submission.
     *
     * @param exercise the exercise which is started
     * @param participant the user or team who starts the exercise
     * @param createInitialSubmission whether an initial empty submission should be created for text,modeling,quiz,fileupload or not
     * @return the participation connecting the given exercise and user
     */
    public StudentParticipation startExercise(Exercise exercise, Participant participant, boolean createInitialSubmission) {
        // common for all exercises
        // Check if participation already exists
        Optional<StudentParticipation> optionalStudentParticipation = findOneByExerciseAndParticipantAnyState(exercise, participant);
        StudentParticipation participation;
        if (optionalStudentParticipation.isEmpty()) {
            // create a new participation only if no participation can be found
            if (exercise instanceof ProgrammingExercise) {
                participation = new ProgrammingExerciseStudentParticipation();
            }
            else {
                participation = new StudentParticipation();
            }
            participation.setInitializationState(UNINITIALIZED);
            participation.setExercise(exercise);
            participation.setParticipant(participant);
            participation = save(participation);
        }
        else {
            // make sure participation and exercise are connected
            participation = optionalStudentParticipation.get();
            participation.setExercise(exercise);
        }

        if (exercise instanceof ProgrammingExercise) {
            participation = startProgrammingExercise((ProgrammingExercise) exercise, (ProgrammingExerciseStudentParticipation) participation);
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
            // clients creates missing participaiton for exercise, call on server succeeds, but response to client is lost
            // -> client tries to create participation again. In this case the submission is not loaded from db -> client errors
            if (optionalStudentParticipation.isEmpty() || !submissionRepository.existsByParticipationId(participation.getId())) {
                // initialize a modeling, text, file upload or quiz submission
                if (createInitialSubmission) {
                    initializeSubmission(participation, exercise, null);
                }
            }
        }
        return save(participation);
    }

    /**
     * Start a programming exercise participation (which does not exist yet) by creating and configuring a student git repository (step 1) and a student build plan (step 2)
     * based on the templates in the given programming exercise
     *
     * @param exercise the programming exercise that the currently active user (student) wants to start
     * @param participation inactive participation
     * @return started participation
     */
    private StudentParticipation startProgrammingExercise(ProgrammingExercise exercise, ProgrammingExerciseStudentParticipation participation) {
        // Step 1a) create the student repository (based on the template repository)
        participation = copyRepository(participation);
        // Step 1b) configure the student repository (e.g. access right, etc.)
        participation = configureRepository(exercise, participation);
        // Step 2a) create the build plan (based on the BASE build plan)
        participation = copyBuildPlan(participation);
        // Step 2b) configure the build plan (e.g. access right, hooks, etc.)
        participation = configureBuildPlan(participation);
        // Step 2c) we might need to perform an empty commit (as a workaround, depending on the CI system) here, because it should not trigger a new programming submission
        // (when the web hook was already initialized, see below)
        participation = performEmptyCommit(participation);
        // Note: we configure the repository webhook last, so that the potential empty commit does not trigger a new programming submission (see empty-commit-necessary)
        // Step 3) configure the web hook of the student repository
        participation = configureRepositoryWebHook(participation);
        participation.setInitializationState(INITIALIZED);
        participation.setInitializationDate(ZonedDateTime.now());
        // after saving, we need to make sure the object that is used after the if statement is the right one
        return participation;
    }

    /**
     * This method checks whether a participation exists for a given exercise and user. If not, it creates such a participation with initialization state FINISHED.
     * If the participation had to be newly created or there were no submissions yet for the existing participation, a new submission is created with the given submission type.
     * For external submissions, the submission is assumed to be submitted immediately upon creation.
     *
     * @param exercise the exercise for which to create a participation and submission
     * @param participant the user/team for which to create a participation and submission
     * @param submissionType the type of submission to create if none exist yet
     * @return the participation connecting the given exercise and user
     */
    public StudentParticipation createParticipationWithEmptySubmissionIfNotExisting(Exercise exercise, Participant participant, SubmissionType submissionType) {
        Optional<StudentParticipation> optionalStudentParticipation = findOneByExerciseAndParticipantAnyState(exercise, participant);
        StudentParticipation participation;
        if (optionalStudentParticipation.isEmpty()) {
            // create a new participation only if no participation can be found
            if (exercise instanceof ProgrammingExercise) {
                participation = new ProgrammingExerciseStudentParticipation();
            }
            else {
                participation = new StudentParticipation();
            }
            participation.setInitializationState(UNINITIALIZED);
            participation.setInitializationDate(ZonedDateTime.now());
            participation.setExercise(exercise);
            participation.setParticipant(participant);

            participation = save(participation);
        }
        else {
            participation = optionalStudentParticipation.get();
        }

        // setup repository in case of programming exercise
        if (exercise instanceof ProgrammingExercise) {
            ProgrammingExercise programmingExercise = (ProgrammingExercise) exercise;
            ProgrammingExerciseStudentParticipation programmingParticipation = (ProgrammingExerciseStudentParticipation) participation;
            // Note: we need a repository, otherwise the student cannot click resume.
            programmingParticipation = copyRepository(programmingParticipation);
            programmingParticipation = configureRepository(programmingExercise, programmingParticipation);
            programmingParticipation = configureRepositoryWebHook(programmingParticipation);
            participation = programmingParticipation;
            if (programmingExercise.getBuildAndTestStudentSubmissionsAfterDueDate() != null || programmingExercise.getAssessmentType() != AssessmentType.AUTOMATIC) {
                // restrict access for the student
                try {
                    versionControlService.get().setRepositoryPermissionsToReadOnly(programmingParticipation.getVcsRepositoryUrl(), programmingExercise.getProjectKey(),
                            programmingParticipation.getStudents());
                }
                catch (VersionControlException e) {
                    log.error("Removing write permissions failed for programming exercise with id " + programmingExercise.getId() + " for student repository with participation id "
                            + programmingParticipation.getId() + ": " + e.getMessage());
                }
            }
        }

        participation.setInitializationState(FINISHED);
        participation = save(participation);

        // Take the latest submission or initialize a new empty submission
        participation = (StudentParticipation) participationRepository.findByIdWithSubmissionsElseThrow(participation.getId());
        Optional<Submission> optionalSubmission = participation.findLatestSubmission();
        Submission submission;
        if (optionalSubmission.isPresent()) {
            submission = optionalSubmission.get();
        }
        else {
            submission = initializeSubmission(participation, exercise, submissionType).get();
            participation = save(participation);
        }

        // If the submission has not yet been submitted, submit it now
        if (!submission.isSubmitted()) {
            submission.setSubmitted(true);
            submission.setSubmissionDate(ZonedDateTime.now());
            submissionRepository.save(submission);
        }

        return participation;
    }

    /**
     * Initializes a new text, modeling or file upload submission (depending on the type of the given exercise), connects it with the given participation and stores it in the
     * database.
     *
     * @param participation                 the participation for which the submission should be initialized
     * @param exercise                      the corresponding exercise, should be either a text, modeling or file upload exercise, otherwise it will instantly return and not do anything
     * @param submissionType                type for the submission to be initialized
     */
    private Optional<Submission> initializeSubmission(Participation participation, Exercise exercise, SubmissionType submissionType) {

        Submission submission;
        if (exercise instanceof ProgrammingExercise) {
            submission = new ProgrammingSubmission();
        }
        else if (exercise instanceof ModelingExercise) {
            submission = new ModelingSubmission();
        }
        else if (exercise instanceof TextExercise) {
            submission = new TextSubmission();
        }
        else if (exercise instanceof FileUploadExercise) {
            submission = new FileUploadSubmission();
        }
        else if (exercise instanceof QuizExercise) {
            submission = new QuizSubmission();
        }
        else {
            throw new RuntimeException("Unsupported exercise type: " + exercise);
        }

        submission.setType(submissionType);
        submission.setParticipation(participation);
        submissionRepository.save(submission);
        participation.addSubmission(submission);
        return Optional.of(submission);
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
    public StudentParticipation participationForQuizWithResult(QuizExercise quizExercise, String username) {
        if (quizExercise.isEnded()) {
            // try getting participation from database
            Optional<StudentParticipation> optionalParticipation = findOneByExerciseAndStudentLoginAnyState(quizExercise, username);

            if (optionalParticipation.isEmpty()) {
                log.error("Participation in quiz " + quizExercise.getTitle() + " not found for user " + username);
                // TODO properly handle this case
                return null;
            }
            StudentParticipation participation = optionalParticipation.get();
            // add exercise
            participation.setExercise(quizExercise);

            // add the appropriate result
            Result result = resultRepository.findFirstByParticipationIdAndRatedOrderByCompletionDateDesc(participation.getId(), true).orElse(null);

            participation.setResults(new HashSet<>());

            if (result != null) {
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

    /*
     * Get all submissions of a participationId
     * @param participationId of submission
     * @return List<submission>
     */
    public List<Submission> getSubmissionsWithResultsAndAssessorsByParticipationId(long participationId) {
        return submissionRepository.findAllWithResultsAndAssessorByParticipationId(participationId);
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
        // Note: the repository webhook (step 1c) already exists so we don't need to set it up again, the empty commit hook (step 2c) is also not necessary here
        // and must be handled by the calling method in case it would be necessary
        participation.setInitializationState(INITIALIZED);
        participation = save(participation);
        if (participation.getInitializationDate() == null) {
            // only set the date if it was not set before (which should NOT be the case)
            participation.setInitializationDate(ZonedDateTime.now());
        }
        return save(participation);
    }

    private ProgrammingExerciseStudentParticipation copyRepository(ProgrammingExerciseStudentParticipation participation) {
        // only execute this step if it has not yet been completed yet or if the repository url is missing for some reason
        if (!participation.getInitializationState().hasCompletedState(InitializationState.REPO_COPIED) || participation.getVcsRepositoryUrl() == null) {
            final var programmingExercise = participation.getProgrammingExercise();
            final var projectKey = programmingExercise.getProjectKey();
            final var participantIdentifier = participation.getParticipantIdentifier();
            // NOTE: we have to get the repository slug of the template participation here, because not all exercises (in particular old ones) follow the naming conventions
            final var templateRepoName = urlService.getRepositorySlugFromRepositoryUrl(programmingExercise.getTemplateParticipation().getVcsRepositoryUrl());
            // the next action includes recovery, which means if the repository has already been copied, we simply retrieve the repository url and do not copy it again
            var newRepoUrl = versionControlService.get().copyRepository(projectKey, templateRepoName, projectKey, participantIdentifier);
            // add the userInfo part to the repoURL only if the participation belongs to a single student (and not a team of students)
            if (participation.getStudent().isPresent()) {
                newRepoUrl = newRepoUrl.withUser(participantIdentifier);
            }
            participation.setRepositoryUrl(newRepoUrl.toString());
            participation.setInitializationState(REPO_COPIED);

            return save(participation);
        }
        else {
            return participation;
        }
    }

    private ProgrammingExerciseStudentParticipation configureRepository(ProgrammingExercise exercise, ProgrammingExerciseStudentParticipation participation) {
        if (!participation.getInitializationState().hasCompletedState(InitializationState.REPO_CONFIGURED)) {
            // do not allow the student to access the repository if this is an exam exercise that has not started yet
            boolean allowAccess = !exercise.isExamExercise() || ZonedDateTime.now().isAfter(getIndividualReleaseDate(exercise));
            versionControlService.get().configureRepository(exercise, participation.getVcsRepositoryUrl(), participation.getStudents(), allowAccess);
            participation.setInitializationState(InitializationState.REPO_CONFIGURED);
            return save(participation);
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
            // the next action includes recovery, which means if the build plan has already been copied, we simply retrieve the build plan id and do not copy it again
            final var buildPlanId = continuousIntegrationService.get().copyBuildPlan(projectKey, planName, projectKey, buildProjectName, username.toUpperCase(), true);
            participation.setBuildPlanId(buildPlanId);
            participation.setInitializationState(InitializationState.BUILD_PLAN_COPIED);
            return save(participation);
        }
        else {
            return participation;
        }
    }

    private ProgrammingExerciseStudentParticipation configureBuildPlan(ProgrammingExerciseStudentParticipation participation) {
        if (!participation.getInitializationState().hasCompletedState(InitializationState.BUILD_PLAN_CONFIGURED)) {
            try {
                continuousIntegrationService.get().configureBuildPlan(participation);
            }
            catch (ContinuousIntegrationException ex) {
                // this means something with the configuration of the build plan is wrong.
                // we try to recover from typical edge cases by setting the initialization state back, so that the previous action (copy build plan) is tried again, when
                // the user again clicks on the start / resume exercise button.
                participation.setInitializationState(InitializationState.REPO_CONFIGURED);
                save(participation);
                // rethrow
                throw ex;
            }
            participation.setInitializationState(InitializationState.BUILD_PLAN_CONFIGURED);
            return save(participation);
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
     * Return the StudentExam of the participation's user, if possible
     *
     * @param exercise that is possibly part of an exam
     * @param participation the participation of the student
     * @return an optional StudentExam, which is empty if the exercise is not part of an exam or the student exam hasn't been created
     */
    public Optional<StudentExam> findStudentExam(Exercise exercise, StudentParticipation participation) {
        if (exercise.isExamExercise()) {
            var examUser = participation.getStudent().orElseThrow(() -> new EntityNotFoundException("Exam Participation with " + participation.getId() + " has no student!"));
            return studentExamRepository.findByExerciseIdAndUserId(exercise.getId(), examUser.getId());
        }
        return Optional.empty();
    }

    /**
     * Return the individual release date for the exercise of the participation's user
     * <p>
     * Currently, exercise start dates are the same for all users
     *
     * @param exercise that is possibly part of an exam
     * @return the time from which on access to the exercise is allowed, for exercises that are not part of an exam, this is just the release date.
     */
    public ZonedDateTime getIndividualReleaseDate(Exercise exercise) {
        if (exercise.isExamExercise()) {
            return exercise.getExerciseGroup().getExam().getStartDate();
        }
        else {
            return exercise.getReleaseDate();
        }
    }

    /**
     * Return the individual due date for the exercise of the participation's user
     * <p>
     * For exam exercises, this depends on the StudentExam's working time
     *
     * @param exercise that is possibly part of an exam
     * @param participation the participation of the student
     * @return the time from which on submissions are not allowed, for exercises that are not part of an exam, this is just the due date.
     */
    public ZonedDateTime getIndividualDueDate(Exercise exercise, StudentParticipation participation) {
        if (exercise.isExamExercise()) {
            var studentExam = findStudentExam(exercise, participation).orElse(null);
            if (studentExam == null) {
                return exercise.getDueDate();
            }
            return studentExam.getExam().getStartDate().plusSeconds(studentExam.getWorkingTime());
        }
        return exercise.getDueDate();
    }

    /**
     * Perform an empty commit so that the build plan definitely runs for the actual student commit
     *
     * @param participation the participation of the student
     * @return the participation of the student
     */
    public ProgrammingExerciseStudentParticipation performEmptyCommit(ProgrammingExerciseStudentParticipation participation) {
        continuousIntegrationService.get().performEmptySetupCommit(participation);
        return participation;
    }

    /**
     * Get one student participation by id with fetched Result, Submissions, Exercise and Course.
     *
     * @param participationId the id of the entity
     * @return the entity
     **/
    public StudentParticipation findOneStudentParticipationWithEagerSubmissionsResultsFeedbacks(Long participationId) {
        log.debug("Request to get Participation : {}", participationId);
        Optional<StudentParticipation> participation = studentParticipationRepository.findWithEagerSubmissionsResultsFeedbacksById(participationId);
        if (participation.isEmpty()) {
            throw new EntityNotFoundException("StudentParticipation with " + participationId + " was not found!");
        }
        return participation.get();
    }

    /**
     * Get one programming exercise participation by id.
     *
     * @param participationId the id of the entity
     * @return the entity
     */
    public ProgrammingExerciseStudentParticipation findProgrammingExerciseParticipation(Long participationId) {
        log.debug("Request to get Participation : {}", participationId);
        Optional<ProgrammingExerciseStudentParticipation> participation = programmingExerciseStudentParticipationRepository.findById(participationId);
        if (participation.isEmpty()) {
            throw new EntityNotFoundException("ProgrammingExerciseStudentParticipation with " + participationId + " was not found!");
        }
        return participation.get();
    }

    /**
     * Get one participation by id including all results.
     *
     * @param participationId the id of the participation
     * @return the participation with all its results
     */
    public StudentParticipation findOneWithEagerResults(Long participationId) {
        log.debug("Request to get Participation : {}", participationId);
        Optional<StudentParticipation> participation = studentParticipationRepository.findWithEagerResultsById(participationId);
        if (participation.isEmpty()) {
            throw new EntityNotFoundException("Participation with " + participationId + " was not found!");
        }
        return participation.get();
    }

    /**
     * Get one participation by id including all submissions and results. Throws an EntityNotFoundException if the participation with the given id could not be found.
     *
     * @param participationId the id of the entity
     * @return the participation with all its submissions and results
     */
    public StudentParticipation findOneWithEagerSubmissionsResultsFeedback(Long participationId) {
        log.debug("Request to get Participation : {}", participationId);
        Optional<StudentParticipation> participation = studentParticipationRepository.findWithEagerSubmissionsResultsFeedbacksById(participationId);
        if (participation.isEmpty()) {
            throw new EntityNotFoundException("Participation with " + participationId + " was not found!");
        }
        return participation.get();
    }

    /**
     * Get one participation (in any state) by its student and exercise.
     *
     * @param exercise the exercise for which to find a participation
     * @param username the username of the student
     * @return the participation of the given student and exercise in any state
     */
    public Optional<StudentParticipation> findOneByExerciseAndStudentLoginAnyState(Exercise exercise, String username) {
        log.debug("Request to get Participation for User {} for Exercise with id: {}", username, exercise.getId());
        if (exercise.isTeamMode()) {
            Optional<Team> optionalTeam = teamRepository.findOneByExerciseIdAndUserLogin(exercise.getId(), username);
            return optionalTeam.flatMap(team -> studentParticipationRepository.findWithEagerSubmissionsByExerciseIdAndTeamId(exercise.getId(), team.getId()));
        }
        return studentParticipationRepository.findWithEagerSubmissionsByExerciseIdAndStudentLogin(exercise.getId(), username);
    }

    /**
     * Get one participation (in any state) by its team and exercise.
     *
     * @param exercise the exercise for which to find a participation
     * @param teamId the id of the team
     * @return the participation of the given team and exercise in any state
     */
    public Optional<StudentParticipation> findOneByExerciseAndTeamIdAnyState(Exercise exercise, Long teamId) {
        log.debug("Request to get Participation for Team with id {} for Exercise with id: {}", teamId, exercise.getId());
        return studentParticipationRepository.findWithEagerSubmissionsByExerciseIdAndTeamId(exercise.getId(), teamId);
    }

    /**
     * Get one participation (in any state) by its participant and exercise.
     *
     * @param exercise the exercise for which to find a participation
     * @param participant the short name of the team
     * @return the participation of the given team and exercise in any state
     */
    public Optional<StudentParticipation> findOneByExerciseAndParticipantAnyState(Exercise exercise, Participant participant) {
        if (participant instanceof User) {
            return findOneByExerciseAndStudentLoginAnyState(exercise, ((User) participant).getLogin());
        }
        else if (participant instanceof Team) {
            return findOneByExerciseAndTeamIdAnyState(exercise, ((Team) participant).getId());
        }
        else {
            throw new Error("Unknown Participant type");
        }
    }

    /**
     * Get one participation (in any state) by its student and exercise with all its results.
     *
     * @param exercise the exercise for which to find a participation
     * @param username   the username of the student
     * @return the participation of the given student and exercise in any state
     */
    public Optional<StudentParticipation> findOneByExerciseAndStudentLoginAnyStateWithEagerResults(Exercise exercise, String username) {
        log.debug("Request to get Participation for User {} for Exercise with id: {}", username, exercise.getId());
        if (exercise.isTeamMode()) {
            Optional<Team> optionalTeam = teamRepository.findOneByExerciseIdAndUserLogin(exercise.getId(), username);
            return optionalTeam.flatMap(team -> studentParticipationRepository.findWithEagerResultsByExerciseIdAndTeamId(exercise.getId(), team.getId()));
        }
        return studentParticipationRepository.findWithEagerResultsByExerciseIdAndStudentLogin(exercise.getId(), username);
    }

    /**
     * Get one participation (in any state) by its student and exercise with eager submissions.
     *
     * @param exercise the exercise for which to find a participation
     * @param username   the username of the student
     * @return the participation of the given student and exercise with eager submissions in any state
     */
    public Optional<StudentParticipation> findOneByExerciseAndStudentLoginWithEagerSubmissionsAnyState(Exercise exercise, String username) {
        log.debug("Request to get Participation for User {} for Exercise with id: {}", username, exercise.getId());
        if (exercise.isTeamMode()) {
            Optional<Team> optionalTeam = teamRepository.findOneByExerciseIdAndUserLogin(exercise.getId(), username);
            return optionalTeam.flatMap(team -> studentParticipationRepository.findWithEagerSubmissionsByExerciseIdAndTeamId(exercise.getId(), team.getId()));
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
    public List<StudentParticipation> findByExerciseAndStudentId(Exercise exercise, Long studentId) {
        if (exercise.isTeamMode()) {
            Optional<Team> optionalTeam = teamRepository.findOneByExerciseIdAndUserId(exercise.getId(), studentId);
            return optionalTeam.map(team -> studentParticipationRepository.findByExerciseIdAndTeamId(exercise.getId(), team.getId())).orElse(List.of());
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
            return optionalTeam.map(team -> studentParticipationRepository.findByExerciseIdAndTeamIdWithEagerSubmissions(exercise.getId(), team.getId())).orElse(List.of());
        }
        return studentParticipationRepository.findByExerciseIdAndStudentIdWithEagerSubmissions(exercise.getId(), studentId);
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
            return optionalTeam.map(team -> studentParticipationRepository.findByExerciseIdAndTeamIdWithEagerResultsAndSubmissions(exercise.getId(), team.getId()))
                    .orElse(List.of());
        }
        return studentParticipationRepository.findByExerciseIdAndStudentIdWithEagerResultsAndSubmissions(exercise.getId(), studentId);
    }

    /**
     * Deletes the build plan on the continuous integration server and sets the initialization state of the participation to inactive This means the participation can be resumed in
     * the future
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
            save(participation);
        }
    }

    /**
     * NOTICE: be careful with this method because it deletes the students code on the version control server Deletes the repository on the version control server and sets the
     * initialization state of the participation to finished This means the participation cannot be resumed in the future and would need to be restarted
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
            save(participation);
        }
    }

    /**
     * Delete the participation by participationId.
     *
     * @param participationId  the participationId of the entity
     * @param deleteBuildPlan  determines whether the corresponding build plan should be deleted as well
     * @param deleteRepository determines whether the corresponding repository should be deleted as well
     */
    @Transactional // ok
    public void delete(Long participationId, boolean deleteBuildPlan, boolean deleteRepository) {
        StudentParticipation participation = studentParticipationRepository.findWithEagerSubmissionsResultsFeedbacksById(participationId).get();
        log.debug("Request to delete Participation : {}", participation);

        if (participation instanceof ProgrammingExerciseStudentParticipation) {
            ProgrammingExerciseStudentParticipation programmingExerciseParticipation = (ProgrammingExerciseStudentParticipation) participation;
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
                    log.error("Could not delete repository: " + ex.getMessage());
                }
            }

            // delete local repository cache
            gitService.deleteLocalRepository(repositoryUrl);
        }

        complaintResponseRepository.deleteByComplaint_Result_Participation_Id(participationId);
        complaintRepository.deleteByResult_Participation_Id(participationId);
        ratingRepository.deleteByResult_Participation_Id(participationId);

        participation = (StudentParticipation) deleteResultsAndSubmissionsOfParticipation(participation.getId());

        Exercise exercise = participation.getExercise();
        exercise.removeParticipation(participation);
        exerciseRepository.save(exercise);
        studentParticipationRepository.delete(participation);
    }

    /**
     * Remove all results and submissions of the given participation. Will do nothing if invoked with a participation without results/submissions.
     *
     * @param participationId the id of the participation to delete results/submissions from.
     * @return participation without submissions and results.
     */
    @Transactional // ok
    public Participation deleteResultsAndSubmissionsOfParticipation(Long participationId) {
        Participation participation = participationRepository.getOneWithEagerSubmissionsAndResults(participationId);
        Set<Submission> submissions = participation.getSubmissions();
        List<Result> resultsToBeDeleted = new ArrayList<>();

        // The result of the submissions will be deleted via cascade
        submissions.forEach(submission -> {
            resultsToBeDeleted.addAll(Objects.requireNonNull(submission.getResults()));
            submissionRepository.deleteById(submission.getId());
        });

        // The results that are only connected to a participation are also deleted
        resultsToBeDeleted.forEach(participation::removeResult);
        participation.getResults().forEach(result -> resultRepository.deleteById(result.getId()));
        return participation;
    }

    /**
     * Delete all participations belonging to the given exercise
     *
     * @param exerciseId the id of the exercise
     * @param deleteBuildPlan specify if build plan should be deleted
     * @param deleteRepository specify if repository should be deleted
     */
    @Transactional // ok
    public void deleteAllByExerciseId(Long exerciseId, boolean deleteBuildPlan, boolean deleteRepository) {
        List<StudentParticipation> participationsToDelete = studentParticipationRepository.findByExerciseId(exerciseId);

        for (StudentParticipation participation : participationsToDelete) {
            delete(participation.getId(), deleteBuildPlan, deleteRepository);
        }
    }

    /**
     * Delete all participations belonging to the given team
     *
     * @param teamId the id of the team
     * @param deleteBuildPlan specify if build plan should be deleted
     * @param deleteRepository specify if repository should be deleted
     */
    @Transactional // ok
    public void deleteAllByTeamId(Long teamId, boolean deleteBuildPlan, boolean deleteRepository) {
        log.info("Request to delete all participations of Team with id : {}", teamId);

        List<StudentParticipation> participationsToDelete = studentParticipationRepository.findByTeamId(teamId);

        for (StudentParticipation participation : participationsToDelete) {
            delete(participation.getId(), deleteBuildPlan, deleteRepository);
        }
    }

    /**
     * Check if a participation can be accessed with the current user.
     *
     * @param participation to access
     * @return can user access participation
     */
    public boolean canAccessParticipation(StudentParticipation participation) {
        return Optional.ofNullable(participation).isPresent() && userHasPermissions(participation);
    }

    /**
     * Check if a user has permissions to access a certain participation. This includes not only the owner of the participation but also the TAs and instructors of the course.
     *
     * @param participation to access
     * @return does user has permissions to access participation
     */
    private boolean userHasPermissions(StudentParticipation participation) {
        if (authCheckService.isOwnerOfParticipation(participation))
            return true;
        // if the user is not the owner of the participation, the user can only see it in case he is
        // a teaching assistant or an instructor of the course, or in case he is admin
        User user = userRepository.getUserWithGroupsAndAuthorities();
        Course course = participation.getExercise().getCourseViaExerciseGroupOrCourseMember();
        return authCheckService.isAtLeastTeachingAssistantInCourse(course, user);
    }

    /**
     * Get all participations for the given studentExam and exercises combined with their submissions with a result.
     * Distinguishes between student exams and test runs and only loads the respective participations
     *
     * @param studentExam studentExam with exercises loaded
     * @return student's participations with submissions and results
     */
    public List<StudentParticipation> findByStudentExamWithEagerSubmissionsResult(StudentExam studentExam) {
        if (studentExam.isTestRun()) {
            return studentParticipationRepository.findTestRunParticipationsByStudentIdAndIndividualExercisesWithEagerSubmissionsResult(studentExam.getUser().getId(),
                    studentExam.getExercises());
        }
        else {
            return studentParticipationRepository.findByStudentIdAndIndividualExercisesWithEagerSubmissionsResultIgnoreTestRuns(studentExam.getUser().getId(),
                    studentExam.getExercises());
        }
    }

    /**
     * Get a mapping of participation ids to the number of submission for each participation.
     *
     * @param exerciseId the id of the exercise for which to consider participations
     * @return the number of submissions per participation in the given exercise
     */
    public Map<Long, Integer> countSubmissionsPerParticipationByExerciseId(long exerciseId) {
        return convertListOfCountsIntoMap(studentParticipationRepository.countSubmissionsPerParticipationByExerciseId(exerciseId));
    }

    /**
     * Get a mapping of participation ids to the number of submission for each participation.
     *
     * @param courseId the id of the course for which to consider participations
     * @param teamShortName the short name of the team for which to consider participations
     * @return the number of submissions per participation in the given course for the team
     */
    public Map<Long, Integer> countSubmissionsPerParticipationByCourseIdAndTeamShortName(long courseId, String teamShortName) {
        return convertListOfCountsIntoMap(studentParticipationRepository.countSubmissionsPerParticipationByCourseIdAndTeamShortName(courseId, teamShortName));
    }

    /**
     * Converts List<[participationId, submissionCount]> into Map<participationId -> submissionCount>
     *
     * @param participationIdAndSubmissionCountPairs list of pairs (participationId, submissionCount)
     * @return map of participation id to submission count
     */
    private Map<Long, Integer> convertListOfCountsIntoMap(List<long[]> participationIdAndSubmissionCountPairs) {

        return participationIdAndSubmissionCountPairs.stream().collect(Collectors.toMap(participationIdAndSubmissionCountPair -> participationIdAndSubmissionCountPair[0], // participationId
                participationIdAndSubmissionCountPair -> Math.toIntExact(participationIdAndSubmissionCountPair[1]) // submissionCount
        ));
    }
}
