package de.tum.cit.aet.artemis.iris.service.pyris.dto.chat;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * A command Iris performs on the client alongside a chat answer, such as pointing the student to a position in the lecture combined view.
 * <p>
 * The wire format is intentionally open: {@code type} identifies the command and {@code parameters} carries its command-specific data as raw JSON, so a new command needs no Java
 * subtype. Artemis still only executes the types it knows — {@link de.tum.cit.aet.artemis.iris.service.pyris.IrisCommandService} switches on the type and drops anything without a
 * case — so a new type means a case there plus the client code that carries it out.
 *
 * @param type       the type identifier for this command (e.g. "pointOut")
 * @param parameters the command-specific parameters, never {@code null}
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisCommandDTO(String type, Map<String, JsonNode> parameters) {

    public PyrisCommandDTO {
        parameters = parameters != null ? Map.copyOf(parameters) : Map.of();
    }
}
