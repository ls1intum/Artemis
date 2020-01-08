package de.tum.in.www1.artemis.service.connectors;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.appfire.bamboo.cli.BambooClient;
import com.appfire.common.cli.CliClient;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.exception.BambooException;
import de.tum.in.www1.artemis.service.connectors.bamboo.BambooBuildPlanUpdateProvider;

@Service
// Only activate this service bean, if both Bamboo and Bitbucket are activated (@Profile({"bitbucket","bamboo"}) would activate
// this if any profile is active (OR). We want both (AND)
@Profile("bamboo & bitbucket")
public class BitbucketBambooUpdateService implements ContinuousIntegrationUpdateService {

    private static final String OLD_ASSIGNMENT_REPO_NAME = "Assignment";

    private final Logger log = LoggerFactory.getLogger(BitbucketBambooUpdateService.class);

    private final BambooClient bambooClient;

    private final BambooBuildPlanUpdateProvider bambooBuildPlanUpdateProvider;

    public BitbucketBambooUpdateService(BambooClient bambooClient, BambooBuildPlanUpdateProvider bambooBuildPlanUpdateProvider) {
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
                bambooRemoteRepository = bambooClient.getRepositoryHelper().getRemoteRepository(OLD_ASSIGNMENT_REPO_NAME, planKey, false);
                if (bambooRemoteRepository == null) {
                    throw new BambooException("Something went wrong while updating the template repository of the build plan " + planKey
                            + " to the student repository : Could not find assignment nor Assignment repository");
                }
            }

            bambooBuildPlanUpdateProvider.updateRepository(bambooRemoteRepository, bitbucketRepository, bitbucketProject, planKey);

            // Overwrite triggers if needed, incl workaround for different repo names
            if (triggeredBy.isPresent() && bambooRemoteRepository.getName().equals(OLD_ASSIGNMENT_REPO_NAME)) {
                triggeredBy = Optional
                        .of(triggeredBy.get().stream().map(trigger -> trigger.replace(Constants.ASSIGNMENT_REPO_NAME, OLD_ASSIGNMENT_REPO_NAME)).collect(Collectors.toList()));
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
}
