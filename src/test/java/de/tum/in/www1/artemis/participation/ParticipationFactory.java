package de.tum.in.www1.artemis.participation;

import static de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseFactory.DEFAULT_BRANCH;
import static java.time.ZonedDateTime.now;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.NotNull;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.*;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.QuizSubmission;
import de.tum.in.www1.artemis.exercise.GradingCriterionUtil;
import de.tum.in.www1.artemis.util.GitUtilService;
import de.tum.in.www1.artemis.util.LocalRepository;

/**
 * Factory for creating Participations and related objects.
 */
public class ParticipationFactory {

    /**
     * Generates a MockFileRepositoryUri for the given repository.
     *
     * @param repository The repository to generate the MockFileRepositoryUri for
     * @return The generated MockFileRepositoryUri
     */
    public static GitUtilService.MockFileRepositoryUri getMockFileRepositoryUri(LocalRepository repository) {
        return new GitUtilService.MockFileRepositoryUri(repository.originRepoFile);
    }

    /**
     * Generates a TextSubmission with the given arguments.
     *
     * @param text      The text of the Submission
     * @param language  The Language of the Submission
     * @param submitted True, if the Submission was submitted one day ago; False, if the Submission was not submitted
     * @return The generated TextSubmission
     */
    public static TextSubmission generateTextSubmission(String text, Language language, boolean submitted) {
        TextSubmission textSubmission = new TextSubmission();
        textSubmission.text(text);
        textSubmission.setLanguage(language);
        textSubmission.setSubmitted(submitted);
        if (submitted) {
            textSubmission.setSubmissionDate(now().minusDays(1));
        }
        return textSubmission;
    }

    /**
     * Generates a TextSubmission with SubmissionDate in the future (now + 1 day).
     *
     * @param text     The text of the Submission
     * @param language The Language of the Submission
     * @return The generated TextSubmission
     */
    public static TextSubmission generateLateTextSubmission(String text, Language language) {
        TextSubmission textSubmission = new TextSubmission();
        textSubmission.text(text);
        textSubmission.setLanguage(language);
        textSubmission.setSubmitted(true);
        textSubmission.setSubmissionDate(now().plusDays(1));
        return textSubmission;
    }

    /**
     * Generates a ProgrammingSubmission with the given arguments.
     *
     * @param submitted  True, if the Submission was submitted one day ago; False, if the Submission was not submitted
     * @param commitHash The commitHash of the Submission
     * @param type       The type of the Submission
     * @return The generated ProgrammingSubmission
     */
    public static ProgrammingSubmission generateProgrammingSubmission(boolean submitted, String commitHash, SubmissionType type) {
        ProgrammingSubmission programmingSubmission = new ProgrammingSubmission();
        programmingSubmission.setSubmitted(submitted);
        if (submitted) {
            programmingSubmission.setSubmissionDate(now().minusDays(1));
        }
        programmingSubmission.setCommitHash(commitHash);
        programmingSubmission.setType(type);
        return programmingSubmission;
    }

    /**
     * Generates a ProgrammingSubmission with optional SubmissionDate in the past.
     *
     * @param submitted True, if the Submission was submitted one day ago; False, if the Submission was not submitted
     * @return The generated ProgrammingSubmission
     */
    public static ProgrammingSubmission generateProgrammingSubmission(boolean submitted) {
        ProgrammingSubmission programmingSubmission = new ProgrammingSubmission();
        programmingSubmission.setSubmitted(submitted);
        if (submitted) {
            programmingSubmission.setSubmissionDate(now().minusDays(1));
        }
        return programmingSubmission;
    }

    /**
     * Generates a FileUploadSubmission with optional SubmissionDate in the past.
     *
     * @param submitted True, if the Submission was submitted one day ago; False, if the Submission was not submitted
     * @return The generated FileUploadSubmission
     */
    public static FileUploadSubmission generateFileUploadSubmission(boolean submitted) {
        FileUploadSubmission fileUploadSubmission = new FileUploadSubmission();
        fileUploadSubmission.setSubmitted(submitted);
        if (submitted) {
            fileUploadSubmission.setSubmissionDate(now().minusDays(1));
        }
        return fileUploadSubmission;
    }

    /**
     * Generates a FileUploadSubmission with the given arguments.
     *
     * @param submitted True, if the Submission was submitted one day ago; False, if the Submission was not submitted
     * @param filePath  The filePath of the Submission
     * @return The generated FileUploadSubmission
     */
    public static FileUploadSubmission generateFileUploadSubmissionWithFile(boolean submitted, String filePath) {
        FileUploadSubmission fileUploadSubmission = generateFileUploadSubmission(submitted);
        fileUploadSubmission.setFilePath(filePath);
        if (submitted) {
            fileUploadSubmission.setSubmissionDate(now().minusDays(1));
        }
        return fileUploadSubmission;
    }

