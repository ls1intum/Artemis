package de.tum.cit.aet.artemis.assessment.service;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.assessment.repository.ParticipantScoreRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentBatchTest;

class AssessmentUploadResultServiceTest extends AbstractSpringIntegrationIndependentBatchTest {

    @Autowired
    private AssessmentUploadResultService assessmentUploadResultService;

    @Autowired
    private ParticipantScoreRepository participantScoreRepository;

    @Test
    void shouldRejectInvalidResultParameters() {
        assertThatIllegalArgumentException().isThrownBy(() -> assessmentUploadResultService.createNewManualResults(null, true));
        assertThatIllegalArgumentException().isThrownBy(() -> assessmentUploadResultService.createNewManualResults(Collections.singletonList(null), true));
        assertThatIllegalArgumentException().isThrownBy(() -> assessmentUploadResultService.deleteResultsByIds(null));
        assertThatIllegalArgumentException().isThrownBy(() -> assessmentUploadResultService.deleteResultsByIds(List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> assessmentUploadResultService.deleteManualResults(0, Set.of(1L)));
        assertThatIllegalArgumentException().isThrownBy(() -> assessmentUploadResultService.deleteManualResults(1, Set.of()));
        // Spring's repository exception translator wraps the IllegalArgumentException thrown by default repository methods.
        assertThatThrownBy(() -> participantScoreRepository.clearAllByResultIds(null)).hasRootCauseInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> participantScoreRepository.clearAllByResultIds(Set.of())).hasRootCauseInstanceOf(IllegalArgumentException.class);
    }
}
