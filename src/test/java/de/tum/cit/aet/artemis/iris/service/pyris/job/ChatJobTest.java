package de.tum.cit.aet.artemis.iris.service.pyris.job;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

class ChatJobTest {

    @Test
    void shouldDefaultToChatPipelineNameWhenUsingLegacyConstructor() {
        var job = new ChatJob("job-1", 1L, 2L, 3L, 4L, 5L, 6L);

        assertThat(job.pipelineName()).isEqualTo(ChatJob.CHAT_PIPELINE_NAME);
        assertThat(job.isAskUserPipeline()).isFalse();
    }

    @Test
    void shouldDefaultToChatPipelineNameWhenPipelineNameIsNullInCanonicalConstructor() {
        var job = new ChatJob("job-1", 1L, 2L, 3L, 4L, 5L, 6L, null);

        assertThat(job.pipelineName()).isEqualTo(ChatJob.CHAT_PIPELINE_NAME);
        assertThat(job.isAskUserPipeline()).isFalse();
    }

    @Test
    void shouldStoreExplicitPipelineNameWhenProvidedInCanonicalConstructor() {
        var job = new ChatJob("job-1", 1L, 2L, 3L, 4L, 5L, 6L, ChatJob.ASK_USER_PIPELINE_NAME);

        assertThat(job.pipelineName()).isEqualTo(ChatJob.ASK_USER_PIPELINE_NAME);
    }

    @Test
    void shouldReturnTrueForIsAskUserPipelineWhenPipelineNameIsAskUser() {
        var job = new ChatJob("job-1", 1L, 2L, 3L, 4L, 5L, 6L, ChatJob.ASK_USER_PIPELINE_NAME);

        assertThat(job.isAskUserPipeline()).isTrue();
    }

    @Test
    void shouldReturnFalseForIsAskUserPipelineWhenPipelineNameIsChat() {
        var job = new ChatJob("job-1", 1L, 2L, 3L, 4L, 5L, 6L, ChatJob.CHAT_PIPELINE_NAME);

        assertThat(job.isAskUserPipeline()).isFalse();
    }

    @Test
    void shouldPreservePipelineNameWhenWithUserMessageId() {
        var job = new ChatJob("job-1", 1L, 2L, 3L, 4L, 5L, 6L, ChatJob.ASK_USER_PIPELINE_NAME);

        var updated = job.withUserMessageId(42L);

        assertThat(updated.userMessageId()).isEqualTo(42L);
        assertThat(updated.pipelineName()).isEqualTo(ChatJob.ASK_USER_PIPELINE_NAME);
        assertThat(updated.isAskUserPipeline()).isTrue();
    }

    @Test
    void shouldPreservePipelineNameWhenWithAssistantMessageId() {
        var job = new ChatJob("job-1", 1L, 2L, 3L, 4L, 5L, 6L, ChatJob.ASK_USER_PIPELINE_NAME);

        var updated = job.withAssistantMessageId(42L);

        assertThat(updated.assistantMessageId()).isEqualTo(42L);
        assertThat(updated.pipelineName()).isEqualTo(ChatJob.ASK_USER_PIPELINE_NAME);
    }

    @Test
    void shouldPreservePipelineNameWhenWithTraceId() {
        var job = new ChatJob("job-1", 1L, 2L, 3L, 4L, 5L, 6L, ChatJob.ASK_USER_PIPELINE_NAME);

        var updated = job.withTraceId(99L);

        assertThat(updated.traceId()).isEqualTo(99L);
        assertThat(updated.pipelineName()).isEqualTo(ChatJob.ASK_USER_PIPELINE_NAME);
    }

    @Test
    void shouldReturnTrueForCanAccessWhenCourseIdMatches() {
        var job = new ChatJob("job-1", 1L, 2L, 3L, null, null, null);
        var course = new Course();
        course.setId(1L);

        assertThat(job.canAccess(course)).isTrue();
    }

    @Test
    void shouldReturnFalseForCanAccessWhenCourseIdDoesNotMatch() {
        var job = new ChatJob("job-1", 1L, 2L, 3L, null, null, null);
        var course = new Course();
        course.setId(2L);

        assertThat(job.canAccess(course)).isFalse();
    }

    @Test
    void shouldReturnTrueForCanAccessWhenExerciseIdMatchesEntityId() {
        var job = new ChatJob("job-1", 1L, 2L, 3L, null, null, null);
        Exercise exercise = new TextExercise();
        exercise.setId(3L);

        assertThat(job.canAccess(exercise)).isTrue();
    }

    @Test
    void shouldReturnFalseForCanAccessWhenExerciseIdDoesNotMatchEntityId() {
        var job = new ChatJob("job-1", 1L, 2L, 3L, null, null, null);
        Exercise exercise = new TextExercise();
        exercise.setId(4L);

        assertThat(job.canAccess(exercise)).isFalse();
    }
}
