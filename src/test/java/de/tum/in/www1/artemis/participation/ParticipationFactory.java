package de.tum.in.www1.artemis.participation;

import static java.time.ZonedDateTime.now;

import java.util.ArrayList;
import java.util.List;

import javax.validation.constraints.NotNull;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.*;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.QuizSubmission;
import de.tum.in.www1.artemis.util.GitUtilService;
import de.tum.in.www1.artemis.util.LocalRepository;

/**
 * Factory for creating Participations and related objects.
 */
public class ParticipationFactory {

    public static GitUtilService.MockFileRepositoryUrl getMockFileRepositoryUrl(LocalRepository repository) {
        return new GitUtilService.MockFileRepositoryUrl(repository.originRepoFile);
    }

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
     * Generates example TextSubmissions
     *
     * @param count How many submissions should be generated (max. 10)
     * @return A list containing the generated TextSubmissions
     */
    public static List<TextSubmission> generateTextSubmissions(int count) {
        if (count > 10) {
            throw new IllegalArgumentException();
        }

        // Example texts for submissions
        String[] submissionTexts = {
                "Differences: \nAntipatterns: \n-Have one problem and two solutions(one problematic and one refactored)\n-Antipatterns are a sign of bad architecture and bad coding \nPattern:\n-Have one problem and one solution\n-Patterns are a sign of elaborated architecutre and coding",
                "The main difference between patterns and antipatterns is, that patterns show you a good way to do something and antipatterns show a bad way to do something. Nevertheless patterns may become antipatterns in the course of changing understanding of how good software engineering looks like. One example for that is functional decomposition, which used to be a pattern and \"good practice\". Over the time it turned out that it is not a goog way to solve problems, so it became a antipattern.\n\nA pattern itsself is a proposed solution to a problem that occurs often and in different situations.\nIn contrast to that a antipattern shows commonly made mistakes when dealing with a certain problem. Nevertheless a refactored solution is aswell proposed.",
                "1.Patterns can evolve into Antipatterns when change occurs\\n2. Pattern has one solution, whereas anti pattern can have subtypes of solution\\n3. Antipattern has negative consequences and symptom, where as patterns looks only into benefits and consequences",
                "Patterns: A way to Model code in differents ways \nAntipattern: A way of how Not to Model code",
                "Antipatterns are used when there are common mistakes in software management and development to find these, while patterns by themselves are used to build software systems in the context of frequent change by reducing complexity and isolating the change.\nAnother difference is that the antipatterns have problematic solution and then refactored solution, while patterns only have a solution.",
                "- In patterns we have a problem and a solution, in antipatterns we have a problematic solution and a refactored solution instead\n- patterns represent best practices from the industry etc. so proven concepts, whereas antipatterns shed a light on common mistakes during software development etc.",
                "1) Patterns have one solution, antipatterns have to solutions (one problematic and one refactored).\n2) for the coice of patterns code has to be written; for antipatterns, the bad smell code already exists",
                "Design Patterns:\n\nSolutions which are productive and efficient and are developed by Software Engineers over the years of practice and solving problems.\n\nAnti Patterns:\n\nKnown solutions which are actually bad or defective to certain kind of problems.",
                "Patterns has one problem and one solution.\nAntipatterns have one problematic solution and a solution for that. The antipattern happens when  a solution that is been used for a long time can not apply anymore. ",
                "Patterns identify problems and present solutions.\nAntipatterns identify problems but two kinds of solutions. One problematic solution and a better \"refactored\" version of the solution. Problematic solutions are suggested not to be used because they results in smells or hinder future work." };

        // Create Submissions with id's 0 - count
        List<TextSubmission> textSubmissions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            TextSubmission textSubmission = new TextSubmission((long) i).text(submissionTexts[i]);
            textSubmission.setLanguage(Language.ENGLISH);
            textSubmissions.add(textSubmission);
        }

