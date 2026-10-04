package de.tum.cit.aet.artemis.programming;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tum.cit.aet.artemis.programming.service.PlantUmlService;
import net.sourceforge.plantuml.security.SecurityProfile;
import net.sourceforge.plantuml.security.SecurityUtils;

/**
 * Pins the rendering profile PlantUML runs under.
 * <p>
 * Diagram sources reach the renderer straight from request bodies, and the library's own default profile allows a diagram
 * to pull in local files and remote resources through directives such as {@code !include}. The profile is therefore set by
 * {@link PlantUmlService} rather than inherited, and this test fails if that stops happening or is loosened.
 */
class PlantUmlSecurityProfileTest {

    /**
     * Forces initialisation of the service class, which is what applies the profile. A class literal only loads the class,
     * it does not run the static initialiser, so {@link Class#forName} is required here.
     */
    private static void loadServiceClass() {
        try {
            Class.forName(PlantUmlService.class.getName());
        }
        catch (ClassNotFoundException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void theRenderingProfileIsRestrictive() {
        loadServiceClass();

        assertThat(SecurityUtils.getSecurityProfile()).as("the permissive profiles must never be in effect").isIn(SecurityProfile.SANDBOX, SecurityProfile.ALLOWLIST);
    }

    @Test
    void aDiagramCannotPullInALocalFile(@TempDir Path tempDir) throws IOException {
        loadServiceClass();
        Path included = tempDir.resolve("included.iuml");
        FileUtils.writeStringToFile(included.toFile(), "Alice -> Bob: CONTENT_OF_AN_INCLUDED_FILE\n", StandardCharsets.UTF_8);

        String svg = renderSvg("@startuml\n!include " + included.toAbsolutePath() + "\n@enduml");

        assertThat(svg).as("an included file's content must not reach the rendered diagram").doesNotContain("CONTENT_OF_AN_INCLUDED_FILE");
    }

    @Test
    void anOrdinaryDiagramStillRenders() {
        loadServiceClass();

        String svg = renderSvg("@startuml\n!pragma layout smetana\nAlice -> Bob: hello\n@enduml");

        assertThat(svg).contains("hello");
    }

    private static String renderSvg(String source) {
        try (var out = new java.io.ByteArrayOutputStream()) {
            new net.sourceforge.plantuml.SourceStringReader(source).outputImage(out, new net.sourceforge.plantuml.FileFormatOption(net.sourceforge.plantuml.FileFormat.SVG));
            return out.toString(java.nio.charset.StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            throw new AssertionError(e);
        }
    }
}
