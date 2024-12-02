package de.tum.cit.aet.artemis.communication.service.notifications;

import java.util.Map;

import org.commonmark.node.Node;
import org.commonmark.renderer.html.AttributeProvider;

public class MarkdownRelativeToAbsolutePathAttributeProvider implements AttributeProvider {

    private final String baseUrl;

    public MarkdownRelativeToAbsolutePathAttributeProvider(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /**
     * We store images and attachments with relative urls, so when rendering we need to replace them with absolute ones
     *
     * @param node       rendered Node, if Image or Link we try to replace the source
     * @param attributes of the Node
     * @param tagName    of the html element
     */
    @Override
    public void setAttributes(Node node, String tagName, Map<String, String> attributes) {
        if ("a".equals(tagName)) {
            String href = attributes.get("href");
            if (href != null && href.startsWith("/")) {
                attributes.put("href", baseUrl + href);
            }
        }
    }
}
