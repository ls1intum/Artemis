package de.tum.cit.aet.artemis.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.config.LLMModelCostConfiguration;
import de.tum.cit.aet.artemis.core.domain.LLMRequest;
import de.tum.cit.aet.artemis.core.repository.LLMTokenUsageRequestRepository;
import de.tum.cit.aet.artemis.core.repository.LLMTokenUsageTraceRepository;

class LLMTokenUsageServiceTest {

    private final LLMTokenUsageTraceRepository llmTokenUsageTraceRepository = mock(LLMTokenUsageTraceRepository.class);

    private final LLMTokenUsageRequestRepository llmTokenUsageRequestRepository = mock(LLMTokenUsageRequestRepository.class);

    @Test
    void buildLLMRequest_withDashlessDateSuffixedModel_resolvesConfiguredCost() {
        LLMTokenUsageService service = new LLMTokenUsageService(llmTokenUsageTraceRepository, llmTokenUsageRequestRepository,
                createCostConfiguration(Map.of("gpt-5-mini", createModelCostProperties(0.23f, 1.84f))));

        LLMRequest request = service.buildLLMRequest("gpt5mini2025-08-07", 100, 40, "HYPERION_TEST_PIPELINE");

        assertThat(request.costPerMillionInputToken()).isEqualTo(0.23f);
        assertThat(request.costPerMillionOutputToken()).isEqualTo(1.84f);
    }

    @Test
    void constructor_withDashlessModelKeyCollision_throwsIllegalStateException() {
        Map<String, LLMModelCostConfiguration.ModelCostProperties> modelCosts = Map.of("gpt-5-mini", createModelCostProperties(0.23f, 1.84f), "gpt5-mini",
                createModelCostProperties(0.30f, 2.00f));
        LLMModelCostConfiguration configuration = createCostConfiguration(modelCosts);

        assertThatThrownBy(() -> new LLMTokenUsageService(llmTokenUsageTraceRepository, llmTokenUsageRequestRepository, configuration)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Conflicting model-cost keys after dashless normalization").hasMessageContaining("gpt-5-mini").hasMessageContaining("gpt5-mini");
    }

    private static LLMModelCostConfiguration createCostConfiguration(Map<String, LLMModelCostConfiguration.ModelCostProperties> modelCosts) {
        LLMModelCostConfiguration configuration = new LLMModelCostConfiguration();
        configuration.setModelCosts(new HashMap<>(modelCosts));
        return configuration;
    }

    private static LLMModelCostConfiguration.ModelCostProperties createModelCostProperties(float inputCostPerMillionEur, float outputCostPerMillionEur) {
        LLMModelCostConfiguration.ModelCostProperties modelCostProperties = new LLMModelCostConfiguration.ModelCostProperties();
        modelCostProperties.setInputCostPerMillionEur(inputCostPerMillionEur);
        modelCostProperties.setOutputCostPerMillionEur(outputCostPerMillionEur);
        return modelCostProperties;
    }
}
