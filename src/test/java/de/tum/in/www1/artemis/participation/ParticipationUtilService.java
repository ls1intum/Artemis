package de.tum.in.www1.artemis.participation;

import static de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseFactory.DEFAULT_BRANCH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.ZonedDateTime;
import java.util.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.*;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.*;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.domain.quiz.QuizSubmission;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.ParticipationService;
import de.tum.in.www1.artemis.service.UrlService;
import de.tum.in.www1.artemis.service.connectors.ci.ContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.vcs.VersionControlService;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.util.FileUtils;

/**
 * Service responsible for initializing the database with specific testdata related to participations, submissions and results.
 */
@Service
public class ParticipationUtilService {

    private static final ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(1);

    @Autowired
    private ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepo;

    @Autowired
    private StudentParticipationRepository studentParticipationRepo;

    @Autowired
    private ExerciseRepository exerciseRepo;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private TeamRepository teamRepo;

    @Autowired
    private ResultRepository resultRepo;

    @Autowired
    private FeedbackRepository feedbackRepo;

    @Autowired
    private RatingRepository ratingRepo;

    @Autowired
    private ModelingSubmissionRepository modelingSubmissionRepo;

    @Autowired
    private TextSubmissionRepository textSubmissionRepo;

    @Autowired
    private ProgrammingSubmissionRepository programmingSubmissionRepo;

    @Autowired
    private ExampleSubmissionRepository exampleSubmissionRepo;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private ParticipationService participationService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ObjectMapper mapper;

    @Value("${artemis.version-control.default-branch:main}")
    protected String defaultBranch;

    public Result addProgrammingParticipationWithResultForExercise(ProgrammingExercise exercise, String login) {
        var storedParticipation = programmingExerciseStudentParticipationRepo.findByExerciseIdAndStudentLogin(exercise.getId(), login);
        final StudentParticipation studentParticipation;
        if (storedParticipation.isEmpty()) {
            final var user = userUtilService.getUserByLogin(login);
            final var participation = new ProgrammingExerciseStudentParticipation();
            final var buildPlanId = exercise.getProjectKey().toUpperCase() + "-" + login.toUpperCase();
            final var repoName = (exercise.getProjectKey() + "-" + login).toLowerCase();
            participation.setInitializationDate(ZonedDateTime.now());
            participation.setParticipant(user);
            participation.setBuildPlanId(buildPlanId);
            participation.setProgrammingExercise(exercise);
            participation.setInitializationState(InitializationState.INITIALIZED);
            participation.setRepositoryUrl(String.format("http://some.test.url/%s/%s.git", exercise.getProjectKey(), repoName));
            programmingExerciseStudentParticipationRepo.save(participation);
            storedParticipation = programmingExerciseStudentParticipationRepo.findByExerciseIdAndStudentLogin(exercise.getId(), login);
            assertThat(storedParticipation).isPresent();
            studentParticipation = studentParticipationRepo.findWithEagerLegalSubmissionsAndResultsAssessorsById(storedParticipation.get().getId()).get();
        }
        else {
            studentParticipation = storedParticipation.get();
        }
        return addResultToParticipation(null, null, studentParticipation);
    }

    public Result createParticipationSubmissionAndResult(long exerciseId, Participant participant, Double points, Double bonusPoints, long scoreAwarded, boolean rated) {
        Exercise exercise = exerciseRepo.findById(exerciseId).get();
        if (!exercise.getMaxPoints().equals(points)) {
            exercise.setMaxPoints(points);
        }
        if (!exercise.getBonusPoints().equals(bonusPoints)) {
            exercise.setBonusPoints(bonusPoints);
        }
        exercise = exerciseRepo.saveAndFlush(exercise);
        StudentParticipation studentParticipation = participationService.startExercise(exercise, participant, false);
        return createSubmissionAndResult(studentParticipation, scoreAwarded, rated);
    }

