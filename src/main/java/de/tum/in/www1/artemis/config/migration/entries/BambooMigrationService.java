package de.tum.in.www1.artemis.config.migration.entries;

import static de.tum.in.www1.artemis.config.Constants.NEW_RESULT_RESOURCE_API_PATH;

import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import de.tum.in.www1.artemis.domain.AuxiliaryRepository;
import de.tum.in.www1.artemis.domain.VcsRepositoryUrl;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.in.www1.artemis.service.connectors.bamboo.BambooInternalUrlService;
import de.tum.in.www1.artemis.service.connectors.bamboo.BambooService;

/**
 * Services for executing migration tasks for Bamboo.
 */
@Service
@Profile("bamboo")
public class BambooMigrationService implements CIVCSMigrationService {

    private final RestTemplate restTemplate;

    @Value("${artemis.continuous-integration.url}")
    protected URL bambooServerUrl;

    private final BambooInternalUrlService bambooInternalUrlService;

    private final BambooService bambooService;

    @Value("${server.url}")
    private String artemisServerUrl;

    @Value("${artemis.version-control.user}")
    private String gitUser;

    private Optional<Long> sharedCredentialId = Optional.empty();

    private final Logger log = LoggerFactory.getLogger(BambooMigrationService.class);

    public BambooMigrationService(@Qualifier("bambooRestTemplate") RestTemplate restTemplate, BambooService bambooService, BambooInternalUrlService bambooInternalUrlService) {
        this.restTemplate = restTemplate;
        this.bambooService = bambooService;
        this.bambooInternalUrlService = bambooInternalUrlService;
    }

    /**
     * Returns a list of all data-item-ids in the given html.
     *
     * @param html response that contains a list of items with the data-item-id attribute
     * @return a list of all data-item-ids in the given html
     */
    private static List<Long> getDataItemIds(String html) {
        if (html == null) {
            return List.of();
        }
        Elements repositories = Jsoup.parse(html).select(".item");
        List<Long> ids = new ArrayList<>();
        for (Element repository : repositories) {
            String repositoryIdString = repository.attr("data-item-id");
            ids.add(Long.parseLong(repositoryIdString));
        }
        return ids;
    }

    /**
     * Returns the name of the repository with the given id.
     *
     * @param html Response to the request to the editChainRepository.action endpoint
     * @param id   The id of the repository we need the name for
     * @return the name of the repository with the given id
     */
    private static Optional<String> getRepositoryNameById(String html, Long id) {
        if (html == null) {
            return Optional.empty();
        }
        Elements repositories = Jsoup.parse(html).select(".item");
        for (Element repository : repositories) {
            var link = Jsoup.parse(repository.html()).selectFirst("a");
            if (link == null) {
                continue;
            }
            String repositoryLink = link.attr("href");
            Element nameElement = repository.selectFirst("h3");
            if (repositoryLink.contains("repositoryId=" + id.toString()) && nameElement != null) {
                return Optional.of(nameElement.text());
            }
        }
        return Optional.empty();
    }

    @Override
    public void overrideBuildPlanNotification(String projectKey, String buildPlanKey, VcsRepositoryUrl vcsRepositoryUrl) {
        List<Long> notificationIds = getAllArtemisBuildPlanServerNotificationIds(buildPlanKey);

        for (var id : notificationIds) {
            deleteBuildPlanServerNotificationId(buildPlanKey, id);
        }

        createBuildPlanServerNotification(buildPlanKey, artemisServerUrl + NEW_RESULT_RESOURCE_API_PATH);
    }

    @Override
    public void deleteBuildTriggers(String projectKey, String buildPlanKey, VcsRepositoryUrl repositoryUrl) {
        List<Long> triggerIds = getAllTriggerIds(buildPlanKey);
        if (triggerIds.size() != 1) {
            log.warn("The build plan {} has {} triggers", buildPlanKey, triggerIds.size());
        }
        for (var id : triggerIds) {
            deleteBuildPlanTriggerId(buildPlanKey, id);
            log.debug("Deleted trigger with id " + id + " for build plan " + buildPlanKey);
        }
    }

