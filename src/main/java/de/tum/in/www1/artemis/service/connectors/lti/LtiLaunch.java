package de.tum.in.www1.artemis.service.connectors.lti;

import jakarta.servlet.http.HttpServletRequest;

public record LtiLaunch(LtiUser user, String version, String messageType, String resourceLinkId, String contextId, String launchPresentationReturnUrl,
        String toolConsumerInstanceGuid) {

    public LtiLaunch(HttpServletRequest request) {
        this(new LtiUser(request), request.getParameter("lti_version"), request.getParameter("lti_message_type"), request.getParameter("resource_link_id"),
                request.getParameter("context_id"), request.getParameter("launch_presentation_return_url"), request.getParameter("tool_consumer_instance_guid"));
    }
}
