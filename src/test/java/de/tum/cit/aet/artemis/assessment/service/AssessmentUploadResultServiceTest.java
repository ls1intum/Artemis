package de.tum.cit.aet.artemis.assessment.service;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.util.AssessmentUploadResultTestService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentBatchTest;

class AssessmentUploadResultServiceTest extends AbstractSpringIntegrationIndependentBatchTest {

    @Autowired
    private AssessmentUploadResultService assessmentUploadResultService;

    @Autowired
    private AssessmentUploadResultTestService assessmentUploadResultTestService;

    @Test
    void shouldRejectInvalidResultParameters() {
        assertThatIllegalArgumentException().isThrownBy(() -> assessmentUploadResultService.saveManualResults(null, List.of(), true, new ProgrammingExercise(), Map.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> assessmentUploadResultService.saveManualResults(List.of(), null, true, new ProgrammingExercise(), Map.of()));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> assessmentUploadResultService.saveManualResults(Collections.singletonList(null), List.of(), true, new ProgrammingExercise(), Map.of()));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> assessmentUploadResultService.saveManualResults(List.of(), Collections.singletonList(null), true, new ProgrammingExercise(), Map.of()));
        // An updated result must already be persisted, otherwise the in-place overwrite would silently create a second assessment.
        assertThatIllegalArgumentException()
                .isThrownBy(() -> assessmentUploadResultService.saveManualResults(List.of(), List.of(new Result()), true, new ProgrammingExercise(), Map.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> assessmentUploadResultTestService.deleteResultsByIds(null));
        assertThatIllegalArgumentException().isThrownBy(() -> assessmentUploadResultTestService.deleteResultsByIds(List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> assessmentUploadResultTestService.deleteManualResults(0, Set.of(1L)));
        assertThatIllegalArgumentException().isThrownBy(() -> assessmentUploadResultTestService.deleteManualResults(1, Set.of()));
    }
}
