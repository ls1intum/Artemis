package de.tum.cit.aet.artemis.tutorialgroup.service;

import static de.tum.cit.aet.artemis.tutorialgroup.web.TutorialGroupResource.TutorialGroupImportErrors.MULTIPLE_REGISTRATIONS;
import static jakarta.persistence.Persistence.getPersistenceUtil;

import java.io.IOException;
import java.io.StringWriter;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.validation.constraints.NotNull;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.course_notifications.DeregisteredFromTutorialGroupNotification;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.RegisteredToTutorialGroupNotification;
import de.tum.cit.aet.artemis.communication.repository.conversation.OneToOneChatRepository;
import de.tum.cit.aet.artemis.communication.service.CourseNotificationService;
import de.tum.cit.aet.artemis.communication.service.conversation.ConversationDTOService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.StudentDTO;
import de.tum.cit.aet.artemis.core.dto.calendar.CalendarEventDTO;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.tutorialgroup.config.TutorialGroupEnabled;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupRegistration;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupRegistrationType;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSession;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSessionStatus;
import de.tum.cit.aet.artemis.tutorialgroup.dto.TutorialGroupDetailGroupDTO;
import de.tum.cit.aet.artemis.tutorialgroup.dto.TutorialGroupDetailSessionDTO;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupRegistrationRepository;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupRepository;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupSessionRepository;
import de.tum.cit.aet.artemis.tutorialgroup.util.RawTutorialGroupDetailGroupDTO;
import de.tum.cit.aet.artemis.tutorialgroup.util.RawTutorialGroupDetailSessionDTO;
import de.tum.cit.aet.artemis.tutorialgroup.web.TutorialGroupResource.TutorialGroupImportErrors;
import de.tum.cit.aet.artemis.tutorialgroup.web.TutorialGroupResource.TutorialGroupRegistrationImportDTO;

@Conditional(TutorialGroupEnabled.class)
@Lazy
@Service
public class TutorialGroupService {

    private static final Logger log = LoggerFactory.getLogger(TutorialGroupService.class);

    private final TutorialGroupRegistrationRepository tutorialGroupRegistrationRepository;

    private final UserRepository userRepository;

    private final TutorialGroupRepository tutorialGroupRepository;

    private final TutorialGroupSessionRepository tutorialGroupSessionRepository;

    private final TutorialGroupChannelManagementService tutorialGroupChannelManagementService;

    private final ConversationDTOService conversationDTOService;

    private final CourseNotificationService courseNotificationService;

    private final OneToOneChatRepository oneToOneChatRepository;

    public TutorialGroupService(TutorialGroupRegistrationRepository tutorialGroupRegistrationRepository, TutorialGroupRepository tutorialGroupRepository,
            UserRepository userRepository, TutorialGroupSessionRepository tutorialGroupSessionRepository,
            TutorialGroupChannelManagementService tutorialGroupChannelManagementService, ConversationDTOService conversationDTOService,
            CourseNotificationService courseNotificationService, OneToOneChatRepository oneToOneChatRepository) {
        this.tutorialGroupRegistrationRepository = tutorialGroupRegistrationRepository;
        this.tutorialGroupRepository = tutorialGroupRepository;
        this.userRepository = userRepository;
        this.tutorialGroupSessionRepository = tutorialGroupSessionRepository;
        this.tutorialGroupChannelManagementService = tutorialGroupChannelManagementService;
        this.conversationDTOService = conversationDTOService;
        this.courseNotificationService = courseNotificationService;
        this.oneToOneChatRepository = oneToOneChatRepository;
    }

    /**
     * Sets the transient fields for the given user.
     *
     * @param user          the user for which the transient fields should be set
     * @param tutorialGroup the tutorial group for which the transient fields should be set
     */
    public void setTransientPropertiesForUser(User user, TutorialGroup tutorialGroup) {

        if (getPersistenceUtil().isLoaded(tutorialGroup, "registrations") && tutorialGroup.getRegistrations() != null) {
            tutorialGroup.setIsUserRegistered(tutorialGroup.getRegistrations().stream().anyMatch(registration -> registration.getStudent().equals(user)));
            tutorialGroup.setNumberOfRegisteredUsers(tutorialGroup.getRegistrations().size());
        }
        else {
            tutorialGroup.setIsUserRegistered(null);
            tutorialGroup.setNumberOfRegisteredUsers(null);
        }

        if (getPersistenceUtil().isLoaded(tutorialGroup, "course") && tutorialGroup.getCourse() != null) {
            tutorialGroup.setCourseTitle(tutorialGroup.getCourse().getTitle());
        }
        else {
            tutorialGroup.setCourseTitle(null);
        }

        if (getPersistenceUtil().isLoaded(tutorialGroup, "teachingAssistant") && tutorialGroup.getTeachingAssistant() != null) {
            tutorialGroup.setTeachingAssistantName(tutorialGroup.getTeachingAssistant().getName());
            tutorialGroup.setTeachingAssistantId(tutorialGroup.getTeachingAssistant().getId());
            tutorialGroup.setTeachingAssistantImageUrl(tutorialGroup.getTeachingAssistant().getImageUrl());
            tutorialGroup.setIsUserTutor(tutorialGroup.getTeachingAssistant().equals(user));
        }
        else {
            tutorialGroup.setTeachingAssistantName(null);
            tutorialGroup.setTeachingAssistantId(null);
            tutorialGroup.setTeachingAssistantImageUrl(null);
        }

        if (tutorialGroup.getTutorialGroupChannel() != null) {
            tutorialGroup.setChannel(conversationDTOService.convertChannelToDTO(user, tutorialGroup.getTutorialGroupChannel()));
        }

        this.setNextSession(tutorialGroup);
        this.setAverageAttendance(tutorialGroup);
    }

