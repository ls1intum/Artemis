package de.tum.cit.aet.artemis.atlas.web.admin;

import java.io.FilterInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
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
import de.tum.cit.aet.artemis.atlas.dto.ScienceEnabledCourseDTO;
import de.tum.cit.aet.artemis.atlas.dto.ScienceResearchExportAuditDTO;
import de.tum.cit.aet.artemis.atlas.dto.ScienceResearchExportRequestDTO;
import de.tum.cit.aet.artemis.atlas.service.ScienceCourseService;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggle;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.course.dto.CourseSelectionDTO;

/**
 * Admin REST controller for managing science data collection and exports.
 */
@Conditional(AtlasEnabled.class)
@FeatureUsage("research/science-administration")
@FeatureToggle(Feature.Science)
@EnforceAdmin
@Lazy
@RestController
@RequestMapping("api/atlas/admin/science/")
public class AdminScienceResource {

    private final ScienceCourseService scienceCourseService;

    public AdminScienceResource(ScienceCourseService scienceCourseService) {
        this.scienceCourseService = scienceCourseService;
    }

    /**
     * GET courses : Returns the science-enabled course history.
     *
     * @return the ResponseEntity with status 200 (OK) and the enabled-course history
     */
    @GetMapping("courses")
    public ResponseEntity<List<ScienceEnabledCourseDTO>> getScienceEnabledCourseHistory() {
        return ResponseEntity.ok(scienceCourseService.getEnabledCourseHistory());
    }

    /**
     * GET selectable-courses : Returns the courses an administrator can enable science data collection for.
     *
     * @return the ResponseEntity with status 200 (OK) and the selectable courses
     */
    @GetMapping("selectable-courses")
    public ResponseEntity<List<CourseSelectionDTO>> getSelectableCourses() {
        return ResponseEntity.ok(scienceCourseService.getSelectableCourses());
    }

    /**
     * PUT courses/{courseId} : Enables science data collection for a course.
     *
     * @param courseId the id of the course
     * @return the ResponseEntity with status 200 (OK) and the enabled-course entry
     */
    @PutMapping("courses/{courseId}")
    public ResponseEntity<ScienceEnabledCourseDTO> enableScienceForCourse(@PathVariable long courseId) {
        return ResponseEntity.ok(scienceCourseService.enableCourse(courseId));
    }

    /**
     * DELETE courses/{courseId} : Disables science data collection for a course.
     *
     * @param courseId the id of the course
     * @return the ResponseEntity with status 200 (OK) and the updated enabled-course entry
     */
    @DeleteMapping("courses/{courseId}")
    public ResponseEntity<ScienceEnabledCourseDTO> disableScienceForCourse(@PathVariable long courseId) {
        return ResponseEntity.ok(scienceCourseService.disableCourse(courseId));
    }

    /**
     * GET export-audits : Returns the immutable science research export audit history.
     *
     * @return the ResponseEntity with status 200 (OK) and the export audit history
     */
    @GetMapping("export-audits")
    public ResponseEntity<List<ScienceResearchExportAuditDTO>> getScienceResearchExportAudits() {
        return ResponseEntity.ok(scienceCourseService.getResearchExportAudits());
    }

    /**
     * POST exports : Creates a pseudonymized science research CSV export.
     *
     * @param request the export filter and purpose
     * @return the ResponseEntity with status 200 (OK) and the generated CSV file
     * @throws IOException if the generated file cannot be streamed
     */
    // The server writes the CSV to a temporary file and streams it, but the administration client receives it as a Blob,
    // which browsers buffer whole before the download starts. That is the platform's standard download shape - repository
    // and submission exports ship far larger payloads the same way - and it bounds this endpoint at what one browser can
    // hold. It is adequate because a research export covers one course a handful of times per semester; an export sized
    // to exhaust that would need a different contract for every export in Artemis, not just this one.
    @PostMapping("exports")
    public ResponseEntity<Resource> createScienceResearchExport(@RequestBody @Valid ScienceResearchExportRequestDTO request) throws IOException {
        var export = scienceCourseService.createResearchExport(request);
        String filename = "science-research-export-" + ZonedDateTime.now().toInstant().toEpochMilli() + ".csv";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        InputStreamResource body;
        try {
            body = new InputStreamResource(new AutoDeletingFileInputStream(export.path()));
        }
        catch (IOException e) {
            // The stream deletes the file when it is closed, so a file whose stream was never opened has nothing to
            // delete it. Interaction data must not be what is left lying in the temporary directory.
            Files.deleteIfExists(export.path());
            throw e;
        }
        return ResponseEntity.ok().headers(headers).contentLength(export.contentLength()).contentType(new MediaType("text", "csv")).body(body);
    }

    /** Streams the generated export and removes the temporary file once the response has been written. */
    private static final class AutoDeletingFileInputStream extends FilterInputStream {

        private final Path path;

        private AutoDeletingFileInputStream(Path path) throws IOException {
            super(Files.newInputStream(path));
            this.path = path;
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            }
            finally {
                Files.deleteIfExists(path);
            }
        }
    }
}
