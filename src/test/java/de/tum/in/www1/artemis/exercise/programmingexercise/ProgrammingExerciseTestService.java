package de.tum.in.www1.artemis.exercise.programmingexercise;

import static de.tum.in.www1.artemis.domain.enumeration.ExerciseMode.INDIVIDUAL;
import static de.tum.in.www1.artemis.domain.enumeration.ExerciseMode.TEAM;
import static de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage.C;
import static de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage.JAVA;
import static de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage.SWIFT;
import static de.tum.in.www1.artemis.service.export.ProgrammingExerciseExportService.BUILD_PLAN_FILE_NAME;
import static de.tum.in.www1.artemis.service.export.ProgrammingExerciseExportService.EXPORTED_EXERCISE_DETAILS_FILE_PREFIX;
import static de.tum.in.www1.artemis.service.export.ProgrammingExerciseExportService.EXPORTED_EXERCISE_PROBLEM_STATEMENT_FILE_PREFIX;
import static de.tum.in.www1.artemis.util.TestConstants.COMMIT_HASH_OBJECT_ID;
import static de.tum.in.www1.artemis.web.rest.programming.ProgrammingExerciseResourceEndpoints.GENERATE_TESTS;
import static de.tum.in.www1.artemis.web.rest.programming.ProgrammingExerciseResourceEndpoints.IMPORT;
import static de.tum.in.www1.artemis.web.rest.programming.ProgrammingExerciseResourceEndpoints.ROOT;
import static de.tum.in.www1.artemis.web.rest.programming.ProgrammingExerciseResourceEndpoints.SETUP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.within;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.validation.constraints.NotNull;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.CanceledException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.config.StaticCodeAnalysisConfigurer;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.Authority;
import de.tum.in.www1.artemis.domain.AuxiliaryRepository;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.StaticCodeAnalysisCategory;
import de.tum.in.www1.artemis.domain.Team;
import de.tum.in.www1.artemis.domain.TeamAssignmentConfig;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.VcsRepositoryUri;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseMode;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.ProjectType;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExamUser;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.domain.hestia.ExerciseHint;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseTask;
import de.tum.in.www1.artemis.domain.participation.Participant;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismDetectionConfig;
import de.tum.in.www1.artemis.domain.statistics.BuildLogStatisticsEntry;
import de.tum.in.www1.artemis.domain.submissionpolicy.LockRepositoryPolicy;
import de.tum.in.www1.artemis.exam.ExamFactory;
import de.tum.in.www1.artemis.exam.ExamUtilService;
import de.tum.in.www1.artemis.exception.GitException;
import de.tum.in.www1.artemis.exception.VersionControlException;
import de.tum.in.www1.artemis.participation.ParticipationFactory;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.repository.AuxiliaryRepositoryRepository;
import de.tum.in.www1.artemis.repository.BuildLogStatisticsEntryRepository;
import de.tum.in.www1.artemis.repository.BuildPlanRepository;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.repository.ExamUserRepository;
import de.tum.in.www1.artemis.repository.ParticipationRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseStudentParticipationTestRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTestRepository;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionTestRepository;
import de.tum.in.www1.artemis.repository.StaticCodeAnalysisCategoryRepository;
import de.tum.in.www1.artemis.repository.StudentExamRepository;
import de.tum.in.www1.artemis.repository.SubmissionRepository;
import de.tum.in.www1.artemis.repository.TeamRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.hestia.ProgrammingExerciseTaskRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.FilePathService;
import de.tum.in.www1.artemis.service.ParticipationService;
import de.tum.in.www1.artemis.service.UriService;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.connectors.ci.ContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.gitlab.GitLabException;
import de.tum.in.www1.artemis.service.connectors.jenkins.build_plan.JenkinsBuildPlanUtils;
import de.tum.in.www1.artemis.service.connectors.vcs.VersionControlRepositoryPermission;
import de.tum.in.www1.artemis.service.connectors.vcs.VersionControlService;
import de.tum.in.www1.artemis.service.export.CourseExamExportService;
import de.tum.in.www1.artemis.service.programming.JavaTemplateUpgradeService;
import de.tum.in.www1.artemis.service.programming.ProgrammingLanguageFeature;
import de.tum.in.www1.artemis.service.scheduled.AutomaticProgrammingExerciseCleanupService;
import de.tum.in.www1.artemis.service.user.PasswordService;
import de.tum.in.www1.artemis.user.UserFactory;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.util.ExamPrepareExercisesTestUtil;
import de.tum.in.www1.artemis.util.GitUtilService.MockFileRepositoryUri;
import de.tum.in.www1.artemis.util.InvalidExamExerciseDatesArgumentProvider.InvalidExamExerciseDateConfiguration;
import de.tum.in.www1.artemis.util.LocalRepository;
import de.tum.in.www1.artemis.util.RequestUtilService;
import de.tum.in.www1.artemis.util.TestConstants;
import de.tum.in.www1.artemis.util.ZipFileTestUtilService;
import de.tum.in.www1.artemis.web.rest.dto.BuildLogStatisticsDTO;
import de.tum.in.www1.artemis.web.rest.dto.CourseForDashboardDTO;

/**
 * Note: this class should be independent of the actual VCS and CIS and contains common test logic for scenarios:
 * 1) Jenkins + Gitlab
 * The local CI + local VC systems require a different setup as there are no requests to external systems and only minimal mocking is necessary. See
 * {@link ProgrammingExerciseLocalVCLocalCIIntegrationTest}.
 */
