package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.*;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.domain.quiz.QuizSubmission;
import de.tum.in.www1.artemis.repository.ModelingSubmissionRepository;
import de.tum.in.www1.artemis.repository.QuizSubmissionRepository;
import de.tum.in.www1.artemis.repository.StudentExamRepository;
import de.tum.in.www1.artemis.repository.TextSubmissionRepository;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Service Implementation for managing StudentExam.
 */
@Service
public class StudentExamService {

    private static final String ENTITY_NAME = "studentExam";

    private final Logger log = LoggerFactory.getLogger(StudentExamService.class);

    private final ParticipationService participationService;

    private final StudentExamRepository studentExamRepository;

    private final QuizSubmissionRepository quizSubmissionRepository;

    private final TextSubmissionRepository textSubmissionRepository;

    private final ModelingSubmissionRepository modelingSubmissionRepository;

    public StudentExamService(StudentExamRepository studentExamRepository, ParticipationService participationService, QuizSubmissionRepository quizSubmissionRepository,
            TextSubmissionRepository textSubmissionRepository, ModelingSubmissionRepository modelingSubmissionRepository) {
        this.participationService = participationService;
        this.studentExamRepository = studentExamRepository;
        this.quizSubmissionRepository = quizSubmissionRepository;
        this.textSubmissionRepository = textSubmissionRepository;
        this.modelingSubmissionRepository = modelingSubmissionRepository;
    }

    /**
     * Get one student exam by id.
     *
     * @param studentExamId the id of the student exam
     * @return the student exam
     */
    @NotNull
    public StudentExam findOne(Long studentExamId) {
        log.debug("Request to get student exam : {}", studentExamId);
        return studentExamRepository.findById(studentExamId).orElseThrow(() -> new EntityNotFoundException("Student exam with id \"" + studentExamId + "\" does not exist"));
    }

    /**
     * Get one student exam by exam id and user.
     *
     * @param examId    the id of the exam
     * @param userId    the id of the user
     * @return the student exam with exercises
     */
    @NotNull
    public StudentExam findOneWithExercisesByUserIdAndExamId(Long userId, Long examId) {
        log.debug("Request to get student exam by userId {} and examId {}", userId, examId);
        return studentExamRepository.findWithExercisesByUserIdAndExamId(userId, examId)
                .orElseThrow(() -> new EntityNotFoundException("Student exam with for userId \"" + userId + "\" and examId \"" + examId + "\" does not exist"));
    }

    /**
     * Get one optional student exam by exam id and user.
     *
     * @param examId    the id of the exam
     * @param userId    the id of the user
     * @return the student exam with exercises
     */
    @NotNull
    public Optional<StudentExam> findOneWithExercisesByUserIdAndExamIdOptional(Long userId, Long examId) {
        log.debug("Request to get optional student exam by userId {} and examId {}", userId, examId);
        return studentExamRepository.findWithExercisesByUserIdAndExamId(userId, examId);
    }

