package de.tum.cit.aet.artemis.buildagent.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Set;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentAddressInfo;
import de.tum.cit.aet.artemis.localci.service.DistributedDataAccessService;

/**
 * Publishes the address a core node sees this build agent at, so that its clones can be bound to it.
 * <p>
 * The core nodes prefer to observe an agent's origin themselves, and under Hazelcast they can: an agent's client
 * connects to the cluster members, which are the nodes that serve git, so the address the middleware accepted is the
 * address a clone will arrive from. Under Redis it is not - clients connect to Redis, which is neither a core node nor
 * on the path to one - and the addresses genuinely differ wherever a gateway sits on one path and not the other. The
 * origin binding would then have to be switched off, which is what this restores.
 * <p>
 * <b>Why the agent cannot work this out alone.</b> A host knows only its own socket's local address, which is what it
 * looks like <em>before</em> any NAT between it and the core node. That is precisely why
 * {@code BuildAgentDTO.memberAddress} is unusable here. So the agent asks: it calls the core nodes' git base URL and is
 * told which address the request arrived from, resolved by the same code that will later decide the origin. What it
 * publishes is therefore a measurement taken on the path that matters, not a claim about itself.
 * <p>
 * <b>Why a self-reported value is still sound.</b> Writing it requires being in the cluster, and the registry already
 * grants a cluster member everything this could give away: such a node can read any build job's clone token straight
 * out of the queue, and can write to the address maps directly whatever this service does. The binding has never
 * defended against a cluster member - the cluster password, transport security and the build agent networks do that -
 * and it is unchanged for the adversary it does defend against, one holding a leaked token or key from outside. That
 * caller cannot publish anything, so it still has to call from an address the agent is registered at.
 *
 * @see de.tum.cit.aet.artemis.localci.service.BuildAgentAddressRegistryService
 */
@Service
@Profile(PROFILE_BUILDAGENT)
@Lazy(false)
public class BuildAgentAddressReportingService {

    private static final Logger log = LoggerFactory.getLogger(BuildAgentAddressReportingService.class);

    /**
     * How often the address is re-measured and republished. Comfortably shorter than the entry's lifetime, so a single
     * failed round never unregisters a live agent, and short enough that an agent whose address changes - a reconnect
     * behind a NAT gateway that rebinds - is authorized again quickly.
     */
    private static final long REPORT_INTERVAL_MS = 60_000;

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private static final String OBSERVED_ADDRESS_PATH = "api/localvc/public/observed-client-address";

    private final DistributedDataAccessService distributedDataAccessService;

    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).followRedirects(HttpClient.Redirect.NORMAL).build();

    @Value("${artemis.version-control.url}")
    private URI localVCBaseUri;

    @Value("${artemis.continuous-integration.build-agent.short-name}")
    private String buildAgentShortName;

    public BuildAgentAddressReportingService(DistributedDataAccessService distributedDataAccessService, ObjectMapper objectMapper) {
        this.distributedDataAccessService = distributedDataAccessService;
        this.objectMapper = objectMapper;
    }

    /**
     * Measures and republishes this agent's address.
     * <p>
     * Runs on every agent regardless of provider. Under Hazelcast the core nodes observe the same agent anyway and the
     * registry reads the union, so the two agree and the extra entry costs nothing; where they disagree - a load
     * balancer on the git path but not on the cluster path - having both is what keeps the agent authorized.
     */
    // The first attempt waits longer than the rest. An agent sharing a JVM with the core node it is configured against
    // is asking itself, and at five seconds the web server is reliably not accepting yet - a guaranteed failure and a
    // warning on every single startup, which is how a log stops being read.
    @Scheduled(initialDelay = 30_000, fixedDelay = REPORT_INTERVAL_MS)
    public void reportObservedAddress() {
        if (!distributedDataAccessService.isConnectedToCluster()) {
            return;
        }
        try {
            String address = measureObservedAddress();
            if (address == null || address.isBlank()) {
                return;
            }
            // withinAllowlist is left true because an agent cannot evaluate the core nodes' allowlist and must not
            // appear to have judged itself. The core nodes recompute it from the addresses before showing it, and the
            // allowlist is enforced against the address of the request itself regardless of anything stored here.
            distributedDataAccessService.getDistributedBuildAgentReportedAddresses().put(buildAgentShortName,
                    new BuildAgentAddressInfo(buildAgentShortName, Set.of(address), ZonedDateTime.now(), true));
            log.debug("Reported {} as the address core nodes see build agent {} at", address, buildAgentShortName);
        }
        catch (Exception e) {
            // Never let this stop the agent: failing to publish only means the origin binding has nothing to bind this
            // agent to, which leaves it unconstrained rather than refused. The next round retries.
            log.warn("Could not report the address build agent {} is seen at, so its clones are not bound to an address: {}", buildAgentShortName, e.getMessage());
        }
    }

    /**
     * Asks a core node which address it sees this agent at.
     *
     * @return the address as the core node resolved it, or null when it could not be obtained
     * @throws Exception if the request could not be made or the response could not be read
     */
    private String measureObservedAddress() throws Exception {
        URI uri = localVCBaseUri.resolve("/" + OBSERVED_ADDRESS_PATH);
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(REQUEST_TIMEOUT).GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            log.warn("Asking {} for the address it sees this build agent at returned {}", uri, response.statusCode());
            return null;
        }
        return objectMapper.readTree(response.body()).path("address").asText(null);
    }

    /**
     * Withdraws this agent's entry when it shuts down, so a planned restart does not leave an address authorizing
     * clones until it expires.
     */
    @PreDestroy
    public void removeReportedAddress() {
        try {
            distributedDataAccessService.getDistributedBuildAgentReportedAddresses().remove(buildAgentShortName);
        }
        catch (Exception e) {
            // The entry expires by itself, so a failure here costs only the time until it does
            log.debug("Could not withdraw the reported address of build agent {} during shutdown", buildAgentShortName, e);
        }
    }
}
