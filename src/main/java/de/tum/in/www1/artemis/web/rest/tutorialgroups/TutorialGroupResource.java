package de.tum.in.www1.artemis.web.rest.tutorialgroups;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.validation.Valid;
import javax.ws.rs.BadRequestException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroup;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.TutorialGroupService;
import de.tum.in.www1.artemis.service.dto.StudentDTO;
import de.tum.in.www1.artemis.service.feature.Feature;
import de.tum.in.www1.artemis.service.feature.FeatureToggle;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

@RestController
@RequestMapping("/api")
public class TutorialGroupResource {

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private static final String ENTITY_NAME = "tutorialGroup";

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
     * GET /tutorial-groups/:tutorialGroupId/title : Returns the title of the tutorial-group with the given id
     *
     * @param tutorialGroupId the id of the tutorial group
     * @return ResponseEntity with status 200 (OK) and with body containing the title of the tutorial group
     */
    @GetMapping("/tutorial-groups/{tutorialGroupId}/title")
    @PreAuthorize("hasRole('USER')")
    @FeatureToggle(Feature.TutorialGroups)
    public ResponseEntity<String> getTitle(@PathVariable Long tutorialGroupId) {
        final var title = tutorialGroupRepository.getTutorialGroupTitle(tutorialGroupId);
        return title == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(title);
    }

