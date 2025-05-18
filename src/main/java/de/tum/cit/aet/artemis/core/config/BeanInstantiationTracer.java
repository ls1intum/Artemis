package de.tum.cit.aet.artemis.core.config;

import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_DEVELOPMENT;

import java.util.ArrayDeque;
import java.util.Deque;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile(SPRING_PROFILE_DEVELOPMENT)
@Component
public class BeanInstantiationTracer implements InstantiationAwareBeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(BeanInstantiationTracer.class);

    private static final String BASE_PACKAGE = "de.tum.cit.aet.artemis";

    private final ThreadLocal<Deque<String>> beanCallStack = ThreadLocal.withInitial(ArrayDeque::new);

    @Override
    public Object postProcessBeforeInstantiation(Class<?> beanClass, String beanName) throws BeansException {
        if (beanClass.getName().startsWith(BASE_PACKAGE)) {
            Deque<String> stack = beanCallStack.get();
            String parent = stack.peek();
            log.info("Instantiating: {} <- triggered by {}", beanName, (parent != null ? parent : "root context"));
            stack.push(beanName);
        }
        return null; // let Spring continue with normal instantiation
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean.getClass().getName().startsWith(BASE_PACKAGE)) {
            Deque<String> stack = beanCallStack.get();
            if (!stack.isEmpty() && beanName.equals(stack.peek())) {
                stack.pop();
            }
        }
        return bean;
    }
}