    /**
     * Computes and sets the transient {@code averageAttendance} field for the given {@link TutorialGroup}.
     * <p>
     * The method evaluates the attendance of up to the last three completed and valid sessions:
     * <ul>
     * <li>Fetches sessions from the tutorial group if they are already loaded; otherwise queries the repository.</li>
     * <li>Filters for completed sessions (i.e., with an end date before now), active status, and non-null attendance.</li>
     * <li>Sorts the sessions by start date in descending order and selects the most recent three.</li>
     * <li>If no sessions remain after filtering, sets {@code averageAttendance} to {@code null}.</li>
     * <li>Otherwise, calculates the arithmetic mean of attendance counts (rounded to nearest integer) and sets it.</li>
     * </ul>
     *
     * @param tutorialGroup the {@link TutorialGroup} entity for which the attendance average should be computed
     */
    private void setAverageAttendance(TutorialGroup tutorialGroup) {
        Collection<TutorialGroupSession> sessions;

        // Check if sessions are already loaded via JPA; otherwise fetch from the database
        if (getPersistenceUtil().isLoaded(tutorialGroup, "tutorialGroupSessions") && tutorialGroup.getTutorialGroupSessions() != null) {
            sessions = tutorialGroup.getTutorialGroupSessions();
        }
        else {
            sessions = tutorialGroupSessionRepository.findAllByTutorialGroupId(tutorialGroup.getId());
        }

        //@formatter:off
        sessions.stream()
            // Keep only sessions that have already ended
            .filter(session -> session.getEnd().isBefore(ZonedDateTime.now()))
            // Keep only sessions that are marked as ACTIVE
            .filter(session -> TutorialGroupSessionStatus.ACTIVE.equals(session.getStatus()))
            // Exclude sessions without attendance data
            .filter(session -> session.getAttendanceCount() != null)
            // Sort by start time in descending order (most recent first)
            .sorted(Comparator.comparing(TutorialGroupSession::getStart).reversed())
            // Limit to the last three valid sessions
            .limit(3)
            // Map to attendance count for averaging
            .mapToInt(TutorialGroupSession::getAttendanceCount)
            // Compute the average and set it (rounded to integer), or null if no valid sessions
            .average()
            .ifPresentOrElse(
                value -> tutorialGroup.setAverageAttendance((int) Math.round(value)),
                () -> tutorialGroup.setAverageAttendance(null)
            );
        //@formatter:on
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
            // we show currently running sessions and up to 30 minutes after the end of the session so that students can still join and tutors can easily update the attendance of
            // the session
            nextSessionOptional = tutorialGroup.getTutorialGroupSessions().stream().filter(session -> session.getStatus() == TutorialGroupSessionStatus.ACTIVE)
                    .filter(session -> session.getEnd().plusMinutes(30).isAfter(ZonedDateTime.now())).min(Comparator.comparing(TutorialGroupSession::getStart));
        }
        else {
            var nextSessions = tutorialGroupSessionRepository.findNextSessionsOfStatus(tutorialGroup.getId(), ZonedDateTime.now(), TutorialGroupSessionStatus.ACTIVE);
            if (!nextSessions.isEmpty()) {
                nextSessionOptional = Optional.of(nextSessions.getFirst());
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
     * <p>
     * In addition, also removes the students from the respective tutorial group channel if it exists.
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
            // we still need to make sure the user is also already removed from the channel
            tutorialGroupChannelManagementService.removeUsersFromTutorialGroupChannel(tutorialGroup, Set.of(student));
            return; // No registration found, nothing to do.
        }
        tutorialGroupRegistrationRepository.delete(existingRegistration.get());

        var course = tutorialGroup.getCourse();
        var deregisteredFromTutorialGroupNotification = new DeregisteredFromTutorialGroupNotification(course.getId(), course.getTitle(), course.getCourseIcon(),
                tutorialGroup.getTitle(), tutorialGroup.getId(), responsibleUser.getName());
        courseNotificationService.sendCourseNotification(deregisteredFromTutorialGroupNotification, List.of(student));

        tutorialGroupChannelManagementService.removeUsersFromTutorialGroupChannel(tutorialGroup, Set.of(student));
    }

