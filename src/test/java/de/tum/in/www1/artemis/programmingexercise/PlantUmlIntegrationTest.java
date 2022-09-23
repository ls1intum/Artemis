package de.tum.in.www1.artemis.programmingexercise;

import static de.tum.in.www1.artemis.web.rest.PlantUmlResource.Endpoints.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import net.sourceforge.plantuml.SourceStringReader;
import net.sourceforge.plantuml.core.DiagramDescription;

class PlantUmlIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String UML_DIAGRAM_STRING = "@somePlantUml";

    private static final String UML_SVG = "foobar";

    private static final DiagramDescription description = new DiagramDescription(UML_SVG);

    private final byte[] UML_PNG = new byte[] { 3, 4, 2, 1 };

    @BeforeEach
    void setUp() throws IOException {
        database.addUsers(1, 0, 0, 0);
    }

    @AfterEach
    void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void generatePng_asStudent_success() throws Exception {
        try (var ignored = Mockito.mockConstruction(ByteArrayOutputStream.class, (bosMock, context) -> doReturn(UML_PNG).when(bosMock).toByteArray())) {
            try (var ignored2 = Mockito.mockConstruction(SourceStringReader.class, (readerMock, context) -> doReturn(description).when(readerMock).outputImage(any(), any()))) {
                final var paramMap = new LinkedMultiValueMap<String, String>();
                paramMap.setAll(Map.of("plantuml", UML_DIAGRAM_STRING));
                final var pngResponse = request.getPng(ROOT + GENERATE_PNG, HttpStatus.OK, paramMap);
                assertThat(pngResponse).isEqualTo(UML_PNG);
            }
        }
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void generateSvg_asStudent_success() throws Exception {
        // Mock the method outputImage, so that it simply writes the expected value into the byte array output stream
        Answer<DiagramDescription> answer = invocation -> {
            ByteArrayOutputStream bos = invocation.getArgument(0);
            bos.write(UML_SVG.getBytes(StandardCharsets.UTF_8));
            return description;
        };
        try (var ignored = Mockito.mockConstruction(SourceStringReader.class, (readerMock, context) -> when(readerMock.outputImage(any(), any())).then(answer))) {
            final var paramMap = new LinkedMultiValueMap<String, String>();
            paramMap.setAll(Map.of("plantuml", UML_DIAGRAM_STRING));
            final var svgResponse = request.get(ROOT + GENERATE_SVG, HttpStatus.OK, String.class, paramMap);
            assertThat(svgResponse).isEqualTo(UML_SVG);
        }
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void generateSvg_asStudent_error() throws Exception {
        final var paramMap = new LinkedMultiValueMap<String, String>();
        paramMap.setAll(Map.of("plantuml", ""));    // empty string
        request.get(ROOT + GENERATE_SVG, HttpStatus.INTERNAL_SERVER_ERROR, String.class, paramMap);

        var veryLongString = new String(new char[10001]).replace('\0', 'a');
        paramMap.setAll(Map.of("plantuml", veryLongString));
        request.get(ROOT + GENERATE_SVG, HttpStatus.INTERNAL_SERVER_ERROR, String.class, paramMap);
    }
}
