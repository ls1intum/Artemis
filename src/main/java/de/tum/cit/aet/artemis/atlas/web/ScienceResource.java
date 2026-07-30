package de.tum.cit.aet.artemis.atlas.web;

import java.time.ZonedDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.dto.ScienceConsentUpdateDTO;
import de.tum.cit.aet.artemis.atlas.dto.ScienceCourseConsentDTO;
import de.tum.cit.aet.artemis.atlas.dto.ScienceEnabledCourseDTO;
import de.tum.cit.aet.artemis.atlas.dto.ScienceEventDTO;
import de.tum.cit.aet.artemis.atlas.dto.ScienceResearchExportAuditDTO;
import de.tum.cit.aet.artemis.atlas.dto.ScienceResearchExportRequestDTO;
import de.tum.cit.aet.artemis.atlas.service.ScienceCourseService;
import de.tum.cit.aet.artemis.atlas.service.ScienceEventService;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggle;

/**
 * REST controller providing the science related endpoints.
 */
@Conditional(AtlasEnabled.class)
@FeatureToggle(Feature.Science)
@Lazy
@RestController
@RequestMapping("api/atlas/")
public class ScienceResource {

    private static final Logger log = LoggerFactory.getLogger(ScienceResource.class);

    private final ScienceEventService scienceEventService;

    private final ScienceCourseService scienceCourseService;

    public ScienceResource(ScienceEventService scienceEventService, ScienceCourseService scienceCourseService) {
        this.scienceEventService = scienceEventService;
        this.scienceCourseService = scienceCourseService;
    }

    /**
     * PUT science : Logs an event of the given type in the event list
     *
     * @param event the type of the event that should be logged
     * @return the ResponseEntity with status 200 (OK)
     */
    @PutMapping(value = "science")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> science(@RequestBody ScienceEventDTO event) {
        log.debug("REST request to log science event of type {}", event);
        scienceEventService.logEvent(event);
        return ResponseEntity.ok().build();
    }

    @GetMapping("science/courses/{courseId}/consent")
    @EnforceAtLeastStudent
    public ResponseEntity<ScienceCourseConsentDTO> getConsentForCurrentUser(@PathVariable long courseId) {
        return ResponseEntity.ok(scienceCourseService.getConsentForCurrentUser(courseId));
    }

    @PutMapping("science/courses/{courseId}/consent")
    @EnforceAtLeastStudent
    public ResponseEntity<ScienceCourseConsentDTO> saveConsentForCurrentUser(@PathVariable long courseId, @RequestBody ScienceConsentUpdateDTO consentUpdate) {
        return ResponseEntity.ok(scienceCourseService.saveConsentForCurrentUser(courseId, consentUpdate.active()));
    }

    @GetMapping("science/consents")
    @EnforceAtLeastStudent
    public ResponseEntity<List<ScienceCourseConsentDTO>> getConsentsForCurrentUser() {
        return ResponseEntity.ok(scienceCourseService.getConsentsForCurrentUser());
    }

    @DeleteMapping("science/courses/{courseId}/data")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> deleteScienceDataForCurrentUser(@PathVariable long courseId) {
        scienceCourseService.deleteScienceDataForCurrentUser(courseId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("admin/science/courses")
    @EnforceAdmin
    public ResponseEntity<List<ScienceEnabledCourseDTO>> getScienceEnabledCourseHistory() {
        return ResponseEntity.ok(scienceCourseService.getEnabledCourseHistory());
    }

    @PutMapping("admin/science/courses/{courseId}")
    @EnforceAdmin
    public ResponseEntity<ScienceEnabledCourseDTO> enableScienceForCourse(@PathVariable long courseId) {
        return ResponseEntity.ok(scienceCourseService.enableCourse(courseId));
    }

    @DeleteMapping("admin/science/courses/{courseId}")
    @EnforceAdmin
    public ResponseEntity<ScienceEnabledCourseDTO> disableScienceForCourse(@PathVariable long courseId) {
        return ResponseEntity.ok(scienceCourseService.disableCourse(courseId));
    }

    @GetMapping("admin/science/export-audits")
    @EnforceAdmin
    public ResponseEntity<List<ScienceResearchExportAuditDTO>> getScienceResearchExportAudits() {
        return ResponseEntity.ok(scienceCourseService.getResearchExportAudits());
    }

    @PostMapping("admin/science/exports")
    @EnforceAdmin
    public ResponseEntity<ByteArrayResource> createScienceResearchExport(@RequestBody ScienceResearchExportRequestDTO request) {
        byte[] csvBytes = scienceCourseService.createResearchExport(request);
        String filename = "science-research-export-" + ZonedDateTime.now().toInstant().toEpochMilli() + ".csv";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        return ResponseEntity.ok().headers(headers).contentLength(csvBytes.length).contentType(MediaType.TEXT_PLAIN).body(new ByteArrayResource(csvBytes));
    }
}
