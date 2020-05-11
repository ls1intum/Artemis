package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.modeling.ApollonDiagram;
import de.tum.in.www1.artemis.repository.ApollonDiagramRepository;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.CourseService;
import de.tum.in.www1.artemis.service.UserService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import io.github.jhipster.web.util.ResponseUtil;

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

    private final UserService userService;

    private final CourseService courseService;

    public ApollonDiagramResource(ApollonDiagramRepository apollonDiagramRepository, AuthorizationCheckService authCheckService, UserService userService,
                                  CourseService courseService) {
        this.apollonDiagramRepository = apollonDiagramRepository;
        this.authCheckService = authCheckService;
        this.userService = userService;
        this.courseService = courseService;
    }

    private boolean hasAuthorties(Long courseId) {
        User currentUser = userService.getUserWithGroupsAndAuthorities();
        Course course = courseService.findOne(courseId);

        return authCheckService.isTeachingAssistantInCourse(course, currentUser) || authCheckService.isInstructorInCourse(course, currentUser) || authCheckService.isAdmin();
    }

    /**
     * POST /apollon-diagrams : Create a new apollonDiagram.
     *
     * @param apollonDiagram the apollonDiagram to create
     * @return the ResponseEntity with status 201 (Created) and with body the new apollonDiagram, or with status 400 (Bad Request) if the apollonDiagram has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/apollon-diagrams")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ApollonDiagram> createApollonDiagram(@RequestBody ApollonDiagram apollonDiagram) throws URISyntaxException {
        if (!hasAuthorties(apollonDiagram.getCourseId())) {
            return forbidden();
        }

        log.debug("REST request to save ApollonDiagram : {}", apollonDiagram);
        if (apollonDiagram.getId() != null) {
            throw new BadRequestAlertException("A new apollonDiagram cannot already have an ID", ENTITY_NAME, "idexists");
        }
        ApollonDiagram result = apollonDiagramRepository.save(apollonDiagram);
        return ResponseEntity.created(new URI("/api/apollon-diagrams/" + result.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.getId().toString())).body(result);
    }

    /**
     * PUT /apollon-diagrams : Updates an existing apollonDiagram.
     *
     * @param apollonDiagram the apollonDiagram to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated apollonDiagram, or with status 201 (CREATED) if the apollonDiagram has not been created before, or with status
     *         500 (Internal Server Error) if the apollonDiagram couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/apollon-diagrams")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ApollonDiagram> updateApollonDiagram(@RequestBody ApollonDiagram apollonDiagram) throws URISyntaxException {
        if (!hasAuthorties(apollonDiagram.getCourseId())) {
            return forbidden();
        }

        log.debug("REST request to update ApollonDiagram : {}", apollonDiagram);
        if (apollonDiagram.getId() == null) {
            return createApollonDiagram(apollonDiagram);
        }
        ApollonDiagram result = apollonDiagramRepository.save(apollonDiagram);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, apollonDiagram.getId().toString())).body(result);
    }

    /**
     * GET /apollon-diagrams/list/{courseId} : get all the apollonDiagrams for current course.
     *
     * @param courseId id of current course
     * @return the ResponseEntity with status 200 (OK) and the list of apollonDiagrams in body
     */
    @GetMapping("/apollon-diagrams/list/{courseId}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public List<ApollonDiagram> getDiagramsByCourse(@PathVariable Long courseId) {
        if (!hasAuthorties(courseId)) {
            return (List<ApollonDiagram>) forbidden();
        }

        log.debug("REST request to get ApollonDiagrams matching current course");
        List<ApollonDiagram> result = apollonDiagramRepository.findDiagramsByCourseId(courseId);
        // addAll(findDiagramsByCourseId(null)) because older diagrams have no course and might belong to this course; can be deleted in the future
        result.addAll(apollonDiagramRepository.findDiagramsByCourseId(null));
        return result;
    }

    /**
     * GET /apollon-diagrams/:id : get the "id" apollonDiagram.
     *
     * @param id the id of the apollonDiagram to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the apollonDiagram, or with status 404 (Not Found)
     */
    @GetMapping("/apollon-diagrams/{id}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ApollonDiagram> getApollonDiagram(@PathVariable Long id) {
        log.debug("REST request to get ApollonDiagram : {}", id);
        Optional<ApollonDiagram> apollonDiagram = apollonDiagramRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(apollonDiagram);
    }

    /**
     * DELETE /apollon-diagrams/:id : delete the "id" apollonDiagram.
     *
     * @param id the id of the apollonDiagram to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/apollon-diagrams/{id}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> deleteApollonDiagram(@PathVariable Long id) {
        log.debug("REST request to delete ApollonDiagram : {}", id);
        apollonDiagramRepository.deleteById(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString())).build();
    }
}
