package de.tum.in.www1.artemis.service.exam;

import static de.tum.in.www1.artemis.domain.Authority.ADMIN_AUTHORITY;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.ParticipationService;
import de.tum.in.www1.artemis.service.dto.StudentDTO;
import de.tum.in.www1.artemis.service.user.UserService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Service Implementation for registering students in the exam.
 */
@Service
public class ExamRegistrationService {

    private final Logger log = LoggerFactory.getLogger(ExamRegistrationService.class);

    private final UserRepository userRepository;

    private final UserService userService;

    private final ParticipationService participationService;

    private final StudentExamRepository studentExamRepository;

    private final AuditEventRepository auditEventRepository;

    private final ExamRepository examRepository;

    private final CourseRepository courseRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final AuthorizationCheckService authorizationCheckService;

    public ExamRegistrationService(ExamRepository examRepository, UserService userService, ParticipationService participationService, UserRepository userRepository,
            AuditEventRepository auditEventRepository, CourseRepository courseRepository, StudentExamRepository studentExamRepository,
            StudentParticipationRepository studentParticipationRepository, AuthorizationCheckService authorizationCheckService) {
        this.examRepository = examRepository;
        this.userService = userService;
        this.userRepository = userRepository;
        this.participationService = participationService;
        this.auditEventRepository = auditEventRepository;
        this.courseRepository = courseRepository;
        this.studentExamRepository = studentExamRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.authorizationCheckService = authorizationCheckService;
    }

    /**
     * Add multiple users to the students of the exam so that they can access the exam
     * The passed list of UserDTOs must include the registration number (the other entries are currently ignored and can be left out)
     * Note: registration based on other user attributes (e.g. email, name, login) is currently NOT supported
     * <p>
     * This method first tries to find the student in the internal Artemis user database (because the user is most probably already using Artemis).
     * In case the user cannot be found, we additionally search the (TUM) LDAP in case it is configured properly.
     *
     * @param courseId    the id of the course
     * @param examId      the id of the exam
     * @param studentDTOs the list of students (with at least registration number) who should get access to the exam
     * @return the list of students who could not be registered for the exam, because they could NOT be found in the Artemis database and could NOT be found in the TUM LDAP
     */
    public List<StudentDTO> registerStudentsForExam(Long courseId, Long examId, List<StudentDTO> studentDTOs) {
        var course = courseRepository.findByIdElseThrow(courseId);
        var exam = examRepository.findWithRegisteredUsersById(examId).orElseThrow(() -> new EntityNotFoundException("Exam", examId));

        if (exam.isTestExam()) {
            throw new AccessForbiddenException("Registration of students is only allowed for real exams");
        }

        List<StudentDTO> notFoundStudentsDTOs = new ArrayList<>();
        for (var studentDto : studentDTOs) {
            var registrationNumber = studentDto.getRegistrationNumber();
            var login = studentDto.getLogin();
            Optional<User> optionalStudent = userService.findUserAndAddToCourse(registrationNumber, course.getStudentGroupName(), Role.STUDENT, login);
            if (optionalStudent.isEmpty()) {
                notFoundStudentsDTOs.add(studentDto);
            }
            else {
                exam.addRegisteredUser(optionalStudent.get());
            }
        }
        examRepository.save(exam);

        try {
            User currentUser = userRepository.getUserWithGroupsAndAuthorities();
            Map<String, Object> userData = new HashMap<>();
            userData.put("exam", exam.getTitle());
            for (var i = 0; i < studentDTOs.size(); i++) {
                var studentDTO = studentDTOs.get(i);
                userData.put("student" + i, studentDTO.toDatabaseString());
            }
            AuditEvent auditEvent = new AuditEvent(currentUser.getLogin(), Constants.ADD_USER_TO_EXAM, userData);
            auditEventRepository.add(auditEvent);
            log.info("User {} has added multiple users {} to the exam {} with id {}", currentUser.getLogin(), studentDTOs, exam.getTitle(), exam.getId());
        }
        catch (Exception ex) {
            log.warn("Could not add audit event to audit log", ex);
        }

        return notFoundStudentsDTOs;
    }

