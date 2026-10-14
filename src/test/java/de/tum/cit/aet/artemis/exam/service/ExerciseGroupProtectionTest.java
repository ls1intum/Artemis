package de.tum.cit.aet.artemis.exam.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.repository.ExerciseGroupRepository;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.hyperion.api.HyperionExerciseMutationApi;

class ExerciseGroupProtectionTest {

    @Test
    void disabledProtectionKeepsTheOriginalUpdateWithoutExamLock() {
        var groups = mock(ExerciseGroupRepository.class);
        var exams = mock(ExamTestRepository.class);
        var mutation = mock(HyperionExerciseMutationApi.class);
        when(groups.moveToExerciseGroupIfNoStudentExams(2L, 3L, 1L)).thenReturn(true);

        new ExerciseGroupService(groups, exams, Optional.of(mutation)).moveExerciseToGroup(1L, 2L, 3L);

        verify(groups).moveToExerciseGroupIfNoStudentExams(2L, 3L, 1L);
        verifyNoInteractions(exams);
    }

    @Test
    void absentHyperionKeepsTheOriginalUpdateWithoutExamLock() {
        var groups = mock(ExerciseGroupRepository.class);
        var exams = mock(ExamTestRepository.class);
        when(groups.moveToExerciseGroupIfNoStudentExams(2L, 3L, 1L)).thenReturn(true);

        new ExerciseGroupService(groups, exams, Optional.empty()).moveExerciseToGroup(1L, 2L, 3L);

        verify(groups).moveToExerciseGroupIfNoStudentExams(2L, 3L, 1L);
        verifyNoInteractions(exams);
    }

    @Test
    void enabledProtectionMovesOnlyInsideTheSharedExamSelectionLock() {
        var groups = mock(ExerciseGroupRepository.class);
        var exams = mock(ExamTestRepository.class);
        var mutation = mock(HyperionExerciseMutationApi.class);
        when(mutation.isCoordinationEnabled()).thenReturn(true);
        when(exams.withExerciseSelectionLock(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any())).thenAnswer(call -> {
            verifyNoInteractions(groups);
            return call.<Function<Exam, Boolean>>getArgument(1).apply(new Exam());
        });
        when(groups.moveToExerciseGroupIfNoStudentExams(2L, 3L, 1L)).thenReturn(true);

        new ExerciseGroupService(groups, exams, Optional.of(mutation)).moveExerciseToGroup(1L, 2L, 3L);

        verify(exams).withExerciseSelectionLock(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any());
        verify(groups).moveToExerciseGroupIfNoStudentExams(2L, 3L, 1L);
    }
}
