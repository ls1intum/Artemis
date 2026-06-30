package de.tum.cit.aet.artemis.iris.struggle;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.iris.service.pyris.job.StruggleInterventionJob;

class StruggleInterventionJobTest {

    @Test
    void carriesContextAndAuthorizesOwningCourse() {
        var job = new StruggleInterventionJob("token-1", 7L, 42L, 3L, null, null, null, null);
        assertThat(job.jobId()).isEqualTo("token-1");
        assertThat(job.exerciseId()).isEqualTo(42L);
        assertThat(job.userId()).isEqualTo(3L);

        var owning = new Course();
        owning.setId(7L);
        assertThat(job.canAccess(owning)).isTrue();

        var other = new Course();
        other.setId(8L);
        assertThat(job.canAccess(other)).isFalse();
    }

    @Test
    void carriesIntentEpisodeIdConfirmReasonAndRequestToken() {
        var job = new StruggleInterventionJob("tok", 1L, 2L, 3L, "decide", "ep-42", "progress", "rt-uuid");
        assertThat(job.intent()).isEqualTo("decide");
        assertThat(job.episodeId()).isEqualTo("ep-42");
        assertThat(job.confirmReason()).isEqualTo("progress");
        assertThat(job.requestToken()).isEqualTo("rt-uuid");
    }

    @Test
    void nullFieldsAreAllowedOnDefaultDecidePath() {
        var job = new StruggleInterventionJob("tok2", 1L, 2L, 3L, null, null, null, null);
        assertThat(job.intent()).isNull();
        assertThat(job.episodeId()).isNull();
        assertThat(job.confirmReason()).isNull();
        assertThat(job.requestToken()).isNull();
    }
}
