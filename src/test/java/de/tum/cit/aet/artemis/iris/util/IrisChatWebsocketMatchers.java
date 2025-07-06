package de.tum.cit.aet.artemis.iris.util;

import java.util.Arrays;
import java.util.List;

import org.mockito.ArgumentMatcher;

import de.tum.cit.aet.artemis.iris.dto.IrisChatWebsocketDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageState;

public class IrisChatWebsocketMatchers {

    public static ArgumentMatcher<Object> statusDTO(PyrisStageState... stageStates) {
        return new ArgumentMatcher<>() {

            @Override
            public boolean matches(Object argument) {
                if (!(argument instanceof IrisChatWebsocketDTO websocketDTO)) {
                    return false;
                }
                if (websocketDTO.type() != IrisChatWebsocketDTO.IrisWebsocketMessageType.STATUS) {
                    return false;
                }
                if (websocketDTO.stages() == null) {
                    return stageStates == null;
                }
                if (websocketDTO.stages().size() != stageStates.length) {
                    return false;
                }
                return websocketDTO.stages().stream().map(PyrisStageDTO::state).toList().equals(List.of(stageStates));
            }

            @Override
            public String toString() {
                return "IrisChatWebsocketService.IrisWebsocketDTO with type STATUS and stage states " + Arrays.toString(stageStates);
            }
        };
    }

}
