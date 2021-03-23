package de.tum.in.www1.artemis.service.connectors.bamboo;

import static de.tum.in.www1.artemis.config.Constants.*;
import static de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService.RepositoryCheckoutPath;
import static de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService.getDockerImageName;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import com.atlassian.bamboo.specs.api.builders.AtlassianModule;
import com.atlassian.bamboo.specs.api.builders.BambooKey;
import com.atlassian.bamboo.specs.api.builders.applink.ApplicationLink;
import com.atlassian.bamboo.specs.api.builders.docker.DockerConfiguration;
import com.atlassian.bamboo.specs.api.builders.notification.AnyNotificationRecipient;
import com.atlassian.bamboo.specs.api.builders.notification.Notification;
import com.atlassian.bamboo.specs.api.builders.permission.PermissionType;
import com.atlassian.bamboo.specs.api.builders.permission.Permissions;
import com.atlassian.bamboo.specs.api.builders.permission.PlanPermissions;
import com.atlassian.bamboo.specs.api.builders.plan.Job;
import com.atlassian.bamboo.specs.api.builders.plan.Plan;
import com.atlassian.bamboo.specs.api.builders.plan.PlanIdentifier;
import com.atlassian.bamboo.specs.api.builders.plan.Stage;
import com.atlassian.bamboo.specs.api.builders.plan.artifact.Artifact;
import com.atlassian.bamboo.specs.api.builders.plan.branches.BranchCleanup;
import com.atlassian.bamboo.specs.api.builders.plan.branches.PlanBranchManagement;
import com.atlassian.bamboo.specs.api.builders.plan.configuration.ConcurrentBuilds;
import com.atlassian.bamboo.specs.api.builders.project.Project;
import com.atlassian.bamboo.specs.api.builders.repository.VcsChangeDetection;
import com.atlassian.bamboo.specs.api.builders.repository.VcsRepository;
import com.atlassian.bamboo.specs.api.builders.repository.VcsRepositoryIdentifier;
import com.atlassian.bamboo.specs.api.builders.task.Task;
import com.atlassian.bamboo.specs.builders.notification.PlanCompletedNotification;
import com.atlassian.bamboo.specs.builders.repository.bitbucket.server.BitbucketServerRepository;
import com.atlassian.bamboo.specs.builders.repository.viewer.BitbucketServerRepositoryViewer;
import com.atlassian.bamboo.specs.builders.task.*;
import com.atlassian.bamboo.specs.builders.trigger.BitbucketServerTrigger;
import com.atlassian.bamboo.specs.model.task.TestParserTaskProperties;
import com.atlassian.bamboo.specs.util.BambooServer;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.enumeration.BuildPlanType;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.StaticCodeAnalysisTool;
import de.tum.in.www1.artemis.exception.ContinuousIntegrationBuildPlanException;
import de.tum.in.www1.artemis.service.ResourceLoaderService;
import io.github.jhipster.config.JHipsterConstants;

@Service
@Profile("bamboo")
public class BambooBuildPlanService {

    @Value("${artemis.continuous-integration.user}")
    private String bambooUser;

    @Value("${artemis.user-management.external.admin-group-name}")
    private String adminGroupName;

    @Value("${server.url}")
    private URL artemisServerUrl;

    @Value("${artemis.continuous-integration.vcs-application-link-name}")
    private String vcsApplicationLinkName;

    private final ResourceLoaderService resourceLoaderService;

    private final BambooServer bambooServer;

    private final Environment env;

    public BambooBuildPlanService(ResourceLoaderService resourceLoaderService, BambooServer bambooServer, Environment env) {
        this.resourceLoaderService = resourceLoaderService;
        this.bambooServer = bambooServer;
        this.env = env;
    }

