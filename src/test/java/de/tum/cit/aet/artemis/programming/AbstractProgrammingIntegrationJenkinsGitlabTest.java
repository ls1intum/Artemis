package de.tum.cit.aet.artemis.programming;

import java.net.URL;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.communication.test_repository.PostTestRepository;
import de.tum.cit.aet.artemis.core.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.CourseTestService;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.exam.repository.ExamRepository;
import de.tum.cit.aet.artemis.exam.test_repository.StudentExamTestRepository;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.modeling.util.ModelingExerciseUtilService;
import de.tum.cit.aet.artemis.plagiarism.repository.PlagiarismCaseRepository;
import de.tum.cit.aet.artemis.plagiarism.repository.PlagiarismComparisonRepository;
import de.tum.cit.aet.artemis.programming.repository.BuildLogStatisticsEntryRepository;
import de.tum.cit.aet.artemis.programming.repository.BuildPlanRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.service.BuildLogEntryService;
import de.tum.cit.aet.artemis.programming.service.ConsistencyCheckTestService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseGradingService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseImportService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingLanguageFeatureService;
import de.tum.cit.aet.artemis.programming.service.RepositoryAccessService;
import de.tum.cit.aet.artemis.programming.service.gitlab.GitLabPersonalAccessTokenManagementService;
import de.tum.cit.aet.artemis.programming.service.jenkins.JenkinsAuthorizationInterceptor;
import de.tum.cit.aet.artemis.programming.service.jenkins.JenkinsInternalUrlService;
import de.tum.cit.aet.artemis.programming.service.jenkins.build_plan.JenkinsPipelineScriptCreator;
import de.tum.cit.aet.artemis.programming.service.jenkins.jobs.JenkinsJobPermissionsService;
import de.tum.cit.aet.artemis.programming.service.jenkins.jobs.JenkinsJobService;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseStudentParticipationTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestCaseTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingSubmissionTestRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseResultTestService;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseTestService;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.programming.util.ProgrammingSubmissionAndResultIntegrationTestService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationJenkinsGitlabTest;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

public class AbstractProgrammingIntegrationJenkinsGitlabTest extends AbstractSpringIntegrationJenkinsGitlabTest {

    // Config

    @Value("${artemis.continuous-integration.artemis-authentication-token-value}")
    protected String ARTEMIS_AUTHENTICATION_TOKEN_VALUE;

    @Value("${artemis.version-control.url}")
    protected URL gitlabServerUrl;

    @Value("${artemis.git.name}")
    protected String artemisGitName;

    @Value("${artemis.git.email}")
    protected String artemisGitEmail;

    @Value("${artemis.continuous-integration.url}")
    protected URL jenkinsServerUrl;

    @Autowired
    protected RestTemplate restTemplate;

    @Autowired
    protected ObjectMapper objectMapper;

    // Repositories

    @Autowired
    protected BuildLogStatisticsEntryRepository buildLogStatisticsEntryRepository;

    @Autowired
    protected BuildPlanRepository buildPlanRepository;

    @Autowired
    protected ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    @Autowired
    protected ProgrammingExerciseStudentParticipationTestRepository participationRepository;

    @Autowired
    protected ProgrammingExerciseTestCaseTestRepository testCaseRepository;

    @Autowired
    protected ProgrammingExerciseTestRepository programmingExerciseRepository;

    @Autowired
    protected ProgrammingSubmissionTestRepository submissionRepository;

    // External Repositories

    @Autowired
    protected ChannelRepository channelRepository;

    @Autowired
    protected ExamRepository examRepository;

    @Autowired
    protected PlagiarismCaseRepository plagiarismCaseRepository;

    @Autowired
    protected PlagiarismComparisonRepository plagiarismComparisonRepository;

    @Autowired
    protected PostTestRepository postRepository;

    @Autowired
    protected StudentExamTestRepository studentExamRepository;

    @Autowired
    protected StudentParticipationTestRepository studentParticipationRepository;

    @Autowired
    protected UserTestRepository userRepository;

    // Services

    @Autowired
    protected BuildLogEntryService buildLogEntryService;

    @Autowired
    protected ConsistencyCheckTestService consistencyCheckTestService;

    @Autowired
    protected GitLabPersonalAccessTokenManagementService gitLabPersonalAccessTokenManagementService;

    @Autowired
    protected JenkinsAuthorizationInterceptor jenkinsAuthorizationInterceptor;

    @Autowired
    protected JenkinsInternalUrlService jenkinsInternalUrlService;

    @Autowired
    protected JenkinsJobPermissionsService jenkinsJobPermissionsService;

    @Autowired
    protected JenkinsJobService jenkinsJobService;

    @Autowired
    protected JenkinsPipelineScriptCreator jenkinsPipelineScriptCreator;

    @Autowired
    protected ProgrammingExerciseGradingService gradingService;

    @Autowired
    protected ProgrammingExerciseImportService programmingExerciseImportService;

    @Autowired
    protected ProgrammingExerciseIntegrationTestService programmingExerciseIntegrationTestService;

    @Autowired
    protected ProgrammingExerciseService programmingExerciseService;

    @Autowired
    protected ProgrammingLanguageFeatureService programmingLanguageFeatureService;

    @Autowired
    protected ProgrammingSubmissionAndResultIntegrationTestService testService;

    @Autowired
    protected RepositoryAccessService repositoryAccessService;

    // External Services

    // Util Services

    @Autowired
    protected ContinuousIntegrationTestService continuousIntegrationTestService;

    @Autowired
    protected ProgrammingExerciseResultTestService programmingExerciseResultTestService;

    @Autowired
    protected ProgrammingExerciseTestService programmingExerciseTestService;

    @Autowired
    protected ProgrammingExerciseUtilService programmingExerciseUtilService;

    // External Util Services

    @Autowired
    protected CourseTestService courseTestService;

    @Autowired
    protected CourseUtilService courseUtilService;

    @Autowired
    protected ExamUtilService examUtilService;

    @Autowired
    protected ExerciseUtilService exerciseUtilService;

    @Autowired
    protected ModelingExerciseUtilService modelingExerciseUtilService;

    @Autowired
    protected ParticipationUtilService participationUtilService;

    @Autowired
    protected TextExerciseUtilService textExerciseUtilService;

    @Autowired
    protected UserUtilService userUtilService;
}
