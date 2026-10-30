package de.tum.cit.aet.artemis.iris.dto;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.databind.annotation.JsonDeserialize;

import de.tum.cit.aet.artemis.core.config.StrictStringDeserializer;

/**
 * One question of a multiple-choice question set in the JSON content of an Iris message. The text fields are read strictly, so a value of another JSON type fails the read
 * instead of being coerced.
 *
 * @param type        the type Iris tagged the question with, usually {@code mcq}
 * @param question    the text of the question
 * @param options     the answer options
 * @param explanation the explanation shown once the question is answered
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisMcqQuestionDTO(@Nullable String type, @Nullable @JsonDeserialize(using = StrictStringDeserializer.class) String question,
        @Nullable List<IrisMcqOptionDTO> options, @Nullable @JsonDeserialize(using = StrictStringDeserializer.class) String explanation) {
}
