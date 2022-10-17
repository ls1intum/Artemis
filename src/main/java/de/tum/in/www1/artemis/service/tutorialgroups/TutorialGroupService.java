package de.tum.in.www1.artemis.service.tutorialgroups;

import java.util.*;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.tutorialgroups.TutorialGroupRegistrationType;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroup;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupRegistration;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupRegistrationRepository;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupRepository;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.dto.StudentDTO;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

@Service
public class TutorialGroupService {

    private final TutorialGroupRegistrationRepository tutorialGroupRegistrationRepository;

    private final TutorialGroupRepository tutorialGroupRepository;

    private final UserRepository userRepository;

    private final AuthorizationCheckService authorizationCheckService;

    public TutorialGroupService(TutorialGroupRegistrationRepository tutorialGroupRegistrationRepository, TutorialGroupRepository tutorialGroupRepository,
            UserRepository userRepository, AuthorizationCheckService authorizationCheckService) {
        this.tutorialGroupRegistrationRepository = tutorialGroupRegistrationRepository;
        this.tutorialGroupRepository = tutorialGroupRepository;
        this.userRepository = userRepository;
        this.authorizationCheckService = authorizationCheckService;
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
     * Get all tutorial groups for a course, including setting the transient properties for the given user
     *
     * @param course The course for which the tutorial groups should be retrieved.
     * @param user   The user for whom to set the transient properties of the tutorial groups.
     * @return A list of tutorial groups for the given course with the transient properties set for the given user.
     */
    public Set<TutorialGroup> findAllForCourse(@NotNull Course course, @NotNull User user) {
        Set<TutorialGroup> tutorialGroups = tutorialGroupRepository.findAllByCourseIdWithTeachingAssistantAndRegistrations(course.getId());
        tutorialGroups.forEach(tutorialGroup -> tutorialGroup.setTransientPropertiesForUser(user));
        tutorialGroups.forEach(tutorialGroup -> {
            if (!authorizationCheckService.isAllowedToSeePrivateTutorialGroupInformation(tutorialGroup, user)) {
                tutorialGroup.hidePrivacySensitiveInformation();
            }
        });
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
        tutorialGroup.setTransientPropertiesForUser(user);
        if (!authorizationCheckService.isAllowedToSeePrivateTutorialGroupInformation(tutorialGroup, user)) {
            tutorialGroup.hidePrivacySensitiveInformation();
        }
        return tutorialGroup;
    }

    private Optional<User> findStudent(StudentDTO studentDto, String studentCourseGroupName) {
        var userOptional = userRepository.findUserWithGroupsAndAuthoritiesByRegistrationNumber(studentDto.getRegistrationNumber())
                .or(() -> userRepository.findUserWithGroupsAndAuthoritiesByLogin(studentDto.getLogin()));
        return userOptional.isPresent() && userOptional.get().getGroups().contains(studentCourseGroupName) ? userOptional : Optional.empty();
    }

}
