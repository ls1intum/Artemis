package de.tum.cit.aet.artemis.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import java.net.URI;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exam.dto.ExerciseForExerciseGroupDTO;
import de.tum.cit.aet.artemis.exam.dto.ExerciseGroupCreateDTO;
import de.tum.cit.aet.artemis.exam.dto.ExerciseGroupDTO;
import de.tum.cit.aet.artemis.exam.dto.ExerciseGroupImportResultDTO;
import de.tum.cit.aet.artemis.exam.dto.ExerciseGroupUpdateDTO;
import de.tum.cit.aet.artemis.exam.repository.ExerciseGroupRepository;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exam.util.ExamFactory;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.ExerciseSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityWeaviateService;
import de.tum.cit.aet.artemis.globalsearch.service.WeaviateService;
import de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseFactory;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationJenkinsLocalVCTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.repository.TextExerciseRepository;
import de.tum.cit.aet.artemis.text.util.TextExerciseFactory;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

class ExerciseGroupIntegrationJenkinsLocalVCTest extends AbstractSpringIntegrationJenkinsLocalVCTest {

    private static final String TEST_PREFIX = "gtsettingtest";

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    @Autowired
    private ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    @Autowired
    private ExamTestRepository examRepository;

    @Autowired
    private ExerciseGroupRepository exerciseGroupRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired(required = false)
    private WeaviateService weaviateService;

    @Autowired(required = false)
    private SearchableEntityWeaviateService searchableEntityWeaviateService;

    private Course course1;

    private Exam exam1;

    private Exam exam2;

    private ExerciseGroup exerciseGroup1;

