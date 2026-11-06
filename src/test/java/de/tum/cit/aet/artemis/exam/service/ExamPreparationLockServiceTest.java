package de.tum.cit.aet.artemis.exam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class ExamPreparationLockServiceTest {

    private final ExamPreparationLockService service = new ExamPreparationLockService(null);

    @Test
    void runAllCleanupsContinuesAfterFailureAndRethrowsTheFirst() {
        List<String> executed = new ArrayList<>();
        List<Runnable> cleanups = List.of(() -> executed.add("first"), () -> {
            executed.add("failing");
            throw new IllegalStateException("build plan deletion failed");
        }, () -> executed.add("third"), () -> {
            executed.add("failing-too");
            throw new IllegalArgumentException("second failure");
        });

        assertThatThrownBy(() -> service.runAllCleanups(cleanups)).isInstanceOf(IllegalStateException.class).hasMessage("build plan deletion failed")
                .hasSuppressedException(new IllegalArgumentException("second failure"));
        assertThat(executed).containsExactly("first", "failing", "third", "failing-too");
    }

    @Test
    void runAllCleanupsRunsEverythingWithoutFailures() {
        List<String> executed = new ArrayList<>();
        service.runAllCleanups(List.of(() -> executed.add("a"), () -> executed.add("b")));
        assertThat(executed).containsExactly("a", "b");
    }
}
