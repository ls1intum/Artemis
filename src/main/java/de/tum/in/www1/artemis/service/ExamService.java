package de.tum.in.www1.artemis.service;

import java.util.Set;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Service Implementation for managing Course.
 */
@Service
public class ExamService {

    private final Logger log = LoggerFactory.getLogger(ExamService.class);

    private final ExamRepository examRepository;

    private final AuthorizationCheckService authCheckService;

    public ExamService(ExamRepository examRepository, AuthorizationCheckService authCheckService) {
        this.examRepository = examRepository;
        this.authCheckService = authCheckService;
    }

    /**
     * Save an exam.
     *
     * @param exam the entity to save
     * @return the persisted entity
     */
    public Exam save(Exam exam) {
        log.debug("Request to save exam : {}", exam);
        return examRepository.save(exam);
    }

    /**
     * Get one exam by id.
     *
     * @param examId the id of the entity
     * @return the entity
     */
    @NotNull
    public Exam findOne(Long examId) {
        log.debug("Request to get exam : {}", examId);
        return examRepository.findById(examId).orElseThrow(() -> new EntityNotFoundException("Exam with id: \"" + examId + "\" does not exist"));
    }

    /**
     * Delete the exam by id.
     *
     * @param examId the id of the entity
     */
    public void delete(Long examId) {
        log.debug("Request to delete exam : {}", examId);
        examRepository.deleteById(examId);
    }

    /**
     * find all visible exams for the given user in the given course
     *
     * @param course the course for which the exams should be found
     * @param user the user who wants to see the exams
     * @return all visible exams
     */
    public Set<Exam> findAllForCourse(Course course, User user) {
        Set<Exam> exams = examRepository.findByCourseId(course.getId());
        if (authCheckService.isAtLeastTeachingAssistantInCourse(course, user)) {
            // tutors/instructors/admins can see all exams of the course
        }
        else if (authCheckService.isStudentInCourse(course, user)) {
            // user is student for this course and might not have the right to see it so we have to
            // filter out exercises that are not released (or explicitly made visible to students) yet
            exams = exams.stream().filter(Exam::isVisibleToStudents).collect(Collectors.toSet());
        }

        return exams;
    }
}