    private TextExercise textExercise1;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);
        course1 = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        exam1 = examUtilService.addExamWithExerciseGroup(course1, true);
        exam2 = examUtilService.addExamWithExerciseGroup(course1, true);
        exerciseGroup1 = exam1.getExerciseGroups().getFirst();
        var textEx = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup1);
        textExercise1 = textExerciseRepository.save(textEx);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testAll_asStudent() throws Exception {
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testAll_asTutor() throws Exception {
        this.testAllPreAuthorize();
    }

    private void testAllPreAuthorize() throws Exception {
        ExerciseGroup exerciseGroup = ExamFactory.generateExerciseGroup(true, exam1);
        request.post("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exercise-groups", ExerciseGroupCreateDTO.of(exerciseGroup), HttpStatus.FORBIDDEN);
        request.put("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exercise-groups", ExerciseGroupUpdateDTO.of(exerciseGroup1), HttpStatus.FORBIDDEN);
        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exercise-groups/" + exerciseGroup1.getId(), HttpStatus.FORBIDDEN,
                ExerciseGroupDTO.class);
        request.getList("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exercise-groups", HttpStatus.FORBIDDEN, ExerciseGroupDTO.class);
        request.delete("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exercise-groups/" + exerciseGroup1.getId(), HttpStatus.FORBIDDEN);
        request.postListWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/import-exercise-group", List.of(exerciseGroup), ExerciseGroup.class,
                HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testCreateExerciseGroup_asEditor() throws Exception {
        int groupsBefore = examRepository.findWithExerciseGroupsById(exam2.getId()).orElseThrow().getExerciseGroups().size();

        // A body carrying an id is rejected, preserving the previous endpoint behavior (400 idExists).
        ExerciseGroup withId = ExamFactory.generateExerciseGroup(true, exam1);
        withId.setId(55L);
        request.post("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exercise-groups", withId, HttpStatus.BAD_REQUEST);

        // A body without an exam reference is rejected (409 missingExam).
        ExerciseGroup withoutExam = ExamFactory.generateExerciseGroup(true, exam1);
        withoutExam.setExam(null);
        request.post("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exercise-groups", ExerciseGroupCreateDTO.of(withoutExam), HttpStatus.CONFLICT);

        // A body whose exam reference does not match the path exam is rejected (409 wrongExamId).
        ExerciseGroup wrongExam = ExamFactory.generateExerciseGroup(true, exam1);
        request.post("/api/exam/courses/" + course1.getId() + "/exams/" + exam2.getId() + "/exercise-groups", ExerciseGroupCreateDTO.of(wrongExam), HttpStatus.CONFLICT);

        ExerciseGroup exerciseGroup = ExamFactory.generateExerciseGroup(true, exam2);
        exerciseGroup.setTitle("      ExerciseGroup 123       ");
        URI exerciseGroupUri = request.post("/api/exam/courses/" + course1.getId() + "/exams/" + exam2.getId() + "/exercise-groups", ExerciseGroupCreateDTO.of(exerciseGroup),
                HttpStatus.CREATED);
        verify(examAccessService).checkCourseAndExamAccessForEditorElseThrow(course1.getId(), exam2.getId());

        ExerciseGroupDTO savedExerciseGroup = request.get(String.valueOf(exerciseGroupUri), HttpStatus.OK, ExerciseGroupDTO.class);
        // title is stripped server-side, mandatory flag is persisted, and the created group belongs to the path exam's course
        assertThat(savedExerciseGroup.title()).isEqualTo("ExerciseGroup 123");
        assertThat(savedExerciseGroup.isMandatory()).isTrue();
        assertThat(savedExerciseGroup.exam()).isNotNull();
        assertThat(savedExerciseGroup.exam().id()).isEqualTo(exam2.getId());

        // Independent DB check: exactly one new exercise group row was created for the exam (B1: never trust the response body).
        Exam reloaded = examRepository.findWithExerciseGroupsById(exam2.getId()).orElseThrow();
        assertThat(reloaded.getExerciseGroups()).hasSize(groupsBefore + 1);
        assertThat(reloaded.getExerciseGroups()).extracting(ExerciseGroup::getTitle).contains("ExerciseGroup 123");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testCreateExerciseGroup_acceptsEntityShapedBody_asEditor() throws Exception {
        // The Angular client posts a full entity-shaped body (no id, but a fully nested exam and possibly exercises).
        // The create DTO uses @JsonIgnoreProperties(ignoreUnknown = true), so all properties beyond the declared ones
        // must be silently ignored and the group created (201), proving zero-client-change compatibility.
        ExerciseGroup entityShaped = ExamFactory.generateExerciseGroup(true, exam2);
        entityShaped.setTitle("Entity Shaped Group");

        URI uri = request.post("/api/exam/courses/" + course1.getId() + "/exams/" + exam2.getId() + "/exercise-groups", entityShaped, HttpStatus.CREATED);
        ExerciseGroupDTO created = request.get(String.valueOf(uri), HttpStatus.OK, ExerciseGroupDTO.class);
        assertThat(created.title()).isEqualTo("Entity Shaped Group");
        assertThat(created.id()).isNotNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testCreateExerciseGroup_defaultsMandatoryToTrue_asEditor() throws Exception {
        // A body omitting isMandatory must fall back to the entity default (true), preserving the previous
        // deserialization behavior where the absent field left the entity's initialized value untouched.
        Map<String, Object> body = Map.of("title", "Default Mandatory Group", "exam", Map.of("id", exam2.getId()));

        URI uri = request.post("/api/exam/courses/" + course1.getId() + "/exams/" + exam2.getId() + "/exercise-groups", body, HttpStatus.CREATED);
        long createdId = Long.parseLong(uri.toString().substring(uri.toString().lastIndexOf('/') + 1));
        ExerciseGroup created = exerciseGroupRepository.findByIdElseThrow(createdId);
        assertThat(created.getIsMandatory()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testGetExerciseGroupsForExam_withoutGroups_returnsEmptyList() throws Exception {
        // The repository query inner-joins the groups off the exam, so an exam without groups yields no rows at all and
        // the endpoint returns the documented empty list.
        Exam emptyExam = examUtilService.addExam(course1);
        List<ExerciseGroupDTO> result = request.getList("/api/exam/courses/" + course1.getId() + "/exams/" + emptyExam.getId() + "/exercise-groups", HttpStatus.OK,
                ExerciseGroupDTO.class);
        assertThat(result).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testUpdateExerciseGroup_asEditor() throws Exception {
        // Exercise group with non-existent ID -> not found
        ExerciseGroup exerciseGroup = ExamFactory.generateExerciseGroup(true, exam1);
        exerciseGroup.setId(999999L);
        request.put("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exercise-groups", ExerciseGroupUpdateDTO.of(exerciseGroup), HttpStatus.NOT_FOUND);

        // Valid update
        request.put("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exercise-groups", ExerciseGroupUpdateDTO.of(exerciseGroup1), HttpStatus.OK);
        verify(examAccessService).checkCourseAndExamAndExerciseGroupAccessElseThrow(Role.EDITOR, course1.getId(), exam1.getId(), exerciseGroup1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testGetExerciseGroup_asEditor() throws Exception {
        ExerciseGroupDTO result = request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exercise-groups/" + exerciseGroup1.getId(), HttpStatus.OK,
                ExerciseGroupDTO.class);
        verify(examAccessService).checkCourseAndExamAndExerciseGroupAccessElseThrow(Role.EDITOR, course1.getId(), exam1.getId(), exerciseGroup1);
        // The single-group response embeds the nested exam (with its course) that the exam-exercise editors read to
        // rebuild course / exam references. Assert the exact fields the client reads, at the endpoint.
        assertThat(result.id()).isEqualTo(exerciseGroup1.getId());
        assertThat(result.exam()).isNotNull();
        assertThat(result.exam().id()).isEqualTo(exam1.getId());
        assertThat(result.exam().examMode()).isEqualTo(exam1.getExamMode());
        assertThat(result.exam().course()).isNotNull();
        assertThat(result.exam().course().id()).isEqualTo(course1.getId());
        // The list-only exercises component is intentionally not populated on the single-group response.
        assertThat(result.exercises()).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testGetExerciseGroup_carriesExamExampleSolutionPublicationDate_asEditor() throws Exception {
        // Non-default fixture: the exam's exampleSolutionPublicationDate is explicitly SET. The programming-exercise
        // editor reads exerciseGroup.exam.exampleSolutionPublicationDate to gate the "release tests with example
        // solution" checkbox; dropping it under @JsonInclude(NON_EMPTY) would disable the checkbox for exam programming
        // exercises. An unset date would serialize-omit and pass vacuously, so the date must be non-null here.
        // Truncate to millis: PostgreSQL stores microsecond precision, so a nanosecond-precise fixture would not survive
        // the DB round-trip and break the exact instant comparison (the field itself is carried correctly).
        ZonedDateTime exampleSolutionPublicationDate = ZonedDateTime.now().plusDays(3).truncatedTo(ChronoUnit.MILLIS);
        exam1.setExampleSolutionPublicationDate(exampleSolutionPublicationDate);
        examRepository.save(exam1);

        ExerciseGroupDTO result = request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exercise-groups/" + exerciseGroup1.getId(), HttpStatus.OK,
                ExerciseGroupDTO.class);

        assertThat(result.exam()).isNotNull();
        assertThat(result.exam().exampleSolutionPublicationDate()).isNotNull();
        assertThat(result.exam().exampleSolutionPublicationDate().toInstant()).isEqualTo(exampleSolutionPublicationDate.toInstant());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testGetExerciseGroupsForExam_asEditor() throws Exception {
        List<ExerciseGroupDTO> result = assertThatDb(
                () -> request.getList("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exercise-groups", HttpStatus.OK, ExerciseGroupDTO.class))
                .hasBeenCalledAtMostTimes(5);
        verify(examAccessService).checkCourseAndExamAccessForEditorElseThrow(course1.getId(), exam1.getId());
        assertThat(result).hasSize(1);
        // The list response embeds the exercise summaries. The previously serialized exam of each group is deliberately
        // dropped: the web client does not call this endpoint at all, and the Playwright helper reads only the exercises.
        ExerciseGroupDTO group = result.getFirst();
        assertThat(group.exam()).isNull();
        assertThat(group.exercises()).hasSize(1);
        ExerciseForExerciseGroupDTO exercise = group.exercises().getFirst();
        assertThat(exercise.id()).isEqualTo(textExercise1.getId());
        assertThat(exercise.type()).isEqualTo(ExerciseType.TEXT);
        assertThat(exercise.title()).isEqualTo(textExercise1.getTitle());
        assertThat(exercise.maxPoints()).isEqualTo(textExercise1.getMaxPoints());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteExerciseGroup_asInstructor() throws Exception {
        if (searchableEntityWeaviateService != null) {
            searchableEntityWeaviateService.upsertExerciseAsync(ExerciseSearchableEntityDTO.fromExercise(textExercise1));
            WeaviateTestUtil.assertExerciseExistsInWeaviate(weaviateService, textExercise1);
        }
        WeaviateTestUtil.assertExerciseExistsInWeaviate(weaviateService, textExercise1);

        request.delete("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exercise-groups/" + exerciseGroup1.getId(), HttpStatus.OK);
        verify(examAccessService).checkCourseAndExamAndExerciseGroupAccessElseThrow(Role.INSTRUCTOR, course1.getId(), exam1.getId(), exerciseGroup1);
        assertThat(textExerciseRepository.findById(textExercise1.getId())).isEmpty();

        WeaviateTestUtil.assertExerciseNotInWeaviate(weaviateService, textExercise1.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testDeleteExerciseGroup_asEditor() throws Exception {
        request.delete("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exercise-groups/" + exerciseGroup1.getId(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testCreateExerciseGroup_setsExamBackReferenceOnChild_asEditor() throws Exception {
        // C3: after a create, reload the CHILD directly (not via the parent) and assert its exam FK back-reference is set.
        // A parent-side-only assertion passes while the child.exam_id column is null.
        ExerciseGroup toCreate = ExamFactory.generateExerciseGroup(true, exam2);
        toCreate.setTitle("Back-reference group");
        URI uri = request.post("/api/exam/courses/" + course1.getId() + "/exams/" + exam2.getId() + "/exercise-groups", ExerciseGroupCreateDTO.of(toCreate), HttpStatus.CREATED);
        long createdId = Long.parseLong(uri.toString().substring(uri.toString().lastIndexOf('/') + 1));

        ExerciseGroup child = exerciseGroupRepository.findByIdElseThrow(createdId);
        assertThat(child.getExam()).as("the created exercise group must reference its exam").isNotNull();
        assertThat(child.getExam().getId()).isEqualTo(exam2.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteNonLastExerciseGroup_preservesOrderColumn_asInstructor() throws Exception {
        // C4: deleting a NON-LAST element of the @OrderColumn exercise-group list must not leave a null slot in the
        // reloaded list, and the remaining ids must stay stable and in order.
        Exam exam = ExamFactory.generateExam(course1);
        ExamFactory.generateExerciseGroupWithTitle(true, exam, "first");
        ExamFactory.generateExerciseGroupWithTitle(true, exam, "second");
        ExamFactory.generateExerciseGroupWithTitle(true, exam, "third");
        exam = examRepository.save(exam);
        Long firstId = exam.getExerciseGroups().get(0).getId();
        Long secondId = exam.getExerciseGroups().get(1).getId();
        Long thirdId = exam.getExerciseGroups().get(2).getId();

        request.delete("/api/exam/courses/" + course1.getId() + "/exams/" + exam.getId() + "/exercise-groups/" + secondId, HttpStatus.OK);

        Exam reloaded = examRepository.findWithExerciseGroupsById(exam.getId()).orElseThrow();
        assertThat(reloaded.getExerciseGroups()).as("the ordered exercise-group list must not contain a null gap").doesNotContainNull();
        assertThat(reloaded.getExerciseGroups()).extracting(ExerciseGroup::getId).as("the remaining ids stay stable and in order").containsExactly(firstId, thirdId);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @CsvSource({ "A,A,B,C", "A,B,C,C", "A,A,B,B" })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportExerciseGroup_programmingExerciseSameShortNameOrTitle(String shortName1, String shortName2, String title1, String title2) throws Exception {
        Exam exam = ExamFactory.generateExamWithExerciseGroup(course1, true);
        ExerciseGroup exerciseGroup = exam.getExerciseGroups().getFirst();
        ProgrammingExercise exercise1 = ProgrammingExerciseFactory.generateProgrammingExerciseForExam(exerciseGroup);
        ProgrammingExercise exercise2 = ProgrammingExerciseFactory.generateProgrammingExerciseForExam(exerciseGroup);

        exercise1.setShortName(shortName1);
        exercise2.setShortName(shortName2);
        exercise1.setTitle(title1);
        exercise2.setTitle(title2);
        examRepository.save(exam);

        request.postListWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/import-exercise-group", List.of(exerciseGroup), ExerciseGroup.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importExerciseGroup_successfulWithExercisesIntoSameExam() throws Exception {
        Exam targetExam = examUtilService.addExamWithModellingAndTextAndFileUploadAndQuizAndEmptyGroup(course1);

        final List<ExerciseGroup> exerciseGroupsBefore = targetExam.getExerciseGroups();
        final List<Long> idsBefore = exerciseGroupsBefore.stream().map(ExerciseGroup::getId).toList();

        final List<ExerciseGroupDTO> exerciseGroupsNow = request
                .postWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + targetExam.getId() + "/import-exercise-group", exerciseGroupsBefore,
                        ExerciseGroupImportResultDTO.class, HttpStatus.OK)
                .exerciseGroups();

        // Response shape: the 5 pre-existing groups plus the 4 imported groups (the empty source group is skipped) = 9, all with ids.
        assertThat(exerciseGroupsNow).hasSize(9).allMatch(element -> element.id() > 0);
        assertThat(exerciseGroupsNow).extracting(ExerciseGroupDTO::id).containsAll(idsBefore);

        // B1 independent DB check: count the actually persisted exercise-group rows via a fresh reload, never trusting the
        // HTTP response body. A merge-cascade duplicate row would surface here as a size mismatch that the in-memory /
        // response-body check cannot see.
        Exam reloaded = examRepository.findWithExerciseGroupsById(targetExam.getId()).orElseThrow();
        assertThat(reloaded.getExerciseGroups()).as("exactly 9 exercise-group rows persisted (5 existing + 4 imported)").hasSize(9);
        assertThat(reloaded.getExerciseGroups()).extracting(ExerciseGroup::getId).containsAll(idsBefore);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importExerciseGroup_skipsFailedExerciseAndReportsPartialSuccess() throws Exception {
        // Import groups from a source exam into a separate (empty) target exam, but remove the source quiz first so its
        // import yields Optional.empty (a real "source exercise no longer available" failure). The import must skip the
        // quiz, import the rest, drop the now-empty quiz group, and report the skipped quiz via the "skipped" list in the
        // response body instead of silently dropping it.
        Exam targetExam = examUtilService.addExam(course1);
        Exam sourceExam = examUtilService.addExamWithModellingAndTextAndFileUploadAndQuizAndEmptyGroup(course1);
        List<ExerciseGroup> groupsToImport = sourceExam.getExerciseGroups();

        QuizExercise sourceQuiz = (QuizExercise) groupsToImport.stream().flatMap(group -> group.getExercises().stream()).filter(QuizExercise.class::isInstance).findFirst()
                .orElseThrow();
        String quizTitle = sourceQuiz.getTitle();
        exerciseRepository.deleteById(sourceQuiz.getId());

        ExerciseGroupImportResultDTO result = request.postWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + targetExam.getId() + "/import-exercise-group",
                groupsToImport, ExerciseGroupImportResultDTO.class, HttpStatus.OK);

        // The skipped quiz is reported to the editor via the "skipped" list in the response body (not silently dropped).
        assertThat(result.skippedExercises()).as("the skipped quiz title must be reported").contains(quizTitle);
        // No exercise failed partway, so the incomplete list is empty and omitted from the response (DTO uses @JsonInclude(NON_EMPTY)).
        assertThat(result.incompleteExercises()).as("no exercise must be reported as incomplete").isNullOrEmpty();

        // The target exam received modelling, text and file upload (the empty source group is filtered out before import).
        // The quiz was skipped, so its group is empty but is intentionally KEPT (not deleted), in order, with no null
        // element. No QuizExercise was imported.
        Exam reloaded = examRepository.findWithExerciseGroupsAndExercisesById(targetExam.getId()).orElseThrow();
        long importedExerciseCount = reloaded.getExerciseGroups().stream().mapToLong(group -> group.getExercises().size()).sum();
        assertThat(importedExerciseCount).isEqualTo(3);
        assertThat(reloaded.getExerciseGroups().stream().flatMap(group -> group.getExercises().stream())).as("the quiz must be the skipped exercise")
                .noneMatch(QuizExercise.class::isInstance);
        assertThat(reloaded.getExerciseGroups()).as("all four imported groups are retained, including the emptied quiz group").hasSize(4);
        assertThat(reloaded.getExerciseGroups()).as("the ordered exercise-group list must not contain a null").doesNotContainNull();
        assertThat(reloaded.getExerciseGroups()).filteredOn(group -> group.getExercises().isEmpty()).as("the emptied quiz group is retained").hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void importExerciseGroup_successfulIntoDifferentExam() throws Exception {
        Exam targetExam = examUtilService.addExamWithModellingAndTextAndFileUploadAndQuizAndEmptyGroup(course1);

        Exam secondExam = examUtilService.addExamWithModellingAndTextAndFileUploadAndQuizAndEmptyGroup(course1);
        final List<ExerciseGroup> listSendToServer = secondExam.getExerciseGroups();

        final List<ExerciseGroupDTO> listReceived = request.postWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + targetExam.getId() + "/import-exercise-group",
                listSendToServer, ExerciseGroupImportResultDTO.class, HttpStatus.OK).exerciseGroups();

        final List<ExerciseGroup> listExpected = new ArrayList<>(targetExam.getExerciseGroups());
        listExpected.addAll(listSendToServer);

        assertThat(listReceived).hasSize(9);
        for (int i = 0; i <= 4; i++) {
            assertThat(listReceived.get(i).id()).isEqualTo(listExpected.get(i).getId());
        }
        for (int i = 5; i < 8; i++) {
            assertThat(listReceived.get(i).id()).isNotNull();
            assertThat(listReceived.get(i).id()).isNotEqualTo(listExpected.get(i).getId());
            assertThat(listReceived.get(i).title()).isEqualTo(listExpected.get(i).getTitle());
            assertThat(listReceived.get(i).isMandatory()).isEqualTo(listExpected.get(i).getIsMandatory());
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importExerciseGroup_successfulWithImportToOtherCourse() throws Exception {
        Course course2 = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        Exam targetExam = examUtilService.addExamWithModellingAndTextAndFileUploadAndQuizAndEmptyGroup(course2);

        Exam secondExam = examUtilService.addExamWithModellingAndTextAndFileUploadAndQuizAndEmptyGroup(course1);
        final List<ExerciseGroup> listSendToServer = secondExam.getExerciseGroups();

        final List<ExerciseGroupDTO> listReceived = request.postWithResponseBody("/api/exam/courses/" + course2.getId() + "/exams/" + targetExam.getId() + "/import-exercise-group",
                listSendToServer, ExerciseGroupImportResultDTO.class, HttpStatus.OK).exerciseGroups();
        assertThat(listReceived).hasSize(9);

        final List<ExerciseGroup> listExpected = new ArrayList<>(targetExam.getExerciseGroups());
        listExpected.addAll(listSendToServer);

        for (int i = 0; i <= 4; i++) {
            assertThat(listReceived.get(i).id()).isEqualTo(listExpected.get(i).getId());
        }
        for (int i = 5; i < 8; i++) {
            assertThat(listReceived.get(i).id()).isNotNull();
            assertThat(listReceived.get(i).id()).isNotEqualTo(listExpected.get(i).getId());
            assertThat(listReceived.get(i).title()).isEqualTo(listExpected.get(i).getTitle());
            assertThat(listReceived.get(i).isMandatory()).isEqualTo(listExpected.get(i).getIsMandatory());

            // D3: the raw JSON response deserializes into the new DTO types and the embedded exercise summary survives
            // (a fresh id, distinct from the source, with the copied title and a non-null type discriminator).
            ExerciseForExerciseGroupDTO importedExercise = listReceived.get(i).exercises().stream().findFirst().orElseThrow();
            Exercise sourceExercise = listExpected.get(i).getExercises().stream().findFirst().orElseThrow();
            assertThat(importedExercise.id()).isNotNull().isNotEqualTo(sourceExercise.getId());
            assertThat(importedExercise.type()).isNotNull();
            assertThat(importedExercise.title()).isEqualTo(sourceExercise.getTitle());
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importExerciseGroup_preCheckFailed() throws Exception {
        Exam exam = ExamFactory.generateExam(course1);
        ExerciseGroup programmingGroup = ExamFactory.generateExerciseGroup(false, exam);
        exam = examRepository.save(exam);
        ProgrammingExercise programming = ProgrammingExerciseFactory.generateProgrammingExerciseForExam(programmingGroup, ProgrammingLanguage.JAVA);
        programmingGroup.addExercise(programming);
        programming.setBuildConfig(programmingExerciseBuildConfigRepository.save(programming.getBuildConfig()));
        exerciseRepository.save(programming);

        versionControlService.createProjectForExercise(programming);
        doReturn(null).when(continuousIntegrationService).checkIfProjectExists(any(), any());

        request.postListWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + exam.getId() + "/import-exercise-group", List.of(programmingGroup),
                ExerciseGroup.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateOrderOfExerciseGroups() throws Exception {
        Exam exam = ExamFactory.generateExam(course1);
        ExerciseGroup exerciseGroup1 = ExamFactory.generateExerciseGroupWithTitle(true, exam, "first");
        ExerciseGroup exerciseGroup2 = ExamFactory.generateExerciseGroupWithTitle(true, exam, "second");
        ExerciseGroup exerciseGroup3 = ExamFactory.generateExerciseGroupWithTitle(true, exam, "third");
        examRepository.save(exam);

        TextExercise exercise1_1 = textExerciseUtilService.createTextExerciseForExam(exerciseGroup1);
        TextExercise exercise1_2 = textExerciseUtilService.createTextExerciseForExam(exerciseGroup1);
        TextExercise exercise2_1 = textExerciseUtilService.createTextExerciseForExam(exerciseGroup2);
        TextExercise exercise3_1 = textExerciseUtilService.createTextExerciseForExam(exerciseGroup3);
        TextExercise exercise3_2 = textExerciseUtilService.createTextExerciseForExam(exerciseGroup3);
        TextExercise exercise3_3 = textExerciseUtilService.createTextExerciseForExam(exerciseGroup3);

        List<Long> orderedExerciseGroupIds = new ArrayList<>(List.of(exerciseGroup2.getId(), exerciseGroup3.getId(), exerciseGroup1.getId()));
        // Should save new order
        request.put("/api/exam/courses/" + course1.getId() + "/exams/" + exam.getId() + "/exercise-groups-order", orderedExerciseGroupIds, HttpStatus.OK);
        verify(examAccessService).checkCourseAndExamAccessForEditorElseThrow(course1.getId(), exam.getId());

        List<ExerciseGroup> savedExerciseGroups = examRepository.findWithExerciseGroupsById(exam.getId()).orElseThrow().getExerciseGroups();
        assertThat(savedExerciseGroups.getFirst().getTitle()).isEqualTo("second");
        assertThat(savedExerciseGroups.get(1).getTitle()).isEqualTo("third");
        assertThat(savedExerciseGroups.get(2).getTitle()).isEqualTo("first");

        // Exercises should be preserved
        Exam savedExam = examRepository.findWithExerciseGroupsAndExercisesById(exam.getId()).orElseThrow();
        ExerciseGroup savedExerciseGroup1 = savedExam.getExerciseGroups().get(2);
        ExerciseGroup savedExerciseGroup2 = savedExam.getExerciseGroups().getFirst();
        ExerciseGroup savedExerciseGroup3 = savedExam.getExerciseGroups().get(1);
        assertThat(savedExerciseGroup1.getExercises()).containsExactlyInAnyOrder(exercise1_1, exercise1_2);
        assertThat(savedExerciseGroup2.getExercises()).containsExactlyInAnyOrder(exercise2_1);
        assertThat(savedExerciseGroup3.getExercises()).containsExactlyInAnyOrder(exercise3_1, exercise3_2, exercise3_3);

        // Should fail with too many exercise groups
        orderedExerciseGroupIds.add(exerciseGroup1.getId());
        request.put("/api/exam/courses/" + course1.getId() + "/exams/" + exam.getId() + "/exercise-groups-order", orderedExerciseGroupIds, HttpStatus.BAD_REQUEST);

        // Should fail with too few exercise groups
        orderedExerciseGroupIds.remove(3);
        orderedExerciseGroupIds.remove(2);
        request.put("/api/exam/courses/" + course1.getId() + "/exams/" + exam.getId() + "/exercise-groups-order", orderedExerciseGroupIds, HttpStatus.BAD_REQUEST);

        // Should fail with an exercise group id that does not belong to the exam
        List<Long> idsWithForeignGroup = Arrays.asList(exerciseGroup2.getId(), exerciseGroup3.getId(), exerciseGroup1.getId() + 100_000L);
        request.put("/api/exam/courses/" + course1.getId() + "/exams/" + exam.getId() + "/exercise-groups-order", idsWithForeignGroup, HttpStatus.BAD_REQUEST);

        // Should fail with duplicate ids: the rebuilt list would omit a group, and orphanRemoval on
        // Exam.exerciseGroups would delete the omitted group and its exercises.
        List<Long> idsWithDuplicate = Arrays.asList(exerciseGroup2.getId(), exerciseGroup2.getId(), exerciseGroup1.getId());
        request.put("/api/exam/courses/" + course1.getId() + "/exams/" + exam.getId() + "/exercise-groups-order", idsWithDuplicate, HttpStatus.BAD_REQUEST);

        // A fresh reload proves the rejected requests changed nothing: all three groups intact, still in the last
        // successfully saved order, and every exercise still in its original group.
        Exam reloadedExam = examRepository.findWithExerciseGroupsAndExercisesById(exam.getId()).orElseThrow();
        List<ExerciseGroup> reloadedGroups = reloadedExam.getExerciseGroups();
        assertThat(reloadedGroups).extracting(ExerciseGroup::getId).containsExactly(exerciseGroup2.getId(), exerciseGroup3.getId(), exerciseGroup1.getId());
        assertThat(reloadedGroups.getFirst().getExercises()).extracting(Exercise::getId).containsExactlyInAnyOrder(exercise2_1.getId());
        assertThat(reloadedGroups.get(1).getExercises()).extracting(Exercise::getId).containsExactlyInAnyOrder(exercise3_1.getId(), exercise3_2.getId(), exercise3_3.getId());
        assertThat(reloadedGroups.get(2).getExercises()).extracting(Exercise::getId).containsExactlyInAnyOrder(exercise1_1.getId(), exercise1_2.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateOrderOfExerciseGroups_wirePinsRequestAndResponseDTO() throws Exception {
        Exam exam = ExamFactory.generateExam(course1);
        ExerciseGroup exerciseGroupA = ExamFactory.generateExerciseGroupWithTitle(true, exam, "alpha");
        ExerciseGroup exerciseGroupB = ExamFactory.generateExerciseGroupWithTitle(true, exam, "beta");
        ExerciseGroup exerciseGroupC = ExamFactory.generateExerciseGroupWithTitle(true, exam, "gamma");
        exam = examRepository.save(exam);

        TextExercise exerciseA1 = textExerciseUtilService.createTextExerciseForExam(exerciseGroupA);
        TextExercise exerciseB1 = textExerciseUtilService.createTextExerciseForExam(exerciseGroupB);
        TextExercise exerciseC1 = textExerciseUtilService.createTextExerciseForExam(exerciseGroupC);

        // This is the actual wire shape the client sends: only the exercise-group ids, in the desired order,
        // not full ExerciseGroup entities. The response carries no body.
        List<Long> requestBody = List.of(exerciseGroupC.getId(), exerciseGroupA.getId(), exerciseGroupB.getId());

        request.put("/api/exam/courses/" + course1.getId() + "/exams/" + exam.getId() + "/exercise-groups-order", requestBody, HttpStatus.OK);

        // Fresh repository read (a new query, not the JPA session used to build the request) confirms the persisted
        // order, that the @OrderColumn-backed list has no null slot, and that every group AND exercise id survived.
        Exam reloaded = examRepository.findWithExerciseGroupsAndExercisesById(exam.getId()).orElseThrow();
        List<ExerciseGroup> persistedGroups = reloaded.getExerciseGroups();
        assertThat(persistedGroups).as("the @OrderColumn-backed list must not contain a null slot").doesNotContainNull();
        assertThat(persistedGroups).extracting(ExerciseGroup::getId).containsExactly(exerciseGroupC.getId(), exerciseGroupA.getId(), exerciseGroupB.getId());

        assertThat(persistedGroups.get(0).getExercises()).extracting(Exercise::getId).containsExactly(exerciseC1.getId());
        assertThat(persistedGroups.get(1).getExercises()).extracting(Exercise::getId).containsExactly(exerciseA1.getId());
        assertThat(persistedGroups.get(2).getExercises()).extracting(Exercise::getId).containsExactly(exerciseB1.getId());
    }
}
