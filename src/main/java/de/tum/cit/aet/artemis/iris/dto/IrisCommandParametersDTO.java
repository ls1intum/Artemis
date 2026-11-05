package de.tum.cit.aet.artemis.iris.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.databind.annotation.JsonDeserialize;

import de.tum.cit.aet.artemis.core.config.NumberOrNaNDeserializer;

/**
 * The parameters of a command Iris performs on the client, as Pyris sends them, as Artemis pushes them to the client and as it records them in a COMMAND marker. Point-out is
 * the only command type Artemis implements, so these are its parameters; a parameter Pyris sends that is not listed here is dropped.
 * <p>
 * The numeric parameters come from a model, so they are not trusted: a value that is not a number, including an explicit JSON {@code null}, is read as {@link Double#NaN}, which
 * no validity check accepts and which differs from an absent parameter ({@code null}). That way the command is rejected as not applicable instead of failing the request.
 *
 * @param lectureUnitId   the lecture unit to navigate in
 * @param page            the index of the slide to navigate to, counted from the start of the deck
 * @param displayPage     the number printed on that slide, a label only
 * @param timestamp       the position in the video to seek to, in seconds
 * @param materialType    {@code attachment} or {@code video}: the kind of material the position was generated from, stamped by Artemis
 * @param materialVersion the version of that material, stamped by Artemis; 0 if it could not be verified
 * @param lectureUnitName the name of the lecture unit, only in markers
 * @param lectureId       the id of the lecture the unit belongs to, only in markers
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisCommandParametersDTO(@Nullable @JsonDeserialize(using = NumberOrNaNDeserializer.class) Number lectureUnitId,
        @Nullable @JsonDeserialize(using = NumberOrNaNDeserializer.class) Number page, @Nullable @JsonDeserialize(using = NumberOrNaNDeserializer.class) Number displayPage,
        @Nullable @JsonDeserialize(using = NumberOrNaNDeserializer.class) Number timestamp, @Nullable String materialType,
        @Nullable @JsonDeserialize(using = NumberOrNaNDeserializer.class) Number materialVersion, @Nullable String lectureUnitName,
        @Nullable @JsonDeserialize(using = NumberOrNaNDeserializer.class) Number lectureId) {
}