    @Override
    public void checkPrerequisites() throws RuntimeException {
        Optional<Long> credentialsId = getSharedCredential();
        if (credentialsId.isEmpty()) {
            log.error("No shared credential found for git user " + gitUser + ". Migration will fail.");
            throw new RuntimeException("No shared credential found for git user " + gitUser
                    + " in Bamboo. Migration will fail. Please create a shared Username and Password credential for this user and run " + "the migration again.");
        }
        this.sharedCredentialId = credentialsId;
    }

    @Override
    public void overrideBuildPlanRepository(String buildPlanId, String name, String repositoryUrl, String defaultBranch) {
        if (this.sharedCredentialId.isEmpty()) {
            Optional<Long> credentialsId = getSharedCredential();
            if (credentialsId.isEmpty()) {
                log.error("No shared credential found for git user " + gitUser + ". Migration will fail.");
            }
            this.sharedCredentialId = credentialsId;
        }
        Optional<Long> repositoryId = getConnectedRepositoryId(buildPlanId, name);

        if (repositoryId.isEmpty()) {
            log.info("Repository " + name + " not found for build plan " + buildPlanId + ", will be added now");
        }
        else {
            log.debug("Deleting repository " + name + " for build plan " + buildPlanId);
            deleteLinkedRepository(buildPlanId, repositoryId.get());
        }
        log.debug("Adding repository " + name + " for build plan " + buildPlanId);
        addGitRepository(buildPlanId, bambooInternalUrlService.toInternalVcsUrl(repositoryUrl), name, this.sharedCredentialId.orElseThrow(), defaultBranch);
    }

    private List<Long> getAllTriggerIds(String buildPlanName) {
        if (buildPlanName == null) {
            return List.of();
        }
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("buildKey", buildPlanName);
        String requestUrl = bambooServerUrl + "/chain/admin/config/editChainTriggers.action";
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(requestUrl).queryParams(parameters);

        var response = restTemplate.exchange(builder.build().toUri(), HttpMethod.GET, null, String.class);
        return getDataItemIds(response.getBody());
    }

    /**
     * Returns the id of the repository with the given name for the given build plan.
     * If no repository with the given name exists, an empty optional is returned.
     *
     * @param buildPlanId The key of the build plan, e.g. 'EIST16W1-BASE'.
     * @param name        The name of the repository, e.g. 'assignment'.
     * @return the id of the repository with the given name for the given build plan
     */
    private Optional<Long> getConnectedRepositoryId(String buildPlanId, String name) {
        if ((name == null || name.isEmpty()) || (buildPlanId == null || buildPlanId.isEmpty())) {
            return Optional.empty();
        }
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("buildKey", buildPlanId);
        String requestUrl = bambooServerUrl + "/chain/admin/config/editChainRepository.action";
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(requestUrl).queryParams(parameters);

        var response = restTemplate.exchange(builder.build().toUri(), HttpMethod.GET, null, String.class);
        var html = response.getBody();
        var ids = getDataItemIds(html);
        for (var id : ids) {
            var repositoryName = getRepositoryNameById(html, id);
            // old repositories have the name "Assignment", new ones "assignment"
            if (repositoryName.isPresent() && repositoryName.get().equalsIgnoreCase(name)) {
                return Optional.of(id);
            }
        }
        return Optional.empty();
    }

    @Override
    public void overrideRepositoriesToCheckout(String buildPlanKey, List<AuxiliaryRepository> auxiliaryRepositoryList) {
        Optional<Long> testRepositoryId = getConnectedRepositoryId(buildPlanKey, "tests");
        Optional<Long> assignmentRepositoryId = getConnectedRepositoryId(buildPlanKey, "assignment");

        if (testRepositoryId.isEmpty()) {
            log.error("Repository tests not found for build plan {}", buildPlanKey);
        }
        if (assignmentRepositoryId.isEmpty()) {
            log.error("Repository assignment not found for build plan {}", buildPlanKey);
        }

        Map<String, Long> auxiliaryRepositoryIds = new HashMap<>();
        for (AuxiliaryRepository auxiliaryRepository : auxiliaryRepositoryList) {
            Optional<Long> repositoryId = getConnectedRepositoryId(buildPlanKey, auxiliaryRepository.getName());
            if (repositoryId.isPresent()) {
                auxiliaryRepositoryIds.put(auxiliaryRepository.getName(), repositoryId.get());
            }
            else {
                log.error("Repository {} not found for build plan {}", auxiliaryRepository.getName(), buildPlanKey);
            }
        }
        setRepositoriesToCheckout(buildPlanKey, testRepositoryId.orElseThrow(), assignmentRepositoryId.orElseThrow(), auxiliaryRepositoryIds, auxiliaryRepositoryList);
    }

