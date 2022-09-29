package de.tum.in.www1.artemis.service;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.tutorialgroups.TutorialGroupRegistrationType;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroup;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupRegistration;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupRegistrationRepository;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupRepository;
import de.tum.in.www1.artemis.service.dto.StudentDTO;
import de.tum.in.www1.artemis.web.rest.tutorialgroups.TutorialGroupResource.TutorialGroupImportErrors;
import de.tum.in.www1.artemis.web.rest.tutorialgroups.TutorialGroupResource.TutorialGroupRegistrationImportDTO;

@Service
public class TutorialGroupService {

    private final TutorialGroupRegistrationRepository tutorialGroupRegistrationRepository;

    private final TutorialGroupRepository tutorialGroupRepository;

    private final UserRepository userRepository;

    public TutorialGroupService(TutorialGroupRegistrationRepository tutorialGroupRegistrationRepository, TutorialGroupRepository tutorialGroupRepository,
            UserRepository userRepository) {
        this.tutorialGroupRegistrationRepository = tutorialGroupRegistrationRepository;
        this.tutorialGroupRepository = tutorialGroupRepository;
        this.userRepository = userRepository;
    }

    /**
     * Deregister a student from a tutorial group.
     *
     * @param student          The student to deregister.
     * @param tutorialGroup    The tutorial group to deregister from.
     * @param registrationType The type of registration that should be removed;
     */
    public void deregisterStudent(User student, TutorialGroup tutorialGroup, TutorialGroupRegistrationType registrationType) {
        Optional<TutorialGroupRegistration> existingRegistration = tutorialGroupRegistrationRepository.findTutorialGroupRegistrationByTutorialGroupAndStudentAndType(tutorialGroup,
                student, registrationType);
        if (existingRegistration.isEmpty()) {
            return; // No registration found, nothing to do.
        }
        tutorialGroupRegistrationRepository.delete(existingRegistration.get());
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
     */
    public void registerStudent(User student, TutorialGroup tutorialGroup, TutorialGroupRegistrationType registrationType) {
        Optional<TutorialGroupRegistration> existingRegistration = tutorialGroupRegistrationRepository.findTutorialGroupRegistrationByTutorialGroupAndStudentAndType(tutorialGroup,
                student, registrationType);
        if (existingRegistration.isPresent()) {
            return; // Registration already exists, nothing to do.
        }
        TutorialGroupRegistration newRegistration = new TutorialGroupRegistration(student, tutorialGroup, registrationType);
        tutorialGroupRegistrationRepository.save(newRegistration);
    }

    private void registerMultipleStudentsToTutorialGroup(Set<User> students, TutorialGroup tutorialGroup, TutorialGroupRegistrationType registrationType) {
        Set<User> registeredStudents = tutorialGroupRegistrationRepository.findAllByTutorialGroupAndType(tutorialGroup, registrationType).stream()
                .map(TutorialGroupRegistration::getStudent).collect(Collectors.toSet());
        Set<User> studentsToRegister = students.stream().filter(student -> !registeredStudents.contains(student)).collect(Collectors.toSet());
        Set<TutorialGroupRegistration> newRegistrations = studentsToRegister.stream().map(student -> new TutorialGroupRegistration(student, tutorialGroup, registrationType))
                .collect(Collectors.toSet());
        tutorialGroupRegistrationRepository.saveAll(newRegistrations);
    }

    /**
     * Register multiple students to a tutorial group.
     *
     * @param tutorialGroup    the tutorial group to register the students for
     * @param studentDTOs      The students to register.
     * @param registrationType The type of registration.
     * @return The students that could not be found and thus not registered.
     */
    public Set<StudentDTO> registerMultipleStudents(TutorialGroup tutorialGroup, Set<StudentDTO> studentDTOs, TutorialGroupRegistrationType registrationType) {
        Set<User> foundStudents = new HashSet<>();
        Set<StudentDTO> notFoundStudentDTOs = new HashSet<>();
        for (var studentDto : studentDTOs) {
            findStudent(studentDto, tutorialGroup.getCourse().getStudentGroupName()).ifPresentOrElse(foundStudents::add, () -> notFoundStudentDTOs.add(studentDto));
        }
        registerMultipleStudentsToTutorialGroup(foundStudents, tutorialGroup, registrationType);
        return notFoundStudentDTOs;
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
        var failedRegistrations = new HashSet<TutorialGroupRegistrationImportDTO>();

        // === Step 1: Try to find all tutorial groups with the mentioned title. Create them if they do not exist yet ===
        var registrationsWithTitle = filterOutWithoutTitle(registrations, failedRegistrations);
        var tutorialGroupTitleToTutorialGroup = findOrCreateTutorialGroups(course, registrationsWithTitle).stream().collect(Collectors.groupingBy(TutorialGroup::getTitle));

        // === Step 2: If the registration contains a student, try to find a user in the database with the mentioned registration number ===
        var registrationWithUserIdentifier = registrationsWithTitle.stream().filter(registration -> {
            if (registration.student() == null) {
                return false;
            }
            boolean hasRegistrationNumber = registration.student().getRegistrationNumber() != null && !registration.student().getRegistrationNumber().isBlank();
            boolean hasLogin = registration.student().getLogin() != null && !registration.student().getLogin().isBlank();
            return hasRegistrationNumber || hasLogin;
        }).collect(Collectors.toSet());

        var registrationsWithMatchingUsers = filterOutWithoutMatchingUser(course, registrationWithUserIdentifier, failedRegistrations);
        var uniqueRegistrationsWithMatchingUsers = filterOutMultipleRegistrationsForSameUser(registrationsWithMatchingUsers, failedRegistrations);

        // === Step 3: Register all found users to their respective tutorial groups ===
        var tutorialGroupToRegisteredUsers = new HashMap<TutorialGroup, Set<User>>();
        for (var registrationUserPair : uniqueRegistrationsWithMatchingUsers) {
            var tutorialGroup = tutorialGroupTitleToTutorialGroup.get(registrationUserPair.getFirst().title().trim()).get(0);
            var user = registrationUserPair.getSecond();
            tutorialGroupToRegisteredUsers.computeIfAbsent(tutorialGroup, key -> new HashSet<>()).add(user);
        }

        // deregister all students that should be registered to a new tutorial group from their old tutorial groups
        deregisterStudentsFromAllTutorialGroupInCourse(uniqueRegistrationsWithMatchingUsers.stream().map(Pair::getSecond).collect(Collectors.toSet()), course,
                TutorialGroupRegistrationType.INSTRUCTOR_REGISTRATION);

        for (var tutorialGroupAndRegisteredUsers : tutorialGroupToRegisteredUsers.entrySet()) {
            registerMultipleStudentsToTutorialGroup(tutorialGroupAndRegisteredUsers.getValue(), tutorialGroupAndRegisteredUsers.getKey(),
                    TutorialGroupRegistrationType.INSTRUCTOR_REGISTRATION);
        }

        // === Step 4: Create the result for the successful and failed imports ===
        HashSet<TutorialGroupRegistrationImportDTO> registrationsWithImportResults = new HashSet<>();

        var successfulImports = new HashSet<>(registrations);
        successfulImports.removeAll(failedRegistrations);
        for (var successfulImport : successfulImports) {
            registrationsWithImportResults.add(successfulImport.withImportResult(true, null));
        }
        registrationsWithImportResults.addAll(failedRegistrations);

        return registrationsWithImportResults;
    }

    private Set<Pair<TutorialGroupRegistrationImportDTO, User>> filterOutMultipleRegistrationsForSameUser(
            Set<Pair<TutorialGroupRegistrationImportDTO, User>> registrationsWithMatchingUsers, Set<TutorialGroupRegistrationImportDTO> failedRegistrations) {
        var userToRegistrations = registrationsWithMatchingUsers.stream().collect(Collectors.groupingBy(Pair::getSecond));
        var uniqueRegistrationsWithMatchingUsers = new HashSet<Pair<TutorialGroupRegistrationImportDTO, User>>();
        for (var registrationAndMatchingUser : registrationsWithMatchingUsers) {
            var user = registrationAndMatchingUser.getSecond();
            var registrationsForUser = userToRegistrations.get(user);
            if (registrationsForUser.size() > 1) {
                failedRegistrations.add(registrationAndMatchingUser.getFirst().withImportResult(false, TutorialGroupImportErrors.MULTIPLE_REGISTRATIONS));
            }
            else {
                uniqueRegistrationsWithMatchingUsers.add(registrationAndMatchingUser);
            }
        }
        return uniqueRegistrationsWithMatchingUsers;
    }

    private Set<TutorialGroup> findOrCreateTutorialGroups(Course course, Set<TutorialGroupRegistrationImportDTO> registrations) {
        var titlesMentionedInRegistrations = registrations.stream().map(TutorialGroupRegistrationImportDTO::title).map(String::trim).collect(Collectors.toSet());

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
            if (importDTO.title() == null || importDTO.title().isBlank()) {
                registrationsWithoutTitle.add(importDTO.withImportResult(false, TutorialGroupImportErrors.NO_TITLE));
            }
            else {
                registrationsWithTitle.add(importDTO);
            }
        }
        failedRegistrations.addAll(registrationsWithoutTitle);
        return registrationsWithTitle;
    }

