package de.tum.in.www1.artemis.service.plagiarism;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toUnmodifiableList;

import java.io.File;
import java.io.IOException;
import java.util.*;

import jplag.ExitException;
import jplag.JPlag;
import jplag.JPlagOptions;
import jplag.JPlagResult;
import jplag.options.LanguageOption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextPlagiarismResult;
import de.tum.in.www1.artemis.service.TextSubmissionExportService;
import de.tum.in.www1.artemis.service.util.TimeLogUtil;

@Service
public class TextPlagiarismDetectionService {

    private final Logger log = LoggerFactory.getLogger(TextPlagiarismDetectionService.class);

    private final TextSubmissionExportService textSubmissionExportService;

    public TextPlagiarismDetectionService(TextSubmissionExportService textSubmissionExportService) {
        this.textSubmissionExportService = textSubmissionExportService;
    }

    /**
     * Reduce a TextExercise Object to a list of latest text submissions. Filters the empty ones because they do not need to be compared
     *
     * @param exerciseWithParticipationsAndSubmissions TextExercise with fetched participations and submissions
     * @param minimumScore consider only submissions whose score is greater or equal to this value
     * @param minimumSize consider only submissions whose size is greater or equal to this value
     * @return List containing the latest text submission for every participation
     */
    public List<TextSubmission> textSubmissionsForComparison(TextExercise exerciseWithParticipationsAndSubmissions, int minimumScore, int minimumSize) {
        var textSubmissions = exerciseWithParticipationsAndSubmissions.getStudentParticipations().parallelStream().map(Participation::findLatestSubmission)
                .filter(Optional::isPresent).map(Optional::get).filter(submission -> submission instanceof TextSubmission).map(submission -> (TextSubmission) submission)
                .filter(submission -> minimumSize == 0 || submission.getText() != null && submission.getText().length() >= minimumSize)
                .filter(submission -> minimumScore == 0
                        || submission.getLatestResult() != null && submission.getLatestResult().getScore() != null && submission.getLatestResult().getScore() >= minimumScore)
                .collect(toList());

        log.info("Found " + textSubmissions.size() + " text submissions in exercise " + exerciseWithParticipationsAndSubmissions.getId());

        return textSubmissions.parallelStream().filter(textSubmission -> !textSubmission.isEmpty()).collect(toUnmodifiableList());
    }

    /**
     * Download all submissions of the exercise, run JPlag, and return the result
     *
     * @param textExercise to detect plagiarism for
     * @param similarityThreshold ignore comparisons whose similarity is below this threshold (%)
     * @param minimumScore consider only submissions whose score is greater or equal to this value
     * @param minimumSize consider only submissions whose size is greater or equal to this value
     * @return a zip file that can be returned to the client
     * @throws ExitException is thrown if JPlag exits unexpectedly
     */
    public TextPlagiarismResult checkPlagiarism(TextExercise textExercise, float similarityThreshold, int minimumScore, int minimumSize) throws ExitException {
        long start = System.nanoTime();

        // TODO: why do we have such a strange folder name?
        final var submissionsFolderName = "./tmp/submissions";
        final var submissionFolderFile = new File(submissionsFolderName);
        submissionFolderFile.mkdirs();

        final List<TextSubmission> textSubmissions = textSubmissionsForComparison(textExercise, minimumScore, minimumSize);
        final var submissionsSize = textSubmissions.size();
        log.info("Save text submissions for JPlag text comparison with " + submissionsSize + " submissions");

        if (textSubmissions.size() < 2) {
            log.info("Insufficient amount of submissions for plagiarism detection. Return empty result.");
            return new TextPlagiarismResult();
        }

        textSubmissions.forEach(submission -> {
            submission.setResults(new ArrayList<>());

            StudentParticipation participation = (StudentParticipation) submission.getParticipation();
            participation.setExercise(null);
            participation.setSubmissions(null);

            String studentLogin = participation.getStudent().map(User::getLogin).orElse("unknown");

            try {
                textSubmissionExportService.saveSubmissionToFile(submission, studentLogin, submissionsFolderName);
            }
            catch (IOException e) {
                log.error(e.getMessage());
            }
        });

        log.info("Saving text submissions done");

        JPlagOptions options = new JPlagOptions(submissionsFolderName, LanguageOption.TEXT);

        // Important: for large courses with more than 1000 students, we might get more than one million results and 10 million files in the file system due to many 0% results,
        // therefore we limit the results to at least 50% or 0.5 similarity, the passed threshold is between 0 and 100%
        options.setSimilarityThreshold(similarityThreshold);

        log.info("Start JPlag Text comparison");
        JPlag jplag = new JPlag(options);
        JPlagResult jPlagResult = jplag.run();
        log.info("JPlag Text comparison finished with " + jPlagResult.getComparisons().size() + " comparisons");

        log.info("Delete submission folder");
        if (submissionFolderFile.exists()) {
            FileSystemUtils.deleteRecursively(submissionFolderFile);
        }

        TextPlagiarismResult textPlagiarismResult = new TextPlagiarismResult(jPlagResult);
        textPlagiarismResult.setExerciseId(textExercise.getId());

        log.info("JPlag text comparison for " + submissionsSize + " submissions done in " + TimeLogUtil.formatDurationFrom(start));

        return textPlagiarismResult;
    }
}
