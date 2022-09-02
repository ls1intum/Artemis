package de.tum.in.www1.artemis.service;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.tutorialgroups.TutorialGroupRegistrationType;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroup;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupRegistration;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupRegistrationRepository;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupRepository;
import de.tum.in.www1.artemis.service.dto.StudentDTO;

@Service
public class TutorialGroupService {

    private final TutorialGroupRepository tutorialGroupRepository;

    private final TutorialGroupRegistrationRepository tutorialGroupRegistrationRepository;

    private final CourseRepository courseRepository;

    private final UserRepository userRepository;

    public TutorialGroupService(TutorialGroupRepository tutorialGroupRepository, TutorialGroupRegistrationRepository tutorialGroupRegistrationRepository,
            CourseRepository courseRepository, UserRepository userRepository) {
        this.tutorialGroupRepository = tutorialGroupRepository;
        this.tutorialGroupRegistrationRepository = tutorialGroupRegistrationRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
    }

    /**
     * Deregister a student from a tutorial group.
     *
     * @param student       The student to deregister.
     * @param tutorialGroup The tutorial group to deregister from.
     */
    public void deregisterStudent(User student, TutorialGroup tutorialGroup) {
        Optional<TutorialGroupRegistration> existingRegistration = tutorialGroupRegistrationRepository.findTutorialGroupRegistrationByTutorialGroupAndStudent(tutorialGroup,
                student);
        if (existingRegistration.isEmpty()) {
            return; // No registration found, nothing to do.
        }
        tutorialGroupRegistrationRepository.delete(existingRegistration.get());
    }

    /**
     * Register a student to a tutorial group.
     *
     * @param student       The student to register.
     * @param tutorialGroup The tutorial group to register to.
     */
    public void registerStudent(User student, TutorialGroup tutorialGroup) {
        Optional<TutorialGroupRegistration> existingRegistration = tutorialGroupRegistrationRepository.findTutorialGroupRegistrationByTutorialGroupAndStudent(tutorialGroup,
                student);
        if (existingRegistration.isPresent()) {
            return; // Registration already exists, nothing to do.
        }
        TutorialGroupRegistration newRegistration = new TutorialGroupRegistration(student, tutorialGroup, TutorialGroupRegistrationType.INSTRUCTOR_REGISTRATION);
        tutorialGroupRegistrationRepository.save(newRegistration);
    }

    private void registerMultipleStudentsToTutorialGroup(Set<User> students, TutorialGroup tutorialGroup) {
        Set<User> registeredStudents = tutorialGroupRegistrationRepository.findAllByTutorialGroup(tutorialGroup).stream().map(TutorialGroupRegistration::getStudent)
                .collect(Collectors.toSet());
        Set<User> studentsToRegister = students.stream().filter(student -> !registeredStudents.contains(student)).collect(Collectors.toSet());
        Set<TutorialGroupRegistration> newRegistrations = studentsToRegister.stream()
                .map(student -> new TutorialGroupRegistration(student, tutorialGroup, TutorialGroupRegistrationType.INSTRUCTOR_REGISTRATION)).collect(Collectors.toSet());
        tutorialGroupRegistrationRepository.saveAll(newRegistrations);
    }

    /**
     * Register multiple students to a tutorial group.
     *
     * @param courseId        The id of the course the tutorial group belongs to.
     * @param tutorialGroupId The id of the tutorial group to register to.
     * @param studentDTOs     The students to register.
     * @return The students that could not be found and thus not registered.
     */
    public Set<StudentDTO> registerMultipleStudents(Long courseId, Long tutorialGroupId, Set<StudentDTO> studentDTOs) {
        var course = this.courseRepository.findByIdElseThrow(courseId);
        var tutorialGroup = tutorialGroupRepository.findByIdWithTeachingAssistantAndRegistrationsElseThrow(tutorialGroupId);

        Set<User> foundStudents = new HashSet();
        Set<StudentDTO> notFoundStudentsDTOs = new HashSet<>();
        for (var studentDto : studentDTOs) {
            var studentOptional = findStudent(studentDto, course.getStudentGroupName());
            if (studentOptional.isEmpty()) {
                notFoundStudentsDTOs.add(studentDto);
            }
            else {
                foundStudents.add(studentOptional.get());
            }
        }
        registerMultipleStudentsToTutorialGroup(foundStudents, tutorialGroup);
        return notFoundStudentsDTOs;
    }

    private Optional<User> findStudent(StudentDTO studentDto, String studentCourseGroupName) {
        var registrationNumber = studentDto.getRegistrationNumber();
        var login = studentDto.getLogin();

        // try to find the user by login
        var studentByRegistrationNumber = userRepository.findUserWithGroupsAndAuthoritiesByRegistrationNumber(registrationNumber);
        if (studentByRegistrationNumber.isPresent()) {
            var student = studentByRegistrationNumber.get();
            if (student.getGroups().contains(studentCourseGroupName)) {
                return studentByRegistrationNumber;
            }
        }
        // try to find the student by login
        var studentByLogin = userRepository.findUserWithGroupsAndAuthoritiesByLogin(login);
        if (studentByLogin.isPresent()) {
            var student = studentByLogin.get();
            if (student.getGroups().contains(studentCourseGroupName)) {
                return studentByLogin;
            }
        }
        return Optional.empty();
    }

}
