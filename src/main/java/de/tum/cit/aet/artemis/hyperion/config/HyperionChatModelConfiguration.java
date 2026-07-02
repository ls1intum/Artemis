package de.tum.cit.aet.artemis.hyperion.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.hyperion.exercisegeneration.agent.HarmonyScrubbingChatModel;

/**
 * Wraps the Spring AI OpenAI-starter {@link OpenAiChatModel} in the {@link HarmonyScrubbingChatModel} decorator so the Hyperion agent loop and every other Spring AI consumer see
 * assistant content with gpt-oss "harmony" control tokens stripped.
 * <p>
 * This replaces the prototype's bespoke {@code GpuEndpointChatModel} + {@code HyperionGpuChatModelConfiguration}: the transport is now the stock OpenAI starter (auto-configured
 * from {@code spring.ai.openai.*} — point {@code spring.ai.openai.base-url} at {@code https://staging.hephaestus.aet.cit.tum.de/logos/v1} and
 * {@code spring.ai.openai.chat.options.model} at {@code openai/gpt-oss-120b}, and set {@code spring.ai.model.chat=openai}). Spring AI 2.0 never auto-executes tools, so the loop
 * still drives them; this decorator only cleans leaked harmony tokens (see {@link HarmonyScrubbingChatModel}). A {@link BeanPostProcessor} is used (rather than a second
 * {@code @Primary} bean) so the decorated model REPLACES the raw one in the context, leaving exactly one {@code ChatModel} for {@code Collection<ChatModel>} injection points.
 */
@Component
@Conditional(HyperionEnabled.class)
public class HyperionChatModelConfiguration implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(HyperionChatModelConfiguration.class);

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof OpenAiChatModel chatModel) {
            log.info("Wrapping OpenAI chat model bean '{}' in the Hyperion harmony-token scrubber", beanName);
            // Concrete-type coupling caveat: the returned decorator is only a ChatModel, not an OpenAiChatModel. The stock Spring AI ChatClient auto-configuration injects the
            // ChatModel interface, so this is safe today and also applies the (harmless) scrubbing to every Spring AI consumer. A future consumer that injects the concrete
            // OpenAiChatModel type would fail to resolve — such a consumer should either accept the ChatModel interface or the scrubbing should be scoped inside AgentLoopRunner.
            return new HarmonyScrubbingChatModel(chatModel);
        }
        return bean;
    }
}
