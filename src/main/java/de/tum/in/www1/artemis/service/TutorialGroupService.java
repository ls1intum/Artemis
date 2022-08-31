package de.tum.in.www1.artemis.service;

import java.util.*;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.TutorialGroup;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.TutorialGroupRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.dto.StudentDTO;
import de.tum.in.www1.artemis.service.user.UserService;

@Service
public class TutorialGroupService {

    private final TutorialGroupRepository tutorialGroupRepository;

    private final CourseRepository courseRepository;

    private final UserService userService;

    public TutorialGroupService(TutorialGroupRepository tutorialGroupRepository, CourseRepository courseRepository, UserService userService) {
        this.tutorialGroupRepository = tutorialGroupRepository;
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
        tutorialGroup.getRegisteredStudents().remove(student);
        tutorialGroupRepository.save(tutorialGroup);
    }

    /**
     * Register a student to a tutorial group.
     *
     * @param student       The student to register.
     * @param tutorialGroup The tutorial group to register to.
     */
    public void registerStudent(User student, TutorialGroup tutorialGroup) {
        tutorialGroup.getRegisteredStudents().add(student);
        tutorialGroupRepository.save(tutorialGroup);
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
        var tutorialGroup = tutorialGroupRepository.findByIdWithTeachingAssistantAndRegisteredStudentsAndSessionsElseThrow(tutorialGroupId);

        List<StudentDTO> notFoundStudentsDTOs = new ArrayList<>();
        for (var studentDto : studentDTOs) {
            var registrationNumber = studentDto.getRegistrationNumber();
            var login = studentDto.getLogin();
            Optional<User> optionalStudent = userService.findUserAndAddToCourse(registrationNumber, course.getStudentGroupName(), Role.STUDENT, login);
            if (optionalStudent.isEmpty()) {
                notFoundStudentsDTOs.add(studentDto);
            }
            else {
                tutorialGroup.getRegisteredStudents().add(optionalStudent.get());
            }
        }
        tutorialGroupRepository.save(tutorialGroup);

        return notFoundStudentsDTOs;
    }

}
