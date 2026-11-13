package de.tum.cit.aet.artemis.hyperion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.exception.ServiceUnavailableAlertException;

class HyperionQuizQuestionPoolModelServiceTest {

    private static final String LOCAL_ENDPOINT = "https://logos.example.org/v1";

    private static final String MODEL = "Qwen/Qwen3.8-27B";

    private static final String OTHER_MODEL = "openai/gpt-oss-120b";

    private static final List<String> ALLOWED = List.of(MODEL, OTHER_MODEL);

    @Test
    void constructor_leavesThePoolOffWithoutModels() {
        HyperionQuizQuestionPoolModelService service = service(LOCAL_ENDPOINT, "", "", LOCAL_ENDPOINT, false);

        assertThat(service.isConfigured()).isFalse();
        assertThat(service.generatorModel()).isEmpty();
        assertThatThrownBy(service::chatClient).isInstanceOf(ServiceUnavailableAlertException.class).hasMessageContaining("no local model configured");
    }

    @Test
    void constructor_buildsTheClientOfTheOwnEndpointForAllowedModels() {
        HyperionQuizQuestionPoolModelService service = service(" " + LOCAL_ENDPOINT + " ", " " + MODEL + " ", OTHER_MODEL, "", false);

        assertThat(service.isConfigured()).isTrue();
        assertThat(service.chatClient()).isNotNull();
        assertThat(service.generatorModel()).isEqualTo(MODEL);
        assertThat(service.judgeModel()).isEqualTo(OTHER_MODEL);
    }

    @Test
    void constructor_usesTheChatEndpointWithoutAnOwnEndpoint() {
        HyperionQuizQuestionPoolModelService service = service("", MODEL, MODEL, LOCAL_ENDPOINT, false);

        assertThat(service.isConfigured()).isTrue();
        assertThat(service.chatClient()).isNotNull();
    }

    @Test
    void constructor_leavesThePoolOffWhenTheChatEndpointIsACloudProvider() {
        for (HyperionQuizQuestionPoolModelService service : List.of(service("", MODEL, MODEL, "https://my-resource.openai.azure.com", false),
                service("", MODEL, MODEL, "https://ai.example.org/v1", true), service("", MODEL, MODEL, "", false))) {
            assertThat(service.isConfigured()).isFalse();
        }
    }

    @Test
    void constructor_prefersTheOwnEndpointOverACloudChatEndpoint() {
        HyperionQuizQuestionPoolModelService service = service(LOCAL_ENDPOINT, MODEL, MODEL, "https://api.openai.com/v1", true);

        assertThat(service.isConfigured()).isTrue();
    }

    @Test
    void constructor_leavesThePoolOffWhenTheConfigurationIsInvalid() {
        for (HyperionQuizQuestionPoolModelService service : List.of(service(LOCAL_ENDPOINT, MODEL, "", "", false),
                service(LOCAL_ENDPOINT, MODEL, "anthropic/claude-opus", "", false), service("https://api.openai.com/v1", MODEL, MODEL, LOCAL_ENDPOINT, false),
                service("logos.example.org/v1", MODEL, MODEL, "", false))) {
            assertThat(service.isConfigured()).isFalse();
            assertThatThrownBy(service::chatClient).isInstanceOf(ServiceUnavailableAlertException.class);
        }
    }

    @Test
    void validate_rejectsAPartialConfiguration() {
        assertThatThrownBy(() -> validate(LOCAL_ENDPOINT, MODEL, "", ALLOWED)).isInstanceOf(IllegalStateException.class).hasMessageContaining("judge-model");
        assertThatThrownBy(() -> validate(LOCAL_ENDPOINT, "", MODEL, ALLOWED)).isInstanceOf(IllegalStateException.class).hasMessageContaining("generator-model");
    }

    @Test
    void validate_rejectsAModelOutsideTheAllowlist() {
        assertThatThrownBy(() -> validate(LOCAL_ENDPOINT, MODEL, "anthropic/claude-opus", ALLOWED)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("anthropic/claude-opus").hasMessageContaining("allowed-models");
        assertThatThrownBy(() -> validate(LOCAL_ENDPOINT, MODEL, MODEL, List.of())).isInstanceOf(IllegalStateException.class).hasMessageContaining("allowed-models");
    }

    @Test
    void validate_rejectsCloudProviderEndpoints() {
        for (String endpoint : List.of("https://api.openai.com/v1", "https://my-resource.openai.azure.com", "https://api.anthropic.com/v1",
                "https://generativelanguage.googleapis.com/v1beta/openai", "https://bedrock-runtime.eu-central-1.amazonaws.com")) {
            assertThatThrownBy(() -> validate(endpoint, MODEL, MODEL, ALLOWED)).as(endpoint).isInstanceOf(IllegalStateException.class).hasMessageContaining("self-hosted");
        }
    }

    @Test
    void validate_rejectsAnEndpointThatIsNotAnHttpUrl() {
        assertThatThrownBy(() -> validate("logos.example.org/v1", MODEL, MODEL, ALLOWED)).isInstanceOf(IllegalStateException.class).hasMessageContaining("http or https");
        assertThatThrownBy(() -> validate("ftp://logos.example.org/v1", MODEL, MODEL, ALLOWED)).isInstanceOf(IllegalStateException.class).hasMessageContaining("http or https");
    }

    @Test
    void validate_acceptsALocalEndpointWithAllowedModels() {
        validate(LOCAL_ENDPOINT, MODEL, OTHER_MODEL, ALLOWED);
        validate("http://localhost:11434/v1", MODEL, MODEL, ALLOWED);
    }

    @Test
    void isCloudHost_matchesProviderDomainsButNotLookalikes() {
        assertThat(HyperionQuizQuestionPoolModelService.isCloudHost("api.openai.com")).isTrue();
        assertThat(HyperionQuizQuestionPoolModelService.isCloudHost("openai.com")).isTrue();
        assertThat(HyperionQuizQuestionPoolModelService.isCloudHost("logos.aet.cit.tum.de")).isFalse();
        assertThat(HyperionQuizQuestionPoolModelService.isCloudHost("notopenai.com")).isFalse();
        assertThat(HyperionQuizQuestionPoolModelService.isCloudHost("localhost")).isFalse();
    }

    private static void validate(String baseUrl, String generatorModel, String judgeModel, List<String> allowedModels) {
        HyperionQuizQuestionPoolModelService.validate(baseUrl, "artemis.hyperion.quiz-pool.base-url", generatorModel, judgeModel, allowedModels);
    }

    private static HyperionQuizQuestionPoolModelService service(String baseUrl, String generatorModel, String judgeModel, String chatBaseUrl, boolean chatMicrosoftFoundry) {
        return new HyperionQuizQuestionPoolModelService(baseUrl, "", Duration.ofMinutes(5), generatorModel, judgeModel, ALLOWED, "openai", chatBaseUrl, "", chatMicrosoftFoundry);
    }
}
