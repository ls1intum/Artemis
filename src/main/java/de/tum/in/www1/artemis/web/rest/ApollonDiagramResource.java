package de.tum.in.www1.artemis.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.modeling.ApollonDiagram;
import de.tum.in.www1.artemis.repository.ApollonDiagramRepository;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

/**
 * REST controller for managing ApollonDiagram.
 */
@RestController
@RequestMapping("/api")
public class ApollonDiagramResource {

    private final Logger log = LoggerFactory.getLogger(ApollonDiagramResource.class);

    private static final String ENTITY_NAME = "apollonDiagram";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final ApollonDiagramRepository apollonDiagramRepository;

    private final AuthorizationCheckService authCheckService;

    private final CourseRepository courseRepository;

    public ApollonDiagramResource(ApollonDiagramRepository apollonDiagramRepository, AuthorizationCheckService authCheckService, CourseRepository courseRepository) {
        this.apollonDiagramRepository = apollonDiagramRepository;
        this.authCheckService = authCheckService;
        this.courseRepository = courseRepository;
    }

    /**
     * POST /course/{courseId}/apollon-diagrams : Create a new apollonDiagram.
     *
     * @param apollonDiagram the apollonDiagram to create
     * @param courseId the id of the current course
     * @return the ResponseEntity with status 201 (Created) and with body the new apollonDiagram, or with status 400 (Bad Request) if the apollonDiagram has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/course/{courseId}/apollon-diagrams")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<ApollonDiagram> createApollonDiagram(@RequestBody ApollonDiagram apollonDiagram, @PathVariable Long courseId) throws URISyntaxException {
        log.debug("REST request to save ApollonDiagram : {}", apollonDiagram);

        if (apollonDiagram.getId() != null) {
            throw new BadRequestAlertException("A new apollonDiagram cannot already have an ID", ENTITY_NAME, "idexists");
        }

        if (!Objects.equals(apollonDiagram.getCourseId(), courseId)) {
            throw new ConflictException("Specified course id does not match request payload", "ApollonDiagram", "courseMismatch");
        }

        Course course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, null);

        ApollonDiagram result = apollonDiagramRepository.save(apollonDiagram);
        return ResponseEntity.created(new URI("/api/apollon-diagrams/" + result.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.getId().toString())).body(result);
    }

    /**
     * PUT /course/{courseId}/apollon-diagrams : Updates an existing apollonDiagram.
     *
     * @param apollonDiagram the apollonDiagram to update
     * @param courseId the id of the current course
     * @return the ResponseEntity with status 200 (OK) and with body the updated apollonDiagram, or with status 201 (CREATED) if the apollonDiagram has not been created before, or with status
     *         500 (Internal Server Error) if the apollonDiagram couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/course/{courseId}/apollon-diagrams")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<ApollonDiagram> updateApollonDiagram(@RequestBody ApollonDiagram apollonDiagram, @PathVariable Long courseId) throws URISyntaxException {
        log.debug("REST request to update ApollonDiagram : {}", apollonDiagram);

        if (apollonDiagram.getId() == null) {
            return createApollonDiagram(apollonDiagram, courseId);
        }

        if (!Objects.equals(apollonDiagram.getCourseId(), courseId)) {
            throw new ConflictException("Specified course id does not match request payload", "ApollonDiagram", "courseMismatch");
        }
        Course course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, null);

        ApollonDiagram result = apollonDiagramRepository.save(apollonDiagram);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, apollonDiagram.getId().toString())).body(result);
    }

    /**
     * GET /apollon-diagrams/:diagramId/title : Returns the title of the diagram with the given id
     *
     * @param diagramId the id of the diagram
     * @return the title of the diagram wrapped in an ResponseEntity or 404 Not Found if no diagram with that id exists
     */
    @GetMapping(value = "/apollon-diagrams/{diagramId}/title")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<String> getDiagramTitle(@PathVariable Long diagramId) {
        final var title = apollonDiagramRepository.getDiagramTitle(diagramId);
        return title == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(title);
    }

    /**
     * GET /course/{courseId}/apollon-diagrams : get all the apollonDiagrams for current course.
     *
     * @param courseId id of current course
     * @return the ResponseEntity with status 200 (OK) and the list of apollonDiagrams in body
     */
    @GetMapping("/course/{courseId}/apollon-diagrams")
    @PreAuthorize("hasRole('TA')")
    public List<ApollonDiagram> getDiagramsByCourse(@PathVariable Long courseId) {
        log.debug("REST request to get ApollonDiagrams matching current course");

        Course course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, null);

        return apollonDiagramRepository.findDiagramsByCourseId(courseId);
    }

    /**
     * GET /course/{courseId}/apollon-diagrams/:apollonDiagramId : get the apollonDiagram for the given id
     *
     * @param apollonDiagramId the id of the apollonDiagram to retrieve
     * @param courseId the id of the current course
     * @return the ResponseEntity with status 200 (OK) and with body the apollonDiagram, or with status 404 (Not Found)
     */
    @GetMapping("/course/{courseId}/apollon-diagrams/{apollonDiagramId}")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<ApollonDiagram> getApollonDiagram(@PathVariable Long apollonDiagramId, @PathVariable Long courseId) {
        log.debug("REST request to get ApollonDiagram : {}", apollonDiagramId);
        ApollonDiagram apollonDiagram = apollonDiagramRepository.findByIdElseThrow(apollonDiagramId);

        Course course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, null);

        return ResponseEntity.ok().body(apollonDiagram);
    }

    /**
     * DELETE /course/{courseId}/apollon-diagrams/:apollonDiagramId : delete the apollonDiagram for the given id
     *
     * @param apollonDiagramId the id of the apollonDiagram to delete
     * @param courseId the id of the current course
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/course/{courseId}/apollon-diagrams/{apollonDiagramId}")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<Void> deleteApollonDiagram(@PathVariable Long apollonDiagramId, @PathVariable Long courseId) {
        log.debug("REST request to delete ApollonDiagram : {}", apollonDiagramId);

        ApollonDiagram apollonDiagram = apollonDiagramRepository.findByIdElseThrow(apollonDiagramId);

        Course course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, null);

        apollonDiagramRepository.delete(apollonDiagram);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, apollonDiagramId.toString())).build();
    }
}
