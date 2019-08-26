package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.config.Constants.PROGRAMMING_SUBMISSION_RESOURCE_API_PATH;
import static de.tum.in.www1.artemis.domain.enumeration.InitializationState.*;

import java.net.URL;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.domain.quiz.QuizSubmission;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.connectors.VersionControlService;
import de.tum.in.www1.artemis.service.scheduled.QuizScheduleService;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Service Implementation for managing Participation.
 */
@Service
@Transactional
public class ParticipationService {

    private final Logger log = LoggerFactory.getLogger(ParticipationService.class);

    @Value("${server.url}")
    private String ARTEMIS_BASE_URL;

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

    private final QuizSubmissionService quizSubmissionService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final UserService userService;

    private final Optional<GitService> gitService;

    private final Optional<ContinuousIntegrationService> continuousIntegrationService;

    private final Optional<VersionControlService> versionControlService;

    private final SimpMessageSendingOperations messagingTemplate;

    private final ModelAssessmentConflictService conflictService;

    private final AuthorizationCheckService authCheckService;

    public ParticipationService(ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository,
            TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository,
            SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository, ParticipationRepository participationRepository,
            StudentParticipationRepository studentParticipationRepository, ExerciseRepository exerciseRepository, ResultRepository resultRepository,
            SubmissionRepository submissionRepository, ComplaintResponseRepository complaintResponseRepository, ComplaintRepository complaintRepository,
            QuizSubmissionService quizSubmissionService, ProgrammingExerciseRepository programmingExerciseRepository, UserService userService, Optional<GitService> gitService,
            Optional<ContinuousIntegrationService> continuousIntegrationService, Optional<VersionControlService> versionControlService,
            SimpMessageSendingOperations messagingTemplate, ModelAssessmentConflictService conflictService, AuthorizationCheckService authCheckService) {
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
        this.quizSubmissionService = quizSubmissionService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.userService = userService;
        this.gitService = gitService;
        this.continuousIntegrationService = continuousIntegrationService;
        this.versionControlService = versionControlService;
        this.messagingTemplate = messagingTemplate;
        this.conflictService = conflictService;
        this.authCheckService = authCheckService;
    }

