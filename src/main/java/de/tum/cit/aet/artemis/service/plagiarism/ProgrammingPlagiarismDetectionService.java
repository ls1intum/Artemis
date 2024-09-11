package de.tum.cit.aet.artemis.service.plagiarism;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.service.plagiarism.PlagiarismService.filterParticipationMinimumScore;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import jakarta.validation.constraints.NotNull;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.jplag.JPlag;
import de.jplag.JPlagResult;
import de.jplag.Language;
import de.jplag.c.CLanguage;
import de.jplag.clustering.ClusteringOptions;
import de.jplag.exceptions.ExitException;
import de.jplag.java.JavaLanguage;
import de.jplag.kotlin.KotlinLanguage;
import de.jplag.options.JPlagOptions;
import de.jplag.python3.PythonLanguage;
import de.jplag.reporting.reportobject.ReportObjectFactory;
import de.jplag.swift.SwiftLanguage;
import de.tum.cit.aet.artemis.core.exception.GitException;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismCheckState;
import de.tum.cit.aet.artemis.plagiarism.domain.text.TextPlagiarismResult;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.service.FileService;
import de.tum.cit.aet.artemis.service.UriService;
import de.tum.cit.aet.artemis.service.connectors.GitService;
import de.tum.cit.aet.artemis.service.export.ProgrammingExerciseExportService;
import de.tum.cit.aet.artemis.service.hestia.ProgrammingExerciseGitDiffReportService;
import de.tum.cit.aet.artemis.service.plagiarism.cache.PlagiarismCacheService;
import de.tum.cit.aet.artemis.service.util.TimeLogUtil;
import de.tum.cit.aet.artemis.web.rest.errors.BadRequestAlertException;

@Profile(PROFILE_CORE)
@Service
public class ProgrammingPlagiarismDetectionService {

    @Value("${artemis.repo-download-clone-path}")
    private Path repoDownloadClonePath;

    private static final Logger log = LoggerFactory.getLogger(ProgrammingPlagiarismDetectionService.class);

    private final FileService fileService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final PlagiarismService plagiarismService;

    private final GitService gitService;

