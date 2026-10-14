package de.tum.cit.aet.artemis.exam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.repository.ExamUserRepository;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exam.test_repository.StudentExamTestRepository;

class StudentExamServiceProtectionTest {

    private final StudentExamService service = mock(StudentExamService.class, org.mockito.Mockito.CALLS_REAL_METHODS);

    private final StudentExamAssignmentService assignment = mock();

    private final ExamTestRepository exams = mock();

    private final StudentExamTestRepository students = mock();

    private final ExamUserRepository registered = mock();

    private final UserTestRepository users = mock();

    private final Exam supplied = new Exam();

    @BeforeEach
    void setUp() {
        supplied.setId(10L);
        ReflectionTestUtils.setField(service, "assignmentService", assignment);
        ReflectionTestUtils.setField(service, "examRepository", exams);
        ReflectionTestUtils.setField(service, "studentExamRepository", students);
        ReflectionTestUtils.setField(service, "examUserRepository", registered);
        ReflectionTestUtils.setField(service, "userRepository", users);
        ReflectionTestUtils.setField(service, "cacheManager", new ConcurrentMapCacheManager(Constants.EXAM_EXERCISE_START_STATUS));
    }

    @Test
    void disabledBulkAssignmentUsesTheSuppliedGraphWithoutLockOrReload() {
        var expected = List.of(new StudentExam());
        when(registered.findUserIdsByExamId(10L)).thenReturn(Set.of(11L));
        when(students.createRandomStudentExams(supplied, Set.of(11L))).thenReturn(expected);

        assertThat(service.generateStudentExams(supplied)).isSameAs(expected);

        verify(students).createRandomStudentExams(supplied, Set.of(11L));
        verifyNoInteractions(exams);
        verify(assignment).isProtectionEnabled();
        org.mockito.Mockito.verifyNoMoreInteractions(assignment);
    }

    @Test
    void disabledMissingAssignmentKeepsItsOriginalSingleGraphRead() {
        var current = new Exam();
        current.setId(10L);
        when(exams.findWithExerciseGroupsAndExercisesByIdOrElseThrow(10L)).thenReturn(current);
        when(registered.findUserIdsByExamId(10L)).thenReturn(Set.of(11L, 12L));
        when(students.findUserIdsWithStudentExamsForExam(10L)).thenReturn(Set.of(11L));
        var expected = List.of(new StudentExam());
        when(students.createRandomStudentExams(current, Set.of(12L))).thenReturn(expected);

        assertThat(service.generateMissingStudentExams(supplied)).isSameAs(expected);

        verify(exams).findWithExerciseGroupsAndExercisesByIdOrElseThrow(10L);
        org.mockito.Mockito.verifyNoMoreInteractions(exams);
        verify(assignment).isProtectionEnabled();
        org.mockito.Mockito.verifyNoMoreInteractions(assignment);
    }

    @Test
    void disabledIndividualAssignmentKeepsItsOriginalQueryAndSelection() {
        var student = new User();
        student.setId(11L);
        var current = new Exam();
        current.setId(10L);
        var expected = new StudentExam();
        expected.setExam(supplied);
        when(exams.findWithExerciseGroupsAndExercisesByIdOrElseThrow(10L)).thenReturn(current);
        when(students.createRandomStudentExams(current, Set.of(11L))).thenReturn(List.of(expected));

        assertThat(service.generateIndividualStudentExam(supplied, student)).isSameAs(expected);
        assertThat(expected.getUser()).isSameAs(student);
        verify(exams).findWithExerciseGroupsAndExercisesByIdOrElseThrow(10L);
        org.mockito.Mockito.verifyNoMoreInteractions(exams);
        verify(assignment).isProtectionEnabled();
        org.mockito.Mockito.verifyNoMoreInteractions(assignment);
    }

    @Test
    void disabledTestRunUsesSaveWithoutSelectionLockOrFlush() {
        when(students.save(org.mockito.ArgumentMatchers.any())).thenAnswer(call -> call.getArgument(0));
        StudentExam testRun = ReflectionTestUtils.invokeMethod(service, "generateTestRun", supplied, List.of(), 60);

        verify(students).save(testRun);
        org.mockito.Mockito.verifyNoMoreInteractions(students);
        verifyNoInteractions(exams);
        verify(assignment).isProtectionEnabled();
        org.mockito.Mockito.verifyNoMoreInteractions(assignment);
    }

    @Test
    void enabledEntryPointsDelegateToTheProtectedAssignmentBoundary() {
        when(assignment.isProtectionEnabled()).thenReturn(true);
        var expected = new StudentExam();
        expected.setExam(supplied);
        var list = List.of(expected);
        when(assignment.assignRegisteredStudents(10L, false)).thenReturn(list);
        when(assignment.assignRegisteredStudents(10L, true)).thenReturn(list);
        when(assignment.assignStudent(10L, 11L)).thenReturn(expected);
        when(assignment.assignTestRun(org.mockito.ArgumentMatchers.any())).thenAnswer(call -> call.getArgument(0));
        var student = new User();
        student.setId(11L);

        assertThat(service.generateStudentExams(supplied)).isSameAs(list);
        assertThat(service.generateMissingStudentExams(supplied)).isSameAs(list);
        assertThat(service.generateIndividualStudentExam(supplied, student)).isSameAs(expected);
        StudentExam testRun = ReflectionTestUtils.invokeMethod(service, "generateTestRun", supplied, List.of(), 60);
        verify(assignment).assignTestRun(testRun);
        verifyNoInteractions(exams, students, registered);
    }
}
