package de.tum.in.www1.artemis.authorization;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;

/**
 * Contains the one automatic test covering all rest endpoints for authorization tests.
 */
class AuthorizationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private AuthorizationTestService authorizationTestService;

    @Test
    void testEndpoints() throws InvocationTargetException, IllegalAccessException {
        var requestMappingHandlerMapping = applicationContext.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);
        Map<RequestMappingInfo, HandlerMethod> endpointMap = requestMappingHandlerMapping.getHandlerMethods();
        // Filter out endpoints that should not be tested.
        endpointMap = endpointMap.entrySet().stream().filter(entry -> authorizationTestService.validEndpointToTest(entry.getValue(), false))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        authorizationTestService.testEndpoints(endpointMap);
    }
}
