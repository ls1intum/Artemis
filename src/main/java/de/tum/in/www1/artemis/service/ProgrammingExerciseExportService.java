package de.tum.in.www1.artemis.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
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

import jplag.ExitException;
import jplag.JPlag;
import jplag.JPlagOptions;
import jplag.JPlagResult;
import jplag.options.LanguageOption;

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
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.exception.GitException;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.web.rest.dto.RepositoryExportOptionsDTO;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

@Service
public class ProgrammingExerciseExportService {

    private final Logger log = LoggerFactory.getLogger(ProgrammingExerciseExportService.class);

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final FileService fileService;

    private final GitService gitService;

    private final ZipFileService zipFileService;

    private final UrlService urlService;

    public ProgrammingExerciseExportService(ProgrammingExerciseRepository programmingExerciseRepository, FileService fileService, GitService gitService,
            ZipFileService zipFileService, UrlService urlService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.fileService = fileService;
        this.gitService = gitService;
        this.zipFileService = zipFileService;
        this.urlService = urlService;
    }

    // The downloaded repos should be cloned into another path in order to not interfere with the repo used by the student
    @Value("${artemis.repo-download-clone-path}")
    private String REPO_DOWNLOAD_CLONE_PATH;

    /**
     * Get participations of programming exercises of a requested list of students packed together in one zip file.
     *
     * @param programmingExerciseId the id of the exercise entity
     * @param participations participations that should be exported
     * @param repositoryExportOptions the options that should be used for the export
     * @return a zip file containing all requested participations
     */
    public File exportStudentRepositories(long programmingExerciseId, @NotNull List<ProgrammingExerciseStudentParticipation> participations,
            RepositoryExportOptionsDTO repositoryExportOptions) {
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findWithTemplateParticipationAndSolutionParticipationById(programmingExerciseId).get();

        if (repositoryExportOptions.isExportAllParticipants()) {
            log.info(
                    "Request to export all student or team repositories of programming exercise " + programmingExerciseId + " with title '" + programmingExercise.getTitle() + "'");
        }
        else {
            log.info("Request to export the repositories of programming exercise " + programmingExerciseId + " with title '" + programmingExercise.getTitle()
                    + "' of the following students or teams: " + participations.stream().map(StudentParticipation::getParticipantIdentifier).collect(Collectors.joining(", ")));
        }

        List<Path> pathsToZippedRepoFiles = Collections.synchronizedList(new ArrayList<>());
        participations.parallelStream().forEach(participation -> {
            Repository repo = null;
            try {
                if (participation.getRepositoryUrlAsUrl() == null) {
                    log.warn("Ignore participation " + participation.getId() + " for export, because its repository URL is null");
                    return;
                }
                repo = zipRepositoryForParticipation(programmingExercise, participation, repositoryExportOptions, pathsToZippedRepoFiles);
            }
            catch (IOException | GitException | GitAPIException | InterruptedException ex) {
                log.error("export student repository " + participation.getRepositoryUrlAsUrl() + " in exercise '" + programmingExercise.getTitle() + "' did not work as expected: "
                        + ex.getMessage());
            }
            finally {
                deleteTempLocalRepository(participation, repo);
            }
        });

        if (pathsToZippedRepoFiles.isEmpty()) {
            log.warn("The zip file could not be created. Ignoring the request to export repositories for exercise " + programmingExercise.getTitle());
            return null;
        }

        try {
            // create a large zip file with all zipped repos and provide it for download
            return createZipWithAllRepositories(programmingExercise, pathsToZippedRepoFiles);
        }
        catch (IOException ex) {
            log.error("Export students repositories for exercise '" + programmingExercise.getTitle() + "' did not work as expected: " + ex.getMessage());
        }
        finally {
            // we do some cleanup here to prevent future errors with file handling
            deleteTempZipRepoFiles(pathsToZippedRepoFiles);
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
     * downloads all repos of the exercise and runs JPlag
     *
     * @param programmingExerciseId the id of the programming exercises which should be checked
     * @return a zip file that can be returned to the client
     * @throws ExitException is thrown if JPlag exits unexpectedly
     * @throws IOException is thrown for file handling errors
     */
    public File checkPlagiarism(long programmingExerciseId) throws ExitException, IOException {
        // TODO: offer the following options in the client
        // 1) filter empty submissions, i.e. repositories with no student commits
        // 2) filter submissions with a result score of 0%

        final var programmingExercise = programmingExerciseRepository.findWithAllParticipationsById(programmingExerciseId).get();

        downloadRepositories(programmingExercise);

        final var output = "output";
        final var projectKey = programmingExercise.getProjectKey();
        final var outputFolder = REPO_DOWNLOAD_CLONE_PATH + (REPO_DOWNLOAD_CLONE_PATH.endsWith(File.separator) ? "" : File.separator) + projectKey + "-" + output;

        final var outputFolderFile = new File(outputFolder);
        outputFolderFile.mkdirs();

        final var programmingLanguage = getJPlagProgrammingLanguage(programmingExercise);

        final var repoFolder = REPO_DOWNLOAD_CLONE_PATH + (REPO_DOWNLOAD_CLONE_PATH.endsWith(File.separator) ? "" : File.separator) + projectKey;
        final var templateRepoName = urlService.getRepositorySlugFromUrl(programmingExercise.getTemplateParticipation().getRepositoryUrlAsUrl());
        final var args = new String[] { "-l", programmingLanguage, "-r", outputFolder, "-s", repoFolder, "-bc", templateRepoName, "-vq" };

        // TODO
        JPlagOptions options = new JPlagOptions("/path/to/rootDir", LanguageOption.JAVA_1_9);

        JPlag jplag = new JPlag(options);
        JPlagResult result = jplag.run();

        final var zipFilePath = Paths.get(REPO_DOWNLOAD_CLONE_PATH, programmingExercise.getCourseViaExerciseGroupOrCourseMember().getShortName() + "-"
                + programmingExercise.getShortName() + "-" + System.currentTimeMillis() + "-Jplag-Analysis-Output.zip");
        zipFileService.createZipFileWithFolderContent(zipFilePath, Paths.get(outputFolder));
        fileService.scheduleForDeletion(zipFilePath, 5);

        // cleanup
        if (outputFolderFile.exists()) {
            FileSystemUtils.deleteRecursively(outputFolderFile);
        }

        cleanupRepositories(programmingExercise);

        return new File(zipFilePath.toString());
    }

    private String getJPlagProgrammingLanguage(ProgrammingExercise programmingExercise) {
        return switch (programmingExercise.getProgrammingLanguage()) {
            case JAVA -> "java19";
            case C -> "c/c++";
            case PYTHON -> "python3";
            default -> throw new BadRequestAlertException("Programming language " + programmingExercise.getProgrammingLanguage() + " not supported for plagiarism check.",
                    "ProgrammingExercise", "notSupported");
        };
    }

    private void cleanupRepositories(ProgrammingExercise programmingExercise) {
        programmingExercise.getStudentParticipations().parallelStream().forEach(participation -> {
            var programmingExerciseParticipation = (ProgrammingExerciseParticipation) participation;
            try {
                if (programmingExerciseParticipation.getRepositoryUrlAsUrl() == null) {
                    return;
                }
                Repository repo = gitService.getOrCheckoutRepository(programmingExerciseParticipation, REPO_DOWNLOAD_CLONE_PATH);
                deleteTempLocalRepository(programmingExerciseParticipation, repo);
            }
            catch (GitException | GitAPIException | InterruptedException ex) {
                log.error("cleanup student repository " + programmingExerciseParticipation.getRepositoryUrlAsUrl() + " in exercise '" + programmingExercise.getTitle()
                        + "' did not work as expected: " + ex.getMessage());
            }
        });
        try {
            Repository templateRepo = gitService.getOrCheckoutRepository(programmingExercise.getTemplateParticipation(), REPO_DOWNLOAD_CLONE_PATH);
            deleteTempLocalRepository(programmingExercise.getTemplateParticipation(), templateRepo);
        }
        catch (GitException | GitAPIException | InterruptedException ex) {
            log.error("cleanup template repository " + programmingExercise.getTemplateParticipation().getRepositoryUrlAsUrl() + " in exercise '" + programmingExercise.getTitle()
                    + "' did not work as expected: " + ex.getMessage());
        }
    }

    private void downloadRepositories(ProgrammingExercise programmingExercise) {
        programmingExercise.getStudentParticipations().parallelStream().forEach(participation -> {
            var programmingExerciseParticipation = (ProgrammingExerciseParticipation) participation;
            try {
                if (programmingExerciseParticipation.getRepositoryUrlAsUrl() == null) {
                    log.warn("Ignore participation " + participation.getId() + " for export, because its repository URL is null");
                    return;
                }
                Repository repo = gitService.getOrCheckoutRepository(programmingExerciseParticipation, REPO_DOWNLOAD_CLONE_PATH);
                gitService.resetToOriginMaster(repo); // start with clean state

                repo.close();
            }
            catch (GitException | GitAPIException | InterruptedException ex) {
                log.error("clone student repository " + programmingExerciseParticipation.getRepositoryUrlAsUrl() + " in exercise '" + programmingExercise.getTitle()
                        + "' did not work as expected: " + ex.getMessage());
            }
        });

        // clone the template repo
        try {
            Repository templateRepo = gitService.getOrCheckoutRepository(programmingExercise.getTemplateParticipation(), REPO_DOWNLOAD_CLONE_PATH);

            gitService.resetToOriginMaster(templateRepo); // start with clean state
            templateRepo.close();
        }
        catch (GitException | GitAPIException | InterruptedException ex) {
            log.error("clone template repository " + programmingExercise.getTemplateParticipation().getRepositoryUrlAsUrl() + " in exercise '" + programmingExercise.getTitle()
                    + "' did not work as expected: " + ex.getMessage());
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
     * @return The checked out and zipped repository
     * @throws GitAPIException If something went wrong checking out the repo
     * @throws InterruptedException
     * @throws IOException
     */
    private Repository zipRepositoryForParticipation(final ProgrammingExercise programmingExercise, final ProgrammingExerciseStudentParticipation participation,
            final RepositoryExportOptionsDTO repositoryExportOptions, List<Path> pathsToZippedRepos) throws GitAPIException, InterruptedException, IOException {
        final var repository = gitService.getOrCheckoutRepository(participation, REPO_DOWNLOAD_CLONE_PATH);
        gitService.resetToOriginMaster(repository); // start with clean state

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
        Path zippedRepoFile = gitService.zipRepository(repository, REPO_DOWNLOAD_CLONE_PATH, repositoryExportOptions.isHideStudentNameInZippedFolder());
        pathsToZippedRepos.add(zippedRepoFile);

        // if repository is not closed, it causes weird IO issues when trying to delete the repository again
        // java.io.IOException: Unable to delete file: ...\.git\objects\pack\...
        repository.close();
        return repository;
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
        Path zipFilePath = Paths.get(pathsToZippedRepos.get(0).getParent().toString(), programmingExercise.getCourseViaExerciseGroupOrCourseMember().getShortName() + "-"
                + programmingExercise.getShortName() + "-" + System.currentTimeMillis() + ".zip");
        zipFileService.createZipFile(zipFilePath, pathsToZippedRepos);
        fileService.scheduleForDeletion(zipFilePath, 5);
        return new File(zipFilePath.toString());
    }

    /**
     * Deletes the locally checked out repository.
     *
     * @param participation The participation related to the repository
     * @param repository The repository that should get deleted
     */
    private void deleteTempLocalRepository(ProgrammingExerciseParticipation participation, Repository repository) {
        // we do some cleanup here to prevent future errors with file handling
        // We can always delete the repository as it won't be used by the student (separate path)
        if (repository != null) {
            try {
                // if repository is not closed, it causes weird IO issues when trying to delete the repository again
                // java.io.IOException: Unable to delete file: ...\.git\objects\pack\...
                repository.close();

                log.debug("Delete temporary repository " + repository.getLocalPath().toString());
                gitService.deleteLocalRepository(participation, REPO_DOWNLOAD_CLONE_PATH);
            }
            catch (Exception ex) {
                log.warn("Could not delete temporary repository " + repository.getLocalPath().toString() + ": " + ex.getMessage());
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
     * Adds the participant identifier (student login or team short name) of the given student participation to the project name in all .project (Eclipse)
     * and pom.xml (Maven) files found in the given repository.
     *
     * @param repository The repository for which the student id should get added
     * @param programmingExercise The checked out exercise in the repository
     * @param participation The student participation for the student/team identifier, which should be added.
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
