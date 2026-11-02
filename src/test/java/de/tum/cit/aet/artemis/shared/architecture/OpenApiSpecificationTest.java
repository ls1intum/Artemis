package de.tum.cit.aet.artemis.shared.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * Guards the generated application contract's deliberately limited Atlas migration slice.
 * Agent coverage is checked independently against every {@code @AllowedScopes} endpoint in {@link AllowedScopesArchitectureTest}.
 */
@Tag("ArchitectureTest")
class OpenApiSpecificationTest {

    @Test
    void applicationSpecificationExposesOnlyTheThreeStandardizedCompetencyReadsInAtlas() throws IOException {
        var paths = YAMLMapper.builder().build().readTree(Path.of("config", "openapi", "openapi.yaml").toFile()).path("paths");
        List<String> atlasOperations = new ArrayList<>();
        List<String> httpMethods = List.of("get", "put", "post", "patch", "delete", "head", "options", "trace");
        for (var path : paths.properties()) {
            if (path.getKey().startsWith("/api/atlas/")) {
                for (String method : httpMethods) {
                    if (path.getValue().has(method)) {
                        atlasOperations.add(method + " " + path.getKey());
                    }
                }
            }
        }

        assertThat(atlasOperations).containsExactlyInAnyOrder("get /api/atlas/standardized-competencies/{competencyId}",
                "get /api/atlas/standardized-competencies/for-tree-view", "get /api/atlas/standardized-competencies/sources");
    }
}
