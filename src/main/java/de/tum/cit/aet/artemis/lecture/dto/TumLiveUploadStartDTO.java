package de.tum.cit.aet.artemis.lecture.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Where the editor's browser sends the recording of a unit.
 *
 * @param ref       the upload's reference, which the browser sends back to discard it
 * @param uploadUrl the same-origin Artemis URL to send the file to
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TumLiveUploadStartDTO(String ref, String uploadUrl) {

    @Override
    public String toString() {
        return "TumLiveUploadStartDTO[ref=" + ref + ", uploadUrl=[REDACTED]]";
    }
}
