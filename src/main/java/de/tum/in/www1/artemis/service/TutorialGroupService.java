package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;
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
import de.tum.in.www1.artemis.service.dto.StudentDTO;
import de.tum.in.www1.artemis.service.notifications.SingleUserNotificationService;

@Service
public class TutorialGroupService {

    private final SingleUserNotificationService singleUserNotificationService;

    private final TutorialGroupRegistrationRepository tutorialGroupRegistrationRepository;

    private final CourseRepository courseRepository;

    private final UserRepository userRepository;

    public TutorialGroupService(SingleUserNotificationService singleUserNotificationService, TutorialGroupRegistrationRepository tutorialGroupRegistrationRepository,
            CourseRepository courseRepository, UserRepository userRepository) {
        this.singleUserNotificationService = singleUserNotificationService;
        this.tutorialGroupRegistrationRepository = tutorialGroupRegistrationRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
    }

    /**
     * Deregister a student from a tutorial group.
     *
     * @param student          The student to deregister.
     * @param tutorialGroup    The tutorial group to deregister from.
     * @param registrationType The type of registration.
     * @param responsibleUser  The user who is responsible for the deregistration.
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

    private void registerMultipleStudentsToTutorialGroup(Set<User> students, TutorialGroup tutorialGroup, TutorialGroupRegistrationType registrationType, User responsibleUser) {
        Set<User> registeredStudents = tutorialGroupRegistrationRepository.findAllByTutorialGroupAndType(tutorialGroup, registrationType).stream()
                .map(TutorialGroupRegistration::getStudent).collect(Collectors.toSet());
        Set<User> studentsToRegister = students.stream().filter(student -> !registeredStudents.contains(student)).collect(Collectors.toSet());
        Set<TutorialGroupRegistration> newRegistrations = studentsToRegister.stream().map(student -> new TutorialGroupRegistration(student, tutorialGroup, registrationType))
                .collect(Collectors.toSet());
        tutorialGroupRegistrationRepository.saveAll(newRegistrations);

        for (User student : studentsToRegister) {
            singleUserNotificationService.notifyStudentAboutRegistrationToTutorialGroup(tutorialGroup, student, responsibleUser);
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
        registerMultipleStudentsToTutorialGroup(foundStudents, tutorialGroup, registrationType, responsibleUser);
        return notFoundStudentDTOs;
    }

    public List<TutorialGroup> findAllForNotifications(User user) {
        return courseRepository.findAllActiveWithTutorialGroupsWhereUserIsRegisteredOrTutor(ZonedDateTime.now(), user.getId()).stream()
                .flatMap(course -> course.getTutorialGroups().stream()).collect(Collectors.toList());

    }

    private Optional<User> findStudent(StudentDTO studentDto, String studentCourseGroupName) {
        var userOptional = userRepository.findUserWithGroupsAndAuthoritiesByRegistrationNumber(studentDto.getRegistrationNumber())
                .or(() -> userRepository.findUserWithGroupsAndAuthoritiesByLogin(studentDto.getLogin()));
        return userOptional.isPresent() && userOptional.get().getGroups().contains(studentCourseGroupName) ? userOptional : Optional.empty();
    }
}
