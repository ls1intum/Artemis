package de.tum.in.www1.artemis.service.connectors.bamboo;

import static de.tum.in.www1.artemis.config.Constants.NEW_RESULT_RESOURCE_API_PATH;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import de.tum.in.www1.artemis.domain.VcsRepositoryUrl;
import de.tum.in.www1.artemis.service.connectors.ci.CIMigrationService;

/**
 * Services for executing migration tasks for Bamboo.
 */
@Service
@Profile("bamboo")
public class BambooMigrationService implements CIMigrationService {

    @Value("${artemis.continuous-integration.url}")
    protected URL bambooServerUrl;

    @Value("${server.url}")
    private String artemisServerUrl;

    private final RestTemplate restTemplate;

    public BambooMigrationService(@Qualifier("bambooRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public void overrideBuildPlanNotification(String projectKey, String buildPlanKey, VcsRepositoryUrl vcsRepositoryUrl) {
        List<Long> notificationIds = getAllArtemisBuildPlanServerNotificationIds(buildPlanKey);

        for (var id : notificationIds) {
            deleteBuildPlanServerNotificationId(buildPlanKey, id);
        }

        createBuildPlanServerNotification(buildPlanKey, artemisServerUrl + NEW_RESULT_RESOURCE_API_PATH);
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
}
