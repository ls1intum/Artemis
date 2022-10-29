package de.tum.in.www1.artemis.service.tutorialgroups;

import static de.tum.in.www1.artemis.web.rest.tutorialgroups.TutorialGroupResource.TutorialGroupImportErrors.MULTIPLE_REGISTRATIONS;
import static javax.persistence.Persistence.getPersistenceUtil;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.persistence.Persistence;
import javax.validation.constraints.NotNull;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.TutorialGroupSessionStatus;
import de.tum.in.www1.artemis.domain.enumeration.tutorialgroups.TutorialGroupRegistrationType;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroup;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupRegistration;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupSession;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupRegistrationRepository;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupRepository;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupSessionRepository;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.dto.StudentDTO;
import de.tum.in.www1.artemis.service.notifications.SingleUserNotificationService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.tutorialgroups.TutorialGroupResource.TutorialGroupImportErrors;
import de.tum.in.www1.artemis.web.rest.tutorialgroups.TutorialGroupResource.TutorialGroupRegistrationImportDTO;

@Service
public class TutorialGroupService {

    private final SingleUserNotificationService singleUserNotificationService;

    private final TutorialGroupRegistrationRepository tutorialGroupRegistrationRepository;

    private final UserRepository userRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final TutorialGroupRepository tutorialGroupRepository;

    private final CourseRepository courseRepository;

    private final TutorialGroupSessionRepository tutorialGroupSessionRepository;

    public TutorialGroupService(SingleUserNotificationService singleUserNotificationService, TutorialGroupRegistrationRepository tutorialGroupRegistrationRepository,
            TutorialGroupRepository tutorialGroupRepository, UserRepository userRepository, AuthorizationCheckService authorizationCheckService, CourseRepository courseRepository,
            TutorialGroupSessionRepository tutorialGroupSessionRepository) {
        this.tutorialGroupRegistrationRepository = tutorialGroupRegistrationRepository;
        this.tutorialGroupRepository = tutorialGroupRepository;
        this.userRepository = userRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.singleUserNotificationService = singleUserNotificationService;
        this.courseRepository = courseRepository;
        this.tutorialGroupSessionRepository = tutorialGroupSessionRepository;
    }

    /**
     * Sets the transient fields for the given user.
     *
     * @param user          the user for which the transient fields should be set
     * @param tutorialGroup the tutorial group for which the transient fields should be set
     */
    public void setTransientPropertiesForUser(User user, TutorialGroup tutorialGroup) {

        if (Persistence.getPersistenceUtil().isLoaded(tutorialGroup, "registrations") && tutorialGroup.getRegistrations() != null) {
            tutorialGroup.setIsUserRegistered(tutorialGroup.getRegistrations().stream().anyMatch(registration -> registration.getStudent().equals(user)));
            tutorialGroup.setNumberOfRegisteredUsers(tutorialGroup.getRegistrations().size());
        }
        else {
            tutorialGroup.setIsUserRegistered(null);
            tutorialGroup.setNumberOfRegisteredUsers(null);
        }

        if (Persistence.getPersistenceUtil().isLoaded(tutorialGroup, "course") && tutorialGroup.getCourse() != null) {
            tutorialGroup.setCourseTitle(tutorialGroup.getCourse().getTitle());
        }
        else {
            tutorialGroup.setCourseTitle(null);
        }

        if (Persistence.getPersistenceUtil().isLoaded(tutorialGroup, "teachingAssistant") && tutorialGroup.getTeachingAssistant() != null) {
            tutorialGroup.setTeachingAssistantName(tutorialGroup.getTeachingAssistant().getName());
            tutorialGroup.setIsUserTutor(tutorialGroup.getTeachingAssistant().equals(user));
        }
        else {
            tutorialGroup.setTeachingAssistantName(null);
        }
        this.setNextSession(tutorialGroup);
    }

