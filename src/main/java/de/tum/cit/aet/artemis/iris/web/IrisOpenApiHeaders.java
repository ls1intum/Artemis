package de.tum.cit.aet.artemis.iris.web;

import org.springframework.http.HttpHeaders;

/** Headers documented on Iris REST responses. */
final class IrisOpenApiHeaders {

    static final String LOCATION = HttpHeaders.LOCATION;

    static final String SESSION_LOCATION_DESCRIPTION = "URI identifying the Iris session";

    static final String FRAME_OPTIONS = "X-Frame-Options";

    static final String FRAME_OPTIONS_DESCRIPTION = "DENY, set by Artemis security to prevent framing";

    private IrisOpenApiHeaders() {
    }
}
