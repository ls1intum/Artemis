package de.tum.cit.aet.artemis.tutorialgroup.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.ws.rs.BadRequestException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.communication.domain.course_notifications.TutorialGroupAssignedNotification;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.TutorialGroupDeletedNotification;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.TutorialGroupUnassignedNotification;
import de.tum.cit.aet.artemis.communication.service.CourseNotificationService;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorException;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastEditorInCourse;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastInstructorInCourse;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastStudentInCourse;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastTutorInCourse;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.tutorialgroup.config.TutorialGroupEnabled;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupRegistrationType;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSchedule;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupsConfiguration;
import de.tum.cit.aet.artemis.tutorialgroup.dto.CreateOrUpdateTutorialGroupRequestDTO;
import de.tum.cit.aet.artemis.tutorialgroup.dto.TutorialGroupDetailDataDTO;
import de.tum.cit.aet.artemis.tutorialgroup.dto.TutorialGroupExportDataDTO;
import de.tum.cit.aet.artemis.tutorialgroup.dto.TutorialGroupImportDataDTO;
import de.tum.cit.aet.artemis.tutorialgroup.dto.TutorialGroupScheduleDTO;
import de.tum.cit.aet.artemis.tutorialgroup.dto.TutorialGroupStudentDTO;
import de.tum.cit.aet.artemis.tutorialgroup.dto.TutorialGroupStudentImportDataDTO;
import de.tum.cit.aet.artemis.tutorialgroup.dto.TutorialGroupSummaryDTO;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupRegistrationRepository;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupRepository;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupScheduleRepository;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupsConfigurationRepository;
import de.tum.cit.aet.artemis.tutorialgroup.service.TutorialGroupChannelManagementService;
import de.tum.cit.aet.artemis.tutorialgroup.service.TutorialGroupScheduleService;
import de.tum.cit.aet.artemis.tutorialgroup.service.TutorialGroupService;

@Conditional(TutorialGroupEnabled.class)
@Lazy
@RestController
@RequestMapping("api/tutorialgroup/")
public class TutorialGroupResource {

    public static final String ENTITY_NAME = "tutorialGroup";

    private static final Logger log = LoggerFactory.getLogger(TutorialGroupResource.class);

    private final TutorialGroupService tutorialGroupService;

    private final TutorialGroupRepository tutorialGroupRepository;

    private final CourseRepository courseRepository;

    private final UserRepository userRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final TutorialGroupsConfigurationRepository tutorialGroupsConfigurationRepository;

    private final TutorialGroupScheduleService tutorialGroupScheduleService;

    private final TutorialGroupChannelManagementService tutorialGroupChannelManagementService;

    private final CourseNotificationService courseNotificationService;

    private final TutorialGroupRegistrationRepository tutorialGroupRegistrationRepository;

    private final TutorialGroupScheduleRepository tutorialGroupScheduleRepository;

    public TutorialGroupResource(AuthorizationCheckService authorizationCheckService, UserRepository userRepository, CourseRepository courseRepository,
            TutorialGroupService tutorialGroupService, TutorialGroupRepository tutorialGroupRepository, TutorialGroupsConfigurationRepository tutorialGroupsConfigurationRepository,
            TutorialGroupScheduleService tutorialGroupScheduleService, TutorialGroupChannelManagementService tutorialGroupChannelManagementService,
            CourseNotificationService courseNotificationService, TutorialGroupRegistrationRepository tutorialGroupRegistrationRepository,
            TutorialGroupScheduleRepository tutorialGroupScheduleRepository) {
        this.tutorialGroupService = tutorialGroupService;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.tutorialGroupRepository = tutorialGroupRepository;
        this.tutorialGroupsConfigurationRepository = tutorialGroupsConfigurationRepository;
        this.tutorialGroupScheduleService = tutorialGroupScheduleService;
        this.tutorialGroupChannelManagementService = tutorialGroupChannelManagementService;
        this.courseNotificationService = courseNotificationService;
        this.tutorialGroupRegistrationRepository = tutorialGroupRegistrationRepository;
        this.tutorialGroupScheduleRepository = tutorialGroupScheduleRepository;
    }

