package de.tum.cit.aet.artemis.course;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import de.tum.cit.aet.artemis.assessment.dto.score.StudentScoresDTO;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.dto.ActiveExamForCourseDashboardDTO;
import de.tum.cit.aet.artemis.course.dto.CourseDashboardDTO;
import de.tum.cit.aet.artemis.course.dto.CourseForDashboardDTO;
import de.tum.cit.aet.artemis.course.dto.CourseManagementDTO;
import de.tum.cit.aet.artemis.course.dto.CourseManagementExerciseDTO;
import de.tum.cit.aet.artemis.course.dto.CourseScoresDTO;
import de.tum.cit.aet.artemis.course.dto.CourseWithExercisesDTO;
import de.tum.cit.aet.artemis.course.dto.CoursesForDashboardDTO;
import de.tum.cit.aet.artemis.course.dto.LockedCourseSubmissionDTO;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseResponseDTO;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.dto.TextExerciseResponseDTO;

/**
 * Serialization and contract tests for course management and dashboard DTOs.
 * Verifies backward compatibility, complete JSON contract schemas, and the absence of entity-only fields.
 */
class CourseDtoSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule()).configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void testCoursesForDashboardSerializationWithActiveExam() throws Exception {
        Course course = new Course();
        course.setId(10L);
        course.setTitle("Active Exam Course");
        course.setShortName("aec");

        Exam exam = new Exam();
        exam.setId(20L);
        exam.setTitle("Active Midterm");
        exam.setStartDate(ZonedDateTime.now().minusHours(1));
        exam.setEndDate(ZonedDateTime.now().plusHours(1));
        exam.setTestExam(false);
        exam.setCourse(course);

        ActiveExamForCourseDashboardDTO activeExamDTO = ActiveExamForCourseDashboardDTO.of(exam);
        StudentScoresDTO studentScores = new StudentScoresDTO(80.0, 80.0, 80.0, 80.0, 0.0);
        CourseScoresDTO courseScores = new CourseScoresDTO(100.0, 100.0, 0.0, studentScores);
        CourseForDashboardDTO courseForDashboardDTO = new CourseForDashboardDTO(CourseDashboardDTO.of(course), courseScores, null, null, null, null, null, Set.of());

        CoursesForDashboardDTO multiCourseDTO = new CoursesForDashboardDTO(Set.of(courseForDashboardDTO), Set.of(activeExamDTO));
        String json = objectMapper.writeValueAsString(multiCourseDTO);

        JsonNode tree = objectMapper.readTree(json);
        assertThat(tree.has("courses")).isTrue();
        assertThat(tree.has("activeExams")).isTrue();
        assertThat(tree.get("activeExams").get(0).get("title").asText()).isEqualTo("Active Midterm");
        assertThat(tree.get("activeExams").get(0).get("course").get("title").asText()).isEqualTo("Active Exam Course");

