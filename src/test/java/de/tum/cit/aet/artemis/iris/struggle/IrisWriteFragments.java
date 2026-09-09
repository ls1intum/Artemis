package de.tum.cit.aet.artemis.iris.struggle;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import org.springframework.beans.factory.ObjectProvider;

import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.message.IrisProactiveOutcome;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode;
import de.tum.cit.aet.artemis.iris.repository.IrisChatSessionRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisMessageRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisProactiveEpisodeRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisProactiveEpisodeWriteRepositoryImpl;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionWriteRepositoryImpl;

/**
 * Wires mocked Iris repositories to the REAL implementations of their custom write fragments.
 *
 * <p>
 * The multi-statement writes moved out of the services and into repository fragments, so a plain
 * {@code mock(IrisProactiveEpisodeRepository.class)} answers {@code recordOutcomeUnderLock} with null and the logic
 * under test never runs. Mockito cannot compose a mock with a partial real implementation, so each fragment method is
 * stubbed to delegate into the fragment implementation built over the very same mocks. What the unit tests then
 * exercise is the real first-terminal-wins logic, the real reveal guards and the real list compaction, against stubbed
 * queries - which is what they asserted before the move.
 *
 * <p>
 * Stubs are {@link org.mockito.Mockito#lenient() lenient} because no single test drives all of them.
 */
final class IrisWriteFragments {

    private IrisWriteFragments() {
    }

    /**
     * Attach both write fragments to the given mocks.
     *
     * @param sessions the mocked session repository
     * @param messages the mocked message repository
     * @param episodes the mocked proactive episode repository
     */
    static void attachTo(IrisSessionRepository sessions, IrisMessageRepository messages, IrisProactiveEpisodeRepository episodes) {
        // Only the context switch writes through the chat session repository, and no caller of this helper drives one.
        var sessionFragment = new IrisSessionWriteRepositoryImpl(providerOf(sessions), mock(IrisChatSessionRepository.class), messages);
        var episodeFragment = new IrisProactiveEpisodeWriteRepositoryImpl(providerOf(episodes), messages, sessions);

        lenient().when(sessions.appendMessage(anyLong(), any(), any(IrisMessageSender.class)))
                .thenAnswer(call -> sessionFragment.appendMessage(call.getArgument(0), call.getArgument(1), call.getArgument(2)));
        lenient().when(sessions.switchContextAndAppendMarker(anyLong(), any(IrisChatMode.class), anyLong(), anyLong(), any())).thenAnswer(
                call -> sessionFragment.switchContextAndAppendMarker(call.getArgument(0), call.getArgument(1), call.getArgument(2), call.getArgument(3), call.getArgument(4)));
        lenient().when(sessions.appendProactiveMessage(anyLong(), anyLong(), any(), nullable(String.class)))
                .thenAnswer(call -> sessionFragment.appendProactiveMessage(call.getArgument(0), call.getArgument(1), call.getArgument(2), call.getArgument(3)));
        lenient().doAnswer(call -> {
            sessionFragment.deleteSupersededProactiveMessageAndCompact(call.getArgument(0), call.getArgument(1));
            return null;
        }).when(sessions).deleteSupersededProactiveMessageAndCompact(anyLong(), anyLong());

        lenient().doAnswer(call -> {
            episodeFragment.registerOrTouchInNewTransaction(call.getArgument(0), call.getArgument(1), call.getArgument(2));
            return null;
        }).when(episodes).registerOrTouchInNewTransaction(anyLong(), anyLong(), any());
        lenient().when(episodes.findInNewTransaction(anyLong(), anyLong(), any()))
                .thenAnswer(call -> episodeFragment.findInNewTransaction(call.getArgument(0), call.getArgument(1), call.getArgument(2)));
        lenient().when(episodes.recordOutcomeUnderLock(any(), anyLong(), anyLong(), any(IrisProactiveOutcome.class)))
                .thenAnswer(call -> episodeFragment.recordOutcomeUnderLock(call.getArgument(0), call.getArgument(1), call.getArgument(2), call.getArgument(3)));
        lenient().when(episodes.recordAmbientOfferUnderLock(anyLong(), anyLong(), any(), any()))
                .thenAnswer(call -> episodeFragment.recordAmbientOfferUnderLock(call.getArgument(0), call.getArgument(1), call.getArgument(2), call.getArgument(3)));
        lenient().when(episodes.revealAmbient(anyLong(), anyLong(), any(), anyLong()))
                .thenAnswer(call -> episodeFragment.revealAmbient(call.getArgument(0), call.getArgument(1), call.getArgument(2), call.getArgument(3)));
        lenient().when(episodes.appendProactiveMessageWithOutcome(anyLong(), anyLong(), anyLong(), any(), nullable(String.class), nullable(IrisProactiveOutcome.class)))
                .thenAnswer(call -> episodeFragment.appendProactiveMessageWithOutcome(call.getArgument(0), call.getArgument(1), call.getArgument(2), call.getArgument(3),
                        call.getArgument(4), call.getArgument(5)));
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> providerOf(T bean) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        lenient().when(provider.getObject()).thenReturn(bean);
        return provider;
    }
}
