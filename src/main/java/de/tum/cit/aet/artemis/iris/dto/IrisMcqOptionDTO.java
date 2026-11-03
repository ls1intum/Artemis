package de.tum.cit.aet.artemis.iris.dto;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.databind.annotation.JsonDeserialize;

import de.tum.cit.aet.artemis.core.config.StrictBooleanDeserializer;
import de.tum.cit.aet.artemis.core.config.StrictStringDeserializer;

/**
 * One answer option of a multiple-choice question in the JSON content of an Iris message. The fields are read strictly, so a value of another JSON type fails the read instead
 * of being coerced.
 *
 * @param text                 the text of the option
 * @param correct              whether the option is the correct answer
 * @param additionalProperties extension fields preserved when an answer is saved
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisMcqOptionDTO(@Nullable @JsonDeserialize(using = StrictStringDeserializer.class) String text,
        @Nullable @JsonDeserialize(using = StrictBooleanDeserializer.class) Boolean correct, @Nullable @JsonAnySetter @JsonAnyGetter Map<String, Object> additionalProperties) {
}