    /**
     * Submit StudentExam and uses submissions as final submissions if studentExam is not yet submitted
     * and if it was submitted after exam startDate and before individual endDate + gracePeriod
     *
     * @param studentExam latest studentExam object which will be submitted (final submission)
     * @param currentUser the current user
     * @return ResponseEntity.ok() on success or HTTP error with a custom error message on failure
     */
    public ResponseEntity<Void> submitStudentExam(StudentExam studentExam, User currentUser) {
        log.debug("Submit student exam with id {}", studentExam.getId());
        // checks if student exam is already marked as submitted
        StudentExam existingStudentExam = findOne(studentExam.getId());
        if (Boolean.TRUE.equals(studentExam.isSubmitted()) || Boolean.TRUE.equals(existingStudentExam.isSubmitted())) {
            return conflict(ENTITY_NAME, "alreadySubmitted", "You have already submitted.");
        }

        // gets individual exam end or exam.endDate if individual cannot be calculated
        ZonedDateTime examEndDate = existingStudentExam.getExam().getStartDate() != null && existingStudentExam.getWorkingTime() != null
                ? existingStudentExam.getExam().getStartDate().plusSeconds(existingStudentExam.getWorkingTime())
                : existingStudentExam.getExam().getEndDate();

        // checks if student exam is live (after start date, before end date + grace period)
        if ((existingStudentExam.getExam().getStartDate() != null && !ZonedDateTime.now().isAfter(existingStudentExam.getExam().getStartDate()))
                || (examEndDate != null && !(ZonedDateTime.now().isBefore(examEndDate.plusSeconds(existingStudentExam.getExam().getGracePeriod()))))) {
            return forbidden(ENTITY_NAME, "submissionNotInTime", "You can only submit between start and end of the exam.");
        }

        List<StudentParticipation> existingParticipations = participationService.findByStudentIdAndIndividualExercisesWithEagerSubmissionsResult(currentUser.getId(),
                studentExam.getExercises());

        if (studentExam.getExercises() == null) {
            return badRequest();
        }

        for (Exercise exercise : studentExam.getExercises()) {
            // if exercise is either QuizExercise, TextExercise or ModelingExercise and exactly one participation exists
            if (exercise.getStudentParticipations() != null && exercise.getStudentParticipations().size() == 1) {
                for (StudentParticipation studentParticipation : exercise.getStudentParticipations()) {
                    StudentParticipation existingParticipation = existingParticipations.stream().filter(p -> p.getId().equals(studentParticipation.getId()))
                            .collect(Collectors.toList()).get(0);
                    // if exactly one submission exists we save the submission
                    if (studentParticipation.getSubmissions() != null && studentParticipation.getSubmissions().size() == 1) {
                        // check that the current user owns the participation
                        if (!studentParticipation.isOwnedBy(currentUser) || !existingParticipation.isOwnedBy(currentUser)) {
                            return forbidden();
                        }
                        studentParticipation.setExercise(exercise);
                        for (Submission submission : studentParticipation.getSubmissions()) {
                            // we do not apply the following checks for programming exercises
                            if (exercise instanceof ProgrammingExercise) {
                                continue;
                            }
                            // check that the submission belongs to the already saved participation
                            if (!existingParticipation.getSubmissions().contains(submission)) {
                                return forbidden();
                            }
                            // check that no result has been injected
                            if (submission.getResult() != null) {
                                return forbidden();
                            }
                            submission.setParticipation(studentParticipation);
                            submission.submissionDate(ZonedDateTime.now());
                            submission.submitted(true);
                            if (exercise instanceof QuizExercise) {
                                // recreate pointers back to submission in each submitted answer
                                for (SubmittedAnswer submittedAnswer : ((QuizSubmission) submission).getSubmittedAnswers()) {
                                    submittedAnswer.setSubmission(((QuizSubmission) submission));
                                }
                                quizSubmissionRepository.save((QuizSubmission) submission);
                            }
                            else if (exercise instanceof TextExercise) {
                                textSubmissionRepository.save((TextSubmission) submission);
                            }
                            else if (exercise instanceof ModelingExercise) {
                                modelingSubmissionRepository.save((ModelingSubmission) submission);
                            }
                        }
                    }
                }
            }
        }

        // if everything worked -> set studentExam to submitted
        studentExam.setSubmitted(true);
        studentExamRepository.save(studentExam);

        return ResponseEntity.ok().build();
    }

    /**
     * Get one student exam by id with exercises.
     *
     * @param studentExamId the id of the student exam
     * @return the student exam with exercises
     */
    @NotNull
    public StudentExam findOneWithExercises(Long studentExamId) {
        log.debug("Request to get student exam {} with exercises", studentExamId);
        return studentExamRepository.findWithExercisesById(studentExamId)
                .orElseThrow(() -> new EntityNotFoundException("Student exam with id \"" + studentExamId + "\" does not exist"));
    }

    /**
     * Get all student exams for the given exam.
     *
     * @param examId the id of the exam
     * @return the list of all student exams
     */
    public List<StudentExam> findAllByExamId(Long examId) {
        log.debug("Request to get all student exams for Exam : {}", examId);
        return studentExamRepository.findByExamId(examId);
    }

    /**
     * Delete a student exam by the Id
     *
     * @param studentExamId the id of the student exam to be deleted
     */
    public void deleteStudentExam(Long studentExamId) {
        log.debug("Request to delete the student exam with Id : {}", studentExamId);
        studentExamRepository.deleteById(studentExamId);
    }

    /**
     * Get one student exam by exercise and user
     *
     * @param exerciseId the id of an exam exercise
     * @param userId the id of the student taking the exam
     * @return the student exam without associated entities
     */
    @NotNull
    public StudentExam findOneByExerciseIdAndUserId(Long exerciseId, Long userId) {
        log.debug("Request to get student exam with exercise {} for user {}", exerciseId, userId);
        return studentExamRepository.findByExerciseIdAndUserId(exerciseId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Student exam for exercise " + exerciseId + " and user " + userId + " does not exist"));
    }

    /**
     * Get the maximal working time of all student exams for the exam with the given id.
     *
     * @param examId the id of the exam
     * @return the maximum of all student exam working times for the given exam
     * @throws EntityNotFoundException if no student exams could be found
     */
    @NotNull
    public Integer findMaxWorkingTimeByExamId(Long examId) {
        log.debug("Request to get the maximum working time of all student exams for Exam : {}", examId);
        return studentExamRepository.findMaxWorkingTimeByExamId(examId).orElseThrow(() -> new EntityNotFoundException("No student exams found for exam id " + examId));
    }

    /**
     * Get all distinct student working times of one exam.
     *
     * @param examId the id of the exam
     * @return a set of all distinct working time values among the student exams of an exam. May be empty if no student exams can be found.
     */
    @NotNull
    public Set<Integer> findAllDistinctWorkingTimesByExamId(Long examId) {
        log.debug("Request to find all distinct working times for Exam : {}", examId);
        return studentExamRepository.findAllDistinctWorkingTimesByExamId(examId);
    }
}