    /**
     * Sets the nextSession transient field of the given tutorial group
     *
     * @param tutorialGroup the tutorial group to set the next session for
     */
    private void setNextSession(TutorialGroup tutorialGroup) {
        Optional<TutorialGroupSession> nextSessionOptional = Optional.empty();
        if (getPersistenceUtil().isLoaded(tutorialGroup, "tutorialGroupSessions") && tutorialGroup.getTutorialGroupSessions() != null) {
            // determine the next session
            nextSessionOptional = tutorialGroup.getTutorialGroupSessions().stream().filter(session -> session.getStatus() == TutorialGroupSessionStatus.ACTIVE)
                    .filter(session -> session.getStart().isAfter(ZonedDateTime.now())).min(Comparator.comparing(TutorialGroupSession::getStart));
        }
        else {
            var nextSessions = tutorialGroupSessionRepository.findNextSessionsOfStatus(tutorialGroup.getId(), ZonedDateTime.now(), TutorialGroupSessionStatus.ACTIVE);
            if (nextSessions.size() > 0) {
                nextSessionOptional = Optional.of(nextSessions.get(0));
            }
        }
        nextSessionOptional.ifPresent(tutorialGroupSession -> {
            tutorialGroupSession.setTutorialGroup(null);
            if (tutorialGroupSession.getTutorialGroupSchedule() != null) {
                tutorialGroupSession.getTutorialGroupSchedule().setTutorialGroup(null);
                tutorialGroupSession.getTutorialGroupSchedule().setTutorialGroupSessions(null);
            }
            tutorialGroup.setNextSession(tutorialGroupSession);
        });

    }

    /**
     * Deregister a student from a tutorial group.
     *
     * @param student          The student to deregister.
     * @param tutorialGroup    The tutorial group to deregister from.
     * @param registrationType The type of registration that should be removed;
     * @param responsibleUser  The user that is responsible for the deregistration.
     */
    public void deregisterStudent(User student, TutorialGroup tutorialGroup, TutorialGroupRegistrationType registrationType, User responsibleUser) {
        Optional<TutorialGroupRegistration> existingRegistration = tutorialGroupRegistrationRepository.findTutorialGroupRegistrationByTutorialGroupAndStudentAndType(tutorialGroup,
                student, registrationType);
        if (existingRegistration.isEmpty()) {
            return; // No registration found, nothing to do.
        }
        tutorialGroupRegistrationRepository.delete(existingRegistration.get());
        singleUserNotificationService.notifyStudentAboutDeregistrationFromTutorialGroup(tutorialGroup, student, responsibleUser);
        if (!Objects.isNull(tutorialGroup.getTeachingAssistant()) && !responsibleUser.equals(tutorialGroup.getTeachingAssistant())) {
            singleUserNotificationService.notifyTutorAboutDeregistrationFromTutorialGroup(tutorialGroup, student, responsibleUser);
        }
    }

    public void deregisterStudentsFromAllTutorialGroupInCourse(Set<User> students, Course course, TutorialGroupRegistrationType registrationType) {
        tutorialGroupRegistrationRepository.deleteAllByStudentIsInAndTypeAndTutorialGroupCourse(students, registrationType, course);
    }

    /**
     * Register a student to a tutorial group.
     *
     * @param student          The student to register.
     * @param tutorialGroup    The tutorial group to register to.
     * @param registrationType The type of registration.
     * @param responsibleUser  The user who is responsible for the registration.
     */
    public void registerStudent(User student, TutorialGroup tutorialGroup, TutorialGroupRegistrationType registrationType, User responsibleUser) {
        Optional<TutorialGroupRegistration> existingRegistration = tutorialGroupRegistrationRepository.findTutorialGroupRegistrationByTutorialGroupAndStudentAndType(tutorialGroup,
                student, registrationType);
        if (existingRegistration.isPresent()) {
            return; // Registration already exists, nothing to do.
        }
        TutorialGroupRegistration newRegistration = new TutorialGroupRegistration(student, tutorialGroup, registrationType);
        tutorialGroupRegistrationRepository.save(newRegistration);
        singleUserNotificationService.notifyStudentAboutRegistrationToTutorialGroup(tutorialGroup, student, responsibleUser);
        if (!Objects.isNull(tutorialGroup.getTeachingAssistant()) && !responsibleUser.equals(tutorialGroup.getTeachingAssistant())) {
            singleUserNotificationService.notifyTutorAboutRegistrationToTutorialGroup(tutorialGroup, student, responsibleUser);
        }
    }