    /**
     * Generates a FileUploadSubmission with SubmissionDate in the future (now + 1 day).
     *
     * @return The generated FileUploadSubmission
     */
    public static FileUploadSubmission generateLateFileUploadSubmission() {
        FileUploadSubmission fileUploadSubmission = new FileUploadSubmission();
        fileUploadSubmission.setSubmitted(true);
        fileUploadSubmission.setSubmissionDate(now().plusDays(1));
        return fileUploadSubmission;
    }

    /**
     * Generates a ModelingSubmission with the given arguments.
     *
     * @param model     The model of the Submission
     * @param submitted True, if the Submission was submitted one day ago; False, if the Submission was not submitted
     * @return The generated ModelingSubmission
     */
    public static ModelingSubmission generateModelingSubmission(String model, boolean submitted) {
        ModelingSubmission submission = new ModelingSubmission();
        submission.setModel(model);
        submission.setSubmitted(submitted);
        if (submitted) {
            submission.setSubmissionDate(now().minusDays(1));
        }
        return submission;
    }

    /**
     * Generates a QuizSubmission with optional SubmissionDate in the past.
     *
     * @param submitted True, if the Submission was submitted one day ago; False, if the Submission was not submitted
     * @return The generated QuizSubmission
     */
    public static QuizSubmission generateQuizSubmission(boolean submitted) {
        QuizSubmission submission = new QuizSubmission();
        submission.setSubmitted(submitted);
        if (submitted) {
            submission.setSubmissionDate(now().minusDays(1));
        }
        return submission;
    }

    /**
     * Generates an ExampleSubmission for the given Submission and Exercise.
     *
     * @param submission      The Submission the ExampleSubmission belongs to
     * @param exercise        The Exercise the ExampleSubmission belongs to
     * @param usedForTutorial True, if the ExampleSubmission is used for a tutorial
     * @return The generated ExampleSubmission
     */
    public static ExampleSubmission generateExampleSubmission(Submission submission, Exercise exercise, boolean usedForTutorial) {
        ExampleSubmission exampleSubmission = new ExampleSubmission();
        exampleSubmission.setSubmission(submission);
        exampleSubmission.setExercise(exercise);
        exampleSubmission.setUsedForTutorial(usedForTutorial);
        return exampleSubmission;
    }

    /**
     * Generates a List of 3 Feedbacks with FeedbackType AUTOMATIC. The first two Feedbacks are positive, the third one is negative.
     * Credits of Feedbacks are set to 2, 1 and -1.
     *
     * @return The generated List of Feedbacks
     */
    public static List<Feedback> generateFeedback() {
        List<Feedback> feedbacks = new ArrayList<>();
        Feedback positiveFeedback = createPositiveFeedback(FeedbackType.AUTOMATIC);
        positiveFeedback.setReference("theory");
        feedbacks.add(positiveFeedback);
        Feedback positiveFeedback2 = new Feedback();
        positiveFeedback2.setCredits(1D);
        positiveFeedback2.setReference("theory2");
        positiveFeedback2.setType(FeedbackType.AUTOMATIC);
        feedbacks.add(positiveFeedback2);
        Feedback negativeFeedback = createNegativeFeedback(FeedbackType.AUTOMATIC);
        negativeFeedback.setReference("practice");
        negativeFeedback.setType(FeedbackType.AUTOMATIC);
        feedbacks.add(negativeFeedback);
        return feedbacks;
    }

    /**
     * Generates a List of 3 Feedbacks. The first two Feedbacks have FeedbackType MANUAL, the third one has FeedbackType MANUAL_UNREFERENCED.
     * Credits of Feedbacks are set to 2, -1 and -1.
     *
     * @return The generated List of Feedbacks
     */
    public static List<Feedback> generateManualFeedback() {
        List<Feedback> feedbacks = new ArrayList<>();
        Feedback positiveFeedback = createPositiveFeedback(FeedbackType.MANUAL);
        feedbacks.add(positiveFeedback);
        Feedback negativeFeedback = createNegativeFeedback(FeedbackType.MANUAL);
        feedbacks.add(negativeFeedback);
        Feedback unrefFeedback = new Feedback();
        unrefFeedback.setCredits(-1D);
        unrefFeedback.setText("no reference");
        unrefFeedback.setType(FeedbackType.MANUAL_UNREFERENCED);
        feedbacks.add(unrefFeedback);
        return feedbacks;
    }

    /**
     * Generates a positive Feedback with Credits set to 2.
     *
     * @param type The FeedbackType of the Feedback
     * @return The generated Feedback
     */
    @NotNull
    public static Feedback createPositiveFeedback(FeedbackType type) {
        Feedback positiveFeedback = new Feedback();
        positiveFeedback.setCredits(2D);
        positiveFeedback.setText("good");
        positiveFeedback.setType(type);
        return positiveFeedback;
    }

