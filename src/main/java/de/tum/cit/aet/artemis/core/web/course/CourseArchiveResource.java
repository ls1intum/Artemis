package de.tum.cit.aet.artemis.core.web.course;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static java.time.ZonedDateTime.now;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.CourseForArchiveDTO;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.enforceAccessPolicy.EnforceAccessPolicy;
import de.tum.cit.aet.artemis.core.service.course.CourseArchiveService;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggle;
import de.tum.cit.aet.artemis.core.util.TimeLogUtil;

/**
 * REST controller for archiving and cleaning course.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/core/")
@Lazy
public class CourseArchiveResource {

    private static final Logger log = LoggerFactory.getLogger(CourseArchiveResource.class);

    private final CourseRepository courseRepository;

    private final UserRepository userRepository;

    private final CourseArchiveService courseArchiveService;

    @Value("${artemis.course-archives-path}")
    private String courseArchivesDirPath;

    public CourseArchiveResource(CourseRepository courseRepository, UserRepository userRepository, CourseArchiveService courseArchiveService) {
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.courseArchiveService = courseArchiveService;
    }

    /**
     * PUT /courses/{courseId} : archive an existing course asynchronously. This method starts the process of archiving all course exercises, submissions and results in a large
     * zip file. It immediately returns and runs this task asynchronously. When the task is done, the course is marked as archived, which means the zip file can be downloaded.
     *
     * @param courseId the id of the course
     * @return the ResponseEntity with status 200 (OK) when no exception occurred
     */
    @PutMapping("courses/{courseId}/archive")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @EnforceAccessPolicy(value = "courseInstructorAccessPolicy", resourceIdFieldName = "courseId")
    @FeatureToggle(Feature.Exports)
    public ResponseEntity<Void> archiveCourse(@PathVariable Long courseId) {
        log.info("REST request to archive Course : {}", courseId);
        final Course course = courseRepository.findByIdWithExercisesAndExerciseDetailsAndLecturesElseThrow(courseId);
        // Archiving a course is only possible after the course is over
        if (now().isBefore(course.getEndDate())) {
            throw new BadRequestAlertException("You cannot archive a course that is not over.", Course.ENTITY_NAME, "courseNotOver", true);
        }
        courseArchiveService.archiveCourse(course);

        // Note: in the first version, we do not store the results with feedback and other metadata, as those will stay available in Artemis, the main focus is to allow
        // instructors to download student repos in order to delete those in the VCS

        // Note: Lectures are not part of the archive at the moment and will be included in a future version
        // 1) Get all lectures (attachments) of the course and store them in a folder

        // Note: Questions and answers are not part of the archive at the moment and will be included in a future version
        // 1) Get all questions and answers for exercises and lectures and store those in structured text files

        return ResponseEntity.ok().build();
    }

    /**
     * Downloads the zip file of the archived course if it exists. Throws a 404 if the course doesn't exist
     *
     * @param courseId The course id of the archived course
     * @return ResponseEntity with status
     */
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @EnforceAccessPolicy(value = "courseInstructorAccessPolicy", resourceIdFieldName = "courseId")
    @GetMapping("courses/{courseId}/download-archive")
    public ResponseEntity<Resource> downloadCourseArchive(@PathVariable Long courseId) throws IOException {
        log.info("REST request to download archive of Course : {}", courseId);
        final Course course = courseRepository.findByIdElseThrow(courseId);
        if (!course.hasCourseArchive()) {
            throw new EntityNotFoundException("Archived course", courseId);
        }

        // The path is stored in the course table
        Path archive = Path.of(courseArchivesDirPath, course.getCourseArchivePath());
        InputStreamResource resource = new InputStreamResource(Files.newInputStream(archive));
        File zipFile = archive.toFile();
        ContentDisposition contentDisposition = ContentDisposition.builder("attachment").filename(zipFile.getName()).build();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(contentDisposition);
        return ResponseEntity.ok().headers(headers).contentLength(zipFile.length()).contentType(MediaType.APPLICATION_OCTET_STREAM).header("filename", zipFile.getName())
                .body(resource);
    }

    /**
     * DELETE /courses/:course/cleanup : Cleans up a course by deleting all student submissions.
     *
     * @param courseId  id of the course to clean up
     * @param principal the user that wants to cleanup the course
     * @return ResponseEntity with status
     */
    @DeleteMapping("courses/{courseId}/cleanup")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @EnforceAccessPolicy(value = "courseInstructorAccessPolicy", resourceIdFieldName = "courseId")
    public ResponseEntity<Resource> cleanup(@PathVariable Long courseId, Principal principal) {
        log.info("REST request to cleanup the Course : {}", courseId);
        final Course course = courseRepository.findByIdElseThrow(courseId);
        // Forbid cleaning the course if no archive has been created
        if (!course.hasCourseArchive()) {
            throw new BadRequestAlertException("Failed to clean up course " + courseId + " because it needs to be archived first.", Course.ENTITY_NAME, "archivenonexistent");
        }
        courseArchiveService.cleanupCourse(courseId, principal);
        return ResponseEntity.ok().build();
    }

    /**
     * GET /courses/for-archive : get all courses for course archive
     *
     * @return the ResponseEntity with status 200 (OK) and with body containing
     *         a set of DTOs, which contain the courses with id, title, semester, color, icon
     */
    @GetMapping("courses/for-archive")
    @EnforceAtLeastStudent
    public ResponseEntity<Set<CourseForArchiveDTO>> getCoursesForArchive() {
        long start = System.nanoTime();
        User user = userRepository.getUserWithGroupsAndAuthorities();
        log.debug("REST request to get all inactive courses from previous semesters user {} has access to", user.getLogin());
        Set<CourseForArchiveDTO> courses = courseArchiveService.getAllCoursesForCourseArchive();
        log.debug("courseService.getCoursesForArchive done");

        log.info("GET /courses/for-archive took {} for {} courses for user {}", TimeLogUtil.formatDurationFrom(start), courses.size(), user.getLogin());
        return ResponseEntity.ok(courses);
    }

}
