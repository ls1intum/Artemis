package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Component
@Profile(PROFILE_CORE)
public class SpringBeanProvider implements ApplicationContextAware, BeanFactoryPostProcessor {

    private static ApplicationContext applicationContext;

    private static ConfigurableListableBeanFactory beanFactory;

    // Runs very early (before bean instantiation)
    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory bf) throws BeansException {
        SpringBeanProvider.beanFactory = bf;
    }

    @Override
    public void setApplicationContext(ApplicationContext ctx) {
        SpringBeanProvider.applicationContext = ctx;
    }

    public static <T> T getBean(Class<T> type) {
        // Prefer beanFactory (available earliest), fall back to applicationContext
        if (beanFactory != null) {
            return beanFactory.getBean(type);
        }
        Assert.state(applicationContext != null, "ApplicationContext not initialized yet");
        return applicationContext.getBean(type);
    }
}