    /**
     * Generates a negative Feedback with Credits set to -1.
     *
     * @param type The FeedbackType of the Feedback
     * @return The generated Feedback
     */
    @NotNull
    public static Feedback createNegativeFeedback(FeedbackType type) {
        Feedback negativeFeedback = new Feedback();
        negativeFeedback.setCredits(-1D);
        negativeFeedback.setText("bad");
        negativeFeedback.setType(type);
        return negativeFeedback;
    }

    /**
     * Generates a Feedback with the given arguments.
     *
     * @param credits            The credits of the Feedback
     * @param textBlockReference The textBlockReference of the Feedback
     * @return The generated Feedback
     */
    @NotNull
    public static Feedback createManualTextFeedback(Double credits, String textBlockReference) {
        Feedback feedback = new Feedback();
        feedback.setCredits(credits);
        feedback.setText("bad");
        feedback.setType(FeedbackType.MANUAL);
        feedback.setReference(textBlockReference);
        return feedback;
    }

    /**
     * Generates a List of Feedbacks with FeedbackType AUTOMATIC. The Feedbacks are for static code analysis and their text is set to
     * Feedback.STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER.
     *
     * @param numOfFeedback The number of Feedbacks to generate
     * @return The generated List of Feedbacks
     */
    public static List<Feedback> generateStaticCodeAnalysisFeedbackList(int numOfFeedback) {
        List<Feedback> feedbackList = new ArrayList<>();
        for (int i = 0; i < numOfFeedback; i++) {
            feedbackList.add(generateStaticCodeAnalysisFeedback());
        }
        return feedbackList;
    }

    /**
     * Generates a Feedback with FeedbackType AUTOMATIC. The Feedback is for static code analysis and its text is set to Feedback.STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER.
     *
     * @return The generated Feedback
     */
    private static Feedback generateStaticCodeAnalysisFeedback() {
        Feedback feedback = new Feedback();
        feedback.setPositive(false);
        feedback.setType(FeedbackType.AUTOMATIC);
        feedback.setText(Feedback.STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER);
        feedback.setReference("Tool");
        feedback.setDetailText("{\"filePath\":\"" + Constants.STUDENT_WORKING_DIRECTORY
                + "/www/withSCA/MergeSort.java\",\"startLine\":9,\"endLine\":9,\"startColumn\":11,\"endColumn\":11,\"rule\":\"rule\",\"category\":\"category\",\"message\":\"message\"}");
        return feedback;
    }

    /**
     * Generates a List of 4 Feedbacks given the Exercise's GradingCriteria.
     * Credits of Feedbacks are set to 1, 1, 1 and 1.
     *
     * @param receivedExercise The Exercise with GradingCriteria
     * @return The generated List of Feedbacks
     */
    public static List<Feedback> applySGIonFeedback(Exercise receivedExercise) {
        List<Feedback> feedbacks = generateFeedback();

        final GradingInstruction gradingInstructionWithNoLimit = GradingCriterionUtil.findInstructionByMaxUsageCount(receivedExercise.getGradingCriteria(), 0);
        final GradingInstruction gradingInstructionWithLimit = GradingCriterionUtil.findInstructionByMaxUsageCount(receivedExercise.getGradingCriteria(), 1);

        feedbacks.get(0).setGradingInstruction(gradingInstructionWithLimit);
        feedbacks.get(0).setCredits(gradingInstructionWithLimit.getCredits()); // score +1P
        feedbacks.get(1).setGradingInstruction(gradingInstructionWithLimit);
        feedbacks.get(1).setCredits(gradingInstructionWithLimit.getCredits()); // score +1P
        feedbacks.get(2).setGradingInstruction(gradingInstructionWithNoLimit);
        feedbacks.get(2).setCredits(gradingInstructionWithNoLimit.getCredits()); // score +1P
        var moreFeedback = new Feedback();
        moreFeedback.setGradingInstruction(gradingInstructionWithNoLimit);
        moreFeedback.setCredits(gradingInstructionWithNoLimit.getCredits()); // score +1P
        feedbacks.add(moreFeedback);

        return feedbacks; // total score should be 3P
    }

    /**
     * Generates a StudentParticipation with the given arguments. The StudentParticipation's initializationDate is set to now - 5 days.
     *
     * @param initializationState The InitializationState of the StudentParticipation
     * @param exercise            The Exercise the StudentParticipation belongs to
     * @param user                The User the StudentParticipation belongs to
     * @return The generated StudentParticipation
     */
    public static StudentParticipation generateStudentParticipation(InitializationState initializationState, Exercise exercise, User user) {
        StudentParticipation studentParticipation = new StudentParticipation();
        studentParticipation.setInitializationState(initializationState);
        studentParticipation.setInitializationDate(now().minusDays(5));
        studentParticipation.setExercise(exercise);
        studentParticipation.setParticipant(user);
        return studentParticipation;
    }