    /**
     * GET courses/:courseId/tutorial-groups: gets the tutorial groups of the specified course.
     *
     * @param courseId the id of the course to which the tutorial groups belong to
     * @return the ResponseEntity with status 200 (OK) and with body containing the tutorial groups of the course
     */
    @GetMapping("/courses/{courseId}/tutorial-groups")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @FeatureToggle(Feature.TutorialGroups)
    public ResponseEntity<List<TutorialGroup>> getAllOfCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all tutorial groups of course with id: {}", courseId);
        var course = courseRepository.findByIdElseThrow(courseId);
        var user = userRepository.getUserWithGroupsAndAuthorities();

        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, user);

        // ToDo: Optimization Idea: Do not send all registered student information but just the number in a DTO
        var tutorialGroups = tutorialGroupRepository.findAllByCourseIdWithTeachingAssistantAndRegistrations(courseId);

        return ResponseEntity.ok(new ArrayList<>(tutorialGroups));
    }

    /**
     * GET /tutorial-groups/:tutorialGroupId : gets the tutorial group with the specified id.
     *
     * @param tutorialGroupId the id of the tutorial group to retrieve
     * @return ResponseEntity with status 200 (OK) and with body the tutorial group
     */
    @GetMapping("/tutorial-groups/{tutorialGroupId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @FeatureToggle(Feature.TutorialGroups)
    public ResponseEntity<TutorialGroup> getOne(@PathVariable Long tutorialGroupId) {
        log.debug("REST request to get tutorial group: {}", tutorialGroupId);
        var tutorialGroup = tutorialGroupRepository.findByIdWithTeachingAssistantAndRegistrationsElseThrow(tutorialGroupId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, tutorialGroup.getCourse(), null);

        return ResponseEntity.ok().body(tutorialGroup);
    }

    /**
     * POST /courses/:courseId/tutorial-groups : creates a new tutorial group.
     *
     * @param courseId      the id of the course to which the tutorial group should be added
     * @param tutorialGroup the tutorial group that should be created
     * @return ResponseEntity with status 201 (Created) and in the body the new tutorial group
     */
    @PostMapping("/courses/{courseId}/tutorial-groups")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @FeatureToggle(Feature.TutorialGroups)
    public ResponseEntity<TutorialGroup> create(@PathVariable Long courseId, @RequestBody @Valid TutorialGroup tutorialGroup) throws URISyntaxException {
        log.debug("REST request to create TutorialGroup: {} in course: {}", tutorialGroup, courseId);
        if (tutorialGroup.getId() != null) {
            throw new BadRequestException("A new tutorial group cannot already have an ID");
        }

        var course = courseRepository.findByIdElseThrow(courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        tutorialGroup.setCourse(course);

        trimStringFields(tutorialGroup);
        checkTitleIsUnique(tutorialGroup);
        TutorialGroup persistedTutorialGroup = tutorialGroupRepository.save(tutorialGroup);

        return ResponseEntity.created(new URI("/api/tutorial-groups/" + persistedTutorialGroup.getId())).body(persistedTutorialGroup);
    }

    /**
     * DELETE tutorial-groups/:tutorialGroupId : delete a tutorial group.
     *
     * @param tutorialGroupId the id of the tutorial group to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/tutorial-groups/{tutorialGroupId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @FeatureToggle(Feature.TutorialGroups)
    public ResponseEntity<Void> delete(@PathVariable Long tutorialGroupId) {
        log.info("REST request to delete a TutorialGroup : {}", tutorialGroupId);
        var tutorialGroupFromDatabase = this.tutorialGroupRepository.findByIdWithTeachingAssistantAndRegistrationsElseThrow(tutorialGroupId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, tutorialGroupFromDatabase.getCourse(), null);
        tutorialGroupRepository.deleteById(tutorialGroupFromDatabase.getId());
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, tutorialGroupFromDatabase.getTitle())).build();
    }

    /**
     * PUT /tutorial-groups : Updates an existing tutorial group
     *
     * @param tutorialGroup group the tutorial group to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated tutorial group
     */
    @PutMapping("/tutorial-groups")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @FeatureToggle(Feature.TutorialGroups)
    public ResponseEntity<TutorialGroup> update(@RequestBody @Valid TutorialGroup tutorialGroup) {
        log.debug("REST request to update TutorialGroup : {}", tutorialGroup);
        if (tutorialGroup.getId() == null) {
            throw new BadRequestException("A tutorial group cannot be updated without an id");
        }
        var tutorialGroupFromDatabase = this.tutorialGroupRepository.findByIdWithTeachingAssistantAndRegistrationsElseThrow(tutorialGroup.getId());
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, tutorialGroupFromDatabase.getCourse(), null);

        trimStringFields(tutorialGroup);
        if (!tutorialGroupFromDatabase.getTitle().equals(tutorialGroup.getTitle())) {
            checkTitleIsUnique(tutorialGroup);
        }
        overrideValues(tutorialGroup, tutorialGroupFromDatabase);

        var updatedTutorialGroup = tutorialGroupRepository.save(tutorialGroupFromDatabase);
        return ResponseEntity.ok(updatedTutorialGroup);
    }

    /**
     * DELETE tutorial-groups/:tutorialGroupId/deregister/:studentLogin : deregister a student from a tutorial group.
     *
     * @param tutorialGroupId the id of the tutorial group
     * @param studentLogin    the login of the student to deregister
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/tutorial-groups/{tutorialGroupId}/deregister/{studentLogin:" + Constants.LOGIN_REGEX + "}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @FeatureToggle(Feature.TutorialGroups)
    public ResponseEntity<Void> deregisterStudent(@PathVariable Long tutorialGroupId, @PathVariable String studentLogin) {
        log.debug("REST request to deregister {} student from tutorial group : {}", studentLogin, tutorialGroupId);
        var tutorialGroupFromDatabase = this.tutorialGroupRepository.findByIdElseThrow(tutorialGroupId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, tutorialGroupFromDatabase.getCourse(), null);

        Optional<User> studentToDeregister = userRepository.findOneWithGroupsAndAuthoritiesByLogin(studentLogin);
        if (studentToDeregister.isEmpty()) {
            throw new EntityNotFoundException("User", studentLogin);
        }
        tutorialGroupService.deregisterStudent(studentToDeregister.get(), tutorialGroupFromDatabase);
        return ResponseEntity.ok().body(null);
    }

    /**
     * POST tutorial-groups/:tutorialGroupId/register/:studentLogin : register a student to a tutorial group.
     *
     * @param tutorialGroupId the id of the tutorial group
     * @param studentLogin    the login of the student to register
     * @return the ResponseEntity with status 200 (OK)
     */
    @PostMapping("/tutorial-groups/{tutorialGroupId}/register/{studentLogin:" + Constants.LOGIN_REGEX + "}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @FeatureToggle(Feature.TutorialGroups)
    public ResponseEntity<Void> registerStudent(@PathVariable Long tutorialGroupId, @PathVariable String studentLogin) {
        log.debug("REST request to register {} student to tutorial group : {}", studentLogin, tutorialGroupId);
        var tutorialGroupFromDatabase = this.tutorialGroupRepository.findByIdElseThrow(tutorialGroupId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, tutorialGroupFromDatabase.getCourse(), null);

        Optional<User> userToRegister = userRepository.findOneWithGroupsAndAuthoritiesByLogin(studentLogin);
        if (userToRegister.isEmpty()) {
            throw new EntityNotFoundException("User", studentLogin);
        }
        var student = userToRegister.get();
        if (!student.getGroups().contains(tutorialGroupFromDatabase.getCourse().getStudentGroupName())) {
            throw new BadRequestAlertException("The user is not a student of the course", ENTITY_NAME, "userNotPartOfCourse");
        }

        tutorialGroupService.registerStudent(userToRegister.get(), tutorialGroupFromDatabase);
        return ResponseEntity.ok().body(null);
    }

    /**
     * POST tutorial-groups/:tutorialGroupId/register-multiple/" : Register multiple users to the tutorial group
     *
     * @param tutorialGroupId the id of the tutorial group to which the users should be registered to
     * @param studentDtos     the list of students who should be registered to the tutorial group
     * @return the list of students who could not be registered for the tutorial group, because they could NOT be found in the Artemis database as students of the tutorial group course
     */
    @PostMapping("/tutorial-groups/{tutorialGroupId}/register-multiple")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @FeatureToggle(Feature.TutorialGroups)
    public ResponseEntity<Set<StudentDTO>> registerMultipleStudentsToTutorialGroup(@PathVariable Long tutorialGroupId, @RequestBody Set<StudentDTO> studentDtos) {
        log.debug("REST request to register {} to tutorial group {}", studentDtos, tutorialGroupId);
        var tutorialGroupFromDatabase = this.tutorialGroupRepository.findByIdElseThrow(tutorialGroupId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, tutorialGroupFromDatabase.getCourse(), null);
        Set<StudentDTO> notFoundStudentsDtos = tutorialGroupService.registerMultipleStudents(tutorialGroupFromDatabase, studentDtos);
        return ResponseEntity.ok().body(notFoundStudentsDtos);
    }

    private void trimStringFields(TutorialGroup tutorialGroup) {
        if (tutorialGroup.getTitle() != null) {
            tutorialGroup.setTitle(tutorialGroup.getTitle().trim());
        }
        if (tutorialGroup.getAdditionalInformation() != null) {
            tutorialGroup.setAdditionalInformation(tutorialGroup.getAdditionalInformation().trim());
        }
    }

    private void checkTitleIsUnique(TutorialGroup tutorialGroup) {
        if (tutorialGroupRepository.findByTitle(tutorialGroup.getTitle()).isPresent()) {
            throw new BadRequestException("A tutorial group with this title already exists in the course.");
        }
    }

    private static void overrideValues(TutorialGroup sourceTutorialGroup, TutorialGroup originalTutorialGroup) {
        originalTutorialGroup.setTitle(sourceTutorialGroup.getTitle());
        originalTutorialGroup.setTeachingAssistant(sourceTutorialGroup.getTeachingAssistant());
        originalTutorialGroup.setAdditionalInformation(sourceTutorialGroup.getAdditionalInformation());
        originalTutorialGroup.setCapacity(sourceTutorialGroup.getCapacity());
        originalTutorialGroup.setOnline(sourceTutorialGroup.getOnline());
        originalTutorialGroup.setLanguage(sourceTutorialGroup.getLanguage());
        originalTutorialGroup.setLocation(sourceTutorialGroup.getLocation());
    }

}
