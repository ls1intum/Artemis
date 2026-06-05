package de.tum.cit.aet.artemis.text.dto;

/**
 * Input DTO mirroring the {@code ComplaintResponse} wire shape the client sends when updating a text assessment after a
 * complaint. Only the fields the server needs are captured (the lock id, the response text and the complaint resolution
 * decision); the controller reconstructs a transient {@code ComplaintResponse} from these for the shared
 * assessment-update logic.
 *
 * @param id           the id of the (locked) complaint response
 * @param responseText the tutor's response text
 * @param complaint    the complaint carrying the accept/reject decision
 */
public record ComplaintResponseRequestDTO(Long id, String responseText, ComplaintRequestDTO complaint) {

    /**
     * The nested complaint shape carrying the resolution decision.
     *
     * @param id       the complaint id
     * @param accepted whether the complaint was accepted
     */
    public record ComplaintRequestDTO(Long id, Boolean accepted) {
    }
}
