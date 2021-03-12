package de.tum.in.www1.artemis.util;

import static de.tum.in.www1.artemis.config.Constants.PROGRAMMING_SUBMISSION_RESOURCE_API_PATH;
import static de.tum.in.www1.artemis.domain.enumeration.ExerciseMode.TEAM;
import static de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResource.Endpoints.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.*;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.participation.Participant;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.exception.VersionControlException;
import de.tum.in.www1.artemis.programmingexercise.MockDelegate;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.AuthoritiesConstants;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.CourseService;
import de.tum.in.www1.artemis.service.ParticipationService;
import de.tum.in.www1.artemis.service.TeamService;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.connectors.VersionControlService;
import de.tum.in.www1.artemis.service.connectors.bamboo.dto.BambooBuildResultDTO;
import de.tum.in.www1.artemis.service.programming.ProgrammingLanguageFeature;
import de.tum.in.www1.artemis.service.user.PasswordService;
import de.tum.in.www1.artemis.util.GitUtilService.MockFileRepositoryUrl;
import de.tum.in.www1.artemis.web.rest.ParticipationResource;

/**
 * Note: this class should be independent of the actual VCS and CIS and contains common test logic for both scenarios:
 * 1) Bamboo + Bitbucket
 * 2) Jenkins + Gitlab
 */
@Service
public class ProgrammingExerciseTestService {

    @Autowired
    private DatabaseUtilService database;

    @Autowired
    private RequestUtilService request;

    @Autowired
    private GitService gitService;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private TeamService teamService;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private StaticCodeAnalysisCategoryRepository staticCodeAnalysisCategoryRepository;

    @Autowired
    private ParticipationService participationService;

    @Autowired
    private ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    @Autowired
    private ParticipationRepository participationRepository;

    @Autowired
    @Qualifier("staticCodeAnalysisConfiguration")
    private Map<ProgrammingLanguage, List<StaticCodeAnalysisDefaultCategory>> staticCodeAnalysisDefaultConfigurations;

    @Autowired
    private PasswordService passwordService;

    @Value("${artemis.lti.user-prefix-edx:#{null}}")
    private Optional<String> userPrefixEdx;

    @Autowired
    private CourseService courseService;

    public Course course;

    public ProgrammingExercise exercise;

    public ProgrammingExercise examExercise;

    public static final int numberOfStudents = 2;

    public static final String studentLogin = "student1";

    public static final String teamShortName = "team1";

    public static final String REPOBASEURL = "/api/repository/";

    public static final String PARTICIPATIONBASEURL = "/api/participations/";

    public LocalRepository exerciseRepo = new LocalRepository();

    public LocalRepository testRepo = new LocalRepository();

    public LocalRepository solutionRepo = new LocalRepository();

    public LocalRepository sourceExerciseRepo = new LocalRepository();

    public LocalRepository sourceTestRepo = new LocalRepository();

    public LocalRepository sourceSolutionRepo = new LocalRepository();

    public LocalRepository studentRepo = new LocalRepository();

    public LocalRepository studentTeamRepo = new LocalRepository();

    private VersionControlService versionControlService;

    // not needed right now but maybe in the future
    private ContinuousIntegrationService continuousIntegrationService;

    private MockDelegate mockDelegate;

    public List<User> setupTestUsers(int numberOfStudents, int numberOfTutors, int numberOfInstructors) {
        return database.addUsers(ProgrammingExerciseTestService.numberOfStudents + numberOfStudents, numberOfTutors + 1, numberOfInstructors + 1);
    }

    public void setup(MockDelegate mockDelegate, VersionControlService versionControlService, ContinuousIntegrationService continuousIntegrationService) throws Exception {
        this.mockDelegate = mockDelegate;
        this.versionControlService = versionControlService;
        this.continuousIntegrationService = continuousIntegrationService;

        course = database.addEmptyCourse();
        ExerciseGroup exerciseGroup = database.addExerciseGroupWithExamAndCourse(true);
        examExercise = ModelFactory.generateProgrammingExerciseForExam(exerciseGroup);
        exercise = ModelFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(7), course);

        exerciseRepo.configureRepos("exerciseLocalRepo", "exerciseOriginRepo");
        testRepo.configureRepos("testLocalRepo", "testOriginRepo");
        solutionRepo.configureRepos("solutionLocalRepo", "solutionOriginRepo");
        sourceExerciseRepo.configureRepos("sourceExerciseLocalRepo", "sourceExerciseOriginRepo");
        sourceTestRepo.configureRepos("sourceTestLocalRepo", "sourceTestOriginRepo");
        sourceSolutionRepo.configureRepos("sourceSolutionLocalRepo", "sourceSolutionOriginRepo");
        studentRepo.configureRepos("studentRepo", "studentOriginRepo");
        studentTeamRepo.configureRepos("studentTeamRepo", "studentTeamOriginRepo");

