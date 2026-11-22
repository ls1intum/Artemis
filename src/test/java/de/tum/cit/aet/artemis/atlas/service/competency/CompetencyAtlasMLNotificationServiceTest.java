package de.tum.cit.aet.artemis.atlas.service.competency;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.atlas.api.AtlasMLApi;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.SaveCompetencyRequestDTO.OperationTypeDTO;

class CompetencyAtlasMLNotificationServiceTest {

    private final AtlasMLApi api = mock(AtlasMLApi.class);

    private final CompetencyAtlasMLNotificationService service = new CompetencyAtlasMLNotificationService(Optional.of(api));

    @Test
    void skipsWhenEmpty() {
        service.notifyAtlasML(List.of(), OperationTypeDTO.UPDATE, "op");
        verify(api, never()).saveCompetencies(any(), any());
    }

    @Test
    void skipsWhenNull() {
        service.notifyAtlasML(null, OperationTypeDTO.UPDATE, "op");
        verify(api, never()).saveCompetencies(any(), any());
    }

    @Test
    void delegatesToApi() {
        List<Competency> list = List.of(new Competency());
        when(api.saveCompetencies(list, OperationTypeDTO.UPDATE)).thenReturn(true);
        service.notifyAtlasML(list, OperationTypeDTO.UPDATE, "op");
        verify(api).saveCompetencies(list, OperationTypeDTO.UPDATE);
    }

    @Test
    void swallowsApiFailure() {
        List<Competency> list = List.of(new Competency());
        when(api.saveCompetencies(any(), eq(OperationTypeDTO.DELETE))).thenReturn(false);
        service.notifyAtlasML(list, OperationTypeDTO.DELETE, "op");
        verify(api).saveCompetencies(list, OperationTypeDTO.DELETE);
    }

    @Test
    void swallowsException() {
        List<Competency> list = List.of(new Competency());
        when(api.saveCompetencies(any(), any())).thenThrow(new RuntimeException("boom"));
        service.notifyAtlasML(list, OperationTypeDTO.UPDATE, "op");
        verify(api).saveCompetencies(list, OperationTypeDTO.UPDATE);
    }

    @Test
    void noOpWhenApiAbsent() {
        new CompetencyAtlasMLNotificationService(Optional.empty()).notifyAtlasML(List.of(new Competency()), OperationTypeDTO.UPDATE, "op");
    }
}
