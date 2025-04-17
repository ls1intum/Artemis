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

        log.info("Logging all elements in the Hazelcast map:");
        hazelcastMap.forEach((key, value) -> log.info("Key: {}, rpId: {}, challenge: {}", key, value.getRpId(), value.getChallenge().toString()));

        HttpSession session = request.getSession();
        session.setAttribute(this.attrName, options);

        log.info("Hazelcast size before removing session info: {}", hazelcastMap.size());
        hazelcastMap.remove(session.getId());
        log.info("Hazelcast size after removing session info: {}", hazelcastMap.size());
    }

    @Override
    public PublicKeyCredentialRequestOptions load(HttpServletRequest request) {
        log.info("Loading PublicKeyCredentialRequestOptions");
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        log.info("Loading PublicKeyCredentialRequestOptions from session with id {}", request.getSession().getId());

        String sessionId = request.getSession().getId();
        // load from hazelcast, if it is stored there
        if (hazelcastMap.containsKey(sessionId)) {
            log.info("Found PublicKeyCredentialRequestOptions in hazelcast for session with id {}", sessionId);
            return hazelcastMap.get(sessionId);
        }
        // not found in hazelcast => we have a new request => read from request options and store it in hazelcast
        PublicKeyCredentialRequestOptions options = (PublicKeyCredentialRequestOptions) session.getAttribute(this.attrName);
        hazelcastMap.put(sessionId, options);

        log.info("Logging all elements in the Hazelcast map:");
        hazelcastMap.forEach((key, value) -> log.info("Key: {}, rpId: {}, challenge: {}", key, value.getRpId(), value.getChallenge().toString()));

        log.info("Stored PublicKeyCredentialRequestOptions in hazelcast for session with id {}", sessionId);
        log.info("Hazelcast size: {}", hazelcastMap.size());

        return options;
    }
}
