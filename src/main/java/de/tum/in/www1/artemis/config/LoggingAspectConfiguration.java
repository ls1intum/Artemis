package de.tum.in.www1.artemis.config;

import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_DEVELOPMENT;

import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

import de.tum.in.www1.artemis.aop.logging.LoggingAspect;

@Configuration
@EnableAspectJAutoProxy
public class LoggingAspectConfiguration {

    @Bean
    @Profile(SPRING_PROFILE_DEVELOPMENT)
    public LoggingAspect loggingAspect(Environment env) {
        return new LoggingAspect(env);
    }
}
