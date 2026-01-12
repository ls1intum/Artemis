package de.tum.cit.aet.artemis.core.repository.passkey;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRequestOptions;
import org.springframework.security.web.webauthn.authentication.PublicKeyCredentialRequestOptionsRepository;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.programming.service.localci.DistributedDataAccessService;

/**
 * A distributed implementation of {@link PublicKeyCredentialRequestOptionsRepository} using Hazelcast
 * to store and synchronize WebAuthn authentication request options across multiple nodes.
 *
 * <p>
 * This implementation ensures that authentication challenges (e.g., Face ID, fingerprint scan)
 * remain consistent in clustered environments, supporting stateless or load-balanced deployments.
 * </p>
 *
 * <p>
 * The repository stores options in Hazelcast with a short time-to-live (2 minutes by default),
 * since authentication is a fast, single-step user interaction. Stored options are removed after use
 * or expiration.
 * </p>
 *
 * <p>
 * This bean is only active under the {@code core} Spring profile.
 * </p>
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public class DistributedPublicKeyCredentialRequestOptionsRepository implements PublicKeyCredentialRequestOptionsRepository {

    private static final Logger log = LoggerFactory.getLogger(DistributedPublicKeyCredentialRequestOptionsRepository.class);

    /** Default session attribute name used to store options in the local session */
    static final String DEFAULT_ATTR_NAME = PublicKeyCredentialRequestOptionsRepository.class.getName().concat(".ATTR_NAME");

    /** Session attribute name used internally */
    private final String attrName = DEFAULT_ATTR_NAME;

    private final DistributedDataAccessService distributedDataAccessService;

    /**
     * Constructs the repository using the injected DistributedDataAccessService.
     *
     * @param distributedDataAccessService the shared distributed data access service
     */
    public DistributedPublicKeyCredentialRequestOptionsRepository(DistributedDataAccessService distributedDataAccessService) {
        this.distributedDataAccessService = distributedDataAccessService;
    }

    /**
     * Saves the given {@link PublicKeyCredentialRequestOptions} in both the local HTTP session
     * and the Hazelcast distributed map.
     *
     * <p>
     * If {@code options} is {@code null}, the entry is removed instead.
     * </p>
     *
     * @param request  the current HTTP request (used to get the session)
     * @param response the current HTTP response (not used)
     * @param options  the WebAuthn challenge options to store or remove
     */
    @Override
    public void save(HttpServletRequest request, HttpServletResponse response, PublicKeyCredentialRequestOptions options) {
        HttpSession session = request.getSession();
        session.setAttribute(this.attrName, options);

        if (options != null) {
            distributedDataAccessService.getDistributedPasskeyAuthOptionsMap().put(session.getId(), options, 2, java.util.concurrent.TimeUnit.MINUTES);
        }
        else {
            distributedDataAccessService.getDistributedPasskeyAuthOptionsMap().remove(session.getId());
        }
    }

    /**
     * Loads the previously saved {@link PublicKeyCredentialRequestOptions} from the Hazelcast map
     * using the requested session ID from the HTTP request.
     *
     * @param request the HTTP request (used to extract session ID)
     * @return the stored {@link PublicKeyCredentialRequestOptions}, or {@code null} if not found or session is missing
     */
    @Override
    public PublicKeyCredentialRequestOptions load(HttpServletRequest request) {
        String sessionId = request.getRequestedSessionId();
        if (sessionId == null) {
            log.warn("Session ID is null. This might indicate that the session does not exist or has expired. Unable to load PublicKeyCredentialRequestOptions.");
            return null;
        }

        return distributedDataAccessService.getPasskeyAuthOptionsMap().get(sessionId);
    }
}
