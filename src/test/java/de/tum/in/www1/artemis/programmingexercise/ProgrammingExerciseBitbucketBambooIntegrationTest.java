package de.tum.in.www1.artemis.programmingexercise;

import static de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResource.Endpoints.*;
import static org.assertj.core.api.Assertions.assertThat;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseMode;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.StaticCodeAnalysisCategoryRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.AuthoritiesConstants;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.ParticipationService;
import de.tum.in.www1.artemis.service.TeamService;
import de.tum.in.www1.artemis.service.UserService;
import de.tum.in.www1.artemis.service.connectors.bamboo.dto.BambooBuildResultDTO;
import de.tum.in.www1.artemis.util.*;
import de.tum.in.www1.artemis.web.rest.ParticipationResource;

public class ProgrammingExerciseBitbucketBambooIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private RequestUtilService request;

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

    private Course course;

    private ExerciseGroup exerciseGroup;

    private ProgrammingExercise exercise;

    private ProgrammingExercise examExercise;

    private final static int numberOfStudents = 2;

    private final static String studentLogin = "student1";

    private final static String teamShortName = "team1";

    private final static String REPOBASEURL = "/api/repository/";

    private final static String PARTICIPATIONBASEURL = "/api/participations/";

    LocalRepository exerciseRepo = new LocalRepository();

    LocalRepository testRepo = new LocalRepository();

    LocalRepository solutionRepo = new LocalRepository();

    LocalRepository studentRepo = new LocalRepository();

    LocalRepository studentTeamRepo = new LocalRepository();

    @BeforeEach
    public void setup() throws Exception {
        database.addUsers(numberOfStudents, 1, 1);
        course = database.addEmptyCourse();
        exerciseGroup = database.addExerciseGroupWithExamAndCourse(true);
        examExercise = ModelFactory.generateProgrammingExerciseForExam(exerciseGroup);
        exercise = ModelFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(7), course);
        bambooRequestMockProvider.enableMockingOfRequests();
        bitbucketRequestMockProvider.enableMockingOfRequests();

        exerciseRepo.configureRepos("exerciseLocalRepo", "exerciseOriginRepo");
        testRepo.configureRepos("testLocalRepo", "testOriginRepo");
        solutionRepo.configureRepos("solutionLocalRepo", "solutionOriginRepo");
        studentRepo.configureRepos("studentRepo", "studentOriginRepo");
        studentTeamRepo.configureRepos("studentTeamRepo", "studentTeamOriginRepo");

        setupRepositoryMocks(exercise, exerciseRepo, solutionRepo, testRepo);
        setupRepositoryMocksParticipant(exercise, studentLogin, studentRepo);
        setupRepositoryMocksParticipant(exercise, teamShortName, studentTeamRepo);
    }

    @AfterEach
    public void tearDown() throws IOException {
        database.resetDatabase();
        bitbucketRequestMockProvider.reset();
        bambooRequestMockProvider.reset();
        exerciseRepo.resetLocalRepo();
        testRepo.resetLocalRepo();
        solutionRepo.resetLocalRepo();
        studentRepo.resetLocalRepo();
        studentTeamRepo.resetLocalRepo();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createProgrammingExercise_sequential_validExercise_created() throws Exception {
        exercise.setSequentialTestRuns(true);
        mockConnectorRequestsForSetup(exercise);
        validateProgrammingExercise(request.postWithResponseBody(ROOT + SETUP, exercise, ProgrammingExercise.class, HttpStatus.CREATED));
    }

    @ParameterizedTest
    @EnumSource(ExerciseMode.class)
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createProgrammingExercise_mode_validExercise_created(ExerciseMode mode) throws Exception {
        exercise.setMode(mode);
        mockConnectorRequestsForSetup(exercise);
        validateProgrammingExercise(request.postWithResponseBody(ROOT + SETUP, exercise, ProgrammingExercise.class, HttpStatus.CREATED));
    }

    @ParameterizedTest
    @EnumSource(ProgrammingLanguage.class)
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createProgrammingExercise_programmingLanguage_validExercise_created(ProgrammingLanguage language) throws Exception {
        exercise.setProgrammingLanguage(language);
        mockConnectorRequestsForSetup(exercise);
        validateProgrammingExercise(request.postWithResponseBody(ROOT + SETUP, exercise, ProgrammingExercise.class, HttpStatus.CREATED));
    }

    private void validateProgrammingExercise(ProgrammingExercise generatedExercise) {
        exercise.setId(generatedExercise.getId());
        assertThat(exercise).isEqualTo(generatedExercise);
        assertThat(programmingExerciseRepository.count()).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createProgrammingExercise_validExercise_bonusPointsIsNull() throws Exception {
        exercise.setBonusPoints(null);
        mockConnectorRequestsForSetup(exercise);
        var generatedExercise = request.postWithResponseBody(ROOT + SETUP, exercise, ProgrammingExercise.class);
        var savedExercise = programmingExerciseRepository.findById(generatedExercise.getId()).get();
        assertThat(generatedExercise.getBonusPoints()).isEqualTo(0D);
        assertThat(savedExercise.getBonusPoints()).isEqualTo(0D);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createProgrammingExercise_validExercise_withStaticCodeAnalysis() throws Exception {
        exercise.setStaticCodeAnalysisEnabled(true);
        mockConnectorRequestsForSetup(exercise);
        var generatedExercise = request.postWithResponseBody(ROOT + SETUP, exercise, ProgrammingExercise.class);

        exercise.setId(generatedExercise.getId());
        assertThat(exercise).isEqualTo(generatedExercise);
        var staticCodeAnalysisCategories = staticCodeAnalysisCategoryRepository.findByExerciseId(generatedExercise.getId());
        assertThat(staticCodeAnalysisCategories).usingRecursiveFieldByFieldElementComparator().usingElementComparatorIgnoringFields("id", "exercise")
                .isEqualTo(staticCodeAnalysisDefaultConfigurations.get(exercise.getProgrammingLanguage()));
        staticCodeAnalysisDefaultConfigurations.get(exercise.getProgrammingLanguage()).forEach(config -> {
            config.getCategoryMappings().forEach(mapping -> {
                assertThat(mapping.getTool()).isNotNull();
                assertThat(mapping.getCategory()).isNotNull();
            });
        });
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createProgrammingExerciseForExam_validExercise_created() throws Exception {
        setupRepositoryMocks(examExercise, exerciseRepo, solutionRepo, testRepo);

        mockConnectorRequestsForSetup(examExercise);
        final var generatedExercise = request.postWithResponseBody(ROOT + SETUP, examExercise, ProgrammingExercise.class, HttpStatus.CREATED);

        examExercise.setId(generatedExercise.getId());
        assertThat(examExercise).isEqualTo(generatedExercise);
        assertThat(programmingExerciseRepository.count()).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(ProgrammingLanguage.class)
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
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
        List<Verifiable> verifiables = mockConnectorRequestsForImport(sourceExercise, exerciseToBeImported);
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

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createProgrammingExercise_validExercise_structureOracle() throws Exception {
        structureOracle(exercise);
    }

    private void structureOracle(ProgrammingExercise programmingExercise) throws Exception {
        mockConnectorRequestsForSetup(programmingExercise);
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

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createProgrammingExercise_noTutors_created() throws Exception {
        course.setTeachingAssistantGroupName(null);
        courseRepository.save(course);
        mockConnectorRequestsForSetup(exercise);

        final var generatedExercise = request.postWithResponseBody(ROOT + SETUP, exercise, ProgrammingExercise.class, HttpStatus.CREATED);

        validateProgrammingExercise(generatedExercise);
    }

    @Test
    @WithMockUser(username = studentLogin, roles = "USER")
    public void startProgrammingExercise_student_correctInitializationState() throws Exception {
        final var course = exercise.getCourseViaExerciseGroupOrCourseMember();
        programmingExerciseRepository.save(exercise);
        database.addTemplateParticipationForProgrammingExercise(exercise);
        database.addSolutionParticipationForProgrammingExercise(exercise);

        User user = userRepo.findOneByLogin(studentLogin).orElseThrow();
        final var verifications = mockConnectorRequestsForStartParticipation(exercise, user.getParticipantIdentifier(), Set.of(user));
        final var path = ParticipationResource.Endpoints.ROOT + ParticipationResource.Endpoints.START_PARTICIPATION.replace("{courseId}", String.valueOf(course.getId()))
                .replace("{exerciseId}", String.valueOf(exercise.getId()));
        final var participation = request.postWithResponseBody(path, null, ProgrammingExerciseStudentParticipation.class, HttpStatus.CREATED);

        for (final var verification : verifications) {
            verification.performVerification();
        }

        assertThat(participation.getInitializationState()).as("Participation should be initialized").isEqualTo(InitializationState.INITIALIZED);
    }

    @Test
    @WithMockUser(username = studentLogin, roles = "USER")
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

        final var verifications = mockConnectorRequestsForStartParticipation(exercise, team.getParticipantIdentifier(), team.getStudents());
        final var path = ParticipationResource.Endpoints.ROOT + ParticipationResource.Endpoints.START_PARTICIPATION.replace("{courseId}", String.valueOf(course.getId()))
                .replace("{exerciseId}", String.valueOf(exercise.getId()));
        final var participation = request.postWithResponseBody(path, null, ProgrammingExerciseStudentParticipation.class, HttpStatus.CREATED);

        for (final var verification : verifications) {
            verification.performVerification();
        }

        assertThat(participation.getInitializationState()).as("Participation should be initialized").isEqualTo(InitializationState.INITIALIZED);
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

    @Test
    @WithMockUser(username = studentLogin, roles = "USER")
    public void startProgrammingExerciseStudentSubmissionFailedWithBuildlog() throws Exception {
        final var course = getCourseForExercise();
        User user = userRepo.findOneByLogin(studentLogin).orElseThrow();
        final var verifications = mockConnectorRequestsForStartParticipation(exercise, user.getParticipantIdentifier(), Set.of(user));
        final var participation = createUserParticipation(course);

        // create a submission which fails
        database.createProgrammingSubmission(participation, true);

        bambooRequestMockProvider.reset();

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

        // get the failed build log
        bambooRequestMockProvider.mockGetBuildLogs(participation.getBuildPlanId(), List.of(log1, log2, log3, log4, log5, log6, log7, log8, log9, log10));
        var buildLogs = request.get(REPOBASEURL + participation.getId() + "/buildlogs", HttpStatus.OK, List.class);

        for (final var verification : verifications) {
            verification.performVerification();
        }

        assertThat(participation.getInitializationState()).as("Participation should be initialized").isEqualTo(InitializationState.INITIALIZED);
        // some build logs have been filtered out
        assertThat(buildLogs.size()).as("Failed build log was created").isEqualTo(1);
    }

    @Test
    @WithMockUser(username = studentLogin, roles = "USER")
    public void startProgrammingExerciseStudentRetrieveEmptyArtifactPage() throws Exception {
        final var course = getCourseForExercise();
        User user = userRepo.findOneByLogin(studentLogin).orElseThrow();
        final var verifications = mockConnectorRequestsForStartParticipation(exercise, user.getParticipantIdentifier(), Set.of(user));

        final var participation = createUserParticipation(course);

        // create a submission
        database.createProgrammingSubmission(participation, false);

        bambooRequestMockProvider.reset();

        // prepare the build result
        bambooRequestMockProvider.mockQueryLatestBuildResultFromBambooServer(participation.getBuildPlanId());
        // prepare the artifact to be null
        bambooRequestMockProvider.mockRetrieveEmptyArtifactPage();

        var artifact = request.get(PARTICIPATIONBASEURL + participation.getId() + "/buildArtifact", HttpStatus.OK, byte[].class);

        for (final var verification : verifications) {
            verification.performVerification();
        }

        assertThat(participation.getInitializationState()).as("Participation should be initialized").isEqualTo(InitializationState.INITIALIZED);
        assertThat(artifact).as("No build artifact available for this plan").isEmpty();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
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

        // Set up mock requests for start participation
        final var verifications = mockConnectorRequestsForStartParticipation(exercise, team.getParticipantIdentifier(), team.getStudents());

        // Add a new student to the team
        User newStudent = ModelFactory.generateActivatedUsers("new-student", new String[] { "tumuser", "testgroup" }, Set.of(new Authority(AuthoritiesConstants.USER)), 1).get(0);
        newStudent = userRepo.save(newStudent);
        team.addStudents(newStudent);

        // Mock repository write permission give call
        final var repositorySlug = (exercise.getProjectKey() + "-" + team.getParticipantIdentifier()).toLowerCase();
        bitbucketRequestMockProvider.mockGiveWritePermission(exercise, repositorySlug, newStudent.getLogin());

        // Start participation with original team
        participationService.startExercise(exercise, team, false);

        // Update team with new student after participation has already started
        Team serverTeam = request.putWithResponseBody("/api/exercises/" + exercise.getId() + "/teams/" + team.getId(), team, Team.class, HttpStatus.OK);
        assertThat(serverTeam.getStudents()).as("Team students were updated correctly").hasSize(numberOfStudents + 1); // new student was added

        for (final var verification : verifications) {
            verification.performVerification();
        }
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void configureRepository_createUserWhenLtiUserIsNotExistent() throws Exception {
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

        // Set up mock requests for start participation
        mockConnectorRequestsForStartParticipation(exercise, team.getParticipantIdentifier(), team.getStudents(), false);

        // Start participation with original team
        participationService.startExercise(exercise, team, false);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
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

        // Set up mock requests for start participation
        final var verifications = mockConnectorRequestsForStartParticipation(exercise, team.getParticipantIdentifier(), team.getStudents());

        // Remove the first student from the team
        User firstStudent = students.iterator().next();
        team.removeStudents(firstStudent);

        // Mock repository access removal call
        final var repositorySlug = (exercise.getProjectKey() + "-" + team.getParticipantIdentifier()).toLowerCase();
        bitbucketRequestMockProvider.mockRemoveMemberFromRepository(repositorySlug, exercise.getProjectKey(), firstStudent);

        // Start participation with original team
        participationService.startExercise(exercise, team, false);

        // Update team with removed student
        Team serverTeam = request.putWithResponseBody("/api/exercises/" + exercise.getId() + "/teams/" + team.getId(), team, Team.class, HttpStatus.OK);
        assertThat(serverTeam.getStudents()).as("Team students were updated correctly").hasSize(numberOfStudents - 1); // first student was removed

        for (final var verification : verifications) {
            verification.performVerification();
        }
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
