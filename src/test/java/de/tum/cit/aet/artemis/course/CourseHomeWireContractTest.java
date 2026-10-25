package de.tum.cit.aet.artemis.course;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.communication.dto.CourseHomeQuestionsDTO;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.course.dto.home.CourseHomeDTO;

class CourseHomeWireContractTest {

    @Test
    void unknownUnreadIsAbsentButKnownZeroIsKept() {
        var unknown = new CourseHomeQuestionsDTO.Item(1, 2, "Question", 0, CourseHomeQuestionsDTO.State.AWAITING_REPLY, null, Instant.EPOCH, null);
        var known = new CourseHomeQuestionsDTO.Item(1, 2, "Question", 0, CourseHomeQuestionsDTO.State.AWAITING_REPLY, 0L, Instant.EPOCH, null);
        assertThat(JsonObjectMapper.get().writeValueAsString(unknown)).doesNotContain("unreadReplies");
        assertThat(JsonObjectMapper.get().writeValueAsString(known)).contains("\"unreadReplies\":0");
        assertThat(JsonObjectMapper.get().writeValueAsString(new CourseHomeQuestionsDTO(CourseHomeDTO.SectionStatus.AVAILABLE, List.of()))).contains("AVAILABLE")
                .doesNotContain("items");
    }
}
