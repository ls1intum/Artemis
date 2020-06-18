package de.tum.in.www1.artemis.service;

import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.repository.StudentExamRepository;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Service Implementation for managing Course.
 */
@Service
public class ExamService {

    private final Logger log = LoggerFactory.getLogger(ExamService.class);

    private final ExamRepository examRepository;

    private final StudentExamRepository studentExamRepository;

    public ExamService(ExamRepository examRepository, StudentExamRepository studentExamRepository) {
        this.examRepository = examRepository;
        this.studentExamRepository = studentExamRepository;
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
     * Get one exam by id with exercise groups.
     *
     * @param examId the id of the entity
     * @return the exam with exercise groups
     */
    @NotNull
    public Exam findOneWithExerciseGroups(Long examId) {
        log.debug("Request to get exam with exercise groups : {}", examId);
        return examRepository.findWithExerciseGroupsById(examId).orElseThrow(() -> new EntityNotFoundException("Exam with id: \"" + examId + "\" does not exist"));
    }

    /**
     * Get one exam by id with registered users.
     *
     * @param examId the id of the entity
     * @return the exam with registered user
     */
    @NotNull
    public Exam findOneWithRegisteredUsers(Long examId) {
        log.debug("Request to get exam with registered users : {}", examId);
        return examRepository.findWithRegisteredUsersById(examId).orElseThrow(() -> new EntityNotFoundException("Exam with id: \"" + examId + "\" does not exist"));
    }

    /**
     * Get all exams for the given course.
     *
     * @param courseId the id of the course
     * @return the list of all exams
     */
    public List<Exam> findAllByCourseId(Long courseId) {
        log.debug("REST request to get all exams for Course : {}", courseId);
        return examRepository.findByCourseId(courseId);
    }

    /**
     * Get the exam of a course with exercise groups and student exams
     * @param examId {Long} The courseId of the course which contains the exam
     * @return The exam
     */
    public Exam findOneWithExercisesGroupsAndStudentExamsByExamId(Long examId) {
        log.debug("REST request to get the exam with student exams and exercise groups for Id : {}", examId);
        return examRepository.findOneWithEagerExercisesGroupsAndStudentExams(examId);
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
     * Filters the visible exams (excluding the ones that are not visible yet)
     *
     * @param exams a set of exams (e.g. the ones of a course)
     * @return only the visible exams
     */
    public Set<Exam> filterVisibleExams(Set<Exam> exams) {
        return exams.stream().filter(Exam::isVisibleToStudents).collect(Collectors.toSet());
    }

    /**
     * Generates the student exams randomly based on the exam configuration and the exercise groups
     *
     * @param examId        the id of the exam
     * @return the list of student exams with their corresponding users
     */
    public List<StudentExam> generateStudentExams(Long examId) {
        List<StudentExam> studentExams = new ArrayList<>();
        SecureRandom random = new SecureRandom();

        Exam exam = examRepository.findWithExercisesRegisteredUsersStudentExamsById(examId).get();
        // TODO: double check that all previously generated student exams are automatically deleted (based on orphan removal)

        for (User registeredUser : exam.getRegisteredUsers()) {
            // create one student exam per user
            StudentExam studentExam = new StudentExam();
            studentExam.setExam(exam);
            studentExam.setUser(registeredUser);

            List<ExerciseGroup> exerciseGroups = exam.getExerciseGroups();

            if (exerciseGroups.size() < exam.getNumberOfExercisesInExam()) {
                throw new BadRequestAlertException("The number of exercise groups is too small", "Exam", "exam.validation.tooFewExerciseGroups");
            }

            long numberOfOptionalExercises = exam.getNumberOfExercisesInExam() - exerciseGroups.stream().filter(ExerciseGroup::getIsMandatory).count();

            if (numberOfOptionalExercises < 0) {
                throw new BadRequestAlertException("The number of mandatory exercise groups is too large", "Exam", "exam.validation.tooManyMandatoryExerciseGroups");
            }

            for (ExerciseGroup exerciseGroup : exam.getExerciseGroups()) {
                if (Boolean.TRUE.equals(exerciseGroup.getIsMandatory())) {
                    // we get one random exercise from all mandatory exercise groups
                    studentExam.addExercise(selectRandomExercise(random, exerciseGroup));
                }
                else {
                    // this is an optional exercise group, we randomly take those if we still have spots left
                    boolean shouldSelectOptionalExercise = random.nextBoolean();
                    if (numberOfOptionalExercises > 0 && shouldSelectOptionalExercise) {
                        studentExam.addExercise(selectRandomExercise(random, exerciseGroup));
                    }
                }
            }

            if (Boolean.TRUE.equals(exam.getRandomizeExerciseOrder())) {
                Collections.shuffle(studentExam.getExercises());
            }
        }

        studentExamRepository.saveAll(studentExams);

        // TODO: make sure the student exams still contain non proxy users

        return studentExams;
    }

    public Exercise selectRandomExercise(SecureRandom random, ExerciseGroup exerciseGroup) {
        List<Exercise> exercises = new ArrayList<>(exerciseGroup.getExercises());
        int randomIndex = random.nextInt(exercises.size());
        Exercise randomExercise = exercises.get(randomIndex);
        return randomExercise;
    }
}
