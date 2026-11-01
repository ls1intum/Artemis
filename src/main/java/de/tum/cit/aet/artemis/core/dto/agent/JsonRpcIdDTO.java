package de.tum.cit.aet.artemis.core.dto.agent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The id of a JSON-RPC request, which the answer repeats. JSON-RPC allows a number or a string, and a client compares the answer with
 * what it sent, so the kind has to survive the round trip.
 *
 * @param number the id, if the client sent a number
 * @param text   the id, if the client sent a string
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record JsonRpcIdDTO(Long number, String text) {

    /**
     * Reads a numeric id.
     *
     * @param number the id the client sent
     * @return the id
     */
    @JsonCreator
    public static JsonRpcIdDTO ofNumber(long number) {
        return new JsonRpcIdDTO(number, null);
    }

    /**
     * Reads a text id.
     *
     * @param text the id the client sent
     * @return the id
     */
    @JsonCreator
    public static JsonRpcIdDTO ofText(String text) {
        return new JsonRpcIdDTO(null, text);
    }

    /**
     * The id as it is written into the answer: a number or a string, never an object.
     *
     * @return the number if there is one, else the text
     */
    @JsonValue
    public Object toJson() {
        return number != null ? number : text;
    }
}
