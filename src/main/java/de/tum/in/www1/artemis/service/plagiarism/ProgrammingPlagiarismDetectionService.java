package de.tum.in.www1.artemis.service.plagiarism;

import java.io.File;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.validation.constraints.NotNull;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import de.jplag.JPlag;
import de.jplag.JPlagResult;
import de.jplag.Language;
import de.jplag.clustering.ClusteringOptions;
import de.jplag.exceptions.ExitException;
import de.jplag.options.JPlagOptions;
import de.jplag.reporting.reportobject.ReportObjectFactory;
import de.tum.in.www1.artemis.domain.PlagiarismCheckState;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.Repository;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextPlagiarismResult;
import de.tum.in.www1.artemis.exception.GitException;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismResultRepository;
import de.tum.in.www1.artemis.service.FileService;
import de.tum.in.www1.artemis.service.UrlService;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.export.ProgrammingExerciseExportService;
import de.tum.in.www1.artemis.service.plagiarism.cache.PlagiarismCacheService;
import de.tum.in.www1.artemis.service.util.TimeLogUtil;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

@Service
public class ProgrammingPlagiarismDetectionService {

    @Value("${artemis.repo-download-clone-path}")
    private Path repoDownloadClonePath;

    private final Logger log = LoggerFactory.getLogger(ProgrammingPlagiarismDetectionService.class);

    private final FileService fileService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final GitService gitService;

