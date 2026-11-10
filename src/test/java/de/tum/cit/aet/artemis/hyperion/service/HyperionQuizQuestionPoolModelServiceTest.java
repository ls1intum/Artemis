package de.tum.cit.aet.artemis.hyperion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.exception.ServiceUnavailableAlertException;

class HyperionQuizQuestionPoolModelServiceTest {

    private static final String LOCAL_ENDPOINT = "https://logos.example.org/v1";

    private static final List<String> ALLOWED = List.of("openai/gpt-oss-120b", "Qwen/Qwen3.8-27B");

    @Test
    void constructor_leavesThePoolOffWithoutAnyConfiguration() {
        HyperionQuizQuestionPoolModelService service = service("", "", "", ALLOWED);

        assertThat(service.isConfigured()).isFalse();
        assertThat(service.generatorModel()).isEmpty();
        assertThatThrownBy(service::chatClient).isInstanceOf(ServiceUnavailableAlertException.class).hasMessageContaining("no local model configured");
    }

    @Test
    void constructor_buildsTheClientOfTheOwnEndpointForAllowedModels() {
        HyperionQuizQuestionPoolModelService service = service(" " + LOCAL_ENDPOINT + " ", " openai/gpt-oss-120b ", "Qwen/Qwen3.8-27B", ALLOWED);

        assertThat(service.isConfigured()).isTrue();
        assertThat(service.chatClient()).isNotNull();
        assertThat(service.generatorModel()).isEqualTo("openai/gpt-oss-120b");
        assertThat(service.judgeModel()).isEqualTo("Qwen/Qwen3.8-27B");
    }

    @Test
    void constructor_rejectsAPartialConfiguration() {
        assertThatThrownBy(() -> service(LOCAL_ENDPOINT, "openai/gpt-oss-120b", "", ALLOWED)).isInstanceOf(IllegalStateException.class).hasMessageContaining("judge-model");
        assertThatThrownBy(() -> service("", "openai/gpt-oss-120b", "openai/gpt-oss-120b", ALLOWED)).isInstanceOf(IllegalStateException.class).hasMessageContaining("base-url");
    }

    @Test
    void constructor_rejectsAModelOutsideTheAllowlist() {
        assertThatThrownBy(() -> service(LOCAL_ENDPOINT, "openai/gpt-oss-120b", "anthropic/claude-opus", ALLOWED)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("anthropic/claude-opus").hasMessageContaining("allowed-models");
        assertThatThrownBy(() -> service(LOCAL_ENDPOINT, "openai/gpt-oss-120b", "openai/gpt-oss-120b", List.of())).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("allowed-models");
    }

    @Test
    void constructor_rejectsCloudProviderEndpoints() {
        for (String endpoint : List.of("https://api.openai.com/v1", "https://my-resource.openai.azure.com", "https://api.anthropic.com/v1",
                "https://generativelanguage.googleapis.com/v1beta/openai", "https://bedrock-runtime.eu-central-1.amazonaws.com")) {
            assertThatThrownBy(() -> service(endpoint, "openai/gpt-oss-120b", "openai/gpt-oss-120b", ALLOWED)).as(endpoint).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("self-hosted");
        }
    }

    @Test
    void constructor_rejectsAnEndpointThatIsNotAnHttpUrl() {
        assertThatThrownBy(() -> service("logos.example.org/v1", "openai/gpt-oss-120b", "openai/gpt-oss-120b", ALLOWED)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("http or https");
        assertThatThrownBy(() -> service("ftp://logos.example.org/v1", "openai/gpt-oss-120b", "openai/gpt-oss-120b", ALLOWED)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("http or https");
    }

    @Test
    void isCloudHost_matchesProviderDomainsButNotLookalikes() {
        assertThat(HyperionQuizQuestionPoolModelService.isCloudHost("api.openai.com")).isTrue();
        assertThat(HyperionQuizQuestionPoolModelService.isCloudHost("openai.com")).isTrue();
        assertThat(HyperionQuizQuestionPoolModelService.isCloudHost("logos.aet.cit.tum.de")).isFalse();
        assertThat(HyperionQuizQuestionPoolModelService.isCloudHost("notopenai.com")).isFalse();
        assertThat(HyperionQuizQuestionPoolModelService.isCloudHost("localhost")).isFalse();
    }

    private static HyperionQuizQuestionPoolModelService service(String baseUrl, String generatorModel, String judgeModel, List<String> allowedModels) {
        return new HyperionQuizQuestionPoolModelService(baseUrl, "", Duration.ofMinutes(5), generatorModel, judgeModel, allowedModels);
    }
}