    private void deregisterStudentsFromAllTutorialGroupInCourse(Set<User> students, Course course, TutorialGroupRegistrationType registrationType) {
        tutorialGroupRegistrationRepository.deleteAllByStudentIsInAndTypeAndTutorialGroupCourse(students, registrationType, course);
        tutorialGroupChannelManagementService.removeUsersFromAllTutorialGroupChannelsInCourse(course, students);
    }

    /**
     * Register a student to a tutorial group.
     * <p>
     * In addition, also adds the student to the respective tutorial group channel if it exists.
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
            // we still need to make sure the user is also already added to the channel
            tutorialGroupChannelManagementService.addUsersToTutorialGroupChannel(tutorialGroup, Set.of(student));
            return; // Registration already exists, nothing to do.
        }
        TutorialGroupRegistration newRegistration = new TutorialGroupRegistration(student, tutorialGroup, registrationType);
        tutorialGroupRegistrationRepository.save(newRegistration);
        notifyStudentAboutRegistration(tutorialGroup, responsibleUser, student);
        tutorialGroupChannelManagementService.addUsersToTutorialGroupChannel(tutorialGroup, Set.of(student));
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
                notifyStudentAboutRegistration(tutorialGroup, responsibleUser, student);
            }
        }
        tutorialGroupChannelManagementService.addUsersToTutorialGroupChannel(tutorialGroup, students);
    }

    /**
     * Notifies the student that they were registered to a tutorial group.
     *
     * @param tutorialGroup   the tutorial group the student was registered to
     * @param responsibleUser the user that registered the student
     * @param student         to notify
     */
    private void notifyStudentAboutRegistration(TutorialGroup tutorialGroup, User responsibleUser, User student) {
        var course = tutorialGroup.getCourse();
        var registeredFromTutorialGroupNotification = new RegisteredToTutorialGroupNotification(course.getId(), course.getTitle(), course.getCourseIcon(), tutorialGroup.getTitle(),
                tutorialGroup.getId(), responsibleUser.getName());
        courseNotificationService.sendCourseNotification(registeredFromTutorialGroupNotification, List.of(student));
    }

