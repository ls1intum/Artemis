package de.tum.in.www1.artemis.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.ws.rs.BadRequestException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.TutorialGroupRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.TutorialGroupService;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;

@RestController
@RequestMapping("/api")
public class TutorialGroupResource {

    private final Logger log = LoggerFactory.getLogger(TutorialGroupResource.class);

    private final TutorialGroupService tutorialGroupService;

    private final TutorialGroupRepository tutorialGroupRepository;

    private final CourseRepository courseRepository;

    private final UserRepository userRepository;

    private final AuthorizationCheckService authorizationCheckService;

    public TutorialGroupResource(AuthorizationCheckService authorizationCheckService, UserRepository userRepository, CourseRepository courseRepository,
            TutorialGroupService tutorialGroupService, TutorialGroupRepository tutorialGroupRepository) {
        this.tutorialGroupService = tutorialGroupService;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.tutorialGroupRepository = tutorialGroupRepository;
    }

    /**
     * GET courses/:courseId/tutorial-groups: gets the tutorial groups of the specified course.
     *
     * @param courseId the id of the course to which the tutorial groups belong to
     * @return the ResponseEntity with status 200 (OK) and with body containing the tutorial groups of the course
     */
    @GetMapping("/courses/{courseId}/tutorial-groups")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<List<TutorialGroup>> getTutorialGroups(@PathVariable Long courseId) {
        log.debug("REST request to get tutorial groups for course with id: {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();

        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);

        Set<TutorialGroup> tutorialGroups = tutorialGroupRepository.findAllByCourseIdWithTeachingAssistantAndRegisteredStudents(courseId);

        return ResponseEntity.ok(new ArrayList<>(tutorialGroups));
    }

    /**
     * POST /courses/:courseId/tutorial-groups : creates a new tutorial group.
     *
     * @param courseId      the id of the course to which the tutorial group should be added
     * @param tutorialGroup the tutorial group that should be created
     * @return the ResponseEntity with status 201 (Created) and with body the new tutorial group
     */
    @PostMapping("/courses/{courseId}/tutorial-groups")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<TutorialGroup> createTutorialGroup(@PathVariable Long courseId, @RequestBody TutorialGroup tutorialGroup) throws URISyntaxException {
        log.debug("REST request to create TutorialGroup : {}", tutorialGroup);
        if (tutorialGroup.getId() != null) {
            throw new BadRequestException();
        }

        Course course = courseRepository.findByIdElseThrow(courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);

        tutorialGroup.setCourse(course);
        TutorialGroup persistedTutorialGroup = tutorialGroupRepository.save(tutorialGroup);

        return ResponseEntity.created(new URI("/api/tutorial-groups/" + persistedTutorialGroup.getId())).body(persistedTutorialGroup);
    }

    /**
     * GET /courses/:courseId/tutorial-groups/:tutorialGroupId : gets the tutorial group with the specified id.
     *
     * @param tutorialGroupId the id of the tutorial group to retrieve
     * @param courseId        the id of the course to which the tutorial group belongs
     * @return the ResponseEntity with status 200 (OK) and with body the tutorial group, or with status 404 (Not Found)
     */
    @GetMapping("/courses/{courseId}/tutorial-groups/{tutorialGroupId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<TutorialGroup> getTutorialGroup(@PathVariable Long tutorialGroupId, @PathVariable Long courseId) {
        log.debug("REST request to get Tutorial Group : {}", tutorialGroupId);

        TutorialGroup tutorialGroup = tutorialGroupRepository.findByIdWithTeachingAssistantAndRegisteredStudentsElseThrow(tutorialGroupId);
        if (tutorialGroup.getCourse() == null) {
            throw new ConflictException("A tutorial group must belong to a course", "TutorialGroup", "tutorialGroupNoCourse");
        }
        if (!tutorialGroup.getCourse().getId().equals(courseId)) {
            throw new ConflictException("The tutorial group does not belong to the correct course", "TutorialGroup", "tutorialGroupWrongCourse");
        }
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, tutorialGroup.getCourse(), null);

        return ResponseEntity.ok().body(tutorialGroup);
    }

    /**
     * PUT /courses/:courseId/tutorial-groups : Updates an existing tutorial group
     *
     * @param courseId      the id of the course to which the tutorial group belongs
     * @param tutorialGroup group the tutorial group to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated tutorial group
     */
    @PutMapping("/courses/{courseId}/tutorial-groups")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<TutorialGroup> updateTutorialGroup(@PathVariable Long courseId, @RequestBody TutorialGroup tutorialGroup) {
        log.debug("REST request to update TutorialGroup : {}", tutorialGroup);
        if (tutorialGroup.getId() == null) {
            throw new BadRequestException();
        }
        var tutorialGroupFromDatabase = this.tutorialGroupRepository.findByIdWithTeachingAssistantAndRegisteredStudentsElseThrow(tutorialGroup.getId());
        if (tutorialGroupFromDatabase.getCourse() == null || !tutorialGroupFromDatabase.getCourse().getId().equals(courseId)) {
            throw new BadRequestException();
        }
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, tutorialGroupFromDatabase.getCourse(), null);
        tutorialGroupFromDatabase.setTitle(tutorialGroup.getTitle());
        tutorialGroupFromDatabase.setTeachingAssistant(tutorialGroup.getTeachingAssistant());
        tutorialGroupFromDatabase.setRegisteredStudents(tutorialGroup.getRegisteredStudents());

        TutorialGroup updatedTutorialGroup = tutorialGroupRepository.save(tutorialGroupFromDatabase);

        return ResponseEntity.ok(updatedTutorialGroup);
    }

}
