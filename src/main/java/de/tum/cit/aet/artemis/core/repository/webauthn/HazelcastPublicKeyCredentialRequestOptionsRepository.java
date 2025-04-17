package de.tum.cit.aet.artemis.core.repository.webauthn;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.security.web.webauthn.api.PublicKeyCredentialRequestOptions;
import org.springframework.security.web.webauthn.authentication.PublicKeyCredentialRequestOptionsRepository;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

public class HazelcastPublicKeyCredentialRequestOptionsRepository implements PublicKeyCredentialRequestOptionsRepository {

    private static final String MAP_NAME = "PublicKeyCredentialRequestOptions";

    private final IMap<String, PublicKeyCredentialRequestOptions> hazelcastMap;

    static final String DEFAULT_ATTR_NAME = PublicKeyCredentialRequestOptionsRepository.class.getName().concat(".ATTR_NAME");

    private final String attrName = DEFAULT_ATTR_NAME;

    public HazelcastPublicKeyCredentialRequestOptionsRepository(HazelcastInstance hazelcastInstance) {
        this.hazelcastMap = hazelcastInstance.getMap(MAP_NAME);
    }

    @Override
    public void save(HttpServletRequest request, HttpServletResponse response, PublicKeyCredentialRequestOptions options) {
        HttpSession session = request.getSession();
        session.setAttribute(this.attrName, options);
        hazelcastMap.remove(session.getId());
    }

    @Override
    public PublicKeyCredentialRequestOptions load(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }

        String sessionId = request.getSession().getId();
        // load from hazelcast, if it is stored there
        if (hazelcastMap.containsKey(sessionId)) {
            return hazelcastMap.get(sessionId);
        }
        // not found in hazelcast => we have a new request => read from request options and store it in hazelcast
        PublicKeyCredentialRequestOptions options = (PublicKeyCredentialRequestOptions) session.getAttribute(this.attrName);
        hazelcastMap.put(sessionId, options);

        return options;
    }
}
