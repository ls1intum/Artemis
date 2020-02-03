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
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.exception.GitException;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.web.rest.dto.RepositoryExportOptionsDTO;

@Service
public class ProgrammingExerciseExportService {

    private final Logger log = LoggerFactory.getLogger(ProgrammingExerciseExportService.class);

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final FileService fileService;

    private final GitService gitService;

    public ProgrammingExerciseExportService(ProgrammingExerciseRepository programmingExerciseRepository, FileService fileService, GitService gitService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.fileService = fileService;
        this.gitService = gitService;
    }

    // The downloaded repos should be cloned into another path in order to not interfere with the repo used by the student
    @Value("${artemis.repo-download-clone-path}")
    private String REPO_DOWNLOAD_CLONE_PATH;

    /**
     * Get participations of coding exercises of a requested list of students packed together in one zip file.
     *
     * @param programmingExerciseId the id of the exercise entity
     * @param participations participations that should be exported
     * @param repositoryExportOptions the options that should be used for the export
     * @return a zip file containing all requested participations
     */
    public File exportStudentRepositories(long programmingExerciseId, @NotNull List<ProgrammingExerciseStudentParticipation> participations,
            RepositoryExportOptionsDTO repositoryExportOptions) {
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findWithTemplateParticipationAndSolutionParticipationById(programmingExerciseId).get();

        if (repositoryExportOptions.isExportAllStudents()) {
            log.info("Request to export all student repositories of programming exercise " + programmingExerciseId + " with title '" + programmingExercise.getTitle() + "'");
        }
        else {
            log.info("Request to export the repositories of programming exercise " + programmingExerciseId + " with title '" + programmingExercise.getTitle()
                    + "' of the following students: " + participations.stream().map(p -> p.getStudent().getLogin()).collect(Collectors.joining(", ")));
        }

        List<Path> zippedRepoFiles = new ArrayList<>();
        for (ProgrammingExerciseStudentParticipation participation : participations) {
            Repository repo = null;
            try {
                if (participation.getRepositoryUrlAsUrl() == null) {
                    log.warn("Ignore participation " + participation.getId() + " for export, because its repository URL is null");
                    continue;
                }
                repo = zipRepositoryForParticipation(programmingExercise, participation, repositoryExportOptions, zippedRepoFiles);
            }
            catch (IOException | GitException | GitAPIException | InterruptedException ex) {
                log.error("export student repository " + participation.getRepositoryUrlAsUrl() + " in exercise '" + programmingExercise.getTitle() + "' did not work as expected: "
                        + ex.getMessage());
            }
            finally {
                deleteTempLocalRepository(participation, repo);
            }
        }

        if (zippedRepoFiles.isEmpty()) {
            log.warn("The zip file could not be created. Ignoring the request to export repositories for exercise " + programmingExercise.getTitle());
            return null;
        }

        try {
            // create a large zip file with all zipped repos and provide it for download
            return createZipWithAllRepositories(programmingExercise, zippedRepoFiles);
        }
        catch (IOException ex) {
            log.error("Export students repositories for exercise '" + programmingExercise.getTitle() + "' did not work as expected: " + ex.getMessage());
        }
        finally {
            // we do some cleanup here to prevent future errors with file handling
            deleteTempZipRepoFiles(zippedRepoFiles);
        }

        return null;
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
     * Checks out the repository fo the given participation, zips it and adds the path to the given list of already
     * zipped repos.
     *
     * @param programmingExercise The programming exercise for the participation
     * @param participation The participation, for which the repository should get zipped
     * @param repositoryExportOptions The options, that should get applied to the zipeed repo
     * @param pathsToZippedRepos A list of already zipped repos. The path of the newly zip file will get added to this list
     * @return The checked out and zipeed repository
     * @throws GitAPIException If something went wrong checking out the repo
     * @throws InterruptedException
     * @throws IOException
     */
    private Repository zipRepositoryForParticipation(final ProgrammingExercise programmingExercise, final ProgrammingExerciseStudentParticipation participation,
            final RepositoryExportOptionsDTO repositoryExportOptions, List<Path> pathsToZippedRepos) throws GitAPIException, InterruptedException, IOException {
        final var repo = gitService.getOrCheckoutRepository(participation, REPO_DOWNLOAD_CLONE_PATH);
        gitService.resetToOriginMaster(repo); // start with clean state

        if (repositoryExportOptions.isFilterLateSubmissions() && repositoryExportOptions.getFilterLateSubmissionsDate() != null) {
            filterLateSubmissions(repositoryExportOptions.getFilterLateSubmissionsDate(), participation, repo);
        }

        if (repositoryExportOptions.isAddStudentName()) {
            log.debug("Adding student name to participation {}", participation.toString());
            addStudentIdToProjectName(repo, programmingExercise, participation);
        }

        if (repositoryExportOptions.isCombineStudentCommits()) {
            log.debug("Combining commits for participation {}", participation.toString());
            gitService.combineAllStudentCommits(repo, programmingExercise);
        }

        if (repositoryExportOptions.isNormalizeCodeStyle()) {
            try {
                log.debug("Normalizing code style for participation {}", participation.toString());
                fileService.normalizeLineEndingsDirectory(repo.getLocalPath().toString());
                fileService.convertToUTF8Directory(repo.getLocalPath().toString());
            }
            catch (Exception ex) {
                log.warn("Cannot normalize code style in the repo " + repo.getLocalPath() + " due to the following exception: " + ex.getMessage());
            }
        }

        log.debug("Create temporary zip file for repository " + repo.getLocalPath().toString());
        Path zippedRepoFile = gitService.zipRepository(repo, REPO_DOWNLOAD_CLONE_PATH);
        pathsToZippedRepos.add(zippedRepoFile);

        return repo;
    }

    /**
     * Creates one single zip archive containing all zipped repositories found under the given paths
     *
     * @param programmingExercise The programming exercise to which all repos belong to
     * @param pathsToZippedRepos The paths to all zipped repositories
     * @return
     * @throws IOException
     */
    private File createZipWithAllRepositories(ProgrammingExercise programmingExercise, List<Path> pathsToZippedRepos) throws IOException {
        log.debug("Create zip file for all repositories");
        final var programmingExerciseId = programmingExercise.getId();
        Path zipFilePath = Paths.get(pathsToZippedRepos.get(0).getParent().toString(),
                programmingExercise.getCourse().getShortName() + "-" + programmingExercise.getShortName() + "-" + System.currentTimeMillis() + ".zip");
        createZipFile(zipFilePath, pathsToZippedRepos);
        scheduleForDeletion(zipFilePath, 15);
        log.info("Export student repositories of programming exercise " + programmingExerciseId + " with title '" + programmingExercise.getTitle() + "' was successful.");

        return new File(zipFilePath.toString());
    }

    /**
     * Deletes the locally checked out repository.
     *
     * @param participation The participation related to the repository
     * @param repo The repository that should get deleted
     */
    private void deleteTempLocalRepository(ProgrammingExerciseStudentParticipation participation, Repository repo) {
        // we do some cleanup here to prevent future errors with file handling
        // We can always delete the repository as it won't be used by the student (separate path)
        if (repo != null) {
            try {
                log.debug("Delete temporary repository " + repo.getLocalPath().toString());
                gitService.deleteLocalRepository(participation, REPO_DOWNLOAD_CLONE_PATH);
            }
            catch (Exception ex) {
                log.warn("Could not delete temporary repository " + repo.getLocalPath().toString() + ": " + ex.getMessage());
            }
        }
    }

    /**
     * Filters out all late commits of submissions from the checked out repository of a participation
     *
     * @param submissionDate The submission date (inclusive), after which all submissions should get filtered out
     * @param participation The participation related to the repository
     * @param repo The repository for which to filter all late submissions
     */
    private void filterLateSubmissions(ZonedDateTime submissionDate, ProgrammingExerciseStudentParticipation participation, Repository repo) {
        log.debug("Filter late submissions for participation {}", participation.toString());
        Optional<Submission> lastValidSubmission = participation.getSubmissions().stream()
                .filter(s -> s.getSubmissionDate() != null && s.getSubmissionDate().isBefore(submissionDate)).max(Comparator.comparing(Submission::getSubmissionDate));

        gitService.filterLateSubmissions(repo, lastValidSubmission, submissionDate);
    }

    /**
     * Create a zipfile of the given paths and save it in the zipFilePath
     *
     * @param zipFilePath path where the zipfile should be saved
     * @param paths the paths that should be zipped
     * @throws IOException if an error occured while zipping
     */
    private void createZipFile(Path zipFilePath, List<Path> paths) throws IOException {
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(zipFilePath))) {
            paths.stream().filter(path -> !Files.isDirectory(path)).forEach(path -> {
                ZipEntry zipEntry = new ZipEntry(path.toString());
                try {
                    zipOutputStream.putNextEntry(zipEntry);
                    Files.copy(path, zipOutputStream);
                    zipOutputStream.closeEntry();
                }
                catch (Exception e) {
                    log.error("Create zip file error", e);
                }
            });
        }
    }

    /**
     * Adds the student id of the given student participation to the project name in all .project (Eclipse)
     * and pom.xml (Maven) files found in the given repository.
     *
     * @param repo The repository for which the student id should get added
     * @param programmingExercise The checked out exercise in the repository
     * @param participation The student participation for the student id, which should be added.
     */
    public void addStudentIdToProjectName(Repository repo, ProgrammingExercise programmingExercise, StudentParticipation participation) {
        String studentId = participation.getStudent().getLogin();

        // Get all files in repository expect .git files
        List<String> allRepoFiles = listAllFilesInPath(repo.getLocalPath());

        // is Java programming language
        if (programmingExercise.getProgrammingLanguage() == ProgrammingLanguage.JAVA) {
            // Filter all Eclipse .project files
            List<String> eclipseProjectFiles = allRepoFiles.stream().filter(file -> file.endsWith(".project")).collect(Collectors.toList());

            for (String eclipseProjectFilePath : eclipseProjectFiles) {
                addStudentIdToEclipseProjectName(repo, studentId, eclipseProjectFilePath);
            }

            // Filter all pom.xml files
            List<String> pomFiles = allRepoFiles.stream().filter(file -> file.endsWith("pom.xml")).collect(Collectors.toList());
            for (String pomFilePath : pomFiles) {
                addStudentIdToMavenProjectName(repo, studentId, pomFilePath);
            }
        }

        try {
            gitService.stageAllChanges(repo);
            gitService.commit(repo, "Add Student Id to Project Name");
        }
        catch (GitAPIException ex) {
            log.error("Cannot stage or commit to the repo " + repo.getLocalPath() + " due to the following exception: " + ex);
        }
    }

    private void addStudentIdToMavenProjectName(Repository repo, String studentId, String pomFilePath) {
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

            // 3- Append Student Id to Project Names
            if (nameNode != null) {
                nameNode.setTextContent(nameNode.getTextContent() + " " + studentId);
            }
            if (artifactIdNode != null) {
                String artifactId = (artifactIdNode.getTextContent() + "-" + studentId).replaceAll(" ", "-").toLowerCase();
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

    private void addStudentIdToEclipseProjectName(Repository repo, String studentId, String eclipseProjectFilePath) {
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

            // 3- Append Student Id to Project Name
            if (nameNode != null) {
                nameNode.setTextContent(nameNode.getTextContent() + " " + studentId);
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

    private Map<Path, ScheduledFuture> futures = new HashMap<>();

    private ScheduledExecutorService executor = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());

    private static final TimeUnit MINUTES = TimeUnit.MINUTES; // your time unit

    /**
     * Schedule the deletion of the given path with a given delay
     *
     * @param path The path that should be deleted
     * @param delayInMinutes The delay in minutes after which the path should be deleted
     */
    private void scheduleForDeletion(Path path, long delayInMinutes) {
        ScheduledFuture future = executor.schedule(() -> {
            try {
                log.info("Delete file " + path);
                Files.delete(path);
                futures.remove(path);
            }
            catch (IOException e) {
                log.error("Deleting the file " + path + " did not work", e);
            }
        }, delayInMinutes, MINUTES);

        futures.put(path, future);
    }
}
