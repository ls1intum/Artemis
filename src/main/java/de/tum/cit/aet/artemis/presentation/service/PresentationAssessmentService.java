package de.tum.cit.aet.artemis.presentation.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
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

    private static final LocalDate EARLIEST_PRESENTATION_DATE = LocalDate.of(1970, 1, 1);

    private final PresentationAssessmentRepository presentationAssessmentRepository;

    private final UserRepository userRepository;

    public PresentationAssessmentService(PresentationAssessmentRepository presentationAssessmentRepository, UserRepository userRepository) {
        this.presentationAssessmentRepository = presentationAssessmentRepository;
        this.userRepository = userRepository;
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
        Set<User> students = resolveAssignedCourseStudents(course, dto.studentLogins());
        applyDto(presentationAssessment, dto);
        presentationAssessment.setStudents(students);
        return presentationAssessmentRepository.save(presentationAssessment);
    }

    /**
     * Update a presentation assessment.
     *
     * @param course the owning course
     * @param dto    the updated presentation assessment data
     * @return the persisted presentation assessment
     */
    public PresentationAssessment update(Course course, PresentationAssessmentDTO dto) {
        Set<User> students = dto.studentLogins() != null ? resolveAssignedCourseStudents(course, dto.studentLogins()) : null;
        PresentationAssessment presentationAssessment = presentationAssessmentRepository.findByIdAndCourseIdElseThrow(dto.id(), course.getId());
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
        PresentationAssessment presentationAssessment = presentationAssessmentRepository.findByIdAndCourseIdElseThrow(assessmentId, courseId);
        presentationAssessmentRepository.delete(presentationAssessment);
    }

    private void applyDto(PresentationAssessment presentationAssessment, PresentationAssessmentDTO dto) {
        if (dto.resultPoints() != null && dto.resultPoints() > dto.maxPoints()) {
            throw new BadRequestAlertException("The achieved result points cannot exceed the maximum points", PresentationAssessment.ENTITY_NAME, "resultPointsExceedMaxPoints");
        }
        if (dto.presentationDate() != null && dto.presentationDate().toLocalDate().isBefore(EARLIEST_PRESENTATION_DATE)) {
            throw new BadRequestAlertException("The presentation date cannot be before 1970-01-01", PresentationAssessment.ENTITY_NAME, "presentationDateBeforeUnixEpoch");
        }
        presentationAssessment.setTitle(dto.title().trim());
        presentationAssessment.setDescription(dto.description());
        presentationAssessment.setMaxPoints(dto.maxPoints());
        presentationAssessment.setResultPoints(dto.resultPoints());
        presentationAssessment.setPresentationDate(dto.presentationDate());
    }

    private Set<User> resolveAssignedCourseStudents(Course course, List<String> studentLogins) {
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
        Set<User> students = new HashSet<>(userRepository.findAllByCourseIdAndRoleAndLoginIn(course.getId(), CourseRole.STUDENT, uniqueLogins));
        Set<String> foundLogins = students.stream().map(User::getLogin).collect(Collectors.toSet());
        if (!foundLogins.containsAll(uniqueLogins)) {
            throw new BadRequestAlertException("At least one user is not a student in the course", PresentationAssessment.ENTITY_NAME, "studentNotInCourse");
        }
        return students;
    }
}