    private final StudentParticipationRepository studentParticipationRepository;

    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());

    private final ProgrammingExerciseExportService programmingExerciseExportService;

    private final PlagiarismWebsocketService plagiarismWebsocketService;

    private final PlagiarismCacheService plagiarismCacheService;

    private final UriService uriService;

    private final ProgrammingExerciseGitDiffReportService programmingExerciseGitDiffReportService;

    public ProgrammingPlagiarismDetectionService(FileService fileService, ProgrammingExerciseRepository programmingExerciseRepository, PlagiarismService plagiarismService,
            GitService gitService, StudentParticipationRepository studentParticipationRepository, ProgrammingExerciseExportService programmingExerciseExportService,
            PlagiarismWebsocketService plagiarismWebsocketService, PlagiarismCacheService plagiarismCacheService, UriService uriService,
            ProgrammingExerciseGitDiffReportService programmingExerciseGitDiffReportService) {
        this.fileService = fileService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.plagiarismService = plagiarismService;
        this.gitService = gitService;
        this.studentParticipationRepository = studentParticipationRepository;
        this.programmingExerciseExportService = programmingExerciseExportService;
        this.plagiarismWebsocketService = plagiarismWebsocketService;
        this.plagiarismCacheService = plagiarismCacheService;
        this.uriService = uriService;
        this.programmingExerciseGitDiffReportService = programmingExerciseGitDiffReportService;
    }

    /**
     * downloads all repos of the exercise and runs JPlag
     *
     * @param programmingExerciseId the id of the programming exercises which should be checked
     * @param similarityThreshold   ignore comparisons whose similarity is below this threshold (in % between 0 and 100)
     * @param minimumScore          consider only submissions whose score is greater or equal to this value
     * @param minimumSize           consider only submissions whose number of lines in diff to template is greater or equal to this value
     * @return the text plagiarism result container with up to 500 comparisons with the highest similarity values
     * @throws ExitException is thrown if JPlag exits unexpectedly
     * @throws IOException   is thrown for file handling errors
     */
    public TextPlagiarismResult checkPlagiarism(long programmingExerciseId, float similarityThreshold, int minimumScore, int minimumSize) throws IOException {
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

            JPlagResult jPlagResult = computeJPlagResult(programmingExercise, similarityThreshold, minimumScore, minimumSize);
            if (jPlagResult == null) {
                log.info("Insufficient amount of submissions for plagiarism detection. Return empty result.");
                TextPlagiarismResult textPlagiarismResult = new TextPlagiarismResult();
                textPlagiarismResult.setExercise(programmingExercise);
                textPlagiarismResult.setSimilarityDistribution(new int[0]);

                log.info("Finished programmingExerciseExportService.checkPlagiarism call for {} comparisons in {}", textPlagiarismResult.getComparisons().size(),
                        TimeLogUtil.formatDurationFrom(start));
                log.info("Finished plagiarismResultRepository.savePlagiarismResultAndRemovePrevious call in {}", TimeLogUtil.formatDurationFrom(start));
                return textPlagiarismResult;
            }

            log.info("JPlag programming comparison finished with {} comparisons for programming exercise {}", jPlagResult.getAllComparisons().size(), programmingExerciseId);
            TextPlagiarismResult textPlagiarismResult = new TextPlagiarismResult();
            textPlagiarismResult.convertJPlagResult(jPlagResult, programmingExercise);

            log.info("JPlag programming comparison done in {}", TimeLogUtil.formatDurationFrom(start));
            plagiarismWebsocketService.notifyInstructorAboutPlagiarismState(topic, PlagiarismCheckState.COMPLETED, List.of());
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
     * @param minimumSize           consider only submissions whose number of lines in diff to template is greater or equal to this value
     * @return a zip file that can be returned to the client
     */
    public File checkPlagiarismWithJPlagReport(long programmingExerciseId, float similarityThreshold, int minimumScore, int minimumSize) {
        long start = System.nanoTime();

        final var programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(programmingExerciseId);
        JPlagResult result = computeJPlagResult(programmingExercise, similarityThreshold, minimumScore, minimumSize);

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
    private JPlagResult computeJPlagResult(ProgrammingExercise programmingExercise, float similarityThreshold, int minimumScore, int minimumSize) {
        long programmingExerciseId = programmingExercise.getId();
        final var targetPath = fileService.getTemporaryUniqueSubfolderPath(repoDownloadClonePath, 60);
        List<ProgrammingExerciseParticipation> participations = findStudentParticipationsForComparison(programmingExercise, minimumScore);
        log.info("Download repositories for JPlag for programming exercise {} to compare {} participations", programmingExerciseId, participations.size());

        if (participations.size() < 2) {
            throw new BadRequestAlertException("Insufficient amount of valid and long enough submissions available for comparison", "Plagiarism Check", "notEnoughSubmissions");
        }

        List<Repository> repositories = downloadRepositories(programmingExercise, participations, targetPath.toString(), minimumSize);
        log.info("Downloading repositories done for programming exercise {}", programmingExerciseId);

        final var projectKey = programmingExercise.getProjectKey();
        final var repoFolder = targetPath.resolve(projectKey).toFile();
        final var programmingLanguage = getJPlagProgrammingLanguage(programmingExercise);
        final var templateRepoName = uriService.getRepositorySlugFromRepositoryUri(programmingExercise.getTemplateParticipation().getVcsRepositoryUri());

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

        JPlagResult result;
        try {
            result = JPlag.run(options);
        }
        catch (Exception e) {
            // Handling small or invalid base codes
            log.error(e.getMessage(), e);
            log.warn("Retrying JPlag Plagiarism Check without BaseCode");
            try {
                options = options.withBaseCodeSubmissionDirectory(null);
                result = JPlag.run(options);
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
     * Generates a JPlag report and zips it.
     *
     * @param jPlagResult         The JPlag result
     * @param programmingExercise the programming exercise
     * @return the zip file
     */
    public File generateJPlagReportZip(JPlagResult jPlagResult, ProgrammingExercise programmingExercise) {
        final var targetPath = fileService.getTemporaryUniqueSubfolderPath(repoDownloadClonePath, 5);
        final var reportFolder = targetPath.resolve(programmingExercise.getProjectKey() + "-JPlag-Report.zip");

        try {
            // Create directories.
            Files.createDirectories(targetPath);

            // Write JPlag report result to the file.
            log.info("Write JPlag report into folder {} and zip it", reportFolder);

            ReportObjectFactory reportObjectFactory = new ReportObjectFactory(reportFolder.toFile());
            reportObjectFactory.createAndSaveReport(jPlagResult);
            // JPlag automatically zips the report
        }
        catch (IOException e) {
            log.error("Failed to write JPlag report to file: {}", reportFolder, e);
        }
        fileService.schedulePathForDeletion(reportFolder.toAbsolutePath(), 1);
        return reportFolder.toFile();
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
            case JAVA -> new JavaLanguage();
            case C -> new CLanguage();
            case PYTHON -> new PythonLanguage();
            case SWIFT -> new SwiftLanguage();
            case KOTLIN -> new KotlinLanguage();
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
    public List<ProgrammingExerciseParticipation> findStudentParticipationsForComparison(ProgrammingExercise programmingExercise, int minimumScore) {
        long start = System.nanoTime();
        var studentParticipations = studentParticipationRepository.findAllForPlagiarism(programmingExercise.getId());
        log.info("findAllForPlagiarism took {}", TimeLogUtil.formatDurationFrom(start));

        return studentParticipations.parallelStream().filter(participation -> !participation.isPracticeMode())
                .filter(participation -> participation instanceof ProgrammingExerciseStudentParticipation).filter(plagiarismService.filterForStudents())
                .map(participation -> (ProgrammingExerciseParticipation) participation).filter(participation -> participation.getVcsRepositoryUri() != null)
                .filter(filterParticipationMinimumScore(minimumScore)).toList();
    }

    private Optional<Repository> cloneTemplateRepository(ProgrammingExercise programmingExercise, String targetPath) {
        try {
            var templateRepo = gitService.getOrCheckoutRepository(programmingExercise.getTemplateParticipation(), targetPath);
            gitService.resetToOriginHead(templateRepo); // start with clean state
            return Optional.of(templateRepo);
        }
        catch (GitException | GitAPIException ex) {
            log.error("Clone template repository {} in exercise '{}' did not work as expected: {}", programmingExercise.getTemplateParticipation().getVcsRepositoryUri(),
                    programmingExercise.getTitle(), ex.getMessage());
            return Optional.empty();
        }
    }

    private boolean shouldAddRepo(int minimumSize, Repository repo, Optional<Repository> templateRepo) {
        if (templateRepo.isEmpty()) {
            return true;
        }

        var diffToTemplate = programmingExerciseGitDiffReportService.calculateNumberOfDiffLinesBetweenRepos(repo.getRemoteRepositoryUri(), repo.getLocalPath(),
                templateRepo.get().getRemoteRepositoryUri(), templateRepo.get().getLocalPath());
        return diffToTemplate >= minimumSize;
    }

    private List<Repository> downloadRepositories(ProgrammingExercise programmingExercise, List<ProgrammingExerciseParticipation> participations, String targetPath,
            int minimumSize) {
        // Used for sending progress notifications
        var topic = plagiarismWebsocketService.getProgrammingExercisePlagiarismCheckTopic(programmingExercise.getId());

        int maxRepositories = participations.size() + 1;
        List<Repository> downloadedRepositories = new ArrayList<>();

        plagiarismWebsocketService.notifyInstructorAboutPlagiarismState(topic, PlagiarismCheckState.RUNNING, List.of("Downloading repositories: 0/" + maxRepositories));
        var templateRepo = cloneTemplateRepository(programmingExercise, targetPath);
        templateRepo.ifPresent(downloadedRepositories::add);

        participations.parallelStream().forEach(participation -> {
            try {
                var progressMessage = "Downloading repositories: " + (downloadedRepositories.size() + 1) + "/" + maxRepositories;
                plagiarismWebsocketService.notifyInstructorAboutPlagiarismState(topic, PlagiarismCheckState.RUNNING, List.of(progressMessage));

                Repository repo = gitService.getOrCheckoutRepositoryForJPlag(participation, targetPath);
                gitService.resetToOriginHead(repo); // start with clean state

                if (shouldAddRepo(minimumSize, repo, templateRepo)) {
                    downloadedRepositories.add(repo);
                }
                else {
                    deleteTempLocalRepository(repo);
                }
            }
            catch (GitException | GitAPIException | InvalidPathException ex) {
                log.error("Clone student repository {} in exercise '{}' did not work as expected: {}", participation.getVcsRepositoryUri(), programmingExercise.getTitle(),
                        ex.getMessage());
            }
        });

        return downloadedRepositories;
    }
}
