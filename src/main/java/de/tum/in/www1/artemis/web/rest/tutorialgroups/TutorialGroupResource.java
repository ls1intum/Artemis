package de.tum.in.www1.artemis.web.rest.tutorialgroups;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

import javax.annotation.Nullable;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.ws.rs.BadRequestException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.tutorialgroups.TutorialGroupRegistrationType;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroup;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupSchedule;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupsConfiguration;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupNotificationRepository;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupRepository;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupsConfigurationRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.dto.StudentDTO;
import de.tum.in.www1.artemis.service.feature.Feature;
import de.tum.in.www1.artemis.service.feature.FeatureToggle;
import de.tum.in.www1.artemis.service.notifications.SingleUserNotificationService;
import de.tum.in.www1.artemis.service.notifications.TutorialGroupNotificationService;
import de.tum.in.www1.artemis.service.tutorialgroups.TutorialGroupScheduleService;
import de.tum.in.www1.artemis.service.tutorialgroups.TutorialGroupService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@RestController
@RequestMapping("/api")
public class TutorialGroupResource {

    private static final String TITLE_REGEX = "^[a-zA-Z0-9]{1}[a-zA-Z0-9- ]{0,19}$";

    public static final String ENTITY_NAME = "tutorialGroup";

    private final Logger log = LoggerFactory.getLogger(TutorialGroupResource.class);

    private final TutorialGroupService tutorialGroupService;

    private final TutorialGroupRepository tutorialGroupRepository;

    private final CourseRepository courseRepository;

    private final UserRepository userRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final TutorialGroupNotificationService tutorialGroupNotificationService;

    private final TutorialGroupNotificationRepository tutorialGroupNotificationRepository;

    private final SingleUserNotificationService singleUserNotificationService;

    private final TutorialGroupsConfigurationRepository tutorialGroupsConfigurationRepository;

    private final TutorialGroupScheduleService tutorialGroupScheduleService;

    public TutorialGroupResource(AuthorizationCheckService authorizationCheckService, UserRepository userRepository, CourseRepository courseRepository,
            TutorialGroupService tutorialGroupService, TutorialGroupRepository tutorialGroupRepository, TutorialGroupNotificationService tutorialGroupNotificationService,
            TutorialGroupNotificationRepository tutorialGroupNotificationRepository, SingleUserNotificationService singleUserNotificationService,
            TutorialGroupsConfigurationRepository tutorialGroupsConfigurationRepository, TutorialGroupScheduleService tutorialGroupScheduleService) {
        this.tutorialGroupService = tutorialGroupService;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.tutorialGroupRepository = tutorialGroupRepository;
        this.tutorialGroupNotificationService = tutorialGroupNotificationService;
        this.tutorialGroupNotificationRepository = tutorialGroupNotificationRepository;
        this.singleUserNotificationService = singleUserNotificationService;
        this.tutorialGroupsConfigurationRepository = tutorialGroupsConfigurationRepository;
        this.tutorialGroupScheduleService = tutorialGroupScheduleService;
    }

    /**
     * GET /tutorial-groups/for-notifications
     *
     * @return the list of tutorial groups for which the current user should receive notifications
     */
    @GetMapping("/tutorial-groups/for-notifications")
    @PreAuthorize("hasRole('USER')")
    public List<TutorialGroup> getAllTutorialGroupsForNotifications() {
        log.debug("REST request to get all tutorial groups for which the current user should receive notifications");
        User user = userRepository.getUserWithGroupsAndAuthorities();
        return tutorialGroupService.findAllForNotifications(user);
    }

    /**
     * GET /tutorial-groups/:tutorialGroupId/title : Returns the title of the tutorial-group with the given id
     * <p>
     * NOTE: Used by entity-title service in the client to resolve the title of a tutorial group for breadcrumbs
     *
     * @param tutorialGroupId the id of the tutorial group
     * @return ResponseEntity with status 200 (OK) and with body containing the title of the tutorial group
     */
    @GetMapping("/tutorial-groups/{tutorialGroupId}/title")
    @PreAuthorize("hasRole('USER')")
    @FeatureToggle(Feature.TutorialGroups)
    public ResponseEntity<String> getTitle(@PathVariable Long tutorialGroupId) {
        log.debug("REST request to get title of TutorialGroup : {}", tutorialGroupId);
        return tutorialGroupRepository.getTutorialGroupTitle(tutorialGroupId).map(ResponseEntity::ok)
                .orElseThrow(() -> new EntityNotFoundException("TutorialGroup", tutorialGroupId));
    }

