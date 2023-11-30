package de.tum.in.www1.artemis.config;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan
class HibernatePropertiesConfig {

    @Bean
    public HibernatePropertiesCustomizer hibernateCustomizer(StatementInspector statementInspector) {
        return (properties) -> properties.put(AvailableSettings.STATEMENT_INSPECTOR, statementInspector);
    }
}
