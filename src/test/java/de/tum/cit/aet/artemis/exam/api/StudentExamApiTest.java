package de.tum.cit.aet.artemis.exam.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import de.tum.cit.aet.artemis.exam.repository.StudentExamRepository;

class StudentExamApiTest {

    @Test
    void readsStudentExamStateWithoutParticipationPreparationInfrastructure() {
        var repository = mock(StudentExamRepository.class);
        when(repository.isSubmitted(42L, 7L)).thenReturn(Optional.of(false));

        new ApplicationContextRunner().withPropertyValues("artemis.exam.enabled=true").withBean(StudentExamRepository.class, () -> repository).withBean(StudentExamApi.class)
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(StudentExamApi.class);
                    assertThat(context.getBean(StudentExamApi.class).isSubmitted(42L, 7L)).contains(false);
                });
    }
}