    /**
     * Creates a Build Plan for a Programming Exercise
     *
     * @param programmingExercise    programming exercise with the required
     *                               information to create the base build plan
     * @param planKey                the key of the build plan
     * @param repositoryName         the slug of the assignment repository (used to
     *                               separate between exercise and solution), i.e.
     *                               the unique identifier
     * @param testRepositoryName     the slug of the test repository, i.e. the
     *                               unique identifier
     * @param solutionRepositoryName the slug of the solution repository, i.e. the
     *                               unique identifier
     */
    public void createBuildPlanForExercise(ProgrammingExercise programmingExercise, String planKey, String repositoryName, String testRepositoryName,
            String solutionRepositoryName) {
        final String planDescription = planKey + " Build Plan for Exercise " + programmingExercise.getTitle();
        final String projectKey = programmingExercise.getProjectKey();
        final String projectName = programmingExercise.getProjectName();

        Plan plan = createDefaultBuildPlan(planKey, planDescription, projectKey, projectName, repositoryName, testRepositoryName,
                programmingExercise.getCheckoutSolutionRepository(), solutionRepositoryName)
                        .stages(createBuildStage(programmingExercise.getProgrammingLanguage(), programmingExercise.hasSequentialTestRuns(),
                                programmingExercise.isStaticCodeAnalysisEnabled(), programmingExercise.getCheckoutSolutionRepository()));

        bambooServer.publish(plan);

        setBuildPlanPermissionsForExercise(programmingExercise, plan.getKey().toString());
    }

    /**
     * Set Build Plan Permissions for admins, instructors and teaching assistants.
     *
     * @param programmingExercise a programming exercise with the required
     *                            information to set the needed build plan
     *                            permissions
     * @param planKey             The name of the source plan
     */
    public void setBuildPlanPermissionsForExercise(ProgrammingExercise programmingExercise, String planKey) {
        // Get course over exerciseGroup in exam mode
        Course course = programmingExercise.getCourseViaExerciseGroupOrCourseMember();

        final String teachingAssistantGroupName = course.getTeachingAssistantGroupName();
        final String instructorGroupName = course.getInstructorGroupName();
        final PlanPermissions planPermission = generatePlanPermissions(programmingExercise.getProjectKey(), planKey, teachingAssistantGroupName, instructorGroupName,
                adminGroupName);
        bambooServer.publish(planPermission);
    }

    private Project createBuildProject(String name, String key) {
        return new Project().key(key).name(name);
    }

