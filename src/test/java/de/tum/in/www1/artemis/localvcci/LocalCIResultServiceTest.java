package de.tum.in.www1.artemis.localvcci;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.exception.LocalCIException;
import de.tum.in.www1.artemis.service.connectors.bamboo.dto.BambooBuildResultNotificationDTO;
import de.tum.in.www1.artemis.service.connectors.localci.LocalCIResultService;
import de.tum.in.www1.artemis.util.ModelFactory;

class LocalCIResultServiceTest extends AbstractLocalCILocalVCIntegrationTest {

    @Autowired
    private LocalCIResultService localCIResultService;

    @Test
    void testThrowsExceptionWhenResultIsNotLocalCIBuildResult() {
        BambooBuildResultNotificationDTO wrongBuildResult = ModelFactory.generateBambooBuildResult("some-repository", "SOME-PLAN", "", ZonedDateTime.now().minusSeconds(10),
                List.of(), List.of(), List.of());
        LocalCIException exception = assertThrows(LocalCIException.class, () -> localCIResultService.convertBuildResult(wrongBuildResult));
        assertThat(exception.getMessage()).isEqualTo("The request body is not of type LocalCIBuildResult");
    }
}
