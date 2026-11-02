package de.tum.cit.aet.artemis.programming.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;

class ProgrammingParticipationUriTest {

    @ParameterizedTest
    @MethodSource("participations")
    void storesTheSameCanonicalUriForEveryProgrammingParticipation(ProgrammingExerciseParticipation participation) {
        var uri = new LocalVCRepositoryUri(URI.create("http://localhost:8080"), "TSTEX1", "tstex1-solution");
        participation.setRepositoryUri(uri);
        assertThat(participation.getRepositoryUri()).isEqualTo(uri.getURI().toString());
    }

    private static Stream<ProgrammingExerciseParticipation> participations() {
        return Stream.of(new ProgrammingExerciseStudentParticipation(), new SolutionProgrammingExerciseParticipation(), new TemplateProgrammingExerciseParticipation());
    }
}
