package de.tum.in.www1.artemis.authentication;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import de.tum.in.www1.artemis.AbstractSpringIntegrationTest;
import de.tum.in.www1.artemis.security.ArtemisInternalAuthenticationProvider;

public class AuthenticationTest extends AbstractSpringIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    public void analyzeApplicationContext_withExternalUserManagement_NoInternalAuthenticationBeanPresent() {
        assertThatExceptionOfType(NoSuchBeanDefinitionException.class).as("No bean of type ArtemisInternalAuthenticationProvider initialized")
                .isThrownBy(() -> applicationContext.getBean(ArtemisInternalAuthenticationProvider.class));
    }
}
