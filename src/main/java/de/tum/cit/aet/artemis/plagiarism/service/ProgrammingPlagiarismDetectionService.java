package de.tum.cit.aet.artemis.plagiarism.service;

import static de.tum.cit.aet.artemis.plagiarism.service.PlagiarismService.filterParticipationMinimumScore;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.jplag.JPlag;
import de.jplag.JPlagResult;
import de.jplag.Language;
import de.jplag.c.CLanguage;
import de.jplag.clustering.ClusteringOptions;
import de.jplag.cpp.CPPLanguage;
import de.jplag.csharp.CSharpLanguage;
import de.jplag.golang.GoLanguage;
import de.jplag.java.JavaLanguage;
import de.jplag.javascript.JavaScriptLanguage;
import de.jplag.kotlin.KotlinLanguage;
import de.jplag.options.JPlagOptions;
import de.jplag.python3.PythonLanguage;
import de.jplag.reporting.reportobject.ReportObjectFactory;
import de.jplag.rlang.RLanguage;
import de.jplag.rust.RustLanguage;
import de.jplag.swift.SwiftLanguage;
import de.jplag.typescript.TypeScriptLanguage;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.GitException;
import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.core.util.FileUtil;
import de.tum.cit.aet.artemis.core.util.TimeLogUtil;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.plagiarism.config.PlagiarismEnabled;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismCheckState;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismResult;
import de.tum.cit.aet.artemis.plagiarism.service.cache.PlagiarismCacheService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.GitService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseExportService;
import de.tum.cit.aet.artemis.programming.service.UriService;

