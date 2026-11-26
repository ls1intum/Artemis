package de.tum.cit.aet.artemis.iris.dto;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisSolutionChoice;

/**
 * Server-created content of the user message answering a solution offer. Artemis writes it only after validating the offer and never
 * accepts it from a client, so a stored choice can be trusted as is.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisSolutionChoiceDTO(String type, long offerMessageId, IrisSolutionChoice choice) {

    public static final String TYPE = "solution-choice";

    public IrisSolutionChoiceDTO(long offerMessageId, IrisSolutionChoice choice) {
        this(TYPE, offerMessageId, choice);
    }

    /**
     * Reads the choice a message records.
     *
     * @param message any message
     * @return the recorded choice, or empty if the message records none
     */
    public static Optional<IrisSolutionChoice> in(IrisMessage message) {
        return IrisMessageContentTypeDTO.readFirst(message, TYPE, IrisSolutionChoiceDTO.class).map(IrisSolutionChoiceDTO::choice);
    }
}
