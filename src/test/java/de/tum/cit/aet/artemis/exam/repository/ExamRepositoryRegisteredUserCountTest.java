package de.tum.cit.aet.artemis.exam.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.exam.domain.Exam;

class ExamRepositoryRegisteredUserCountTest {

    @Test
    void anExamDeletedBetweenListingAndCountingDefaultsToZeroRegisteredUsers() {
        var repository = mock(ExamRepository.class, CALLS_REAL_METHODS);
        var deletedExam = new Exam();
        deletedExam.setId(17L);
        var remainingExam = new Exam();
        remainingExam.setId(18L);
        // The list query saw both exams; deletion before the aggregate query removes only the first count row.
        when(repository.countExamUsersByExamIds(List.of(17L, 18L))).thenReturn(List.of(new long[] { 18L, 4L }));

        repository.setNumberOfExamUsersForExams(List.of(deletedExam, remainingExam));

        assertThat(deletedExam.getNumberOfExamUsers()).isZero();
        assertThat(remainingExam.getNumberOfExamUsers()).isEqualTo(4L);
    }

    @Test
    void existingAggregateRowsKeepTheirRegisteredUserCounts() {
        var repository = mock(ExamRepository.class, CALLS_REAL_METHODS);
        var exam = new Exam();
        exam.setId(17L);
        when(repository.countExamUsersByExamIds(List.of(17L))).thenReturn(List.of(new long[] { 17L, 7L }));

        repository.setNumberOfExamUsersForExams(List.of(exam));

        assertThat(exam.getNumberOfExamUsers()).isEqualTo(7L);
    }
}
