package de.tum.cit.aet.artemis.presentation.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

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

    public PresentationAssessmentService(PresentationAssessmentRepository presentationAssessmentRepository) {
        this.presentationAssessmentRepository = presentationAssessmentRepository;
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
        applyDto(presentationAssessment, dto);
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
    public PresentationAssessment update(long courseId, long assessmentId, PresentationAssessmentDTO dto) {
        if (dto.id() == null) {
            throw new BadRequestAlertException("A presentation assessment update must have an ID", PresentationAssessment.ENTITY_NAME, "idMissing");
        }
        if (!dto.id().equals(assessmentId)) {
            throw new BadRequestAlertException("The path id and body id must match", PresentationAssessment.ENTITY_NAME, "idMismatch");
        }
        PresentationAssessment presentationAssessment = findByIdAndCourseIdElseThrow(courseId, assessmentId);
        applyDto(presentationAssessment, dto);
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
}
