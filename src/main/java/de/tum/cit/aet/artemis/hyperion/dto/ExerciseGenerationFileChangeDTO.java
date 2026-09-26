package de.tum.cit.aet.artemis.hyperion.dto;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/** A lightweight notification that the generation agent changed a file. */
@Schema(description = "A file changed by the generation agent")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseGenerationFileChangeDTO(
        @Schema(description = "Constant discriminator identifying a file change on the shared topic", allowableValues = TYPE, requiredMode = Schema.RequiredMode.REQUIRED) String type,
        @Schema(description = "Workspace-relative file path", requiredMode = Schema.RequiredMode.REQUIRED) String path,
        @Schema(description = "Owning repository bucket", allowableValues = {
                REPOSITORY_SOLUTION, REPOSITORY_TEMPLATE, REPOSITORY_TESTS, REPOSITORY_OTHER }, requiredMode = Schema.RequiredMode.REQUIRED) String repo,
        @Schema(description = "The successful file tool operation", allowableValues = { ACTION_WRITE, ACTION_EDIT,
                ACTION_DELETE }, requiredMode = Schema.RequiredMode.REQUIRED) String action,
        @Schema(description = "The agent turn the change happened on", requiredMode = Schema.RequiredMode.REQUIRED) int turn,
        @Schema(description = "The moment the change was produced", requiredMode = Schema.RequiredMode.REQUIRED) Instant timestamp) implements Serializable{

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String TYPE = "FILE_CHANGE";

    public static final String REPOSITORY_SOLUTION = "solution";

    public static final String REPOSITORY_TEMPLATE = "template";

    public static final String REPOSITORY_TESTS = "tests";

    public static final String REPOSITORY_OTHER = "other";

    public static final String ACTION_WRITE = "write";

    public static final String ACTION_EDIT = "edit";

    public static final String ACTION_DELETE = "delete";

    public static ExerciseGenerationFileChangeDTO of(String path, String action, int turn) {
        return new ExerciseGenerationFileChangeDTO(TYPE, path, repositoryBucket(path), action, turn, Instant.now());
    }

    static String repositoryBucket(String path) {
        if (path.startsWith("solution/")) {
            return REPOSITORY_SOLUTION;
        }
        if (path.startsWith("template/")) {
            return REPOSITORY_TEMPLATE;
        }
        if (path.startsWith("tests/")) {
            return REPOSITORY_TESTS;
        }
        return REPOSITORY_OTHER;
    }
}
