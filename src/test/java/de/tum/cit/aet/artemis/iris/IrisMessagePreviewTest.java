package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import de.tum.cit.aet.artemis.iris.domain.message.IrisJsonMessageContent;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisTextMessageContent;
import de.tum.cit.aet.artemis.iris.service.session.IrisChatSessionService;

/**
 * Unit tests for the single-line plain-text preview that {@code IrisChatSessionService} builds from an assistant
 * (Markdown) message for use in notifications. The method is private and static, so it is exercised via reflection
 * to keep the assertions focused on the formatting logic (Markdown stripping, content joining, truncation, fallback)
 * without spinning up the full chat flow.
 */
class IrisMessagePreviewTest {

    private static final String FALLBACK = "Iris has answered your message";

    private static final int MAX_LENGTH = 200;

    private static Method buildPreview;

    @BeforeAll
    static void resolveMethod() throws NoSuchMethodException {
        buildPreview = IrisChatSessionService.class.getDeclaredMethod("buildPreview", IrisMessage.class, String.class);
        buildPreview.setAccessible(true);
    }

    private static String buildPreview(IrisMessage message) {
        try {
            return (String) buildPreview.invoke(null, message, FALLBACK);
        }
        catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static IrisMessage textMessage(String... texts) {
        IrisMessage message = new IrisMessage();
        for (String text : texts) {
            message.addContent(new IrisTextMessageContent(text));
        }
        return message;
    }

    @Test
    void stripsMarkdownFormatting() {
        assertThat(buildPreview(textMessage("**Hello** _World_"))).isEqualTo("Hello World");
    }

    @Test
    void collapsesWhitespaceAndNewlinesToSingleLine() {
        assertThat(buildPreview(textMessage("First line\nSecond   line"))).isEqualTo("First line Second line");
    }

    @Test
    void joinsMultipleTextContents() {
        assertThat(buildPreview(textMessage("First part.", "Second part."))).isEqualTo("First part. Second part.");
    }

    @Test
    void truncatesLongTextToMaxLengthWithEllipsis() {
        String preview = buildPreview(textMessage("a".repeat(MAX_LENGTH + 50)));

        assertThat(preview).hasSize(MAX_LENGTH).endsWith("…");
    }

    @Test
    void returnsFallbackWhenNoTextContent() {
        IrisMessage message = new IrisMessage();
        message.addContent(new IrisJsonMessageContent(JsonNodeFactory.instance.objectNode().put("type", "mcq")));

        assertThat(buildPreview(message)).isEqualTo(FALLBACK);
    }

    @Test
    void returnsFallbackWhenTextIsBlank() {
        assertThat(buildPreview(textMessage("   "))).isEqualTo(FALLBACK);
    }

    @Test
    void skipsJsonContentButKeepsText() {
        IrisMessage message = new IrisMessage();
        message.addContent(new IrisJsonMessageContent(JsonNodeFactory.instance.objectNode().put("type", "mcq")));
        message.addContent(new IrisTextMessageContent("Here is your answer"));

        assertThat(buildPreview(message)).isEqualTo("Here is your answer");
    }
}
