package de.tum.cit.aet.artemis.modeling.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Input DTO mirroring the {@code ComplaintResponse} wire shape the client sends when updating a modeling assessment after a
 * complaint. Only the fields the server needs are captured (the lock id, the response text and the complaint resolution
 * decision); the controller reconstructs a transient {@code ComplaintResponse} from these for the shared
 * assessment-update logic.
 * <p>
 * Bare {@code @JsonInclude} (no explicit value, i.e. Jackson's ALWAYS default) is used rather than {@code NON_EMPTY}: this is a request
 * body and must round-trip empty/blank values unchanged.
 *
 * @param id           the id of the (locked) complaint response
 * @param responseText the tutor's response text
 * @param complaint    the complaint carrying the accept/reject decision
 */
@JsonInclude
public record ComplaintResponseRequestDTO(Long id, String responseText, ComplaintRequestDTO complaint) {

    /**
     * The nested complaint shape carrying the resolution decision.
     *
     * @param id       the complaint id
     * @param accepted whether the complaint was accepted
     */
    @JsonInclude
    public record ComplaintRequestDTO(Long id, Boolean accepted) {
    }
}
