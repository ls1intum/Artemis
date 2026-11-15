package de.tum.cit.aet.artemis.exercise;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.dto.StudentParticipationSubmitTargetDTO;

class StudentParticipationSubmitTargetDTOTest {

    /**
     * Every column is given a value of its own, so that two columns swapped in the mapping cannot give the expected record by accident.
     */
    @Test
    void of_copiesEveryColumnTheProjectionQueriesRead() {
        ZonedDateTime initializationDate = ZonedDateTime.parse("2026-03-01T10:15:30+01:00");
        ZonedDateTime individualDueDate = ZonedDateTime.parse("2026-04-02T18:00:00+02:00");
        StudentParticipation participation = new StudentParticipation();
        participation.setId(4711L);
        participation.setInitializationState(InitializationState.INITIALIZED);
        participation.setInitializationDate(initializationDate);
        participation.setIndividualDueDate(individualDueDate);
        participation.setPracticeMode(true);
        participation.setPresentationScore(1.0);

        var target = StudentParticipationSubmitTargetDTO.of(participation);

        assertThat(target.id()).isEqualTo(4711L);
        assertThat(target.initializationState()).isEqualTo(InitializationState.INITIALIZED);
        assertThat(target.initializationDate()).isEqualTo(initializationDate);
        assertThat(target.individualDueDate()).isEqualTo(individualDueDate);
        assertThat(target.testRun()).isTrue();
        assertThat(target.presentationScore()).isEqualTo(1.0);
    }

    @Test
    void of_aGradedParticipationWithoutExtensionIsNoTestRun() {
        StudentParticipation participation = new StudentParticipation();
        participation.setId(8L);
        participation.setInitializationState(InitializationState.UNINITIALIZED);
        participation.setPracticeMode(false);

        var target = StudentParticipationSubmitTargetDTO.of(participation);

        assertThat(target.id()).isEqualTo(8L);
        assertThat(target.initializationState()).isEqualTo(InitializationState.UNINITIALIZED);
        assertThat(target.testRun()).isFalse();
        assertThat(target.individualDueDate()).isNull();
        assertThat(target.initializationDate()).isNull();
        assertThat(target.presentationScore()).isNull();
    }
}
