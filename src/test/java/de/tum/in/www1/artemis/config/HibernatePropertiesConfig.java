package de.tum.in.www1.artemis.config;

import java.util.Map;

import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import de.tum.in.www1.artemis.util.HibernateQueryInterceptor;

@Configuration
@ComponentScan
class HibernatePropertiesConfig implements HibernatePropertiesCustomizer {

    private final HibernateQueryInterceptor hibernateQueryInterceptor;

    public HibernatePropertiesConfig(HibernateQueryInterceptor hibernateQueryInterceptor) {
        this.hibernateQueryInterceptor = hibernateQueryInterceptor;
    }

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put("hibernate.session_factory.interceptor", hibernateQueryInterceptor);
    }
}
