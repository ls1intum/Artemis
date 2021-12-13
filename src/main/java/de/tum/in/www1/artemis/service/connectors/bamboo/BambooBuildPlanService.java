package de.tum.in.www1.artemis.service.connectors.bamboo;

import static de.tum.in.www1.artemis.config.Constants.*;
import static de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService.getDockerImageName;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.*;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
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
import com.atlassian.bamboo.specs.api.builders.plan.*;
import com.atlassian.bamboo.specs.api.builders.plan.artifact.Artifact;
import com.atlassian.bamboo.specs.api.builders.plan.branches.BranchCleanup;
import com.atlassian.bamboo.specs.api.builders.plan.branches.PlanBranchManagement;
import com.atlassian.bamboo.specs.api.builders.plan.configuration.ConcurrentBuilds;
import com.atlassian.bamboo.specs.api.builders.project.Project;
import com.atlassian.bamboo.specs.api.builders.repository.VcsChangeDetection;
import com.atlassian.bamboo.specs.api.builders.repository.VcsRepository;
import com.atlassian.bamboo.specs.api.builders.repository.VcsRepositoryIdentifier;
import com.atlassian.bamboo.specs.api.builders.requirement.Requirement;
import com.atlassian.bamboo.specs.api.builders.task.Task;
import com.atlassian.bamboo.specs.builders.notification.PlanCompletedNotification;
import com.atlassian.bamboo.specs.builders.repository.bitbucket.server.BitbucketServerRepository;
import com.atlassian.bamboo.specs.builders.repository.viewer.BitbucketServerRepositoryViewer;
import com.atlassian.bamboo.specs.builders.task.*;
import com.atlassian.bamboo.specs.builders.trigger.BitbucketServerTrigger;
import com.atlassian.bamboo.specs.model.task.ScriptTaskProperties;
import com.atlassian.bamboo.specs.model.task.TestParserTaskProperties;
import com.atlassian.bamboo.specs.util.BambooServer;

