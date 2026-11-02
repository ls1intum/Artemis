package de.tum.cit.aet.artemis.atlas.api;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.atlas.dto.atlasml.SaveCompetencyRequestDTO.OperationTypeDTO;
import de.tum.cit.aet.artemis.atlas.service.atlasml.AtlasMLService;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

class AtlasMLUpdateNotificationTest {

    private final AtlasMLService service = mock(AtlasMLService.class);

    private final AtlasMLApi api = new AtlasMLApi(service);

    @Test
    void updateNotificationIsBestEffortButDirectSaveKeepsItsFailureContract() {
        var exercise = new TextExercise();
        when(service.saveExerciseWithCompetencies(exercise, OperationTypeDTO.UPDATE)).thenThrow(new IllegalStateException("AtlasML unavailable"));
        assertThatCode(() -> api.notifyExerciseUpdated(exercise)).doesNotThrowAnyException();
        verify(service).saveExerciseWithCompetencies(exercise, OperationTypeDTO.UPDATE);
        assertThatThrownBy(() -> api.saveExerciseWithCompetencies(exercise, OperationTypeDTO.UPDATE)).isInstanceOf(IllegalStateException.class).hasMessage("AtlasML unavailable");
    }

    @Test
    void successfulAndRejectedUpdatesDoNotAffectTheCompletedDatabaseUpdate() {
        var exercise = new TextExercise();
        when(service.saveExerciseWithCompetencies(exercise, OperationTypeDTO.UPDATE)).thenReturn(true, false);
        assertThatCode(() -> api.notifyExerciseUpdated(exercise)).doesNotThrowAnyException();
        assertThatCode(() -> api.notifyExerciseUpdated(exercise)).doesNotThrowAnyException();
    }
}