        return textSubmissions;
    }

    public static TextSubmission generateLateTextSubmission(String text, Language language) {
        TextSubmission textSubmission = new TextSubmission();
        textSubmission.text(text);
        textSubmission.setLanguage(language);
        textSubmission.setSubmitted(true);
        textSubmission.setSubmissionDate(now().plusDays(1));
        return textSubmission;
    }

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

    public static ProgrammingSubmission generateProgrammingSubmission(boolean submitted) {
        ProgrammingSubmission programmingSubmission = new ProgrammingSubmission();
        programmingSubmission.setSubmitted(submitted);
        if (submitted) {
            programmingSubmission.setSubmissionDate(now().minusDays(1));
        }
        return programmingSubmission;
    }

    public static FileUploadSubmission generateFileUploadSubmission(boolean submitted) {
        FileUploadSubmission fileUploadSubmission = new FileUploadSubmission();
        fileUploadSubmission.setSubmitted(submitted);
        if (submitted) {
            fileUploadSubmission.setSubmissionDate(now().minusDays(1));
        }
        return fileUploadSubmission;
    }

    public static FileUploadSubmission generateFileUploadSubmissionWithFile(boolean submitted, String filePath) {
        FileUploadSubmission fileUploadSubmission = generateFileUploadSubmission(submitted);
        fileUploadSubmission.setFilePath(filePath);
        if (submitted) {
            fileUploadSubmission.setSubmissionDate(now().minusDays(1));
        }
        return fileUploadSubmission;
    }

    public static FileUploadSubmission generateLateFileUploadSubmission() {
        FileUploadSubmission fileUploadSubmission = new FileUploadSubmission();
        fileUploadSubmission.setSubmitted(true);
        fileUploadSubmission.setSubmissionDate(now().plusDays(1));
        return fileUploadSubmission;
    }

    public static ModelingSubmission generateModelingSubmission(String model, boolean submitted) {
        ModelingSubmission submission = new ModelingSubmission();
        submission.setModel(model);
        submission.setSubmitted(submitted);
        if (submitted) {
            submission.setSubmissionDate(now().minusDays(1));
        }
        return submission;
    }

    public static QuizSubmission generateQuizSubmission(boolean submitted) {
        QuizSubmission submission = new QuizSubmission();
        submission.setSubmitted(submitted);
        if (submitted) {
            submission.setSubmissionDate(now().minusDays(1));
        }
        return submission;
    }

    public static ExampleSubmission generateExampleSubmission(Submission submission, Exercise exercise, boolean usedForTutorial) {
        ExampleSubmission exampleSubmission = new ExampleSubmission();
        exampleSubmission.setSubmission(submission);
        exampleSubmission.setExercise(exercise);
        exampleSubmission.setUsedForTutorial(usedForTutorial);
        return exampleSubmission;
    }

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

    @NotNull
    public static Feedback createPositiveFeedback(FeedbackType type) {
        Feedback positiveFeedback = new Feedback();
        positiveFeedback.setCredits(2D);
        positiveFeedback.setText("good");
        positiveFeedback.setType(type);
        return positiveFeedback;
    }

    @NotNull
    public static Feedback createNegativeFeedback(FeedbackType type) {
        Feedback negativeFeedback = new Feedback();
        negativeFeedback.setCredits(-1D);
        negativeFeedback.setText("bad");
        negativeFeedback.setType(type);
        return negativeFeedback;
    }

    @NotNull
    public static Feedback createManualTextFeedback(Double credits, String textBlockReference) {
        Feedback feedback = new Feedback();
        feedback.setCredits(credits);
        feedback.setText("bad");
        feedback.setType(FeedbackType.MANUAL);
        feedback.setReference(textBlockReference);
        return feedback;
    }

    public static List<Feedback> generateStaticCodeAnalysisFeedbackList(int numOfFeedback) {
        List<Feedback> feedbackList = new ArrayList<>();
        for (int i = 0; i < numOfFeedback; i++) {
            feedbackList.add(generateStaticCodeAnalysisFeedback());
        }
        return feedbackList;
    }

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

    public static List<Feedback> applySGIonFeedback(Exercise receivedExercise) {
        List<Feedback> feedbacks = generateFeedback();

        var gradingInstructionWithNoLimit = receivedExercise.getGradingCriteria().get(0).getStructuredGradingInstructions().get(0);
        var gradingInstructionWithLimit = receivedExercise.getGradingCriteria().get(1).getStructuredGradingInstructions().get(0);

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

    public static FeedbackConflict generateFeedbackConflictBetweenFeedbacks(Feedback firstFeedback, Feedback secondFeedback) {
        FeedbackConflict feedbackConflict = new FeedbackConflict();
        feedbackConflict.setConflict(true);
        feedbackConflict.setCreatedAt(now());
        feedbackConflict.setFirstFeedback(firstFeedback);
        feedbackConflict.setSecondFeedback(secondFeedback);
        feedbackConflict.setType(FeedbackConflictType.INCONSISTENT_SCORE);
        feedbackConflict.setDiscard(false);
        return feedbackConflict;
    }

    public static StudentParticipation generateStudentParticipation(InitializationState initializationState, Exercise exercise, User user) {
        StudentParticipation studentParticipation = new StudentParticipation();
        studentParticipation.setInitializationState(initializationState);
        studentParticipation.setInitializationDate(now().minusDays(5));
        studentParticipation.setExercise(exercise);
        studentParticipation.setParticipant(user);
        return studentParticipation;
    }

    /**
     * Generates a minimal student participation without a specific user attached.
     *
     * @param initializationState the state of the participation
     * @param exercise            the referenced exercise of the participation
     * @return the StudentParticipation created
     */
    public static StudentParticipation generateStudentParticipationWithoutUser(InitializationState initializationState, Exercise exercise) {
        StudentParticipation studentParticipation = new StudentParticipation();
        studentParticipation.setInitializationState(initializationState);
        studentParticipation.setInitializationDate(now().minusDays(5));
        studentParticipation.setExercise(exercise);
        return studentParticipation;
    }

    public static ProgrammingExerciseStudentParticipation generateProgrammingExerciseStudentParticipation(InitializationState initializationState, ProgrammingExercise exercise,
            User user) {
        ProgrammingExerciseStudentParticipation studentParticipation = new ProgrammingExerciseStudentParticipation();
        studentParticipation.setInitializationState(initializationState);
        studentParticipation.setInitializationDate(now().minusDays(5));
        studentParticipation.setExercise(exercise);
        studentParticipation.setParticipant(user);
        return studentParticipation;
    }

    public static Result generateResult(boolean rated, double score) {
        Result result = new Result();
        result.setRated(rated);
        result.setScore(score);
        return result;
    }
}
