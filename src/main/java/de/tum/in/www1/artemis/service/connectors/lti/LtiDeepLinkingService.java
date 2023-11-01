package de.tum.in.www1.artemis.service.connectors.lti;

import java.util.Optional;

import org.glassfish.jersey.uri.UriComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.lti.Lti13DeepLinkingResponse;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.security.lti.Lti13TokenRetriever;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

/**
 * Service for handling LTI deep linking functionality.
 */
@Service
@Profile("lti")
public class LtiDeepLinkingService {

    @Value("${server.url}")
    private String artemisServerUrl;

    private final Logger log = LoggerFactory.getLogger(LtiDeepLinkingService.class);

    private final ExerciseRepository exerciseRepository;

    private final Lti13TokenRetriever tokenRetriever;

    private Lti13DeepLinkingResponse lti13DeepLinkingResponse;

    private String clientRegistrationId;

    /**
     * Constructor for LtiDeepLinkingService.
     *
     * @param exerciseRepository The repository for exercises.
     * @param tokenRetriever     The LTI 1.3 token retriever.
     */
    public LtiDeepLinkingService(ExerciseRepository exerciseRepository, Lti13TokenRetriever tokenRetriever) {
        this.exerciseRepository = exerciseRepository;
        this.tokenRetriever = tokenRetriever;
    }

    /**
     * Build an LTI deep linking response URL.
     *
     * @return The LTI deep link response URL.
     */
    public String buildLtiDeepLinkResponse() {
        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromUriString(this.artemisServerUrl + "/lti/select-content");

        String jwt = tokenRetriever.createDeepLinkingJWT(this.clientRegistrationId, this.lti13DeepLinkingResponse.getClaims());
        String returnUrl = this.lti13DeepLinkingResponse.getReturnUrl();

        // Validate properties are set to create a response
        validateDeepLinkingResponseSettings(returnUrl, jwt);

        uriComponentsBuilder.queryParam("jwt", jwt);
        uriComponentsBuilder.queryParam("id", this.lti13DeepLinkingResponse.getDeploymentId());
        uriComponentsBuilder.queryParam("deepLinkUri", UriComponent.encode(returnUrl, UriComponent.Type.QUERY_PARAM));

        return uriComponentsBuilder.build().toUriString();

    }

    /**
     * Initialize the deep linking response with information from OidcIdToken and clientRegistrationId.
     *
     * @param ltiIdToken           The LTI 1.3 ID token.
     * @param clientRegistrationId The LTI 1.3 client registration id.
     */
    public void initializeDeepLinkingResponse(OidcIdToken ltiIdToken, String clientRegistrationId) {
        this.clientRegistrationId = clientRegistrationId;
        this.lti13DeepLinkingResponse = new Lti13DeepLinkingResponse(ltiIdToken, clientRegistrationId);
    }

    /**
     * Populate content items for deep linking response.
     *
     * @param courseId   The course ID.
     * @param exerciseId The exercise ID.
     */
    public void populateContentItems(String courseId, String exerciseId) {
        if (this.lti13DeepLinkingResponse == null) {
            throw new BadRequestAlertException("Deep linking response is not initialized correctly.", "LTI", "deepLinkingResponseInitializeFailed");
        }

        JsonObject item = setContentItem(courseId, exerciseId);
        JsonArray contentItems = new JsonArray();
        contentItems.add(item);
        this.lti13DeepLinkingResponse.setContentItems(contentItems.toString());
    }

    public String getClientRegistrationId() {
        return clientRegistrationId;
    }

    public void setClientRegistrationId(String clientRegistrationId) {
        this.clientRegistrationId = clientRegistrationId;
    }

    public Lti13DeepLinkingResponse getLti13DeepLinkingResponse() {
        return lti13DeepLinkingResponse;
    }

    public void setLti13DeepLinkingResponse(Lti13DeepLinkingResponse lti13DeepLinkingResponse) {
        this.lti13DeepLinkingResponse = lti13DeepLinkingResponse;
    }

    private JsonObject setContentItem(String courseId, String exerciseId) {
        Optional<Exercise> exerciseOpt = exerciseRepository.findById(Long.valueOf(exerciseId));
        String launchUrl = String.format(artemisServerUrl + "/courses/%s/exercises/%s", courseId, exerciseId);
        return createContentItem(exerciseOpt.get().getType(), exerciseOpt.get().getTitle(), launchUrl);
    }

    private JsonObject createContentItem(String type, String title, String url) {
        JsonObject item = new JsonObject();
        item.addProperty("type", type);
        item.addProperty("title", title);
        item.addProperty("url", url);
        return item;
    }

    private void validateDeepLinkingResponseSettings(String returnURL, String jwt) {
        if (isEmptyString(jwt)) {
            throw new BadRequestAlertException("Deep linking response cannot be created", "LTI", "deepLinkingResponseFailed");
        }

        if (isEmptyString(returnURL)) {
            throw new BadRequestAlertException("Cannot find platform return URL", "LTI", "deepLinkReturnURLEmpty");
        }

        if (isEmptyString(this.lti13DeepLinkingResponse.getDeploymentId())) {
            throw new BadRequestAlertException("Platform deployment id cannot be empty", "LTI", "deploymentIdEmpty");
        }
    }

    boolean isEmptyString(String string) {
        return string == null || string.isEmpty();
    }
}
