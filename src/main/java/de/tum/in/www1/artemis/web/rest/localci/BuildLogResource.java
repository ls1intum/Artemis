package de.tum.in.www1.artemis.web.rest.localci;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastEditor;
import de.tum.in.www1.artemis.service.BuildLogEntryService;

@Profile("localci")
@RestController
@RequestMapping("/api")
public class BuildLogResource {

    private static final Logger log = LoggerFactory.getLogger(BuildLogResource.class);

    private final BuildLogEntryService buildLogEntryService;

    public BuildLogResource(BuildLogEntryService buildLogEntryService) {
        this.buildLogEntryService = buildLogEntryService;
    }

    /**
     * GET /build-log/{resultId} : get the build log for a given result
     *
     * @param resultId the id of the result for which to retrieve the build log
     * @return the ResponseEntity with status 200 (OK) and the build log in the body, or with status 404 (Not Found) if the build log could not be found
     */
    @GetMapping("/build-log/{resultId}")
    @EnforceAtLeastEditor
    public ResponseEntity<Resource> getBuildLogForSubmission(@PathVariable long resultId) {
        log.debug("REST request to get the build log for result {}", resultId);
        HttpHeaders responseHeaders = new HttpHeaders();
        FileSystemResource buildLog = buildLogEntryService.retrieveBuildLogsFromFileForResult(String.valueOf(resultId));
        if (buildLog == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        responseHeaders.setContentType(MediaType.TEXT_PLAIN);
        responseHeaders.setContentDispositionFormData("attachment", "build-" + resultId + ".log");
        return new ResponseEntity<>(buildLog, responseHeaders, HttpStatus.OK);
    }
}
