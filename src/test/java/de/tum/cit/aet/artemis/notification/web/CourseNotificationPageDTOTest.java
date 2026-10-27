package de.tum.cit.aet.artemis.notification.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.notification.dto.CourseNotificationDTO;
import de.tum.cit.aet.artemis.notification.dto.CourseNotificationPageDTO;
import de.tum.cit.aet.artemis.notification.dto.CourseNotificationPageableDTO;

class CourseNotificationPageDTOTest {

    @Test
    void shouldPreserveReconstructionFailuresAndAllMetadata() {
        List<CourseNotificationDTO> entries = Arrays.asList((CourseNotificationDTO) null);
        var internal = new CourseNotificationPageableDTO<>(entries, 2, 10, 21, 3);
        var page = CourseNotificationPageDTO.from(internal);
        assertThat(page.content()).isSameAs(entries).containsExactly((CourseNotificationDTO) null);
        assertThat(page.pageNumber()).isEqualTo(2);
        assertThat(page.pageSize()).isEqualTo(10);
        assertThat(page.totalElements()).isEqualTo(21);
        assertThat(page.totalPages()).isEqualTo(3);
        var json = new JsonMapper().valueToTree(page);
        assertThat(json.path("content").get(0).isNull()).isTrue();
    }

    @Test
    void shouldOmitEmptyContentButRetainZeroMetadata() {
        var json = new JsonMapper().valueToTree(new CourseNotificationPageDTO(List.of(), 0, 10, 0, 0));
        assertThat(json.has("content")).isFalse();
        assertThat(json.path("pageNumber").asInt()).isZero();
        assertThat(json.path("pageSize").asInt()).isEqualTo(10);
        assertThat(json.has("totalElements")).isTrue();
        assertThat(json.has("totalPages")).isTrue();
    }
}
