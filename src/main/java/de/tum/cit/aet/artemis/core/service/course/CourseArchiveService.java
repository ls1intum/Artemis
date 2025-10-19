package de.tum.cit.aet.artemis.core.service.course;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.dto.CourseForArchiveDTO;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.export.CourseExamExportService;
import de.tum.cit.aet.artemis.core.util.TimeLogUtil;
import de.tum.cit.aet.artemis.exam.api.ExamRepositoryApi;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDeletionService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Service for archiving courses, which includes exporting course data and cleaning up exercises.
 * This service is used to create a zip file containing student submissions for both course exercises and exams.
 */
@Service
@Profile(PROFILE_CORE)
@Lazy
public class CourseArchiveService {

    private static final Logger log = LoggerFactory.getLogger(CourseArchiveService.class);

    @Value("${artemis.course-archives-path}")
    private Path courseArchivesDirPath;

    private final CourseRepository courseRepository;

    private final CourseExamExportService courseExamExportService;

    private final AuthorizationCheckService authCheckService;

    private final UserRepository userRepository;

    private final AuditEventRepository auditEventRepository;

    private final Optional<ExamRepositoryApi> examRepositoryApi;

    private final ExerciseDeletionService exerciseDeletionService;

    public CourseArchiveService(CourseRepository courseRepository, CourseExamExportService courseExamExportService, AuthorizationCheckService authCheckService,
            UserRepository userRepository, AuditEventRepository auditEventRepository, Optional<ExamRepositoryApi> examRepositoryApi,
            ExerciseDeletionService exerciseDeletionService) {
        this.courseRepository = courseRepository;
        this.courseExamExportService = courseExamExportService;
        this.authCheckService = authCheckService;
        this.userRepository = userRepository;
        this.auditEventRepository = auditEventRepository;

        this.examRepositoryApi = examRepositoryApi;
        this.exerciseDeletionService = exerciseDeletionService;
    }

    /**
     * Retrieves all inactive courses from non-null semesters that the current user is enrolled in
     * for the course archive.
     *
     * @return A list of courses for the course archive.
     */
    public Set<CourseForArchiveDTO> getAllCoursesForCourseArchive() {
        var user = userRepository.getUserWithGroupsAndAuthorities();
        boolean isAdmin = authCheckService.isAdmin(user);
        return courseRepository.findInactiveCoursesForUserRolesWithNonNullSemester(isAdmin, user.getGroups(), ZonedDateTime.now());
    }

    /**
     * Archives the course by creating a zip file will student submissions for
     * both the course exercises and exams.
     *
     * @param course the course to archive
     */
    @Async
    public void archiveCourse(Course course) {
        long start = System.nanoTime();
        SecurityUtils.setAuthorizationObject();

        // Archiving a course is only possible after the course is over
        if (ZonedDateTime.now().isBefore(course.getEndDate())) {
            return;
        }

        // This contains possible errors encountered during the archive process
        List<String> exportErrors = Collections.synchronizedList(new ArrayList<>());

        try {
            // Create course archives directory if it doesn't exist
            Files.createDirectories(courseArchivesDirPath);
            log.info("Created the course archives directory at {} because it didn't exist.", courseArchivesDirPath);

            // Export the course to the archives' directory.
            var archivedCoursePath = courseExamExportService.exportCourse(course, courseArchivesDirPath, exportErrors);

            // Attach the path to the archive to the course and save it in the database
            if (archivedCoursePath.isPresent()) {
                course.setCourseArchivePath(archivedCoursePath.get().getFileName().toString());
                courseRepository.saveAndFlush(course);
            }
        }
        catch (Exception e) {
            var error = "Failed to create course archives directory " + courseArchivesDirPath + ": " + e.getMessage();
            exportErrors.add(error);
            log.info(error);
        }

        log.info("archive course took {}", TimeLogUtil.formatDurationFrom(start));
    }

    /**
     * Cleans up a course by cleaning up all exercises from that course. This deletes all student
     * repositories and build plans. Note that a course has to be archived first before being cleaned up.
     *
     * @param courseId  The id of the course to clean up
     * @param principal the user that wants to cleanup the course
     */
    public void cleanupCourse(Long courseId, Principal principal) {
        final var auditEvent = new AuditEvent(principal.getName(), Constants.CLEANUP_COURSE, "course=" + courseId);
        auditEventRepository.add(auditEvent);
        // Get the course with all exercises
        var course = courseRepository.findByIdWithEagerExercisesElseThrow(courseId);
        if (!course.hasCourseArchive()) {
            log.info("Cannot clean up course {} because it hasn't been archived.", courseId);
            return;
        }

        // The Objects::nonNull is needed here because the relationship exam -> exercise groups is ordered and
        // hibernate sometimes adds nulls into the list of exercise groups to keep the order
        Set<Exercise> examExercises = examRepositoryApi.map(api -> api.getExercisesByCourseId(courseId)).orElse(Set.of());

        var exercisesToCleanup = Stream.concat(course.getExercises().stream(), examExercises.stream()).collect(Collectors.toSet());
        exercisesToCleanup.forEach(exercise -> {
            if (exercise instanceof ProgrammingExercise) {
                exerciseDeletionService.cleanup(exercise.getId());
            }
        });

        log.info("The course {} has been cleaned up!", courseId);
    }
}
