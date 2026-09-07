package de.tum.cit.aet.artemis.localvc.web.open;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;
import static de.tum.cit.aet.artemis.core.util.HttpRequestUtils.getPeerIpString;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.config.BuildAgentNetworkPolicy;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceNothing;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.localvc.dto.ObservedClientAddressDTO;

/**
 * Tells a caller which address this node sees it at.
 * <p>
 * Exists for the build agent origin binding. A core node authorizes an agent's clone by comparing the address the
 * request arrives from against the addresses that agent is registered at, and something has to establish what that
 * address is. Where the middleware can answer it - Hazelcast, whose clients connect to the cluster members, which are
 * these same nodes - a core node observes it directly. Where it cannot, the agent has to be told, because no host can
 * work out for itself how it appears on the other side of a NAT gateway or a load balancer: its own socket knows only
 * the pre-NAT local address, which is why {@code BuildAgentDTO.memberAddress} is not usable for this.
 * <p>
 * The answer is produced by the same {@link de.tum.cit.aet.artemis.core.util.HttpRequestUtils#getPeerIpString} call,
 * with the same trusted-proxy predicate, that the git path uses to decide the origin. That identity is the point: a
 * value derived any other way could differ from the one it will later be compared against, which is the failure this
 * endpoint exists to remove rather than relocate.
 * <p>
 * Unauthenticated on purpose, and safe to be: the response contains nothing but the caller's own address, which the
 * caller is by definition already able to observe about itself from any host that will talk to it. A build agent
 * needs it before it has any credential a core node would accept - the clone token belongs to a build job, and it has
 * none until it has an address to be authorized from.
 */
@Profile(PROFILE_LOCALVC)
@Lazy
@RestController
@RequestMapping("api/localvc/public/")
@FeatureUsage("access/build-agent-address")
public class PublicBuildAgentAddressResource {

    private static final Logger log = LoggerFactory.getLogger(PublicBuildAgentAddressResource.class);

    private final BuildAgentNetworkPolicy buildAgentNetworkPolicy;

    public PublicBuildAgentAddressResource(BuildAgentNetworkPolicy buildAgentNetworkPolicy) {
        this.buildAgentNetworkPolicy = buildAgentNetworkPolicy;
    }

    /**
     * GET observed-client-address : the address this node resolves the caller to.
     *
     * @param request the incoming request
     * @return the caller's address as this node sees it, which is the value its git requests will be authorized against
     */
    @GetMapping("observed-client-address")
    @EnforceNothing
    public ResponseEntity<ObservedClientAddressDTO> getObservedClientAddress(HttpServletRequest request) {
        String address = getPeerIpString(request, buildAgentNetworkPolicy::isTrustedProxy);
        log.debug("Reporting observed client address {}", address);
        return ResponseEntity.ok(new ObservedClientAddressDTO(address));
    }
}
