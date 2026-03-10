package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.HAZELCAST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import de.tum.cit.aet.artemis.core.DeferredEagerBeanInitializationCompletedEvent;

/**
 * Tests for {@link DeferredEagerBeanInitializer}.
 */
class DeferredEagerBeanInitializerTest {

    private ConfigurableApplicationContext mockContext;

    private Environment mockEnv;

    private DefaultListableBeanFactory mockBeanFactory;

    private DeferredEagerBeanInitializer initializer;

    @BeforeEach
    void setUp() {
        mockContext = mock(ConfigurableApplicationContext.class);
        mockEnv = mock(Environment.class);
        mockBeanFactory = mock(DefaultListableBeanFactory.class);
        when(mockContext.getBeanFactory()).thenReturn(mockBeanFactory);

        initializer = new DeferredEagerBeanInitializer(mockContext, mockEnv);
    }

    @Test
    void testInitializesHazelcastClusterManagerWhenConfigured() {
        // Setup
        when(mockEnv.getProperty("artemis.continuous-integration.data-store", HAZELCAST)).thenReturn(HAZELCAST);
        when(mockBeanFactory.getBeanDefinitionNames()).thenReturn(new String[0]);

        HazelcastClusterManager mockHazelcast = mock(HazelcastClusterManager.class);
        when(mockContext.getBean(HazelcastClusterManager.class)).thenReturn(mockHazelcast);

        // Execute
        initializer.initializeDeferredEagerBeans();

        // Verify
        verify(mockContext).getBean(HazelcastClusterManager.class);
        verify(mockContext).publishEvent(any(DeferredEagerBeanInitializationCompletedEvent.class));
    }

    @Test
    void testSkipsHazelcastClusterManagerWhenNotConfigured() {
        // Setup - use a different data store
        when(mockEnv.getProperty("artemis.continuous-integration.data-store", HAZELCAST)).thenReturn("other-datastore");
        when(mockBeanFactory.getBeanDefinitionNames()).thenReturn(new String[0]);

        // Execute
        initializer.initializeDeferredEagerBeans();

        // Verify HazelcastClusterManager was not requested
        verify(mockContext, never()).getBean(HazelcastClusterManager.class);
        verify(mockContext).publishEvent(any(DeferredEagerBeanInitializationCompletedEvent.class));
    }

    @Test
    void testInitializesLazySingletonBeans() {
        // Setup
        when(mockEnv.getProperty("artemis.continuous-integration.data-store", HAZELCAST)).thenReturn("other-datastore");

        String beanName = "testBean";
        when(mockBeanFactory.getBeanDefinitionNames()).thenReturn(new String[] { beanName });

        BeanDefinition mockBeanDef = mock(BeanDefinition.class);
        when(mockBeanFactory.getBeanDefinition(beanName)).thenReturn(mockBeanDef);
        when(mockBeanDef.isSingleton()).thenReturn(true);
        when(mockBeanDef.isLazyInit()).thenReturn(true);

        Object mockBean = new Object();
        when(mockContext.getBean(beanName)).thenReturn(mockBean);

        // Execute
        initializer.initializeDeferredEagerBeans();

        // Verify bean was initialized
        verify(mockContext).getBean(beanName);
        verify(mockContext).publishEvent(any(DeferredEagerBeanInitializationCompletedEvent.class));
    }

    @Test
    void testSkipsNonSingletonBeans() {
        // Setup
        when(mockEnv.getProperty("artemis.continuous-integration.data-store", HAZELCAST)).thenReturn("other-datastore");

        String beanName = "prototypeScopedBean";
        when(mockBeanFactory.getBeanDefinitionNames()).thenReturn(new String[] { beanName });

        BeanDefinition mockBeanDef = mock(BeanDefinition.class);
        when(mockBeanFactory.getBeanDefinition(beanName)).thenReturn(mockBeanDef);
        when(mockBeanDef.isSingleton()).thenReturn(false); // Not a singleton
        when(mockBeanDef.isLazyInit()).thenReturn(true);

        // Execute
        initializer.initializeDeferredEagerBeans();

        // Verify bean was NOT initialized (by name)
        verify(mockContext, never()).getBean(beanName);
        verify(mockContext).publishEvent(any(DeferredEagerBeanInitializationCompletedEvent.class));
    }

    @Test
    void testSkipsNonLazyBeans() {
        // Setup
        when(mockEnv.getProperty("artemis.continuous-integration.data-store", HAZELCAST)).thenReturn("other-datastore");

        String beanName = "eagerBean";
        when(mockBeanFactory.getBeanDefinitionNames()).thenReturn(new String[] { beanName });

        BeanDefinition mockBeanDef = mock(BeanDefinition.class);
        when(mockBeanFactory.getBeanDefinition(beanName)).thenReturn(mockBeanDef);
        when(mockBeanDef.isSingleton()).thenReturn(true);
        when(mockBeanDef.isLazyInit()).thenReturn(false); // Not lazy

        // Execute
        initializer.initializeDeferredEagerBeans();

        // Verify bean was NOT initialized (by name)
        verify(mockContext, never()).getBean(beanName);
        verify(mockContext).publishEvent(any(DeferredEagerBeanInitializationCompletedEvent.class));
    }

