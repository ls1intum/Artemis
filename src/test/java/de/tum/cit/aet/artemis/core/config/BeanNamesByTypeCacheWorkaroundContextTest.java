package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Guards that {@link BeanNamesByTypeCacheWorkaround} is applied to the real application context. A registration that Spring does
 * not pick up fails silently: the context starts and only hangs when the unlucky thread interleaving happens.
 */
class BeanNamesByTypeCacheWorkaroundContextTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private ConfigurableListableBeanFactory beanFactory;

    @Test
    void shouldHaveReplacedTheTypeCachesOfTheApplicationContext() throws ReflectiveOperationException {
        assertThat(beanFactory).isInstanceOf(DefaultListableBeanFactory.class);

        for (String fieldName : List.of("allBeanNamesByType", "singletonBeanNamesByType")) {
            Field cacheField = DefaultListableBeanFactory.class.getDeclaredField(fieldName);
            cacheField.setAccessible(true);

            assertThat(cacheField.get(beanFactory)).as(fieldName).isInstanceOf(BeanNamesByTypeCacheWorkaround.ComputeOutsideLockCache.class);
        }
    }
}