    /**
     * GET /courses/:courseId/tutorial-groups/campus-values : gets the campus values used for the tutorial groups of the course with the given id
     * Note: Used for autocomplete in the client tutorial form
     *
     * @param courseId the id of the course to which the tutorial groups belong to
     * @return ResponseEntity with status 200 (OK) and with body containing the unique campus values of the tutorial groups of the course
     */
    @GetMapping("/courses/{courseId}/tutorial-groups/campus-values")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @FeatureToggle(Feature.TutorialGroups)
    public ResponseEntity<Set<String>> getUniqueCampusValues(@PathVariable Long courseId) {
        log.debug("REST request to get unique campus values used for tutorial groups in course : {}", courseId);
        var course = courseRepository.findByIdElseThrow(courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        return ResponseEntity.ok(tutorialGroupRepository.findAllUniqueCampusValuesInCourse(courseId));
    }

    /**
     * GET /courses/:courseId/tutorial-groups: gets the tutorial groups of the specified course.
     *
     * @param courseId the id of the course to which the tutorial groups belong to
     * @return the ResponseEntity with status 200 (OK) and with body containing the tutorial groups of the course
     */
    @GetMapping("/courses/{courseId}/tutorial-groups")
    @PreAuthorize("hasRole('USER')")
    @FeatureToggle(Feature.TutorialGroups)
    public ResponseEntity<List<TutorialGroup>> getAllForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all tutorial groups of course with id: {}", courseId);
        var course = courseRepository.findByIdElseThrow(courseId);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);
        // ToDo: Optimization Idea: Do not send all registered student information but just the number in a DTO
        var tutorialGroups = tutorialGroupService.findAllForCourse(course, user);
        return ResponseEntity.ok(new ArrayList<>(tutorialGroups));
    }

    /**
     * GET /courses/{courseId}/tutorial-groups/:tutorialGroupId : gets the tutorial group with the specified id.
     *
     * @param courseId        the id of the course to which the tutorial group belongs to
     * @param tutorialGroupId the id of the tutorial group to retrieve
     * @return ResponseEntity with status 200 (OK) and with body the tutorial group
     */
    @GetMapping("/courses/{courseId}/tutorial-groups/{tutorialGroupId}")
    @PreAuthorize("hasRole('USER')")
    @FeatureToggle(Feature.TutorialGroups)
    public ResponseEntity<TutorialGroup> getOneOfCourse(@PathVariable Long courseId, @PathVariable Long tutorialGroupId) {
        log.debug("REST request to get tutorial group: {} of course: {}", tutorialGroupId, courseId);
        var course = courseRepository.findByIdElseThrow(courseId);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);
        var tutorialGroup = tutorialGroupService.getOneOfCourse(course, user, tutorialGroupId);
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
        var responsibleUser = userRepository.getUserWithGroupsAndAuthorities();
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, responsibleUser);

        Optional<TutorialGroupsConfiguration> configurationOptional = tutorialGroupsConfigurationRepository.findByCourseIdWithEagerTutorialGroupFreePeriods(courseId);
        if (configurationOptional.isEmpty()) {
            throw new BadRequestException("The course has no tutorial groups configuration");
        }
        var configuration = configurationOptional.get();
        if (configuration.getCourse().getTimeZone() == null) {
            throw new BadRequestException("The course has no time zone");
        }

        tutorialGroup.setCourse(course);
        trimStringFields(tutorialGroup);
        checkTitleIsValid(tutorialGroup);

        // persist first without schedule
        TutorialGroupSchedule tutorialGroupSchedule = tutorialGroup.getTutorialGroupSchedule();
        tutorialGroup.setTutorialGroupSchedule(null);
        TutorialGroup persistedTutorialGroup = tutorialGroupRepository.save(tutorialGroup);

        // persist the schedule and generate the sessions
        if (tutorialGroupSchedule != null) {
            tutorialGroupScheduleService.saveScheduleAndGenerateScheduledSessions(configuration, persistedTutorialGroup, tutorialGroupSchedule);
            persistedTutorialGroup.setTutorialGroupSchedule(tutorialGroupSchedule);
        }

        if (tutorialGroup.getTeachingAssistant() != null) {
            // Note: We have to load the teaching assistants from database otherwise languageKey is not defined and email sending fails
            var taFromDatabase = userRepository.findOneByLogin(tutorialGroup.getTeachingAssistant().getLogin());
            taFromDatabase.ifPresent(user -> singleUserNotificationService.notifyTutorAboutAssignmentToTutorialGroup(persistedTutorialGroup, user, responsibleUser));
        }

        return ResponseEntity.created(new URI("/api/courses/" + courseId + "/tutorial-groups/" + persistedTutorialGroup.getId()))
                .body(TutorialGroup.preventCircularJsonConversion(persistedTutorialGroup));
    }

    /**
     * DELETE /courses/:courseId/tutorial-groups/:tutorialGroupId : delete a tutorial group.
     *
     * @param courseId        the id of the course to which the tutorial group belongs to
     * @param tutorialGroupId the id of the tutorial group to delete
     * @return the ResponseEntity with status 204 (NO_CONTENT)
     */
    @DeleteMapping("/courses/{courseId}/tutorial-groups/{tutorialGroupId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @FeatureToggle(Feature.TutorialGroups)
    public ResponseEntity<Void> delete(@PathVariable Long courseId, @PathVariable Long tutorialGroupId) {
        log.info("REST request to delete a TutorialGroup: {} of course: {}", tutorialGroupId, courseId);
        var tutorialGroupFromDatabase = this.tutorialGroupRepository.findByIdWithTeachingAssistantAndRegistrationsElseThrow(tutorialGroupId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, tutorialGroupFromDatabase.getCourse(), null);
        checkEntityIdMatchesPathIds(tutorialGroupFromDatabase, Optional.of(courseId), Optional.of(tutorialGroupId));
        tutorialGroupNotificationService.notifyAboutTutorialGroupDeletion(tutorialGroupFromDatabase);
        tutorialGroupNotificationRepository.deleteAllByTutorialGroupId(tutorialGroupId);
        tutorialGroupRepository.deleteById(tutorialGroupFromDatabase.getId());
        return ResponseEntity.noContent().build();
    }

    /**
     * A DTO representing an updated tutorial group with an optional notification text about the update
     *
     * @param tutorialGroup    the updated tutorial group
     * @param notificationText the optional notification text
     */
    public record TutorialGroupUpdateDTO(@Valid @NotNull TutorialGroup tutorialGroup, @Size(min = 1, max = 1000) @Nullable String notificationText) {
    }

    /**
     * PUT /courses/:courseId/tutorial-groups/:tutorialGroupId : Updates an existing tutorial group
     *
     * @param courseId               the id of the course to which the tutorial group belongs to
     * @param tutorialGroupId        the id of the tutorial group to update
     * @param tutorialGroupUpdateDTO dto containing the tutorial group to update and the optional notification text
     * @return the ResponseEntity with status 200 (OK) and with body the updated tutorial group
     */
    @PutMapping("/courses/{courseId}/tutorial-groups/{tutorialGroupId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @FeatureToggle(Feature.TutorialGroups)
    public ResponseEntity<TutorialGroup> update(@PathVariable Long courseId, @PathVariable Long tutorialGroupId,
            @RequestBody @Valid TutorialGroupUpdateDTO tutorialGroupUpdateDTO) {
        TutorialGroup updatedTutorialGroup = tutorialGroupUpdateDTO.tutorialGroup();
        log.debug("REST request to update TutorialGroup : {}", updatedTutorialGroup);
        if (updatedTutorialGroup.getId() == null) {
            throw new BadRequestException("A tutorial group cannot be updated without an id");
        }
        var oldTutorialGroup = this.tutorialGroupRepository.findByIdWithTeachingAssistantAndRegistrationsElseThrow(tutorialGroupId);
        checkEntityIdMatchesPathIds(oldTutorialGroup, Optional.ofNullable(courseId), Optional.ofNullable(tutorialGroupId));
        var responsibleUser = userRepository.getUserWithGroupsAndAuthorities();
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, oldTutorialGroup.getCourse(), responsibleUser);

        trimStringFields(updatedTutorialGroup);
        if (!oldTutorialGroup.getTitle().equals(updatedTutorialGroup.getTitle())) {
            checkTitleIsValid(updatedTutorialGroup);
        }

        Optional<TutorialGroupsConfiguration> configurationOptional = tutorialGroupsConfigurationRepository.findByCourseIdWithEagerTutorialGroupFreePeriods(courseId);
        if (configurationOptional.isEmpty()) {
            throw new BadRequestException("The course has no tutorial groups configuration");
        }
        var configuration = configurationOptional.get();
        if (configuration.getCourse().getTimeZone() == null) {
            throw new BadRequestException("The course has no time zone");
        }

        // Note: We have to load the teaching assistants from database otherwise languageKey is not defined and email sending fails

        var oldTA = oldTutorialGroup.getTeachingAssistant();
        var newTA = updatedTutorialGroup.getTeachingAssistant();

        if (newTA != null && (oldTA == null || !oldTA.equals(newTA))) {
            var newTAFromDatabase = userRepository.findOneByLogin(newTA.getLogin());
            newTAFromDatabase.ifPresent(user -> singleUserNotificationService.notifyTutorAboutAssignmentToTutorialGroup(updatedTutorialGroup, user, responsibleUser));
        }
        if (oldTA != null && (newTA == null || !newTA.equals(oldTA))) {
            var oldTAFromDatabase = userRepository.findOneByLogin(oldTA.getLogin());
            oldTAFromDatabase.ifPresent(user -> singleUserNotificationService.notifyTutorAboutUnassignmentFromTutorialGroup(oldTutorialGroup, user, responsibleUser));
        }

        if (StringUtils.hasText(tutorialGroupUpdateDTO.notificationText())) {
            tutorialGroupNotificationService.notifyAboutTutorialGroupUpdate(oldTutorialGroup,
                    updatedTutorialGroup.getTeachingAssistant() == null || !updatedTutorialGroup.getTeachingAssistant().equals(responsibleUser),
                    StringUtils.trimWhitespace(tutorialGroupUpdateDTO.notificationText()));
        }

        overrideValues(updatedTutorialGroup, oldTutorialGroup);
        // persist without schedule at first
        oldTutorialGroup.setTutorialGroupSchedule(null);
        var persistedTutorialGroup = tutorialGroupRepository.save(oldTutorialGroup);

        tutorialGroupScheduleService.updateSchedule(configuration, persistedTutorialGroup, Optional.ofNullable(persistedTutorialGroup.getTutorialGroupSchedule()),
                Optional.ofNullable(updatedTutorialGroup.getTutorialGroupSchedule()));

        return ResponseEntity.ok(TutorialGroup.preventCircularJsonConversion(persistedTutorialGroup));
    }

    /**
     * DELETE /courses/:courseId/tutorial-groups/:tutorialGroupId/deregister/:studentLogin : deregister a student from a tutorial group.
     *
     * @param courseId        the id of the course to which the tutorial group belongs to
     * @param tutorialGroupId the id of the tutorial group
     * @param studentLogin    the login of the student to deregister
     * @return the ResponseEntity with status 204 (NO_CONTENT)
     */
    @DeleteMapping("/courses/{courseId}/tutorial-groups/{tutorialGroupId}/deregister/{studentLogin:" + Constants.LOGIN_REGEX + "}")
    @PreAuthorize("hasRole('TA')")
    @FeatureToggle(Feature.TutorialGroups)
    public ResponseEntity<Void> deregisterStudent(@PathVariable Long courseId, @PathVariable Long tutorialGroupId, @PathVariable String studentLogin) {
        log.debug("REST request to deregister {} student from tutorial group : {}", studentLogin, tutorialGroupId);
        var tutorialGroupFromDatabase = this.tutorialGroupRepository.findByIdElseThrow(tutorialGroupId);
        var responsibleUser = userRepository.getUserWithGroupsAndAuthorities();
        tutorialGroupService.isAllowedToChangeRegistrationsOfTutorialGroup(tutorialGroupFromDatabase, responsibleUser);
        checkEntityIdMatchesPathIds(tutorialGroupFromDatabase, Optional.of(courseId), Optional.of(tutorialGroupId));
        User studentToDeregister = userRepository.getUserWithGroupsAndAuthorities(studentLogin);
        tutorialGroupService.deregisterStudent(studentToDeregister, tutorialGroupFromDatabase, TutorialGroupRegistrationType.INSTRUCTOR_REGISTRATION, responsibleUser);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /courses/:courseId/tutorial-groups/:tutorialGroupId/register/:studentLogin : register a student to a tutorial group.
     *
     * @param courseId        the id of the course to which the tutorial group belongs to
     * @param tutorialGroupId the id of the tutorial group
     * @param studentLogin    the login of the student to register
     * @return the ResponseEntity with status 204 (NO_CONTENT)
     */
    @PostMapping("/courses/{courseId}/tutorial-groups/{tutorialGroupId}/register/{studentLogin:" + Constants.LOGIN_REGEX + "}")
    @PreAuthorize("hasRole('TA')")
    @FeatureToggle(Feature.TutorialGroups)
    public ResponseEntity<Void> registerStudent(@PathVariable Long courseId, @PathVariable Long tutorialGroupId, @PathVariable String studentLogin) {
        log.debug("REST request to register {} student to tutorial group : {}", studentLogin, tutorialGroupId);
        var tutorialGroupFromDatabase = this.tutorialGroupRepository.findByIdElseThrow(tutorialGroupId);
        var responsibleUser = userRepository.getUserWithGroupsAndAuthorities();
        tutorialGroupService.isAllowedToChangeRegistrationsOfTutorialGroup(tutorialGroupFromDatabase, responsibleUser);
        checkEntityIdMatchesPathIds(tutorialGroupFromDatabase, Optional.of(courseId), Optional.of(tutorialGroupId));
        User userToRegister = userRepository.getUserWithGroupsAndAuthorities(studentLogin);
        if (!userToRegister.getGroups().contains(tutorialGroupFromDatabase.getCourse().getStudentGroupName())) {
            throw new BadRequestAlertException("The user is not a student of the course", ENTITY_NAME, "userNotPartOfCourse");
        }
        // ToDo: Discuss if we change the registration type if registration is done by the tutor itself
        tutorialGroupService.registerStudent(userToRegister, tutorialGroupFromDatabase, TutorialGroupRegistrationType.INSTRUCTOR_REGISTRATION, responsibleUser);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /courses/:courseId/tutorial-groups/:tutorialGroupId/register-multiple" : Register multiple users to the tutorial group
     *
     * @param courseId        the id of the course to which the tutorial group belongs to
     * @param tutorialGroupId the id of the tutorial group to which the users should be registered to
     * @param studentDtos     the list of students who should be registered to the tutorial group
     * @return the list of students who could not be registered for the tutorial group, because they could NOT be found in the Artemis database as students of the tutorial group course
     */
    @PostMapping("/courses/{courseId}/tutorial-groups/{tutorialGroupId}/register-multiple")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @FeatureToggle(Feature.TutorialGroups)
    public ResponseEntity<Set<StudentDTO>> registerMultipleStudentsToTutorialGroup(@PathVariable Long courseId, @PathVariable Long tutorialGroupId,
            @RequestBody Set<StudentDTO> studentDtos) {
        log.debug("REST request to register {} to tutorial group {}", studentDtos, tutorialGroupId);
        var tutorialGroupFromDatabase = this.tutorialGroupRepository.findByIdElseThrow(tutorialGroupId);
        var responsibleUser = userRepository.getUserWithGroupsAndAuthorities();
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, tutorialGroupFromDatabase.getCourse(), responsibleUser);
        checkEntityIdMatchesPathIds(tutorialGroupFromDatabase, Optional.ofNullable(courseId), Optional.ofNullable(tutorialGroupId));
        Set<StudentDTO> notFoundStudentDtos = tutorialGroupService.registerMultipleStudents(tutorialGroupFromDatabase, studentDtos,
                TutorialGroupRegistrationType.INSTRUCTOR_REGISTRATION, responsibleUser);
        return ResponseEntity.ok().body(notFoundStudentDtos);
    }

    /**
     * POST /courses/:courseId/tutorial-groups/import: Import tutorial groups and student registrations
     *
     * @param courseId   the id of the course to which the tutorial groups belong
     * @param importDTOs the list registration import DTOsd
     * @return the list of registrations with information about the success of the import sorted by tutorial group title
     */
    @PostMapping("/courses/{courseId}/tutorial-groups/import")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @FeatureToggle(Feature.TutorialGroups)
    public ResponseEntity<List<TutorialGroupRegistrationImportDTO>> importRegistrations(@PathVariable Long courseId,
            @RequestBody @Valid Set<TutorialGroupRegistrationImportDTO> importDTOs) {
        log.debug("REST request to import registrations {} to course {}", importDTOs, courseId);
        var courseFromDatabase = this.courseRepository.findByIdElseThrow(courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, courseFromDatabase, null);
        var registrations = tutorialGroupService.importRegistrations(courseFromDatabase, importDTOs);
        var sortedRegistrations = registrations.stream().sorted(Comparator.comparing(TutorialGroupRegistrationImportDTO::title)).toList();
        return ResponseEntity.ok().body(sortedRegistrations);
    }

    private void trimStringFields(TutorialGroup tutorialGroup) {
        if (tutorialGroup.getTitle() != null) {
            tutorialGroup.setTitle(tutorialGroup.getTitle().trim());
        }
        if (tutorialGroup.getAdditionalInformation() != null) {
            tutorialGroup.setAdditionalInformation(tutorialGroup.getAdditionalInformation().trim());
        }
        if (tutorialGroup.getCampus() != null) {
            tutorialGroup.setCampus(tutorialGroup.getCampus().trim());
        }
    }

    private void checkTitleIsValid(TutorialGroup tutorialGroup) {
        if (tutorialGroupRepository.existsByTitleAndCourse(tutorialGroup.getTitle(), tutorialGroup.getCourse())) {
            throw new BadRequestException("A tutorial group with this title already exists in the course.");
        }
        if (!tutorialGroup.getTitle().matches(TITLE_REGEX)) {
            throw new BadRequestException("Title can only contain letters, numbers, spaces and dashes.");
        }
    }

    private static void overrideValues(TutorialGroup sourceTutorialGroup, TutorialGroup originalTutorialGroup) {
        originalTutorialGroup.setTitle(sourceTutorialGroup.getTitle());
        originalTutorialGroup.setTeachingAssistant(sourceTutorialGroup.getTeachingAssistant());
        originalTutorialGroup.setAdditionalInformation(sourceTutorialGroup.getAdditionalInformation());
        originalTutorialGroup.setCapacity(sourceTutorialGroup.getCapacity());
        originalTutorialGroup.setIsOnline(sourceTutorialGroup.getIsOnline());
        originalTutorialGroup.setLanguage(sourceTutorialGroup.getLanguage());
        originalTutorialGroup.setCampus(sourceTutorialGroup.getCampus());
    }

    private void checkEntityIdMatchesPathIds(TutorialGroup tutorialGroup, Optional<Long> courseId, Optional<Long> tutorialGroupId) {
        courseId.ifPresent(courseIdValue -> {
            if (!tutorialGroup.getCourse().getId().equals(courseIdValue)) {
                throw new BadRequestAlertException("The courseId in the path does not match the courseId in the tutorial group", ENTITY_NAME, "courseIdMismatch");
            }
        });
        tutorialGroupId.ifPresent(tutorialGroupIdValue -> {
            if (!tutorialGroup.getId().equals(tutorialGroupIdValue)) {
                throw new BadRequestAlertException("The tutorialGroupId in the path does not match the id in the tutorial group", ENTITY_NAME, "tutorialGroupIdMismatch");
            }
        });
    }

    /**
     * Describes the Errors that can lead to a failed import of a tutorial group registration
     */
    public enum TutorialGroupImportErrors {
        NO_TITLE, NO_USER_FOUND, MULTIPLE_REGISTRATIONS
    }

    /**
     * DTO used for client-server communication in the import of tutorial groups and student registrations from csv files
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record TutorialGroupRegistrationImportDTO(@Nullable String title, @Nullable StudentDTO student, @Nullable Boolean importSuccessful,
            @Nullable TutorialGroupImportErrors error) {

        public TutorialGroupRegistrationImportDTO withImportResult(boolean importSuccessful, TutorialGroupImportErrors error) {
            return new TutorialGroupRegistrationImportDTO(title(), student(), importSuccessful, error);
        }

        public TutorialGroupRegistrationImportDTO(@Nullable String title, @Nullable StudentDTO student) {
            this(title, student, null, null);
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (object == null || getClass() != object.getClass()) {
                return false;
            }

            TutorialGroupRegistrationImportDTO that = (TutorialGroupRegistrationImportDTO) object;

            if (!Objects.equals(title, that.title)) {
                return false;
            }
            return Objects.equals(student, that.student);
        }

        @Override
        public int hashCode() {
            int result = title != null ? title.hashCode() : 0;
            result = 31 * result + (student != null ? student.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return "TutorialGroupRegistrationImportDTO{" + "title='" + title + '\'' + ", student=" + student + ", importSuccessful=" + importSuccessful + ", error=" + error + '}';
        }
    }
}
