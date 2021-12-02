package de.tum.in.www1.artemis.config;

import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

import de.tum.in.www1.artemis.aop.logging.LoggingAspect;

import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_DEVELOPMENT;

@Configuration
@EnableAspectJAutoProxy
public class LoggingAspectConfiguration {

    @Bean
    @Profile(SPRING_PROFILE_DEVELOPMENT)
    public LoggingAspect loggingAspect(Environment env) {
        return new LoggingAspect(env);
    }
}
