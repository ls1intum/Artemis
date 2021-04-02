package de.tum.in.www1.artemis.config;

import org.slf4j.*;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableJpaRepositories("de.tum.in.www1.artemis.repository")
@EnableJpaAuditing(auditorAwareRef = "springSecurityAuditorAware")
@EnableTransactionManagement
public class DatabaseConfiguration {

    private final Logger log = LoggerFactory.getLogger(DatabaseConfiguration.class);
}