    /**
     * Register multiple students to a tutorial group.
     * <p>
     * In addition, also adds the students to the respective tutorial group channel if it exists.
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
     * Import registrations
     * <p>
     * Important to note: A registration must contain a title of the tutorial group, but it must not contain a student.
     *
     * <ul>
     * <li>Only title -> Create Tutorial Group with the given title if it not exists
     * <li>Title and student -> Create Tutorial Group with given title if not exists AND register student.
     * <ul>
     * <li>If student is already registered in a tutorial group of the same course, the student will be deregistered from the old tutorial group.
     * </ul>
     * </ul>
     *
     * <p>
     *
     * In addition, the students will be added to the tutorial group channel if it exists.
     *
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
        // Add registrations to failedRegistrations if the tutorial group already exists
        var titlesMentionedInRegistrations = registrations.stream().map(TutorialGroupRegistrationImportDTO::title).filter(Objects::nonNull).map(String::trim)
                .collect(Collectors.toSet());

        var foundTutorialGroups = tutorialGroupRepository.findAllByCourseId(course.getId()).stream()
                .filter(tutorialGroup -> titlesMentionedInRegistrations.contains(tutorialGroup.getTitle())).collect(Collectors.toSet());

        registrationsWithTitle.forEach(registration -> {
            if (foundTutorialGroups.stream().anyMatch(tutorialGroup -> tutorialGroup.getTitle().equals(registration.title().trim()))) {
                if (registration.student() != null && !StringUtils.hasText(registration.student().registrationNumber()) && !StringUtils.hasText(registration.student().login())) {
                    failedRegistrations.add(registration);
                }
            }
        });
        Map<String, TutorialGroup> tutorialGroupTitleToTutorialGroup = findOrCreateTutorialGroups(course, registrationsWithTitle).stream()
                .collect(Collectors.toMap(TutorialGroup::getTitle, Function.identity()));

        // === Step 2: If the registration contains a student, try to find a user in the database with the mentioned registration number ===
        Set<TutorialGroupRegistrationImportDTO> registrationWithUserIdentifier = registrationsWithTitle.stream().filter(registration -> {
            if (registration.student() == null) {
                return false;
            }
            boolean hasRegistrationNumber = StringUtils.hasText(registration.student().registrationNumber());
            boolean hasLogin = StringUtils.hasText(registration.student().login());
            return hasRegistrationNumber || hasLogin;
        }).collect(Collectors.toSet());

        Map<TutorialGroupRegistrationImportDTO, User> registrationsWithMatchingUsers = filterOutWithoutMatchingUser(course, registrationWithUserIdentifier, failedRegistrations);
        Map<TutorialGroupRegistrationImportDTO, User> uniqueRegistrationsWithMatchingUsers = filterOutMultipleRegistrationsForSameUser(registrationsWithMatchingUsers,
                failedRegistrations);

        // === Step 3: Register all found users to their respective tutorial groups ===
        Map<TutorialGroup, Set<User>> tutorialGroupToRegisteredUsers = new HashMap<>();
        for (var registrationUserPair : uniqueRegistrationsWithMatchingUsers.entrySet()) {
            String title = Objects.requireNonNull(registrationUserPair.getKey().title());
            var tutorialGroup = tutorialGroupTitleToTutorialGroup.get(title.trim());
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
        var requestingUser = userRepository.getUserWithGroupsAndAuthorities();

        var foundTutorialGroups = tutorialGroupRepository.findAllByCourseId(course.getId()).stream()
                .filter(tutorialGroup -> titlesMentionedInRegistrations.contains(tutorialGroup.getTitle())).collect(Collectors.toSet());
        var tutorialGroupsToCreate = titlesMentionedInRegistrations.stream()
                .filter(title -> foundTutorialGroups.stream().noneMatch(tutorialGroup -> tutorialGroup.getTitle().equals(title))).map(title -> {
                    var tutorialGroup = new TutorialGroup();
                    tutorialGroup.setTitle(title);
                    tutorialGroup.setCourse(course);
                    // default values for the tutorial group
                    tutorialGroup.setLanguage(Language.GERMAN.name());
                    tutorialGroup.setCapacity(1);
                    tutorialGroup.setTeachingAssistant(requestingUser);
                    tutorialGroup.setIsOnline(false);
                    tutorialGroup.setCampus("Campus");

                    // Set additional fields from registrations
                    Optional<TutorialGroupRegistrationImportDTO> registrationOpt = registrations.stream().filter(r -> title.equals(r.title())).findFirst();
                    registrationOpt.ifPresent(registration -> {
                        if (registration.campus() != null && !registration.campus().isEmpty()) {
                            tutorialGroup.setCampus(registration.campus());
                        }
                        if (registration.capacity() != null) {
                            tutorialGroup.setCapacity(registration.capacity());
                        }
                        if (registration.language() != null && !registration.language().isEmpty()) {
                            tutorialGroup.setLanguage(registration.language());
                        }
                        if (registration.additionalInformation() != null && !registration.additionalInformation().isEmpty()) {
                            tutorialGroup.setAdditionalInformation(registration.additionalInformation());
                        }
                        if (registration.isOnline() != null) {
                            tutorialGroup.setIsOnline(registration.isOnline());
                        }
                    });
                    return tutorialGroup;
                }).collect(Collectors.toSet());

        var tutorialGroupsMentionedInRegistrations = new HashSet<>(foundTutorialGroups);
        tutorialGroupsMentionedInRegistrations.addAll(tutorialGroupRepository.saveAll(tutorialGroupsToCreate));

        tutorialGroupsMentionedInRegistrations.forEach(tutorialGroupChannelManagementService::createChannelForTutorialGroup);

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
            if (registration.student() == null) {
                return false;
            }
            boolean hasRegistrationNumber = StringUtils.hasText(registration.student().registrationNumber());
            boolean hasLogin = StringUtils.hasText(registration.student().login());

            if (hasRegistrationNumber && StringUtils.hasText(user.getRegistrationNumber())) {
                return user.getRegistrationNumber().equals(registration.student().registrationNumber().trim());
            }
            if (hasLogin && StringUtils.hasText(user.getLogin())) {
                return user.getLogin().equals(registration.student().login().trim());
            }
            return false;
        }).findFirst();
    }

    private Set<User> tryToFindMatchingUsers(Course course, Set<TutorialGroupRegistrationImportDTO> registrations) {
        var registrationNumbersToSearchFor = new HashSet<String>();
        var loginsToSearchFor = new HashSet<String>();

        for (var registration : registrations) {
            if (registration.student() == null) {
                continue; // should not be the case as we filtered out all registrations without a student in the calling method
            }
            boolean hasRegistrationNumber = StringUtils.hasText(registration.student().registrationNumber());
            boolean hasLogin = StringUtils.hasText(registration.student().login());

            if (hasRegistrationNumber) {
                registrationNumbersToSearchFor.add(registration.student().registrationNumber().trim());
            }
            if (hasLogin) {
                loginsToSearchFor.add(registration.student().login().trim());
            }
        }

        // ToDo: Discuss if we should allow to register course members who are not students
        var result = new HashSet<>(
                userRepository.findAllWithGroupsByDeletedIsFalseAndGroupsContainsAndRegistrationNumberIn(course.getStudentGroupName(), registrationNumbersToSearchFor));
        result.addAll(new HashSet<>(userRepository.findAllWithGroupsByDeletedIsFalseAndGroupsContainsAndLoginIn(course.getStudentGroupName(), loginsToSearchFor)));
        return result;
    }

    /**
     * Get all tutorial groups for a course, including setting the transient properties for the given user
     *
     * @param course              The course for which the tutorial groups should be retrieved.
     * @param user                The user for whom to set the transient properties of the tutorial groups.
     * @param isAdminOrInstructor whether the instructor of the course or is admin
     * @return A list of tutorial groups for the given course with the transient properties set for the given user.
     */
    public Set<TutorialGroup> findAllForCourse(@NotNull Course course, @NotNull User user, boolean isAdminOrInstructor) {
        // do not load all sessions here as they are not needed for the overview page and would slow down the request
        Set<TutorialGroup> tutorialGroups = tutorialGroupRepository.findAllByCourseIdWithTeachingAssistantRegistrationsAndSchedule(course.getId());
        // TODO: this is some overkill, we calculate way too many information with way too many database calls, we must reduce this
        tutorialGroups.forEach(tutorialGroup -> this.setTransientPropertiesForUser(user, tutorialGroup));
        tutorialGroups.forEach(tutorialGroup -> {
            if (!this.userHasManagingRightsForTutorialGroup(tutorialGroup, user, isAdminOrInstructor)) {
                tutorialGroup.hidePrivacySensitiveInformation();
            }
        });
        tutorialGroups.forEach(TutorialGroup::preventCircularJsonConversion);
        return tutorialGroups;
    }