import de.tum.in.www1.artemis.domain.AuxiliaryRepository;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.enumeration.BuildPlanType;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.ProjectType;
import de.tum.in.www1.artemis.domain.enumeration.StaticCodeAnalysisTool;
import de.tum.in.www1.artemis.exception.ContinuousIntegrationBuildPlanException;
import de.tum.in.www1.artemis.service.ResourceLoaderService;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService.RepositoryCheckoutPath;
import de.tum.in.www1.artemis.service.connectors.VersionControlService;
import tech.jhipster.config.JHipsterConstants;

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

    private final Optional<VersionControlService> versionControlService;

    public BambooBuildPlanService(ResourceLoaderService resourceLoaderService, BambooServer bambooServer, Environment env, Optional<VersionControlService> versionControlService) {
        this.resourceLoaderService = resourceLoaderService;
        this.bambooServer = bambooServer;
        this.env = env;
        this.versionControlService = versionControlService;
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
     * @param auxiliaryRepositories  List of auxiliary repositories to be included in
     *                               the build plan
     */
    public void createBuildPlanForExercise(ProgrammingExercise programmingExercise, String planKey, String repositoryName, String testRepositoryName, String solutionRepositoryName,
            List<AuxiliaryRepository.AuxRepoNameWithSlug> auxiliaryRepositories) {
        final String planDescription = planKey + " Build Plan for Exercise " + programmingExercise.getTitle();
        final String projectKey = programmingExercise.getProjectKey();
        final String projectName = programmingExercise.getProjectName();

        Plan plan = createDefaultBuildPlan(planKey, planDescription, projectKey, projectName, repositoryName, testRepositoryName,
                programmingExercise.getCheckoutSolutionRepository(), solutionRepositoryName, auxiliaryRepositories)
                        .stages(createBuildStage(programmingExercise.getProgrammingLanguage(), programmingExercise.getProjectType(), programmingExercise.getPackageName(),
                                programmingExercise.hasSequentialTestRuns(), programmingExercise.isStaticCodeAnalysisEnabled(), programmingExercise.getCheckoutSolutionRepository(),
                                programmingExercise.getAuxiliaryRepositoriesForBuildPlan()));

        bambooServer.publish(plan);
        setBuildPlanPermissionsForExercise(programmingExercise, plan.getKey().toString());
    }

    /**
     * Set Build Plan Permissions for admins, instructors, editors and teaching assistants.
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
        final String editorGroupName = course.getEditorGroupName();
        final String instructorGroupName = course.getInstructorGroupName();
        final PlanPermissions planPermission = generatePlanPermissions(programmingExercise.getProjectKey(), planKey, teachingAssistantGroupName, editorGroupName,
                instructorGroupName, adminGroupName);
        bambooServer.publish(planPermission);
    }

    private Project createBuildProject(String name, String key) {
        return new Project().key(key).name(name);
    }

    private Stage createBuildStage(ProgrammingLanguage programmingLanguage, ProjectType projectType, String packageName, final boolean sequentialBuildRuns,
            Boolean staticCodeAnalysisEnabled, boolean checkoutSolutionRepository, List<AuxiliaryRepository> auxiliaryRepositories) {
        final var assignmentPath = RepositoryCheckoutPath.ASSIGNMENT.forProgrammingLanguage(programmingLanguage);
        final var testPath = RepositoryCheckoutPath.TEST.forProgrammingLanguage(programmingLanguage);
        VcsCheckoutTask checkoutTask;
        if (checkoutSolutionRepository) {
            final var solutionPath = RepositoryCheckoutPath.SOLUTION.forProgrammingLanguage(programmingLanguage);
            checkoutTask = createCheckoutTask(assignmentPath, testPath, Optional.of(solutionPath), auxiliaryRepositories);
        }
        else {
            checkoutTask = createCheckoutTask(assignmentPath, testPath, auxiliaryRepositories);
        }
        Stage defaultStage = new Stage("Default Stage");
        Job defaultJob = new Job("Default Job", new BambooKey("JOB1")).cleanWorkingDirectory(true);

        /*
         * We need the profiles to not run the jobs within Docker containers in the dev-setup as the Bamboo server itself runs in a Docker container when developing.
         */
        Collection<String> activeProfiles = Arrays.asList(env.getActiveProfiles());

        // Do not run the builds in extra docker containers if the dev-profile is active
        // Xcode has no dockerfile, it only runs on agents (e.g. sb2-agent-0050562fddde)
        if (!activeProfiles.contains(JHipsterConstants.SPRING_PROFILE_DEVELOPMENT) && !ProjectType.XCODE.equals(projectType)) {
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
                var tasks = readScriptTasksFromTemplate(programmingLanguage, "", sequentialBuildRuns, false, null);
                tasks.add(0, checkoutTask);
                defaultJob.tasks(tasks.toArray(new Task[0]));

                // Final tasks:
                final TestParserTask testParserTask = new TestParserTask(TestParserTaskProperties.TestType.JUNIT).resultDirectories("test-reports/*results.xml");
                defaultJob.finalTasks(testParserTask);

                if (Boolean.TRUE.equals(staticCodeAnalysisEnabled)) {
                    // Create artifacts and a final task for the execution of static code analysis
                    List<StaticCodeAnalysisTool> staticCodeAnalysisTools = StaticCodeAnalysisTool.getToolsForProgrammingLanguage(ProgrammingLanguage.C);
                    Artifact[] artifacts = staticCodeAnalysisTools.stream()
                            .map(tool -> new Artifact().name(tool.getArtifactLabel()).location("target").copyPattern(tool.getFilePattern()).shared(false)).toArray(Artifact[]::new);
                    defaultJob.artifacts(artifacts);
                    var scaTasks = readScriptTasksFromTemplate(programmingLanguage, "", false, true, null);
                    defaultJob.finalTasks(scaTasks.toArray(new Task[0]));
                }

                // Do not remove target, so the report can be sent to Artemis
                final ScriptTask cleanupTask = new ScriptTask().description("cleanup").inlineBody("""
                        sudo rm -rf tests/
                        sudo rm -rf assignment/
                        sudo rm -rf test-reports/""");
                defaultJob.finalTasks(cleanupTask);
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
                var isXcodeProject = ProjectType.XCODE.equals(projectType);
                var subDirectory = isXcodeProject ? "/xcode" : "";
                Map<String, String> replacements = Map.of("${packageName}", packageName);
                var testParserTask = new TestParserTask(TestParserTaskProperties.TestType.JUNIT).resultDirectories("**/tests.xml");
                if (isXcodeProject) {
                    testParserTask = new TestParserTask(TestParserTaskProperties.TestType.JUNIT).resultDirectories("**/report.junit");
                    replacements = Map.of("${appName}", packageName);
                }
                var tasks = readScriptTasksFromTemplate(programmingLanguage, subDirectory, sequentialBuildRuns, false, replacements);
                tasks.add(0, checkoutTask);
                defaultJob.tasks(tasks.toArray(new Task[0])).finalTasks(testParserTask);
                // SCA for Xcode is not supported yet
                if (!isXcodeProject && Boolean.TRUE.equals(staticCodeAnalysisEnabled)) {
                    // Create artifacts and a final task for the execution of static code analysis
                    List<StaticCodeAnalysisTool> staticCodeAnalysisTools = StaticCodeAnalysisTool.getToolsForProgrammingLanguage(ProgrammingLanguage.SWIFT);
                    Artifact[] artifacts = staticCodeAnalysisTools.stream()
                            .map(tool -> new Artifact().name(tool.getArtifactLabel()).location("target").copyPattern(tool.getFilePattern()).shared(false)).toArray(Artifact[]::new);
                    defaultJob.artifacts(artifacts);
                    var scaTasks = readScriptTasksFromTemplate(programmingLanguage, subDirectory, false, true, null);
                    defaultJob.finalTasks(scaTasks.toArray(new Task[0]));
                }
                if (isXcodeProject) {
                    // add a requirement to be able to run the Xcode build tasks using fastlane
                    var requirement = new Requirement("system.builder.fastlane.fastlane");
                    defaultJob.requirements(requirement);
                }
                return defaultStage.jobs(defaultJob);
            }
            case EMPTY -> {
                ScriptTask mvnVersionTask = new ScriptTask().description("Print Maven Version").inlineBody("mvn --version").interpreterShell()
                        .location(ScriptTaskProperties.Location.INLINE);
                return defaultStage.jobs(defaultJob.tasks(checkoutTask, mvnVersionTask));
            }
            // this is needed, otherwise the compiler complaints with missing return
            // statement
            default -> throw new IllegalArgumentException("No build stage setup for programming language " + programmingLanguage);
        }
    }

    private Stage createDefaultStage(ProgrammingLanguage programmingLanguage, boolean sequentialBuildRuns, VcsCheckoutTask checkoutTask, Stage defaultStage, Job defaultJob,
            String resultDirectories) {
        final var testParserTask = new TestParserTask(TestParserTaskProperties.TestType.JUNIT).resultDirectories(resultDirectories);
        var tasks = readScriptTasksFromTemplate(programmingLanguage, "", sequentialBuildRuns, false, null);
        tasks.add(0, checkoutTask);
        return defaultStage.jobs(defaultJob.tasks(tasks.toArray(new Task[0])).finalTasks(testParserTask));
    }

    private Plan createDefaultBuildPlan(String planKey, String planDescription, String projectKey, String projectName, String repositoryName, String vcsTestRepositorySlug,
            boolean checkoutSolutionRepository, String vcsSolutionRepositorySlug, List<AuxiliaryRepository.AuxRepoNameWithSlug> auxiliaryRepositories) {
        List<VcsRepositoryIdentifier> vcsTriggerRepositories = new ArrayList<>();
        // Trigger the build when a commit is pushed to the ASSIGNMENT_REPO.
        vcsTriggerRepositories.add(new VcsRepositoryIdentifier(ASSIGNMENT_REPO_NAME));
        // Trigger the build when a commit is pushed to the TEST_REPO only for the
        // solution repository!
        if (planKey.equals(BuildPlanType.SOLUTION.getName())) {
            vcsTriggerRepositories.add(new VcsRepositoryIdentifier(TEST_REPO_NAME));
        }

        List<VcsRepository<?, ?>> planRepositories = new ArrayList<>();
        planRepositories.add(
                createBuildPlanRepository(ASSIGNMENT_REPO_NAME, projectKey, repositoryName, versionControlService.get().getDefaultBranchOfRepository(projectKey, repositoryName)));
        planRepositories.add(createBuildPlanRepository(TEST_REPO_NAME, projectKey, vcsTestRepositorySlug,
                versionControlService.get().getDefaultBranchOfRepository(projectKey, vcsTestRepositorySlug)));
        for (var repo : auxiliaryRepositories) {
            planRepositories.add(createBuildPlanRepository(repo.name(), projectKey, repo.repositorySlug(),
                    versionControlService.get().getDefaultBranchOfRepository(projectKey, repo.repositorySlug())));
        }
        if (checkoutSolutionRepository) {
            planRepositories.add(createBuildPlanRepository(SOLUTION_REPO_NAME, projectKey, vcsSolutionRepositorySlug,
                    versionControlService.get().getDefaultBranchOfRepository(projectKey, vcsSolutionRepositorySlug)));
        }

        return new Plan(createBuildProject(projectName, projectKey), planKey, planKey).description(planDescription)
                .pluginConfigurations(new ConcurrentBuilds().useSystemWideDefault(true)).planRepositories(planRepositories.toArray(VcsRepository[]::new))
                .triggers(new BitbucketServerTrigger().selectedTriggeringRepositories(vcsTriggerRepositories.toArray(new VcsRepositoryIdentifier[0])))
                .planBranchManagement(createPlanBranchManagement()).notifications(createNotification());
    }

    private VcsCheckoutTask createCheckoutTask(String assignmentPath, String testPath, List<AuxiliaryRepository> auxiliaryRepositories) {
        return createCheckoutTask(assignmentPath, testPath, Optional.empty(), auxiliaryRepositories);
    }

    private VcsCheckoutTask createCheckoutTask(String assignmentPath, String testPath, Optional<String> solutionPath, List<AuxiliaryRepository> auxiliaryRepositories) {
        List<CheckoutItem> checkoutItems = new ArrayList<>();
        checkoutItems.add(new CheckoutItem().repository(new VcsRepositoryIdentifier().name(TEST_REPO_NAME)).path(testPath));
        checkoutItems.add(new CheckoutItem().repository(new VcsRepositoryIdentifier().name(ASSIGNMENT_REPO_NAME)).path(assignmentPath));
        for (AuxiliaryRepository repo : auxiliaryRepositories) {
            checkoutItems.add(new CheckoutItem().repository(new VcsRepositoryIdentifier().name(repo.getName())).path(repo.getCheckoutDirectory()));
        }
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

    private BitbucketServerRepository createBuildPlanRepository(String name, String vcsProjectKey, String repositorySlug, String defaultBranch) {
        return new BitbucketServerRepository().name(name).branch(defaultBranch).repositoryViewer(new BitbucketServerRepositoryViewer())
                .server(new ApplicationLink().name(vcsApplicationLinkName))
                // make sure to use lower case to avoid problems in change detection between
                // Bamboo and Bitbucket
                .projectKey(vcsProjectKey).repositorySlug(repositorySlug.toLowerCase()).shallowClonesEnabled(true).remoteAgentCacheEnabled(false)
                .changeDetection(new VcsChangeDetection());
    }

    private PlanPermissions generatePlanPermissions(String bambooProjectKey, String bambooPlanKey, @Nullable String teachingAssistantGroupName, @Nullable String editorGroupName,
            String instructorGroupName, String adminGroupName) {
        var permissions = new Permissions().userPermissions(bambooUser, PermissionType.EDIT, PermissionType.BUILD, PermissionType.CLONE, PermissionType.VIEW, PermissionType.ADMIN)
                .groupPermissions(adminGroupName, PermissionType.CLONE, PermissionType.BUILD, PermissionType.EDIT, PermissionType.VIEW, PermissionType.ADMIN)
                .groupPermissions(instructorGroupName, PermissionType.CLONE, PermissionType.BUILD, PermissionType.EDIT, PermissionType.VIEW, PermissionType.ADMIN);
        if (editorGroupName != null) {
            permissions = permissions.groupPermissions(editorGroupName, PermissionType.CLONE, PermissionType.BUILD, PermissionType.EDIT, PermissionType.VIEW, PermissionType.ADMIN);
        }
        if (teachingAssistantGroupName != null) {
            permissions = permissions.groupPermissions(teachingAssistantGroupName, PermissionType.VIEW);
        }
        return new PlanPermissions(new PlanIdentifier(bambooProjectKey, bambooPlanKey)).permissions(permissions);
    }

    private List<Task<?, ?>> readScriptTasksFromTemplate(final ProgrammingLanguage programmingLanguage, String subDirectory, final boolean sequentialBuildRuns,
            final boolean getScaTasks, Map<String, String> replacements) {
        final var directoryPattern = "templates/bamboo/" + programmingLanguage.name().toLowerCase() + subDirectory
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
                        var inlineBody = IOUtils.toString(inputStream, Charset.defaultCharset());
                        if (replacements != null) {
                            for (Map.Entry<String, String> replacement : replacements.entrySet()) {
                                inlineBody = inlineBody.replace(replacement.getKey(), replacement.getValue());
                            }
                        }
                        tasks.add(new ScriptTask().description(scriptDescription).inlineBody(inlineBody));
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
