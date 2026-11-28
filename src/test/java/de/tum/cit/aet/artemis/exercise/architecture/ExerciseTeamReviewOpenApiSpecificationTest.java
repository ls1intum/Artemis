package de.tum.cit.aet.artemis.exercise.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import tools.jackson.dataformat.yaml.YAMLMapper;

@Tag("ArchitectureTest")
class ExerciseTeamReviewOpenApiSpecificationTest {

    @Test
    void applicationSpecificationExposesExactlyTheExerciseTeamAndReviewOperations() throws IOException {
        var specification = YAMLMapper.builder().build().readTree(Path.of("config", "openapi", "openapi.yaml").toFile());
        String prefix = "/api/exercise/";
        var operations = new ArrayList<String>();
        specification.path("paths").properties().stream().filter(entry -> entry.getKey().startsWith(prefix)).forEach(entry -> {
            String path = entry.getKey().substring(prefix.length());
            entry.getValue().propertyNames().forEach(method -> operations.add(method + " " + path));
        });
        assertThat(operations).containsExactlyInAnyOrder("get courses/{courseId}/exercises/{exerciseId}/team-search-users",
                "get courses/{courseId}/teams/{teamShortName}/with-exercises-and-participations",
                "get courses/{courseId}/teams/{teamShortName}/with-exercises-and-participations/for-staff", "get courses/{courseId}/teams/exists",
                "delete exercises/{exerciseId}/review-comments/{commentId}", "put exercises/{exerciseId}/review-comments/{commentId}",
                "put exercises/{exerciseId}/review-comments/{commentId}/inline-fix/applied", "post exercises/{exerciseId}/review-thread-groups",
                "delete exercises/{exerciseId}/review-thread-groups/{groupId}", "put exercises/{exerciseId}/review-thread-groups/{groupId}/resolved",
                "get exercises/{exerciseId}/review-threads", "post exercises/{exerciseId}/review-threads", "post exercises/{exerciseId}/review-threads/{threadId}/comments",
                "put exercises/{exerciseId}/review-threads/{threadId}/resolved", "get exercises/{exerciseId}/teams", "post exercises/{exerciseId}/teams",
                "delete exercises/{exerciseId}/teams/{teamId}", "get exercises/{exerciseId}/teams/{teamId}", "put exercises/{exerciseId}/teams/{teamId}",
                "put exercises/{exerciseId}/teams/import-from-exercise", "put exercises/{exerciseId}/teams/import-from-list");

        var schemas = specification.path("components").path("schemas");
        var discriminator = schemas.path("CommentContent").path("discriminator");
        assertThat(discriminator.path("propertyName").asString()).isEqualTo("contentType");
        var mapping = discriminator.path("mapping");
        assertThat(mapping.size()).isEqualTo(2);
        assertThat(mapping.path("USER").asString()).isEqualTo("#/components/schemas/UserCommentContent");
        assertThat(mapping.path("CONSISTENCY_CHECK").asString()).isEqualTo("#/components/schemas/ConsistencyIssueCommentContent");
    }
}
