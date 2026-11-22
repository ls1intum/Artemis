package de.tum.cit.aet.artemis.atlas.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.Conditional;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.domain.JavaParameter;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.config.AtlasLLMEnabled;
import de.tum.cit.aet.artemis.shared.architecture.AbstractArchitectureTest;

/**
 * Keeps the two halves of the Atlas module separable.
 * <p>
 * {@code artemis.atlas.enabled} carries competencies, learning paths and learner profiles and is on by default;
 * {@code artemis.atlas.atlasllm.enabled} carries the agent and the orchestrator and is off by default. A bean on the
 * first half that requires a bean from the second is invisible in review and in every test, because every test context
 * enables both. It only shows up in production, where the missing bean definition cannot be created and
 * {@code DeferredEagerBeanInitializer} shuts the application down. That is how the crash this split fixed reached
 * develop, and it happened a second time within the same change, to {@code AtlasMLShortlistService}.
 */
class AtlasFeatureGatingArchitectureTest extends AbstractArchitectureTest {

    @Test
    void competencyBeansMustNotRequireAnLLMBean() {
        Set<String> llmGated = llmGatedBeanTypes();
        assertThat(llmGated).as("the LLM half must not be empty, otherwise this test proves nothing").isNotEmpty();

        List<String> violations = productionClasses.stream().filter(javaClass -> isConditionalOn(javaClass, AtlasEnabled.class))
                .flatMap(javaClass -> javaClass.getConstructors().stream()).flatMap(constructor -> requiredParametersOf(constructor, llmGated)).toList();

        assertThat(violations).as("""
                A bean behind artemis.atlas.enabled requires a bean behind artemis.atlas.atlasllm.enabled, which is off by default. \
                The application will not start without a configured chat model. Move the class to @Conditional(AtlasLLMEnabled.class) \
                if it belongs to the LLM half, or take the dependency as Optional<...> or @Nullable if it is genuinely optional.""").isEmpty();
    }

    /**
     * Every bean type that disappears when the LLM flag is off. A class carrying the condition is only the obvious
     * case: a gated {@code @Configuration} also takes its {@code @EnableConfigurationProperties} types and the return
     * types of its {@code @Bean} methods with it, and that indirect case is the one that got missed the first time.
     * Only Artemis types count, so a {@code @Bean} of a JDK type that something else also supplies is not mistaken for
     * an LLM dependency.
     *
     * @return the fully qualified names of the types that exist only behind the LLM flag
     */
    private static Set<String> llmGatedBeanTypes() {
        List<JavaClass> gatedClasses = productionClasses.stream().filter(javaClass -> isConditionalOn(javaClass, AtlasLLMEnabled.class)).toList();

        java.util.stream.Stream<String> ownTypes = gatedClasses.stream().map(JavaClass::getFullName);
        java.util.stream.Stream<String> propertyTypes = gatedClasses.stream().map(javaClass -> javaClass.tryGetAnnotationOfType(EnableConfigurationProperties.class))
                .filter(Optional::isPresent).flatMap(annotation -> List.of(annotation.get().value()).stream()).map(Class::getName);
        java.util.stream.Stream<String> beanTypes = gatedClasses.stream().flatMap(javaClass -> javaClass.getMethods().stream())
                .filter(method -> method.isAnnotatedWith("org.springframework.context.annotation.Bean")).map(method -> method.getRawReturnType().getFullName());

        return java.util.stream.Stream.of(ownTypes, propertyTypes, beanTypes).flatMap(stream -> stream).filter(name -> name.startsWith(ARTEMIS_PACKAGE))
                .collect(Collectors.toSet());
    }

    /**
     * The constructor parameters that would fail the context: the type is gated behind the LLM flag, and the parameter
     * demands it outright. {@code Optional<...>} and {@code @Nullable} both survive a missing bean definition, so
     * neither is a violation.
     *
     * @param constructor the constructor to inspect
     * @param llmGated    the fully qualified names of the classes behind the LLM flag
     * @return a description of each offending parameter, for the assertion message
     */
    private static java.util.stream.Stream<String> requiredParametersOf(JavaConstructor constructor, Set<String> llmGated) {
        return constructor.getParameters().stream().filter(parameter -> llmGated.contains(parameter.getRawType().getName())).filter(parameter -> !isNullable(parameter))
                .map(parameter -> constructor.getOwner().getSimpleName() + " requires " + parameter.getRawType().toErasure().getSimpleName());
    }

    private static boolean isNullable(JavaParameter parameter) {
        return parameter.getAnnotations().stream().anyMatch(annotation -> annotation.getRawType().getSimpleName().equals("Nullable"));
    }

    private static boolean isConditionalOn(JavaClass javaClass, Class<? extends Condition> condition) {
        Optional<Conditional> conditional = javaClass.tryGetAnnotationOfType(Conditional.class);
        return conditional.isPresent() && List.of(conditional.get().value()).contains(condition);
    }
}