    private void registerMultipleStudentsToTutorialGroup(Set<User> students, TutorialGroup tutorialGroup, TutorialGroupRegistrationType registrationType, User responsibleUser,
            boolean sendNotification) {
        Set<User> registeredStudents = tutorialGroupRegistrationRepository.findAllByTutorialGroupAndType(tutorialGroup, registrationType).stream()
                .map(TutorialGroupRegistration::getStudent).collect(Collectors.toSet());
        Set<User> studentsToRegister = students.stream().filter(student -> !registeredStudents.contains(student)).collect(Collectors.toSet());
        Set<TutorialGroupRegistration> newRegistrations = studentsToRegister.stream().map(student -> new TutorialGroupRegistration(student, tutorialGroup, registrationType))
                .collect(Collectors.toSet());
        tutorialGroupRegistrationRepository.saveAll(newRegistrations);

        if (sendNotification && responsibleUser != null) {
            for (User student : studentsToRegister) {
                singleUserNotificationService.notifyStudentAboutRegistrationToTutorialGroup(tutorialGroup, student, responsibleUser);
            }

            if (!Objects.isNull(tutorialGroup.getTeachingAssistant()) && !responsibleUser.equals(tutorialGroup.getTeachingAssistant())) {
                singleUserNotificationService.notifyTutorAboutMultipleRegistrationsToTutorialGroup(tutorialGroup, studentsToRegister, responsibleUser);
            }
        }
    }

    /**
     * Register multiple students to a tutorial group.
     *
     * @param tutorialGroup    the tutorial group to register the students for
     * @param studentDTOs      The students to register.
     * @param registrationType The type of registration.
     * @param responsibleUser  The user who is responsible for the registration.
     * @return The students that could not be found and thus not registered.
     */
    public Set<StudentDTO> registerMultipleStudents(TutorialGroup tutorialGroup, Set<StudentDTO> studentDTOs, TutorialGroupRegistrationType registrationType,
            User responsibleUser) {
        Set<User> foundStudents = new HashSet<>();
        Set<StudentDTO> notFoundStudentDTOs = new HashSet<>();
        for (var studentDto : studentDTOs) {
            findStudent(studentDto, tutorialGroup.getCourse().getStudentGroupName()).ifPresentOrElse(foundStudents::add, () -> notFoundStudentDTOs.add(studentDto));
        }
        registerMultipleStudentsToTutorialGroup(foundStudents, tutorialGroup, registrationType, responsibleUser, true);
        return notFoundStudentDTOs;
    }

    /**
     * Find all tutorial groups for which the given user should be able to receive notifications.
     *
     * @param user The user for which to find the tutorial groups.
     * @return A list of tutorial groups for which the user should receive notifications.
     */
    public List<TutorialGroup> findAllForNotifications(User user) {
        return courseRepository.findAllActiveWithTutorialGroupsWhereUserIsRegisteredOrTutor(ZonedDateTime.now(), user.getId()).stream()
                .flatMap(course -> course.getTutorialGroups().stream()).collect(Collectors.toList());
    }

