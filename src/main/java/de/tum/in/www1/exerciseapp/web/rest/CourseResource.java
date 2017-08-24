package de.tum.in.www1.exerciseapp.web.rest;

import com.codahale.metrics.annotation.Timed;
import de.tum.in.www1.exerciseapp.domain.Course;
import de.tum.in.www1.exerciseapp.repository.CourseRepository;
import de.tum.in.www1.exerciseapp.service.CourseService;
import de.tum.in.www1.exerciseapp.web.rest.util.HeaderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;

/**
 * REST controller for managing Course.
 */
@RestController
@RequestMapping({"/api", "/api_basic"})
@PreAuthorize("hasRole('ADMIN')")
public class CourseResource {

    private final Logger log = LoggerFactory.getLogger(CourseResource.class);

    private static final String ENTITY_NAME = "course";

    private final CourseRepository courseRepository;
    private final CourseService courseService;

    public CourseResource(CourseRepository courseRepository, CourseService courseService) {
        this.courseRepository = courseRepository;
        this.courseService = courseService;
    }

    /**
     * POST  /courses : Create a new course.
     *
     * @param course the course to create
     * @return the ResponseEntity with status 201 (Created) and with body the new course, or with status 400 (Bad Request) if the course has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/courses")
    @Timed
    public ResponseEntity<Course> createCourse(@RequestBody Course course) throws URISyntaxException {
        log.debug("REST request to save Course : {}", course);
        if (course.getId() != null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "idexists", "A new course cannot already have an ID")).body(null);
        }
        Course result = courseService.save(course);
        return ResponseEntity.created(new URI("/api/courses/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    /**
     * PUT  /courses : Updates an existing course.
     *
     * @param course the course to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated course,
     * or with status 400 (Bad Request) if the course is not valid,
     * or with status 500 (Internal Server Error) if the course couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/courses")
    @Timed
    public ResponseEntity<Course> updateCourse(@RequestBody Course course) throws URISyntaxException {
        log.debug("REST request to update Course : {}", course);
        if (course.getId() == null) {
            return createCourse(course);
        }
        Course result = courseService.save(course);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, course.getId().toString()))
            .body(result);
    }

    /**
     * GET  /courses : get all the courses.
     *
     * @return the ResponseEntity with status 200 (OK) and the list of courses in body
     */
    @RequestMapping(value = "/courses",
        method = RequestMethod.GET,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Timed
    public List<Course> getAllCourses() {
        log.debug("REST request to get all Courses");
        return courseService.findAll();
    }

    /**
     * GET  /courses/:id : get the "id" course.
     *
     * @param id the id of the course to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the course, or with status 404 (Not Found)
     */
    @RequestMapping(value = "/courses/{id}",
        method = RequestMethod.GET,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Timed
    public ResponseEntity<Course> getCourse(@PathVariable Long id) {
        log.debug("REST request to get Course : {}", id);
        Course course = courseService.findOne(id);
        return Optional.ofNullable(course)
            .map(result -> new ResponseEntity<>(
                result,
                HttpStatus.OK))
            .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    /**
     * DELETE  /courses/:id : delete the "id" course.
     *
     * @param id the id of the course to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/courses/{id}")
    @Timed
    public ResponseEntity<Void> deleteCourse(@PathVariable Long id) {
        log.debug("REST request to delete Course : {}", id);
        courseRepository.delete(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString())).build();
    }
}