    private Set<Pair<TutorialGroupRegistrationImportDTO, User>> filterOutWithoutMatchingUser(Course course, Set<TutorialGroupRegistrationImportDTO> registrations,
            Set<TutorialGroupRegistrationImportDTO> failedRegistrations) {
        Set<User> matchingUsers = tryToFindMatchingUsers(course, registrations);

        var registrationWithMatchingUser = new HashSet<Pair<TutorialGroupRegistrationImportDTO, User>>();
        for (var registration : registrations) {
            // try to find matching user first by registration number and as a fallback by login
            Optional<User> matchingUser = getMatchingUser(matchingUsers, registration);

            if (matchingUser.isPresent()) {
                registrationWithMatchingUser.add(Pair.of(registration, matchingUser.get()));
            }
            else {
                failedRegistrations.add(registration.withImportResult(false, TutorialGroupImportErrors.NO_USER_FOUND));
            }
        }

        return registrationWithMatchingUser;
    }

    private static Optional<User> getMatchingUser(Set<User> users, TutorialGroupRegistrationImportDTO registration) {
        return users.stream().filter(user -> {
            assert registration.student() != null; // should be the case as we filtered out all registrations without a student
            boolean hasRegistrationNumber = registration.student().getRegistrationNumber() != null && !registration.student().getRegistrationNumber().isBlank();
            boolean hasLogin = registration.student().getLogin() != null && !registration.student().getLogin().isBlank();

            if (hasRegistrationNumber && user.getRegistrationNumber() != null) {
                return user.getRegistrationNumber().equals(registration.student().getRegistrationNumber().trim());
            }
            if (hasLogin && user.getLogin() != null) {
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
            boolean hasRegistrationNumber = registration.student().getRegistrationNumber() != null && !registration.student().getRegistrationNumber().isBlank();
            boolean hasLogin = registration.student().getLogin() != null && !registration.student().getLogin().isBlank();

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

    private Optional<User> findStudent(StudentDTO studentDto, String studentCourseGroupName) {
        var userOptional = userRepository.findUserWithGroupsAndAuthoritiesByRegistrationNumber(studentDto.getRegistrationNumber())
                .or(() -> userRepository.findUserWithGroupsAndAuthoritiesByLogin(studentDto.getLogin()));
        return userOptional.isPresent() && userOptional.get().getGroups().contains(studentCourseGroupName) ? userOptional : Optional.empty();
    }

}
