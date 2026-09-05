package de.tum.cit.aet.artemis.videosource.web;

import java.net.URI;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceNothing;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.videosource.dto.GocastApprovalResultDTO;
import de.tum.cit.aet.artemis.videosource.service.GocastBindingConflictException;
import de.tum.cit.aet.artemis.videosource.service.GocastBindingService;
import de.tum.cit.aet.artemis.videosource.service.GocastIntegrationException;

@Lazy
@FeatureUsage("video/tum-live")
@RestController
@RequestMapping("api/videosource/public/gocast/approval/")
public class GocastApprovalCallbackResource {

    private static final String SUCCESS_PAGE = "<!doctype html><html lang=\"en\"><meta name=\"referrer\" content=\"no-referrer\"><title>TUM.Live connection</title><body><main><h1>Connection completed</h1><p>You can close this page and return to Artemis.</p></main></body></html>";

    private static final String RETRY_PAGE = "<!doctype html><html lang=\"en\"><meta name=\"referrer\" content=\"no-referrer\"><title>TUM.Live connection</title><body><main><h1>Connection not completed</h1><p>Return to Artemis and start the connection again.</p></main></body></html>";

    private final Optional<GocastBindingService> bindingService;

    private final AuthorizationCheckService authorizationCheckService;

    public GocastApprovalCallbackResource(Optional<GocastBindingService> bindingService, AuthorizationCheckService authorizationCheckService) {
        this.bindingService = bindingService;
        this.authorizationCheckService = authorizationCheckService;
    }

    /**
     * Completes a saved approval without trusting browser-supplied course data.
     *
     * @param state     the opaque browser state
     * @param requestId the remote request identifier
     * @param code      the single-use redeem code
     * @return a private redirect for the course instructor or a generic result page
     */
    @GetMapping("callback")
    @EnforceNothing
    public ResponseEntity<String> completeApproval(@RequestParam(required = false) String state, @RequestParam(required = false) String requestId,
            @RequestParam(required = false) String code) {
        HttpHeaders headers = responseHeaders();
        GocastApprovalResultDTO result;
        if (!StringUtils.hasText(state) || !StringUtils.hasText(requestId) || !StringUtils.hasText(code)) {
            result = new GocastApprovalResultDTO(false, null);
        }
        else {
            try {
                result = bindingService.map(service -> service.completeApproval(requestId, state, code)).orElseGet(() -> new GocastApprovalResultDTO(false, null));
            }
            catch (GocastIntegrationException | GocastBindingConflictException | IllegalArgumentException exception) {
                result = new GocastApprovalResultDTO(false, null);
            }
        }
        if (result.completed() && result.artemisCourseId() != null && SecurityUtils.getCurrentUserLogin().isPresent()
                && authorizationCheckService.isAtLeastInstructorInCourse(result.artemisCourseId())) {
            headers.setLocation(URI.create("/course-management/" + result.artemisCourseId() + "/gocast-binding"));
            return ResponseEntity.status(303).headers(headers).build();
        }
        return ResponseEntity.ok().headers(headers).contentType(MediaType.TEXT_HTML).body(result.completed() ? SUCCESS_PAGE : RETRY_PAGE);
    }

    private static HttpHeaders responseHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl(CacheControl.noStore());
        headers.set("Referrer-Policy", "no-referrer");
        return headers;
    }
}
