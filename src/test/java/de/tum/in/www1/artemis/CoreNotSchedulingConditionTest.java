package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.StandardAnnotationMetadata;
import org.springframework.mock.env.MockEnvironment;

import de.tum.in.www1.artemis.config.CoreNotSchedulingCondition;

class CoreNotSchedulingConditionTest {

    @Test
    void testConditionWithCoreProfileOnly() {
        MockEnvironment environment = new MockEnvironment();
        environment.addActiveProfile("core");

        CoreNotSchedulingCondition condition = new CoreNotSchedulingCondition();
        ConditionContext context = createConditionContext(environment);

        assertThat(condition.matches(context, new StandardAnnotationMetadata(CoreNotSchedulingConditionTest.class))).as("Condition should match when only core profile is active")
                .isTrue();
    }

    @Test
    void testConditionWithCoreAndSchedulingProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.addActiveProfile("core");
        environment.addActiveProfile("scheduling");

        CoreNotSchedulingCondition condition = new CoreNotSchedulingCondition();
        ConditionContext context = createConditionContext(environment);

        assertThat(condition.matches(context, new StandardAnnotationMetadata(CoreNotSchedulingConditionTest.class)))
                .as("Condition should not match when both core and scheduling profiles are active").isFalse();
    }

    @Test
    void testConditionWithSchedulingProfileOnly() {
        MockEnvironment environment = new MockEnvironment();
        environment.addActiveProfile("scheduling");

        CoreNotSchedulingCondition condition = new CoreNotSchedulingCondition();
        ConditionContext context = createConditionContext(environment);

        assertThat(condition.matches(context, new StandardAnnotationMetadata(CoreNotSchedulingConditionTest.class)))
                .as("Condition should not match when only scheduling profile is active").isFalse();
    }

    private ConditionContext createConditionContext(MockEnvironment environment) {
        return new ConditionContext() {

            @Override
            public BeanDefinitionRegistry getRegistry() {
                return null;
            }

            @Override
            public ConfigurableListableBeanFactory getBeanFactory() {
                return null;
            }

            @Override
            public MockEnvironment getEnvironment() {
                return environment;
            }

            @Override
            public ResourceLoader getResourceLoader() {
                return null;
            }

            @Override
            public ClassLoader getClassLoader() {
                return null;
            }
        };
    }
}