    private final StudentParticipationRepository studentParticipationRepository;

    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());

    private final ProgrammingExerciseExportService programmingExerciseExportService;

    private final PlagiarismResultRepository plagiarismResultRepository;

    private final PlagiarismWebsocketService plagiarismWebsocketService;

    private final PlagiarismCacheService plagiarismCacheService;

    private final UrlService urlService;

    public ProgrammingPlagiarismDetectionService(ProgrammingExerciseRepository programmingExerciseRepository, FileService fileService, GitService gitService,
            StudentParticipationRepository studentParticipationRepository, PlagiarismResultRepository plagiarismResultRepository,
            ProgrammingExerciseExportService programmingExerciseExportService, PlagiarismWebsocketService plagiarismWebsocketService, PlagiarismCacheService plagiarismCacheService,
            UrlService urlService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.fileService = fileService;
        this.gitService = gitService;
        this.studentParticipationRepository = studentParticipationRepository;
        this.programmingExerciseExportService = programmingExerciseExportService;
        this.plagiarismResultRepository = plagiarismResultRepository;
        this.plagiarismWebsocketService = plagiarismWebsocketService;
        this.plagiarismCacheService = plagiarismCacheService;
        this.urlService = urlService;
    }

    /**
     * downloads all repos of the exercise and runs JPlag
     *
     * @param programmingExerciseId the id of the programming exercises which should be checked
     * @param similarityThreshold   ignore comparisons whose similarity is below this threshold (in % between 0 and 100)
     * @param minimumScore          consider only submissions whose score is greater or equal to this value
     * @return the text plagiarism result container with up to 500 comparisons with the highest similarity values
     * @throws ExitException is thrown if JPlag exits unexpectedly
     * @throws IOException   is thrown for file handling errors
     */
    public TextPlagiarismResult checkPlagiarism(long programmingExerciseId, float similarityThreshold, int minimumScore) throws ExitException, IOException {
        long start = System.nanoTime();
        String topic = plagiarismWebsocketService.getProgrammingExercisePlagiarismCheckTopic(programmingExerciseId);

        final var programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(programmingExerciseId);

        // Only one plagiarism check per course allowed
        var courseId = programmingExercise.getCourseViaExerciseGroupOrCourseMember().getId();

        try {
            if (plagiarismCacheService.isActivePlagiarismCheck(courseId)) {
                throw new BadRequestAlertException("Only one active plagiarism check per course allowed", "PlagiarismCheck", "oneActivePlagiarismCheck");
            }
            plagiarismCacheService.setActivePlagiarismCheck(courseId);

            JPlagResult jPlagResult = computeJPlagResult(programmingExercise, similarityThreshold, minimumScore);
            if (jPlagResult == null) {
                log.info("Insufficient amount of submissions for plagiarism detection. Return empty result.");
                TextPlagiarismResult textPlagiarismResult = new TextPlagiarismResult();
                textPlagiarismResult.setExercise(programmingExercise);
                textPlagiarismResult.setSimilarityDistribution(new int[0]);

                log.info("Finished programmingExerciseExportService.checkPlagiarism call for {} comparisons in {}", textPlagiarismResult.getComparisons().size(),
                        TimeLogUtil.formatDurationFrom(start));
                limitAndSavePlagiarismResult(textPlagiarismResult);
                log.info("Finished plagiarismResultRepository.savePlagiarismResultAndRemovePrevious call in {}", TimeLogUtil.formatDurationFrom(start));
                return textPlagiarismResult;
            }

            log.info("JPlag programming comparison finished with {} comparisons for programming exercise {}", jPlagResult.getAllComparisons().size(), programmingExerciseId);
            TextPlagiarismResult textPlagiarismResult = new TextPlagiarismResult();
            textPlagiarismResult.convertJPlagResult(jPlagResult, programmingExercise);

            log.info("JPlag programming comparison done in {}", TimeLogUtil.formatDurationFrom(start));
            plagiarismWebsocketService.notifyInstructorAboutPlagiarismState(topic, PlagiarismCheckState.COMPLETED, List.of());
            limitAndSavePlagiarismResult(textPlagiarismResult);
            return textPlagiarismResult;
        }
        finally {
            plagiarismCacheService.setInactivePlagiarismCheck(courseId);
        }
    }

    /**
     * downloads all repos of the exercise and runs JPlag
     *
     * @param programmingExerciseId the id of the programming exercises which should be checked
     * @param similarityThreshold   ignore comparisons whose similarity is below this threshold (in % between 0 and 100)
     * @param minimumScore          consider only submissions whose score is greater or equal to this value
     * @return a zip file that can be returned to the client
     */
    public File checkPlagiarismWithJPlagReport(long programmingExerciseId, float similarityThreshold, int minimumScore) {
        long start = System.nanoTime();

        final var programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(programmingExerciseId);
        JPlagResult result = computeJPlagResult(programmingExercise, similarityThreshold, minimumScore);

        log.info("JPlag programming comparison finished with {} comparisons in {}", result.getAllComparisons().size(), TimeLogUtil.formatDurationFrom(start));
        return generateJPlagReportZip(result, programmingExercise);
    }

    /**
     * Checks for plagiarism and returns a JPlag result
     *
     * @param programmingExercise the programming exercise to check
     * @param similarityThreshold the similarity threshold (in % between 0 and 100)
     * @param minimumScore        the minimum score
     * @return the JPlag result or null if there are not enough participations
     */
    @NotNull
    private JPlagResult computeJPlagResult(ProgrammingExercise programmingExercise, float similarityThreshold, int minimumScore) {
        long programmingExerciseId = programmingExercise.getId();
        final var targetPath = fileService.getTemporaryUniqueSubfolderPath(repoDownloadClonePath, 60);
        List<ProgrammingExerciseParticipation> participations = filterStudentParticipationsForComparison(programmingExercise, minimumScore);
        log.info("Download repositories for JPlag for programming exercise {} to compare {} participations", programmingExerciseId, participations.size());

        if (participations.size() < 2) {
            throw new BadRequestAlertException("Insufficient amount of valid and long enough submissions available for comparison", "Plagiarism Check", "notEnoughSubmissions");
        }

        List<Repository> repositories = downloadRepositories(programmingExercise, participations, targetPath.toString());
        log.info("Downloading repositories done for programming exercise {}", programmingExerciseId);

        final var projectKey = programmingExercise.getProjectKey();
        final var repoFolder = targetPath.resolve(projectKey).toFile();
        final var programmingLanguage = getJPlagProgrammingLanguage(programmingExercise);
        final var templateRepoName = urlService.getRepositorySlugFromRepositoryUrl(programmingExercise.getTemplateParticipation().getVcsRepositoryUrl());

        JPlagOptions options = new JPlagOptions(programmingLanguage, Set.of(repoFolder), Set.of())
                // JPlag expects a value between 0.0 and 1.0
                .withSimilarityThreshold(similarityThreshold / 100.0).withClusteringOptions(new ClusteringOptions().withEnabled(false));
        if (templateRepoName != null) {
            var templateFolder = targetPath.resolve(projectKey).resolve(templateRepoName).toFile();
            options = options.withBaseCodeSubmissionDirectory(templateFolder);
        }

        log.info("Start JPlag programming comparison for programming exercise {}", programmingExerciseId);
        String topic = plagiarismWebsocketService.getProgrammingExercisePlagiarismCheckTopic(programmingExerciseId);
        plagiarismWebsocketService.notifyInstructorAboutPlagiarismState(topic, PlagiarismCheckState.RUNNING, List.of("Running JPlag..."));

        JPlag jplag = new JPlag(options);
        JPlagResult result;
        try {
            result = jplag.run();
        }
        catch (Exception e) {
            // Handling small or invalid base codes
            log.error(e.getMessage(), e);
            log.warn("Retrying JPlag Plagiarism Check without BaseCode");
            try {
                options = options.withBaseCodeSubmissionDirectory(null);
                jplag = new JPlag(options);
                result = jplag.run();
            }
            catch (Exception ex) {
                log.info("FAILED: Retrying JPlag Plagiarism Check without BaseCode");
                log.error(ex.getMessage(), ex);
                throw new BadRequestAlertException(ex.getMessage(), "Plagiarism Check", "jplagException");
            }
        }
        finally {
            cleanupResourcesAsync(programmingExercise, repositories, targetPath);
        }

        return result;
    }

    /**
     * Sorts and limits the text plagiarism result amount to 500 and saves it into the database.
     * Removes the previously saved result.
     *
     * @param textPlagiarismResult the plagiarism result to save
     */
    private void limitAndSavePlagiarismResult(TextPlagiarismResult textPlagiarismResult) {
        // TODO: limit the amount temporarily because of database issues
        textPlagiarismResult.sortAndLimit(100);
        log.info("Limited number of comparisons to {} to avoid performance issues when saving to database", textPlagiarismResult.getComparisons().size());
        plagiarismResultRepository.savePlagiarismResultAndRemovePrevious(textPlagiarismResult);
    }

    /**
     * Generates a JPlag report and zips it.
     *
     * @param jPlagResult         The JPlag result
     * @param programmingExercise the programming exercise
     * @return the zip file
     */
    public File generateJPlagReportZip(JPlagResult jPlagResult, ProgrammingExercise programmingExercise) {
        final var targetPath = fileService.getTemporaryUniqueSubfolderPath(repoDownloadClonePath, 5);
        final var reportFolder = targetPath.resolve(programmingExercise.getProjectKey() + " JPlag Report");
        final var reportFolderFile = reportFolder.toFile();

        // Create directories.
        if (!reportFolderFile.mkdirs()) {
            log.error("Cannot generate JPlag report because directories couldn't be created: {}", reportFolder);
            // this error is unlikely to happen
            return null;
        }

        // Write JPlag report result to the file.
        log.info("Write JPlag report to file system and zip it");
        ReportObjectFactory reportObjectFactory = new ReportObjectFactory();
        reportObjectFactory.createAndSaveReport(jPlagResult, reportFolder.toString());
        // JPlag automatically zips the report

        var zipFile = new File(reportFolder + ".zip");
        fileService.schedulePathForDeletion(zipFile.getAbsoluteFile().toPath(), 1);
        return zipFile;
    }

    private void cleanupResourcesAsync(final ProgrammingExercise programmingExercise, final List<Repository> repositories, final Path targetPath) {
        executor.schedule(() -> {
            log.info("Will delete local repositories for programming exercise {} after plagiarism check", programmingExercise.getId());
            deleteLocalRepositories(repositories);
            // delete project root folder in the repos download folder
            programmingExerciseExportService.deleteReposDownloadProjectRootDirectory(programmingExercise, targetPath);
            log.info("Delete local repositories done for programming exercise {} after plagiarism check", programmingExercise.getId());
        }, 10, TimeUnit.SECONDS);
    }

    private void deleteLocalRepositories(List<Repository> repositories) {
        repositories.parallelStream().forEach(repository -> {
            var localPath = repository.getLocalPath();
            try {
                deleteTempLocalRepository(repository);
            }
            catch (GitException ex) {
                log.error("Delete repository {} did not work as expected: {}", localPath, ex.getMessage());
            }
        });
    }

    /**
     * Deletes the locally checked out repository.
     *
     * @param repository The repository that should get deleted
     */
    public void deleteTempLocalRepository(Repository repository) {
        // we do some cleanup here to prevent future errors with file handling
        // We can always delete the repository as it won't be used by the student (separate path)
        if (repository != null) {
            try {
                gitService.deleteLocalRepository(repository);
            }
            catch (IOException ex) {
                log.warn("Could not delete temporary repository {}: {}", repository.getLocalPath().toString(), ex.getMessage());
            }
        }
    }

    private Language getJPlagProgrammingLanguage(ProgrammingExercise programmingExercise) {
        return switch (programmingExercise.getProgrammingLanguage()) {
            case JAVA -> new de.jplag.java.Language();
            case C -> new de.jplag.cpp.Language();
            case PYTHON -> new de.jplag.python3.Language();
            case SWIFT -> new de.jplag.swift.Language();
            case KOTLIN -> new de.jplag.kotlin.Language();
            default -> throw new BadRequestAlertException("Programming language " + programmingExercise.getProgrammingLanguage() + " not supported for plagiarism check.",
                    "ProgrammingExercise", "notSupported");
        };
    }

    /**
     * Find all studentParticipations of the given exercise for plagiarism comparison.
     * Filter out participations without submissions (i.e. empty submissions)
     *
     * @param programmingExercise ProgrammingExercise to fetch the participations for
     * @param minimumScore        consider only submissions whose score is greater or equal to this value
     * @return an unmodifiable list containing the latest text submission for every participation
     */
    public List<ProgrammingExerciseParticipation> filterStudentParticipationsForComparison(ProgrammingExercise programmingExercise, int minimumScore) {
        var studentParticipations = studentParticipationRepository.findAllForPlagiarism(programmingExercise.getId());

        return studentParticipations.parallelStream().filter(participation -> !participation.isPracticeMode())
                .filter(participation -> participation instanceof ProgrammingExerciseParticipation).map(participation -> (ProgrammingExerciseParticipation) participation)
                .filter(participation -> participation.getVcsRepositoryUrl() != null).filter(participation -> {
                    Submission submission = participation.findLatestSubmission().orElse(null);
                    // filter empty submissions
                    if (submission == null) {
                        return false;
                    }
                    return minimumScore == 0
                            || submission.getLatestResult() != null && submission.getLatestResult().getScore() != null && submission.getLatestResult().getScore() >= minimumScore;
                }).toList();
    }

    private List<Repository> downloadRepositories(ProgrammingExercise programmingExercise, List<ProgrammingExerciseParticipation> participations, String targetPath) {
        // Used for sending progress notifications
        var topic = plagiarismWebsocketService.getProgrammingExercisePlagiarismCheckTopic(programmingExercise.getId());

        int maxRepositories = participations.size() + 1;
        List<Repository> downloadedRepositories = new ArrayList<>();
        participations.parallelStream().forEach(participation -> {
            try {
                var progressMessage = "Downloading repositories: " + (downloadedRepositories.size() + 1) + "/" + maxRepositories;
                plagiarismWebsocketService.notifyInstructorAboutPlagiarismState(topic, PlagiarismCheckState.RUNNING, List.of(progressMessage));

                Repository repo = gitService.getOrCheckoutRepositoryForJPlag(participation, targetPath);
                gitService.resetToOriginHead(repo); // start with clean state
                downloadedRepositories.add(repo);
            }
            catch (GitException | GitAPIException | InvalidPathException ex) {
                log.error("Clone student repository {} in exercise '{}' did not work as expected: {}", participation.getVcsRepositoryUrl(), programmingExercise.getTitle(),
                        ex.getMessage());
            }
        });

        // clone the template repo
        try {
            var progressMessage = "Downloading repositories: " + maxRepositories + "/" + maxRepositories;
            plagiarismWebsocketService.notifyInstructorAboutPlagiarismState(topic, PlagiarismCheckState.RUNNING, List.of(progressMessage));

            Repository templateRepo = gitService.getOrCheckoutRepository(programmingExercise.getTemplateParticipation(), targetPath);
            gitService.resetToOriginHead(templateRepo); // start with clean state
            downloadedRepositories.add(templateRepo);
        }
        catch (GitException | GitAPIException ex) {
            log.error("Clone template repository {} in exercise '{}' did not work as expected: {}", programmingExercise.getTemplateParticipation().getVcsRepositoryUrl(),
                    programmingExercise.getTitle(), ex.getMessage());
        }

        return downloadedRepositories;
    }
}