    /**
     * Returns <code>true</code> if the current user is registered for the exam
     *
     * @param examId the id of the exam
     * @return <code>true</code> if the user is registered for the exam, false if this is not the case or the exam does not exist
     */
    public boolean isCurrentUserRegisteredForExam(Long examId) {
        return isUserRegisteredForExam(examId, userRepository.getUser().getId());
    }

    /**
     * Returns <code>true</code> if the user with the given id is registered for the exam
     *
     * @param examId the id of the exam
     * @param userId the id of the user to check
     * @return <code>true</code> if the user is registered for the exam, false if this is not the case or the exam does not exist
     */
    public boolean isUserRegisteredForExam(Long examId, Long userId) {
        return examRepository.isUserRegisteredForExam(examId, userId);
    }

    /**
     * Registers student to the exam. In order to do this, we add the user to the course group, because the user only has access to the exam of a course if the student also has access to the course of the exam.
     * We only need to add the user to the course group, if the student is not yet part of it, otherwise the student cannot access the exam (within the course).
     *
     * @param course  the course containing the exam
     * @param exam    the exam for which we want to register a student
     * @param student the student to be registered to the exam
     */
    public void registerStudentToExam(Course course, Exam exam, User student) {
        if (exam.isTestExam()) {
            throw new AccessForbiddenException("Registration of students is only allowed for real exams");
        }

        exam.addRegisteredUser(student);

        if (!student.getGroups().contains(course.getStudentGroupName())) {
            userService.addUserToGroup(student, course.getStudentGroupName(), Role.STUDENT);
        }
        examRepository.save(exam);

        User currentUser = userRepository.getUserWithGroupsAndAuthorities();
        AuditEvent auditEvent = new AuditEvent(currentUser.getLogin(), Constants.ADD_USER_TO_EXAM, "exam=" + exam.getTitle(), "student=" + student.getLogin());
        auditEventRepository.add(auditEvent);
        log.info("User {} has added user {} to the exam {} with id {}", currentUser.getLogin(), student.getLogin(), exam.getTitle(), exam.getId());
    }

    /**
     * Checks if the current User is registered for the test exam, otherwise the User is registered to the test exam.
     * The calling user must be registered in the respective course
     *
     * @param course      the course containing the exam
     * @param examId      the examId for which we want to register a student
     * @param currentUser the user to be registered in the exam
     */
    public void checkRegistrationOrRegisterStudentToTestExam(Course course, long examId, User currentUser) {
        Exam exam = examRepository.findByIdWithRegisteredUsersElseThrow(examId);

        if (!exam.isTestExam()) {
            // TODO: move to resource and change to BadRequestAlertException
            throw new AccessForbiddenException("Self-Registration is only allowed for test exams");
        }

        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, currentUser);

