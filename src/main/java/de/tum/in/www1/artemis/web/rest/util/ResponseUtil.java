package de.tum.in.www1.artemis.web.rest.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Deprecation: please throw exceptions instead of using the below methods,
 * use e.g. AccessForbiddenException, EntityNotFoundException, BadRequestAlertException, ConflictException
 */
public final class ResponseUtil implements tech.jhipster.web.util.ResponseUtil {

    /**
     * Sends an OK response entity that contains a file. Returns a not found response
     * if the file doesn't exist.
     * @param file the file to send as a response
     * @return the response
     */
    public static ResponseEntity<Resource> ok(File file) {
        try {
            InputStreamResource resource = new InputStreamResource(new FileInputStream(file));
            return ResponseEntity.ok().contentLength(file.length()).contentType(MediaType.APPLICATION_OCTET_STREAM).header("filename", file.getName()).body(resource);
        }
        catch (FileNotFoundException e) {
            throw new EntityNotFoundException("File not found");
        }
    }
}
