package de.tum.cit.aet.artemis.exercise.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;

import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.programming.service.PlantUmlService;

class ProblemStatementRenderingImageHostTest {

    private static final String MARKDOWN = "![a](https://images.example.net/a.png)\n\n![b](https://other.example.org/b.png)\n\n![c](/api/core/files/markdown/c.png)";

    private static String render(String allowedImageHosts) {
        var service = new ProblemStatementRenderingService(mock(PlantUmlService.class), JsonObjectMapper.get(), mock(MessageSource.class), mock(FileService.class),
                "https://artemis.example.org", allowedImageHosts);
        return service.render(MARKDOWN, null, null, Locale.ENGLISH, false, false, false, false).html();
    }

    @Test
    void keepsImagesOfEveryHostByDefault() {
        assertThat(render("")).contains("https://images.example.net/a.png", "https://other.example.org/b.png");
    }

    @Test
    void keepsOnlyImagesOfAllowedHostsAndOfTheServer() {
        assertThat(render("images.example.net")).contains("https://images.example.net/a.png", "https://artemis.example.org/api/core/files/markdown/c.png")
                .doesNotContain("other.example.org");
    }
}
