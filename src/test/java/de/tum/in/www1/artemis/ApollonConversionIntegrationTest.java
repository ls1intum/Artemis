package de.tum.in.www1.artemis;

import static de.tum.in.www1.artemis.config.Constants.APOLLON_CONVERSION_API_PATH;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import de.tum.in.www1.artemis.connector.apollon.ApollonRequestMockProvider;
import de.tum.in.www1.artemis.service.connectors.apollon.ApollonConversionService;
import de.tum.in.www1.artemis.service.connectors.apollon.dto.ApollonModelDTO;

class ApollonConversionIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ApollonRequestMockProvider apollonRequestMockProvider;

    @Autowired
    @Qualifier("apollonRestTemplate")
    RestTemplate restTemplate;

    @Value("${artemis.apollon.conversion-service-url}")
    private String apollonConversionUrl;

    @Autowired
    private ApollonConversionService apollonConversionService;

    /**
     * Initializes atheneService and example exercise
     */
    @BeforeEach
    void init() {
        // Create atheneService and inject @Value fields
        apollonConversionService.setRestTemplate(restTemplate);
        ReflectionTestUtils.setField(apollonConversionService, "apollonConversionUrl", apollonConversionUrl);

        apollonRequestMockProvider.enableMockingOfRequests();
    }

    @AfterEach
    void tearDown() {
        apollonRequestMockProvider.reset();
    }

    /**
     * Returns the pdf of the model in the request body
     */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testConvertingModel() throws Exception {

        String mockPdf = "This is my pdf file";
        InputStream inputStream = new ByteArrayInputStream(mockPdf.getBytes());
        Resource mockResource = Mockito.mock(Resource.class);
        Mockito.when(mockResource.getInputStream()).thenReturn(inputStream);
        apollonRequestMockProvider.mockConvertModel(true, mockResource);

        String model = "model";
        final var requestBody = new ApollonModelDTO();
        requestBody.setModel(model);

        String response = request.postWithResponseBodyString(APOLLON_CONVERSION_API_PATH, requestBody, HttpStatus.OK);
        assertThat(response).isEqualTo(mockPdf);
    }
}
