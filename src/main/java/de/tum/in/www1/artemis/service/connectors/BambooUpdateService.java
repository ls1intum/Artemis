package de.tum.in.www1.artemis.service.connectors;

import java.net.URL;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.appfire.bamboo.cli.BambooClient;
import com.appfire.bitbucket.cli.BitbucketClient;
import com.appfire.bitbucket.cli.objects.RemoteRepository;
import com.appfire.common.cli.Base;
import com.appfire.common.cli.CliClient;
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
        public String updatePlanRepository(String bambooProject, String planKey, String bambooRepositoryName, String bitbucketProject, String bitbucketRepository) {
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
                com.appfire.bamboo.cli.objects.RemoteRepository bambooRemoteRepository = bambooClient.getRepositoryHelper().getRemoteRepository(bambooRepositoryName, planKey,
                        false);
                // Workaround for old exercises which used a different repositoryName
                if (bambooRemoteRepository == null) {
                    bambooRepositoryName = "Assignment";
                }

                String message = bambooClient.getRepositoryHelper().addOrUpdateRepository(remoteRepository.getSlug(), bambooRepositoryName, null, planKey, "BITBUCKET_SERVER", null,
                        false, true, true);
                log.info("Update plan repository for build plan " + planKey + " was successful: " + message);
                return message;
            }
            catch (CliClient.ClientException | CliClient.RemoteRestException e) {
                throw new BambooException(
                        "Something went wrong while updating the template repository of the build plan " + planKey + " to the student repository : " + e.getMessage(), e);
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
