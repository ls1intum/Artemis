package de.tum.cit.aet.artemis.iris.service.pyris.dto.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.dto.IrisCommandParametersDTO;

/**
 * A command Iris performs on the client alongside a chat answer, such as pointing the student to a position in the lecture combined view.
 * <p>
 * {@code type} identifies the command and {@code parameters} carries its parameters. Artemis only executes the types it knows — {@code IrisCommandService} switches on the type
 * and drops anything without a case — so a new type means a case there, parameters for it, plus the client code that carries it out. Point-out is the only type so far.
 *
 * @param type       the type identifier for this command (e.g. "pointOut")
 * @param parameters the command-specific parameters, never {@code null}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisCommandDTO(String type, IrisCommandParametersDTO parameters) {

    public PyrisCommandDTO {
        parameters = parameters != null ? parameters : new IrisCommandParametersDTO(null, null, null, null, null, null, null, null);
    }
}