    /**
     * Import registrations
     * <p>
     * Important to note: A registration must contain a title of the tutorial group, but it must not contain a student.
     *
     * <ul>
     *     <li> Only title -> Create Tutorial Group with the given title if it not exists
     *     <li> Title and student -> Create Tutorial Group with given title if not exists AND register student.
     *     <ul>
     *         <li> If student is already registered in a tutorial group of the same course, the student will be deregistered from the old tutorial group.
     *     </ul>
     * </ul>
     *
     * @param course        The course to import the registrations for.
     * @param registrations The registrations to import.
     * @return The set of registrations with information about the success of the import
     */
    public Set<TutorialGroupRegistrationImportDTO> importRegistrations(Course course, Set<TutorialGroupRegistrationImportDTO> registrations) {
        // container that will be filled with the registrations that could not be imported during the import process
        Set<TutorialGroupRegistrationImportDTO> failedRegistrations = new HashSet<>();

        // === Step 1: Try to find all tutorial groups with the mentioned title. Create them if they do not exist yet ===
        Set<TutorialGroupRegistrationImportDTO> registrationsWithTitle = filterOutWithoutTitle(registrations, failedRegistrations);
        Map<String, TutorialGroup> tutorialGroupTitleToTutorialGroup = findOrCreateTutorialGroups(course, registrationsWithTitle).stream()
                .collect(Collectors.toMap(TutorialGroup::getTitle, Function.identity()));

        // === Step 2: If the registration contains a student, try to find a user in the database with the mentioned registration number ===
        Set<TutorialGroupRegistrationImportDTO> registrationWithUserIdentifier = registrationsWithTitle.stream().filter(registration -> {
            if (registration.student() == null) {
                return false;
            }
            boolean hasRegistrationNumber = StringUtils.hasText(registration.student().getRegistrationNumber());
            boolean hasLogin = StringUtils.hasText(registration.student().getLogin());
            return hasRegistrationNumber || hasLogin;
        }).collect(Collectors.toSet());

        Map<TutorialGroupRegistrationImportDTO, User> registrationsWithMatchingUsers = filterOutWithoutMatchingUser(course, registrationWithUserIdentifier, failedRegistrations);
        Map<TutorialGroupRegistrationImportDTO, User> uniqueRegistrationsWithMatchingUsers = filterOutMultipleRegistrationsForSameUser(registrationsWithMatchingUsers,
                failedRegistrations);

        // === Step 3: Register all found users to their respective tutorial groups ===
        Map<TutorialGroup, Set<User>> tutorialGroupToRegisteredUsers = new HashMap<>();
        for (var registrationUserPair : uniqueRegistrationsWithMatchingUsers.entrySet()) {
            assert registrationUserPair.getKey().title() != null;
            var tutorialGroup = tutorialGroupTitleToTutorialGroup.get(registrationUserPair.getKey().title().trim());
            var user = registrationUserPair.getValue();
            tutorialGroupToRegisteredUsers.computeIfAbsent(tutorialGroup, key -> new HashSet<>()).add(user);
        }

        // deregister all students that should be registered to a new tutorial group from their old tutorial groups
        deregisterStudentsFromAllTutorialGroupInCourse(new HashSet<>(uniqueRegistrationsWithMatchingUsers.values()), course, TutorialGroupRegistrationType.INSTRUCTOR_REGISTRATION);

        for (var tutorialGroupAndRegisteredUsers : tutorialGroupToRegisteredUsers.entrySet()) {
            registerMultipleStudentsToTutorialGroup(tutorialGroupAndRegisteredUsers.getValue(), tutorialGroupAndRegisteredUsers.getKey(),
                    TutorialGroupRegistrationType.INSTRUCTOR_REGISTRATION, null, false);
        }

        // === Step 4: Create the result for the successful and failed imports ===
        HashSet<TutorialGroupRegistrationImportDTO> registrationsWithImportResults = new HashSet<>();

        Set<TutorialGroupRegistrationImportDTO> successfulImports = new HashSet<>(registrations);
        successfulImports.removeAll(failedRegistrations);
        for (var successfulImport : successfulImports) {
            registrationsWithImportResults.add(successfulImport.withImportResult(true, null));
        }
        registrationsWithImportResults.addAll(failedRegistrations);

        return registrationsWithImportResults;
    }

