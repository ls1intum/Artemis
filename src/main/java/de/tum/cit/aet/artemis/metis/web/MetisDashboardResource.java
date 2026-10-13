package de.tum.cit.aet.artemis.metis.web;

import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastInstructorInExercise;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastTutorInExercise;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;
import de.tum.cit.aet.artemis.metis.config.MetisEnabled;
import de.tum.cit.aet.artemis.metis.dto.MetisDashboardDTO;
import de.tum.cit.aet.artemis.metis.dto.MetisGenerateResultDTO;
import de.tum.cit.aet.artemis.metis.service.MetisDashboardService;
import de.tum.cit.aet.artemis.metis.service.MetisDashboardService.CsvExport;
import de.tum.cit.aet.artemis.metis.service.MetisGenerationDispatchService;

/**
 * REST resource for the results of the comprehension checks of an exercise: the dashboard with its summary, the export of every question and answer as CSV,
 * and "generate now", which creates the missing checks and puts the failed ones back into the queue.
 * <p>
 * Tutors may read the results, as on the other dashboards of an exercise, with every row of the exercise. Only instructors may start a generation, which costs
 * LLM calls for a whole cohort.
 */
@Conditional(MetisEnabled.class)
@Lazy
@FeatureUsage(UserFeature.METIS_CHECK_MANAGEMENT)
@RestController
@RequestMapping("api/metis/")
public class MetisDashboardResource {

    private static final Logger log = LoggerFactory.getLogger(MetisDashboardResource.class);

    private static final MediaType TEXT_CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final MetisDashboardService dashboardService;

    private final MetisGenerationDispatchService dispatchService;

    public MetisDashboardResource(MetisDashboardService dashboardService, MetisGenerationDispatchService dispatchService) {
        this.dashboardService = dashboardService;
        this.dispatchService = dispatchService;
    }

    /**
     * GET exercises/{exerciseId}/dashboard : the results of the comprehension checks of an exercise, one row per check with its student, tutorial group and
     * questions, and their summary.
     *
     * @param exerciseId the exercise
     * @return 200 with the summary and the rows, ordered by the student's login
     */
    @GetMapping("exercises/{exerciseId}/dashboard")
    @EnforceAtLeastTutorInExercise
    public ResponseEntity<MetisDashboardDTO> getDashboard(@PathVariable long exerciseId) {
        log.debug("REST request to get the Metis dashboard of exercise {}", exerciseId);
        return ResponseEntity.ok(dashboardService.getDashboard(exerciseId));
    }

    /**
     * GET exercises/{exerciseId}/export : every question and answer of the comprehension checks of an exercise as a CSV file, one line per question.
     *
     * @param exerciseId the exercise
     * @return 200 with the CSV file, encoded as UTF-8 with a byte order mark
     */
    @GetMapping(value = "exercises/{exerciseId}/export", produces = "text/csv")
    @EnforceAtLeastTutorInExercise
    public ResponseEntity<byte[]> exportCsv(@PathVariable long exerciseId) {
        log.debug("REST request to export the Metis results of exercise {}", exerciseId);
        CsvExport export = dashboardService.exportCsv(exerciseId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(TEXT_CSV);
        headers.setContentDispositionFormData("attachment", export.fileName());
        headers.setContentLength(export.content().length);
        return ResponseEntity.ok().headers(headers).body(export.content());
    }

    /**
     * POST exercises/{exerciseId}/checks/generate : creates the missing comprehension checks of an exercise and puts its failed checks back into the queue,
     * together with those skipped for missing consent or a closed window, without waiting for the delays of the poller. The questions are generated afterwards
     * by the scheduling node.
     *
     * @param exerciseId the exercise
     * @return 200 with the number of created and of requeued checks; 400 if the exercise is no individual programming exercise of a course; 409 if the check is
     *         not enabled for the exercise or the exercise has no due date
     */
    @PostMapping("exercises/{exerciseId}/checks/generate")
    @EnforceAtLeastInstructorInExercise
    public ResponseEntity<MetisGenerateResultDTO> generateChecks(@PathVariable long exerciseId) {
        log.debug("REST request to generate the missing Metis checks of exercise {}", exerciseId);
        return ResponseEntity.ok(dispatchService.generateNow(exerciseId));
    }
}
