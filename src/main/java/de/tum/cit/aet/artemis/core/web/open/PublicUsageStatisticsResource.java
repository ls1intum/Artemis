package de.tum.cit.aet.artemis.core.web.open;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Duration;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.admin.dto.PublicUsageStatisticsDTO;
import de.tum.cit.aet.artemis.admin.service.PublicUsageStatisticsService;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceNothing;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;

/**
 * REST controller for the public usage dashboard. The numbers are aggregated and anonymous, see {@link PublicUsageStatisticsService}.
 */
@Profile(PROFILE_CORE)
@Lazy
@FeatureUsage(UserFeature.USAGE_STATISTICS)
@RestController
@RequestMapping("api/core/public/")
public class PublicUsageStatisticsResource {

    private final PublicUsageStatisticsService publicUsageStatisticsService;

    public PublicUsageStatisticsResource(PublicUsageStatisticsService publicUsageStatisticsService) {
        this.publicUsageStatisticsService = publicUsageStatisticsService;
    }

    /**
     * GET usage-statistics : Returns the aggregated usage numbers of the last semesters.
     * Not secured, as anybody should be able to see them (even if not logged in).
     *
     * @return the ResponseEntity with status 200 (OK) and the statistics in the body
     */
    @GetMapping("usage-statistics")
    @EnforceNothing
    public ResponseEntity<PublicUsageStatisticsDTO> getUsageStatistics() {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePublic()).body(publicUsageStatisticsService.getUsageStatistics());
    }
}