        setupRepositoryMocks(exercise, exerciseRepo, solutionRepo, testRepo);
        setupRepositoryMocksParticipant(exercise, studentLogin, studentRepo);
        setupRepositoryMocksParticipant(exercise, teamShortName, studentTeamRepo);
    }

    public void tearDown() throws IOException {
        database.resetDatabase();
        exerciseRepo.resetLocalRepo();
        testRepo.resetLocalRepo();
        solutionRepo.resetLocalRepo();
        sourceExerciseRepo.resetLocalRepo();
        sourceTestRepo.resetLocalRepo();
        sourceSolutionRepo.resetLocalRepo();
        studentRepo.resetLocalRepo();
        studentTeamRepo.resetLocalRepo();
    }

    public void setupRepositoryMocks(ProgrammingExercise exercise) throws Exception {
        setupRepositoryMocks(exercise, exerciseRepo, solutionRepo, testRepo);
    }

    public void setupRepositoryMocks(ProgrammingExercise exercise, LocalRepository exerciseRepository, LocalRepository solutionRepository, LocalRepository testRepository)
            throws Exception {
        final var projectKey = exercise.getProjectKey();

        final var exerciseRepoName = exercise.generateRepositoryName(RepositoryType.TEMPLATE);
        final var solutionRepoName = exercise.generateRepositoryName(RepositoryType.SOLUTION);
        final var testRepoName = exercise.generateRepositoryName(RepositoryType.TESTS);

        var exerciseRepoTestUrl = new MockFileRepositoryUrl(exerciseRepository.originRepoFile);
        var testRepoTestUrl = new MockFileRepositoryUrl(testRepository.originRepoFile);
        var solutionRepoTestUrl = new MockFileRepositoryUrl(solutionRepository.originRepoFile);

        doReturn(exerciseRepoTestUrl).when(versionControlService).getCloneRepositoryUrl(projectKey, exerciseRepoName);
        doReturn(testRepoTestUrl).when(versionControlService).getCloneRepositoryUrl(projectKey, testRepoName);
        doReturn(solutionRepoTestUrl).when(versionControlService).getCloneRepositoryUrl(projectKey, solutionRepoName);

        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(exerciseRepository.localRepoFile.toPath(), null)).when(gitService)
                .getOrCheckoutRepository(exerciseRepoTestUrl, true);
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(testRepository.localRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(testRepoTestUrl,
                true);
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(solutionRepository.localRepoFile.toPath(), null)).when(gitService)
                .getOrCheckoutRepository(solutionRepoTestUrl, true);
        doNothing().when(gitService).pushSourceToTargetRepo(any(), any());

        // we need separate mocks with VcsRepositoryUrl here because MockFileRepositoryUrl and VcsRepositoryUrl do not seem to be compatible here
        mockDelegate.mockGetRepositorySlugFromRepositoryUrl(exerciseRepoName, exerciseRepoTestUrl);
        mockDelegate.mockGetRepositorySlugFromRepositoryUrl(testRepoName, testRepoTestUrl);
        mockDelegate.mockGetRepositorySlugFromRepositoryUrl(solutionRepoName, solutionRepoTestUrl);

        mockDelegate.mockGetProjectKeyFromRepositoryUrl(projectKey, exerciseRepoTestUrl);
        mockDelegate.mockGetProjectKeyFromRepositoryUrl(projectKey, testRepoTestUrl);
        mockDelegate.mockGetProjectKeyFromRepositoryUrl(projectKey, solutionRepoTestUrl);
        mockDelegate.mockGetProjectKeyFromAnyUrl(projectKey);
    }

    /**
     * can be invoked for teams and students
     */
    public void setupRepositoryMocksParticipant(ProgrammingExercise exercise, String participantName, LocalRepository studentRepo) throws Exception {
        final var projectKey = exercise.getProjectKey();
        String participantRepoName = projectKey.toLowerCase() + "-" + participantName;
        var participantRepoTestUrl = getMockFileRepositoryUrl(studentRepo);
        doReturn(participantRepoTestUrl).when(versionControlService).getCloneRepositoryUrl(projectKey, participantRepoName);
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(studentRepo.localRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(participantRepoTestUrl,
                true);
        doNothing().when(gitService).pushSourceToTargetRepo(any(), any());
        mockDelegate.mockGetRepositorySlugFromRepositoryUrl(participantRepoName, participantRepoTestUrl);
        mockDelegate.mockGetProjectKeyFromRepositoryUrl(projectKey, participantRepoTestUrl);
    }

    public MockFileRepositoryUrl getMockFileRepositoryUrl(LocalRepository repository) throws MalformedURLException {
        return new MockFileRepositoryUrl(repository.originRepoFile);
    }

    // TEST
    public void createProgrammingExercise_sequential_validExercise_created() throws Exception {
        exercise.setSequentialTestRuns(true);
        mockDelegate.mockConnectorRequestsForSetup(exercise);
        validateProgrammingExercise(request.postWithResponseBody(ROOT + SETUP, exercise, ProgrammingExercise.class, HttpStatus.CREATED));
    }

    // TEST
    public void createProgrammingExercise_mode_validExercise_created(ExerciseMode mode) throws Exception {
        exercise.setMode(mode);
        mockDelegate.mockConnectorRequestsForSetup(exercise);
        validateProgrammingExercise(request.postWithResponseBody(ROOT + SETUP, exercise, ProgrammingExercise.class, HttpStatus.CREATED));
    }

    // TEST
    public void createProgrammingExercise_programmingLanguage_validExercise_created(ProgrammingLanguage language, ProgrammingLanguageFeature programmingLanguageFeature)
            throws Exception {
        exercise.setProgrammingLanguage(language);
        if (language == ProgrammingLanguage.SWIFT) {
            exercise.setPackageName("swiftTest");
        }
        exercise.setProjectType(programmingLanguageFeature.getProjectTypes().size() > 0 ? programmingLanguageFeature.getProjectTypes().get(0) : null);
        mockDelegate.mockConnectorRequestsForSetup(exercise);
        validateProgrammingExercise(request.postWithResponseBody(ROOT + SETUP, exercise, ProgrammingExercise.class, HttpStatus.CREATED));
    }

    // TEST
    public void createProgrammingExercise_validExercise_bonusPointsIsNull() throws Exception {
        exercise.setBonusPoints(null);
        mockDelegate.mockConnectorRequestsForSetup(exercise);
        var generatedExercise = request.postWithResponseBody(ROOT + SETUP, exercise, ProgrammingExercise.class);
        var savedExercise = programmingExerciseRepository.findById(generatedExercise.getId()).get();
        assertThat(generatedExercise.getBonusPoints()).isEqualTo(0D);
        assertThat(savedExercise.getBonusPoints()).isEqualTo(0D);
    }

    // TEST
    public void createProgrammingExercise_validExercise_withStaticCodeAnalysis(ProgrammingLanguage language, ProgrammingLanguageFeature programmingLanguageFeature)
            throws Exception {
        exercise.setStaticCodeAnalysisEnabled(true);
        exercise.setProgrammingLanguage(language);
        if (language == ProgrammingLanguage.SWIFT) {
            exercise.setPackageName("swiftTest");
        }
        exercise.setProjectType(programmingLanguageFeature.getProjectTypes().size() > 0 ? programmingLanguageFeature.getProjectTypes().get(0) : null);
        mockDelegate.mockConnectorRequestsForSetup(exercise);
        var generatedExercise = request.postWithResponseBody(ROOT + SETUP, exercise, ProgrammingExercise.class);

        exercise.setId(generatedExercise.getId());
        assertThat(exercise).isEqualTo(generatedExercise);
        var staticCodeAnalysisCategories = staticCodeAnalysisCategoryRepository.findByExerciseId(generatedExercise.getId());
        assertThat(staticCodeAnalysisCategories).usingRecursiveFieldByFieldElementComparator().usingElementComparatorIgnoringFields("id", "exercise")
                .isEqualTo(staticCodeAnalysisDefaultConfigurations.get(exercise.getProgrammingLanguage()));
        staticCodeAnalysisDefaultConfigurations.get(exercise.getProgrammingLanguage()).forEach(config -> config.getCategoryMappings().forEach(mapping -> {
            assertThat(mapping.getTool()).isNotNull();
            assertThat(mapping.getCategory()).isNotNull();
        }));
    }

    // TEST
    public void createProgrammingExerciseForExam_validExercise_created() throws Exception {
        setupRepositoryMocks(examExercise, exerciseRepo, solutionRepo, testRepo);

        mockDelegate.mockConnectorRequestsForSetup(examExercise);
        final var generatedExercise = request.postWithResponseBody(ROOT + SETUP, examExercise, ProgrammingExercise.class, HttpStatus.CREATED);

        examExercise.setId(generatedExercise.getId());
        assertThat(examExercise).isEqualTo(generatedExercise);
        assertThat(programmingExerciseRepository.count()).isEqualTo(1);
    }

    // TEST
    public void importExercise_created(ProgrammingLanguage programmingLanguage, boolean recreateBuildPlans) throws Exception {
        boolean staticCodeAnalysisEnabled = programmingLanguage == ProgrammingLanguage.JAVA || programmingLanguage == ProgrammingLanguage.SWIFT;
        // Setup exercises for import
        ProgrammingExercise sourceExercise = database.addCourseWithOneProgrammingExerciseAndStaticCodeAnalysisCategories(programmingLanguage);
        sourceExercise.setStaticCodeAnalysisEnabled(staticCodeAnalysisEnabled);
        database.addTestCasesToProgrammingExercise(sourceExercise);
        database.addHintsToExercise(sourceExercise);
        database.addHintsToProblemStatement(sourceExercise);
        sourceExercise = database.loadProgrammingExerciseWithEagerReferences(sourceExercise);
        ProgrammingExercise exerciseToBeImported = ModelFactory.generateToBeImportedProgrammingExercise("ImportTitle", "imported", sourceExercise, database.addEmptyCourse());
        exerciseToBeImported.setStaticCodeAnalysisEnabled(staticCodeAnalysisEnabled);
        // Mock requests
        mockDelegate.mockConnectorRequestsForImport(sourceExercise, exerciseToBeImported, recreateBuildPlans);
        setupRepositoryMocks(sourceExercise, sourceExerciseRepo, sourceSolutionRepo, sourceTestRepo);
        setupRepositoryMocks(exerciseToBeImported, exerciseRepo, solutionRepo, testRepo);

        // Create request parameters
        var params = new LinkedMultiValueMap<String, String>();
        params.add("recreateBuildPlans", String.valueOf(recreateBuildPlans));

        // Import the exercise and load all referenced entities
        var importedExercise = request.postWithResponseBody(ROOT + IMPORT.replace("{sourceExerciseId}", sourceExercise.getId().toString()), exerciseToBeImported,
                ProgrammingExercise.class, params, HttpStatus.OK);
        SecurityUtils.setAuthorizationObject();
        importedExercise = database.loadProgrammingExerciseWithEagerReferences(importedExercise);

        if (staticCodeAnalysisEnabled) {
            // Assert correct creation of static code analysis categories
            var importedCategoryIds = importedExercise.getStaticCodeAnalysisCategories().stream().map(StaticCodeAnalysisCategory::getId).collect(Collectors.toList());
            var sourceCategoryIds = sourceExercise.getStaticCodeAnalysisCategories().stream().map(StaticCodeAnalysisCategory::getId).collect(Collectors.toList());
            assertThat(importedCategoryIds).doesNotContainAnyElementsOf(sourceCategoryIds);
            assertThat(importedExercise.getStaticCodeAnalysisCategories()).usingRecursiveFieldByFieldElementComparator().usingElementComparatorIgnoringFields("id", "exercise")
                    .containsExactlyInAnyOrderElementsOf(sourceExercise.getStaticCodeAnalysisCategories());
        }

        // Assert correct creation of test cases
        var importedTestCaseIds = importedExercise.getTestCases().stream().map(ProgrammingExerciseTestCase::getId).collect(Collectors.toList());
        var sourceTestCaseIds = sourceExercise.getTestCases().stream().map(ProgrammingExerciseTestCase::getId).collect(Collectors.toList());
        assertThat(importedTestCaseIds).doesNotContainAnyElementsOf(sourceTestCaseIds);
        assertThat(importedExercise.getTestCases()).usingRecursiveFieldByFieldElementComparator().usingElementComparatorIgnoringFields("id", "exercise")
                .containsExactlyInAnyOrderElementsOf(sourceExercise.getTestCases());

        // Assert correct creation of hints
        var importedHintIds = importedExercise.getExerciseHints().stream().map(ExerciseHint::getId).collect(Collectors.toList());
        var sourceHintIds = sourceExercise.getExerciseHints().stream().map(ExerciseHint::getId).collect(Collectors.toList());
        assertThat(importedHintIds).doesNotContainAnyElementsOf(sourceHintIds);
        assertThat(importedExercise.getExerciseHints()).usingRecursiveFieldByFieldElementComparator().usingElementComparatorIgnoringFields("id", "exercise")
                .containsExactlyInAnyOrderElementsOf(sourceExercise.getExerciseHints());
    }

    // TEST
    public void importExercise_enablePlanFails() throws Exception {
        // Setup exercises for import
        ProgrammingExercise sourceExercise = database.addCourseWithOneProgrammingExerciseAndStaticCodeAnalysisCategories();
        // database.addTestCasesToProgrammingExercise(sourceExercise);
        sourceExercise = database.loadProgrammingExerciseWithEagerReferences(sourceExercise);
        ProgrammingExercise exerciseToBeImported = ModelFactory.generateToBeImportedProgrammingExercise("ImportTitle", "imported", sourceExercise, database.addEmptyCourse());

        // Mock requests
        mockDelegate.mockImportProgrammingExerciseWithFailingEnablePlan(sourceExercise, exerciseToBeImported, true, true);
        setupRepositoryMocks(sourceExercise, sourceExerciseRepo, sourceSolutionRepo, sourceTestRepo);
        setupRepositoryMocks(exerciseToBeImported, exerciseRepo, solutionRepo, testRepo);

        // Create request
        var params = new LinkedMultiValueMap<String, String>();
        params.add("recreateBuildPlans", "false");
        params.add("updateTemplate", "true");
        exerciseToBeImported = request.postWithResponseBody(ROOT + IMPORT.replace("{sourceExerciseId}", sourceExercise.getId().toString()), exerciseToBeImported,
                ProgrammingExercise.class, params, HttpStatus.OK);

        assertThat(exerciseToBeImported).isNotNull();
    }

    // TEST
    public void importExercise_planDoesntExist() throws Exception {
        // Setup exercises for import
        ProgrammingExercise sourceExercise = database.addCourseWithOneProgrammingExerciseAndStaticCodeAnalysisCategories();
        // database.addTestCasesToProgrammingExercise(sourceExercise);
        sourceExercise = database.loadProgrammingExerciseWithEagerReferences(sourceExercise);
        ProgrammingExercise exerciseToBeImported = ModelFactory.generateToBeImportedProgrammingExercise("ImportTitle", "imported", sourceExercise, database.addEmptyCourse());

        // Mock requests
        mockDelegate.mockImportProgrammingExerciseWithFailingEnablePlan(sourceExercise, exerciseToBeImported, false, false);
        setupRepositoryMocks(sourceExercise, sourceExerciseRepo, sourceSolutionRepo, sourceTestRepo);
        setupRepositoryMocks(exerciseToBeImported, exerciseRepo, solutionRepo, testRepo);

        // Create request
        var params = new LinkedMultiValueMap<String, String>();
        params.add("recreateBuildPlans", "false");
        params.add("updateTemplate", "true");
        exerciseToBeImported = request.postWithResponseBody(ROOT + IMPORT.replace("{sourceExerciseId}", sourceExercise.getId().toString()), exerciseToBeImported,
                ProgrammingExercise.class, params, HttpStatus.OK);

        assertThat(exerciseToBeImported).isNotNull();
    }

    // TEST
    public void testImportProgrammingExercise_team_modeChange() throws Exception {
        // Setup exercises for import
        ProgrammingExercise sourceExercise = database.addCourseWithOneProgrammingExerciseAndStaticCodeAnalysisCategories();
        sourceExercise.setMode(ExerciseMode.INDIVIDUAL);
        database.addTestCasesToProgrammingExercise(sourceExercise);
        database.addHintsToExercise(sourceExercise);
        database.addHintsToProblemStatement(sourceExercise);
        sourceExercise = database.loadProgrammingExerciseWithEagerReferences(sourceExercise);
        sourceExercise.setCourse(sourceExercise.getCourseViaExerciseGroupOrCourseMember());
        programmingExerciseRepository.save(sourceExercise);
        database.loadProgrammingExerciseWithEagerReferences(sourceExercise);

        ProgrammingExercise exerciseToBeImported = ModelFactory.generateToBeImportedProgrammingExercise("ImportTitle", "imported", sourceExercise, database.addEmptyCourse());
        exerciseToBeImported.setMode(TEAM);
        var teamAssignmentConfig = new TeamAssignmentConfig();
        teamAssignmentConfig.setExercise(exerciseToBeImported);
        teamAssignmentConfig.setMinTeamSize(1);
        teamAssignmentConfig.setMaxTeamSize(10);
        exerciseToBeImported.setTeamAssignmentConfig(teamAssignmentConfig);

        // Mock requests
        mockDelegate.mockConnectorRequestsForImport(sourceExercise, exerciseToBeImported, false);
        setupRepositoryMocks(sourceExercise, sourceExerciseRepo, sourceSolutionRepo, sourceTestRepo);
        setupRepositoryMocks(exerciseToBeImported, exerciseRepo, solutionRepo, testRepo);

        exerciseToBeImported = request.postWithResponseBody(ROOT + IMPORT.replace("{sourceExerciseId}", sourceExercise.getId().toString()), exerciseToBeImported,
                ProgrammingExercise.class, HttpStatus.OK);

        SecurityUtils.setAuthorizationObject();
        assertEquals(TEAM, exerciseToBeImported.getMode());
        assertEquals(teamAssignmentConfig.getMinTeamSize(), exerciseToBeImported.getTeamAssignmentConfig().getMinTeamSize());
        assertEquals(teamAssignmentConfig.getMaxTeamSize(), exerciseToBeImported.getTeamAssignmentConfig().getMaxTeamSize());
        assertEquals(0, teamService.findAllByExerciseIdWithEagerStudents(exerciseToBeImported, null).size());

        sourceExercise = database.loadProgrammingExerciseWithEagerReferences(sourceExercise);
        assertEquals(ExerciseMode.INDIVIDUAL, sourceExercise.getMode());
        assertNull(sourceExercise.getTeamAssignmentConfig());
        assertEquals(0, teamService.findAllByExerciseIdWithEagerStudents(sourceExercise, null).size());
    }

    // TEST
    public void testImportProgrammingExercise_individual_modeChange() throws Exception {
        // Setup exercises for import
        ProgrammingExercise sourceExercise = database.addCourseWithOneProgrammingExerciseAndStaticCodeAnalysisCategories();
        sourceExercise.setMode(TEAM);
        database.addTestCasesToProgrammingExercise(sourceExercise);
        database.addHintsToExercise(sourceExercise);
        database.addHintsToProblemStatement(sourceExercise);
        sourceExercise = database.loadProgrammingExerciseWithEagerReferences(sourceExercise);
        var teamAssignmentConfig = new TeamAssignmentConfig();
        teamAssignmentConfig.setExercise(sourceExercise);
        teamAssignmentConfig.setMinTeamSize(1);
        teamAssignmentConfig.setMaxTeamSize(10);
        sourceExercise.setTeamAssignmentConfig(teamAssignmentConfig);
        sourceExercise.setCourse(sourceExercise.getCourseViaExerciseGroupOrCourseMember());
        programmingExerciseRepository.save(sourceExercise);
        teamService.save(sourceExercise, new Team());
        database.loadProgrammingExerciseWithEagerReferences(sourceExercise);

        ProgrammingExercise exerciseToBeImported = ModelFactory.generateToBeImportedProgrammingExercise("ImportTitle", "imported", sourceExercise, database.addEmptyCourse());
        exerciseToBeImported.setMode(ExerciseMode.INDIVIDUAL);

        // Mock requests
        mockDelegate.mockConnectorRequestsForImport(sourceExercise, exerciseToBeImported, false);
        setupRepositoryMocks(sourceExercise, sourceExerciseRepo, sourceSolutionRepo, sourceTestRepo);
        setupRepositoryMocks(exerciseToBeImported, exerciseRepo, solutionRepo, testRepo);

        exerciseToBeImported = request.postWithResponseBody(ROOT + IMPORT.replace("{sourceExerciseId}", sourceExercise.getId().toString()), exerciseToBeImported,
                ProgrammingExercise.class, HttpStatus.OK);

        SecurityUtils.setAuthorizationObject();
        assertEquals(ExerciseMode.INDIVIDUAL, exerciseToBeImported.getMode());
        assertNull(exerciseToBeImported.getTeamAssignmentConfig());
        assertEquals(0, teamService.findAllByExerciseIdWithEagerStudents(exerciseToBeImported, null).size());

        sourceExercise = database.loadProgrammingExerciseWithEagerReferences(sourceExercise);
        assertEquals(TEAM, sourceExercise.getMode());
        assertEquals(1, teamService.findAllByExerciseIdWithEagerStudents(sourceExercise, null).size());
    }

    // TEST
    public void testImportProgrammingExercise_scaChange_deactivated() throws Exception {
        // Setup exercises for import
        ProgrammingExercise sourceExercise = database.addCourseWithOneProgrammingExerciseAndStaticCodeAnalysisCategories();
        database.addTestCasesToProgrammingExercise(sourceExercise);
        sourceExercise = database.loadProgrammingExerciseWithEagerReferences(sourceExercise);
        ProgrammingExercise exerciseToBeImported = ModelFactory.generateToBeImportedProgrammingExercise("ImportTitle", "imported", sourceExercise, database.addEmptyCourse());

        // Mock requests
        mockDelegate.mockConnectorRequestsForImport(sourceExercise, exerciseToBeImported, true);
        setupRepositoryMocks(sourceExercise, sourceExerciseRepo, sourceSolutionRepo, sourceTestRepo);
        setupRepositoryMocks(exerciseToBeImported, exerciseRepo, solutionRepo, testRepo);

        // Create request
        var params = new LinkedMultiValueMap<String, String>();
        params.add("recreateBuildPlans", "true");
        params.add("updateTemplate", "true");
        exerciseToBeImported = request.postWithResponseBody(ROOT + IMPORT.replace("{sourceExerciseId}", sourceExercise.getId().toString()), exerciseToBeImported,
                ProgrammingExercise.class, params, HttpStatus.OK);

        // Assertions
        assertThat(!exerciseToBeImported.isStaticCodeAnalysisEnabled());
        assertThat(exerciseToBeImported.getStaticCodeAnalysisCategories()).isEmpty();
        assertThat(exerciseToBeImported.getMaxStaticCodeAnalysisPenalty()).isNull();
    }

    public void testImportProgrammingExercise_scaChange_activated() throws Exception {
        // Setup exercises for import
        ProgrammingExercise sourceExercise = (ProgrammingExercise) database.addCourseWithOneProgrammingExercise(false).getExercises().iterator().next();
        database.addTestCasesToProgrammingExercise(sourceExercise);
        sourceExercise = database.loadProgrammingExerciseWithEagerReferences(sourceExercise);
        ProgrammingExercise exerciseToBeImported = ModelFactory.generateToBeImportedProgrammingExercise("ImportTitle", "imported", sourceExercise, database.addEmptyCourse());
        exerciseToBeImported.setStaticCodeAnalysisEnabled(true);
        exerciseToBeImported.setMaxStaticCodeAnalysisPenalty(80);

        // Mock requests
        mockDelegate.mockConnectorRequestsForImport(sourceExercise, exerciseToBeImported, true);
        setupRepositoryMocks(sourceExercise, sourceExerciseRepo, sourceSolutionRepo, sourceTestRepo);
        setupRepositoryMocks(exerciseToBeImported, exerciseRepo, solutionRepo, testRepo);

        // Create request
        var params = new LinkedMultiValueMap<String, String>();
        params.add("recreateBuildPlans", "true");
        params.add("updateTemplate", "true");
        exerciseToBeImported = request.postWithResponseBody(ROOT + IMPORT.replace("{sourceExerciseId}", sourceExercise.getId().toString()), exerciseToBeImported,
                ProgrammingExercise.class, params, HttpStatus.OK);

        // Assertions
        SecurityUtils.setAuthorizationObject();
        var staticCodeAnalysisCategories = staticCodeAnalysisCategoryRepository.findByExerciseId(exerciseToBeImported.getId());
        assertThat(exerciseToBeImported.isStaticCodeAnalysisEnabled());
        assertThat(staticCodeAnalysisCategories).usingRecursiveFieldByFieldElementComparator().usingElementComparatorOnFields("name", "state", "penalty", "maxPenalty")
                .isEqualTo(staticCodeAnalysisDefaultConfigurations.get(exercise.getProgrammingLanguage()));
        assertThat(exerciseToBeImported.getMaxStaticCodeAnalysisPenalty()).isEqualTo(80);
    }

    // TEST
    public void createProgrammingExercise_validExercise_structureOracle() throws Exception {
        structureOracle(exercise);
    }

    // TEST
    public void createProgrammingExercise_noTutors_created() throws Exception {
        course.setTeachingAssistantGroupName(null);
        courseRepository.save(course);
        mockDelegate.mockConnectorRequestsForSetup(exercise);
        final var generatedExercise = request.postWithResponseBody(ROOT + SETUP, exercise, ProgrammingExercise.class, HttpStatus.CREATED);
        validateProgrammingExercise(generatedExercise);
    }

    // TEST
    public void startProgrammingExercise_correctInitializationState(ExerciseMode exerciseMode) throws Exception {
        final Course course = setupCourseWithProgrammingExercise(exerciseMode);
        var user = userRepo.findOneByLogin(studentLogin).orElseThrow();
        Participant participant = user;
        if (exerciseMode == TEAM) {
            participant = setupTeam(user);
        }
        mockDelegate.mockConnectorRequestsForStartParticipation(exercise, participant.getParticipantIdentifier(), participant.getParticipants(), true, HttpStatus.CREATED);
        final var path = ParticipationResource.Endpoints.ROOT + ParticipationResource.Endpoints.START_PARTICIPATION.replace("{courseId}", String.valueOf(course.getId()))
                .replace("{exerciseId}", String.valueOf(exercise.getId()));
        final var participation = request.postWithResponseBody(path, null, ProgrammingExerciseStudentParticipation.class, HttpStatus.CREATED);
        assertThat(participation.getInitializationState()).as("Participation should be initialized").isEqualTo(InitializationState.INITIALIZED);
    }

    // TEST
    public void startProgrammingExerciseAutomaticallyCreateEdxUser_correctInitializationState() throws Exception {
        var user = userRepo.findOneByLogin(studentLogin).orElseThrow();
        user.setLogin("edx_student1");
        user = userRepo.save(user);

        final Course course = setupCourseWithProgrammingExercise(ExerciseMode.INDIVIDUAL);
        Participant participant = user;

        mockDelegate.mockConnectorRequestsForStartParticipation(exercise, participant.getParticipantIdentifier(), participant.getParticipants(), false, HttpStatus.CREATED);

        final var path = ParticipationResource.Endpoints.ROOT + ParticipationResource.Endpoints.START_PARTICIPATION.replace("{courseId}", String.valueOf(course.getId()))
                .replace("{exerciseId}", String.valueOf(exercise.getId()));
        final var participation = request.postWithResponseBody(path, null, ProgrammingExerciseStudentParticipation.class, HttpStatus.CREATED);
        assertThat(participation.getInitializationState()).as("Participation should be initialized").isEqualTo(InitializationState.INITIALIZED);
    }

    private Course setupCourseWithProgrammingExercise(ExerciseMode exerciseMode) {
        final var course = exercise.getCourseViaExerciseGroupOrCourseMember();
        exercise.setMode(exerciseMode);
        programmingExerciseRepository.save(exercise);
        database.addTemplateParticipationForProgrammingExercise(exercise);
        database.addSolutionParticipationForProgrammingExercise(exercise);
        return course;
    }

    // TEST
    public void resumeProgrammingExercise_doesNotExist(ExerciseMode exerciseMode) throws Exception {
        final Course course = setupCourseWithProgrammingExercise(exerciseMode);
        request.putWithResponseBody("/api/courses/" + course.getId() + "/exercises/" + exercise.getId() + "/resume-programming-participation", null,
                ProgrammingExerciseStudentParticipation.class, HttpStatus.NOT_FOUND);
    }

    // TEST
    public void resumeProgrammingExercise_correctInitializationState(ExerciseMode exerciseMode) throws Exception {
        var participation = createStudentParticipationWithSubmission(exerciseMode);

        // These will be updated when the participation is resumed.
        participation.setInitializationState(InitializationState.INACTIVE);
        participation.setBuildPlanId(null);
        programmingExerciseStudentParticipationRepository.saveAndFlush(participation);

        var participant = participation.getParticipant();
        mockDelegate.mockConnectorRequestsForResumeParticipation(exercise, participant.getParticipantIdentifier(), participant.getParticipants(), true);

        participation = request.putWithResponseBody("/api/courses/" + course.getId() + "/exercises/" + exercise.getId() + "/resume-programming-participation", null,
                ProgrammingExerciseStudentParticipation.class, HttpStatus.OK);

        assertThat(participation.getInitializationState()).as("Participation should be initialized").isEqualTo(InitializationState.INITIALIZED);
        assertThat(participation.getBuildPlanId()).as("Build Plan Id should be set")
                .isEqualTo(exercise.getProjectKey().toUpperCase() + "-" + participant.getParticipantIdentifier().toUpperCase());
    }

    // TEST
    public void resumeProgrammingExerciseByPushingIntoRepo_correctInitializationState(ExerciseMode exerciseMode, Object body) throws Exception {
        var participation = createStudentParticipationWithSubmission(exerciseMode);
        var participant = participation.getParticipant();

        mockDelegate.mockConnectorRequestsForResumeParticipation(exercise, participant.getParticipantIdentifier(), participant.getParticipants(), true);
        mockDelegate.mockNotifyPush(participation);

        // These will be updated when pushing a commit
        participation.setInitializationState(InitializationState.INACTIVE);
        participation.setBuildPlanId(null);
        programmingExerciseStudentParticipationRepository.saveAndFlush(participation);

        // Mock REST Call from the VCS for a new programming submission (happens as part of the webhook after pushing code to git)
        request.postWithoutLocation(PROGRAMMING_SUBMISSION_RESOURCE_API_PATH + participation.getId(), body, HttpStatus.OK, new HttpHeaders());

        // Fetch updated participation and assert
        ProgrammingExerciseStudentParticipation updatedParticipation = (ProgrammingExerciseStudentParticipation) participationRepository.findByIdElseThrow(participation.getId());
        assertThat(updatedParticipation.getInitializationState()).as("Participation should be initialized").isEqualTo(InitializationState.INITIALIZED);
        assertThat(updatedParticipation.getBuildPlanId()).as("Build Plan Id should be set")
                .isEqualTo(exercise.getProjectKey().toUpperCase() + "-" + participant.getParticipantIdentifier().toUpperCase());
    }

    // TEST
    public void resumeProgrammingExerciseByTriggeringBuild_correctInitializationState(ExerciseMode exerciseMode, SubmissionType submissionType) throws Exception {
        var participation = createStudentParticipationWithSubmission(exerciseMode);
        var participant = participation.getParticipant();

        mockDelegate.mockTriggerParticipationBuild(participation);
        // We need to mock the call again because we are triggering the build twice in order to verify that the submission isn't re-created
        mockDelegate.mockTriggerParticipationBuild(participation);

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
        var submissions = database.submissionRepository.findAll();
        assertThat(submissions.size()).isEqualTo(1);
    }

    // TEST
    public void resumeProgrammingExerciseByTriggeringFailedBuild_correctInitializationState(ExerciseMode exerciseMode, boolean buildPlanExists) throws Exception {
        var participation = createStudentParticipationWithSubmission(exerciseMode);
        var participant = participation.getParticipant();

        mockDelegate.mockTriggerFailedBuild(participation);
        // We need to mock the call again because we are triggering the build twice in order to verify that the submission isn't re-created
        mockDelegate.mockTriggerFailedBuild(participation);

        // These will be updated triggering a failed build
        participation.setInitializationState(InitializationState.INACTIVE);
        participation.setBuildPlanId(!buildPlanExists ? null : participation.getBuildPlanId());
        programmingExerciseStudentParticipationRepository.saveAndFlush(participation);

        if (!buildPlanExists) {
            mockDelegate.mockConnectorRequestsForResumeParticipation(exercise, participant.getParticipantIdentifier(), participant.getParticipants(), true);
            participation = request.putWithResponseBody("/api/courses/" + course.getId() + "/exercises/" + exercise.getId() + "/resume-programming-participation", null,
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
        var submissions = database.submissionRepository.findAll();
        assertThat(submissions.size()).isEqualTo(1);
    }

    // TEST
    public void resumeProgrammingExerciseByTriggeringInstructorBuild_correctInitializationState(ExerciseMode exerciseMode) throws Exception {
        var participation = createStudentParticipationWithSubmission(exerciseMode);
        var participant = participation.getParticipant();

        mockDelegate.mockTriggerInstructorBuildAll(participation);
        // We need to mock the call again because we are triggering the build twice in order to verify that the submission isn't re-created
        mockDelegate.mockTriggerInstructorBuildAll(participation);

        // These will be updated triggering a failed build
        participation.setInitializationState(InitializationState.INACTIVE);
        participation.setBuildPlanId(null);
        programmingExerciseStudentParticipationRepository.saveAndFlush(participation);

        var url = "/api/programming-exercises/" + exercise.getId() + "/trigger-instructor-build-all";
        request.postWithoutLocation(url, null, HttpStatus.OK, new HttpHeaders());
        await().until(() -> programmingExerciseRepository.findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(exercise.getId()).isPresent());

        // Fetch updated participation and assert
        ProgrammingExerciseStudentParticipation updatedParticipation = (ProgrammingExerciseStudentParticipation) participationRepository.findByIdElseThrow(participation.getId());
        assertThat(updatedParticipation.getInitializationState()).as("Participation should be initialized").isEqualTo(InitializationState.INITIALIZED);
        assertThat(updatedParticipation.getBuildPlanId()).as("Build Plan Id should be set")
                .isEqualTo(exercise.getProjectKey().toUpperCase() + "-" + participant.getParticipantIdentifier().toUpperCase());

        // Trigger the build again and make sure no new submission is created
        request.postWithoutLocation(url, null, HttpStatus.OK, new HttpHeaders());
        var submissions = database.submissionRepository.findAll();
        assertThat(submissions.size()).isEqualTo(1);
    }

    // Test
    public void exportInstructorRepositories_shouldReturnFile() throws Exception {
        var zip = exportInstructorRepository("TEMPLATE", sourceExerciseRepo.localRepoFile.toPath(), HttpStatus.OK);
        assertThat(zip).isNotNull();

        zip = exportInstructorRepository("SOLUTION", sourceSolutionRepo.localRepoFile.toPath(), HttpStatus.OK);
        assertThat(zip).isNotNull();

        zip = exportInstructorRepository("TESTS", sourceTestRepo.localRepoFile.toPath(), HttpStatus.OK);
        assertThat(zip).isNotNull();

    }

    // Test
    public void exportInstructorRepositories_forbidden() throws Exception {
        exportInstructorRepository("TEMPLATE", sourceExerciseRepo.localRepoFile.toPath(), HttpStatus.FORBIDDEN);
        exportInstructorRepository("SOLUTION", sourceSolutionRepo.localRepoFile.toPath(), HttpStatus.FORBIDDEN);
        exportInstructorRepository("TESTS", sourceTestRepo.localRepoFile.toPath(), HttpStatus.FORBIDDEN);
    }

    private String exportInstructorRepository(String repositoryType, Path localPathToRepository, HttpStatus expectedStatus) throws Exception {
        exercise = programmingExerciseRepository.save(exercise);
        exercise = database.addTemplateParticipationForProgrammingExercise(exercise);
        exercise = database.addSolutionParticipationForProgrammingExercise(exercise);
        exercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationById(exercise.getId()).get();

        var vcsUrl = exercise.getRepositoryURL(RepositoryType.valueOf(repositoryType));
        var repository = gitService.getExistingCheckedOutRepositoryByLocalPath(localPathToRepository, null);
        doReturn(repository).when(gitService).getOrCheckoutRepository(eq(vcsUrl), anyString(), anyBoolean());

        var url = "/api/programming-exercises/" + exercise.getId() + "/export-instructor-repository/" + repositoryType;
        // return request.postWithResponseBodyFile(url, null, expectedStatus);
        return request.get(url, expectedStatus, String.class);
    }

    // Test
    public void testArchiveCourseWithProgrammingExercise() throws Exception {
        course.setEndDate(ZonedDateTime.now().minusMinutes(4));
        course.setCourseArchivePath(null);
        course.setExercises(Set.of(exercise));
        courseRepository.save(course);

        exercise = programmingExerciseRepository.save(exercise);
        exercise = database.addTemplateParticipationForProgrammingExercise(exercise);
        exercise = database.addSolutionParticipationForProgrammingExercise(exercise);
        database.addTestCasesToProgrammingExercise(exercise);

        exercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationById(exercise.getId()).get();
        var participation = database.addStudentParticipationForProgrammingExercise(exercise, studentLogin);

        // Mock student repo
        var studentRepository = gitService.getExistingCheckedOutRepositoryByLocalPath(studentRepo.localRepoFile.toPath(), null);
        doReturn(studentRepository).when(gitService).getOrCheckoutRepository(eq(participation.getVcsRepositoryUrl()), anyString(), anyBoolean());

        // Mock template repo
        var templateRepository = gitService.getExistingCheckedOutRepositoryByLocalPath(sourceExerciseRepo.localRepoFile.toPath(), null);
        doReturn(templateRepository).when(gitService).getOrCheckoutRepository(eq(exercise.getRepositoryURL(RepositoryType.TEMPLATE)), anyString(), anyBoolean());

        // Mock solution repo
        var solutionRepository = gitService.getExistingCheckedOutRepositoryByLocalPath(sourceSolutionRepo.localRepoFile.toPath(), null);
        doReturn(solutionRepository).when(gitService).getOrCheckoutRepository(eq(exercise.getRepositoryURL(RepositoryType.SOLUTION)), anyString(), anyBoolean());

        // Mock tests repo
        var testsRepository = gitService.getExistingCheckedOutRepositoryByLocalPath(sourceTestRepo.localRepoFile.toPath(), null);
        doReturn(testsRepository).when(gitService).getOrCheckoutRepository(eq(exercise.getRepositoryURL(RepositoryType.TESTS)), anyString(), anyBoolean());

        request.put("/api/courses/" + course.getId() + "/archive", null, HttpStatus.OK);
        await().until(() -> courseRepository.findById(course.getId()).get().getCourseArchivePath() != null);

        var updatedCourse = courseRepository.findByIdElseThrow(course.getId());
        assertThat(updatedCourse.getCourseArchivePath()).isNotEmpty();
    }

    private ProgrammingExerciseStudentParticipation createStudentParticipationWithSubmission(ExerciseMode exerciseMode) throws Exception {
        setupCourseWithProgrammingExercise(exerciseMode);
        User user = userRepo.findOneByLogin(ProgrammingExerciseTestService.studentLogin).orElseThrow();

        ProgrammingExerciseStudentParticipation participation;
        if (exerciseMode == TEAM) {
            var team = setupTeam(user);
            participation = database.addTeamParticipationForProgrammingExercise(exercise, team);
            // prepare for the mock scenario, so that the empty commit will work properly
            participation.setRepositoryUrl(getMockFileRepositoryUrl(studentTeamRepo).getURL().toString());
        }
        else {
            participation = database.addStudentParticipationForProgrammingExercise(exercise, user.getParticipantIdentifier());
            // prepare for the mock scenario, so that the empty commit will work properly
            participation.setRepositoryUrl(getMockFileRepositoryUrl(studentRepo).getURL().toString());
        }

        ProgrammingSubmission submission = new ProgrammingSubmission();
        submission.setSubmissionDate(ZonedDateTime.now().minusMinutes(4));
        submission.setSubmitted(true);
        submission.setCommitHash(TestConstants.COMMIT_HASH_STRING);
        submission.setType(SubmissionType.MANUAL);
        database.addSubmission(participation, submission);

        return participation;
    }

    @NotNull
    private Team setupTeam(User user) {
        // create a team for the user (necessary condition before starting an exercise)
        Set<User> students = Set.of(user);
        Team team = new Team().name("Team 1").shortName(teamShortName).exercise(exercise).students(students);
        team = teamService.save(exercise, team);
        assertThat(team.getStudents()).as("Student was correctly added to team").hasSize(1);
        return team;
    }

    // TEST
    public void startProgrammingExerciseStudentSubmissionFailedWithBuildlog() throws Exception {
        final var course = getCourseForExercise();
        User user = userRepo.findOneByLogin(studentLogin).orElseThrow();
        mockDelegate.mockConnectorRequestsForStartParticipation(exercise, user.getParticipantIdentifier(), Set.of(user), true, HttpStatus.CREATED);
        final var participation = createUserParticipation(course);

        // create a submission which fails
        database.createProgrammingSubmission(participation, true);

        mockDelegate.resetMockProvider();

        var log1 = new BambooBuildResultDTO.BambooBuildLogEntryDTO(ZonedDateTime.now(), "java.lang.AssertionError: BubbleSort does not sort correctly");
        var log2 = new BambooBuildResultDTO.BambooBuildLogEntryDTO(ZonedDateTime.now(), "[INFO] Test");
        var log3 = new BambooBuildResultDTO.BambooBuildLogEntryDTO(ZonedDateTime.now(), "[WARNING]");
        var log4 = new BambooBuildResultDTO.BambooBuildLogEntryDTO(ZonedDateTime.now(), "[ERROR] [Help 1]");
        var log5 = new BambooBuildResultDTO.BambooBuildLogEntryDTO(ZonedDateTime.now(), "[ERROR] To see the full stack trace of the errors\"");
        var log6 = new BambooBuildResultDTO.BambooBuildLogEntryDTO(ZonedDateTime.now(), "Unable to publish artifact");
        var log7 = new BambooBuildResultDTO.BambooBuildLogEntryDTO(ZonedDateTime.now(), "NOTE: Picked up JDK_JAVA_OPTIONS");
        var log8 = new BambooBuildResultDTO.BambooBuildLogEntryDTO(ZonedDateTime.now(), "[ERROR] Failed to execute goal org.apache.maven.plugins:maven-checkstyle-plugin");
        var log9 = new BambooBuildResultDTO.BambooBuildLogEntryDTO(ZonedDateTime.now(), "[INFO] Downloading error");
        var log10 = new BambooBuildResultDTO.BambooBuildLogEntryDTO(ZonedDateTime.now(), "[INFO] Downloaded error");

        var logs = List.of(log1, log2, log3, log4, log5, log6, log7, log8, log9, log10);
        // get the failed build log
        mockDelegate.mockGetBuildLogs(participation, logs);
        var buildLogs = request.get(REPOBASEURL + participation.getId() + "/buildlogs", HttpStatus.OK, List.class);

        assertThat(participation.getInitializationState()).as("Participation should be initialized").isEqualTo(InitializationState.INITIALIZED);
        // some build logs have been filtered out
        assertThat(buildLogs.size()).as("Failed build log was created").isEqualTo(1);
    }

    // TEST
    public void startProgrammingExerciseStudentRetrieveEmptyArtifactPage() throws Exception {
        final var course = getCourseForExercise();
        User user = userRepo.findOneByLogin(studentLogin).orElseThrow();
        mockDelegate.mockConnectorRequestsForStartParticipation(exercise, user.getParticipantIdentifier(), Set.of(user), true, HttpStatus.CREATED);

        final var participation = createUserParticipation(course);

        // create a submission
        database.createProgrammingSubmission(participation, false);

        mockDelegate.resetMockProvider();
        mockDelegate.mockRetrieveArtifacts(participation);

        var artifact = request.get(PARTICIPATIONBASEURL + participation.getId() + "/buildArtifact", HttpStatus.OK, byte[].class);

        assertThat(participation.getInitializationState()).as("Participation should be initialized").isEqualTo(InitializationState.INITIALIZED);
        assertThat(artifact).as("No build artifact available for this plan").isEmpty();
    }

    // TEST
    public void repositoryAccessIsAdded_whenStudentIsAddedToTeam() throws Exception {
        exercise.setMode(TEAM);
        programmingExerciseRepository.save(exercise);
        database.addTemplateParticipationForProgrammingExercise(exercise);
        database.addSolutionParticipationForProgrammingExercise(exercise);

        // Create a team with students
        Set<User> students = new HashSet<>(userRepo.findAllInGroupWithAuthorities("tumuser"));
        Team team = new Team().name("Team 1").shortName(teamShortName).exercise(exercise).students(students);
        team = teamService.save(exercise, team);

        assertThat(team.getStudents()).as("Students were correctly added to team").hasSize(numberOfStudents);

        // Set up mockRetrieveArtifacts requests for start participation
        mockDelegate.mockConnectorRequestsForStartParticipation(exercise, team.getParticipantIdentifier(), team.getStudents(), true, HttpStatus.CREATED);

        // Add a new student to the team
        User newStudent = ModelFactory.generateActivatedUsers("new-student", new String[] { "tumuser", "testgroup" }, Set.of(new Authority(AuthoritiesConstants.USER)), 1).get(0);
        newStudent = userRepo.save(newStudent);
        team.addStudents(newStudent);

        // Mock repository write permission give call
        mockDelegate.mockRepositoryWritePermissions(team, newStudent, exercise, HttpStatus.OK);

        // Start participation with original team
        participationService.startExercise(exercise, team, false);

        // Update team with new student after participation has already started
        Team serverTeam = request.putWithResponseBody("/api/exercises/" + exercise.getId() + "/teams/" + team.getId(), team, Team.class, HttpStatus.OK);
        assertThat(serverTeam.getStudents()).as("Team students were updated correctly").hasSize(numberOfStudents + 1); // new student was added
    }

    // TEST
    public void repositoryAccessIsRemoved_whenStudentIsRemovedFromTeam() throws Exception {
        exercise.setMode(TEAM);
        programmingExerciseRepository.save(exercise);
        database.addTemplateParticipationForProgrammingExercise(exercise);
        database.addSolutionParticipationForProgrammingExercise(exercise);

        // Create a team with students
        Set<User> students = new HashSet<>(userRepo.findAllInGroupWithAuthorities("tumuser"));
        Team team = new Team().name("Team 1").shortName(teamShortName).exercise(exercise).students(students);
        team = teamService.save(exercise, team);

        assertThat(team.getStudents()).as("Students were correctly added to team").hasSize(numberOfStudents);

        // Set up mockRetrieveArtifacts requests for start participation
        mockDelegate.mockConnectorRequestsForStartParticipation(exercise, team.getParticipantIdentifier(), team.getStudents(), true, HttpStatus.CREATED);

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
    public void configureRepository_createTeamUserWhenLtiUserIsNotExistent() throws Exception {
        exercise.setMode(TEAM);
        programmingExerciseRepository.save(exercise);
        database.addTemplateParticipationForProgrammingExercise(exercise);
        database.addSolutionParticipationForProgrammingExercise(exercise);

        // create a team for the user (necessary condition before starting an exercise)
        final String edxUsername = userPrefixEdx.get() + "student";
        User edxStudent = ModelFactory.generateActivatedUsers(edxUsername, new String[] { "tumuser", "testgroup" }, Set.of(new Authority(AuthoritiesConstants.USER)), 1).get(0);
        edxStudent.setPassword(passwordService.encryptPassword(edxStudent.getPassword()));
        edxStudent = userRepo.save(edxStudent);
        Team team = setupTeam(edxStudent);

        // Set up mock requests for start participation and that a lti user is not existent
        final boolean ltiUserExists = false;
        mockDelegate.mockConnectorRequestsForStartParticipation(exercise, team.getParticipantIdentifier(), team.getStudents(), ltiUserExists, HttpStatus.CREATED);

        // Start participation with original team
        participationService.startExercise(exercise, team, false);
    }

    // TEST
    public void copyRepository_testNotCreatedError() throws Exception {
        exercise.setMode(TEAM);
        programmingExerciseRepository.save(exercise);
        database.addTemplateParticipationForProgrammingExercise(exercise);
        database.addSolutionParticipationForProgrammingExercise(exercise);

        // Create a team with students
        Set<User> students = new HashSet<>(userRepo.findAllInGroupWithAuthorities("tumuser"));
        Team team = new Team().name("Team 1").shortName(teamShortName).exercise(exercise).students(students);
        team = teamService.save(exercise, team);

        assertThat(team.getStudents()).as("Students were correctly added to team").hasSize(numberOfStudents);

        // test for internal server error
        mockDelegate.mockCopyRepositoryForParticipation(exercise, team.getParticipantIdentifier());
        mockDelegate.mockRepositoryWritePermissions(team, team.getStudents().stream().findFirst().get(), exercise, HttpStatus.BAD_REQUEST);
        var participantRepoTestUrl = getMockFileRepositoryUrl(studentTeamRepo);
        final var teamLocalPath = studentTeamRepo.localRepoFile.toPath();
        doReturn(teamLocalPath).when(gitService).getDefaultLocalPathOfRepo(participantRepoTestUrl);
        doThrow(new InterruptedException()).when(gitService).getOrCheckoutRepositoryIntoTargetDirectory(any(), any(), anyBoolean());

        // the local repo should exist before startExercise()
        assertThat(Files.exists(teamLocalPath)).isTrue();
        // Start participation
        try {
            participationService.startExercise(exercise, team, false);
        }
        catch (VersionControlException e) {
            // the directory of the repo should be deleted
            assertThat(Files.exists(teamLocalPath)).isFalse();
            // We cannot compare exception messages because each vcs has their
            // own. Maybe simply checking that the exception is not empty is
            // enough?
            assertThat(e.getMessage()).isNotEmpty();
        }
    }

    // TEST
    public void copyRepository_testConflictError() throws Exception {
        exercise.setMode(TEAM);
        programmingExerciseRepository.save(exercise);
        database.addTemplateParticipationForProgrammingExercise(exercise);
        database.addSolutionParticipationForProgrammingExercise(exercise);

        // Create a team with students
        Set<User> students = new HashSet<>(userRepo.findAllInGroupWithAuthorities("tumuser"));
        Team team = new Team().name("Team 1").shortName(teamShortName).exercise(exercise).students(students);
        team = teamService.save(exercise, team);

        assertThat(team.getStudents()).as("Students were correctly added to team").hasSize(numberOfStudents);

        // test for Conflict exception
        mockDelegate.mockConnectorRequestsForStartParticipation(exercise, team.getParticipantIdentifier(), team.getStudents(), true, HttpStatus.CONFLICT);

        // Start participation
        participationService.startExercise(exercise, team, false);
    }

    // TEST
    public void configureRepository_testBadRequestError() throws Exception {
        exercise.setMode(TEAM);
        programmingExerciseRepository.save(exercise);
        database.addTemplateParticipationForProgrammingExercise(exercise);
        database.addSolutionParticipationForProgrammingExercise(exercise);

        // Create a team with students
        Set<User> students = new HashSet<>(userRepo.findAllInGroupWithAuthorities("tumuser"));
        Team team = new Team().name("Team 1").shortName(teamShortName).exercise(exercise).students(students);
        team = teamService.save(exercise, team);

        assertThat(team.getStudents()).as("Students were correctly added to team").hasSize(numberOfStudents);

        // test for internal server error
        final var username = team.getParticipantIdentifier();
        mockDelegate.mockCopyRepositoryForParticipation(exercise, username);
        mockDelegate.mockRepositoryWritePermissions(team, team.getStudents().stream().findFirst().get(), exercise, HttpStatus.BAD_REQUEST);

        // Start participation
        try {
            participationService.startExercise(exercise, team, false);
        }
        catch (VersionControlException e) {
            // We cannot compare exception messages because each vcs has their own. Maybe simply checking that the exception is not empty is enough?
            assertThat(e.getMessage()).isNotEmpty();
        }
    }

    private void structureOracle(ProgrammingExercise programmingExercise) throws Exception {
        mockDelegate.mockConnectorRequestsForSetup(programmingExercise);
        final var generatedExercise = request.postWithResponseBody(ROOT + SETUP, programmingExercise, ProgrammingExercise.class, HttpStatus.CREATED);
        String response = request.putWithResponseBody(ROOT + GENERATE_TESTS.replace("{exerciseId}", String.valueOf(generatedExercise.getId())), generatedExercise, String.class,
                HttpStatus.OK);
        assertThat(response).startsWith("Successfully generated the structure oracle");

        List<RevCommit> testRepoCommits = testRepo.getAllLocalCommits();
        assertThat(testRepoCommits.size()).isEqualTo(2);

        assertThat(testRepoCommits.get(0).getFullMessage()).isEqualTo("Update the structure oracle file.");
        List<DiffEntry> changes = getChanges(testRepo.localGit.getRepository(), testRepoCommits.get(0));
        assertThat(changes.size()).isEqualTo(1);
        assertThat(changes.get(0).getChangeType()).isEqualTo(DiffEntry.ChangeType.MODIFY);
        assertThat(changes.get(0).getOldPath()).endsWith("test.json");

        // Second time leads to a bad request because the file did not change
        var expectedHeaders = new HashMap<String, String>();
        expectedHeaders.put("X-artemisApp-alert", "Did not update the oracle because there have not been any changes to it.");
        request.putWithResponseBody(ROOT + GENERATE_TESTS.replace("{exerciseId}", String.valueOf(generatedExercise.getId())), generatedExercise, String.class,
                HttpStatus.BAD_REQUEST, expectedHeaders);
        assertThat(response).startsWith("Successfully generated the structure oracle");
    }

    private void validateProgrammingExercise(ProgrammingExercise generatedExercise) {
        exercise.setId(generatedExercise.getId());
        assertThat(exercise).isEqualTo(generatedExercise);
        assertThat(programmingExerciseRepository.count()).isEqualTo(1);
    }

    private Course getCourseForExercise() {
        final var course = exercise.getCourseViaExerciseGroupOrCourseMember();
        programmingExerciseRepository.save(exercise);
        database.addTemplateParticipationForProgrammingExercise(exercise);
        database.addSolutionParticipationForProgrammingExercise(exercise);
        return course;
    }

    private ProgrammingExerciseStudentParticipation createUserParticipation(Course course) throws Exception {
        final var path = ROOT + ParticipationResource.Endpoints.START_PARTICIPATION.replace("{courseId}", String.valueOf(course.getId())).replace("{exerciseId}",
                String.valueOf(exercise.getId()));
        return request.postWithResponseBody(path, null, ProgrammingExerciseStudentParticipation.class, HttpStatus.CREATED);
    }

    public List<DiffEntry> getChanges(Repository repository, RevCommit commit) throws Exception {
        try (ObjectReader reader = repository.newObjectReader()) {
            CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
            oldTreeIter.reset(reader, commit.getParents()[0].getTree());
            CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
            newTreeIter.reset(reader, commit.getTree());

            // finally get the list of changed files
            try (Git git = new Git(repository)) {
                List<DiffEntry> diffs = git.diff().setNewTree(newTreeIter).setOldTree(oldTreeIter).call();
                for (DiffEntry entry : diffs) {
                    System.out.println("Entry: " + entry);
                }
                return diffs;
            }
        }
    }
}
