package de.tum.in.www1.artemis.service.plagiarism.text;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toUnmodifiableList;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;

import jplag.ExitException;
import jplag.Program;
import jplag.options.CommandLineOptions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.service.FileService;
import de.tum.in.www1.artemis.service.TextSubmissionExportService;
import de.tum.in.www1.artemis.service.ZipFileService;

@Service
public class TextPlagiarismDetectionService {

    private final Logger log = LoggerFactory.getLogger(TextPlagiarismDetectionService.class);

    private final FileService fileService;

    private final TextSubmissionExportService textSubmissionExportService;

    private final ZipFileService zipFileService;

    public TextPlagiarismDetectionService(FileService fileService, TextSubmissionExportService textSubmissionExportService, ZipFileService zipFileService) {
        this.fileService = fileService;
        this.textSubmissionExportService = textSubmissionExportService;
        this.zipFileService = zipFileService;
    }

    /**
     * Convenience method to extract all latest submissions from a TextExercise and compute pair-wise distances.
     *
     * @param exerciseWithParticipationsAndSubmissions Text Exercise with fetched participations and submissions
     * @param comparisonStrategy the chosen comparison strategy
     * @param comparisonStrategyName the name of the strategy for logging purpose
     * @param minimumSimilarity the minimum similarity (between 0 and 1) that should be reported in the response
     * @return Map of text submission pairs and similarity score
     */
    public Map<Set<TextSubmission>, Double> compareSubmissionsForExerciseWithStrategy(TextExercise exerciseWithParticipationsAndSubmissions,
            TextComparisonStrategy comparisonStrategy, String comparisonStrategyName, double minimumSimilarity) {
        final List<TextSubmission> textSubmissions = textSubmissionsForComparison(exerciseWithParticipationsAndSubmissions);
        return compareSubmissionsForExerciseWithStrategy(textSubmissions, comparisonStrategy, comparisonStrategyName, minimumSimilarity);
    }

    /**
     * Pairwise comparison of text submissions using a TextComparisonStrategy
     *
     * @param textSubmissions List of text submissions
     * @param comparisonStrategy the chosen comparison strategy
     * @param comparisonStrategyName the name of the strategy for logging purpose
     * @param minimumSimilarity the minimum similarity (between 0 and 1) that should be reported in the response
     * @return Map of text submission pairs and similarity score
     */
    public Map<Set<TextSubmission>, Double> compareSubmissionsForExerciseWithStrategy(List<TextSubmission> textSubmissions, TextComparisonStrategy comparisonStrategy,
            String comparisonStrategyName, double minimumSimilarity) {
        final Map<Set<TextSubmission>, Double> map = new HashMap<>();

        // it is intended to use the classic for loop here, because we only want to check similarity between two different submissions once
        for (int i = 0; i < textSubmissions.size(); i++) {
            for (int j = i + 1; j < textSubmissions.size(); j++) {
                final TextSubmission textSubmission1 = textSubmissions.get(i);
                final TextSubmission textSubmission2 = textSubmissions.get(j);
                final double similarity = 1 - comparisonStrategy.compare(textSubmission1, textSubmission2);
                log.debug("Compare result " + i + " with " + j + ": " + similarity);
                if (similarity >= minimumSimilarity) {
                    log.info("Found similar text " + i + " with " + j + ": " + similarity + " (using strategy " + comparisonStrategyName + ")");
                    map.put(Set.of(textSubmission1, textSubmission2), similarity);
                }
            }
        }

        log.info("Found " + map.size() + " similar text submission combinations ( > " + minimumSimilarity + ") using strategy " + comparisonStrategyName);

        return map;
    }

    /**
     * Reduce a TextExercise Object to a list of latest text submissions. Filters the empty ones because they do not need to be compared
     *
     * @param exerciseWithParticipationsAndSubmissions TextExercise with fetched participations and ssubmissions
     * @return List containing the latest text submission for every participation
     */
    public List<TextSubmission> textSubmissionsForComparison(TextExercise exerciseWithParticipationsAndSubmissions) {
        var textSubmissions = exerciseWithParticipationsAndSubmissions.getStudentParticipations().parallelStream().map(Participation::findLatestSubmission)
                .filter(Optional::isPresent).map(Optional::get).filter(submission -> submission instanceof TextSubmission).map(submission -> (TextSubmission) submission)
                .collect(toList());
        log.info("Found " + textSubmissions.size() + " text submissions in exercise " + exerciseWithParticipationsAndSubmissions.getId());
        return textSubmissions.parallelStream().filter(textSubmission -> !textSubmission.isEmpty()).collect(toUnmodifiableList());
    }

    /**
     * Download all submissions of the exercise, run JPlag, and return the result
     *
     * @param textExercise to detect plagiarism for
     * @return a zip file that can be returned to the client
     * @throws ExitException is thrown if JPlag exits unexpectedly
     * @throws IOException   is thrown for file handling errors
     */
    public File checkPlagiarism(TextExercise textExercise) throws ExitException, IOException {
        // TODO: offer the following options in the client
        // 1) filter empty submissions, i.e. repositories with no student commits
        // 2) filter submissions with a result score of 0%

        final var jplagResultFolderName = "./tmp/jplag-result";
        final var submissionsFolderName = "./tmp/submissions";
        final var zipFilePath = Paths.get(String.format("./tmp/%s-%s-%s-JPlag-Output.zip", textExercise.getCourseViaExerciseGroupOrCourseMember().getShortName(),
                textExercise.getShortName(), System.currentTimeMillis()));

        final var submissionFolderFile = new File(submissionsFolderName);
        submissionFolderFile.mkdirs();

        final List<TextSubmission> textSubmissions = textSubmissionsForComparison(textExercise);

        textSubmissions.forEach(submission -> {
            submission.getParticipation().setExercise(null);
            submission.setResults(null);
            submission.getParticipation().setSubmissions(null);

            try {
                textSubmissionExportService.saveSubmissionToFile(textExercise, submission, submissionsFolderName);
            }
            catch (IOException e) {
                log.error(e.getMessage());
            }
        });

        final var jplagResultFolderFile = new File(jplagResultFolderName);
        jplagResultFolderFile.mkdirs();

        final var jplagArgs = new String[] {
                // Language: text
                "-l", "text",

                // Name of directory in which the resulting web pages will be stored
                "-r", jplagResultFolderName,

                // Option to look at sub-directories too
                "-s",

                // Name of the directory which contains the base code
                // "-bc", templateRepoName,

                // Specify verbosity
                "-vq",

                // The root-directory that contains all submissions
                submissionsFolderName };

        final CommandLineOptions options = new CommandLineOptions(jplagArgs, null);
        final Program program = new Program(options);
        program.run();

        zipFileService.createZipFileWithFolderContent(zipFilePath, jplagResultFolderFile.toPath());
        fileService.scheduleForDeletion(zipFilePath, 5);

        // cleanup
        if (jplagResultFolderFile.exists()) {
            FileSystemUtils.deleteRecursively(jplagResultFolderFile);
        }

        if (submissionFolderFile.exists()) {
            FileSystemUtils.deleteRecursively(submissionFolderFile);
        }

        return new File(zipFilePath.toString());
    }

}
