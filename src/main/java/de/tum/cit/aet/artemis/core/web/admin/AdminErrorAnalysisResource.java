package de.tum.cit.aet.artemis.core.web.admin;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.domain.ErrorAnalysisSortField;
import de.tum.cit.aet.artemis.core.domain.ErrorAnalysisSpan;
import de.tum.cit.aet.artemis.core.domain.ErrorLocation;
import de.tum.cit.aet.artemis.core.domain.ErrorSeverity;
import de.tum.cit.aet.artemis.core.dto.ErrorAnalysisPageDTO;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
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

    /** The most groups that one page may have, so that a request cannot ask for everything at once. */
    static final int MAX_PAGE_SIZE = 100;

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
     * Only one page of the groups is returned, so that a large installation neither computes nor sends all of them to the browser at
     * once. The groups are sorted before they are cut into pages, so the pages follow the sort order.
     *
     * @param span        how far to look back from now, or the release that is running, which is the default
     * @param location    only the errors of the server or only the errors of the clients, all of them if not set
     * @param minSeverity only the errors that are at least this serious, errors and critical errors if not set
     * @param sortBy      what to sort by, the number of errors if not set
     * @param direction   {@code ASC} or {@code DESC}, descending if not set
     * @param page        the number of the page, the first page is 0
     * @param size        how many groups a page has, at most {@value #MAX_PAGE_SIZE}
     * @return the groups of the page, the number of groups and the number of errors in all of them
     * @throws BadRequestAlertException {@code 400 (Bad Request)} if the page or the size is out of range
     */
    @GetMapping("error-analysis")
    public ResponseEntity<ErrorAnalysisPageDTO> getErrorAnalysis(@RequestParam(defaultValue = "RELEASE") ErrorAnalysisSpan span,
            @RequestParam(required = false) ErrorLocation location, @RequestParam(defaultValue = "ERROR") ErrorSeverity minSeverity,
            @RequestParam(defaultValue = "COUNT") ErrorAnalysisSortField sortBy, @RequestParam(defaultValue = "DESC") Sort.Direction direction,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestAlertException("The page must not be negative and the size must be between 1 and " + MAX_PAGE_SIZE, "ErrorAnalysis", "invalidPage");
        }
        ErrorAnalysisPageDTO result = errorAnalysisService.getGroups(span, location, minSeverity, sortBy, direction.isAscending(), page, size);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }
}