    private Stage createBuildStage(ProgrammingLanguage programmingLanguage, final boolean sequentialBuildRuns, Boolean staticCodeAnalysisEnabled,
            boolean checkoutSolutionRepository) {
        final var assignmentPath = RepositoryCheckoutPath.ASSIGNMENT.forProgrammingLanguage(programmingLanguage);
        final var testPath = RepositoryCheckoutPath.TEST.forProgrammingLanguage(programmingLanguage);
        VcsCheckoutTask checkoutTask;
        if (checkoutSolutionRepository) {
            final var solutionPath = RepositoryCheckoutPath.SOLUTION.forProgrammingLanguage(programmingLanguage);
            checkoutTask = createCheckoutTask(assignmentPath, testPath, Optional.of(solutionPath));
        }
        else {
            checkoutTask = createCheckoutTask(assignmentPath, testPath);
        }
        Stage defaultStage = new Stage("Default Stage");
        Job defaultJob = new Job("Default Job", new BambooKey("JOB1")).cleanWorkingDirectory(true);

        /*
         * We need the profiles to not run the jobs within Docker containers in the dev-setup as the Bamboo server itself runs in a Docker container when developing.
         */
        Collection<String> activeProfiles = Arrays.asList(env.getActiveProfiles());

        // Do not run the builds in extra docker containers if the dev-profile is active
        if (!activeProfiles.contains(JHipsterConstants.SPRING_PROFILE_DEVELOPMENT)) {
            defaultJob.dockerConfiguration(dockerConfigurationImageNameFor(programmingLanguage));
        }
        switch (programmingLanguage) {
            case JAVA, KOTLIN -> {
                if (Boolean.TRUE.equals(staticCodeAnalysisEnabled)) {
                    // Create artifacts and a final task for the execution of static code analysis
                    List<StaticCodeAnalysisTool> staticCodeAnalysisTools = StaticCodeAnalysisTool.getToolsForProgrammingLanguage(ProgrammingLanguage.JAVA);
                    String command = StaticCodeAnalysisTool.createBuildPlanCommandForProgrammingLanguage(ProgrammingLanguage.JAVA);
                    Artifact[] artifacts = staticCodeAnalysisTools.stream()
                            .map(tool -> new Artifact().name(tool.getArtifactLabel()).location("target").copyPattern(tool.getFilePattern()).shared(false)).toArray(Artifact[]::new);
                    defaultJob.finalTasks(new MavenTask().goal(command).jdk("JDK").executableLabel("Maven 3").description("Static Code Analysis").hasTests(false));
                    defaultJob.artifacts(artifacts);
                }

                if (!sequentialBuildRuns) {
                    return defaultStage
                            .jobs(defaultJob.tasks(checkoutTask, new MavenTask().goal("clean test").jdk("JDK").executableLabel("Maven 3").description("Tests").hasTests(true)));
                }
                else {
                    return defaultStage.jobs(defaultJob.tasks(checkoutTask,
                            new MavenTask().goal("clean test").workingSubdirectory("structural").jdk("JDK").executableLabel("Maven 3").description("Structural tests")
                                    .hasTests(true),
                            new MavenTask().goal("clean test").workingSubdirectory("behavior").jdk("JDK").executableLabel("Maven 3").description("Behavior tests").hasTests(true)));
                }
            }
            case PYTHON -> {
                return createDefaultStage(programmingLanguage, sequentialBuildRuns, checkoutTask, defaultStage, defaultJob, "test-reports/*results.xml");
            }
            case C -> {
                // Default tasks:
                var tasks = readScriptTasksFromTemplate(programmingLanguage, sequentialBuildRuns, false);
                tasks.add(0, checkoutTask);
                defaultJob.tasks(tasks.toArray(new Task[0]));
                // Final tasks:
                final TestParserTask testParserTask = new TestParserTask(TestParserTaskProperties.TestType.JUNIT).resultDirectories("test-reports/*results.xml");
                final ScriptTask cleanupTask = new ScriptTask().description("cleanup").inlineBody("sudo rm -rf tests/\nsudo rm -rf assignment/\nsudo rm -rf test-reports/");
                defaultJob.finalTasks(new Task[] { testParserTask, cleanupTask });
                defaultStage.jobs(defaultJob);
                return defaultStage;
            }
            case HASKELL, OCAML -> {
                return createDefaultStage(programmingLanguage, sequentialBuildRuns, checkoutTask, defaultStage, defaultJob, "**/test-reports/*.xml");
            }
            case VHDL, ASSEMBLER -> {
                return createDefaultStage(programmingLanguage, sequentialBuildRuns, checkoutTask, defaultStage, defaultJob, "**/result.xml");
            }
            case SWIFT -> {
                final var testParserTask = new TestParserTask(TestParserTaskProperties.TestType.JUNIT).resultDirectories("**/tests.xml");
                var tasks = readScriptTasksFromTemplate(programmingLanguage, sequentialBuildRuns, false);
                tasks.add(0, checkoutTask);
                defaultJob.tasks(tasks.toArray(new Task[0])).finalTasks(testParserTask);
                if (Boolean.TRUE.equals(staticCodeAnalysisEnabled)) {
                    // Create artifacts and a final task for the execution of static code analysis
                    List<StaticCodeAnalysisTool> staticCodeAnalysisTools = StaticCodeAnalysisTool.getToolsForProgrammingLanguage(ProgrammingLanguage.SWIFT);
                    Artifact[] artifacts = staticCodeAnalysisTools.stream()
                            .map(tool -> new Artifact().name(tool.getArtifactLabel()).location("target").copyPattern(tool.getFilePattern()).shared(false)).toArray(Artifact[]::new);
                    defaultJob.artifacts(artifacts);
                    var scaTasks = readScriptTasksFromTemplate(programmingLanguage, false, true);
                    defaultJob.finalTasks(scaTasks.toArray(new Task[0]));
                }
                return defaultStage.jobs(defaultJob);
            }
            // this is needed, otherwise the compiler complaints with missing return
            // statement
            default -> throw new IllegalArgumentException("No build stage setup for programming language " + programmingLanguage);
        }
    }