    public Result createSubmissionAndResult(StudentParticipation studentParticipation, long scoreAwarded, boolean rated) {
        Exercise exercise = studentParticipation.getExercise();
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

        submission.setType(SubmissionType.MANUAL);
        submission.setParticipation(studentParticipation);
        submission = submissionRepository.saveAndFlush(submission);

        Result result = ParticipationFactory.generateResult(rated, scoreAwarded);
        result.setParticipation(studentParticipation);
        result.setSubmission(submission);
        result.completionDate(ZonedDateTime.now());
        submission.addResult(result);
        submission = submissionRepository.saveAndFlush(submission);
        return submission.getResults().get(0);
    }

    /**
     * Stores participation of the user with the given login for the given exercise
     *
     * @param exercise the exercise for which the participation will be created
     * @param login    login of the user
     * @return eagerly loaded representation of the participation object stored in the database
     */
    public StudentParticipation createAndSaveParticipationForExercise(Exercise exercise, String login) {
        Optional<StudentParticipation> storedParticipation = studentParticipationRepo.findWithEagerLegalSubmissionsByExerciseIdAndStudentLoginAndTestRun(exercise.getId(), login,
                false);
        if (storedParticipation.isEmpty()) {
            User user = userUtilService.getUserByLogin(login);
            StudentParticipation participation = new StudentParticipation();
            participation.setInitializationDate(ZonedDateTime.now());
            participation.setParticipant(user);
            participation.setExercise(exercise);
            studentParticipationRepo.save(participation);
            storedParticipation = studentParticipationRepo.findWithEagerLegalSubmissionsByExerciseIdAndStudentLoginAndTestRun(exercise.getId(), login, false);
            assertThat(storedParticipation).isPresent();
        }
        return studentParticipationRepo.findWithEagerLegalSubmissionsAndResultsAssessorsById(storedParticipation.get().getId()).get();
    }

    public StudentParticipation createAndSaveParticipationForExerciseInTheFuture(Exercise exercise, String login) {
        Optional<StudentParticipation> storedParticipation = studentParticipationRepo.findWithEagerLegalSubmissionsByExerciseIdAndStudentLoginAndTestRun(exercise.getId(), login,
                false);
        storedParticipation.ifPresent(studentParticipation -> studentParticipationRepo.delete(studentParticipation));
        User user = userUtilService.getUserByLogin(login);
        StudentParticipation participation = new StudentParticipation();
        participation.setInitializationDate(ZonedDateTime.now().plusDays(2));
        participation.setParticipant(user);
        participation.setExercise(exercise);
        studentParticipationRepo.save(participation);
        storedParticipation = studentParticipationRepo.findWithEagerLegalSubmissionsByExerciseIdAndStudentLoginAndTestRun(exercise.getId(), login, false);
        assertThat(storedParticipation).isPresent();
        return studentParticipationRepo.findWithEagerLegalSubmissionsAndResultsAssessorsById(storedParticipation.get().getId()).get();
    }

    /**
     * Stores participation of the team with the given id for the given exercise
     *
     * @param exercise the exercise for which the participation will be created
     * @param teamId   id of the team
     * @return eagerly loaded representation of the participation object stored in the database
     */
    public StudentParticipation addTeamParticipationForExercise(Exercise exercise, long teamId) {
        Optional<StudentParticipation> storedParticipation = studentParticipationRepo.findWithEagerLegalSubmissionsByExerciseIdAndTeamId(exercise.getId(), teamId);
        if (storedParticipation.isEmpty()) {
            Team team = teamRepo.findById(teamId).orElseThrow();
            StudentParticipation participation = new StudentParticipation();
            participation.setInitializationDate(ZonedDateTime.now());
            participation.setParticipant(team);
            participation.setExercise(exercise);
            studentParticipationRepo.save(participation);
            storedParticipation = studentParticipationRepo.findWithEagerLegalSubmissionsByExerciseIdAndTeamId(exercise.getId(), teamId);
            assertThat(storedParticipation).isPresent();
        }
        return studentParticipationRepo.findWithEagerLegalSubmissionsAndResultsAssessorsById(storedParticipation.get().getId()).get();
    }