        // We only need to update the registered users, if the user is not yet registered for the test exam
        if (!exam.getRegisteredUsers().contains(currentUser)) {
            exam.addRegisteredUser(currentUser);
            examRepository.save(exam);

            AuditEvent auditEvent = new AuditEvent(currentUser.getLogin(), Constants.ADD_USER_TO_EXAM, "TestExam=" + exam.getTitle());
            auditEventRepository.add(auditEvent);
            log.info("User {} has self-registered to the test exam {} with id {}", currentUser.getLogin(), exam.getTitle(), exam.getId());
        }
    }

    /**
     * @param examId                            the exam for which a student should be unregistered
     * @param deleteParticipationsAndSubmission whether the participations and submissions of the student should be deleted
     * @param student                           the user object that should be unregistered
     */
    public void unregisterStudentFromExam(Long examId, boolean deleteParticipationsAndSubmission, User student) {
        var exam = examRepository.findWithRegisteredUsersById(examId).orElseThrow(() -> new EntityNotFoundException("Exam", examId));

        if (exam.isTestExam()) {
            // TODO: move to resource and change to BadRequestAlertException
            throw new AccessForbiddenException("Deletion of users is only allowed for real exams");
        }

        exam.removeRegisteredUser(student);

        // Note: we intentionally do not remove the user from the course, because the student might just have "unregistered" from the exam, but should
        // still have access to the course.
        examRepository.save(exam);

        // The student exam might already be generated, then we need to delete it
        Optional<StudentExam> optionalStudentExam = studentExamRepository.findWithExercisesByUserIdAndExamId(student.getId(), exam.getId());
        optionalStudentExam.ifPresent(studentExam -> removeStudentExam(studentExam, deleteParticipationsAndSubmission));

        User currentUser = userRepository.getUserWithGroupsAndAuthorities();
        AuditEvent auditEvent = new AuditEvent(currentUser.getLogin(), Constants.REMOVE_USER_FROM_EXAM, "exam=" + exam.getTitle(), "user=" + student.getLogin());
        auditEventRepository.add(auditEvent);
        log.info("User {} has removed user {} from the exam {} with id {}. This also deleted a potentially existing student exam with all its participations and submissions.",
                currentUser.getLogin(), student.getLogin(), exam.getTitle(), exam.getId());
    }

    private void removeStudentExam(StudentExam studentExam, boolean deleteParticipationsAndSubmission) {

        // Optionally delete participations and submissions
        if (deleteParticipationsAndSubmission) {
            List<StudentParticipation> participations = studentParticipationRepository.findByStudentExamWithEagerSubmissionsResult(studentExam, false);
            for (var participation : participations) {
                participationService.delete(participation.getId(), true, true);
            }
        }

        // Delete the student exam
        studentExamRepository.deleteById(studentExam.getId());
    }

    /**
     * Unregisters all students from the exam
     *
     * @param examId                            the exam for which a student should be unregistered
     * @param deleteParticipationsAndSubmission whether the participations and submissions of the student should be deleted
     */
    public void unregisterAllStudentFromExam(Long examId, boolean deleteParticipationsAndSubmission) {
        var exam = examRepository.findWithRegisteredUsersById(examId).orElseThrow(() -> new EntityNotFoundException("Exam", examId));

        if (exam.isTestExam()) {
            // TODO: move to resource and change to BadRequestAlertException
            throw new AccessForbiddenException("Unregistration is only allowed for real exams");
        }

        // remove all registered students
        List<Long> userIds = new ArrayList<>();
        exam.getRegisteredUsers().forEach(user -> userIds.add(user.getId()));
        List<User> registeredStudentsList = userRepository.findAllById(userIds);
        registeredStudentsList.forEach(exam::removeRegisteredUser);
        examRepository.save(exam);

        // remove all students exams
        Set<StudentExam> studentExams = studentExamRepository.findAllWithExercisesByExamId(examId);
        studentExams.forEach(studentExam -> removeStudentExam(studentExam, deleteParticipationsAndSubmission));

        User currentUser = userRepository.getUserWithGroupsAndAuthorities();
        AuditEvent auditEvent = new AuditEvent(currentUser.getLogin(), Constants.REMOVE_ALL_USERS_FROM_EXAM, "exam=" + exam.getTitle());
        auditEventRepository.add(auditEvent);
        log.info("User {} has removed all users from the exam {} with id {}. This also deleted potentially existing student exams with all its participations and submissions.",
                currentUser.getLogin(), exam.getTitle(), exam.getId());
    }

    /**
     * Adds all students registered in the course to the given exam
     *
     * @param courseId Id of the course
     * @param examId   Id of the exam
     */
    public void addAllStudentsOfCourseToExam(Long courseId, Long examId) {
        Course course = courseRepository.findByIdElseThrow(courseId);
        var students = userRepository.getStudents(course);
        var exam = examRepository.findByIdWithRegisteredUsersElseThrow(examId);

        if (exam.isTestExam()) {
            // TODO: move to resource and change to BadRequestAlertException
            throw new AccessForbiddenException("Registration is only allowed for real exams");
        }

        Map<String, Object> userData = new HashMap<>();
        userData.put("exam", exam.getTitle());
        for (int i = 0; i < students.size(); i++) {
            var student = students.get(i);
            if (!exam.getRegisteredUsers().contains(student) && !student.getAuthorities().contains(ADMIN_AUTHORITY)
                    && !student.getGroups().contains(course.getInstructorGroupName())) {
                exam.addRegisteredUser(student);
                userData.put("student " + i, student.toDatabaseString());
            }
        }

        examRepository.save(exam);
        AuditEvent auditEvent = new AuditEvent(userRepository.getUser().getLogin(), Constants.ADD_USER_TO_EXAM, userData);
        auditEventRepository.add(auditEvent);
    }
}
