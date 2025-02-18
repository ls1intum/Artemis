package de.tum.cit.aet.artemis.core.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;

/**
 * Deprecation: please throw exceptions instead of using the below methods,
 * use e.g. AccessForbiddenException, EntityNotFoundException, BadRequestAlertException, ConflictException
 */
public final class ResponseUtil implements tech.jhipster.web.util.ResponseUtil {

    /**
     * Sends an OK response entity that contains a filepath. Returns a not found response
     * if the filepath doesn't exist.
     *
     * @param filepath the path to the file to send as a response
     * @return the response
     */
    public static ResponseEntity<Resource> ok(Path filepath) {
        try {
            InputStreamResource resource = new InputStreamResource(Files.newInputStream(filepath));
            return ResponseEntity.ok().contentLength(Files.size(filepath)).contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header("filename", String.valueOf(filepath.getFileName())).body(resource);
        }
        catch (IOException e) {
            throw new EntityNotFoundException("File not found");
        }
    }
}
