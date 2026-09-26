package de.tum.cit.aet.artemis.deimos.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.deimos.exception.DeimosConfigurationException;

class DeimosLlmConfigurationTest {

    @Test
    void mapsBaseUrlAndAbsoluteCompletionsPathToThePrefix() {
        assertThat(DeimosLlmConfiguration.toOpenAiCompatibleBaseUrl("https://llm.example.com", "/api/chat/completions")).isEqualTo("https://llm.example.com/api");
    }

    @Test
    void preservesAnExistingBasePathWhenMappingTheCompletionsPath() {
        // Plain concatenation rather than URI.resolve, so a base URL that already carries a path keeps it.
        assertThat(DeimosLlmConfiguration.toOpenAiCompatibleBaseUrl("https://llm.example.com/v1", "/api/chat/completions")).isEqualTo("https://llm.example.com/v1/api");
    }

    @Test
    void stripsATrailingSlashFromTheBaseUrlBeforeConcatenating() {
        assertThat(DeimosLlmConfiguration.toOpenAiCompatibleBaseUrl("https://llm.example.com/", "/chat/completions")).isEqualTo("https://llm.example.com");
    }

    @Test
    void rejectsARelativeCompletionsPathThatWouldCorruptTheHost() {
        // Without the absolute-path check this produced "https://llm.example.comapi", silently targeting a wrong host.
        assertThatExceptionOfType(DeimosConfigurationException.class)
                .isThrownBy(() -> DeimosLlmConfiguration.toOpenAiCompatibleBaseUrl("https://llm.example.com", "api/chat/completions"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "/api/chat/completions", "/chat/completions", "/deep/nested/api/chat/completions" })
    void acceptsAbsolutePathsEndingWithTheChatCompletionsSuffix(String completionsPath) {
        assertThat(DeimosLlmConfiguration.isValidCompletionsPath(completionsPath)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "api/chat/completions", "/api/completions", "/api/chat/completions/", "http://x/api/chat/completions", "//host/api/chat/completions",
            "/api/chat/completions?x=1", "/api/chat/completions#frag", "" })
    void rejectsRelativeSchemedOrOtherwiseUnmappablePaths(String completionsPath) {
        assertThat(DeimosLlmConfiguration.isValidCompletionsPath(completionsPath)).isFalse();
    }

    @Test
    void rejectsANullCompletionsPath() {
        assertThat(DeimosLlmConfiguration.isValidCompletionsPath(null)).isFalse();
    }
}
