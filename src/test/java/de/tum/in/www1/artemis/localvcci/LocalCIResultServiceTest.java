package de.tum.in.www1.artemis.localvcci;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.exception.LocalCIException;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseFactory;
import de.tum.in.www1.artemis.service.connectors.bamboo.dto.BambooBuildResultNotificationDTO;
import de.tum.in.www1.artemis.service.connectors.localci.LocalCIResultService;

class LocalCIResultServiceTest extends AbstractLocalCILocalVCIntegrationTest {

    @Autowired
    private LocalCIResultService localCIResultService;

    @Test
    void testThrowsExceptionWhenResultIsNotLocalCIBuildResult() {
        BambooBuildResultNotificationDTO wrongBuildResult = ProgrammingExerciseFactory.generateBambooBuildResult("some-repository", "SOME-PLAN", "",
                ZonedDateTime.now().minusSeconds(10), List.of(), List.of(), List.of());
        assertThatExceptionOfType(LocalCIException.class).isThrownBy(() -> localCIResultService.convertBuildResult(wrongBuildResult))
                .withMessage("The request body is not of type LocalCIBuildResult");
    }
}