    public ProgrammingExerciseStudentParticipation addStudentParticipationForProgrammingExercise(ProgrammingExercise exercise, String login) {
        final var existingParticipation = programmingExerciseStudentParticipationRepo.findByExerciseIdAndStudentLogin(exercise.getId(), login);
        if (existingParticipation.isPresent()) {
            return existingParticipation.get();
        }
        ProgrammingExerciseStudentParticipation participation = configureIndividualParticipation(exercise, login);
        final var repoName = (exercise.getProjectKey() + "-" + login).toLowerCase();
        participation.setRepositoryUrl(String.format("http://some.test.url/scm/%s/%s.git", exercise.getProjectKey(), repoName));
        participation = programmingExerciseStudentParticipationRepo.save(participation);

        return (ProgrammingExerciseStudentParticipation) studentParticipationRepo.findWithEagerLegalSubmissionsAndResultsAssessorsById(participation.getId()).get();
    }

    public ProgrammingExerciseStudentParticipation addTeamParticipationForProgrammingExercise(ProgrammingExercise exercise, Team team) {

        final var existingParticipation = programmingExerciseStudentParticipationRepo.findByExerciseIdAndTeamId(exercise.getId(), team.getId());
        if (existingParticipation.isPresent()) {
            return existingParticipation.get();
        }
        ProgrammingExerciseStudentParticipation participation = configureTeamParticipation(exercise, team);
        final var repoName = (exercise.getProjectKey() + "-" + team.getShortName()).toLowerCase();
        participation.setRepositoryUrl(String.format("http://some.test.url/scm/%s/%s.git", exercise.getProjectKey(), repoName));
        participation = programmingExerciseStudentParticipationRepo.save(participation);

        return (ProgrammingExerciseStudentParticipation) studentParticipationRepo.findWithEagerLegalSubmissionsAndResultsAssessorsById(participation.getId()).get();
    }

    public ProgrammingExerciseStudentParticipation addStudentParticipationForProgrammingExerciseForLocalRepo(ProgrammingExercise exercise, String login, URI localRepoPath) {
        final var existingParticipation = programmingExerciseStudentParticipationRepo.findByExerciseIdAndStudentLogin(exercise.getId(), login);
        if (existingParticipation.isPresent()) {
            return existingParticipation.get();
        }
        ProgrammingExerciseStudentParticipation participation = configureIndividualParticipation(exercise, login);
        final var repoName = (exercise.getProjectKey() + "-" + login).toLowerCase();
        participation.setRepositoryUrl(String.format(localRepoPath.toString() + "%s/%s.git", exercise.getProjectKey(), repoName));
        participation = programmingExerciseStudentParticipationRepo.save(participation);

        return (ProgrammingExerciseStudentParticipation) studentParticipationRepo.findWithEagerLegalSubmissionsAndResultsAssessorsById(participation.getId()).get();
    }

    private ProgrammingExerciseStudentParticipation configureIndividualParticipation(ProgrammingExercise exercise, String login) {
        final var user = userUtilService.getUserByLogin(login);
        var participation = new ProgrammingExerciseStudentParticipation();
        final var buildPlanId = exercise.getProjectKey().toUpperCase() + "-" + login.toUpperCase();
        participation.setInitializationDate(ZonedDateTime.now());
        participation.setParticipant(user);
        participation.setBuildPlanId(buildPlanId);
        participation.setProgrammingExercise(exercise);
        participation.setInitializationState(InitializationState.INITIALIZED);
        participation.setBranch(DEFAULT_BRANCH);
        return participation;
    }

    private ProgrammingExerciseStudentParticipation configureTeamParticipation(ProgrammingExercise exercise, Team team) {
        var participation = new ProgrammingExerciseStudentParticipation();
        final var buildPlanId = exercise.getProjectKey().toUpperCase() + "-" + team.getShortName().toUpperCase();
        participation.setInitializationDate(ZonedDateTime.now());
        participation.setParticipant(team);
        participation.setBuildPlanId(buildPlanId);
        participation.setProgrammingExercise(exercise);
        participation.setInitializationState(InitializationState.INITIALIZED);
        return participation;
    }

    public Result addResultToParticipation(AssessmentType type, ZonedDateTime completionDate, Participation participation, boolean successful, boolean rated, double score) {
        Result result = new Result().participation(participation).successful(successful).rated(rated).score(score).assessmentType(type).completionDate(completionDate);
        return resultRepo.save(result);
    }

