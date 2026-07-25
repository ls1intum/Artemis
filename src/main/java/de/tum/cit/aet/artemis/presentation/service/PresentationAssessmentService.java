package de.tum.cit.aet.artemis.presentation.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.presentation.domain.PresentationAssessment;
import de.tum.cit.aet.artemis.presentation.dto.PresentationAssessmentDTO;
import de.tum.cit.aet.artemis.presentation.repository.PresentationAssessmentRepository;

/**
 * Service for managing course-level presentation assessments.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class PresentationAssessmentService {

    private final PresentationAssessmentRepository presentationAssessmentRepository;

    private final UserRepository userRepository;

    public PresentationAssessmentService(PresentationAssessmentRepository presentationAssessmentRepository, UserRepository userRepository) {
        this.presentationAssessmentRepository = presentationAssessmentRepository;
        this.userRepository = userRepository;
    }

    /**
     * Find all presentation assessments for a course.
     *
     * @param courseId the course id
     * @return the presentation assessments in the course
     */
    public List<PresentationAssessment> findAllByCourseId(long courseId) {
        return presentationAssessmentRepository.findAllByCourseId(courseId);
    }

    /**
     * Find a presentation assessment by id and course id.
     *
     * @param courseId     the course id
     * @param assessmentId the presentation assessment id
     * @return the presentation assessment
     */
    public PresentationAssessment findByIdAndCourseIdElseThrow(long courseId, long assessmentId) {
        return presentationAssessmentRepository.findByIdAndCourseId(assessmentId, courseId)
                .orElseThrow(() -> new EntityNotFoundException(PresentationAssessment.ENTITY_NAME, assessmentId));
    }

    /**
     * Create a presentation assessment in a course.
     *
     * @param course the owning course
     * @param dto    the presentation assessment data
     * @return the persisted presentation assessment
     */
    public PresentationAssessment create(Course course, PresentationAssessmentDTO dto) {
        if (dto.id() != null) {
            throw new BadRequestAlertException("A new presentation assessment cannot already have an ID", PresentationAssessment.ENTITY_NAME, "idExists");
        }
        PresentationAssessment presentationAssessment = new PresentationAssessment();
        presentationAssessment.setCourse(course);
        Set<User> students = findCourseStudentsByLogins(course, dto.studentLogins());
        applyDto(presentationAssessment, dto);
        presentationAssessment.setStudents(students);
        return presentationAssessmentRepository.save(presentationAssessment);
    }

    /**
     * Update a presentation assessment.
     *
     * @param courseId     the course id
     * @param assessmentId the presentation assessment id
     * @param dto          the updated presentation assessment data
     * @return the persisted presentation assessment
     */
    public PresentationAssessment update(Course course, long assessmentId, PresentationAssessmentDTO dto) {
        if (dto.id() == null) {
            throw new BadRequestAlertException("A presentation assessment update must have an ID", PresentationAssessment.ENTITY_NAME, "idMissing");
        }
        if (!dto.id().equals(assessmentId)) {
            throw new BadRequestAlertException("The path id and body id must match", PresentationAssessment.ENTITY_NAME, "idMismatch");
        }
        Set<User> students = dto.studentLogins() != null ? findCourseStudentsByLogins(course, dto.studentLogins()) : null;
        PresentationAssessment presentationAssessment = findByIdAndCourseIdElseThrow(course.getId(), assessmentId);
        applyDto(presentationAssessment, dto);
        if (students != null) {
            presentationAssessment.setStudents(students);
        }
        return presentationAssessmentRepository.save(presentationAssessment);
    }

    /**
     * Delete a presentation assessment.
     *
     * @param courseId     the course id
     * @param assessmentId the presentation assessment id
     */
    public void delete(long courseId, long assessmentId) {
        PresentationAssessment presentationAssessment = findByIdAndCourseIdElseThrow(courseId, assessmentId);
        presentationAssessmentRepository.delete(presentationAssessment);
    }

    /**
     * Find all students assigned to a presentation assessment.
     *
     * @param courseId     the course id
     * @param assessmentId the presentation assessment id
     * @return the assigned students
     */
    public Set<User> findStudents(long courseId, long assessmentId) {
        return findWithStudentsByIdAndCourseIdElseThrow(courseId, assessmentId).getStudents();
    }

    /**
     * Add a course student to a presentation assessment.
     *
     * @param course       the owning course
     * @param assessmentId the presentation assessment id
     * @param studentLogin the login of the student to add
     */
    public void addStudent(Course course, long assessmentId, String studentLogin) {
        PresentationAssessment presentationAssessment = findWithStudentsByIdAndCourseIdElseThrow(course.getId(), assessmentId);
        User student = userRepository.findOneWithGroupsAndAuthoritiesByLogin(studentLogin)
                .orElseThrow(() -> new EntityNotFoundException("User with login " + studentLogin + " does not exist"));
        if (!student.getGroups().contains(course.getStudentGroupName())) {
            throw new BadRequestAlertException("The user is not a student in the course", PresentationAssessment.ENTITY_NAME, "studentNotInCourse");
        }
        presentationAssessment.getStudents().add(student);
        presentationAssessmentRepository.save(presentationAssessment);
    }

    /**
     * Remove a student from a presentation assessment.
     *
     * @param courseId     the course id
     * @param assessmentId the presentation assessment id
     * @param studentLogin the login of the student to remove
     */
    public void removeStudent(long courseId, long assessmentId, String studentLogin) {
        PresentationAssessment presentationAssessment = findWithStudentsByIdAndCourseIdElseThrow(courseId, assessmentId);
        presentationAssessment.getStudents().removeIf(student -> studentLogin.equals(student.getLogin()));
        presentationAssessmentRepository.save(presentationAssessment);
    }

    private PresentationAssessment findWithStudentsByIdAndCourseIdElseThrow(long courseId, long assessmentId) {
        return presentationAssessmentRepository.findWithStudentsByIdAndCourseId(assessmentId, courseId)
                .orElseThrow(() -> new EntityNotFoundException(PresentationAssessment.ENTITY_NAME, assessmentId));
    }

    private void applyDto(PresentationAssessment presentationAssessment, PresentationAssessmentDTO dto) {
        if (dto.resultPoints() != null && dto.resultPoints() > dto.maxPoints()) {
            throw new BadRequestAlertException("The achieved result points cannot exceed the maximum points", PresentationAssessment.ENTITY_NAME, "resultPointsExceedMaxPoints");
        }
        presentationAssessment.setTitle(dto.title().trim());
        presentationAssessment.setDescription(dto.description());
        presentationAssessment.setMaxPoints(dto.maxPoints());
        presentationAssessment.setResultPoints(dto.resultPoints());
        presentationAssessment.setPresentationDate(dto.presentationDate());
    }

    private Set<User> findCourseStudentsByLogins(Course course, List<String> studentLogins) {
        if (studentLogins == null || studentLogins.isEmpty()) {
            return new HashSet<>();
        }

        Set<String> uniqueLogins = new HashSet<>();
        for (String login : studentLogins) {
            if (login == null || login.isBlank()) {
                throw new BadRequestAlertException("A student login must not be empty", PresentationAssessment.ENTITY_NAME, "studentLoginInvalid");
            }
            uniqueLogins.add(login.trim());
        }
        Set<User> students = new HashSet<>(userRepository.findAllWithGroupsByDeletedIsFalseAndGroupsContainsAndLoginIn(course.getStudentGroupName(), uniqueLogins));
        Set<String> foundLogins = students.stream().map(User::getLogin).collect(Collectors.toSet());
        if (!foundLogins.containsAll(uniqueLogins)) {
            throw new BadRequestAlertException("At least one user is not a student in the course", PresentationAssessment.ENTITY_NAME, "studentNotInCourse");
        }
        return students;
    }
}
