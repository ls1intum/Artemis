package de.tum.in.www1.artemis.web.rest.localci;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
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

    @GetMapping("/build-log/{resultId}")
    @EnforceAtLeastEditor
    public ResponseEntity<String> getBuildLogForSubmission(@PathVariable long resultId) {
        log.debug("REST request to get the build log for result {}", resultId);
        HttpHeaders responseHeaders = new HttpHeaders();
        String buildLog = buildLogEntryService.retrieveBuildLogsFromFileForResult(String.valueOf(resultId));
        responseHeaders.setContentType(MediaType.TEXT_PLAIN);
        return new ResponseEntity<>(buildLog, responseHeaders, HttpStatus.OK);
    }
}