@Conditional(PlagiarismEnabled.class)
@Lazy
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

    public ProgrammingPlagiarismDetectionService(FileService fileService, ProgrammingExerciseRepository programmingExerciseRepository, PlagiarismService plagiarismService,
            GitService gitService, StudentParticipationRepository studentParticipationRepository, ProgrammingExerciseExportService programmingExerciseExportService,
            PlagiarismWebsocketService plagiarismWebsocketService, PlagiarismCacheService plagiarismCacheService, UriService uriService) {
        this.fileService = fileService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.plagiarismService = plagiarismService;
        this.gitService = gitService;
        this.studentParticipationRepository = studentParticipationRepository;
        this.programmingExerciseExportService = programmingExerciseExportService;
        this.plagiarismWebsocketService = plagiarismWebsocketService;
        this.plagiarismCacheService = plagiarismCacheService;
        this.uriService = uriService;
    }

    /**
     * downloads all repos of the exercise and runs JPlag
     *
     * @param programmingExerciseId the id of the programming exercises which should be checked
     * @param similarityThreshold   ignore comparisons whose similarity is below this threshold (in % between 0 and 100)
     * @param minimumScore          consider only submissions whose score is greater or equal to this value
     * @param minimumSize           consider only submissions whose number of lines in diff to template is greater or equal to this value
     * @return the text plagiarism result container with up to 500 comparisons with the highest similarity values
     * @throws IOException is thrown for file handling errors
     */
    public PlagiarismResult checkPlagiarism(long programmingExerciseId, float similarityThreshold, int minimumScore, int minimumSize) throws IOException {
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
                PlagiarismResult textPlagiarismResult = new PlagiarismResult();
                textPlagiarismResult.setExercise(programmingExercise);
                textPlagiarismResult.setSimilarityDistribution(new int[0]);

                log.info("Finished programmingExerciseExportService.checkPlagiarism call for {} comparisons in {}", textPlagiarismResult.getComparisons().size(),
                        TimeLogUtil.formatDurationFrom(start));
                log.info("Finished plagiarismResultRepository.savePlagiarismResultAndRemovePrevious call in {}", TimeLogUtil.formatDurationFrom(start));
                return textPlagiarismResult;
            }

            log.info("JPlag programming comparison finished with {} comparisons for programming exercise {}", jPlagResult.getAllComparisons().size(), programmingExerciseId);
            PlagiarismResult textPlagiarismResult = new PlagiarismResult();
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
    @NonNull
    private JPlagResult computeJPlagResult(ProgrammingExercise programmingExercise, float similarityThreshold, int minimumScore, int minimumSize) {
        // TODO: Move minimumSize to configuration parameter in next refactoring
        long programmingExerciseId = programmingExercise.getId();
        final var targetPath = fileService.getTemporaryUniqueSubfolderPath(repoDownloadClonePath, 60);
        List<ProgrammingExerciseParticipation> participations = findStudentParticipationsForComparison(programmingExercise, minimumScore);
        log.info("Download repositories for JPlag for programming exercise {} to compare {} participations", programmingExerciseId, participations.size());

        if (participations.size() < 2) {
            throw new BadRequestAlertException("Insufficient amount of valid submissions available for comparison after applying minimum score filter", "Plagiarism Check",
                    "notEnoughSubmissions");
        }

        List<Repository> repositories = downloadRepositories(programmingExercise, participations, targetPath, minimumSize);
        log.info("Downloading repositories done for programming exercise {}", programmingExerciseId);

        // Check if we have enough repositories after filtering
        if (repositories.size() < 2) {
            throw new BadRequestAlertException(
                    "Insufficient amount of valid and long enough submissions available for comparison after applying minimum score and minimum size filters", "Plagiarism Check",
                    "notEnoughSubmissions");
        }

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

    /**
     * * Returns the JPlag programming language based on the programming exercise's programming language.
     *
     * @param programmingExercise The programming exercise to get the programming language for
     * @return the JPlag Language object corresponding to the programming exercise's programming language
     * @throws BadRequestAlertException if the programming language is not supported for plagiarism check
     */
    @NonNull
    public static Language getJPlagProgrammingLanguage(ProgrammingExercise programmingExercise) {
        return switch (programmingExercise.getProgrammingLanguage()) {
            case C -> new CLanguage();
            case C_PLUS_PLUS -> new CPPLanguage();
            case C_SHARP -> new CSharpLanguage();
            case GO -> new GoLanguage();
            case JAVA -> new JavaLanguage();
            case JAVASCRIPT -> new JavaScriptLanguage();
            case KOTLIN -> new KotlinLanguage();
            case PYTHON -> new PythonLanguage();
            case R -> new RLanguage();
            case RUST -> new RustLanguage();
            case SWIFT -> new SwiftLanguage();
            case TYPESCRIPT -> new TypeScriptLanguage();
            case EMPTY, PHP, DART, HASKELL, ASSEMBLER, OCAML, SQL, MATLAB, BASH, VHDL, RUBY, POWERSHELL, ADA -> throw new BadRequestAlertException(
                    "Programming language " + programmingExercise.getProgrammingLanguage() + " not supported for plagiarism check.", "ProgrammingExercise", "notSupported");
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

    private Optional<Repository> cloneTemplateRepository(ProgrammingExercise programmingExercise, Path targetPath) {
        try {
            var templateRepo = gitService.getOrCheckoutRepository(programmingExercise.getTemplateParticipation(), targetPath, false);
            gitService.resetToOriginHead(templateRepo); // start with clean state
            return Optional.of(templateRepo);
        }
        catch (GitException | GitAPIException ex) {
            log.error("Clone template repository {} in exercise '{}' did not work as expected: {}", programmingExercise.getTemplateParticipation().getVcsRepositoryUri(),
                    programmingExercise.getTitle(), ex.getMessage());
            return Optional.empty();
        }
    }

    private List<Repository> downloadRepositories(ProgrammingExercise programmingExercise, List<ProgrammingExerciseParticipation> participations, Path targetPath,
            int minimumTokenSize) {
        // Used for sending progress notifications
        var topic = plagiarismWebsocketService.getProgrammingExercisePlagiarismCheckTopic(programmingExercise.getId());

        int maxRepositories = participations.size() + 1;
        List<Repository> downloadedRepositories = new ArrayList<>();

        plagiarismWebsocketService.notifyInstructorAboutPlagiarismState(topic, PlagiarismCheckState.RUNNING, List.of("Downloading repositories: 0/" + maxRepositories));
        var templateRepo = cloneTemplateRepository(programmingExercise, targetPath);
        templateRepo.ifPresent(downloadedRepositories::add);

        List<Repository> studentRepositories = participations.parallelStream().map(participation -> {
            try {
                Repository repo = gitService.getOrCheckoutRepositoryForJPlag(participation, targetPath);
                gitService.resetToOriginHead(repo); // start with clean state

                // Check if repository meets minimum size requirement
                if (minimumTokenSize > 0) {
                    boolean meetsMinimumSize = meetsMinimumSize(repo, programmingExercise, minimumTokenSize);

                    if (meetsMinimumSize) {
                        log.debug("Repository {} meets minimum size requirement ({} tokens), including in plagiarism check", participation.getVcsRepositoryUri(), minimumTokenSize);
                        return repo;
                    }
                    else {
                        log.info("Repository {} does not meet minimum size requirement ({} tokens), excluding from plagiarism check", participation.getVcsRepositoryUri(),
                                minimumTokenSize);
                        // Clean up the repository since we won't use it
                        try {
                            deleteTempLocalRepository(repo);
                        }
                        catch (Exception e) {
                            log.warn("Failed to delete filtered repository {}: {}", repo.getLocalPath(), e.getMessage());
                        }
                        return null;
                    }
                }
                else {
                    // If no minimum size is specified, include all repositories
                    return repo;
                }
            }
            catch (GitException | GitAPIException | InvalidPathException ex) {
                log.error("Clone student repository {} in exercise '{}' did not work as expected: {}", participation.getVcsRepositoryUri(), programmingExercise.getTitle(),
                        ex.getMessage());
                return null;
            }
        }).filter(Objects::nonNull).toList();

        downloadedRepositories.addAll(studentRepositories);

        // Update progress message
        var progressMessage = "Processing repositories: " + downloadedRepositories.size() + " valid out of " + maxRepositories + " total";
        plagiarismWebsocketService.notifyInstructorAboutPlagiarismState(topic, PlagiarismCheckState.RUNNING, List.of(progressMessage));

        log.info("Downloaded and filtered {} repositories out of {} participations for exercise {} (minimum token size: {} tokens)", downloadedRepositories.size(),
                participations.size(), programmingExercise.getId(), minimumTokenSize);

        return downloadedRepositories;
    }

    /**
     * Checks if a repository meets the minimum size requirement by counting tokens in relevant files.
     * Returns true as soon as the minimum token count is reached across all files.
     * Returns true in case of any errors to be INCLUSIVE. I/O errors should not prevent plagiarism check.
     *
     * @param repository          The repository to check
     * @param programmingExercise The programming exercise
     * @param minimumTokenSize    The minimum number of tokens required
     * @return true if the repository meets the minimum size requirement or if there are any errors
     */
    public static boolean meetsMinimumSize(Repository repository, ProgrammingExercise programmingExercise, int minimumTokenSize) {
        try {
            Path repoPath = repository.getLocalPath();
            if (!Files.exists(repoPath) || !Files.isDirectory(repoPath)) {
                log.warn("Repository path does not exist or is not a directory: {}", repoPath);
                return true;
            }

            // Get file extensions for the programming language for filtering
            Set<String> fileExtensions = programmingExercise.getProgrammingLanguage().getFileExtensions().stream().map(ext -> "." + ext).collect(Collectors.toSet());

            try (Stream<Path> paths = Files.walk(repoPath)) {
                List<Path> relevantFiles = paths.filter(Files::isRegularFile).filter(path -> {
                    // Only consider files with the correct file extension
                    String fileName = path.getFileName().toString().toLowerCase();
                    return fileExtensions.stream().anyMatch(fileName::endsWith);
                }).toList();

                int totalTokenCount = 0;
                for (Path path : relevantFiles) {
                    totalTokenCount = FileUtil.countTokensInFile(path, minimumTokenSize, totalTokenCount);
                    if (totalTokenCount >= minimumTokenSize) {
                        // Return true as soon as the minimum token size is reached
                        return true;
                    }
                }

                // Return false if minimum token size was not reached after checking all files
                return false;
            }
        }
        catch (IOException e) {
            // Check for plagiarism if there is an error reading the repository
            log.warn("Failed to check repository token count {}: {}", repository.getLocalPath(), e.getMessage());
            return true;
        }
    }
}
