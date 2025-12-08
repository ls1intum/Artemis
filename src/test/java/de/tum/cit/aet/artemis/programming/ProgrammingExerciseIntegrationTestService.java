package de.tum.cit.aet.artemis.programming;

import static de.tum.cit.aet.artemis.core.util.TestResourceUtils.HalfSecond;
import static de.tum.cit.aet.artemis.programming.domain.build.BuildPlanType.SOLUTION;
import static de.tum.cit.aet.artemis.programming.domain.build.BuildPlanType.TEMPLATE;
import static de.tum.cit.aet.artemis.programming.exception.ProgrammingExerciseErrorKeys.INVALID_SOLUTION_BUILD_PLAN_ID;
import static de.tum.cit.aet.artemis.programming.exception.ProgrammingExerciseErrorKeys.INVALID_SOLUTION_REPOSITORY_URL;
import static de.tum.cit.aet.artemis.programming.exception.ProgrammingExerciseErrorKeys.INVALID_TEMPLATE_BUILD_PLAN_ID;
import static de.tum.cit.aet.artemis.programming.exception.ProgrammingExerciseErrorKeys.INVALID_TEMPLATE_REPOSITORY_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.zip.ZipFile;

import org.apache.commons.io.FileUtils;
import org.assertj.core.data.Offset;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.fasterxml.jackson.core.type.TypeReference;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.assessment.domain.Visibility;
import de.tum.cit.aet.artemis.assessment.repository.GradingCriterionRepository;
import de.tum.cit.aet.artemis.assessment.util.GradingCriterionUtil;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.dto.RepositoryExportOptionsDTO;
import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.core.util.RequestUtilService;
import de.tum.cit.aet.artemis.core.util.TestResourceUtils;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.fileupload.util.ZipFileTestUtilService;
import de.tum.cit.aet.artemis.plagiarism.PlagiarismUtilService;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismComparison;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismResult;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismStatus;
import de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismResultDTO;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseResetOptionsDTO;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseTestCaseDTO;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseTestCaseStateDTO;
import de.tum.cit.aet.artemis.programming.icl.LocalVCLocalCITestService;
import de.tum.cit.aet.artemis.programming.repository.AuxiliaryRepositoryRepository;
import de.tum.cit.aet.artemis.programming.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.service.GitService;
import de.tum.cit.aet.artemis.programming.service.UriService;
import de.tum.cit.aet.artemis.programming.service.ci.ContinuousIntegrationService;
import de.tum.cit.aet.artemis.programming.service.vcs.VersionControlService;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseStudentParticipationTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestCaseTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.TemplateProgrammingExerciseParticipationTestRepository;
import de.tum.cit.aet.artemis.programming.util.LocalRepository;
import de.tum.cit.aet.artemis.programming.util.MockDelegate;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseFactory;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseParticipationUtilService;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.programming.util.ProgrammingUtilTestService;
import de.tum.cit.aet.artemis.programming.util.RepositoryExportTestUtil;
import de.tum.cit.aet.artemis.programming.util.TestFileUtil;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

/**
 * Note: this class should be independent of the actual VCS and CIS and contains common test logic for scenarios:
 * 1) LocalVC + LocalCI
 */
@Lazy
@Service
@Profile(SPRING_PROFILE_TEST)
public class ProgrammingExerciseIntegrationTestService {

    private static final String NON_EXISTING_ID = Integer.toString(Integer.MAX_VALUE);

    private String userPrefix;

    @Value("${artemis.version-control.default-branch:main}")
    private String defaultBranch;

    @Value("${artemis.version-control.local-vcs-repo-path}")
    private Path localVCBasePath;

    @Value("${artemis.temp-path}")
    private Path tempPath;

    @Value("${artemis.version-control.url}")
    private URI localVCBaseUri;

    @Autowired
    // this will be a MockitoSpyBean because it was configured as MockitoSpyBean in the super class of the actual test class (see AbstractArtemisIntegrationTest)
    private FileService fileService;

    @Value("${server.port}")
    private int serverPort;

    @Autowired
    // this will be a MockitoSpyBean because it was configured as MockitoSpyBean in the super class of the actual test class (see AbstractArtemisIntegrationTest)
    private UriService uriService;

    @Autowired
    private CourseTestRepository courseRepository;

    @Autowired
    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    @Autowired
    private ProgrammingExerciseStudentParticipationTestRepository programmingExerciseStudentParticipationRepository;

    @Autowired
    private ProgrammingExerciseTestCaseTestRepository programmingExerciseTestCaseRepository;

    @Autowired
    private AuxiliaryRepositoryRepository auxiliaryRepositoryRepository;

    @Autowired
    private RequestUtilService request;

    @Autowired
    // this will be a MockitoSpyBean because it was configured as MockitoSpyBean in the super class of the actual test class (see AbstractArtemisIntegrationTest)
    private GitService gitService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ProgrammingExerciseParticipationUtilService programmingExerciseParticipationUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private PlagiarismUtilService plagiarismUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ProgrammingExerciseTestRepository programmingExerciseTestRepository;

    @Autowired
    private ZipFileTestUtilService zipFileTestUtilService;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private GradingCriterionRepository gradingCriterionRepository;

    @Autowired
    private ProgrammingUtilTestService programmingUtilTestService;

    @Autowired
    private LocalVCLocalCITestService localVCLocalCITestService;

    @Autowired
    private TemplateProgrammingExerciseParticipationTestRepository templateProgrammingExerciseParticipationRepository;

    @Autowired
    private SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    private Course course;

    public ProgrammingExercise programmingExercise;

    private ProgrammingExercise programmingExerciseInExam;

    private ProgrammingExerciseStudentParticipation participation1;

    private ProgrammingExerciseStudentParticipation participation2;

    private File downloadedFile;

    private LocalRepository studentRepository1;

    private LocalRepository studentRepository2;

    private MockDelegate mockDelegate;

    // this will be a MockitoSpyBean because it was configured as MockitoSpyBean in the super class of the actual test class (see AbstractArtemisIntegrationTest)
    private VersionControlService versionControlService;

    // this will be a MockitoSpyBean because it was configured as MockitoSpyBean in the super class of the actual test class (see AbstractArtemisIntegrationTest)
    private ContinuousIntegrationService continuousIntegrationService;

    private File plagiarismChecksTestReposDir;