    private Map<TutorialGroupRegistrationImportDTO, User> filterOutMultipleRegistrationsForSameUser(Map<TutorialGroupRegistrationImportDTO, User> registrationToUser,
            Set<TutorialGroupRegistrationImportDTO> failedRegistrations) {

        // reverse the map
        Map<User, Set<TutorialGroupRegistrationImportDTO>> userToRegistrations = new HashMap<>();
        for (var registrationUserPair : registrationToUser.entrySet()) {
            userToRegistrations.computeIfAbsent(registrationUserPair.getValue(), key -> new HashSet<>()).add(registrationUserPair.getKey());
        }

        // filter out all users that are registered multiple times
        Map<TutorialGroupRegistrationImportDTO, User> uniqueRegistrationsWithMatchingUsers = new HashMap<>();

        for (var userToRegistration : userToRegistrations.entrySet()) {
            if (userToRegistration.getValue().size() > 1) {
                failedRegistrations.addAll(
                        userToRegistration.getValue().stream().map(registration -> registration.withImportResult(false, MULTIPLE_REGISTRATIONS)).collect(Collectors.toSet()));
            }
            else {
                uniqueRegistrationsWithMatchingUsers.put(userToRegistration.getValue().iterator().next(), userToRegistration.getKey());
            }
        }
        return uniqueRegistrationsWithMatchingUsers;
    }

    private Set<TutorialGroup> findOrCreateTutorialGroups(Course course, Set<TutorialGroupRegistrationImportDTO> registrations) {
        var titlesMentionedInRegistrations = registrations.stream().map(TutorialGroupRegistrationImportDTO::title).filter(Objects::nonNull).map(String::trim)
                .collect(Collectors.toSet());

        var foundTutorialGroups = tutorialGroupRepository.findAllByCourseId(course.getId()).stream()
                .filter(tutorialGroup -> titlesMentionedInRegistrations.contains(tutorialGroup.getTitle())).collect(Collectors.toSet());
        var tutorialGroupsToCreate = titlesMentionedInRegistrations.stream()
                .filter(title -> foundTutorialGroups.stream().noneMatch(tutorialGroup -> tutorialGroup.getTitle().equals(title))).map(title -> new TutorialGroup(course, title))
                .collect(Collectors.toSet());

        var tutorialGroupsMentionedInRegistrations = new HashSet<>(foundTutorialGroups);
        tutorialGroupsMentionedInRegistrations.addAll(tutorialGroupRepository.saveAll(tutorialGroupsToCreate));
        return tutorialGroupsMentionedInRegistrations;
    }

    private Set<TutorialGroupRegistrationImportDTO> filterOutWithoutTitle(Set<TutorialGroupRegistrationImportDTO> registrations,
            Set<TutorialGroupRegistrationImportDTO> failedRegistrations) {
        var registrationsWithTitle = new HashSet<TutorialGroupRegistrationImportDTO>();
        var registrationsWithoutTitle = new HashSet<TutorialGroupRegistrationImportDTO>();
        for (var importDTO : registrations) {
            if (!StringUtils.hasText(importDTO.title())) {
                registrationsWithoutTitle.add(importDTO.withImportResult(false, TutorialGroupImportErrors.NO_TITLE));
            }
            else {
                registrationsWithTitle.add(importDTO);
            }
        }
        failedRegistrations.addAll(registrationsWithoutTitle);
        return registrationsWithTitle;
    }

    private Map<TutorialGroupRegistrationImportDTO, User> filterOutWithoutMatchingUser(Course course, Set<TutorialGroupRegistrationImportDTO> registrations,
            Set<TutorialGroupRegistrationImportDTO> failedRegistrations) {
        Set<User> matchingUsers = tryToFindMatchingUsers(course, registrations);

        HashMap<TutorialGroupRegistrationImportDTO, User> registrationToUser = new HashMap<>();

        for (var registration : registrations) {
            // try to find matching user first by registration number and as a fallback by login
            Optional<User> matchingUser = getMatchingUser(matchingUsers, registration);

            if (matchingUser.isPresent()) {
                registrationToUser.put(registration, matchingUser.get());
            }
            else {
                failedRegistrations.add(registration.withImportResult(false, TutorialGroupImportErrors.NO_USER_FOUND));
            }
        }

        return registrationToUser;
    }

