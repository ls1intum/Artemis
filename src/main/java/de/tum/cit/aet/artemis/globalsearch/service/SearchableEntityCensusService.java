package de.tum.cit.aet.artemis.globalsearch.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_SCHEDULING;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.service.CourseIndexCensusService.CourseCensus;
import de.tum.cit.aet.artemis.globalsearch.service.CourseIndexCensusService.TypeCensus;

/**
 * Dry-run reconcile census: on the scheduling node, periodically runs the shared per-course drift computation
 * ({@link CourseIndexCensusService}) over every course and logs the per-course, per-type drift. It writes nothing (no
 * sync-state, no Weaviate, no enqueue), so it is safe to run against production to measure how incomplete the index is.
 * <p>
 * Gating mirrors {@code WeaviateMigrationStartupService}: scheduling node only, Weaviate enabled, and a config flag
 * {@code artemis.global-search.census.enabled} (default on) so the dry-run can be turned off without a redeploy. The
 * schedule is {@code artemis.global-search.census.cron} (default nightly). {@code drifted} is not computed yet (it needs
 * the achieved hash stamped onto the Weaviate row, added by Ingestion Durable Sync); until then it is logged as pending.
 */
@Lazy
@Service
@Conditional(WeaviateEnabled.class)
@Profile(PROFILE_SCHEDULING)
public class SearchableEntityCensusService {

    private static final Logger log = LoggerFactory.getLogger(SearchableEntityCensusService.class);

    private final boolean censusEnabled;

    private final CourseIndexCensusService courseIndexCensusService;

    public SearchableEntityCensusService(@Value("${artemis.global-search.census.enabled:true}") boolean censusEnabled, CourseIndexCensusService courseIndexCensusService) {
        this.censusEnabled = censusEnabled;
        this.courseIndexCensusService = courseIndexCensusService;
    }

    /**
     * Runs the dry-run census over all courses on the configured schedule (default nightly) and logs the drift. Never
     * writes.
     */
    @Scheduled(cron = "${artemis.global-search.census.cron:0 0 3 * * *}")
    public void runCensus() {
        if (!censusEnabled) {
            log.debug("SearchableEntities census is disabled by configuration; skipping");
            return;
        }
        List<CourseCensus> results = courseIndexCensusService.censusAllCourses();
        log.info("Starting dry-run SearchableEntities census over {} courses (read-only, no writes)", results.size());
        results.forEach(this::logCourse);
        log.info("Completed dry-run SearchableEntities census");
    }

    private void logCourse(CourseCensus result) {
        for (TypeCensus type : result.types()) {
            if (type.expected() == null) {
                log.info("census course={} type={} expected=unknown present={} drift=pending-hash-stamping", result.courseId(), type.type(), type.present());
            }
            else {
                log.info("census course={} type={} expected={} present={} missing={} orphaned={} drift=pending-hash-stamping", result.courseId(), type.type(), type.expected(),
                        type.present(), type.missing(), type.orphaned());
            }
        }
    }
}
