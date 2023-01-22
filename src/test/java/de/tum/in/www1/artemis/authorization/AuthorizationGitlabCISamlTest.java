package de.tum.in.www1.artemis.authorization;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import de.tum.in.www1.artemis.AbstractSpringIntegrationGitlabCIGitlabSamlTest;

/**
 * Contains the one automatic test covering all rest endpoints for authorization tests.
 */
class AuthorizationGitlabCISamlTest extends AbstractSpringIntegrationGitlabCIGitlabSamlTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private AuthorizationTestService authorizationTestService;

    @Test
    void testEndpoints() throws InvocationTargetException, IllegalAccessException {
        var requestMappingHandlerMapping = applicationContext.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);
        Map<RequestMappingInfo, HandlerMethod> endpointMap = requestMappingHandlerMapping.getHandlerMethods();
        // Filter out endpoints that should not be tested.
        endpointMap = endpointMap.entrySet().stream().filter(entry -> authorizationTestService.validEndpointToTest(entry.getValue(), true))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        authorizationTestService.testEndpoints(endpointMap);
    }
}
