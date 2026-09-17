package de.tum.cit.aet.artemis.course;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;
import org.hibernate.collection.spi.PersistentSet;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.assessment.dto.score.StudentScoresDTO;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.dto.ActiveExamForCourseDashboardDTO;
import de.tum.cit.aet.artemis.course.dto.CourseAssessmentDashboardDTO;
import de.tum.cit.aet.artemis.course.dto.CourseDashboardDTO;
import de.tum.cit.aet.artemis.course.dto.CourseExerciseDueDateDTO;
import de.tum.cit.aet.artemis.course.dto.CourseForDashboardDTO;
import de.tum.cit.aet.artemis.course.dto.CourseForEnrollmentDTO;
import de.tum.cit.aet.artemis.course.dto.CourseForQuizSelectionDTO;
import de.tum.cit.aet.artemis.course.dto.CourseManagementDTO;
import de.tum.cit.aet.artemis.course.dto.CourseManagementExerciseDTO;
import de.tum.cit.aet.artemis.course.dto.CourseScoresDTO;
import de.tum.cit.aet.artemis.course.dto.CourseWithContentDTO;
import de.tum.cit.aet.artemis.course.dto.CourseWithExercisesDTO;
import de.tum.cit.aet.artemis.course.dto.CourseWithOrganizationsDTO;
import de.tum.cit.aet.artemis.course.dto.CoursesForDashboardDTO;
import de.tum.cit.aet.artemis.course.dto.LockedCourseSubmissionDTO;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.modeling.domain.DiagramType;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseResponseDTO;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.dto.TextExerciseResponseDTO;

/**
 * Serialization and contract tests for course management and dashboard DTOs.
 * Verifies backward compatibility, complete JSON contract schemas, and the absence of entity-only fields.
 */
class CourseDtoSerializationTest {

    private final JsonMapper objectMapper = JsonObjectMapper.get();

    @Test
    void shouldSerializeIsoDatesAndExamMaxPointsWhenActiveExamExistsForDashboard() {
        Course course = new Course();
        course.setId(10L);
        course.setTitle("Active Exam Course");
        course.setShortName("aec");

        Exam exam = new Exam();
        exam.setId(20L);
        exam.setTitle("Active Midterm");
        exam.setStartDate(ZonedDateTime.parse("2026-09-16T12:00:00Z"));
        exam.setEndDate(ZonedDateTime.parse("2026-09-16T13:00:00Z"));
        exam.setTestExam(false);
        exam.setExamMaxPoints(80);
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
        JsonNode activeExam = tree.get("activeExams").get(0);
        assertThat(activeExam.get("title").asString()).isEqualTo("Active Midterm");
        assertThat(activeExam.get("course").get("title").asString()).isEqualTo("Active Exam Course");
        assertThat(activeExam.get("examMaxPoints").asInt()).isEqualTo(80);
        // The app writes dates as ISO-8601 strings, not epoch numbers.
        assertThat(activeExam.get("startDate").asString()).isEqualTo("2026-09-16T12:00:00Z");

        CoursesForDashboardDTO deserialized = objectMapper.readValue(json, CoursesForDashboardDTO.class);
        assertThat(deserialized.courses()).hasSize(1);
        assertThat(deserialized.activeExams()).hasSize(1);
        assertThat(deserialized.activeExams().iterator().next().examMaxPoints()).isEqualTo(80);
    }

