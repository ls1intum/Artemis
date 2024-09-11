package de.tum.cit.aet.artemis.localvcci;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.ZonedDateTime;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.core.exception.LocalCIException;
import de.tum.cit.aet.artemis.exercise.programming.ProgrammingExerciseFactory;
import de.tum.cit.aet.artemis.service.connectors.localci.LocalCIResultService;

class LocalCIResultServiceTest extends AbstractLocalCILocalVCIntegrationTest {

    @Autowired
    private LocalCIResultService localCIResultService;

    @Test
    void testThrowsExceptionWhenResultIsNotLocalCIBuildResult() {
        var wrongBuildResult = ProgrammingExerciseFactory.generateTestResultDTO("some-name", "some-repository", ZonedDateTime.now().minusSeconds(10),
                programmingExercise.getProgrammingLanguage(), false, Collections.emptyList(), Collections.emptyList(), null, null, null);
        assertThatExceptionOfType(LocalCIException.class).isThrownBy(() -> localCIResultService.convertBuildResult(wrongBuildResult))
                .withMessage("The request body is not of type LocalCIBuildResult");
    }
}
