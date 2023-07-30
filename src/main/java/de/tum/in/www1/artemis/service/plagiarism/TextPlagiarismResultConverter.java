package de.tum.in.www1.artemis.service.plagiarism;

import static java.util.stream.Collectors.toUnmodifiableSet;

import java.io.File;
import java.util.ArrayList;
import java.util.Objects;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import de.jplag.JPlagComparison;
import de.jplag.JPlagResult;
import de.jplag.Submission;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.plagiarism.*;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextPlagiarismResult;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextSubmissionElement;

/**
 * Contains logic that converts JPlagResult into a TextPlagiarismResult for text and programming exercises
 */
@Component
class TextPlagiarismResultConverter {

    private static final Logger log = LoggerFactory.getLogger(TextPlagiarismResultConverter.class);

    private final JPlagSubmissionDataExtractor jPlagSubmissionDataExtractor;

    TextPlagiarismResultConverter(JPlagSubmissionDataExtractor jPlagSubmissionDataExtractor) {
        this.jPlagSubmissionDataExtractor = jPlagSubmissionDataExtractor;
    }

    /**
     * converts the given JPlagResult into a TextPlagiarismResult, only uses the 500 most interesting comparisons based on the highest similarity
     *
     * @param result   the JPlagResult contains comparisons
     * @param exercise the exercise to which the result should belong, either Text or Programming
     */
    TextPlagiarismResult fromJplagResult(JPlagResult result, Exercise exercise) {
        verifyAllowedExerciseType(exercise);

        var textPlagiarismResult = new TextPlagiarismResult();

        // sort and limit the number of comparisons to 500 to save memory and cpu power
        var comparisons = result.getComparisons(500).stream().map(it -> fromJPlagComparison(it, exercise, result.getOptions().submissionDirectories().iterator().next()))
                .collect(toUnmodifiableSet());
        comparisons.forEach(it -> it.setPlagiarismResult(textPlagiarismResult));
        textPlagiarismResult.setComparisons(comparisons);

        textPlagiarismResult.setDuration(result.getDuration());
        textPlagiarismResult.setSimilarityDistribution(result.getSimilarityDistribution());
        textPlagiarismResult.setExercise(exercise);

        return textPlagiarismResult;
    }

    private static void verifyAllowedExerciseType(Exercise exercise) {
        if (!(exercise instanceof TextExercise || exercise instanceof ProgrammingExercise)) {
            throw new IllegalStateException("TextPlagiarismResults are supported only for TextExercise and ProgrammingExercise.");
        }
    }

    /**
     * Create a new PlagiarismComparison instance from an existing JPlagComparison object.
     *
     * @param jplagComparison     JPlag comparison to map to the new PlagiarismComparison instance
     * @param exercise            the exercise to which the comparison belongs, either Text or Programming
     * @param submissionDirectory the directory to which all student submissions have been downloaded / stored
     * @return a new instance with the content of the JPlagComparison
     */
    private PlagiarismComparison<TextSubmissionElement> fromJPlagComparison(JPlagComparison jplagComparison, Exercise exercise, File submissionDirectory) {
        PlagiarismComparison<TextSubmissionElement> comparison = new PlagiarismComparison<>();

        comparison.setSubmissionA(createSubmissionFromJPlagSubmission(jplagComparison.firstSubmission(), exercise, submissionDirectory));
        comparison.setSubmissionB(createSubmissionFromJPlagSubmission(jplagComparison.secondSubmission(), exercise, submissionDirectory));
        comparison.setMatches(jplagComparison.matches().stream().map(PlagiarismMatch::fromJPlagMatch).collect(Collectors.toSet()));
        // Note: JPlag returns a value between 0 and 1, we assume and store a value between 0 and 100 (percentage) in the database
        comparison.setSimilarity(jplagComparison.similarity() * 100);
        comparison.setStatus(PlagiarismStatus.NONE);

        return comparison;
    }

    /**
     * Create a new PlagiarismSubmission instance from an existing JPlag Submission
     *
     * @param jplagSubmission     the JPlag Submission to create the PlagiarismSubmission from
     * @param exercise            the exercise to which the comparison belongs, either Text or Programming
     * @param submissionDirectory the directory to which all student submissions have been downloaded / stored
     * @return a new PlagiarismSubmission instance
     */
    private PlagiarismSubmission<TextSubmissionElement> createSubmissionFromJPlagSubmission(Submission jplagSubmission, Exercise exercise, File submissionDirectory) {
        var submission = new PlagiarismSubmission<TextSubmissionElement>();

        submission.setElements(jplagSubmission.getTokenList().stream().filter(Objects::nonNull)
                .map(token -> TextSubmissionElement.fromJPlagToken(token, submission, exercise, submissionDirectory)).collect(Collectors.toCollection(ArrayList::new)));
        submission.setSize(jplagSubmission.getNumberOfTokens());
        submission.setScore(null);

        jPlagSubmissionDataExtractor.retrieveAndSetSubmissionIdAndStudentLogin(submission, jplagSubmission, exercise);
        return submission;
    }
}
