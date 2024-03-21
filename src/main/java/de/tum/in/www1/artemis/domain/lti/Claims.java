package de.tum.in.www1.artemis.domain.lti;

public class Claims extends uk.ac.ox.ctl.lti13.lti.Claims {

    /**
     * Constant for LTI Assignment and Grade Services (AGS) claim endpoint.
     * Used to identify the AGS service endpoint in LTI messages.
     */
    public static final String AGS_CLAIM = "https://purl.imsglobal.org/spec/lti-ags/claim/endpoint";

    /**
     * Constant for LTI Deep Linking message claim.
     * Used to carry messages specific to LTI Deep Linking requests and responses.
     */
    public static final String MSG = "https://purl.imsglobal.org/spec/lti-dl/claim/msg";
}