    /**
     * Get one tutorial group of a course, including setting the transient properties for the given user
     *
     * @param course              The course for which the tutorial group should be retrieved.
     * @param tutorialGroupId     The id of the tutorial group to retrieve.
     * @param user                The user for whom to set the transient properties of the tutorial group.
     * @param isAdminOrInstructor whether the instructor of the course of the tutorial group or is admin
     * @return The tutorial group of the course with the transient properties set for the given user.
     */
    public TutorialGroup getOneOfCourse(@NotNull Course course, long tutorialGroupId, @NotNull User user, boolean isAdminOrInstructor) {
        TutorialGroup tutorialGroup = tutorialGroupRepository.findByIdWithTeachingAssistantAndRegistrationsAndSessionsElseThrow(tutorialGroupId);
        if (!course.equals(tutorialGroup.getCourse())) {
            throw new BadRequestAlertException("The courseId in the path does not match the courseId in the tutorial group", "tutorialGroup", "courseIdMismatch");
        }
        this.setTransientPropertiesForUser(user, tutorialGroup);
        if (!this.userHasManagingRightsForTutorialGroup(tutorialGroup, user, isAdminOrInstructor)) {
            tutorialGroup.hidePrivacySensitiveInformation();
        }
        return TutorialGroup.preventCircularJsonConversion(tutorialGroup);
    }

    /**
     * Retrieves the required data and uses them to assembles a DTO needed to display the information in the course-tutorial-group-detail.component.ts.
     *
     * @param tutorialGroupId the ID of the tutorial group to fetch
     * @param courseId        the ID of the course of the tutorial group
     * @param courseTimeZone  the time zone of the course, used for session status evaluation
     * @return a {@link TutorialGroupDetailGroupDTO}
     * @throws EntityNotFoundException if no tutorial group exists with the given ID
     */
    public TutorialGroupDetailGroupDTO getTutorialGroupDetailGroupDTO(long tutorialGroupId, long courseId, ZoneId courseTimeZone) {
        RawTutorialGroupDetailGroupDTO rawGroupDTOs = tutorialGroupRepository.getTutorialGroupDetailData(tutorialGroupId, courseId)
                .orElseThrow(() -> new EntityNotFoundException("No tutorial group found with id " + tutorialGroupId + " found for course with id " + courseId + "."));

        String tutorLogin = rawGroupDTOs.teachingAssistantLogin();
        String currentUserLogin = userRepository.getCurrentUserLogin();
        Long tutorChatId = null;
        if (!tutorLogin.equals(currentUserLogin)) {
            tutorChatId = oneToOneChatRepository.findIdOfChatInCourseBetweenUsers(courseId, tutorLogin, currentUserLogin);
        }

        List<RawTutorialGroupDetailSessionDTO> rawSessionDTOs = tutorialGroupSessionRepository.getTutorialGroupDetailSessionData(tutorialGroupId);
        List<TutorialGroupDetailSessionDTO> sessionDTOs;
        // the schedule related properties are null if and only if there is no schedule for the tutorial group
        if (rawGroupDTOs.scheduleDayOfWeek() != null) {
            int scheduleDayOfWeek = rawGroupDTOs.scheduleDayOfWeek();
            LocalTime scheduleStart = LocalTime.parse(rawGroupDTOs.scheduleStartTime());
            LocalTime scheduleEnd = LocalTime.parse(rawGroupDTOs.scheduleEndTime());
            String scheduleLocation = rawGroupDTOs.scheduleLocation();
            sessionDTOs = rawSessionDTOs.stream()
                    .map(data -> TutorialGroupDetailSessionDTO.from(data, scheduleDayOfWeek, scheduleStart, scheduleEnd, scheduleLocation, courseTimeZone)).toList();
        }
        else {
            sessionDTOs = rawSessionDTOs.stream().map(TutorialGroupDetailSessionDTO::from).toList();
        }

        return TutorialGroupDetailGroupDTO.from(rawGroupDTOs, sessionDTOs, tutorChatId);
    }

