package de.tum.cit.aet.artemis.iris.dto;

import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.databind.annotation.JsonDeserialize;

import de.tum.cit.aet.artemis.core.config.StrictStringDeserializer;

/**
 * The JSON content of an Iris message that carries multiple-choice questions, as the Iris client reads it. It is either a single question ({@code type} is {@code mcq}, with
 * {@code question}, {@code options}, {@code explanation} and the student's {@code response}) or a set of questions ({@code type} is {@code mcq-set}, with {@code questions}
 * and the student's {@code responses}). The text fields are read strictly, so a value of another JSON type fails the read instead of being coerced.
 *
 * @param type                 {@code mcq} or {@code mcq-set}
 * @param question             the text of the question; only for a single question
 * @param options              the answer options; only for a single question
 * @param explanation          the explanation shown once the question is answered; only for a single question
 * @param response             the student's answer; only for a single question
 * @param questions            the questions of the set; only for a set
 * @param responses            the student's answers, one per answered question of the set; only for a set
 * @param additionalProperties extension fields preserved when an answer is saved
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisMcqContentDTO(@Nullable String type, @Nullable @JsonDeserialize(using = StrictStringDeserializer.class) String question, @Nullable List<IrisMcqOptionDTO> options,
        @Nullable @JsonDeserialize(using = StrictStringDeserializer.class) String explanation, @Nullable IrisMcqStoredResponseDTO response,
        @Nullable List<IrisMcqQuestionDTO> questions, @Nullable List<IrisMcqStoredResponseDTO> responses,
        @Nullable @JsonAnySetter @JsonAnyGetter Map<String, Object> additionalProperties) {
}
