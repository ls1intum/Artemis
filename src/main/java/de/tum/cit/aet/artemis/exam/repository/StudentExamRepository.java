package de.tum.cit.aet.artemis.exam.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.security.SecureRandom;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;

/**
 * Spring Data JPA repository for the StudentExam entity.
 */
@Conditional(ExamEnabled.class)
@Lazy
@Repository
public interface StudentExamRepository extends ArtemisJpaRepository<StudentExam, Long> {

    @EntityGraph(type = LOAD, attributePaths = { "exercises" })
    Optional<StudentExam> findWithExercisesById(Long studentExamId);

    @EntityGraph(type = LOAD, attributePaths = { "exercises", "studentParticipations" })
    Optional<StudentExam> findWithExercisesAndStudentParticipationsById(Long studentExamId);

    @Query("""
            SELECT se
            FROM StudentExam se
                LEFT JOIN FETCH se.exercises e
                LEFT JOIN FETCH e.submissionPolicy
                LEFT JOIN FETCH se.examSessions
                LEFT JOIN FETCH se.studentParticipations
            WHERE se.id = :studentExamId
            """)
    Optional<StudentExam> findWithExercisesSubmissionPolicySessionsAndStudentParticipationsById(@Param("studentExamId") long studentExamId);

    @Query("""
            SELECT DISTINCT se
            FROM StudentExam se
                LEFT JOIN FETCH se.exercises e
            WHERE se.testRun = :isTestRun
                AND se.exam.id = :examId
                AND se.user.id = :userId
            """)
    Optional<StudentExam> findWithExercisesByUserIdAndExamId(@Param("userId") long userId, @Param("examId") long examId, @Param("isTestRun") boolean isTestRun);

    @Query("""
            SELECT DISTINCT se
            FROM StudentExam se
                LEFT JOIN FETCH se.exercises e
                LEFT JOIN FETCH e.studentParticipations sp
                LEFT JOIN FETCH sp.submissions s
            WHERE se.id = :studentExamId
            	AND se.testRun = :isTestRun
            """)
    Optional<StudentExam> findWithExercisesParticipationsSubmissionsById(@Param("studentExamId") long studentExamId, @Param("isTestRun") boolean isTestRun);

    @Query("""
            SELECT se
            FROM StudentExam se
            WHERE se.exam.id = :examId
                AND se.testRun = FALSE
            """)
    Set<StudentExam> findByExamId(@Param("examId") long examId);

    @Query("""
            SELECT COUNT(DISTINCT se)
            FROM StudentExam se
            WHERE se.exam.id = :examId
                AND se.testRun = FALSE
            """)
    long countByExamId(@Param("examId") long examId);

    @Query("""
            SELECT se
            FROM StudentExam se
                LEFT JOIN FETCH se.examSessions
            WHERE se.exam.id = :examId
            	AND se.testRun = FALSE
            """)
    Set<StudentExam> findByExamIdWithSessions(@Param("examId") long examId);

    @Query("""
            SELECT se
            FROM StudentExam se
                LEFT JOIN FETCH se.exercises
            WHERE se.exam.id = :examId
            """)
    Set<StudentExam> findAllWithExercisesByExamId(@Param("examId") long examId);

    @Query("""
            SELECT se
            FROM StudentExam se
                LEFT JOIN FETCH se.exercises e
            WHERE se.exam.id = :examId
            	AND se.testRun = FALSE
            """)
    Set<StudentExam> findAllWithoutTestRunsWithExercisesByExamId(@Param("examId") long examId);

    @Query("""
            SELECT se
            FROM StudentExam se
            WHERE se.exam.id = :examId
            	AND se.testRun = TRUE
            """)
    List<StudentExam> findAllTestRunsByExamId(@Param("examId") Long examId);

    @Query("""
            SELECT COUNT(se)
            FROM StudentExam se
            WHERE se.exam.id = :examId
            	AND se.testRun = TRUE
            """)
    long countTestRunsByExamId(@Param("examId") Long examId);

    @Query("""
            SELECT COUNT(se)
            FROM StudentExam se
            WHERE se.exam.id = :examId
            	AND (se.started = FALSE OR se.started IS NULL)
            	AND se.testRun = FALSE
            """)
    long countStudentExamsNotStartedByExamIdIgnoreTestRuns(@Param("examId") long examId);

    @Query("""
            SELECT COUNT(se)
            FROM StudentExam se
            WHERE se.exam.id = :examId
            	AND se.started = TRUE
            	AND se.testRun = FALSE
            """)
    long countStudentExamsStartedByExamIdIgnoreTestRuns(@Param("examId") long examId);

