package de.tum.cit.aet.artemis.iris.dto;

import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisSolutionChoice;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode;

/**
 * Structured assistant content offering a choice of the solution workflow, bound to the context in which it was made.
 *
 * @param choices the buttons in order; a choice of this offer must be one of them
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisSolutionOfferDTO(String type, IrisChatMode chatMode, long entityId, List<IrisSolutionChoice> choices) {

    public static final String TYPE = "solution-offer";

    /**
     * Reads the offer a message makes.
     *
     * @param message any message
     * @return the offer, or empty if the message makes none
     */
    public static Optional<IrisSolutionOfferDTO> in(IrisMessage message) {
        return IrisMessageContentTypeDTO.readFirst(message, TYPE, IrisSolutionOfferDTO.class);
    }

    /**
     * Whether a choice answers this offer; only such a choice may be stored, because the next step does not check a stored choice against its offer again.
     *
     * @param choice the choice of the student
     * @return true if the offer has a button for it
     */
    public boolean offers(IrisSolutionChoice choice) {
        return choices.contains(choice);
    }

    /** Whether this offer is the one for the complete solution, rather than the check after it. */
    public boolean offersSolution() {
        return offers(IrisSolutionChoice.SHOW_SOLUTION);
    }
}