    @Test
    void shouldExcludeForbiddenEntityAndProxyFieldsAndIncludeArchivePathWhenCourseManagementDtoSerialized() {
        Course course = new Course();
        course.setId(42L);
        course.setTitle("Management Course");
        course.setShortName("mgmt");
        course.setCourseArchivePath("archives/mgmt-42.zip");

        CourseManagementDTO dto = CourseManagementDTO.of(course);
        JsonNode json = objectMapper.valueToTree(dto);

        assertThat(json.has("id")).isTrue();
        assertThat(json.has("title")).isTrue();
        assertThat(json.has("shortName")).isTrue();
        assertThat(json.get("courseArchivePath").asString()).isEqualTo("archives/mgmt-42.zip");

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
    void shouldSerializeAndDeserializePolymorphicExerciseTypesWhenCourseWithExercisesDtoUsed() {
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
        assertThat(Pattern.compile("\"type\"\\s*:").matcher(json).results().count()).isEqualTo(2);

        CourseWithExercisesDTO deserialized = objectMapper.readValue(json, CourseWithExercisesDTO.class);
        assertThat(deserialized.exercises()).hasSize(2);

        Map<String, CourseManagementExerciseDTO> exercisesByTitle = deserialized.exercises().stream().collect(Collectors.toMap(CourseManagementExerciseDTO::title, e -> e));

        CourseManagementExerciseDTO loadedProg = exercisesByTitle.get("Polymorphic Programming");
        assertThat(loadedProg).isInstanceOf(ProgrammingExerciseResponseDTO.class);
        assertThat(loadedProg.type()).isEqualTo("programming");

        CourseManagementExerciseDTO loadedText = exercisesByTitle.get("Polymorphic Text");
        assertThat(loadedText).isInstanceOf(TextExerciseResponseDTO.class);
        assertThat(loadedText.type()).isEqualTo("text");
    }

    @Test
    void shouldOmitCourseKeyForEveryExerciseTypeWhenCourseWithExercisesOrContentDtoSerialized() {
        Course course = new Course();
        course.setId(60L);
        course.setTitle("Discriminator Course");
        course.setShortName("disc");

        ProgrammingExercise programmingExercise = new ProgrammingExercise();
        programmingExercise.setId(201L);
        programmingExercise.setTitle("Prog");
        programmingExercise.setShortName("prog");
        programmingExercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        course.addExercises(programmingExercise);

        TextExercise textExercise = new TextExercise();
        textExercise.setId(202L);
        textExercise.setTitle("Text");
        textExercise.setShortName("text");
        course.addExercises(textExercise);

        ModelingExercise modelingExercise = new ModelingExercise();
        modelingExercise.setId(203L);
        modelingExercise.setTitle("Modeling");
        modelingExercise.setShortName("model");
        modelingExercise.setDiagramType(DiagramType.ClassDiagram);
        course.addExercises(modelingExercise);

        FileUploadExercise fileUploadExercise = new FileUploadExercise();
        fileUploadExercise.setId(204L);
        fileUploadExercise.setTitle("Upload");
        fileUploadExercise.setShortName("upload");
        fileUploadExercise.setFilePattern("pdf");
        course.addExercises(fileUploadExercise);

        QuizExercise quizExercise = new QuizExercise();
        quizExercise.setId(205L);
        quizExercise.setTitle("Quiz");
        quizExercise.setShortName("quiz");
        course.addExercises(quizExercise);

        assertNoCourseKeyOnAnyExerciseAndKeepsSubtypeFields(objectMapper.valueToTree(CourseWithExercisesDTO.of(course)).get("exercises"));
        assertNoCourseKeyOnAnyExerciseAndKeepsSubtypeFields(objectMapper.valueToTree(CourseWithContentDTO.of(course)).get("exercises"));
    }

    private void assertNoCourseKeyOnAnyExerciseAndKeepsSubtypeFields(JsonNode exercises) {
        assertThat(exercises).hasSize(5);

        Map<String, JsonNode> exercisesByType = new HashMap<>();
        exercises.forEach(exercise -> exercisesByType.put(exercise.get("type").asString(), exercise));
        assertThat(exercisesByType).containsOnlyKeys("programming", "text", "modeling", "file-upload", "quiz");

        Set<String> typesWithCourseKey = exercisesByType.entrySet().stream().filter(entry -> entry.getValue().has("course")).map(Map.Entry::getKey).collect(Collectors.toSet());
        assertThat(typesWithCourseKey).as("exercise types that still leak a course back-reference").isEmpty();

        assertThat(exercisesByType.get("programming").get("programmingLanguage").asString()).isEqualTo("JAVA");
        assertThat(exercisesByType.get("modeling").get("diagramType").asString()).isEqualTo("ClassDiagram");
        assertThat(exercisesByType.get("file-upload").get("filePattern").asString()).isEqualTo("pdf");
        assertThat(exercisesByType.get("quiz").get("quizMode").asString()).isEqualTo("SYNCHRONIZED");
    }

    @Test
    void shouldIncludeTeamModeWhenAssessmentExerciseDtoSerialized() {
        Course course = new Course();
        course.setId(70L);
        course.setTitle("Assessment Course");
        course.setShortName("assess");

        TextExercise teamExercise = new TextExercise();
        teamExercise.setId(301L);
        teamExercise.setTitle("Team Exercise");
        teamExercise.setMode(ExerciseMode.TEAM);
        course.addExercises(teamExercise);

        CourseAssessmentDashboardDTO dto = CourseAssessmentDashboardDTO.of(course);
        JsonNode exercise = objectMapper.valueToTree(dto).get("exercises").get(0);
        assertThat(exercise.get("teamMode").asBoolean()).isTrue();
    }

    @Test
    void shouldExcludeSensitiveContentAndBackReferencesWhenLockedCourseSubmissionDtoSerialized() {
        Course course = new Course();
        course.setId(80L);
        course.setTitle("Locked Course");
        course.setShortName("locked");

        TextExercise exercise = new TextExercise();
        exercise.setId(201L);
        exercise.setTitle("Exam Essay");
        // The exercise carries a course back-reference in production; the DTO must not leak it regardless.
        exercise.setCourse(course);

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
    void shouldOmitOptionalFieldsWhenLockedCourseSubmissionDtoHasNoParticipation() {
        LockedCourseSubmissionDTO dto = new LockedCourseSubmissionDTO(402L, null, "text", null, null);
        JsonNode json = objectMapper.valueToTree(dto);

        assertThat(json.has("id")).isTrue();
        assertThat(json.has("participation")).isFalse();
        assertThat(json.has("latestResult")).isFalse();
    }

    @Test
    void shouldMapEmptyCollectionsWhenCourseCollectionsAreNull() {
        Course course = new Course();
        course.setId(99L);
        course.setTitle("Uninitialized Course");
        course.setShortName("uninit");
        course.setExercises(null);
        course.setLectures(null);
        course.setExams(null);
        course.setCompetencies(null);
        course.setPrerequisites(null);

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

    @Test
    void shouldMapEmptyCollectionsWithoutLoadingWhenCourseCollectionsAreUnloaded() {
        Course course = new Course();
        course.setId(98L);
        course.setTitle("Unloaded Course");
        course.setShortName("unloaded");
        course.setExercises(new PersistentSet<>());
        course.setLectures(new PersistentSet<>());
        course.setExams(new PersistentSet<>());
        course.setCompetencies(new PersistentSet<>());
        course.setPrerequisites(new PersistentSet<>());
        course.setOrganizations(new PersistentSet<>());

        TextExercise exercise = new TextExercise();
        exercise.setId(500L);
        exercise.setTitle("Unloaded Categories Exercise");
        exercise.setCategories(new PersistentSet<>());

        assertCourseCollectionsUninitialized(course);
        assertThat(Hibernate.isInitialized(exercise.getCategories())).isFalse();

        CourseDashboardDTO dashboardDTO = CourseDashboardDTO.of(course);
        assertThat(dashboardDTO.exercises()).isEmpty();
        assertThat(dashboardDTO.lectures()).isEmpty();
        assertThat(dashboardDTO.exams()).isEmpty();
        assertThat(dashboardDTO.competencies()).isEmpty();
        assertThat(dashboardDTO.prerequisites()).isEmpty();

        CourseManagementDTO managementDTO = CourseManagementDTO.of(course);
        assertThat(managementDTO.onlineCourseConfiguration()).isNull();

        assertThat(CourseForEnrollmentDTO.of(course).prerequisites()).isEmpty();
        assertThat(CourseForQuizSelectionDTO.of(course).exercises()).isEmpty();
        assertThat(CourseWithOrganizationsDTO.of(course).organizations()).isEmpty();
        assertThat(CourseExerciseDueDateDTO.of(exercise).categories()).isEmpty();

        assertCourseCollectionsUninitialized(course);
        assertThat(Hibernate.isInitialized(exercise.getCategories())).isFalse();
    }

    private void assertCourseCollectionsUninitialized(Course course) {
        assertThat(Hibernate.isInitialized(course.getExercises())).isFalse();
        assertThat(Hibernate.isInitialized(course.getLectures())).isFalse();
        assertThat(Hibernate.isInitialized(course.getExams())).isFalse();
        assertThat(Hibernate.isInitialized(course.getCompetencies())).isFalse();
        assertThat(Hibernate.isInitialized(course.getPrerequisites())).isFalse();
        assertThat(Hibernate.isInitialized(course.getOrganizations())).isFalse();
    }
}