    private Stage createDefaultStage(ProgrammingLanguage programmingLanguage, boolean sequentialBuildRuns, VcsCheckoutTask checkoutTask, Stage defaultStage, Job defaultJob,
            String resultDirectories) {
        final var testParserTask = new TestParserTask(TestParserTaskProperties.TestType.JUNIT).resultDirectories(resultDirectories);
        var tasks = readScriptTasksFromTemplate(programmingLanguage, sequentialBuildRuns, false);
        tasks.add(0, checkoutTask);
        return defaultStage.jobs(defaultJob.tasks(tasks.toArray(new Task[0])).finalTasks(testParserTask));
    }

    private Plan createDefaultBuildPlan(String planKey, String planDescription, String projectKey, String projectName, String repositoryName, String vcsTestRepositorySlug,
            boolean checkoutSolutionRepository, String vcsSolutionRepositorySlug) {
        List<VcsRepositoryIdentifier> vcsTriggerRepositories = new LinkedList<>();
        // Trigger the build when a commit is pushed to the ASSIGNMENT_REPO.
        vcsTriggerRepositories.add(new VcsRepositoryIdentifier(ASSIGNMENT_REPO_NAME));
        // Trigger the build when a commit is pushed to the TEST_REPO only for the
        // solution repository!
        if (planKey.equals(BuildPlanType.SOLUTION.getName())) {
            vcsTriggerRepositories.add(new VcsRepositoryIdentifier(TEST_REPO_NAME));
        }

        List<VcsRepository<?, ?>> planRepositories = new ArrayList<>();
        planRepositories.add(createBuildPlanRepository(ASSIGNMENT_REPO_NAME, projectKey, repositoryName));
        planRepositories.add(createBuildPlanRepository(TEST_REPO_NAME, projectKey, vcsTestRepositorySlug));
        if (checkoutSolutionRepository) {
            planRepositories.add(createBuildPlanRepository(SOLUTION_REPO_NAME, projectKey, vcsSolutionRepositorySlug));
        }

        return new Plan(createBuildProject(projectName, projectKey), planKey, planKey).description(planDescription)
                .pluginConfigurations(new ConcurrentBuilds().useSystemWideDefault(true)).planRepositories(planRepositories.toArray(VcsRepository[]::new))
                .triggers(new BitbucketServerTrigger().selectedTriggeringRepositories(vcsTriggerRepositories.toArray(new VcsRepositoryIdentifier[0])))
                .planBranchManagement(createPlanBranchManagement()).notifications(createNotification());
    }

    private VcsCheckoutTask createCheckoutTask(String assignmentPath, String testPath) {
        return createCheckoutTask(assignmentPath, testPath, Optional.empty());
    }

    private VcsCheckoutTask createCheckoutTask(String assignmentPath, String testPath, Optional<String> solutionPath) {
        List<CheckoutItem> checkoutItems = new ArrayList<>();
        checkoutItems.add(new CheckoutItem().repository(new VcsRepositoryIdentifier().name(TEST_REPO_NAME)).path(testPath));
        checkoutItems.add(new CheckoutItem().repository(new VcsRepositoryIdentifier().name(ASSIGNMENT_REPO_NAME)).path(assignmentPath));
        solutionPath.ifPresent(s -> checkoutItems.add(new CheckoutItem().repository(new VcsRepositoryIdentifier().name(SOLUTION_REPO_NAME)).path(s)));
        return new VcsCheckoutTask().description("Checkout Default Repository").checkoutItems(checkoutItems.toArray(CheckoutItem[]::new));
    }

