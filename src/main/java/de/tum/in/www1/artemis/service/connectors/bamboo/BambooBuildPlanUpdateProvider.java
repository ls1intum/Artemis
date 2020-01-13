package de.tum.in.www1.artemis.service.connectors.bamboo;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

import org.json.simple.JSONObject;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.appfire.bamboo.cli.BambooClient;
import com.appfire.common.cli.CliClient;
import com.appfire.common.cli.CliUtils;
import com.appfire.common.cli.JsonUtils;
import com.appfire.common.cli.objects.RemoteApplicationLink;
import com.appfire.common.cli.requesthelpers.DefaultRequestHelper;

@Component
@Profile("bamboo")
public class BambooBuildPlanUpdateProvider {

    private final BambooClient bambooClient;

    public BambooBuildPlanUpdateProvider(BambooClient bambooClient) {
        this.bambooClient = bambooClient;
    }

    public void updateRepository(@Nonnull com.appfire.bamboo.cli.objects.RemoteRepository bambooRemoteRepository, String bitbucketRepositoryName, String bitbucketProjectKey,
            String completePlanName) throws CliClient.ClientException, CliClient.RemoteRestException {

        Map<String, String> parameters = new HashMap<>();
        parameters.put("planKey", completePlanName);

        bambooClient.getRepositoryHelper().addRepositoryDetails(bambooRemoteRepository);

        parameters.put("selectedRepository", "com.atlassian.bamboo.plugins.stash.atlassian-bamboo-plugin-stash:stash-rep");
        // IMPORTANT: Don't change the name of the repo! We depend on the naming (assignment, tests) in some other parts of the application
        parameters.put("repositoryName", bambooRemoteRepository.getName());
        parameters.put("repositoryId", Long.toString(bambooRemoteRepository.getId()));
        parameters.put("confirm", "true");
        parameters.put("save", "Save repository");
        parameters.put("bamboo.successReturnMode", "json");
        parameters.put("repository.stash.branch", "master");

        com.appfire.bitbucket.cli.objects.RemoteRepository bitbucketRepository;
        try {
            bitbucketRepository = bambooClient.getBitbucketClient().getRepositoryHelper().getRemoteRepository(bitbucketProjectKey, bitbucketRepositoryName, true);
        }
        catch (CliClient.ClientException | CliClient.RemoteRestException ex) {
            throw new CliClient.ClientException("Bitbucket failed trying to get repository details: " + ex.getMessage());
        }

        RemoteApplicationLink link = bambooClient.getBitbucketClient().getApplicationLink();
        if (link == null) {
            link = bambooClient.getApplicationLinksRequestHelper().getApplicationLink(bambooClient.getString("targetServer"), "stash", true);
        }

        if (link != null) {
            parameters.put("repository.stash.server", link.getId());
        }

        parameters.put("repository.stash.repositoryId", bitbucketRepository.getIdString());
        parameters.put("repository.stash.repositorySlug", bitbucketRepository.getSlug());
        parameters.put("repository.stash.projectKey", bitbucketRepository.getProject());
        parameters.put("repository.stash.repositoryUrl", bitbucketRepository.getCloneSshUrl());

        String responseData = "";

        try {

            DefaultRequestHelper helper = bambooClient.getPseudoRequestHelper();
            helper.setRequestType(DefaultRequestHelper.RequestType.POST);
            helper.setContentType(DefaultRequestHelper.RequestContentType.JSON);
            helper.setParameters(parameters);
            helper.makeRequest("/chain/admin/config/updateRepository.action");
            responseData = helper.getResponseData();

        }
        catch (CliClient.RemoteInternalServerErrorException ex) {
            String message = "Request failed on the server with response code 500. Make sure all required fields have been provided using the various field and value parameters. "
                    + "The server log may provide insight into missing fields: " + ex.getMessage();
            throw new CliClient.ClientException(message);
        }

        JSONObject json = bambooClient.getJsonWithVerboseLogging(responseData);
        JSONObject repositoryJson = JsonUtils.getJsonOrNull(JsonUtils.getStringOrNull(json, "repositoryResult"));
        if (repositoryJson == null) {
            String error = checkForError(responseData);
            throw new CliClient.ClientException(error.equals("") ? "Unknown error occurred." : error);
        }
    }

    /**
     * This method was taken from RepositoryHelper of the Bamboo CLI Plugin
     * @param data the response from the server
     * @return an error message
     * @see com.appfire.bamboo.cli.helpers.RepositoryHelper
     */
    private String checkForError(String data) {
        String message = CliUtils.matchRegex(data, "(?s)<div[^>]*class=\"aui-message error\">\\s+<p>([^<]*)<").trim();

        String regex = "<div[^>]*class=\"error(?: control-form-error){0,1}\"[^>]*data-field-name=\"([^\"]+)\"[^<]*>([^<]*)</div>";
        Pattern pattern = Pattern.compile(regex, 2);
        Matcher matcher = pattern.matcher(data);
        if (matcher.find()) {
            String field = matcher.group(1);
            message = CliUtils.endWithPeriod(matcher.group(2)) + (field == null ? "" : " Error field is " + field + ".");
        }

        if (message.equals("")) {
            message = CliUtils.matchRegex(data, "<div[^>]*class=\"error(?: control-form-error){0,1}\"[^>]*>([^<]*)</div>").trim();
        }

        if (message.equals("")) {
            message = CliUtils.matchRegex(data, "error[^>]*>\\s*<p class=\"title\"[^>]*>([^<]*)<").trim();
        }

        return message;
    }
}
