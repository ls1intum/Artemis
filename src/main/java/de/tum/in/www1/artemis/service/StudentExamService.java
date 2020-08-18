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
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.*;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
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

    private final SubmissionVersionService submissionVersionService;

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    private final ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    public StudentExamService(StudentExamRepository studentExamRepository, ParticipationService participationService, QuizSubmissionRepository quizSubmissionRepository,
            TextSubmissionRepository textSubmissionRepository, ModelingSubmissionRepository modelingSubmissionRepository, SubmissionVersionService submissionVersionService,
            ProgrammingExerciseParticipationService programmingExerciseParticipationService, ProgrammingSubmissionRepository programmingSubmissionRepository) {
        this.participationService = participationService;
        this.studentExamRepository = studentExamRepository;
        this.quizSubmissionRepository = quizSubmissionRepository;
        this.textSubmissionRepository = textSubmissionRepository;
        this.modelingSubmissionRepository = modelingSubmissionRepository;
        this.submissionVersionService = submissionVersionService;
        this.programmingExerciseParticipationService = programmingExerciseParticipationService;
        this.programmingSubmissionRepository = programmingSubmissionRepository;
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
     * @param examId the id of the exam
     * @param userId the id of the user
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
     * @param examId the id of the exam
     * @param userId the id of the user
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
     * @param existingStudentExam the existing student exam object in the database
     * @param studentExam the student exam object from the client which will be submitted (final submission)
     * @param currentUser the current user
     * @return ResponseEntity.ok() on success or HTTP error with a custom error message on failure
     */
    public ResponseEntity<StudentExam> submitStudentExam(StudentExam existingStudentExam, StudentExam studentExam, User currentUser) {
        log.debug("Submit student exam with id {}", studentExam.getId());

        // most important aspect here: set studentExam to submitted and set submission date
        submitStudentExam(studentExam);

        try {
            // in case there were last second changes, that have not been submitted yet.
            saveSubmissions(studentExam, currentUser);
        }
        catch (Exception e) {
            log.error("saveSubmissions threw an exception", e);
        }

        try {
            // lock the programming exercise repository access (important in case of early exam submissions)
            lockStudentRepositories(currentUser, existingStudentExam);
        }
        catch (Exception e) {
            log.error("lockStudentRepositories threw an exception", e);
        }

        return ResponseEntity.ok(studentExam);
    }

    private void submitStudentExam(StudentExam studentExam) {
        studentExam.setSubmitted(true);
        studentExam.setSubmissionDate(ZonedDateTime.now());
        studentExamRepository.save(studentExam);
    }

    private void saveSubmissions(StudentExam studentExam, User currentUser) {
        List<StudentParticipation> existingParticipations = participationService.findByStudentIdAndIndividualExercisesWithEagerSubmissionsResult(currentUser.getId(),
                studentExam.getExercises());

        for (Exercise exercise : studentExam.getExercises()) {
            // we do not apply the following checks for programming exercises or file upload exercises
            try {
                saveSubmission(currentUser, existingParticipations, exercise);
            }
            catch (Exception e) {
                log.error("saveSubmission threw an exception", e);
            }
        }
    }

    private void saveSubmission(User currentUser, List<StudentParticipation> existingParticipations, Exercise exercise) {
        if (exercise instanceof ProgrammingExercise) {
            // there is an edge case in which the student exam does not contain the latest programming submission (e.g. when the user was offline in between)
            // we fetch the latest programming submission from the DB here and replace it in the participation of the exercise so that the latest one will be returned below
            try {
                if (exercise.getStudentParticipations() != null && exercise.getStudentParticipations().size() == 1) {
                    var studentParticipation = exercise.getStudentParticipations().iterator().next();
                    var latestSubmission = programmingSubmissionRepository.findLatestSubmissionForParticipation(studentParticipation.getId(), PageRequest.of(0, 1)).stream()
                            .findFirst();
                    latestSubmission.ifPresent(programmingSubmission -> studentParticipation.setSubmissions(Set.of(programmingSubmission)));
                }
            }
            catch (Exception ex) {
                log.error("An error occurred when trying to find the latest submissions for programming exercise {} for user {}", exercise.getId(), currentUser.getLogin());
            }
            return;
        }
        if (exercise instanceof FileUploadExercise) {
            return;
        }

        // if exercise is either QuizExercise, TextExercise or ModelingExercise and exactly one participation exists
        if (exercise.getStudentParticipations() != null && exercise.getStudentParticipations().size() == 1) {
            for (StudentParticipation studentParticipation : exercise.getStudentParticipations()) {
                StudentParticipation existingParticipation = existingParticipations.stream().filter(p -> p.getId().equals(studentParticipation.getId()))
                        .collect(Collectors.toList()).get(0);
                // if exactly one submission exists we save the submission
                if (studentParticipation.getSubmissions() != null && studentParticipation.getSubmissions().size() == 1) {
                    // check that the current user owns the participation
                    if (!studentParticipation.isOwnedBy(currentUser) || !existingParticipation.isOwnedBy(currentUser)) {
                        throw new AccessForbiddenException("User " + currentUser.getLogin() + " is not allowed to access the participation " + existingParticipation.getId());
                    }
                    studentParticipation.setExercise(exercise);
                    for (Submission submission : studentParticipation.getSubmissions()) {

                        // check that the submission belongs to the already saved participation
                        if (!existingParticipation.getSubmissions().contains(submission)) {
                            throw new AccessForbiddenException("User " + currentUser.getLogin() + " cannot submit a different submission " + submission + " for participation "
                                    + existingParticipation.getId());
                        }
                        // check that no result has been injected
                        if (submission.getResult() != null) {
                            throw new AccessForbiddenException("User " + currentUser.getLogin() + " cannot inject a result " + submission.getResult() + " for submission "
                                    + submission + " and participation " + existingParticipation.getId());
                        }
                        submission.setParticipation(studentParticipation);
                        submission.submissionDate(ZonedDateTime.now());
                        submission.submitted(true);
                        if (exercise instanceof QuizExercise) {
                            // recreate pointers back to submission in each submitted answer
                            for (SubmittedAnswer submittedAnswer : ((QuizSubmission) submission).getSubmittedAnswers()) {
                                submittedAnswer.setSubmission(((QuizSubmission) submission));
                                if (submittedAnswer instanceof DragAndDropSubmittedAnswer) {
                                    ((DragAndDropSubmittedAnswer) submittedAnswer).getMappings()
                                            .forEach(dragAndDropMapping -> dragAndDropMapping.setSubmittedAnswer(((DragAndDropSubmittedAnswer) submittedAnswer)));
                                }
                                else if (submittedAnswer instanceof ShortAnswerSubmittedAnswer) {
                                    ((ShortAnswerSubmittedAnswer) submittedAnswer).getSubmittedTexts()
                                            .forEach(submittedText -> submittedText.setSubmittedAnswer(((ShortAnswerSubmittedAnswer) submittedAnswer)));
                                }
                            }
                            quizSubmissionRepository.save((QuizSubmission) submission);
                        }
                        else if (exercise instanceof TextExercise) {
                            textSubmissionRepository.save((TextSubmission) submission);
                        }
                        else if (exercise instanceof ModelingExercise) {
                            modelingSubmissionRepository.save((ModelingSubmission) submission);
                        }

                        // versioning of submission
                        try {
                            submissionVersionService.saveVersionForIndividual(submission, currentUser.getLogin());
                        }
                        catch (Exception ex) {
                            log.error("Submission version could not be saved: " + ex);
                        }
                    }
                }
            }
        }
    }

    private void lockStudentRepositories(User currentUser, StudentExam existingStudentExam) {
        // Only lock programming exercises when the student submitted early. Otherwise, the lock operations were already scheduled/executed.
        if (existingStudentExam.getIndividualEndDate() != null && ZonedDateTime.now().isBefore(existingStudentExam.getIndividualEndDate())) {
            // Use the programming exercises in the DB to lock the repositories (for safety)
            for (Exercise exercise : existingStudentExam.getExercises()) {
                if (exercise instanceof ProgrammingExercise) {
                    ProgrammingExercise programmingExercise = (ProgrammingExercise) exercise;
                    try {
                        log.debug("lock student repositories for {}", currentUser);
                        ProgrammingExerciseStudentParticipation participation = programmingExerciseParticipationService.findStudentParticipationByExerciseAndStudentId(exercise,
                                currentUser.getLogin());
                        programmingExerciseParticipationService.lockStudentRepository(programmingExercise, participation);
                    }
                    catch (Exception e) {
                        log.error("Locking programming exercise " + exercise.getId() + " submitted manually by " + currentUser.getLogin() + " failed", e);
                    }
                }
            }
        }
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
     * @param userId     the id of the student taking the exam
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
