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

    private static final String FILE_UPLOAD_SEARCH = "/api/fileupload/file-upload-exercises";

    private static final String FILE_UPLOAD_SUBMISSION_WITHOUT_ASSESSMENT = "/api/fileupload/exercises/{exerciseId}/file-upload-submission-without-assessment";

    private static final String FILE_UPLOAD_SUBMISSION_CREATION = "/api/fileupload/exercises/{exerciseId}/file-upload-submissions";

    private static JsonNode specification;

    @BeforeAll
    static void readSpecification() throws IOException {
        specification = YAMLMapper.builder().build().readTree(APPLICATION_SPECIFICATION.toFile());
    }

    /**
     * The whole {@code fileupload.web} package is scanned, without a path exclusion.
     */
    @Test
    void applicationSpecificationExposesExactlyTheFileUploadOperations() {
        assertThat(operationsWithPrefix("/api/fileupload/")).containsExactlyInAnyOrder("post /api/fileupload/file-upload-exercises",
                "post /api/fileupload/file-upload-exercises/import", "get /api/fileupload/file-upload-exercises", "put /api/fileupload/file-upload-exercises/{exerciseId}",
                "get /api/fileupload/courses/{courseId}/file-upload-exercises", "get /api/fileupload/file-upload-exercises/{exerciseId}",
                "get /api/fileupload/file-upload-exercises/{exerciseId}/for-exam", "delete /api/fileupload/file-upload-exercises/{exerciseId}",
                "post /api/fileupload/file-upload-exercises/{exerciseId}/export-submissions", "get /api/fileupload/file-upload-exercises/{exerciseId}/export",
                "put /api/fileupload/file-upload-exercises/{exerciseId}/re-evaluate", "post /api/fileupload/exercises/{exerciseId}/file-upload-submissions",
                "get /api/fileupload/file-upload-submissions/{submissionId}", "get /api/fileupload/exercises/{exerciseId}/file-upload-submissions",
                "get /api/fileupload/exercises/{exerciseId}/file-upload-submissions/assessed-by-me",
                "get /api/fileupload/exercises/{exerciseId}/file-upload-submission-without-assessment", "get /api/fileupload/participations/{participationId}/file-upload-editor",
                "get /api/fileupload/participations/{participationId}/file-upload-editor/for-staff", "get /api/fileupload/file-upload-submissions/{submissionId}/result",
                "get /api/fileupload/file-upload-submissions/{submissionId}/result/for-instructor", "put /api/fileupload/file-upload-submissions/{submissionId}/feedback",
                "put /api/fileupload/file-upload-submissions/{submissionId}/assessment-after-complaint",
                "put /api/fileupload/file-upload-submissions/{submissionId}/cancel-assessment",
                "delete /api/fileupload/participations/{participationId}/file-upload-submissions/{submissionId}/results/{resultId}");
    }

    /**
     * The generic context names are reserved for the Course, Exercise and Exam migrations, and the course-management union for the Course migration.
     */
    @Test
    void applicationSpecificationDoesNotClaimReservedSchemaNames() {
        JsonNode schemas = specification.path("components").path("schemas");
        assertThat(List.of("CourseContext", "ExerciseGroupContext", "ExamContext", "CourseManagementExercise")).noneMatch(schemas::has);
    }

    @Test
    void fileUploadSearchTakesExplicitPagingParameters() {
        List<String> parameters = new ArrayList<>();
        specification.path("paths").path(FILE_UPLOAD_SEARCH).path("get").path("parameters").forEach(parameter -> parameters.add(parameter.path("name").asString()));
        assertThat(parameters).containsExactlyInAnyOrder("page", "pageSize", "sortingOrder", "sortedColumn", "searchTerm", "isCourseFilter", "isExamFilter");
    }

    /**
     * The server answers {@code 200} with an empty body when no submission is eligible, see {@code @NullableSuccessBody}.
     */
    @Test
    void fileUploadSubmissionWithoutAssessmentMayHaveAnEmptyBody() {
        JsonNode branches = specification.path("paths").path(FILE_UPLOAD_SUBMISSION_WITHOUT_ASSESSMENT).path("get").path("responses").path("200").path("content")
                .path("application/json").path("schema").path("oneOf");
        List<String> descriptions = new ArrayList<>();
        branches.forEach(branch -> descriptions.add(branch.has("$ref") ? branch.path("$ref").asString() : branch.path("type").asString()));
        assertThat(descriptions).containsExactly("#/components/schemas/FileUploadSubmission", "null");
    }

    @Test
    void fileUploadSubmissionIsAMultipartRequestWithASubmissionAndAFile() {
        JsonNode schema = specification.path("paths").path(FILE_UPLOAD_SUBMISSION_CREATION).path("post").path("requestBody").path("content").path("multipart/form-data")
                .path("schema");
        assertThat(schema.path("properties").path("submission").path("$ref").asString()).isEqualTo("#/components/schemas/FileUploadSubmissionInput");
        assertThat(schema.path("properties").path("file").path("format").asString()).isEqualTo("binary");
        List<String> required = new ArrayList<>();
        schema.path("required").forEach(name -> required.add(name.asString()));
        assertThat(required).containsExactlyInAnyOrder("submission", "file");
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