    /**
     * Save any type of participation without knowing the explicit subtype
     *
     * @param participation the entity to save
     * @return the persisted entity
     */
    public Participation save(Participation participation) {
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
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SolutionProgrammingExerciseParticipation save(SolutionProgrammingExerciseParticipation participation) {
        log.debug("Request to save SolutionProgrammingExerciseParticipation : {}", participation);
        return solutionProgrammingExerciseParticipationRepository.saveAndFlush(participation);
    }

    /**
     * This method is triggered when a student starts an exercise. It creates a Participation which connects the corresponding student and exercise. Additionally, it configures
     * repository / build plan related stuff for programming exercises. In the case of modeling or text exercises, it also initializes and stores the corresponding submission.
     *
     * @param exercise the exercise which is started
     * @param username the name of the user who starts the exercise
     * @return the participation connecting the given exercise and user
     */
    @Transactional
    public StudentParticipation startExercise(Exercise exercise, String username) {

        // common for all exercises
        // Check if participation already exists
        Optional<StudentParticipation> optionalStudentParticipation = findOneByExerciseIdAndStudentLoginAnyState(exercise.getId(), username);
        StudentParticipation participation;
        if (optionalStudentParticipation.isEmpty()) {
            // create a new participation only if no participation can be found
            if (exercise instanceof ProgrammingExercise) {
                participation = new ProgrammingExerciseStudentParticipation();
            }
            else {
                participation = new StudentParticipation();
            }
            participation.setExercise(exercise);

            Optional<User> user = userService.getUserByLogin(username);
            if (user.isPresent()) {
                participation.setStudent(user.get());
            }
            participation = save(participation);
        }
        else {
            // make sure participation and exercise are connected
            participation = optionalStudentParticipation.get();
            participation.setExercise(exercise);
        }

        // specific to programming exercises
        if (exercise instanceof ProgrammingExercise) {
            // if (exercise.getCourse().isOnlineCourse()) {
            // participation.setLti(true);
            // } //TODO use lti in the future
            ProgrammingExerciseStudentParticipation programmingExerciseStudentParticipation = (ProgrammingExerciseStudentParticipation) participation;
            participation.setInitializationState(InitializationState.UNINITIALIZED);
            programmingExerciseStudentParticipation = copyRepository(programmingExerciseStudentParticipation);
            programmingExerciseStudentParticipation = configureRepository(programmingExerciseStudentParticipation);
            programmingExerciseStudentParticipation = copyBuildPlan(programmingExerciseStudentParticipation);
            programmingExerciseStudentParticipation = configureBuildPlan(programmingExerciseStudentParticipation);
            participation = configureRepositoryWebHook(programmingExerciseStudentParticipation);
            // we configure the repository webhook after the build plan, because we might have to push an empty commit due to the bamboo workaround (see empty-commit-necessary)
            programmingExerciseStudentParticipation.setInitializationState(INITIALIZED);
            programmingExerciseStudentParticipation.setInitializationDate(ZonedDateTime.now());
        }
        else {// for all other exercises: QuizExercise, ModelingExercise, TextExercise, FileUploadExercise
            if (participation.getInitializationState() == null || participation.getInitializationState() == FINISHED) {
                // in case the participation was finished before, we set it to initialized again so that the user sees the correct button "Open modeling editor" on the client side
                participation.setInitializationState(INITIALIZED);
            }

            if (Optional.ofNullable(participation.getInitializationDate()).isEmpty()) {
                participation.setInitializationDate(ZonedDateTime.now());
            }

            if (optionalStudentParticipation.isEmpty() || !submissionRepository.existsByParticipationId(participation.getId())) {
                // initialize a modeling, text or file upload submission (depending on the exercise type), it will not do anything in the case of a quiz exercise
                initializeSubmission(participation, exercise);
            }
        }

        participation = save(participation);

        if (optionalStudentParticipation.isEmpty()) {
            // only send a new participation to the client over websocket
            messagingTemplate.convertAndSendToUser(username, "/topic/exercise/" + exercise.getId() + "/participation", participation);
        }

        return participation;
    }

    /**
     * Initializes a new text, modeling or file upload submission (depending on the type of the given exercise), connects it with the given participation and stores it in the
     * database.
     *
     * @param participation the participation for which the submission should be initialized
     * @param exercise      the corresponding exercise, should be either a text, modeling or file upload exercise, otherwise it will instantly return and not do anything
     */
    private void initializeSubmission(Participation participation, Exercise exercise) {
        if (exercise instanceof ProgrammingExercise || exercise instanceof QuizExercise) {
            return;
        }

        Submission submission;
        if (exercise instanceof ModelingExercise) {
            submission = new ModelingSubmission();
        }
        else if (exercise instanceof TextExercise) {
            submission = new TextSubmission();
        }
        else {
            submission = new FileUploadSubmission();
        }

        submission.setParticipation(participation);
        submissionRepository.save(submission);
        participation.addSubmissions(submission);
    }

    /**
     * Get a participation for the given quiz and username If the quiz hasn't ended, participation is constructed from cached submission If the quiz has ended, we first look in the
     * database for the participation and construct one if none was found
     *
     * @param quizExercise the quiz exercise to attach to the participation
     * @param username     the username of the user that the participation belongs to
     * @return the found or created participation with a result
     */

    public StudentParticipation participationForQuizWithResult(QuizExercise quizExercise, String username) {
        if (quizExercise.isEnded()) {
            // try getting participation from database first
            Optional<StudentParticipation> optionalParticipation = findOneByExerciseIdAndStudentLoginAndFinished(quizExercise.getId(), username);

            if (!optionalParticipation.isPresent()) {
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
                if (result.getSubmission() == null) {
                    Submission submission = quizSubmissionService.findOne(result.getSubmission().getId());
                    result.setSubmission(submission);
                }
            }

            return participation;
        }

        // Look for Participation in ParticipationHashMap first
        StudentParticipation participation = QuizScheduleService.getParticipation(quizExercise.getId(), username);
        if (participation != null) {
            return participation;
        }

        // get submission from HashMap
        QuizSubmission quizSubmission = QuizScheduleService.getQuizSubmission(quizExercise.getId(), username);
        if (quizExercise.isEnded() && quizSubmission.getSubmissionDate() != null) {
            if (quizSubmission.isSubmitted()) {
                quizSubmission.setType(SubmissionType.MANUAL);
            }
            else {
                quizSubmission.setSubmitted(true);
                quizSubmission.setType(SubmissionType.TIMEOUT);
                quizSubmission.setSubmissionDate(ZonedDateTime.now());
            }
        }

        // construct result
        Result result = new Result().submission(quizSubmission);

        // construct participation
        participation = new StudentParticipation();
        participation.setInitializationState(INITIALIZED);
        participation.setExercise(quizExercise);
        participation.addResult(result);

        if (quizExercise.isEnded() && quizSubmission.getSubmissionDate() != null) {
            // update result and participation state
            result.setRated(true);
            result.setAssessmentType(AssessmentType.AUTOMATIC);
            result.setCompletionDate(ZonedDateTime.now());
            participation.setInitializationState(InitializationState.FINISHED);

            // calculate scores and update result and submission accordingly
            quizSubmission.calculateAndUpdateScores(quizExercise);
            result.evaluateSubmission();
        }

        return participation;
    }

    /**
     * Service method to resume inactive participation (with previously deleted build plan)
     *
     * @param exercise exercise to which the inactive participation belongs
     * @param participation inactive participation
     * @return resumed participation
     */
    public ProgrammingExerciseStudentParticipation resumeExercise(Exercise exercise, ProgrammingExerciseStudentParticipation participation) {
        // This is needed as a request using a custom query is made using the ProgrammingExerciseRepository, but the user is not authenticated
        // as the VCS-server performs the request
        SecurityUtils.setAuthorizationObject();

        // Reload programming exercise from database so that the template participation is available
        Optional<ProgrammingExercise> programmingExercise = programmingExerciseRepository.findById(exercise.getId());
        if (!programmingExercise.isPresent()) {
            return null;
        }
        participation = copyBuildPlan(participation);
        participation = configureBuildPlan(participation);
        participation.setInitializationState(INITIALIZED);
        if (participation.getInitializationDate() == null) {
            // only set the date if it was not set before (which should NOT be the case)
            participation.setInitializationDate(ZonedDateTime.now());
        }
        save(participation);
        return participation;
    }

    private ProgrammingExerciseStudentParticipation copyRepository(ProgrammingExerciseStudentParticipation participation) {
        if (!participation.getInitializationState().hasCompletedState(InitializationState.REPO_COPIED)) {
            URL repositoryUrl = versionControlService.get().copyRepository(participation.getProgrammingExercise().getTemplateRepositoryUrlAsUrl(),
                    participation.getStudent().getLogin());
            if (Optional.ofNullable(repositoryUrl).isPresent()) {
                participation.setRepositoryUrl(repositoryUrl.toString());
                participation.setInitializationState(InitializationState.REPO_COPIED);
            }
            return save(participation);
        }
        else {
            return participation;
        }
    }

    private ProgrammingExerciseStudentParticipation configureRepository(ProgrammingExerciseStudentParticipation participation) {
        if (!participation.getInitializationState().hasCompletedState(InitializationState.REPO_CONFIGURED)) {
            versionControlService.get().configureRepository(participation.getRepositoryUrlAsUrl(), participation.getStudent().getLogin());
            participation.setInitializationState(InitializationState.REPO_CONFIGURED);
            return save(participation);
        }
        else {
            return participation;
        }
    }

    private ProgrammingExerciseStudentParticipation copyBuildPlan(ProgrammingExerciseStudentParticipation participation) {
        if (!participation.getInitializationState().hasCompletedState(InitializationState.BUILD_PLAN_COPIED)) {
            String buildPlanId = continuousIntegrationService.get().copyBuildPlan(participation.getProgrammingExercise().getTemplateBuildPlanId(),
                    participation.getStudent().getLogin());
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
            continuousIntegrationService.get().configureBuildPlan(participation);
            participation.setInitializationState(InitializationState.BUILD_PLAN_CONFIGURED);
            return save(participation);
        }
        else {
            return participation;
        }
    }

    private ProgrammingExerciseStudentParticipation configureRepositoryWebHook(ProgrammingExerciseStudentParticipation participation) {
        if (!participation.getInitializationState().hasCompletedState(InitializationState.INITIALIZED)) {
            versionControlService.get().addWebHook(participation.getRepositoryUrlAsUrl(), ARTEMIS_BASE_URL + PROGRAMMING_SUBMISSION_RESOURCE_API_PATH + participation.getId(),
                    "Artemis WebHook");
            return save(participation);
        }
        else {
            return participation;
        }
    }

    /**
     * Get one participation by id.
     *
     * @param participationId the id of the entity
     * @return the entity
     * @throws EntityNotFoundException throws if participation was not found
     **/
    @Transactional(readOnly = true)
    public Participation findOne(Long participationId) throws EntityNotFoundException {
        log.debug("Request to get Participation : {}", participationId);
        Optional<Participation> participation = participationRepository.findById(participationId);
        if (!participation.isPresent()) {
            throw new EntityNotFoundException("Participation with " + participationId + " was not found!");
        }
        return participation.get();
    }

    /**
     * Get one student participation by id.
     *
     * @param participationId the id of the entity
     * @return the entity
     **/
    @Transactional(readOnly = true)
    public StudentParticipation findOneStudentParticipation(Long participationId) {
        log.debug("Request to get Participation : {}", participationId);
        Optional<StudentParticipation> participation = studentParticipationRepository.findById(participationId);
        if (!participation.isPresent()) {
            throw new EntityNotFoundException("StudentParticipation with " + participationId + " was not found!");
        }
        return participation.get();
    }

    /**
     * Get one student participation by id with fetched Result, Submissions, Exercise and Course.
     *
     * @param participationId the id of the entity
     * @return the entity
     **/
    @Transactional(readOnly = true)
    public StudentParticipation findOneStudentParticipationWithEagerSubmissionsResultsExerciseAndCourse(Long participationId) {
        log.debug("Request to get Participation : {}", participationId);
        Optional<StudentParticipation> participation = studentParticipationRepository.findWithEagerSubmissionsAndResultsAndExerciseAndCourseById(participationId);
        if (!participation.isPresent()) {
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
    @Transactional(readOnly = true)
    public ProgrammingExerciseStudentParticipation findProgrammingExerciseParticipation(Long participationId) {
        log.debug("Request to get Participation : {}", participationId);
        Optional<ProgrammingExerciseStudentParticipation> participation = programmingExerciseStudentParticipationRepository.findById(participationId);
        if (!participation.isPresent()) {
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
    @Transactional(readOnly = true)
    public StudentParticipation findOneWithEagerResults(Long participationId) {
        log.debug("Request to get Participation : {}", participationId);
        Optional<StudentParticipation> participation = studentParticipationRepository.findByIdWithEagerResults(participationId);
        if (!participation.isPresent()) {
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
    @Transactional(readOnly = true)
    public StudentParticipation findOneWithEagerSubmissionsAndResults(Long participationId) {
        log.debug("Request to get Participation : {}", participationId);
        Optional<StudentParticipation> participation = studentParticipationRepository.findWithEagerSubmissionsAndResultsById(participationId);
        if (!participation.isPresent()) {
            throw new EntityNotFoundException("Participation with " + participationId + " was not found!");
        }
        return participation.get();
    }

    /**
     * Get one participation by id including all results and submissions.
     *
     * @param participationId the id of the participation
     * @return the participation with all its results
     */
    @Transactional(readOnly = true)
    public StudentParticipation findOneWithEagerResultsAndSubmissionsAndAssessor(Long participationId) {
        log.debug("Request to get Participation : {}", participationId);
        Optional<StudentParticipation> participation = studentParticipationRepository.findByIdWithEagerSubmissionsAndEagerResultsAndEagerAssessors(participationId);
        if (!participation.isPresent()) {
            throw new EntityNotFoundException("Participation with " + participationId + " was not found!");
        }
        return participation.get();
    }

    /**
     * Get one participation (in any state) by its student and exercise.
     *
     * @param exerciseId the project key of the exercise
     * @param username   the username of the student
     * @return the participation of the given student and exercise in any state
     */
    @Transactional(readOnly = true)
    public Optional<StudentParticipation> findOneByExerciseIdAndStudentLoginAnyState(Long exerciseId, String username) {
        log.debug("Request to get Participation for User {} for Exercise with id: {}", username, exerciseId);
        return studentParticipationRepository.findByExerciseIdAndStudentLogin(exerciseId, username);
    }

    /**
     * Get one finished participation by its student and exercise.
     *
     * @param exerciseId the project key of the exercise
     * @param username   the username of the student
     * @return the participation of the given student and exercise in state finished
     */
    @Transactional(readOnly = true)
    public Optional<StudentParticipation> findOneByExerciseIdAndStudentLoginAndFinished(Long exerciseId, String username) {
        log.debug("Request to get Participation for User {} for Exercise with id: {}", username, exerciseId);
        return studentParticipationRepository.findByInitializationStateAndExerciseIdAndStudentLogin(InitializationState.FINISHED, exerciseId, username);
    }

    /**
     * Get one participation (in any state) by its student and exercise with eager submissions.
     *
     * @param exerciseId the project key of the exercise
     * @param username   the username of the student
     * @return the participation of the given student and exercise with eager submissions in any state
     */
    @Transactional(readOnly = true)
    public Optional<StudentParticipation> findOneByExerciseIdAndStudentLoginWithEagerSubmissionsAnyState(Long exerciseId, String username) {
        log.debug("Request to get Participation for User {} for Exercise with id: {}", username, exerciseId);
        return studentParticipationRepository.findWithEagerSubmissionsByExerciseIdAndStudentLogin(exerciseId, username);
    }

    /**
     * Get all participations for the given student including all results
     *
     * @param username the username of the student
     * @return the list of participations of the given student including all results
     */
    @Transactional(readOnly = true)
    public List<StudentParticipation> findWithResultsByStudentUsername(String username) {
        return studentParticipationRepository.findByStudentUsernameWithEagerResults(username);
    }

    /**
     * Get all programming exercise participations belonging to build plan and initialize there state with eager results.
     *
     * @param buildPlanId the id of build plan
     * @param state initialization state
     * @return the list of programming exercise participations belonging to build plan
     */
    @Transactional(readOnly = true)
    public List<ProgrammingExerciseStudentParticipation> findByBuildPlanIdAndInitializationStateWithEagerResults(String buildPlanId, InitializationState state) {
        log.debug("Request to get Participation for build plan id: {}", buildPlanId);
        return programmingExerciseStudentParticipationRepository.findByBuildPlanIdAndInitializationState(buildPlanId, state);
    }

    /**
     * Get all programming exercise participations belonging to exercise.
     *
     * @param exerciseId the id of exercise
     * @return the list of programming exercise participations belonging to exercise
     */
    @Transactional(readOnly = true)
    public List<StudentParticipation> findByExerciseId(Long exerciseId) {
        return studentParticipationRepository.findByExerciseId(exerciseId);
    }

    /**
     * Get all programming exercise participations belonging to exercise with eager results.
     *
     * @param exerciseId the id of exercise
     * @return the list of programming exercise participations belonging to exercise
     */
    @Transactional(readOnly = true)
    public List<StudentParticipation> findByExerciseIdWithEagerResults(Long exerciseId) {
        return studentParticipationRepository.findByExerciseIdWithEagerResults(exerciseId);
    }

    /**
     * Get all programming exercise participations belonging to exercise and student with eager results.
     *
     * @param exerciseId the id of exercise
     * @param studentId the id of student
     * @return the list of programming exercise participations belonging to exercise and student
     */
    @Transactional(readOnly = true)
    public List<StudentParticipation> findByExerciseIdAndStudentIdWithEagerResults(Long exerciseId, Long studentId) {
        return studentParticipationRepository.findByExerciseIdAndStudentIdWithEagerResults(exerciseId, studentId);
    }

    /**
     * Get all participations of submissions that are submitted and do not already have a manual result. No manual result means that no user has started an assessment for the
     * corresponding submission yet.
     *
     * @param exerciseId the id of the exercise the participations should belong to
     * @return a list of participations including their submitted submissions that do not have a manual result
     */
    @Transactional(readOnly = true)
    public List<StudentParticipation> findByExerciseIdWithEagerSubmittedSubmissionsWithoutManualResults(Long exerciseId) {
        return studentParticipationRepository.findByExerciseIdWithEagerSubmittedSubmissionsWithoutManualResults(exerciseId);
    }

    /**
     * Get all participations belonging to course with relevant results.
     *
     * @param courseId the id of the exercise
     * @param includeNotRatedResults specify is not rated results are included
     * @param includeAssessors specify id assessors are included
     * @return list of participations belonging to course
     */
    @Transactional(readOnly = true)
    public List<StudentParticipation> findByCourseIdWithRelevantResults(Long courseId, Boolean includeNotRatedResults, Boolean includeAssessors) {
        List<StudentParticipation> participations = includeAssessors ? studentParticipationRepository.findByCourseIdWithEagerResultsAndAssessors(courseId)
                : studentParticipationRepository.findByCourseIdWithEagerResults(courseId);

        return participations.stream()

                // Filter out participations without Students
                // These participations are used e.g. to store template and solution build plans in programming exercises
                .filter(participation -> participation.getStudent() != null)

                // filter all irrelevant results, i.e. rated = false or before exercise due date
                .peek(participation -> {
                    List<Result> relevantResults = new ArrayList<Result>();

                    // search for the relevant result by filtering out irrelevant results using the continue keyword
                    // this for loop is optimized for performance and thus not very easy to understand ;)
                    for (Result result : participation.getResults()) {
                        if (!includeNotRatedResults && result.isRated() == Boolean.FALSE) {
                            // we are only interested in results with rated == null (for compatibility) and rated == Boolean.TRUE
                            // TODO: for compatibility reasons, we include rated == null, in the future we can remove this
                            continue;
                        }
                        if (result.getCompletionDate() == null || result.getScore() == null) {
                            // we are only interested in results with completion date and with score
                            continue;
                        }
                        if (participation.getExercise() instanceof QuizExercise) {
                            // in quizzes we take all rated results, because we only have one! (independent of later checks)
                        }
                        else if (participation.getExercise().getDueDate() != null) {
                            if (participation.getExercise() instanceof ModelingExercise || participation.getExercise() instanceof TextExercise) {
                                if (result.getSubmission() != null && result.getSubmission().getSubmissionDate() != null
                                        && result.getSubmission().getSubmissionDate().isAfter(participation.getExercise().getDueDate())) {
                                    // Filter out late results using the submission date, because in this exercise types, the
                                    // difference between submissionDate and result.completionDate can be significant due to manual assessment
                                    continue;
                                }
                            }
                            // For all other exercises the result completion date is the same as the submission date
                            else if (result.getCompletionDate().isAfter(participation.getExercise().getDueDate())) {
                                // and we continue (i.e. dismiss the result) if the result completion date is after the exercise due date
                                continue;
                            }
                        }
                        relevantResults.add(result);
                    }
                    if (!relevantResults.isEmpty()) {
                        // make sure to take the latest result
                        relevantResults.sort((r1, r2) -> r2.getCompletionDate().compareTo(r1.getCompletionDate()));
                        Result correctResult = relevantResults.get(0);
                        relevantResults.clear();
                        relevantResults.add(correctResult);
                    }
                    participation.setResults(new HashSet<>(relevantResults));
                }).collect(Collectors.toList());
    }

    /**
     * Deletes the build plan on the continuous integration server and sets the initialization state of the participation to inactive This means the participation can be resumed in
     * the future
     *
     * @param participation that will be set to inactive
     */
    @Transactional
    public void cleanupBuildPlan(ProgrammingExerciseStudentParticipation participation) {
        if (participation.getBuildPlanId() != null) { // ignore participations without build plan id
            continuousIntegrationService.get().deleteBuildPlan(participation.getBuildPlanId());
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
    @Transactional
    public void cleanupRepository(ProgrammingExerciseStudentParticipation participation) {
        if (participation.getRepositoryUrl() != null) {      // ignore participations without repository URL
            versionControlService.get().deleteRepository(participation.getRepositoryUrlAsUrl());
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
    @Transactional(noRollbackFor = { Throwable.class })
    public void delete(Long participationId, boolean deleteBuildPlan, boolean deleteRepository) {
        StudentParticipation participation = studentParticipationRepository.findWithEagerSubmissionsAndResultsById(participationId).get();
        log.debug("Request to delete Participation : {}", participation);

        if (participation instanceof ProgrammingExerciseStudentParticipation) {
            ProgrammingExerciseStudentParticipation programmingExerciseParticipation = (ProgrammingExerciseStudentParticipation) participation;
            if (deleteBuildPlan && programmingExerciseParticipation.getBuildPlanId() != null) {
                continuousIntegrationService.get().deleteBuildPlan(programmingExerciseParticipation.getBuildPlanId());
            }
            if (deleteRepository && programmingExerciseParticipation.getRepositoryUrl() != null) {
                try {
                    versionControlService.get().deleteRepository(programmingExerciseParticipation.getRepositoryUrlAsUrl());
                }
                catch (Exception ex) {
                    log.error("Could not delete repository: " + ex.getMessage());
                }
            }

            // delete local repository cache
            try {
                gitService.get().deleteLocalRepository(programmingExerciseParticipation);
            }
            catch (Exception ex) {
                log.error("Error while deleting local repository", ex.getMessage());
            }
        }
        else if (participation.getExercise() instanceof ModelingExercise) {
            conflictService.deleteAllConflictsForParticipation(participation);
        }

        if (participation.getExercise() instanceof ModelingExercise || participation.getExercise() instanceof TextExercise) {
            // For modeling and text exercises students can send complaints about their assessments and we need to remove
            // the complaints and the according responses belonging to a participation before deleting the participation itself.
            complaintResponseRepository.deleteByComplaint_Result_Participation_Id(participationId);
            complaintRepository.deleteByResult_Participation_Id(participationId);
        }

        participation = (StudentParticipation) deleteResultsAndSubmissionsOfParticipation(participation);

        Exercise exercise = participation.getExercise();
        exercise.removeParticipation(participation);
        exerciseRepository.save(exercise);
        studentParticipationRepository.delete(participation);
    }

    /**
     * Remove all results and submissions of the given participation.
     * Will do nothing if invoked with a participation without results/submissions.
     * @param participation to delete results/submissions from.
     * @return participation without submissions and results.
     */
    @Transactional
    public Participation deleteResultsAndSubmissionsOfParticipation(Participation participation) {
        // This is the default case: We delete results and submissions from direction result -> submission. This will only delete submissions that have a result.
        if (participation.getResults() != null) {
            for (Result result : participation.getResults()) {
                resultRepository.deleteById(result.getId());
                // The following code is necessary, because we might have submissions in results which are not properly connected to a participation and CASCASE_REMOVE is not
                // active in this case
                if (result.getSubmission() != null) {
                    Submission submissionToDelete = result.getSubmission();
                    submissionRepository.deleteById(submissionToDelete.getId());
                    result.setSubmission(null);
                    participation.removeSubmissions(submissionToDelete);
                }
            }
        }
        // The following case is necessary, because we might have submissions without a result. At this point only submissions without a result will still be connected to the
        // participation.
        if (participation.getSubmissions() != null) {
            for (Submission submission : participation.getSubmissions()) {
                submissionRepository.deleteById(submission.getId());
            }
        }
        return participation;
    }

    /**
     * Delete all participations belonging to the given exercise
     *
     * @param exerciseId the id of the exercise
     * @param deleteBuildPlan specify if build plan should be deleted
     * @param deleteRepository specify if repository should be deleted
     */
    @Transactional
    public void deleteAllByExerciseId(Long exerciseId, boolean deleteBuildPlan, boolean deleteRepository) {
        List<StudentParticipation> participationsToDelete = findByExerciseId(exerciseId);

        for (StudentParticipation participation : participationsToDelete) {
            delete(participation.getId(), deleteBuildPlan, deleteRepository);
        }
    }

    /**
     * Get one participation with eager course.
     *
     * @param participationId id of the participation
     * @return participation with eager course
     */
    public StudentParticipation findOneWithEagerCourse(Long participationId) {
        return studentParticipationRepository.findOneByIdWithEagerExerciseAndEagerCourse(participationId);
    }

    /**
     * Check if a participation can be accessed with the current user.
     *
     * @param participation to access
     * @return can user access participation
     */
    @Nullable
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
        User user = userService.getUserWithGroupsAndAuthorities();
        Course course = participation.getExercise().getCourse();
        return authCheckService.isAtLeastTeachingAssistantInCourse(course, user);
    }

    /**
     * Get template exercise participation belonging to build plan.
     *
     * @param planKey the id of build plan
     * @return template exercise participation belonging to build plan
     */
    public Optional<TemplateProgrammingExerciseParticipation> findTemplateParticipationByBuildPlanId(String planKey) {
        return templateProgrammingExerciseParticipationRepository.findByBuildPlanIdWithResults(planKey);
    }

    /**
     * Get solution exercise participation belonging to build plan.
     *
     * @param planKey the id of build plan
     * @return solution exercise participation belonging to build plan
     */
    public Optional<SolutionProgrammingExerciseParticipation> findSolutionParticipationByBuildPlanId(String planKey) {
        return solutionProgrammingExerciseParticipationRepository.findByBuildPlanIdWithResults(planKey);
    }

    /**
     * Get all participations for the given student including all results
     *
     * @param studentId the user id of the student
     * @return the list of participations of the given student including all results
     */
    @Transactional(readOnly = true)
    public List<StudentParticipation> findWithResultsByStudentId(Long studentId, Set<Exercise> exercises) {
        return studentParticipationRepository.findByStudentIdWithEagerResults(studentId, exercises);
    }

    public List<StudentParticipation> findWithSubmissionsWithResultByStudentId(Long studentId, Set<Exercise> exercises) {
        return studentParticipationRepository.findAllByStudentIdWithSubmissionsWithResult(studentId, exercises);
    }
}
