package de.tum.cit.aet.artemis.core.repository.webauthn;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialCreationOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialParameters;
import org.springframework.security.web.webauthn.registration.PublicKeyCredentialCreationOptionsRepository;

import com.hazelcast.cluster.Member;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

public class HazelcastHttpSessionPublicKeyCredentialCreationOptionsRepository implements PublicKeyCredentialCreationOptionsRepository {

    private static final Logger log = LoggerFactory.getLogger(HazelcastHttpSessionPublicKeyCredentialCreationOptionsRepository.class);

    static final String DEFAULT_ATTR_NAME = HazelcastHttpSessionPublicKeyCredentialCreationOptionsRepository.class.getName().concat("ATTR_NAME");

    private final String attrName = DEFAULT_ATTR_NAME;

    private final HazelcastInstance hazelcastInstance;

    private static final String MAP_NAME = "http-session-public-key-credential-creation-options-map";

    // private IMap<String, PublicKeyCredentialCreationOptions> creationOptionsMap;
    private IMap<String, PublicKeyCredentialCreationOptionsDTO> creationOptionsMap;

    public HazelcastHttpSessionPublicKeyCredentialCreationOptionsRepository(@Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance) {
        this.hazelcastInstance = hazelcastInstance;
    }

    @PostConstruct
    public void init() {
        int registrationOptionsTimeToLive = 300; // 5 minutes

        var mapConfig = hazelcastInstance.getConfig().getMapConfig(MAP_NAME);
        mapConfig.setTimeToLiveSeconds(registrationOptionsTimeToLive);
        creationOptionsMap = hazelcastInstance.getMap(MAP_NAME);
    }

    @Override
    public void save(HttpServletRequest request, HttpServletResponse response, PublicKeyCredentialCreationOptions options) {

        HttpSession session = request.getSession();
        session.setAttribute(this.attrName, options);

        if (options != null) {
            // creationOptionsMap.put(session.getId(), toDTO(options));
            creationOptionsMap.put(session.getId(), toDTO(options));
        }
        else {
            // TODO verify this has no unwanted sideeffects (e.g. save method called with null options on different node)
            // hazelcastMap.remove(session.getId());
        }
        logHazelcastMap();
    }

    public PublicKeyCredentialCreationOptions load(HttpServletRequest request) {
        String sessionId = request.getRequestedSessionId();
        if (sessionId == null) {
            log.warn("Session ID is null. This might indicate that the session does not exist or has expired. Unable to load PublicKeyCredentialCreationOptions.");
            return null;
        }

        log.info("Searching PublicKeyCredentialRequestOptions in hazelcast for session with id {}", sessionId);
        logHazelcastMap();
        return fromDTO(creationOptionsMap.get(sessionId));
    }

    private PublicKeyCredentialCreationOptionsDTO toDTO(PublicKeyCredentialCreationOptions options) {
        PublicKeyCredentialCreationOptionsDTO dto = new PublicKeyCredentialCreationOptionsDTO();
        dto.setRpName(options.getRp().getName());
        dto.setUserId(options.getUser().getId().toBase64UrlString());
        dto.setUserName(options.getUser().getName());
        dto.setUserDisplayName(options.getUser().getDisplayName());
        dto.setChallenge(options.getChallenge().toBase64UrlString());
        dto.setPubKeyCredParams(options.getPubKeyCredParams().stream().map(param -> param.getAlg().toString()).toList());
        // dto.setPublicKeyCredentialCreationOptions(options);
        return dto;
    }

    private PublicKeyCredentialCreationOptions fromDTO(PublicKeyCredentialCreationOptionsDTO dto) {
        //@formatter:off
        return PublicKeyCredentialCreationOptions.builder()
            .rp(org.springframework.security.web.webauthn.api.PublicKeyCredentialRpEntity.builder()
                .name(dto.getRpName())
                .id("example-id") // todo replace with actual id
                .build())
            .user(ImmutablePublicKeyCredentialUserEntity.builder().id(Bytes.fromBase64(dto.getUserId())).name(dto.getUserName()).displayName(dto.getUserDisplayName()).build())
            .challenge(Bytes.fromBase64(dto.getChallenge()))
            .pubKeyCredParams(dto.getPubKeyCredParams().stream()
                .map(this::mapToPublicKeyCredentialParameters)
                .toList())
            .timeout(Duration.ofSeconds(300)) // Example timeout
            .excludeCredentials(List.of()) // Example empty list
            .authenticatorSelection(null) // Example null value
            .attestation(null) // Example null value
            .extensions(null) // Example null value
            .build();
        //@formatter:on
    }

    private PublicKeyCredentialParameters mapToPublicKeyCredentialParameters(String alg) {
        return switch (Integer.parseInt(alg)) {
            case -7 -> PublicKeyCredentialParameters.ES256;
            case -35 -> PublicKeyCredentialParameters.ES384;
            case -36 -> PublicKeyCredentialParameters.ES512;
            case -257 -> PublicKeyCredentialParameters.RS256;
            case -258 -> PublicKeyCredentialParameters.RS384;
            case -259 -> PublicKeyCredentialParameters.RS512;
            case -8 -> PublicKeyCredentialParameters.EdDSA;
            default -> throw new IllegalArgumentException("Unsupported algorithm: " + alg);
        };
    }

    private void logClusterMembers(HazelcastInstance hazelcastInstance) {
        Set<Member> members = hazelcastInstance.getCluster().getMembers();
        log.info("Current Hazelcast cluster has {} member(s):", members.size());
        for (Member member : members) {
            log.info("\tMember UUID: {}, Address: {}", member.getUuid(), member.getAddress());
        }
    }

    private void logHazelcastMap() {
        logClusterMembers(hazelcastInstance);

        int size = creationOptionsMap.size();
        log.info("Hazelcast map '{}' contains {} entries.", MAP_NAME, size);

        if (size == 0) {
            log.info("Hazelcast map '{}' is empty.", MAP_NAME);
        }
        else {
            creationOptionsMap.forEach((key, value) -> {
                log.info("\tEntry in Hazelcast map '{}': Key = {}, Challenge = {}", MAP_NAME, key, value.getChallenge());
            });
        }
    }

}
