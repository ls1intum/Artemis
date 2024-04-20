package de.tum.in.www1.artemis.participation;

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
import de.tum.in.www1.artemis.exercise.GradingCriterionUtil;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.ParticipationService;
import de.tum.in.www1.artemis.service.UriService;
import de.tum.in.www1.artemis.service.connectors.ci.ContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.vcs.VersionControlService;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.util.TestResourceUtils;

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
    private ProgrammingSubmissionTestRepository programmingSubmissionRepo;

    @Autowired
    private ExampleSubmissionRepository exampleSubmissionRepo;

    @Autowired
    private ParticipationService participationService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ObjectMapper mapper;

    @Value("${artemis.version-control.default-branch:main}")
    protected String defaultBranch;

    /**
     * Creates and saves a Result for a ProgrammingExerciseStudentParticipation associated with the given ProgrammingExercise and login. If no corresponding
     * ProgrammingExerciseStudentParticipation exists, it will be created and saved as well.
     *
     * @param exercise The ProgrammingExercise the ProgrammingExerciseStudentParticipation belongs to
     * @param login    The login of the user the ProgrammingExerciseStudentParticipation belongs to
     * @return The created Result
     */
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
            participation.setRepositoryUri(String.format("http://some.test.url/%s/%s.git", exercise.getProjectKey(), repoName));
            programmingExerciseStudentParticipationRepo.save(participation);
            storedParticipation = programmingExerciseStudentParticipationRepo.findByExerciseIdAndStudentLogin(exercise.getId(), login);
            assertThat(storedParticipation).isPresent();
            studentParticipation = studentParticipationRepo.findWithEagerLegalSubmissionsAndResultsAssessorsById(storedParticipation.get().getId()).orElseThrow();
        }
        else {
            studentParticipation = storedParticipation.get();
        }
        return addResultToParticipation(null, null, studentParticipation);
    }

    /**
     * Creates and saves a StudentParticipation for the given exerciseId and Participant. Then creates and saves a Result and Submission for the StudentParticipation.
     *
     * @param exerciseId   The id of the Exercise the StudentParticipation belongs to
     * @param participant  The Participant the StudentParticipation belongs to
     * @param points       The maxPoints of the Exercise
     * @param bonusPoints  The bonusPoints of the Exercise
     * @param scoreAwarded The score awarded for the Result
     * @param rated        True, if the Result is rated
     * @return The created Result
     */
    public Result createParticipationSubmissionAndResult(long exerciseId, Participant participant, Double points, Double bonusPoints, long scoreAwarded, boolean rated) {
        Exercise exercise = exerciseRepo.findById(exerciseId).orElseThrow();
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

    /**
     * Creates and saves a Result and Submission for the given StudentParticipation.
     *
     * @param studentParticipation The StudentParticipation the Result and Submission belong to
     * @param scoreAwarded         The score awarded for the Result
     * @param rated                True, if the Result is rated
     * @return The created Result
     */
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
     * Creates and saves a StudentParticipation for the given Exercise given the Participant's login.
     *
     * @param exercise The Exercise the StudentParticipation belongs to
     * @param login    The login of the Participant the StudentParticipation belongs to
     * @return The created StudentParticipation with eagerly loaded submissions, results and assessors
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
        return studentParticipationRepo.findWithEagerLegalSubmissionsAndResultsAssessorsById(storedParticipation.get().getId()).orElseThrow();
    }

    /**
     * Creates and saves a StudentParticipation for the given Exercise given the Participant's login. The StudentParticipation's initializationDate is set to now + 2 days.
     *
     * @param exercise The Exercise the StudentParticipation belongs to
     * @param login    The login of the Participant the StudentParticipation belongs to
     * @return The created StudentParticipation with eagerly loaded submissions, results and assessors
     */
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
        return studentParticipationRepo.findWithEagerLegalSubmissionsAndResultsAssessorsById(storedParticipation.get().getId()).orElseThrow();
    }

    /**
     * Creates and saves a StudentParticipation for the given Exercise for a team.
     *
     * @param exercise The Exercise the StudentParticipation belongs to
     * @param teamId   The id of the Team the StudentParticipation belongs to
     * @return The created StudentParticipation with eagerly loaded submissions, results and assessors
     */
    public StudentParticipation addTeamParticipationForExercise(Exercise exercise, long teamId) {
        Optional<StudentParticipation> storedParticipation = studentParticipationRepo.findWithEagerLegalSubmissionsAndTeamStudentsByExerciseIdAndTeamId(exercise.getId(), teamId);
        if (storedParticipation.isEmpty()) {
            Team team = teamRepo.findById(teamId).orElseThrow();
            StudentParticipation participation = new StudentParticipation();
            participation.setInitializationDate(ZonedDateTime.now());
            participation.setParticipant(team);
            participation.setExercise(exercise);
            studentParticipationRepo.save(participation);
            storedParticipation = studentParticipationRepo.findWithEagerLegalSubmissionsAndTeamStudentsByExerciseIdAndTeamId(exercise.getId(), teamId);
            assertThat(storedParticipation).isPresent();
        }
        return studentParticipationRepo.findWithEagerLegalSubmissionsAndResultsAssessorsById(storedParticipation.get().getId()).orElseThrow();
    }

    /**
     * Creates and saves a ProgrammingExerciseStudentParticipation for the given ProgrammingExercise given the Participant's login. If an existing
     * ProgrammingExerciseStudentParticipation exists for the given ProgrammingExercise and Participant, it will be returned instead.
     *
     * @param exercise The ProgrammingExercise the ProgrammingExerciseStudentParticipation belongs to
     * @param login    The login of the Participant the ProgrammingExerciseStudentParticipation belongs to
     * @return The created or existing ProgrammingExerciseStudentParticipation with eagerly loaded submissions, results and assessors
     */
    public ProgrammingExerciseStudentParticipation addStudentParticipationForProgrammingExercise(ProgrammingExercise exercise, String login) {
        final var existingParticipation = programmingExerciseStudentParticipationRepo.findByExerciseIdAndStudentLogin(exercise.getId(), login);
        if (existingParticipation.isPresent()) {
            return existingParticipation.get();
        }
        ProgrammingExerciseStudentParticipation participation = ParticipationFactory.generateIndividualProgrammingExerciseStudentParticipation(exercise,
                userUtilService.getUserByLogin(login));
        final var repoName = (exercise.getProjectKey() + "-" + login).toLowerCase();
        participation.setRepositoryUri(String.format("http://some.test.url/scm/%s/%s.git", exercise.getProjectKey(), repoName));
        participation = programmingExerciseStudentParticipationRepo.save(participation);

        return (ProgrammingExerciseStudentParticipation) studentParticipationRepo.findWithEagerLegalSubmissionsAndResultsAssessorsById(participation.getId()).orElseThrow();
    }

    /**
     * Creates and saves a ProgrammingExerciseStudentParticipation for the given ProgrammingExercise for a team. If an existing
     * ProgrammingExerciseStudentParticipation exists for the given ProgrammingExercise and Team, it will be returned instead.
     *
     * @param exercise The ProgrammingExercise the ProgrammingExerciseStudentParticipation belongs to
     * @param team     The Team the ProgrammingExerciseStudentParticipation belongs to
     * @return The created or existing ProgrammingExerciseStudentParticipation with eagerly loaded submissions, results and assessors
     */
    public ProgrammingExerciseStudentParticipation addTeamParticipationForProgrammingExercise(ProgrammingExercise exercise, Team team) {

        final var existingParticipation = programmingExerciseStudentParticipationRepo.findByExerciseIdAndTeamId(exercise.getId(), team.getId());
        if (existingParticipation.isPresent()) {
            return existingParticipation.get();
        }
        ProgrammingExerciseStudentParticipation participation = ParticipationFactory.generateTeamProgrammingExerciseStudentParticipation(exercise, team);
        final var repoName = (exercise.getProjectKey() + "-" + team.getShortName()).toLowerCase();
        participation.setRepositoryUri(String.format("http://some.test.url/scm/%s/%s.git", exercise.getProjectKey(), repoName));
        participation = programmingExerciseStudentParticipationRepo.save(participation);

        return (ProgrammingExerciseStudentParticipation) studentParticipationRepo.findWithEagerLegalSubmissionsAndResultsAssessorsById(participation.getId()).orElseThrow();
    }

    /**
     * Creates and saves a ProgrammingExerciseStudentParticipation for the given ProgrammingExercise given the Participant's login. If an existing
     * ProgrammingExerciseStudentParticipation exists for the given ProgrammingExercise and Participant, it will be returned instead. The
     * ProgrammingExerciseStudentParticipation's repositoryUri is set to the given localRepoPath.
     *
     * @param exercise      The ProgrammingExercise the ProgrammingExerciseStudentParticipation belongs to
     * @param login         The login of the Participant the ProgrammingExerciseStudentParticipation belongs to
     * @param localRepoPath The repositoryUri of the ProgrammingExerciseStudentParticipation
     * @return The created or existing ProgrammingExerciseStudentParticipation with eagerly loaded submissions, results and assessors
     */
    public ProgrammingExerciseStudentParticipation addStudentParticipationForProgrammingExerciseForLocalRepo(ProgrammingExercise exercise, String login, URI localRepoPath) {
        final var existingParticipation = programmingExerciseStudentParticipationRepo.findByExerciseIdAndStudentLogin(exercise.getId(), login);
        if (existingParticipation.isPresent()) {
            return existingParticipation.get();
        }
        ProgrammingExerciseStudentParticipation participation = ParticipationFactory.generateIndividualProgrammingExerciseStudentParticipation(exercise,
                userUtilService.getUserByLogin(login));
        final var repoName = (exercise.getProjectKey() + "-" + login).toLowerCase();
        participation.setRepositoryUri(String.format(localRepoPath.toString() + "%s/%s.git", exercise.getProjectKey(), repoName));
        participation = programmingExerciseStudentParticipationRepo.save(participation);

        return (ProgrammingExerciseStudentParticipation) studentParticipationRepo.findWithEagerLegalSubmissionsAndResultsAssessorsById(participation.getId()).orElseThrow();
    }

    /**
     * Creates and saves a Result with the given arguments.
     *
     * @param type           The AssessmentType of the Result
     * @param completionDate The completionDate of the Result
     * @param participation  The Participation the Result belongs to
     * @param successful     True, if the Result is successful
     * @param rated          True, if the Result is rated
     * @param score          The score of the Result
     * @return The created Result
     */
    public Result addResultToParticipation(AssessmentType type, ZonedDateTime completionDate, Participation participation, boolean successful, boolean rated, double score) {
        Result result = new Result().participation(participation).successful(successful).rated(rated).score(score).assessmentType(type).completionDate(completionDate);
        return resultRepo.save(result);
    }

    /**
     * Creates and saves a Result with the given arguments. The Result is successful and rated. The Result's score is 100.
     *
     * @param assessmentType The AssessmentType of the Result
     * @param completionDate The completionDate of the Result
     * @param participation  The Participation the Result belongs to
     * @return The created Result
     */
    public Result addResultToParticipation(AssessmentType assessmentType, ZonedDateTime completionDate, Participation participation) {
        Result result = new Result().participation(participation).successful(true).rated(true).score(100D).assessmentType(assessmentType).completionDate(completionDate);
        return resultRepo.save(result);
    }

    /**
     * Creates and saves a Result with the given arguments.
     *
     * @param assessmentType The AssessmentType of the Result
     * @param completionDate The completionDate of the Result
     * @param participation  The Participation the Result belongs to
     * @param assessorLogin  The login of the assessor of the Result
     * @param feedbacks      The Feedbacks of the Result
     * @return The created Result
     */
    public Result addResultToParticipation(AssessmentType assessmentType, ZonedDateTime completionDate, Participation participation, String assessorLogin,
            List<Feedback> feedbacks) {
        Result result = new Result().participation(participation).assessmentType(assessmentType).completionDate(completionDate).feedbacks(feedbacks);
        result.setAssessor(userUtilService.getUserByLogin(assessorLogin));
        return resultRepo.save(result);
    }

    /**
     * Creates and saves a Result for the given Participation and Submission. The Result is successful and rated. The Result's score is 100.
     *
     * @param participation The Participation the Result belongs to
     * @return The created Result
     */
    public Result addResultToParticipation(Participation participation, Submission submission) {
        Result result = new Result().participation(participation).successful(true).score(100D).rated(true);
        result = resultRepo.save(result);
        result.setSubmission(submission);
        submission.addResult(result);
        submission.setParticipation(participation);
        submissionRepository.save(submission);
        return result;
    }

    /**
     * Creates and saves 2 Feedbacks for the given Result.
     *
     * @param result The Result the Feedbacks belong to
     * @return The updated Result
     */
    public Result addSampleFeedbackToResults(Result result) {
        Feedback feedback1 = feedbackRepo.save(new Feedback().detailText("detail1"));
        Feedback feedback2 = feedbackRepo.save(new Feedback().detailText("detail2"));
        List<Feedback> feedbacks = new ArrayList<>();
        feedbacks.add(feedback1);
        feedbacks.add(feedback2);
        result.addFeedbacks(feedbacks);
        return resultRepo.save(result);
    }

    /**
     * Creates and saves 4 Feedbacks for the given Result.
     * The Feedbacks are of type MANUAL, MANUAL_UNREFERENCED, AUTOMATIC_ADAPTED and AUTOMATIC.
     *
     * @param result The Result the Feedbacks belong to
     * @return The updated Result
     */
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

    /**
     * Creates and saves 3 Feedbacks for the given Result.
     * The Feedbacks have the visibility AFTER_DUE_DATE, NEVER and ALWAYS.
     *
     * @param result The Result the Feedbacks belong to
     * @return The updated Result
     */
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

    /**
     * Saves the given Feedback and adds it to the given Result.
     *
     * @param feedback The Feedback to save
     * @param result   The Result the Feedback belongs to
     * @return The updated Result
     */
    public Result addFeedbackToResult(Feedback feedback, Result result) {
        feedbackRepo.save(feedback);
        result.addFeedback(feedback);
        return resultRepo.save(result);
    }

    /**
     * Creates and saves 5 SCA Feedbacks (1p each) and 3 Feedbacks (2p, 1p and -1p) for the given Result.
     *
     * @param result The Result the Feedbacks belong to
     * @return The updated Result
     */
    public Result addFeedbackToResults(Result result) {
        List<Feedback> feedback = ParticipationFactory.generateStaticCodeAnalysisFeedbackList(5);
        feedback.addAll(ParticipationFactory.generateFeedback());
        feedback = feedbackRepo.saveAll(feedback);
        result.addFeedbacks(feedback);
        return resultRepo.save(result);
    }

    /**
     * Creates and saves a Result for the given Submission.
     *
     * @param submission     The Submission the Result belongs to
     * @param assessmentType The AssessmentType of the Result
     * @param user           The assessor of the Result
     * @param score          The score of the Result
     * @param rated          True, if the Result is rated
     * @param completionDate The completionDate of the Result
     * @return The updated Submission with eagerly loaded results and assessor
     */
    public Submission addResultToSubmission(final Submission submission, AssessmentType assessmentType, User user, Double score, boolean rated, ZonedDateTime completionDate) {
        Result result = new Result().participation(submission.getParticipation()).assessmentType(assessmentType).score(score).rated(rated).completionDate(completionDate);
        result.setAssessor(user);
        result = resultRepo.save(result);
        result.setSubmission(submission);
        submission.addResult(result);
        var savedSubmission = submissionRepository.save(submission);
        return submissionRepository.findWithEagerResultsAndAssessorById(savedSubmission.getId()).orElseThrow();
    }

    /**
     * Creates and saves a Result for the given Submission. The Result is rated, the score is 100, the assessor and completionDate are unset.
     *
     * @param submission     The Submission the Result belongs to
     * @param assessmentType The AssessmentType of the Result
     * @return The updated Submission with eagerly loaded results and assessor
     */
    public Submission addResultToSubmission(Submission submission, AssessmentType assessmentType) {
        return addResultToSubmission(submission, assessmentType, null, 100D, true, null);
    }

    /**
     * Creates and saves a Result for the given Submission. The Result is rated, the score is 100, the completionDate is set to now.
     *
     * @param submission     The Submission the Result belongs to
     * @param assessmentType The AssessmentType of the Result
     * @param user           The assessor of the Result
     * @return The updated Submission with eagerly loaded results and assessor
     */
    public Submission addResultToSubmission(Submission submission, AssessmentType assessmentType, User user) {
        return addResultToSubmission(submission, assessmentType, user, 100D, true, ZonedDateTime.now());
    }

    /**
     * Creates and saves a Result for the given Submission. The Result's completionDate is set to now.
     *
     * @param submission     The Submission the Result belongs to
     * @param assessmentType The AssessmentType of the Result
     * @param user           The assessor of the Result
     * @param score          The score of the Result
     * @param rated          True, if the Result is rated
     * @return The updated Submission with eagerly loaded results and assessor
     */
    public Submission addResultToSubmission(Submission submission, AssessmentType assessmentType, User user, Double score, boolean rated) {
        return addResultToSubmission(submission, assessmentType, user, score, rated, ZonedDateTime.now());
    }

    /**
     * Creates and saves a Rating for the given Result.
     *
     * @param result The Result the Rating belongs to
     * @param score  The score of the Rating
     */
    public void addRatingToResult(Result result, int score) {
        var rating = new Rating();
        rating.setResult(result);
        rating.setRating(score);
        ratingRepo.save(rating);
    }

    /**
     * Creates and saves a List of Submissions for each StudentParticipation associated with the given Exercise.
     *
     * @param exercise The Exercise the Submissions belong to
     * @return The List of created Submissions
     */
    public List<Submission> getAllSubmissionsOfExercise(Exercise exercise) {
        var participations = studentParticipationRepo.findByExerciseId(exercise.getId());
        var allSubmissions = new ArrayList<Submission>();
        participations.forEach(participation -> {
            Submission submission = submissionRepository.findAllByParticipationId(participation.getId()).get(0);
            allSubmissions.add(submissionRepository.findWithEagerResultAndFeedbackById(submission.getId()).orElseThrow());
        });
        return allSubmissions;
    }

    /**
     * Updates and saves the Submission's StudentParticipation by adding the given Result to it.
     *
     * @param submission The Submission to update
     * @param result     The Result to add to the Submission's StudentParticipation
     */
    public void saveResultInParticipation(Submission submission, Result result) {
        submission.addResult(result);
        StudentParticipation participation = (StudentParticipation) submission.getParticipation();
        participation.addResult(result);
        studentParticipationRepo.save(participation);
    }

    /**
     * Creates and saves a Result for the given Submission. The Result's completionDate is set to now - 1 day, the assessmentType is SEMI_AUTOMATIC and the Result is rated.
     *
     * @param submission The Submission the Result belongs to
     * @param assessor   The assessor of the Result
     * @return The created Result
     */
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

    /**
     * Creates and saves a Result for the given Submission. The Result's completionDate is set to now - 1 day, the assessmentType is SEMI_AUTOMATIC and the Result is rated.
     *
     * @param submission The Submission the Result belongs to
     * @param assessor   The assessor of the Result
     * @param score      The score of the Result
     * @return The created Result
     */
    public Result generateResultWithScore(Submission submission, User assessor, Double score) {
        Result result = generateResult(submission, assessor);
        result.setScore(score);
        return result;
    }

    /**
     * Creates and saves a StudentParticipation for the given Exercise and Submission. Updates and saves the Submission accordingly.
     *
     * @param exercise   The Exercise the StudentParticipation belongs to
     * @param submission The Submission to update
     * @param login      The login of the Participant the StudentParticipation belongs to
     * @return The updated submission.
     */
    public Submission addSubmission(Exercise exercise, Submission submission, String login) {
        StudentParticipation participation = createAndSaveParticipationForExercise(exercise, login);
        participation.addSubmission(submission);
        submission.setParticipation(participation);
        submissionRepository.save(submission);
        studentParticipationRepo.save(participation);
        return submission;
    }

    /**
     * Updates and saves the given Submission by adding it to the given Participation.
     *
     * @param participation The Participation the Submission belongs to
     * @param submission    The Submission to add and update
     * @return The updated Submission
     */
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

    /**
     * Creates and saves a Result. Updates the Submission by adding the result to it and updates the StudentParticipation by adding the result to it.
     *
     * @param participation The StudentParticipation the Result belongs to
     * @param submission    The Submission the Result belongs to
     * @param assessorLogin The login of the assessor of the Result
     * @return The updated Submission
     */
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

    /**
     * Saves the given Submission to the corresponding repository.
     *
     * @param submission The Submission to save
     * @return The saved Submission
     */
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

    /**
     * Saves the given ExampleSubmission to the corresponding repository.
     *
     * @param exampleSubmission The ExampleSubmission to save
     * @return The saved ExampleSubmission
     */
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

    /**
     * Creates a list of Feedbacks from the given file.
     *
     * @param path The path to the file
     * @return The created list of Feedbacks
     * @throws Exception If the file cannot be read
     */
    public List<Feedback> loadAssessmentFomResources(String path) throws Exception {
        String fileContent = TestResourceUtils.loadFileFromResources(path);
        return mapper.readValue(fileContent, mapper.getTypeFactory().constructCollectionType(List.class, Feedback.class));
    }

    /**
     * Generates an ExampleSubmission for a given model or Text and the corresponding Modeling- or TextExercise. Creates and saves a TextSubmission for the ExampleSubmission if
     * the Exercise is a TextExercise.
     *
     * @param modelOrText             The uml model or text for the ExampleSubmission
     * @param exercise                The Exercise for which the ExampleSubmission is created
     * @param flagAsExampleSubmission True, if the submission is an ExampleSubmission
     * @return The generated ExampleSubmission
     */
    public ExampleSubmission generateExampleSubmission(String modelOrText, Exercise exercise, boolean flagAsExampleSubmission) {
        return generateExampleSubmission(modelOrText, exercise, flagAsExampleSubmission, false);
    }

    /**
     * Generates an ExampleSubmission for a given model or Text and the corresponding Modeling- or TextExercise. Creates and saves a TextSubmission for the ExampleSubmission if
     * the Exercise is a TextExercise.
     *
     * @param modelOrText             The uml model for the ExampleSubmission
     * @param exercise                The Exercise for which the ExampleSubmission is created
     * @param flagAsExampleSubmission True, if the submission is an ExampleSubmission
     * @param usedForTutorial         True, if the ExampleSubmission is used for a tutorial
     * @return The generated ExampleSubmission
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

    /**
     * Checks if the sent Feedback is correctly stored.
     *
     * @param sentFeedback   The List of Feedbacks sent to the server
     * @param storedFeedback The List of Feedback stored in the database
     * @param feedbackType   The FeedbackType of the Feedback
     */
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

    /**
     * Creates and saves a StudentParticipation for the given Exercise given the Participant's login. Also creates and saves a Submission and a Result with Feedback for the
     * StudentParticipation.
     *
     * @param exercise The Exercise the StudentParticipation belongs to
     * @param login    The login of the Participant the StudentParticipation belongs to
     * @return The created StudentParticipation with eagerly loaded submissions, results and assessors
     */
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
        assertThat(exercise.getGradingCriteria()).allMatch(gradingCriterion -> gradingCriterion.getStructuredGradingInstructions() != null);

        // add feedback which is associated with structured grading instructions
        GradingInstruction anyInstruction = GradingCriterionUtil.findAnyInstructionWhere(exercise.getGradingCriteria(), gradingInstruction -> true).orElseThrow();
        Feedback feedback = new Feedback();
        feedback.setGradingInstruction(anyInstruction);
        addFeedbackToResult(feedback, result);

        return studentParticipation;
    }

    /**
     * Gets all Results for the given Exercise.
     *
     * @param exercise The Exercise the Results belong to
     * @return A List of Results with eagerly loaded submissions and feedbacks
     */
    public List<Result> getResultsForExercise(Exercise exercise) {
        return resultRepo.findWithEagerSubmissionAndFeedbackByParticipationExerciseId(exercise.getId());
    }

    public void mockCreationOfExerciseParticipation(boolean useGradedParticipationOfResult, Result gradedResult, ProgrammingExercise programmingExercise, UriService uriService,
            VersionControlService versionControlService, ContinuousIntegrationService continuousIntegrationService) throws URISyntaxException {
        String templateRepoName;
        if (useGradedParticipationOfResult) {
            templateRepoName = uriService.getRepositorySlugFromRepositoryUri(((ProgrammingExerciseStudentParticipation) gradedResult.getParticipation()).getVcsRepositoryUri());
        }
        else {
            templateRepoName = uriService.getRepositorySlugFromRepositoryUri(programmingExercise.getVcsTemplateRepositoryUri());
        }
        mockCreationOfExerciseParticipation(templateRepoName, programmingExercise, versionControlService, continuousIntegrationService);
    }

    /**
     * Mocks methods in VC and CI system needed for the creation of a StudentParticipation given the ProgrammingExercise. The StudentParticipation's repositoryUri is set to a fake
     * URL.
     *
     * @param templateRepoName             The expected sourceRepositoryName when calling the copyRepository method of the mocked VersionControlService
     * @param programmingExercise          The ProgrammingExercise the StudentParticipation belongs to
     * @param versionControlService        The mocked VersionControlService
     * @param continuousIntegrationService The mocked ContinuousIntegrationService
     */
    public void mockCreationOfExerciseParticipation(String templateRepoName, ProgrammingExercise programmingExercise, VersionControlService versionControlService,
            ContinuousIntegrationService continuousIntegrationService) throws URISyntaxException {
        var someURL = new VcsRepositoryUri("http://vcs.fake.fake");
        doReturn(someURL).when(versionControlService).copyRepository(any(String.class), eq(templateRepoName), any(String.class), any(String.class), any(String.class));
        mockCreationOfExerciseParticipationInternal(programmingExercise, versionControlService, continuousIntegrationService);
    }

    /**
     * Mocks methods in VC and CI system needed for the creation of a StudentParticipation given the ProgrammingExercise. The StudentParticipation's repositoryUri is set to a fake
     * URL.
     *
     * @param programmingExercise          The ProgrammingExercise the StudentParticipation belongs to
     * @param versionControlService        The mocked VersionControlService
     * @param continuousIntegrationService The mocked ContinuousIntegrationService
     */
    public void mockCreationOfExerciseParticipation(ProgrammingExercise programmingExercise, VersionControlService versionControlService,
            ContinuousIntegrationService continuousIntegrationService) throws URISyntaxException {
        var someURL = new VcsRepositoryUri("http://vcs.fake.fake");
        doReturn(someURL).when(versionControlService).copyRepository(any(String.class), any(), any(String.class), any(String.class), any(String.class));
        mockCreationOfExerciseParticipationInternal(programmingExercise, versionControlService, continuousIntegrationService);
    }

    /**
     * Mocks methods in VC and CI system needed for the creation of a StudentParticipation given the ProgrammingExercise.
     *
     * @param programmingExercise          The ProgrammingExercise the StudentParticipation belongs to
     * @param versionControlService        The mocked VersionControlService
     * @param continuousIntegrationService The mocked ContinuousIntegrationService
     */
    private void mockCreationOfExerciseParticipationInternal(ProgrammingExercise programmingExercise, VersionControlService versionControlService,
            ContinuousIntegrationService continuousIntegrationService) {
        doReturn(defaultBranch).when(versionControlService).getOrRetrieveBranchOfExercise(programmingExercise);
        doReturn(defaultBranch).when(versionControlService).getOrRetrieveBranchOfStudentParticipation(any());

        doNothing().when(versionControlService).configureRepository(any(), any(), anyBoolean());
        doReturn("buildPlanId").when(continuousIntegrationService).copyBuildPlan(any(), any(), any(), any(), any(), anyBoolean());
        doNothing().when(continuousIntegrationService).configureBuildPlan(any(), any());
        doNothing().when(versionControlService).addWebHookForParticipation(any());
    }
}
