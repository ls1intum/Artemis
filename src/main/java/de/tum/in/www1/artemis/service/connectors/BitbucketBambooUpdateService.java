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

import com.appfire.bamboo.cli.BambooClient;
import com.appfire.bitbucket.cli.BitbucketClient;
import com.appfire.common.cli.CliClient;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.exception.BambooException;
import de.tum.in.www1.artemis.service.connectors.bamboo.BambooBuildPlanUpdateProvider;

@Service
@Profile("bamboo & bitbucket")
public class BitbucketBambooUpdateService implements ContinuousIntegrationUpdateService {

    @Value("${artemis.version-control.url}")
    private URL BITBUCKET_SERVER;

    private final Logger log = LoggerFactory.getLogger(BitbucketBambooUpdateService.class);

    private final BitbucketClient bitbucketClient;

    private final BambooClient bambooClient;

    private final BambooBuildPlanUpdateProvider bambooBuildPlanUpdateProvider;

    public BitbucketBambooUpdateService(BitbucketClient bitbucketClient, BambooClient bambooClient, BambooBuildPlanUpdateProvider bambooBuildPlanUpdateProvider) {
        this.bitbucketClient = bitbucketClient;
        this.bambooClient = bambooClient;
        this.bambooBuildPlanUpdateProvider = bambooBuildPlanUpdateProvider;
    }

    @Override
    public void updatePlanRepository(String bambooProject, String planKey, String bambooRepositoryName, String bitbucketProject, String bitbucketRepository,
            Optional<List<String>> triggeredBy) {
        try {
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

            bambooBuildPlanUpdateProvider.updateRepository(bambooRemoteRepository, bitbucketRepository, bitbucketProject, planKey);

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