    private PlanBranchManagement createPlanBranchManagement() {
        return new PlanBranchManagement().delete(new BranchCleanup()).notificationForCommitters();
    }

    private Notification createNotification() {
        return new Notification().type(new PlanCompletedNotification())
                .recipients(new AnyNotificationRecipient(new AtlassianModule("de.tum.in.www1.bamboo-server:recipient.server"))
                        .recipientString(artemisServerUrl + NEW_RESULT_RESOURCE_API_PATH));
    }

    private BitbucketServerRepository createBuildPlanRepository(String name, String vcsProjectKey, String repositorySlug) {
        return new BitbucketServerRepository().name(name).repositoryViewer(new BitbucketServerRepositoryViewer()).server(new ApplicationLink().name(vcsApplicationLinkName))
                // make sure to use lower case to avoid problems in change detection between
                // Bamboo and Bitbucket
                .projectKey(vcsProjectKey).repositorySlug(repositorySlug.toLowerCase()).shallowClonesEnabled(true).remoteAgentCacheEnabled(false)
                .changeDetection(new VcsChangeDetection());
    }

    private PlanPermissions generatePlanPermissions(String bambooProjectKey, String bambooPlanKey, @Nullable String teachingAssistantGroupName, String instructorGroupName,
            String adminGroupName) {
        var permissions = new Permissions().userPermissions(bambooUser, PermissionType.EDIT, PermissionType.BUILD, PermissionType.CLONE, PermissionType.VIEW, PermissionType.ADMIN)
                .groupPermissions(adminGroupName, PermissionType.CLONE, PermissionType.BUILD, PermissionType.EDIT, PermissionType.VIEW, PermissionType.ADMIN)
                .groupPermissions(instructorGroupName, PermissionType.CLONE, PermissionType.BUILD, PermissionType.EDIT, PermissionType.VIEW, PermissionType.ADMIN);
        if (teachingAssistantGroupName != null) {
            permissions = permissions.groupPermissions(teachingAssistantGroupName, PermissionType.BUILD, PermissionType.EDIT, PermissionType.VIEW);
        }
        return new PlanPermissions(new PlanIdentifier(bambooProjectKey, bambooPlanKey)).permissions(permissions);
    }

    private List<Task<?, ?>> readScriptTasksFromTemplate(final ProgrammingLanguage programmingLanguage, final boolean sequentialBuildRuns, final boolean getScaTasks) {
        final var directoryPattern = "templates/bamboo/" + programmingLanguage.name().toLowerCase()
                + (getScaTasks ? "/staticCodeAnalysisRuns/" : sequentialBuildRuns ? "/sequentialRuns/" : "/regularRuns/") + "*.sh";
        try {
            List<Task<?, ?>> tasks = new ArrayList<>();
            final var scriptResources = Arrays.asList(resourceLoaderService.getResources(directoryPattern));
            scriptResources.sort(Comparator.comparing(Resource::getFilename));
            for (final var resource : scriptResources) {
                // 1_some_description.sh --> "some description"
                String fileName = resource.getFilename();
                if (fileName != null) {
                    final var descriptionElements = Arrays.stream(fileName.split("\\.")[0] // cut .sh suffix
                            .split("_")).collect(Collectors.toList());
                    descriptionElements.remove(0); // Remove the index prefix: 1 some description --> some description
                    final var scriptDescription = String.join(" ", descriptionElements);
                    try (final var inputStream = resource.getInputStream()) {
                        tasks.add(new ScriptTask().description(scriptDescription).inlineBody(IOUtils.toString(inputStream, Charset.defaultCharset())));
                    }
                }
            }

            return tasks;
        }
        catch (IOException e) {
            throw new ContinuousIntegrationBuildPlanException("Unable to load template build plans", e);
        }
    }

    private DockerConfiguration dockerConfigurationImageNameFor(ProgrammingLanguage language) {
        var dockerImage = getDockerImageName(language);
        return new DockerConfiguration().image(dockerImage);
    }
}