    private static Optional<User> getMatchingUser(Set<User> users, TutorialGroupRegistrationImportDTO registration) {
        return users.stream().filter(user -> {
            assert registration.student() != null; // should be the case as we filtered out all registrations without a student
            boolean hasRegistrationNumber = StringUtils.hasText(registration.student().getRegistrationNumber());
            boolean hasLogin = StringUtils.hasText(registration.student().getLogin());

            if (hasRegistrationNumber && StringUtils.hasText(user.getRegistrationNumber())) {
                return user.getRegistrationNumber().equals(registration.student().getRegistrationNumber().trim());
            }
            if (hasLogin && StringUtils.hasText(user.getLogin())) {
                return user.getLogin().equals(registration.student().getLogin().trim());
            }
            return false;
        }).findFirst();
    }

    private Set<User> tryToFindMatchingUsers(Course course, Set<TutorialGroupRegistrationImportDTO> registrations) {
        var registrationNumbersToSearchFor = new HashSet<String>();
        var loginsToSearchFor = new HashSet<String>();

        for (var registration : registrations) {
            assert registration.student() != null; // should be the case as we filtered out all registrations without a student in the calling method
            boolean hasRegistrationNumber = StringUtils.hasText(registration.student().getRegistrationNumber());
            boolean hasLogin = StringUtils.hasText(registration.student().getLogin());

            if (hasRegistrationNumber) {
                registrationNumbersToSearchFor.add(registration.student().getRegistrationNumber().trim());
            }
            if (hasLogin) {
                loginsToSearchFor.add(registration.student().getLogin().trim());
            }
        }

        // ToDo: Discuss if we should allow to register course members who are not students
        var result = findUsersByRegistrationNumbers(registrationNumbersToSearchFor, course.getStudentGroupName());
        result.addAll(findUsersByLogins(loginsToSearchFor, course.getStudentGroupName()));
        return result;
    }

    private Set<User> findUsersByRegistrationNumbers(Set<String> registrationNumbers, String groupName) {
        return new HashSet<>(userRepository.findAllByRegistrationNumbersInGroup(groupName, registrationNumbers));
    }

    private Set<User> findUsersByLogins(Set<String> logins, String groupName) {
        return new HashSet<>(userRepository.findAllByLoginsInGroup(groupName, logins));

    }

    /**
     * Get all tutorial groups for a course, including setting the transient properties for the given user
     *
     * @param course The course for which the tutorial groups should be retrieved.
     * @param user   The user for whom to set the transient properties of the tutorial groups.
     * @return A list of tutorial groups for the given course with the transient properties set for the given user.
     */
    public Set<TutorialGroup> findAllForCourse(@NotNull Course course, @NotNull User user) {
        // do not load all sessions here as they are not needed for the overview page and would slow down the request
        Set<TutorialGroup> tutorialGroups = tutorialGroupRepository.findAllByCourseIdWithTeachingAssistantAndRegistrations(course.getId());
        tutorialGroups.forEach(tutorialGroup -> this.setTransientPropertiesForUser(user, tutorialGroup));
        tutorialGroups.forEach(tutorialGroup -> {
            if (!this.isAllowedToSeePrivateTutorialGroupInformation(tutorialGroup, user)) {
                tutorialGroup.hidePrivacySensitiveInformation();
            }
        });
        tutorialGroups.forEach(TutorialGroup::preventCircularJsonConversion);
        return tutorialGroups;
    }

