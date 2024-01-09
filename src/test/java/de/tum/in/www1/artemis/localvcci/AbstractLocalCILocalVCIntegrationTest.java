package de.tum.in.www1.artemis.localvcci;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;

import de.tum.in.www1.artemis.AbstractSpringIntegrationLocalCILocalVCTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.ProjectType;
import de.tum.in.www1.artemis.domain.participation.SolutionProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.TemplateProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseUtilService;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.StaticCodeAnalysisService;
import de.tum.in.www1.artemis.user.UserUtilService;

public class AbstractLocalCILocalVCIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    protected static final String TEST_PREFIX = "localvclocalciintegration";

    @Autowired
    protected TeamRepository teamRepository;

    @Autowired
    protected ExerciseGroupRepository exerciseGroupRepository;

    @Autowired
    protected ExamRepository examRepository;

    @Autowired
    protected StudentExamRepository studentExamRepository;

    @Autowired
    protected UserUtilService userUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    protected ParticipationUtilService participationUtilService;

    @Autowired
    private StaticCodeAnalysisService staticCodeAnalysisService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuxiliaryRepositoryRepository auxiliaryRepositoryRepository;

    @Value("${artemis.version-control.user}")
    protected String localVCBaseUsername;

    @LocalServerPort
    protected int port;

    // The error messages returned by JGit contain these Strings that correspond to the HTTP status codes.
    protected static final String NOT_FOUND = "not found";

    protected static final String NOT_AUTHORIZED = "not authorized";

    protected static final String INTERNAL_SERVER_ERROR = "500";

    protected static final String FORBIDDEN = "not permitted";

    protected Course course;

    protected ProgrammingExercise programmingExercise;

    protected TemplateProgrammingExerciseParticipation templateParticipation;

    protected SolutionProgrammingExerciseParticipation solutionParticipation;

    protected String student1Login;

    protected User student1;

    protected String student2Login;

    protected String tutor1Login;

    protected String instructor1Login;

    protected User instructor1;

    protected String instructor2Login;

    protected User instructor2;

    protected String projectKey1;

    protected String assignmentRepositorySlug;

    protected String templateRepositorySlug;

    protected String solutionRepositorySlug;

    protected String testsRepositorySlug;

    protected String auxiliaryRepositorySlug;

    @BeforeEach
    void initUsersAndExercise() {
        // The port cannot be injected into the LocalVCLocalCITestService because {local.server.port} is not available when the class is instantiated.
        // Thus, "inject" the port from here.
        localVCLocalCITestService.setPort(port);

        List<User> users = userUtilService.addUsers(TEST_PREFIX, 2, 1, 0, 2);
        student1Login = TEST_PREFIX + "student1";
        student1 = users.stream().filter(user -> student1Login.equals(user.getLogin())).findFirst().orElseThrow();
        student2Login = TEST_PREFIX + "student2";
        tutor1Login = TEST_PREFIX + "tutor1";
        instructor1Login = TEST_PREFIX + "instructor1";
        instructor1 = users.stream().filter(user -> instructor1Login.equals(user.getLogin())).findFirst().orElseThrow();
        instructor2Login = TEST_PREFIX + "instructor2";
        instructor2 = users.stream().filter(user -> instructor2Login.equals(user.getLogin())).findFirst().orElseThrow();
        // Remove instructor2 from the instructor group of the course.
        instructor2.setGroups(Set.of());
        userRepository.save(instructor2);

        course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        programmingExercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        projectKey1 = programmingExercise.getProjectKey();
        programmingExercise.setReleaseDate(ZonedDateTime.now().minusDays(1));
        programmingExercise.setProjectType(ProjectType.PLAIN_GRADLE);
        programmingExercise.setAllowOfflineIde(true);
        programmingExercise.setTestRepositoryUri(localVCBaseUrl + "/git/" + projectKey1 + "/" + projectKey1.toLowerCase() + "-tests.git");
        programmingExerciseRepository.save(programmingExercise);
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsById(programmingExercise.getId()).orElseThrow();
        staticCodeAnalysisService.createDefaultCategories(programmingExercise);

        // Set the correct repository URIs for the template and the solution participation.
        templateRepositorySlug = localVCLocalCITestService.getRepositorySlug(projectKey1, "exercise");
        templateParticipation = programmingExercise.getTemplateParticipation();
        templateParticipation.setRepositoryUri(localVCBaseUrl + "/git/" + projectKey1 + "/" + templateRepositorySlug + ".git");
        templateProgrammingExerciseParticipationRepository.save(templateParticipation);
        solutionRepositorySlug = localVCLocalCITestService.getRepositorySlug(projectKey1, "solution");
        solutionParticipation = programmingExercise.getSolutionParticipation();
        solutionParticipation.setRepositoryUri(localVCBaseUrl + "/git/" + projectKey1 + "/" + solutionRepositorySlug + ".git");
        solutionProgrammingExerciseParticipationRepository.save(solutionParticipation);

        auxiliaryRepositorySlug = localVCLocalCITestService.getRepositorySlug(projectKey1, "auxiliary");
        List<AuxiliaryRepository> auxiliaryRepositories = auxiliaryRepositoryRepository.findAll();
        AuxiliaryRepository auxiliaryRepository = new AuxiliaryRepository();
        auxiliaryRepository.setName("auxiliary");
        auxiliaryRepository.setCheckoutDirectory("aux");
        auxiliaryRepository.setRepositoryUri(localVCBaseUrl + "/git/" + projectKey1 + "/" + auxiliaryRepositorySlug + ".git");
        auxiliaryRepositoryRepository.save(auxiliaryRepository);
        auxiliaryRepository.setExercise(programmingExercise);
        auxiliaryRepositories.add(auxiliaryRepository);

        programmingExercise.setAuxiliaryRepositories(auxiliaryRepositories);
        programmingExerciseRepository.save(programmingExercise);

        assignmentRepositorySlug = localVCLocalCITestService.getRepositorySlug(projectKey1, student1Login);

        testsRepositorySlug = localVCLocalCITestService.getRepositorySlug(projectKey1, "tests");

        localVCLocalCITestService.addTestCases(programmingExercise);
    }
}
