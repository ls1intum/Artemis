package de.tum.in.www1.artemis.lecture.web.rest;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.lecture.service.LectureImportService;
import de.tum.in.www1.artemis.lecture.service.LectureService;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.web.rest.dto.PageableSearchDTO;
import de.tum.in.www1.artemis.web.rest.dto.SearchResultPageDTO;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.Set;

/**
 * REST controller for managing Lecture.
 */
@RestController
@RequestMapping("api/")
public class LectureResource {

    private static final String ENTITY_NAME = "lecture";
    private final Logger log = LoggerFactory.getLogger(LectureResource.class);
    private final LectureRepository lectureRepository;
    private final LectureService lectureService;
    private final LectureImportService lectureImportService;
    private final CourseRepository courseRepository;
    private final AuthorizationCheckService authCheckService;
    private final UserRepository userRepository;
    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    public LectureResource(LectureRepository lectureRepository, LectureService lectureService, LectureImportService lectureImportService,
                           CourseRepository courseRepository, UserRepository userRepository, AuthorizationCheckService authCheckService) {
        this.lectureRepository = lectureRepository;
        this.lectureService = lectureService;
        this.lectureImportService = lectureImportService;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.authCheckService = authCheckService;
    }

    /**
     * POST lectures : Create a new lecture.
     *
     * @param lecture the lecture to create
     * @return the ResponseEntity with status 201 (Created) and with body the new lecture, or with status 400 (Bad Request) if the lecture has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("lectures")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<Lecture> createLecture(@RequestBody Lecture lecture) throws URISyntaxException {
        log.debug("REST request to save Lecture : {}", lecture);
        if (lecture.getId() != null) {
            throw new BadRequestAlertException("A new lecture cannot already have an ID", ENTITY_NAME, "idexists");
        }
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, lecture.getCourse(), null);

        Lecture result = lectureRepository.save(lecture);
        return ResponseEntity.created(new URI("/api/lectures/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.getId().toString())).body(result);
    }

    /**
     * PUT lectures : Updates an existing lecture.
     *
     * @param lecture the lecture to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated lecture, or with status 400 (Bad Request) if the lecture is not valid, or with status 500 (Internal
     * Server Error) if the lecture couldn't be updated
     */
    @PutMapping("lectures")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<Lecture> updateLecture(@RequestBody Lecture lecture) {
        log.debug("REST request to update Lecture : {}", lecture);
        if (lecture.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }

        // Make sure that the original references are preserved.
        Optional<Lecture> optionalLecture = lectureRepository.findByIdWithPostsAndLectureUnitsAndLearningGoals(lecture.getId());
        if (optionalLecture.isEmpty()) {
            return ResponseEntity.notFound().headers(HeaderUtil.createFailureAlert(applicationName, true,
                ENTITY_NAME, "internalServerError", "The lecture couldn't be retrieved.")).build();
        }

        Lecture originalLecture = optionalLecture.get();
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, originalLecture.getCourse(), null);

        // NOTE: Make sure that all references are preserved here
        lecture.setLectureUnits(originalLecture.getLectureUnits());

