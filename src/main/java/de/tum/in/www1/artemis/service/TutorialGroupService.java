package de.tum.in.www1.artemis.service;

import java.util.*;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.tutorialGroups.TutorialGroupRegistrationType;
import de.tum.in.www1.artemis.domain.tutorialGroups.TutorialGroup;
import de.tum.in.www1.artemis.domain.tutorialGroups.TutorialGroupRegistration;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.tutorialGroups.TutorialGroupRegistrationRepository;
import de.tum.in.www1.artemis.repository.tutorialGroups.TutorialGroupRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.dto.StudentDTO;
import de.tum.in.www1.artemis.service.user.UserService;

@Service
public class TutorialGroupService {

    private final TutorialGroupRepository tutorialGroupRepository;

    private final TutorialGroupRegistrationRepository tutorialGroupRegistrationRepository;

    private final CourseRepository courseRepository;

    private final UserService userService;

    public TutorialGroupService(TutorialGroupRepository tutorialGroupRepository, TutorialGroupRegistrationRepository tutorialGroupRegistrationRepository,
            CourseRepository courseRepository, UserService userService) {
        this.tutorialGroupRepository = tutorialGroupRepository;
        this.tutorialGroupRegistrationRepository = tutorialGroupRegistrationRepository;
        this.courseRepository = courseRepository;
        this.userService = userService;
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

    /**
     * Register multiple students to a tutorial group.
     *
     * @param courseId        The id of the course the tutorial group belongs to.
     * @param tutorialGroupId The id of the tutorial group to register to.
     * @param studentDTOs     The students to register.
     * @return The students that could not be found and thus not registered.
     */
    public List<StudentDTO> registerMultipleStudents(Long courseId, Long tutorialGroupId, List<StudentDTO> studentDTOs) {
        var course = this.courseRepository.findByIdElseThrow(courseId);
        var tutorialGroup = tutorialGroupRepository.findByIdWithTeachingAssistantAndRegistrationsElseThrow(tutorialGroupId);

        List<StudentDTO> notFoundStudentsDTOs = new ArrayList<>();
        for (var studentDto : studentDTOs) {
            var registrationNumber = studentDto.getRegistrationNumber();
            var login = studentDto.getLogin();
            Optional<User> optionalStudent = userService.findUserAndAddToCourse(registrationNumber, course.getStudentGroupName(), Role.STUDENT, login);
            if (optionalStudent.isEmpty()) {
                notFoundStudentsDTOs.add(studentDto);
            }
            else {
                registerStudent(optionalStudent.get(), tutorialGroup);
            }
        }
        return notFoundStudentsDTOs;
    }

}
