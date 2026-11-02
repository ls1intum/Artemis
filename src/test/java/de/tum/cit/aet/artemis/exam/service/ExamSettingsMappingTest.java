package de.tum.cit.aet.artemis.exam.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.dto.ExamImportDTO;
import de.tum.cit.aet.artemis.exam.dto.ExamUpdateDTO;

class ExamSettingsMappingTest {

    @Test
    void creationAndImportApplyTheSameSettingsWithoutChangingTheWireShape() {
        String json = """
                {"title":"Exam", "testExam":true, "examWithAttendanceCheck":true, "visibleDate":"2026-10-01T10:00:00Z",
                "startDate":"2026-10-01T11:00:00Z", "endDate":"2026-10-01T12:00:00Z", "gracePeriod":180,
                "workingTime":3600, "startText":"Start", "endText":"End", "examMaxPoints":100,
                "randomizeExerciseOrder":true, "numberOfExercisesInExam":5, "numberOfCorrectionRoundsInExam":2,
                "examiner":"Alice", "moduleNumber":"IN0001", "courseName":"Algorithms", "channelName":"exam"}
                """;
        var mapper = JsonObjectMapper.get();
        Exam created = mapper.readValue(json, ExamUpdateDTO.class).toEntity();
        var course = new Course();
        Exam imported = mapper.readValue(json, ExamImportDTO.class).toEntity(course);
        assertThat(created.isTestExam()).isTrue();
        assertThat(created.isExamWithAttendanceCheck()).isTrue();
        assertThat(created.getGracePeriod()).isEqualTo(180);
        assertThat(created.getWorkingTime()).isEqualTo(3600);
        assertThat(created.getExamMaxPoints()).isEqualTo(100);
        assertThat(created.getExaminer()).isEqualTo("Alice");
        assertThat(imported).usingRecursiveComparison().ignoringFields("course").isEqualTo(created);
        assertThat(imported.getCourse()).isSameAs(course);
        assertThat(mapper.readTree(mapper.writeValueAsString(mapper.readValue(json, ExamUpdateDTO.class))).has("settings")).isFalse();
    }

    @Test
    void updateKeepsExamKindAndOmittedGracePeriodButClearsNullableSettings() {
        var exam = new Exam();
        exam.setTestExam(true);
        exam.setGracePeriod(120);
        exam.setExaminer("Previous examiner");
        var dto = JsonObjectMapper.get().readValue("{\"title\":\"Updated\",\"testExam\":false}", ExamUpdateDTO.class);
        dto.applyTo(exam);
        assertThat(exam.isTestExam()).isTrue();
        assertThat(exam.getGracePeriod()).isEqualTo(120);
        assertThat(exam.getExaminer()).isNull();
        assertThat(exam.getTitle()).isEqualTo("Updated");
    }
}
