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
     * @param registrationType The type of registration.
     */
    public void deregisterStudent(User student, TutorialGroup tutorialGroup, TutorialGroupRegistrationType registrationType) {
        Optional<TutorialGroupRegistration> existingRegistration = tutorialGroupRegistrationRepository.findTutorialGroupRegistrationByTutorialGroupAndStudentAndType(tutorialGroup,
                student, registrationType);
        if (existingRegistration.isEmpty()) {
            return; // No registration found, nothing to do.
        }
        tutorialGroupRegistrationRepository.delete(existingRegistration.get());
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
     * Import tutorial groups and their registrations
     * <p>
     * Important to note: A dto must contain a title of the tutorial group, but it must not contain a student.
     * - Only title -> Create Tutorial Group if not exists
     * - Title and student -> Create Tutorial Group if not exists and register student.
     *
     * @param course     The course to import the tutorial groups for.
     * @param importDTOs The tutorial group import DTOs.
     * @return the set of registrations with information about the success of the import
     */
    public Set<TutorialGroupRegistrationImportDTO> importRegistrations(Course course, Set<TutorialGroupRegistrationImportDTO> importDTOs) {
        var failedImports = new HashSet<TutorialGroupRegistrationImportDTO>();

        // === Step 1: Try to find all tutorial groups with the mentioned title. Create them if they do not exist yet ===
        var titleSplit = splitIntoTitleAndNoTitle(importDTOs);
        // no title -> fail as title of tutorial is mandatory
        failedImports.addAll(titleSplit.getSecond());

        var withTutorialGroupTitle = titleSplit.getFirst();
        // group by title
        var titleToTutorialGroup = findOrCreateTutorialGroupsMentioned(course, titleSplit.getFirst()).stream().collect(Collectors.groupingBy(TutorialGroup::getTitle));

        // === Step 2: If the dto contains a student, try to find a user in the database with the mentioned registration number ===
        var withStudent = withTutorialGroupTitle.stream().filter(dto -> dto.student() != null).collect(Collectors.toSet());

        var userExistsSplit = splitIntoUserExistsAndUserDoesNotExist(course, withStudent);
        // student in dto but no registration number or no user with registration number could be found-> fail
        failedImports.addAll(userExistsSplit.getSecond());

        var dtosMatchedWithStudent = userExistsSplit.getFirst();

        // === Step 3: Register all found users to their respective tutorial groups ===
        var tutorialGroupToStudentsWhoShouldBeRegistered = new HashMap<TutorialGroup, Set<User>>();
        for (var dtoToUser : dtosMatchedWithStudent) {
            var tutorialGroup = titleToTutorialGroup.get(dtoToUser.getFirst().title()).get(0);
            var student = dtoToUser.getSecond();
            tutorialGroupToStudentsWhoShouldBeRegistered.computeIfAbsent(tutorialGroup, key -> new HashSet<>()).add(student);
        }

        for (var entry : tutorialGroupToStudentsWhoShouldBeRegistered.entrySet()) {
            registerMultipleStudentsToTutorialGroup(entry.getValue(), entry.getKey(), TutorialGroupRegistrationType.INSTRUCTOR_REGISTRATION);
        }

        // === Step 4: Create the result DTOs for the successful and failed imports ===
        var result = new HashSet<TutorialGroupRegistrationImportDTO>();

        var successfulImports = new HashSet<>(importDTOs);
        successfulImports.removeAll(failedImports);
        for (var successfulImport : successfulImports) {
            result.add(successfulImport.withImportResult(true));
        }
        for (var failedImport : failedImports) {
            result.add(failedImport.withImportResult(false));
        }

        return result;
    }

    /**
     * Tries to find tutorial groups with the title mentioned in the import dtos. If a tutorial group does not exist yet with that title, it will be created.
     *
     * @param course     The course for which the tutorial groups should be created.
     * @param importDTOs The import dtos containing the tutorial group titles.
     * @return A set of all tutorial groups with the titles mentioned in the import dtos.
     */
    private Set<TutorialGroup> findOrCreateTutorialGroupsMentioned(Course course, Set<TutorialGroupRegistrationImportDTO> importDTOs) {
        var titlesMentionedInDTOs = importDTOs.stream().map(TutorialGroupRegistrationImportDTO::title).map(String::trim).collect(Collectors.toSet());

        // filter out tutorial groups with titles not mentioned in the import dtos
        var foundTutorialGroups = tutorialGroupRepository.findAllByCourseId(course.getId()).stream()
                .filter(tutorialGroup -> titlesMentionedInDTOs.contains(tutorialGroup.getTitle())).collect(Collectors.toSet());

        // create tutorial groups with titles not found in the database
        var tutorialGroupsToCreate = titlesMentionedInDTOs.stream().filter(title -> foundTutorialGroups.stream().noneMatch(tutorialGroup -> tutorialGroup.getTitle().equals(title)))
                .map(title -> new TutorialGroup(course, title)).collect(Collectors.toSet());

        var tutorialGroupsMentionedInDTOs = new HashSet<TutorialGroup>(foundTutorialGroups);
        tutorialGroupsMentionedInDTOs.addAll(tutorialGroupRepository.saveAll(tutorialGroupsToCreate));
        return tutorialGroupsMentionedInDTOs;
    }

    /**
     * Splits the import dtos into two sets: One containing a tutorial group title and those without
     *
     * @param importDTOs The import dtos to split
     * @return Pair of sets: The first set contains the import dtos with a tutorial group title, the second set contains the import dtos without a tutorial group title.
     */
    private Pair<Set<TutorialGroupRegistrationImportDTO>, Set<TutorialGroupRegistrationImportDTO>> splitIntoTitleAndNoTitle(Set<TutorialGroupRegistrationImportDTO> importDTOs) {
        var importDTOsWithTitle = new HashSet<TutorialGroupRegistrationImportDTO>();
        var importDTOsWithoutTitle = new HashSet<TutorialGroupRegistrationImportDTO>();
        for (var importDTO : importDTOs) {
            if (importDTO.title() == null || importDTO.title().isBlank()) {
                importDTOsWithoutTitle.add(importDTO);
            }
            else {
                importDTOsWithTitle.add(importDTO);
            }
        }
        return Pair.of(importDTOsWithTitle, importDTOsWithoutTitle);
    }

    /**
     * Splits the import dtos into ones where the user could be found and ones where the user could not be found.
     *
     * @param course     The course for which the users should be found.
     * @param importDTOs The import dtos to filter
     * @return Pair of sets: The first set contains the import dtos matched with the found user, the second set contains the import dtos where the user could not be found.
     */
    private Pair<Set<Pair<TutorialGroupRegistrationImportDTO, User>>, Set<TutorialGroupRegistrationImportDTO>> splitIntoUserExistsAndUserDoesNotExist(Course course,
            Set<TutorialGroupRegistrationImportDTO> importDTOs) {
        var importDTOsWhereNoUserWasFound = new HashSet<TutorialGroupRegistrationImportDTO>();

        var importDTOsWithRegistrationNumber = new HashSet<TutorialGroupRegistrationImportDTO>();
        for (var importDTO : importDTOs) {
            if (importDTO.student().getRegistrationNumber() == null || importDTO.student().getRegistrationNumber().isBlank()) {
                importDTOsWhereNoUserWasFound.add(importDTO);
            }
            else {
                importDTOsWithRegistrationNumber.add(importDTO);
            }
        }

        var registrationNumbersToSearchFor = importDTOsWithRegistrationNumber.stream()
                .map(tutorialGroupRegistrationImportDTO -> tutorialGroupRegistrationImportDTO.student().getRegistrationNumber().trim()).collect(Collectors.toSet());

        // ToDo: Discuss if we should allow to register course members who are not students
        // ToDo: Implement login fallback
        var searchResults = getUsersFromRegistrationNumbers(registrationNumbersToSearchFor, course.getStudentGroupName());
        var foundStudents = searchResults.getFirst();
        var registrationNumbersWhereNoUserWasFound = searchResults.getSecond();

        var registrationNumberToFoundUser = new HashMap<String, User>();
        for (var student : foundStudents) {
            registrationNumberToFoundUser.put(student.getRegistrationNumber(), student);
        }

        // Step 3: Filter out all importDTOs where no user was found
        var importDTOsWhereUserWasFound = new HashSet<Pair<TutorialGroupRegistrationImportDTO, User>>();
        for (var importDTO : importDTOs) {
            if (registrationNumbersWhereNoUserWasFound.contains(importDTO.student().getRegistrationNumber())) {
                importDTOsWhereNoUserWasFound.add(importDTO);
            }
            else {
                var foundPair = Pair.of(importDTO, registrationNumberToFoundUser.get(importDTO.student().getRegistrationNumber()));
                importDTOsWhereUserWasFound.add(foundPair);
            }
        }

        return Pair.of(importDTOsWhereUserWasFound, importDTOsWhereNoUserWasFound);
    }

    /**
     * Tries to find users by their registration number
     *
     * @param registrationsNumbersToSearchFor registration numbers to find users with
     * @param groupName                       course group in which users will be searched
     * @return Pair of found users and not found registration numbers
     */
    private Pair<Set<User>, Set<String>> getUsersFromRegistrationNumbers(Set<String> registrationsNumbersToSearchFor, String groupName) {
        var foundUsers = new HashSet<User>();
        var registrationNumbersWithoutMatchingUser = new HashSet<String>();

        if (groupName != null && registrationsNumbersToSearchFor != null && !registrationsNumbersToSearchFor.isEmpty()) {
            foundUsers = new HashSet<>(userRepository.findAllByRegistrationNumbersInGroup(groupName, registrationsNumbersToSearchFor));
            var foundRegistrationNumbers = foundUsers.stream().map(User::getRegistrationNumber).collect(Collectors.toSet());
            registrationNumbersWithoutMatchingUser = new HashSet<>(registrationsNumbersToSearchFor);
            registrationNumbersWithoutMatchingUser.removeAll(foundRegistrationNumbers);
        }
        // Return both found users and not found registration numbers
        return Pair.of(foundUsers, registrationNumbersWithoutMatchingUser);
    }

    private Optional<User> findStudent(StudentDTO studentDto, String studentCourseGroupName) {
        var userOptional = userRepository.findUserWithGroupsAndAuthoritiesByRegistrationNumber(studentDto.getRegistrationNumber())
                .or(() -> userRepository.findUserWithGroupsAndAuthoritiesByLogin(studentDto.getLogin()));
        return userOptional.isPresent() && userOptional.get().getGroups().contains(studentCourseGroupName) ? userOptional : Optional.empty();
    }

}
