package de.tum.cit.aet.artemis.iris.service.pyris;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisCourseDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisLectureUnitDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisLectureIngestionStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisLectureUnitWebhookDTO;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.TextUnit;
import de.tum.cit.aet.artemis.videosource.domain.VideoSourceType;

/**
 * Pins the Pyris wire format contract:
 * <ul>
 * <li>Outbound webhook uses camelCase {@code videoSourceType}.</li>
 * <li>Inbound status update reads {@code error.code}.</li>
 * <li>Inbound ingestion status reads optional {@code displayPageNumbers} from its dedicated field.</li>
 * <li>Inbound status update silently ignores camelCase {@code errorCode} (unknown field), matching Spring Boot's default mapper config.</li>
 * <li>Outbound course DTO lists the lectures of the course, restricted to lecture units that are released.</li>
 * </ul>
 */
class WireFormatContractTest {

    private final ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void outboundWebhookUsesCamelCaseVideoSourceType() throws Exception {
        var dto = new PyrisLectureUnitWebhookDTO("", 0, null, 1L, "name", 2L, "lecture", 3L, "course", "desc", "url", "https://x", VideoSourceType.YOUTUBE);
        String json = mapper.writeValueAsString(dto);
        assertThat(json).contains("\"videoSourceType\":\"YOUTUBE\"");
        assertThat(json).doesNotContain("video_source_type");
    }

    @Test
    void inboundStatusUpdateReadsErrorCodeFromErrorObject() throws Exception {
        String json = "{\"result\":\"error\",\"runState\":\"FAILED\",\"error\":{\"code\":\"YOUTUBE_PRIVATE\"},\"jobId\":7}";
        var dto = mapper.readValue(json, PyrisLectureIngestionStatusUpdateDTO.class);
        assertThat(dto.error().code()).isEqualTo("YOUTUBE_PRIVATE");
    }

    @Test
    void inboundStatusUpdateReadsDedicatedDisplayPageNumbersField() throws Exception {
        String json = "{\"result\":\"done\",\"runState\":\"FINISHED\",\"jobId\":7,\"displayPageNumbers\":[1,2,-1]}";
        var dto = mapper.readValue(json, PyrisLectureIngestionStatusUpdateDTO.class);
        assertThat(dto.result()).isEqualTo("done");
        assertThat(dto.displayPageNumbers()).containsExactly(1, 2, -1);
    }

    @Test
    void inboundStatusUpdateKeepsMissingDisplayPageNumbersNullable() throws Exception {
        String json = "{\"result\":\"done\",\"runState\":\"FINISHED\",\"jobId\":7}";
        var dto = mapper.readValue(json, PyrisLectureIngestionStatusUpdateDTO.class);
        assertThat(dto.displayPageNumbers()).isNull();
    }

    @Test
    void inboundStatusUpdateRejectsCamelCaseErrorCode() throws Exception {
        String wire = "{\"result\":\"error\",\"runState\":\"FAILED\",\"jobId\":7,\"errorCode\":\"YOUTUBE_PRIVATE\"}";
        // Spring Boot's autoconfigured mapper has FAIL_ON_UNKNOWN_PROPERTIES=false, so camelCase "errorCode" is silently
        // ignored and error() returns null — this test mirrors that production behavior and documents the risk:
        // if Pyris accidentally sends "errorCode" instead of "error": {"code": ...}, we will silently see null.
        var dto = mapper.readValue(wire, PyrisLectureIngestionStatusUpdateDTO.class);
        assertThat(dto.error()).isNull();
    }

    @Test
    void outboundCourseDTOListsLecturesWithReleasedUnitsOnly() {
        var released = textUnit(40L, "Collisions", ZonedDateTime.now().minusDays(1));
        var unreleased = textUnit(41L, "Open Addressing", ZonedDateTime.now().plusDays(1));
        var course = courseWithLecture(lecture(4L, "Hashing", released, unreleased));

        var dto = PyrisCourseDTO.of(course);

        assertThat(dto.lectures()).hasSize(1);
        var lectureDTO = dto.lectures().getFirst();
        assertThat(lectureDTO.id()).isEqualTo(4L);
        assertThat(lectureDTO.title()).isEqualTo("Hashing");
        assertThat(lectureDTO.units()).extracting(PyrisLectureUnitDTO::name).containsExactly("Collisions");
        assertThat(lectureDTO.units()).extracting(PyrisLectureUnitDTO::lectureId).containsExactly(4L);
    }

    @Test
    void outboundCourseDTOKeepsLecturesWhoseUnitsAreAllUnreleased() {
        // The lecture remains a valid context switch target; only the unreleased unit names must stay hidden.
        var unreleased = textUnit(40L, "Open Addressing", ZonedDateTime.now().plusDays(1));
        var course = courseWithLecture(lecture(4L, "Hashing", unreleased));

        var dto = PyrisCourseDTO.of(course);

        assertThat(dto.lectures()).hasSize(1);
        assertThat(dto.lectures().getFirst().units()).isEmpty();
    }

    @Test
    void outboundCourseDTOWithoutLecturesOmitsTheLecturesField() throws Exception {
        var course = new Course();
        course.setId(1L);

        var dto = PyrisCourseDTO.of(course);

        assertThat(dto.lectures()).isEmpty();
        // NON_EMPTY drops the empty list on the wire; Pyris then falls back to its default
        assertThat(mapper.writeValueAsString(dto)).doesNotContain("\"lectures\"");
    }

    private static Course courseWithLecture(Lecture lecture) {
        var course = new Course();
        course.setId(1L);
        course.setLectures(Set.of(lecture));
        return course;
    }

    private static Lecture lecture(long id, String title, TextUnit... units) {
        var lecture = new Lecture();
        lecture.setId(id);
        lecture.setTitle(title);
        lecture.setLectureUnits(List.of(units));
        return lecture;
    }

    private static TextUnit textUnit(long id, String name, ZonedDateTime releaseDate) {
        var unit = new TextUnit();
        unit.setId(id);
        unit.setName(name);
        unit.setReleaseDate(releaseDate);
        return unit;
    }
}
