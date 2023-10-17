package de.tum.in.www1.artemis.service.connectors.lti;

import java.util.HashMap;
import java.util.Map;
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
import de.tum.in.www1.artemis.domain.lti.Claims;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.security.lti.Lti13TokenRetriever;
import net.minidev.json.JSONObject;

@Service
@Profile("lti")
public class LtiDeepLinkingService {

    @Value("${server.url}")
    private String artemisServerUrl;

    private final Logger log = LoggerFactory.getLogger(LtiDeepLinkingService.class);

    private final ExerciseRepository exerciseRepository;

    private final Lti13TokenRetriever tokenRetriever;

    private JsonObject deepLinkingResponse;

    private JSONObject deepLinkingSettings;

    private String deploymentId;

    private String clientRegistrationId;

    public LtiDeepLinkingService(ExerciseRepository exerciseRepository, Lti13TokenRetriever tokenRetriever) {
        this.exerciseRepository = exerciseRepository;
        this.tokenRetriever = tokenRetriever;
        this.deepLinkingResponse = new JsonObject();
        this.deepLinkingSettings = new JSONObject();
    }

    public String buildLtiDeepLinkResponse() {
        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromUriString(this.artemisServerUrl + "/lti/select-content");
        Map<String, Object> claims = new HashMap<String, Object>();
        for (var entry : deepLinkingResponse.entrySet()) {
            claims.put(entry.getKey(), entry.getValue().getAsString());
        }
        String jwt = tokenRetriever.createDeepLinkingJWT(this.clientRegistrationId, claims);
        uriComponentsBuilder.queryParam("jwt", jwt);
        uriComponentsBuilder.queryParam("id", this.deploymentId);
        uriComponentsBuilder.queryParam("deepLinkUri", UriComponent.encode(this.deepLinkingSettings.getAsString("deep_link_return_url"), UriComponent.Type.QUERY_PARAM));

        return uriComponentsBuilder.build().toUriString();
    }

    public void setupDeepLinkingSettings(OidcIdToken ltiIdToken, String clientRegistrationId) {
        this.deepLinkingSettings = new JSONObject(ltiIdToken.getClaim(Claims.DEEP_LINKING_SETTINGS));
        this.deploymentId = ltiIdToken.getClaim(Claims.LTI_DEPLOYMENT_ID).toString();
        this.clientRegistrationId = clientRegistrationId;
    }

    public void populateDeepLinkingResponse(OidcIdToken ltiIdToken) {
        this.deepLinkingResponse.addProperty("aud", ltiIdToken.getClaim("iss").toString());
        this.deepLinkingResponse.addProperty("iss", ltiIdToken.getClaim("aud").toString().replace("[", "").replace("]", "")); // "http://localhost:9000/"
        this.deepLinkingResponse.addProperty("exp", ltiIdToken.getClaim("exp").toString());
        this.deepLinkingResponse.addProperty("iat", ltiIdToken.getClaim("iat").toString());
        this.deepLinkingResponse.addProperty("nonce", ltiIdToken.getClaim("nonce").toString());
        this.deepLinkingResponse.addProperty(Claims.MSG, "Content successfully linked");
        this.deepLinkingResponse.addProperty(Claims.LTI_DEPLOYMENT_ID, ltiIdToken.getClaim(Claims.LTI_DEPLOYMENT_ID).toString());
        this.deepLinkingResponse.addProperty(Claims.MESSAGE_TYPE, "LtiDeepLinkingResponse");
        this.deepLinkingResponse.addProperty(Claims.LTI_VERSION, "1.3.0");
    }

    public void populateContentItems(String courseId, String exerciseId) {
        JsonObject item = setContentItem(courseId, exerciseId);

        JsonArray contentItems = new JsonArray();
        contentItems.add(item);
        this.deepLinkingResponse.addProperty(Claims.CONTENT_ITEMS, contentItems.toString());
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
}
