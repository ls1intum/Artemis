package de.tum.cit.aet.artemis.programming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.cit.aet.artemis.programming.service.PlantUmlService;
import net.sourceforge.plantuml.SourceStringReader;
import net.sourceforge.plantuml.core.DiagramDescription;

class PlantUmlIntegrationTest extends AbstractProgrammingIntegrationIndependentTest {

    private static final String TEST_PREFIX = "plantumlintegration";

    private static final String UML_DIAGRAM_STRING = "@somePlantUml";

    private static final String UML_SVG = "foobar";

    private static final DiagramDescription description = new DiagramDescription(UML_SVG);

    private final byte[] UML_PNG = new byte[] { 3, 4, 2, 1 };

    /**
     * A simple but valid PlantUML class diagram for testing actual rendering.
     * This diagram must be renderable by PlantUML without errors.
     */
    private static final String VALID_PLANTUML_DIAGRAM = """
            @startuml
            class Student {
                +name: String
                +getId(): int
            }
            class Course {
                +title: String
            }
            Student --> Course
            @enduml
            """;

    @Autowired
    private PlantUmlService plantUmlService;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void generatePng_asStudent_success() throws Exception {
        // Mock the method outputImage, so that it simply writes the expected value into the byte array output stream
        Answer<DiagramDescription> answer = invocation -> {
            ByteArrayOutputStream bos = invocation.getArgument(0);
            bos.write(UML_PNG);
            return description;
        };
        try (var ignored = Mockito.mockConstruction(SourceStringReader.class, (readerMock, _) -> when(readerMock.outputImage(any(), any())).then(answer))) {
            final var paramMap = new LinkedMultiValueMap<String, String>();
            paramMap.setAll(Map.of("plantuml", UML_DIAGRAM_STRING));
            final var pngResponse = request.getPng("/api/programming/plantuml/png", HttpStatus.OK, paramMap);
            assertThat(pngResponse).isEqualTo(UML_PNG);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void generateSvg_asStudent_success() throws Exception {
        // Mock the method outputImage, so that it simply writes the expected value into the byte array output stream
        Answer<DiagramDescription> answer = invocation -> {
            ByteArrayOutputStream bos = invocation.getArgument(0);
            bos.write(UML_SVG.getBytes(StandardCharsets.UTF_8));
            return description;
        };
        try (var ignored = Mockito.mockConstruction(SourceStringReader.class, (readerMock, _) -> when(readerMock.outputImage(any(), any())).then(answer))) {
            final var paramMap = new LinkedMultiValueMap<String, String>();
            paramMap.setAll(Map.of("plantuml", UML_DIAGRAM_STRING));
            final var svgResponse = request.get("/api/programming/plantuml/svg", HttpStatus.OK, String.class, paramMap);
            assertThat(svgResponse).isEqualTo(UML_SVG);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void generateSvg_asStudent_error() throws Exception {
        final var paramMap = new LinkedMultiValueMap<String, String>();
        paramMap.setAll(Map.of("plantuml", ""));    // empty string
        request.get("/api/programming/plantuml/svg", HttpStatus.INTERNAL_SERVER_ERROR, String.class, paramMap);

        var veryLongString = new String(new char[10001]).replace('\0', 'a');
        paramMap.setAll(Map.of("plantuml", veryLongString));
        request.get("/api/programming/plantuml/svg", HttpStatus.INTERNAL_SERVER_ERROR, String.class, paramMap);
    }

    /**
     * Tests that PlantUML can actually render a diagram with the light theme applied.
     * This test does NOT mock the PlantUML rendering and will fail if:
     * - The theme files cannot be loaded
     * - PlantUML cannot process the theme (e.g., security profile issues)
     * - The theme syntax is invalid
     */
    @Test
    void generateSvg_withLightTheme_actualRendering() throws Exception {
        String svg = plantUmlService.generateSvg(VALID_PLANTUML_DIAGRAM, false);

        assertThat(svg).as("SVG output should not be empty").isNotEmpty();
        assertThat(svg).as("Output should be valid SVG").contains("<svg");
        assertThat(svg).as("SVG should contain the class name from diagram").contains("Student");
        assertThat(svg).as("Output should not contain PlantUML error markers").doesNotContain("Syntax Error");
    }

    /**
     * Tests that PlantUML can actually render a diagram with the dark theme applied.
     * This test does NOT mock the PlantUML rendering and will fail if:
     * - The theme files cannot be loaded
     * - PlantUML cannot process the theme (e.g., security profile issues)
     * - The theme syntax is invalid
     */
    @Test
    void generateSvg_withDarkTheme_actualRendering() throws Exception {
        String svg = plantUmlService.generateSvg(VALID_PLANTUML_DIAGRAM, true);

        assertThat(svg).as("SVG output should not be empty").isNotEmpty();
        assertThat(svg).as("Output should be valid SVG").contains("<svg");
        assertThat(svg).as("SVG should contain the class name from diagram").contains("Student");
        assertThat(svg).as("Output should not contain PlantUML error markers").doesNotContain("Syntax Error");
    }

    /**
     * Tests that PlantUML can actually render a PNG with theme applied.
     * This test does NOT mock the PlantUML rendering and validates that
     * the output is a valid PNG file (starts with PNG magic bytes).
     */
    @Test
    void generatePng_withTheme_actualRendering() throws Exception {
        byte[] png = plantUmlService.generatePng(VALID_PLANTUML_DIAGRAM, false);

        assertThat(png).as("PNG output should not be empty").isNotEmpty();
        // PNG files start with magic bytes: 0x89 0x50 0x4E 0x47 (‰PNG)
        assertThat(png[0]).as("PNG should start with correct magic byte").isEqualTo((byte) 0x89);
        assertThat(png[1]).as("PNG should have 'P' as second byte").isEqualTo((byte) 0x50);
        assertThat(png[2]).as("PNG should have 'N' as third byte").isEqualTo((byte) 0x4E);
        assertThat(png[3]).as("PNG should have 'G' as fourth byte").isEqualTo((byte) 0x47);
    }
}
