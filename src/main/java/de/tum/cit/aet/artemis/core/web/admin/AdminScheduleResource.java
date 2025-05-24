package de.tum.cit.aet.artemis.core.web.admin;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE_AND_SCHEDULING;
import static tech.jhipster.web.util.PaginationUtil.generatePaginationHttpHeaders;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.core.service.ScheduleService;

/**
 * REST controller for getting the audit events. Currently limited to the primary node with scheduling enabled.
 * In the future, we want to expose the information on all core nodes so that the information can be displayed in the admin UI in the webapp.
 */
@Profile(PROFILE_CORE_AND_SCHEDULING)
@EnforceAdmin
@RestController
@RequestMapping("api/core/admin/")
public class AdminScheduleResource {

    private final ScheduleService scheduleService;

    public AdminScheduleResource(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    /**
     * GET /exercise-schedules : get a page of scheduled events.
     *
     * @param pageable the pagination information
     * @return the ResponseEntity with status 200 (OK) and the list of ScheduledExerciseEvents in body
     */
    @GetMapping("exercise-schedules")
    public ResponseEntity<List<ScheduleService.ScheduledExerciseEvent>> getAllExerciseSchedules(Pageable pageable) {
        Page<ScheduleService.ScheduledExerciseEvent> page = scheduleService.findAllExerciseEvents(pageable);
        HttpHeaders headers = generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    /**
     * GET /slide-schedules : get a page of scheduled slide events.
     *
     * @param pageable the pagination information
     * @return the ResponseEntity with status 200 (OK) and the list of ScheduledSlideEvents in body
     */
    @GetMapping("slide-schedules")
    public ResponseEntity<List<ScheduleService.ScheduledSlideEvent>> getAllSlideSchedules(Pageable pageable) {
        Page<ScheduleService.ScheduledSlideEvent> page = scheduleService.findAllSlideEvents(pageable);
        HttpHeaders headers = generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }
}
