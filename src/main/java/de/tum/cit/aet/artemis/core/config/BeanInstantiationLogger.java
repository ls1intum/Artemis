package de.tum.cit.aet.artemis.core.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.ArtemisApp;
import de.tum.cit.aet.artemis.core.util.TimeLogUtil;

@Component
public class BeanInstantiationLogger implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(BeanInstantiationLogger.class);

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        String className = bean.getClass().getName();
        if (log.isDebugEnabled() && className.startsWith("de.tum.cit.aet.artemis")) {
            log.debug("Instantiated at {}: {} - {}", TimeLogUtil.formatDurationFrom(ArtemisApp.appStart), beanName, className);
        }
        return bean;
    }
}
