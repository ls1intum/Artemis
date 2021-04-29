package de.tum.in.www1.artemis.service.programming;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
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

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
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
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.exception.GitException;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.service.FileService;
import de.tum.in.www1.artemis.service.ZipFileService;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.web.rest.dto.RepositoryExportOptionsDTO;

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

    public ProgrammingExerciseExportService(ProgrammingExerciseRepository programmingExerciseRepository, StudentParticipationRepository studentParticipationRepository,
            FileService fileService, GitService gitService, ZipFileService zipFileService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.fileService = fileService;
        this.gitService = gitService;
        this.zipFileService = zipFileService;
    }

    /**
     * Export a programming exercise by creating a zip file. The zip file includes all student, template, solution,
     * and tests repositories.
     *
     * @param exercise           the programming exercise
     * @param pathToStoreZipFile The path to a directory that will be used to store the zipped programming exercise.
     * @param exportErrors       List of failures that occurred during the export
     * @return the path to the zip file
     */
    public Path exportProgrammingExercise(ProgrammingExercise exercise, String pathToStoreZipFile, List<String> exportErrors) {
        // Will contain the zipped files. Note that there can be null elements
        // because e.g exportStudentRepositories returns null if student repositories don't
        // exist.
        var zipFiles = new ArrayList<File>();

        // Lazy load student participations and set the export options.
        var studentParticipations = studentParticipationRepository.findByExerciseId(exercise.getId()).stream()
                .map(studentParticipation -> (ProgrammingExerciseStudentParticipation) studentParticipation).collect(Collectors.toList());
        var exportOptions = new RepositoryExportOptionsDTO();
        exportOptions.setHideStudentNameInZippedFolder(false);

        // Export student repositories
        var studentZipFilePaths = exportStudentRepositories(exercise, studentParticipations, exportOptions, exportErrors).stream().filter(Objects::nonNull).map(Path::toFile)
                .collect(Collectors.toList());
        zipFiles.addAll(studentZipFilePaths);

        // Export the template, solution, and tests repositories
        zipFiles.add(exportInstructorRepositoryForExercise(exercise.getId(), RepositoryType.TEMPLATE, exportErrors));
        zipFiles.add(exportInstructorRepositoryForExercise(exercise.getId(), RepositoryType.SOLUTION, exportErrors));
        zipFiles.add(exportInstructorRepositoryForExercise(exercise.getId(), RepositoryType.TESTS, exportErrors));

        // Remove null elements and get the file path of each zip file.
        var zipFilePathsNonNull = zipFiles.stream().filter(Objects::nonNull).map(File::toPath).collect(Collectors.toList());

        try {
            // Zip the student and instructor repos together.
            var timestamp = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-Hmss"));
            var filename = exercise.getCourseViaExerciseGroupOrCourseMember().getShortName() + "-" + exercise.getTitle() + "-" + exercise.getId() + "-" + timestamp + ".zip";
            var pathToZippedExercise = Path.of(pathToStoreZipFile, filename);
            zipFileService.createZipFile(pathToZippedExercise, zipFilePathsNonNull, false);
            return pathToZippedExercise;
        }
        catch (IOException e) {
            var error = "Failed to export programming exercise " + exercise.getId() + " because the zip file " + pathToStoreZipFile + " could not be created: " + e.getMessage();
            log.info(error);
            exportErrors.add(error);
            return null;
        }
        finally {
            // Delete the zipped repo files since we don't need those anymore.
            zipFilePathsNonNull.forEach(zipFilePath -> fileService.scheduleForDeletion(zipFilePath, 1));
        }
    }

    /**
     * Exports a repository available for an instructor/tutor for a given programming exercise. This can be a template,
     * solution, or tests repository
     *
     * @param exerciseId     The id of the programming exercise that has the repository
     * @param repositoryType the type of repository to export
     * @param exportErrors   List of failures that occurred during the export
     * @return a zipped file
     */
    public File exportInstructorRepositoryForExercise(long exerciseId, RepositoryType repositoryType, List<String> exportErrors) {
        var exerciseOrEmpty = programmingExerciseRepository.findWithTemplateAndSolutionParticipationById(exerciseId);
        if (exerciseOrEmpty.isEmpty()) {
            var error = "Failed to export instructor repository " + repositoryType + " because the exercise " + exerciseId + " does not exist.";
            log.info(error);
            exportErrors.add(error);
            return null;
        }

        var exercise = exerciseOrEmpty.get();
        log.info("Request to export instructor repository of type {} of programming exercise {} with title '{}'", repositoryType.getName(), exercise, exercise.getTitle());

        // Construct the name of the zip file
        String courseShortName = exercise.getCourseViaExerciseGroupOrCourseMember().getShortName();
        String zippedRepoName = courseShortName + "-" + exercise.getTitle() + "-" + repositoryType.getName();

        try {
            // Get the url to the repository and zip it.
            var repositoryUrl = exercise.getRepositoryURL(repositoryType);

            // It's not guaranteed that the repository url is defined (old courses).
            if (repositoryUrl == null) {
                var error = "Failed to export instructor repository " + repositoryType + " because the repository url is not defined.";
                log.info(error);
                exportErrors.add(error);
                return null;
            }

            Path zippedRepo = createZipForRepository(repositoryUrl, zippedRepoName);
            if (zippedRepo != null) {
                return new File(zippedRepo.toString());
            }
        }
        catch (IOException | GitAPIException | GitException | InterruptedException ex) {
            var error = "Failed to export instructor repository " + repositoryType + " for programming exercise '" + exercise.getTitle() + "' (id: " + exercise.getId() + ")";
            log.info(error);
            exportErrors.add(error);
        }
        return null;
    }

    /**
     * Get participations of programming exercises of a requested list of students packed together in one zip file.
     *
     * @param programmingExerciseId   the id of the exercise entity
     * @param participations          participations that should be exported
     * @param repositoryExportOptions the options that should be used for the export
     * @return a zip file containing all requested participations
     */
    public File exportStudentRepositoriesToZipFile(long programmingExerciseId, @NotNull List<ProgrammingExerciseStudentParticipation> participations,
            RepositoryExportOptionsDTO repositoryExportOptions) {
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(programmingExerciseId)
                .get();

        var zippedRepos = exportStudentRepositories(programmingExercise, participations, repositoryExportOptions, new ArrayList<>());

        // delete project root folder
        final var targetPath = fileService.getUniquePathString(repoDownloadClonePath);
        deleteReposDownloadProjectRootDirectory(programmingExercise, targetPath);

        // Create a zip folder containing the zipped repositories.
        return createZipWithAllRepositories(programmingExercise, zippedRepos);
    }

    /**
     * Zip the participations of programming exercises of a requested list of students separately.
     *
     * @param programmingExercise     the programming exercise
     * @param participations          participations that should be exported
     * @param repositoryExportOptions the options that should be used for the export
     * @param exportErrors            A list of errors that occured during export (populated by this function)
     * @return List of zip file paths
     */
    public List<Path> exportStudentRepositories(ProgrammingExercise programmingExercise, @NotNull List<ProgrammingExerciseStudentParticipation> participations,
            RepositoryExportOptionsDTO repositoryExportOptions, List<String> exportErrors) {
        var programmingExerciseId = programmingExercise.getId();
        if (repositoryExportOptions.isExportAllParticipants()) {
            log.info("Request to export all student or team repositories of programming exercise {} with title '{}'", programmingExerciseId, programmingExercise.getTitle());
        }
        else {
            log.info("Request to export the repositories of programming exercise {} with title '{}' of the following students or teams: {}", programmingExerciseId,
                    programmingExercise.getTitle(), participations.stream().map(StudentParticipation::getParticipantIdentifier).collect(Collectors.joining(", ")));
        }

        List<Path> zippedRepos = Collections.synchronizedList(new ArrayList<>());
        participations.parallelStream().forEach(participation -> {
            try {
                var zippedRepo = createZipForRepositoryWithParticipation(programmingExercise, participation, repositoryExportOptions);
                if (zippedRepo != null) {
                    zippedRepos.add(zippedRepo);
                }
            }
            catch (IOException e) {
                var error = "Failed to export the student repository with participation: " + participation.getId() + " for programming exercise '" + programmingExercise.getTitle()
                        + "' (id: " + programmingExercise.getId() + ") because the repository couldn't be downloaded. ";
                exportErrors.add(error);
            }
        });
        return zippedRepos;
    }

    /**
     * Creates a zip file with the contents of the git repository. Note that the zip file is deleted in 5 minutes.
     *
     * @param repositoryUrl The url of the repository to zip
     * @param zipFilename   The name of the zip file
     * @return The path to the zip file.
     * @throws IOException if the zip file couldn't be created
     * @throws GitAPIException if the repo couldn't get checked out
     * @throws InterruptedException if the repo couldn't get checked out
     */
    private Path createZipForRepository(VcsRepositoryUrl repositoryUrl, String zipFilename) throws IOException, GitAPIException, GitException, InterruptedException {
        var repoProjectPath = fileService.getUniquePathString(repoDownloadClonePath);
        Repository repository = null;

        try {
            // Checkout the repository
            repository = gitService.getOrCheckoutRepository(repositoryUrl, repoProjectPath, true);
            gitService.resetToOriginMaster(repository);

            // Zip it and return the path to the file
            return gitService.zipRepository(repository.getLocalPath(), zipFilename, repoProjectPath);
        }
        finally {
            deleteTempLocalRepository(repository);
            fileService.scheduleForDirectoryDeletion(Path.of(repoProjectPath), 5);
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
            log.warn("The zip file could not be created. Ignoring the request to export repositories for exercise {}", programmingExercise.getTitle());
            return null;
        }

        try {
            log.debug("Create zip file for {} repositorie(s) of programming exercise: {}", pathsToZippedRepos.size(), programmingExercise.getTitle());

            String filename = programmingExercise.getCourseViaExerciseGroupOrCourseMember().getShortName() + "-" + programmingExercise.getShortName() + "-"
                    + System.currentTimeMillis() + ".zip";
            Path zipFilePath = Paths.get(pathsToZippedRepos.get(0).getParent().toString(), filename);
            zipFileService.createZipFile(zipFilePath, pathsToZippedRepos, false);
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
     * @throws IOException if zip file creation failed
     */
    private Path createZipForRepositoryWithParticipation(final ProgrammingExercise programmingExercise, final ProgrammingExerciseStudentParticipation participation,
            final RepositoryExportOptionsDTO repositoryExportOptions) throws IOException {
        if (participation.getVcsRepositoryUrl() == null) {
            log.warn("Ignore participation {} for export, because its repository URL is null", participation.getId());
            return null;
        }

        String targetPath = null;
        Repository repository = null;
        try {
            // Construct a unique path that will contain repo contents
            targetPath = fileService.getUniquePathString(repoDownloadClonePath);

            // Checkout the repository
            repository = gitService.getOrCheckoutRepository(participation, targetPath);
            if (repository == null) {
                log.warn("Cannot checkout repository for participation id: {}", participation.getId());
                return null;
            }

            gitService.resetToOriginMaster(repository);

            if (repositoryExportOptions.isFilterLateSubmissions() && repositoryExportOptions.getFilterLateSubmissionsDate() != null) {
                filterLateSubmissions(repositoryExportOptions.getFilterLateSubmissionsDate(), participation, repository);
            }

            if (repositoryExportOptions.isAddParticipantName()) {
                log.debug("Adding student or team name to participation {}", participation);
                addParticipantIdentifierToProjectName(repository, programmingExercise, participation);
            }

            if (repositoryExportOptions.isCombineStudentCommits()) {
                log.debug("Combining commits for participation {}", participation);
                gitService.combineAllStudentCommits(repository, programmingExercise);
            }

            if (repositoryExportOptions.isNormalizeCodeStyle()) {
                try {
                    log.debug("Normalizing code style for participation {}", participation);
                    fileService.normalizeLineEndingsDirectory(repository.getLocalPath().toString());
                    fileService.convertToUTF8Directory(repository.getLocalPath().toString());
                }
                catch (IOException ex) {
                    log.warn("Cannot normalize code style in the repository {} due to the following exception: {}", repository.getLocalPath(), ex.getMessage());
                }
            }

            log.debug("Create temporary zip file for repository {}", repository.getLocalPath().toString());
            return gitService.zipRepositoryWithParticipation(repository, targetPath, repositoryExportOptions.isHideStudentNameInZippedFolder());
        }
        catch (InterruptedException | GitAPIException | GitException e) {
            log.error("Cannot create zip for participation id {} because the repo download clone path is invalid.", participation.getId());
            return null;
        }
        finally {
            deleteTempLocalRepository(repository);
            fileService.scheduleForDirectoryDeletion(Path.of(targetPath), 5);
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
                log.warn("Could not delete file {}. Error message: {}", zippedRepoFile, ex.getMessage());
            }
        }
    }

    public void deleteReposDownloadProjectRootDirectory(ProgrammingExercise programmingExercise, String targetPath) {
        final String projectDirName = programmingExercise.getProjectKey();
        Path projectPath = Paths.get(targetPath, projectDirName);
        try {
            log.info("Delete project root directory {}", projectPath.toFile());
            FileUtils.deleteDirectory(projectPath.toFile());
        }
        catch (IOException ex) {
            log.warn("The project root directory '" + projectPath + "' could not be deleted.", ex);
        }
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
        }
        catch (GitAPIException ex) {
            log.error("Cannot stage or commit to the repository " + repository.getLocalPath(), ex);
        }
        finally {
            // if repo is not closed, it causes weird IO issues when trying to delete the repo again
            // java.io.IOException: Unable to delete file: ...\.git\objects\pack\...
            repository.close();
        }
    }

    private void addParticipantIdentifierToMavenProjectName(Repository repo, String participantIdentifier, String pomFilePath) {
        try {
            File pomFile = new File(pomFilePath);
            // check if file exists and full file name is pom.xml and not just the file ending.
            if (!pomFile.exists() || !pomFile.getName().equals("pom.xml")) {
                return;
            }

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
            log.error("Cannot rename pom.xml file in " + repo.getLocalPath(), ex);
        }
    }

    private void addParticipantIdentifierToEclipseProjectName(Repository repo, String participantIdentifier, String eclipseProjectFilePath) {
        try {
            File eclipseProjectFile = new File(eclipseProjectFilePath);
            // Check if file exists and full file name is .project and not just the file ending.
            if (!eclipseProjectFile.exists() || !eclipseProjectFile.getName().equals(".project")) {
                return;
            }

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
            log.error("Cannot rename .project file in " + repo.getLocalPath(), ex);
        }
    }

    /**
     * Get all files in path except .git files
     *
     * @param path The path for which all file names should be listed
     * @return A list of all file names under the given path
     */
    private List<String> listAllFilesInPath(Path path) {
        List<String> allRepoFiles = Collections.emptyList();
        try (Stream<Path> walk = Files.walk(path)) {
            allRepoFiles = walk.filter(Files::isRegularFile).map(Path::toString).filter(s -> !s.contains(".git")).collect(Collectors.toList());
        }
        catch (IOException | SecurityException e) {
            log.error("Cannot list all files in path {}: {}", path, e.getMessage());
        }
        return allRepoFiles;
    }
}
