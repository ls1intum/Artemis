package de.tum.in.www1.artemis.service.connectors.lti;

import java.util.Optional;
import java.util.Set;

import org.glassfish.jersey.uri.UriComponent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.enumeration.IncludedInOverallScore;
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

    private static final double DEFAULT_SCORE_MAXIMUM = 100D;

    private final ExerciseRepository exerciseRepository;

    private final Lti13TokenRetriever tokenRetriever;

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
     * Constructs an LTI Deep Linking response URL with JWT for the specified course and exercise.
     *
     * @param ltiIdToken           OIDC ID token with the user's authentication claims.
     * @param clientRegistrationId Client registration ID for the LTI tool.
     * @param courseId             ID of the course for deep linking.
     * @param exerciseIds          Set of IDs of the exercises for deep linking.
     * @return Constructed deep linking response URL.
     * @throws BadRequestAlertException if there are issues with the OIDC ID token claims.
     */
    public String performDeepLinking(OidcIdToken ltiIdToken, String clientRegistrationId, Long courseId, Set<Long> exerciseIds) {
        // Initialize DeepLinkingResponse
        Lti13DeepLinkingResponse lti13DeepLinkingResponse = new Lti13DeepLinkingResponse(ltiIdToken, clientRegistrationId);
        // Fill selected exercise link into content items
        String contentItems = this.populateContentItems(String.valueOf(courseId), exerciseIds);

        String contentItemsJsonArr = new GsonBuilder().create().toJson(contentItems);
        lti13DeepLinkingResponse.setContentItems(contentItemsJsonArr);

        // Prepare return url with jwt and id parameters
        return this.buildLtiDeepLinkResponse(clientRegistrationId, lti13DeepLinkingResponse);
    }

    /**
     * Build an LTI deep linking response URL.
     *
     * @return The LTI deep link response URL.
     */
    private String buildLtiDeepLinkResponse(String clientRegistrationId, Lti13DeepLinkingResponse lti13DeepLinkingResponse) {
        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromUriString(this.artemisServerUrl + "/lti/select-content");

        String jwt = tokenRetriever.createDeepLinkingJWT(clientRegistrationId, lti13DeepLinkingResponse.getClaims());
        String returnUrl = lti13DeepLinkingResponse.getReturnUrl();

        // Validate properties are set to create a response
        validateDeepLinkingResponseSettings(returnUrl, jwt, lti13DeepLinkingResponse.getDeploymentId());

        uriComponentsBuilder.queryParam("jwt", jwt);
        uriComponentsBuilder.queryParam("id", lti13DeepLinkingResponse.getDeploymentId());
        uriComponentsBuilder.queryParam("deepLinkUri", UriComponent.encode(returnUrl, UriComponent.Type.QUERY_PARAM));

        return uriComponentsBuilder.build().toUriString();

    }

    /**
     * Populate content items for deep linking response.
     *
     * @param courseId    The course ID.
     * @param exerciseIds The set of exercise IDs.
     */
    private String populateContentItems(String courseId, Set<Long> exerciseIds) {
        JsonArray contentItems = new JsonArray();
        for (Long exerciseId : exerciseIds) {
            JsonObject item = setContentItem(courseId, String.valueOf(exerciseId));
            contentItems.add(item);
        }

        // TODO: properly serialize JsonArray ( should not generate element and value fields)
        return contentItems.toString();
    }

    private JsonObject setContentItem(String courseId, String exerciseId) {
        Optional<Exercise> exerciseOpt = exerciseRepository.findById(Long.valueOf(exerciseId));
        String launchUrl = String.format(artemisServerUrl + "/courses/%s/exercises/%s", courseId, exerciseId);
        return exerciseOpt.map(exercise -> createContentItem(exerciseOpt.get(), launchUrl)).orElse(null);
    }

    private JsonObject createContentItem(Exercise exercise, String url) {
        JsonObject item = new JsonObject();
        item.addProperty("type", exercise.getType());
        item.addProperty("title", exercise.getTitle());
        item.addProperty("url", url);

        addLineItemIfIncluded(exercise, item);
        return item;
    }

    private void validateDeepLinkingResponseSettings(String returnURL, String jwt, String deploymentId) {
        if (isEmptyString(jwt)) {
            throw new BadRequestAlertException("Deep linking response cannot be created", "LTI", "deepLinkingResponseFailed");
        }

        if (isEmptyString(returnURL)) {
            throw new BadRequestAlertException("Cannot find platform return URL", "LTI", "deepLinkReturnURLEmpty");
        }

        if (isEmptyString(deploymentId)) {
            throw new BadRequestAlertException("Platform deployment id cannot be empty", "LTI", "deploymentIdEmpty");
        }
    }

    private boolean isEmptyString(String string) {
        return string == null || string.isEmpty();
    }

    private void addLineItemIfIncluded(Exercise exercise, JsonObject item) {
        if (exercise.getIncludedInOverallScore() != IncludedInOverallScore.NOT_INCLUDED) {
            JsonObject lineItem = new JsonObject();
            lineItem.addProperty("scoreMaximum", DEFAULT_SCORE_MAXIMUM);
            item.addProperty("lineItem", lineItem.toString());
        }
    }
}
