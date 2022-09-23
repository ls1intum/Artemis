package de.tum.in.www1.artemis.service.connectors.bamboo;

import static de.tum.in.www1.artemis.config.Constants.*;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.*;

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

import de.tum.in.www1.artemis.config.ProgrammingLanguageConfiguration;
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

    private final ProgrammingLanguageConfiguration programmingLanguageConfiguration;

    private final ResourceLoaderService resourceLoaderService;

    private final BambooServer bambooServer;

    private final Environment env;

    private final Optional<VersionControlService> versionControlService;

    public BambooBuildPlanService(ResourceLoaderService resourceLoaderService, BambooServer bambooServer, Environment env, Optional<VersionControlService> versionControlService,
            ProgrammingLanguageConfiguration programmingLanguageConfiguration) {
        this.resourceLoaderService = resourceLoaderService;
        this.bambooServer = bambooServer;
        this.env = env;
        this.versionControlService = versionControlService;
        this.programmingLanguageConfiguration = programmingLanguageConfiguration;
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
        final boolean recordTestwiseCoverage = Boolean.TRUE.equals(programmingExercise.isTestwiseCoverageEnabled()) && "SOLUTION".equals(planKey);

        Plan plan = createDefaultBuildPlan(planKey, planDescription, projectKey, projectName, repositoryName, testRepositoryName,
                programmingExercise.getCheckoutSolutionRepository(), solutionRepositoryName, auxiliaryRepositories)
                        .stages(createBuildStage(programmingExercise.getProgrammingLanguage(), programmingExercise.getProjectType(), programmingExercise.getPackageName(),
                                programmingExercise.hasSequentialTestRuns(), programmingExercise.isStaticCodeAnalysisEnabled(), programmingExercise.getCheckoutSolutionRepository(),
                                recordTestwiseCoverage, programmingExercise.getAuxiliaryRepositoriesForBuildPlan()));

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
            Boolean staticCodeAnalysisEnabled, boolean checkoutSolutionRepository, final boolean recordTestwiseCoverage, List<AuxiliaryRepository> auxiliaryRepositories) {
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
            defaultJob.dockerConfiguration(dockerConfigurationImageNameFor(programmingLanguage, Optional.ofNullable(projectType)));
        }
        switch (programmingLanguage) {
            case JAVA, KOTLIN -> {
                // the project type can be null for Kotlin exercises and exercises created before the project type was
                // added to Artemis. The project type for these exercises is implicitly Maven (because Gradle did not
                // exist in Artemis yet)
                boolean isMavenProject = ProjectType.isMavenProject(projectType);

                var defaultTasks = new ArrayList<Task<?, ?>>();
                defaultTasks.add(checkoutTask);
                var finalTasks = new ArrayList<Task<?, ?>>();
                var artifacts = new ArrayList<Artifact>();

                if (Boolean.TRUE.equals(staticCodeAnalysisEnabled)) {
                    modifyBuildConfigurationForStaticCodeAnalysisForJavaAndKotlinExercise(isMavenProject, finalTasks, artifacts);
                }

                if (!sequentialBuildRuns) {
                    modifyBuildConfigurationForRegularTestsForJavaAndKotlinExercise(isMavenProject, recordTestwiseCoverage, defaultTasks, finalTasks, artifacts);
                }
                else {
                    modifyBuildConfigurationForSequentialTestsForJavaAndKotlinExercise(isMavenProject, defaultTasks, finalTasks);
                }

                // This conversion is required because the attributes are passed as varargs-parameter which is only possible
                // for array collections
                var defaultTasksArray = defaultTasks.toArray(new Task<?, ?>[defaultTasks.size()]);
                var finalTasksArray = finalTasks.toArray(new Task<?, ?>[finalTasks.size()]);
                var artifactsArray = artifacts.toArray(new Artifact[artifacts.size()]);

                // assign tasks and artifacts to job
                defaultJob.tasks(defaultTasksArray);
                defaultJob.finalTasks(finalTasksArray);
                defaultJob.artifacts(artifactsArray);

                return defaultStage.jobs(defaultJob);
            }
            case PYTHON -> {
                return createDefaultStage(programmingLanguage, sequentialBuildRuns, checkoutTask, defaultStage, defaultJob, "test-reports/*results.xml");
            }
            case C -> {
                // Default tasks:
                var tasks = readScriptTasksFromTemplate(programmingLanguage, File.separator + projectType.name().toLowerCase(), sequentialBuildRuns, false, null);
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
                if (Boolean.TRUE.equals(staticCodeAnalysisEnabled)) {
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

    /**
     * Modify the lists containing default tasks, final tasks and artifacts for executing a static code analysis for
     * Java and Kotlin exercises.
     * @param isMavenProject whether the project is a Maven build (or implicitly a Gradle build)
     * @param finalTasks the list containing the final tasks for the build plan to be created
     * @param artifacts the list containing all artifacts for the build plan to be created
     */
    private void modifyBuildConfigurationForStaticCodeAnalysisForJavaAndKotlinExercise(boolean isMavenProject, List<Task<?, ?>> finalTasks, List<Artifact> artifacts) {
        // Create artifacts and a final task for the execution of static code analysis
        List<StaticCodeAnalysisTool> staticCodeAnalysisTools = StaticCodeAnalysisTool.getToolsForProgrammingLanguage(ProgrammingLanguage.JAVA);
        var scaArtifacts = staticCodeAnalysisTools.stream()
                .map(tool -> new Artifact().name(tool.getArtifactLabel()).location("target").copyPattern(tool.getFilePattern()).shared(false)).toList();

        if (isMavenProject) {
            String command = StaticCodeAnalysisTool.createBuildPlanCommandForProgrammingLanguage(ProgrammingLanguage.JAVA);
            finalTasks.add(new MavenTask().goal(command).jdk("JDK").executableLabel("Maven 3").description("Static Code Analysis").hasTests(false));
        }
        else {
            finalTasks.add(new ScriptTask().inlineBody("./gradlew check -x test").description("Static Code Analysis"));
        }
        artifacts.addAll(scaArtifacts);
    }

    /**
     * Modify the lists containing default and final tasks for executing a non-sequential test run
     * @param isMavenProject whether the project is a Maven project (or implicitly a Gradle project)
     * @param recordTestwiseCoverage whether the testwise coverage should be recorded
     * @param defaultTasks the list containing the default tasks for the build plan to be created
     * @param finalTasks the list containing the final tasks for the build plan to be created
     * @param artifacts the list containing all artifacts for the build plan to be created
     */
    private void modifyBuildConfigurationForRegularTestsForJavaAndKotlinExercise(boolean isMavenProject, boolean recordTestwiseCoverage, List<Task<?, ?>> defaultTasks,
            List<Task<?, ?>> finalTasks, List<Artifact> artifacts) {
        if (isMavenProject) {
            String goals = "clean test";
            if (recordTestwiseCoverage) {
                // If a testwise coverage should be performed, a custom profile is used for the execution
                goals += " -Pcoverage";
                artifacts.add(new Artifact().name("testwiseCoverageReport").location("target/tia/reports").copyPattern("tiaTests.json"));
            }

            defaultTasks.add(new MavenTask().goal(goals).jdk("JDK").executableLabel("Maven 3").description("Tests").hasTests(true));

            // the report name of the artifact has to be renamed, since the name contains the latest timestamp and artifact pattern matching
            // returns a folder of artifacts, instead of the individual artifact. The report has to be renamed after the build execution
            if (recordTestwiseCoverage) {
                defaultTasks.add(new ScriptTask().description("Move Report File").inlineBody("mv target/tia/reports/*/testwise-coverage-*.json target/tia/reports/tiaTests.json"));
            }
        }
        else {
            // setting the permission as a final task is required as a workaround because the docker container runs as a root user
            // and creates files that cannot be deleted by Bamboo because it does not have these root permissions
            String testCommand = "./gradlew clean test";
            if (recordTestwiseCoverage) {
                testCommand += " tiaTests --run-all-tests";
                artifacts.add(new Artifact().name("testwiseCoverageReport").location("build/reports/testwise-coverage/tiaTests").copyPattern("tiaTests.json"));
            }
            defaultTasks.add(new ScriptTask().inlineBody(testCommand).description("Tests"));
            finalTasks.add(new TestParserTask(TestParserTaskProperties.TestType.JUNIT).resultDirectories("**/test-results/test/*.xml").description("JUnit Parser"));
            finalTasks.add(new ScriptTask().inlineBody("chmod -R 777 ${bamboo.working.directory}").description("Setup working directory for cleanup"));
        }
    }

    /**
     * Modify the lists containing default and final tasks for executing a sequential test run
     * @param isMavenProject whether the project is a Maven project (or implicitly a Gradle project)
     * @param defaultTasks the list containing the default tasks for the build plan to be created
     * @param finalTasks the list containing the final tasks for the build plan to be created
     */
    private void modifyBuildConfigurationForSequentialTestsForJavaAndKotlinExercise(boolean isMavenProject, List<Task<?, ?>> defaultTasks, List<Task<?, ?>> finalTasks) {
        if (isMavenProject) {
            defaultTasks
                    .add(new MavenTask().goal("clean test").workingSubdirectory("structural").jdk("JDK").executableLabel("Maven 3").description("Structural tests").hasTests(true));
            defaultTasks.add(new MavenTask().goal("clean test").workingSubdirectory("behavior").jdk("JDK").executableLabel("Maven 3").description("Behavior tests").hasTests(true));
        }
        else {
            // setting the permission as a final task is required as a workaround because the docker container runs as a root user
            // and creates files that cannot be deleted by Bamboo because it does not have these root permissions
            finalTasks.add(new TestParserTask(TestParserTaskProperties.TestType.JUNIT)
                    .resultDirectories("**/test-results/structuralTests/*.xml,**/test-results/behaviorTests/*.xml").description("JUnit Parser"));
            finalTasks.add(new ScriptTask().inlineBody("chmod -R 777 ${bamboo.working.directory}").description("Setup working directory for cleanup"));
            // the script task for executing the behavior tests must not clean the build files because the test parser would not have parsed the tests for the structural tests yet
            defaultTasks.add(new ScriptTask().inlineBody("./gradlew clean structuralTests").description("Structural tests"));
            defaultTasks.add(new ScriptTask().inlineBody("./gradlew behaviorTests").description("Behavior tests"));
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

    private BitbucketServerRepository createBuildPlanRepository(String name, String vcsProjectKey, String repositorySlug, String branch) {
        return new BitbucketServerRepository().name(name).branch(branch).repositoryViewer(new BitbucketServerRepositoryViewer())
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
                            .split("_")).skip(1) // Remove the index prefix: 1 some description --> some description
                            .toList();
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

    private DockerConfiguration dockerConfigurationImageNameFor(ProgrammingLanguage programmingLanguage, Optional<ProjectType> projectType) {
        var dockerImage = programmingLanguageConfiguration.getImage(programmingLanguage, projectType);
        return new DockerConfiguration().image(dockerImage);
    }
}
