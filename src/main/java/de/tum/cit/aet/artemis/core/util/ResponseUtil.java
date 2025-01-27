package de.tum.cit.aet.artemis.core.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

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
     * Sends an OK response entity that contains a file. Returns a not found response
     * if the file doesn't exist.
     *
     * @param file the file to send as a response
     * @return the response
     */
    public static ResponseEntity<Resource> ok(File file) {
        try {
            InputStreamResource resource = new InputStreamResource(Files.newInputStream(file.toPath()));
            return ResponseEntity.ok().contentLength(file.length()).contentType(MediaType.APPLICATION_OCTET_STREAM).header("filename", file.getName()).body(resource);
        }
        catch (IOException e) {
            throw new EntityNotFoundException("File not found");
        }
    }
}
