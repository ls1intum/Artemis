package de.tum.cit.aet.artemis.assessment.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.ResultListener;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.service.messaging.InstanceMessageSendService;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.text.domain.TextBlock;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

class AthenaFeedbackRequestCompletionOrderTest {

    @Test
    void childPersistenceFailureNeverPublishesATerminalScore() {
        var fixture = new CompletionFixture(true);
        assertThatThrownBy(() -> fixture.complete()).isInstanceOf(IllegalStateException.class).hasMessage("block constraint failed");
        verifyNoInteractions(fixture.messages);
    }

    @Test
    void aSuccessfulCompletionPublishesOnlyAfterFeedbackAndBlocksAreFlushed() {
        var fixture = new CompletionFixture(false);
        assertThat(fixture.complete()).isTrue();
        assertThat(fixture.events).containsExactly("feedback-flushed", "blocks-flushed", "score-published");
    }

    private static class CompletionFixture {

        private final InstanceMessageSendService messages = mock(InstanceMessageSendService.class);

        private final List<String> events = new ArrayList<>();

        private final AthenaFeedbackRequestWriteRepositoryImpl writes;

        private final Result request;

        private final Feedback feedback = new Feedback();

        @SuppressWarnings("unchecked")
        private CompletionFixture(boolean failBlocks) {
            var user = new User();
            user.setId(3L);
            var exercise = new TextExercise();
            exercise.setId(7L);
            var participation = new StudentParticipation();
            participation.setId(10L);
            participation.setExercise(exercise);
            participation.setParticipant(user);
            var submission = new TextSubmission();
            submission.setId(1L);
            submission.setParticipation(participation);
            request = new Result();
            request.setId(11L);
            request.setSubmission(submission);
            request.setAssessmentType(AssessmentType.AUTOMATIC_ATHENA);
            request.setSuccessful(null);
            request.setRated(false);
            request.setScore(null);
            request.setCompletionDate(ZonedDateTime.now().plusMinutes(5));
            var results = mock(AthenaFeedbackRequestRepository.class);
            when(results.findByIdElseThrow(request.getId())).thenReturn(request);
            var listener = new ResultListener(messages);
            when(results.saveAndFlush(any())).thenAnswer(invocation -> {
                Result result = invocation.getArgument(0);
                listener.createOrUpdateResult(result);
                return result;
            });
            doAnswer(invocation -> {
                events.add("score-published");
                return null;
            }).when(messages).sendParticipantScoreSchedule(any(), any(), any());
            var feedbacks = mock(FeedbackRepository.class);
            when(feedbacks.saveAll(any())).thenAnswer(invocation -> {
                events.add("feedback-written-without-flush");
                return List.of(feedback);
            });
            when(feedbacks.saveAllAndFlush(any())).thenAnswer(invocation -> {
                events.add("feedback-flushed");
                return List.of(feedback);
            });
            var blocks = mock(TextBlockRepository.class);
            when(blocks.saveAll(any())).thenAnswer(invocation -> {
                events.add("blocks-written-without-flush");
                if (failBlocks) {
                    throw new IllegalStateException("block constraint failed");
                }
                return List.of();
            });
            when(blocks.saveAllAndFlush(any())).thenAnswer(invocation -> {
                events.add("blocks-flushed");
                if (failBlocks) {
                    throw new IllegalStateException("block constraint failed");
                }
                return List.of();
            });
            ObjectProvider<AthenaFeedbackRequestRepository> provider = mock(ObjectProvider.class);
            when(provider.getObject()).thenReturn(results);
            writes = new AthenaFeedbackRequestWriteRepositoryImpl(provider, blocks, feedbacks, 10);
        }

        private boolean complete() {
            return writes.completeAthenaFeedbackRequest(request, 20.0, List.of(feedback), Set.of(new TextBlock()));
        }
    }
}