    @Query("""
            SELECT COUNT(se)
            FROM StudentExam se
            WHERE se.exam.id = :examId
            	AND se.submitted = TRUE
            	AND se.testRun = FALSE
            """)
    long countStudentExamsSubmittedByExamIdIgnoreTestRuns(@Param("examId") long examId);

    /**
     * It might happen that multiple test exams exist for a combination of userId/examId, that's why we return a set here.
     *
     * @param userId the id of the user
     * @return all student exams for the given user
     */
    @Query("""
            SELECT DISTINCT se
            FROM StudentExam se
                LEFT JOIN FETCH se.exam exam
                LEFT JOIN FETCH se.exercises e
                LEFT JOIN FETCH e.submissionPolicy spo
                LEFT JOIN FETCH e.studentParticipations sp
                LEFT JOIN FETCH sp.submissions s
                LEFT JOIN FETCH s.results r
                LEFT JOIN FETCH r.feedbacks f
                LEFT JOIN FETCH f.testCase
            WHERE se.user.id = sp.student.id
                  AND se.user.id = :userId
            """)
    Set<StudentExam> findAllWithExercisesSubmissionPolicyParticipationsSubmissionsResultsAndFeedbacksByUserId(@Param("userId") long userId);

    @Query("""
            SELECT DISTINCT se
            FROM StudentExam se
                LEFT JOIN FETCH se.exercises e
            WHERE se.exam.id = :examId
            	AND se.testRun = TRUE
            	AND se.user.id = :userId
            """)
    List<StudentExam> findAllTestRunsWithExercisesByExamIdForUser(@Param("examId") Long examId, @Param("userId") Long userId);

    @Query("""
            SELECT DISTINCT se
            FROM StudentExam se
            WHERE se.testRun = FALSE
            	AND se.exam.id = :examId
            	AND se.user.id = :userId
            """)
    Optional<StudentExam> findByExamIdAndUserId(@Param("examId") long examId, @Param("userId") long userId);

    Optional<StudentExam> findFirstByExamIdAndUserIdOrderByCreatedDateDesc(long examId, long userId);

    @Query("""
            SELECT se
            FROM StudentExam se
            JOIN se.studentParticipations p
            WHERE se.exam.id = :examId
                AND p.id = :participationId
            """)
    Optional<StudentExam> findByExamIdAndParticipationId(@Param("examId") long examId, @Param("participationId") long participationId);

    /**
     * Return the StudentExam for the given examId and userId, if possible. For test exams, the latest Student Exam is returned.
     *
     * @param examId id of the exam
     * @param userId id of the user
     * @return the student exam
     * @throws EntityNotFoundException if no student exams could be found
     */
    default StudentExam findOneByExamIdAndUserIdElseThrow(long examId, long userId) {
        return getValueElseThrow(this.findFirstByExamIdAndUserIdOrderByCreatedDateDesc(examId, userId));
    }

    /**
     * Retrieves the submission status of a student exam.
     * <p>
     * This query fetches the {@code submitted} status of a {@link StudentExam} for a given student
     * and exam. The result is wrapped in an {@link Optional} to handle cases where no matching
     * record exists.
     * </p>
     * <p>
     * <strong>Note:</strong> This method should not be used for test exams, as multiple
     * {@link StudentExam} entries may exist for the same student and exam.
     * </p>
     *
     * @param examId The ID of the exam.
     * @param userId The ID of the user (student).
     * @return An {@link Optional} containing {@code true} if the student has submitted the exam,
     *         {@code false} if not, or an empty {@code Optional} if no record is found.
     */
    @Query("""
            SELECT se.submitted
            FROM StudentExam se
            WHERE se.exam.id = :examId
            	AND se.user.id = :userId
            """)
    Optional<Boolean> isSubmitted(@Param("examId") long examId, @Param("userId") long userId);

    /**
     * Retrieves the submission status of a student exam.
     * <p>
     * This query fetches the {@code submitted} status of a {@link StudentExam} for a given participation.
     * The result is wrapped in an {@link Optional} to handle cases where no matching
     * record exists.
     * </p>
     *
     * @param participationId The ID of the participation
     * @return An {@link Optional} containing {@code true} if the exam containing the participation was submitted,
     *         {@code false} if not, or an empty {@code Optional} if no record is found.
     */
    @Query("""
            SELECT se.submitted
            FROM StudentExam se
                JOIN se.studentParticipations p
            WHERE p.id = :participationId
            """)
    Optional<Boolean> isSubmitted(@Param("participationId") long participationId);