    /**
     * GET /tutorial-groups/:tutorialGroupId/title : Returns the title of the tutorial-group with the given id
     * <p>
     * NOTE: Used by entity-title service in the client to resolve the title of a tutorial group for breadcrumbs
     *
     * @param tutorialGroupId the id of the tutorial group
     * @return ResponseEntity with status 200 (OK) and with body containing the title of the tutorial group
     */
    @GetMapping("tutorial-groups/{tutorialGroupId}/title")
    @EnforceAtLeastStudent
    public ResponseEntity<String> getTitle(@PathVariable Long tutorialGroupId) {
        log.debug("REST request to get title of TutorialGroup : {}", tutorialGroupId);
        return tutorialGroupRepository.getTutorialGroupTitle(tutorialGroupId).map(ResponseEntity::ok)
                .orElseThrow(() -> new EntityNotFoundException("TutorialGroup", tutorialGroupId));
    }

    /**
     * GET /courses/:courseId/tutorial-groups/language-values : gets the unique language values used for tutorial groups in the specified course
     * Note: Used for autocomplete in the client tutorial form
     *
     * @param courseId the id of the course to which the tutorial groups belong to
     * @return ResponseEntity with status 200 (OK) and with body containing the unique language values of tutorial groups in the course
     */
    @GetMapping("courses/{courseId}/tutorial-groups/language-values")
    @EnforceAtLeastEditorInCourse
    public ResponseEntity<Set<String>> getUniqueLanguageValues(@PathVariable Long courseId) {
        log.debug("REST request to get unique language values used for tutorial groups in course : {}", courseId);
        return ResponseEntity.ok(tutorialGroupRepository.findAllUniqueLanguageValuesByCourseId(courseId));
    }

