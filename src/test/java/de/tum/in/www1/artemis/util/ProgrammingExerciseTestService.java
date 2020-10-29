package de.tum.in.www1.artemis.util;

import static de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResource.Endpoints.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doReturn;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseMode;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.exception.BitbucketException;
import de.tum.in.www1.artemis.programmingexercise.MockDelegate;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.StaticCodeAnalysisCategoryRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.AuthoritiesConstants;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.ParticipationService;
import de.tum.in.www1.artemis.service.TeamService;
import de.tum.in.www1.artemis.service.UserService;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.connectors.VersionControlService;
import de.tum.in.www1.artemis.service.connectors.bamboo.dto.BambooBuildResultDTO;
import de.tum.in.www1.artemis.service.programming.ProgrammingLanguageFeature;
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
    @Qualifier("staticCodeAnalysisConfiguration")
    private Map<ProgrammingLanguage, List<StaticCodeAnalysisDefaultCategory>> staticCodeAnalysisDefaultConfigurations;

    @Autowired
    private UserService userService;

    @Value("${artemis.lti.user-prefix-edx:#{null}}")
    private Optional<String> userPrefixEdx;

    public Course course;

    public ProgrammingExercise exercise;

    public ProgrammingExercise examExercise;

    public final static int numberOfStudents = 2;

    public final static String studentLogin = "student1";

    public final static String teamShortName = "team1";

    public final static String REPOBASEURL = "/api/repository/";

    public final static String PARTICIPATIONBASEURL = "/api/participations/";

    public LocalRepository exerciseRepo = new LocalRepository();

    public LocalRepository testRepo = new LocalRepository();

    public LocalRepository solutionRepo = new LocalRepository();

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
        studentRepo.resetLocalRepo();
        studentTeamRepo.resetLocalRepo();
    }

    public void setupRepositoryMocks(ProgrammingExercise exercise) throws Exception {
        setupRepositoryMocks(exercise, exerciseRepo, solutionRepo, testRepo);
    }

    public void setupRepositoryMocks(ProgrammingExercise exercise, LocalRepository exerciseRepository, LocalRepository solutionRepository, LocalRepository testRepository)
            throws Exception {
        final var projectKey = exercise.getProjectKey();

        String exerciseRepoName = projectKey.toLowerCase() + "-" + RepositoryType.TEMPLATE.getName();
        String testRepoName = projectKey.toLowerCase() + "-" + RepositoryType.TESTS.getName();
        String solutionRepoName = projectKey.toLowerCase() + "-" + RepositoryType.SOLUTION.getName();

        var exerciseRepoTestUrl = new GitUtilService.MockFileRepositoryUrl(exerciseRepository.originRepoFile);
        var testRepoTestUrl = new GitUtilService.MockFileRepositoryUrl(testRepository.originRepoFile);
        var solutionRepoTestUrl = new GitUtilService.MockFileRepositoryUrl(solutionRepository.originRepoFile);

        doReturn(exerciseRepoTestUrl).when(versionControlService).getCloneRepositoryUrl(projectKey, exerciseRepoName);
        doReturn(testRepoTestUrl).when(versionControlService).getCloneRepositoryUrl(projectKey, testRepoName);
        doReturn(solutionRepoTestUrl).when(versionControlService).getCloneRepositoryUrl(projectKey, solutionRepoName);

        doReturn(gitService.getRepositoryByLocalPath(exerciseRepository.localRepoFile.toPath())).when(gitService).getOrCheckoutRepository(exerciseRepoTestUrl.getURL(), true);
        doReturn(gitService.getRepositoryByLocalPath(testRepository.localRepoFile.toPath())).when(gitService).getOrCheckoutRepository(testRepoTestUrl.getURL(), true);
        doReturn(gitService.getRepositoryByLocalPath(solutionRepository.localRepoFile.toPath())).when(gitService).getOrCheckoutRepository(solutionRepoTestUrl.getURL(), true);

        mockDelegate.mockGetRepositorySlugFromUrl(exerciseRepoName, exerciseRepoTestUrl.getURL());
        mockDelegate.mockGetRepositorySlugFromUrl(testRepoName, testRepoTestUrl.getURL());
        mockDelegate.mockGetRepositorySlugFromUrl(solutionRepoName, solutionRepoTestUrl.getURL());

        mockDelegate.mockGetProjectKeyFromUrl(projectKey, exerciseRepoTestUrl.getURL());
        mockDelegate.mockGetProjectKeyFromUrl(projectKey, testRepoTestUrl.getURL());
        mockDelegate.mockGetProjectKeyFromUrl(projectKey, solutionRepoTestUrl.getURL());
        mockDelegate.mockGetProjectKeyFromAnyUrl(projectKey);
    }

    /**
     * can be invoked for teams and students
     */
    public void setupRepositoryMocksParticipant(ProgrammingExercise exercise, String participantName, LocalRepository studentRepo) throws Exception {
        final var projectKey = exercise.getProjectKey();
        String participantRepoName = projectKey.toLowerCase() + "-" + participantName;
        var participantRepoTestUrl = new GitUtilService.MockFileRepositoryUrl(studentRepo.originRepoFile);
        doReturn(participantRepoTestUrl).when(versionControlService).getCloneRepositoryUrl(projectKey, participantRepoName);
        doReturn(gitService.getRepositoryByLocalPath(studentRepo.localRepoFile.toPath())).when(gitService).getOrCheckoutRepository(participantRepoTestUrl.getURL(), true);
        mockDelegate.mockGetRepositorySlugFromUrl(participantRepoName, participantRepoTestUrl.getURL());
        mockDelegate.mockGetProjectKeyFromUrl(projectKey, participantRepoTestUrl.getURL());
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
    public void createProgrammingExercise_validExercise_withStaticCodeAnalysis() throws Exception {
        exercise.setStaticCodeAnalysisEnabled(true);
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
    public void importExercise_created(ProgrammingLanguage programmingLanguage) throws Exception {
        boolean staticCodeAnalysisEnabled = programmingLanguage == ProgrammingLanguage.JAVA;
        // Setup exercises for import
        ProgrammingExercise sourceExercise = database.addCourseWithOneProgrammingExerciseAndStaticCodeAnalysisCategories();
        sourceExercise.setProgrammingLanguage(programmingLanguage);
        sourceExercise.setStaticCodeAnalysisEnabled(staticCodeAnalysisEnabled);
        database.addTestCasesToProgrammingExercise(sourceExercise);
        database.addHintsToExercise(sourceExercise);
        database.addHintsToProblemStatement(sourceExercise);
        sourceExercise = database.loadProgrammingExerciseWithEagerReferences(sourceExercise);
        ProgrammingExercise exerciseToBeImported = ModelFactory.generateToBeImportedProgrammingExercise("ImportTitle", "imported", sourceExercise, database.addEmptyCourse());
        exerciseToBeImported.setProgrammingLanguage(programmingLanguage);
        exerciseToBeImported.setStaticCodeAnalysisEnabled(staticCodeAnalysisEnabled);
        // Mock requests
        List<Verifiable> verifiables = mockDelegate.mockConnectorRequestsForImport(sourceExercise, exerciseToBeImported);
        setupRepositoryMocks(exerciseToBeImported, exerciseRepo, solutionRepo, testRepo);

        // Import the exercise and load all referenced entities
        var importedExercise = request.postWithResponseBody(ROOT + IMPORT.replace("{sourceExerciseId}", sourceExercise.getId().toString()), exerciseToBeImported,
                ProgrammingExercise.class, HttpStatus.OK);
        SecurityUtils.setAuthorizationObject();
        importedExercise = database.loadProgrammingExerciseWithEagerReferences(importedExercise);

        // Assert correct creation of repos and plans
        for (var verifiable : verifiables) {
            verifiable.performVerification();
        }
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
        exerciseToBeImported.setMode(ExerciseMode.TEAM);
        var teamAssignmentConfig = new TeamAssignmentConfig();
        teamAssignmentConfig.setExercise(exerciseToBeImported);
        teamAssignmentConfig.setMinTeamSize(1);
        teamAssignmentConfig.setMaxTeamSize(10);
        exerciseToBeImported.setTeamAssignmentConfig(teamAssignmentConfig);

        // Mock requests
        mockDelegate.mockConnectorRequestsForImport(sourceExercise, exerciseToBeImported);
        setupRepositoryMocks(exerciseToBeImported, exerciseRepo, solutionRepo, testRepo);

        exerciseToBeImported = request.postWithResponseBody(ROOT + IMPORT.replace("{sourceExerciseId}", sourceExercise.getId().toString()), exerciseToBeImported,
                ProgrammingExercise.class, HttpStatus.OK);

        SecurityUtils.setAuthorizationObject();
        assertEquals(ExerciseMode.TEAM, exerciseToBeImported.getMode());
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
        sourceExercise.setMode(ExerciseMode.TEAM);
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
        mockDelegate.mockConnectorRequestsForImport(sourceExercise, exerciseToBeImported);
        setupRepositoryMocks(exerciseToBeImported, exerciseRepo, solutionRepo, testRepo);

        exerciseToBeImported = request.postWithResponseBody(ROOT + IMPORT.replace("{sourceExerciseId}", sourceExercise.getId().toString()), exerciseToBeImported,
                ProgrammingExercise.class, HttpStatus.OK);

        SecurityUtils.setAuthorizationObject();
        assertEquals(ExerciseMode.INDIVIDUAL, exerciseToBeImported.getMode());
        assertNull(exerciseToBeImported.getTeamAssignmentConfig());
        assertEquals(0, teamService.findAllByExerciseIdWithEagerStudents(exerciseToBeImported, null).size());

        sourceExercise = database.loadProgrammingExerciseWithEagerReferences(sourceExercise);
        assertEquals(ExerciseMode.TEAM, sourceExercise.getMode());
        assertEquals(1, teamService.findAllByExerciseIdWithEagerStudents(sourceExercise, null).size());
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
    public void startProgrammingExercise_student_correctInitializationState() throws Exception {
        final var course = exercise.getCourseViaExerciseGroupOrCourseMember();
        programmingExerciseRepository.save(exercise);
        database.addTemplateParticipationForProgrammingExercise(exercise);
        database.addSolutionParticipationForProgrammingExercise(exercise);

        User user = userRepo.findOneByLogin(studentLogin).orElseThrow();
        mockDelegate.mockCopyRepositoryForParticipation(exercise, user.getParticipantIdentifier(), HttpStatus.CREATED);
        final var verifications = mockDelegate.mockConnectorRequestsForStartParticipation(exercise, user.getParticipantIdentifier(), Set.of(user), true);
        final var path = ParticipationResource.Endpoints.ROOT + ParticipationResource.Endpoints.START_PARTICIPATION.replace("{courseId}", String.valueOf(course.getId()))
                .replace("{exerciseId}", String.valueOf(exercise.getId()));
        final var participation = request.postWithResponseBody(path, null, ProgrammingExerciseStudentParticipation.class, HttpStatus.CREATED);

        for (final var verification : verifications) {
            verification.performVerification();
        }

        assertThat(participation.getInitializationState()).as("Participation should be initialized").isEqualTo(InitializationState.INITIALIZED);
    }

    // TEST
    public void startProgrammingExercise_team_correctInitializationState() throws Exception {
        final var course = exercise.getCourseViaExerciseGroupOrCourseMember();
        exercise.setMode(ExerciseMode.TEAM);
        programmingExerciseRepository.save(exercise);
        database.addTemplateParticipationForProgrammingExercise(exercise);
        database.addSolutionParticipationForProgrammingExercise(exercise);

        // create a team for the user (necessary condition before starting an exercise)
        Set<User> students = Set.of(userRepo.findOneByLogin(studentLogin).get());
        Team team = new Team().name("Team 1").shortName(teamShortName).exercise(exercise).students(students);
        team = teamService.save(exercise, team);

        assertThat(team.getStudents()).as("Student was correctly added to team").hasSize(1);

        mockDelegate.mockCopyRepositoryForParticipation(exercise, team.getParticipantIdentifier(), HttpStatus.CREATED);
        final var verifications = mockDelegate.mockConnectorRequestsForStartParticipation(exercise, team.getParticipantIdentifier(), team.getStudents(), true);
        final var path = ParticipationResource.Endpoints.ROOT + ParticipationResource.Endpoints.START_PARTICIPATION.replace("{courseId}", String.valueOf(course.getId()))
                .replace("{exerciseId}", String.valueOf(exercise.getId()));
        final var participation = request.postWithResponseBody(path, null, ProgrammingExerciseStudentParticipation.class, HttpStatus.CREATED);

        for (final var verification : verifications) {
            verification.performVerification();
        }

        assertThat(participation.getInitializationState()).as("Participation should be initialized").isEqualTo(InitializationState.INITIALIZED);
    }

    // TEST
    public void startProgrammingExerciseStudentSubmissionFailedWithBuildlog() throws Exception {
        final var course = getCourseForExercise();
        User user = userRepo.findOneByLogin(studentLogin).orElseThrow();
        mockDelegate.mockCopyRepositoryForParticipation(exercise, user.getParticipantIdentifier(), HttpStatus.CREATED);
        final var verifications = mockDelegate.mockConnectorRequestsForStartParticipation(exercise, user.getParticipantIdentifier(), Set.of(user), true);
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

        for (final var verification : verifications) {
            verification.performVerification();
        }

        assertThat(participation.getInitializationState()).as("Participation should be initialized").isEqualTo(InitializationState.INITIALIZED);
        // some build logs have been filtered out
        assertThat(buildLogs.size()).as("Failed build log was created").isEqualTo(1);
    }

    // TEST
    public void startProgrammingExerciseStudentRetrieveEmptyArtifactPage() throws Exception {
        final var course = getCourseForExercise();
        User user = userRepo.findOneByLogin(studentLogin).orElseThrow();
        mockDelegate.mockCopyRepositoryForParticipation(exercise, user.getParticipantIdentifier(), HttpStatus.CREATED);
        final var verifications = mockDelegate.mockConnectorRequestsForStartParticipation(exercise, user.getParticipantIdentifier(), Set.of(user), true);

        final var participation = createUserParticipation(course);

        // create a submission
        database.createProgrammingSubmission(participation, false);

        mockDelegate.resetMockProvider();
        mockDelegate.mockRetrieveArtifacts(participation);

        var artifact = request.get(PARTICIPATIONBASEURL + participation.getId() + "/buildArtifact", HttpStatus.OK, byte[].class);

        for (final var verification : verifications) {
            verification.performVerification();
        }

        assertThat(participation.getInitializationState()).as("Participation should be initialized").isEqualTo(InitializationState.INITIALIZED);
        assertThat(artifact).as("No build artifact available for this plan").isEmpty();
    }

    // TEST
    public void repositoryAccessIsAdded_whenStudentIsAddedToTeam() throws Exception {
        exercise.setMode(ExerciseMode.TEAM);
        programmingExerciseRepository.save(exercise);
        database.addTemplateParticipationForProgrammingExercise(exercise);
        database.addSolutionParticipationForProgrammingExercise(exercise);

        // Create a team with students
        Set<User> students = new HashSet<>(userRepo.findAllInGroup("tumuser"));
        Team team = new Team().name("Team 1").shortName(teamShortName).exercise(exercise).students(students);
        team = teamService.save(exercise, team);

        assertThat(team.getStudents()).as("Students were correctly added to team").hasSize(numberOfStudents);

        // Set up mockRetrieveArtifacts requests for start participation
        mockDelegate.mockCopyRepositoryForParticipation(exercise, team.getParticipantIdentifier(), HttpStatus.CREATED);
        final var verifications = mockDelegate.mockConnectorRequestsForStartParticipation(exercise, team.getParticipantIdentifier(), team.getStudents(), true);

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

        for (final var verification : verifications) {
            verification.performVerification();
        }
    }

    // TEST
    public void repositoryAccessIsRemoved_whenStudentIsRemovedFromTeam() throws Exception {
        exercise.setMode(ExerciseMode.TEAM);
        programmingExerciseRepository.save(exercise);
        database.addTemplateParticipationForProgrammingExercise(exercise);
        database.addSolutionParticipationForProgrammingExercise(exercise);

        // Create a team with students
        Set<User> students = new HashSet<>(userRepo.findAllInGroup("tumuser"));
        Team team = new Team().name("Team 1").shortName(teamShortName).exercise(exercise).students(students);
        team = teamService.save(exercise, team);

        assertThat(team.getStudents()).as("Students were correctly added to team").hasSize(numberOfStudents);

        // Set up mockRetrieveArtifacts requests for start participation
        mockDelegate.mockCopyRepositoryForParticipation(exercise, team.getParticipantIdentifier(), HttpStatus.CREATED);
        final var verifications = mockDelegate.mockConnectorRequestsForStartParticipation(exercise, team.getParticipantIdentifier(), team.getStudents(), true);

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

        for (final var verification : verifications) {
            verification.performVerification();
        }
    }

    // TEST
    public void configureRepository_createTeamUserWhenLtiUserIsNotExistent() throws Exception {
        exercise.setMode(ExerciseMode.TEAM);
        programmingExerciseRepository.save(exercise);
        database.addTemplateParticipationForProgrammingExercise(exercise);
        database.addSolutionParticipationForProgrammingExercise(exercise);

        // create a team for the user (necessary condition before starting an exercise)
        final String edxUsername = userPrefixEdx.get() + "student";
        User edxStudent = ModelFactory.generateActivatedUsers(edxUsername, new String[] { "tumuser", "testgroup" }, Set.of(new Authority(AuthoritiesConstants.USER)), 1).get(0);
        edxStudent.setPassword(userService.encryptor().encrypt(edxStudent.getPassword()));
        edxStudent = userRepo.save(edxStudent);
        Set<User> students = Set.of(edxStudent);
        Team team = new Team().name("Team 1").shortName(teamShortName).exercise(exercise).students(students);
        team = teamService.save(exercise, team);

        assertThat(team.getStudents()).as("Student was correctly added to team").hasSize(1);

        // Set up mock requests for start participation and that a lti user is not existent
        final boolean ltiUserExists = false;
        mockDelegate.mockCopyRepositoryForParticipation(exercise, team.getParticipantIdentifier(), HttpStatus.CREATED);
        mockDelegate.mockConnectorRequestsForStartParticipation(exercise, team.getParticipantIdentifier(), team.getStudents(), ltiUserExists);

        // Start participation with original team
        participationService.startExercise(exercise, team, false);
    }

    // TEST
    public void copyRepository_testInternalServerError() throws Exception {
        exercise.setMode(ExerciseMode.TEAM);
        programmingExerciseRepository.save(exercise);
        database.addTemplateParticipationForProgrammingExercise(exercise);
        database.addSolutionParticipationForProgrammingExercise(exercise);

        // Create a team with students
        Set<User> students = new HashSet<>(userRepo.findAllInGroup("tumuser"));
        Team team = new Team().name("Team 1").shortName(teamShortName).exercise(exercise).students(students);
        team = teamService.save(exercise, team);

        assertThat(team.getStudents()).as("Students were correctly added to team").hasSize(numberOfStudents);

        // test for internal server error
        mockDelegate.mockCopyRepositoryForParticipation(exercise, team.getParticipantIdentifier(), HttpStatus.INTERNAL_SERVER_ERROR);

        // Start participation
        try {
            participationService.startExercise(exercise, team, false);
        }
        catch (BitbucketException e) {
            assertThat(e.getMessage()).isEqualTo("Error while forking repository");
        }
    }

    // TEST
    public void copyRepository_testBadRequestError() throws Exception {
        exercise.setMode(ExerciseMode.TEAM);
        programmingExerciseRepository.save(exercise);
        database.addTemplateParticipationForProgrammingExercise(exercise);
        database.addSolutionParticipationForProgrammingExercise(exercise);

        // Create a team with students
        Set<User> students = new HashSet<>(userRepo.findAllInGroup("tumuser"));
        Team team = new Team().name("Team 1").shortName(teamShortName).exercise(exercise).students(students);
        team = teamService.save(exercise, team);

        assertThat(team.getStudents()).as("Students were correctly added to team").hasSize(numberOfStudents);

        // test for internal server error
        mockDelegate.mockCopyRepositoryForParticipation(exercise, team.getParticipantIdentifier(), HttpStatus.BAD_REQUEST);

        // Start participation
        try {
            participationService.startExercise(exercise, team, false);
        }
        catch (BitbucketException e) {
            assertThat(e.getMessage()).isEqualTo("Error while forking repository");
        }
    }

    // TEST
    public void copyRepository_testConflictError() throws Exception {
        exercise.setMode(ExerciseMode.TEAM);
        programmingExerciseRepository.save(exercise);
        database.addTemplateParticipationForProgrammingExercise(exercise);
        database.addSolutionParticipationForProgrammingExercise(exercise);

        // Create a team with students
        Set<User> students = new HashSet<>(userRepo.findAllInGroup("tumuser"));
        Team team = new Team().name("Team 1").shortName(teamShortName).exercise(exercise).students(students);
        team = teamService.save(exercise, team);

        assertThat(team.getStudents()).as("Students were correctly added to team").hasSize(numberOfStudents);

        // test for Conflict exception
        mockDelegate.mockCopyRepositoryForParticipation(exercise, team.getParticipantIdentifier(), HttpStatus.CONFLICT);
        mockDelegate.mockConnectorRequestsForStartParticipation(exercise, team.getParticipantIdentifier(), team.getStudents(), true);

        // Start participation
        participationService.startExercise(exercise, team, false);
    }

    // TEST
    public void configureRepository_testBadRequestError() throws Exception {
        exercise.setMode(ExerciseMode.TEAM);
        programmingExerciseRepository.save(exercise);
        database.addTemplateParticipationForProgrammingExercise(exercise);
        database.addSolutionParticipationForProgrammingExercise(exercise);

        // Create a team with students
        Set<User> students = new HashSet<>(userRepo.findAllInGroup("tumuser"));
        Team team = new Team().name("Team 1").shortName(teamShortName).exercise(exercise).students(students);
        team = teamService.save(exercise, team);

        assertThat(team.getStudents()).as("Students were correctly added to team").hasSize(numberOfStudents);

        // test for internal server error
        final var username = team.getParticipantIdentifier();
        mockDelegate.mockCopyRepositoryForParticipation(exercise, username, HttpStatus.CREATED);
        final var projectKey = exercise.getProjectKey();
        final var repoName = projectKey.toLowerCase() + "-" + username.toLowerCase();
        mockDelegate.mockRepositoryWritePermissions(team, team.getStudents().stream().findFirst().get(), exercise, HttpStatus.BAD_REQUEST);

        // Start participation
        try {
            participationService.startExercise(exercise, team, false);
        }
        catch (BitbucketException e) {
            assertThat(e.getMessage()).isEqualTo("Error while giving repository permissions");
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