    public Result addResultToParticipation(AssessmentType assessmentType, ZonedDateTime completionDate, Participation participation) {
        Result result = new Result().participation(participation).successful(true).rated(true).score(100D).assessmentType(assessmentType).completionDate(completionDate);
        return resultRepo.save(result);
    }

    public Result addResultToParticipation(AssessmentType assessmentType, ZonedDateTime completionDate, Participation participation, String assessorLogin,
            List<Feedback> feedbacks) {
        Result result = new Result().participation(participation).assessmentType(assessmentType).completionDate(completionDate).feedbacks(feedbacks);
        result.setAssessor(userUtilService.getUserByLogin(assessorLogin));
        return resultRepo.save(result);
    }

    public Result addResultToParticipation(Participation participation, Submission submission) {
        Result result = new Result().participation(participation).successful(true).score(100D);
        result = resultRepo.save(result);
        result.setSubmission(submission);
        submission.addResult(result);
        submission.setParticipation(participation);
        submissionRepository.save(submission);
        return result;
    }

    public Result addSampleFeedbackToResults(Result result) {
        Feedback feedback1 = feedbackRepo.save(new Feedback().detailText("detail1"));
        Feedback feedback2 = feedbackRepo.save(new Feedback().detailText("detail2"));
        List<Feedback> feedbacks = new ArrayList<>();
        feedbacks.add(feedback1);
        feedbacks.add(feedback2);
        result.addFeedbacks(feedbacks);
        return resultRepo.save(result);
    }

    // @formatter:off
    public Result addVariousFeedbackTypeFeedbacksToResult(Result result) {
        // The order of declaration here should be the same order as in FeedbackType for each enum type
        List<Feedback> feedbacks = feedbackRepo.saveAll(Arrays.asList(
            new Feedback().detailText("manual").type(FeedbackType.MANUAL),
            new Feedback().detailText("manual_unreferenced").type(FeedbackType.MANUAL_UNREFERENCED),
            new Feedback().detailText("automatic_adapted").type(FeedbackType.AUTOMATIC_ADAPTED),
            new Feedback().detailText("automatic").type(FeedbackType.AUTOMATIC)
        ));

        result.addFeedbacks(feedbacks);
        return resultRepo.save(result);
    }

    public Result addVariousVisibilityFeedbackToResult(Result result) {
        List<Feedback> feedbacks = feedbackRepo.saveAll(Arrays.asList(
            new Feedback().detailText("afterDueDate1").visibility(Visibility.AFTER_DUE_DATE),
            new Feedback().detailText("never1").visibility(Visibility.NEVER),
            new Feedback().detailText("always1").visibility(Visibility.ALWAYS)
        ));

        result.addFeedbacks(feedbacks);
        return resultRepo.save(result);
    }
    // @formatter:on

    public Result addFeedbackToResult(Feedback feedback, Result result) {
        feedbackRepo.save(feedback);
        result.addFeedback(feedback);
        return resultRepo.save(result);
    }

    public Result addFeedbackToResults(Result result) {
        List<Feedback> feedback = ParticipationFactory.generateStaticCodeAnalysisFeedbackList(5);
        feedback.addAll(ParticipationFactory.generateFeedback());
        feedback = feedbackRepo.saveAll(feedback);
        result.addFeedbacks(feedback);
        return resultRepo.save(result);
    }

    public Submission addResultToSubmission(final Submission submission, AssessmentType assessmentType, User user, Double score, boolean rated, ZonedDateTime completionDate) {
        Result result = new Result().participation(submission.getParticipation()).assessmentType(assessmentType).score(score).rated(rated).completionDate(completionDate);
        result.setAssessor(user);
        result = resultRepo.save(result);
        result.setSubmission(submission);
        submission.addResult(result);
        var savedSubmission = submissionRepository.save(submission);
        return submissionRepository.findWithEagerResultsAndAssessorById(savedSubmission.getId()).orElseThrow();
    }

    public Submission addResultToSubmission(Submission submission, AssessmentType assessmentType) {
        return addResultToSubmission(submission, assessmentType, null, 100D, true, null);
    }