        CoursesForDashboardDTO deserialized = objectMapper.readValue(json, CoursesForDashboardDTO.class);
        assertThat(deserialized.courses()).hasSize(1);
        assertThat(deserialized.activeExams()).hasSize(1);
    }

    @Test
    void testCourseManagementDtoExcludesForbiddenEntityAndProxyFields() throws Exception {
        Course course = new Course();
        course.setId(42L);
        course.setTitle("Management Course");
        course.setShortName("mgmt");

        CourseManagementDTO dto = CourseManagementDTO.of(course);
        JsonNode json = objectMapper.valueToTree(dto);

        assertThat(json.has("id")).isTrue();
        assertThat(json.has("title")).isTrue();
        assertThat(json.has("shortName")).isTrue();

        assertThat(json.has("courseRoles")).isFalse();
        assertThat(json.has("organizations")).isFalse();
        assertThat(json.has("tutorialGroups")).isFalse();
        assertThat(json.has("learningPaths")).isFalse();
        assertThat(json.has("exercises")).isFalse();
        assertThat(json.has("lectures")).isFalse();
        assertThat(json.has("hibernateLazyInitializer")).isFalse();
        assertThat(json.has("handler")).isFalse();
    }

    @Test
    void testCourseWithExercisesDtoPolymorphicSerializationAndDeserialization() throws Exception {
        ProgrammingExercise programmingExercise = new ProgrammingExercise();
        programmingExercise.setId(101L);
        programmingExercise.setTitle("Polymorphic Programming");
        programmingExercise.setShortName("polyprog");

        TextExercise textExercise = new TextExercise();
        textExercise.setId(102L);
        textExercise.setTitle("Polymorphic Text");
        textExercise.setShortName("polytext");

        Course course = new Course();
        course.setId(50L);
        course.setTitle("Exercise Course");
        course.setShortName("excourse");
        course.addExercises(programmingExercise);
        course.addExercises(textExercise);

        CourseWithExercisesDTO dto = CourseWithExercisesDTO.of(course);
        String json = objectMapper.writeValueAsString(dto);

        assertThat(json).contains("\"type\":\"programming\"");
        assertThat(json).contains("\"type\":\"text\"");

        CourseWithExercisesDTO deserialized = objectMapper.readValue(json, CourseWithExercisesDTO.class);
        assertThat(deserialized.exercises()).hasSize(2);

        Map<String, CourseManagementExerciseDTO> exercisesByTitle = deserialized.exercises().stream()
                .collect(java.util.stream.Collectors.toMap(CourseManagementExerciseDTO::title, e -> e));

        CourseManagementExerciseDTO loadedProg = exercisesByTitle.get("Polymorphic Programming");
        assertThat(loadedProg).isInstanceOf(ProgrammingExerciseResponseDTO.class);
        assertThat(loadedProg.type()).isEqualTo("programming");

        CourseManagementExerciseDTO loadedText = exercisesByTitle.get("Polymorphic Text");
        assertThat(loadedText).isInstanceOf(TextExerciseResponseDTO.class);
        assertThat(loadedText.type()).isEqualTo("text");
    }

    @Test
    void testLockedCourseSubmissionDtoExcludesSensitiveContentAndBackreferences() throws Exception {
        TextExercise exercise = new TextExercise();
        exercise.setId(201L);
        exercise.setTitle("Exam Essay");

        StudentParticipation participation = new StudentParticipation();
        participation.setId(301L);
        participation.setExercise(exercise);

        TextSubmission submission = new TextSubmission();
        submission.setId(401L);
        submission.setText("Private sensitive student text that must not leak");
        submission.setParticipation(participation);

        LockedCourseSubmissionDTO dto = LockedCourseSubmissionDTO.of(submission);
        JsonNode json = objectMapper.valueToTree(dto);

        assertThat(json.has("id")).isTrue();
        assertThat(json.has("submissionExerciseType")).isTrue();
        assertThat(json.has("participation")).isTrue();
        assertThat(json.has("text")).isFalse();
        assertThat(json.get("participation").has("exercise")).isTrue();
        assertThat(json.get("participation").get("exercise").has("course")).isFalse();
    }

    @Test
    void testLockedCourseSubmissionDtoAllowsMissingParticipation() {
        LockedCourseSubmissionDTO dto = new LockedCourseSubmissionDTO(402L, null, "text", null, null);
        JsonNode json = objectMapper.valueToTree(dto);

        assertThat(json.has("id")).isTrue();
        assertThat(json.has("participation")).isFalse();
        assertThat(json.has("latestResult")).isFalse();
    }

    @Test
    void testMapperHandlesUninitializedAndNullCollectionsSafely() {
        Course course = new Course();
        course.setId(99L);
        course.setTitle("Uninitialized Course");
        course.setShortName("uninit");

        CourseDashboardDTO dashboardDTO = CourseDashboardDTO.of(course);
        assertThat(dashboardDTO.exercises()).isEmpty();
        assertThat(dashboardDTO.lectures()).isEmpty();
        assertThat(dashboardDTO.exams()).isEmpty();
        assertThat(dashboardDTO.competencies()).isEmpty();
        assertThat(dashboardDTO.prerequisites()).isEmpty();

        CourseManagementDTO managementDTO = CourseManagementDTO.of(course);
        assertThat(managementDTO.onlineCourseConfiguration()).isNull();
        assertThat(managementDTO.tutorialGroupsConfiguration()).isNull();
        assertThat(managementDTO.courseConfiguration()).isNull();
    }
}
