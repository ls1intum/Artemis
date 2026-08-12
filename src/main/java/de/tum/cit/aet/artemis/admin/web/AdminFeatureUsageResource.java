package de.tum.cit.aet.artemis.admin.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.admin.dto.FeatureAdoptionDTO;
import de.tum.cit.aet.artemis.admin.dto.FeatureUsageOverviewDTO;
import de.tum.cit.aet.artemis.admin.dto.FeatureUsageTrendPointDTO;
import de.tum.cit.aet.artemis.admin.service.FeatureUsageDigestScheduleService;
import de.tum.cit.aet.artemis.admin.service.FeatureUsageQueryService;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;

/**
 * REST controller for the built-in feature usage analysis.
 * <p>
 * Read only: the counters are written by the collection path, never through the API.
 */
@Profile(PROFILE_CORE)
@EnforceAdmin
@Lazy
@FeatureUsage("monitoring/feature-usage")
@RestController
@RequestMapping("api/admin/")
public class AdminFeatureUsageResource {

    private static final Logger log = LoggerFactory.getLogger(AdminFeatureUsageResource.class);

    private static final String ENTITY_NAME = "featureUsage";

    /**
     * The windows the admin page offers. Restricted to these so an arbitrary value cannot be used to scan a range far
     * beyond the retention period, and so the responses stay cacheable per window.
     */
    private static final List<Integer> ALLOWED_WINDOWS_IN_DAYS = List.of(7, 30, 90, 180);

    private final FeatureUsageQueryService featureUsageQueryService;

    private final FeatureUsageDigestScheduleService featureUsageDigestScheduleService;

    public AdminFeatureUsageResource(FeatureUsageQueryService featureUsageQueryService, FeatureUsageDigestScheduleService featureUsageDigestScheduleService) {
        this.featureUsageQueryService = featureUsageQueryService;
        this.featureUsageDigestScheduleService = featureUsageDigestScheduleService;
    }

    /**
     * GET admin/feature-usage : the usage report for the given window, including the features that saw no usage.
     *
     * @param days       the length of the window, one of 7, 30, 90 or 180
     * @param callerRole optional filter, restricting the counters to callers whose highest global role is this one
     * @return the ResponseEntity with status 200 (OK) and the report in the body
     */
    @GetMapping("feature-usage")
    public ResponseEntity<FeatureUsageOverviewDTO> getFeatureUsage(@RequestParam(defaultValue = "30") int days, @RequestParam(required = false) @Nullable Role callerRole) {
        log.debug("REST request to get feature usage over the last {} days for role {}", days, callerRole);
        return ResponseEntity.ok(featureUsageQueryService.getOverview(validateWindow(days), callerRole));
    }

    /**
     * GET admin/feature-usage/trend : the daily usage of a single feature, for the trend chart.
     *
     * @param featureId the feature to chart
     * @param days      the length of the window, one of 7, 30, 90 or 180
     * @return the ResponseEntity with status 200 (OK) and the daily totals in the body
     */
    @GetMapping("feature-usage/trend")
    public ResponseEntity<List<FeatureUsageTrendPointDTO>> getFeatureUsageTrend(@RequestParam long featureId, @RequestParam(defaultValue = "30") int days) {
        log.debug("REST request to get the usage trend of feature {} over the last {} days", featureId, days);
        return ResponseEntity.ok(featureUsageQueryService.getTrend(featureId, validateWindow(days)));
    }

    /**
     * GET admin/feature-usage/adoption : how many entities have each optional feature switched on.
     *
     * @return the ResponseEntity with status 200 (OK) and the adoption counts in the body
     */
    @GetMapping("feature-usage/adoption")
    public ResponseEntity<List<FeatureAdoptionDTO>> getFeatureAdoption() {
        log.debug("REST request to get feature adoption");
        return ResponseEntity.ok(featureUsageQueryService.getAdoption());
    }

    /**
     * POST admin/feature-usage/digest/send-email : send the weekly usage summary now.
     * <p>
     * Lets an administrator confirm that delivery and the configured recipients work without waiting for the next Monday.
     *
     * @return the ResponseEntity with status 200 (OK) if the email was sent, or 400 (Bad Request) if the digest is switched
     *         off, no recipient is configured, or sending failed
     */
    @PostMapping("feature-usage/digest/send-email")
    public ResponseEntity<Void> sendFeatureUsageDigestEmail() {
        log.info("REST request to send the feature usage digest email");
        if (featureUsageDigestScheduleService.sendDigestEmail()) {
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.badRequest().build();
    }

    private static int validateWindow(int days) {
        if (!ALLOWED_WINDOWS_IN_DAYS.contains(days)) {
            throw new BadRequestAlertException("The window must be one of " + ALLOWED_WINDOWS_IN_DAYS + " days", ENTITY_NAME, "invalidWindow");
        }
        return days;
    }
}
