package de.tum.cit.aet.artemis.iris.dto;

import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.domain.message.IrisJsonMessageContent;
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
        return message.getContent().stream().filter(content -> TYPE.equals(IrisMessageContentTypeDTO.typeOf(content)))
                .map(content -> ((IrisJsonMessageContent) content).readAs(IrisSolutionOfferDTO.class)).findFirst();
    }

    /** Whether this offer is the one for the complete solution, rather than the check after it. */
    public boolean offersSolution() {
        return choices.contains(IrisSolutionChoice.SHOW_SOLUTION);
    }
}