        Lecture result = lectureRepository.save(lecture);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, result.getId().toString())).body(result);
    }

    /**
     * Search for all lectures by title and course title. The result is pageable.
     *
     * @param search The pageable search containing the page size, page number and query string
     * @return The desired page, sorted and matching the given query
     */
    @GetMapping("lectures")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<SearchResultPageDTO<Lecture>> getAllLecturesOnPage(PageableSearchDTO<String> search) {
        final var user = userRepository.getUserWithGroupsAndAuthorities();
        return ResponseEntity.ok(lectureService.getAllOnPageWithSize(search, user));
    }

    /**
     * GET courses/:courseId/lectures : get all the lectures of a course for the course administration page
     *
     * @param withLectureUnits if set associated lecture units will also be loaded
     * @param courseId         the courseId of the course for which all lectures should be returned
     * @return the ResponseEntity with status 200 (OK) and the list of lectures in body
     */
    @GetMapping("courses/{courseId}/lectures")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<Set<Lecture>> getLecturesForCourse(@PathVariable Long courseId, @RequestParam(required = false, defaultValue = "false") boolean withLectureUnits) {
        log.debug("REST request to get all Lectures for the course with id : {}", courseId);

        Course course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, null);

        Set<Lecture> lectures;
        if (withLectureUnits) {
            lectures = lectureRepository.findAllByCourseIdWithAttachmentsAndLectureUnits(courseId);
        } else {
            lectures = lectureRepository.findAllByCourseIdWithAttachments(courseId);
        }

        return ResponseEntity.ok().body(lectures);
    }

    /**
     * GET lectures/:lectureId : get the "lectureId" lecture.
     *
     * @param lectureId the lectureId of the lecture to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the lecture, or with status 404 (Not Found)
     */
    @GetMapping("lectures/{lectureId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Lecture> getLecture(@PathVariable Long lectureId) {
        log.debug("REST request to get lecture {}", lectureId);
        Lecture lecture = lectureRepository.findByIdElseThrow(lectureId);
        authCheckService.checkHasAtLeastRoleForLectureElseThrow(Role.STUDENT, lecture, null);
        return ResponseEntity.ok(lecture);
    }

    /**
     * POST lectures/import/:sourceLectureId : Imports an existing lecture into an existing course
     * <p>
     * This will clone and import the whole lecture with associated lectureUnits and attachments.
     *
     * @param sourceLectureId The ID of the original lecture which should get imported
     * @param courseId        The ID of the course to import the lecture to
     * @return The imported lecture (200), a not found error (404) if the lecture does not exist,
     * or a forbidden error (403) if the user is not at least an editor in the source or target course.
     * @throws URISyntaxException When the URI of the response entity is invalid
     */
    @PostMapping("lectures/import/{sourceLectureId}")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<Lecture> importLecture(@PathVariable long sourceLectureId, @RequestParam(required = true) long courseId) throws URISyntaxException {
        final var user = userRepository.getUserWithGroupsAndAuthorities();
        final var sourceLecture = lectureRepository.findByIdWithLectureUnitsElseThrow(sourceLectureId);
        final var destinationCourse = courseRepository.findByIdWithLecturesElseThrow(courseId);

        Course course = sourceLecture.getCourse();
        if (course == null) {
            return ResponseEntity.badRequest().build();
        }

        // Check that the user is an editor in both the source and target course
        authCheckService.checkHasAtLeastRoleForLectureElseThrow(Role.EDITOR, sourceLecture, user);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, destinationCourse, user);

        final var result = lectureImportService.importLecture(sourceLecture, destinationCourse);
        return ResponseEntity.created(new URI("/api/lectures/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.getId().toString())).body(result);
    }

    /**
     * GET lectures/:lectureId/details : get the "lectureId" lecture.
     *
     * @param lectureId the lectureId of the lecture to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the lecture including posts, lecture units and learning goals, or with status 404 (Not Found)
     */
    @GetMapping("lectures/{lectureId}/details")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Lecture> getLectureWithDetails(@PathVariable Long lectureId) {
        log.debug("REST request to get lecture {} with details", lectureId);
        Lecture lecture = lectureRepository.findByIdWithPostsAndLectureUnitsAndLearningGoalsElseThrow(lectureId);
        Course course = lecture.getCourse();
        if (course == null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, false,
                Course.ENTITY_NAME, "courseDataNotSet", "The course data is not set")).body(null);

        }
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, null);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        lecture = lectureService.filterLectureContentForUser(lecture, user);

        return ResponseEntity.ok(lecture);
    }

    /**
     * GET lectures/:lectureId/title : Returns the title of the lecture with the given id
     *
     * @param lectureId the id of the lecture
     * @return the title of the lecture wrapped in an ResponseEntity or 404 Not Found if no lecture with that id exists
     */
    @GetMapping("lectures/{lectureId}/title")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<String> getLectureTitle(@PathVariable Long lectureId) {
        final var title = lectureRepository.getLectureTitle(lectureId);
        return title == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(title);
    }

    /**
     * DELETE lectures/:id : delete the "id" lecture.
     *
     * @param lectureId the id of the lecture to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("lectures/{lectureId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> deleteLecture(@PathVariable Long lectureId) {
        Optional<Lecture> optionalLecture = lectureRepository.findByIdWithPostsAndLectureUnitsAndLearningGoals(lectureId);
        if (optionalLecture.isEmpty()) {
            return ResponseEntity.notFound().headers(HeaderUtil.createFailureAlert(applicationName, true,
                ENTITY_NAME, "internalServerError", "The lecture couldn't be retrieved.")).build();
        }
        Lecture lecture = optionalLecture.get();
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, lecture.getCourse(), null);
        log.debug("REST request to delete Lecture : {}", lectureId);
        lectureService.delete(lecture);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true,
            ENTITY_NAME, lectureId.toString())).build();
    }
}
