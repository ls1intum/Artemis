package de.tum.in.www1.artemis.util;

import jakarta.servlet.http.HttpServletMapping;
import jakarta.servlet.http.MappingMatch;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import de.tum.in.www1.artemis.AbstractSpringIntegrationLocalCILocalVCTest;

/**
 * The {@link WebEnvironment} set for example in {@link AbstractSpringIntegrationLocalCILocalVCTest} causes the MockMvc to not set the servlet path correctly.
 * Considering <a href="https://github.com/spring-projects/spring-security/issues/14418">this Github issue</a>, there is no fix other this workaround.
 * <p>
 * We set this processor only in the loaded config so that it does not affect other tests that don't use the WebEnvironment.
 */
public class FixMissingServletPathProcessor implements RequestPostProcessor {

    @NotNull
    @Override
    public MockHttpServletRequest postProcessRequest(MockHttpServletRequest request) {
        request.setHttpServletMapping(new HttpServletMapping() {

            @Override
            public String getMatchValue() {
                return "";
            }

            @Override
            public String getPattern() {
                return "";
            }

            @Override
            public String getServletName() {
                return "dispatcherServlet";
            }

            @Override
            public MappingMatch getMappingMatch() {
                return MappingMatch.PATH;
            }
        });

        request.setContextPath("/");

        return request;
    }
}
