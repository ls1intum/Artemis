package de.tum.cit.aet.artemis.communication.notifications.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.communication.service.notifications.MarkdownCustomReferenceRendererService;

class MarkdownCustomReferenceRendererServiceTest {

    private MarkdownCustomReferenceRendererService service;

    @BeforeEach
    void setUp() {
        service = new MarkdownCustomReferenceRendererService();
    }

    @Test
    void shouldRenderUserReferenceWhenValidUserTagProvided() {
        String input = "[user]Max Muster(ga1234as)[/user]";
        assertThat(service.render(input)).isEqualTo("@Max Muster");
    }

    @Test
    void shouldRenderChannelReferenceWhenValidChannelTagProvided() {
        String input = "[channel]General Channel(general)[/channel]";
        assertThat(service.render(input)).isEqualTo("#General Channel");
    }

    @Test
    void shouldRenderMultipleReferencesInSingleContent() {
        String input = "[user]Max Muster(ga1234as)[/user] and [channel]General Channel(general)[/channel]";
        assertThat(service.render(input)).isEqualTo("@Max Muster and #General Channel");
    }

    @Test
    void shouldHandleEmptyContentWithoutError() {
        String input = "";
        assertThat(service.render(input)).isEmpty();
    }

    @Test
    void shouldReturnOriginalContentWhenNoValidTagsFound() {
        String input = "Normal text without tags";
        assertThat(service.render(input)).isEqualTo(input);
    }

    @Test
    void shouldRenderReferenceWithAdditionalTextBeforeAndAfter() {
        String input = "Before [user]Max Muster(ga1234as)[/user] After";
        assertThat(service.render(input)).isEqualTo("Before @Max Muster After");
    }

    @Test
    void shouldHandleInvalidTagsGracefully() {
        String input = "[invalid]Some Text(reference)[/invalid]";
        assertThat(service.render(input)).isEqualTo(input);
    }
}
