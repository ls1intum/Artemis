package de.tum.in.www1.artemis.service.connectors;

import static de.tum.in.www1.artemis.config.Constants.*;

import java.net.URL;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.atlassian.bamboo.specs.api.builders.AtlassianModule;
import com.atlassian.bamboo.specs.api.builders.BambooKey;
import com.atlassian.bamboo.specs.api.builders.applink.ApplicationLink;
import com.atlassian.bamboo.specs.api.builders.notification.AnyNotificationRecipient;
import com.atlassian.bamboo.specs.api.builders.notification.Notification;
import com.atlassian.bamboo.specs.api.builders.permission.PermissionType;
import com.atlassian.bamboo.specs.api.builders.permission.Permissions;
import com.atlassian.bamboo.specs.api.builders.permission.PlanPermissions;
import com.atlassian.bamboo.specs.api.builders.plan.Job;
import com.atlassian.bamboo.specs.api.builders.plan.Plan;
import com.atlassian.bamboo.specs.api.builders.plan.PlanIdentifier;
import com.atlassian.bamboo.specs.api.builders.plan.Stage;
import com.atlassian.bamboo.specs.api.builders.plan.branches.BranchCleanup;
import com.atlassian.bamboo.specs.api.builders.plan.branches.PlanBranchManagement;
import com.atlassian.bamboo.specs.api.builders.plan.configuration.ConcurrentBuilds;
import com.atlassian.bamboo.specs.api.builders.project.Project;
import com.atlassian.bamboo.specs.api.builders.repository.VcsChangeDetection;
import com.atlassian.bamboo.specs.api.builders.repository.VcsRepositoryIdentifier;
import com.atlassian.bamboo.specs.api.builders.requirement.Requirement;
import com.atlassian.bamboo.specs.builders.notification.PlanCompletedNotification;
import com.atlassian.bamboo.specs.builders.repository.bitbucket.server.BitbucketServerRepository;
import com.atlassian.bamboo.specs.builders.repository.viewer.BitbucketServerRepositoryViewer;
import com.atlassian.bamboo.specs.builders.task.*;
import com.atlassian.bamboo.specs.builders.trigger.BitbucketServerTrigger;
import com.atlassian.bamboo.specs.model.task.TestParserTaskProperties;
import com.atlassian.bamboo.specs.util.BambooServer;
import com.atlassian.bamboo.specs.util.SimpleUserPasswordCredentials;
import com.atlassian.bamboo.specs.util.UserPasswordCredentials;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;

@Service
@Profile("bamboo")
public class BambooBuildPlanService {

    @Value("${artemis.bamboo.user}")
    private String BAMBOO_USER;

    @Value("${artemis.bamboo.password}")
    private String BAMBOO_PASSWORD;

    @Value("${artemis.bamboo.url}")
    private URL BAMBOO_SERVER_URL;

    @Value("${artemis.jira.admin-group-name}")
    private String ADMIN_GROUP_NAME;

    @Value("${server.url}")
    private URL SERVER_URL;

    @Value("${artemis.bamboo.bitbucket-application-link-id}")
    private String BITBUCKET_APPLICATION_LINK_ID;

    public void createBuildPlanForExercise(ProgrammingExercise programmingExercise, String planKey, String repositoryName, String testRepositoryName) {
        final String planDescription = planKey + " Build Plan for Exercise " + programmingExercise.getTitle();
        final String projectKey = programmingExercise.getProjectKey();
        final String projectName = programmingExercise.getProjectName();

        Plan plan = createDefaultBuildPlan(planKey, planDescription, projectKey, projectName, repositoryName, testRepositoryName)
                .stages(createBuildStage(programmingExercise.getProgrammingLanguage(), programmingExercise.getSequentialTestRuns()));

        UserPasswordCredentials userPasswordCredentials = new SimpleUserPasswordCredentials(BAMBOO_USER, BAMBOO_PASSWORD);
        BambooServer bambooServer = new BambooServer(BAMBOO_SERVER_URL.toString(), userPasswordCredentials);

        bambooServer.publish(plan);

        Course course = programmingExercise.getCourse();
        final String teachingAssistantGroupName = course.getTeachingAssistantGroupName();
        final String instructorGroupName = course.getInstructorGroupName();
        final PlanPermissions planPermission = generatePlanPermissions(programmingExercise.getProjectKey(), plan.getKey().toString(), teachingAssistantGroupName,
                instructorGroupName, ADMIN_GROUP_NAME);
        bambooServer.publish(planPermission);
    }

    private Project createBuildProject(String name, String key) {
        return new Project().key(key).name(name);
    }

