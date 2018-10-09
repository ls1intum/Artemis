package de.tum.in.www1.artemis.service;

import de.tum.in.www1.artemis.exception.BambooException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.swift.bamboo.cli.BambooClient;
import org.swift.bitbucket.cli.BitbucketClient;
import org.swift.bitbucket.cli.objects.RemoteRepository;
import org.swift.common.cli.CliClient;

import java.net.URL;
import java.util.Map;

@Profile("bamboo")
@Service
public class BambooUpdateService {

    @Value("${artemis.bamboo.url}")
    protected URL BAMBOO_SERVER_URL;

    @Value("${artemis.bamboo.user}")
    protected String BAMBOO_USER;

    @Value("${artemis.bamboo.password}")
    protected String BAMBOO_PASSWORD;

    private BambooClient getBambooClient() {
        final BambooClient bambooClient = new BambooClient();
        //setup the Bamboo Client to use the correct username and password

        String[] args = new String[]{
            "-s", BAMBOO_SERVER_URL.toString(),
            "--user", BAMBOO_USER,
            "--password", BAMBOO_PASSWORD,
        };

        bambooClient.doWork(args); //only invoke this to set server address, username and password so that the following action will work
        return bambooClient;
    }

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

        @Value("${artemis.bamboo.bitbucket-application-link-id}")
        private String BITBUCKET_APPLICATION_LINK_ID;

        private BitbucketClient getBitbucketClient() {
            final BitbucketClient bitbucketClient = new BitbucketClient();
            //setup the Bamboo Client to use the correct username and password

            String[] args = new String[]{
                "-s", BITBUCKET_SERVER_URL.toString(),
                "--user", BITBUCKET_USER,
                "--password", BITBUCKET_PASSWORD,
            };

            bitbucketClient.doWork(args); //only invoke this to set server address, username and password so that the following action will work
            return bitbucketClient;
        }

        @Override
        public String updatePlanRepository(String bambooProject, String bambooPlan, String bambooRepositoryName, String bitbucketProject, String bitbucketRepository) {
            try {
                //get the repositoryId to find the correct value for field2 below
                final BitbucketClient bitbucketClient = getBitbucketClient();
                RemoteRepository remoteRepository = bitbucketClient.getRepositoryHelper().getRemoteRepository(bitbucketProject, bitbucketRepository, true);

                final BambooClient bambooClient = new BambooClient();
                String[] args = new String[]{
                    "--field1", "repository.stash.projectKey", "--value1", bitbucketProject,
                    "--field2", "repository.stash.repositoryId", "--value2", remoteRepository.getId().toString(),
                    "--field3", "repository.stash.repositorySlug", "--value3", bitbucketRepository,
                    "--field4", "repository.stash.repositoryUrl", "--value4", buildSshRepositoryUrl(bitbucketProject, bitbucketRepository), // e.g. "ssh://git@repobruegge.in.tum.de:7999/madm/helloworld.git"
                    "--field5", "repository.stash.server", "--value5", BITBUCKET_APPLICATION_LINK_ID,
                    "--field6", "repository.stash.branch", "--value6", "master",
                    "-s", BAMBOO_SERVER_URL.toString(),
                    "--user", BAMBOO_USER,
                    "--password", BAMBOO_PASSWORD,
//            "--targetServer", "https://repobruegge.in.tum.de"     //in the future, we might be able to use this and save many other arguments above, then we could also get rid of BITBUCKET_APPLICATION_LINK_ID
                };
                //workaround to pass additional fields
                bambooClient.doWork(args);

                log.info("Update plan repository for build plan " + bambooProject + "-" + bambooPlan);
                String message = bambooClient.getRepositoryHelper().addOrUpdateRepository(bambooRepositoryName, null, null, bambooProject + "-" + bambooPlan, "BITBUCKET_SERVER", null, false, true, true);
                log.info("Update plan repository for build plan " + bambooProject + "-" + bambooPlan + " was successful. " + message);
                return message;
            } catch (CliClient.ClientException | CliClient.RemoteRestException e) {
                log.error(e.getMessage(), e);
                throw new BambooException("Something went wrong while updating the plan repository", e);
            }
        }

        @Override
        public void triggerUpdate(String buildPlanId, boolean initialBuild) {
            // NOT NEEDED
        }

        private String buildSshRepositoryUrl(String project, String slug) {
            final int sshPort = 7999;

            return "ssh://git@" + BITBUCKET_SERVER.getHost() + ":" + sshPort + "/" + project.toLowerCase() + "/" + slug.toLowerCase() + ".git";
        }
    }
}
