package de.tum.cit.aet.artemis.hyperion.service;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.config.llm.LlmPresetAssignment;
import de.tum.cit.aet.artemis.core.config.llm.LlmPresetAssignments;
import de.tum.cit.aet.artemis.core.exception.ServiceUnavailableAlertException;
import de.tum.cit.aet.artemis.core.service.llm.LlmClientService;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;

/**
 * Provides the language models of the quiz question pool: the presets assigned under {@code artemis.hyperion.quiz-pool.generator-llm-preset} and
 * {@code artemis.hyperion.quiz-pool.judge-llm-preset}. Both are set or neither; without them the pool is not configured and builds are refused.
 * The pool sends lecture material to these models, so the administrator chooses their endpoint explicitly.
 */
@Service
@Lazy(false)
@Conditional(HyperionEnabled.class)
public class HyperionQuizQuestionPoolModelService implements LlmPresetAssignments {

    static final String GENERATOR_KEY = "artemis.hyperion.quiz-pool.generator-llm-preset";

    static final String JUDGE_KEY = "artemis.hyperion.quiz-pool.judge-llm-preset";

    private final LlmClientService llmClientService;

    @Nullable
    private final String generatorPreset;

    @Nullable
    private final String judgePreset;

    public HyperionQuizQuestionPoolModelService(LlmClientService llmClientService, @Value("${" + GENERATOR_KEY + ":}") String generatorPreset,
            @Value("${" + JUDGE_KEY + ":}") String judgePreset) {
        this.llmClientService = llmClientService;
        this.generatorPreset = generatorPreset.isBlank() ? null : generatorPreset.strip();
        this.judgePreset = judgePreset.isBlank() ? null : judgePreset.strip();
    }

    /**
     * The pool is optional: without both keys it is off. Once one key is set, the other is required as well.
     */
    @Override
    public List<LlmPresetAssignment> llmPresetAssignments() {
        if (generatorPreset == null && judgePreset == null) {
            return List.of();
        }
        return List.of(LlmPresetAssignment.ofSingle(GENERATOR_KEY, generatorPreset, LlmPresetAssignment.Usage.CHAT),
                LlmPresetAssignment.ofSingle(JUDGE_KEY, judgePreset, LlmPresetAssignment.Usage.CHAT));
    }

    /**
     * Returns whether both presets of the pool are assigned.
     *
     * @return whether builds can run
     */
    public boolean isConfigured() {
        return generatorPreset != null && judgePreset != null;
    }

    /**
     * Returns the client of the preset that generates the pool's questions.
     *
     * @return the client
     * @throws ServiceUnavailableAlertException if the pool is not configured
     */
    public ChatClient generatorChatClient() {
        return llmClientService.chatClient(requireConfigured(generatorPreset));
    }

    /**
     * Returns the client of the preset that judges the pool's questions.
     *
     * @return the client
     * @throws ServiceUnavailableAlertException if the pool is not configured
     */
    public ChatClient judgeChatClient() {
        return llmClientService.chatClient(requireConfigured(judgePreset));
    }

    /**
     * Returns the model that generates the pool's questions.
     *
     * @return the model name, empty when the pool is not configured
     */
    public String generatorModel() {
        return isConfigured() ? llmClientService.preset(generatorPreset).model() : "";
    }

    /**
     * Returns the model that judges the pool's questions.
     *
     * @return the model name, empty when the pool is not configured
     */
    public String judgeModel() {
        return isConfigured() ? llmClientService.preset(judgePreset).model() : "";
    }

    private String requireConfigured(@Nullable String preset) {
        if (!isConfigured() || preset == null) {
            throw notConfigured();
        }
        return preset;
    }

    /**
     * Creates the exception reported when a build is requested while the pool has no presets assigned.
     *
     * @return the exception
     */
    public static ServiceUnavailableAlertException notConfigured() {
        return new ServiceUnavailableAlertException("The quiz question pool has no LLM presets assigned", "QuizQuestionPool", "quizQuestionPoolModelNotConfigured");
    }
}
