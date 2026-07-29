package de.tum.cit.aet.artemis.iris.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.dto.IrisMessageContextDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisChatSessionRepository;
import de.tum.cit.aet.artemis.iris.service.session.IrisChatSessionService;
import de.tum.cit.aet.artemis.iris.service.session.IrisPromptUserService;
import de.tum.cit.aet.artemis.iris.service.session.IrisTutorSuggestionSessionService;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;

class IrisSessionServiceTest {

    private IrisChatSessionService irisChatSessionService;

    private IrisPromptUserService irisPromptUserService;

    private IrisSessionService irisSessionService;

    @BeforeEach
    void setUp() {
        irisChatSessionService = mock(IrisChatSessionService.class);
        irisPromptUserService = mock(IrisPromptUserService.class);
        irisSessionService = new IrisSessionService(mock(UserRepository.class), irisChatSessionService, irisPromptUserService, mock(IrisTutorSuggestionSessionService.class),
                mock(IrisChatSessionRepository.class), mock(IrisSettingsService.class));
    }

    @Test
    void requestMessageFromIrisUsesPromptUserPipelineForActivePromptingModeSession() {
        var session = new IrisChatSession();
        session.setInPromptingModePipeline(true);

        irisSessionService.requestMessageFromIris(session, Map.of("Main.java", "class Main {}"), List.of());

        verify(irisPromptUserService).requestAndHandleResponse(session);
        verify(irisChatSessionService, never()).requestAndHandleResponseWithAdditionalData(any(), any(), any());
    }

    @Test
    void requestMessageFromIrisUsesChatPipelineForRegularChatSession() {
        var session = new IrisChatSession();
        Map<String, String> uncommittedFiles = Map.of("Main.java", "class Main {}");
        List<IrisMessageContextDTO> context = List.of();

        irisSessionService.requestMessageFromIris(session, uncommittedFiles, context);

        verify(irisChatSessionService).requestAndHandleResponseWithAdditionalData(session, uncommittedFiles, context);
        verify(irisPromptUserService, never()).requestAndHandleResponse(any());
    }
}
