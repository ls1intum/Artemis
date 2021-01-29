package de.tum.in.www1.artemis.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.validation.constraints.NotNull;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathException;
import javax.xml.xpath.XPathFactory;

import jplag.ExitException;
import jplag.JPlag;
import jplag.JPlagOptions;
import jplag.JPlagResult;
import jplag.options.LanguageOption;
import jplag.reporting.Report;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.Repository;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.VcsRepositoryUrl;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextPlagiarismResult;
import de.tum.in.www1.artemis.exception.GitException;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.util.TimeLogUtil;
import de.tum.in.www1.artemis.web.rest.dto.RepositoryExportOptionsDTO;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

@Service
public class ProgrammingExerciseExportService {

    private final Logger log = LoggerFactory.getLogger(ProgrammingExerciseExportService.class);

    // The downloaded repos should be cloned into another path in order to not interfere with the repo used by the student
    @Value("${artemis.repo-download-clone-path}")
    private String repoDownloadClonePath;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final FileService fileService;

    private final GitService gitService;

    private final ZipFileService zipFileService;

    private final UrlService urlService;

    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());

    public ProgrammingExerciseExportService(ProgrammingExerciseRepository programmingExerciseRepository, StudentParticipationRepository studentParticipationRepository,
            FileService fileService, GitService gitService, ZipFileService zipFileService, UrlService urlService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.fileService = fileService;
        this.gitService = gitService;
        this.zipFileService = zipFileService;
        this.urlService = urlService;
    }

    /**
     * Archives a programming exercise by creating a zip file. The zip file includes all student, template, solution,
     * and tests repositories.
     *
     * @param exercise              the programming exercise
     * @param studentParticipations the student participations
     * @param pathToStoreZipFile   The path to a directory that will be used to store the zipped programming exercise.
     * @return the path to the zip file
     */
    public Path archiveProgrammingExercise(ProgrammingExercise exercise, List<ProgrammingExerciseStudentParticipation> studentParticipations, String pathToStoreZipFile) {
        // Export student repositories
        var zippedStudentRepos = exportStudentRepositories(exercise.getId(), studentParticipations, new RepositoryExportOptionsDTO());

        // Export the template, solution, and tests repositories
        var zippedTemplateRepo = exportInstructorRepositoryForExercise(exercise.getId(), RepositoryType.TEMPLATE);
        var zippedSolutionRepo = exportInstructorRepositoryForExercise(exercise.getId(), RepositoryType.SOLUTION);
        var zippedTestsRepo = exportInstructorRepositoryForExercise(exercise.getId(), RepositoryType.TESTS);
        var zipFilePaths = List.of(zippedStudentRepos.toPath(), zippedTemplateRepo.toPath(), zippedSolutionRepo.toPath(), zippedTestsRepo.toPath());

        try {
            // Zip the student and instructor repos together.
            var pathToZippedExercise = Path.of(pathToStoreZipFile, exercise.getShortName());
            zipFileService.createZipFile(pathToZippedExercise, zipFilePaths);
            return pathToZippedExercise;
        }
        catch (IOException e) {
            log.info("Failed to export programming exercise {}: {}", exercise.getId(), e.getMessage());
            return null;
        }
        finally {
            // Delete the zipped repo files since we don't need those anymore.
            zipFilePaths.forEach(zipFilePath -> fileService.scheduleForDeletion(zipFilePath, 5));
        }
    }

    /**
     * Exports a repository available for an instructor/tutor for a given programming exercise. This can be a template,
     * solution, or tests repository
     *
     * @param exerciseId       The id of the programming exercise that has the repository
     * @param repositoryType the type of repository to export
     * @return a zipped file
     */
    public File exportInstructorRepositoryForExercise(long exerciseId, RepositoryType repositoryType) {
        var exerciseOrEmpty = programmingExerciseRepository.findWithTemplateAndSolutionParticipationById(exerciseId);
        if (exerciseOrEmpty.isEmpty()) {
            log.info("Cannot export instructor repository because the exercise of id {} was not found", exerciseId);
            return null;
        }

        var exercise = exerciseOrEmpty.get();
        log.info("Request to export instructor repository of type " + repositoryType.getName() + " of programming exercise " + exercise + " with title '" + exercise.getTitle()
                + "'");

        // Construct the name of the zip file
        String courseShortName = exercise.getCourseViaExerciseGroupOrCourseMember().getShortName();
        String zippedRepoName = courseShortName + "-" + exercise.getShortName();

        // Get the url to the repository and zip it.
        var repositoryUrl = exercise.getRepositoryURL(repositoryType);
        Path zippedRepo = createZipForRepository(repositoryUrl, zippedRepoName);

        // Delete all files
        var repoProjectPath = fileService.getUniquePathString(repoDownloadClonePath);
        deleteReposDownloadProjectRootDirectory(exercise, repoProjectPath);

        if (zippedRepo == null) {
            log.warn("The zip file could not be created. Ignoring the request to export repositories for exercise " + exercise.getTitle());
            return null;
        }

        fileService.scheduleForDeletion(zippedRepo, 5);
        return new File(zippedRepo.toString());
    }

    /**
     * Get participations of programming exercises of a requested list of students packed together in one zip file.
     *
     * @param programmingExerciseId   the id of the exercise entity
     * @param participations          participations that should be exported
     * @param repositoryExportOptions the options that should be used for the export
     * @return a zip file containing all requested participations
     */
    public File exportStudentRepositories(long programmingExerciseId, @NotNull List<ProgrammingExerciseStudentParticipation> participations,
            RepositoryExportOptionsDTO repositoryExportOptions) {
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(programmingExerciseId)
                .get();

        if (repositoryExportOptions.isExportAllParticipants()) {
            log.info("Request to export all student or team repositories of programming exercise {} with title '{}'", programmingExerciseId, programmingExercise.getTitle());
        }
        else {
            log.info("Request to export the repositories of programming exercise {} with title '{}' of the following students or teams: {}", programmingExerciseId,
                    programmingExercise.getTitle(), participations.stream().map(StudentParticipation::getParticipantIdentifier).collect(Collectors.joining(", ")));
        }

        List<Path> zippedRepos = Collections.synchronizedList(new ArrayList<>());
        participations.parallelStream().forEach(participation -> {
            var zippedRepo = createZipForRepositoryWithParticipation(programmingExercise, participation, repositoryExportOptions);
            if (zippedRepo != null) {
                zippedRepos.add(zippedRepo);
            }
        });

        // delete project root folder
        final var targetPath = fileService.getUniquePathString(repoDownloadClonePath);
        deleteReposDownloadProjectRootDirectory(programmingExercise, targetPath);

        // Create a zip folder containing the zipped repositories.
        return createZipWithAllRepositories(programmingExercise, zippedRepos);
    }

    private Path createZipForRepository(VcsRepositoryUrl repositoryUrl, String zipFilename) {
        var repoProjectPath = fileService.getUniquePathString(repoDownloadClonePath);
        Repository repository = null;
        Path zippedRepo = null;
        try {
            // Checkout the repository
            repository = gitService.getOrCheckoutRepository(repositoryUrl, repoProjectPath, true);
            gitService.resetToOriginMaster(repository);

            // Zip it
            zippedRepo = gitService.zipRepository(repository.getLocalPath(), zipFilename, repoProjectPath);

            // if repository is not closed, it causes weird IO issues when trying to delete the repository again
            // java.io.IOException: Unable to delete file: ...\.git\objects\pack\...
            repository.close();
            return zippedRepo;
        }
        catch (InterruptedException | GitAPIException | IOException ex) {
            log.error("Zip repository {} did not work as expected: {}", repositoryUrl.getURL(), ex.getMessage());
            return null;
        }
        finally {
            deleteTempLocalRepository(repository);
        }
    }

    /**
     * Creates one single zip archive containing all zipped repositories found under the given paths
     *
     * @param programmingExercise The programming exercise to which all repos belong to
     * @param pathsToZippedRepos  The paths to all zipped repositories
     * @return the zip file
     */
    private File createZipWithAllRepositories(ProgrammingExercise programmingExercise, List<Path> pathsToZippedRepos) {
        if (pathsToZippedRepos.isEmpty()) {
            log.warn("The zip file could not be created. Ignoring the request to export repositories for exercise " + programmingExercise.getTitle());
            return null;
        }

        try {
            log.debug("Create zip file for {} repositorie(s) of programming exercise: {}", pathsToZippedRepos.size(), programmingExercise.getTitle());

            String filename = programmingExercise.getCourseViaExerciseGroupOrCourseMember().getShortName() + "-" + programmingExercise.getShortName() + "-"
                    + System.currentTimeMillis() + ".zip";
            Path zipFilePath = Paths.get(pathsToZippedRepos.get(0).getParent().toString(), filename);
            zipFileService.createZipFile(zipFilePath, pathsToZippedRepos);
            fileService.scheduleForDeletion(zipFilePath, 5);
            return new File(zipFilePath.toString());
        }
        catch (IOException ex) {
            log.error("Creating zip file for programming exercise {} did not work correctly: {} ", programmingExercise.getTitle(), ex.getMessage());
            return null;
        }
        finally {
            // we do some cleanup here to prevent future errors with file handling
            deleteTempZipRepoFiles(pathsToZippedRepos);
        }
    }

    /**
     * Checks out the repository for the given participation, zips it and adds the path to the given list of already
     * zipped repos.
     *
     * @param programmingExercise     The programming exercise for the participation
     * @param participation           The participation, for which the repository should get zipped
     * @param repositoryExportOptions The options, that should get applied to the zipeed repo
     * @return The checked out and zipped repository
     */
    private Path createZipForRepositoryWithParticipation(final ProgrammingExercise programmingExercise, final ProgrammingExerciseStudentParticipation participation,
            final RepositoryExportOptionsDTO repositoryExportOptions) {
        if (participation.getVcsRepositoryUrl() == null) {
            log.warn("Ignore participation " + participation.getId() + " for export, because its repository URL is null");
            return null;
        }
        final var targetPath = fileService.getUniquePathString(repoDownloadClonePath);
        Repository repository = null;
        try {
            // Checkout the repository
            repository = gitService.getOrCheckoutRepository(participation, targetPath);
            gitService.resetToOriginMaster(repository);

            if (repositoryExportOptions.isFilterLateSubmissions() && repositoryExportOptions.getFilterLateSubmissionsDate() != null) {
                filterLateSubmissions(repositoryExportOptions.getFilterLateSubmissionsDate(), participation, repository);
            }

            if (repositoryExportOptions.isAddParticipantName()) {
                log.debug("Adding student or team name to participation {}", participation.toString());
                addParticipantIdentifierToProjectName(repository, programmingExercise, participation);
            }

            if (repositoryExportOptions.isCombineStudentCommits()) {
                log.debug("Combining commits for participation {}", participation.toString());
                gitService.combineAllStudentCommits(repository, programmingExercise);
            }

            if (repositoryExportOptions.isNormalizeCodeStyle()) {
                try {
                    log.debug("Normalizing code style for participation {}", participation.toString());
                    fileService.normalizeLineEndingsDirectory(repository.getLocalPath().toString());
                    fileService.convertToUTF8Directory(repository.getLocalPath().toString());
                }
                catch (Exception ex) {
                    log.warn("Cannot normalize code style in the repository " + repository.getLocalPath() + " due to the following exception: " + ex.getMessage());
                }
            }

            log.debug("Create temporary zip file for repository " + repository.getLocalPath().toString());
            Path zippedRepoFile = gitService.zipRepositoryWithParticipation(repository, targetPath, repositoryExportOptions.isHideStudentNameInZippedFolder());

            // if repository is not closed, it causes weird IO issues when trying to delete the repository again
            // java.io.IOException: Unable to delete file: ...\.git\objects\pack\...
            repository.close();

            return zippedRepoFile;

        }
        catch (IOException | GitException | GitAPIException | InterruptedException ex) {
            log.error("export student repository " + participation.getVcsRepositoryUrl() + " in exercise '" + programmingExercise.getTitle() + "' did not work as expected: "
                    + ex.getMessage());
            return null;
        }
        finally {
            deleteTempLocalRepository(repository);
        }
    }

    /**
     * Delete all temporary zipped repositories created during export
     *
     * @param pathsToZipeedRepos A list of all paths to the zip files, that should be deleted
     */
    private void deleteTempZipRepoFiles(List<Path> pathsToZipeedRepos) {
        log.debug("Delete all temporary zip repo files");
        // delete the temporary zipped repo files
        for (Path zippedRepoFile : pathsToZipeedRepos) {
            try {
                Files.delete(zippedRepoFile);
            }
            catch (Exception ex) {
                log.warn("Could not delete file " + zippedRepoFile + ". Error message: " + ex.getMessage());
            }
        }
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
        log.info("Download repositories for JPlag programming comparison with " + numberOfParticipations + " participations");

        final var targetPath = fileService.getUniquePathString(repoDownloadClonePath);
        List<ProgrammingExerciseParticipation> participations = studentParticipationsForComparison(programmingExercise, minimumScore);

        if (participations.size() < 2) {
            log.info("Insufficient amount of submissions for plagiarism detection. Return empty result.");
            return new TextPlagiarismResult();
        }

        List<Repository> repositories = downloadRepositories(programmingExercise, participations, targetPath);
        log.info("Downloading repositories done");

        final var projectKey = programmingExercise.getProjectKey();
        final var repoFolder = Paths.get(targetPath, projectKey).toString();
        final LanguageOption programmingLanguage = getJPlagProgrammingLanguage(programmingExercise);

        final var templateRepoName = urlService.getRepositorySlugFromRepositoryUrl(programmingExercise.getTemplateParticipation().getVcsRepositoryUrl());

        JPlagOptions options = new JPlagOptions(repoFolder, programmingLanguage);
        options.setBaseCodeSubmissionName(templateRepoName);

        // Important: for large courses with more than 1000 students, we might get more than one million results and 10 million files in the file system due to many 0% results,
        // therefore we limit the results to at least 50% or 0.5 similarity, the passed threshold is between 0 and 100%
        options.setSimilarityThreshold(similarityThreshold);

        log.info("Start JPlag programming comparison");

        JPlag jplag = new JPlag(options);
        JPlagResult result = jplag.run();

        log.info("JPlag programming comparison finished with " + result.getComparisons().size() + " comparisons");

        cleanupResourcesAsync(programmingExercise, repositories, targetPath);

        TextPlagiarismResult textPlagiarismResult = new TextPlagiarismResult(result);
        textPlagiarismResult.setExerciseId(programmingExercise.getId());

        log.info("JPlag programming comparison for " + numberOfParticipations + " participations done in " + TimeLogUtil.formatDurationFrom(start));

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
     * @throws IOException   is thrown for file handling errors
     */
    public File checkPlagiarismWithJPlagReport(long programmingExerciseId, float similarityThreshold, int minimumScore) throws ExitException, IOException {
        long start = System.nanoTime();

        final var programmingExercise = programmingExerciseRepository.findWithAllParticipationsById(programmingExerciseId).get();
        final var numberOfParticipations = programmingExercise.getStudentParticipations().size();

        log.info("Download repositories for JPlag programming comparison with " + numberOfParticipations + " participations");
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
        options.setBaseCodeSubmissionName(templateRepoName);

        // Important: for large courses with more than 1000 students, we might get more than one million results and 10 million files in the file system due to many 0% results,
        // therefore we limit the results to at least 50% or 0.5 similarity, the passed threshold is between 0 and 100%
        options.setSimilarityThreshold(similarityThreshold);

        log.info("Start JPlag programming comparison");
        JPlag jplag = new JPlag(options);
        JPlagResult result = jplag.run();
        log.info("JPlag programming comparison finished with " + result.getComparisons().size() + " comparisons");

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

        log.info("JPlag programming report for " + numberOfParticipations + " participations done in " + TimeLogUtil.formatDurationFrom(start));

        return new File(zipFilePath.toString());
    }

    private void cleanupResourcesAsync(final ProgrammingExercise programmingExercise, final List<Repository> repositories, final String targetPath) {
        executor.schedule(() -> {
            log.info("Will delete local repositories");
            deleteLocalRepositories(repositories);
            // delete project root folder in the repos download folder
            deleteReposDownloadProjectRootDirectory(programmingExercise, targetPath);
            log.info("Delete repositories done");
        }, 10, TimeUnit.SECONDS);
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

    private void deleteLocalRepositories(List<Repository> repositories) {
        repositories.parallelStream().forEach(repository -> {
            var localPath = repository.getLocalPath();
            try {
                deleteTempLocalRepository(repository);
            }
            catch (GitException ex) {
                log.error("delete repository " + localPath + " did not work as expected: " + ex.getMessage());
            }
        });
    }

    private void deleteReposDownloadProjectRootDirectory(ProgrammingExercise programmingExercise, String targetPath) {
        final String projectDirName = programmingExercise.getProjectKey();
        Path projectPath = Paths.get(targetPath, projectDirName);
        try {
            log.info("Delete project root directory " + projectPath.toFile());
            FileUtils.deleteDirectory(projectPath.toFile());
        }
        catch (IOException ex) {
            log.warn("The project root directory '" + projectPath.toString() + "' could not be deleted.", ex);
        }
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
                log.error("clone student repository " + participation.getVcsRepositoryUrl() + " in exercise '" + programmingExercise.getTitle() + "' did not work as expected: "
                        + ex.getMessage());
            }
        });

        // clone the template repo
        try {
            Repository templateRepo = gitService.getOrCheckoutRepository(programmingExercise.getTemplateParticipation(), targetPath);
            gitService.resetToOriginMaster(templateRepo); // start with clean state
            downloadedRepositories.add(templateRepo);
        }
        catch (GitException | GitAPIException | InterruptedException ex) {
            log.error("clone template repository " + programmingExercise.getTemplateParticipation().getVcsRepositoryUrl() + " in exercise '" + programmingExercise.getTitle()
                    + "' did not work as expected: " + ex.getMessage());
        }

        return downloadedRepositories;
    }

    /**
     * Find all studentParticipations of the given exercise for plagiarism comparison.
     *
     * @param programmingExercise ProgrammingExercise to fetcch the participations for
     * @param minimumScore        consider only submissions whose score is greater or equal to this value
     * @return List containing the latest text submission for every participation
     */
    public List<ProgrammingExerciseParticipation> studentParticipationsForComparison(ProgrammingExercise programmingExercise, int minimumScore) {
        var studentParticipations = studentParticipationRepository.findAllWithEagerSubmissionsAndEagerResultsByExerciseId(programmingExercise.getId());

        return studentParticipations.parallelStream().filter(participation -> participation instanceof ProgrammingExerciseParticipation)
                .map(participation -> (ProgrammingExerciseParticipation) participation).filter(participation -> participation.getVcsRepositoryUrl() != null)
                .filter(participation -> {
                    Submission submission = ((StudentParticipation) participation).findLatestSubmission().orElse(null);
                    return minimumScore == 0 || submission != null && submission.getLatestResult() != null && submission.getLatestResult().getScore() != null
                            && submission.getLatestResult().getScore() >= minimumScore;
                }).collect(Collectors.toList());
    }

    /**
     * Deletes the locally checked out repository.
     *
     * @param repository The repository that should get deleted
     */
    private void deleteTempLocalRepository(Repository repository) {
        // we do some cleanup here to prevent future errors with file handling
        // We can always delete the repository as it won't be used by the student (separate path)
        if (repository != null) {
            try {
                log.info("Delete temporary repository " + repository.getLocalPath().toString());
                gitService.deleteLocalRepository(repository);
            }
            catch (Exception ex) {
                log.warn("Could not delete temporary repository " + repository.getLocalPath().toString() + ": " + ex.getMessage());
            }
        }
        else {
            log.error("Cannot delete temp local repository because the passed repository is null");
        }
    }

    /**
     * Filters out all late commits of submissions from the checked out repository of a participation
     *
     * @param submissionDate The submission date (inclusive), after which all submissions should get filtered out
     * @param participation  The participation related to the repository
     * @param repo           The repository for which to filter all late submissions
     */
    private void filterLateSubmissions(ZonedDateTime submissionDate, ProgrammingExerciseStudentParticipation participation, Repository repo) {
        log.debug("Filter late submissions for participation {}", participation.toString());
        Optional<Submission> lastValidSubmission = participation.getSubmissions().stream()
                .filter(s -> s.getSubmissionDate() != null && s.getSubmissionDate().isBefore(submissionDate)).max(Comparator.comparing(Submission::getSubmissionDate));

        gitService.filterLateSubmissions(repo, lastValidSubmission, submissionDate);
    }

    /**
     * Adds the participant identifier (student login or team short name) of the given student participation to the project name in all .project (Eclipse)
     * and pom.xml (Maven) files found in the given repository.
     *
     * @param repository          The repository for which the student id should get added
     * @param programmingExercise The checked out exercise in the repository
     * @param participation       The student participation for the student/team identifier, which should be added.
     */
    public void addParticipantIdentifierToProjectName(Repository repository, ProgrammingExercise programmingExercise, StudentParticipation participation) {
        String participantIdentifier = participation.getParticipantIdentifier();

        // Get all files in repository expect .git files
        List<String> allRepoFiles = listAllFilesInPath(repository.getLocalPath());

        // is Java or Kotlin programming language
        if (programmingExercise.getProgrammingLanguage() == ProgrammingLanguage.JAVA || programmingExercise.getProgrammingLanguage() == ProgrammingLanguage.KOTLIN) {
            // Filter all Eclipse .project files
            List<String> eclipseProjectFiles = allRepoFiles.stream().filter(file -> file.endsWith(".project")).collect(Collectors.toList());

            for (String eclipseProjectFilePath : eclipseProjectFiles) {
                addParticipantIdentifierToEclipseProjectName(repository, participantIdentifier, eclipseProjectFilePath);
            }

            // Filter all pom.xml files
            List<String> pomFiles = allRepoFiles.stream().filter(file -> file.endsWith("pom.xml")).collect(Collectors.toList());
            for (String pomFilePath : pomFiles) {
                addParticipantIdentifierToMavenProjectName(repository, participantIdentifier, pomFilePath);
            }
        }

        try {
            gitService.stageAllChanges(repository);
            gitService.commit(repository, "Add participant identifier (student login or team short name) to project name");
            // if repo is not closed, it causes weird IO issues when trying to delete the repo again
            // java.io.IOException: Unable to delete file: ...\.git\objects\pack\...
            repository.close();
        }
        catch (GitAPIException ex) {
            log.error("Cannot stage or commit to the repository " + repository.getLocalPath() + " due to the following exception: " + ex);
        }
    }

    private void addParticipantIdentifierToMavenProjectName(Repository repo, String participantIdentifier, String pomFilePath) {
        File pomFile = new File(pomFilePath);
        // check if file exists and full file name is pom.xml and not just the file ending.
        if (!pomFile.exists() || !pomFile.getName().equals("pom.xml")) {
            return;
        }

        try {
            // 1- Build the doc from the XML file
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(pomFile.getPath()));
            doc.setXmlStandalone(true);

            // 2- Find the relevant nodes with xpath
            XPath xPath = XPathFactory.newInstance().newXPath();
            Node nameNode = (Node) xPath.compile("/project/name").evaluate(doc, XPathConstants.NODE);
            Node artifactIdNode = (Node) xPath.compile("/project/artifactId").evaluate(doc, XPathConstants.NODE);

            // 3- Append Participant Identifier (student login or team short name) to Project Names
            if (nameNode != null) {
                nameNode.setTextContent(nameNode.getTextContent() + " " + participantIdentifier);
            }
            if (artifactIdNode != null) {
                String artifactId = (artifactIdNode.getTextContent() + "-" + participantIdentifier).replaceAll(" ", "-").toLowerCase();
                artifactIdNode.setTextContent(artifactId);
            }

            // 4- Save the result to a new XML doc
            Transformer xformer = TransformerFactory.newInstance().newTransformer();
            xformer.transform(new DOMSource(doc), new StreamResult(new File(pomFile.getPath())));

        }
        catch (SAXException | IOException | ParserConfigurationException | TransformerException | XPathException ex) {
            log.error("Cannot rename pom.xml file in " + repo.getLocalPath() + " due to the following exception: " + ex);
        }
    }

    private void addParticipantIdentifierToEclipseProjectName(Repository repo, String participantIdentifier, String eclipseProjectFilePath) {
        File eclipseProjectFile = new File(eclipseProjectFilePath);
        // Check if file exists and full file name is .project and not just the file ending.
        if (!eclipseProjectFile.exists() || !eclipseProjectFile.getName().equals(".project")) {
            return;
        }

        try {
            // 1- Build the doc from the XML file
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(eclipseProjectFile.getPath()));
            doc.setXmlStandalone(true);

            // 2- Find the node with xpath
            XPath xPath = XPathFactory.newInstance().newXPath();
            Node nameNode = (Node) xPath.compile("/projectDescription/name").evaluate(doc, XPathConstants.NODE);

            // 3- Append Participant Identifier (student login or team short name) to Project Name
            if (nameNode != null) {
                nameNode.setTextContent(nameNode.getTextContent() + " " + participantIdentifier);
            }

            // 4- Save the result to a new XML doc
            Transformer xformer = TransformerFactory.newInstance().newTransformer();
            xformer.transform(new DOMSource(doc), new StreamResult(new File(eclipseProjectFile.getPath())));

        }
        catch (SAXException | IOException | ParserConfigurationException | TransformerException | XPathException ex) {
            log.error("Cannot rename .project file in " + repo.getLocalPath() + " due to the following exception: " + ex);
        }
    }

    /**
     * Get all files in path except .git files
     *
     * @param path The path for which all file names should be listed
     * @return A list of all file names under the given path
     */
    private List<String> listAllFilesInPath(Path path) {
        List<String> allRepoFiles = null;
        try (Stream<Path> walk = Files.walk(path)) {
            allRepoFiles = walk.filter(Files::isRegularFile).map(Path::toString).filter(s -> !s.contains(".git")).collect(Collectors.toList());
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        return allRepoFiles;
    }
}
