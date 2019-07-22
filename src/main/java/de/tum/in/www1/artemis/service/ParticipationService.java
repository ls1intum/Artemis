package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.config.Constants.PROGRAMMING_SUBMISSION_RESOURCE_API_PATH;
import static de.tum.in.www1.artemis.domain.enumeration.InitializationState.FINISHED;
import static de.tum.in.www1.artemis.domain.enumeration.InitializationState.INITIALIZED;

import java.net.URL;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    private final ProgrammingExerciseService programmingExerciseService;

    public ParticipationService(ParticipationRepository participationRepository, ExerciseRepository exerciseRepository, ResultRepository resultRepository,
            SubmissionRepository submissionRepository, ComplaintResponseRepository complaintResponseRepository, ComplaintRepository complaintRepository,
            QuizSubmissionService quizSubmissionService, ProgrammingExerciseRepository programmingExerciseRepository, UserService userService, Optional<GitService> gitService,
            Optional<ContinuousIntegrationService> continuousIntegrationService, Optional<VersionControlService> versionControlService,
            SimpMessageSendingOperations messagingTemplate, ModelAssessmentConflictService conflictService, AuthorizationCheckService authCheckService,
            ProgrammingExerciseService programmingExerciseService) {
        this.participationRepository = participationRepository;
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
        this.programmingExerciseService = programmingExerciseService;
    }

    /**
     * Save a participation.
     *
     * @param participation the entity to save
     * @return the persisted entity
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Participation save(Participation participation) {
        log.debug("Request to save Participation : {}", participation);
        return participationRepository.saveAndFlush(participation);
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
    public Participation startExercise(Exercise exercise, String username) {

        // common for all exercises
        // Check if participation already exists
        Participation participation = findOneByExerciseIdAndStudentLogin(exercise.getId(), username);
        boolean isNewParticipation = participation == null;
        if (isNewParticipation || (exercise instanceof ProgrammingExercise && participation.getInitializationState() == InitializationState.FINISHED)) {
            // create a new participation only if it was finished before (only for programming exercises)
            participation = new Participation();
            participation.setExercise(exercise);

            Optional<User> user = userService.getUserByLogin(username);
            if (user.isPresent()) {
                participation.setStudent(user.get());
            }
            participation = save(participation);
        }
        else {
            // make sure participation and exercise are connected
            participation.setExercise(exercise);
        }

        // specific to programming exercises
        if (exercise instanceof ProgrammingExercise) {
            // if (exercise.getCourse().isOnlineCourse()) {
            // participation.setLti(true);
            // } //TODO use lti in the future
            ProgrammingExercise programmingExercise = (ProgrammingExercise) exercise;
            participation.setInitializationState(InitializationState.UNINITIALIZED);
            participation = copyRepository(participation, programmingExercise);
            participation = configureRepository(participation);
            participation = copyBuildPlan(participation, programmingExercise);
            participation = configureBuildPlan(participation);
            participation = configureRepositoryWebHook(participation);
            // we configure the repository webhook after the build plan, because we might have to push an empty commit due to the bamboo workaround (see empty-commit-necessary)
            participation.setInitializationState(INITIALIZED);
            participation.setInitializationDate(ZonedDateTime.now());
        }
        else if (exercise instanceof QuizExercise || exercise instanceof ModelingExercise || exercise instanceof TextExercise || exercise instanceof FileUploadExercise) {
            if (participation.getInitializationState() == null || participation.getInitializationState() == FINISHED) {
                // in case the participation was finished before, we set it to initialized again so that the user sees the correct button "Open modeling editor" on the client side
                participation.setInitializationState(INITIALIZED);
            }

            if (!Optional.ofNullable(participation.getInitializationDate()).isPresent()) {
                participation.setInitializationDate(ZonedDateTime.now());
            }

            if (participation.getId() == null || !submissionRepository.existsByParticipationId(participation.getId())) {
                // initialize a modeling, text or file upload submission (depending on the exercise type), it will not do anything in the case of a quiz exercise
                initializeSubmission(participation, exercise);
            }
        }

        participation = save(participation);

        if (isNewParticipation) {
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

    public Participation participationForQuizWithResult(QuizExercise quizExercise, String username) {
        if (quizExercise.isEnded()) {
            // try getting participation from database first
            Optional<Participation> optionalParticipation = findOneByExerciseIdAndStudentLoginAndFinished(quizExercise.getId(), username);

            if (!optionalParticipation.isPresent()) {
                log.error("Participation in quiz " + quizExercise.getTitle() + " not found for user " + username);
                // TODO properly handle this case
                return null;
            }
            Participation participation = optionalParticipation.get();
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
        Participation participation = QuizScheduleService.getParticipation(quizExercise.getId(), username);
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
        participation = new Participation().initializationState(INITIALIZED).exercise(quizExercise).addResult(result);

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
     * @return resumed participation
     */
    public Participation resumeExercise(Exercise exercise, Participation participation) {
        // This is needed as a request using a custom query is made using the ProgrammingExerciseRepository, but the user is not authenticated
        // as the VCS-server performs the request
        SecurityUtils.setAuthorizationObject();

        // Reload programming exercise from database so that the template participation is available
        Optional<ProgrammingExercise> programmingExercise = programmingExerciseRepository.findById(exercise.getId());
        if (!programmingExercise.isPresent()) {
            return null;
        }
        participation = copyBuildPlan(participation, programmingExercise.get());
        participation = configureBuildPlan(participation);
        participation.setInitializationState(INITIALIZED);
        if (participation.getInitializationDate() == null) {
            // only set the date if it was not set before (which should NOT be the case)
            participation.setInitializationDate(ZonedDateTime.now());
        }
        save(participation);
        return participation;
    }

    private Participation copyRepository(Participation participation, ProgrammingExercise exercise) {
        if (!participation.getInitializationState().hasCompletedState(InitializationState.REPO_COPIED)) {
            URL repositoryUrl = versionControlService.get().copyRepository(exercise.getTemplateRepositoryUrlAsUrl(), participation.getStudent().getLogin());
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

    private Participation configureRepository(Participation participation) {
        if (!participation.getInitializationState().hasCompletedState(InitializationState.REPO_CONFIGURED)) {
            versionControlService.get().configureRepository(participation.getRepositoryUrlAsUrl(), participation.getStudent().getLogin());
            participation.setInitializationState(InitializationState.REPO_CONFIGURED);
            return save(participation);
        }
        else {
            return participation;
        }
    }

    private Participation copyBuildPlan(Participation participation, ProgrammingExercise exercise) {
        if (!participation.getInitializationState().hasCompletedState(InitializationState.BUILD_PLAN_COPIED)) {
            String buildPlanId = continuousIntegrationService.get().copyBuildPlan(exercise.getTemplateBuildPlanId(), participation.getStudent().getLogin());
            participation.setBuildPlanId(buildPlanId);
            participation.setInitializationState(InitializationState.BUILD_PLAN_COPIED);
            return save(participation);
        }
        else {
            return participation;
        }
    }

    private Participation configureBuildPlan(Participation participation) {
        if (!participation.getInitializationState().hasCompletedState(InitializationState.BUILD_PLAN_CONFIGURED)) {
            continuousIntegrationService.get().configureBuildPlan(participation);
            participation.setInitializationState(InitializationState.BUILD_PLAN_CONFIGURED);
            return save(participation);
        }
        else {
            return participation;
        }
    }

    private Participation configureRepositoryWebHook(Participation participation) {
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
     * Get all the participations.
     *
     * @return the list of participations
     */
    @Transactional(readOnly = true)
    public List<Participation> findAll() {
        log.debug("Request to get all Participations");
        return participationRepository.findAll();
    }

    /**
     * Get all the participations.
     *
     * @param pageable the pagination information
     * @return the list of participations
     */
    @Transactional(readOnly = true)
    public Page<Participation> findAll(Pageable pageable) {
        log.debug("Request to get all Participations");
        return participationRepository.findAll(pageable);
    }

    /**
     * Get one participation by id.
     *
     * @param participationId the id of the participation
     * @return the participation
     */
    @Transactional(readOnly = true)
    public Participation findOne(Long participationId) {
        log.debug("Request to get Participation : {}", participationId);
        Optional<Participation> participation = participationRepository.findById(participationId);
        if (!participation.isPresent()) {
            throw new EntityNotFoundException("Participation with " + participationId + " was not found!");
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
    public Participation findOneWithEagerResults(Long participationId) {
        log.debug("Request to get Participation : {}", participationId);
        Optional<Participation> participation = participationRepository.findByIdWithEagerResults(participationId);
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
    public Participation findOneWithEagerSubmissionsAndResults(Long participationId) {
        log.debug("Request to get Participation : {}", participationId);
        Optional<Participation> participation = participationRepository.findWithEagerSubmissionsAndResultsById(participationId);
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
    public Participation findOneWithEagerResultsAndSubmissionsAndAssessor(Long participationId) {
        log.debug("Request to get Participation : {}", participationId);
        Optional<Participation> participation = participationRepository.findByIdWithEagerSubmissionsAndEagerResultsAndEagerAssessors(participationId);
        if (!participation.isPresent()) {
            throw new EntityNotFoundException("Participation with " + participationId + " was not found!");
        }
        return participation.get();
    }

    /**
     * Get one initialized/inactive participation by its student and exercise.
     *
     * @param exerciseId the project key of the exercise
     * @param username   the username of the student
     * @return the participation of the given student and exercise in state initialized or inactive
     */
    @Transactional(readOnly = true)
    public Participation findOneByExerciseIdAndStudentLogin(Long exerciseId, String username) {
        log.debug("Request to get initialized/inactive Participation for User {} for Exercise with id: {}", username, exerciseId);

        Participation participation = participationRepository.findOneByExerciseIdAndStudentLoginAndInitializationState(exerciseId, username, INITIALIZED);
        if (participation == null) {
            participation = participationRepository.findOneByExerciseIdAndStudentLoginAndInitializationState(exerciseId, username, InitializationState.INACTIVE);
        }
        return participation;
    }

    /**
     * Get one participation (in any state) by its student and exercise.
     *
     * @param exerciseId the project key of the exercise
     * @param username   the username of the student
     * @return the participation of the given student and exercise in any state
     */
    @Transactional(readOnly = true)
    public Optional<Participation> findOneByExerciseIdAndStudentLoginAnyState(Long exerciseId, String username) {
        log.debug("Request to get Participation for User {} for Exercise with id: {}", username, exerciseId);
        return participationRepository.findByExerciseIdAndStudentLogin(exerciseId, username);
    }

    /**
     * Get one finished participation by its student and exercise.
     *
     * @param exerciseId the project key of the exercise
     * @param username   the username of the student
     * @return the participation of the given student and exercise in state finished
     */
    @Transactional(readOnly = true)
    public Optional<Participation> findOneByExerciseIdAndStudentLoginAndFinished(Long exerciseId, String username) {
        log.debug("Request to get Participation for User {} for Exercise with id: {}", username, exerciseId);
        return participationRepository.findByInitializationStateAndExerciseIdAndStudentLogin(InitializationState.FINISHED, exerciseId, username);
    }

    /**
     * Get one participation (in any state) by its student and exercise with eager submissions.
     *
     * @param exerciseId the project key of the exercise
     * @param username   the username of the student
     * @return the participation of the given student and exercise with eager submissions in any state
     */
    @Transactional(readOnly = true)
    public Optional<Participation> findOneByExerciseIdAndStudentLoginWithEagerSubmissionsAnyState(Long exerciseId, String username) {
        log.debug("Request to get Participation for User {} for Exercise with id: {}", username, exerciseId);
        return participationRepository.findWithEagerSubmissionsByExerciseIdAndStudentLogin(exerciseId, username);
    }

    /**
     * Get all participations for the given student including all results
     *
     * @param username the username of the student
     * @return the list of participations of the given student including all results
     */
    @Transactional(readOnly = true)
    public List<Participation> findWithResultsByStudentUsername(String username) {
        return participationRepository.findByStudentUsernameWithEagerResults(username);
    }

    @Transactional(readOnly = true)
    public List<Participation> findByBuildPlanIdAndInitializationState(String buildPlanId, InitializationState state) {
        log.debug("Request to get Participation for build plan id: {}", buildPlanId);
        return participationRepository.findByBuildPlanIdAndInitializationState(buildPlanId, state);
    }

    @Transactional(readOnly = true)
    public List<Participation> findByExerciseId(Long exerciseId) {
        return participationRepository.findByExerciseId(exerciseId);
    }

    @Transactional(readOnly = true)
    public List<Participation> findByExerciseIdWithEagerResults(Long exerciseId) {
        return participationRepository.findByExerciseIdWithEagerResults(exerciseId);
    }

    @Transactional(readOnly = true)
    public List<Participation> findByExerciseIdAndStudentIdWithEagerResults(Long exerciseId, Long studentId) {
        return participationRepository.findByExerciseIdAndStudentIdWithEagerResults(exerciseId, studentId);
    }

    /**
     * Get all participations of submissions that are submitted and do not already have a manual result. No manual result means that no user has started an assessment for the
     * corresponding submission yet.
     *
     * @param exerciseId the id of the exercise the participations should belong to
     * @return a list of participations including their submitted submissions that do not have a manual result
     */
    @Transactional(readOnly = true)
    public List<Participation> findByExerciseIdWithEagerSubmittedSubmissionsWithoutManualResults(Long exerciseId) {
        return participationRepository.findByExerciseIdWithEagerSubmittedSubmissionsWithoutManualResults(exerciseId);
    }

    @Transactional(readOnly = true)
    public List<Participation> findByCourseIdWithRelevantResults(Long courseId, Boolean includeNotRatedResults, Boolean includeAssessors) {
        List<Participation> participations = includeAssessors ? participationRepository.findByCourseIdWithEagerResultsAndAssessors(courseId)
                : participationRepository.findByCourseIdWithEagerResults(courseId);

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
     * @param participation
     */
    @Transactional
    public void cleanupBuildPlan(Participation participation) {
        if (participation.getBuildPlanId() != null) { // ignore participations without build plan id
            continuousIntegrationService.get().deleteBuildPlan(participation.getBuildPlanId());
            participation.setInitializationState(InitializationState.INACTIVE);
            participation.setBuildPlanId(null);
            save(participation);
        }
    }

    /**
     * NOTICE: be careful with this method because it deletes the students code on the version control server Deletes the repository on the version control server and sets the
     * initialization state of the participation to finished This means the participation cannot be resumed in the future and would need to be restarted
     *
     * @param participation
     */
    @Transactional
    public void cleanupRepository(Participation participation) {
        if (participation.getRepositoryUrl() != null) {      // ignore participations without repository URL
            versionControlService.get().deleteRepository(participation.getRepositoryUrlAsUrl());
            participation.setRepositoryUrl(null);
            participation.setInitializationState(InitializationState.FINISHED);
            save(participation);
        }
    }

    /**
     * Delete the participation by id.
     *
     * @param id               the id of the entity
     * @param deleteBuildPlan  determines whether the corresponding build plan should be deleted as well
     * @param deleteRepository determines whether the corresponding repository should be deleted as well
     */
    @Transactional(noRollbackFor = { Throwable.class })
    public void delete(Long id, boolean deleteBuildPlan, boolean deleteRepository) {
        Participation participation = participationRepository.findById(id).get();
        log.debug("Request to delete Participation : {}", participation);

        if (participation.getExercise() instanceof ProgrammingExercise) {
            if (deleteBuildPlan && participation.getBuildPlanId() != null) {
                continuousIntegrationService.get().deleteBuildPlan(participation.getBuildPlanId());
            }
            if (deleteRepository && participation.getRepositoryUrl() != null) {
                try {
                    versionControlService.get().deleteRepository(participation.getRepositoryUrlAsUrl());
                }
                catch (Exception ex) {
                    log.error("Could not delete repository: " + ex.getMessage());
                }
            }

            // delete local repository cache
            try {
                gitService.get().deleteLocalRepository(participation);
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
            complaintResponseRepository.deleteByComplaint_Result_Participation_Id(id);
            complaintRepository.deleteByResult_Participation_Id(id);
        }

        if (participation.getResults() != null && participation.getResults().size() > 0) {
            for (Result result : participation.getResults()) {
                resultRepository.deleteById(result.getId());
                // The following code is necessary, because we might have submissions in results which are not properly connected to a participation and CASCASE_REMOVE is not
                // active in this case
                if (result.getSubmission() != null) {
                    Submission submissionToDelete = result.getSubmission();
                    submissionRepository.deleteById(submissionToDelete.getId());
                    result.setSubmission(null);
                    // make sure submissions don't get deleted twice (see below)
                    participation.removeSubmissions(submissionToDelete);
                }
            }
        }
        // The following case is necessary, because we might have submissions without result
        if (participation.getSubmissions() != null && participation.getSubmissions().size() > 0) {
            for (Submission submission : participation.getSubmissions()) {
                submissionRepository.deleteById(submission.getId());
            }
        }

        Exercise exercise = participation.getExercise();
        exercise.removeParticipation(participation);
        exerciseRepository.save(exercise);
        participationRepository.delete(participation);
    }

    /**
     * Delete all participations belonging to the given exercise
     *
     * @param exerciseId the id of the exercise
     */
    @Transactional
    public void deleteAllByExerciseId(Long exerciseId, boolean deleteBuildPlan, boolean deleteRepository) {
        List<Participation> participationsToDelete = findByExerciseId(exerciseId);
        System.out.println("FranciscoTest:");
        System.out.println(participationsToDelete.toString());

        for (Participation participation : participationsToDelete) {
            delete(participation.getId(), deleteBuildPlan, deleteRepository);
        }
    }

    public Participation findOneWithEagerCourse(Long participationId) {
        return participationRepository.findOneByIdWithEagerExerciseAndEagerCourse(participationId);
    }

    /**
     * Check if a participation can be accessed with the current user.
     *
     * @param participation
     * @return
     */
    @Nullable
    public boolean canAccessParticipation(Participation participation) {
        return Optional.ofNullable(participation).isPresent() && userHasPermissions(participation);
    }

    /**
     * Check if a user has permissions to to access a certain participation. This includes not only the owner of the participation but also the TAs and instructors of the course.
     *
     * @param participation
     * @return
     */
    private boolean userHasPermissions(Participation participation) {
        if (authCheckService.isOwnerOfParticipation(participation))
            return true;
        // if the user is not the owner of the participation, the user can only see it in case he is
        // a teaching assistant or an instructor of the course, or in case he is admin
        User user = userService.getUserWithGroupsAndAuthorities();
        Course course;
        // TODO: temporary workaround for problems with the relationship between exercise and participations / templateParticipation / solutionParticipation
        if (participation.getExercise() == null) {
            Optional<ProgrammingExercise> exercise = programmingExerciseService.getExerciseForSolutionOrTemplateParticipation(participation);
            if (!exercise.isPresent()) {
                return false;
            }
            course = exercise.get().getCourse();
        }
        else {
            course = participation.getExercise().getCourse();
        }
        return authCheckService.isAtLeastTeachingAssistantInCourse(course, user);
    }
}
