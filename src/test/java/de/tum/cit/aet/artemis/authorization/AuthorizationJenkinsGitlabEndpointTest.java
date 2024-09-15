package de.tum.cit.aet.artemis.authorization;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import de.tum.cit.aet.artemis.AbstractSpringIntegrationJenkinsGitlabTest;

/**
 * Contains the one automatic test covering all rest endpoints for authorization tests.
 */
class AuthorizationJenkinsGitlabEndpointTest extends AbstractSpringIntegrationJenkinsGitlabTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private AuthorizationTestService authorizationTestService;

    @Test
    void testEndpoints() {
        var requestMappingHandlerMapping = applicationContext.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);
        authorizationTestService.testConditionalEndpoints(requestMappingHandlerMapping.getHandlerMethods());
    }
}
