package de.tum.in.www1.artemis;

import static de.tum.in.www1.artemis.web.rest.PlantUmlResource.Endpoints.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import java.io.IOException;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;

public class PlantUmlIntegrationTest extends AbstractSpringIntegrationTest {

    private final String UML_DIAGRAM_STRING = "@someplantuml";

    private final byte[] UML_PNG = new byte[] { 3, 4, 2, 1 };

    private final String UML_SVG = "foobar";

    @Autowired
    private DatabaseUtilService database;

    @Autowired
    private RequestUtilService request;

    @BeforeEach
    public void setUp() throws IOException {
        database.addUsers(1, 0, 0);
        doReturn(UML_PNG).when(plantUmlService).generatePng(UML_DIAGRAM_STRING);
        doReturn(UML_SVG).when(plantUmlService).generateSvg(UML_DIAGRAM_STRING);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void generatePng_asStudent_success() throws Exception {
        final var paramMap = new LinkedMultiValueMap<String, String>();
        paramMap.setAll(Map.of("plantuml", UML_DIAGRAM_STRING));
        final var pngResponse = request.getPng(ROOT + GENERATE_PNG, HttpStatus.OK, paramMap);

        assertThat(UML_PNG).isEqualTo(pngResponse);
    }

    @Test
    @WithMockUser
    public void generateSvg_asStudent_success() throws Exception {
        final var paramMap = new LinkedMultiValueMap<String, String>();
        paramMap.setAll(Map.of("plantuml", UML_DIAGRAM_STRING));
        final var svgResponse = request.get(ROOT + GENERATE_SVG, HttpStatus.OK, String.class, paramMap);

        assertThat(UML_SVG).isEqualTo(svgResponse);
    }
}
