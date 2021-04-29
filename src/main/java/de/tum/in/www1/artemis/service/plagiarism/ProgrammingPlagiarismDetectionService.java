package de.tum.in.www1.artemis.service.plagiarism;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.Repository;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextPlagiarismResult;
import de.tum.in.www1.artemis.exception.GitException;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.service.FileService;
import de.tum.in.www1.artemis.service.UrlService;
import de.tum.in.www1.artemis.service.ZipFileService;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseExportService;
import de.tum.in.www1.artemis.service.util.TimeLogUtil;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import jplag.ExitException;
import jplag.JPlag;
import jplag.JPlagOptions;
import jplag.JPlagResult;
import jplag.options.LanguageOption;
import jplag.reporting.Report;

@Service
public class ProgrammingPlagiarismDetectionService {

    @Value("${artemis.repo-download-clone-path}")
    private String repoDownloadClonePath;

    private final Logger log = LoggerFactory.getLogger(ProgrammingPlagiarismDetectionService.class);

    private final FileService fileService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final UrlService urlService;

    private final ZipFileService zipFileService;

    private final GitService gitService;

    private final StudentParticipationRepository studentParticipationRepository;

    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());

    private final ProgrammingExerciseExportService programmingExerciseExportService;

    public ProgrammingPlagiarismDetectionService(ProgrammingExerciseRepository programmingExerciseRepository, FileService fileService, UrlService urlService,
            ZipFileService zipFileService, GitService gitService, StudentParticipationRepository studentParticipationRepository,
            ProgrammingExerciseExportService programmingExerciseExportService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.fileService = fileService;
        this.urlService = urlService;
        this.zipFileService = zipFileService;
        this.gitService = gitService;
        this.studentParticipationRepository = studentParticipationRepository;
        this.programmingExerciseExportService = programmingExerciseExportService;
    }

    /**
     * downloads all repos of the exercise and runs JPlag
     *
     * @param programmingExerciseId the id of the programming exercises which should be checked
     * @param similarityThreshold   ignore comparisons whose similarity is below this threshold (%)
     * @param minimumScore          consider only submissions whose score is greater or equal to this value
     * @return a zip file that can be returned to the client
     * @throws ExitException is thrown if JPlag exits unexpectedly
     * @throws IOException   is thrown for file handling errors
     */
    public TextPlagiarismResult checkPlagiarism(long programmingExerciseId, float similarityThreshold, int minimumScore) throws ExitException, IOException {
        long start = System.nanoTime();

        final var programmingExercise = programmingExerciseRepository.findWithAllParticipationsById(programmingExerciseId).get();

        final var numberOfParticipations = programmingExercise.getStudentParticipations().size();
        log.info("Download repositories for JPlag for programming exexericse {} to compare {} participations", programmingExerciseId, numberOfParticipations);

        final var targetPath = fileService.getUniquePathString(repoDownloadClonePath);
        List<ProgrammingExerciseParticipation> participations = studentParticipationsForComparison(programmingExercise, minimumScore);

        if (participations.size() < 2) {
            log.info("Insufficient amount of submissions for plagiarism detection. Return empty result.");
            TextPlagiarismResult textPlagiarismResult = new TextPlagiarismResult();
            textPlagiarismResult.setExercise(programmingExercise);
            textPlagiarismResult.setSimilarityDistribution(new int[0]);

            return textPlagiarismResult;
        }

        // Todo: Takes long
        List<Repository> repositories = downloadRepositories(programmingExercise, participations, targetPath);
        log.info("Downloading repositories done for programming exercise {}", programmingExerciseId);

        final var projectKey = programmingExercise.getProjectKey();
        final var repoFolder = Paths.get(targetPath, projectKey).toString();
        final LanguageOption programmingLanguage = getJPlagProgrammingLanguage(programmingExercise);

        final var templateRepoName = urlService.getRepositorySlugFromRepositoryUrl(programmingExercise.getTemplateParticipation().getVcsRepositoryUrl());

        JPlagOptions options = new JPlagOptions(repoFolder, programmingLanguage);
        if (templateRepoName != null) {
            options.setBaseCodeSubmissionName(templateRepoName);
        }

        // Important: for large courses with more than 1000 students, we might get more than one million results and 10 million files in the file system due to many 0% results,
        // therefore we limit the results to at least 50% or 0.5 similarity, the passed threshold is between 0 and 100%
        options.setSimilarityThreshold(similarityThreshold);

        log.info("Start JPlag programming comparison for programming exercise {}", programmingExerciseId);

        JPlag jplag = new JPlag(options);
        JPlagResult result = jplag.run();

        log.info("JPlag programming comparison finished with {} comparisons for programming exercise {}", result.getComparisons().size(), programmingExerciseId);

        cleanupResourcesAsync(programmingExercise, repositories, targetPath);

        TextPlagiarismResult textPlagiarismResult = new TextPlagiarismResult();
        textPlagiarismResult.convertJPlagResult(result);
        textPlagiarismResult.setExercise(programmingExercise);

        log.info("JPlag programming comparison for {} participations done in {}", numberOfParticipations, TimeLogUtil.formatDurationFrom(start));

        return textPlagiarismResult;
    }

    /**
     * downloads all repos of the exercise and runs JPlag
     *
     * @param programmingExerciseId the id of the programming exercises which should be checked
     * @param similarityThreshold   ignore comparisons whose similarity is below this threshold (%)
     * @param minimumScore          consider only submissions whose score is greater or equal to this value
     * @return a zip file that can be returned to the client
     * @throws ExitException is thrown if JPlag exits unexpectedly
     * @throws IOException is created the zip failed
     */
    public File checkPlagiarismWithJPlagReport(long programmingExerciseId, float similarityThreshold, int minimumScore) throws ExitException, IOException {
        long start = System.nanoTime();

        final var programmingExercise = programmingExerciseRepository.findWithAllParticipationsById(programmingExerciseId).get();
        final var numberOfParticipations = programmingExercise.getStudentParticipations().size();

        log.info("Download repositories for JPlag programming comparison with {} participations", numberOfParticipations);
        final var targetPath = fileService.getUniquePathString(repoDownloadClonePath);
        List<ProgrammingExerciseParticipation> participations = studentParticipationsForComparison(programmingExercise, minimumScore);

        if (participations.size() < 2) {
            log.info("Insufficient amount of submissions for plagiarism detection. Return empty result.");
            return null;
        }

        List<Repository> repositories = downloadRepositories(programmingExercise, participations, targetPath);
        log.info("Downloading repositories done");

        final var output = "output";
        final var projectKey = programmingExercise.getProjectKey();
        final var outputFolder = Paths.get(targetPath, projectKey + "-" + output).toString();
        final var outputFolderFile = new File(outputFolder);

        outputFolderFile.mkdirs();

        final var repoFolder = Paths.get(targetPath, projectKey).toString();
        final LanguageOption programmingLanguage = getJPlagProgrammingLanguage(programmingExercise);

        final var templateRepoName = urlService.getRepositorySlugFromRepositoryUrl(programmingExercise.getTemplateParticipation().getVcsRepositoryUrl());

        JPlagOptions options = new JPlagOptions(repoFolder, programmingLanguage);
        if (templateRepoName != null) {
            options.setBaseCodeSubmissionName(templateRepoName);
        }

        // Important: for large courses with more than 1000 students, we might get more than one million results and 10 million files in the file system due to many 0% results,
        // therefore we limit the results to at least 50% or 0.5 similarity, the passed threshold is between 0 and 100%
        options.setSimilarityThreshold(similarityThreshold);

        log.info("Start JPlag programming comparison");
        JPlag jplag = new JPlag(options);
        JPlagResult result = jplag.run();
        log.info("JPlag programming comparison finished with {} comparisons", result.getComparisons().size());

        log.info("Write JPlag report to file system");
        Report jplagReport = new Report(outputFolderFile);
        jplagReport.writeResult(result);

        log.info("JPlag report done. Will zip it now");

        final var zipFilePath = Paths.get(targetPath, programmingExercise.getCourseViaExerciseGroupOrCourseMember().getShortName() + "-" + programmingExercise.getShortName() + "-"
                + System.currentTimeMillis() + "-Jplag-Analysis-Output.zip");
        zipFileService.createZipFileWithFolderContent(zipFilePath, Paths.get(outputFolder));

        log.info("JPlag report zipped. Delete report output folder");

        // cleanup
        if (outputFolderFile.exists()) {
            FileSystemUtils.deleteRecursively(outputFolderFile);
        }

        cleanupResourcesAsync(programmingExercise, repositories, targetPath);

        log.info("Schedule deletion of zip file in 1 minute");
        fileService.scheduleForDeletion(zipFilePath, 1);

        log.info("JPlag programming report for {} participations done in {}", numberOfParticipations, TimeLogUtil.formatDurationFrom(start));

        return new File(zipFilePath.toString());
    }

    private void cleanupResourcesAsync(final ProgrammingExercise programmingExercise, final List<Repository> repositories, final String targetPath) {
        executor.schedule(() -> {
            log.info("Will delete local repositories for programming exercise {}", programmingExercise.getId());
            deleteLocalRepositories(repositories);
            // delete project root folder in the repos download folder
            programmingExerciseExportService.deleteReposDownloadProjectRootDirectory(programmingExercise, targetPath);
            log.info("Delete repositories done for programming exercise {}", programmingExercise.getId());
        }, 10, TimeUnit.SECONDS);
    }

    private void deleteLocalRepositories(List<Repository> repositories) {
        repositories.parallelStream().forEach(repository -> {
            var localPath = repository.getLocalPath();
            try {
                programmingExerciseExportService.deleteTempLocalRepository(repository);
            }
            catch (GitException ex) {
                log.error("Delete repository {} did not work as expected: {}", localPath, ex.getMessage());
            }
        });
    }

    private LanguageOption getJPlagProgrammingLanguage(ProgrammingExercise programmingExercise) {
        return switch (programmingExercise.getProgrammingLanguage()) {
            case JAVA -> LanguageOption.JAVA_1_9;
            case C -> LanguageOption.C_CPP;
            case PYTHON -> LanguageOption.PYTHON_3;
            default -> throw new BadRequestAlertException("Programming language " + programmingExercise.getProgrammingLanguage() + " not supported for plagiarism check.",
                    "ProgrammingExercise", "notSupported");
        };
    }

    /**
     * Find all studentParticipations of the given exercise for plagiarism comparison.
     *
     * @param programmingExercise ProgrammingExercise to fetcch the participations for
     * @param minimumScore        consider only submissions whose score is greater or equal to this value
     * @return List containing the latest text submission for every participation
     */
    public List<ProgrammingExerciseParticipation> studentParticipationsForComparison(ProgrammingExercise programmingExercise, int minimumScore) {
        var studentParticipations = studentParticipationRepository.findAllWithEagerLegalSubmissionsAndEagerResultsByExerciseId(programmingExercise.getId());

        return studentParticipations.parallelStream().filter(participation -> participation instanceof ProgrammingExerciseParticipation)
                .map(participation -> (ProgrammingExerciseParticipation) participation).filter(participation -> participation.getVcsRepositoryUrl() != null)
                .filter(participation -> {
                    Submission submission = ((StudentParticipation) participation).findLatestSubmission().orElse(null);
                    return minimumScore == 0 || submission != null && submission.getLatestResult() != null && submission.getLatestResult().getScore() != null
                            && submission.getLatestResult().getScore() >= minimumScore;
                }).collect(Collectors.toList());
    }

    private List<Repository> downloadRepositories(ProgrammingExercise programmingExercise, List<ProgrammingExerciseParticipation> participations, String targetPath) {
        List<Repository> downloadedRepositories = new ArrayList<>();

        participations.forEach(participation -> {
            try {
                Repository repo = gitService.getOrCheckoutRepositoryForJPlag(participation, targetPath);
                gitService.resetToOriginMaster(repo); // start with clean state
                downloadedRepositories.add(repo);
            }
            catch (GitException | GitAPIException | InterruptedException ex) {
                log.error("Clone student repository {} in exercise '{}' did not work as expected: {}", participation.getVcsRepositoryUrl(), programmingExercise.getTitle(),
                        ex.getMessage());
            }
        });

        // clone the template repo
        try {
            Repository templateRepo = gitService.getOrCheckoutRepository(programmingExercise.getTemplateParticipation(), targetPath);
            gitService.resetToOriginMaster(templateRepo); // start with clean state
            downloadedRepositories.add(templateRepo);
        }
        catch (GitException | GitAPIException | InterruptedException ex) {
            log.error("Clone template repository {} in exercise '{}' did not work as expected: {}", programmingExercise.getTemplateParticipation().getVcsRepositoryUrl(),
                    programmingExercise.getTitle(), ex.getMessage());
        }

        return downloadedRepositories;
    }
}
