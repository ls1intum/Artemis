package de.tum.cit.aet.artemis.iris.domain.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisActivityDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisActivityKind;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisActivityState;

class IrisMessageToolActivityConverterTest {

    private final IrisMessageToolActivityConverter converter = new IrisMessageToolActivityConverter();

    @Test
    void convertsActivitiesToJsonAndBack() {
        var activity = new PyrisActivityDTO("activity-1", PyrisActivityKind.TOOL, "lecture_content_retrieval", PyrisActivityState.FINISHED, "Lecture 1", "2 chunks", 120L);

        String json = converter.convertToDatabaseColumn(List.of(activity));

        assertThat(json).contains("\"id\":\"activity-1\"", "\"name\":\"lecture_content_retrieval\"");
        assertThat(converter.convertToEntityAttribute(json)).containsExactly(activity);
    }

    @Test
    void keepsNullActivityTrailNullable() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