    private Stage createBuildStage(ProgrammingLanguage programmingLanguage, Boolean sequentialBuildRuns) {
        if (programmingLanguage == ProgrammingLanguage.JAVA && !sequentialBuildRuns) {
            return new Stage("Default Stage").jobs(new Job("Default Job", new BambooKey("JOB1")).tasks(createCheckoutTask(ASSIGNMENT_REPO_PATH, ""),
                    new MavenTask().goal("clean test").jdk("JDK 1.8").executableLabel("Maven 3").description("Tests").hasTests(true)));
        }
        else if (programmingLanguage == ProgrammingLanguage.JAVA) {
            return new Stage("Default Stage").jobs(new Job("Default Job", new BambooKey("JOB1")).tasks(createCheckoutTask(ASSIGNMENT_REPO_PATH, ""),
                    new MavenTask().goal("clean test").workingSubdirectory("sequential").jdk("JDK 1.8").executableLabel("Maven 3").description("Structural tests").hasTests(true),
                    new MavenTask().goal("clean test").workingSubdirectory("behavior").jdk("JDK 1.8").executableLabel("Maven 3").description("Behavior tests").hasTests(true)));
        }
        else if (programmingLanguage == ProgrammingLanguage.PYTHON && !sequentialBuildRuns) {
            return new Stage("Default Stage")
                    .jobs(new Job("Default Job", new BambooKey("JOB1"))
                            .tasks(createCheckoutTask("", "tests"),
                                    new ScriptTask().description("Builds and tests the code").inlineBody("pytest --junitxml=test-reports/results.xml\nexit 0"),
                                    new TestParserTask(TestParserTaskProperties.TestType.JUNIT).resultDirectories("test-reports/results.xml"))
                            .requirements(new Requirement("Python3")));
        }
        else if (programmingLanguage == ProgrammingLanguage.PYTHON) {
            return new Stage("Default Stage").jobs(new Job("Default Job", new BambooKey("JOB1")).tasks(createCheckoutTask("", "tests"),
                    new ScriptTask().description("Builds and tests the code").inlineBody("pytest test/structural --junitxml=test-reports/structural-results.xml\nexit 0"),
                    new ScriptTask().description("Builds and tests the code").inlineBody("pytest test/behavior --junitxml=test-reports/behavior-results.xml\nexit 0"),
                    new TestParserTask(TestParserTaskProperties.TestType.JUNIT).resultDirectories("test-reports/results.xml")).requirements(new Requirement("Python3")));
        }

        return null;
    }

    private Plan createDefaultBuildPlan(String planKey, String planDescription, String projectKey, String projectName, String repositoryName, String vcsTestRepositorySlug) {
        return new Plan(createBuildProject(projectName, projectKey), planKey, planKey).description(planDescription)
                .pluginConfigurations(new ConcurrentBuilds().useSystemWideDefault(true))
                .planRepositories(createBuildPlanRepository(ASSIGNMENT_REPO_NAME, projectKey, repositoryName),
                        createBuildPlanRepository(TEST_REPO_NAME, projectKey, vcsTestRepositorySlug))
                .triggers(new BitbucketServerTrigger()).planBranchManagement(createPlanBranchManagement()).notifications(createNotification());
    }

    private VcsCheckoutTask createCheckoutTask(String assignmentPath, String testPath) {
        return new VcsCheckoutTask().description("Checkout Default Repository").checkoutItems(
                new CheckoutItem().repository(new VcsRepositoryIdentifier().name(ASSIGNMENT_REPO_NAME)).path(assignmentPath), // NOTE: this path needs to be specified in the Maven
                                                                                                                              // pom.xml in the Tests Repo
                new CheckoutItem().repository(new VcsRepositoryIdentifier().name(TEST_REPO_NAME)).path(testPath));
    }

    private PlanBranchManagement createPlanBranchManagement() {
        return new PlanBranchManagement().delete(new BranchCleanup()).notificationForCommitters();
    }

    private Notification createNotification() {
        return new Notification().type(new PlanCompletedNotification()).recipients(
                new AnyNotificationRecipient(new AtlassianModule("de.tum.in.www1.bamboo-server:recipient.server")).recipientString(SERVER_URL + NEW_RESULT_RESOURCE_API_PATH));
    }

    private BitbucketServerRepository createBuildPlanRepository(String name, String vcsProjectKey, String repositorySlug) {
        return new BitbucketServerRepository().name(name).repositoryViewer(new BitbucketServerRepositoryViewer()).server(new ApplicationLink().id(BITBUCKET_APPLICATION_LINK_ID))
                .projectKey(vcsProjectKey).repositorySlug(repositorySlug.toLowerCase())   // make sure to use lower case to avoid problems in change detection between Bamboo and
                                                                                          // Bitbucket
                .shallowClonesEnabled(true).remoteAgentCacheEnabled(false).changeDetection(new VcsChangeDetection());
    }

    private PlanPermissions generatePlanPermissions(String bambooProjectKey, String bambooPlanKey, String teachingAssistantGroupName, String instructorGroupName,
            String adminGroupName) {
        return new PlanPermissions(new PlanIdentifier(bambooProjectKey, bambooPlanKey)).permissions(
                new Permissions().userPermissions(BAMBOO_USER, PermissionType.EDIT, PermissionType.BUILD, PermissionType.CLONE, PermissionType.VIEW, PermissionType.ADMIN)
                        .groupPermissions(adminGroupName, PermissionType.CLONE, PermissionType.BUILD, PermissionType.EDIT, PermissionType.VIEW, PermissionType.ADMIN)
                        .groupPermissions(instructorGroupName, PermissionType.CLONE, PermissionType.BUILD, PermissionType.EDIT, PermissionType.VIEW, PermissionType.ADMIN)
                        .groupPermissions(teachingAssistantGroupName, PermissionType.BUILD, PermissionType.EDIT, PermissionType.VIEW));
    }

}