    /**
     * Determines if a user has managing rights for the tutorial group (e.g. see private information about a tutorial group such as the list of registered students)
     *
     * @param tutorialGroup       the tutorial group for which to check permission
     * @param user                the user for which to check permission
     * @param isAdminOrInstructor whether the instructor of the course of the tutorial group or is admin
     * @return true if the user is allowed, false otherwise
     */
    public boolean userHasManagingRightsForTutorialGroup(@NotNull TutorialGroup tutorialGroup, @NotNull User user, boolean isAdminOrInstructor) {
        if (isAdminOrInstructor) {
            return true;
        }
        var persistenceUtil = getPersistenceUtil();
        var tutorialGroupToCheck = tutorialGroup;
        var teachingAssistantInitialized = persistenceUtil.isLoaded(tutorialGroup, "teachingAssistant");
        if (!teachingAssistantInitialized || tutorialGroupToCheck.getTeachingAssistant() == null) {
            tutorialGroupToCheck = tutorialGroupRepository.findByIdWithTeachingAssistantAndCourseElseThrow(tutorialGroupToCheck.getId());
        }
        return (tutorialGroupToCheck.getTeachingAssistant() != null && tutorialGroupToCheck.getTeachingAssistant().equals(user));
    }

    /**
     * Checks if a user is allowed to change the registrations of a tutorial group
     *
     * @param tutorialGroup       the tutorial group for which to check permission
     * @param user                the user for which to check permission
     * @param isAdminOrInstructor whether the instructor of the course of the tutorial group or is admin
     */
    public void checkIfUserIsAllowedToChangeRegistrationsOfTutorialGroupElseThrow(@NotNull TutorialGroup tutorialGroup, @NotNull User user, boolean isAdminOrInstructor) {
        // ToDo: Clarify if this is the correct permission check
        if (!this.userHasManagingRightsForTutorialGroup(tutorialGroup, user, isAdminOrInstructor)) {
            throw new AccessForbiddenException("The user is not allowed to change the registrations of tutorial group: " + tutorialGroup.getId());
        }
    }

    /**
     * Checks if a user is allowed to delete the passed tutorial group. This is the case if the user is admin, or instructor of the group's course, or tutor of thr group.
     *
     * @param tutorialGroup       the tutorial group for which to check permission
     * @param user                the user for which to check permission
     * @param isAdminOrInstructor whether the instructor of the course of the tutorial group or is admin
     */
    public void checkIfUserIsAllowedToDeleteTutorialGroupElseThrow(@NotNull TutorialGroup tutorialGroup, @NotNull User user, boolean isAdminOrInstructor) {
        if (!this.userHasManagingRightsForTutorialGroup(tutorialGroup, user, isAdminOrInstructor)) {
            throw new AccessForbiddenException("The user is not allowed to delete the tutorial group: " + tutorialGroup.getId());
        }
    }

    /**
     * Checks if a user is allowed to modify the sessions of a tutorial group
     *
     * @param tutorialGroup       the tutorial group for which to check permission
     * @param user                the user for which to check permission
     * @param isAdminOrInstructor whether the instructor of the course of the tutorial group or is admin
     */
    public void checkIfUserIsAllowedToModifySessionsOfTutorialGroupElseThrow(@NotNull TutorialGroup tutorialGroup, @NotNull User user, boolean isAdminOrInstructor) {
        // ToDo: Clarify if this is the correct permission check
        if (!this.userHasManagingRightsForTutorialGroup(tutorialGroup, user, isAdminOrInstructor)) {
            throw new AccessForbiddenException("The user is not allowed to modify the sessions of tutorial group: " + tutorialGroup.getId());
        }
    }

    private Optional<User> findStudent(StudentDTO studentDto, String studentCourseGroupName) {
        var userOptional = userRepository.findUserWithGroupsAndAuthoritiesByRegistrationNumber(studentDto.registrationNumber())
                .or(() -> userRepository.findUserWithGroupsAndAuthoritiesByLogin(studentDto.login()));
        return userOptional.isPresent() && userOptional.get().getGroups().contains(studentCourseGroupName) ? userOptional : Optional.empty();
    }

    /**
     * Exports tutorial groups for a specific course to a CSV file.
     *
     * @param course              the course for which the tutorial groups should be exported
     * @param user                the user performing the export operation
     * @param isAdminOrInstructor whether the instructor of the course or is admin
     * @param fields              the list of fields to include in the CSV export
     * @return a String containing the CSV data
     * @throws IOException if an I/O error occurs
     */
    public String exportTutorialGroupsToCSV(Course course, User user, boolean isAdminOrInstructor, List<String> fields) throws IOException {
        Set<TutorialGroup> tutorialGroups = findAllForCourse(course, user, isAdminOrInstructor);

        StringWriter out = new StringWriter();

        // Check if "Students" is in the fields list and add separate columns for student details
        boolean includeStudents = fields.contains("Students");
        if (includeStudents) {
            fields.remove("Students");
            fields.add("Registration Number");
            fields.add("First Name");
            fields.add("Last Name");
        }

        CSVFormat csvFormat = CSVFormat.DEFAULT.builder().setHeader(fields.toArray(new String[0])).get();
        try (CSVPrinter printer = new CSVPrinter(out, csvFormat)) {
            for (TutorialGroup tutorialGroup : tutorialGroups) {
                writeTutorialGroupRow(printer, tutorialGroup, fields, null);
                if (includeStudents) {
                    Set<User> students = getStudentsRegisteredForTutorial(tutorialGroup.getRegistrations());
                    for (User student : students) {
                        writeTutorialGroupRow(printer, tutorialGroup, fields, student);
                    }
                }
            }
        }
        return out.toString();
    }