    /**
     * Get one tutorial group of a course, including setting the transient properties for the given user
     *
     * @param tutorialGroupId The id of the tutorial group to retrieve.
     * @param user            The user for whom to set the transient properties of the tutorial group.
     * @param course          The course for which the tutorial group should be retrieved.
     * @return The tutorial group of the course with the transient properties set for the given user.
     */
    public TutorialGroup getOneOfCourse(@NotNull Course course, @NotNull User user, @NotNull Long tutorialGroupId) {
        TutorialGroup tutorialGroup = tutorialGroupRepository.findByIdWithTeachingAssistantAndRegistrationsAndSessionsElseThrow(tutorialGroupId);
        if (!course.equals(tutorialGroup.getCourse())) {
            throw new BadRequestAlertException("The courseId in the path does not match the courseId in the tutorial group", "tutorialGroup", "courseIdMismatch");
        }
        this.setTransientPropertiesForUser(user, tutorialGroup);
        if (!this.isAllowedToSeePrivateTutorialGroupInformation(tutorialGroup, user)) {
            tutorialGroup.hidePrivacySensitiveInformation();
        }
        return TutorialGroup.preventCircularJsonConversion(tutorialGroup);
    }

    /**
     * Determines if a user is allowed to see private information about a tutorial group such as the list of registered students
     *
     * @param tutorialGroup the tutorial group for which to check permission
     * @param user          the user for which to check permission
     * @return true if the user is allowed, false otherwise
     */
    public boolean isAllowedToSeePrivateTutorialGroupInformation(@NotNull TutorialGroup tutorialGroup, @Nullable User user) {
        var persistenceUtil = Persistence.getPersistenceUtil();
        if (user == null || !persistenceUtil.isLoaded(user, "authorities") || !persistenceUtil.isLoaded(user, "groups") || user.getGroups() == null
                || user.getAuthorities() == null) {
            user = userRepository.getUserWithGroupsAndAuthorities();
        }
        if (authorizationCheckService.isAdmin(user)) {
            return true;
        }

        var courseInitialized = persistenceUtil.isLoaded(tutorialGroup, "course");
        var teachingAssistantInitialized = persistenceUtil.isLoaded(tutorialGroup, "teachingAssistant");

        if (!courseInitialized || !teachingAssistantInitialized || tutorialGroup.getCourse() == null || tutorialGroup.getTeachingAssistant() == null) {
            tutorialGroup = tutorialGroupRepository.findByIdWithTeachingAssistantAndCourseElseThrow(tutorialGroup.getId());
        }

        Course course = tutorialGroup.getCourse();
        if (authorizationCheckService.isAtLeastInstructorInCourse(course, user)) {
            return true;
        }
        return (tutorialGroup.getTeachingAssistant() != null && tutorialGroup.getTeachingAssistant().equals(user));
    }

    /**
     * Checks if a user is allowed to change the registrations of a tutorial group
     *
     * @param tutorialGroup the tutorial group for which to check permission
     * @param user          the user for which to check permission
     */
    public void isAllowedToChangeRegistrationsOfTutorialGroup(@NotNull TutorialGroup tutorialGroup, @Nullable User user) {
        // ToDo: Clarify if this is the correct permission check
        if (!this.isAllowedToSeePrivateTutorialGroupInformation(tutorialGroup, user)) {
            throw new AccessForbiddenException("The user is not allowed to change the registrations of tutorial group: " + tutorialGroup.getId());
        }
    }

    /**
     * Checks if a user is allowed to modify the sessions of a tutorial group
     *
     * @param tutorialGroup the tutorial group for which to check permission
     * @param user          the user for which to check permission
     */
    public void isAllowedToModifySessionsOfTutorialGroup(@NotNull TutorialGroup tutorialGroup, @Nullable User user) {
        // ToDo: Clarify if this is the correct permission check
        if (!this.isAllowedToSeePrivateTutorialGroupInformation(tutorialGroup, user)) {
            throw new AccessForbiddenException("The user is not allowed to modify the sessions of tutorial group: " + tutorialGroup.getId());
        }
    }

    private Optional<User> findStudent(StudentDTO studentDto, String studentCourseGroupName) {
        var userOptional = userRepository.findUserWithGroupsAndAuthoritiesByRegistrationNumber(studentDto.getRegistrationNumber())
                .or(() -> userRepository.findUserWithGroupsAndAuthoritiesByLogin(studentDto.getLogin()));
        return userOptional.isPresent() && userOptional.get().getGroups().contains(studentCourseGroupName) ? userOptional : Optional.empty();
    }

}
