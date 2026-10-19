package de.tum.cit.aet.artemis.hyperion.dto;

import java.io.Serial;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/** One text file of a generated candidate that was never saved to the exercise, retained so the run's work stays inspectable. */
@Schema(description = "A file of a generated candidate that was not saved to the exercise")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseGenerationRetainedFileDTO(@Schema(description = "Owning repository bucket", allowableValues = {
        ExerciseGenerationFileChangeDTO.REPOSITORY_SOLUTION, ExerciseGenerationFileChangeDTO.REPOSITORY_TEMPLATE,
        ExerciseGenerationFileChangeDTO.REPOSITORY_TESTS }, requiredMode = Schema.RequiredMode.REQUIRED) String repo,
        @Schema(description = "Repository-relative file path", requiredMode = Schema.RequiredMode.REQUIRED) String path,
        @JsonInclude @Schema(description = "The file's text content as the agent left it", requiredMode = Schema.RequiredMode.REQUIRED) String content) implements Serializable{

    @Serial
    private static final long serialVersionUID = 1L;
}