    /**
     * Checks if any StudentExam exists for the given user (student) id in the given course.
     *
     * @param courseId the id of the course which should have the exam.
     * @param examId   the id of the exam
     * @param userId   the id of the user (student) who may or may not have a StudentExam
     * @return True if the given user id has a matching StudentExam in the given exam and course, else false.
     */
    boolean existsByExam_CourseIdAndExamIdAndUserId(long courseId, long examId, long userId);

    @Query("""
            SELECT DISTINCT se
            FROM StudentExam se
                LEFT JOIN FETCH se.exercises e
            WHERE se.testRun = FALSE
            	AND e.id = :exerciseId
            	AND se.user.id = :userId
            """)
    Optional<StudentExam> findByExerciseIdAndUserId(@Param("exerciseId") Long exerciseId, @Param("userId") Long userId);

    @Query("""
            SELECT MAX(se.workingTime)
            FROM StudentExam se
            WHERE se.testRun = FALSE
            	AND se.exam.id = :examId
            """)
    Optional<Integer> findMaxWorkingTimeByExamId(@Param("examId") Long examId);

    @Query("""
            SELECT DISTINCT se.workingTime
            FROM StudentExam se
            WHERE se.testRun = FALSE
            	AND se.exam.id = :examId
            """)
    Set<Integer> findAllDistinctWorkingTimesByExamId(@Param("examId") Long examId);

    @Query("""
            SELECT DISTINCT u
            FROM StudentExam se
                LEFT JOIN se.user u
            WHERE se.testRun = FALSE
            	AND se.exam.id = :examId
            """)
    Set<User> findUsersWithStudentExamsForExam(@Param("examId") Long examId);

    @Query("""
            SELECT DISTINCT se
            FROM StudentExam se
                LEFT JOIN FETCH se.exercises
            WHERE se.exam.id = :examId
            	AND se.submitted = FALSE
            	AND se.testRun = FALSE
            """)
    Set<StudentExam> findAllUnsubmittedWithExercisesByExamId(@Param("examId") Long examId);

    List<StudentExam> findAllByExamId_AndTestRunIsTrue(Long examId);

    @Query("""
            SELECT DISTINCT se
            FROM StudentExam se
            WHERE se.user.id = :userId
                AND se.exam.course.id = :courseId
                AND se.exam.testExam = TRUE
                AND se.testRun = FALSE
            """)
    List<StudentExam> findStudentExamsForTestExamsByUserIdAndCourseId(@Param("userId") Long userId, @Param("courseId") Long courseId);

    @Query("""
            SELECT DISTINCT se
            FROM StudentExam se
            WHERE se.user.id = :userId
                AND se.exam.id = :examId
                AND se.exam.testExam = TRUE
                AND se.testRun = FALSE
            """)
    List<StudentExam> findStudentExamsForTestExamsByUserIdAndExamId(@Param("userId") Long userId, @Param("examId") Long examId);