@Service
public class ProgrammingExerciseTestService {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingExerciseTestService.class);

    @Value("${artemis.version-control.default-branch:main}")
    protected String defaultBranch;

    @Autowired
    private RequestUtilService request;

    @Autowired
    private GitService gitService;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private StaticCodeAnalysisCategoryRepository staticCodeAnalysisCategoryRepository;

    @Autowired
    private ParticipationService participationService;

    @Autowired
    private ParticipationRepository participationRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private ProgrammingSubmissionTestRepository programmingSubmissionRepository;

    @Autowired
    private BuildLogStatisticsEntryRepository buildLogStatisticsEntryRepository;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private ZipFileTestUtilService zipFileTestUtilService;

    @Autowired(required = false)
    private AutomaticProgrammingExerciseCleanupService automaticProgrammingExerciseCleanupService;

    @Value("${artemis.course-archives-path}")
    private Path courseArchivesDirPath;

    @Autowired
    private CourseExamExportService courseExamExportService;

    @Autowired
    private AuxiliaryRepositoryRepository auxiliaryRepositoryRepository;

    @Autowired
    private JavaTemplateUpgradeService javaTemplateUpgradeService;

    @Autowired
    private ProgrammingExerciseTaskRepository programmingExerciseTaskRepository;

    @Autowired
    private ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository;

    @Autowired
    private UriService uriService;

    @Autowired
    private ProgrammingExerciseStudentParticipationTestRepository programmingExerciseParticipationTestRepository;

    @Autowired
    private ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    @Autowired
    private StudentExamRepository studentExamRepository;

    @Autowired
    private ExamUserRepository examUserRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private BuildPlanRepository buildPlanRepository;

    public Course course;

    public ProgrammingExercise exercise;

    public ProgrammingExercise examExercise;

    public static final int numberOfStudents = 5;

    public static final String studentLogin = "student1";

    public static final String teamShortName = "team1";

    public static final String PARTICIPATION_BASE_URL = "/api/participations/";

    public LocalRepository exerciseRepo;

    public LocalRepository testRepo;

    public LocalRepository solutionRepo;

    public LocalRepository auxRepo;

    public LocalRepository sourceExerciseRepo;

    public LocalRepository sourceTestRepo;

    public LocalRepository sourceSolutionRepo;

    public LocalRepository sourceAuxRepo;

    public LocalRepository studentRepo;

    public LocalRepository studentTeamRepo;

    // Injected in the constructor
    private VersionControlService versionControlService;

    // Injected in the constructor
    @SuppressWarnings("unused") // might be used in the future and is here for consistency reasons
    private ContinuousIntegrationService continuousIntegrationService;

    private MockDelegate mockDelegate;

    private String userPrefix;

    @Autowired
    private ProgrammingExerciseTestRepository programmingExerciseTestRepository;

    public void setupTestUsers(String userPrefix, int additionalStudents, int additionalTutors, int additionalEditors, int additionalInstructors) {
        this.userPrefix = userPrefix;
        userUtilService.addUsers(userPrefix, numberOfStudents + additionalStudents, additionalTutors + 1, additionalEditors + 1, additionalInstructors + 1);
    }

    public void setup(MockDelegate mockDelegate, VersionControlService versionControlService, ContinuousIntegrationService continuousIntegrationService) throws Exception {
        mockDelegate.resetMockProvider();
        exerciseRepo = new LocalRepository(defaultBranch);
        testRepo = new LocalRepository(defaultBranch);
        solutionRepo = new LocalRepository(defaultBranch);
        auxRepo = new LocalRepository(defaultBranch);
        sourceExerciseRepo = new LocalRepository(defaultBranch);
        sourceTestRepo = new LocalRepository(defaultBranch);
        sourceSolutionRepo = new LocalRepository(defaultBranch);
        sourceAuxRepo = new LocalRepository(defaultBranch);
        studentRepo = new LocalRepository(defaultBranch);
        studentTeamRepo = new LocalRepository(defaultBranch);
        this.mockDelegate = mockDelegate;
        this.versionControlService = versionControlService;
        this.continuousIntegrationService = continuousIntegrationService;

        course = courseUtilService.addEmptyCourse();
        ExerciseGroup exerciseGroup = examUtilService.addExerciseGroupWithExamAndCourse(true);
        examExercise = ProgrammingExerciseFactory.generateProgrammingExerciseForExam(exerciseGroup);
        exercise = ProgrammingExerciseFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(7), course);

        exerciseRepo.configureRepos("exerciseLocalRepo", "exerciseOriginRepo");
        testRepo.configureRepos("testLocalRepo", "testOriginRepo");
        solutionRepo.configureRepos("solutionLocalRepo", "solutionOriginRepo");
        auxRepo.configureRepos("auxLocalRepo", "auxOriginRepo");
        sourceExerciseRepo.configureRepos("sourceExerciseLocalRepo", "sourceExerciseOriginRepo");
        sourceTestRepo.configureRepos("sourceTestLocalRepo", "sourceTestOriginRepo");
        sourceSolutionRepo.configureRepos("sourceSolutionLocalRepo", "sourceSolutionOriginRepo");
        sourceAuxRepo.configureRepos("sourceAuxLocalRepo", "sourceAuxOriginRepo");
        studentRepo.configureRepos("studentRepo", "studentOriginRepo");
        studentTeamRepo.configureRepos("studentTeamRepo", "studentTeamOriginRepo");

        setupRepositoryMocks(exercise, exerciseRepo, solutionRepo, testRepo, auxRepo);
        setupRepositoryMocksParticipant(exercise, userPrefix + studentLogin, studentRepo);
        setupRepositoryMocksParticipant(exercise, userPrefix + teamShortName, studentTeamRepo);
    }

    public void tearDown() throws Exception {
        exerciseRepo.resetLocalRepo();
        testRepo.resetLocalRepo();
        solutionRepo.resetLocalRepo();
        sourceExerciseRepo.resetLocalRepo();
        sourceTestRepo.resetLocalRepo();
        sourceSolutionRepo.resetLocalRepo();
        sourceAuxRepo.resetLocalRepo();
        studentRepo.resetLocalRepo();
        studentTeamRepo.resetLocalRepo();
    }

    public void setupRepositoryMocks(ProgrammingExercise exercise) throws Exception {
        setupRepositoryMocks(exercise, exerciseRepo, solutionRepo, testRepo, auxRepo);
    }

    public void setupRepositoryMocks(ProgrammingExercise exercise, LocalRepository exerciseRepository, LocalRepository solutionRepository, LocalRepository testRepository,
            LocalRepository auxRepository) throws Exception {
        final var projectKey = exercise.getProjectKey();
        final var exerciseRepoName = exercise.generateRepositoryName(RepositoryType.TEMPLATE);
        final var solutionRepoName = exercise.generateRepositoryName(RepositoryType.SOLUTION);
        final var testRepoName = exercise.generateRepositoryName(RepositoryType.TESTS);
        final var auxRepoName = exercise.generateRepositoryName("auxrepo");
        setupRepositoryMocks(projectKey, exerciseRepository, exerciseRepoName, solutionRepository, solutionRepoName, testRepository, testRepoName, auxRepository, auxRepoName);
    }

    /**
     * Mocks the access and interaction with repository mocks on the local file system.
     *
     * @param projectKey         the unique short identifier of the exercise in the CI system
     * @param exerciseRepository represents exercise template code repository
     * @param exerciseRepoName   the name of the exercise repository
     * @param solutionRepository represents exercise solution code repository
     * @param solutionRepoName   the name of the solution repository
     * @param testRepository     represents exercise test code repository
     * @param testRepoName       the name of the test repository
     * @param auxRepository      represents an arbitrary template code repository
     * @param auxRepoName        the name of the auxiliary repository
     * @throws Exception in case any repository uri is malformed or the GitService fails
     */
    public void setupRepositoryMocks(String projectKey, LocalRepository exerciseRepository, String exerciseRepoName, LocalRepository solutionRepository, String solutionRepoName,
            LocalRepository testRepository, String testRepoName, LocalRepository auxRepository, String auxRepoName) throws Exception {
        var exerciseRepoTestUrl = new MockFileRepositoryUri(exerciseRepository.originRepoFile);
        var testRepoTestUrl = new MockFileRepositoryUri(testRepository.originRepoFile);
        var solutionRepoTestUrl = new MockFileRepositoryUri(solutionRepository.originRepoFile);
        var auxRepoTestUrl = new MockFileRepositoryUri(auxRepository.originRepoFile);

        doReturn(exerciseRepoTestUrl).when(versionControlService).getCloneRepositoryUri(projectKey, exerciseRepoName);
        doReturn(testRepoTestUrl).when(versionControlService).getCloneRepositoryUri(projectKey, testRepoName);
        doReturn(solutionRepoTestUrl).when(versionControlService).getCloneRepositoryUri(projectKey, solutionRepoName);
        doReturn(auxRepoTestUrl).when(versionControlService).getCloneRepositoryUri(projectKey, auxRepoName);

        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(exerciseRepository.localRepoFile.toPath(), null)).when(gitService)
                .getOrCheckoutRepository(exerciseRepoTestUrl, true);
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(testRepository.localRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(testRepoTestUrl,
                true);
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(solutionRepository.localRepoFile.toPath(), null)).when(gitService)
                .getOrCheckoutRepository(solutionRepoTestUrl, true);
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(auxRepository.localRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(auxRepoTestUrl, true);

        doNothing().when(gitService).pushSourceToTargetRepo(any(), any(), any());
        doNothing().when(gitService).combineAllCommitsOfRepositoryIntoOne(any());

        // we need separate mocks with VcsRepositoryUri here because MockFileRepositoryUri and VcsRepositoryUri do not seem to be compatible here
        mockDelegate.mockGetRepositorySlugFromRepositoryUri(exerciseRepoName, exerciseRepoTestUrl);
        mockDelegate.mockGetRepositorySlugFromRepositoryUri(testRepoName, testRepoTestUrl);
        mockDelegate.mockGetRepositorySlugFromRepositoryUri(solutionRepoName, solutionRepoTestUrl);
        mockDelegate.mockGetRepositorySlugFromRepositoryUri(auxRepoName, auxRepoTestUrl);

        mockDelegate.mockGetProjectKeyFromRepositoryUri(projectKey, exerciseRepoTestUrl);
        mockDelegate.mockGetProjectKeyFromRepositoryUri(projectKey, testRepoTestUrl);
        mockDelegate.mockGetProjectKeyFromRepositoryUri(projectKey, solutionRepoTestUrl);
        mockDelegate.mockGetProjectKeyFromRepositoryUri(projectKey, auxRepoTestUrl);

        mockDelegate.mockGetRepositoryPathFromRepositoryUri(projectKey + "/" + exerciseRepoName, exerciseRepoTestUrl);
        mockDelegate.mockGetRepositoryPathFromRepositoryUri(projectKey + "/" + testRepoName, testRepoTestUrl);
        mockDelegate.mockGetRepositoryPathFromRepositoryUri(projectKey + "/" + solutionRepoName, solutionRepoTestUrl);
        mockDelegate.mockGetRepositoryPathFromRepositoryUri(projectKey + "/" + auxRepoName, auxRepoTestUrl);

        mockDelegate.mockGetProjectKeyFromAnyUrl(projectKey);
    }

    /**
     * can be invoked for teams and students
     */
    public void setupRepositoryMocksParticipant(ProgrammingExercise exercise, String participantName, LocalRepository studentRepo) throws Exception {
        setupRepositoryMocksParticipant(exercise, participantName, studentRepo, false);
    }

    public void setupRepositoryMocksParticipant(ProgrammingExercise exercise, String participantName, LocalRepository studentRepo, boolean practiceMode) throws Exception {
        final var projectKey = exercise.getProjectKey();
        String participantRepoName = projectKey.toLowerCase() + "-" + (practiceMode ? "practice-" : "") + participantName;
        var participantRepoTestUrl = ParticipationFactory.getMockFileRepositoryUri(studentRepo);
        doReturn(participantRepoTestUrl).when(versionControlService).getCloneRepositoryUri(projectKey, participantRepoName);
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(studentRepo.localRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(participantRepoTestUrl,
                true);
        mockDelegate.mockGetRepositorySlugFromRepositoryUri(participantRepoName, participantRepoTestUrl);
        mockDelegate.mockGetProjectKeyFromRepositoryUri(projectKey, participantRepoTestUrl);
        mockDelegate.mockGetRepositoryPathFromRepositoryUri(projectKey + "/" + participantRepoName, participantRepoTestUrl);
    }

    // TEST
    void createProgrammingExercise_sequential_validExercise_created(ProgrammingLanguage programmingLanguage) throws Exception {
        exercise = ProgrammingExerciseFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(7), course, programmingLanguage);
        exercise.setSequentialTestRuns(true);
        exercise.setChannelName("testchannel-pe");
        setupRepositoryMocks(exercise, exerciseRepo, solutionRepo, testRepo, auxRepo);
        mockDelegate.mockConnectorRequestsForSetup(exercise, false, false, false);
        validateProgrammingExercise(request.postWithResponseBody(ROOT + SETUP, exercise, ProgrammingExercise.class, HttpStatus.CREATED));
    }

    // TEST
    void createProgrammingExercise_custom_build_plan_validExercise_created(ProgrammingLanguage programmingLanguage, boolean customBuildPlanWorks) throws Exception {
        exercise = ProgrammingExerciseFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(7), course, programmingLanguage);
        String validWindfile = """
                {
                  "api": "v0.0.1",
                  "metadata": {
                    "name": "example windfile",
                    "description": "example windfile",
                    "id": "example-windfile"
                  },
                  "actions": [
                    {
                      "name": "valid-action",
                      "class": "script-action",
                      "script": "echo $PATH",
                      "runAlways": true
                    },
                    {
                      "name": "valid-action1",
                      "platform": "jenkins",
                      "runAlways": true
                    },
                    {
                      "name": "valid-action2",
                      "script": "bash script",
                      "runAlways": true
                    }
                  ]
                }""";

        exercise.setBuildPlanConfiguration(validWindfile);
        if (programmingLanguage == C) {
            exercise.setProjectType(ProjectType.FACT);
        }
        exercise.setChannelName("testchannel-pe");
        setupRepositoryMocks(exercise, exerciseRepo, solutionRepo, testRepo, auxRepo);
        mockDelegate.mockConnectorRequestsForSetup(exercise, false, true, customBuildPlanWorks);
        validateProgrammingExercise(request.postWithResponseBody(ROOT + SETUP, exercise, ProgrammingExercise.class, HttpStatus.CREATED));
    }

    // TEST
    void createProgrammingExercise_mode_validExercise_created(ExerciseMode mode) throws Exception {
        exercise.setMode(mode);
        exercise.setChannelName("testchannel-pe");
        mockDelegate.mockConnectorRequestsForSetup(exercise, false, false, false);
        validateProgrammingExercise(request.postWithResponseBody(ROOT + SETUP, exercise, ProgrammingExercise.class, HttpStatus.CREATED));
    }

    // TEST
    void createProgrammingExercise_programmingLanguage_validExercise_created(ProgrammingLanguage language, ProgrammingLanguageFeature programmingLanguageFeature) throws Exception {
        exercise.setProgrammingLanguage(language);
        if (language == SWIFT) {
            exercise.setPackageName("swiftTest");
        }
        exercise.setProjectType(programmingLanguageFeature.projectTypes().isEmpty() ? null : programmingLanguageFeature.projectTypes().get(0));
        mockDelegate.mockConnectorRequestsForSetup(exercise, false, false, false);
        exercise.setChannelName("testchannel-pe");
        validateProgrammingExercise(request.postWithResponseBody(ROOT + SETUP, exercise, ProgrammingExercise.class, HttpStatus.CREATED));
    }

    // TEST
    void createProgrammingExercise_validExercise_bonusPointsIsNull() throws Exception {
        exercise.setBonusPoints(null);
        mockDelegate.mockConnectorRequestsForSetup(exercise, false, false, false);
        exercise.setChannelName("testchannel-pe");
        var generatedExercise = request.postWithResponseBody(ROOT + SETUP, exercise, ProgrammingExercise.class);
        var savedExercise = programmingExerciseRepository.findById(generatedExercise.getId()).orElseThrow();
        assertThat(generatedExercise.getBonusPoints()).isZero();
        assertThat(savedExercise.getBonusPoints()).isZero();
    }

    void importFromFile_validJavaExercise_isSuccessfullyImported(boolean scaEnabled) throws Exception {
        mockDelegate.mockConnectorRequestForImportFromFile(exercise);
        Resource resource = new ClassPathResource("test-data/import-from-file/valid-import.zip");
        if (scaEnabled) {
            exercise.setStaticCodeAnalysisEnabled(true);
        }

        var file = new MockMultipartFile("file", "test.zip", "application/zip", resource.getInputStream());
        var course = courseUtilService.addEmptyCourse();
        exercise.setChannelName("testchannel-pe");
        var importedExercise = request.postWithMultipartFile(ROOT + "/courses/" + course.getId() + "/programming-exercises/import-from-file", exercise, "programmingExercise", file,
                ProgrammingExercise.class, HttpStatus.OK);
        assertThat(importedExercise).isNotNull();
        assertThat(importedExercise.getProgrammingLanguage()).isEqualTo(JAVA);
        assertThat(importedExercise.getMode()).isEqualTo(ExerciseMode.INDIVIDUAL);
        assertThat(importedExercise.getProjectType()).isEqualTo(ProjectType.PLAIN_MAVEN);
        if (scaEnabled) {
            assertThat(importedExercise.isStaticCodeAnalysisEnabled()).isTrue();
        }
        else {
            assertThat(importedExercise.isStaticCodeAnalysisEnabled()).isFalse();
        }
        var savedExercise = programmingExerciseRepository.findById(importedExercise.getId()).orElseThrow();
        assertThat(savedExercise).isNotNull();
        assertThat(savedExercise.getProgrammingLanguage()).isEqualTo(JAVA);
        assertThat(savedExercise.getMode()).isEqualTo(ExerciseMode.INDIVIDUAL);
        assertThat(savedExercise.getProjectType()).isEqualTo(ProjectType.PLAIN_MAVEN);
        if (scaEnabled) {
            assertThat(savedExercise.isStaticCodeAnalysisEnabled()).isTrue();
        }
        else {
            assertThat(savedExercise.isStaticCodeAnalysisEnabled()).isFalse();
        }
        assertThat(importedExercise.getCourseViaExerciseGroupOrCourseMember()).isEqualTo(course);
    }

    void importFromFile_validExercise_isSuccessfullyImported(ProgrammingLanguage language) throws Exception {
        mockDelegate.mockConnectorRequestForImportFromFile(exercise);
        Resource resource = null;
        exercise.programmingLanguage(language);
        exercise.setProjectType(null);
        switch (language) {
            case PYTHON -> resource = new ClassPathResource("test-data/import-from-file/valid-import-python.zip");
            case C -> {
                resource = new ClassPathResource("test-data/import-from-file/valid-import-c.zip");
                exercise.setProjectType(ProjectType.FACT);
            }
            case HASKELL -> resource = new ClassPathResource("test-data/import-from-file/valid-import-haskell.zip");
            case OCAML -> resource = new ClassPathResource("test-data/import-from-file/valid-import-ocaml.zip");
            case ASSEMBLER -> resource = new ClassPathResource("test-data/import-from-file/valid-import-assembler.zip");
        }

        var file = new MockMultipartFile("file", "test.zip", "application/zip", resource.getInputStream());
        exercise.setChannelName("testchannel-pe");
        request.postWithMultipartFile(ROOT + "/courses/" + course.getId() + "/programming-exercises/import-from-file", exercise, "programmingExercise", file,
                ProgrammingExercise.class, HttpStatus.OK);
    }

    void importFromFile_embeddedFiles_embeddedFilesCopied() throws Exception {
        String embeddedFileName1 = "Markdown_2023-05-06T16-17-46-410_ad323711.jpg";
        String embeddedFileName2 = "Markdown_2023-05-06T16-17-46-822_b921f475.jpg";
        Path fileSystemPathEmbeddedFile1 = FilePathService.getMarkdownFilePath().resolve(embeddedFileName1);
        Path fileSystemPathEmbeddedFile2 = FilePathService.getMarkdownFilePath().resolve(embeddedFileName2);
        // clean up to make sure the test doesn't pass because it has passed previously
        if (Files.exists(fileSystemPathEmbeddedFile1)) {
            Files.delete(fileSystemPathEmbeddedFile1);
        }
        if (Files.exists(fileSystemPathEmbeddedFile2)) {
            Files.delete(fileSystemPathEmbeddedFile2);
        }
        mockDelegate.mockConnectorRequestForImportFromFile(exercise);

        Resource resource = new ClassPathResource("test-data/import-from-file/valid-import-embedded-files.zip");
        var file = new MockMultipartFile("file", "test.zip", "application/zip", resource.getInputStream());
        exercise.setChannelName("testchannel-pe");

        request.postWithMultipartFile(ROOT + "/courses/" + course.getId() + "/programming-exercises/import-from-file", exercise, "programmingExercise", file,
                ProgrammingExercise.class, HttpStatus.OK);
        assertThat(FilePathService.getMarkdownFilePath()).isDirectoryContaining(path -> embeddedFileName1.equals(path.getFileName().toString()))
                .isDirectoryContaining(path -> embeddedFileName2.equals(path.getFileName().toString()));

    }

    void importFromFile_buildPlanPresent_buildPlanUsed() throws Exception {
        mockDelegate.mockConnectorRequestForImportFromFile(exercise);
        var resource = new ClassPathResource("test-data/import-from-file/import-with-build-plan.zip");
        var file = new MockMultipartFile("file", "test.zip", "application/zip", resource.getInputStream());
        exercise.setChannelName("testchannel-pe");
        var importedExercise = request.postWithMultipartFile(ROOT + "/courses/" + course.getId() + "/programming-exercises/import-from-file", exercise, "programmingExercise", file,
                ProgrammingExercise.class, HttpStatus.OK);
        var buildPlan = buildPlanRepository.findByProgrammingExercises_Id(importedExercise.getId());
        assertThat(buildPlan).isPresent();
        assertThat(buildPlan.orElseThrow().getBuildPlan()).isEqualTo("my super cool build plan");

    }

    void importFromFile_missingExerciseDetailsJson_badRequest() throws Exception {
        Resource resource = new ClassPathResource("test-data/import-from-file/missing-json.zip");
        var file = new MockMultipartFile("file", "test.zip", "application/zip", resource.getInputStream());
        request.postWithMultipartFile(ROOT + "/courses/" + course.getId() + "/programming-exercises/import-from-file", exercise, "programmingExercise", file,
                ProgrammingExercise.class, HttpStatus.BAD_REQUEST);
    }

    void importFromFile_fileNoZip_badRequest() throws Exception {
        Resource resource = new ClassPathResource("test-data/import-from-file/valid-import.zip");
        var file = new MockMultipartFile("file", "test.txt", "application/zip", resource.getInputStream());
        request.postWithMultipartFile(ROOT + "/courses/" + course.getId() + "/programming-exercises/import-from-file", exercise, "programmingExercise", file,
                ProgrammingExercise.class, HttpStatus.BAD_REQUEST);
    }

    void importFromFile_tutor_forbidden() throws Exception {
        course.setInstructorGroupName("test");
        courseRepository.save(course);
        var file = new MockMultipartFile("file", "test.zip", "application/zip", new byte[0]);
        request.postWithMultipartFile(ROOT + "/courses/" + course.getId() + "/programming-exercises/import-from-file", exercise, "programmingExercise", file,
                ProgrammingExercise.class, HttpStatus.FORBIDDEN);
    }

    void importFromFile_missingRepository_BadRequest() throws Exception {
        Resource resource = new ClassPathResource("test-data/import-from-file/missing-repository.zip");
        var file = new MockMultipartFile("file", "test.zip", "application/zip", resource.getInputStream());
        request.postWithMultipartFile(ROOT + "/courses/" + course.getId() + "/programming-exercises/import-from-file", exercise, "programmingExercise", file,
                ProgrammingExercise.class, HttpStatus.BAD_REQUEST);
    }

    public void importFromFile_exception_DirectoryDeleted() throws Exception {
        mockDelegate.mockConnectorRequestForImportFromFile(exercise);
        Resource resource = new ClassPathResource("test-data/import-from-file/valid-import.zip");

        var file = new MockMultipartFile("file", "test.zip", "application/zip", resource.getInputStream());
        var course = courseUtilService.addEmptyCourse();
        exercise.setChannelName("testchannel-pe");
        request.postWithMultipartFile(ROOT + "/courses/" + course.getId() + "/programming-exercises/import-from-file", exercise, "programmingExercise", file,
                ProgrammingExercise.class, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // TEST
    void createProgrammingExercise_validExercise_withStaticCodeAnalysis(ProgrammingLanguage language, ProgrammingLanguageFeature programmingLanguageFeature) throws Exception {
        exercise.setStaticCodeAnalysisEnabled(true);
        exercise.setProgrammingLanguage(language);
        if (language == SWIFT) {
            exercise.setPackageName("swiftTest");
        }
        // Exclude ProjectType FACT as SCA is not supported
        if (language == C) {
            exercise.setProjectType(ProjectType.GCC);
        }
        else {
            exercise.setProjectType(programmingLanguageFeature.projectTypes().isEmpty() ? null : programmingLanguageFeature.projectTypes().get(0));
        }
        mockDelegate.mockConnectorRequestsForSetup(exercise, false, false, false);
        exercise.setChannelName("testchannel-pe");
        var generatedExercise = request.postWithResponseBody(ROOT + SETUP, exercise, ProgrammingExercise.class);

        exercise.setId(generatedExercise.getId());
        assertThat(exercise).isEqualTo(generatedExercise);
        var staticCodeAnalysisCategories = staticCodeAnalysisCategoryRepository.findByExerciseId(generatedExercise.getId());
        assertThat(staticCodeAnalysisCategories).usingRecursiveFieldByFieldElementComparatorIgnoringFields("id", "exercise")
                .isEqualTo(StaticCodeAnalysisConfigurer.staticCodeAnalysisConfiguration().get(exercise.getProgrammingLanguage()));
        StaticCodeAnalysisConfigurer.staticCodeAnalysisConfiguration().get(exercise.getProgrammingLanguage()).forEach(config -> config.categoryMappings().forEach(mapping -> {
            assertThat(mapping.tool()).isNotNull();
            assertThat(mapping.category()).isNotNull();
        }));
    }

    // TEST
    void createProgrammingExercise_failToCreateProjectInCi() throws Exception {
        exercise.setMode(ExerciseMode.INDIVIDUAL);
        exercise.setChannelName("testchannel-pe");
        mockDelegate.mockConnectorRequestsForSetup(exercise, true, false, false);
        var programmingExercise = request.postWithResponseBody(ROOT + SETUP, exercise, ProgrammingExercise.class, HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(programmingExercise).isNull();
    }

    // TEST
    void createProgrammingExerciseForExam_validExercise_created() throws Exception {
        setupRepositoryMocks(examExercise, exerciseRepo, solutionRepo, testRepo, auxRepo);

        mockDelegate.mockConnectorRequestsForSetup(examExercise, false, false, false);
        final var generatedExercise = request.postWithResponseBody(ROOT + SETUP, examExercise, ProgrammingExercise.class, HttpStatus.CREATED);

        examExercise.setId(generatedExercise.getId());
        assertThat(examExercise).isEqualTo(generatedExercise);
        final Exam loadedExam = examRepository.findWithExerciseGroupsAndExercisesById(examExercise.getExamViaExerciseGroupOrCourseMember().getId()).orElseThrow();
        assertThat(loadedExam.getNumberOfExercisesInExam()).isEqualTo(1);
    }

    // TEST
    void createProgrammingExerciseForExam_invalidExercise_dates(InvalidExamExerciseDateConfiguration dates) throws Exception {
        setupRepositoryMocks(examExercise, exerciseRepo, solutionRepo, testRepo, auxRepo);
        mockDelegate.mockConnectorRequestsForSetup(examExercise, false, false, false);

        request.postWithResponseBody(ROOT + SETUP, dates.applyTo(examExercise), ProgrammingExercise.class, HttpStatus.BAD_REQUEST);
    }

    // TEST
    void createProgrammingExerciseForExam_DatesSet() throws Exception {
        setupRepositoryMocks(examExercise, exerciseRepo, solutionRepo, testRepo, auxRepo);
        mockDelegate.mockConnectorRequestsForSetup(examExercise, false, false, false);
        ZonedDateTime someMoment = ZonedDateTime.of(2000, 6, 15, 0, 0, 0, 0, ZoneId.of("Z"));
        examExercise.setDueDate(someMoment);

        request.postWithResponseBody(ROOT + SETUP, examExercise, ProgrammingExercise.class, HttpStatus.BAD_REQUEST);
    }

    private AuxiliaryRepository addAuxiliaryRepositoryToProgrammingExercise(ProgrammingExercise sourceExercise) {
        AuxiliaryRepository repository = programmingExerciseUtilService.addAuxiliaryRepositoryToExercise(sourceExercise);
        var url = versionControlService.getCloneRepositoryUri(sourceExercise.getProjectKey(), new MockFileRepositoryUri(sourceAuxRepo.originRepoFile).toString());
        repository.setRepositoryUri(url.toString());
        return auxiliaryRepositoryRepository.save(repository);
    }

    // TEST
    void createAndImportJavaProgrammingExercise(boolean staticCodeAnalysisEnabled) throws Exception {
        setupRepositoryMocks(exercise, sourceExerciseRepo, sourceSolutionRepo, sourceTestRepo, sourceAuxRepo);
        mockDelegate.mockConnectorRequestsForSetup(exercise, false, false, false);
        exercise.setProjectType(ProjectType.MAVEN_MAVEN);
        exercise.setStaticCodeAnalysisEnabled(staticCodeAnalysisEnabled);
        exercise.setChannelName("testchannel-pe");
        var sourceExercise = request.postWithResponseBody(ROOT + SETUP, exercise, ProgrammingExercise.class, HttpStatus.CREATED);
        sourceExercise = programmingExerciseUtilService.loadProgrammingExerciseWithEagerReferences(sourceExercise);

        javaTemplateUpgradeService.upgradeTemplate(sourceExercise);

        // Setup exercises for import
        programmingExerciseUtilService.addTestCasesToProgrammingExercise(sourceExercise);
        programmingExerciseUtilService.addTasksToProgrammingExercise(sourceExercise);
        // Manually add task
        var task = new ProgrammingExerciseTask();
        task.setTaskName("Task 1");
        task.setExercise(sourceExercise);
        task.setTestCases(programmingExerciseTestCaseRepository.findByExerciseId(sourceExercise.getId()));
        sourceExercise.setTasks(Collections.singletonList(task));
        programmingExerciseTaskRepository.save(task);
        programmingExerciseRepository.save(sourceExercise);
        programmingExerciseUtilService.addHintsToExercise(sourceExercise);

        // Reset because we will add mocks for new requests
        mockDelegate.resetMockProvider();

        ProgrammingExercise exerciseToBeImported = ProgrammingExerciseFactory.generateToBeImportedProgrammingExercise("ImportTitle", "imported", exercise,
                courseUtilService.addEmptyCourse());
        exerciseToBeImported.setStaticCodeAnalysisEnabled(false);

        // TODO: at the moment, it does not work that the copied repositories include the same files as ones that have been created originally
        // this is probably the case, because the actual copy is not executed due to mocks
        final var exerciseRepoName = uriService.getRepositorySlugFromRepositoryUriString(sourceExercise.getTemplateParticipation().getRepositoryUri()).toLowerCase();
        final var solutionRepoName = uriService.getRepositorySlugFromRepositoryUriString(sourceExercise.getSolutionParticipation().getRepositoryUri()).toLowerCase();
        final var testRepoName = uriService.getRepositorySlugFromRepositoryUriString(sourceExercise.getTestRepositoryUri()).toLowerCase();
        final var auxRepoName = sourceExercise.generateRepositoryName("auxrepo");
        setupRepositoryMocks(sourceExercise.getProjectKey(), sourceExerciseRepo, exerciseRepoName, sourceSolutionRepo, solutionRepoName, sourceTestRepo, testRepoName,
                sourceAuxRepo, auxRepoName);
        setupRepositoryMocks(exerciseToBeImported, exerciseRepo, solutionRepo, testRepo, auxRepo);

        // Create request parameters
        var params = new LinkedMultiValueMap<String, String>();
        params.add("recreateBuildPlans", String.valueOf(true));
        params.add("updateTemplate", String.valueOf(true));

        mockDelegate.mockConnectorRequestsForImport(sourceExercise, exerciseToBeImported, true, false);
        setupMocksForConsistencyChecksOnImport(sourceExercise);

        // Import the exercise and load all referenced entities
        exerciseToBeImported.setChannelName("testchannel-pe-import");

        var importedExercise = request.postWithResponseBody(ROOT + IMPORT.replace("{sourceExerciseId}", sourceExercise.getId().toString()), exerciseToBeImported,
                ProgrammingExercise.class, params, HttpStatus.OK);
        importedExercise = programmingExerciseUtilService.loadProgrammingExerciseWithEagerReferences(importedExercise);

        // Check that the tasks were imported correctly (see #5474)
        assertThat(programmingExerciseTaskRepository.findByExerciseId(importedExercise.getId())).hasSameSizeAs(sourceExercise.getTasks());
    }

    // TEST
    void importExercise_created(ProgrammingLanguage programmingLanguage, boolean recreateBuildPlans, boolean addAuxRepos) throws Exception {
        boolean staticCodeAnalysisEnabled = programmingLanguage == JAVA || programmingLanguage == SWIFT;
        // Setup exercises for import
        ProgrammingExercise sourceExercise = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndStaticCodeAnalysisCategories(programmingLanguage);
        sourceExercise.setPlagiarismDetectionConfig(PlagiarismDetectionConfig.createDefault());
        sourceExercise = programmingExerciseRepository.save(sourceExercise);
        sourceExercise.setStaticCodeAnalysisEnabled(staticCodeAnalysisEnabled);
        programmingExerciseUtilService.addTestCasesToProgrammingExercise(sourceExercise);
        programmingExerciseUtilService.addHintsToExercise(sourceExercise);
        sourceExercise = programmingExerciseUtilService.loadProgrammingExerciseWithEagerReferences(sourceExercise);
        ProgrammingExercise exerciseToBeImported = ProgrammingExerciseFactory.generateToBeImportedProgrammingExercise("ImportTitle", "imported", sourceExercise,
                courseUtilService.addEmptyCourse());
        exerciseToBeImported.setStaticCodeAnalysisEnabled(staticCodeAnalysisEnabled);
        if (addAuxRepos) {
            addAuxiliaryRepositoryToProgrammingExercise(sourceExercise);
        }
        // Mock requests
        setupRepositoryMocks(sourceExercise, sourceExerciseRepo, sourceSolutionRepo, sourceTestRepo, sourceAuxRepo);
        setupRepositoryMocks(exerciseToBeImported, exerciseRepo, solutionRepo, testRepo, auxRepo);
        mockDelegate.mockConnectorRequestsForImport(sourceExercise, exerciseToBeImported, recreateBuildPlans, addAuxRepos);
        setupMocksForConsistencyChecksOnImport(sourceExercise);

        // Create request parameters
        var params = new LinkedMultiValueMap<String, String>();
        params.add("recreateBuildPlans", String.valueOf(recreateBuildPlans));

        // Import the exercise and load all referenced entities
        var importedExercise = request.postWithResponseBody(ROOT + IMPORT.replace("{sourceExerciseId}", sourceExercise.getId().toString()), exerciseToBeImported,
                ProgrammingExercise.class, params, HttpStatus.OK);
        importedExercise = programmingExerciseUtilService.loadProgrammingExerciseWithEagerReferences(importedExercise);

        if (staticCodeAnalysisEnabled) {
            // Assert correct creation of static code analysis categories
            var importedCategoryIds = importedExercise.getStaticCodeAnalysisCategories().stream().map(StaticCodeAnalysisCategory::getId).collect(Collectors.toSet());
            var sourceCategoryIds = sourceExercise.getStaticCodeAnalysisCategories().stream().map(StaticCodeAnalysisCategory::getId).collect(Collectors.toSet());
            assertThat(importedCategoryIds).doesNotContainAnyElementsOf(sourceCategoryIds);
            assertThat(importedExercise.getStaticCodeAnalysisCategories()).usingRecursiveFieldByFieldElementComparatorIgnoringFields("id", "exercise")
                    .containsExactlyInAnyOrderElementsOf(sourceExercise.getStaticCodeAnalysisCategories());
        }

        // Assert correct creation of test cases
        var importedTestCaseIds = importedExercise.getTestCases().stream().map(ProgrammingExerciseTestCase::getId).collect(Collectors.toSet());
        var sourceTestCaseIds = sourceExercise.getTestCases().stream().map(ProgrammingExerciseTestCase::getId).collect(Collectors.toSet());
        assertThat(importedTestCaseIds).doesNotContainAnyElementsOf(sourceTestCaseIds);
        assertThat(importedExercise.getTestCases()).usingRecursiveFieldByFieldElementComparator()
                .usingRecursiveFieldByFieldElementComparatorIgnoringFields("id", "exercise", "tasks", "solutionEntries", "coverageEntries")
                .containsExactlyInAnyOrderElementsOf(sourceExercise.getTestCases());

        // Assert correct creation of hints
        var importedHintIds = importedExercise.getExerciseHints().stream().map(ExerciseHint::getId).collect(Collectors.toSet());
        var sourceHintIds = sourceExercise.getExerciseHints().stream().map(ExerciseHint::getId).collect(Collectors.toSet());
        assertThat(importedHintIds).doesNotContainAnyElementsOf(sourceHintIds);
        assertThat(importedExercise.getExerciseHints()).usingRecursiveFieldByFieldElementComparatorIgnoringFields("id", "exercise", "exerciseHintActivations")
                .containsExactlyInAnyOrderElementsOf(sourceExercise.getExerciseHints());

        // Assert creation of new build plan ids
        assertThat(importedExercise.getSolutionParticipation().getBuildPlanId()).isNotBlank().isNotEqualTo(sourceExercise.getSolutionParticipation().getBuildPlanId());
        assertThat(importedExercise.getTemplateParticipation().getBuildPlanId()).isNotBlank().isNotEqualTo(sourceExercise.getTemplateParticipation().getBuildPlanId());
    }

    void updateBuildPlanURL() throws Exception {
        MockedStatic<JenkinsBuildPlanUtils> mockedUtils = mockStatic(JenkinsBuildPlanUtils.class);
        ArgumentCaptor<String> toBeReplacedCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> replacementCaptor = ArgumentCaptor.forClass(String.class);
        mockedUtils.when(() -> JenkinsBuildPlanUtils.replaceScriptParameters(any(), toBeReplacedCaptor.capture(), replacementCaptor.capture())).thenCallRealMethod();

        boolean staticCodeAnalysisEnabled = true;
        // Setup exercises for import
        ProgrammingExercise sourceExercise = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndStaticCodeAnalysisCategories(JAVA);
        sourceExercise.setStaticCodeAnalysisEnabled(staticCodeAnalysisEnabled);
        sourceExercise.generateAndSetBuildPlanAccessSecret();
        programmingExerciseUtilService.addTestCasesToProgrammingExercise(sourceExercise);
        programmingExerciseUtilService.addHintsToExercise(sourceExercise);
        sourceExercise = programmingExerciseUtilService.loadProgrammingExerciseWithEagerReferences(sourceExercise);
        ProgrammingExercise exerciseToBeImported = ProgrammingExerciseFactory.generateToBeImportedProgrammingExercise("ImportTitle", "imported", sourceExercise,
                courseUtilService.addEmptyCourse());
        exerciseToBeImported.setStaticCodeAnalysisEnabled(staticCodeAnalysisEnabled);

        // Mock requests
        setupRepositoryMocks(sourceExercise, sourceExerciseRepo, sourceSolutionRepo, sourceTestRepo, sourceAuxRepo);
        setupRepositoryMocks(exerciseToBeImported, exerciseRepo, solutionRepo, testRepo, auxRepo);
        mockDelegate.mockConnectorRequestsForImport(sourceExercise, exerciseToBeImported, false, false);
        setupMocksForConsistencyChecksOnImport(sourceExercise);

        // Create request parameters
        var params = new LinkedMultiValueMap<String, String>();
        params.add("recreateBuildPlans", String.valueOf(false));

        // Import the exercise and load all referenced entities
        var importedExercise = request.postWithResponseBody(ROOT + IMPORT.replace("{sourceExerciseId}", sourceExercise.getId().toString()), exerciseToBeImported,
                ProgrammingExercise.class, params, HttpStatus.OK);

        // other calls are for repository URI replacements, we only care about build plan URL replacements
        List<String> toBeReplacedURLs = toBeReplacedCaptor.getAllValues().subList(0, 2);
        List<String> replacementURLs = replacementCaptor.getAllValues().subList(0, 2);

        assertThat(sourceExercise.getBuildPlanAccessSecret()).isNotEqualTo(importedExercise.getBuildPlanAccessSecret());
        assertThat(toBeReplacedURLs.get(0)).contains(sourceExercise.getBuildPlanAccessSecret());
        assertThat(toBeReplacedURLs.get(1)).contains(sourceExercise.getBuildPlanAccessSecret());
        assertThat(replacementURLs.get(0)).contains(importedExercise.getBuildPlanAccessSecret());
        assertThat(replacementURLs.get(1)).contains(importedExercise.getBuildPlanAccessSecret());
    }

    // TEST
    void importExercise_enablePlanFails() throws Exception {
        // Setup exercises for import
        ProgrammingExercise sourceExercise = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndStaticCodeAnalysisCategories();
        // programmingExerciseUtilService.addTestCasesToProgrammingExercise(sourceExercise);
        sourceExercise = programmingExerciseUtilService.loadProgrammingExerciseWithEagerReferences(sourceExercise);
        ProgrammingExercise exerciseToBeImported = ProgrammingExerciseFactory.generateToBeImportedProgrammingExercise("ImportTitle", "imported", sourceExercise,
                courseUtilService.addEmptyCourse());

        // Mock requests
        mockDelegate.mockImportProgrammingExerciseWithFailingEnablePlan(sourceExercise, exerciseToBeImported, true, true);
        setupRepositoryMocks(sourceExercise, sourceExerciseRepo, sourceSolutionRepo, sourceTestRepo, sourceAuxRepo);
        setupRepositoryMocks(exerciseToBeImported, exerciseRepo, solutionRepo, testRepo, auxRepo);
        setupMocksForConsistencyChecksOnImport(sourceExercise);

        // Create request
        var params = new LinkedMultiValueMap<String, String>();
        params.add("recreateBuildPlans", "false");
        params.add("updateTemplate", "true");
        request.postWithResponseBody(ROOT + IMPORT.replace("{sourceExerciseId}", sourceExercise.getId().toString()), exerciseToBeImported, ProgrammingExercise.class, params,
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // TEST
    void importExercise_planDoesntExist() throws Exception {
        // Setup exercises for import
        ProgrammingExercise sourceExercise = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndStaticCodeAnalysisCategories();
        // programmingExerciseUtilService.addTestCasesToProgrammingExercise(sourceExercise);
        sourceExercise = programmingExerciseUtilService.loadProgrammingExerciseWithEagerReferences(sourceExercise);
        ProgrammingExercise exerciseToBeImported = ProgrammingExerciseFactory.generateToBeImportedProgrammingExercise("ImportTitle", "imported", sourceExercise,
                courseUtilService.addEmptyCourse());

        // Mock requests
        mockDelegate.mockImportProgrammingExerciseWithFailingEnablePlan(sourceExercise, exerciseToBeImported, false, false);
        setupRepositoryMocks(sourceExercise, sourceExerciseRepo, sourceSolutionRepo, sourceTestRepo, sourceAuxRepo);
        setupRepositoryMocks(exerciseToBeImported, exerciseRepo, solutionRepo, testRepo, auxRepo);
        setupMocksForConsistencyChecksOnImport(sourceExercise);

        // Create request
        var params = new LinkedMultiValueMap<String, String>();
        params.add("recreateBuildPlans", "false");
        params.add("updateTemplate", "true");
        request.postWithResponseBody(ROOT + IMPORT.replace("{sourceExerciseId}", sourceExercise.getId().toString()), exerciseToBeImported, ProgrammingExercise.class, params,
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // TEST
    void testImportProgrammingExercise_team_modeChange() throws Exception {
        // Setup exercises for import
        ProgrammingExercise sourceExercise = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndStaticCodeAnalysisCategories();
        sourceExercise.setMode(ExerciseMode.INDIVIDUAL);
        programmingExerciseUtilService.addTestCasesToProgrammingExercise(sourceExercise);
        programmingExerciseUtilService.addHintsToExercise(sourceExercise);
        sourceExercise = programmingExerciseUtilService.loadProgrammingExerciseWithEagerReferences(sourceExercise);
        sourceExercise.setCourse(sourceExercise.getCourseViaExerciseGroupOrCourseMember());
        programmingExerciseRepository.save(sourceExercise);
        programmingExerciseUtilService.loadProgrammingExerciseWithEagerReferences(sourceExercise);

        ProgrammingExercise exerciseToBeImported = ProgrammingExerciseFactory.generateToBeImportedProgrammingExercise("ImportTitle", "imported", sourceExercise,
                courseUtilService.addEmptyCourse());
        exerciseToBeImported.setMode(TEAM);
        var teamAssignmentConfig = new TeamAssignmentConfig();
        teamAssignmentConfig.setExercise(exerciseToBeImported);
        teamAssignmentConfig.setMinTeamSize(1);
        teamAssignmentConfig.setMaxTeamSize(10);
        exerciseToBeImported.setTeamAssignmentConfig(teamAssignmentConfig);

        // Mock requests
        setupRepositoryMocks(sourceExercise, sourceExerciseRepo, sourceSolutionRepo, sourceTestRepo, sourceAuxRepo);
        setupRepositoryMocks(exerciseToBeImported, exerciseRepo, solutionRepo, testRepo, auxRepo);
        mockDelegate.mockConnectorRequestsForImport(sourceExercise, exerciseToBeImported, false, false);
        setupMocksForConsistencyChecksOnImport(sourceExercise);

        exerciseToBeImported = request.postWithResponseBody(ROOT + IMPORT.replace("{sourceExerciseId}", sourceExercise.getId().toString()), exerciseToBeImported,
                ProgrammingExercise.class, HttpStatus.OK);
        assertThat(exerciseToBeImported.getMode()).isEqualTo(TEAM);
        assertThat(exerciseToBeImported.getTeamAssignmentConfig().getMinTeamSize()).isEqualTo(teamAssignmentConfig.getMinTeamSize());
        assertThat(exerciseToBeImported.getTeamAssignmentConfig().getMaxTeamSize()).isEqualTo(teamAssignmentConfig.getMaxTeamSize());
        assertThat(teamRepository.findAllByExerciseIdWithEagerStudents(exerciseToBeImported, null)).isEmpty();

        sourceExercise = programmingExerciseUtilService.loadProgrammingExerciseWithEagerReferences(sourceExercise);
        assertThat(sourceExercise.getMode()).isEqualTo(ExerciseMode.INDIVIDUAL);
        assertThat(sourceExercise.getTeamAssignmentConfig()).isNull();
        assertThat(teamRepository.findAllByExerciseIdWithEagerStudents(sourceExercise, null)).isEmpty();
    }

    // TEST
    void testImportProgrammingExercise_individual_modeChange() throws Exception {
        // Setup exercises for import
        ProgrammingExercise sourceExercise = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndStaticCodeAnalysisCategories();
        sourceExercise.setMode(TEAM);
        programmingExerciseUtilService.addTestCasesToProgrammingExercise(sourceExercise);
        programmingExerciseUtilService.addHintsToExercise(sourceExercise);
        sourceExercise = programmingExerciseUtilService.loadProgrammingExerciseWithEagerReferences(sourceExercise);
        var teamAssignmentConfig = new TeamAssignmentConfig();
        teamAssignmentConfig.setExercise(sourceExercise);
        teamAssignmentConfig.setMinTeamSize(1);
        teamAssignmentConfig.setMaxTeamSize(10);
        sourceExercise.setTeamAssignmentConfig(teamAssignmentConfig);
        sourceExercise.setCourse(sourceExercise.getCourseViaExerciseGroupOrCourseMember());
        programmingExerciseRepository.save(sourceExercise);
        var team = new Team();
        team.setShortName("testImportProgrammingExercise_individual_modeChange");
        teamRepository.save(sourceExercise, team);
        programmingExerciseUtilService.loadProgrammingExerciseWithEagerReferences(sourceExercise);

        ProgrammingExercise exerciseToBeImported = ProgrammingExerciseFactory.generateToBeImportedProgrammingExercise("ImportTitle", "imported", sourceExercise,
                courseUtilService.addEmptyCourse());
        exerciseToBeImported.setMode(ExerciseMode.INDIVIDUAL);

        // Mock requests
        setupRepositoryMocks(sourceExercise, sourceExerciseRepo, sourceSolutionRepo, sourceTestRepo, sourceAuxRepo);
        setupRepositoryMocks(exerciseToBeImported, exerciseRepo, solutionRepo, testRepo, auxRepo);
        mockDelegate.mockConnectorRequestsForImport(sourceExercise, exerciseToBeImported, false, false);
        setupMocksForConsistencyChecksOnImport(sourceExercise);

        exerciseToBeImported = request.postWithResponseBody(ROOT + IMPORT.replace("{sourceExerciseId}", sourceExercise.getId().toString()), exerciseToBeImported,
                ProgrammingExercise.class, HttpStatus.OK);

        assertThat(exerciseToBeImported.getMode()).isEqualTo(ExerciseMode.INDIVIDUAL);
        assertThat(exerciseToBeImported.getTeamAssignmentConfig()).isNull();
        assertThat(teamRepository.findAllByExerciseIdWithEagerStudents(exerciseToBeImported, null)).isEmpty();

        sourceExercise = programmingExerciseUtilService.loadProgrammingExerciseWithEagerReferences(sourceExercise);
        assertThat(sourceExercise.getMode()).isEqualTo(TEAM);
        assertThat(teamRepository.findAllByExerciseIdWithEagerStudents(sourceExercise, null)).hasSize(1);
    }

    // TEST
    void testImportProgrammingExercise_scaChange() throws Exception {
        // Setup exercises for import
        ProgrammingExercise sourceExercise = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndStaticCodeAnalysisCategories();
        programmingExerciseUtilService.addTestCasesToProgrammingExercise(sourceExercise);
        sourceExercise = programmingExerciseUtilService.loadProgrammingExerciseWithEagerReferences(sourceExercise);
        ProgrammingExercise exerciseToBeImported = ProgrammingExerciseFactory.generateToBeImportedProgrammingExercise("ImportTitle", "imported", sourceExercise,
                courseUtilService.addEmptyCourse());

        // Mock requests
        mockDelegate.mockConnectorRequestsForImport(sourceExercise, exerciseToBeImported, true, false);
        setupRepositoryMocks(sourceExercise, sourceExerciseRepo, sourceSolutionRepo, sourceTestRepo, sourceAuxRepo);
        setupRepositoryMocks(exerciseToBeImported, exerciseRepo, solutionRepo, testRepo, auxRepo);
        setupMocksForConsistencyChecksOnImport(sourceExercise);

        // Create request
        var params = new LinkedMultiValueMap<String, String>();
        params.add("recreateBuildPlans", "true");
        params.add("updateTemplate", "true");
        exerciseToBeImported = request.postWithResponseBody(ROOT + IMPORT.replace("{sourceExerciseId}", sourceExercise.getId().toString()), exerciseToBeImported,
                ProgrammingExercise.class, params, HttpStatus.OK);

        // Assertions
        assertThat(exerciseToBeImported.isStaticCodeAnalysisEnabled()).isTrue();
        assertThat(exerciseToBeImported.getStaticCodeAnalysisCategories()).isEmpty();
        assertThat(exerciseToBeImported.getMaxStaticCodeAnalysisPenalty()).isNull();
    }

    void testImportProgrammingExercise_scaChange_activated() throws Exception {
        // Setup exercises for import
        ProgrammingExercise sourceExercise = (ProgrammingExercise) programmingExerciseUtilService.addCourseWithOneProgrammingExercise(false).getExercises().iterator().next();
        programmingExerciseUtilService.addTestCasesToProgrammingExercise(sourceExercise);
        sourceExercise = programmingExerciseUtilService.loadProgrammingExerciseWithEagerReferences(sourceExercise);
        ProgrammingExercise exerciseToBeImported = ProgrammingExerciseFactory.generateToBeImportedProgrammingExercise("ImportTitle", "imported", sourceExercise,
                courseUtilService.addEmptyCourse());
        exerciseToBeImported.setStaticCodeAnalysisEnabled(true);
        exerciseToBeImported.setMaxStaticCodeAnalysisPenalty(80);

        // Mock requests
        mockDelegate.mockConnectorRequestsForImport(sourceExercise, exerciseToBeImported, true, false);
        setupRepositoryMocks(sourceExercise, sourceExerciseRepo, sourceSolutionRepo, sourceTestRepo, sourceAuxRepo);
        setupRepositoryMocks(exerciseToBeImported, exerciseRepo, solutionRepo, testRepo, auxRepo);
        setupMocksForConsistencyChecksOnImport(sourceExercise);

        // Create request
        var params = new LinkedMultiValueMap<String, String>();
        params.add("recreateBuildPlans", "true");
        params.add("updateTemplate", "true");
        exerciseToBeImported = request.postWithResponseBody(ROOT + IMPORT.replace("{sourceExerciseId}", sourceExercise.getId().toString()), exerciseToBeImported,
                ProgrammingExercise.class, params, HttpStatus.OK);

        // Assertions
        var staticCodeAnalysisCategories = staticCodeAnalysisCategoryRepository.findWithExerciseByExerciseId(exerciseToBeImported.getId());
        assertThat(exerciseToBeImported.isStaticCodeAnalysisEnabled()).isTrue();
        assertThat(staticCodeAnalysisCategories).usingRecursiveFieldByFieldElementComparatorOnFields("name", "state", "penalty", "maxPenalty")
                .isEqualTo(StaticCodeAnalysisConfigurer.staticCodeAnalysisConfiguration().get(sourceExercise.getProgrammingLanguage()));
        assertThat(exerciseToBeImported.getMaxStaticCodeAnalysisPenalty()).isEqualTo(80);
    }

    void testImportProgrammingExerciseLockRepositorySubmissionPolicyChange() throws Exception {
        // Setup exercises for import
        ProgrammingExercise sourceExercise = (ProgrammingExercise) programmingExerciseUtilService.addCourseWithOneProgrammingExercise(false).getExercises().iterator().next();
        programmingExerciseUtilService.addTestCasesToProgrammingExercise(sourceExercise);
        sourceExercise = programmingExerciseUtilService.loadProgrammingExerciseWithEagerReferences(sourceExercise);
        ProgrammingExercise exerciseToBeImported = ProgrammingExerciseFactory.generateToBeImportedProgrammingExercise("ImportTitle", "imported", sourceExercise,
                courseUtilService.addEmptyCourse());

        var submissionPolicy = new LockRepositoryPolicy();
        submissionPolicy.setSubmissionLimit(5);
        exerciseToBeImported.setSubmissionPolicy(submissionPolicy);

        // Mock requests
        setupRepositoryMocks(sourceExercise, sourceExerciseRepo, sourceSolutionRepo, sourceTestRepo, sourceAuxRepo);
        setupRepositoryMocks(exerciseToBeImported, exerciseRepo, solutionRepo, testRepo, auxRepo);
        mockDelegate.mockConnectorRequestsForImport(sourceExercise, exerciseToBeImported, false, false);
        setupMocksForConsistencyChecksOnImport(sourceExercise);

        exerciseToBeImported = request.postWithResponseBody(ROOT + IMPORT.replace("{sourceExerciseId}", sourceExercise.getId().toString()), exerciseToBeImported,
                ProgrammingExercise.class, HttpStatus.OK);

        assertThat(exerciseToBeImported.getSubmissionPolicy().getClass()).isEqualTo(LockRepositoryPolicy.class);
        assertThat(exerciseToBeImported.getSubmissionPolicy().getSubmissionLimit()).isEqualTo(5);

        sourceExercise = programmingExerciseUtilService.loadProgrammingExerciseWithEagerReferences(sourceExercise);
        assertThat(sourceExercise.getSubmissionPolicy()).isNull();
    }

    void testImportProgrammingExerciseNoneSubmissionPolicyChange() throws Exception {
        // Setup exercises for import
        ProgrammingExercise sourceExercise = (ProgrammingExercise) programmingExerciseUtilService.addCourseWithOneProgrammingExercise(false).getExercises().iterator().next();
        programmingExerciseUtilService.addTestCasesToProgrammingExercise(sourceExercise);
        var submissionPolicy = new LockRepositoryPolicy();
        submissionPolicy.setSubmissionLimit(5);
        submissionPolicy.setProgrammingExercise(sourceExercise);
        submissionPolicy.setActive(true);
        programmingExerciseUtilService.addSubmissionPolicyToExercise(submissionPolicy, sourceExercise);
        sourceExercise = programmingExerciseUtilService.loadProgrammingExerciseWithEagerReferences(sourceExercise);

        ProgrammingExercise exerciseToBeImported = ProgrammingExerciseFactory.generateToBeImportedProgrammingExercise("ImportTitle", "imported", sourceExercise,
                courseUtilService.addEmptyCourse());
        exerciseToBeImported.setSubmissionPolicy(null);

        // Mock requests
        setupRepositoryMocks(sourceExercise, sourceExerciseRepo, sourceSolutionRepo, sourceTestRepo, sourceAuxRepo);
        setupRepositoryMocks(exerciseToBeImported, exerciseRepo, solutionRepo, testRepo, auxRepo);
        mockDelegate.mockConnectorRequestsForImport(sourceExercise, exerciseToBeImported, false, false);
        setupMocksForConsistencyChecksOnImport(sourceExercise);

        exerciseToBeImported = request.postWithResponseBody(ROOT + IMPORT.replace("{sourceExerciseId}", sourceExercise.getId().toString()), exerciseToBeImported,
                ProgrammingExercise.class, HttpStatus.OK);

        assertThat(exerciseToBeImported.getSubmissionPolicy()).isNull();

        sourceExercise = programmingExerciseUtilService.loadProgrammingExerciseWithEagerReferences(sourceExercise);
        assertThat(sourceExercise.getSubmissionPolicy()).isNotNull();
    }

    /**
     * Method to test the correct import of a programming exercise into an exam during an exam import
     * For more Information see {@link de.tum.in.www1.artemis.service.exam.ExamImportService}
     */
    public void importProgrammingExerciseAsPartOfExamImport() throws Exception {
        // Setup existing exam and exercise
        Exam sourceExam = examUtilService.addExamWithExerciseGroup(course, true);

        ProgrammingExercise sourceExercise = programmingExerciseUtilService.addProgrammingExerciseToExam(sourceExam, 0);
        sourceExercise.setStaticCodeAnalysisEnabled(false);
        programmingExerciseUtilService.addTestCasesToProgrammingExercise(sourceExercise);
        programmingExerciseUtilService.addHintsToExercise(sourceExercise);
        sourceExercise = programmingExerciseUtilService.loadProgrammingExerciseWithEagerReferences(sourceExercise);

        // Setup to be imported exam and exercise
        Exam targetExam = ExamFactory.generateExam(course);
        ExerciseGroup targetExerciseGroup = ExamFactory.generateExerciseGroup(true, targetExam);
        ProgrammingExercise exerciseToBeImported = ProgrammingExerciseFactory.generateToBeImportedProgrammingExercise("ImportTitle", "imported", sourceExercise, course);
        // Exam Exercise has no course
        exerciseToBeImported.setCourse(null);
        exerciseToBeImported.setExerciseGroup(targetExerciseGroup);
        targetExerciseGroup.addExercise(exerciseToBeImported);
        exerciseToBeImported.forceNewProjectKey();
        // The Id of the sourceExercise is used to retrieve it from the database
        exerciseToBeImported.setId(sourceExercise.getId());

        // Mock requests
        setupRepositoryMocks(sourceExercise, sourceExerciseRepo, sourceSolutionRepo, sourceTestRepo, sourceAuxRepo);
        setupRepositoryMocks(exerciseToBeImported, exerciseRepo, solutionRepo, testRepo, auxRepo);
        mockDelegate.mockConnectorRequestsForImport(sourceExercise, exerciseToBeImported, false, false);
        doReturn(false).when(versionControlService).checkIfProjectExists(any(), any());
        // Import the exam
        targetExam.setChannelName("testchannel-imported");
        final Exam received = request.postWithResponseBody("/api/courses/" + course.getId() + "/exam-import", targetExam, Exam.class, HttpStatus.CREATED);

        // Extract the programming exercise from the exam
        Exercise exerciseReceived = received.getExerciseGroups().get(0).getExercises().stream().findFirst().orElseThrow();
        // Additionally, get the programming exercise from the server
        var importedExercise = programmingExerciseUtilService.loadProgrammingExerciseWithEagerReferences((ProgrammingExercise) exerciseReceived);

        assertThat(importedExercise.getId()).isNotNull();
        assertThat(importedExercise.getTitle()).isEqualTo(exerciseReceived.getTitle());
        // Check server-exercise
        assertThat(importedExercise.getTitle()).isEqualTo(exerciseToBeImported.getTitle());
        assertThat(importedExercise.getShortName()).isEqualTo(exerciseToBeImported.getShortName());
        assertThat(importedExercise.getExerciseGroup()).isNotEqualTo(targetExam.getExerciseGroups().get(0));
        // Check exercise send to client after importing
        assertThat(exerciseReceived.getTitle()).isEqualTo(exerciseToBeImported.getTitle());
        assertThat(exerciseReceived.getShortName()).isEqualTo(exerciseToBeImported.getShortName());
        assertThat(exerciseReceived.getExerciseGroup()).isNotEqualTo(targetExam.getExerciseGroups().get(0));

        // Assert correct creation of test cases
        var importedTestCaseIds = importedExercise.getTestCases().stream().map(ProgrammingExerciseTestCase::getId).toList();
        var sourceTestCaseIds = sourceExercise.getTestCases().stream().map(ProgrammingExerciseTestCase::getId).toList();
        assertThat(importedTestCaseIds).doesNotContainAnyElementsOf(sourceTestCaseIds);
        assertThat(importedExercise.getTestCases()).usingRecursiveFieldByFieldElementComparator()
                .usingRecursiveFieldByFieldElementComparatorIgnoringFields("id", "exercise", "tasks", "solutionEntries", "coverageEntries")
                .containsExactlyInAnyOrderElementsOf(sourceExercise.getTestCases());

        // Assert correct creation of hints
        var importedHintIds = importedExercise.getExerciseHints().stream().map(ExerciseHint::getId).toList();
        var sourceHintIds = sourceExercise.getExerciseHints().stream().map(ExerciseHint::getId).toList();
        assertThat(importedHintIds).doesNotContainAnyElementsOf(sourceHintIds);
        assertThat(importedExercise.getExerciseHints()).usingRecursiveFieldByFieldElementComparatorIgnoringFields("id", "exercise", "exerciseHintActivations")
                .containsExactlyInAnyOrderElementsOf(sourceExercise.getExerciseHints());
    }

    // TEST
    void createProgrammingExercise_validExercise_structureOracle() throws Exception {
        mockDelegate.mockConnectorRequestsForSetup(exercise, false, false, false);
        exercise.setChannelName("testchannel-pe");

        final var generatedExercise = request.postWithResponseBody(ROOT + SETUP, exercise, ProgrammingExercise.class, HttpStatus.CREATED);
        String response = request.putWithResponseBody(ROOT + GENERATE_TESTS.replace("{exerciseId}", String.valueOf(generatedExercise.getId())), generatedExercise, String.class,
                HttpStatus.OK);
        assertThat(response).startsWith("Successfully generated the structure oracle");

        List<RevCommit> testRepoCommits = testRepo.getAllLocalCommits();
        assertThat(testRepoCommits).hasSize(2);

        assertThat(testRepoCommits.get(0).getFullMessage()).isEqualTo("Update the structure oracle file.");
        List<DiffEntry> changes = getChanges(testRepo.localGit.getRepository(), testRepoCommits.get(0));
        assertThat(changes).hasSize(1);
        assertThat(changes.get(0).getChangeType()).isEqualTo(DiffEntry.ChangeType.MODIFY);
        assertThat(changes.get(0).getOldPath()).endsWith("test.json");

        // Second time leads to a bad request because the file did not change
        var expectedHeaders = new HashMap<String, String>();
        expectedHeaders.put("X-artemisApp-alert", "Did not update the oracle because there have not been any changes to it.");
        request.putWithResponseBody(ROOT + GENERATE_TESTS.replace("{exerciseId}", String.valueOf(generatedExercise.getId())), generatedExercise, String.class,
                HttpStatus.BAD_REQUEST, expectedHeaders);
        assertThat(response).startsWith("Successfully generated the structure oracle");
    }

    // TEST
    void createProgrammingExercise_noTutors_created() throws Exception {
        course.setTeachingAssistantGroupName(null);
        courseRepository.save(course);
        mockDelegate.mockConnectorRequestsForSetup(exercise, false, false, false);
        exercise.setChannelName("testchannel-pe");
        final var generatedExercise = request.postWithResponseBody(ROOT + SETUP, exercise, ProgrammingExercise.class, HttpStatus.CREATED);
        validateProgrammingExercise(generatedExercise);
    }

    // TEST
    void startProgrammingExercise_correctInitializationState(ExerciseMode exerciseMode) throws Exception {
        setupCourseWithProgrammingExercise(exerciseMode);
        var user = userRepo.findOneByLogin(userPrefix + studentLogin).orElseThrow();
        Participant participant = user;
        if (exerciseMode == TEAM) {
            participant = setupTeam(user);
        }
        mockDelegate.mockConnectorRequestsForStartParticipation(exercise, participant.getParticipantIdentifier(), participant.getParticipants(), true);
        final var path = "/api/exercises/{exerciseId}/participations".replace("{exerciseId}", String.valueOf(exercise.getId()));
        final var participation = request.postWithResponseBody(path, null, ProgrammingExerciseStudentParticipation.class, HttpStatus.CREATED);
        assertThat(participation.getInitializationState()).as("Participation should be initialized").isEqualTo(InitializationState.INITIALIZED);
    }

    // TEST
    void startProgrammingExercise_correctInitializationState() throws Exception {
        var user = userUtilService.createAndSaveUser("edx_student1");
        user.setInternal(true);
        user = userRepo.save(user);

        final Course course = setupCourseWithProgrammingExercise(ExerciseMode.INDIVIDUAL);
        user.setGroups(Set.of(course.getStudentGroupName()));
        user = userRepo.save(user);
        Participant participant = user;

        mockDelegate.mockConnectorRequestsForStartParticipation(exercise, participant.getParticipantIdentifier(), participant.getParticipants(), true);

        final var path = "/api/exercises/{exerciseId}/participations".replace("{exerciseId}", String.valueOf(exercise.getId()));
        final var participation = request.postWithResponseBody(path, null, ProgrammingExerciseStudentParticipation.class, HttpStatus.CREATED);
        assertThat(participation.getInitializationState()).as("Participation should be initialized").isEqualTo(InitializationState.INITIALIZED);
    }

    void startProgrammingExercise(Boolean offlineIde) throws Exception {
        exercise.setAllowOnlineEditor(true);
        exercise.setAllowOfflineIde(offlineIde);
        exercise = programmingExerciseRepository.save(exercise);

        startProgrammingExercise_correctInitializationState(INDIVIDUAL);

        final VersionControlRepositoryPermission permissions;
        if (offlineIde == null || Boolean.TRUE.equals(offlineIde)) {
            permissions = VersionControlRepositoryPermission.REPO_WRITE;
        }
        else {
            permissions = VersionControlRepositoryPermission.REPO_READ;
        }

        final User participant = userRepo.getUserByLoginElseThrow(userPrefix + studentLogin);

        verify(versionControlService).addMemberToRepository(any(), eq(participant), eq(permissions));
    }

    private Course setupCourseWithProgrammingExercise(ExerciseMode exerciseMode) {
        final var course = exercise.getCourseViaExerciseGroupOrCourseMember();
        exercise.setMode(exerciseMode);
        programmingExerciseRepository.save(exercise);
        programmingExerciseUtilService.addTemplateParticipationForProgrammingExercise(exercise);
        programmingExerciseUtilService.addSolutionParticipationForProgrammingExercise(exercise);
        return course;
    }

    // TEST
    void resumeProgrammingExercise_doesNotExist(ExerciseMode exerciseMode) throws Exception {
        setupCourseWithProgrammingExercise(exerciseMode);
        request.putWithResponseBody("/api/exercises/" + exercise.getId() + "/resume-programming-participation/" + -1, null, ProgrammingExerciseStudentParticipation.class,
                HttpStatus.NOT_FOUND);
    }

    // TEST
    void resumeProgrammingExercise_correctInitializationState(ExerciseMode exerciseMode) throws Exception {
        var participation = createStudentParticipationWithSubmission(exerciseMode);

        // These will be updated when the participation is resumed.
        participation.setInitializationState(InitializationState.INACTIVE);
        participation.setBuildPlanId(null);
        programmingExerciseStudentParticipationRepository.saveAndFlush(participation);

        var participant = participation.getParticipant();
        mockDelegate.mockConnectorRequestsForResumeParticipation(exercise, participant.getParticipantIdentifier(), participant.getParticipants(), true);

        participation = request.putWithResponseBody("/api/exercises/" + exercise.getId() + "/resume-programming-participation/" + participation.getId(), null,
                ProgrammingExerciseStudentParticipation.class, HttpStatus.OK);

        assertThat(participation.getInitializationState()).as("Participation should be initialized").isEqualTo(InitializationState.INITIALIZED);
        assertThat(participation.getBuildPlanId()).as("Build Plan Id should be set")
                .isEqualTo(exercise.getProjectKey().toUpperCase() + "-" + participant.getParticipantIdentifier().toUpperCase());
    }

    // TEST
    void resumeProgrammingExerciseByPushingIntoRepo_correctInitializationState(ExerciseMode exerciseMode, Object body) throws Exception {
        var participation = createStudentParticipationWithSubmission(exerciseMode);
        var participant = participation.getParticipant();

        mockDelegate.mockConnectorRequestsForResumeParticipation(exercise, participant.getParticipantIdentifier(), participant.getParticipants(), true);
        mockDelegate.mockNotifyPush(participation);

        // These will be updated when pushing a commit
        participation.setInitializationState(InitializationState.INACTIVE);
        participation.setBuildPlanId(null);
        programmingExerciseStudentParticipationRepository.saveAndFlush(participation);

        // Mock REST Call from the VCS for a new programming submission (happens as part of the webhook after pushing code to git)
        request.postWithoutLocation("/api/public/programming-submissions/" + participation.getId(), body, HttpStatus.OK, new HttpHeaders());

        // Fetch updated participation and assert
        ProgrammingExerciseStudentParticipation updatedParticipation = (ProgrammingExerciseStudentParticipation) participationRepository.findByIdElseThrow(participation.getId());
        assertThat(updatedParticipation.getInitializationState()).as("Participation should be initialized").isEqualTo(InitializationState.INITIALIZED);
        assertThat(updatedParticipation.getBuildPlanId()).as("Build Plan Id should be set")
                .isEqualTo(exercise.getProjectKey().toUpperCase() + "-" + participant.getParticipantIdentifier().toUpperCase());
    }

    // TEST
    void resumeProgrammingExerciseByTriggeringBuild_correctInitializationState(ExerciseMode exerciseMode, SubmissionType submissionType) throws Exception {
        var participation = createStudentParticipationWithSubmission(exerciseMode);
        var participant = participation.getParticipant();

        mockDelegate.mockTriggerParticipationBuild(participation);
        // We need to mock the call again because we are triggering the build twice in order to verify that the submission isn't re-created
        mockDelegate.mockTriggerParticipationBuild(participation);

        mockDelegate.mockDefaultBranch(participation.getProgrammingExercise());

        // These will be updated when triggering a build
        participation.setInitializationState(InitializationState.INACTIVE);
        participation.setBuildPlanId(null);
        programmingExerciseStudentParticipationRepository.saveAndFlush(participation);

        // Construct trigger-build url and execute request
        submissionType = submissionType == null ? SubmissionType.MANUAL : submissionType;
        String url = "/api/programming-submissions/" + participation.getId() + "/trigger-build?submissionType=" + submissionType.name();
        request.postWithoutLocation(url, null, HttpStatus.OK, new HttpHeaders());

        // Fetch updated participation and assert
        ProgrammingExerciseStudentParticipation updatedParticipation = (ProgrammingExerciseStudentParticipation) participationRepository.findByIdElseThrow(participation.getId());
        assertThat(updatedParticipation.getInitializationState()).as("Participation should be initialized").isEqualTo(InitializationState.INITIALIZED);
        assertThat(updatedParticipation.getBuildPlanId()).as("Build Plan Id should be set")
                .isEqualTo(exercise.getProjectKey().toUpperCase() + "-" + participant.getParticipantIdentifier().toUpperCase());

        // Trigger the build again and make sure no new submission is created
        request.postWithoutLocation(url, null, HttpStatus.OK, new HttpHeaders());
        var submissions = submissionRepository.findAllByParticipationId(participation.getId());
        assertThat(submissions).hasSize(1);
    }

    // TEST
    void resumeProgrammingExerciseByTriggeringFailedBuild_correctInitializationState(ExerciseMode exerciseMode, boolean buildPlanExists) throws Exception {
        var participation = createStudentParticipationWithSubmission(exerciseMode);
        var participant = participation.getParticipant();

        mockDelegate.mockTriggerFailedBuild(participation);
        // We need to mock the call again because we are triggering the build twice in order to verify that the submission isn't re-created
        mockDelegate.mockTriggerFailedBuild(participation);
        mockDelegate.mockDefaultBranch(participation.getProgrammingExercise());

        // These will be updated triggering a failed build
        participation.setInitializationState(InitializationState.INACTIVE);
        participation.setBuildPlanId(!buildPlanExists ? null : participation.getBuildPlanId());
        programmingExerciseStudentParticipationRepository.saveAndFlush(participation);

        if (!buildPlanExists) {
            mockDelegate.mockConnectorRequestsForResumeParticipation(exercise, participant.getParticipantIdentifier(), participant.getParticipants(), true);
            participation = request.putWithResponseBody("/api/exercises/" + exercise.getId() + "/resume-programming-participation/" + participation.getId(), null,
                    ProgrammingExerciseStudentParticipation.class, HttpStatus.OK);
        }

        // Construct trigger-build url and execute request
        String url = "/api/programming-submissions/" + participation.getId() + "/trigger-failed-build";
        request.postWithoutLocation(url, null, HttpStatus.OK, new HttpHeaders());

        // Fetch updated participation and assert
        ProgrammingExerciseStudentParticipation updatedParticipation = (ProgrammingExerciseStudentParticipation) participationRepository.findByIdElseThrow(participation.getId());
        assertThat(updatedParticipation.getInitializationState()).as("Participation should be initialized").isEqualTo(InitializationState.INITIALIZED);
        assertThat(updatedParticipation.getBuildPlanId()).as("Build Plan Id should be set")
                .isEqualTo(exercise.getProjectKey().toUpperCase() + "-" + participant.getParticipantIdentifier().toUpperCase());

        // Trigger the build again and make sure no new submission is created
        request.postWithoutLocation(url, null, HttpStatus.OK, new HttpHeaders());
        var submissions = submissionRepository.findAllByParticipationId(participation.getId());
        assertThat(submissions).hasSize(1);
    }

    // TEST
    void resumeProgrammingExerciseByTriggeringInstructorBuild_correctInitializationState(ExerciseMode exerciseMode) throws Exception {
        var participation = createStudentParticipationWithSubmission(exerciseMode);
        var participant = participation.getParticipant();

        mockDelegate.mockTriggerInstructorBuildAll(participation);
        // We need to mock the call again because we are triggering the build twice in order to verify that the submission isn't re-created
        mockDelegate.mockTriggerInstructorBuildAll(participation);

        mockDelegate.mockDefaultBranch(participation.getProgrammingExercise());

        // These will be updated triggering a failed build
        participation.setInitializationState(InitializationState.INACTIVE);
        participation.setBuildPlanId(null);
        programmingExerciseStudentParticipationRepository.saveAndFlush(participation);

        var url = "/api/programming-exercises/" + exercise.getId() + "/trigger-instructor-build-all";
        request.postWithoutLocation(url, null, HttpStatus.OK, new HttpHeaders());
        await().timeout(20, TimeUnit.SECONDS)
                .until(() -> programmingExerciseRepository.findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(exercise.getId()).isPresent()
                        && participationRepository.findByIdElseThrow(participation.getId()).getInitializationState().hasCompletedState(InitializationState.INITIALIZED));

        // Fetch updated participation and assert
        ProgrammingExerciseStudentParticipation updatedParticipation = (ProgrammingExerciseStudentParticipation) participationRepository.findByIdElseThrow(participation.getId());
        assertThat(updatedParticipation.getInitializationState()).as("Participation should be initialized").isEqualTo(InitializationState.INITIALIZED);
        assertThat(updatedParticipation.getBuildPlanId()).as("Build Plan Id should be set")
                .isEqualTo(exercise.getProjectKey().toUpperCase() + "-" + participant.getParticipantIdentifier().toUpperCase());

        // Trigger the build again and make sure no new submission is created
        request.postWithoutLocation(url, null, HttpStatus.OK, new HttpHeaders());
        var submissions = submissionRepository.findAllByParticipationId(participation.getId());
        assertThat(submissions).hasSize(1);
    }

    // Test
    void exportInstructorRepositories_shouldReturnFile() throws Exception {
        String zip = exportInstructorRepository(RepositoryType.TEMPLATE, exerciseRepo, HttpStatus.OK);
        assertThat(zip).isNotNull();

        zip = exportInstructorRepository(RepositoryType.SOLUTION, solutionRepo, HttpStatus.OK);
        assertThat(zip).isNotNull();

        zip = exportInstructorRepository(RepositoryType.TESTS, testRepo, HttpStatus.OK);
        assertThat(zip).isNotNull();
    }

    void exportInstructorAuxiliaryRepository_shouldReturnFile() throws Exception {
        generateProgrammingExerciseForExport();
        var auxRepo = addAuxiliaryRepositoryToProgrammingExercise(exercise);
        setupAuxRepoMock(auxRepo);
        setupRepositoryMocks(exercise);
        var url = "/api/programming-exercises/" + exercise.getId() + "/export-instructor-auxiliary-repository/" + auxRepo.getId();
        request.get(url, HttpStatus.OK, String.class);
    }

    private void setupAuxRepoMock(AuxiliaryRepository auxiliaryRepository) throws GitAPIException {
        Repository repository = gitService.getExistingCheckedOutRepositoryByLocalPath(auxRepo.localRepoFile.toPath(), null);

        doReturn(repository).when(gitService).getOrCheckoutRepository(eq(auxiliaryRepository.getVcsRepositoryUri()), anyString(), anyBoolean());
        doReturn(repository).when(gitService).getOrCheckoutRepository(eq(auxiliaryRepository.getVcsRepositoryUri()), (Path) any(), anyBoolean());
    }

    void exportInstructorAuxiliaryRepository_forbidden() throws Exception {
        generateProgrammingExerciseForExport();
        var auxRepo = addAuxiliaryRepositoryToProgrammingExercise(exercise);
        var url = "/api/programming-exercises/" + exercise.getId() + "/export-instructor-auxiliary-repository/" + auxRepo.getId();
        request.get(url, HttpStatus.FORBIDDEN, String.class);
    }

    // Test
    void exportInstructorRepositories_forbidden() throws Exception {
        // change the group name to enforce a HttpStatus forbidden after having accessed the endpoint
        course.setInstructorGroupName("test");
        courseRepository.save(course);
        exportInstructorRepository(RepositoryType.TEMPLATE, exerciseRepo, HttpStatus.FORBIDDEN);
        exportInstructorRepository(RepositoryType.SOLUTION, solutionRepo, HttpStatus.FORBIDDEN);
        exportInstructorRepository(RepositoryType.TESTS, testRepo, HttpStatus.FORBIDDEN);
    }

    private String exportInstructorRepository(RepositoryType repositoryType, LocalRepository localRepository, HttpStatus expectedStatus) throws Exception {
        generateProgrammingExerciseForExport();

        setupMockRepo(localRepository, repositoryType, "some-file.java");

        var url = "/api/programming-exercises/" + exercise.getId() + "/export-instructor-repository/" + repositoryType.name();
        return request.get(url, expectedStatus, String.class);
    }

    private String exportStudentRequestedRepository(HttpStatus expectedStatus, boolean includeTests) throws Exception {
        generateProgrammingExerciseForExport();

        setupMockRepo(exerciseRepo, RepositoryType.SOLUTION, "some-file.java");
        if (includeTests) {
            setupMockRepo(testRepo, RepositoryType.TESTS, "some-test-file.java");
        }

        var url = "/api/programming-exercises/" + exercise.getId() + "/export-student-requested-repository?includeTests=" + includeTests;
        return request.get(url, expectedStatus, String.class);
    }

    // Test

    /**
     * Test that the export of the instructor material works as expected when no build plan exists (e.g. for jenkins setups at the moment)
     * <p>
     *
     * @param saveEmbeddedFiles whether embedded files should be saved or not, not saving them simulates that embedded files are no longer stored on the file system
     * @throws Exception if the export fails
     */
    void exportProgrammingExerciseInstructorMaterial_shouldReturnFileWithoutBuildplan(boolean saveEmbeddedFiles) throws Exception {
        exportProgrammingExerciseInstructorMaterial_shouldReturnFile(saveEmbeddedFiles, false);
    }

    /**
     * Test that the export of the instructor material works as expected with a build plan included (relevant for Gitlab/Jenkins setups).
     *
     * @throws Exception if the export fails
     */
    void exportProgrammingExerciseInstructorMaterial_shouldReturnFileWithBuildplan() throws Exception {
        exportProgrammingExerciseInstructorMaterial_shouldReturnFile(false, true);
    }

    /**
     * Test that the export of the instructor material works as expected.
     *
     * @param saveEmbeddedFiles whether embedded files should be saved or not, not saving them simulates that embedded files are no longer stored on the file system
     * @throws Exception if the export fails
     */
    void exportProgrammingExerciseInstructorMaterial_shouldReturnFile(boolean saveEmbeddedFiles, boolean shouldIncludeBuildplan) throws Exception {
        var zipFile = exportProgrammingExerciseInstructorMaterial(HttpStatus.OK, false, true, saveEmbeddedFiles, shouldIncludeBuildplan);
        // Assure, that the zip folder is already created and not 'in creation' which would lead to a failure when extracting it in the next step
        await().until(zipFile::exists);
        assertThat(zipFile).isNotNull();
        String embeddedFileName1 = "Markdown_2023-05-06T16-17-46-410_ad323711.jpg";
        String embeddedFileName2 = "Markdown_2023-05-06T16-17-46-822_b921f475.jpg";
        // delete the files to not only make a test pass because a previous test run succeeded
        Path embeddedFilePath1 = FilePathService.getMarkdownFilePath().resolve(embeddedFileName1);
        Path embeddedFilePath2 = FilePathService.getMarkdownFilePath().resolve(embeddedFileName2);
        if (Files.exists(embeddedFilePath1)) {
            Files.delete(embeddedFilePath1);
        }
        if (Files.exists(embeddedFilePath2)) {
            Files.delete(embeddedFilePath2);
        }
        // Recursively unzip the exported file, to make sure there is no erroneous content
        Path extractedZipDir = zipFileTestUtilService.extractZipFileRecursively(zipFile.getAbsolutePath());

        // Check that the contents we created exist in the unzipped exported folder
        try (var files = Files.walk(extractedZipDir)) {
            List<Path> listOfIncludedFiles = files.filter(Files::isRegularFile).map(Path::getFileName).toList();
            assertThat(listOfIncludedFiles).anyMatch((filename) -> filename.toString().matches(".*-exercise.zip"))
                    .anyMatch((filename) -> filename.toString().matches(".*-solution.zip")).anyMatch((filename) -> filename.toString().matches(".*-tests.zip"))
                    .anyMatch((filename) -> filename.toString().matches(EXPORTED_EXERCISE_PROBLEM_STATEMENT_FILE_PREFIX + ".*.md"))
                    .anyMatch((filename) -> filename.toString().matches(EXPORTED_EXERCISE_DETAILS_FILE_PREFIX + ".*.json"));
            if (saveEmbeddedFiles) {
                assertThat(listOfIncludedFiles).anyMatch((filename) -> filename.toString().equals(embeddedFileName1))
                        .anyMatch((filename) -> filename.toString().equals(embeddedFileName2));
            }
            if (shouldIncludeBuildplan) {
                assertThat(listOfIncludedFiles).anyMatch((filename) -> BUILD_PLAN_FILE_NAME.equals(filename.toString()));
                Path buildPlanPath = listOfIncludedFiles.stream().filter(file -> BUILD_PLAN_FILE_NAME.equals(file.getFileName().toString())).findFirst().orElseThrow();
                String buildPlanContent = Files.readString(extractedZipDir.resolve(buildPlanPath), StandardCharsets.UTF_8);
                assertThat(buildPlanContent).isEqualTo("my build plan");
            }
            else {
                assertThat(listOfIncludedFiles).noneMatch((filename) -> BUILD_PLAN_FILE_NAME.equals(filename.toString()));
            }
        }

        FileUtils.deleteDirectory(extractedZipDir.toFile());
        FileUtils.delete(zipFile);
    }

    void exportProgrammingExerciseInstructorMaterial_problemStatementNull_success() throws Exception {
        var zipFile = exportProgrammingExerciseInstructorMaterial(HttpStatus.OK, true, true, false, false);
        await().until(zipFile::exists);
        assertThat(zipFile).isNotNull();
        Path extractedZipDir = zipFileTestUtilService.extractZipFileRecursively(zipFile.getAbsolutePath());

        // Check that the contents we created exist in the unzipped exported folder
        try (var files = Files.walk(extractedZipDir)) {
            List<Path> listOfIncludedFiles = files.filter(Files::isRegularFile).map(Path::getFileName).toList();
            assertThat(listOfIncludedFiles).anyMatch((filename) -> filename.toString().matches(".*-exercise.zip"))
                    .anyMatch((filename) -> filename.toString().matches(".*-solution.zip")).anyMatch((filename) -> filename.toString().matches(".*-tests.zip"))
                    .anyMatch((filename) -> filename.toString().matches(EXPORTED_EXERCISE_DETAILS_FILE_PREFIX + ".*.json"));

        }

        FileUtils.deleteDirectory(extractedZipDir.toFile());
        FileUtils.delete(zipFile);
    }

    // Test
    void exportProgrammingExerciseInstructorMaterial_problemStatementShouldContainTestNames() throws Exception {
        programmingExerciseRepository.save(exercise);
        var tests = programmingExerciseUtilService.addTestCasesToProgrammingExercise(exercise);
        var test = tests.get(0);
        exercise.setProblemStatement("[task][name](<testid>%s</testid>)".formatted(test.getId()));
        programmingExerciseRepository.save(exercise);

        var zipFile = exportProgrammingExerciseInstructorMaterial(HttpStatus.OK, true);
        // Assure, that the zip folder is already created and not 'in creation' which would lead to a failure when extracting it in the next step
        assertThat(zipFile).isNotNull();
        await().until(zipFile::exists);
        // Recursively unzip the exported file, to make sure there is no erroneous content
        zipFileTestUtilService.extractZipFileRecursively(zipFile.getAbsolutePath());
        String extractedZipDir = zipFile.getPath().substring(0, zipFile.getPath().length() - 4);

        String problemStatement;
        try (var files = Files.walk(Path.of(extractedZipDir))) {
            var problemStatementFile = files.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().matches(EXPORTED_EXERCISE_PROBLEM_STATEMENT_FILE_PREFIX + ".*\\.md")).findFirst().orElseThrow();
            problemStatement = Files.readString(problemStatementFile, StandardCharsets.UTF_8);
        }

        assertThat(problemStatement).isEqualTo("[task][name](%s)".formatted(test.getTestName()));
    }

    // Test
    void exportProgrammingExerciseInstructorMaterial_forbidden() throws Exception {
        // change the group name to enforce a HttpStatus forbidden after having accessed the endpoint
        course.setInstructorGroupName("test");
        courseRepository.save(course);
        exportProgrammingExerciseInstructorMaterial(HttpStatus.FORBIDDEN, false, false, false, false);
    }

    /**
     * export programming exercise instructor material
     *
     * @param expectedStatus         the expected http status, e.g. 200 OK
     * @param problemStatementNull   whether the problem statement should be null or not
     * @param mockRepos              whether the repos should be mocked or not, if we mock the files API we cannot mock them but also cannot use them
     * @param saveEmbeddedFiles      whether embedded files should be saved or not, not saving them simulates that embedded files are no longer stored on the file system
     * @param shouldIncludeBuildplan whether the build plan should be included in the export or not
     * @return the zip file
     * @throws Exception if the export fails
     */
    File exportProgrammingExerciseInstructorMaterial(HttpStatus expectedStatus, boolean problemStatementNull, boolean mockRepos, boolean saveEmbeddedFiles,
            boolean shouldIncludeBuildplan) throws Exception {
        if (problemStatementNull) {
            generateProgrammingExerciseWithProblemStatementNullForExport();
        }
        else {
            generateProgrammingExerciseForExport(saveEmbeddedFiles, shouldIncludeBuildplan);
        }
        return exportProgrammingExerciseInstructorMaterial(expectedStatus, mockRepos);
    }

    private File exportProgrammingExerciseInstructorMaterial(HttpStatus expectedStatus, boolean mockRepos) throws Exception {
        if (mockRepos) {
            // Mock template repo
            Repository templateRepository = gitService.getExistingCheckedOutRepositoryByLocalPath(exerciseRepo.localRepoFile.toPath(), null);
            createAndCommitDummyFileInLocalRepository(exerciseRepo, "Template.java");
            doReturn(templateRepository).when(gitService).getOrCheckoutRepository(eq(exercise.getRepositoryURL(RepositoryType.TEMPLATE)), any(Path.class), anyBoolean());
            doReturn(COMMIT_HASH_OBJECT_ID).when(gitService).getLastCommitHash(any());

            // Mock solution repo
            Repository solutionRepository = gitService.getExistingCheckedOutRepositoryByLocalPath(solutionRepo.localRepoFile.toPath(), null);
            createAndCommitDummyFileInLocalRepository(solutionRepo, "Solution.java");
            doReturn(solutionRepository).when(gitService).getOrCheckoutRepository(eq(exercise.getRepositoryURL(RepositoryType.SOLUTION)), any(Path.class), anyBoolean());

            // Mock tests repo
            Repository testsRepository = gitService.getExistingCheckedOutRepositoryByLocalPath(testRepo.localRepoFile.toPath(), null);
            createAndCommitDummyFileInLocalRepository(testRepo, "Tests.java");
            doReturn(testsRepository).when(gitService).getOrCheckoutRepository(eq(exercise.getRepositoryURL(RepositoryType.TESTS)), any(Path.class), anyBoolean());
        }
        var url = "/api/programming-exercises/" + exercise.getId() + "/export-instructor-exercise";
        return request.getFile(url, expectedStatus, new LinkedMultiValueMap<>());
    }

    private void generateProgrammingExerciseWithProblemStatementNullForExport() {
        exercise.setProblemStatement(null);
        exercise = programmingExerciseRepository.save(exercise);
        exercise = programmingExerciseUtilService.addTemplateParticipationForProgrammingExercise(exercise);
        exercise = programmingExerciseUtilService.addSolutionParticipationForProgrammingExercise(exercise);
        exercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationById(exercise.getId()).orElseThrow();
    }

    private void generateProgrammingExerciseForExport() throws IOException {
        generateProgrammingExerciseForExport(false, false);
    }

    private void generateProgrammingExerciseForExport(boolean saveEmbeddedFiles, boolean shouldIncludeBuildPlan) throws IOException {
        String embeddedFileName1 = "Markdown_2023-05-06T16-17-46-410_ad323711.jpg";
        String embeddedFileName2 = "Markdown_2023-05-06T16-17-46-822_b921f475.jpg";
        exercise.setProblemStatement(String.format("""
                Problem statement
                ![mountain.jpg](/api/files/markdown/%s)
                <img src="/api/files/markdown/%s" width="400">
                """, embeddedFileName1, embeddedFileName2));
        if (saveEmbeddedFiles) {
            FileUtils.copyToFile(new ClassPathResource("test-data/repository-export/" + embeddedFileName1).getInputStream(),
                    FilePathService.getMarkdownFilePath().resolve(embeddedFileName1).toFile());
            FileUtils.copyToFile(new ClassPathResource("test-data/repository-export/" + embeddedFileName2).getInputStream(),
                    FilePathService.getMarkdownFilePath().resolve(embeddedFileName2).toFile());
        }
        exercise = programmingExerciseRepository.save(exercise);
        if (shouldIncludeBuildPlan) {
            buildPlanRepository.setBuildPlanForExercise("my build plan", exercise);
        }
        exercise = programmingExerciseUtilService.addTemplateParticipationForProgrammingExercise(exercise);
        exercise = programmingExerciseUtilService.addSolutionParticipationForProgrammingExercise(exercise);
        exercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationById(exercise.getId()).orElseThrow();

    }

    private void setupMockRepo(LocalRepository localRepo, RepositoryType repoType, String fileName) throws GitAPIException, IOException {
        VcsRepositoryUri vcsUrl = exercise.getRepositoryURL(repoType);
        Repository repository = gitService.getExistingCheckedOutRepositoryByLocalPath(localRepo.localRepoFile.toPath(), null);

        createAndCommitDummyFileInLocalRepository(localRepo, fileName);
        doReturn(repository).when(gitService).getOrCheckoutRepository(eq(vcsUrl), anyString(), anyBoolean());
        doReturn(repository).when(gitService).getOrCheckoutRepository(eq(vcsUrl), (Path) any(), anyBoolean());
    }

    // Test
    void testArchiveCourseWithProgrammingExercise() throws Exception {
        course.setEndDate(ZonedDateTime.now().minusMinutes(4));
        course.setCourseArchivePath(null);
        course.setExercises(Set.of(exercise));
        courseRepository.save(course);

        // Create a programming exercise with solution, template, and tests participations
        exercise = programmingExerciseRepository.save(exercise);
        exercise = programmingExerciseUtilService.addTemplateParticipationForProgrammingExercise(exercise);
        exercise = programmingExerciseUtilService.addSolutionParticipationForProgrammingExercise(exercise);
        exercise.setProblemStatement("Lorem Ipsum");
        programmingExerciseUtilService.addTestCasesToProgrammingExercise(exercise);

        // Add student participation
        exercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationById(exercise.getId()).orElseThrow();
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, userPrefix + studentLogin);

        // Mock student repo
        Repository studentRepository = gitService.getExistingCheckedOutRepositoryByLocalPath(studentRepo.localRepoFile.toPath(), null);
        createAndCommitDummyFileInLocalRepository(studentRepo, "HelloWorld.java");
        doReturn(studentRepository).when(gitService).getOrCheckoutRepository(eq(participation.getVcsRepositoryUri()), any(Path.class), anyBoolean());

        // Mock template repo
        Repository templateRepository = gitService.getExistingCheckedOutRepositoryByLocalPath(exerciseRepo.localRepoFile.toPath(), null);
        createAndCommitDummyFileInLocalRepository(exerciseRepo, "Template.java");
        doReturn(templateRepository).when(gitService).getOrCheckoutRepository(eq(exercise.getRepositoryURL(RepositoryType.TEMPLATE)), any(Path.class), anyBoolean());

        // Mock solution repo
        Repository solutionRepository = gitService.getExistingCheckedOutRepositoryByLocalPath(solutionRepo.localRepoFile.toPath(), null);
        createAndCommitDummyFileInLocalRepository(solutionRepo, "Solution.java");
        doReturn(solutionRepository).when(gitService).getOrCheckoutRepository(eq(exercise.getRepositoryURL(RepositoryType.SOLUTION)), any(Path.class), anyBoolean());

        // Mock tests repo
        Repository testsRepository = gitService.getExistingCheckedOutRepositoryByLocalPath(testRepo.localRepoFile.toPath(), null);
        createAndCommitDummyFileInLocalRepository(testRepo, "Tests.java");
        doReturn(testsRepository).when(gitService).getOrCheckoutRepository(eq(exercise.getRepositoryURL(RepositoryType.TESTS)), any(Path.class), anyBoolean());

        request.put("/api/courses/" + course.getId() + "/archive", null, HttpStatus.OK);
        await().until(() -> courseRepository.findById(course.getId()).orElseThrow().getCourseArchivePath() != null);

        var updatedCourse = courseRepository.findByIdElseThrow(course.getId());
        assertThat(updatedCourse.getCourseArchivePath()).isNotEmpty();
        // extract archive content and check that all expected files exist.
        Path courseArchivePath = courseArchivesDirPath.resolve(updatedCourse.getCourseArchivePath());
        Path extractedArchiveDir = zipFileTestUtilService.extractZipFileRecursively(courseArchivePath.toString());
        try (var files = Files.walk(extractedArchiveDir)) {
            assertThat(files).map(Path::getFileName).anyMatch((filename) -> filename.toString().matches(".*-exercise.zip"))
                    .anyMatch((filename) -> filename.toString().matches(".*-solution.zip")).anyMatch((filename) -> filename.toString().matches(".*-tests.zip"))
                    .anyMatch((filename) -> filename.toString().matches(EXPORTED_EXERCISE_PROBLEM_STATEMENT_FILE_PREFIX + ".*.md"))
                    .anyMatch((filename) -> filename.toString().matches(EXPORTED_EXERCISE_DETAILS_FILE_PREFIX + ".*.json"))
                    .anyMatch((filename) -> filename.toString().matches(".*student1.zip"));
        }
    }

    // Test
    void testExportCourseCannotExportSingleParticipationCanceledException() throws Exception {
        createCourseWithProgrammingExerciseAndParticipationWithFiles();
        testExportCourseWithFaultyParticipationCannotGetOrCheckoutRepository(new CanceledException("Checkout canceled"));
    }

    // Test
    void testExportCourseCannotExportSingleParticipationGitApiException() throws Exception {
        createCourseWithProgrammingExerciseAndParticipationWithFiles();
        testExportCourseWithFaultyParticipationCannotGetOrCheckoutRepository(new InvalidRemoteException("InvalidRemoteException"));
    }

    // Test
    void testExportCourseCannotExportSingleParticipationGitException() throws Exception {
        createCourseWithProgrammingExerciseAndParticipationWithFiles();
        testExportCourseWithFaultyParticipationCannotGetOrCheckoutRepository(new GitException("GitException"));
    }

    private void testExportCourseWithFaultyParticipationCannotGetOrCheckoutRepository(Exception exceptionToThrow) throws IOException, GitAPIException {
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, userPrefix + "student2");

        // Mock error when exporting a participation
        doThrow(exceptionToThrow).when(gitService).getOrCheckoutRepository(eq(participation.getVcsRepositoryUri()), any(Path.class), anyBoolean());

        course = courseRepository.findByIdWithExercisesAndLecturesElseThrow(course.getId());
        List<String> errors = new ArrayList<>();
        var optionalExportedCourse = courseExamExportService.exportCourse(course, courseArchivesDirPath, errors);
        assertThat(optionalExportedCourse).isPresent();

        // Extract the archive
        Path archivePath = optionalExportedCourse.get();
        Path extractedArchiveDir = zipFileTestUtilService.extractZipFileRecursively(archivePath.toString());

        // Check that the dummy files we created exist in the archive
        try (var files = Files.walk(extractedArchiveDir)) {
            var filenames = files.filter(Files::isRegularFile).map(Path::getFileName).toList();
            assertThat(filenames).contains(Path.of("Template.java"), Path.of("Solution.java"), Path.of("Tests.java"), Path.of("HelloWorld.java"));
        }

        FileUtils.deleteDirectory(extractedArchiveDir.toFile());
        FileUtils.delete(archivePath.toFile());
    }

    private void createCourseWithProgrammingExerciseAndParticipationWithFiles() throws GitAPIException, IOException {
        course.setEndDate(ZonedDateTime.now().minusMinutes(4));
        course.setCourseArchivePath(null);
        course.setExercises(Set.of(exercise));
        course = courseRepository.save(course);

        // Create a programming exercise with solution, template, and tests participations
        exercise = programmingExerciseRepository.save(exercise);
        exercise = programmingExerciseUtilService.addTemplateParticipationForProgrammingExercise(exercise);
        exercise = programmingExerciseUtilService.addSolutionParticipationForProgrammingExercise(exercise);
        programmingExerciseUtilService.addTestCasesToProgrammingExercise(exercise);

        // Add student participation
        exercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationById(exercise.getId()).orElseThrow();
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, userPrefix + studentLogin);

        // Mock student repo
        Repository studentRepository = gitService.getExistingCheckedOutRepositoryByLocalPath(studentRepo.localRepoFile.toPath(), null);
        createAndCommitDummyFileInLocalRepository(studentRepo, "HelloWorld.java");
        doReturn(studentRepository).when(gitService).getOrCheckoutRepository(eq(participation.getVcsRepositoryUri()), any(Path.class), anyBoolean());

        // Mock template repo
        Repository templateRepository = gitService.getExistingCheckedOutRepositoryByLocalPath(exerciseRepo.localRepoFile.toPath(), null);
        createAndCommitDummyFileInLocalRepository(exerciseRepo, "Template.java");
        doReturn(templateRepository).when(gitService).getOrCheckoutRepository(eq(exercise.getRepositoryURL(RepositoryType.TEMPLATE)), any(Path.class), anyBoolean());

        // Mock solution repo
        Repository solutionRepository = gitService.getExistingCheckedOutRepositoryByLocalPath(solutionRepo.localRepoFile.toPath(), null);
        createAndCommitDummyFileInLocalRepository(solutionRepo, "Solution.java");
        doReturn(solutionRepository).when(gitService).getOrCheckoutRepository(eq(exercise.getRepositoryURL(RepositoryType.SOLUTION)), any(Path.class), anyBoolean());

        // Mock tests repo
        Repository testsRepository = gitService.getExistingCheckedOutRepositoryByLocalPath(testRepo.localRepoFile.toPath(), null);
        createAndCommitDummyFileInLocalRepository(testRepo, "Tests.java");
        doReturn(testsRepository).when(gitService).getOrCheckoutRepository(eq(exercise.getRepositoryURL(RepositoryType.TESTS)), any(Path.class), anyBoolean());
    }

    public List<StudentExam> prepareStudentExamsForConduction(String testPrefix, ZonedDateTime examVisibleDate, ZonedDateTime examStartDate, ZonedDateTime examEndDate,
            Set<User> registeredStudents, List<LocalRepository> studentRepos) throws Exception {

        for (int i = 1; i <= registeredStudents.size(); i++) {
            mockDelegate.mockUserExists(testPrefix + "student" + i);
        }

        final var course = courseUtilService.addEmptyCourse();
        var exam = examUtilService.addExam(course, examVisibleDate, examStartDate, examEndDate);
        exam = examUtilService.addExerciseGroupsAndExercisesToExam(exam, true);

        // register users
        Set<ExamUser> registeredExamUsers = new HashSet<>();
        exam = examRepository.save(exam);
        for (var user : registeredStudents) {
            var registeredExamUser = new ExamUser();
            registeredExamUser.setUser(user);
            registeredExamUser.setExam(exam);
            registeredExamUser = examUserRepository.save(registeredExamUser);
            exam.addExamUser(registeredExamUser);
            registeredExamUsers.add(registeredExamUser);
        }
        exam.setExamUsers(registeredExamUsers);
        exam.setNumberOfExercisesInExam(exam.getExerciseGroups().size());
        exam.setRandomizeExerciseOrder(false);
        exam.setNumberOfCorrectionRoundsInExam(2);
        exam = examRepository.save(exam);

        // generate individual student exams
        List<StudentExam> studentExams = request.postListWithResponseBody("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/generate-student-exams", Optional.empty(),
                StudentExam.class, HttpStatus.OK);
        assertThat(studentExams).hasSize(exam.getExamUsers().size());
        assertThat(studentExamRepository.findByExamId(exam.getId())).hasSize(registeredStudents.size());

        // start exercises
        Set<Long> peIds = exam.getExerciseGroups().get(6).getExercises().stream().map(Exercise::getId).collect(Collectors.toSet());
        List<ProgrammingExercise> programmingExercises = programmingExerciseTestRepository.findAllWithTemplateAndSolutionParticipationByIdIn(peIds);
        exam.getExerciseGroups().get(6).setExercises(new HashSet<>(programmingExercises));
        for (var exercise : programmingExercises) {

            setupRepositoryMocks(exercise);
            for (var examUser : exam.getExamUsers()) {
                var repo = new LocalRepository(defaultBranch);
                repo.configureRepos("studentRepo", "studentOriginRepo");
                setupRepositoryMocksParticipant(exercise, examUser.getUser().getLogin(), repo);
                studentRepos.add(repo);
            }
        }

        for (var programmingExercise : programmingExercises) {
            for (var user : registeredStudents) {
                mockDelegate.mockConnectorRequestsForStartParticipation(programmingExercise, user.getParticipantIdentifier(), Set.of(user), true);
            }
        }

        int noGeneratedParticipations = ExamPrepareExercisesTestUtil.prepareExerciseStart(request, exam, course);
        assertThat(noGeneratedParticipations).isEqualTo(registeredStudents.size() * exam.getExerciseGroups().size());

        mockDelegate.resetMockProvider();

        return studentExams;
    }

    /**
     * Creates a dummy file in the repository and commits it locally
     * without pushing it.
     *
     * @param localRepository the repository
     * @param filename        the file to create
     * @throws IOException     when the file cannot be created
     * @throws GitAPIException when git can't add or commit the file
     */
    private void createAndCommitDummyFileInLocalRepository(LocalRepository localRepository, String filename) throws IOException, GitAPIException {
        var file = Path.of(localRepository.localRepoFile.toPath().toString(), filename);
        if (!Files.exists(file)) {
            Files.createFile(file);
        }
        localRepository.localGit.add().addFilepattern(file.getFileName().toString()).call();
        GitService.commit(localRepository.localGit).setMessage("Added testfile").call();
    }

    // Test
    void testDownloadCourseArchiveAsInstructor() throws Exception {
        // Archive the course and wait until it's complete
        testArchiveCourseWithProgrammingExercise();

        // Download the archive
        var archive = request.getFile("/api/courses/" + course.getId() + "/download-archive", HttpStatus.OK, new LinkedMultiValueMap<>());
        assertThat(archive).isNotNull();
        assertThat(archive).exists();

        // Extract the archive
        Path extractedArchiveDir = zipFileTestUtilService.extractZipFileRecursively(archive.getAbsolutePath());

        // Check that the dummy files we created exist in the archive
        try (var files = Files.walk(extractedArchiveDir)) {
            var filenames = files.filter(Files::isRegularFile).map(Path::getFileName).map(Path::toString).toList();
            assertThat(filenames).contains("HelloWorld.java", "Template.java", "Solution.java", "Tests.java");
        }

        FileUtils.deleteDirectory(extractedArchiveDir.toFile());
        FileUtils.delete(archive);
    }

    private ProgrammingExerciseStudentParticipation createStudentParticipationWithSubmission(ExerciseMode exerciseMode) {
        setupCourseWithProgrammingExercise(exerciseMode);
        User user = userRepo.findOneByLogin(userPrefix + studentLogin).orElseThrow();

        ProgrammingExerciseStudentParticipation participation;
        if (exerciseMode == TEAM) {
            var team = setupTeam(user);
            participation = participationUtilService.addTeamParticipationForProgrammingExercise(exercise, team);
            // prepare for the mock scenario, so that the empty commit will work properly
            participation.setRepositoryUri(ParticipationFactory.getMockFileRepositoryUri(studentTeamRepo).getURI().toString());
        }
        else {
            participation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, user.getParticipantIdentifier());
            // prepare for the mock scenario, so that the empty commit will work properly
            participation.setRepositoryUri(ParticipationFactory.getMockFileRepositoryUri(studentRepo).getURI().toString());
        }

        ProgrammingSubmission submission = new ProgrammingSubmission();
        submission.setSubmissionDate(ZonedDateTime.now().minusMinutes(4));
        submission.setSubmitted(true);
        submission.setCommitHash(TestConstants.COMMIT_HASH_STRING);
        submission.setType(SubmissionType.MANUAL);
        participationUtilService.addSubmission(participation, submission);

        return participation;
    }

    @NotNull
    private Team setupTeam(User user) {
        // create a team for the user (necessary condition before starting an exercise)
        Set<User> students = Set.of(user);
        Team team = new Team().name("Team 1").shortName(userPrefix + teamShortName).exercise(exercise).students(students);
        team = teamRepository.save(exercise, team);
        assertThat(team.getStudents()).as("Student was correctly added to team").hasSize(1);
        return team;
    }

    // TEST
    void startProgrammingExerciseStudentRetrieveEmptyArtifactPage() throws Exception {
        persistProgrammingExercise();
        User user = userRepo.findOneByLogin(userPrefix + studentLogin).orElseThrow();
        mockDelegate.mockConnectorRequestsForStartParticipation(exercise, user.getParticipantIdentifier(), Set.of(user), true);

        final var participation = createUserParticipation();

        // create a submission
        programmingExerciseUtilService.createProgrammingSubmission(participation, false);

        mockDelegate.resetMockProvider();
        mockDelegate.mockRetrieveArtifacts(participation);

        var artifact = request.get(PARTICIPATION_BASE_URL + participation.getId() + "/buildArtifact", HttpStatus.OK, byte[].class);

        assertThat(participation.getInitializationState()).as("Participation should be initialized").isEqualTo(InitializationState.INITIALIZED);
        assertThat(artifact).as("No build artifact available for this plan").isEmpty();
    }

    // TEST
    void repositoryAccessIsAdded_whenStudentIsAddedToTeam() throws Exception {
        setupTeamExercise();

        // Create a team with students
        Set<User> students = new HashSet<>(userRepo.searchByLoginOrNameInGroup("tumuser", userPrefix + "student"));
        Team team = new Team().name("Team 1").shortName(userPrefix + teamShortName).exercise(exercise).students(students);
        team = teamRepository.save(exercise, team);

        assertThat(team.getStudents()).as("Students were correctly added to team").hasSize(numberOfStudents).hasSameSizeAs(students);

        // Set up mockRetrieveArtifacts requests for start participation
        mockDelegate.mockConnectorRequestsForStartParticipation(exercise, team.getParticipantIdentifier(), team.getStudents(), true);

        // Add a new student to the team
        User newStudent = userUtilService
                .generateAndSaveActivatedUsers(userPrefix + "new-student", new String[] { "tumuser", "testgroup" }, Set.of(new Authority(Role.STUDENT.getAuthority())), 1).get(0);
        newStudent = userRepo.save(newStudent);
        team.addStudents(newStudent);

        // Mock repository write permission give call
        mockDelegate.mockRepositoryWritePermissionsForTeam(team, newStudent, exercise, HttpStatus.OK);

        // Start participation with original team
        participationService.startExercise(exercise, team, false);

        // Update team with new student after participation has already started
        Team serverTeam = request.putWithResponseBody("/api/exercises/" + exercise.getId() + "/teams/" + team.getId(), team, Team.class, HttpStatus.OK);
        assertThat(serverTeam.getStudents()).as("Team students were updated correctly").hasSize(numberOfStudents + 1); // new student was added
    }

    // TEST
    void repositoryAccessIsRemoved_whenStudentIsRemovedFromTeam() throws Exception {
        setupTeamExercise();

        // Create a team with students
        Set<User> students = new HashSet<>(userRepo.searchByLoginOrNameInGroup("tumuser", userPrefix + "student"));
        Team team = new Team().name("Team 1").shortName(userPrefix + teamShortName).exercise(exercise).students(students);
        team = teamRepository.save(exercise, team);

        assertThat(team.getStudents()).as("Students were correctly added to team").hasSize(numberOfStudents).hasSameSizeAs(students);

        // Set up mockRetrieveArtifacts requests for start participation
        mockDelegate.mockConnectorRequestsForStartParticipation(exercise, team.getParticipantIdentifier(), team.getStudents(), true);

        // Remove the first student from the team
        User firstStudent = students.iterator().next();
        team.removeStudents(firstStudent);

        // Mock repository access removal call
        mockDelegate.mockRemoveRepositoryAccess(exercise, team, firstStudent);

        // Start participation with original team
        participationService.startExercise(exercise, team, false);

        // Update team with removed student
        Team serverTeam = request.putWithResponseBody("/api/exercises/" + exercise.getId() + "/teams/" + team.getId(), team, Team.class, HttpStatus.OK);
        assertThat(serverTeam.getStudents()).as("Team students were updated correctly").hasSize(numberOfStudents - 1); // first student was removed
    }

    // TEST
    void configureRepository_throwExceptionWhenLtiUserIsNotExistent() throws Exception {
        setupTeamExercise();

        // create a team for the user (necessary condition before starting an exercise)
        // final String edxUsername = userPrefixEdx.get() + "student"; // TODO: Fix this (userPrefixEdx is missing)
        final String edxUsername = userPrefix + "ltinotpres" + "student";

        User edxStudent = UserFactory.generateActivatedUsers(edxUsername, new String[] { "tumuser", "testgroup" }, Set.of(new Authority(Role.STUDENT.getAuthority())), 1).get(0);
        edxStudent.setInternal(true);
        edxStudent.setPassword(passwordService.hashPassword(edxStudent.getPassword()));
        edxStudent = userRepo.save(edxStudent);
        Team team = setupTeam(edxStudent);

        // Set up mock requests for start participation and that a lti user is not existent
        final boolean ltiUserExists = false;
        mockDelegate.mockConnectorRequestsForStartParticipation(exercise, team.getParticipantIdentifier(), team.getStudents(), ltiUserExists);

        // Start participation with original team
        assertThatExceptionOfType(GitLabException.class).isThrownBy(() -> participationService.startExercise(exercise, team, false));
    }

    // TEST
    void copyRepository_testNotCreatedError() throws Exception {
        Team team = setupTeamForBadRequestForStartExercise();

        var participantRepoTestUrl = ParticipationFactory.getMockFileRepositoryUri(studentTeamRepo);
        final var teamLocalPath = studentTeamRepo.localRepoFile.toPath();
        doReturn(teamLocalPath).when(gitService).getDefaultLocalPathOfRepo(participantRepoTestUrl);
        doReturn(defaultBranch).when(versionControlService).getOrRetrieveBranchOfExercise(exercise);
        doThrow(new CanceledException("Checkout got interrupted!")).when(gitService).getOrCheckoutRepositoryIntoTargetDirectory(any(), any(), anyBoolean());

        // the local repo should exist before startExercise()
        assertThat(teamLocalPath).exists();

        // Start participation
        assertThatExceptionOfType(VersionControlException.class).isThrownBy(() -> participationService.startExercise(exercise, team, false))
                .matches(exception -> !exception.getMessage().isEmpty());

        // the directory of the repo should be deleted
        assertThat(teamLocalPath).doesNotExist();
    }

    @NotNull
    private Team setupTeamForBadRequestForStartExercise() throws Exception {
        setupTeamExercise();

        // Create a team with students
        var student1 = userUtilService.getUserByLogin(userPrefix + "student1");
        Team team = new Team().name("Team 1").shortName(userPrefix + teamShortName).exercise(exercise).students(Set.of(student1));
        team = teamRepository.save(exercise, team);

        assertThat(team.getStudents()).as("Student1 was correctly added to team").hasSize(1);

        // test for internal server error
        mockDelegate.mockCopyRepositoryForParticipation(exercise, team.getParticipantIdentifier());
        mockDelegate.mockRepositoryWritePermissionsForTeam(team, student1, exercise, HttpStatus.BAD_REQUEST);
        return team;
    }

    private void setupTeamExercise() {
        exercise.setMode(TEAM);
        programmingExerciseRepository.save(exercise);
        programmingExerciseUtilService.addTemplateParticipationForProgrammingExercise(exercise);
        programmingExerciseUtilService.addSolutionParticipationForProgrammingExercise(exercise);
    }

    // TEST
    void copyRepository_testConflictError() throws Exception {
        setupTeamExercise();

        // Create a team with students
        Set<User> students = new HashSet<>(userRepo.searchByLoginOrNameInGroup("tumuser", userPrefix + "student"));
        Team team = new Team().name("Team 1").shortName(userPrefix + teamShortName).exercise(exercise).students(students);
        team = teamRepository.save(exercise, team);

        assertThat(team.getStudents()).as("Students were correctly added to team").hasSize(numberOfStudents);

        // test for Conflict exception
        mockDelegate.mockConnectorRequestsForStartParticipation(exercise, team.getParticipantIdentifier(), team.getStudents(), true);

        // Start participation
        participationService.startExercise(exercise, team, false);

        // TODO add assertions
    }

    // TEST
    void configureRepository_testBadRequestError() throws Exception {
        Team team = setupTeamForBadRequestForStartExercise();

        // Start participation
        assertThatExceptionOfType(VersionControlException.class).isThrownBy(() -> participationService.startExercise(exercise, team, false))
                .matches(exception -> !exception.getMessage().isEmpty());

    }

    // TEST
    void automaticCleanupBuildPlans() throws Exception {
        String testPrefix = "cleanup";
        userUtilService.addUsers(userPrefix + testPrefix, 12, 0, 0, 0);

        // this is needed so that the participations that are currently in the repository get ignored by findAllWithBuildPlanIdWithResults().
        // Otherwise participations with an unexpected buildPlanId are retrieved when calling cleanupBuildPlansOnContinuousIntegrationServer() below
        programmingExerciseParticipationTestRepository.updateBuildPlanIdOfAll(null);

        exercise = programmingExerciseRepository.save(exercise);
        examExercise = programmingExerciseRepository.save(examExercise);

        var exercise2 = ProgrammingExerciseFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(5), ZonedDateTime.now().minusDays(4), course);
        exercise2.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().plusDays(1));
        exercise2 = programmingExerciseRepository.save(exercise2);

        var exercise3 = ProgrammingExerciseFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(5), ZonedDateTime.now().minusDays(4), course);
        exercise3.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().minusDays(3));
        exercise3 = programmingExerciseRepository.save(exercise3);

        var exercise4 = ProgrammingExerciseFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(5), ZonedDateTime.now().minusDays(4), course);
        exercise4 = programmingExerciseRepository.save(exercise4);

        // Note participationXa will always be cleaned up, while participationXb will NOT be cleaned up

        // SuccessfulLatestResultAfter1Days
        var participation1a = createProgrammingParticipationWithSubmissionAndResult(exercise, testPrefix + "student1", 100D, ZonedDateTime.now().minusDays(2), true);
        var participation1b = createProgrammingParticipationWithSubmissionAndResult(exercise, testPrefix + "student2", 100D, ZonedDateTime.now().minusHours(6), true);
        // UnsuccessfulLatestResultAfter5Days
        var participation2a = createProgrammingParticipationWithSubmissionAndResult(exercise, testPrefix + "student3", 80D, ZonedDateTime.now().minusDays(6), true);
        var participation2b = createProgrammingParticipationWithSubmissionAndResult(exercise, testPrefix + "student4", 80D, ZonedDateTime.now().minusDays(4), true);
        // NoResultAfter3Days
        var participation3a = createProgrammingParticipationWithSubmissionAndResult(exercise, testPrefix + "student5", 80D, ZonedDateTime.now().minusDays(6), false);
        participation3a.setInitializationDate(ZonedDateTime.now().minusDays(4));
        var participation3b = createProgrammingParticipationWithSubmissionAndResult(exercise, testPrefix + "student6", 80D, ZonedDateTime.now().minusDays(6), false);
        participation3b.setInitializationDate(ZonedDateTime.now().minusDays(2));

        var participation4b = createProgrammingParticipationWithSubmissionAndResult(examExercise, testPrefix + "student7", 80D, ZonedDateTime.now().minusDays(6), false);
        var participation5b = createProgrammingParticipationWithSubmissionAndResult(examExercise, testPrefix + "student8", 80D, ZonedDateTime.now().minusDays(6), false);
        participation5b.setBuildPlanId(null);

        var participation6b = createProgrammingParticipationWithSubmissionAndResult(examExercise, testPrefix + "student9", 80D, ZonedDateTime.now().minusDays(6), false);
        participation6b.setParticipant(null);

        var participation7a = createProgrammingParticipationWithSubmissionAndResult(exercise3, testPrefix + "student10", 80D, ZonedDateTime.now().minusDays(4), true);
        var participation7b = createProgrammingParticipationWithSubmissionAndResult(exercise2, testPrefix + "student11", 80D, ZonedDateTime.now().minusDays(4), true);

        var participation8b = createProgrammingParticipationWithSubmissionAndResult(exercise4, testPrefix + "student12", 100D, ZonedDateTime.now().minusDays(6), true);

        programmingExerciseStudentParticipationRepository.saveAll(Set.of(participation3a, participation3b, participation5b, participation6b));
        await().untilAsserted(
                () -> assertThat(programmingExerciseStudentParticipationRepository.findAllWithBuildPlanIdWithResults()).containsExactlyInAnyOrderElementsOf(List.of(participation1a,
                        participation1b, participation2a, participation2b, participation3a, participation3b, participation4b, participation7a, participation7b, participation8b)));

        mockDelegate.mockDeleteBuildPlan(exercise.getProjectKey(), exercise.getProjectKey() + "-" + participation1a.getParticipantIdentifier().toUpperCase(), false);
        mockDelegate.mockDeleteBuildPlan(exercise.getProjectKey(), exercise.getProjectKey() + "-" + participation2a.getParticipantIdentifier().toUpperCase(), false);
        mockDelegate.mockDeleteBuildPlan(exercise.getProjectKey(), exercise.getProjectKey() + "-" + participation3a.getParticipantIdentifier().toUpperCase(), false);
        mockDelegate.mockDeleteBuildPlan(exercise3.getProjectKey(), exercise3.getProjectKey() + "-" + participation7a.getParticipantIdentifier().toUpperCase(), false);

        automaticProgrammingExerciseCleanupService.cleanup(); // this call won't do it, because of the missing profile, we execute it anyway to cover at least some code
        automaticProgrammingExerciseCleanupService.cleanupBuildPlansOnContinuousIntegrationServer();

        assertThat(programmingExerciseStudentParticipationRepository.findByIdElseThrow(participation1a.getId()).getBuildPlanId()).isNull();
        assertThat(programmingExerciseStudentParticipationRepository.findByIdElseThrow(participation1b.getId()).getBuildPlanId()).isNotNull();

        assertThat(programmingExerciseStudentParticipationRepository.findByIdElseThrow(participation2a.getId()).getBuildPlanId()).isNull();
        assertThat(programmingExerciseStudentParticipationRepository.findByIdElseThrow(participation2b.getId()).getBuildPlanId()).isNotNull();

        assertThat(programmingExerciseStudentParticipationRepository.findByIdElseThrow(participation3a.getId()).getBuildPlanId()).isNull();
        assertThat(programmingExerciseStudentParticipationRepository.findByIdElseThrow(participation3b.getId()).getBuildPlanId()).isNotNull();

        assertThat(programmingExerciseStudentParticipationRepository.findByIdElseThrow(participation4b.getId()).getBuildPlanId()).isNotNull();
        assertThat(programmingExerciseStudentParticipationRepository.findByIdElseThrow(participation5b.getId()).getBuildPlanId()).isNull(); // was already null before
        assertThat(programmingExerciseStudentParticipationRepository.findByIdElseThrow(participation6b.getId()).getBuildPlanId()).isNotNull();

        assertThat(programmingExerciseStudentParticipationRepository.findByIdElseThrow(participation7a.getId()).getBuildPlanId()).isNull();
        assertThat(programmingExerciseStudentParticipationRepository.findByIdElseThrow(participation7b.getId()).getBuildPlanId()).isNotNull();

        assertThat(programmingExerciseStudentParticipationRepository.findByIdElseThrow(participation8b.getId()).getBuildPlanId()).isNotNull();
    }

    private ProgrammingExerciseStudentParticipation createProgrammingParticipationWithSubmissionAndResult(ProgrammingExercise exercise, String studentLogin, double score,
            ZonedDateTime submissionDate, boolean withResult) {
        var programmingSubmission = ParticipationFactory.generateProgrammingSubmission(true, "abcde", SubmissionType.MANUAL);
        programmingSubmission = programmingExerciseUtilService.addProgrammingSubmission(exercise, programmingSubmission, userPrefix + studentLogin);
        if (withResult) {
            participationUtilService.addResultToParticipation(AssessmentType.AUTOMATIC, submissionDate, programmingSubmission.getParticipation(), score >= 100D, true, 100D);
        }
        return (ProgrammingExerciseStudentParticipation) programmingSubmission.getParticipation();
    }

    // TEST
    void automaticCleanupGitRepositories() {
        var startDate = ZonedDateTime.now().minusWeeks(15L);
        var endDate = startDate.plusDays(5L);
        exercise.setReleaseDate(startDate);
        exercise.setDueDate(endDate);
        exercise = programmingExerciseRepository.save(exercise);
        exercise.getCourseViaExerciseGroupOrCourseMember().setStartDate(startDate);
        exercise.getCourseViaExerciseGroupOrCourseMember().setEndDate(endDate);
        courseRepository.save(exercise.getCourseViaExerciseGroupOrCourseMember());

        examExercise = programmingExerciseRepository.save(examExercise);
        examExercise.getExerciseGroup().getExam().setStartDate(startDate);
        examExercise.getExerciseGroup().getExam().setEndDate(endDate);
        examRepository.save(examExercise.getExerciseGroup().getExam());

        var createdExercise = programmingExerciseRepository.findById(exercise.getId());
        assertThat(createdExercise).isPresent();
        var createdExamExercise = programmingExerciseRepository.findById(examExercise.getId());
        assertThat(createdExamExercise).isPresent();

        createProgrammingParticipationWithSubmissionAndResult(exercise, "student1", 100D, ZonedDateTime.now().minusDays(2L), false);
        createProgrammingParticipationWithSubmissionAndResult(exercise, "student2", 80D, ZonedDateTime.now().minusDays(6L), false);

        createProgrammingParticipationWithSubmissionAndResult(examExercise, "student3", 100D, ZonedDateTime.now().minusDays(2L), false);
        createProgrammingParticipationWithSubmissionAndResult(examExercise, "student4", 80D, ZonedDateTime.now().minusDays(6L), false);

        automaticProgrammingExerciseCleanupService.cleanupGitRepositoriesOnArtemisServer();
        // Note: at the moment, we cannot easily assert something here, it might be possible to verify mocks on gitService, in case we could define it as SpyBean
    }

    private void validateProgrammingExercise(ProgrammingExercise generatedExercise) {
        exercise.setId(generatedExercise.getId());
        exercise.setTemplateParticipation(generatedExercise.getTemplateParticipation());
        exercise.setSolutionParticipation(generatedExercise.getSolutionParticipation());
        assertThat(exercise).isEqualTo(generatedExercise);
        var templateSubmissions = submissionRepository.findAllByParticipationId(exercise.getTemplateParticipation().getId());
        assertThat(templateSubmissions).hasSize(1);
        Optional<ProgrammingSubmission> templateSubmission = programmingSubmissionRepository.findById(templateSubmissions.get(0).getId());
        assertThat(templateSubmission.isPresent()).isTrue();
        assertThat(templateSubmission.get().getType()).isEqualTo(SubmissionType.INSTRUCTOR);
        var solutionSubmissions = submissionRepository.findAllByParticipationId(exercise.getSolutionParticipation().getId());
        assertThat(solutionSubmissions).hasSize(1);
        Optional<ProgrammingSubmission> solutionSubmission = programmingSubmissionRepository.findById(solutionSubmissions.get(0).getId());
        assertThat(solutionSubmission.isPresent()).isTrue();
        assertThat(solutionSubmission.get().getType()).isEqualTo(SubmissionType.INSTRUCTOR);
        assertThat(programmingExerciseRepository.findById(exercise.getId())).isPresent();
    }

    private void persistProgrammingExercise() {
        programmingExerciseRepository.save(exercise);
        programmingExerciseUtilService.addTemplateParticipationForProgrammingExercise(exercise);
        programmingExerciseUtilService.addSolutionParticipationForProgrammingExercise(exercise);
    }

    private ProgrammingExerciseStudentParticipation createUserParticipation() throws Exception {
        final var path = "/api/exercises/{exerciseId}/participations".replace("{exerciseId}", String.valueOf(exercise.getId()));
        return request.postWithResponseBody(path, null, ProgrammingExerciseStudentParticipation.class, HttpStatus.CREATED);
    }

    private List<DiffEntry> getChanges(Repository repository, RevCommit commit) throws Exception {
        try (ObjectReader reader = repository.newObjectReader()) {
            CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
            oldTreeIter.reset(reader, commit.getParents()[0].getTree());
            CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
            newTreeIter.reset(reader, commit.getTree());

            // finally get the list of changed files
            try (Git git = new Git(repository)) {
                List<DiffEntry> diffs = git.diff().setNewTree(newTreeIter).setOldTree(oldTreeIter).call();
                for (DiffEntry entry : diffs) {
                    log.debug("Entry: {}", entry.toString());
                }
                return diffs;
            }
        }
    }

    // TEST
    void importProgrammingExerciseFromCourseToCourse_exampleSolutionPublicationDate() throws Exception {
        Course course1 = courseUtilService.addEmptyCourse();
        Course course2 = courseUtilService.addEmptyCourse();

        ProgrammingExercise sourceExercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course1, false);
        ProgrammingExercise exerciseToBeImported = ProgrammingExerciseFactory.generateToBeImportedProgrammingExercise("ImportTitle", "Imported", sourceExercise, course2);

        exerciseToBeImported.setExampleSolutionPublicationDate(sourceExercise.getDueDate().plusDays(1));

        // Mock requests
        setupRepositoryMocks(sourceExercise, sourceExerciseRepo, sourceSolutionRepo, sourceTestRepo, sourceAuxRepo);
        setupRepositoryMocks(exerciseToBeImported, exerciseRepo, solutionRepo, testRepo, auxRepo);
        mockDelegate.mockConnectorRequestsForImport(sourceExercise, exerciseToBeImported, false, false);
        setupMocksForConsistencyChecksOnImport(sourceExercise);

        ProgrammingExercise newProgrammingExercise = request.postWithResponseBody(ROOT + IMPORT.replace("{sourceExerciseId}", sourceExercise.getId().toString()),
                exerciseToBeImported, ProgrammingExercise.class, HttpStatus.OK);
        assertThat(newProgrammingExercise.getExampleSolutionPublicationDate()).as("programming example solution publication date was correctly set to null in the response")
                .isNull();

        ProgrammingExercise newProgrammingExerciseFromDatabase = programmingExerciseRepository.findById(newProgrammingExercise.getId()).orElseThrow();
        assertThat(newProgrammingExerciseFromDatabase.getExampleSolutionPublicationDate())
                .as("programming example solution publication date was correctly set to null in the database").isNull();
    }

    // TEST
    void createProgrammingExercise_setInvalidExampleSolutionPublicationDate_badRequest() throws Exception {
        final var baseTime = ZonedDateTime.now();

        exercise.setAssessmentDueDate(null);

        exercise.setReleaseDate(baseTime.plusHours(1));
        exercise.setDueDate(baseTime.plusHours(3));
        exercise.setExampleSolutionPublicationDate(baseTime.plusHours(2));

        mockDelegate.mockConnectorRequestsForSetup(exercise, false, false, false);

        request.postWithResponseBody(ROOT + SETUP, exercise, ProgrammingExercise.class, HttpStatus.BAD_REQUEST);

        exercise.setReleaseDate(baseTime.plusHours(3));
        exercise.setDueDate(null);
        exercise.setExampleSolutionPublicationDate(baseTime.plusHours(2));

        request.postWithResponseBody(ROOT + SETUP, exercise, ProgrammingExercise.class, HttpStatus.BAD_REQUEST);
    }

    // TEST
    void createProgrammingExercise_setValidExampleSolutionPublicationDate() throws Exception {
        final var baseTime = ZonedDateTime.now();

        exercise.setAssessmentDueDate(null);

        exercise.setReleaseDate(baseTime.plusHours(1));
        exercise.setDueDate(baseTime.plusHours(2));
        var exampleSolutionPublicationDate = baseTime.plusHours(3);
        exercise.setExampleSolutionPublicationDate(exampleSolutionPublicationDate);
        exercise.setChannelName("testchannel-pe");
        mockDelegate.mockConnectorRequestsForSetup(exercise, false, false, false);

        var result = request.postWithResponseBody(ROOT + SETUP, exercise, ProgrammingExercise.class, HttpStatus.CREATED);
        assertThat(result.getExampleSolutionPublicationDate()).isCloseTo(exampleSolutionPublicationDate, within(1, ChronoUnit.MILLIS));
    }

    // TEST
    void testGetProgrammingExercise_exampleSolutionVisibility(boolean isStudent, String username) throws Exception {

        if (isStudent) {
            assertThat(username).as("The setup is done according to studentLogin value, another username may not work as expected").isEqualTo(userPrefix + studentLogin);
        }

        // Utility function to avoid duplication
        Function<Course, ProgrammingExercise> programmingExerciseGetter = c -> (ProgrammingExercise) c.getExercises().stream().filter(e -> e.getId().equals(exercise.getId()))
                .findAny().orElseThrow();

        // Test example solution publication date not set.
        exercise.setExampleSolutionPublicationDate(null);
        programmingExerciseRepository.save(exercise);

        CourseForDashboardDTO courseForDashboardFromServer = request.get("/api/courses/" + exercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/for-dashboard",
                HttpStatus.OK, CourseForDashboardDTO.class);
        Course courseFromServer = courseForDashboardFromServer.course();
        ProgrammingExercise programmingExerciseFromApi = programmingExerciseGetter.apply(courseFromServer);

        assertThat(programmingExerciseFromApi.isExampleSolutionPublished()).isFalse();

        // Test example solution publication date in the past.
        exercise.setExampleSolutionPublicationDate(ZonedDateTime.now().minusHours(1));
        programmingExerciseRepository.save(exercise);

        courseForDashboardFromServer = request.get("/api/courses/" + exercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/for-dashboard", HttpStatus.OK,
                CourseForDashboardDTO.class);
        courseFromServer = courseForDashboardFromServer.course();
        programmingExerciseFromApi = programmingExerciseGetter.apply(courseFromServer);

        assertThat(programmingExerciseFromApi.isExampleSolutionPublished()).isTrue();

        // Test example solution publication date in the future.
        exercise.setExampleSolutionPublicationDate(ZonedDateTime.now().plusHours(1));
        programmingExerciseRepository.save(exercise);

        courseForDashboardFromServer = request.get("/api/courses/" + exercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/for-dashboard", HttpStatus.OK,
                CourseForDashboardDTO.class);
        courseFromServer = courseForDashboardFromServer.course();
        programmingExerciseFromApi = programmingExerciseGetter.apply(courseFromServer);

        assertThat(programmingExerciseFromApi.isExampleSolutionPublished()).isFalse();

    }

    // TEST
    void exportSolutionRepository_shouldReturnFileOrForbidden() throws Exception {
        // Test example solution publication date not set.
        exercise.setExampleSolutionPublicationDate(null);
        programmingExerciseRepository.save(exercise);

        exportStudentRequestedRepository(HttpStatus.FORBIDDEN, false);

        // Test example solution publication date in the past.
        exercise.setExampleSolutionPublicationDate(ZonedDateTime.now().minusHours(1));
        programmingExerciseRepository.save(exercise);

        String zip = exportStudentRequestedRepository(HttpStatus.OK, false);
        assertThat(zip).isNotNull();

        // Test include tests but not allowed
        exportStudentRequestedRepository(HttpStatus.FORBIDDEN, true);

        // Test include tests
        exercise.setReleaseTestsWithExampleSolution(true);
        programmingExerciseRepository.save(exercise);

        zip = exportStudentRequestedRepository(HttpStatus.OK, true);
        assertThat(zip).isNotNull();

        // Test example solution publication date in the future.
        exercise.setExampleSolutionPublicationDate(ZonedDateTime.now().plusHours(1));
        programmingExerciseRepository.save(exercise);

        exportStudentRequestedRepository(HttpStatus.FORBIDDEN, false);
    }

    // TEST
    void exportExamSolutionRepository_shouldReturnFileOrForbidden() throws Exception {
        Exam exam = examExercise.getExerciseGroup().getExam();
        examUtilService.addStudentExamWithUser(exam, userRepo.getUser());
        exercise = examExercise;

        // Test example solution publication date not set.
        exam.setExampleSolutionPublicationDate(null);
        examRepository.save(exam);

        exportStudentRequestedRepository(HttpStatus.FORBIDDEN, false);

        // Test example solution publication date in the past.
        exam.setExampleSolutionPublicationDate(ZonedDateTime.now().minusHours(1));
        examRepository.save(exam);

        String zip = exportStudentRequestedRepository(HttpStatus.OK, false);
        assertThat(zip).isNotNull();

        // Test include tests but not allowed
        exportStudentRequestedRepository(HttpStatus.FORBIDDEN, true);

        // Test include tests
        exercise.setReleaseTestsWithExampleSolution(true);
        programmingExerciseRepository.save(exercise);

        zip = exportStudentRequestedRepository(HttpStatus.OK, true);
        assertThat(zip).isNotNull();

        // Test example solution publication date in the future.
        exam.setExampleSolutionPublicationDate(ZonedDateTime.now().plusHours(1));
        examRepository.save(exam);

        exportStudentRequestedRepository(HttpStatus.FORBIDDEN, false);
    }

    // TEST
    void buildLogStatistics_unauthorized() throws Exception {
        exercise = programmingExerciseRepository
                .save(ProgrammingExerciseFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(7), course));
        request.get("/api/programming-exercises/" + exercise.getId() + "/build-log-statistics", HttpStatus.FORBIDDEN, BuildLogStatisticsDTO.class);
    }

    // TEST
    void buildLogStatistics_noStatistics() throws Exception {
        exercise = programmingExerciseRepository
                .save(ProgrammingExerciseFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(7), course));
        var statistics = request.get("/api/programming-exercises/" + exercise.getId() + "/build-log-statistics", HttpStatus.OK, BuildLogStatisticsDTO.class);
        assertThat(statistics.buildCount()).isZero();
        assertThat(statistics.agentSetupDuration()).isNull();
        assertThat(statistics.testDuration()).isNull();
        assertThat(statistics.scaDuration()).isNull();
        assertThat(statistics.totalJobDuration()).isNull();
        assertThat(statistics.dependenciesDownloadedCount()).isNull();
    }

    // TEST
    void buildLogStatistics() throws Exception {
        exercise = programmingExerciseRepository
                .save(ProgrammingExerciseFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(7), course));
        var participation = createStudentParticipationWithSubmission(INDIVIDUAL);
        var submission1 = programmingExerciseUtilService.createProgrammingSubmission(participation, false);
        var submission2 = programmingExerciseUtilService.createProgrammingSubmission(participation, false);

        buildLogStatisticsEntryRepository.save(new BuildLogStatisticsEntry(submission1, 10, 20, 30, 60, 5));
        buildLogStatisticsEntryRepository.save(new BuildLogStatisticsEntry(submission2, 8, 15, null, 30, 0));

        var statistics = request.get("/api/programming-exercises/" + exercise.getId() + "/build-log-statistics", HttpStatus.OK, BuildLogStatisticsDTO.class);
        assertThat(statistics.buildCount()).isEqualTo(2);
        assertThat(statistics.agentSetupDuration()).isEqualTo(9);
        assertThat(statistics.testDuration()).isEqualTo(17.5);
        assertThat(statistics.scaDuration()).isEqualTo(30);
        assertThat(statistics.totalJobDuration()).isEqualTo(45);
        assertThat(statistics.dependenciesDownloadedCount()).isEqualTo(2.5);
    }

    private void setupMocksForConsistencyChecksOnImport(ProgrammingExercise sourceExercise) throws Exception {
        var programmingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationAndAuxiliaryRepositoriesById(sourceExercise.getId()).orElseThrow();

        mockDelegate.mockCheckIfProjectExistsInVcs(programmingExercise, true);
        mockDelegate.mockRepositoryUriIsValid(programmingExercise.getVcsTemplateRepositoryUri(),
                uriService.getProjectKeyFromRepositoryUri(programmingExercise.getVcsTemplateRepositoryUri()), true);
        mockDelegate.mockRepositoryUriIsValid(programmingExercise.getVcsSolutionRepositoryUri(),
                uriService.getProjectKeyFromRepositoryUri(programmingExercise.getVcsSolutionRepositoryUri()), true);
        mockDelegate.mockRepositoryUriIsValid(programmingExercise.getVcsTestRepositoryUri(),
                uriService.getProjectKeyFromRepositoryUri(programmingExercise.getVcsTestRepositoryUri()), true);
        for (var auxiliaryRepository : programmingExercise.getAuxiliaryRepositories()) {
            mockDelegate.mockGetRepositorySlugFromRepositoryUri(sourceExercise.generateRepositoryName("auxrepo"), auxiliaryRepository.getVcsRepositoryUri());
            mockDelegate.mockRepositoryUriIsValid(auxiliaryRepository.getVcsRepositoryUri(), uriService.getProjectKeyFromRepositoryUri(auxiliaryRepository.getVcsRepositoryUri()),
                    true);
        }
        mockDelegate.mockCheckIfBuildPlanExists(programmingExercise.getProjectKey(), programmingExercise.getTemplateBuildPlanId(), true, false);
        mockDelegate.mockCheckIfBuildPlanExists(programmingExercise.getProjectKey(), programmingExercise.getSolutionBuildPlanId(), true, false);
    }
}
