package de.tum.cit.aet.artemis.service.exam;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import de.tum.cit.aet.artemis.config.Constants;
import de.tum.cit.aet.artemis.domain.Course;
import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.domain.exam.Exam;
import de.tum.cit.aet.artemis.domain.exam.ExamUser;
import de.tum.cit.aet.artemis.domain.exam.StudentExam;
import de.tum.cit.aet.artemis.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.repository.CourseRepository;
import de.tum.cit.aet.artemis.repository.ExamRepository;
import de.tum.cit.aet.artemis.repository.ExamUserRepository;
import de.tum.cit.aet.artemis.repository.StudentExamRepository;
import de.tum.cit.aet.artemis.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.repository.UserRepository;
import de.tum.cit.aet.artemis.security.Role;
import de.tum.cit.aet.artemis.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.service.ParticipationService;
import de.tum.cit.aet.artemis.service.user.UserService;
import de.tum.cit.aet.artemis.web.rest.dto.ExamUserDTO;
import de.tum.cit.aet.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.cit.aet.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.cit.aet.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Service Implementation for registering students in the exam.
 */
@Profile(PROFILE_CORE)
@Service
public class ExamRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(ExamRegistrationService.class);

    private final UserRepository userRepository;

    private final ExamUserRepository examUserRepository;

    private final UserService userService;

    private final ParticipationService participationService;

    private final StudentExamRepository studentExamRepository;

    private final AuditEventRepository auditEventRepository;

    private final ExamRepository examRepository;

    private final CourseRepository courseRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final ExamUserService examUserService;

    private static final boolean IS_TEST_RUN = false;

    public ExamRegistrationService(ExamUserRepository examUserRepository, ExamRepository examRepository, UserService userService, ParticipationService participationService,
            UserRepository userRepository, AuditEventRepository auditEventRepository, CourseRepository courseRepository, StudentExamRepository studentExamRepository,
            StudentParticipationRepository studentParticipationRepository, AuthorizationCheckService authorizationCheckService, ExamUserService examUserService) {
        this.examRepository = examRepository;
        this.userService = userService;
        this.userRepository = userRepository;
        this.participationService = participationService;
        this.auditEventRepository = auditEventRepository;
        this.courseRepository = courseRepository;
        this.studentExamRepository = studentExamRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.examUserRepository = examUserRepository;
        this.examUserService = examUserService;
    }

    /**
     * Add multiple users to the students of the exam so that they can access the exam
     * The passed list of UserDTOs must include at least one unique user identifier (i.e. registration number OR email OR login)
     * <p>
     * This method first tries to find the user in the internal Artemis user database (because the user is probably already using Artemis).
     * In case the user cannot be found, it additionally searches the connected LDAP in case it is configured.
     *
     * @param courseId     the id of the course
     * @param examId       the id of the exam
     * @param examUserDTOs the list of students (with at least registration number) who should get access to the exam
     * @return the list of students who could not be registered for the exam, because they could NOT be found in the Artemis database and could NOT be found in the TUM LDAP
     */
    public List<ExamUserDTO> registerStudentsForExam(Long courseId, Long examId, List<ExamUserDTO> examUserDTOs) {
        var course = courseRepository.findByIdElseThrow(courseId);
        var exam = examRepository.findByIdWithExamUsersElseThrow(examId);

        if (exam.isTestExam()) {
            throw new AccessForbiddenException("Registration of students is only allowed for real exams");
        }

        List<ExamUserDTO> notFoundStudentsDTOs = new ArrayList<>();
        List<String> usersAddedToExam = new ArrayList<>();
        for (var examUserDto : examUserDTOs) {
            Optional<User> optionalStudent = userService.findUserAndAddToCourse(examUserDto.registrationNumber(), examUserDto.login(), examUserDto.email(),
                    course.getStudentGroupName());
            if (optionalStudent.isEmpty()) {
                notFoundStudentsDTOs.add(examUserDto);
            }
            else {
                User student = optionalStudent.get();
                Optional<ExamUser> examUserOptional = examUserRepository.findByExamIdAndUserId(exam.getId(), student.getId());

                if ((examUserOptional.isEmpty() || !exam.getExamUsers().contains(examUserOptional.get())) && !authorizationCheckService.isInstructorInCourse(course, student)
                        && !authorizationCheckService.isAdmin(student)) {
                    ExamUser registeredExamUser = new ExamUser();
                    registeredExamUser.setUser(optionalStudent.get());
                    registeredExamUser.setExam(exam);

                    if (StringUtils.hasText(examUserDto.room())) {
                        registeredExamUser.setPlannedRoom(examUserDto.room());
                    }
                    if (StringUtils.hasText(examUserDto.seat())) {
                        registeredExamUser.setPlannedSeat(examUserDto.seat());
                    }
                    registeredExamUser = examUserRepository.save(registeredExamUser);
                    exam.addExamUser(registeredExamUser);
                    usersAddedToExam.add(registeredExamUser.getUser().getLogin());
                }

                if (examUserOptional.isPresent() && exam.getExamUsers().contains(examUserOptional.get())) {
                    ExamUser examUser = examUserOptional.get();
                    examUser.setPlannedRoom(examUserDto.room());
                    examUser.setPlannedSeat(examUserDto.seat());
                    examUser = examUserRepository.save(examUser);
                    exam.addExamUser(examUser);
                    usersAddedToExam.add(examUser.getUser().getLogin());
                }
            }
        }
        examRepository.save(exam);

        try {
            User currentUser = userRepository.getUserWithGroupsAndAuthorities();
            Map<String, Object> userData = new HashMap<>();
            userData.put("exam", exam.getTitle());
            for (var i = 0; i < examUserDTOs.size(); i++) {
                var studentDTO = examUserDTOs.get(i);
                userData.put("student" + i, studentDTO.login() + " (" + studentDTO.registrationNumber() + ")");
            }
            AuditEvent auditEvent = new AuditEvent(currentUser.getLogin(), Constants.ADD_USER_TO_EXAM, userData);
            auditEventRepository.add(auditEvent);
            log.info("User {} has added multiple users {} to the exam {} with id {}", currentUser.getLogin(), usersAddedToExam, exam.getTitle(), exam.getId());
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
     * Registers student to the exam. In order to do this, we add the user to the course group, because the user only has access to the exam of a course if the student also has
     * access to the course of the exam.
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

        if (!student.getGroups().contains(course.getStudentGroupName())) {
            userService.addUserToGroup(student, course.getStudentGroupName());
        }

        Optional<ExamUser> registeredExamUserOptional = examUserRepository.findByExamIdAndUserId(exam.getId(), student.getId());

        if (registeredExamUserOptional.isEmpty() || !exam.getExamUsers().contains(registeredExamUserOptional.get())) {
            ExamUser registeredExamUser = new ExamUser();
            registeredExamUser.setUser(student);
            registeredExamUser.setExam(exam);
            registeredExamUser = examUserRepository.save(registeredExamUser);
            exam.addExamUser(registeredExamUser);
            examRepository.save(exam);
        }
        else {
            log.warn("Student {} is already registered for the exam {}", student.getLogin(), exam.getId());
            return;
        }

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
        Exam exam = examRepository.findByIdWithExamUsersElseThrow(examId);

        if (!exam.isTestExam()) {
            throw new BadRequestAlertException("Self-Registration is only allowed for test exams", "ExamRegistrationService", "SelfRegistrationOnlyForRealExams");
        }

        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, currentUser);
        Optional<ExamUser> registeredExamUserOptional = examUserRepository.findByExamIdAndUserId(exam.getId(), currentUser.getId());
        ExamUser registeredExamUser = null;
        if (registeredExamUserOptional.isEmpty()) {
            registeredExamUser = createExamUser(exam, currentUser);
        }

        // We only need to update the registered exam users, if the user is not yet registered for the test exam
        if (registeredExamUser != null && !exam.getExamUsers().contains(registeredExamUser)) {
            exam.addExamUser(registeredExamUser);
            examRepository.save(exam);

            AuditEvent auditEvent = new AuditEvent(currentUser.getLogin(), Constants.ADD_USER_TO_EXAM, "TestExam=" + exam.getTitle());
            auditEventRepository.add(auditEvent);
            log.info("User {} has self-registered to the test exam {} with id {}", currentUser.getLogin(), exam.getTitle(), exam.getId());
        }
    }

    /**
     * @param exam                              the exam with eagerly loaded registered users for which a student should be unregistered
     * @param deleteParticipationsAndSubmission whether the participations and submissions of the student should be deleted
     * @param student                           the user object that should be unregistered
     */
    public void unregisterStudentFromExam(Exam exam, boolean deleteParticipationsAndSubmission, User student) {
        ExamUser registeredExamUser = examUserRepository.findByExamIdAndUserId(exam.getId(), student.getId())
                .orElseThrow(() -> new EntityNotFoundException("User with login: \"" + student.getLogin() + "\" is not registered to the exam with id: \"" + exam.getId() + "\""));
        exam.removeExamUser(registeredExamUser);

        // Note: we intentionally do not remove the user from the course, because the student might just have "unregistered" from the exam, but should
        // still have access to the course.
        examRepository.save(exam);
        examUserRepository.delete(registeredExamUser);

        examUserService.deleteAvailableExamUserImages(registeredExamUser);

        // The student exam might already be generated, then we need to delete it
        Optional<StudentExam> optionalStudentExam = studentExamRepository.findWithExercisesByUserIdAndExamId(student.getId(), exam.getId(), IS_TEST_RUN);
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
            List<StudentParticipation> participations = studentParticipationRepository.findByStudentExamWithEagerSubmissions(studentExam);
            for (var participation : participations) {
                participationService.delete(participation.getId(), true, true, true);
            }
        }

        // Delete the student exam
        studentExamRepository.deleteById(studentExam.getId());
    }

    /**
     * Unregisters all students from the exam
     *
     * @param exam                              the exam with eagerly loaded registered users for which all students should be unregistered
     * @param deleteParticipationsAndSubmission whether the participations and submissions of the student should be deleted
     */
    public void unregisterAllStudentFromExam(Exam exam, boolean deleteParticipationsAndSubmission) {
        // remove all registered students
        List<ExamUser> registeredExamUsers = examUserRepository.findAllByExamId(exam.getId());
        registeredExamUsers.forEach(exam::removeExamUser);
        examRepository.save(exam);
        examUserRepository.deleteAllById(registeredExamUsers.stream().map(ExamUser::getId).toList());

        registeredExamUsers.forEach(examUserService::deleteAvailableExamUserImages);

        // remove all students exams
        Set<StudentExam> studentExams = studentExamRepository.findAllWithoutTestRunsWithExercisesByExamId(exam.getId());
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
     * @param exam     the exam with eagerly loaded registered users to which the course students should be added
     */
    public void addAllStudentsOfCourseToExam(Long courseId, Exam exam) {
        Course course = courseRepository.findByIdElseThrow(courseId);
        var students = new ArrayList<>(userRepository.getStudents(course));

        Map<String, Object> userData = new HashMap<>();
        userData.put("exam", exam.getTitle());
        for (int i = 0; i < students.size(); i++) {
            var student = students.get(i);
            Optional<ExamUser> registeredExamUserCheckOptional = examUserRepository.findByExamIdAndUserId(exam.getId(), student.getId());
            if (registeredExamUserCheckOptional.isEmpty() && !authorizationCheckService.isInstructorInCourse(course, student) && !authorizationCheckService.isAdmin(student)) {
                ExamUser registeredExamUser = createExamUser(exam, student);
                exam.addExamUser(registeredExamUser);
                userData.put("student " + i, student.toDatabaseString());
            }
        }

        examRepository.save(exam);
        AuditEvent auditEvent = new AuditEvent(userRepository.getUser().getLogin(), Constants.ADD_USER_TO_EXAM, userData);
        auditEventRepository.add(auditEvent);
    }

    private ExamUser createExamUser(Exam exam, User user) {
        ExamUser examUser = new ExamUser();
        examUser.setExam(exam);
        examUser.setUser(user);
        return examUserRepository.save(examUser);
    }
}