    @Override
    public Page<ProgrammingExerciseStudentParticipation> getPageableStudentParticipations(
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, Pageable pageable) {
        return programmingExerciseStudentParticipationRepository.findAllWithBuildPlanId(pageable);
    }

    @Override
    public boolean supportsAuxiliaryRepositories() {
        return true;
    }

    @Override
    public boolean buildPlanExists(String projectKey, String buildPlanKey) {
        return bambooService.checkIfBuildPlanExists(projectKey, buildPlanKey);
    }

    /**
     * Returns a list of all notification ids for the given build plan for this server.
     * Bamboo doesn't provide a REST endpoint for this, so we have to parse the HTML page using the admin chain API.
     *
     * @param buildPlanKey The key of the build plan, which is usually the name combined with the project, e.g. 'EIST16W1-GA56HUR'.
     * @return a list of all notification ids
     */
    private List<Long> getAllArtemisBuildPlanServerNotificationIds(String buildPlanKey) {
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("buildKey", buildPlanKey);
        String requestUrl = bambooServerUrl + "/chain/admin/config/defaultChainNotification.action";
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(requestUrl).queryParams(parameters);

        var response = restTemplate.exchange(builder.build().toUri(), HttpMethod.GET, null, String.class);
        var html = response.getBody();
        if (html == null) {
            return List.of();
        }
        Element notificationTableBody = Jsoup.parse(html).selectFirst("table#notificationTable tbody");
        if (notificationTableBody == null) {
            return List.of();
        }
        // First column is the event, second column the recipient, third the actions
        // If there is a URL, the URL is the recipient. In that case we take the notification id from the edit button
        Elements entries = notificationTableBody.select("tr");
        List<Long> notificationIds = new ArrayList<>();
        for (Element entry : entries) {
            Elements columns = entry.select("td");
            if (columns.size() != 3) {
                continue;
            }
            String recipient = columns.get(1).text();
            String actions = columns.get(2).toString();
            Pattern editNotificationIdPattern = Pattern.compile(".*?id=\"editNotification:(\\d+)\".*?");
            if (recipient.trim().startsWith(artemisServerUrl)) {
                Matcher matcher = editNotificationIdPattern.matcher(actions);
                if (matcher.find()) {
                    String notificationIdString = matcher.group(1);
                    notificationIds.add(Long.parseLong(notificationIdString));
                }
            }
        }

        return notificationIds;
    }

    /**
     * Deletes the given notification id for the given build plan
     * Bamboo doesn't provide a REST endpoint for this action, but as we don't need to retrieve anything, it works the same way.
     *
     * @param buildPlanKey         The key of the build plan, which is usually the name combined with the project, e.g. 'EIST16W1-GA56HUR'.
     * @param serverNotificationId the id of the notification to delete
     */
    private void deleteBuildPlanServerNotificationId(String buildPlanKey, Long serverNotificationId) {
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("buildKey", buildPlanKey);
        parameters.add("notificationId", serverNotificationId.toString());

        String requestUrl = bambooServerUrl + "/chain/admin/config/deleteChainNotification.action";
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(requestUrl).queryParams(parameters);

        restTemplate.exchange(builder.build().toUri(), HttpMethod.POST, null, String.class);
    }

