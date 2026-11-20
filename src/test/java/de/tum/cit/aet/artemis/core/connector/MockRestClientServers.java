package de.tum.cit.aet.artemis.core.connector;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Lets a test mock the remote server of a {@link RestClient} bean, with the {@link MockRestServiceServer} that the request mock providers
 * already use for a {@code RestTemplate}.
 * <p>
 * A {@code MockRestServiceServer} can only be bound to a {@link RestClient.Builder}, while the application context only holds the
 * finished client, and the services keep a reference to that client. This post-processor therefore rebuilds every {@link RestClient} bean
 * from its own settings ({@link RestClient#mutate()}) with a mock request factory, before any service receives it. The base URL, the
 * interceptors and the other settings of the bean stay, so a test still sees the headers that an interceptor adds.
 * <p>
 * The client beans of Artemis are lazy, so the post-processor only sees a bean once something creates it. {@link #serverOf} therefore
 * creates the bean itself, and a test does not depend on which other test or service created it first.
 * <p>
 * Each context has its own instance, because the test JVM keeps several contexts at once and runs them in parallel. A provider asks the
 * instance of its context for the server of a bean by the name of the bean.
 * <p>
 * The servers do not require the expectations in order, which is what the request mock providers set up. A test calls
 * {@link MockRestServiceServer#reset()} between its cases.
 */
public class MockRestClientServers implements BeanPostProcessor, BeanFactoryAware {

    private final Map<String, MockRestServiceServer> serversByBeanName = new ConcurrentHashMap<>();

    private ListableBeanFactory beanFactory;

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = (ListableBeanFactory) beanFactory;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof RestClient restClient) {
            RestClient.Builder builder = restClient.mutate();
            serversByBeanName.put(beanName, MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build());
            return builder.build();
        }
        return bean;
    }

    /**
     * Returns the mock server of a {@link RestClient} bean and creates the bean if nothing has created it yet.
     *
     * @param beanName the name of the bean, for example {@code apollonRestClient}
     * @return the server that receives the requests of the bean
     * @throws IllegalStateException if the context has no {@link RestClient} bean of that name, for example because the feature is disabled
     */
    public MockRestServiceServer serverOf(String beanName) {
        try {
            beanFactory.getBean(beanName, RestClient.class);
        }
        catch (NoSuchBeanDefinitionException ex) {
            throw new IllegalStateException(
                    "The context has no RestClient bean named '" + beanName + "', only " + Arrays.toString(beanFactory.getBeanNamesForType(RestClient.class)), ex);
        }
        return serversByBeanName.get(beanName);
    }
}
