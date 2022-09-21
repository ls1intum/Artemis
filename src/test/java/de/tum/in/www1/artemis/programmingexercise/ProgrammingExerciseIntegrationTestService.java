package de.tum.in.www1.artemis.programmingexercise;

import static de.tum.in.www1.artemis.domain.enumeration.BuildPlanType.SOLUTION;
import static de.tum.in.www1.artemis.domain.enumeration.BuildPlanType.TEMPLATE;
import static de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResourceEndpoints.*;
import static de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResourceErrorKeys.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiFunction;
import java.util.stream.IntStream;
import java.util.zip.ZipFile;

import javax.validation.constraints.NotNull;

import org.apache.commons.io.FileUtils;
import org.assertj.core.data.Offset;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.EmptyCommitException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.fasterxml.jackson.core.type.TypeReference;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.*;
import de.tum.in.www1.artemis.domain.notification.Notification;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismComparison;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismStatus;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextPlagiarismResult;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextSubmissionElement;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.FileService;
import de.tum.in.www1.artemis.service.UrlService;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.connectors.VersionControlService;
import de.tum.in.www1.artemis.util.*;
import de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResourceEndpoints;
import de.tum.in.www1.artemis.web.rest.ProgrammingExerciseTestCaseResource;
import de.tum.in.www1.artemis.web.rest.dto.ProgrammingExerciseTestCaseDTO;
import de.tum.in.www1.artemis.web.rest.dto.RepositoryExportOptionsDTO;
import de.tum.in.www1.artemis.web.websocket.dto.ProgrammingExerciseTestCaseStateDTO;

/**
 * Note: this class should be independent of the actual VCS and CIS and contains common test logic for both scenarios:
 * 1) Bamboo + Bitbucket
 * 2) Jenkins + Gitlab
 */
@Service
class ProgrammingExerciseIntegrationTestService {

    @Value("${artemis.version-control.default-branch:main}")
    private String defaultBranch;

    @Value("${artemis.repo-download-clone-path}")
    private String repoDownloadClonePath;

    @Autowired
    // this will be a SpyBean because it was configured as SpyBean in the super class of the actual test class (see AbstractArtemisIntegrationTest)
    private FileService fileService;

    @Autowired
    // this will be a SpyBean because it was configured as SpyBean in the super class of the actual test class (see AbstractArtemisIntegrationTest)
    private UrlService urlService;

    @Autowired
    private GitUtilService gitUtilService;

    @Autowired
    private DatabaseUtilService databaseUtilService;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    @Autowired
    private ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository;

    @Autowired
    private AuxiliaryRepositoryRepository auxiliaryRepositoryRepository;

    @Autowired
    private DatabaseUtilService database;

    @Autowired
    private RequestUtilService request;

    @Autowired
    // this will be a SpyBean because it was configured as SpyBean in the super class of the actual test class (see AbstractArtemisIntegrationTest)
    private GitService gitService;

    private Course course;

    public ProgrammingExercise programmingExercise;

    private ProgrammingExercise programmingExerciseInExam;

    private ProgrammingExerciseStudentParticipation participation1;

    private ProgrammingExerciseStudentParticipation participation2;

    private File downloadedFile;

    private File localRepoFile;

    private Git localGit;

    private Git remoteGit;

    private File localRepoFile2;

    private Git localGit2;

    private Git remoteGit2;

    private MockDelegate mockDelegate;

    // this will be a SpyBean because it was configured as SpyBean in the super class of the actual test class (see AbstractArtemisIntegrationTest)
    private VersionControlService versionControlService;

    void setup(MockDelegate mockDelegate, VersionControlService versionControlService) throws Exception {
        this.mockDelegate = mockDelegate;
        this.versionControlService = versionControlService; // this can be used like a SpyBean

        database.addUsers(3, 2, 2, 2);
        course = database.addCourseWithOneProgrammingExerciseAndTestCases();
        programmingExercise = database.getFirstExerciseWithType(course, ProgrammingExercise.class);
        programmingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationById(programmingExercise.getId()).get();
        programmingExerciseInExam = database.addCourseExamExerciseGroupWithOneProgrammingExerciseAndTestCases();
        programmingExerciseInExam = programmingExerciseRepository.findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(programmingExerciseInExam.getId())
                .get();

        participation1 = database.addStudentParticipationForProgrammingExercise(programmingExercise, "student1");
        participation2 = database.addStudentParticipationForProgrammingExercise(programmingExercise, "student2");

        database.addStudentParticipationForProgrammingExercise(programmingExerciseInExam, "student1");
        database.addStudentParticipationForProgrammingExercise(programmingExerciseInExam, "student2");

        localRepoFile = Files.createTempDirectory("repo").toFile();
        localGit = LocalRepository.initialize(localRepoFile, defaultBranch);
        File originRepoFile = Files.createTempDirectory("repoOrigin").toFile();
        remoteGit = LocalRepository.initialize(originRepoFile, defaultBranch);
        StoredConfig config = localGit.getRepository().getConfig();
        config.setString("remote", "origin", "url", originRepoFile.getAbsolutePath());
        config.save();

        localRepoFile2 = Files.createTempDirectory("repo2").toFile();
        localGit2 = LocalRepository.initialize(localRepoFile2, defaultBranch);
        File originRepoFile2 = Files.createTempDirectory("repoOrigin").toFile();
        remoteGit2 = LocalRepository.initialize(originRepoFile2, defaultBranch);
        StoredConfig config2 = localGit2.getRepository().getConfig();
        config2.setString("remote", "origin", "url", originRepoFile2.getAbsolutePath());
        config2.save();

        // TODO use createProgrammingExercise or setupTemplateAndPush to create actual content (based on the template repos) in this repository
        // so that e.g. addStudentIdToProjectName in ProgrammingExerciseExportService is tested properly as well

        // the following 2 lines prepare the generation of the structural test oracle
        var testjsonFilePath = Path.of(localRepoFile.getPath(), "test", programmingExercise.getPackageFolderName(), "test.json");
        gitUtilService.writeEmptyJsonFileToPath(testjsonFilePath);
        // create two empty commits
        localGit.commit().setMessage("empty").setAllowEmpty(true).setSign(false).setAuthor("test", "test@test.com").call();
        localGit.push().call();

        // we use the temp repository as remote origin for all repositories that are created during the
        // TODO: distinguish between template, test and solution
        doReturn(new GitUtilService.MockFileRepositoryUrl(originRepoFile)).when(versionControlService).getCloneRepositoryUrl(anyString(), anyString());
    }

    void tearDown() throws IOException {
        database.resetDatabase();
        if (downloadedFile != null && downloadedFile.exists()) {
            FileUtils.forceDelete(downloadedFile);
        }
        if (localRepoFile != null && localRepoFile.exists()) {
            FileUtils.deleteDirectory(localRepoFile);
        }
        if (repoDownloadClonePath != null && Files.exists(Path.of(repoDownloadClonePath))) {
            FileUtils.deleteDirectory(new File(repoDownloadClonePath));
        }
        if (localGit != null) {
            localGit.close();
        }
        if (remoteGit != null) {
            remoteGit.close();
        }
        if (localGit2 != null) {
            localGit2.close();
        }
        if (remoteGit2 != null) {
            remoteGit2.close();
        }
    }

    void testProgrammingExerciseIsReleased_IsReleasedAndHasResults() throws Exception {
        programmingExercise.setReleaseDate(ZonedDateTime.now().minusHours(5L));
        programmingExerciseRepository.save(programmingExercise);
        StudentParticipation participation = database.createAndSaveParticipationForExercise(programmingExercise, "student1");
        database.addResultToParticipation(null, null, participation);

        ProgrammingExerciseTestCaseStateDTO releaseStateDTO = request.get("/api/programming-exercises/" + programmingExercise.getId() + "/test-case-state", HttpStatus.OK,
                ProgrammingExerciseTestCaseStateDTO.class);
        assertThat(releaseStateDTO.isReleased()).isTrue();
        assertThat(releaseStateDTO.isHasStudentResult()).isTrue();
        assertThat(releaseStateDTO.isTestCasesChanged()).isFalse();
    }

    void testProgrammingExerciseIsReleased_IsNotReleasedAndHasResults() throws Exception {
        programmingExercise.setReleaseDate(ZonedDateTime.now().plusHours(5L));
        programmingExerciseRepository.save(programmingExercise);
        StudentParticipation participation = database.createAndSaveParticipationForExercise(programmingExercise, "student1");
        database.addResultToParticipation(null, null, participation);

        ProgrammingExerciseTestCaseStateDTO releaseStateDTO = request.get("/api/programming-exercises/" + programmingExercise.getId() + "/test-case-state", HttpStatus.OK,
                ProgrammingExerciseTestCaseStateDTO.class);
        assertThat(releaseStateDTO.isReleased()).isFalse();
        assertThat(releaseStateDTO.isHasStudentResult()).isTrue();
        assertThat(releaseStateDTO.isTestCasesChanged()).isFalse();
    }

    void checkIfProgrammingExerciseIsReleased_IsReleasedAndHasNoResults() throws Exception {
        programmingExercise.setReleaseDate(ZonedDateTime.now().minusHours(5L));
        programmingExercise.setTestCasesChanged(true);
        programmingExerciseRepository.save(programmingExercise);

        ProgrammingExerciseTestCaseStateDTO releaseStateDTO = request.get("/api/programming-exercises/" + programmingExercise.getId() + "/test-case-state", HttpStatus.OK,
                ProgrammingExerciseTestCaseStateDTO.class);
        assertThat(releaseStateDTO.isReleased()).isTrue();
        assertThat(releaseStateDTO.isHasStudentResult()).isFalse();
        assertThat(releaseStateDTO.isTestCasesChanged()).isTrue();
    }

    void testProgrammingExerciseIsReleased_forbidden() throws Exception {
        request.get("/api/programming-exercises/" + programmingExercise.getId() + "/test-case-state", HttpStatus.FORBIDDEN, Boolean.class);
    }

    void testExportSubmissionsByParticipationIds_addParticipantIdentifierToProjectName() throws Exception {
        var repository1 = gitService.getExistingCheckedOutRepositoryByLocalPath(localRepoFile.toPath(), null);
        var repository2 = gitService.getExistingCheckedOutRepositoryByLocalPath(localRepoFile2.toPath(), null);

        doReturn(repository1).when(gitService).getOrCheckoutRepository(eq(participation1.getVcsRepositoryUrl()), anyString(), anyBoolean());
        doReturn(repository2).when(gitService).getOrCheckoutRepository(eq(participation2.getVcsRepositoryUrl()), anyString(), anyBoolean());
        doThrow(EmptyCommitException.class).when(gitService).stageAllChanges(any());

        // Create the eclipse .project file which will be modified.
        Path projectFilePath = Path.of(repository1.getLocalPath().toString(), ".project");
        File projectFile = new File(projectFilePath.toString());
        String projectFileContents = de.tum.in.www1.artemis.util.FileUtils.loadFileFromResources("test-data/repository-export/sample.project");
        FileUtils.writeStringToFile(projectFile, projectFileContents, StandardCharsets.UTF_8);

        // Create the maven .pom file
        Path pomPath = Path.of(repository1.getLocalPath().toString(), "pom.xml");
        File pomFile = new File(pomPath.toString());
        String pomContents = de.tum.in.www1.artemis.util.FileUtils.loadFileFromResources("test-data/repository-export/pom.xml");
        FileUtils.writeStringToFile(pomFile, pomContents, StandardCharsets.UTF_8);

        var participation = programmingExerciseStudentParticipationRepository.findByExerciseIdAndStudentLogin(programmingExercise.getId(), "student1");
        assertThat(participation).isPresent();

        final var path = ROOT + EXPORT_SUBMISSIONS_BY_PARTICIPATIONS.replace("{exerciseId}", String.valueOf(programmingExercise.getId())).replace("{participationIds}",
                String.join(",", List.of(participation.get().getId().toString())));
        // all options false by default, only test if export works at all
        var exportOptions = new RepositoryExportOptionsDTO();
        exportOptions.setAddParticipantName(true);

        downloadedFile = request.postWithResponseBodyFile(path, exportOptions, HttpStatus.OK);
        assertThat(downloadedFile).exists();

        // Make sure both repositories are present
        String modifiedEclipseProjectFile = FileUtils.readFileToString(projectFile, StandardCharsets.UTF_8);
        assertThat(modifiedEclipseProjectFile).contains("student1");

        String modifiedPom = FileUtils.readFileToString(pomFile, StandardCharsets.UTF_8);
        assertThat(modifiedPom).contains("student1");

        Files.deleteIfExists(projectFilePath);
        Files.deleteIfExists(pomPath);
    }

