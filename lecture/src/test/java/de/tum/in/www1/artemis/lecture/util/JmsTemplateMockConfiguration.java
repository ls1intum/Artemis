package de.tum.in.www1.artemis.lecture.util;

import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.core.JmsTemplate;

@Configuration
public class JmsTemplateMockConfiguration {
    @Bean
    public JmsTemplate getJmsTemplate() {
        return Mockito.mock(JmsTemplate.class);
    }
}
