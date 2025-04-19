package de.tum.cit.aet.artemis.core.repository.webauthn;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialCreationOptions;
import org.springframework.security.web.webauthn.registration.PublicKeyCredentialCreationOptionsRepository;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

import de.tum.cit.aet.artemis.core.dto.passkey.ArtemisAttestationConveyancePreferenceDTO;
import de.tum.cit.aet.artemis.core.dto.passkey.ArtemisAuthenticatorSelectionCriteriaDTO;
import de.tum.cit.aet.artemis.core.dto.passkey.ArtemisPublicKeyCredentialParametersDTO;
import de.tum.cit.aet.artemis.core.dto.passkey.ArtemisPublicKeyCredentialRpEntityDTO;
import de.tum.cit.aet.artemis.core.dto.passkey.PublicKeyCredentialCreationOptionsDTO;

public class HazelcastHttpSessionPublicKeyCredentialCreationOptionsRepository implements PublicKeyCredentialCreationOptionsRepository {

    private static final Logger log = LoggerFactory.getLogger(HazelcastHttpSessionPublicKeyCredentialCreationOptionsRepository.class);

    static final String DEFAULT_ATTR_NAME = HazelcastHttpSessionPublicKeyCredentialCreationOptionsRepository.class.getName().concat("ATTR_NAME");

    private final String attrName = DEFAULT_ATTR_NAME;

    private final HazelcastInstance hazelcastInstance;

    private static final String MAP_NAME = "http-session-public-key-credential-creation-options-map";

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
        // TODO sessionId appears to change and not to equal the requestedSessionId - is there a better way than using the userId?
        var userId = request.getRemoteUser();

        if (options != null) {
            creationOptionsMap.put(userId, toDTO(options));
        }
        else {
            // TODO verify this has no unwanted side effects (e.g. save method called with null options on different node)
            creationOptionsMap.remove(session.getId());
        }
    }

    public PublicKeyCredentialCreationOptions load(HttpServletRequest request) {
        var userId = request.getRemoteUser();
        if (userId == null) {
            log.warn("User ID is null. This might indicate that the session does not exist or has expired. Unable to load PublicKeyCredentialCreationOptions.");
            return null;
        }

        log.debug("Searching PublicKeyCredentialRequestOptions in hazelcast for user with id {}", userId);
        return creationOptionsMap.get(userId).toPublicKeyCredentialCreationOptions();
    }

    private PublicKeyCredentialCreationOptionsDTO toDTO(PublicKeyCredentialCreationOptions options) {
        //@formatter:off
        return new PublicKeyCredentialCreationOptionsDTO(
            options.getChallenge(),
            options.getUser(),
            new ArtemisAttestationConveyancePreferenceDTO(options.getAttestation().getValue()),
            new ArtemisPublicKeyCredentialRpEntityDTO(options.getRp().getName(), options.getRp().getId()),
            options.getPubKeyCredParams().stream()
                .map(param -> new ArtemisPublicKeyCredentialParametersDTO(param.getType(), param.getAlg().getValue()))
                .toList(),
            new ArtemisAuthenticatorSelectionCriteriaDTO(
                options.getAuthenticatorSelection().getAuthenticatorAttachment(),
                options.getAuthenticatorSelection().getResidentKey().toString(),
                options.getAuthenticatorSelection().getUserVerification()),
            options.getExcludeCredentials(),
            options.getExtensions(),
            options.getTimeout()
        );
        //@formatter:on
    }
}