    void testExportSubmissionsByParticipationIds_addParticipantIdentifierToProjectNameError() throws Exception {
        var repository1 = gitService.getExistingCheckedOutRepositoryByLocalPath(localRepoFile.toPath(), null);
        var repository2 = gitService.getExistingCheckedOutRepositoryByLocalPath(localRepoFile2.toPath(), null);

        doReturn(repository1).when(gitService).getOrCheckoutRepository(eq(participation1.getVcsRepositoryUrl()), anyString(), anyBoolean());
        doReturn(repository2).when(gitService).getOrCheckoutRepository(eq(participation2.getVcsRepositoryUrl()), anyString(), anyBoolean());

        // Create the eclipse .project file which will be modified.
        Path projectFilePath = Path.of(repository1.getLocalPath().toString(), ".project");
        File projectFile = new File(projectFilePath.toString());
        if (!projectFile.exists()) {
            Files.createFile(projectFilePath);
        }

        // Create the maven .pom file
        Path pomPath = Path.of(repository1.getLocalPath().toString(), "pom.xml");
        File pomFile = new File(pomPath.toString());
        if (!pomFile.exists()) {
            Files.createFile(pomPath);
        }

        var participation = programmingExerciseStudentParticipationRepository.findByExerciseIdAndStudentLogin(programmingExercise.getId(), "student1");
        assertThat(participation).isPresent();

        final var path = ROOT + EXPORT_SUBMISSIONS_BY_PARTICIPATIONS.replace("{exerciseId}", String.valueOf(programmingExercise.getId())).replace("{participationIds}",
                String.join(",", List.of(participation.get().getId().toString())));
        // all options false by default, only test if export works at all
        var exportOptions = new RepositoryExportOptionsDTO();
        exportOptions.setAddParticipantName(true);

        downloadedFile = request.postWithResponseBodyFile(path, exportOptions, HttpStatus.OK);
        assertThat(downloadedFile).exists();

        // Make sure both repositories are present
        String modifiedEclipseProjectFile = FileUtils.readFileToString(projectFile, StandardCharsets.UTF_8);
        assertThat(modifiedEclipseProjectFile).contains("");

        String modifiedPom = FileUtils.readFileToString(pomFile, StandardCharsets.UTF_8);
        assertThat(modifiedPom).contains("");

        Files.deleteIfExists(projectFilePath);
        Files.deleteIfExists(pomPath);
    }

    void testExportSubmissionsByParticipationIds() throws Exception {
        var repository1 = gitService.getExistingCheckedOutRepositoryByLocalPath(localRepoFile.toPath(), null);
        var repository2 = gitService.getExistingCheckedOutRepositoryByLocalPath(localRepoFile2.toPath(), null);
        doReturn(repository1).when(gitService).getOrCheckoutRepository(eq(participation1.getVcsRepositoryUrl()), anyString(), anyBoolean());
        doReturn(repository2).when(gitService).getOrCheckoutRepository(eq(participation2.getVcsRepositoryUrl()), anyString(), anyBoolean());

        var participationIds = programmingExerciseStudentParticipationRepository.findAll().stream().map(participation -> participation.getId().toString()).toList();
        final var path = ROOT + EXPORT_SUBMISSIONS_BY_PARTICIPATIONS.replace("{exerciseId}", String.valueOf(programmingExercise.getId())).replace("{participationIds}",
                String.join(",", participationIds));
        // all options false by default, only test if export works at all
        var exportOptions = new RepositoryExportOptionsDTO();

        downloadedFile = request.postWithResponseBodyFile(path, exportOptions, HttpStatus.OK);
        assertThat(downloadedFile).exists();

        List<Path> entries = unzipExportedFile();

        // Make sure both repositories are present
        assertThat(entries).anyMatch(entry -> entry.toString().endsWith(Path.of("student1", ".git").toString()))
                .anyMatch(entry -> entry.toString().endsWith(Path.of("student2", ".git").toString()));
    }

    void testExportSubmissionAnonymizationCombining() throws Exception {
        // provide repositories
        var repository = gitService.getExistingCheckedOutRepositoryByLocalPath(localRepoFile.toPath(), null);
        doReturn(repository).when(gitService).getOrCheckoutRepository(eq(participation1.getVcsRepositoryUrl()), anyString(), anyBoolean());

        // Mock and pretend first commit is template commit
        ObjectId head = localGit.getRepository().findRef("HEAD").getObjectId();
        when(gitService.getLastCommitHash(any())).thenReturn(head);
        doNothing().when(gitService).resetToOriginHead(any());

        // Add commit to anonymize
        assertThat(localRepoFile.toPath().resolve("Test.java").toFile().createNewFile()).isTrue();
        localGit.add().addFilepattern(".").call();
        localGit.commit().setMessage("commit").setAuthor("user1", "email1").call();

        // Rest call
        final var path = ROOT + EXPORT_SUBMISSIONS_BY_PARTICIPATIONS.replace("{exerciseId}", String.valueOf(programmingExercise.getId())).replace("{participationIds}",
                String.valueOf(participation1.getId()));
        var exportOptions = getOptions();
        exportOptions.setAddParticipantName(false);
        downloadedFile = request.postWithResponseBodyFile(path, getOptions(), HttpStatus.OK);
        assertThat(downloadedFile).exists();

        List<Path> entries = unzipExportedFile();

        // Checks
        assertThat(entries).anyMatch(entry -> entry.endsWith("Test.java"));
        Optional<Path> extractedRepo1 = entries.stream().filter(entry -> entry.toString().endsWith(Path.of("student1", ".git").toString())).findFirst();
        assertThat(extractedRepo1).isPresent();
        try (Git downloadedGit = Git.open(extractedRepo1.get().toFile())) {
            RevCommit commit = downloadedGit.log().setMaxCount(1).call().iterator().next();
            assertThat(commit.getAuthorIdent().getName()).isEqualTo("student");
            assertThat(commit.getFullMessage()).isEqualTo("All student changes in one commit");
        }
    }

    /**
     * Recursively unzips the exported file.
     *
     * @return the list of files that the {@code downloadedFile} contained.
     */
    private List<Path> unzipExportedFile() throws Exception {
        (new ZipFileTestUtilService()).extractZipFileRecursively(downloadedFile.getAbsolutePath());
        Path extractedZipDir = Path.of(downloadedFile.getPath().substring(0, downloadedFile.getPath().length() - 4));
        try (var files = Files.walk(extractedZipDir)) {
            return files.toList();
        }
    }

    void testExportSubmissionsByParticipationIds_invalidParticipationId_badRequest() throws Exception {
        final var path = ROOT + EXPORT_SUBMISSIONS_BY_PARTICIPATIONS.replace("{exerciseId}", String.valueOf(programmingExercise.getId())).replace("{participationIds}", "10");
        request.postWithResponseBodyFile(path, getOptions(), HttpStatus.BAD_REQUEST);
    }

    void testExportSubmissionsByParticipationIds_instructorNotInCourse_forbidden() throws Exception {
        database.addInstructor("other-instructors", "instructoralt");
        var participationIds = programmingExerciseStudentParticipationRepository.findAll().stream().map(participation -> participation.getId().toString()).toList();
        final var path = ROOT + EXPORT_SUBMISSIONS_BY_PARTICIPATIONS.replace("{exerciseId}", String.valueOf(programmingExercise.getId())).replace("{participationIds}",
                String.join(",", participationIds));
        request.postWithResponseBodyFile(path, getOptions(), HttpStatus.FORBIDDEN);
    }

    void testExportSubmissionsByStudentLogins() throws Exception {
        File downloadedFile = exportSubmissionsByStudentLogins(HttpStatus.OK);
        assertThat(downloadedFile).exists();
        // TODO: unzip the files and add some checks
    }

    void testExportSubmissionsByStudentLogins_failToCreateZip() throws Exception {
        exportSubmissionsByStudentLogins(HttpStatus.BAD_REQUEST);
    }

    private File exportSubmissionsByStudentLogins(HttpStatus expectedStatus) throws Exception {
        var repository1 = gitService.getExistingCheckedOutRepositoryByLocalPath(localRepoFile.toPath(), null);
        var repository2 = gitService.getExistingCheckedOutRepositoryByLocalPath(localRepoFile2.toPath(), null);
        doReturn(repository1).when(gitService).getOrCheckoutRepository(eq(participation1.getVcsRepositoryUrl()), anyString(), anyBoolean());
        doReturn(repository2).when(gitService).getOrCheckoutRepository(eq(participation2.getVcsRepositoryUrl()), anyString(), anyBoolean());
        final var path = ROOT
                + EXPORT_SUBMISSIONS_BY_PARTICIPANTS.replace("{exerciseId}", String.valueOf(programmingExercise.getId())).replace("{participantIdentifiers}", "student1,student2");
        return request.postWithResponseBodyFile(path, getOptions(), expectedStatus);
    }

    private RepositoryExportOptionsDTO getOptions() {
        final var repositoryExportOptions = new RepositoryExportOptionsDTO();
        repositoryExportOptions.setFilterLateSubmissions(true);
        repositoryExportOptions.setCombineStudentCommits(true);
        repositoryExportOptions.setAnonymizeStudentCommits(true);
        repositoryExportOptions.setAddParticipantName(true);
        repositoryExportOptions.setNormalizeCodeStyle(true);
        return repositoryExportOptions;
    }

