package de.tum.in.www1.artemis.service.connectors;

import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.swift.bamboo.cli.BambooClient;
import org.swift.bitbucket.cli.BitbucketClient;
import org.swift.bitbucket.cli.objects.RemoteRepository;
import org.swift.common.cli.Base;
import org.swift.common.cli.CliClient;

import de.tum.in.www1.artemis.exception.BambooException;

@Profile("bamboo")
@Service
public class BambooUpdateService {

    @Value("${artemis.bamboo.url}")
    protected URL BAMBOO_SERVER_URL;

    @Value("${artemis.bamboo.user}")
    protected String BAMBOO_USER;

    @Value("${artemis.bamboo.password}")
    protected String BAMBOO_PASSWORD;

    @Service
    @Profile("bitbucket")
    public class BitBucketBambooUpdateService extends BambooUpdateService implements ContinuousIntegrationUpdateService {

        private final Logger log = LoggerFactory.getLogger(BitBucketBambooUpdateService.class);

        @Value("${artemis.version-control.url}")
        private URL BITBUCKET_SERVER_URL;

        @Value("${artemis.version-control.user}")
        private String BITBUCKET_USER;

        @Value("${artemis.version-control.secret}")
        private String BITBUCKET_PASSWORD;

        @Value("${artemis.version-control.url}")
        private URL BITBUCKET_SERVER;

        private BitbucketClient getBitbucketClient() {
            // TODO: we might prevent console log message by passing a Settings object into Base
            final BitbucketClient bitbucketClient = new BitbucketClient(new Base());
            // setup the Bamboo Client to use the correct username and password

            String[] args = new String[] { "-s", BITBUCKET_SERVER_URL.toString(), "--user", BITBUCKET_USER, "--password", BITBUCKET_PASSWORD, };

            bitbucketClient.doWork(args); // only invoke this to set server address, username and password so that the following action will work
            return bitbucketClient;
        }

        @Override
        public String updatePlanRepository(String bambooProject, String planKey, String bambooRepositoryName, String bitbucketProject, String bitbucketRepository,
                Optional<List<String>> triggeredBy) {
            try {
                // get the repositoryId to find the correct value for field2 below
                final BitbucketClient bitbucketClient = getBitbucketClient();
                RemoteRepository remoteRepository = bitbucketClient.getRepositoryHelper().getRemoteRepository(bitbucketProject, bitbucketRepository, true);

                // TODO: we might prevent console log message by passing a Settings object into Base
                final BambooClient bambooClient = new BambooClient(new Base());
                String[] args = new String[] { "--field1", "repository.stash.projectKey", "--value1", bitbucketProject, "--targetServer", BITBUCKET_SERVER_URL.toString(), "-s",
                        BAMBOO_SERVER_URL.toString(), "--user", BAMBOO_USER, "--password", BAMBOO_PASSWORD, };
                // workaround to pass additional fields
                bambooClient.doWork(args);

                log.debug("Update plan repository for build plan " + planKey);
                org.swift.bamboo.cli.objects.RemoteRepository bambooRemoteRepository = bambooClient.getRepositoryHelper().getRemoteRepository(bambooRepositoryName, planKey, false);
                // Workaround for old exercises which used a different repositoryName
                if (bambooRemoteRepository == null) {
                    bambooRepositoryName = "Assignment";
                }

                String message = bambooClient.getRepositoryHelper().addOrUpdateRepository(remoteRepository.getSlug(), bambooRepositoryName, null, planKey, "BITBUCKET_SERVER", null,
                        false, true, true);

                // Overwrite triggers if needed
                triggeredBy.ifPresent(strings -> overwriteTriggers(planKey, bambooClient, strings));

                log.info("Update plan repository for build plan " + planKey + " was successful: " + message);
                return message;
            }
            catch (CliClient.ClientException | CliClient.RemoteRestException e) {
                log.error(e.getMessage(), e);
                throw new BambooException("Something went wrong while updating the plan repository", e);
            }
        }

        public void overwriteTriggers(final String planKey, final BambooClient bambooClient, final List<String> triggeredBy) {
            try {
                final var triggersString = bambooClient.getTriggerHelper().getTriggerList(planKey, null, null, 99, Pattern.compile(".*"));
                // Bamboo CLI returns a weird String, which is the reason for this way of parsing it
                final var oldTriggers = Arrays.stream(triggersString.split("\n")).map(trigger -> trigger.replace("\"", "").split(","))
                        .filter(trigger -> trigger.length > 2 && NumberUtils.isNumber(trigger[1])).map(trigger -> Long.parseLong(trigger[1])).collect(Collectors.toSet());

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
                throw new BambooException("Unable to overwrite triggers for " + planKey, e);
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
