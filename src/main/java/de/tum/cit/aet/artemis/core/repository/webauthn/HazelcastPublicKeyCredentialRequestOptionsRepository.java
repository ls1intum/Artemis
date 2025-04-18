package de.tum.cit.aet.artemis.core.repository.webauthn;

import java.util.Set;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRequestOptions;
import org.springframework.security.web.webauthn.authentication.PublicKeyCredentialRequestOptionsRepository;

import com.hazelcast.cluster.Member;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

public class HazelcastPublicKeyCredentialRequestOptionsRepository implements PublicKeyCredentialRequestOptionsRepository {

    private static final Logger log = LoggerFactory.getLogger(HazelcastPublicKeyCredentialRequestOptionsRepository.class);

    private static final String MAP_NAME = "public-key-credentials-request-options-map";

    private IMap<String, PublicKeyCredentialRequestOptions> authOptionsMap;

    static final String DEFAULT_ATTR_NAME = PublicKeyCredentialRequestOptionsRepository.class.getName().concat(".ATTR_NAME");

    private final String attrName = DEFAULT_ATTR_NAME;

    private final HazelcastInstance hazelcastInstance;

    @PostConstruct
    public void init() {
        int authOptionsTimeToLive = 300; // 5 minutes

        var mapConfig = hazelcastInstance.getConfig().getMapConfig(MAP_NAME);
        mapConfig.setTimeToLiveSeconds(authOptionsTimeToLive);
        authOptionsMap = hazelcastInstance.getMap(MAP_NAME);
    }

    public HazelcastPublicKeyCredentialRequestOptionsRepository(@Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance) {
        this.hazelcastInstance = hazelcastInstance;
    }

    @Override
    public void save(HttpServletRequest request, HttpServletResponse response, PublicKeyCredentialRequestOptions options) {
        log.info("Saving PublicKeyCredentialRequestOptions to session with id {}", request.getSession().getId());

        HttpSession session = request.getSession();
        session.setAttribute(this.attrName, options);

        logHazlecastMap();
        if (options != null) {
            authOptionsMap.put(session.getId(), options);
        }
        else {
            // hazelcastMap.remove(session.getId());
        }
        logHazlecastMap();
    }

    public void logClusterMembers(HazelcastInstance hazelcastInstance) {
        Set<Member> members = hazelcastInstance.getCluster().getMembers();
        log.info("Current Hazelcast cluster has {} member(s):", members.size());
        for (Member member : members) {
            log.info("\tMember UUID: {}, Address: {}", member.getUuid(), member.getAddress());
        }
    }

    private void logHazlecastMap() {
        logClusterMembers(hazelcastInstance);

        int size = authOptionsMap.size();
        log.info("Hazelcast map '{}' contains {} entries.", MAP_NAME, size);

        if (size == 0) {
            log.info("Hazelcast map '{}' is empty.", MAP_NAME);
        }
        else {
            authOptionsMap.forEach((key, value) -> {
                log.info("\tEntry in Hazelcast map '{}': Key = {}, RP ID = {}, Challenge = {}", MAP_NAME, key, value.getRpId(), value.getChallenge());
            });
        }
    }

    @Override
    public PublicKeyCredentialRequestOptions load(HttpServletRequest request) {
        String sessionId = request.getRequestedSessionId();
        if (sessionId == null) {
            log.warn("Session ID is null. This might indicate that the session does not exist or has expired. Unable to load PublicKeyCredentialRequestOptions.");
            return null;
        }

        log.info("Loading PublicKeyCredentialRequestOptions for session id {}", sessionId);

        // load from hazelcast, if it is stored there
        // if (hazelcastMap.containsKey(sessionId)) {
        log.info("Searching PublicKeyCredentialRequestOptions in hazelcast for session with id {}", sessionId);
        logHazlecastMap();
        return authOptionsMap.get(sessionId);
    }
}