    @Test
    void testInitializesMultipleLazySingletonBeans() {
        // Setup
        when(mockEnv.getProperty("artemis.continuous-integration.data-store", HAZELCAST)).thenReturn("other-datastore");

        String bean1 = "lazyBean1";
        String bean2 = "lazyBean2";
        String bean3 = "eagerBean";

        when(mockBeanFactory.getBeanDefinitionNames()).thenReturn(new String[] { bean1, bean2, bean3 });

        BeanDefinition lazyDef1 = mock(BeanDefinition.class);
        when(lazyDef1.isSingleton()).thenReturn(true);
        when(lazyDef1.isLazyInit()).thenReturn(true);

        BeanDefinition lazyDef2 = mock(BeanDefinition.class);
        when(lazyDef2.isSingleton()).thenReturn(true);
        when(lazyDef2.isLazyInit()).thenReturn(true);

        BeanDefinition eagerDef = mock(BeanDefinition.class);
        when(eagerDef.isSingleton()).thenReturn(true);
        when(eagerDef.isLazyInit()).thenReturn(false);

        when(mockBeanFactory.getBeanDefinition(bean1)).thenReturn(lazyDef1);
        when(mockBeanFactory.getBeanDefinition(bean2)).thenReturn(lazyDef2);
        when(mockBeanFactory.getBeanDefinition(bean3)).thenReturn(eagerDef);

        when(mockContext.getBean(bean1)).thenReturn(new Object());
        when(mockContext.getBean(bean2)).thenReturn(new Object());

        // Execute
        initializer.initializeDeferredEagerBeans();

        // Verify only lazy beans were initialized
        verify(mockContext).getBean(bean1);
        verify(mockContext).getBean(bean2);
        verify(mockContext, never()).getBean(bean3);
        verify(mockContext).publishEvent(any(DeferredEagerBeanInitializationCompletedEvent.class));
    }

    @Test
    void testPublishesDeferredEagerBeanInitializationCompletedEvent() {
        // Setup
        when(mockEnv.getProperty("artemis.continuous-integration.data-store", HAZELCAST)).thenReturn("other-datastore");
        when(mockBeanFactory.getBeanDefinitionNames()).thenReturn(new String[0]);

        // Execute
        initializer.initializeDeferredEagerBeans();

        // Verify event was published
        ArgumentCaptor<DeferredEagerBeanInitializationCompletedEvent> eventCaptor = ArgumentCaptor.forClass(DeferredEagerBeanInitializationCompletedEvent.class);
        verify(mockContext, times(1)).publishEvent(eventCaptor.capture());

        assertThat(eventCaptor.getValue()).isNotNull();
    }

    @Test
    void testHazelcastCaseInsensitiveComparison() {
        // Setup - use uppercase HAZELCAST
        when(mockEnv.getProperty("artemis.continuous-integration.data-store", HAZELCAST)).thenReturn("HAZELCAST");
        when(mockBeanFactory.getBeanDefinitionNames()).thenReturn(new String[0]);

        HazelcastClusterManager mockHazelcast = mock(HazelcastClusterManager.class);
        when(mockContext.getBean(HazelcastClusterManager.class)).thenReturn(mockHazelcast);

        // Execute
        initializer.initializeDeferredEagerBeans();

        // Verify HazelcastClusterManager was initialized
        verify(mockContext).getBean(HazelcastClusterManager.class);
    }

    @Test
    void testHazelcastMixedCaseComparison() {
        // Setup - use mixed case to verify case-insensitive comparison
        when(mockEnv.getProperty("artemis.continuous-integration.data-store", HAZELCAST)).thenReturn("HaZeLcAsT");
        when(mockBeanFactory.getBeanDefinitionNames()).thenReturn(new String[0]);

        HazelcastClusterManager mockHazelcast = mock(HazelcastClusterManager.class);
        when(mockContext.getBean(HazelcastClusterManager.class)).thenReturn(mockHazelcast);

        // Execute
        initializer.initializeDeferredEagerBeans();

        // Verify HazelcastClusterManager was initialized
        verify(mockContext).getBean(HazelcastClusterManager.class);
    }

    @Test
    void testBeansAreInitializedBeforeEventIsPublished() {
        // Setup
        when(mockEnv.getProperty("artemis.continuous-integration.data-store", HAZELCAST)).thenReturn("other-datastore");

        String beanName = "testBean";
        when(mockBeanFactory.getBeanDefinitionNames()).thenReturn(new String[] { beanName });

        BeanDefinition mockBeanDef = mock(BeanDefinition.class);
        when(mockBeanFactory.getBeanDefinition(beanName)).thenReturn(mockBeanDef);
        when(mockBeanDef.isSingleton()).thenReturn(true);
        when(mockBeanDef.isLazyInit()).thenReturn(true);

        AtomicBoolean beanWasInitialized = new AtomicBoolean(false);
        AtomicBoolean eventWasPublishedAfterBeanInit = new AtomicBoolean(false);

        when(mockContext.getBean(beanName)).thenAnswer(invocation -> {
            beanWasInitialized.set(true);
            return new Object();
        });

        doAnswer(invocation -> {
            eventWasPublishedAfterBeanInit.set(beanWasInitialized.get());
            return null;
        }).when(mockContext).publishEvent(any(DeferredEagerBeanInitializationCompletedEvent.class));

        // Execute
        initializer.initializeDeferredEagerBeans();

        // Verify order
        assertThat(beanWasInitialized.get()).isTrue();
        assertThat(eventWasPublishedAfterBeanInit.get()).isTrue();
    }
}