    void testProgrammingExerciseDelete() throws Exception {
        final var projectKey = programmingExercise.getProjectKey();
        final var path = ROOT + PROGRAMMING_EXERCISE.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));
        var params = new LinkedMultiValueMap<String, String>();
        params.add("deleteStudentReposBuildPlans", "true");
        params.add("deleteBaseReposBuildPlans", "true");

        for (final var planName : List.of("student1", "student2", TEMPLATE.getName(), SOLUTION.getName())) {
            mockDelegate.mockDeleteBuildPlan(projectKey, projectKey + "-" + planName.toUpperCase(), false);
        }
        mockDelegate.mockDeleteBuildPlanProject(projectKey, false);

        for (final var repoName : List.of("student1", "student2", RepositoryType.TEMPLATE.getName(), RepositoryType.SOLUTION.getName(), RepositoryType.TESTS.getName())) {
            mockDelegate.mockDeleteRepository(projectKey, (projectKey + "-" + repoName).toLowerCase(), false);
        }
        mockDelegate.mockDeleteProjectInVcs(projectKey, false);

        request.delete(path, HttpStatus.OK, params);
    }

    void testProgrammingExerciseDelete_failToDeleteBuildPlan() throws Exception {
        final var projectKey = programmingExercise.getProjectKey();
        final var path = ROOT + PROGRAMMING_EXERCISE.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));
        var params = new LinkedMultiValueMap<String, String>();
        params.add("deleteStudentReposBuildPlans", "true");
        params.add("deleteBaseReposBuildPlans", "true");

        for (final var planName : List.of("student1", "student2", TEMPLATE.getName(), SOLUTION.getName())) {
            mockDelegate.mockDeleteBuildPlan(projectKey, projectKey + "-" + planName.toUpperCase(), true);
        }
        mockDelegate.mockDeleteBuildPlanProject(projectKey, false);

        request.delete(path, HttpStatus.INTERNAL_SERVER_ERROR, params);
    }

    void testProgrammingExerciseDelete_buildPlanDoesntExist() throws Exception {
        final var projectKey = programmingExercise.getProjectKey();
        final var path = ROOT + PROGRAMMING_EXERCISE.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));
        var params = new LinkedMultiValueMap<String, String>();
        params.add("deleteStudentReposBuildPlans", "true");
        params.add("deleteBaseReposBuildPlans", "true");

        for (final var planName : List.of("student1", "student2", TEMPLATE.getName(), SOLUTION.getName())) {
            mockDelegate.mockDeleteBuildPlan(projectKey, projectKey + "-" + planName.toUpperCase(), false);
        }
        mockDelegate.mockDeleteBuildPlanProject(projectKey, false);

        request.delete(path, HttpStatus.OK, params);
    }

    void testProgrammingExerciseDelete_failToDeleteCiProject() throws Exception {
        final var projectKey = programmingExercise.getProjectKey();
        final var path = ROOT + PROGRAMMING_EXERCISE.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));
        var params = new LinkedMultiValueMap<String, String>();
        params.add("deleteStudentReposBuildPlans", "true");
        params.add("deleteBaseReposBuildPlans", "true");

        for (final var planName : List.of("student1", "student2", TEMPLATE.getName(), SOLUTION.getName())) {
            mockDelegate.mockDeleteBuildPlan(projectKey, projectKey + "-" + planName.toUpperCase(), false);
        }
        mockDelegate.mockDeleteBuildPlanProject(projectKey, true);

        request.delete(path, HttpStatus.INTERNAL_SERVER_ERROR, params);
    }

    void testProgrammingExerciseDelete_failToDeleteVcsProject() throws Exception {
        final var projectKey = programmingExercise.getProjectKey();
        final var path = ROOT + PROGRAMMING_EXERCISE.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));
        var params = new LinkedMultiValueMap<String, String>();
        params.add("deleteStudentReposBuildPlans", "true");
        params.add("deleteBaseReposBuildPlans", "true");

        for (final var planName : List.of("student1", "student2", TEMPLATE.getName(), SOLUTION.getName())) {
            mockDelegate.mockDeleteBuildPlan(projectKey, projectKey + "-" + planName.toUpperCase(), false);
        }
        mockDelegate.mockDeleteBuildPlanProject(projectKey, false);

        for (final var repoName : List.of("student1", "student2", RepositoryType.TEMPLATE.getName(), RepositoryType.SOLUTION.getName(), RepositoryType.TESTS.getName())) {
            mockDelegate.mockDeleteRepository(projectKey, (projectKey + "-" + repoName).toLowerCase(), false);
        }
        mockDelegate.mockDeleteProjectInVcs(projectKey, true);

        request.delete(path, HttpStatus.INTERNAL_SERVER_ERROR, params);
    }

    void testProgrammingExerciseDelete_failToDeleteVcsRepositories() throws Exception {
        final var projectKey = programmingExercise.getProjectKey();
        final var path = ROOT + PROGRAMMING_EXERCISE.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));
        var params = new LinkedMultiValueMap<String, String>();
        params.add("deleteStudentReposBuildPlans", "true");
        params.add("deleteBaseReposBuildPlans", "true");

        for (final var planName : List.of("student1", "student2", TEMPLATE.getName(), SOLUTION.getName())) {
            mockDelegate.mockDeleteBuildPlan(projectKey, projectKey + "-" + planName.toUpperCase(), false);
        }
        mockDelegate.mockDeleteBuildPlanProject(projectKey, false);

        for (final var repoName : List.of("student1", "student2", RepositoryType.TEMPLATE.getName(), RepositoryType.SOLUTION.getName(), RepositoryType.TESTS.getName())) {
            mockDelegate.mockDeleteRepository(projectKey, (projectKey + "-" + repoName).toLowerCase(), true);
        }
        mockDelegate.mockDeleteProjectInVcs(projectKey, false);

        request.delete(path, HttpStatus.INTERNAL_SERVER_ERROR, params);
    }

    void testProgrammingExerciseDelete_invalidId_notFound() throws Exception {
        programmingExercise.setId(20L);
        final var path = ROOT + PROGRAMMING_EXERCISE.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));
        request.delete(path, HttpStatus.NOT_FOUND);
    }

    void testProgrammingExerciseDelete_instructorNotInCourse_forbidden() throws Exception {
        database.addInstructor("other-instructors", "instructoralt");
        final var path = ROOT + PROGRAMMING_EXERCISE.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));
        request.delete(path, HttpStatus.FORBIDDEN);
    }

    void testGetProgrammingExercise() throws Exception {
        final var path = ROOT + PROGRAMMING_EXERCISE.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));
        var programmingExerciseServer = request.get(path, HttpStatus.OK, ProgrammingExercise.class);
        assertThat(programmingExerciseServer.getTitle()).isEqualTo(programmingExercise.getTitle());
        // TODO add more assertions
    }

    void testGetProgrammingExerciseWithStructuredGradingInstruction() throws Exception {
        final var path = ROOT + PROGRAMMING_EXERCISE.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));
        var programmingExerciseServer = request.get(path, HttpStatus.OK, ProgrammingExercise.class);
        assertThat(programmingExerciseServer.getTitle()).isEqualTo(programmingExercise.getTitle());

        List<GradingCriterion> gradingCriteria = database.addGradingInstructionsToExercise(programmingExerciseServer);

        assertThat(programmingExerciseServer.getGradingCriteria().get(0).getTitle()).isNull();
        assertThat(programmingExerciseServer.getGradingCriteria().get(1).getTitle()).isEqualTo("test title");

        assertThat(gradingCriteria.get(0).getStructuredGradingInstructions()).hasSize(1);
        assertThat(gradingCriteria.get(1).getStructuredGradingInstructions()).hasSize(3);
        assertThat(gradingCriteria.get(0).getStructuredGradingInstructions().get(0).getInstructionDescription())
                .isEqualTo("created first instruction with empty criteria for testing");
    }

    void testGetProgrammingExercise_instructorNotInCourse_forbidden() throws Exception {
        database.addInstructor("other-instructors", "instructoralt");
        final var path = ROOT + PROGRAMMING_EXERCISE.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));
        request.get(path, HttpStatus.FORBIDDEN, ProgrammingExercise.class);
    }

    void testGetProgrammingExerciseWithSetupParticipations() throws Exception {
        database.addStudentParticipationForProgrammingExercise(programmingExercise, "instructor1");
        final var path = ROOT + PROGRAMMING_EXERCISE_WITH_PARTICIPATIONS.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));
        var programmingExerciseServer = request.get(path, HttpStatus.OK, ProgrammingExercise.class);
        assertThat(programmingExerciseServer.getTitle()).isEqualTo(programmingExercise.getTitle());
        assertThat(programmingExerciseServer.getStudentParticipations()).isNotEmpty();
        assertThat(programmingExerciseServer.getTemplateParticipation()).isNotNull();
        assertThat(programmingExerciseServer.getSolutionParticipation()).isNotNull();
        // TODO add more assertions
    }

    void testGetProgrammingExerciseWithJustTemplateAndSolutionParticipation() throws Exception {
        database.addStudentParticipationForProgrammingExercise(programmingExercise, "tutor1");
        final var path = ROOT + PROGRAMMING_EXERCISE_WITH_TEMPLATE_AND_SOLUTION_PARTICIPATION.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));
        var programmingExerciseServer = request.get(path, HttpStatus.OK, ProgrammingExercise.class);
        assertThat(programmingExerciseServer.getTitle()).isEqualTo(programmingExercise.getTitle());
        assertThat(programmingExerciseServer.getSolutionParticipation().getId()).isNotNull();
        assertThat(programmingExerciseServer.getTemplateParticipation().getId()).isNotNull();
    }

    void testGetProgrammingExerciseWithSetupParticipations_instructorNotInCourse_forbidden() throws Exception {
        database.addInstructor("other-instructors", "instructoralt");
        final var path = ROOT + PROGRAMMING_EXERCISE_WITH_PARTICIPATIONS.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));
        request.get(path, HttpStatus.FORBIDDEN, ProgrammingExercise.class);
    }

    void testGetProgrammingExerciseWithSetupParticipations_invalidId_notFound() throws Exception {
        programmingExercise.setId(20L);
        final var path = ROOT + PROGRAMMING_EXERCISE_WITH_PARTICIPATIONS.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));
        request.get(path, HttpStatus.NOT_FOUND, ProgrammingExercise.class);
    }

    void testGetProgrammingExercisesForCourse() throws Exception {
        final var path = ROOT + GET_FOR_COURSE.replace("{courseId}", String.valueOf(programmingExercise.getCourseViaExerciseGroupOrCourseMember().getId()));
        var programmingExercisesServer = request.getList(path, HttpStatus.OK, ProgrammingExercise.class);
        assertThat(programmingExercisesServer).isNotEmpty();
        // TODO add more assertions
    }

    void testGetProgrammingExercisesForCourse_instructorNotInCourse_forbidden() throws Exception {
        database.addInstructor("other-instructors", "instructoralt");
        final var path = ROOT + GET_FOR_COURSE.replace("{courseId}", String.valueOf(programmingExercise.getCourseViaExerciseGroupOrCourseMember().getId()));
        request.getList(path, HttpStatus.FORBIDDEN, ProgrammingExercise.class);
    }

    void testGenerateStructureOracle() throws Exception {
        var repository = gitService.getExistingCheckedOutRepositoryByLocalPath(localRepoFile.toPath(), null);
        doReturn(repository).when(gitService).getOrCheckoutRepository(any(VcsRepositoryUrl.class), anyString(), anyBoolean());
        final var path = ROOT + GENERATE_TESTS.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));
        var result = request.putWithResponseBody(path, programmingExercise, String.class, HttpStatus.OK);
        assertThat(result).startsWith("Successfully generated the structure oracle");
        request.putWithResponseBody(path, programmingExercise, String.class, HttpStatus.BAD_REQUEST);
    }

    void updateProgrammingExercise_invalidTemplateBuildPlan_badRequest() throws Exception {
        database.addTemplateParticipationForProgrammingExercise(programmingExercise);
        mockDelegate.mockCheckIfBuildPlanExists(programmingExercise.getProjectKey(), programmingExercise.getTemplateBuildPlanId(), false, false);
        request.putAndExpectError(ROOT + PROGRAMMING_EXERCISES, programmingExercise, HttpStatus.BAD_REQUEST, INVALID_TEMPLATE_BUILD_PLAN_ID);
    }

    void updateProgrammingExercise_idIsNull_badRequest() throws Exception {
        database.addTemplateParticipationForProgrammingExercise(programmingExercise);
        programmingExercise.setId(null);
        request.put(ROOT + PROGRAMMING_EXERCISES, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void updateProgrammingExercise_eitherCourseOrExerciseGroupSet_badRequest() throws Exception {
        // both values are not set --> bad request
        programmingExercise.setCourse(null);
        request.put(ROOT + PROGRAMMING_EXERCISES, programmingExercise, HttpStatus.BAD_REQUEST);
        // both values are set --> bad request
        programmingExerciseInExam.setCourse(course);
        request.put(ROOT + PROGRAMMING_EXERCISES, programmingExerciseInExam, HttpStatus.BAD_REQUEST);
    }

    private void mockBuildPlanAndRepositoryCheck(ProgrammingExercise programmingExercise) throws Exception {
        mockDelegate.mockCheckIfBuildPlanExists(programmingExercise.getProjectKey(), programmingExercise.getTemplateBuildPlanId(), true, false);
        mockDelegate.mockCheckIfBuildPlanExists(programmingExercise.getProjectKey(), programmingExercise.getSolutionBuildPlanId(), true, false);
        mockDelegate.mockRepositoryUrlIsValid(programmingExercise.getVcsTemplateRepositoryUrl(), programmingExercise.getProjectKey(), true);
        mockDelegate.mockRepositoryUrlIsValid(programmingExercise.getVcsSolutionRepositoryUrl(), programmingExercise.getProjectKey(), true);
    }

    void updateProgrammingExercise_staticCodeAnalysisMustNotChange_falseToTrue_badRequest() throws Exception {
        mockBuildPlanAndRepositoryCheck(programmingExercise);
        programmingExercise.setStaticCodeAnalysisEnabled(true);
        request.put(ROOT + PROGRAMMING_EXERCISES, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void updateProgrammingExercise_staticCodeAnalysisMustNotChange_trueToFalse_badRequest() throws Exception {
        mockBuildPlanAndRepositoryCheck(programmingExercise);
        programmingExercise.setStaticCodeAnalysisEnabled(true);
        programmingExerciseRepository.save(programmingExercise);
        programmingExercise.setStaticCodeAnalysisEnabled(false);
        request.put(ROOT + PROGRAMMING_EXERCISES, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void updateProgrammingExercise_instructorNotInCourse_forbidden() throws Exception {
        database.addInstructor("other-instructors", "instructoralt");
        request.put(ROOT + PROGRAMMING_EXERCISES, programmingExercise, HttpStatus.FORBIDDEN);
    }

    void updateProgrammingExercise_invalidTemplateVcs_badRequest() throws Exception {
        database.addTemplateParticipationForProgrammingExercise(programmingExercise);
        mockDelegate.mockCheckIfBuildPlanExists(programmingExercise.getProjectKey(), programmingExercise.getTemplateBuildPlanId(), true, false);
        mockDelegate.mockRepositoryUrlIsValid(programmingExercise.getVcsTemplateRepositoryUrl(), programmingExercise.getProjectKey(), false);

        request.putAndExpectError(ROOT + PROGRAMMING_EXERCISES, programmingExercise, HttpStatus.BAD_REQUEST, INVALID_TEMPLATE_REPOSITORY_URL);
    }

    void updateProgrammingExercise_invalidSolutionBuildPlan_badRequest() throws Exception {
        database.addTemplateParticipationForProgrammingExercise(programmingExercise);
        database.addSolutionParticipationForProgrammingExercise(programmingExercise);
        mockDelegate.mockCheckIfBuildPlanExists(programmingExercise.getProjectKey(), programmingExercise.getTemplateBuildPlanId(), true, false);
        mockDelegate.mockRepositoryUrlIsValid(programmingExercise.getVcsTemplateRepositoryUrl(), programmingExercise.getProjectKey(), true);
        mockDelegate.mockCheckIfBuildPlanExists(programmingExercise.getProjectKey(), programmingExercise.getSolutionBuildPlanId(), false, false);

        request.putAndExpectError(ROOT + PROGRAMMING_EXERCISES, programmingExercise, HttpStatus.BAD_REQUEST, INVALID_SOLUTION_BUILD_PLAN_ID);
    }

    void updateProgrammingExercise_invalidSolutionRepository_badRequest() throws Exception {
        database.addTemplateParticipationForProgrammingExercise(programmingExercise);
        database.addSolutionParticipationForProgrammingExercise(programmingExercise);
        mockDelegate.mockCheckIfBuildPlanExists(programmingExercise.getProjectKey(), programmingExercise.getTemplateBuildPlanId(), true, false);
        mockDelegate.mockCheckIfBuildPlanExists(programmingExercise.getProjectKey(), programmingExercise.getSolutionBuildPlanId(), true, false);
        mockDelegate.mockRepositoryUrlIsValid(programmingExercise.getVcsTemplateRepositoryUrl(), programmingExercise.getProjectKey(), true);
        mockDelegate.mockRepositoryUrlIsValid(programmingExercise.getVcsSolutionRepositoryUrl(), programmingExercise.getProjectKey(), false);

        request.putAndExpectError(ROOT + PROGRAMMING_EXERCISES, programmingExercise, HttpStatus.BAD_REQUEST, INVALID_SOLUTION_REPOSITORY_URL);
    }

    void updateProgrammingExercise_checkIfBuildPlanExistsFails_badRequest() throws Exception {
        database.addTemplateParticipationForProgrammingExercise(programmingExercise);
        mockDelegate.mockCheckIfBuildPlanExists(programmingExercise.getProjectKey(), programmingExercise.getTemplateBuildPlanId(), true, true);
        mockDelegate.mockRepositoryUrlIsValid(programmingExercise.getVcsTemplateRepositoryUrl(), programmingExercise.getProjectKey(), true);
        request.putAndExpectError(ROOT + PROGRAMMING_EXERCISES, programmingExercise, HttpStatus.BAD_REQUEST, INVALID_TEMPLATE_BUILD_PLAN_ID);
    }

    /**
     * This test checks that it is not allowed to change the courseId of an exercise
     * in an update request. The request should fail with 'HttpStatus.CONFLICT'.
     */
    void updateProgrammingExerciseShouldFailWithConflictWhenUpdatingCourseId() throws Exception {
        // Create a programming exercise.
        mockBuildPlanAndRepositoryCheck(programmingExercise);

        // Create a new course with different id.
        Long oldCourseId = programmingExercise.getCourseViaExerciseGroupOrCourseMember().getId();
        Long newCourseId = oldCourseId + 1;
        Course newCourse = databaseUtilService.createCourse(newCourseId);

        // Assign new course to the programming exercise.
        ProgrammingExercise newProgrammingExercise = programmingExercise;
        newProgrammingExercise.setCourse(newCourse);

        // Programming exercise update with the new course should fail.
        request.put(ROOT + PROGRAMMING_EXERCISES, newProgrammingExercise, HttpStatus.CONFLICT);
    }

    /**
     * This test checks that it is not allowed to change SCA enabled option
     */
    void updateProgrammingExerciseShouldFailWithBadRequestWhenUpdatingSCAOption() throws Exception {
        mockBuildPlanAndRepositoryCheck(programmingExercise);

        ProgrammingExercise updatedExercise = programmingExercise;
        updatedExercise.setStaticCodeAnalysisEnabled(true);

        request.put(ROOT + PROGRAMMING_EXERCISES, updatedExercise, HttpStatus.BAD_REQUEST);
    }

    /**
     * This test checks that it is not allowed to change coverage enabled option
     */
    void updateProgrammingExerciseShouldFailWithBadRequestWhenUpdatingCoverageOption() throws Exception {
        mockBuildPlanAndRepositoryCheck(programmingExercise);

        ProgrammingExercise updatedExercise = programmingExercise;
        updatedExercise.setTestwiseCoverageEnabled(true);

        request.put(ROOT + PROGRAMMING_EXERCISES, updatedExercise, HttpStatus.BAD_REQUEST);
    }

    void updateExerciseDueDateWithIndividualDueDateUpdate() throws Exception {
        mockBuildPlanAndRepositoryCheck(programmingExercise);

        final ZonedDateTime individualDueDate = ZonedDateTime.now().plusHours(20);

        {
            final var participations = programmingExerciseStudentParticipationRepository.findByExerciseId(programmingExercise.getId());
            participations.get(0).setIndividualDueDate(ZonedDateTime.now().plusHours(2));
            participations.get(1).setIndividualDueDate(individualDueDate);
            programmingExerciseStudentParticipationRepository.saveAll(participations);
        }

        programmingExercise.setDueDate(ZonedDateTime.now().plusHours(12));
        programmingExercise.setReleaseDate(programmingExercise.getDueDate().minusDays(1));
        request.put(ROOT + PROGRAMMING_EXERCISES, programmingExercise, HttpStatus.OK);

        {
            final var participations = programmingExerciseStudentParticipationRepository.findByExerciseId(programmingExercise.getId());
            final var withNoIndividualDueDate = participations.stream().filter(participation -> participation.getIndividualDueDate() == null).toList();
            assertThat(withNoIndividualDueDate).hasSize(1);

            final var withIndividualDueDate = participations.stream().filter(participation -> participation.getIndividualDueDate() != null).toList();
            assertThat(withIndividualDueDate).hasSize(1);
            assertThat(withIndividualDueDate.get(0).getIndividualDueDate()).isEqualToIgnoringNanos(individualDueDate);
        }
    }

    void updateExerciseRemoveDueDate() throws Exception {
        mockBuildPlanAndRepositoryCheck(programmingExercise);

        {
            final var participations = programmingExerciseStudentParticipationRepository.findByExerciseId(programmingExercise.getId());
            assertThat(participations).hasSize(2);
            participations.get(0).setIndividualDueDate(ZonedDateTime.now().plusHours(2));
            participations.get(1).setIndividualDueDate(ZonedDateTime.now().plusHours(20));
            programmingExerciseStudentParticipationRepository.saveAll(participations);
        }

        programmingExercise.setDueDate(null);
        programmingExercise.setAssessmentDueDate(null);
        request.put(ROOT + PROGRAMMING_EXERCISES, programmingExercise, HttpStatus.OK);

        {
            final var participations = programmingExerciseStudentParticipationRepository.findByExerciseId(programmingExercise.getId());
            final var withNoIndividualDueDate = participations.stream().filter(participation -> participation.getIndividualDueDate() == null).toList();
            assertThat(withNoIndividualDueDate).hasSize(2);
        }
    }

    void updateTimeline_intructorNotInCourse_forbidden() throws Exception {
        database.addInstructor("other-instructors", "instructoralt");
        final var endpoint = "/api" + TIMELINE;
        MultiValueMap<String, String> params = new HttpHeaders();
        params.add("notificationText", "The notification text");
        request.putWithResponseBodyAndParams(endpoint, programmingExercise, ProgrammingExercise.class, HttpStatus.FORBIDDEN, params);
    }

    void updateTimeline_invalidId_notFound() throws Exception {
        programmingExercise.setId(20L);
        final var endpoint = "/api" + TIMELINE;
        MultiValueMap<String, String> params = new HttpHeaders();
        params.add("notificationText", "The notification text");
        request.putWithResponseBodyAndParams(endpoint, programmingExercise, ProgrammingExercise.class, HttpStatus.NOT_FOUND, params);
    }

    void updateTimeline_ok() throws Exception {
        final var endpoint = "/api" + TIMELINE;
        MultiValueMap<String, String> params = new HttpHeaders();
        params.add("notificationText", "The notification text");
        request.putWithResponseBodyAndParams(endpoint, programmingExercise, ProgrammingExercise.class, HttpStatus.OK, params);
    }

    void updateProblemStatement_instructorNotInCourse_forbidden() throws Exception {
        database.addInstructor("other-instructors", "instructoralt");
        final var endpoint = "/api" + ProgrammingExerciseResourceEndpoints.PROBLEM.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));
        request.patchWithResponseBody(endpoint, "a new problem statement", ProgrammingExercise.class, HttpStatus.FORBIDDEN, MediaType.TEXT_PLAIN);
    }

    void updateProblemStatement_invalidId_notFound() throws Exception {
        programmingExercise.setId(20L);
        final var endpoint = "/api" + ProgrammingExerciseResourceEndpoints.PROBLEM.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));
        request.patchWithResponseBody(endpoint, "a new problem statement", ProgrammingExercise.class, HttpStatus.NOT_FOUND, MediaType.TEXT_PLAIN);
    }

    void createProgrammingExercise_exerciseIsNull_badRequest() throws Exception {
        request.post(ROOT + SETUP, null, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_idIsNotNull_badRequest() throws Exception {
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_eitherCourseOrExerciseGroupSet_badRequest() throws Exception {
        programmingExercise.setCourse(null);
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
        programmingExerciseInExam.setCourse(programmingExercise.getCourseViaExerciseGroupOrCourseMember());
        request.post(ROOT + SETUP, programmingExerciseInExam, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_instructorNotInCourse_forbidden() throws Exception {
        database.addInstructor("other-instructors", "instructoralt");
        programmingExercise.setId(null);
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.FORBIDDEN);
    }

    void createProgrammingExercise_titleNull_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle(null);
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_titleContainsBadCharacter_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("abc?=§ ``+##");
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_invalidShortName_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("New title");
        programmingExercise.setShortName(null);
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
        programmingExercise.setShortName("hi");
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_invalidCourseShortName_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("New title");
        course.setShortName(null);
        courseRepository.save(course);
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
        course.setShortName("Hi");
        courseRepository.save(course);
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_sameShortNameInCourse_badRequest() throws Exception {
        programmingExerciseInExam.setId(null);
        programmingExercise.setTitle("New title");
        programmingExercise.setId(null);
        request.post(ROOT + SETUP, programmingExerciseInExam, HttpStatus.BAD_REQUEST);
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_shortNameContainsBadCharacters_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("New title");
        programmingExercise.setShortName("asdb ³¼²½¼³`` ");
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_noProgrammingLanguageSet_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setShortName("testShortName");
        programmingExercise.setProgrammingLanguage(null);
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_packageNameContainsBadCharacters_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setPackageName("..asd. ß?");
        programmingExercise.setShortName("testShortName");
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_packageNameContainsKeyword_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setPackageName("abc.final.xyz");
        programmingExercise.setShortName("testShortName");
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_packageNameElementBeginsWithDigit_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setPackageName("eist.2020something");
        programmingExercise.setShortName("testShortName");
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_packageNameIsNull_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setPackageName(null);
        programmingExercise.setShortName("testShortName");
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_maxScoreIsNull_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setMaxPoints(null);
        programmingExercise.setShortName("testShortName");
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_noParticipationModeSelected_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setAllowOfflineIde(false);
        programmingExercise.setAllowOnlineEditor(false);
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_staticCodeAnalysisMustBeSet_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("New title");
        programmingExercise.setShortName("NewShortname");
        programmingExercise.setStaticCodeAnalysisEnabled(null);
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_staticCodeAnalysisAndSequential_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("New title");
        programmingExercise.setShortName("NewShortname");
        programmingExercise.setStaticCodeAnalysisEnabled(true);
        programmingExercise.setSequentialTestRuns(true);
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_unsupportedProgrammingLanguageForStaticCodeAnalysis_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("New title");
        programmingExercise.setShortName("NewShortname");
        programmingExercise.setStaticCodeAnalysisEnabled(true);
        programmingExercise.programmingLanguage(ProgrammingLanguage.C);
        programmingExercise.setProjectType(ProjectType.FACT);
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_noStaticCodeAnalysisButMaxPenalty_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("New title");
        programmingExercise.setShortName("NewShortname");
        programmingExercise.setStaticCodeAnalysisEnabled(false);
        programmingExercise.setMaxStaticCodeAnalysisPenalty(20);
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_maxStaticCodePenaltyNegative_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("New title");
        programmingExercise.setShortName("NewShortname");
        programmingExercise.setStaticCodeAnalysisEnabled(true);
        programmingExercise.setMaxStaticCodeAnalysisPenalty(-20);
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_vcsProjectWithSameKeyAlreadyExists_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("testTitle");
        programmingExercise.setShortName("testShortName");
        mockDelegate.mockCheckIfProjectExistsInVcs(programmingExercise, true);
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_bambooProjectWithSameKeyAlreadyExists_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("testTitle");
        programmingExercise.setShortName("testShortName");
        mockDelegate.mockCheckIfProjectExistsInVcs(programmingExercise, false);
        mockDelegate.mockCheckIfProjectExistsInCi(programmingExercise, true, false);
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_vcsProjectWithSameTitleAlreadyExists_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("testTitle");
        programmingExercise.setShortName("testShortName");
        mockDelegate.mockCheckIfProjectExistsInVcs(programmingExercise, true);
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_bambooProjectWithSameTitleAlreadyExists_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("testTitle");
        programmingExercise.setShortName("testShortName");
        mockDelegate.mockCheckIfProjectExistsInVcs(programmingExercise, false);
        mockDelegate.mockCheckIfProjectExistsInCi(programmingExercise, true, false);
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_failToCheckIfProjectExistsInCi() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("unique-title");
        programmingExercise.setShortName("testuniqueshortname");
        mockDelegate.mockCheckIfProjectExistsInVcs(programmingExercise, false);
        mockDelegate.mockCheckIfProjectExistsInCi(programmingExercise, true, true);
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_projectTypeMissing_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("New title");
        programmingExercise.setShortName("NewShortname");
        programmingExercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        programmingExercise.setProjectType(null);
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_projectTypeNotExpected_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("New title");
        programmingExercise.setShortName("NewShortname");
        programmingExercise.setProgrammingLanguage(ProgrammingLanguage.PYTHON);
        programmingExercise.setProjectType(ProjectType.MAVEN_MAVEN);
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_onlineCodeEditorNotExpected_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("New title");
        programmingExercise.setShortName("NewShortname");
        programmingExercise.setProgrammingLanguage(ProgrammingLanguage.SWIFT);
        programmingExercise.setProjectType(ProjectType.XCODE);
        programmingExercise.setAllowOnlineEditor(true);
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_checkoutSolutionRepositoryProgrammingLanguageNotSupported_badRequest(ProgrammingLanguage programmingLanguage) throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("New title");
        programmingExercise.setShortName("NewShortname");
        programmingExercise.setProgrammingLanguage(programmingLanguage);
        programmingExercise.setCheckoutSolutionRepository(true);
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_invalidMaxScore_badRequest() throws Exception {
        database.addInstructor("other-instructors", "instructoralt");
        programmingExercise.setId(null);
        programmingExercise.setMaxPoints(0.0);
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_includedAsBonus_invalidBonusPoints_badRequest() throws Exception {
        database.addInstructor("other-instructors", "instructoralt");
        programmingExercise.setId(null);
        programmingExercise.setMaxPoints(10.0);
        programmingExercise.setBonusPoints(1.0);
        programmingExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_AS_BONUS);
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_notIncluded_invalidBonusPoints_badRequest() throws Exception {
        database.addInstructor("other-instructors", "instructoralt");
        programmingExercise.setId(null);
        programmingExercise.setMaxPoints(10.0);
        programmingExercise.setBonusPoints(1.0);
        programmingExercise.setIncludedInOverallScore(IncludedInOverallScore.NOT_INCLUDED);
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void importProgrammingExercise_sourceExerciseIdNegative_badRequest() throws Exception {
        programmingExercise.setId(-1L);
        request.post(ROOT + IMPORT.replace("{sourceExerciseId}", programmingExercise.getId().toString()), programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void importProgrammingExerciseMaxScoreNullBadRequest() throws Exception {
        programmingExercise.setMaxPoints(null);
        request.post(ROOT + IMPORT.replace("{sourceExerciseId}", programmingExercise.getId().toString()), programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void importProgrammingExercise_noParticipationModeSelected_badRequest() throws Exception {
        programmingExercise.setAllowOfflineIde(false);
        programmingExercise.setAllowOnlineEditor(false);
        request.post(ROOT + IMPORT.replace("{sourceExerciseId}", programmingExercise.getId().toString()), programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void importProgrammingExercise_noProgrammingLanguage_badRequest() throws Exception {
        programmingExercise.setProgrammingLanguage(null);
        request.post(ROOT + IMPORT.replace("{sourceExerciseId}", programmingExercise.getId().toString()), programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void importProgrammingExercise_instructorNotInCourse_forbidden() throws Exception {
        database.addInstructor("other-instructors", "instructoralt");
        request.post(ROOT + IMPORT.replace("{sourceExerciseId}", programmingExercise.getId().toString()), programmingExercise, HttpStatus.FORBIDDEN);
    }

    void importProgrammingExercise_templateIdDoesNotExist_notFound() throws Exception {
        programmingExercise.setShortName("newShortName");
        programmingExercise.setTitle("newTitle");
        request.post(ROOT + IMPORT.replace("{sourceExerciseId}", "1337"), programmingExercise, HttpStatus.NOT_FOUND);
    }

    void importProgrammingExercise_sameShortNameInCourse_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle(programmingExercise.getTitle() + "change");
        programmingExerciseInExam.setId(null);
        programmingExerciseInExam.setTitle(programmingExerciseInExam.getTitle() + "change");
        // short name will still be the same
        request.post(ROOT + IMPORT.replace("{sourceExerciseId}", "1337"), programmingExercise, HttpStatus.BAD_REQUEST);
        request.post(ROOT + IMPORT.replace("{sourceExerciseId}", "1337"), programmingExerciseInExam, HttpStatus.BAD_REQUEST);
    }

    void importProgrammingExercise_sameTitleInCourse_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setShortName(programmingExercise.getShortName() + "change");
        programmingExerciseInExam.setId(null);
        programmingExerciseInExam.setShortName(programmingExerciseInExam.getShortName() + "change");
        // title will still be the same
        request.post(ROOT + IMPORT.replace("{sourceExerciseId}", "1337"), programmingExercise, HttpStatus.BAD_REQUEST);
        request.post(ROOT + IMPORT.replace("{sourceExerciseId}", "1337"), programmingExerciseInExam, HttpStatus.BAD_REQUEST);
    }

    void importProgrammingExercise_staticCodeAnalysisMustBeSet_badRequest() throws Exception {
        var id = programmingExercise.getId();
        programmingExercise.setId(null);
        programmingExercise.setStaticCodeAnalysisEnabled(null);
        request.post(ROOT + IMPORT.replace("{sourceExerciseId}", String.valueOf(id)), programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void importProgrammingExercise_scaChanged_badRequest(boolean recreateBuildPlan, boolean updateTemplate) throws Exception {
        var params = new LinkedMultiValueMap<String, String>();
        params.add("recreateBuildPlans", String.valueOf(recreateBuildPlan));
        params.add("updateTemplate", String.valueOf(updateTemplate));

        // false -> true
        var sourceId = programmingExercise.getId();
        programmingExercise.setId(null);
        programmingExercise.setTitle("NewTitle1");
        programmingExercise.setShortName("NewShortname1");
        programmingExercise.setStaticCodeAnalysisEnabled(true);
        request.postWithResponseBody(ROOT + IMPORT.replace("{sourceExerciseId}", String.valueOf(sourceId)), programmingExercise, ProgrammingExercise.class, params,
                HttpStatus.BAD_REQUEST);

        // true -> false
        var programmingExerciseSca = database.addCourseWithOneProgrammingExerciseAndStaticCodeAnalysisCategories();
        sourceId = programmingExerciseSca.getId();
        programmingExerciseSca.setId(null);
        programmingExerciseSca.setStaticCodeAnalysisEnabled(false);
        programmingExerciseSca.setMaxStaticCodeAnalysisPenalty(null);
        programmingExerciseSca.setTitle("NewTitle2");
        programmingExerciseSca.setShortName("NewShortname2");
        request.postWithResponseBody(ROOT + IMPORT.replace("{sourceExerciseId}", String.valueOf(sourceId)), programmingExerciseSca, ProgrammingExercise.class, params,
                HttpStatus.BAD_REQUEST);
    }

    void importProgrammingExercise_eitherCourseOrExerciseGroupSet_badRequest() throws Exception {
        programmingExercise.setCourse(null);
        request.post(ROOT + IMPORT.replace("{sourceExerciseId}", "1337"), programmingExercise, HttpStatus.BAD_REQUEST);
        programmingExerciseInExam.setCourse(programmingExercise.getCourseViaExerciseGroupOrCourseMember());
        request.post(ROOT + IMPORT.replace("{sourceExerciseId}", "1337"), programmingExerciseInExam, HttpStatus.BAD_REQUEST);
    }

    void importProgrammingExercise_vcsProjectWithSameKeyAlreadyExists_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setShortName("testShortName");
        mockDelegate.mockCheckIfProjectExistsInVcs(programmingExercise, true);
        request.post(ROOT + IMPORT.replace("{sourceExerciseId}", "1337"), programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void importProgrammingExercise_bambooProjectWithSameKeyAlreadyExists_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setShortName("testShortName");
        mockDelegate.mockCheckIfProjectExistsInVcs(programmingExercise, false);
        mockDelegate.mockCheckIfProjectExistsInCi(programmingExercise, true, false);
        request.post(ROOT + IMPORT.replace("{sourceExerciseId}", "1337"), programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void importProgrammingExercise_vcsProjectWithSameTitleAlreadyExists_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setShortName("testShortName");
        mockDelegate.mockCheckIfProjectExistsInVcs(programmingExercise, true);
        request.post(ROOT + IMPORT.replace("{sourceExerciseId}", "1337"), programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void importProgrammingExercise_bambooProjectWithSameTitleAlreadyExists_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setShortName("testShortName");
        mockDelegate.mockCheckIfProjectExistsInVcs(programmingExercise, false);
        mockDelegate.mockCheckIfProjectExistsInCi(programmingExercise, true, false);
        request.post(ROOT + IMPORT.replace("{sourceExerciseId}", "1337"), programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void exportSubmissionsByStudentLogins_notInstructorForExercise_forbidden() throws Exception {
        database.addInstructor("other-instructors", "instructoralt");
        request.post(getDefaultAPIEndpointForExportRepos(), getOptions(), HttpStatus.FORBIDDEN);
    }

    @NotNull
    private String getDefaultAPIEndpointForExportRepos() {
        return ROOT + EXPORT_SUBMISSIONS_BY_PARTICIPANTS.replace("{exerciseId}", String.valueOf(programmingExercise.getId())).replace("{participantIdentifiers}", "1,2,3");
    }

    void exportSubmissionsByStudentLogins_exportAllAsTutor_forbidden() throws Exception {
        final var options = getOptions();
        options.setExportAllParticipants(true);
        request.post(getDefaultAPIEndpointForExportRepos(), options, HttpStatus.FORBIDDEN);
    }

    void generateStructureOracleForExercise_exerciseDoesNotExist_badRequest() throws Exception {
        request.put(ROOT + GENERATE_TESTS.replace("{exerciseId}", String.valueOf(programmingExercise.getId() + 8337)), programmingExercise, HttpStatus.NOT_FOUND);
    }

    void generateStructureOracleForExercise_userIsNotAdminInCourse_badRequest() throws Exception {
        database.addInstructor("other-instructors", "instructoralt");
        request.put(ROOT + GENERATE_TESTS.replace("{exerciseId}", String.valueOf(programmingExercise.getId())), programmingExercise, HttpStatus.FORBIDDEN);
    }

    void generateStructureOracleForExercise_invalidPackageName_badRequest() throws Exception {
        programmingExercise.setPackageName(null);
        programmingExerciseRepository.saveAndFlush(programmingExercise);
        request.put(ROOT + GENERATE_TESTS.replace("{exerciseId}", String.valueOf(programmingExercise.getId())), programmingExercise, HttpStatus.BAD_REQUEST);

        programmingExercise.setPackageName("ab");
        programmingExerciseRepository.saveAndFlush(programmingExercise);
        request.put(ROOT + GENERATE_TESTS.replace("{exerciseId}", String.valueOf(programmingExercise.getId())), programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void hasAtLeastOneStudentResult_exerciseDoesNotExist_notFound() throws Exception {
        request.get(ROOT + TEST_CASE_STATE.replace("{exerciseId}", String.valueOf(programmingExercise.getId() + 1337)), HttpStatus.NOT_FOUND, String.class);
    }

    void hasAtLeastOneStudentResult_isNotTeachingAssistant_forbidden() throws Exception {
        database.addTeachingAssistant("other-tutors", "tutoralt");
        request.get(ROOT + TEST_CASE_STATE.replace("{exerciseId}", String.valueOf(programmingExercise.getId())), HttpStatus.FORBIDDEN, String.class);
    }

    void getTestCases_asTutor() throws Exception {
        final var endpoint = ProgrammingExerciseTestCaseResource.Endpoints.TEST_CASES.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));
        final List<ProgrammingExerciseTestCase> returnedTests = request.getList(ROOT + endpoint, HttpStatus.OK, ProgrammingExerciseTestCase.class);
        final List<ProgrammingExerciseTestCase> testsInDB = new ArrayList<>(programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId()));
        returnedTests.forEach(testCase -> testCase.setExercise(programmingExercise));
        assertThat(returnedTests).containsExactlyInAnyOrderElementsOf(testsInDB);
    }

    void getTestCases_asStudent_forbidden() throws Exception {
        final var endpoint = ProgrammingExerciseTestCaseResource.Endpoints.TEST_CASES.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));
        request.getList(ROOT + endpoint, HttpStatus.FORBIDDEN, ProgrammingExerciseTestCase.class);
    }

    void getTestCases_tutorInOtherCourse_forbidden() throws Exception {
        database.addTeachingAssistant("other-teaching-assistants", "other-teaching-assistant");
        final var endpoint = ProgrammingExerciseTestCaseResource.Endpoints.TEST_CASES.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));

        request.getList(ROOT + endpoint, HttpStatus.FORBIDDEN, ProgrammingExerciseTestCase.class);
    }

    void updateTestCases_asInstrutor() throws Exception {
        programmingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(programmingExercise.getId()).get();
        mockDelegate.mockTriggerBuild(programmingExercise.getSolutionParticipation());
        mockDelegate.mockTriggerBuild(programmingExercise.getTemplateParticipation());
        final var testCases = programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId());
        final var updates = testCases.stream().map(testCase -> {
            final var testCaseUpdate = new ProgrammingExerciseTestCaseDTO();
            testCaseUpdate.setId(testCase.getId());
            testCaseUpdate.setVisibility(Visibility.AFTER_DUE_DATE);
            testCaseUpdate.setWeight(testCase.getId() + 42.0);
            testCaseUpdate.setBonusMultiplier(testCase.getId() + 1.0);
            testCaseUpdate.setBonusPoints(testCase.getId() + 2.0);
            return testCaseUpdate;
        }).toList();
        final var endpoint = ProgrammingExerciseTestCaseResource.Endpoints.UPDATE_TEST_CASES.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));

        final var testCasesResponse = request.patchWithResponseBody(ROOT + endpoint, updates, new TypeReference<List<ProgrammingExerciseTestCase>>() {
        }, HttpStatus.OK);
        testCasesResponse.forEach(testCase -> testCase.setExercise(programmingExercise));
        final var testCasesInDB = programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId());

        assertThat(new HashSet<>(testCasesResponse)).usingElementComparatorIgnoringFields("exercise", "tasks", "solutionEntries", "coverageEntries")
                .containsExactlyInAnyOrderElementsOf(testCasesInDB);
        assertThat(testCasesResponse).allSatisfy(testCase -> {
            assertThat(testCase.isAfterDueDate()).isTrue();
            assertThat(testCase.getWeight()).isEqualTo(testCase.getId() + 42);
            assertThat(testCase.getBonusMultiplier()).isEqualTo(testCase.getId() + 1.0);
            assertThat(testCase.getBonusPoints()).isEqualTo(testCase.getId() + 2.0);
        });
    }

    void updateTestCases_asInstrutor_triggerBuildFails() throws Exception {
        programmingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(programmingExercise.getId()).get();
        mockDelegate.mockTriggerBuildFailed(programmingExercise.getSolutionParticipation());
        mockDelegate.mockTriggerBuildFailed(programmingExercise.getTemplateParticipation());

        final var testCases = programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId());
        final var updates = testCases.stream().map(testCase -> {
            final var testCaseUpdate = new ProgrammingExerciseTestCaseDTO();
            testCaseUpdate.setId(testCase.getId());
            testCaseUpdate.setVisibility(Visibility.AFTER_DUE_DATE);
            testCaseUpdate.setWeight(testCase.getId() + 42.0);
            testCaseUpdate.setBonusMultiplier(testCase.getId() + 1.0);
            testCaseUpdate.setBonusPoints(testCase.getId() + 2.0);
            return testCaseUpdate;
        }).toList();
        final var endpoint = ProgrammingExerciseTestCaseResource.Endpoints.UPDATE_TEST_CASES.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));

        final var testCasesResponse = request.patchWithResponseBody(ROOT + endpoint, updates, new TypeReference<List<ProgrammingExerciseTestCase>>() {
        }, HttpStatus.OK);

        assertThat(testCasesResponse).isNotNull();
    }

    void updateTestCases_nonExistingExercise_notFound() throws Exception {
        final var update = new ProgrammingExerciseTestCaseDTO();
        final var endpoint = ProgrammingExerciseTestCaseResource.Endpoints.UPDATE_TEST_CASES.replace("{exerciseId}", String.valueOf(programmingExercise.getId() + 1337));
        request.patchWithResponseBody(ROOT + endpoint, List.of(update), String.class, HttpStatus.NOT_FOUND);
    }

    void updateTestCases_instructorInWrongCourse_forbidden() throws Exception {
        database.addInstructor("other-instructors", "other-instructor");
        final var update = new ProgrammingExerciseTestCaseDTO();
        final var endpoint = ProgrammingExerciseTestCaseResource.Endpoints.UPDATE_TEST_CASES.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));

        request.patchWithResponseBody(ROOT + endpoint, List.of(update), String.class, HttpStatus.FORBIDDEN);
    }

    void updateTestCases_testCaseWeightSmallerThanZero_badRequest() throws Exception {
        final var testCases = programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId());
        final var updates = testCases.stream().map(testCase -> {
            final var testCaseUpdate = new ProgrammingExerciseTestCaseDTO();
            testCaseUpdate.setId(testCase.getId());
            testCaseUpdate.setVisibility(Visibility.AFTER_DUE_DATE);
            testCaseUpdate.setWeight(0D);
            testCaseUpdate.setBonusMultiplier(testCase.getId() + 1.0);
            testCaseUpdate.setBonusPoints(testCase.getId() + 2.0);
            return testCaseUpdate;
        }).toList();
        final var endpoint = ProgrammingExerciseTestCaseResource.Endpoints.UPDATE_TEST_CASES.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));

        request.patchWithResponseBody(ROOT + endpoint, updates, String.class, HttpStatus.BAD_REQUEST);
    }

    void updateTestCases_testCaseMultiplierSmallerThanZero_badRequest() throws Exception {
        final var testCases = List.copyOf(programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId()));
        final var updates = transformTestCasesToDto(testCases);
        updates.get(0).setBonusMultiplier(-1.0);
        final var endpoint = ProgrammingExerciseTestCaseResource.Endpoints.UPDATE_TEST_CASES.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));

        request.getMvc()
                .perform(MockMvcRequestBuilders.patch(new URI(ROOT + endpoint)).contentType(MediaType.APPLICATION_JSON)
                        .content(request.getObjectMapper().writeValueAsString(updates)))
                .andExpect(status().isBadRequest()) //
                .andExpect(jsonPath("$.errorKey").value("settingNegative")) //
                .andExpect(jsonPath("$.testCase").value(testCases.get(0).getTestName()));
    }

    /**
     * Setting the bonus points to {@code null} is okay, as {@link ProgrammingExerciseTestCase#getBonusPoints()} will replace that with 0.
     */
    void updateTestCases_testCaseBonusPointsNull() throws Exception {
        {
            final var originalTestCases = programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId());
            originalTestCases.forEach(testCase -> testCase.setBonusPoints(1d));
            programmingExerciseTestCaseRepository.saveAll(originalTestCases);
        }

        final var testCases = List.copyOf(programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId()));
        mockDelegate.mockTriggerBuild(programmingExercise.getSolutionParticipation());
        mockDelegate.mockTriggerBuild(programmingExercise.getTemplateParticipation());

        final var updates = transformTestCasesToDto(testCases);
        updates.get(0).setBonusPoints(null);
        final var endpoint = ProgrammingExerciseTestCaseResource.Endpoints.UPDATE_TEST_CASES.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));

        final var testCasesResponse = request.patchWithResponseBody(ROOT + endpoint, updates, new TypeReference<List<ProgrammingExerciseTestCase>>() {
        }, HttpStatus.OK);
        final var updatedTestCase = testCasesResponse.stream().filter(testCase -> testCase.getId().equals(updates.get(0).getId())).findFirst().get();
        assertThat(updatedTestCase.getBonusPoints()).isEqualTo(0d);
        assertThat(testCasesResponse.stream().filter(testCase -> !testCase.getId().equals(updatedTestCase.getId()))).allMatch(testCase -> testCase.getBonusPoints() == 1d);
    }

    private static List<ProgrammingExerciseTestCaseDTO> transformTestCasesToDto(Collection<ProgrammingExerciseTestCase> testCases) {
        return testCases.stream().map(testCase -> {
            final var testCaseUpdate = new ProgrammingExerciseTestCaseDTO();
            testCaseUpdate.setId(testCase.getId());
            testCaseUpdate.setVisibility(testCase.getVisibility());
            testCaseUpdate.setWeight(testCase.getWeight());
            testCaseUpdate.setBonusMultiplier(testCase.getBonusMultiplier());
            testCaseUpdate.setBonusPoints(testCase.getBonusPoints());
            return testCaseUpdate;
        }).toList();
    }

    void resetTestCaseWeights_asInstructor() throws Exception {
        programmingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(programmingExercise.getId()).get();
        mockDelegate.mockTriggerBuild(programmingExercise.getSolutionParticipation());
        mockDelegate.mockTriggerBuild(programmingExercise.getTemplateParticipation());
        final var endpoint = ProgrammingExerciseTestCaseResource.Endpoints.RESET.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));
        programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId()).forEach(test -> {
            test.setWeight(42.0);
            programmingExerciseTestCaseRepository.saveAndFlush(test);
        });

        final var testCasesResponse = request.patchWithResponseBody(ROOT + endpoint, "{}", new TypeReference<List<ProgrammingExerciseTestCase>>() {
        }, HttpStatus.OK);
        // Otherwise the HashSet for comparison can't be created because exercise id is used for the hashCode
        testCasesResponse.forEach(testCase -> testCase.setExercise(programmingExercise));
        final var testsInDB = programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId());

        assertThat(testCasesResponse).containsExactlyInAnyOrderElementsOf(testsInDB);
        assertThat(testsInDB).allSatisfy(test -> assertThat(test.getWeight()).isEqualTo(1));
        assertThat(testsInDB).allSatisfy(test -> assertThat(test.getBonusMultiplier()).isEqualTo(1.0));
        assertThat(testsInDB).allSatisfy(test -> assertThat(test.getBonusPoints()).isEqualTo(0.0));
    }

    void resetTestCaseWeights_instructorInWrongCourse_forbidden() throws Exception {
        database.addInstructor("other-instructors", "other-instructor");
        final var endpoint = ProgrammingExerciseTestCaseResource.Endpoints.RESET.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));
        request.patchWithResponseBody(ROOT + endpoint, "{}", String.class, HttpStatus.FORBIDDEN);
    }

    void lockAllRepositories_asStudent_forbidden() throws Exception {
        final var endpoint = ProgrammingExerciseResourceEndpoints.LOCK_ALL_REPOSITORIES.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));
        request.put(ROOT + endpoint, null, HttpStatus.FORBIDDEN);
    }

    void lockAllRepositories_asTutor_forbidden() throws Exception {
        final var endpoint = ProgrammingExerciseResourceEndpoints.LOCK_ALL_REPOSITORIES.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));
        request.put(ROOT + endpoint, null, HttpStatus.FORBIDDEN);
    }

    void lockAllRepositories() throws Exception {
        mockDelegate.mockSetRepositoryPermissionsToReadOnly(participation1.getVcsRepositoryUrl(), programmingExercise.getProjectKey(), participation1.getStudents());
        mockDelegate.mockSetRepositoryPermissionsToReadOnly(participation2.getVcsRepositoryUrl(), programmingExercise.getProjectKey(), participation2.getStudents());

        final var endpoint = ProgrammingExerciseResourceEndpoints.LOCK_ALL_REPOSITORIES.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));
        request.put(ROOT + endpoint, null, HttpStatus.OK);

        verify(versionControlService, times(1)).setRepositoryPermissionsToReadOnly(participation1.getVcsRepositoryUrl(), programmingExercise.getProjectKey(),
                participation1.getStudents());
        verify(versionControlService, times(1)).setRepositoryPermissionsToReadOnly(participation2.getVcsRepositoryUrl(), programmingExercise.getProjectKey(),
                participation2.getStudents());

        database.changeUser("instructor1");

        var notifications = request.getList("/api/notifications", HttpStatus.OK, Notification.class);
        assertThat(notifications).as("Instructor get notified that lock operations were successful")
                .anyMatch(n -> n.getText().contains(Constants.PROGRAMMING_EXERCISE_SUCCESSFUL_LOCK_OPERATION_NOTIFICATION))
                .noneMatch(n -> n.getText().contains(Constants.PROGRAMMING_EXERCISE_FAILED_LOCK_OPERATIONS_NOTIFICATION));
    }

    void unlockAllRepositories_asStudent_forbidden() throws Exception {
        final var endpoint = ProgrammingExerciseResourceEndpoints.UNLOCK_ALL_REPOSITORIES.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));
        request.put(ROOT + endpoint, null, HttpStatus.FORBIDDEN);
    }

    void unlockAllRepositories_asTutor_forbidden() throws Exception {
        final var endpoint = ProgrammingExerciseResourceEndpoints.UNLOCK_ALL_REPOSITORIES.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));
        request.put(ROOT + endpoint, null, HttpStatus.FORBIDDEN);
    }

    void unlockAllRepositories() throws Exception {
        mockDelegate.mockConfigureRepository(programmingExercise, participation1.getParticipantIdentifier(), participation1.getStudents(), true);
        mockDelegate.mockConfigureRepository(programmingExercise, participation2.getParticipantIdentifier(), participation2.getStudents(), true);
        mockDelegate.mockDefaultBranch(programmingExercise);

        final var endpoint = ProgrammingExerciseResourceEndpoints.UNLOCK_ALL_REPOSITORIES.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));
        request.put(ROOT + endpoint, null, HttpStatus.OK);

        verify(versionControlService, times(1)).configureRepository(programmingExercise, participation1, true);
        verify(versionControlService, times(1)).configureRepository(programmingExercise, participation2, true);

        database.changeUser("instructor1");

        var notifications = request.getList("/api/notifications", HttpStatus.OK, Notification.class);
        assertThat(notifications).as("Instructor get notified that unlock operations were successful")
                .anyMatch(n -> n.getText().contains(Constants.PROGRAMMING_EXERCISE_SUCCESSFUL_UNLOCK_OPERATION_NOTIFICATION))
                .noneMatch(n -> n.getText().contains(Constants.PROGRAMMING_EXERCISE_FAILED_UNLOCK_OPERATIONS_NOTIFICATION));
    }

    void testCheckPlagiarism() throws Exception {
        database.addCourseWithOneProgrammingExercise();
        var programmingExercise = programmingExerciseRepository.findAllWithEagerTemplateAndSolutionParticipations().get(0);
        prepareTwoRepositoriesForPlagiarismChecks(programmingExercise);

        final var path = ROOT + CHECK_PLAGIARISM.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));
        var result = request.get(path, HttpStatus.OK, TextPlagiarismResult.class, database.getDefaultPlagiarismOptions());
        assertPlagiarismResult(programmingExercise, result, 100.0);
    }

    void testCheckPlagiarismJplagReport() throws Exception {
        database.addCourseWithOneProgrammingExercise();
        var programmingExercise = programmingExerciseRepository.findAllWithEagerTemplateAndSolutionParticipations().get(0);
        prepareTwoRepositoriesForPlagiarismChecks(programmingExercise);

        final var path = ROOT + CHECK_PLAGIARISM_JPLAG_REPORT.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));
        var jplagZipArchive = request.getFile(path, HttpStatus.OK, database.getDefaultPlagiarismOptions());
        assertThat(jplagZipArchive).isNotNull();
        assertThat(jplagZipArchive).exists();
        try (ZipFile zipFile = new ZipFile(jplagZipArchive)) {
            assertThat(zipFile.getEntry("index.html")).isNotNull();
            assertThat(zipFile.getEntry("match0.html")).isNotNull();
            assertThat(zipFile.getEntry("matches_avg.csv")).isNotNull();
            // only one match exists
            assertThat(zipFile.getEntry("match1.html")).isNull();
        }
    }

    private void assertPlagiarismResult(ProgrammingExercise programmingExercise, TextPlagiarismResult result, double expectedSimilarity) {
        assertThat(result.getComparisons()).hasSize(1);
        assertThat(result.getExercise().getId()).isEqualTo(programmingExercise.getId());

        PlagiarismComparison<TextSubmissionElement> comparison = result.getComparisons().iterator().next();
        assertThat(comparison.getSimilarity()).isEqualTo(expectedSimilarity, Offset.offset(0.0001));
        assertThat(comparison.getStatus()).isEqualTo(PlagiarismStatus.NONE);
        assertThat(comparison.getMatches()).hasSize(1);
    }

    private void prepareTwoRepositoriesForPlagiarismChecks(ProgrammingExercise programmingExercise) throws IOException, GitAPIException {
        var participationStudent1 = database.addStudentParticipationForProgrammingExercise(programmingExercise, "student1");
        var participationStudent2 = database.addStudentParticipationForProgrammingExercise(programmingExercise, "student2");
        var submissionStudent1 = database.createProgrammingSubmission(participationStudent1, false);
        var submissionStudent2 = database.createProgrammingSubmission(participationStudent2, false);
        database.addResultToSubmission(submissionStudent1, AssessmentType.AUTOMATIC, null);
        database.addResultToSubmission(submissionStudent2, AssessmentType.AUTOMATIC, null);

        var jPlagReposDir = Path.of(repoDownloadClonePath, "jplag-repos").toString();
        var projectKey = programmingExercise.getProjectKey();

        var exampleProgram = """
                public class Main {

                    /**
                     * DO NOT EDIT!
                     */
                    public static void main(String[] args) {
                        Main main = new Main();
                        int magicNumber = main.calculateMagicNumber();

                        System.out.println("Magic number: " + magicNumber);
                    }

                    /**
                     * Calculate the magic number.
                     *
                     * @return the magic number.
                     */
                    private int calculateMagicNumber() {
                        int a = 0;
                        int b = 5;
                        int magicNumber = 0;

                        while (a < b) {
                            magicNumber += b;
                            a++;
                        }

                        return magicNumber;
                    }
                }
                """;

        Files.createDirectories(Path.of(jPlagReposDir, projectKey));
        Path file1 = Files.createFile(Path.of(jPlagReposDir, projectKey, "Submission-1.java"));
        Files.writeString(file1, exampleProgram);
        Path file2 = Files.createFile(Path.of(jPlagReposDir, projectKey, "Submission-2.java"));
        Files.writeString(file2, exampleProgram);

        doReturn(jPlagReposDir).when(fileService).getUniquePathString(any());
        doReturn(null).when(urlService).getRepositorySlugFromRepositoryUrl(any());

        var repository1 = gitService.getExistingCheckedOutRepositoryByLocalPath(localRepoFile.toPath(), null);
        var repository2 = gitService.getExistingCheckedOutRepositoryByLocalPath(localRepoFile2.toPath(), null);
        doReturn(repository1).when(gitService).getOrCheckoutRepository(eq(participation1.getVcsRepositoryUrl()), anyString(), anyBoolean());
        doReturn(repository2).when(gitService).getOrCheckoutRepository(eq(participation2.getVcsRepositoryUrl()), anyString(), anyBoolean());
    }

    void testGetPlagiarismResult() throws Exception {
        database.addCourseWithOneProgrammingExercise();
        ProgrammingExercise programmingExercise = this.programmingExerciseRepository.findAllWithEagerTemplateAndSolutionParticipations().get(0);

        TextPlagiarismResult expectedResult = database.createTextPlagiarismResultForExercise(programmingExercise);

        TextPlagiarismResult result = request.get("/api/programming-exercises/" + programmingExercise.getId() + "/plagiarism-result", HttpStatus.OK, TextPlagiarismResult.class);
        assertThat(result.getId()).isEqualTo(expectedResult.getId());
    }

    void testGetPlagiarismResultWithoutResult() throws Exception {
        database.addCourseWithOneProgrammingExercise();
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findAllWithEagerTemplateAndSolutionParticipations().get(0);
        var result = request.get("/api/programming-exercises/" + programmingExercise.getId() + "/plagiarism-result", HttpStatus.OK, String.class);
        assertThat(result).isNullOrEmpty();
    }

    void testGetPlagiarismResultWithoutExercise() throws Exception {
        TextPlagiarismResult result = request.get("/api/programming-exercises/" + 1 + "/plagiarism-result", HttpStatus.NOT_FOUND, TextPlagiarismResult.class);
        assertThat(result).isNull();
    }

    void testValidateValidAuxiliaryRepository() throws Exception {
        AuxiliaryRepositoryBuilder auxRepoBuilder = AuxiliaryRepositoryBuilder.defaults();
        testAuxRepo(auxRepoBuilder, HttpStatus.CREATED);
    }

    void testValidateAuxiliaryRepositoryIdSetOnRequest() throws Exception {
        testAuxRepo(AuxiliaryRepositoryBuilder.defaults().withId(0L), HttpStatus.BAD_REQUEST);

    }

    void testValidateAuxiliaryRepositoryWithoutName() throws Exception {
        testAuxRepo(AuxiliaryRepositoryBuilder.defaults().withoutName(), HttpStatus.BAD_REQUEST);
        testAuxRepo(AuxiliaryRepositoryBuilder.defaults().withName(""), HttpStatus.BAD_REQUEST);
    }

    void testValidateAuxiliaryRepositoryWithTooLongName() throws Exception {
        testAuxRepo(AuxiliaryRepositoryBuilder.defaults().withName(generateStringWithMoreThanNCharacters(AuxiliaryRepository.MAX_NAME_LENGTH)), HttpStatus.BAD_REQUEST);
    }

    void testValidateAuxiliaryRepositoryWithDuplicatedName() throws Exception {
        testAuxRepo(List.of(AuxiliaryRepositoryBuilder.defaults().get(), AuxiliaryRepositoryBuilder.defaults().withoutCheckoutDirectory().get()), HttpStatus.BAD_REQUEST);
    }

    void testValidateAuxiliaryRepositoryWithRestrictedName() throws Exception {
        for (RepositoryType repositoryType : RepositoryType.values()) {
            testAuxRepo(AuxiliaryRepositoryBuilder.defaults().withName(repositoryType.getName()), HttpStatus.BAD_REQUEST);
        }
    }

    void testValidateAuxiliaryRepositoryWithInvalidCheckoutDirectory() throws Exception {
        testAuxRepo(AuxiliaryRepositoryBuilder.defaults().withCheckoutDirectory("..."), HttpStatus.BAD_REQUEST);
    }

    void testValidateAuxiliaryRepositoryWithoutCheckoutDirectory() throws Exception {
        AuxiliaryRepositoryBuilder auxRepoBuilder = AuxiliaryRepositoryBuilder.defaults().withoutCheckoutDirectory();
        testAuxRepo(auxRepoBuilder, HttpStatus.CREATED);
    }

    void testValidateAuxiliaryRepositoryWithBlankCheckoutDirectory() throws Exception {
        AuxiliaryRepositoryBuilder auxRepoBuilder = AuxiliaryRepositoryBuilder.defaults().withCheckoutDirectory("   ");
        testAuxRepo(auxRepoBuilder, HttpStatus.CREATED);
    }

    void testValidateAuxiliaryRepositoryWithTooLongCheckoutDirectory() throws Exception {
        testAuxRepo(AuxiliaryRepositoryBuilder.defaults().withCheckoutDirectory(generateStringWithMoreThanNCharacters(AuxiliaryRepository.MAX_CHECKOUT_DIRECTORY_LENGTH)),
                HttpStatus.BAD_REQUEST);
    }

    void testValidateAuxiliaryRepositoryWithDuplicatedCheckoutDirectory() throws Exception {
        testAuxRepo(List.of(AuxiliaryRepositoryBuilder.defaults().get(), AuxiliaryRepositoryBuilder.defaults().withDifferentName().get()), HttpStatus.BAD_REQUEST);
    }

    void testValidateAuxiliaryRepositoryWithNullCheckoutDirectory() throws Exception {
        testAuxRepo(List.of(AuxiliaryRepositoryBuilder.defaults().get(), AuxiliaryRepositoryBuilder.defaults().withDifferentName().withoutCheckoutDirectory().get(),
                AuxiliaryRepositoryBuilder.defaults().get()), HttpStatus.BAD_REQUEST);
    }

    void testValidateAuxiliaryRepositoryWithTooLongDescription() throws Exception {
        testAuxRepo(AuxiliaryRepositoryBuilder.defaults().withDescription(generateStringWithMoreThanNCharacters(500)), HttpStatus.BAD_REQUEST);
    }

    void testValidateAuxiliaryRepositoryWithoutDescription() throws Exception {
        AuxiliaryRepositoryBuilder auxRepoBuilder = AuxiliaryRepositoryBuilder.defaults().withoutDescription();
        testAuxRepo(auxRepoBuilder, HttpStatus.CREATED);
    }

    void testGetAuxiliaryRepositoriesMissingExercise() throws Exception {
        request.get(defaultGetAuxReposEndpoint(-1L), HttpStatus.NOT_FOUND, List.class);
    }

    void testGetAuxiliaryRepositoriesOk() throws Exception {
        programmingExercise = programmingExerciseRepository.findWithAuxiliaryRepositoriesById(programmingExercise.getId()).orElseThrow();
        programmingExercise.addAuxiliaryRepository(auxiliaryRepositoryRepository.save(AuxiliaryRepositoryBuilder.defaults().get()));
        programmingExercise
                .addAuxiliaryRepository(auxiliaryRepositoryRepository.save(AuxiliaryRepositoryBuilder.defaults().withDifferentName().withDifferentCheckoutDirectory().get()));
        programmingExerciseRepository.save(programmingExercise);
        var returnedAuxiliaryRepositories = request.get(defaultGetAuxReposEndpoint(), HttpStatus.OK, List.class);
        assertThat(returnedAuxiliaryRepositories).hasSize(2);
    }

    void testGetAuxiliaryRepositoriesEmptyOk() throws Exception {
        programmingExercise = programmingExerciseRepository.findWithAuxiliaryRepositoriesById(programmingExercise.getId()).orElseThrow();
        var returnedAuxiliaryRepositories = request.get(defaultGetAuxReposEndpoint(), HttpStatus.OK, List.class);
        assertThat(returnedAuxiliaryRepositories).isEmpty();
    }

    void testGetAuxiliaryRepositoriesForbidden() throws Exception {
        request.get(defaultGetAuxReposEndpoint(), HttpStatus.FORBIDDEN, List.class);
    }

    void testRecreateBuildPlansForbidden() throws Exception {
        request.put(defaultRecreateBuildPlanEndpoint(), programmingExercise, HttpStatus.FORBIDDEN);
    }

    void testRecreateBuildPlansExerciseNotFound() throws Exception {
        request.put(defaultRecreateBuildPlanEndpoint(-1L), programmingExercise, HttpStatus.NOT_FOUND);
    }

    void testRecreateBuildPlansExerciseSuccess() throws Exception {
        addAuxiliaryRepositoryToExercise();
        mockDelegate.mockGetProjectKeyFromAnyUrl(programmingExercise.getProjectKey());
        String templateBuildPlanName = programmingExercise.getProjectKey() + "-" + TEMPLATE.getName();
        String solutionBuildPlanName = programmingExercise.getProjectKey() + "-" + SOLUTION.getName();
        mockDelegate.mockGetBuildPlan(programmingExercise.getProjectKey(), templateBuildPlanName, true, true, false, false);
        mockDelegate.mockGetBuildPlan(programmingExercise.getProjectKey(), solutionBuildPlanName, true, true, false, false);
        mockDelegate.mockDeleteBuildPlan(programmingExercise.getProjectKey(), templateBuildPlanName, false);
        mockDelegate.mockDeleteBuildPlan(programmingExercise.getProjectKey(), solutionBuildPlanName, false);
        mockDelegate.mockConnectorRequestsForSetup(programmingExercise, false);
        request.put(defaultRecreateBuildPlanEndpoint(), programmingExercise, HttpStatus.OK);
    }

    void testExportAuxiliaryRepositoryForbidden() throws Exception {
        AuxiliaryRepository repository = addAuxiliaryRepositoryToExercise();
        request.get(defaultExportInstructorAuxiliaryRepository(repository), HttpStatus.FORBIDDEN, File.class);
    }

    void testExportAuxiliaryRepositoryBadRequest() throws Exception {
        AuxiliaryRepository repository = addAuxiliaryRepositoryToExercise();
        request.get(defaultExportInstructorAuxiliaryRepository(repository), HttpStatus.BAD_REQUEST, File.class);
    }

    void testExportAuxiliaryRepositoryExerciseNotFound() throws Exception {
        request.get(defaultExportInstructorAuxiliaryRepository(-1L, 1L), HttpStatus.NOT_FOUND, File.class);
    }

    void testExportAuxiliaryRepositoryRepositoryNotFound() throws Exception {
        request.get(defaultExportInstructorAuxiliaryRepository(programmingExercise.getId(), -1L), HttpStatus.NOT_FOUND, File.class);
    }

    private String generateStringWithMoreThanNCharacters(int n) {
        return IntStream.range(0, n + 1).mapToObj(unused -> "a").reduce("", String::concat);
    }

    private AuxiliaryRepository addAuxiliaryRepositoryToExercise() {
        AuxiliaryRepository repository = AuxiliaryRepositoryBuilder.defaults().get();
        auxiliaryRepositoryRepository.save(repository);
        programmingExercise.setAuxiliaryRepositories(new ArrayList<>());
        programmingExercise.addAuxiliaryRepository(repository);
        programmingExerciseRepository.save(programmingExercise);
        return repository;
    }

    private String defaultAuxiliaryRepositoryEndpoint() {
        return ROOT + SETUP;
    }

    private String defaultRecreateBuildPlanEndpoint() {
        return defaultRecreateBuildPlanEndpoint(programmingExercise.getId());
    }

    private String defaultGetAuxReposEndpoint() {
        return defaultGetAuxReposEndpoint(programmingExercise.getId());
    }

    private String defaultExportInstructorAuxiliaryRepository(AuxiliaryRepository repository) {
        return defaultExportInstructorAuxiliaryRepository(programmingExercise.getId(), repository.getId());
    }

    private String defaultRecreateBuildPlanEndpoint(Long exerciseId) {
        return ROOT + RECREATE_BUILD_PLANS.replace("{exerciseId}", exerciseId.toString());
    }

    private String defaultGetAuxReposEndpoint(Long exerciseId) {
        return ROOT + AUXILIARY_REPOSITORY.replace("{exerciseId}", exerciseId.toString());
    }

    private String defaultExportInstructorAuxiliaryRepository(Long exerciseId, Long repositoryId) {
        return ROOT + EXPORT_INSTRUCTOR_AUXILIARY_REPOSITORY.replace("{exerciseId}", exerciseId.toString()).replace("{repositoryId}", repositoryId.toString());
    }

    private void testAuxRepo(AuxiliaryRepositoryBuilder body, HttpStatus expectedStatus) throws Exception {
        testAuxRepo(List.of(body.get()), expectedStatus);
    }

    private void testAuxRepo(List<AuxiliaryRepository> body, HttpStatus expectedStatus) throws Exception {
        String uniqueExerciseTitle = String.format("Title%d%d", System.nanoTime(), ThreadLocalRandom.current().nextInt(100));
        programmingExercise.setAuxiliaryRepositories(body);
        programmingExercise.setId(null);
        programmingExercise.setSolutionParticipation(null);
        programmingExercise.setTemplateParticipation(null);
        programmingExercise.setShortName(uniqueExerciseTitle);
        programmingExercise.setTitle(uniqueExerciseTitle);
        if (expectedStatus == HttpStatus.CREATED) {
            mockDelegate.mockConnectorRequestsForSetup(programmingExercise, false);
            mockDelegate.mockGetProjectKeyFromAnyUrl(programmingExercise.getProjectKey());
        }
        request.postWithResponseBody(defaultAuxiliaryRepositoryEndpoint(), programmingExercise, ProgrammingExercise.class, expectedStatus);
    }

    private static class AuxiliaryRepositoryBuilder {

        private final AuxiliaryRepository repository;

        private AuxiliaryRepositoryBuilder() {
            this.repository = new AuxiliaryRepository();
        }

        static AuxiliaryRepositoryBuilder of() {
            return new AuxiliaryRepositoryBuilder();
        }

        static AuxiliaryRepositoryBuilder defaults() {
            return of().withoutId().withName("defaultname").withCheckoutDirectory("directory").withDescription("DefaultDescription");
        }

        AuxiliaryRepositoryBuilder withName(String name) {
            repository.setName(name);
            return this;
        }

        AuxiliaryRepositoryBuilder withoutName() {
            repository.setName(null);
            return this;
        }

        AuxiliaryRepositoryBuilder withDifferentName() {
            repository.setName("differentname");
            return this;
        }

        AuxiliaryRepositoryBuilder withDescription(String description) {
            repository.setDescription(description);
            return this;
        }

        AuxiliaryRepositoryBuilder withoutDescription() {
            repository.setDescription(null);
            return this;
        }

        AuxiliaryRepositoryBuilder withCheckoutDirectory(String checkoutDirectory) {
            repository.setCheckoutDirectory(checkoutDirectory);
            return this;
        }

        AuxiliaryRepositoryBuilder withoutCheckoutDirectory() {
            repository.setCheckoutDirectory(null);
            return this;
        }

        AuxiliaryRepositoryBuilder withDifferentCheckoutDirectory() {
            repository.setCheckoutDirectory("differentcheckoutdirectory");
            return this;
        }

        AuxiliaryRepositoryBuilder withId(Long id) {
            repository.setId(id);
            return this;
        }

        AuxiliaryRepositoryBuilder withoutId() {
            repository.setId(null);
            return this;
        }

        AuxiliaryRepository get() {
            return repository;
        }
    }

    void testReEvaluateAndUpdateProgrammingExercise_instructorNotInCourse_forbidden() throws Exception {
        database.addInstructor("other-instructors", "instructoralt");
        database.addCourseWithOneProgrammingExercise();
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findAllWithEagerTemplateAndSolutionParticipations().get(0);
        request.put("/api/programming-exercises/" + programmingExercise.getId() + "/re-evaluate", programmingExercise, HttpStatus.FORBIDDEN);
    }

    void testReEvaluateAndUpdateProgrammingExercise_notFound() throws Exception {
        request.put("/api/programming-exercises/" + 123456789 + "/re-evaluate", programmingExercise, HttpStatus.NOT_FOUND);
    }

    void testReEvaluateAndUpdateProgrammingExercise_isNotSameGivenExerciseIdInRequestBody_conflict() throws Exception {
        database.addCourseWithOneProgrammingExercise();
        database.addCourseWithOneProgrammingExercise();
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findAllWithEagerTemplateAndSolutionParticipations().get(0);
        ProgrammingExercise programmingExerciseToBeConflicted = programmingExerciseRepository.findAllWithEagerTemplateAndSolutionParticipations().get(1);

        request.put("/api/programming-exercises/" + programmingExercise.getId() + "/re-evaluate", programmingExerciseToBeConflicted, HttpStatus.CONFLICT);
    }

    public void test_redirectGetSolutionRepositoryFilesWithoutContent(BiFunction<ProgrammingExercise, Map<String, String>, LocalRepository> setupRepositoryMock) throws Exception {
        setupRepositoryMock.apply(programmingExercise, Map.ofEntries(Map.entry("A.java", "abc"), Map.entry("B.java", "cde"), Map.entry("C.java", "efg")));

        var savedExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(programmingExercise.getId());

        request.getWithForwardedUrl("/api/programming-exercises/" + programmingExercise.getId() + "/file-names", HttpStatus.OK,
                "/api/repository/" + savedExercise.getSolutionParticipation().getId() + "/file-names");
    }

    public void test_redirectGetTemplateRepositoryFilesWithContent(BiFunction<ProgrammingExercise, Map<String, String>, LocalRepository> setupRepositoryMock) throws Exception {
        setupRepositoryMock.apply(programmingExercise, Map.ofEntries(Map.entry("A.java", "abc"), Map.entry("B.java", "cde"), Map.entry("C.java", "efg")));

        var savedExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(programmingExercise.getId());

        request.getWithForwardedUrl("/api/programming-exercises/" + programmingExercise.getId() + "/template-files-content", HttpStatus.OK,
                "/api/repository/" + savedExercise.getTemplateParticipation().getId() + "/files-content");
    }
}
