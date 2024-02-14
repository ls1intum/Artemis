package de.tum.in.www1.artemis;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        assertTrue(condition.matches(context, new StandardAnnotationMetadata(CoreNotSchedulingConditionTest.class)), "Condition should match when only core profile is active");
    }

    @Test
    void testConditionWithCoreAndSchedulingProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.addActiveProfile("core");
        environment.addActiveProfile("scheduling");

        CoreNotSchedulingCondition condition = new CoreNotSchedulingCondition();
        ConditionContext context = createConditionContext(environment);

        assertFalse(condition.matches(context, new StandardAnnotationMetadata(CoreNotSchedulingConditionTest.class)),
                "Condition should not match when both core and scheduling profiles are active");
    }

    @Test
    void testConditionWithSchedulingProfileOnly() {
        MockEnvironment environment = new MockEnvironment();
        environment.addActiveProfile("scheduling");

        CoreNotSchedulingCondition condition = new CoreNotSchedulingCondition();
        ConditionContext context = createConditionContext(environment);

        assertFalse(condition.matches(context, new StandardAnnotationMetadata(CoreNotSchedulingConditionTest.class)),
                "Condition should not match when only scheduling profile is active");
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
