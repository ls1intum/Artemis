package de.tum.in.www1.artemis.service.connectors.lti;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.stereotype.Service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import de.tum.in.www1.artemis.domain.lti.Claims;
import net.minidev.json.JSONObject;

@Service
@Profile("lti")
public class LtiDeepLinkingService {

    private final Logger log = LoggerFactory.getLogger(LtiDeepLinkingService.class);

    private JsonObject deepLinkingResponse;

    private JSONObject deepLinkingSettings;

    public LtiDeepLinkingService() {
        this.deepLinkingResponse = new JsonObject();
        this.deepLinkingSettings = new JSONObject();
    }

    public void buildLtiDeepLinkingResponse(OidcIdToken ltiIdToken) {
        populateDeepLinkingResponse(ltiIdToken);
        populateContentItems();
    }

    public void setupDeepLinkingSettings(OidcIdToken ltiIdToken) {
        this.deepLinkingSettings = new JSONObject(ltiIdToken.getClaim(Claims.DEEP_LINKING_SETTINGS));
    }

    public JsonObject getDeepLinkingResponse() {
        return deepLinkingResponse;
    }

    public JSONObject getDeepLinkingSettings() {
        return deepLinkingSettings;
    }

    private void populateDeepLinkingResponse(OidcIdToken ltiIdToken) {
        this.deepLinkingResponse.addProperty("aud", ltiIdToken.getClaim("iss").toString());
        this.deepLinkingResponse.addProperty("iss", ltiIdToken.getClaim("aud").toString().replace("[", "").replace("]", "")); // "http://localhost:9000/"
        this.deepLinkingResponse.addProperty("exp", ltiIdToken.getClaim("exp").toString()); // "1510185728"
        this.deepLinkingResponse.addProperty("iat", ltiIdToken.getClaim("iat").toString());  // "1510185228"
        this.deepLinkingResponse.addProperty("nonce", ltiIdToken.getClaim("nonce").toString());
        this.deepLinkingResponse.addProperty(Claims.MSG, "Content successfully linked");
        this.deepLinkingResponse.addProperty(Claims.LTI_DEPLOYMENT_ID, ltiIdToken.getClaim(Claims.LTI_DEPLOYMENT_ID).toString());
        this.deepLinkingResponse.addProperty(Claims.MESSAGE_TYPE, "LtiDeepLinkingResponse");
        this.deepLinkingResponse.addProperty(Claims.LTI_VERSION, "1.3.0");
    }

    private void populateContentItems() {
        JsonObject item = createContentItem("ltiResourceLink", "A title", "http://localhost:9000/courses/3/exercises/82");
        JsonObject item2 = createContentItem("ltiResourceLink", "A title2", "http://localhost:9000/courses/3/exercises/81");

        JsonArray contentItems = new JsonArray();
        contentItems.add(item);
        this.deepLinkingResponse.addProperty(Claims.CONTENT_ITEMS, contentItems.toString());
    }

    private JsonObject createContentItem(String type, String title, String url) {
        JsonObject item = new JsonObject();
        item.addProperty("type", type);
        item.addProperty("title", title);
        item.addProperty("url", url);
        return item;
    }
}
