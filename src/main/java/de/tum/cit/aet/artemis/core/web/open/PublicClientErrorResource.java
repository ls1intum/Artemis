package de.tum.cit.aet.artemis.core.web.open;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import jakarta.validation.Valid;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.domain.FeatureInteraction;
import de.tum.cit.aet.artemis.core.dto.ClientErrorReportDTO;
import de.tum.cit.aet.artemis.core.security.RateLimitType;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceNothing;
import de.tum.cit.aet.artemis.core.service.ClientErrorReportService;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.service.featureusage.UsageInteraction;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;

/**
 * REST controller for the errors that clients report to the server.
 */
@Profile(PROFILE_CORE)
@Lazy
@FeatureUsage(UserFeature.MONITORING)
@RestController
@RequestMapping("api/core/public/")
public class PublicClientErrorResource {

    private final ClientErrorReportService clientErrorReportService;

    public PublicClientErrorResource(ClientErrorReportService clientErrorReportService) {
        this.clientErrorReportService = clientErrorReportService;
    }

    /**
     * {@code POST /client-errors} : report an error of the client, which the server writes to the log.
     * <p>
     * The endpoint is public, because the errors that are the most interesting often happen where nobody is signed in: on the
     * login page, or while the application starts. A client that is not authenticated is bounded per address by
     * {@link RateLimitType#CLIENT_ERROR_REPORT}, since every report writes a log entry, see {@link ClientErrorReportService#report}.
     *
     * @param report    the error that the client reports
     * @param userAgent the {@code User-Agent} header of the request, which tells the browser and the operating system
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} once the error is logged
     */
    @PostMapping("client-errors")
    @EnforceNothing
    @UsageInteraction(FeatureInteraction.AUTOMATIC)
    public ResponseEntity<Void> reportClientError(@Valid @RequestBody ClientErrorReportDTO report, @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        clientErrorReportService.report(report, userAgent);
        return ResponseEntity.ok().build();
    }
}
