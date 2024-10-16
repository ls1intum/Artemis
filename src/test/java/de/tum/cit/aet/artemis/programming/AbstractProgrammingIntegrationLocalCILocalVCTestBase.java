package de.tum.cit.aet.artemis.programming;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.apache.sshd.server.SshServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.buildagent.service.SharedQueueProcessingService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.PageableSearchUtilService;
import de.tum.cit.aet.artemis.exam.repository.ExamRepository;
import de.tum.cit.aet.artemis.exam.test_repository.StudentExamTestRepository;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseIntegrationTestService;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.hestia.util.HestiaUtilTestService;
import de.tum.cit.aet.artemis.programming.repository.AuxiliaryRepositoryRepository;
import de.tum.cit.aet.artemis.programming.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.hestia.CoverageFileReportRepository;
import de.tum.cit.aet.artemis.programming.repository.hestia.CoverageReportRepository;
import de.tum.cit.aet.artemis.programming.repository.hestia.ProgrammingExerciseGitDiffReportRepository;
import de.tum.cit.aet.artemis.programming.repository.hestia.TestwiseCoverageReportEntryRepository;
import de.tum.cit.aet.artemis.programming.service.BuildLogEntryService;
import de.tum.cit.aet.artemis.programming.service.BuildScriptProviderService;
import de.tum.cit.aet.artemis.programming.service.ParticipationVcsAccessTokenService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseImportBasicService;
import de.tum.cit.aet.artemis.programming.service.aeolus.AeolusTemplateService;
import de.tum.cit.aet.artemis.programming.service.hestia.ProgrammingExerciseGitDiffReportService;
import de.tum.cit.aet.artemis.programming.service.hestia.TestwiseCoverageService;
import de.tum.cit.aet.artemis.programming.service.hestia.behavioral.BehavioralTestCaseService;
import de.tum.cit.aet.artemis.programming.service.hestia.structural.StructuralTestCaseService;
import de.tum.cit.aet.artemis.programming.service.localci.LocalCIResultService;
import de.tum.cit.aet.artemis.programming.service.localci.LocalCITriggerService;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCServletService;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingSubmissionTestRepository;

public abstract class AbstractProgrammingIntegrationLocalCILocalVCTestBase extends AbstractProgrammingIntegrationLocalCILocalVCTest {

    // Config
    @Value("${artemis.version-control.user}")
    protected String localVCBaseUsername;

    @LocalServerPort
    protected int port;

    @Autowired
    protected SshServer sshServer;

    // Repositories
    @Autowired
    protected AuxiliaryRepositoryRepository auxiliaryRepositoryRepository;

    @Autowired
    protected CoverageFileReportRepository coverageFileReportRepository;

    @Autowired
    protected CoverageReportRepository coverageReportRepository;

    @Autowired
    protected ProgrammingExerciseGitDiffReportRepository reportRepository;

    @Autowired
    protected ProgrammingExerciseTestRepository programmingExerciseRepository;

    @Autowired
    protected ProgrammingSubmissionTestRepository programmingSubmissionRepository;

    @Autowired
    protected SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseRepository;

    @Autowired
    protected TestwiseCoverageReportEntryRepository testwiseCoverageReportEntryRepository;

    // External Repositories
    @Autowired
    protected ExamRepository examRepository;

    @Autowired
    protected StudentExamTestRepository studentExamRepository;

    @Autowired
    protected TeamRepository teamRepository;

    // Services
    @Autowired
    protected AeolusTemplateService aeolusTemplateService;

    @Autowired
    protected BehavioralTestCaseService behavioralTestCaseService;

    @Autowired
    protected BuildLogEntryService buildLogEntryService;

    @Autowired
    protected BuildScriptProviderService buildScriptProviderService;

    @Autowired
    protected HestiaUtilTestService hestiaUtilTestService;

    @Autowired
    protected LocalCIResultService localCIResultService;

    @Autowired
    protected LocalVCServletService localVCServletService;

    @Autowired
    protected LocalCITriggerService localCITriggerService;

    @Autowired
    protected ParticipationVcsAccessTokenService participationVcsAccessTokenService;

    @Autowired
    protected ProgrammingExerciseImportBasicService programmingExerciseImportBasicService;

    @Autowired
    protected ProgrammingExerciseGitDiffReportService reportService;

    @Autowired
    protected SharedQueueProcessingService sharedQueueProcessingService;

    @Autowired
    protected StructuralTestCaseService structuralTestCaseService;

    @Autowired
    protected TestwiseCoverageService testwiseCoverageService;

    // External Services

    // Util Services

    // External Util services
    @Autowired
    protected ExerciseIntegrationTestService exerciseIntegrationTestService;

    @Autowired
    protected ExerciseUtilService exerciseUtilService;

    @Autowired
    protected PageableSearchUtilService pageableSearchUtilService;

    @Autowired
    protected UserUtilService userUtilService;

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

    protected abstract String getTestPrefix();

    @BeforeEach
    void initUsersAndExercise() throws JsonProcessingException {
        // The port cannot be injected into the LocalVCLocalCITestService because {local.server.port} is not available when the class is instantiated.
        // Thus, "inject" the port from here.
        localVCLocalCITestService.setPort(port);

        String testPrefix = getTestPrefix();
        List<User> users = userUtilService.addUsers(testPrefix, 2, 1, 0, 2);
        student1Login = testPrefix + "student1";
        student1 = users.stream().filter(user -> student1Login.equals(user.getLogin())).findFirst().orElseThrow();
        student2Login = testPrefix + "student2";
        tutor1Login = testPrefix + "tutor1";
        instructor1Login = testPrefix + "instructor1";
        instructor1 = users.stream().filter(user -> instructor1Login.equals(user.getLogin())).findFirst().orElseThrow();
        instructor2Login = testPrefix + "instructor2";
        instructor2 = users.stream().filter(user -> instructor2Login.equals(user.getLogin())).findFirst().orElseThrow();
        // Remove instructor2 from the instructor group of the course.
        instructor2.setGroups(Set.of());
        userTestRepository.save(instructor2);

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
