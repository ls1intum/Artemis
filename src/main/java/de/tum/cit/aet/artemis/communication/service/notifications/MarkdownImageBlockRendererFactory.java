package de.tum.cit.aet.artemis.communication.service.notifications;

import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.html.HtmlNodeRendererContext;
import org.commonmark.renderer.html.HtmlNodeRendererFactory;

public class MarkdownImageBlockRendererFactory implements HtmlNodeRendererFactory {

    private final String baseUrl;

    public MarkdownImageBlockRendererFactory(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Override
    public NodeRenderer create(HtmlNodeRendererContext context) {
        return new MarkdownImageBlockRenderer(context, baseUrl);
    }
}