    /**
     * GET /courses/:courseId/tutorial-groups: gets the tutorial groups of the specified course.
     *
     * @param courseId the id of the course to which the tutorial groups belong to
     * @return the ResponseEntity with status 200 (OK) and with body containing the tutorial groups of the course
     */
    @GetMapping("courses/{courseId}/tutorial-groups")
    @EnforceAtLeastStudent
    public ResponseEntity<List<TutorialGroupSummaryDTO>> getTutorialGroupsForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all tutorial groups of course with id: {}", courseId);
        var course = courseRepository.findByIdElseThrow(courseId);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);
        boolean isAdminOrInstructor = authorizationCheckService.isAdmin(user) || authorizationCheckService.isAtLeastInstructorInCourse(course, user);
        var tutorialGroups = tutorialGroupService.findAllForCourse(course, user, isAdminOrInstructor);
        return ResponseEntity.ok(tutorialGroups.stream().map(TutorialGroupSummaryDTO::from).toList());
    }

    /**
     * GET /courses/{courseId}/tutorial-groups/{tutorialGroupId} : Retrieves a DTO needed to display information in the course-tutorial-group-detail.component.ts
     *
     * @param courseId        the ID of the course containing the tutorial group
     * @param tutorialGroupId the ID of the tutorial group to retrieve
     * @return {@link ResponseEntity} with status 200 (OK) containing the DTO
     * @throws EntityNotFoundException      {@code 404 (Not Found)} if no course exists for courseId
     * @throws EntityNotFoundException      {@code 404 (Not Found)} if no tutorial group exists for tutorialGroupId
     * @throws AccessForbiddenException     {@code 403 (Forbidden)} if the requesting user is not part of the course
     * @throws InternalServerErrorException {@code 500 (Internal Server Error)} if no time zone is set for the course
     */
    @GetMapping("courses/{courseId}/tutorial-groups/{tutorialGroupId}")
    @EnforceAtLeastStudentInCourse
    public ResponseEntity<TutorialGroupDetailDataDTO> getTutorialGroup(@PathVariable long courseId, @PathVariable long tutorialGroupId) {
        log.debug("REST request to get tutorial group: {} of course: {}", tutorialGroupId, courseId);
        Optional<String> timeZoneString = courseRepository.getTimeZoneOfCourseById(courseId);
        if (timeZoneString.isEmpty()) {
            throw new InternalServerErrorException("The course of the tutorial group has an invalid timezone value. This should never happen when tutorial groups exist.");
        }
        if (!tutorialGroupRepository.existsByIdAndCourse_Id(tutorialGroupId, courseId)) {
            throw new EntityNotFoundException("There exists no tutorial group with the given tutorialGroupId for the course with the given courseId.");
        }
        ZoneId timeZone = ZoneId.of(timeZoneString.get());
        TutorialGroupDetailDataDTO tutorialGroupDetailDataDTO = tutorialGroupService.getTutorialGroupDTO(tutorialGroupId, courseId, timeZone);
        return ResponseEntity.ok().body(tutorialGroupDetailDataDTO);
    }

    /**
     * GET /courses/:courseId/tutorial-groups/:tutorialGroupId/schedule : get the schedule of a tutorial group.
     *
     * @param courseId        the id of the course to which the tutorial group belongs
     * @param tutorialGroupId the id of the tutorial group
     * @return the {@link ResponseEntity} with the tutorial group schedule, or 204 if no schedule exists
     */
    @GetMapping("courses/{courseId}/tutorial-groups/{tutorialGroupId}/schedule")
    @EnforceAtLeastEditorInCourse
    public ResponseEntity<TutorialGroupScheduleDTO> getTutorialGroupSchedule(@PathVariable long courseId, @PathVariable long tutorialGroupId) {
        log.debug("REST request to get tutorial group schedule: {} of course: {}", tutorialGroupId, courseId);
        if (!tutorialGroupRepository.existsByIdAndCourse_Id(tutorialGroupId, courseId)) {
            throw new EntityNotFoundException("There exists no tutorial group with the given tutorialGroupId for the course with the given courseId.");
        }
        Optional<TutorialGroupSchedule> scheduleOptional = tutorialGroupScheduleRepository.findByTutorialGroup_Id(tutorialGroupId);
        return scheduleOptional.map(schedule -> ResponseEntity.ok(TutorialGroupScheduleDTO.toTutorialGroupScheduleDTO((schedule)))).orElse(ResponseEntity.noContent().build());
    }

    /**
     * POST /courses/:courseId/tutorial-groups : create a tutorial group for a course.
     *
     * @param courseId                      the id of the course to which the tutorial group belongs
     * @param createTutorialGroupRequestDTO the data used to create the tutorial group
     * @return the {@link ResponseEntity} with the id of the created tutorial group
     */
    @PostMapping("courses/{courseId}/tutorial-groups")
    @EnforceAtLeastEditorInCourse
    public ResponseEntity<Long> createTutorialGroup(@PathVariable Long courseId, @RequestBody @Valid CreateOrUpdateTutorialGroupRequestDTO createTutorialGroupRequestDTO) {
        TutorialGroupScheduleDTO tutorialGroupScheduleDTO = createTutorialGroupRequestDTO.tutorialGroupSchedule();
        if (tutorialGroupScheduleDTO != null) {
            tutorialGroupScheduleDTO.validateMaximumTutorialPeriodLength();
        }
        log.debug("REST request to create TutorialGroup: {} in course: {}", createTutorialGroupRequestDTO, courseId);

        var course = courseRepository.findByIdElseThrow(courseId);
        var user = userRepository.getUserWithGroupsAndAuthorities();

        if (tutorialGroupRepository.existsByTitleAndCourse(createTutorialGroupRequestDTO.title(), course)) {
            throw new BadRequestException("A tutorial group with this title already exists in the course.");
        }

        TutorialGroupsConfiguration configuration = tutorialGroupsConfigurationRepository.findByCourseIdWithEagerTutorialGroupFreePeriods(courseId)
                .orElseThrow(() -> new BadRequestException("The course has no tutorial groups configuration"));

        if (configuration.getCourse().getTimeZone() == null) {
            throw new BadRequestException("The course has no time zone");
        }

        User teachingAssistant = userRepository.findByIdElseThrow(createTutorialGroupRequestDTO.tutorId());

        TutorialGroup tutorialGroup = new TutorialGroup();
        tutorialGroup.setCourse(course);
        tutorialGroup.setTitle(createTutorialGroupRequestDTO.title());
        tutorialGroup.setTeachingAssistant(teachingAssistant);
        tutorialGroup.setLanguage(createTutorialGroupRequestDTO.language());
        tutorialGroup.setIsOnline(createTutorialGroupRequestDTO.isOnline());
        tutorialGroup.setCampus(createTutorialGroupRequestDTO.campus());
        tutorialGroup.setCapacity(createTutorialGroupRequestDTO.capacity());
        tutorialGroup.setAdditionalInformation(createTutorialGroupRequestDTO.additionalInformation());
        tutorialGroup = tutorialGroupRepository.save(tutorialGroup);

        if (tutorialGroupScheduleDTO != null) {
            TutorialGroupSchedule schedule = TutorialGroupScheduleDTO.toTutorialGroupSchedule(tutorialGroupScheduleDTO);
            tutorialGroupScheduleService.saveScheduleAndGenerateScheduledSessions(course, tutorialGroup, schedule);
            tutorialGroup.setTutorialGroupSchedule(schedule);
        }

        if (configuration.getUseTutorialGroupChannels()) {
            tutorialGroupChannelManagementService.createChannelForTutorialGroup(tutorialGroup);
        }

        if (!user.equals(teachingAssistant)) {
            var tutorialGroupAssignedNotification = new TutorialGroupAssignedNotification(course.getId(), course.getTitle(), course.getCourseIcon(), tutorialGroup.getTitle(),
                    tutorialGroup.getId(), user.getName());
            courseNotificationService.sendCourseNotification(tutorialGroupAssignedNotification, List.of(teachingAssistant));
        }

        return ResponseEntity.ok().body(tutorialGroup.getId());
    }

    /**
     * PUT /courses/:courseId/tutorial-groups/:tutorialGroupId : update an existing tutorial group.
     *
     * @param courseId                      the id of the course to which the tutorial group belongs
     * @param tutorialGroupId               the id of the tutorial group to update
     * @param updateTutorialGroupRequestDTO the data used to update the tutorial group
     * @return the {@link ResponseEntity} with status 204 (NO_CONTENT)
     */
    @PutMapping("courses/{courseId}/tutorial-groups/{tutorialGroupId}")
    @EnforceAtLeastEditorInCourse
    public ResponseEntity<Void> updateTutorialGroup(@PathVariable long courseId, @PathVariable long tutorialGroupId,
            @RequestBody @Valid CreateOrUpdateTutorialGroupRequestDTO updateTutorialGroupRequestDTO) {
        TutorialGroupScheduleDTO tutorialGroupScheduleDTO = updateTutorialGroupRequestDTO.tutorialGroupSchedule();
        if (tutorialGroupScheduleDTO != null) {
            tutorialGroupScheduleDTO.validateMaximumTutorialPeriodLength();
        }
        log.debug("REST request to update TutorialGroup : {}", updateTutorialGroupRequestDTO);

        TutorialGroup tutorialGroup = this.tutorialGroupRepository.findByIdWithTeachingAssistantAndRegistrationsElseThrow(tutorialGroupId);
        checkIfGroupMatchesPathIds(tutorialGroup, Optional.of(courseId), Optional.of(tutorialGroupId));

        Course course = tutorialGroup.getCourse();
        User user = userRepository.getUserWithGroupsAndAuthorities();
        User oldTutor = tutorialGroup.getTeachingAssistant();
        User newTutor = userRepository.findByIdElseThrow(updateTutorialGroupRequestDTO.tutorId());

        TutorialGroupsConfiguration configuration = tutorialGroupsConfigurationRepository.findByCourseIdWithEagerTutorialGroupFreePeriods(courseId)
                .orElseThrow(() -> new BadRequestException("The course has no tutorial groups configuration"));

        if (course.getTimeZone() == null) {
            throw new BadRequestException("The course has no time zone");
        }

        boolean titleChanges = !tutorialGroup.getTitle().equals(updateTutorialGroupRequestDTO.title());
        if (titleChanges && tutorialGroupRepository.existsByTitleAndCourse(updateTutorialGroupRequestDTO.title(), course)) {
            throw new BadRequestException("A tutorial group with this title already exists in the course.");
        }

        boolean newTutorDoesNotEqualOldTutor = !newTutor.equals(oldTutor);
        if (newTutorDoesNotEqualOldTutor) {
            boolean newTutorIsNotLoggedInUser = !newTutor.equals(user);
            if (newTutorIsNotLoggedInUser) {
                var tutorialGroupAssignedNotification = new TutorialGroupAssignedNotification(course.getId(), course.getTitle(), course.getCourseIcon(),
                        updateTutorialGroupRequestDTO.title(), tutorialGroupId, user.getName());
                courseNotificationService.sendCourseNotification(tutorialGroupAssignedNotification, List.of(newTutor));
            }
            boolean oldTutorIsNotLoggedInUser = !oldTutor.equals(user);
            if (oldTutorIsNotLoggedInUser) {
                var tutorialGroupUnassignedNotification = new TutorialGroupUnassignedNotification(course.getId(), course.getTitle(), course.getCourseIcon(),
                        tutorialGroup.getTitle(), tutorialGroupId, user.getName());
                courseNotificationService.sendCourseNotification(tutorialGroupUnassignedNotification, List.of(oldTutor));
            }
            if (configuration.getUseTutorialGroupChannels()) {
                tutorialGroupChannelManagementService.addUsersToTutorialGroupChannel(tutorialGroup, Set.of(newTutor));
                tutorialGroupChannelManagementService.grantUsersModeratorRoleToTutorialGroupChannel(tutorialGroup, Set.of(newTutor));
                tutorialGroupChannelManagementService.removeUsersFromTutorialGroupChannel(tutorialGroup, Set.of(oldTutor));
            }
        }

        tutorialGroup.setTitle(updateTutorialGroupRequestDTO.title().trim());
        tutorialGroup.setTeachingAssistant(newTutor);
        tutorialGroup.setAdditionalInformation(updateTutorialGroupRequestDTO.additionalInformation());
        tutorialGroup.setCapacity(updateTutorialGroupRequestDTO.capacity());
        tutorialGroup.setIsOnline(updateTutorialGroupRequestDTO.isOnline());
        tutorialGroup.setLanguage(updateTutorialGroupRequestDTO.language());
        tutorialGroup.setCampus(updateTutorialGroupRequestDTO.campus());
        tutorialGroup = tutorialGroupRepository.save(tutorialGroup);

        TutorialGroupSchedule oldSchedule = tutorialGroup.getTutorialGroupSchedule();
        TutorialGroupSchedule newSchedule = TutorialGroupScheduleDTO.toTutorialGroupSchedule(tutorialGroupScheduleDTO);
        tutorialGroupScheduleService.updateScheduleAndSessionsIfChanged(course, tutorialGroup, Optional.ofNullable(oldSchedule), Optional.ofNullable(newSchedule));

        tutorialGroupChannelManagementService.updateNameOfTutorialGroupChannelIfItExists(tutorialGroup);

        return ResponseEntity.noContent().build();
    }

    /**
     * DELETE /courses/:courseId/tutorial-groups/:tutorialGroupId : delete a tutorial group.
     *
     * @param courseId        the id of the course to which the tutorial group belongs to
     * @param tutorialGroupId the id of the tutorial group to delete
     * @return the ResponseEntity with status 204 (NO_CONTENT)
     */
    @DeleteMapping("courses/{courseId}/tutorial-groups/{tutorialGroupId}")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<Void> deleteTutorialGroup(@PathVariable Long courseId, @PathVariable Long tutorialGroupId) {
        log.info("REST request to delete a TutorialGroup: {} of course: {}", tutorialGroupId, courseId);
        var tutorialGroup = this.tutorialGroupRepository.findByIdWithTeachingAssistantAndRegistrationsElseThrow(tutorialGroupId);
        checkIfGroupMatchesPathIds(tutorialGroup, Optional.of(courseId), Optional.of(tutorialGroupId));

        tutorialGroupChannelManagementService.deleteTutorialGroupChannel(tutorialGroup.getId());
        tutorialGroupRepository.deleteById(tutorialGroup.getId());

        var course = tutorialGroup.getCourse();
        var currentUser = userRepository.getUser();
        var tutorialGroupDeletedNotification = new TutorialGroupDeletedNotification(course.getId(), course.getTitle(), course.getCourseIcon(), tutorialGroup.getTitle(),
                tutorialGroup.getId(), currentUser.getName());
        courseNotificationService.sendCourseNotification(tutorialGroupDeletedNotification,
                tutorialGroupService.findUsersToNotify(tutorialGroup).stream().filter((user -> !Objects.equals(currentUser.getId(), user.getId()))).toList());

        return ResponseEntity.noContent().build();
    }

    /**
     * POST /courses/:courseId/tutorial-groups/:tutorialGroupId/batch-register : register multiple students to a tutorial group using their logins.
     *
     * @param courseId        the id of the course to which the tutorial group belongs
     * @param tutorialGroupId the id of the tutorial group
     * @param logins          the logins of the students to register
     * @return the {@link ResponseEntity} with status 204 (NO_CONTENT)
     */
    @PostMapping("courses/{courseId}/tutorial-groups/{tutorialGroupId}/batch-register")
    @EnforceAtLeastTutor
    public ResponseEntity<Void> batchRegisterStudents(@PathVariable long courseId, @PathVariable long tutorialGroupId, @RequestBody List<String> logins) {
        log.debug("REST request to register {} to tutorial group {}", logins, tutorialGroupId);
        var tutorialGroup = this.tutorialGroupRepository.findByIdElseThrow(tutorialGroupId);
        checkIfGroupMatchesPathIds(tutorialGroup, Optional.of(courseId), Optional.of(tutorialGroupId));

        User user = userRepository.getUserWithGroupsAndAuthorities();
        boolean userIsTutorOfGroup = tutorialGroup.getTeachingAssistant().equals(user);
        boolean userIsAtLeastEditorInCourse = authorizationCheckService.isAtLeastEditorInCourse(user.getLogin(), courseId);
        if (!userIsTutorOfGroup && !userIsAtLeastEditorInCourse) {
            throw new AccessForbiddenException("Only the tutor of a tutorial group or a user that is at least editor in the course can register students.");
        }

        tutorialGroupService.registerMultipleStudentsViaLogin(tutorialGroup, logins, TutorialGroupRegistrationType.INSTRUCTOR_REGISTRATION, user);
        return ResponseEntity.noContent().build();
    }

    /**
     * DELETE /courses/:courseId/tutorial-groups/:tutorialGroupId/deregister/:studentLogin : deregister a student from a tutorial group.
     *
     * @param courseId        the id of the course to which the tutorial group belongs to
     * @param tutorialGroupId the id of the tutorial group
     * @param studentLogin    the login of the student to deregister
     * @return the ResponseEntity with status 204 (NO_CONTENT)
     */
    @DeleteMapping("courses/{courseId}/tutorial-groups/{tutorialGroupId}/deregister/{studentLogin:" + Constants.LOGIN_REGEX + "}")
    @EnforceAtLeastTutor
    public ResponseEntity<Void> deregisterStudent(@PathVariable Long courseId, @PathVariable Long tutorialGroupId, @PathVariable String studentLogin) {
        log.debug("REST request to deregister {} student from tutorial group : {}", studentLogin, tutorialGroupId);
        var tutorialGroup = this.tutorialGroupRepository.findByIdElseThrow(tutorialGroupId);
        checkIfGroupMatchesPathIds(tutorialGroup, Optional.of(courseId), Optional.of(tutorialGroupId));

        User user = userRepository.getUserWithGroupsAndAuthorities();
        boolean userIsTutorOfGroup = tutorialGroup.getTeachingAssistant().equals(user);
        boolean userIsAtLeastEditorInCourse = authorizationCheckService.isAtLeastEditorInCourse(user.getLogin(), courseId);
        if (!userIsTutorOfGroup && !userIsAtLeastEditorInCourse) {
            throw new AccessForbiddenException("Only the tutor of a tutorial group or a user that is at least editor in the course can deregister a student.");
        }

        User studentToDeregister = userRepository.getUserWithGroupsAndAuthorities(studentLogin);
        tutorialGroupService.deregisterStudent(studentToDeregister, tutorialGroup, TutorialGroupRegistrationType.INSTRUCTOR_REGISTRATION, user);
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /courses/:courseId/tutorial-groups/:tutorialGroupId/unregistered-students : search students of the course that are not registered in the tutorial group yet.
     *
     * @param courseId        the id of the course to which the tutorial group belongs
     * @param tutorialGroupId the id of the tutorial group
     * @param loginOrName     the search string matched against login or name
     * @param pageIndex       the zero-based page index
     * @param pageSize        the number of results per page
     * @return the {@link ResponseEntity} with the matching unregistered students
     */
    @GetMapping("courses/{courseId}/tutorial-groups/{tutorialGroupId}/unregistered-students")
    @EnforceAtLeastTutor
    public ResponseEntity<List<TutorialGroupStudentDTO>> searchUnregisteredStudents(@PathVariable long courseId, @PathVariable long tutorialGroupId,
            @RequestParam String loginOrName, @RequestParam int pageIndex, @RequestParam int pageSize) {
        if (!tutorialGroupRepository.existsByIdAndCourse_Id(tutorialGroupId, courseId)) {
            throw new EntityNotFoundException("There exists no tutorial group with the given tutorialGroupId for the course with the given courseId.");
        }

        User user = userRepository.getUserWithGroupsAndAuthorities();
        var isUserTutorInTutorialGroup = tutorialGroupRepository.isTutorInTutorialGroup(user.getId(), tutorialGroupId, courseId);
        var isUserAtLeastEditorInCourse = authorizationCheckService.isAtLeastEditorInCourse(user.getLogin(), courseId);
        if (!isUserTutorInTutorialGroup && !isUserAtLeastEditorInCourse) {
            throw new AccessForbiddenException("Only the tutor of the group, editors and instructors are allowed to access unregistered students of a tutorial group.");
        }

        String studentGroupName = courseRepository.getStudentGroupNameById(courseId);
        List<TutorialGroupStudentDTO> foundStudents = tutorialGroupRegistrationRepository.searchUnregisteredStudents(tutorialGroupId, studentGroupName, loginOrName,
                PageRequest.of(pageIndex, pageSize));

        return ResponseEntity.ok().body(foundStudents);
    }

    /**
     * GET /courses/:courseId/tutorial-groups/:tutorialGroupId/registered-students : get all registered students of a tutorial group.
     *
     * @param courseId        the id of the course to which the tutorial group belongs
     * @param tutorialGroupId the id of the tutorial group
     * @return the {@link ResponseEntity} with the registered students of the tutorial group
     */
    @GetMapping("courses/{courseId}/tutorial-groups/{tutorialGroupId}/registered-students")
    @EnforceAtLeastTutorInCourse
    public ResponseEntity<Set<TutorialGroupStudentDTO>> getRegisteredStudents(@PathVariable long courseId, @PathVariable long tutorialGroupId) {
        if (!tutorialGroupRepository.existsByIdAndCourse_Id(tutorialGroupId, courseId)) {
            throw new EntityNotFoundException("There exists no tutorial group with the given tutorialGroupId for the course with the given courseId.");
        }

        var registerStudentDTOs = tutorialGroupRepository.getRegisteredStudentsOfTutorialGroup(tutorialGroupId);
        return ResponseEntity.ok().body(registerStudentDTOs);
    }

    /**
     * POST /courses/:courseId/tutorial-groups/:tutorialGroupId/import-registrations : register multiple students to a tutorial group using registration data.
     *
     * @param courseId          the id of the course to which the tutorial group belongs
     * @param tutorialGroupId   the id of the tutorial group
     * @param studentImportDTOs DTOs containing login or registration number of the students that should be registered
     * @return the students that could not be found and therefore were not registered
     */
    @PostMapping("courses/{courseId}/tutorial-groups/{tutorialGroupId}/import-registrations")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<List<TutorialGroupStudentImportDataDTO>> importRegistrations(@PathVariable long courseId, @PathVariable long tutorialGroupId,
            @RequestBody List<TutorialGroupStudentImportDataDTO> studentImportDTOs) {
        log.debug("REST request to register {} to tutorial group {}", studentImportDTOs, tutorialGroupId);
        var tutorialGroup = this.tutorialGroupRepository.findByIdElseThrow(tutorialGroupId);
        checkIfGroupMatchesPathIds(tutorialGroup, Optional.of(courseId), Optional.of(tutorialGroupId));
        User user = userRepository.getUser();

        List<TutorialGroupStudentImportDataDTO> notFoundStudentDtos = tutorialGroupService.registerMultipleStudentsViaLoginOrRegistrationNumber(tutorialGroup, studentImportDTOs,
                TutorialGroupRegistrationType.INSTRUCTOR_REGISTRATION, user);
        return ResponseEntity.ok().body(notFoundStudentDtos);
    }

    /**
     * POST /courses/:courseId/tutorial-groups/import: Import tutorial groups and student registrations
     *
     * @param courseId   the id of the course to which the tutorial groups belong
     * @param importDTOs the list registration import DTOsd
     * @return the list of registrations with information about the success of the import sorted by tutorial group title
     */
    @PostMapping("courses/{courseId}/tutorial-groups/import")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<List<TutorialGroupImportDataDTO>> importTutorialGroupsWithRegistrations(@PathVariable Long courseId,
            @RequestBody @Valid Set<TutorialGroupImportDataDTO> importDTOs) {
        log.debug("REST request to import registrations {} to course {}", importDTOs, courseId);
        var courseFromDatabase = this.courseRepository.findByIdElseThrow(courseId);

        var registrations = tutorialGroupService.importRegistrations(courseFromDatabase, importDTOs);
        var sortedRegistrations = registrations.stream().sorted(Comparator.comparing(TutorialGroupImportDataDTO::title)).toList();
        return ResponseEntity.ok().body(sortedRegistrations);
    }

    /**
     * GET /courses/:courseId/tutorial-groups/export : Export tutorial groups for a specific course to a CSV file.
     *
     * @param courseId the id of the course for which the tutorial groups should be exported
     * @param fields   the list of fields to include in the CSV export
     * @return the ResponseEntity with status 200 (OK) and the CSV file containing the tutorial groups
     */
    @GetMapping(value = "courses/{courseId}/tutorial-groups/export/csv", produces = "text/csv")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<byte[]> exportTutorialGroupsToCSV(@PathVariable Long courseId, @RequestParam List<String> fields) {
        log.debug("REST request to export TutorialGroups to CSV for course: {}", courseId);
        var course = courseRepository.findByIdElseThrow(courseId);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        boolean isAdminOrInstructor = authorizationCheckService.isAdmin(user) || authorizationCheckService.isAtLeastInstructorInCourse(course, user);
        String csvContent;
        try {
            csvContent = tutorialGroupService.exportTutorialGroupsToCSV(course, user, isAdminOrInstructor, fields);
        }
        catch (IOException e) {
            throw new BadRequestException("Error occurred while exporting tutorial groups", e);
        }
        byte[] bytes = csvContent.getBytes(StandardCharsets.UTF_8);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv"));
        headers.setContentDispositionFormData("attachment", "tutorial-groups.csv");
        headers.setContentLength(bytes.length);

        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    /**
     * GET /courses/:courseId/tutorial-groups/export/json : Export tutorial groups to JSON.
     *
     * @param courseId the id of the course to which the tutorial groups belong to
     * @param fields   the fields to be included in the export
     * @return ResponseEntity with the JSON data of the tutorial groups
     */
    @GetMapping(value = "courses/{courseId}/tutorial-groups/export/json", produces = MediaType.APPLICATION_JSON_VALUE)
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<List<TutorialGroupExportDataDTO>> exportTutorialGroupsToJSON(@PathVariable Long courseId, @RequestParam List<String> fields) {
        log.debug("REST request to export TutorialGroups to JSON for course: {}", courseId);
        var exportInformation = tutorialGroupService.exportTutorialGroupInformation(courseId, fields);
        return ResponseEntity.ok().body(exportInformation);
    }

    private void checkIfGroupMatchesPathIds(TutorialGroup tutorialGroup, Optional<Long> courseId, Optional<Long> tutorialGroupId) {
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
}
