package de.tum.cit.aet.artemis.lti.service;

import java.util.HashMap;
import java.util.Map;

public record LtiContentItem(String type, String title, String url, LtiDeepLinkingService.LineItem lineItem) {

    /**
     * Converts this ContentItem into a Map<String, Object> suitable for LTI 1.3 Deep Linking Response.
     *
     * @return A map representing the content item.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("type", type);
        map.put("title", title);
        map.put("url", url);
        if (lineItem != null) {
            Map<String, Object> lineItemMap = new HashMap<>();
            lineItemMap.put("scoreMaximum", lineItem.scoreMaximum());
            map.put("lineItem", lineItemMap);
        }
        return map;
    }
}