    void setup(String userPrefix, MockDelegate mockDelegate, VersionControlService versionControlService, ContinuousIntegrationService continuousIntegrationService)
            throws Exception {
        this.userPrefix = userPrefix;
        this.mockDelegate = mockDelegate;
        this.versionControlService = versionControlService; // this can be used like a MockitoSpyBean
        this.continuousIntegrationService = continuousIntegrationService; // this can be used like a MockitoSpyBean
        localVCLocalCITestService.setPort(serverPort);

        userUtilService.addUsers(userPrefix, 3, 2, 2, 2);
        course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        programmingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationAndBuildConfigById(programmingExercise.getId()).orElseThrow();
        programmingExerciseInExam = programmingExerciseUtilService.addCourseExamExerciseGroupWithOneProgrammingExerciseAndTestCases();
        programmingExerciseInExam = programmingExerciseRepository.findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(programmingExerciseInExam.getId())
                .orElseThrow();

        participation1 = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, userPrefix + "student1");
        participation2 = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, userPrefix + "student2");

        participationUtilService.addStudentParticipationForProgrammingExercise(programmingExerciseInExam, userPrefix + "student1");
        participationUtilService.addStudentParticipationForProgrammingExercise(programmingExerciseInExam, userPrefix + "student2");

        studentRepository1 = RepositoryExportTestUtil.trackRepository(new LocalRepository(defaultBranch));
        studentRepository2 = RepositoryExportTestUtil.trackRepository(new LocalRepository(defaultBranch));

        studentRepository1.configureRepos(localVCBasePath, "studentLocalRepo1", "studentOriginRepo1", true);
        studentRepository2.configureRepos(localVCBasePath, "studentLocalRepo2", "studentOriginRepo2", true);

        // TODO use createProgrammingExercise or setupTemplateAndPush to create actual content (based on the template repos) in this repository
        // so that e.g. addStudentIdToProjectName in ProgrammingExerciseExportService is tested properly as well

        // the following 2 lines prepare the generation of the structural test oracle
        var testjsonFilePath = studentRepository1.workingCopyGitRepoFile.toPath().resolve("test").resolve(programmingExercise.getPackageFolderName()).resolve("test.json");
        TestFileUtil.writeEmptyJsonFileToPath(testjsonFilePath);
        // create two empty commits
        GitService.commit(studentRepository1.workingCopyGitRepo).setMessage("empty").setAllowEmpty(true).setSign(false).setAuthor("test", "test@test.com").call();
        studentRepository1.workingCopyGitRepo.push().call();

        this.plagiarismChecksTestReposDir = Files.createTempDirectory(tempPath, "jplag-repos").toFile();
    }

    void tearDown() throws IOException {
        RepositoryExportTestUtil.cleanupTrackedRepositories();
        if (downloadedFile != null && downloadedFile.exists()) {
            FileUtils.forceDelete(downloadedFile);
        }
        if (plagiarismChecksTestReposDir != null && plagiarismChecksTestReposDir.exists()) {
            RepositoryExportTestUtil.safeDeleteDirectory(plagiarismChecksTestReposDir.toPath());
        }
    }

    /**
     * Deletes the LocalVC project folder for the given project key if present.
     * Useful to avoid false positives in uniqueness checks between tests.
     */
    public void cleanupLocalVcProjectForKey(String projectKey) {
        try {
            RepositoryExportTestUtil.deleteLocalVcProjectIfPresent(localVCBasePath, projectKey);
        }
        catch (IOException ignored) {
            // best-effort cleanup
        }
    }

    void testProgrammingExerciseIsReleased_IsReleasedAndHasResults() throws Exception {
        programmingExercise.setReleaseDate(ZonedDateTime.now().minusHours(5L));
        programmingExerciseRepository.save(programmingExercise);
        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(programmingExercise, userPrefix + "student1");
        var submission = participationUtilService.addSubmission(participation, new ProgrammingSubmission());
        participationUtilService.addResultToSubmission(null, null, submission);

        ProgrammingExerciseTestCaseStateDTO releaseStateDTO = request.get("/api/programming/programming-exercises/" + programmingExercise.getId() + "/test-case-state",
                HttpStatus.OK, ProgrammingExerciseTestCaseStateDTO.class);
        assertThat(releaseStateDTO.released()).isTrue();
        assertThat(releaseStateDTO.hasStudentResult()).isTrue();
        assertThat(releaseStateDTO.testCasesChanged()).isFalse();
    }

    void testProgrammingExerciseIsReleased_IsNotReleasedAndHasResults() throws Exception {
        programmingExercise.setReleaseDate(ZonedDateTime.now().plusHours(5L));
        programmingExerciseRepository.save(programmingExercise);
        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(programmingExercise, userPrefix + "student1");
        var submission = participationUtilService.addSubmission(participation, new ProgrammingSubmission());
        participationUtilService.addResultToSubmission(null, null, submission);

        ProgrammingExerciseTestCaseStateDTO releaseStateDTO = request.get("/api/programming/programming-exercises/" + programmingExercise.getId() + "/test-case-state",
                HttpStatus.OK, ProgrammingExerciseTestCaseStateDTO.class);
        assertThat(releaseStateDTO.released()).isFalse();
        assertThat(releaseStateDTO.hasStudentResult()).isTrue();
        assertThat(releaseStateDTO.testCasesChanged()).isFalse();
    }

    void checkIfProgrammingExerciseIsReleased_IsReleasedAndHasNoResults() throws Exception {
        programmingExercise.setReleaseDate(ZonedDateTime.now().minusHours(5L));
        programmingExercise.setTestCasesChanged(true);
        programmingExerciseRepository.save(programmingExercise);

        ProgrammingExerciseTestCaseStateDTO releaseStateDTO = request.get("/api/programming/programming-exercises/" + programmingExercise.getId() + "/test-case-state",
                HttpStatus.OK, ProgrammingExerciseTestCaseStateDTO.class);
        assertThat(releaseStateDTO.released()).isTrue();
        assertThat(releaseStateDTO.hasStudentResult()).isFalse();
        assertThat(releaseStateDTO.testCasesChanged()).isTrue();
    }

    void testProgrammingExerciseIsReleased_forbidden() throws Exception {
        request.get("/api/programming/programming-exercises/" + programmingExercise.getId() + "/test-case-state", HttpStatus.FORBIDDEN, Boolean.class);
    }

    List<Path> exportSubmissionsWithPracticeSubmissionByParticipationIds(boolean excludePracticeSubmissions) throws Exception {
        // Clean up any stale LocalVC project folder from parallel tests that might share the same projectKey
        cleanupLocalVcProjectForKey(programmingExercise.getProjectKey());

        // Seed LocalVC repositories for both participations and wire URIs
        RepositoryExportTestUtil.seedStudentRepositoryForParticipation(localVCLocalCITestService, participation1);
        RepositoryExportTestUtil.seedStudentRepositoryForParticipation(localVCLocalCITestService, participation2);

        // Set one of the participations to practice mode
        participation1.setPracticeMode(false);
        participation2.setPracticeMode(true);
        programmingExerciseStudentParticipationRepository.saveAll(List.of(participation1, participation2));

        // Export with excludePracticeSubmissions - only include participations for this exercise
        var participationIds = programmingExerciseStudentParticipationRepository.findByExerciseId(programmingExercise.getId()).stream()
                .map(participation -> participation.getId().toString()).toList();
        final var path = "/api/programming/programming-exercises/" + programmingExercise.getId() + "/export-repos-by-participation-ids/" + String.join(",", participationIds);
        var exportOptions = new RepositoryExportOptionsDTO(false, false, false, null, excludePracticeSubmissions, false, false, false, false);

        downloadedFile = request.postWithResponseBodyFile(path, exportOptions, HttpStatus.OK);
        assertThat(downloadedFile).exists();

        return unzipExportedFile();
    }

    void testExportSubmissionsByParticipationIds_excludePracticeSubmissions() throws Exception {
        List<Path> entries = exportSubmissionsWithPracticeSubmissionByParticipationIds(true);

        // Make sure that the practice submission is not included
        assertThat(entries).anyMatch(entry -> entry.toString().endsWith(Path.of(userPrefix + "student1", ".git").toString()))
                .noneMatch(entry -> entry.toString().matches(".*practice-[^/]*" + userPrefix + "student2.*\\.git$"));
    }

    void testExportSubmissionsByParticipationIds_includePracticeSubmissions() throws Exception {
        List<Path> entries = exportSubmissionsWithPracticeSubmissionByParticipationIds(false);

        // Make sure that the practice submission is included
        assertThat(entries).anyMatch(entry -> entry.toString().endsWith(Path.of(userPrefix + "student1", ".git").toString()))
                .anyMatch(entry -> entry.toString().matches(".*practice-[^/]*" + userPrefix + "student2.*\\.git$"));
    }

    void testExportSubmissionsByParticipationIds_addParticipantIdentifierToProjectName() throws Exception {
        // Ensure a clean LocalVC project folder so we don't reuse stale repositories from previous tests
        cleanupLocalVcProjectForKey(programmingExercise.getProjectKey());

        // Seed LocalVC student repos and wire URIs
        var repo1 = RepositoryExportTestUtil.seedStudentRepositoryForParticipation(localVCLocalCITestService, participation1);
        RepositoryExportTestUtil.seedStudentRepositoryForParticipation(localVCLocalCITestService, participation2);
        programmingExerciseStudentParticipationRepository.saveAll(List.of(participation1, participation2));

        // Create .project and pom.xml in the student's repo to be modified during export
        String projectFileContents = TestResourceUtils.loadFileFromResources("test-data/repository-export/sample.project");
        String pomContents = TestResourceUtils.loadFileFromResources("test-data/repository-export/pom.xml");
        Path projectFilePath = repo1.workingCopyGitRepoFile.toPath().resolve(".project");
        Path pomPath = repo1.workingCopyGitRepoFile.toPath().resolve("pom.xml");
        FileUtils.writeStringToFile(projectFilePath.toFile(), projectFileContents, StandardCharsets.UTF_8);
        FileUtils.writeStringToFile(pomPath.toFile(), pomContents, StandardCharsets.UTF_8);
        repo1.workingCopyGitRepo.add().addFilepattern(".").call();
        GitService.commit(repo1.workingCopyGitRepo).setMessage("seed project and pom").call();
        repo1.workingCopyGitRepo.push().setRemote("origin").call();

        var participation = programmingExerciseStudentParticipationRepository.findByExerciseIdAndStudentLogin(programmingExercise.getId(), userPrefix + "student1");
        assertThat(participation).isPresent();

        final var path = "/api/programming/programming-exercises/" + programmingExercise.getId() + "/export-repos-by-participation-ids/"
                + String.join(",", List.of(participation.get().getId().toString()));
        var exportOptions = new RepositoryExportOptionsDTO(false, false, false, null, false, true, false, false, false);

        downloadedFile = request.postWithResponseBodyFile(path, exportOptions, HttpStatus.OK);
        assertThat(downloadedFile).exists();

        // Read exported project files and assert participant identifier appended
        List<Path> entries = unzipExportedFile();
        Optional<Path> extractedRepo = entries.stream().filter(entry -> entry.toString().endsWith(Path.of(userPrefix + "student1", ".git").toString())).findFirst();
        assertThat(extractedRepo).isPresent();
        Path repoRoot = extractedRepo.get().getParent();
        String modifiedEclipseProjectFile = Files.readString(repoRoot.resolve(".project"));
        assertThat(modifiedEclipseProjectFile).contains(userPrefix + "student1");
        String modifiedPom = Files.readString(repoRoot.resolve("pom.xml"));
        assertThat(modifiedPom).contains((userPrefix + "student1").toLowerCase());
        Files.deleteIfExists(projectFilePath);
        Files.deleteIfExists(pomPath);
    }

    private static Path findFirstFile(List<Path> zipPaths, String fileName) throws IOException {
        Objects.requireNonNull(zipPaths, "zipPaths must not be null");
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("fileName must not be null/blank");
        }

        // Prefer a shallower path if multiple matches exist
        return zipPaths.stream().filter(Files::isRegularFile).filter(p -> p.getFileName().toString().equals(fileName)).min(Comparator.comparingInt(Path::getNameCount))
                .orElseThrow(() -> new FileNotFoundException("File '" + fileName + "' not found in unzipped export"));
    }

    void testExportSubmissionsByParticipationIds_addParticipantIdentifierToProjectNameError() throws Exception {
        var repository1 = gitService.getExistingCheckedOutRepositoryByLocalPath(studentRepository1.workingCopyGitRepoFile.toPath(), null);
        var repository2 = gitService.getExistingCheckedOutRepositoryByLocalPath(studentRepository2.workingCopyGitRepoFile.toPath(), null);

        // Create the eclipse .project file which will be modified.
        Path projectFilePath = Path.of(repository1.getLocalPath().toString(), ".project");
        File projectFile = Path.of(projectFilePath.toString()).toFile();
        if (!projectFile.exists()) {
            Files.createFile(projectFilePath);
        }

        // Create the maven .pom file
        Path pomPath = Path.of(repository1.getLocalPath().toString(), "pom.xml");
        File pomFile = Path.of(pomPath.toString()).toFile();
        if (!pomFile.exists()) {
            Files.createFile(pomPath);
        }

        var participation = programmingExerciseStudentParticipationRepository.findByExerciseIdAndStudentLogin(programmingExercise.getId(), userPrefix + "student1");
        assertThat(participation).isPresent();

        final var path = "/api/programming/programming-exercises/" + programmingExercise.getId() + "/export-repos-by-participation-ids/"
                + String.join(",", List.of(participation.get().getId().toString()));
        // all options false by default, only test if export works at all
        var exportOptions = new RepositoryExportOptionsDTO(false, false, false, null, false, true, false, false, false);

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
        // Seed LocalVC student repositories and wire URIs
        // use shared util to seed and wire repos for both participations
        RepositoryExportTestUtil.seedStudentRepositoryForParticipation(localVCLocalCITestService, participation1);
        RepositoryExportTestUtil.seedStudentRepositoryForParticipation(localVCLocalCITestService, participation2);
        programmingExerciseStudentParticipationRepository.saveAll(List.of(participation1, participation2));

        // Only get participations for this specific exercise to avoid interference from other tests
        var participationIds = programmingExerciseStudentParticipationRepository.findByExerciseId(programmingExercise.getId()).stream()
                .map(participation -> participation.getId().toString()).toList();
        final var path = "/api/programming/programming-exercises/" + programmingExercise.getId() + "/export-repos-by-participation-ids/" + String.join(",", participationIds);
        // all options false by default, only test if export works at all
        var exportOptions = new RepositoryExportOptionsDTO();

        downloadedFile = request.postWithResponseBodyFile(path, exportOptions, HttpStatus.OK);
        assertThat(downloadedFile).exists();

        List<Path> entries = unzipExportedFile();

        // Make sure both repositories are present (by login suffix)
        assertThat(entries).anyMatch(entry -> entry.toString().endsWith(Path.of(userPrefix + "student1", ".git").toString()))
                .anyMatch(entry -> entry.toString().endsWith(Path.of(userPrefix + "student2", ".git").toString()));
    }

    void testExportSubmissionAnonymizationCombining() throws Exception {
        // Ensure base repos exist and retrieve handles for template/solution/tests
        var baseRepositories = RepositoryExportTestUtil.createAndWireBaseRepositoriesWithHandles(localVCLocalCITestService, programmingExercise);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);

        // Prepare template working copy handle
        var templateRepo = baseRepositories.templateRepository();
        String projectKey = programmingExercise.getProjectKey();

        // Seed student repository from template bare and wire URI
        String studentSlug = localVCLocalCITestService.getRepositorySlug(projectKey, participation1.getParticipantIdentifier());
        var studentRepo = RepositoryExportTestUtil.seedLocalVcBareFrom(localVCLocalCITestService, projectKey, studentSlug, templateRepo);
        participation1.setRepositoryUri(localVCLocalCITestService.buildLocalVCUri(null, null, projectKey, studentSlug));
        programmingExerciseStudentParticipationRepository.save(participation1);

        // Add a student commit to anonymize
        localVCLocalCITestService.commitFile(studentRepo.workingCopyGitRepoFile.toPath(), studentRepo.workingCopyGitRepo, "Test.java");
        studentRepo.workingCopyGitRepo.push().setRemote("origin").call();

        // Rest call with options (combine + anonymize enabled in getOptions())
        final var path = "/api/programming/programming-exercises/" + programmingExercise.getId() + "/export-repos-by-participation-ids/" + participation1.getId();
        downloadedFile = request.postWithResponseBodyFile(path, getOptions(), HttpStatus.OK);
        assertThat(downloadedFile).exists();

        List<Path> entries = unzipExportedFile();

        // Checks: file present and single anonymized commit
        assertThat(entries).anyMatch(entry -> entry.endsWith("Test.java"));
        // The exported repo format is: courseShortName-exerciseTitle-participationId-student-submission.git
        // We match the suffix pattern that includes participation id and the anonymized suffix
        String expectedSuffix = "-" + participation1.getId() + "-student-submission.git" + File.separator + ".git";
        Optional<Path> extractedRepo1 = entries.stream().filter(entry -> entry.toString().endsWith(expectedSuffix)).findFirst();
        assertThat(extractedRepo1).as("Expected to find exported repo matching pattern *%s but entries were: %s", expectedSuffix, entries).isPresent();
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
        Path extractedZipDir = zipFileTestUtilService.extractZipFileRecursively(downloadedFile.getAbsolutePath());
        try (var files = Files.walk(extractedZipDir)) {
            return files.toList();
        }
    }

    void testExportSubmissionsByParticipationIds_nonExistentParticipationId_notFound() throws Exception {
        final var path = "/api/programming/programming-exercises/" + programmingExercise.getId() + "/export-repos-by-participation-ids/" + Long.MAX_VALUE;
        request.postWithResponseBodyFile(path, getOptions(), HttpStatus.NOT_FOUND);
    }

    void testExportSubmissionsByParticipationIds_instructorNotInCourse_forbidden() throws Exception {
        userUtilService.addInstructor("other-instructors", userPrefix + "instructoralt1");
        var participationIds = programmingExerciseStudentParticipationRepository.findAll().stream().map(participation -> participation.getId().toString()).toList();
        final var path = "/api/programming/programming-exercises/" + programmingExercise.getId() + "/export-repos-by-participation-ids/" + String.join(",", participationIds);
        request.postWithResponseBodyFile(path, getOptions(), HttpStatus.FORBIDDEN);
    }

    void testExportSubmissionsByStudentLogins() throws Exception {
        downloadedFile = exportSubmissionsByStudentLogins();
        assertThat(downloadedFile).exists();
        List<Path> entries = unzipExportedFile();
        // Assert both participant repos are included. For this endpoint/options, repos are anonymized and use the student-submission.git suffix
        assertThat(entries).anyMatch(entry -> entry.toString().endsWith("student-submission.git" + File.separator + ".git"));
    }

    private File exportSubmissionsByStudentLogins() throws Exception {
        // Seed LocalVC student repositories and wire URIs
        RepositoryExportTestUtil.seedStudentRepositoryForParticipation(localVCLocalCITestService, participation1);
        RepositoryExportTestUtil.seedStudentRepositoryForParticipation(localVCLocalCITestService, participation2);
        programmingExerciseStudentParticipationRepository.saveAll(List.of(participation1, participation2));
        final var path = "/api/programming/programming-exercises/" + programmingExercise.getId() + "/export-repos-by-participant-identifiers/" + userPrefix + "student1,"
                + userPrefix + "student2";
        return request.postWithResponseBodyFile(path, getOptions(), HttpStatus.OK);
    }

    private RepositoryExportOptionsDTO getOptions() {
        return new RepositoryExportOptionsDTO(false, true, false, null, false, true, true, true, true);
    }

    void testProgrammingExerciseDelete() throws Exception {
        final var projectKey = programmingExercise.getProjectKey();
        final var path = "/api/programming/programming-exercises/" + programmingExercise.getId();
        var params = new LinkedMultiValueMap<String, String>();
        params.add("deleteStudentReposBuildPlans", "true");
        params.add("deleteBaseReposBuildPlans", "true");

        for (final var planName : List.of(userPrefix + "student1", userPrefix + "student2", TEMPLATE.getName(), SOLUTION.getName())) {
            mockDelegate.mockDeleteBuildPlan(projectKey, projectKey + "-" + planName.toUpperCase(), false);
        }
        mockDelegate.mockDeleteBuildPlanProject(projectKey, false);
        request.delete(path, HttpStatus.OK, params);
    }

    void testProgrammingExerciseDelete_failToDeleteBuildPlan() throws Exception {
        final var projectKey = programmingExercise.getProjectKey();
        final var path = "/api/programming/programming-exercises/" + programmingExercise.getId();
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
        final var path = "/api/programming/programming-exercises/" + programmingExercise.getId();
        var params = new LinkedMultiValueMap<String, String>();
        params.add("deleteStudentReposBuildPlans", "true");
        params.add("deleteBaseReposBuildPlans", "true");

        for (final var planName : List.of(userPrefix + "student1", userPrefix + "student2", TEMPLATE.getName(), SOLUTION.getName())) {
            mockDelegate.mockDeleteBuildPlan(projectKey, projectKey + "-" + planName.toUpperCase(), false);
        }
        mockDelegate.mockDeleteBuildPlanProject(projectKey, false);

        request.delete(path, HttpStatus.OK, params);
    }

    void testProgrammingExerciseDelete_failToDeleteCiProject() throws Exception {
        final var projectKey = programmingExercise.getProjectKey();
        final var path = "/api/programming/programming-exercises/" + programmingExercise.getId();
        var params = new LinkedMultiValueMap<String, String>();
        params.add("deleteStudentReposBuildPlans", "true");
        params.add("deleteBaseReposBuildPlans", "true");

        for (final var planName : List.of(userPrefix + "student1", userPrefix + "student2", TEMPLATE.getName(), SOLUTION.getName())) {
            mockDelegate.mockDeleteBuildPlan(projectKey, projectKey + "-" + planName.toUpperCase(), false);
        }
        mockDelegate.mockDeleteBuildPlanProject(projectKey, true);

        request.delete(path, HttpStatus.INTERNAL_SERVER_ERROR, params);
    }

    void testProgrammingExerciseDelete_failToDeleteVcsProject() throws Exception {
        final var projectKey = programmingExercise.getProjectKey();
        final var path = "/api/programming/programming-exercises/" + programmingExercise.getId();
        var params = new LinkedMultiValueMap<String, String>();
        params.add("deleteStudentReposBuildPlans", "true");
        params.add("deleteBaseReposBuildPlans", "true");

        for (final var planName : List.of("student1", "student2", TEMPLATE.getName(), SOLUTION.getName(), RepositoryType.TESTS.getName())) {
            mockDelegate.mockDeleteBuildPlan(projectKey, projectKey + "-" + planName.toUpperCase(), false);
        }
        mockDelegate.mockDeleteBuildPlanProject(projectKey, false);

        request.delete(path, HttpStatus.INTERNAL_SERVER_ERROR, params);
    }

    void testProgrammingExerciseDelete_failToDeleteVcsRepositories() throws Exception {
        final var projectKey = programmingExercise.getProjectKey();
        final var path = "/api/programming/programming-exercises/" + programmingExercise.getId();
        var params = new LinkedMultiValueMap<String, String>();
        params.add("deleteStudentReposBuildPlans", "true");
        params.add("deleteBaseReposBuildPlans", "true");

        for (final var planName : List.of("student1", "student2", TEMPLATE.getName(), SOLUTION.getName())) {
            mockDelegate.mockDeleteBuildPlan(projectKey, projectKey + "-" + planName.toUpperCase(), false);
        }
        mockDelegate.mockDeleteBuildPlanProject(projectKey, false);

        request.delete(path, HttpStatus.INTERNAL_SERVER_ERROR, params);
    }

    void testProgrammingExerciseDelete_invalidId_notFound() throws Exception {
        programmingExercise.setId(getMaxProgrammingExerciseId() + 1);
        final var path = "/api/programming/programming-exercises/" + programmingExercise.getId();
        request.delete(path, HttpStatus.NOT_FOUND);
    }

    void testProgrammingExerciseDelete_instructorNotInCourse_forbidden() throws Exception {
        userUtilService.addInstructor("other-instructors", userPrefix + "instructoralt");
        final var path = "/api/programming/programming-exercises/" + programmingExercise.getId();
        request.delete(path, HttpStatus.FORBIDDEN);
    }

    void testGetProgrammingExercise() throws Exception {
        final var path = "/api/programming/programming-exercises/" + programmingExercise.getId();
        var programmingExerciseServer = request.get(path, HttpStatus.OK, ProgrammingExercise.class);
        assertThat(programmingExerciseServer.getTitle()).isEqualTo(programmingExercise.getTitle());
        // TODO add more assertions
    }

    void testGetProgrammingExerciseWithStructuredGradingInstruction() throws Exception {
        final var path = "/api/programming/programming-exercises/" + programmingExercise.getId();
        var programmingExerciseServer = request.get(path, HttpStatus.OK, ProgrammingExercise.class);
        assertThat(programmingExerciseServer.getTitle()).isEqualTo(programmingExercise.getTitle());

        exerciseUtilService.addGradingInstructionsToExercise(programmingExerciseServer);

        GradingCriterion criterionWithoutTitle = GradingCriterionUtil.findGradingCriterionByTitle(programmingExerciseServer, null);
        GradingCriterion criterionWithTitle = GradingCriterionUtil.findGradingCriterionByTitle(programmingExerciseServer, "test title");

        assertThat(criterionWithTitle.getStructuredGradingInstructions()).hasSize(3);
        assertThat(criterionWithoutTitle.getStructuredGradingInstructions()).hasSize(1);
        final String expectedDescription = "created first instruction with empty criteria for testing";
        assertThat(criterionWithoutTitle.getStructuredGradingInstructions().stream().filter(instruction -> expectedDescription.equals(instruction.getInstructionDescription()))
                .findAny()).isPresent();
    }

    void testGetProgrammingExercise_instructorNotInCourse_forbidden() throws Exception {
        userUtilService.addInstructor("other-instructors", userPrefix + "instructoralt");
        final var path = "/api/programming/programming-exercises/" + programmingExercise.getId();
        request.get(path, HttpStatus.FORBIDDEN, ProgrammingExercise.class);
    }

    void testGetProgrammingExerciseWithSetupParticipations() throws Exception {
        participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, userPrefix + "instructor1");
        final var path = "/api/programming/programming-exercises/" + programmingExercise.getId() + "/with-participations";
        var programmingExerciseServer = request.get(path, HttpStatus.OK, ProgrammingExercise.class);
        checkTemplateAndSolutionParticipationsFromServer(programmingExerciseServer);
        assertThat(programmingExerciseServer.getStudentParticipations()).isNotEmpty();
        // TODO add more assertions
    }

    void testGetProgrammingExerciseWithJustTemplateAndSolutionParticipation(boolean withSubmissionResults) throws Exception {
        participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, userPrefix + "tutor1");
        final var path = "/api/programming/programming-exercises/" + programmingExercise.getId() + "/with-template-and-solution-participation?withSubmissionResults="
                + withSubmissionResults;
        var programmingExerciseServer = request.get(path, HttpStatus.OK, ProgrammingExercise.class);
        checkTemplateAndSolutionParticipationsFromServer(programmingExerciseServer);
        assertThat(programmingExerciseServer.getStudentParticipations()).isEmpty();
    }

    void testGetProgrammingExerciseWithTemplateAndSolutionParticipationAndAuxiliaryRepositories(boolean withSubmissionResults) throws Exception {
        AuxiliaryRepository auxiliaryRepository = programmingExerciseUtilService.addAuxiliaryRepositoryToExercise(programmingExercise);
        Set<GradingCriterion> gradingCriteria = exerciseUtilService.addGradingInstructionsToExercise(programmingExercise);
        gradingCriterionRepository.saveAll(gradingCriteria);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);

        var path = "/api/programming/programming-exercises/" + programmingExercise.getId() + "/with-template-and-solution-participation" + "?withSubmissionResults="
                + withSubmissionResults + "&withGradingCriteria=" + false;
        var programmingExerciseServer = request.get(path, HttpStatus.OK, ProgrammingExercise.class);

        checkTemplateAndSolutionParticipationsFromServer(programmingExerciseServer);
        assertThat(programmingExerciseServer.getAuxiliaryRepositories()).hasSize(1).containsExactly(auxiliaryRepository);
        assertThat(programmingExerciseServer.getGradingCriteria()).isEmpty();
    }

    private void checkTemplateAndSolutionParticipationsFromServer(ProgrammingExercise programmingExerciseServer) {
        assertThat(programmingExerciseServer.getTitle()).isEqualTo(programmingExercise.getTitle());
        assertThat(programmingExerciseServer.getTemplateParticipation()).isNotNull().extracting(DomainObject::getId).isNotNull();
        assertThat(programmingExerciseServer.getSolutionParticipation()).isNotNull().extracting(DomainObject::getId).isNotNull();
    }

    void testGetProgrammingExerciseWithSetupParticipations_instructorNotInCourse_forbidden() throws Exception {
        userUtilService.addInstructor("other-instructors", userPrefix + "instructoralt1");
        final var path = "/api/programming/programming-exercises/" + programmingExercise.getId() + "/with-participations";
        request.get(path, HttpStatus.FORBIDDEN, ProgrammingExercise.class);
    }

    void testGetProgrammingExerciseWithSetupParticipations_invalidId_notFound() throws Exception {
        programmingExercise.setId(getMaxProgrammingExerciseId() + 1);
        final var path = "/api/programming/programming-exercises/" + programmingExercise.getId() + "/with-participations";
        request.get(path, HttpStatus.NOT_FOUND, ProgrammingExercise.class);
    }

    void testGetProgrammingExercisesForCourse() throws Exception {
        final var path = "/api/programming/courses/" + programmingExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/programming-exercises";
        var programmingExercisesServer = request.getList(path, HttpStatus.OK, ProgrammingExercise.class);
        assertThat(programmingExercisesServer).isNotEmpty();
        // TODO add more assertions
    }

    void testGetProgrammingExercisesForCourse_instructorNotInCourse_forbidden() throws Exception {
        userUtilService.addInstructor("other-instructors", userPrefix + "instructoralt");
        final var path = "/api/programming/courses/" + programmingExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/programming-exercises";
        request.getList(path, HttpStatus.FORBIDDEN, ProgrammingExercise.class);
    }

    void testGenerateStructureOracle() throws Exception {
        // Wire base repositories in LocalVC and ensure tests repo has the expected directory
        var baseRepos = RepositoryExportTestUtil.createAndWireBaseRepositoriesWithHandles(localVCLocalCITestService, programmingExercise);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        programmingExercise = programmingExerciseRepository.getProgrammingExerciseWithBuildConfigElseThrow(programmingExercise);

        // Use the tests repository handle from base repos instead of creating a new one
        var testsRepo = baseRepos.testsRepository();
        String testsPath = java.nio.file.Path.of("test", programmingExercise.getPackageFolderName()).toString();
        if (programmingExercise.getBuildConfig().hasSequentialTestRuns()) {
            testsPath = java.nio.file.Path.of("structural", testsPath).toString();
        }
        RepositoryExportTestUtil.writeFilesAndPush(testsRepo, Map.of(testsPath + "/.placeholder", ""), "Init tests dir");

        final var path = "/api/programming/programming-exercises/" + programmingExercise.getId() + "/generate-tests";
        var result = request.putWithResponseBody(path, programmingExercise, String.class, HttpStatus.OK);
        assertThat(result).startsWith("Successfully generated the structure oracle");
        request.putWithResponseBody(path, programmingExercise, String.class, HttpStatus.BAD_REQUEST);
    }

    void updateProgrammingExercise_invalidTemplateBuildPlan_badRequest() throws Exception {
        programmingExerciseParticipationUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);
        mockDelegate.mockCheckIfBuildPlanExists(programmingExercise.getProjectKey(), programmingExercise.getTemplateBuildPlanId(), false, false);
        request.putAndExpectError("/api/programming/programming-exercises", programmingExercise, HttpStatus.BAD_REQUEST, INVALID_TEMPLATE_BUILD_PLAN_ID);
    }

    void updateProgrammingExercise_idIsNull_badRequest() throws Exception {
        programmingExerciseParticipationUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);
        programmingExercise.setId(null);
        request.put("/api/programming/programming-exercises", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void updateProgrammingExercise_eitherCourseOrExerciseGroupSet_badRequest() throws Exception {
        // both values are not set --> bad request
        programmingExercise.setCourse(null);
        request.put("/api/programming/programming-exercises", programmingExercise, HttpStatus.BAD_REQUEST);
        // both values are set --> bad request
        programmingExerciseInExam.setCourse(course);
        request.put("/api/programming/programming-exercises", programmingExerciseInExam, HttpStatus.BAD_REQUEST);
    }

    void updateProgrammingExercise_correctlySavesTestIds() throws Exception {
        var tests = programmingExerciseUtilService.addTestCasesToProgrammingExercise(programmingExercise);
        var test1 = tests.getFirst();

        String problemStatement = "[task][taskname](test1)";
        String problemStatementWithId = "[task][taskname](<testid>%s</testid>)".formatted(test1.getId());
        programmingExercise.setProblemStatement(problemStatement);

        mockBuildPlanAndRepositoryCheck(programmingExercise);

        var response = request.putWithResponseBody("/api/programming/programming-exercises", programmingExercise, ProgrammingExercise.class, HttpStatus.OK);
        assertThat(response.getProblemStatement()).as("the REST endpoint should return a problem statement with test names").isEqualTo(problemStatement);

        programmingExercise = programmingExerciseRepository.findByIdElseThrow(programmingExercise.getId());
        assertThat(programmingExercise.getProblemStatement()).as("test saved exercise contains test ids").isEqualTo(problemStatementWithId);
    }

    private void mockBuildPlanAndRepositoryCheck(ProgrammingExercise programmingExercise) throws Exception {
        mockDelegate.mockCheckIfBuildPlanExists(programmingExercise.getProjectKey(), programmingExercise.getTemplateBuildPlanId(), true, false);
        mockDelegate.mockCheckIfBuildPlanExists(programmingExercise.getProjectKey(), programmingExercise.getSolutionBuildPlanId(), true, false);
    }

    void updateProgrammingExercise_staticCodeAnalysisMustNotChange_falseToTrue_badRequest() throws Exception {
        mockBuildPlanAndRepositoryCheck(programmingExercise);
        programmingExercise.setStaticCodeAnalysisEnabled(true);
        request.put("/api/programming/programming-exercises", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void updateProgrammingExercise_staticCodeAnalysisMustNotChange_trueToFalse_badRequest() throws Exception {
        mockBuildPlanAndRepositoryCheck(programmingExercise);
        programmingExercise.setStaticCodeAnalysisEnabled(true);
        programmingExerciseRepository.save(programmingExercise);
        programmingExercise.setStaticCodeAnalysisEnabled(false);
        request.put("/api/programming/programming-exercises", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void updateProgrammingExercise_instructorNotInCourse_forbidden() throws Exception {
        userUtilService.addInstructor("other-instructors", userPrefix + "instructoralt1");
        request.put("/api/programming/programming-exercises", programmingExercise, HttpStatus.FORBIDDEN);
    }

    void updateProgrammingExercise_invalidTemplateVcs_badRequest() throws Exception {
        programmingExerciseParticipationUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);
        mockDelegate.mockCheckIfBuildPlanExists(programmingExercise.getProjectKey(), programmingExercise.getTemplateBuildPlanId(), true, false);
        request.putAndExpectError("/api/programming/programming-exercises", programmingExercise, HttpStatus.BAD_REQUEST, INVALID_TEMPLATE_REPOSITORY_URL);
    }

    void updateProgrammingExercise_invalidSolutionBuildPlan_badRequest() throws Exception {
        programmingExerciseParticipationUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);
        programmingExerciseParticipationUtilService.addSolutionParticipationForProgrammingExercise(programmingExercise);
        mockDelegate.mockCheckIfBuildPlanExists(programmingExercise.getProjectKey(), programmingExercise.getTemplateBuildPlanId(), true, false);
        mockDelegate.mockCheckIfBuildPlanExists(programmingExercise.getProjectKey(), programmingExercise.getSolutionBuildPlanId(), false, false);

        request.putAndExpectError("/api/programming/programming-exercises", programmingExercise, HttpStatus.BAD_REQUEST, INVALID_SOLUTION_BUILD_PLAN_ID);
    }

    void updateProgrammingExercise_invalidSolutionRepository_badRequest() throws Exception {
        programmingExerciseParticipationUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);
        programmingExerciseParticipationUtilService.addSolutionParticipationForProgrammingExercise(programmingExercise);
        mockDelegate.mockCheckIfBuildPlanExists(programmingExercise.getProjectKey(), programmingExercise.getTemplateBuildPlanId(), true, false);
        mockDelegate.mockCheckIfBuildPlanExists(programmingExercise.getProjectKey(), programmingExercise.getSolutionBuildPlanId(), true, false);
        request.putAndExpectError("/api/programming/programming-exercises", programmingExercise, HttpStatus.BAD_REQUEST, INVALID_SOLUTION_REPOSITORY_URL);
    }

    void updateProgrammingExercise_checkIfBuildPlanExistsFails_badRequest() throws Exception {
        programmingExerciseParticipationUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);
        mockDelegate.mockCheckIfBuildPlanExists(programmingExercise.getProjectKey(), programmingExercise.getTemplateBuildPlanId(), true, true);
        request.putAndExpectError("/api/programming/programming-exercises", programmingExercise, HttpStatus.BAD_REQUEST, INVALID_TEMPLATE_BUILD_PLAN_ID);
    }

    /**
     * This test checks that it is not allowed to change the courseId of an exercise
     * in an update request. The request should fail with 'HttpStatus.CONFLICT'.
     */
    void updateProgrammingExerciseShouldFailWithConflictWhenUpdatingCourseId() throws Exception {
        // Create a programming exercise.
        mockBuildPlanAndRepositoryCheck(programmingExercise);

        // Create a new course with different id.
        Course newCourse = courseUtilService.createCourse();

        // Assign new course to the programming exercise.
        ProgrammingExercise newProgrammingExercise = programmingExercise;
        newProgrammingExercise.setCourse(newCourse);

        // Programming exercise update with the new course should fail.
        request.put("/api/programming/programming-exercises", newProgrammingExercise, HttpStatus.CONFLICT);
    }

    /**
     * This test checks that it is not allowed to change SCA enabled option
     */
    void updateProgrammingExerciseShouldFailWithBadRequestWhenUpdatingSCAOption() throws Exception {
        mockBuildPlanAndRepositoryCheck(programmingExercise);

        ProgrammingExercise updatedExercise = programmingExercise;
        updatedExercise.setStaticCodeAnalysisEnabled(true);

        request.put("/api/programming/programming-exercises", updatedExercise, HttpStatus.BAD_REQUEST);
    }

    void updateExerciseDueDateWithIndividualDueDateUpdate() throws Exception {
        mockBuildPlanAndRepositoryCheck(programmingExercise);

        final ZonedDateTime individualDueDate = ZonedDateTime.now().plusHours(20);

        {
            final var participations = programmingExerciseStudentParticipationRepository.findByExerciseId(programmingExercise.getId());
            participations.iterator().next().setIndividualDueDate(ZonedDateTime.now().plusHours(2));
            participations.iterator().next().setIndividualDueDate(individualDueDate);
            programmingExerciseStudentParticipationRepository.saveAll(participations);
        }

        programmingExercise.setDueDate(ZonedDateTime.now().plusHours(12));
        assertThat(programmingExercise.getDueDate()).isNotNull();
        programmingExercise.setReleaseDate(programmingExercise.getDueDate().minusDays(1));
        request.put("/api/programming/programming-exercises", programmingExercise, HttpStatus.OK);

        {
            final var participations = programmingExerciseStudentParticipationRepository.findByExerciseId(programmingExercise.getId());
            final var withNoIndividualDueDate = participations.stream().filter(participation -> participation.getIndividualDueDate() == null).toList();
            assertThat(withNoIndividualDueDate).hasSize(1);

            final var withIndividualDueDate = participations.stream().filter(participation -> participation.getIndividualDueDate() != null).toList();
            assertThat(withIndividualDueDate).hasSize(1);
            assertThat(withIndividualDueDate.getFirst().getIndividualDueDate()).isCloseTo(individualDueDate, HalfSecond());
        }
    }

    void updateExerciseRemoveDueDate() throws Exception {
        mockBuildPlanAndRepositoryCheck(programmingExercise);

        {
            final var participations = programmingExerciseStudentParticipationRepository.findByExerciseId(programmingExercise.getId());
            assertThat(participations).hasSize(2);
            participations.iterator().next().setIndividualDueDate(ZonedDateTime.now().plusHours(2));
            participations.iterator().next().setIndividualDueDate(ZonedDateTime.now().plusHours(20));
            programmingExerciseStudentParticipationRepository.saveAll(participations);
        }

        programmingExercise.setDueDate(null);
        programmingExercise.setAssessmentDueDate(null);
        request.put("/api/programming/programming-exercises", programmingExercise, HttpStatus.OK);

        {
            final var participations = programmingExerciseStudentParticipationRepository.findByExerciseId(programmingExercise.getId());
            final var withNoIndividualDueDate = participations.stream().filter(participation -> participation.getIndividualDueDate() == null).toList();
            assertThat(withNoIndividualDueDate).hasSize(2);
        }
    }

    void updateTimeline_intructorNotInCourse_forbidden() throws Exception {
        userUtilService.addInstructor("other-instructors", userPrefix + "instructoralt1");
        final var endpoint = "/api/programming/programming-exercises/timeline";
        MultiValueMap<String, String> params = new HttpHeaders();
        params.add("notificationText", "The notification text");
        request.putWithResponseBodyAndParams(endpoint, programmingExercise, ProgrammingExercise.class, HttpStatus.FORBIDDEN, params);
    }

    void updateTimeline_invalidId_notFound() throws Exception {
        programmingExercise.setId(getMaxProgrammingExerciseId() + 1);
        final var endpoint = "/api/programming/programming-exercises/timeline";
        MultiValueMap<String, String> params = new HttpHeaders();
        params.add("notificationText", "The notification text");
        request.putWithResponseBodyAndParams(endpoint, programmingExercise, ProgrammingExercise.class, HttpStatus.NOT_FOUND, params);
    }

    void updateTimeline_ok() throws Exception {
        final var endpoint = "/api/programming/programming-exercises/timeline";
        MultiValueMap<String, String> params = new HttpHeaders();
        params.add("notificationText", "The notification text");
        request.putWithResponseBodyAndParams(endpoint, programmingExercise, ProgrammingExercise.class, HttpStatus.OK, params);
    }

    void updateProblemStatement_instructorNotInCourse_forbidden() throws Exception {
        userUtilService.addInstructor("other-instructors", userPrefix + "instructoralt");
        final var endpoint = "/api/programming/programming-exercises/" + programmingExercise.getId() + "/problem-statement";
        request.patchWithResponseBody(endpoint, "a new problem statement", ProgrammingExercise.class, HttpStatus.FORBIDDEN, MediaType.TEXT_PLAIN);
    }

    void updateProblemStatement_invalidId_notFound() throws Exception {
        programmingExercise.setId(getMaxProgrammingExerciseId() + 1);
        final var endpoint = "/api/programming/programming-exercises/" + programmingExercise.getId() + "/problem-statement";
        request.patchWithResponseBody(endpoint, "a new problem statement", ProgrammingExercise.class, HttpStatus.NOT_FOUND, MediaType.TEXT_PLAIN);
    }

    void createProgrammingExercise_exerciseIsNull_badRequest() throws Exception {
        request.post("/api/programming/programming-exercises/setup", null, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_idIsNotNull_badRequest() throws Exception {
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_eitherCourseOrExerciseGroupSet_badRequest() throws Exception {
        programmingExercise.setCourse(null);
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.BAD_REQUEST);
        programmingExerciseInExam.setCourse(programmingExercise.getCourseViaExerciseGroupOrCourseMember());
        request.post("/api/programming/programming-exercises/setup", programmingExerciseInExam, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_instructorNotInCourse_forbidden() throws Exception {
        userUtilService.addInstructor("other-instructors", userPrefix + "instructoralt1");
        programmingExercise.setId(null);
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.FORBIDDEN);
    }

    void createProgrammingExercise_titleNull_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle(null);
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_titleContainsBadCharacter_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("abc?=§ ``+##");
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_invalidShortName_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("New title");
        programmingExercise.setShortName(null);
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.BAD_REQUEST);
        programmingExercise.setShortName("hi");
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_invalidCourseShortName_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("New title");
        course.setShortName(null);
        courseRepository.save(course);
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.BAD_REQUEST);
        course.setShortName("Hi");
        courseRepository.save(course);
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_sameShortNameInCourse_badRequest() throws Exception {
        programmingExerciseInExam.setId(null);
        programmingExercise.setTitle("New title");
        programmingExercise.setId(null);
        request.post("/api/programming/programming-exercises/setup", programmingExerciseInExam, HttpStatus.BAD_REQUEST);
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_shortNameContainsBadCharacters_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("New title");
        programmingExercise.setShortName("asdb ³¼²½¼³`` ");
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_noProgrammingLanguageSet_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setShortName("testShortName");
        programmingExercise.setProgrammingLanguage(null);
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_packageNameContainsBadCharacters_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setPackageName("..asd. ß?");
        programmingExercise.setShortName("testShortName");
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_packageNameContainsKeyword_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setPackageName("abc.final.xyz");
        programmingExercise.setShortName("testShortName");
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_packageNameElementBeginsWithDigit_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setPackageName("eist.2020something");
        programmingExercise.setShortName("testShortName");
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_packageNameIsNull_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setPackageName(null);
        programmingExercise.setShortName("testShortName");
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_maxScoreIsNull_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setMaxPoints(null);
        programmingExercise.setShortName("testShortName");
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_noParticipationModeSelected_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setAllowOfflineIde(false);
        programmingExercise.setAllowOnlineEditor(false);
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_staticCodeAnalysisMustBeSet_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("New title");
        programmingExercise.setShortName("NewShortname");
        programmingExercise.setStaticCodeAnalysisEnabled(null);
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_staticCodeAnalysisAndSequential_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("New title");
        programmingExercise.setShortName("NewShortname");
        programmingExercise.setStaticCodeAnalysisEnabled(true);
        programmingExercise.getBuildConfig().setSequentialTestRuns(true);
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_unsupportedProgrammingLanguageForStaticCodeAnalysis_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("New title");
        programmingExercise.setShortName("NewShortname");
        programmingExercise.setStaticCodeAnalysisEnabled(true);
        programmingExercise.programmingLanguage(ProgrammingLanguage.C);
        programmingExercise.setProjectType(ProjectType.FACT);
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_noStaticCodeAnalysisButMaxPenalty_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("New title");
        programmingExercise.setShortName("NewShortname");
        programmingExercise.setStaticCodeAnalysisEnabled(false);
        programmingExercise.setMaxStaticCodeAnalysisPenalty(20);
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_maxStaticCodePenaltyNegative_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("New title");
        programmingExercise.setShortName("NewShortname");
        programmingExercise.setStaticCodeAnalysisEnabled(true);
        programmingExercise.setMaxStaticCodeAnalysisPenalty(-20);
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_vcsProjectWithSameKeyAlreadyExists_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("testTitle");
        programmingExercise.setShortName("testShortName");
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_vcsProjectWithSameTitleAlreadyExists_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("testTitle");
        programmingExercise.setShortName("testShortName");
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_failToCheckIfProjectExistsInCi() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("unique-title");
        programmingExercise.setShortName("testuniqueshortname");
        mockDelegate.mockCheckIfProjectExistsInCi(programmingExercise, true, true);
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_projectTypeMissing_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("New title");
        programmingExercise.setShortName("NewShortname");
        programmingExercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        programmingExercise.setProjectType(null);
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_projectTypeNotExpected_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("New title");
        programmingExercise.setShortName("NewShortname");
        programmingExercise.setProgrammingLanguage(ProgrammingLanguage.PYTHON);
        programmingExercise.setProjectType(ProjectType.MAVEN_MAVEN);
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_onlineCodeEditorNotExpected_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("New title");
        programmingExercise.setShortName("NewShortname");
        programmingExercise.setProgrammingLanguage(ProgrammingLanguage.SWIFT);
        programmingExercise.setProjectType(ProjectType.XCODE);
        programmingExercise.setAllowOnlineEditor(true);
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_checkoutSolutionRepositoryProgrammingLanguageNotSupported_badRequest(ProgrammingLanguage programmingLanguage) throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("New title");
        programmingExercise.setShortName("NewShortname");
        programmingExercise.setProgrammingLanguage(programmingLanguage);
        programmingExercise.getBuildConfig().setCheckoutSolutionRepository(true);
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_invalidMaxScore_badRequest() throws Exception {
        userUtilService.addInstructor("other-instructors", userPrefix + "instructoralt");
        programmingExercise.setId(null);
        programmingExercise.setMaxPoints(0.0);
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_includedAsBonus_invalidBonusPoints_badRequest() throws Exception {
        userUtilService.addInstructor("other-instructors", userPrefix + "instructoralt");
        programmingExercise.setId(null);
        programmingExercise.setMaxPoints(10.0);
        programmingExercise.setBonusPoints(1.0);
        programmingExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_AS_BONUS);
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_notIncluded_invalidBonusPoints_badRequest() throws Exception {
        userUtilService.addInstructor("other-instructors", userPrefix + "instructoralt");
        programmingExercise.setId(null);
        programmingExercise.setMaxPoints(10.0);
        programmingExercise.setBonusPoints(1.0);
        programmingExercise.setIncludedInOverallScore(IncludedInOverallScore.NOT_INCLUDED);
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void importProgrammingExercise_sourceExerciseIdNegative_badRequest() throws Exception {
        programmingExercise.setId(-1L);
        request.post("/api/programming/programming-exercises/import/" + programmingExercise.getId(), programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void importProgrammingExerciseMaxScoreNullBadRequest() throws Exception {
        programmingExercise.setMaxPoints(null);
        request.post("/api/programming/programming-exercises/import/" + programmingExercise.getId(), programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void importProgrammingExercise_noParticipationModeSelected_badRequest() throws Exception {
        programmingExercise.setAllowOfflineIde(false);
        programmingExercise.setAllowOnlineEditor(false);
        request.post("/api/programming/programming-exercises/import/" + programmingExercise.getId(), programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void importProgrammingExercise_noProgrammingLanguage_badRequest() throws Exception {
        programmingExercise.setProgrammingLanguage(null);
        request.post("/api/programming/programming-exercises/import/" + programmingExercise.getId(), programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void importProgrammingExercise_instructorNotInCourse_forbidden() throws Exception {
        userUtilService.addInstructor("other-instructors", userPrefix + "instructoralt1");
        request.post("/api/programming/programming-exercises/import/" + programmingExercise.getId(), programmingExercise, HttpStatus.FORBIDDEN);
    }

    void importProgrammingExercise_templateIdDoesNotExist_notFound() throws Exception {
        programmingExercise.setShortName("newShortName");
        programmingExercise.setTitle("newTitle");
        request.post("/api/programming/programming-exercises/import/" + NON_EXISTING_ID, programmingExercise, HttpStatus.NOT_FOUND);
    }

    void importProgrammingExercise_sameShortNameInCourse_badRequest() throws Exception {
        String sourceId = programmingExercise.getId().toString();
        programmingExercise.setId(null);
        programmingExercise.setTitle(programmingExercise.getTitle() + "change");
        String sourceIdExam = programmingExerciseInExam.getId().toString();
        programmingExerciseInExam.setId(null);
        programmingExerciseInExam.setTitle(programmingExerciseInExam.getTitle() + "change");
        // short name will still be the same
        request.post("/api/programming/programming-exercises/import/" + sourceId, programmingExercise, HttpStatus.BAD_REQUEST);
        request.post("/api/programming/programming-exercises/import/" + sourceIdExam, programmingExerciseInExam, HttpStatus.BAD_REQUEST);
    }

    void importProgrammingExercise_sameTitleInCourse_badRequest() throws Exception {
        String sourceId = programmingExercise.getId().toString();
        programmingExercise.setId(null);
        programmingExercise.setShortName(programmingExercise.getShortName() + "change");
        String sourceIdExam = programmingExerciseInExam.getId().toString();
        programmingExerciseInExam.setId(null);
        programmingExerciseInExam.setShortName(programmingExerciseInExam.getShortName() + "change");
        // title will still be the same
        request.post("/api/programming/programming-exercises/import/" + sourceId, programmingExercise, HttpStatus.BAD_REQUEST);
        request.post("/api/programming/programming-exercises/import/" + sourceIdExam, programmingExerciseInExam, HttpStatus.BAD_REQUEST);
    }

    void importProgrammingExercise_staticCodeAnalysisMustBeSet_badRequest() throws Exception {
        var id = programmingExercise.getId();
        programmingExercise.setId(null);
        programmingExercise.setStaticCodeAnalysisEnabled(null);
        request.post("/api/programming/programming-exercises/import/" + id, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void importProgrammingExercise_scaChanged_badRequest(boolean recreateBuildPlan, boolean updateTemplate) throws Exception {
        var params = new LinkedMultiValueMap<String, String>();
        params.add("recreateBuildPlans", String.valueOf(recreateBuildPlan));
        params.add("updateTemplate", String.valueOf(updateTemplate));
        var programmingExerciseSca = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndStaticCodeAnalysisCategories();

        setupMocksForConsistencyChecksOnImport(programmingExercise);
        setupMocksForConsistencyChecksOnImport(programmingExerciseSca);

        // false -> true
        var sourceId = programmingExercise.getId();
        programmingExercise.setId(null);
        programmingExercise.setTitle("NewTitle1");
        programmingExercise.setShortName("NewShortname1");
        programmingExercise.setStaticCodeAnalysisEnabled(true);
        request.postWithResponseBody("/api/programming/programming-exercises/import/" + sourceId, programmingExercise, ProgrammingExercise.class, params, HttpStatus.BAD_REQUEST);

        // true -> false
        sourceId = programmingExerciseSca.getId();
        programmingExerciseSca.setId(null);
        programmingExerciseSca.setStaticCodeAnalysisEnabled(false);
        programmingExerciseSca.setMaxStaticCodeAnalysisPenalty(null);
        programmingExerciseSca.setTitle("NewTitle2");
        programmingExerciseSca.setShortName("NewShortname2");
        request.postWithResponseBody("/api/programming/programming-exercises/import/" + sourceId, programmingExerciseSca, ProgrammingExercise.class, params,
                HttpStatus.BAD_REQUEST);
    }

    void importProgrammingExercise_eitherCourseOrExerciseGroupSet_badRequest() throws Exception {
        programmingExercise.setCourse(null);
        request.post("/api/programming/programming-exercises/import/" + NON_EXISTING_ID, programmingExercise, HttpStatus.BAD_REQUEST);
        programmingExerciseInExam.setCourse(programmingExercise.getCourseViaExerciseGroupOrCourseMember());
        request.post("/api/programming/programming-exercises/import/" + NON_EXISTING_ID, programmingExerciseInExam, HttpStatus.BAD_REQUEST);
    }

    void importProgrammingExercise_vcsProjectWithSameKeyAlreadyExists_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setShortName("testShortName");
        request.post("/api/programming/programming-exercises/import/" + NON_EXISTING_ID, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void importProgrammingExercise_vcsProjectWithSameTitleAlreadyExists_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setShortName("testShortName");
        request.post("/api/programming/programming-exercises/import/" + NON_EXISTING_ID, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void importProgrammingExercise_updatesTestCaseIds() throws Exception {
        programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationAndAuxiliaryRepositoriesElseThrow(programmingExercise.getId());
        var tests = programmingExerciseUtilService.addTestCasesToProgrammingExercise(programmingExercise);
        var test1 = tests.getFirst();

        String problemStatementWithId = "[task][Taskname](<testid>" + test1.getId() + "</testid>)";
        String problemStatement = "[task][Taskname](test1)";
        programmingExercise.setProblemStatement(problemStatementWithId);
        programmingExerciseRepository.save(programmingExercise);
        programmingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationAndBuildConfigById(programmingExercise.getId()).orElseThrow();

        String sourceId = programmingExercise.getId().toString();

        ProgrammingExercise exerciseToBeImported = ProgrammingExerciseFactory.generateToBeImportedProgrammingExercise("ImportTitle", "imported", programmingExercise, course);
        exerciseToBeImported.setProblemStatement(problemStatement);

        mockDelegate.mockConnectorRequestsForImport(programmingExercise, exerciseToBeImported, false, false);
        mockDelegate.mockConnectorRequestsForSetup(exerciseToBeImported, false, false, false);
        mockBuildPlanAndRepositoryCheck(programmingExercise);
        doNothing().when(continuousIntegrationService).updatePlanRepository(any(), any(), any(), any(), any(), any(), any());

        var response = request.postWithResponseBody("/api/programming/programming-exercises/import/" + sourceId, exerciseToBeImported, ProgrammingExercise.class, HttpStatus.OK);

        assertThat(response.getProblemStatement()).isEqualTo("[task][Taskname](test1)");

        var newTestCase = programmingExerciseTestCaseRepository.findByExerciseIdAndTestName(response.getId(), test1.getTestName()).orElseThrow();
        String newProblemStatement = "[task][Taskname](<testid>" + newTestCase.getId() + "</testid>)";
        var savedProgrammingExercise = programmingExerciseRepository.findByIdElseThrow(response.getId());

        assertThat(savedProgrammingExercise.getProblemStatement()).isEqualTo(newProblemStatement);

    }

    void exportSubmissionsByStudentLogins_notInstructorForExercise_forbidden() throws Exception {
        userUtilService.addInstructor("other-instructors", userPrefix + "instructoralt1");
        request.post(getDefaultAPIEndpointForExportRepos(), getOptions(), HttpStatus.FORBIDDEN);
    }

    @NonNull
    private String getDefaultAPIEndpointForExportRepos() {
        return "/api/programming/programming-exercises/" + programmingExercise.getId() + "/export-repos-by-participant-identifiers/1,2,3";
    }

    void exportSubmissionsByStudentLogins_exportAllAsTutor_forbidden() throws Exception {
        final var options = new RepositoryExportOptionsDTO(true, true, false, null, false, true, true, true, true);
        request.post(getDefaultAPIEndpointForExportRepos(), options, HttpStatus.FORBIDDEN);
    }

    void generateStructureOracleForExercise_exerciseDoesNotExist_badRequest() throws Exception {
        request.put("/api/programming/programming-exercises/" + (programmingExercise.getId() + 8337) + "/generate-tests", programmingExercise, HttpStatus.NOT_FOUND);
    }

    void generateStructureOracleForExercise_userIsNotAdminInCourse_badRequest() throws Exception {
        userUtilService.addInstructor("other-instructors", userPrefix + "instructoralt");
        request.put("/api/programming/programming-exercises/" + programmingExercise.getId() + "/generate-tests", programmingExercise, HttpStatus.FORBIDDEN);
    }

    void generateStructureOracleForExercise_invalidPackageName_badRequest() throws Exception {
        programmingExercise.setPackageName(null);
        programmingExerciseRepository.saveAndFlush(programmingExercise);
        request.put("/api/programming/programming-exercises/" + programmingExercise.getId() + "/generate-tests", programmingExercise, HttpStatus.BAD_REQUEST);

        programmingExercise.setPackageName("ab");
        programmingExerciseRepository.saveAndFlush(programmingExercise);
        request.put("/api/programming/programming-exercises/" + programmingExercise.getId() + "/generate-tests", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void hasAtLeastOneStudentResult_exerciseDoesNotExist_notFound() throws Exception {
        request.get("/api/programming/programming-exercises/" + (programmingExercise.getId() + 1337) + "/test-case-state", HttpStatus.NOT_FOUND, String.class);
    }

    void hasAtLeastOneStudentResult_isNotTeachingAssistant_forbidden() throws Exception {
        userUtilService.addTeachingAssistant("other-tutors", userPrefix + "tutoralt1");
        request.get("/api/programming/programming-exercises/" + programmingExercise.getId() + "/test-case-state", HttpStatus.FORBIDDEN, String.class);
    }

    void getTestCases_asTutor() throws Exception {
        final var endpoint = "/programming/programming-exercises/" + programmingExercise.getId() + "/test-cases";
        final List<ProgrammingExerciseTestCase> returnedTests = request.getList("/api" + endpoint, HttpStatus.OK, ProgrammingExerciseTestCase.class);
        final List<ProgrammingExerciseTestCase> testsInDB = new ArrayList<>(programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId()));
        returnedTests.forEach(testCase -> testCase.setExercise(programmingExercise));
        assertThat(returnedTests).containsExactlyInAnyOrderElementsOf(testsInDB);
    }

    void getTestCases_asStudent_forbidden() throws Exception {
        final var endpoint = "/programming/programming-exercises/" + programmingExercise.getId() + "/test-cases";
        request.getList("/api" + endpoint, HttpStatus.FORBIDDEN, ProgrammingExerciseTestCase.class);
    }

    void getTestCases_tutorInOtherCourse_forbidden() throws Exception {
        userUtilService.addTeachingAssistant("other-teaching-assistants", userPrefix + "other-teaching-assistant1");
        final var endpoint = "/programming/programming-exercises/" + programmingExercise.getId() + "/test-cases";

        request.getList("/api" + endpoint, HttpStatus.FORBIDDEN, ProgrammingExerciseTestCase.class);
    }

    void updateTestCases_asInstrutor() throws Exception {
        programmingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(programmingExercise.getId()).orElseThrow();
        mockDelegate.mockTriggerBuild(programmingExercise.getSolutionParticipation());
        mockDelegate.mockTriggerBuild(programmingExercise.getTemplateParticipation());
        final var testCases = programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId());
        final var updates = testCases.stream().map(testCase -> new ProgrammingExerciseTestCaseDTO(testCase.getId(), testCase.getId() + 42.0, testCase.getId() + 1.0,
                testCase.getId() + 2.0, Visibility.AFTER_DUE_DATE)).toList();
        final var endpoint = "/programming/programming-exercises/" + programmingExercise.getId() + "/update-test-cases";

        final var testCasesResponse = request.patchWithResponseBody("/api" + endpoint, updates, new TypeReference<List<ProgrammingExerciseTestCase>>() {
        }, HttpStatus.OK);
        testCasesResponse.forEach(testCase -> testCase.setExercise(programmingExercise));
        final var testCasesInDB = programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId());

        assertThat(new HashSet<>(testCasesResponse)).usingRecursiveFieldByFieldElementComparatorIgnoringFields("exercise", "tasks")
                .containsExactlyInAnyOrderElementsOf(testCasesInDB);
        assertThat(testCasesResponse).allSatisfy(testCase -> {
            assertThat(testCase.isAfterDueDate()).isTrue();
            assertThat(testCase.getWeight()).isEqualTo(testCase.getId() + 42);
            assertThat(testCase.getBonusMultiplier()).isEqualTo(testCase.getId() + 1.0);
            assertThat(testCase.getBonusPoints()).isEqualTo(testCase.getId() + 2.0);
        });
    }

    void updateTestCases_asInstrutor_triggerBuildFails() throws Exception {
        programmingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(programmingExercise.getId()).orElseThrow();
        mockDelegate.mockTriggerBuildFailed(programmingExercise.getSolutionParticipation());
        mockDelegate.mockTriggerBuildFailed(programmingExercise.getTemplateParticipation());

        final var testCases = programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId());
        final var updates = testCases.stream().map(testCase -> new ProgrammingExerciseTestCaseDTO(testCase.getId(), testCase.getId() + 42.0, testCase.getId() + 1.0,
                testCase.getId() + 2.0, Visibility.AFTER_DUE_DATE)).toList();
        final var endpoint = "/programming/programming-exercises/" + programmingExercise.getId() + "/update-test-cases";

        final var testCasesResponse = request.patchWithResponseBody("/api" + endpoint, updates, new TypeReference<List<ProgrammingExerciseTestCase>>() {
        }, HttpStatus.OK);

        assertThat(testCasesResponse).isNotNull();
    }

    void updateTestCases_nonExistingExercise_notFound() throws Exception {
        final var update = new ProgrammingExerciseTestCaseDTO(null, null, null, null, null);
        final var endpoint = "/programming/programming-exercises/" + (programmingExercise.getId() + 1337) + "/update-test-cases";
        request.patchWithResponseBody("/api" + endpoint, List.of(update), String.class, HttpStatus.NOT_FOUND);
    }

    void updateTestCases_instructorInWrongCourse_forbidden() throws Exception {
        userUtilService.addInstructor("other-instructors", userPrefix + "other-instructor1");
        final var update = new ProgrammingExerciseTestCaseDTO(null, null, null, null, null);
        final var endpoint = "/programming/programming-exercises/" + programmingExercise.getId() + "/update-test-cases";

        request.patchWithResponseBody("/api" + endpoint, List.of(update), String.class, HttpStatus.FORBIDDEN);
    }

    void updateTestCases_testCaseWeightSmallerThanZero_badRequest() throws Exception {
        final var testCases = programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId());
        final var updates = testCases.stream()
                .map(testCase -> new ProgrammingExerciseTestCaseDTO(testCase.getId(), -1., testCase.getId() + 1.0, testCase.getId() + 2.0, Visibility.AFTER_DUE_DATE)).toList();
        final var endpoint = "/programming/programming-exercises/" + programmingExercise.getId() + "/update-test-cases";

        request.patchWithResponseBody("/api" + endpoint, updates, String.class, HttpStatus.BAD_REQUEST);
    }

    void updateTestCases_testCaseMultiplierSmallerThanZero_badRequest() throws Exception {
        final var testCases = List.copyOf(programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId()));
        final var updates = transformTestCasesToDto(testCases);
        var firstUpdate = updates.getFirst();
        firstUpdate = new ProgrammingExerciseTestCaseDTO(firstUpdate.id(), firstUpdate.weight(), -1.0, firstUpdate.bonusPoints(), firstUpdate.visibility());
        updates.set(0, firstUpdate);
        final var endpoint = "/programming/programming-exercises/" + programmingExercise.getId() + "/update-test-cases";

        request.performMvcRequest(
                MockMvcRequestBuilders.patch(new URI("/api" + endpoint)).contentType(MediaType.APPLICATION_JSON).content(request.getObjectMapper().writeValueAsString(updates)))
                .andExpect(status().isBadRequest()) //
                .andExpect(jsonPath("$.errorKey").value("settingNegative")) //
                .andExpect(jsonPath("$.testCase").value(testCases.getFirst().getTestName()));
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
        var firstUpdate = updates.getFirst();
        firstUpdate = new ProgrammingExerciseTestCaseDTO(firstUpdate.id(), firstUpdate.weight(), firstUpdate.bonusMultiplier(), null, firstUpdate.visibility());
        updates.set(0, firstUpdate);

        final var endpoint = "/programming/programming-exercises/" + programmingExercise.getId() + "/update-test-cases";

        final var testCasesResponse = request.patchWithResponseBody("/api" + endpoint, updates, new TypeReference<List<ProgrammingExerciseTestCase>>() {
        }, HttpStatus.OK);
        final var updatedTestCase = testCasesResponse.stream().filter(testCase -> testCase.getId().equals(updates.getFirst().id())).findFirst().orElseThrow();
        assertThat(updatedTestCase.getBonusPoints()).isZero();
        assertThat(testCasesResponse.stream().filter(testCase -> !testCase.getId().equals(updatedTestCase.getId()))).allMatch(testCase -> testCase.getBonusPoints() == 1d);
    }

    private static List<ProgrammingExerciseTestCaseDTO> transformTestCasesToDto(Collection<ProgrammingExerciseTestCase> testCases) {
        return testCases.stream().map(testCase -> new ProgrammingExerciseTestCaseDTO(testCase.getId(), testCase.getWeight(), testCase.getBonusMultiplier(),
                testCase.getBonusPoints(), testCase.getVisibility())).collect(Collectors.toCollection(ArrayList::new));
    }

    void resetTestCaseWeights_asInstructor() throws Exception {
        programmingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(programmingExercise.getId()).orElseThrow();
        mockDelegate.mockTriggerBuild(programmingExercise.getSolutionParticipation());
        mockDelegate.mockTriggerBuild(programmingExercise.getTemplateParticipation());
        final var endpoint = "/programming/programming-exercises/" + programmingExercise.getId() + "/test-cases/reset";
        programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId()).forEach(test -> {
            test.setWeight(42.0);
            programmingExerciseTestCaseRepository.saveAndFlush(test);
        });

        final var testCasesResponse = request.patchWithResponseBody("/api" + endpoint, "{}", new TypeReference<List<ProgrammingExerciseTestCase>>() {
        }, HttpStatus.OK);
        // Otherwise the HashSet for comparison can't be created because exercise id is used for the hashCode
        testCasesResponse.forEach(testCase -> testCase.setExercise(programmingExercise));
        final var testsInDB = programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId());

        assertThat(testCasesResponse).containsExactlyInAnyOrderElementsOf(testsInDB);
        assertThat(testsInDB).allSatisfy(test -> assertThat(test.getWeight()).isEqualTo(1));
        assertThat(testsInDB).allSatisfy(test -> assertThat(test.getBonusMultiplier()).isEqualTo(1.0));
        assertThat(testsInDB).allSatisfy(test -> assertThat(test.getBonusPoints()).isZero());
    }

    void resetTestCaseWeights_instructorInWrongCourse_forbidden() throws Exception {
        userUtilService.addInstructor("other-instructors", userPrefix + "other-instructor1");
        final var endpoint = "/programming/programming-exercises/" + programmingExercise.getId() + "/test-cases/reset";
        request.patchWithResponseBody("/api" + endpoint, "{}", String.class, HttpStatus.FORBIDDEN);
    }

    void testCheckPlagiarism() throws Exception {
        // Use unique exercise shortName to avoid conflicts with parallel tests that share the same projectKey
        var course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise(false, "Plagiarism Test", "PLAG1");
        var programmingExercise = programmingExerciseRepository
                .findWithTemplateAndSolutionParticipationById(ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class).getId()).orElseThrow();
        prepareTwoStudentAndOneInstructorRepositoriesForPlagiarismChecks(programmingExercise);

        final var path = "/api/programming/programming-exercises/" + programmingExercise.getId() + "/check-plagiarism";
        var result = request.get(path, HttpStatus.OK, PlagiarismResultDTO.class, plagiarismUtilService.getDefaultPlagiarismOptions());
        assertPlagiarismResult(programmingExercise, result, 100.0);
    }

    void testCheckPlagiarismForTeamExercise() throws Exception {
        // Use unique exercise shortName to avoid conflicts with parallel tests that share the same projectKey
        var course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise(false, "Plagiarism Team Test", "PLAG2");

        var programmingExercise = programmingExerciseRepository
                .findWithTemplateAndSolutionParticipationById(ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class).getId()).orElseThrow();
        programmingExercise.setMode(ExerciseMode.TEAM);
        programmingExerciseRepository.save(programmingExercise);

        prepareTwoTeamRepositoriesForPlagiarismChecks(programmingExercise);

        final var path = "/api/programming/programming-exercises/" + programmingExercise.getId() + "/check-plagiarism";
        var result = request.get(path, HttpStatus.OK, PlagiarismResultDTO.class, plagiarismUtilService.getDefaultPlagiarismOptions());
        assertPlagiarismResult(programmingExercise, result, 100.0);
    }

    void testCheckPlagiarismJplagReport() throws Exception {
        // Use unique exercise shortName to avoid conflicts with parallel tests that share the same projectKey
        var course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise(false, "Plagiarism Report Test", "PLAG3");
        var programmingExercise = programmingExerciseRepository
                .findWithTemplateAndSolutionParticipationById(ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class).getId()).orElseThrow();
        prepareTwoStudentAndOneInstructorRepositoriesForPlagiarismChecks(programmingExercise);

        final var path = "/api/programming/programming-exercises/" + programmingExercise.getId() + "/check-plagiarism-jplag-report";
        var jplagZipArchive = request.getFile(path, HttpStatus.OK, plagiarismUtilService.getDefaultPlagiarismOptions());
        assertThat(jplagZipArchive).isNotNull();
        assertThat(jplagZipArchive).exists();

        try (ZipFile zipFile = new ZipFile(jplagZipArchive)) {
            assertThat(zipFile.getEntry("submissionMappings.json")).isNotNull();
            var fileEntries = zipFile.stream().filter(entry -> entry.getName().startsWith("files/") && !entry.isDirectory()).toList();
            assertThat(fileEntries).isNotEmpty();

            var comparisonEntries = zipFile.stream().filter(entry -> entry.getName().startsWith("comparisons/") && entry.getName().endsWith(".json") && !entry.isDirectory())
                    .toList();
            assertThat(comparisonEntries).isNotEmpty();
        }
    }

    private void assertPlagiarismResult(ProgrammingExercise programmingExercise, PlagiarismResultDTO result, double expectedSimilarity) {
        // verify plagiarism result
        assertThat(result.plagiarismResult().getComparisons()).hasSize(1);
        assertThat(result.plagiarismResult().getExercise().getId()).isEqualTo(programmingExercise.getId());

        PlagiarismComparison comparison = result.plagiarismResult().getComparisons().iterator().next();
        assertThat(comparison.getSimilarity()).isEqualTo(expectedSimilarity, Offset.offset(0.0001));
        assertThat(comparison.getStatus()).isEqualTo(PlagiarismStatus.NONE);
        assertThat(comparison.getMatches()).hasSize(1);

        // verify plagiarism result stats
        var stats = result.plagiarismResultStats();
        assertThat(stats.numberOfDetectedSubmissions()).isEqualTo(2);
        assertThat(stats.averageSimilarity()).isEqualTo(expectedSimilarity, Offset.offset(0.0001));
        assertThat(stats.maximalSimilarity()).isEqualTo(expectedSimilarity, Offset.offset(0.0001));
    }

    private void prepareTwoStudentAndOneInstructorRepositoriesForPlagiarismChecks(ProgrammingExercise programmingExercise) throws IOException, GitAPIException {
        var participationStudent1 = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, userPrefix + "student1");
        var participationStudent2 = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, userPrefix + "student2");
        var participationInstructor1 = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, userPrefix + "instructor1");
        var submissionStudent1 = programmingExerciseUtilService.createProgrammingSubmission(participationStudent1, false);
        var submissionStudent2 = programmingExerciseUtilService.createProgrammingSubmission(participationStudent2, false);
        var submissionInstructor1 = programmingExerciseUtilService.createProgrammingSubmission(participationInstructor1, false);
        participationUtilService.addResultToSubmission(submissionStudent1, AssessmentType.AUTOMATIC, null);
        participationUtilService.addResultToSubmission(submissionStudent2, AssessmentType.AUTOMATIC, null);
        participationUtilService.addResultToSubmission(submissionInstructor1, AssessmentType.AUTOMATIC, null);

        prepareTwoSubmissionsForPlagiarismChecks(programmingExercise);
    }

    private Team createTeam(ProgrammingExercise programmingExercise, String suffix) {
        var student = userUtilService.getUserByLogin(userPrefix + "student" + suffix);
        var team = new Team().name("Team " + suffix).shortName(userPrefix + "team" + suffix).exercise(programmingExercise).students(Set.of(student));
        return teamRepository.save(programmingExercise, team);
    }

    private void prepareTwoTeamRepositoriesForPlagiarismChecks(ProgrammingExercise programmingExercise) throws IOException, GitAPIException {
        var participationTeam1 = participationUtilService.addTeamParticipationForProgrammingExercise(programmingExercise, createTeam(programmingExercise, "1"));
        var participationTeam2 = participationUtilService.addTeamParticipationForProgrammingExercise(programmingExercise, createTeam(programmingExercise, "2"));
        var submissionTeam1 = programmingExerciseUtilService.createProgrammingSubmission(participationTeam1, false);
        var submissionTeam2 = programmingExerciseUtilService.createProgrammingSubmission(participationTeam2, false);
        participationUtilService.addResultToSubmission(submissionTeam1, AssessmentType.AUTOMATIC, null);
        participationUtilService.addResultToSubmission(submissionTeam2, AssessmentType.AUTOMATIC, null);

        prepareTwoSubmissionsForPlagiarismChecks(programmingExercise);
    }

    private void prepareTwoSubmissionsForPlagiarismChecks(ProgrammingExercise programmingExercise) throws IOException, GitAPIException {
        var projectKey = programmingExercise.getProjectKey();
        // Ensure no stale LocalVC repositories from previous tests interfere with seeding
        cleanupLocalVcProjectForKey(projectKey);

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

        // Get student participations (excluding instructor)
        var studentParticipations = programmingExerciseStudentParticipationRepository.findByExerciseId(programmingExercise.getId()).stream()
                .filter(p -> p.getParticipant() != null && p.getParticipant().getName() != null && !p.getParticipant().getName().contains("instructor"))
                .sorted(Comparator.comparing(DomainObject::getId)).toList();

        if (studentParticipations.size() < 2) {
            throw new IllegalStateException("Expected at least 2 student participations for plagiarism checks");
        }

        // Seed real LocalVC repositories for all student participations with identical Java content to ensure JPlag has multiple valid submissions
        for (ProgrammingExerciseStudentParticipation participation : studentParticipations) {
            try {
                var repositorySlug = localVCLocalCITestService.getRepositorySlug(projectKey, participation.getParticipantIdentifier());
                var repo = RepositoryExportTestUtil.trackRepository(localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, repositorySlug));
                var repoUri = localVCLocalCITestService.buildLocalVCUri(participation.getParticipantIdentifier(), projectKey, repositorySlug);
                participation.setRepositoryUri(repoUri);
                RepositoryExportTestUtil.writeFilesAndPush(repo, Map.of("Main.java", exampleProgram), "seed plagiarism test content");
                programmingExerciseStudentParticipationRepository.save(participation);
            }
            catch (Exception e) {
                throw new RuntimeException("Failed to seed plagiarism test repository", e);
            }
        }

        // Ensure the project folder exists
        Files.createDirectories(localVCBasePath.resolve(projectKey));
    }

    void testGetPlagiarismResult() throws Exception {
        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        ProgrammingExercise programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        programmingExercise = programmingExerciseRepository.findWithEagerStudentParticipationsStudentAndSubmissionsById(programmingExercise.getId()).orElseThrow();

        PlagiarismResult expectedResult = textExerciseUtilService.createPlagiarismResultForExercise(programmingExercise);

        var result = request.get("/api/programming/programming-exercises/" + programmingExercise.getId() + "/plagiarism-result", HttpStatus.OK, PlagiarismResultDTO.class);
        assertThat(result.plagiarismResult().getId()).isEqualTo(expectedResult.getId());
    }

    void testGetPlagiarismResultWithoutResult() throws Exception {
        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        ProgrammingExercise programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        var result = request.get("/api/programming/programming-exercises/" + programmingExercise.getId() + "/plagiarism-result", HttpStatus.OK, String.class);
        assertThat(result).isNullOrEmpty();
    }

    void testGetPlagiarismResultWithoutExercise() throws Exception {
        PlagiarismResult result = request.get("/api/programming/programming-exercises/-1/plagiarism-result", HttpStatus.NOT_FOUND, PlagiarismResult.class);
        assertThat(result).isNull();
    }

    void testValidateValidAuxiliaryRepository() throws Exception {
        AuxiliaryRepositoryBuilder auxRepoBuilder = AuxiliaryRepositoryBuilder.defaults();
        testAuxRepo(auxRepoBuilder, HttpStatus.OK);
    }

    void testValidateAuxiliaryRepositoryIdSetOnRequest() throws Exception {
        testAuxRepo(AuxiliaryRepositoryBuilder.defaults().withId(0L), HttpStatus.INTERNAL_SERVER_ERROR);

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
        testAuxRepo(auxRepoBuilder, HttpStatus.OK);
    }

    void testValidateAuxiliaryRepositoryWithBlankCheckoutDirectory() throws Exception {
        AuxiliaryRepositoryBuilder auxRepoBuilder = AuxiliaryRepositoryBuilder.defaults().withCheckoutDirectory("   ");
        testAuxRepo(auxRepoBuilder, HttpStatus.OK);
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
        testAuxRepo(auxRepoBuilder, HttpStatus.OK);
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

    void testResetForbidden() throws Exception {
        var resetOptions = new ProgrammingExerciseResetOptionsDTO(false, false);
        request.put(defaultResetEndpoint(), resetOptions, HttpStatus.FORBIDDEN);
    }

    void testResetOnlyDeleteStudentParticipationsSubmissionsAndResultsForbidden() throws Exception {
        var resetOptions = new ProgrammingExerciseResetOptionsDTO(true, false);
        request.put(defaultResetEndpoint(), resetOptions, HttpStatus.FORBIDDEN);
    }

    void testResetExerciseNotFound() throws Exception {
        var resetOptions = new ProgrammingExerciseResetOptionsDTO(false, false);
        request.put(defaultResetEndpoint(-1L), resetOptions, HttpStatus.NOT_FOUND);
    }

    void testResetOnlyDeleteStudentParticipationsSubmissionsAndResultsSuccess() throws Exception {
        final var projectKey = programmingExercise.getProjectKey();
        for (final var planName : List.of(userPrefix + "student1", userPrefix + "student2")) {
            mockDelegate.mockDeleteBuildPlan(projectKey, projectKey + "-" + planName.toUpperCase(), false);
        }

        // Two participations exist before reset
        assertThat(programmingExerciseStudentParticipationRepository.findByExerciseId(programmingExercise.getId())).hasSize(2);

        var resetOptions = new ProgrammingExerciseResetOptionsDTO(true, false);
        request.put(defaultResetEndpoint(programmingExercise.getId()), resetOptions, HttpStatus.OK);

        // No participations exist after reset
        assertThat(programmingExerciseStudentParticipationRepository.findByExerciseId(programmingExercise.getId())).isEmpty();
    }

    void testResetOnlyRecreateBuildPlansSuccess() throws Exception {
        addAuxiliaryRepositoryToExercise();
        mockDelegate.mockGetProjectKeyFromAnyUrl(programmingExercise.getProjectKey());
        String templateBuildPlanName = programmingExercise.getProjectKey() + "-" + TEMPLATE.getName();
        String solutionBuildPlanName = programmingExercise.getProjectKey() + "-" + SOLUTION.getName();
        mockDelegate.mockGetBuildPlan(programmingExercise.getProjectKey(), templateBuildPlanName, true, true, false, false);
        mockDelegate.mockGetBuildPlan(programmingExercise.getProjectKey(), solutionBuildPlanName, true, true, false, false);
        mockDelegate.mockDeleteBuildPlan(programmingExercise.getProjectKey(), templateBuildPlanName, false);
        mockDelegate.mockDeleteBuildPlan(programmingExercise.getProjectKey(), solutionBuildPlanName, false);
        mockDelegate.mockGetBuildPlanConfig(programmingExercise.getProjectKey(), templateBuildPlanName);
        mockDelegate.mockGetBuildPlanConfig(programmingExercise.getProjectKey(), solutionBuildPlanName);
        mockDelegate.mockConnectorRequestsForSetup(programmingExercise, false, false, false);

        var resetOptions = new ProgrammingExerciseResetOptionsDTO(false, true);
        request.put(defaultResetEndpoint(), resetOptions, HttpStatus.OK);
    }

    void testExportAuxiliaryRepositoryForbidden() throws Exception {
        AuxiliaryRepository repository = addAuxiliaryRepositoryToExercise();
        request.get(defaultExportInstructorAuxiliaryRepository(repository), HttpStatus.FORBIDDEN, File.class);
    }

    void testExportAuxiliaryRepositoryUnprocessableEntity() throws Exception {
        AuxiliaryRepository repository = addAuxiliaryRepositoryToExercise();
        request.get(defaultExportInstructorAuxiliaryRepository(repository), HttpStatus.UNPROCESSABLE_ENTITY, File.class);
    }

    void testExportAuxiliaryRepositoryExerciseAccessForbidden() throws Exception {
        request.get(defaultExportInstructorAuxiliaryRepository(-1L, 1L), HttpStatus.FORBIDDEN, File.class);
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

    public void addAuxiliaryRepositoryToExercise(ProgrammingExercise exercise) {
        AuxiliaryRepository repository = AuxiliaryRepositoryBuilder.defaults().get();
        auxiliaryRepositoryRepository.save(repository);
        exercise.setAuxiliaryRepositories(new ArrayList<>());
        exercise.addAuxiliaryRepository(repository);
        programmingExerciseRepository.save(exercise);
    }

    private String defaultAuxiliaryRepositoryEndpoint() {
        return "/api/programming/programming-exercises";
    }

    private String defaultResetEndpoint() {
        return defaultResetEndpoint(programmingExercise.getId());
    }

    private String defaultGetAuxReposEndpoint() {
        return defaultGetAuxReposEndpoint(programmingExercise.getId());
    }

    private String defaultExportInstructorAuxiliaryRepository(AuxiliaryRepository repository) {
        return defaultExportInstructorAuxiliaryRepository(programmingExercise.getId(), repository.getId());
    }

    private String defaultResetEndpoint(Long exerciseId) {
        return "/api/programming/programming-exercises/" + exerciseId + "/reset";
    }

    private String defaultGetAuxReposEndpoint(Long exerciseId) {
        return "/api/programming/programming-exercises/" + exerciseId + "/auxiliary-repository";
    }

    private String defaultExportInstructorAuxiliaryRepository(Long exerciseId, Long repositoryId) {
        return "/api/programming/programming-exercises/" + exerciseId + "/export-instructor-auxiliary-repository/" + repositoryId;
    }

    private void testAuxRepo(AuxiliaryRepositoryBuilder body, HttpStatus expectedStatus) throws Exception {
        testAuxRepo(List.of(body.get()), expectedStatus);
    }

    private void testAuxRepo(List<AuxiliaryRepository> body, HttpStatus expectedStatus) throws Exception {
        programmingExercise.setAuxiliaryRepositories(body);
        request.putWithResponseBody(defaultAuxiliaryRepositoryEndpoint(), programmingExercise, ProgrammingExercise.class, expectedStatus);
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

    void testReEvaluateAndUpdateProgrammingExercise_instructorNotInCourse_forbidden(String testPrefix) throws Exception {
        userUtilService.addInstructor("other-instructors", testPrefix + "instructoralt1");
        programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        ProgrammingExercise programmingExercise = programmingExerciseTestRepository.findAllWithEagerTemplateAndSolutionParticipations().getFirst();
        request.put("/api/programming/programming-exercises/" + programmingExercise.getId() + "/re-evaluate", programmingExercise, HttpStatus.FORBIDDEN);
    }

    void testReEvaluateAndUpdateProgrammingExercise_notFound() throws Exception {
        request.put("/api/programming/programming-exercises/" + 123456789 + "/re-evaluate", programmingExercise, HttpStatus.NOT_FOUND);
    }

    void testReEvaluateAndUpdateProgrammingExercise_isNotSameGivenExerciseIdInRequestBody_conflict() throws Exception {
        programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        ProgrammingExercise programmingExercise = programmingExerciseTestRepository.findAllWithEagerTemplateAndSolutionParticipations().getFirst();
        ProgrammingExercise programmingExerciseToBeConflicted = programmingExerciseTestRepository.findAllWithEagerTemplateAndSolutionParticipations().get(1);

        request.put("/api/programming/programming-exercises/" + programmingExercise.getId() + "/re-evaluate", programmingExerciseToBeConflicted, HttpStatus.CONFLICT);
    }

    void test_redirectGetTemplateRepositoryFilesWithContentOmitBinaries() throws Exception {
        // Wire base repos via LocalVC
        RepositoryExportTestUtil.createAndWireBaseRepositories(localVCLocalCITestService, programmingExercise);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);

        var templateRepo = RepositoryExportTestUtil.createTemplateWorkingCopy(localVCLocalCITestService, programmingExercise);
        RepositoryExportTestUtil.writeFilesAndPush(templateRepo, Map.of("A.java", "abc", "B.jar", "binaryContent"), "seed template files");

        var savedExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(programmingExercise.getId());
        var queryParams = "?omitBinaries=true";
        request.getWithForwardedUrl("/api/programming/programming-exercises/" + programmingExercise.getId() + "/template-files-content" + queryParams, HttpStatus.OK,
                "/api/programming/repository/" + savedExercise.getTemplateParticipation().getId() + "/files-content" + queryParams);
    }

    void test_redirectGetTemplateRepositoryFilesWithContent() throws Exception {
        // Wire base repos via LocalVC
        RepositoryExportTestUtil.createAndWireBaseRepositories(localVCLocalCITestService, programmingExercise);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);

        var templateRepo = RepositoryExportTestUtil.createTemplateWorkingCopy(localVCLocalCITestService, programmingExercise);
        RepositoryExportTestUtil.writeFilesAndPush(templateRepo, Map.of("A.java", "abc", "B.java", "cde", "C.java", "efg"), "seed template files");

        var savedExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(programmingExercise.getId());
        request.getWithForwardedUrl("/api/programming/programming-exercises/" + programmingExercise.getId() + "/template-files-content", HttpStatus.OK,
                "/api/programming/repository/" + savedExercise.getTemplateParticipation().getId() + "/files-content");
    }

    // Legacy BiFunction-based helper is no longer needed after LocalVC conversion; removed to simplify the suite.

    void testRedirectGetParticipationRepositoryFilesWithContentAtCommit(String testPrefix) throws Exception {
        // Ensure base repositories (template, solution, tests) exist and URIs are wired for this exercise
        RepositoryExportTestUtil.createAndWireBaseRepositories(localVCLocalCITestService, programmingExercise);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);

        // Create student participation with LocalVC repo
        // Use a unique student to avoid repo collisions with other tests in this class
        String studentLogin = testPrefix + "student3";
        var studentParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, studentLogin);
        var repo = RepositoryExportTestUtil.seedStudentRepositoryForParticipation(localVCLocalCITestService, studentParticipation);
        programmingExerciseStudentParticipationRepository.save(studentParticipation);

        // Write files in one commit and push to origin to ensure the commit exists remotely
        var commit = RepositoryExportTestUtil.writeFilesAndPush(repo,
                Map.of("README.md", "Initial commit", "A.java", "abc", "B.java", "cde", "C.java", "efg", "test.txt", "Initial commit"), "seed student files");

        // Persist submission with commit hash
        var submission = new ProgrammingSubmission();
        submission.setType(SubmissionType.MANUAL);
        submission.setCommitHash(commit.getId().getName());
        programmingExerciseUtilService.addProgrammingSubmission(programmingExercise, submission, studentLogin);

        String filesWithContentsAsJson = """
                {
                  "test.txt" : "Initial commit",
                  "C.java" : "efg",
                  "B.java" : "cde",
                  "A.java" : "abc",
                  "README.md" : "Initial commit"
                }""";

        request.getWithFileContents("/api/programming/programming-exercise-participations/" + studentParticipation.getId() + "/files-content/" + submission.getCommitHash(),
                HttpStatus.OK, filesWithContentsAsJson);
    }

    void testRedirectGetParticipationRepositoryFilesWithContentAtCommitForbidden(String testPrefix) throws Exception {
        // Seed LocalVC repo for existing participation1 and create a submission for its latest commit
        var studentLogin = participation1.getParticipantIdentifier();
        var repo = RepositoryExportTestUtil.seedStudentRepositoryForParticipation(localVCLocalCITestService, participation1);
        programmingExerciseStudentParticipationRepository.save(participation1);

        // Write files, commit, and push via util
        var commit = RepositoryExportTestUtil.writeFilesAndPush(repo, Map.of("README.md", "Initial commit", "A.java", "abc", "B.java", "cde", "C.java", "efg"),
                "seed student files");

        // Persist submission with commit hash
        var submission = new ProgrammingSubmission();
        submission.setType(SubmissionType.MANUAL);
        submission.setCommitHash(commit.getId().getName());
        programmingExerciseUtilService.addProgrammingSubmission(programmingExercise, submission, studentLogin);

        // Expect forbidden for current user
        request.get("/api/programming/programming-exercise-participations/" + participation1.getId() + "/files-content/" + submission.getCommitHash(), HttpStatus.FORBIDDEN,
                Map.class);
    }

    private long getMaxProgrammingExerciseId() {
        return programmingExerciseRepository.findAll(PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "id"))).stream().mapToLong(ProgrammingExercise::getId).max().orElse(1L);
    }

    private void setupMocksForConsistencyChecksOnImport(ProgrammingExercise sourceExercise) throws Exception {
        var programmingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationAndAuxiliaryRepositoriesById(sourceExercise.getId()).orElseThrow();

        for (var auxiliaryRepository : programmingExercise.getAuxiliaryRepositories()) {
            mockDelegate.mockGetRepositorySlugFromRepositoryUri(sourceExercise.generateRepositoryName("auxrepo"), auxiliaryRepository.getVcsRepositoryUri());
        }
        mockDelegate.mockCheckIfBuildPlanExists(uriService.getProjectKeyFromRepositoryUri(programmingExercise.getVcsTemplateRepositoryUri()),
                programmingExercise.getTemplateBuildPlanId(), true, false);
        mockDelegate.mockCheckIfBuildPlanExists(uriService.getProjectKeyFromRepositoryUri(programmingExercise.getVcsSolutionRepositoryUri()),
                programmingExercise.getSolutionBuildPlanId(), true, false);
    }

    /**
     * Tests export of instructor repositories (template, solution, tests) with LocalVC server
     */
    public void exportInstructorRepositories_shouldReturnFile(RepositoryType repositoryType) throws Exception {
        // Set up the exercise with problem statement for export
        setupExerciseForExport();
        var templateUrl = "/api/programming/programming-exercises/" + programmingExercise.getId() + "/export-instructor-repository/" + repositoryType.name();
        String zip = request.get(templateUrl, HttpStatus.OK, String.class);
        assertThat(zip).isNotNull();
    }

    /**
     * Tests export of auxiliary repository with LocalVC server
     */
    public void exportInstructorAuxiliaryRepository_shouldReturnFile() throws Exception {
        setupExerciseForExport();

        var auxRepo = programmingExerciseUtilService.addAuxiliaryRepositoryToExercise(programmingExercise);

        String projectKey = programmingExercise.getProjectKey();
        String auxRepoName = programmingExercise.generateRepositoryName("auxrepo");
        auxRepo.setRepositoryUri(localVCBaseUri + "/git/" + projectKey + "/" + auxRepoName + ".git");
        auxRepo = auxiliaryRepositoryRepository.save(auxRepo);

        localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, auxRepoName);

        var url = "/api/programming/programming-exercises/" + programmingExercise.getId() + "/export-instructor-auxiliary-repository/" + auxRepo.getId();
        request.get(url, HttpStatus.OK, String.class);
    }

    /**
     * Helper method to set up the exercise for export testing
     */
    private void setupExerciseForExport() throws IOException, GitAPIException, URISyntaxException, Exception {
        // Add problem statement with embedded files (simplified version of generateProgrammingExerciseForExport)
        String problemStatement = """
                Problem statement
                ![mountain.jpg](/api/core/files/markdown/test-image.jpg)
                <img src="/api/core/files/markdown/test-image2.jpg" width="400">
                """;
        programmingExercise.setProblemStatement(problemStatement);

        programmingExercise = programmingExerciseRepository.save(programmingExercise);

        if (programmingExercise.getTemplateParticipation() == null) {
            programmingExercise = programmingExerciseParticipationUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);
        }
        if (programmingExercise.getSolutionParticipation() == null) {
            programmingExercise = programmingExerciseParticipationUtilService.addSolutionParticipationForProgrammingExercise(programmingExercise);
        }

        programmingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationById(programmingExercise.getId()).orElseThrow();

        // Create and wire base repos via shared util to reduce duplication
        RepositoryExportTestUtil.createAndWireBaseRepositories(localVCLocalCITestService, programmingExercise);

        programmingExercise = programmingExerciseRepository.save(programmingExercise);
    }
}
