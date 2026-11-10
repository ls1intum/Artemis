package de.tum.cit.aet.artemis.shared.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * Guards the slices of the committed application specification ({@code config/openapi/openapi.yaml}) that are deliberately limited, so that a regeneration
 * that scans more, or less, than intended fails here instead of silently changing the generated client.
 */
@Tag("ArchitectureTest")
class OpenApiSpecificationTest {

    private static final Path APPLICATION_SPECIFICATION = Path.of("config", "openapi", "openapi.yaml");

    private static final List<String> HTTP_METHODS = List.of("get", "put", "post", "patch", "delete", "head", "options", "trace");

    private static JsonNode specification;

    @BeforeAll
    static void readSpecification() throws IOException {
        specification = YAMLMapper.builder().build().readTree(APPLICATION_SPECIFICATION.toFile());
    }

    /**
     * The plagiarism posts and answer posts share the scanned {@code plagiarism.web} package but stay on the handwritten communication client until the
     * Communication migration, so {@code gradle/openapi.gradle} keeps their paths out of the application specification.
     */
    @Test
    void applicationSpecificationExposesExactlyThePlagiarismCaseAndComparisonOperations() {
        assertThat(operationsWithPrefix("/api/plagiarism/")).containsExactlyInAnyOrder("put /api/plagiarism/courses/{courseId}/plagiarism-comparisons/{comparisonId}/status",
                "get /api/plagiarism/courses/{courseId}/plagiarism-comparisons/{comparisonId}/for-split-view",
                "delete /api/plagiarism/exercises/{exerciseId}/plagiarism-results/{plagiarismResultId}/plagiarism-comparisons",
                "get /api/plagiarism/exercises/{exerciseId}/potential-plagiarism-count", "get /api/plagiarism/courses/{courseId}/plagiarism-cases/for-scores",
                "get /api/plagiarism/courses/{courseId}/plagiarism-cases/for-instructor", "get /api/plagiarism/courses/{courseId}/exams/{examId}/plagiarism-cases/for-instructor",
                "get /api/plagiarism/courses/{courseId}/plagiarism-cases/{plagiarismCaseId}/for-instructor",
                "get /api/plagiarism/courses/{courseId}/exercises/{exerciseId}/plagiarism-cases-count",
                "put /api/plagiarism/courses/{courseId}/plagiarism-cases/{plagiarismCaseId}/verdict",
                "get /api/plagiarism/courses/{courseId}/exercises/{exerciseId}/plagiarism-case", "get /api/plagiarism/courses/{courseId}/plagiarism-cases",
                "get /api/plagiarism/courses/{courseId}/plagiarism-cases/{plagiarismCaseId}/for-student");
    }

    @Test
    void applicationSpecificationExcludesThePlagiarismPostsAndAnswerPosts() {
        List<String> postPaths = new ArrayList<>();
        specification.path("paths").properties().forEach(path -> {
            if (path.getKey().startsWith("/api/plagiarism/") && (path.getKey().contains("/posts") || path.getKey().contains("/answer-posts"))) {
                postPaths.add(path.getKey());
            }
        });
        assertThat(postPaths).isEmpty();
    }

    private static List<String> operationsWithPrefix(String prefix) {
        List<String> operations = new ArrayList<>();
        specification.path("paths").properties().forEach(path -> {
            if (path.getKey().startsWith(prefix)) {
                for (String method : HTTP_METHODS) {
                    if (path.getValue().has(method)) {
                        operations.add(method + " " + path.getKey());
                    }
                }
            }
        });
        return operations;
    }
}
