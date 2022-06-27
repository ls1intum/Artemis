package de.tum.in.www1.artemis.service.plagiarism;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

import de.jplag.JPlag;
import de.jplag.JPlagResult;
import de.jplag.exceptions.ExitException;
import de.jplag.options.JPlagOptions;
import de.jplag.options.LanguageOption;
import de.tum.in.www1.artemis.domain.PlagiarismCheckState;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextPlagiarismResult;
import de.tum.in.www1.artemis.service.TextSubmissionExportService;
import de.tum.in.www1.artemis.service.plagiarism.cache.PlagiarismCacheService;
import de.tum.in.www1.artemis.service.util.TimeLogUtil;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

@Service
public class TextPlagiarismDetectionService {

    private final Logger log = LoggerFactory.getLogger(TextPlagiarismDetectionService.class);

    private final TextSubmissionExportService textSubmissionExportService;

    private final PlagiarismWebsocketService plagiarismWebsocketService;

    private final PlagiarismCacheService plagiarismCacheService;

    public TextPlagiarismDetectionService(TextSubmissionExportService textSubmissionExportService, PlagiarismWebsocketService plagiarismWebsocketService,
            PlagiarismCacheService plagiarismCacheService) {
        this.textSubmissionExportService = textSubmissionExportService;
        this.plagiarismWebsocketService = plagiarismWebsocketService;
        this.plagiarismCacheService = plagiarismCacheService;
    }

    /**
     * Reduce a TextExercise Object to a list of latest text submissions. Filters the empty ones because they do not need to be compared
     *
     * @param exerciseWithParticipationsAndSubmissions TextExercise with fetched participations and submissions
     * @param minimumScore                             consider only submissions whose score is greater or equal to this value
     * @param minimumSize                              consider only submissions whose number of words is greater or equal to this value
     * @return List containing the latest text submission for every participation
     */
    public List<TextSubmission> textSubmissionsForComparison(TextExercise exerciseWithParticipationsAndSubmissions, int minimumScore, int minimumSize) {
        var textSubmissions = exerciseWithParticipationsAndSubmissions.getStudentParticipations().parallelStream().map(Participation::findLatestSubmission)
                .filter(Optional::isPresent).map(Optional::get).filter(submission -> submission instanceof TextSubmission).map(submission -> (TextSubmission) submission)
                .filter(submission -> minimumSize == 0 || submission.getText() != null && submission.countWords() >= minimumSize)
                .filter(submission -> minimumScore == 0
                        || submission.getLatestResult() != null && submission.getLatestResult().getScore() != null && submission.getLatestResult().getScore() >= minimumScore)
                .toList();

        log.info("Found {} text submissions in exercise {}", textSubmissions.size(), exerciseWithParticipationsAndSubmissions.getId());

        return textSubmissions.parallelStream().filter(textSubmission -> !textSubmission.isEmpty()).toList();
    }

    /**
     * Download all submissions of the exercise, run JPlag, and return the result
     *
     * @param textExercise        to detect plagiarism for
     * @param similarityThreshold ignore comparisons whose similarity is below this threshold (%)
     * @param minimumScore        consider only submissions whose score is greater or equal to this value
     * @param minimumSize         consider only submissions whose size is greater or equal to this value
     * @return a zip file that can be returned to the client
     * @throws ExitException is thrown if JPlag exits unexpectedly
     */
    public TextPlagiarismResult checkPlagiarism(TextExercise textExercise, float similarityThreshold, int minimumScore, int minimumSize) throws ExitException {
        // Only one plagiarism check per course allowed
        var courseId = textExercise.getCourseViaExerciseGroupOrCourseMember().getId();

        try {
            if (plagiarismCacheService.isActivePlagiarismCheck(courseId)) {
                throw new BadRequestAlertException("Only one active plagiarism check per course allowed", "PlagiarismCheck", "oneActivePlagiarismCheck");
            }
            plagiarismCacheService.setActivePlagiarismCheck(courseId);

            long start = System.nanoTime();
            String topic = plagiarismWebsocketService.getTextExercisePlagiarismCheckTopic(textExercise.getId());

            // TODO: why do we have such a strange folder name?
            final var submissionsFolderName = "./tmp/submissions";
            final var submissionFolderFile = new File(submissionsFolderName);
            submissionFolderFile.mkdirs();

            final List<TextSubmission> textSubmissions = textSubmissionsForComparison(textExercise, minimumScore, minimumSize);
            final var submissionsSize = textSubmissions.size();
            log.info("Save text submissions for JPlag text comparison with {} submissions", submissionsSize);

            if (textSubmissions.size() < 2) {
                log.info("Insufficient amount of submissions for plagiarism detection. Return empty result.");
                TextPlagiarismResult textPlagiarismResult = new TextPlagiarismResult();
                textPlagiarismResult.setExercise(textExercise);
                textPlagiarismResult.setSimilarityDistribution(new int[0]);

                return textPlagiarismResult;
            }

            AtomicInteger processedSubmissionCount = new AtomicInteger(1);
            textSubmissions.forEach(submission -> {
                var progressMessage = "Getting submission: " + processedSubmissionCount + "/" + textSubmissions.size();
                plagiarismWebsocketService.notifyInstructorAboutPlagiarismState(topic, PlagiarismCheckState.RUNNING, List.of(progressMessage));
                submission.setResults(new ArrayList<>());

                StudentParticipation participation = (StudentParticipation) submission.getParticipation();
                participation.setExercise(null);
                participation.setSubmissions(null);

                String participantIdentifier = participation.getParticipantIdentifier();
                if (participantIdentifier == null) {
                    participantIdentifier = "unknown";
                }

                try {
                    textSubmissionExportService.saveSubmissionToFile(submission, participantIdentifier, submissionsFolderName);
                }
                catch (IOException e) {
                    log.error(e.getMessage());
                }

                processedSubmissionCount.getAndIncrement();
            });

            log.info("Saving text submissions done");

            JPlagOptions options = new JPlagOptions(submissionsFolderName, LanguageOption.TEXT);
            options.setMinimumTokenMatch(minimumSize);

            // Important: for large courses with more than 1000 students, we might get more than one million results and 10 million files in the file system due to many 0% results,
            // therefore we limit the results to at least 50% or 0.5 similarity, the passed threshold is between 0 and 100%
            options.setSimilarityThreshold(similarityThreshold);

            log.info("Start JPlag Text comparison");
            JPlag jplag = new JPlag(options);
            JPlagResult jPlagResult = jplag.run();
            log.info("JPlag Text comparison finished with {} comparisons. Will limit the number of comparisons to 500", jPlagResult.getComparisons().size());

            log.info("Delete submission folder");
            if (submissionFolderFile.exists()) {
                FileSystemUtils.deleteRecursively(submissionFolderFile);
            }

            TextPlagiarismResult textPlagiarismResult = new TextPlagiarismResult();
            textPlagiarismResult.convertJPlagResult(jPlagResult);
            textPlagiarismResult.setExercise(textExercise);

            log.info("JPlag text comparison for {} submissions done in {}", submissionsSize, TimeLogUtil.formatDurationFrom(start));
            plagiarismWebsocketService.notifyInstructorAboutPlagiarismState(topic, PlagiarismCheckState.COMPLETED, List.of());
            return textPlagiarismResult;
        }
        finally {
            plagiarismCacheService.setInactivePlagiarismCheck(courseId);
        }
    }
}