    public Submission addResultToSubmission(Submission submission, AssessmentType assessmentType, User user) {
        return addResultToSubmission(submission, assessmentType, user, 100D, true, ZonedDateTime.now());
    }

    public Submission addResultToSubmission(Submission submission, AssessmentType assessmentType, User user, Double score, boolean rated) {
        return addResultToSubmission(submission, assessmentType, user, score, rated, ZonedDateTime.now());
    }

    public void addRatingToResult(Result result, int score) {
        var rating = new Rating();
        rating.setResult(result);
        rating.setRating(score);
        ratingRepo.save(rating);
    }

    public List<Submission> getAllSubmissionsOfExercise(Exercise exercise) {
        var participations = studentParticipationRepo.findByExerciseId(exercise.getId());
        var allSubmissions = new ArrayList<Submission>();
        participations.forEach(participation -> {
            Submission submission = submissionRepository.findAllByParticipationId(participation.getId()).get(0);
            allSubmissions.add(submissionRepository.findWithEagerResultAndFeedbackById(submission.getId()).orElseThrow());
        });
        return allSubmissions;
    }

    public void saveResultInParticipation(Submission submission, Result result) {
        submission.addResult(result);
        StudentParticipation participation = (StudentParticipation) submission.getParticipation();
        participation.addResult(result);
        studentParticipationRepo.save(participation);
    }

    public Result generateResult(Submission submission, User assessor) {
        Result result = new Result();
        result = resultRepo.save(result);
        result.setSubmission(submission);
        result.completionDate(pastTimestamp);
        result.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        result.setAssessor(assessor);
        result.setRated(true);
        return result;
    }

    public Result generateResultWithScore(Submission submission, User assessor, Double score) {
        Result result = generateResult(submission, assessor);
        result.setScore(score);
        return result;
    }

    public Submission addSubmission(Exercise exercise, Submission submission, String login) {
        StudentParticipation participation = createAndSaveParticipationForExercise(exercise, login);
        participation.addSubmission(submission);
        submission.setParticipation(participation);
        submissionRepository.save(submission);
        studentParticipationRepo.save(participation);
        return submission;
    }

    public Submission addSubmission(StudentParticipation participation, Submission submission) {
        participation.addSubmission(submission);
        submission.setParticipation(participation);
        submissionRepository.save(submission);
        studentParticipationRepo.save(participation);
        return submission;
    }

    public Submission addSubmissionWithTwoFinishedResultsWithAssessor(Exercise exercise, Submission submission, String login, String assessorLogin) {
        StudentParticipation participation = createAndSaveParticipationForExercise(exercise, login);
        submission = addSubmissionWithFinishedResultsWithAssessor(participation, submission, assessorLogin);
        submission = addSubmissionWithFinishedResultsWithAssessor(participation, submission, assessorLogin);
        return submission;
    }

    public Submission addSubmissionWithFinishedResultsWithAssessor(StudentParticipation participation, Submission submission, String assessorLogin) {
        Result result = new Result();
        result.setAssessor(userUtilService.getUserByLogin(assessorLogin));
        result.setCompletionDate(ZonedDateTime.now());
        result.setSubmission(submission);
        submission.setParticipation(participation);
        submission.addResult(result);
        submission.getParticipation().addResult(result);
        submission = saveSubmissionToRepo(submission);
        studentParticipationRepo.save(participation);
        return submission;
    }

    private Submission saveSubmissionToRepo(Submission submission) {
        if (submission instanceof ModelingSubmission) {
            return modelingSubmissionRepo.save((ModelingSubmission) submission);
        }
        else if (submission instanceof TextSubmission) {
            return textSubmissionRepo.save((TextSubmission) submission);
        }
        else if (submission instanceof ProgrammingSubmission) {
            return programmingSubmissionRepo.save((ProgrammingSubmission) submission);
        }
        return null;
    }

