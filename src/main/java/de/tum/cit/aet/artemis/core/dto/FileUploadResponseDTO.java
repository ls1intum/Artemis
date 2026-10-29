package de.tum.cit.aet.artemis.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The public path of an uploaded file.
 *
 * @param path the path clients can use to retrieve the file
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FileUploadResponseDTO(String path) {
}
