package de.tum.in.www1.artemis.service.connectors;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
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
import com.appfire.common.cli.*;
import com.appfire.common.cli.objects.RemoteApplicationLink;
import com.appfire.common.cli.requesthelpers.DefaultRequestHelper;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.exception.BambooException;

@Profile("bamboo")
@Service
public class BambooUpdateService {

    @Value("${artemis.continuous-integration.url}")
    protected URL BAMBOO_SERVER_URL;

    @Value("${artemis.continuous-integration.user}")
    protected String BAMBOO_USER;

    @Value("${artemis.continuous-integration.password}")
    protected String BAMBOO_PASSWORD;

    @Service
    @Profile("bitbucket")
    public class BitBucketBambooUpdateService extends BambooUpdateService implements ContinuousIntegrationUpdateService {

        private final Logger log = LoggerFactory.getLogger(BitBucketBambooUpdateService.class);

        @Value("${artemis.version-control.url}")
        private URL BITBUCKET_SERVER_URL;

        @Value("${artemis.version-control.user}")
        private String BITBUCKET_USER;

        @Value("${artemis.version-control.password}")
        private String BITBUCKET_PASSWORD;

        @Value("${artemis.version-control.url}")
        private URL BITBUCKET_SERVER;

        private Base createBase() {
            // we override the out stream to prevent unnecessary log statements in our log files
            ByteArrayOutputStream outContent = new ByteArrayOutputStream();
            var out = new PrintStream(outContent);
            var settings = new Settings();
            settings.setOut(out);
            settings.setOverrideOut(out);
            settings.setDebugOut(out);
            settings.setErr(out);
            return new Base(settings);
        }

        private BitbucketClient createBitbucketClient() {
            final BitbucketClient bitbucketClient = new BitbucketClient(createBase());
            // setup the Bamboo Client to use the correct username and password

            String[] args = new String[] { "-s", BITBUCKET_SERVER_URL.toString(), "--user", BITBUCKET_USER, "--password", BITBUCKET_PASSWORD, };
            bitbucketClient.doWork(args);   // only invoke this to set server address, username and password so that the following action will work
            return bitbucketClient;
        }

        @Override
        public void updatePlanRepository(String bambooProject, String planKey, String bambooRepositoryName, String bitbucketProject, String bitbucketRepository,
                Optional<List<String>> triggeredBy) {
            try {
                final BambooClient bambooClient = new BambooClient(createBase());
                String[] args = new String[] { "--targetServer", BITBUCKET_SERVER_URL.toString(), "-s", BAMBOO_SERVER_URL.toString(), "--user", BAMBOO_USER, "--password",
                        BAMBOO_PASSWORD, "--targetUser", BITBUCKET_USER, "--targetPassword", BITBUCKET_PASSWORD, };
                // workaround to pass additional fields
                bambooClient.doWork(args); // only invoke this to set server address, username and password so that the following action will work

                // get the repositoryId to find the correct value for the remote repository below
                RemoteRepository remoteRepository = createBitbucketClient().getRepositoryHelper().getRemoteRepository(bitbucketProject, bitbucketRepository, true);

                log.debug("Update plan repository for build plan " + planKey);
                com.appfire.bamboo.cli.objects.RemoteRepository bambooRemoteRepository = bambooClient.getRepositoryHelper().getRemoteRepository(bambooRepositoryName, planKey,
                        false);
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
                    triggeredBy = Optional
                            .of(triggeredBy.get().stream().map(trigger -> trigger.replace(Constants.ASSIGNMENT_REPO_NAME, "Assignment")).collect(Collectors.toList()));
                }
                triggeredBy.ifPresent(repoTriggers -> overwriteTriggers(planKey, bambooClient, repoTriggers));

                log.info("Update plan repository for build plan " + planKey + " was successful");
            }
            catch (CliClient.ClientException | CliClient.RemoteRestException e) {
                throw new BambooException(
                        "Something went wrong while updating the template repository of the build plan " + planKey + " to the student repository : " + e.getMessage(), e);
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
}
