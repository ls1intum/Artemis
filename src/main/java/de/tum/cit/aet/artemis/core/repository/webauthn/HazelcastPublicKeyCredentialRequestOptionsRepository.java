package de.tum.cit.aet.artemis.core.repository.webauthn;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRequestOptions;
import org.springframework.security.web.webauthn.authentication.PublicKeyCredentialRequestOptionsRepository;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

public class HazelcastPublicKeyCredentialRequestOptionsRepository implements PublicKeyCredentialRequestOptionsRepository {

    private static final Logger log = LoggerFactory.getLogger(HazelcastPublicKeyCredentialRequestOptionsRepository.class);

    private static final String MAP_NAME = "PublicKeyCredentialRequestOptions";

    private final IMap<String, PublicKeyCredentialRequestOptions> hazelcastMap;

    static final String DEFAULT_ATTR_NAME = PublicKeyCredentialRequestOptionsRepository.class.getName().concat(".ATTR_NAME");

    private final String attrName = DEFAULT_ATTR_NAME;

    public HazelcastPublicKeyCredentialRequestOptionsRepository(HazelcastInstance hazelcastInstance) {
        this.hazelcastMap = hazelcastInstance.getMap(MAP_NAME);
    }

    @Override
    public void save(HttpServletRequest request, HttpServletResponse response, PublicKeyCredentialRequestOptions options) {
        log.info("Saving PublicKeyCredentialRequestOptions to session with id {}", request.getSession().getId());

        HttpSession session = request.getSession();
        session.setAttribute(this.attrName, options);

        logHazlecastMap();
        if (options != null) {
            hazelcastMap.put(session.getId(), options);
        }
        else {
            // hazelcastMap.remove(session.getId());
        }
        logHazlecastMap();
    }

    private void logHazlecastMap() {
        log.info("Logging all elements in the Hazelcast map:");
        log.info("Hazelcast size: {}", hazelcastMap.size());
        hazelcastMap.forEach((key, value) -> log.info("Key: {}, rpId: {}, challenge: {}", key, value.getRpId(), value.getChallenge().toString()));
    }

    private String getSessionId(HttpServletRequest request) {
        log.info("Getting session id for request {}", request.getRequestURI());

        // HttpSession session = request.getSession(false);

        // boolean sessionExistsInLocalStorage = session == null;
        // if (sessionExistsInLocalStorage) {
        // return request.getSession().getId();
        // }

        // this should only happen in multinode environments when the session was created on another node (with the `webauthn/authenticate/options` endpoint)
        return request.getRequestedSessionId();
    }

    @Override
    public PublicKeyCredentialRequestOptions load(HttpServletRequest request) {
        String sessionId = getSessionId(request);
        if (sessionId == null) {
            log.warn("Session ID is null. This might indicate that the session does not exist or has expired. Unable to load PublicKeyCredentialRequestOptions.");
            return null;
        }

        log.info("Loading PublicKeyCredentialRequestOptions for session id {}", sessionId);

        // load from hazelcast, if it is stored there
        // if (hazelcastMap.containsKey(sessionId)) {
        log.info("Searching PublicKeyCredentialRequestOptions in hazelcast for session with id {}", sessionId);
        logHazlecastMap();
        return hazelcastMap.get(sessionId);
        // }

        // not found in hazelcast => we have a new request => read from request options and store it in hazelcast
        // HttpSession session = request.getSession(false);
        // if (session == null) {
        // log.error("Session is null. Unable to load PublicKeyCredentialRequestOptions.");
        // return null;
        // }
        // PublicKeyCredentialRequestOptions options = (PublicKeyCredentialRequestOptions) session.getAttribute(this.attrName);
        //
        // log.info("request options from session attribute: {}", options);
        //
        // hazelcastMap.put(sessionId, options);
        //
        // logHazlecastMap();
        //
        // return options;
    }
}
