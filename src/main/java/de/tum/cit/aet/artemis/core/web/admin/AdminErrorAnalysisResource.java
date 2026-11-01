package de.tum.cit.aet.artemis.core.web.admin;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.domain.ErrorAnalysisSpan;
import de.tum.cit.aet.artemis.core.domain.ErrorLocation;
import de.tum.cit.aet.artemis.core.domain.ErrorSeverity;
import de.tum.cit.aet.artemis.core.dto.ErrorGroupDTO;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.core.service.erroranalysis.ErrorAnalysisService;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;

/**
 * REST controller for the error analysis, which shows the errors of the server and the clients that are alike as one group.
 */
@Profile(PROFILE_CORE)
@EnforceAdmin
@Lazy
@FeatureUsage(UserFeature.MONITORING)
@RestController
@RequestMapping("api/core/admin/")
public class AdminErrorAnalysisResource {

    private final ErrorAnalysisService errorAnalysisService;

    public AdminErrorAnalysisResource(ErrorAnalysisService errorAnalysisService) {
        this.errorAnalysisService = errorAnalysisService;
    }

    /**
     * {@code GET /error-analysis} : the errors that happened in a time span, grouped by what they have in common.
     * <p>
     * The analysis is computed again from the distributed data on every call, and the response must not be cached by the browser or
     * by a proxy, so an administrator who opens the page always sees the current errors of all core nodes. The only delay is the
     * interval in which a node hands the errors that it collected to the cluster, ten seconds by default, and the node that answers
     * adds what it has collected itself.
     *
     * @param span        how far to look back from now, the last day if not set
     * @param location    only the errors of the server or only the errors of the clients, all of them if not set
     * @param minSeverity only the errors that are at least this serious, errors and critical errors if not set
     * @return the groups, the most frequent first
     */
    @GetMapping("error-analysis")
    public ResponseEntity<List<ErrorGroupDTO>> getErrorAnalysis(@RequestParam(defaultValue = "DAY") ErrorAnalysisSpan span, @RequestParam(required = false) ErrorLocation location,
            @RequestParam(defaultValue = "ERROR") ErrorSeverity minSeverity) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(errorAnalysisService.getGroups(span, location, minSeverity));
    }
}
