package de.tum.in.www1.artemis.service;

import java.util.*;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.TutorialGroup;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.TutorialGroupRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.dto.StudentDTO;
import de.tum.in.www1.artemis.service.user.UserService;

@Service
public class TutorialGroupService {

    private final TutorialGroupRepository tutorialGroupRepository;

    private final CourseRepository courseRepository;

    private final UserService userService;

    public TutorialGroupService(TutorialGroupRepository tutorialGroupRepository, CourseRepository courseRepository, UserRepository userRepository, UserService userService) {
        this.tutorialGroupRepository = tutorialGroupRepository;
        this.courseRepository = courseRepository;
        this.userService = userService;
    }

    public void deregisterStudent(User student, TutorialGroup tutorialGroup) {
        tutorialGroup.getRegisteredStudents().remove(student);
        tutorialGroupRepository.save(tutorialGroup);
    }

    public void registerStudent(User student, TutorialGroup tutorialGroup) {
        tutorialGroup.getRegisteredStudents().add(student);
        tutorialGroupRepository.save(tutorialGroup);
    }

    public List<StudentDTO> registerMultipleStudents(Long courseId, Long tutorialGroupId, List<StudentDTO> studentDTOs) {
        var course = this.courseRepository.findByIdElseThrow(courseId);
        var tutorialGroup = tutorialGroupRepository.findByIdWithTeachingAssistantAndRegisteredStudentsElseThrow(tutorialGroupId);

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
