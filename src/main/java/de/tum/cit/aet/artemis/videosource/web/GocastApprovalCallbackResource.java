package de.tum.cit.aet.artemis.videosource.web;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

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

    private final Optional<GocastBindingService> bindingService;

    private final AuthorizationCheckService authorizationCheckService;

    private final MessageSource messageSource;

    public GocastApprovalCallbackResource(Optional<GocastBindingService> bindingService, AuthorizationCheckService authorizationCheckService, MessageSource messageSource) {
        this.bindingService = bindingService;
        this.authorizationCheckService = authorizationCheckService;
        this.messageSource = messageSource;
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
        return ResponseEntity.ok().headers(headers).contentType(MediaType.TEXT_HTML).body(resultPage(result.completed()));
    }

    private static HttpHeaders responseHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl(CacheControl.noStore());
        headers.set("Referrer-Policy", "no-referrer");
        return headers;
    }

    private String resultPage(boolean completed) {
        Locale locale = "de".equals(LocaleContextHolder.getLocale().getLanguage()) ? Locale.GERMAN : Locale.ENGLISH;
        String resultKey = completed ? "success" : "retry";
        String title = HtmlUtils.htmlEscape(messageSource.getMessage("gocast.callback.title", null, locale));
        String heading = HtmlUtils.htmlEscape(messageSource.getMessage("gocast.callback." + resultKey + ".heading", null, locale));
        String description = HtmlUtils.htmlEscape(messageSource.getMessage("gocast.callback." + resultKey + ".description", null, locale));
        return "<!doctype html><html lang=\"%s\"><meta name=\"referrer\" content=\"no-referrer\"><title>%s</title><body><main><h1>%s</h1><p>%s</p></main></body></html>"
                .formatted(locale.getLanguage(), title, heading, description);
    }
}