    /**
     * Creates a new notification for the given build plan
     * Bamboo doesn't provide a REST endpoint for this action, but as we don't need to retrieve anything, it works the same way.
     *
     * @param buildPlanKey          The key of the build plan, which is usually the name combined with the project, e.g. 'EIST16W1-GA56HUR'.
     * @param serverNotificationUrl the url of the endpoint to notify
     */
    private void createBuildPlanServerNotification(String buildPlanKey, String serverNotificationUrl) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("conditionKey", "com.atlassian.bamboo.plugin.system.notifications:chainCompleted.allBuilds");
        body.add("selectFields", "conditionKey");
        body.add("notificationRecipientType", "de.tum.in.www1.bamboo-server:recipient.server");
        body.add("selectFields", "notificationRecipientType");
        body.add("webhookUrl", serverNotificationUrl);
        body.add("buildKey", buildPlanKey);
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        headers.add("Content-Type", "application/x-www-form-urlencoded");

        String requestUrl = bambooServerUrl + "/chain/admin/config/configureChainNotification.action";
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(requestUrl);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        restTemplate.exchange(builder.build().toUri(), HttpMethod.POST, request, String.class);
    }

    /**
     * Deletes the given trigger id for the given build plan
     * Bamboo doesn't provide a REST endpoint for this action, but as we don't need to retrieve anything, it works the same way.
     *
     * @param buildPlanKey The key of the build plan, which is usually the name combined with the project, e.g. 'EIST16W1-GA56HUR'.
     * @param triggerId    the id of the notification to delete
     */
    private void deleteBuildPlanTriggerId(String buildPlanKey, Long triggerId) {
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("triggerId", triggerId.toString());
        parameters.add("planKey", buildPlanKey);

        String requestUrl = bambooServerUrl + "/chain/admin/config/deleteChainTrigger.action";
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(requestUrl).queryParams(parameters);

        restTemplate.exchange(builder.build().toUri(), HttpMethod.POST, null, String.class);
    }

    /**
     * Deletes the given repository from the given build plan
     *
     * @param buildPlanKey The key of the build plan, e.g. 'EIST16W1-BASE'.
     * @param repositoryId the id of the repository to delete
     */
    private void deleteLinkedRepository(String buildPlanKey, Long repositoryId) {
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("replaceRepository", Integer.toString(0));
        parameters.add("repositoryId", repositoryId.toString());
        parameters.add("planKey", buildPlanKey);

        String requestUrl = bambooServerUrl + "/chain/admin/config/deleteRepository.action";
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(requestUrl).queryParams(parameters);

        restTemplate.exchange(builder.build().toUri(), HttpMethod.POST, null, String.class);
    }

    /**
     * Sets the repositories to be checked out in the first task (-JOB1) of the given build plan.
     * testsRepositoryId is the id of the repository for the tests, assignmentRepositoryId is the id of the repository for the assignment.
     *
     * @param buildPlanId             The key of the build plan, e.g. 'EIST16W1-BASE'.
     * @param testsRepositoryId       The id of the repository for the tests checked out in the root folder
     * @param assignmentRepositoryId  The id of the repository for the assignment, checked out in the folder 'assignment'
     * @param assignmentRepositoryIds The ids of the auxiliary repositories, checked out in the folder specified in the AuxiliaryRepository object
     * @param auxiliaryRepositories   The auxiliary repositories to be checked out
     */
    private void setRepositoriesToCheckout(String buildPlanId, Long testsRepositoryId, Long assignmentRepositoryId, Map<String, Long> assignmentRepositoryIds,
            List<AuxiliaryRepository> auxiliaryRepositories) {
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("planKey", buildPlanId + "-JOB1");

        parameters.add("userDescription", "Checkout+Default+Repository");
        parameters.add("checkBoxFields", "taskDisabled");
        parameters.add("checkBoxFields", "conditionalTask");
        parameters.add("selectedCondition", "com.atlassian.bamboo.plugins.bamboo-conditional-tasks:variableCondition");
        parameters.add("selectFields", "selectedCondition");
        parameters.add("task.condition.variable.operation", "exists");
        parameters.add("selectFields", "task.condition.variable.operation");
        parameters.add("selectedRepository_0", testsRepositoryId.toString());
        parameters.add("selectFields", "selectedRepository_0");
        parameters.add("checkoutDir_0", "");
        parameters.add("selectedRepository_1", assignmentRepositoryId.toString());
        parameters.add("selectFields", "selectedRepository_1");
        parameters.add("checkoutDir_1", "assignment");
        int index = 2;
        for (var auxiliaryRepo : auxiliaryRepositories) {
            Long id = assignmentRepositoryIds.get(auxiliaryRepo.getName());
            if (id == null) {
                // should not happen, if it does, error has been logged before calling this method
                continue;
            }
            parameters.add("selectedRepository_" + index, id.toString());
            parameters.add("selectFields", "selectedRepository_" + index);
            parameters.add("checkoutDir_" + index, auxiliaryRepo.getCheckoutDirectory());
            index++;
        }
        parameters.add("checkBoxFields", "cleanCheckout");
        parameters.add("taskId", "1");

        String requestUrl = bambooServerUrl + "/build/admin/edit/updateTask.action";
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(requestUrl).queryParams(parameters);

        restTemplate.exchange(builder.build().toUri(), HttpMethod.POST, null, String.class);
    }

    /**
     * Returns the id of the shared credential for the git user configured in the application properties.
     *
     * @return the id of the shared credential for the git user configured in the application properties
     */
    private Optional<Long> getSharedCredential() {
        String requestUrl = bambooServerUrl + "/admin/credentials/configureSharedCredentials.action";
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(requestUrl);

        var response = restTemplate.exchange(builder.build().toUri(), HttpMethod.GET, null, String.class);
        var html = response.getBody();
        if (html == null) {
            return Optional.empty();
        }
        Elements entries = Jsoup.parse(html).select("table#sharedCredentialsTable tbody");

        for (Element entry : entries) {
            Elements row = entry.select("tr");
            Element name = entry.selectFirst("td");
            if (name != null && name.text().equals(gitUser)) {
                var id = row.attr("id");
                return Optional.of(Long.parseLong(id.replace("item-", "")));
            }

        }
        return Optional.empty();
    }

    /**
     * Adds a new git repository to the given build plan. The repository is configured to use the given credentials.
     * Bamboo doesn't provide a REST endpoint for this action, so we have to directly make the requests.
     *
     * @param buildPlanKey  The key of the build plan, e.g. 'EIST16W1-BASE'.
     * @param repository    The URL of the repository
     * @param name          The name of the repository, e.g. 'assignment' or 'tests'
     * @param credentialsId The id of the credentials to use for the repository
     * @param defaultBranch The default branch of the repository
     */
    private void addGitRepository(String buildPlanKey, String repository, String name, Long credentialsId, String defaultBranch) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("planKey", buildPlanKey);
        body.add("repositoryId", Integer.toString(0));
        body.add("selectedRepository", "com.atlassian.bamboo.plugins.atlassian-bamboo-plugin-git:gitv2");
        body.add("respositoryPluginKey", "com.atlassian.bamboo.plugins.atlassian-bamboo-plugin-git:gitv2");
        body.add("repositoryName", name);
        body.add("repository.git.repositoryUrl", repository);
        body.add("repository.git.authenticationType", "PASSWORD");
        body.add("selectFields", "repository.git.authenticationType");
        body.add("repository.git.passwordCredentialsSource", "SHARED_CREDENTIALS");
        body.add("repository.git.passwordSharedCredentials", credentialsId.toString());
        body.add("selectFields", "repository.git.passwordSharedCredentials");
        body.add("repository.git.branch", defaultBranch);
        body.add("repository.git.commandTimeout", Integer.toString(180));
        body.add("checkBoxFields", "repository.git.useShallowClones");
        body.add("repository.git.useShallowClones", Boolean.TRUE.toString());

        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        String requestUrl = bambooServerUrl + "/chain/admin/config/createRepository.action";
        parameters.add("planKey", buildPlanKey);

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(requestUrl).queryParams(parameters);

        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        headers.add("Content-Type", "multipart/form-data");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        restTemplate.exchange(builder.build().toUri(), HttpMethod.POST, request, String.class);
    }
}
