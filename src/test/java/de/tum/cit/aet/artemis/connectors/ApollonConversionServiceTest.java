package de.tum.cit.aet.artemis.connectors;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import de.tum.cit.aet.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.connector.apollon.ApollonRequestMockProvider;
import de.tum.cit.aet.artemis.modeling.service.apollon.ApollonConversionService;

class ApollonConversionServiceTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private ApollonRequestMockProvider apollonRequestMockProvider;

    @Autowired
    @Qualifier("apollonRestTemplate")
    RestTemplate restTemplate;

    @Value("${artemis.apollon.conversion-service-url}")
    private String apollonConversionUrl;

    ApollonConversionService apollonConversionService;

    /**
     * Initializes apollonConversionService
     */
    @BeforeEach
    void init() {
        // Create apollonConversionService and inject @Value fields
        apollonConversionService = new ApollonConversionService(restTemplate);
        ReflectionTestUtils.setField(apollonConversionService, "apollonConversionUrl", apollonConversionUrl);

        apollonRequestMockProvider.enableMockingOfRequests();
    }

    @AfterEach
    void tearDown() throws Exception {
        apollonRequestMockProvider.reset();
    }

    /**
     * Converts the model to pdf
     */
    @Test
    void testConvertingModel() throws IOException {
        String mockPdf = "This is my pdf file";
        InputStream inputStream = new ByteArrayInputStream(mockPdf.getBytes());
        Resource mockResource = Mockito.mock(Resource.class);
        Mockito.when(mockResource.getInputStream()).thenReturn(inputStream);
        apollonRequestMockProvider.mockConvertModel(true, mockResource);
        final InputStream returnedInputStream = apollonConversionService.convertModel("model");
        String text = new BufferedReader(new InputStreamReader(returnedInputStream, StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n"));
        assertThat(text).isEqualTo(mockPdf);
    }

}
