package de.tum.cit.aet.artemis.hyperion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.exception.ServiceUnavailableAlertException;

class HyperionQuizQuestionPoolModelServiceTest {

    private static final String LOCAL_ENDPOINT = "https://logos.example.org/v1";

    private static final String MODEL = "openai/gpt-oss-120b";

    private static final List<String> ALLOWED = List.of(MODEL, "Qwen/Qwen3.8-27B");

    @Test
    void constructor_leavesThePoolOffWithoutAnyConfiguration() {
        HyperionQuizQuestionPoolModelService service = service("", "", "", ALLOWED);

        assertThat(service.isConfigured()).isFalse();
        assertThat(service.generatorModel()).isEmpty();
        assertThatThrownBy(service::chatClient).isInstanceOf(ServiceUnavailableAlertException.class).hasMessageContaining("no local model configured");
    }

    @Test
    void constructor_buildsTheClientOfTheOwnEndpointForAllowedModels() {
        HyperionQuizQuestionPoolModelService service = service(" " + LOCAL_ENDPOINT + " ", " " + MODEL + " ", "Qwen/Qwen3.8-27B", ALLOWED);

        assertThat(service.isConfigured()).isTrue();
        assertThat(service.chatClient()).isNotNull();
        assertThat(service.generatorModel()).isEqualTo(MODEL);
        assertThat(service.judgeModel()).isEqualTo("Qwen/Qwen3.8-27B");
    }

    @Test
    void constructor_leavesThePoolOffWhenTheConfigurationIsInvalid() {
        for (HyperionQuizQuestionPoolModelService service : List.of(service(LOCAL_ENDPOINT, MODEL, "", ALLOWED), service(LOCAL_ENDPOINT, MODEL, "anthropic/claude-opus", ALLOWED),
                service("https://api.openai.com/v1", MODEL, MODEL, ALLOWED), service("logos.example.org/v1", MODEL, MODEL, ALLOWED))) {
            assertThat(service.isConfigured()).isFalse();
            assertThatThrownBy(service::chatClient).isInstanceOf(ServiceUnavailableAlertException.class);
        }
    }

    @Test
    void validate_rejectsAPartialConfiguration() {
        assertThatThrownBy(() -> validate(LOCAL_ENDPOINT, MODEL, "", ALLOWED)).isInstanceOf(IllegalStateException.class).hasMessageContaining("judge-model");
        assertThatThrownBy(() -> validate("", MODEL, MODEL, ALLOWED)).isInstanceOf(IllegalStateException.class).hasMessageContaining("base-url");
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
    void validate_acceptsALocalEndpointWithAllowedModelsAndReportsAnEmptyConfiguration() {
        assertThat(validate(LOCAL_ENDPOINT, MODEL, MODEL, ALLOWED)).isTrue();
        assertThat(validate("http://localhost:11434/v1", MODEL, MODEL, ALLOWED)).isTrue();
        assertThat(validate("", "", "", ALLOWED)).isFalse();
    }

    @Test
    void isCloudHost_matchesProviderDomainsButNotLookalikes() {
        assertThat(HyperionQuizQuestionPoolModelService.isCloudHost("api.openai.com")).isTrue();
        assertThat(HyperionQuizQuestionPoolModelService.isCloudHost("openai.com")).isTrue();
        assertThat(HyperionQuizQuestionPoolModelService.isCloudHost("logos.aet.cit.tum.de")).isFalse();
        assertThat(HyperionQuizQuestionPoolModelService.isCloudHost("notopenai.com")).isFalse();
        assertThat(HyperionQuizQuestionPoolModelService.isCloudHost("localhost")).isFalse();
    }

    private static boolean validate(String baseUrl, String generatorModel, String judgeModel, List<String> allowedModels) {
        return HyperionQuizQuestionPoolModelService.validate(baseUrl, generatorModel, judgeModel, allowedModels);
    }

    private static HyperionQuizQuestionPoolModelService service(String baseUrl, String generatorModel, String judgeModel, List<String> allowedModels) {
        return new HyperionQuizQuestionPoolModelService(baseUrl, "", Duration.ofMinutes(5), generatorModel, judgeModel, allowedModels);
    }
}