    /**
     * Generates a StudentParticipation for the given Exercise and without participant. The StudentParticipation's initializationDate is set to now - 5 days.
     *
     * @param initializationState The InitializationState of the StudentParticipation
     * @param exercise            The Exercise the StudentParticipation belongs to
     * @return The generated StudentParticipation
     */
    public static StudentParticipation generateStudentParticipationWithoutUser(InitializationState initializationState, Exercise exercise) {
        StudentParticipation studentParticipation = new StudentParticipation();
        studentParticipation.setInitializationState(initializationState);
        studentParticipation.setInitializationDate(now().minusDays(5));
        studentParticipation.setExercise(exercise);
        return studentParticipation;
    }

    /**
     * Generates a ProgrammingExerciseStudentParticipation with the given arguments. The StudentParticipation's initializationDate is set to now - 5 days.
     *
     * @param initializationState The InitializationState of the StudentParticipation
     * @param exercise            The Exercise the StudentParticipation belongs to
     * @param user                The User the StudentParticipation belongs to
     * @return The generated StudentParticipation
     */
    public static ProgrammingExerciseStudentParticipation generateProgrammingExerciseStudentParticipation(InitializationState initializationState, ProgrammingExercise exercise,
            User user) {
        ProgrammingExerciseStudentParticipation studentParticipation = new ProgrammingExerciseStudentParticipation();
        studentParticipation.setInitializationState(initializationState);
        studentParticipation.setInitializationDate(now().minusDays(5));
        studentParticipation.setExercise(exercise);
        studentParticipation.setParticipant(user);
        return studentParticipation;
    }

    /**
     * Generates a ProgrammingExerciseStudentParticipation with the given arguments. The StudentParticipation's initializationDate is set to InitializationState.INITIALIZED,
     * the buildPlanId is set and the branch is set to DEFAULT_BRANCH.
     *
     * @param exercise The Exercise the StudentParticipation belongs to
     * @param user     The User the StudentParticipation belongs to
     * @return The generated StudentParticipation
     */
    public static ProgrammingExerciseStudentParticipation generateIndividualProgrammingExerciseStudentParticipation(ProgrammingExercise exercise, User user) {
        var participation = new ProgrammingExerciseStudentParticipation();
        final var buildPlanId = exercise.getProjectKey().toUpperCase() + "-" + user.getLogin().toUpperCase();
        participation.setInitializationDate(ZonedDateTime.now());
        participation.setParticipant(user);
        participation.setBuildPlanId(buildPlanId);
        participation.setProgrammingExercise(exercise);
        participation.setInitializationState(InitializationState.INITIALIZED);
        participation.setBranch(DEFAULT_BRANCH);
        return participation;
    }

    /**
     * Generates a ProgrammingExerciseStudentParticipation with the given arguments. The StudentParticipation's initializationDate is set to InitializationState.INITIALIZED and
     * the buildPlanId is set.
     *
     * @param exercise The Exercise the StudentParticipation belongs to
     * @param team     The Team the StudentParticipation belongs to
     * @return The generated StudentParticipation
     */
    public static ProgrammingExerciseStudentParticipation generateTeamProgrammingExerciseStudentParticipation(ProgrammingExercise exercise, Team team) {
        var participation = new ProgrammingExerciseStudentParticipation();
        final var buildPlanId = exercise.getProjectKey().toUpperCase() + "-" + team.getShortName().toUpperCase();
        participation.setInitializationDate(ZonedDateTime.now());
        participation.setParticipant(team);
        participation.setBuildPlanId(buildPlanId);
        participation.setProgrammingExercise(exercise);
        participation.setInitializationState(InitializationState.INITIALIZED);
        return participation;
    }

    /**
     * Generates a Result with the given arguments.
     *
     * @param rated True, if the Result is rated
     * @param score The score of the Result
     * @return The generated Result
     */
    public static Result generateResult(boolean rated, double score) {
        Result result = new Result();
        result.setRated(rated);
        result.setScore(score);
        return result;
    }

    /**
     * Generates a SubmissionVersion for the given Submission, User, and content.
     *
     * @param content    The content of the SubmissionVersion
     * @param submission The Submission, the SubmissionVersion belongs to
     * @param user       The User the SubmissionVersion belongs to
     * @return The generated SubmissionVersion
     */
    public static SubmissionVersion generateSubmissionVersion(String content, Submission submission, User user) {
        SubmissionVersion submissionVersion = new SubmissionVersion();
        submissionVersion.setContent(content);
        submissionVersion.setSubmission(submission);
        submissionVersion.setAuthor(user);
        return submissionVersion;
    }
}
