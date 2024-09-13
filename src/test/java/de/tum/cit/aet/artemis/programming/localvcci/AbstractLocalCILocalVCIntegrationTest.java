package de.tum.cit.aet.artemis.programming.localvcci;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.exam.repository.ExamRepository;
import de.tum.cit.aet.artemis.exam.repository.StudentExamRepository;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.repository.AuxiliaryRepositoryRepository;
import de.tum.cit.aet.artemis.programming.service.StaticCodeAnalysisService;
import de.tum.cit.aet.artemis.programming.service.aeolus.AeolusTemplateService;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

public class AbstractLocalCILocalVCIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    protected static final String TEST_PREFIX = "localvclocalciintegration";

    @Autowired
    protected TeamRepository teamRepository;

    @Autowired
    protected ExamRepository examRepository;

    @Autowired
    protected StudentExamRepository studentExamRepository;

    @Autowired
    protected UserUtilService userUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    protected ParticipationUtilService participationUtilService;

    @Autowired
    private StaticCodeAnalysisService staticCodeAnalysisService;

    @Autowired
    protected AuxiliaryRepositoryRepository auxiliaryRepositoryRepository;

    @Autowired
    private AeolusTemplateService aeolusTemplateService;

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
    void initUsersAndExercise() throws JsonProcessingException {
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
        programmingExercise.getBuildConfig().setBuildPlanConfiguration(new ObjectMapper().writeValueAsString(aeolusTemplateService.getDefaultWindfileFor(programmingExercise)));
        programmingExerciseBuildConfigRepository.save(programmingExercise.getBuildConfig());
        programmingExerciseRepository.save(programmingExercise);
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(programmingExercise.getId()).orElseThrow();
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

        assignmentRepositorySlug = localVCLocalCITestService.getRepositorySlug(projectKey1, student1Login);

        testsRepositorySlug = localVCLocalCITestService.getRepositorySlug(projectKey1, "tests");

        localVCLocalCITestService.addTestCases(programmingExercise);
    }

    @AfterEach
    void tearDown() {
        buildJobRepository.deleteAll();
    }
}