    @Query("""
            SELECT DISTINCT se
            FROM StudentExam se
                LEFT JOIN FETCH se.exercises exercises
            WHERE se.exam.id = :examId
                AND se.user.id = :userId
                AND se.submitted = FALSE
                AND se.testRun = FALSE
                AND se.exam.testExam = TRUE
            """)
    List<StudentExam> findUnsubmittedStudentExamsForTestExamsWithExercisesByExamIdAndUserId(@Param("examId") Long examId, @Param("userId") Long userId);

    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE StudentExam s
            SET s.submitted = TRUE,
                s.submissionDate = :submissionDate
            WHERE s.id = :studentExamId
            """)
    void submitStudentExam(@Param("studentExamId") Long studentExamId, @Param("submissionDate") ZonedDateTime submissionDate);

    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE StudentExam s
            SET s.started = TRUE,
                s.startedDate = :startedDate
            WHERE s.id = :studentExamId
            """)
    void startStudentExam(@Param("studentExamId") Long studentExamId, @Param("startedDate") ZonedDateTime startedDate);

    /**
     * Return the StudentExam of the participation's user, if possible
     *
     * @param exercise      that is possibly part of an exam
     * @param participation the participation of the student
     * @return an optional StudentExam, which is empty if the exercise is not part of an exam or the student exam hasn't been created
     */
    default Optional<StudentExam> findStudentExam(Exercise exercise, StudentParticipation participation) {
        if (exercise.isExamExercise()) {
            var examUser = getArbitraryValueElseThrow(participation.getStudent());
            return findByExerciseIdAndUserId(exercise.getId(), examUser.getId());
        }
        return Optional.empty();
    }

    /**
     * Get one student exam by id with exercises.
     *
     * @param studentExamId the id of the student exam
     * @return the student exam with exercises
     */
    @NotNull
    default StudentExam findByIdWithExercisesElseThrow(Long studentExamId) {
        return getValueElseThrow(findWithExercisesById(studentExamId), studentExamId);
    }

    @NotNull
    default StudentExam findByIdWithExercisesAndStudentParticipationsElseThrow(Long studentExamId) {
        return getValueElseThrow(findWithExercisesAndStudentParticipationsById(studentExamId));
    }

    /**
     * Get one student exam by id with exercises, sessions and student participations
     *
     * @param studentExamId the id of the student exam
     * @return the student exam with exercises, sessions and student participations
     */
    @NotNull
    default StudentExam findByIdWithExercisesAndSessionsAndStudentParticipationsElseThrow(Long studentExamId) {
        return getValueElseThrow(findWithExercisesSubmissionPolicySessionsAndStudentParticipationsById(studentExamId), studentExamId);
    }

    /**
     * Generates random exams for each user in the given users set and saves them.
     *
     * @param exam  exam for which the individual student exams will be generated
     * @param users users for which the individual exams will be generated
     * @return List of StudentExams generated for the given users
     */
    default List<StudentExam> createRandomStudentExams(Exam exam, Set<User> users) {
        List<StudentExam> studentExams = new ArrayList<>();
        SecureRandom random = new SecureRandom();

        // In case the total number of exercises in the exam is not set by the instructor
        if (exam.getNumberOfExercisesInExam() == null) {
            throw new EntityNotFoundException("The number of exercises in the exam " + exam.getId() + " does not exist");
        }
        long numberOfOptionalExercises = exam.getNumberOfExercisesInExam() - exam.getExerciseGroups().stream().filter(ExerciseGroup::getIsMandatory).count();

        // Determine the default working time by computing the duration between start and end date of the exam
        int defaultWorkingTime = exam.getWorkingTime();

        // Prepare indices of mandatory and optional exercise groups to preserve order of exercise groups
        List<Integer> indicesOfMandatoryExerciseGroups = new ArrayList<>();
        List<Integer> indicesOfOptionalExerciseGroups = new ArrayList<>();
        for (int i = 0; i < exam.getExerciseGroups().size(); i++) {
            if (Boolean.TRUE.equals(exam.getExerciseGroups().get(i).getIsMandatory())) {
                indicesOfMandatoryExerciseGroups.add(i);
            }
            else {
                indicesOfOptionalExerciseGroups.add(i);
            }
        }

        for (User user : users) {
            // Create one student exam per user
            StudentExam studentExam = new StudentExam();

            studentExam.setWorkingTime(defaultWorkingTime);
            studentExam.setExam(exam);
            studentExam.setUser(user);
            studentExam.setSubmitted(false);
            studentExam.setTestRun(false);

            // Add a random exercise for each exercise group if the index of the exercise group is in assembledIndices
            List<Integer> assembledIndices = assembleIndicesListWithRandomSelection(indicesOfMandatoryExerciseGroups, indicesOfOptionalExerciseGroups, numberOfOptionalExercises);
            for (Integer index : assembledIndices) {
                // We get one random exercise from all preselected exercise groups
                studentExam.addExercise(selectRandomExercise(random, exam.getExerciseGroups().get(index)));
            }

            // Apply random exercise order
            if (Boolean.TRUE.equals(exam.getRandomizeExerciseOrder())) {
                Collections.shuffle(studentExam.getExercises());
            }

            studentExams.add(studentExam);
        }
        studentExams = saveAll(studentExams);
        return studentExams;
    }

    private List<Integer> assembleIndicesListWithRandomSelection(List<Integer> mandatoryIndices, List<Integer> optionalIndices, Long numberOfOptionalExercises) {
        // Add all mandatory indices
        List<Integer> indices = new ArrayList<>(mandatoryIndices);

        // Add as many optional indices as numberOfOptionalExercises
        if (numberOfOptionalExercises > 0) {
            Collections.shuffle(optionalIndices);
            indices.addAll(optionalIndices.stream().limit(numberOfOptionalExercises).toList());
        }

        // Sort the indices to preserve the original order
        Collections.sort(indices);
        return indices;
    }

    private Exercise selectRandomExercise(SecureRandom random, ExerciseGroup exerciseGroup) {
        List<Exercise> exercises = new ArrayList<>(exerciseGroup.getExercises());
        int randomIndex = random.nextInt(exercises.size());
        return exercises.get(randomIndex);
    }

    /**
     * Gets the longest working time of the exam with the given id
     *
     * @param examId the id of the exam
     * @return number longest working time of the exam
     */
    @Query("""
                SELECT MAX(se.workingTime)
                FROM StudentExam se
                JOIN se.exam e
                WHERE e.id = :examId
            """)
    Integer findLongestWorkingTimeForExam(@Param("examId") Long examId);
}
