package de.tum.cit.aet.artemis.hyperion.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.HarmonyScrubbingChatModel;

/**
 * Wraps the Spring AI OpenAI-starter {@link OpenAiChatModel} in the {@link HarmonyScrubbingChatModel} decorator so the Hyperion agent loop and every other Spring AI consumer see
 * assistant content with gpt-oss "harmony" control tokens stripped. The transport is the stock OpenAI starter, auto-configured from {@code spring.ai.openai.*}. A
 * {@link BeanPostProcessor} (rather than a second {@code @Primary} bean) replaces the raw model in the context, leaving exactly one {@code ChatModel} for
 * {@code Collection<ChatModel>} injection points.
 */
@Lazy
@Component
@Conditional(HyperionEnabled.class)
public class HyperionChatModelConfiguration implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(HyperionChatModelConfiguration.class);

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof OpenAiChatModel chatModel) {
            log.info("Wrapping OpenAI chat model bean '{}' in the Hyperion harmony-token scrubber", beanName);
            // The returned decorator is a ChatModel, not an OpenAiChatModel. Spring AI's ChatClient auto-configuration injects the ChatModel interface; a consumer that injects the
            // concrete OpenAiChatModel type would fail to resolve.
            return new HarmonyScrubbingChatModel(chatModel);
        }
        return bean;
    }
}
