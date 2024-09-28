package de.tum.cit.aet.artemis.authorization;

import java.lang.reflect.InvocationTargetException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import de.tum.cit.aet.artemis.AbstractSpringIntegrationGitlabCIGitlabSamlTest;

/**
 * Contains the one automatic test covering all rest endpoints for authorization tests.
 */
class AuthorizationGitlabCISamlEndpointTest extends AbstractSpringIntegrationGitlabCIGitlabSamlTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private AuthorizationTestService authorizationTestService;

    @Test
    void testEndpoints() throws InvocationTargetException, IllegalAccessException {
        var requestMappingHandlerMapping = applicationContext.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);
        authorizationTestService.testConditionalEndpoints(requestMappingHandlerMapping.getHandlerMethods());
    }
}
