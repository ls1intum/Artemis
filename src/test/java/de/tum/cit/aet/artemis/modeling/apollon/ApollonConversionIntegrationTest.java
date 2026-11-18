package de.tum.cit.aet.artemis.modeling.apollon;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.connector.apollon.ApollonRequestMockProvider;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.LimitRequestsPerMinuteAspect;
import de.tum.cit.aet.artemis.core.util.RateLimitTestUtil;
import de.tum.cit.aet.artemis.modeling.dto.ApollonModelDTO;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class ApollonConversionIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private ApollonRequestMockProvider apollonRequestMockProvider;

    @Autowired
    private LimitRequestsPerMinuteAspect requestLimitAspect;

    @BeforeEach
    void init() {
        apollonRequestMockProvider.enableMockingOfRequests();
    }

    @AfterEach
    void tearDown() throws Exception {
        apollonRequestMockProvider.reset();
    }

    /**
     * Returns the pdf of the model in the request body
     */
    @Test
    @WithMockUser(username = "instructor1", roles = "USER")
    void testConvertingModel() throws Exception {

        String mockPdf = "This is my pdf file";
        InputStream inputStream = new ByteArrayInputStream(mockPdf.getBytes());
        Resource mockResource = Mockito.mock(Resource.class);
        Mockito.when(mockResource.getInputStream()).thenReturn(inputStream);
        apollonRequestMockProvider.mockConvertModel(true, mockResource);

        final var apollonModel = new ApollonModelDTO("model");
        String response = request.postWithResponseBodyString("/api/modeling/apollon/convert-to-pdf", apollonModel, HttpStatus.OK);
        assertThat(response).isEqualTo(mockPdf);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "USER")
    void testConvertingModelTooOftenIsRejected() throws Exception {
        apollonRequestMockProvider.mockConvertModel(true, resourceOf("pdf"));
        apollonRequestMockProvider.mockConvertModel(true, resourceOf("pdf"));
        RateLimitTestUtil.withRateLimits(requestLimitAspect, properties -> properties.setApollonConversionRequestsPerMinute(1), () -> {
            var apollonModel = new ApollonModelDTO("model");
            request.postWithResponseBodyString("/api/modeling/apollon/convert-to-pdf", apollonModel, HttpStatus.OK);
            var originalAuthentication = SecurityContextHolder.getContext().getAuthentication();
            try {
                TestSecurityContextHolder
                        .setAuthentication(UsernamePasswordAuthenticationToken.authenticated("instructor2", "", List.of(new SimpleGrantedAuthority(Role.STUDENT.getAuthority()))));
                request.postWithResponseBodyString("/api/modeling/apollon/convert-to-pdf", apollonModel, HttpStatus.OK);
            }
            finally {
                TestSecurityContextHolder.setAuthentication(originalAuthentication);
            }
            request.postWithResponseBodyString("/api/modeling/apollon/convert-to-pdf", apollonModel, HttpStatus.TOO_MANY_REQUESTS);
        });
    }

    private static Resource resourceOf(String content) throws Exception {
        Resource mockResource = Mockito.mock(Resource.class);
        Mockito.when(mockResource.getInputStream()).thenReturn(new ByteArrayInputStream(content.getBytes()));
        return mockResource;
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, ApollonModelDTO.MAX_MODEL_LENGTH + 1 })
    @WithMockUser(username = "instructor1", roles = "USER")
    void testConvertingModelWithInvalidSizeIsRejected(int length) throws Exception {
        final var apollonModel = new ApollonModelDTO("a".repeat(length));
        request.postWithResponseBodyString("/api/modeling/apollon/convert-to-pdf", apollonModel, HttpStatus.BAD_REQUEST);
    }
}
