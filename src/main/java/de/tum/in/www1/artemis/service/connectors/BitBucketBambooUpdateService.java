package de.tum.in.www1.artemis.service.connectors;

import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import org.apache.commons.lang3.math.NumberUtils;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.appfire.bamboo.cli.BambooClient;
import com.appfire.bitbucket.cli.BitbucketClient;
import com.appfire.bitbucket.cli.objects.RemoteRepository;
import com.appfire.common.cli.CliClient;
import com.appfire.common.cli.CliUtils;
import com.appfire.common.cli.JsonUtils;
import com.appfire.common.cli.objects.RemoteApplicationLink;
import com.appfire.common.cli.requesthelpers.DefaultRequestHelper;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.exception.BambooException;

@Service
@Profile("bamboo & bitbucket")
public class BitBucketBambooUpdateService implements ContinuousIntegrationUpdateService {

    @Value("${artemis.version-control.url}")
    private URL BITBUCKET_SERVER;

    private final Logger log = LoggerFactory.getLogger(BitBucketBambooUpdateService.class);

    private final BitbucketClient bitbucketClient;

    private final BambooClient bambooClient;

    public BitBucketBambooUpdateService(BitbucketClient bitbucketClient, BambooClient bambooClient) {
        this.bitbucketClient = bitbucketClient;
        this.bambooClient = bambooClient;
    }

    @Override
    public void updatePlanRepository(String bambooProject, String planKey, String bambooRepositoryName, String bitbucketProject, String bitbucketRepository,
            Optional<List<String>> triggeredBy) {
        try {

            // get the repositoryId to find the correct value for the remote repository below
            RemoteRepository remoteRepository = bitbucketClient.getRepositoryHelper().getRemoteRepository(bitbucketProject, bitbucketRepository, true);

            log.debug("Update plan repository for build plan " + planKey);
            com.appfire.bamboo.cli.objects.RemoteRepository bambooRemoteRepository = bambooClient.getRepositoryHelper().getRemoteRepository(bambooRepositoryName, planKey, false);
            // Workaround for old exercises which used a different repositoryName
            if (bambooRemoteRepository == null) {
                bambooRemoteRepository = bambooClient.getRepositoryHelper().getRemoteRepository("Assignment", planKey, false);
                if (bambooRemoteRepository == null) {
                    throw new BambooException("Something went wrong while updating the template repository of the build plan " + planKey
                            + " to the student repository : Could not find assignment nor Assignment repository");
                }
            }

            updateRepository(bambooClient, bambooRemoteRepository, remoteRepository.getSlug(), bitbucketProject, planKey);

            // Overwrite triggers if needed, incl workaround for different repo names
            if (triggeredBy.isPresent() && bambooRemoteRepository.getName().equals("Assignment")) {
                triggeredBy = Optional.of(triggeredBy.get().stream().map(trigger -> trigger.replace(Constants.ASSIGNMENT_REPO_NAME, "Assignment")).collect(Collectors.toList()));
            }
            triggeredBy.ifPresent(repoTriggers -> overwriteTriggers(planKey, bambooClient, repoTriggers));

            log.info("Update plan repository for build plan " + planKey + " was successful");
        }
        catch (CliClient.ClientException | CliClient.RemoteRestException e) {
            throw new BambooException("Something went wrong while updating the template repository of the build plan " + planKey + " to the student repository : " + e.getMessage(),
                    e);
        }
    }

    private void updateRepository(BambooClient bambooClient, @Nonnull com.appfire.bamboo.cli.objects.RemoteRepository bambooRemoteRepository, String bitbucketRepositoryName,
            String bitbucketProjectKey, String completePlanName) throws CliClient.ClientException, CliClient.RemoteRestException {

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

    private void overwriteTriggers(final String planKey, final BambooClient bambooClient, final List<String> triggeredBy) {
        try {
            final var triggersString = bambooClient.getTriggerHelper().getTriggerList(planKey, null, null, 99, Pattern.compile(".*"));
            // Bamboo CLI returns a weird String, which is the reason for this way of parsing it
            final var oldTriggers = Arrays.stream(triggersString.split("\n")).map(trigger -> trigger.replace("\"", "").split(","))
                    .filter(trigger -> trigger.length > 2 && NumberUtils.isCreatable(trigger[1])).map(trigger -> Long.parseLong(trigger[1])).collect(Collectors.toSet());

            // Remove all old triggers
            for (final var triggerId : oldTriggers) {
                bambooClient.getTriggerHelper().removeTrigger(planKey, null, null, triggerId, null, false);
            }

            // Add new triggers
            for (final var repo : triggeredBy) {
                bambooClient.getTriggerHelper().addTrigger(planKey, null, "remoteBitbucketServer", null, null, repo, null, null, false);
            }
        }
        catch (CliClient.ClientException | CliClient.RemoteRestException e) {
            throw new BambooException("Unable to overwrite triggers for " + planKey + "\n" + e.getMessage(), e);
        }
    }

    @Override
    public void triggerUpdate(String buildPlanId, boolean initialBuild) {
        // NOT NEEDED
    }

    /**
     * e.g. "ssh://git@repobruegge.in.tum.de:7999/madm/helloworld.git"
     * @param project the bitbucket project name
     * @param slug the bitbucket repo name
     * @return the ssh repository url
     */
    private String buildSshRepositoryUrl(String project, String slug) {
        final int sshPort = 7999;
        return "ssh://git@" + BITBUCKET_SERVER.getHost() + ":" + sshPort + "/" + project.toLowerCase() + "/" + slug.toLowerCase() + ".git";
    }
}
