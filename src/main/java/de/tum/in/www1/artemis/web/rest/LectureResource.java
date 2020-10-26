package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.lecture_unit.LectureUnit;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.CourseService;
import de.tum.in.www1.artemis.service.LectureService;
import de.tum.in.www1.artemis.service.UserService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

/**
 * REST controller for managing Lecture.
 */
@RestController
@RequestMapping("/api")
public class LectureResource {

    private final Logger log = LoggerFactory.getLogger(LectureResource.class);

    private static final String ENTITY_NAME = "lecture";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final LectureRepository lectureRepository;

    private final LectureService lectureService;

    private final CourseService courseService;

    private final AuthorizationCheckService authCheckService;

    private final UserService userService;

    public LectureResource(LectureRepository lectureRepository, LectureService lectureService, CourseService courseService, UserService userService,
            AuthorizationCheckService authCheckService) {
        this.lectureRepository = lectureRepository;
        this.lectureService = lectureService;
        this.courseService = courseService;
        this.userService = userService;
        this.authCheckService = authCheckService;
    }

    /**
     * POST /lectures : Create a new lecture.
     *
     * @param lecture the lecture to create
     * @return the ResponseEntity with status 201 (Created) and with body the new lecture, or with status 400 (Bad Request) if the lecture has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/lectures")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Lecture> createLecture(@RequestBody Lecture lecture) throws URISyntaxException {
        log.debug("REST request to save Lecture : {}", lecture);
        if (lecture.getId() != null) {
            throw new BadRequestAlertException("A new lecture cannot already have an ID", ENTITY_NAME, "idexists");
        }
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isInstructorInCourse(lecture.getCourse(), user) && !authCheckService.isAdmin(user)) {
            return forbidden();
        }
        Lecture result = lectureRepository.save(lecture);
        return ResponseEntity.created(new URI("/api/lectures/" + result.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.getId().toString())).body(result);
    }

    /**
     * PUT /lectures : Updates an existing lecture.
     *
     * @param lecture the lecture to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated lecture, or with status 400 (Bad Request) if the lecture is not valid, or with status 500 (Internal
     *         Server Error) if the lecture couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/lectures")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Lecture> updateLecture(@RequestBody Lecture lecture) throws URISyntaxException {
        log.debug("REST request to update Lecture : {}", lecture);
        if (lecture.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isInstructorInCourse(lecture.getCourse(), user) && !authCheckService.isAdmin(user)) {
            return forbidden();
        }

        // Make sure that the original references are preserved.
        Lecture originalLecture = lectureRepository.findByIdWithStudentQuestionsAndLectureUnits(lecture.getId()).get();

        // NOTE: Make sure that all references are preserved here
        lecture.setLectureUnits(originalLecture.getLectureUnits());

        Lecture result = lectureRepository.save(lecture);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, lecture.getId().toString())).body(result);
    }

    /**
     * GET /courses/:courseId/lectures : get all the lectures of a course for the course administration page
     *
     * @param courseId the courseId of the course for which all lectures should be returned
     * @return the ResponseEntity with status 200 (OK) and the list of lectures in body
     */
    @GetMapping(value = "/courses/{courseId}/lectures")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Set<Lecture>> getLecturesForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all Lectures for the course with id : {}", courseId);

        User user = userService.getUserWithGroupsAndAuthorities();
        Course course = courseService.findOne(courseId);
        if (!authCheckService.isInstructorInCourse(course, user) && !authCheckService.isAdmin(user)) {
            return forbidden();
        }
        return ResponseEntity.ok().body(lectureService.findAllByCourseId(courseId));
    }

    /**
     * GET /lectures/:id : get the "id" lecture.
     *
     * @param id the id of the lecture to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the lecture, or with status 404 (Not Found)
     */
    @GetMapping("/lectures/{id}")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Lecture> getLecture(@PathVariable Long id) {
        log.debug("REST request to get Lecture : {}", id);
        Optional<Lecture> lectureOptional = lectureRepository.findByIdWithStudentQuestionsAndLectureUnits(id);
        if (lectureOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Lecture lecture = lectureOptional.get();
        Course course = lecture.getCourse();
        if (course == null) {
            return ResponseEntity.badRequest().build();
        }
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isAtLeastStudentInCourse(course, user)) {
            return forbidden();
        }

        List<LectureUnit> lectureUnitsStudentIsAllowedToSee = lecture.getLectureUnits().stream()
                .filter(lectureUnit -> authCheckService.isAllowedToSeeLectureUnit(lectureUnit, user)).collect(Collectors.toList());
        lecture.setLectureUnits(lectureUnitsStudentIsAllowedToSee);
        return ResponseEntity.ok(lecture);
    }

    /**
     * DELETE /lectures/:id : delete the "id" lecture.
     *
     * @param id the id of the lecture to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/lectures/{id}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> deleteLecture(@PathVariable Long id) {
        User user = userService.getUserWithGroupsAndAuthorities();
        Optional<Lecture> optionalLecture = lectureRepository.findById(id);
        if (optionalLecture.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Lecture lecture = optionalLecture.get();
        if (!authCheckService.isInstructorInCourse(lecture.getCourse(), user) && !authCheckService.isAdmin(user)) {
            return forbidden();
        }
        Course course = lecture.getCourse();
        if (course == null) {
            return ResponseEntity.badRequest().build();
        }
        log.debug("REST request to delete Lecture : {}", id);
        lectureService.delete(lecture);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString())).build();
    }
}
