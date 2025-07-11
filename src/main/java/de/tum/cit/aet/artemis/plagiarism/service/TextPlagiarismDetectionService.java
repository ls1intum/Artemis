package de.tum.cit.aet.artemis.plagiarism.service;

import static de.tum.cit.aet.artemis.plagiarism.service.PlagiarismService.hasMinimumScore;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

import de.jplag.JPlag;
import de.jplag.JPlagResult;
import de.jplag.Language;
import de.jplag.clustering.ClusteringOptions;
import de.jplag.options.JPlagOptions;
import de.jplag.text.NaturalLanguage;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.util.TimeLogUtil;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.plagiarism.config.PlagiarismEnabled;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismCheckState;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismResult;
import de.tum.cit.aet.artemis.plagiarism.service.cache.PlagiarismCacheService;
import de.tum.cit.aet.artemis.text.api.TextSubmissionExportApi;
import de.tum.cit.aet.artemis.text.config.TextApiNotPresentException;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

@Conditional(PlagiarismEnabled.class)
@Lazy
@Service
public class TextPlagiarismDetectionService {

    private static final Logger log = LoggerFactory.getLogger(TextPlagiarismDetectionService.class);

    private final Optional<TextSubmissionExportApi> textSubmissionExportApi;

    private final PlagiarismWebsocketService plagiarismWebsocketService;

    private final PlagiarismCacheService plagiarismCacheService;

    private final PlagiarismService plagiarismService;

    public TextPlagiarismDetectionService(Optional<TextSubmissionExportApi> textSubmissionExportApi, PlagiarismWebsocketService plagiarismWebsocketService,
            PlagiarismCacheService plagiarismCacheService, PlagiarismService plagiarismService) {
        this.textSubmissionExportApi = textSubmissionExportApi;
        this.plagiarismWebsocketService = plagiarismWebsocketService;
        this.plagiarismCacheService = plagiarismCacheService;
        this.plagiarismService = plagiarismService;
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
        var textSubmissions = exerciseWithParticipationsAndSubmissions.getStudentParticipations().parallelStream().filter(plagiarismService.filterForStudents())
                .map(Participation::findLatestSubmission).filter(Optional::isPresent).map(Optional::get).filter(submission -> submission instanceof TextSubmission)
                .map(submission -> (TextSubmission) submission).filter(submission -> minimumSize == 0 || submission.getText() != null && submission.countWords() >= minimumSize)
                .filter(submission -> hasMinimumScore(submission, minimumScore)).toList();

        log.info("Found {} text submissions in exercise {}", textSubmissions.size(), exerciseWithParticipationsAndSubmissions.getId());

        return textSubmissions.parallelStream().filter(textSubmission -> !textSubmission.isEmpty()).toList();
    }

    /**
     * Download all submissions of the exercise, run JPlag, and return the result
     *
     * @param textExercise        to detect plagiarism for
     * @param similarityThreshold ignore comparisons whose similarity is below this threshold (in % between 0 and 100)
     * @param minimumScore        consider only submissions whose score is greater or equal to this value
     * @param minimumSize         consider only submissions whose size is greater or equal to this value
     * @return a zip file that can be returned to the client
     */
    public PlagiarismResult checkPlagiarism(TextExercise textExercise, float similarityThreshold, int minimumScore, int minimumSize) {
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
            final var submissionFolderFile = Path.of(submissionsFolderName).toFile();
            submissionFolderFile.mkdirs();

            final List<TextSubmission> textSubmissions = textSubmissionsForComparison(textExercise, minimumScore, minimumSize);
            final var submissionsSize = textSubmissions.size();
            log.info("Save text submissions for JPlag text comparison with {} submissions", submissionsSize);

            if (textSubmissions.size() < 2) {
                log.info("Insufficient amount of submissions for plagiarism detection. Inform the client with a bad request response.");
                throw new BadRequestAlertException("Insufficient amount of valid and long enough submissions available for comparison", "Plagiarism Check", "notEnoughSubmissions");
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
                    textSubmissionExportApi.orElseThrow(() -> new TextApiNotPresentException(TextSubmissionExportApi.class)).saveSubmissionToFile(submission, participantIdentifier,
                            submissionsFolderName);
                }
                catch (IOException e) {
                    log.error(e.getMessage());
                }

                processedSubmissionCount.getAndIncrement();
            });

            log.info("Saving text submissions done");

            // Important: for large courses with more than 1000 students, we might get more than one million results and 10 million files in the file system due to many 0% results,
            // therefore we limit the results to at least 50% or 0.5 similarity, the passed threshold is between 0 and 100%
            Language language = new NaturalLanguage();
            JPlagOptions options = new JPlagOptions(language, Set.of(submissionFolderFile), Set.of())
                    // JPlag expects a value between 0.0 and 1.0
                    .withSimilarityThreshold(similarityThreshold / 100.0).withClusteringOptions(new ClusteringOptions().withEnabled(false));

            log.info("Start JPlag Text comparison");
            JPlagResult jPlagResult = JPlag.run(options);
            log.info("JPlag Text comparison finished with {} comparisons. Will limit the number of comparisons to 500", jPlagResult.getAllComparisons().size());

            log.info("Delete submission folder");
            if (submissionFolderFile.exists()) {
                FileSystemUtils.deleteRecursively(submissionFolderFile);
            }

            PlagiarismResult textPlagiarismResult = new PlagiarismResult();
            textPlagiarismResult.convertJPlagResult(jPlagResult, textExercise);

            log.info("JPlag text comparison for {} submissions done in {}", submissionsSize, TimeLogUtil.formatDurationFrom(start));
            plagiarismWebsocketService.notifyInstructorAboutPlagiarismState(topic, PlagiarismCheckState.COMPLETED, List.of());
            return textPlagiarismResult;
        }
        catch (Exception ex) {
            log.warn("Text plagiarism detection NOT successful", ex);
            throw new BadRequestAlertException(ex.getMessage(), "Plagiarism Check", "jplagException");
        }
        finally {
            plagiarismCacheService.setInactivePlagiarismCheck(courseId);
        }
    }
}