    public ExampleSubmission addExampleSubmission(ExampleSubmission exampleSubmission) {
        Submission submission;
        if (exampleSubmission.getSubmission() instanceof ModelingSubmission) {
            submission = modelingSubmissionRepo.save((ModelingSubmission) exampleSubmission.getSubmission());
        }
        else {
            submission = textSubmissionRepo.save((TextSubmission) exampleSubmission.getSubmission());
        }
        exampleSubmission.setSubmission(submission);
        return exampleSubmissionRepo.save(exampleSubmission);
    }

    public List<Feedback> loadAssessmentFomResources(String path) throws Exception {
        String fileContent = FileUtils.loadFileFromResources(path);
        return mapper.readValue(fileContent, mapper.getTypeFactory().constructCollectionType(List.class, Feedback.class));
    }

    /**
     * Generates an example submission for a given model and exercise
     *
     * @param modelOrText             given uml model for the example submission
     * @param exercise                exercise for which the example submission is created
     * @param flagAsExampleSubmission true if the submission is an example submission
     * @return created example submission
     */
    public ExampleSubmission generateExampleSubmission(String modelOrText, Exercise exercise, boolean flagAsExampleSubmission) {
        return generateExampleSubmission(modelOrText, exercise, flagAsExampleSubmission, false);
    }

    /**
     * Generates an example submission for a given model and exercise
     *
     * @param modelOrText             given uml model for the example submission
     * @param exercise                exercise for which the example submission is created
     * @param flagAsExampleSubmission true if the submission is an example submission
     * @param usedForTutorial         true if the example submission is used for tutorial
     * @return created example submission
     */
    public ExampleSubmission generateExampleSubmission(String modelOrText, Exercise exercise, boolean flagAsExampleSubmission, boolean usedForTutorial) {
        Submission submission;
        if (exercise instanceof ModelingExercise) {
            submission = ParticipationFactory.generateModelingSubmission(modelOrText, false);
        }
        else {
            submission = ParticipationFactory.generateTextSubmission(modelOrText, Language.ENGLISH, false);
            saveSubmissionToRepo(submission);
        }
        submission.setExampleSubmission(flagAsExampleSubmission);
        return ParticipationFactory.generateExampleSubmission(submission, exercise, usedForTutorial);
    }

    public void checkFeedbackCorrectlyStored(List<Feedback> sentFeedback, List<Feedback> storedFeedback, FeedbackType feedbackType) {
        assertThat(sentFeedback).as("contains the same amount of feedback").hasSize(storedFeedback.size());
        Result storedFeedbackResult = new Result();
        Result sentFeedbackResult = new Result();
        storedFeedbackResult.setFeedbacks(storedFeedback);
        sentFeedbackResult.setFeedbacks(sentFeedback);

        Course course = new Course();
        course.setAccuracyOfScores(1);
        storedFeedbackResult.setParticipation(new StudentParticipation().exercise(new ProgrammingExercise().course(course)));
        sentFeedbackResult.setParticipation(new StudentParticipation().exercise(new ProgrammingExercise().course(course)));

        double calculatedTotalPoints = resultRepo.calculateTotalPoints(storedFeedback);
        double totalPoints = resultRepo.constrainToRange(calculatedTotalPoints, 20.0);
        storedFeedbackResult.setScore(100.0 * totalPoints / 20.0);

        double calculatedTotalPoints2 = resultRepo.calculateTotalPoints(sentFeedback);
        double totalPoints2 = resultRepo.constrainToRange(calculatedTotalPoints2, 20.0);
        sentFeedbackResult.setScore(100.0 * totalPoints2 / 20.0);

        assertThat(storedFeedbackResult.getScore()).as("stored feedback evaluates to the same score as sent feedback").isEqualTo(sentFeedbackResult.getScore());
        storedFeedback.forEach(feedback -> assertThat(feedback.getType()).as("type has been set correctly").isEqualTo(feedbackType));
    }