    // Method to write a row for a tutorial group, optionally including student details
    private void writeTutorialGroupRow(CSVPrinter printer, TutorialGroup tutorialGroup, List<String> fields, User student) throws IOException {
        for (String field : fields) {
            printer.print(switch (field) {
                case "Registration Number" -> getStudentField(student, StudentField.REGISTRATION_NUMBER);
                case "First Name" -> getStudentField(student, StudentField.FIRST_NAME);
                case "Last Name" -> getStudentField(student, StudentField.LAST_NAME);
                default -> getCSVInput(tutorialGroup, field);
            });
        }
        printer.println();
    }

    private String getCSVInput(TutorialGroup tutorialGroup, String field) {
        return switch (field) {
            case "ID" -> getValueOrDefault(tutorialGroup.getId());
            case "Title" -> getValueOrDefault(tutorialGroup.getTitle());
            case "Campus" -> getValueOrDefault(tutorialGroup.getCampus());
            case "Language" -> getValueOrDefault(tutorialGroup.getLanguage());
            case "Additional Information" -> getValueOrDefault(tutorialGroup.getAdditionalInformation());
            case "Capacity" -> getValueOrDefault(tutorialGroup.getCapacity());
            case "Is Online" -> getValueOrDefault(tutorialGroup.getIsOnline());
            case "Day of Week" -> getDayOfWeek(tutorialGroup);
            case "Start Time" -> getScheduleField(tutorialGroup, ScheduleField.START_TIME);
            case "End Time" -> getScheduleField(tutorialGroup, ScheduleField.END_TIME);
            case "Location" -> getScheduleField(tutorialGroup, ScheduleField.LOCATION);
            default -> "";
        };
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record TutorialGroupExportDTO(Long id, String title, String dayOfWeek, String startTime, String endTime, String location, String campus, String language,
            String additionalInformation, Integer capacity, Boolean isOnline, List<StudentExportDTO> students /* optional, only set if selected */) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record StudentExportDTO(String registrationNumber, String firstName, String lastName) {
    }

    /**
     * Exports selected information about all tutorial groups in a given course.
     * <p>
     * This method retrieves tutorial groups for the specified course ID and maps each one
     * to a {@link TutorialGroupExportDTO}. Only the fields explicitly listed in {@code selectedFields}
     * are populated; all others remain {@code null}. Field names must match exactly with those
     * defined in the corresponding client component (e.g., {@code tutorial-groups-export-button.component.ts}).
     * <p>
     * If the "Students" field is selected, the list of registered students is included using
     * {@link StudentExportDTO}.
     *
     * @param courseId       the ID of the course whose tutorial groups should be exported
     * @param selectedFields a list of field names to include in the export;
     *                           valid values include: "ID", "Title", "Day of Week", "Start Time", "End Time",
     *                           "Location", "Campus", "Language", "Additional Information", "Capacity",
     *                           "Is Online", and "Students"
     * @return a list of tutorial group DTOs with selectively populated fields
     */
    public List<TutorialGroupExportDTO> exportTutorialGroupInformation(Long courseId, List<String> selectedFields) {
        Set<TutorialGroup> tutorialGroups = tutorialGroupRepository.findAllByCourseIdWithTeachingAssistantRegistrationsAndSchedule(courseId);

        List<TutorialGroupExportDTO> exportData = new ArrayList<>();
        for (TutorialGroup group : tutorialGroups) {
            // NOTE: fields must be identical as defined in tutorial-groups-export-button.component.ts
            // @formatter:off
            TutorialGroupExportDTO tutorialGroupExportDTO = new TutorialGroupExportDTO(
                selectedFields.contains("ID") ? group.getId() : null,
                selectedFields.contains("Title") ? getCSVInput(group, "Title") : null,
                selectedFields.contains("Day of Week") ? getCSVInput(group, "Day of Week") : null,
                selectedFields.contains("Start Time") ? getCSVInput(group, "Start Time") : null,
                selectedFields.contains("End Time") ? getCSVInput(group, "End Time") : null,
                selectedFields.contains("Location") ? getCSVInput(group, "Location") : null,
                selectedFields.contains("Campus") ? getCSVInput(group, "Campus") : null,
                selectedFields.contains("Language") ? getCSVInput(group, "Language") : null,
                selectedFields.contains("Additional Information") ? getCSVInput(group, "Additional Information") : null,
                selectedFields.contains("Capacity") ? group.getCapacity() : null,
                selectedFields.contains("Is Online") ? group.getIsOnline() : null,
                selectedFields.contains("Students") ? convertStudents(group) : null
            );
            // @formatter:on
            exportData.add(tutorialGroupExportDTO);
        }

        return exportData;
    }

    /**
     * Converts the student registrations of the given tutorial group into a list of {@link StudentExportDTO}s.
     * <p>
     * Returns an empty list if there are no student registrations.
     * Each student is mapped with their registration number, first name, and last name,
     * using a fallback for {@code null} values via {@code getValueOrDefault}.
     *
     * @param group the tutorial group whose student registrations should be converted
     * @return a list of student export DTOs; empty if no students are registered
     */
    private List<StudentExportDTO> convertStudents(TutorialGroup group) {
        if (group.getRegistrations() == null || group.getRegistrations().isEmpty()) {
            return List.of();
        }

        return group.getRegistrations().stream().map(registration -> {
            var student = registration.getStudent();
            return new StudentExportDTO(getValueOrDefault(student.getRegistrationNumber()), getValueOrDefault(student.getFirstName()), getValueOrDefault(student.getLastName()));
        }).toList();
    }

    private String getValueOrDefault(Object value) {
        return value != null ? value.toString() : "";
    }

    /**
     * Returns the string representation of the day of the week for the tutorial group's schedule.
     * If the schedule or day of the week is null, returns the default day of "None".
     */
    private String getDayOfWeek(TutorialGroup tutorialGroup) {
        if (tutorialGroup.getTutorialGroupSchedule() != null && tutorialGroup.getTutorialGroupSchedule().getDayOfWeek() != null) {
            return getDayOfWeekString(tutorialGroup.getTutorialGroupSchedule().getDayOfWeek());
        }
        return getDayOfWeekString(8); // Default to "None"
    }

    /**
     * Returns the requested field (start time, end time, location) from the tutorial group's schedule.
     * If the schedule or the requested field is null, returns an empty string.
     */
    private String getScheduleField(TutorialGroup tutorialGroup, ScheduleField field) {
        if (tutorialGroup.getTutorialGroupSchedule() != null) {
            switch (field) {
                case START_TIME -> {
                    return getValueOrDefault(tutorialGroup.getTutorialGroupSchedule().getStartTime());
                }
                case END_TIME -> {
                    return getValueOrDefault(tutorialGroup.getTutorialGroupSchedule().getEndTime());
                }
                case LOCATION -> {
                    return getValueOrDefault(tutorialGroup.getTutorialGroupSchedule().getLocation());
                }
            }
        }
        return "";
    }

    /**
     * Returns the requested field (registration number, first name, last name) from the student.
     * If the student or the requested field is null, returns an empty string.
     */
    private String getStudentField(User student, StudentField field) {
        if (student != null) {
            switch (field) {
                case REGISTRATION_NUMBER -> {
                    return getValueOrDefault(student.getRegistrationNumber());
                }
                case FIRST_NAME -> {
                    return getValueOrDefault(student.getFirstName());
                }
                case LAST_NAME -> {
                    return getValueOrDefault(student.getLastName());
                }
            }
        }
        return "";
    }

    private enum ScheduleField {
        START_TIME, END_TIME, LOCATION
    }

    private enum StudentField {
        REGISTRATION_NUMBER, FIRST_NAME, LAST_NAME
    }

    /**
     * Converts a numeric representation of a day of the week to its corresponding String name.
     * This method is used primarily for CSV export purposes.
     *
     * @param dayOfWeek the numeric representation of the day of the week, where Monday = 1 and Sunday = 7. 8 is a default value which will be applied if a tutorial group schedule
     *                      is not created yet.
     * @return a String representing the name of the corresponding day of the week.
     * @throws IllegalArgumentException if the provided dayOfWeek is not within the range 1 to 8.
     */
    public String getDayOfWeekString(int dayOfWeek) {
        if (dayOfWeek < 1 || dayOfWeek > 8) {
            throw new IllegalArgumentException("Invalid day of the week: " + dayOfWeek);
        }
        String[] days = { "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday", "None" };
        return days[dayOfWeek - 1];
    }

    /**
     * Retrieves the set of students registered for a specific tutorial group.
     *
     * @param registrations the set of tutorial group registrations
     * @return a set of users (students) registered for the tutorial group
     */
    public Set<User> getStudentsRegisteredForTutorial(Set<TutorialGroupRegistration> registrations) {
        Set<User> students = new HashSet<>();
        for (TutorialGroupRegistration registration : registrations) {
            User user = registration.getStudent();
            if (user != null) {
                students.add(user);
            }
        }
        return students;
    }

    /**
     * Derives a set of {@link CalendarEventDTO}s from {@link TutorialGroupSession}s that the {@link User} participates in and that are related to the given {@link Course}.
     *
     * @param userId   the user for which the DTOs should be retrieved
     * @param courseId the course to which sessions should belong
     * @return the retrieved events
     */
    public Set<CalendarEventDTO> getCalendarEventDTOsFromTutorialsGroups(long userId, long courseId) {
        Set<Long> tutorialGroupIds = tutorialGroupRepository.findTutorialGroupIdsWhereUserParticipatesForCourseId(courseId, userId);
        return tutorialGroupSessionRepository.getCalendarEventDTOsFromActiveSessionsForTutorialGroupIds(tutorialGroupIds);
    }
}
