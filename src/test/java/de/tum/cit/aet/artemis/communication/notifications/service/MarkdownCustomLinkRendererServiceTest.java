package de.tum.cit.aet.artemis.communication.notifications.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.communication.service.notifications.MarkdownCustomLinkRendererService;

class MarkdownCustomLinkRendererServiceTest {

    private MarkdownCustomLinkRendererService markdownCustomLinkRendererService;

    @BeforeEach
    void setUp() throws MalformedURLException, URISyntaxException {
        markdownCustomLinkRendererService = new MarkdownCustomLinkRendererService();

        ReflectionTestUtils.setField(markdownCustomLinkRendererService, "artemisServerUrl", new URI("http://localhost:8080").toURL());
    }

    @Test
    void shouldRenderProperlyWhenProgrammingTagIsSupplied() {
        String input = "[programming]Example (/example)[/programming]";
        String expected = "<a href=\"http://localhost:8080/example\">Example</a>";

        String result = markdownCustomLinkRendererService.render(input);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void shouldRenderMultipleTagsWhenMultipleTagsAreSupplied() {
        String input = "[programming]Code (/code)[/programming] and [quiz]Quiz (/quiz)[/quiz]";
        String expected = "<a href=\"http://localhost:8080/code\">Code</a> and <a href=\"http://localhost:8080/quiz\">Quiz</a>";

        String result = markdownCustomLinkRendererService.render(input);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void shouldNotRenderWhenUnsupportedTagsAreSupplied() {
        String input = "[unsupported]Text (/unsupported)[/unsupported]";
        String result = markdownCustomLinkRendererService.render(input);

        // Unsupported tags should not be rendered, so the input remains unchanged.
        assertThat(result).isEqualTo(input);
    }

    @Test
    void shouldNotRenderWhenLinkIsOmitted() {
        String input = "[programming]Example[/programming]";
        String result = markdownCustomLinkRendererService.render(input);

        // Invalid format should not be rendered, so the input remains unchanged.
        assertThat(result).isEqualTo(input);
    }

    @Test
    void shouldReturnEmptyOutputWhenSuppliedWithEmptyInput() {
        String input = "";
        String result = markdownCustomLinkRendererService.render(input);

        assertThat(result).isEmpty();
    }
}