    public StudentParticipation addAssessmentWithFeedbackWithGradingInstructionsForExercise(Exercise exercise, String login) {
        // add participation and submission for exercise
        StudentParticipation studentParticipation = createAndSaveParticipationForExercise(exercise, login);
        Submission submission = null;
        if (exercise instanceof TextExercise) {
            submission = ParticipationFactory.generateTextSubmission("test", Language.ENGLISH, true);
        }
        if (exercise instanceof FileUploadExercise) {
            submission = ParticipationFactory.generateFileUploadSubmission(true);
        }
        if (exercise instanceof ModelingExercise) {
            submission = ParticipationFactory.generateModelingSubmission(null, true);
        }
        if (exercise instanceof ProgrammingExercise) {
            submission = ParticipationFactory.generateProgrammingSubmission(true);
        }
        Submission submissionWithParticipation = addSubmission(studentParticipation, submission);
        Result result = addResultToParticipation(studentParticipation, submissionWithParticipation);
        resultRepo.save(result);

        assertThat(exercise.getGradingCriteria()).isNotNull();
        assertThat(exercise.getGradingCriteria().get(0).getStructuredGradingInstructions()).isNotNull();

        // add feedback which is associated with structured grading instructions
        Feedback feedback = new Feedback();
        feedback.setGradingInstruction(exercise.getGradingCriteria().get(0).getStructuredGradingInstructions().get(0));
        addFeedbackToResult(feedback, result);
        return studentParticipation;
    }

    public List<Result> getResultsForExercise(Exercise exercise) {
        return resultRepo.findWithEagerSubmissionAndFeedbackByParticipationExerciseId(exercise.getId());
    }

    public void mockCreationOfExerciseParticipation(boolean useGradedParticipationOfResult, Result gradedResult, ProgrammingExercise programmingExercise, UrlService urlService,
            VersionControlService versionControlService, ContinuousIntegrationService continuousIntegrationService) throws URISyntaxException {
        String templateRepoName;
        if (useGradedParticipationOfResult) {
            templateRepoName = urlService.getRepositorySlugFromRepositoryUrl(((ProgrammingExerciseStudentParticipation) gradedResult.getParticipation()).getVcsRepositoryUrl());
        }
        else {
            templateRepoName = urlService.getRepositorySlugFromRepositoryUrl(programmingExercise.getVcsTemplateRepositoryUrl());
        }
        mockCreationOfExerciseParticipation(templateRepoName, programmingExercise, versionControlService, continuousIntegrationService);
    }

    public void mockCreationOfExerciseParticipation(String templateRepoName, ProgrammingExercise programmingExercise, VersionControlService versionControlService,
            ContinuousIntegrationService continuousIntegrationService) throws URISyntaxException {
        var someURL = new VcsRepositoryUrl("http://vcs.fake.fake");
        doReturn(someURL).when(versionControlService).copyRepository(any(String.class), eq(templateRepoName), any(String.class), any(String.class), any(String.class));
        mockCreationOfExerciseParticipationInternal(programmingExercise, versionControlService, continuousIntegrationService);
    }

    public void mockCreationOfExerciseParticipation(ProgrammingExercise programmingExercise, VersionControlService versionControlService,
            ContinuousIntegrationService continuousIntegrationService) throws URISyntaxException {
        var someURL = new VcsRepositoryUrl("http://vcs.fake.fake");
        doReturn(someURL).when(versionControlService).copyRepository(any(String.class), any(), any(String.class), any(String.class), any(String.class));
        mockCreationOfExerciseParticipationInternal(programmingExercise, versionControlService, continuousIntegrationService);
    }

    private void mockCreationOfExerciseParticipationInternal(ProgrammingExercise programmingExercise, VersionControlService versionControlService,
            ContinuousIntegrationService continuousIntegrationService) {
        doReturn(defaultBranch).when(versionControlService).getOrRetrieveBranchOfExercise(programmingExercise);
        doReturn(defaultBranch).when(versionControlService).getOrRetrieveBranchOfStudentParticipation(any());

        doNothing().when(versionControlService).configureRepository(any(), any(), anyBoolean());
        doReturn("buildPlanId").when(continuousIntegrationService).copyBuildPlan(any(), any(), any(), any(), any(), anyBoolean());
        doNothing().when(continuousIntegrationService).configureBuildPlan(any(), any());
        doNothing().when(continuousIntegrationService).performEmptySetupCommit(any());
        doNothing().when(versionControlService).addWebHookForParticipation(any());
    }
}
