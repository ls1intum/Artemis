package de.tum.cit.aet.artemis.communication.service.notifications;

import java.util.Map;
import java.util.Set;

import org.commonmark.node.Image;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.html.HtmlNodeRendererContext;
import org.commonmark.renderer.html.HtmlWriter;

public class MarkdownImageBlockRenderer implements NodeRenderer {

    private final String baseUrl;

    private final HtmlWriter html;

    MarkdownImageBlockRenderer(HtmlNodeRendererContext context, String baseUrl) {
        html = context.getWriter();
        this.baseUrl = baseUrl;
    }

    @Override
    public Set<Class<? extends Node>> getNodeTypes() {
        return Set.of(Image.class);
    }

    @Override
    public void render(Node node) {
        Image image = (Image) node;

        html.tag("a", Map.of("href", baseUrl + image.getDestination()));

        try {
            html.text(((Text) image.getFirstChild()).getLiteral());
        }
        catch (Exception e) {
            html.text(image.getDestination());
        }

        html.tag("/a");
    }
}
