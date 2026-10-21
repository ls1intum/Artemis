package de.tum.cit.aet.artemis.exercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import tools.jackson.databind.node.ObjectNode;

import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.core.util.RequestUtilService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.dto.CourseMaterialImportOptionsDTO;
import de.tum.cit.aet.artemis.course.dto.CourseMaterialImportResultDTO;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVariantGroup;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseVariantGroupAssignmentDTO;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseTestRepository;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVariantGroupRepository;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.fileupload.dto.FileUploadExerciseDTO;
import de.tum.cit.aet.artemis.fileupload.dto.FileUploadExerciseInputDTO;
import de.tum.cit.aet.artemis.fileupload.dto.UpdateFileUploadExerciseDTO;
import de.tum.cit.aet.artemis.fileupload.util.FileUploadExerciseFactory;
import de.tum.cit.aet.artemis.fileupload.util.FileUploadExerciseUtilService;
import de.tum.cit.aet.artemis.modeling.domain.DiagramType;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.dto.ImportModelingExerciseDTO;
import de.tum.cit.aet.artemis.modeling.dto.ModelingExerciseResponseDTO;
import de.tum.cit.aet.artemis.modeling.dto.UpdateModelingExerciseDTO;
import de.tum.cit.aet.artemis.modeling.util.ModelingExerciseFactory;
import de.tum.cit.aet.artemis.modeling.util.ModelingExerciseUtilService;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;
import de.tum.cit.aet.artemis.quiz.dto.exercise.QuizExerciseCreateDTO;
import de.tum.cit.aet.artemis.quiz.dto.exercise.QuizExerciseDetailsDTO;
import de.tum.cit.aet.artemis.quiz.dto.exercise.UpdateQuizExerciseDTO;
import de.tum.cit.aet.artemis.quiz.service.QuizExerciseImportService;
import de.tum.cit.aet.artemis.quiz.test_repository.QuizExerciseTestRepository;
import de.tum.cit.aet.artemis.quiz.util.QuizExerciseFactory;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.dto.ImportTextExerciseDTO;
import de.tum.cit.aet.artemis.text.dto.TextExerciseResponseDTO;
import de.tum.cit.aet.artemis.text.dto.UpdateTextExerciseDTO;
import de.tum.cit.aet.artemis.text.util.TextExerciseFactory;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

/**
 * An instructor adds an exercise to a variant group or removes it through {@code PUT courses/{courseId}/exercises/{exerciseId}/variant-group}, which rejects
 * exam exercises, exercises of another course and quizzes that are not in individual mode, and stamps the group's timeline onto the exercise. These tests pin
 * that no other way of storing an exercise changes the membership: not a creation request, not an import, not a copy built from a loaded source exercise and
 * not an update, even when the request body names a group. The import dialogs post back the exercise they loaded, which carries the group of its source,
 * so every request below sends a group reference the way the client does.
 */
class ExerciseVariantGroupMembershipIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "exvargrpmember";

    private static final ZonedDateTime RELEASE = ZonedDateTime.now().plusDays(1).truncatedTo(ChronoUnit.MILLIS);

    private static final ZonedDateTime DUE = RELEASE.plusDays(6);

    private static final ZonedDateTime ASSESSMENT_DUE = DUE.plusDays(1);

    @Autowired
    private RequestUtilService request;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ModelingExerciseUtilService modelingExerciseUtilService;

    @Autowired
    private FileUploadExerciseUtilService fileUploadExerciseUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private ExerciseVariantGroupRepository exerciseVariantGroupRepository;

    @Autowired
    private ExerciseTestRepository exerciseRepository;

    @Autowired
    private QuizExerciseTestRepository quizExerciseRepository;

    @Autowired
    private QuizExerciseImportService quizExerciseImportService;

    /** The course that owns {@link #group}. */
    private Course course;

    /** A second course of the same users, the target of the cross-course requests. */
    private Course otherCourse;

    private ExerciseVariantGroup group;

    @BeforeEach
    void setup() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);
        course = courseUtilService.createEnrolledCourse(TEST_PREFIX);
        otherCourse = courseUtilService.createEnrolledCourse(TEST_PREFIX);
        group = createGroup("Loop variants");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testNewExerciseIsNeverStoredAsGroupMember() {
        TextExercise exercise = TextExerciseFactory.generateTextExercise(RELEASE, DUE, ASSESSMENT_DUE, course);
        exercise.setExerciseVariantGroup(group);

        Exercise saved = exerciseRepository.save(exercise);

        assertThat(saved.getExerciseVariantGroup()).as("the saved instance does not claim a membership the database does not have").isNull();
        assertNotInAnyGroup(saved.getId());
        assertGroupMembers(group);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testSavingAnExistingMemberKeepsItsGroup() throws Exception {
        TextExercise member = groupedTextExercise();
        Exercise loaded = exerciseRepository.findByIdElseThrow(member.getId());
        loaded.setTitle("Renamed member");

        exerciseRepository.save(loaded);

        assertGroupMembers(group, member);
    }

    @ParameterizedTest(name = "{displayName} [into the source course: {0}]")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testCreateTextExerciseIgnoresVariantGroupInBody(boolean intoSourceCourse) throws Exception {
        TextExercise member = groupedTextExercise();
        TextExercise newExercise = TextExerciseFactory.generateTextExercise(RELEASE, DUE, ASSESSMENT_DUE, target(intoSourceCourse));
        newExercise.setChannelName("text-variant-create");

        var created = request.postWithResponseBody("/api/text/text-exercises", withVariantGroup(UpdateTextExerciseDTO.of(newExercise)), TextExerciseResponseDTO.class,
                HttpStatus.CREATED);

        assertNotInAnyGroup(created.id());
        assertGroupMembers(group, member);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testCreateExamExerciseIgnoresVariantGroupInBody() throws Exception {
        TextExercise member = groupedTextExercise();
        ExerciseGroup exerciseGroup = examUtilService.createAndSaveActiveExerciseGroup(course, true);
        TextExercise newExercise = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup);

        var created = request.postWithResponseBody("/api/text/text-exercises", withVariantGroup(UpdateTextExerciseDTO.of(newExercise)), TextExerciseResponseDTO.class,
                HttpStatus.CREATED);

        assertNotInAnyGroup(created.id());
        assertGroupMembers(group, member);
    }

    @ParameterizedTest(name = "{displayName} [into the source course: {0}]")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testImportTextExerciseLeavesCopyOutOfSourceGroup(boolean intoSourceCourse) throws Exception {
        TextExercise source = groupedTextExercise();
        long sourceId = source.getId();
        // The exercise the import dialog posts back: the loaded source with the target course and the edited values.
        source.setCourse(target(intoSourceCourse));
        source.setTitle("Imported text variant");
        source.setChannelName("text-variant-import");

        var imported = request.postWithResponseBody("/api/text/text-exercises/import?sourceExerciseId=" + sourceId, withVariantGroup(ImportTextExerciseDTO.of(source)),
                TextExerciseResponseDTO.class, HttpStatus.CREATED);

        assertNotInAnyGroup(imported.id());
        assertGroupMemberIds(group, sourceId);
    }

    @ParameterizedTest(name = "{displayName} [into the source course: {0}]")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testCreateModelingExerciseIgnoresVariantGroupInBody(boolean intoSourceCourse) throws Exception {
        TextExercise member = groupedTextExercise();
        ModelingExercise newExercise = ModelingExerciseFactory.generateModelingExercise(RELEASE, DUE, ASSESSMENT_DUE, DiagramType.ClassDiagram, target(intoSourceCourse));
        newExercise.setChannelName("modeling-variant-create");

        var created = request.postWithResponseBody("/api/modeling/modeling-exercises", withVariantGroup(UpdateModelingExerciseDTO.of(newExercise)),
                ModelingExerciseResponseDTO.class, HttpStatus.CREATED);

        assertNotInAnyGroup(created.id());
        assertGroupMembers(group, member);
    }

    @ParameterizedTest(name = "{displayName} [into the source course: {0}]")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testImportModelingExerciseLeavesCopyOutOfSourceGroup(boolean intoSourceCourse) throws Exception {
        ModelingExercise source = modelingExerciseUtilService.addModelingExercise(course, RELEASE, null, DUE, ASSESSMENT_DUE);
        assign(source, group);
        long sourceId = source.getId();
        source.setCourse(target(intoSourceCourse));
        source.setTitle("Imported modeling variant");
        source.setChannelName("modeling-variant-import");

        var imported = request.postWithResponseBody("/api/modeling/modeling-exercises/import?sourceExerciseId=" + sourceId, withVariantGroup(ImportModelingExerciseDTO.of(source)),
                ModelingExerciseResponseDTO.class, HttpStatus.CREATED);

        assertNotInAnyGroup(imported.id());
        assertGroupMemberIds(group, sourceId);
    }

    @ParameterizedTest(name = "{displayName} [into the source course: {0}]")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testCreateFileUploadExerciseIgnoresVariantGroupInBody(boolean intoSourceCourse) throws Exception {
        TextExercise member = groupedTextExercise();
        FileUploadExercise newExercise = FileUploadExerciseFactory.generateFileUploadExercise(RELEASE, DUE, ASSESSMENT_DUE, "pdf", target(intoSourceCourse));
        newExercise.setChannelName("upload-variant-create");

        var created = request.postWithResponseBody("/api/fileupload/file-upload-exercises", withVariantGroup(fileUploadInput(newExercise, target(intoSourceCourse))),
                FileUploadExerciseDTO.class, HttpStatus.CREATED);

        assertNotInAnyGroup(created.id());
        assertGroupMembers(group, member);
    }

    @ParameterizedTest(name = "{displayName} [into the source course: {0}]")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testImportFileUploadExerciseLeavesCopyOutOfSourceGroup(boolean intoSourceCourse) throws Exception {
        FileUploadExercise source = fileUploadExerciseUtilService.addFileUploadExercise(course, RELEASE, null, DUE, ASSESSMENT_DUE);
        assign(source, group);
        source.setTitle("Imported upload variant");
        source.setChannelName("upload-variant-import");

        var imported = request.postWithResponseBody("/api/fileupload/file-upload-exercises/import?sourceId=" + source.getId(),
                withVariantGroup(fileUploadInput(source, target(intoSourceCourse))), FileUploadExerciseDTO.class, HttpStatus.CREATED);

        assertNotInAnyGroup(imported.id());
        assertGroupMemberIds(group, source.getId());
    }

    /**
     * The quiz import dialog creates the copy through the creation endpoint, so this covers the quiz import of the client as well. The quiz is synchronized,
     * which could not join a group at all: its single shared run cannot follow a per-student group timeline.
     */
    @ParameterizedTest(name = "{displayName} [into the source course: {0}]")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testCreateSynchronizedQuizIgnoresVariantGroupInBody(boolean intoSourceCourse) throws Exception {
        TextExercise member = groupedTextExercise();
        Course target = target(intoSourceCourse);
        QuizExercise newQuiz = quizWithOneQuestion(target, QuizMode.SYNCHRONIZED);
        newQuiz.setChannelName("quiz-variant-create");

        var created = request.postWithMultipartFiles("/api/quiz/courses/" + target.getId() + "/quiz-exercises", withVariantGroup(QuizExerciseCreateDTO.of(newQuiz)), "exercise",
                null, QuizExerciseDetailsDTO.class, HttpStatus.CREATED);

        assertNotInAnyGroup(created.quizExercise().id());
        assertGroupMembers(group, member);
    }

    /**
     * The quiz variant generation does not build a fresh exercise for its copy: it hands the import a loaded instance of the source exercise, which carries
     * the source's group. The copy still has to start outside of it, whatever placement the instructor picked afterwards.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testImportingQuizFromLoadedGroupMemberLeavesCopyOutOfGroup() throws Exception {
        QuizExercise source = exerciseRepository.save(quizWithOneQuestion(course, QuizMode.INDIVIDUAL));
        assign(source, group);
        // Two separately loaded instances, the way the quiz variant provisioning calls the import.
        QuizExercise copy = quizExerciseRepository.findWithEagerQuestionsAndCompetenciesAndBatchesAndGradingCriteriaById(source.getId()).orElseThrow();
        QuizExercise importSource = quizExerciseRepository.findWithEagerQuestionsAndCompetenciesAndBatchesAndGradingCriteriaById(source.getId()).orElseThrow();
        assertThat(copy.getExerciseVariantGroup()).as("precondition: the loaded instance carries the group").isNotNull();
        copy.setTitle("Quiz variant");

        QuizExercise imported = quizExerciseImportService.importQuizExercise(copy, importSource, null);

        assertThat(imported.getId()).isNotEqualTo(source.getId());
        assertNotInAnyGroup(imported.getId());
        assertGroupMemberIds(group, source.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCourseMaterialImportLeavesCopiesOutOfSourceGroup() throws Exception {
        TextExercise source = groupedTextExercise();
        var options = new CourseMaterialImportOptionsDTO(course.getId(), true, false, false, false, false, false);

        request.postWithResponseBody("/api/course/courses/" + otherCourse.getId() + "/import-material", options, CourseMaterialImportResultDTO.class, HttpStatus.OK);

        Exercise copy = exerciseRepository.findAllExercisesByCourseId(otherCourse.getId()).stream().filter(exercise -> source.getTitle().equals(exercise.getTitle())).findFirst()
                .orElseThrow();
        assertNotInAnyGroup(copy.getId());
        assertGroupMembers(group, source);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testUpdateTextExerciseKeepsMembership() throws Exception {
        TextExercise member = groupedTextExercise();
        ExerciseVariantGroup anotherGroup = createGroup("Recursion variants");
        member.setTitle("Renamed text member");

        request.putWithResponseBody("/api/text/text-exercises", withVariantGroup(UpdateTextExerciseDTO.of(member), anotherGroup), TextExerciseResponseDTO.class, HttpStatus.OK);
        request.putWithResponseBody("/api/text/text-exercises", withoutVariantGroup(UpdateTextExerciseDTO.of(member)), TextExerciseResponseDTO.class, HttpStatus.OK);

        assertGroupMembers(group, member);
        assertGroupMembers(anotherGroup);
        assertThat(exerciseRepository.findByIdElseThrow(member.getId()).getTitle()).as("the rest of the update applies").isEqualTo("Renamed text member");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testUpdateModelingExerciseKeepsMembership() throws Exception {
        ModelingExercise member = modelingExerciseUtilService.addModelingExercise(course, RELEASE, null, DUE, ASSESSMENT_DUE);
        assign(member, group);
        ExerciseVariantGroup anotherGroup = createGroup("Recursion variants");

        request.putWithResponseBody("/api/modeling/modeling-exercises", withVariantGroup(UpdateModelingExerciseDTO.of(member), anotherGroup), ModelingExerciseResponseDTO.class,
                HttpStatus.OK);
        request.putWithResponseBody("/api/modeling/modeling-exercises", withoutVariantGroup(UpdateModelingExerciseDTO.of(member)), ModelingExerciseResponseDTO.class,
                HttpStatus.OK);

        assertGroupMembers(group, member);
        assertGroupMembers(anotherGroup);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testUpdateFileUploadExerciseKeepsMembership() throws Exception {
        FileUploadExercise member = fileUploadExerciseUtilService.addFileUploadExercise(course, RELEASE, null, DUE, ASSESSMENT_DUE);
        assign(member, group);
        ExerciseVariantGroup anotherGroup = createGroup("Recursion variants");
        String url = "/api/fileupload/file-upload-exercises/" + member.getId();

        request.putWithResponseBody(url, withVariantGroup(UpdateFileUploadExerciseDTO.of(member), anotherGroup), FileUploadExerciseDTO.class, HttpStatus.OK);
        request.putWithResponseBody(url, withoutVariantGroup(UpdateFileUploadExerciseDTO.of(member)), FileUploadExerciseDTO.class, HttpStatus.OK);

        assertGroupMembers(group, member);
        assertGroupMembers(anotherGroup);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testUpdateQuizExerciseKeepsMembership() throws Exception {
        QuizExercise member = exerciseRepository.save(quizWithOneQuestion(course, QuizMode.INDIVIDUAL));
        assign(member, group);
        ExerciseVariantGroup anotherGroup = createGroup("Recursion variants");
        QuizExercise loaded = quizExerciseRepository.findWithEagerQuestionsAndCompetenciesAndBatchesAndGradingCriteriaById(member.getId()).orElseThrow();

        updateQuiz(member.getId(), withVariantGroup(UpdateQuizExerciseDTO.of(loaded), anotherGroup));
        updateQuiz(member.getId(), withoutVariantGroup(UpdateQuizExerciseDTO.of(loaded)));

        assertGroupMembers(group, member);
        assertGroupMembers(anotherGroup);
    }

    private Course target(boolean intoSourceCourse) {
        return intoSourceCourse ? course : otherCourse;
    }

    private ExerciseVariantGroup createGroup(String title) {
        ExerciseVariantGroup newGroup = new ExerciseVariantGroup();
        newGroup.setTitle(title);
        newGroup.setMaxPoints(10.0);
        newGroup.setCourse(course);
        return exerciseVariantGroupRepository.save(newGroup);
    }

    /** A text exercise of the course that joined {@link #group} through the guarded endpoint. */
    private TextExercise groupedTextExercise() throws Exception {
        TextExercise member = textExerciseUtilService.createIndividualTextExercise(course, RELEASE, DUE, ASSESSMENT_DUE);
        assign(member, group);
        return member;
    }

    private static QuizExercise quizWithOneQuestion(Course course, QuizMode quizMode) {
        QuizExercise quiz = QuizExerciseFactory.generateQuizExercise(RELEASE, DUE, quizMode, course);
        quiz.addQuestion(QuizExerciseFactory.createMultipleChoiceQuestion());
        quiz.setMaxPoints(quiz.getOverallQuizPoints());
        return quiz;
    }

    /** Joins the group through the guarded endpoint, the way an instructor groups an exercise. */
    private void assign(Exercise exercise, ExerciseVariantGroup targetGroup) throws Exception {
        request.put("/api/exercise/courses/" + course.getId() + "/exercises/" + exercise.getId() + "/variant-group", new ExerciseVariantGroupAssignmentDTO(targetGroup.getId()),
                HttpStatus.OK);
        assertThat(exerciseVariantGroupRepository.findByExerciseId(exercise.getId())).as("precondition: the endpoint added the exercise to the group")
                .map(ExerciseVariantGroup::getId).contains(targetGroup.getId());
    }

    /** The request body with a reference to {@link #group}, as the client sends it after loading a member of that group. */
    private ObjectNode withVariantGroup(Object requestBody) {
        return withVariantGroup(requestBody, group);
    }

    private ObjectNode withVariantGroup(Object requestBody, ExerciseVariantGroup variantGroup) {
        ObjectNode body = request.getObjectMapper().valueToTree(requestBody);
        body.putObject("exerciseVariantGroup").put("id", variantGroup.getId()).put("title", variantGroup.getTitle()).put("maxPoints", variantGroup.getMaxPoints());
        return body;
    }

    /** The request body of a client that cleared the group on its copy of the exercise. */
    private ObjectNode withoutVariantGroup(Object requestBody) {
        ObjectNode body = request.getObjectMapper().valueToTree(requestBody);
        body.putNull("exerciseVariantGroup");
        return body;
    }

    private void updateQuiz(long quizId, ObjectNode body) throws Exception {
        var builder = MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/quiz/quiz-exercises/" + quizId);
        builder.file(new MockMultipartFile("exercise", "", MediaType.APPLICATION_JSON_VALUE, request.getObjectMapper().writeValueAsBytes(body)));
        request.performMvcRequest(builder).andExpect(status().isOk());
        request.restoreSecurityContext();
    }

    private static FileUploadExerciseInputDTO fileUploadInput(FileUploadExercise exercise, Course target) {
        return new FileUploadExerciseInputDTO(null, exercise.getTitle(), exercise.getChannelName(), exercise.getShortName(), exercise.getProblemStatement(), Set.of(),
                exercise.getDifficulty(), exercise.getMaxPoints(), exercise.getBonusPoints(), exercise.getIncludedInOverallScore(), exercise.getMode(), null,
                exercise.getAllowComplaintsForAutomaticAssessments(), exercise.getPresentationScoreEnabled(), exercise.getSecondCorrectionEnabled(),
                exercise.getGradingInstructions(), exercise.getReleaseDate(), exercise.getStartDate(), exercise.getDueDate(), exercise.getAssessmentDueDate(),
                exercise.getExampleSolutionPublicationDate(), exercise.getExampleSolution(), exercise.getFilePattern(), target.getId(), null, null, null, null);
    }

    private void assertNotInAnyGroup(long exerciseId) {
        assertThat(exerciseVariantGroupRepository.findByExerciseId(exerciseId)).as("exercise %d must not be a member of a variant group", exerciseId).isEmpty();
    }

    private void assertGroupMembers(ExerciseVariantGroup variantGroup, Exercise... members) {
        assertGroupMemberIds(variantGroup, Arrays.stream(members).mapToLong(Exercise::getId).toArray());
    }

    private void assertGroupMemberIds(ExerciseVariantGroup variantGroup, long... memberIds) {
        var loaded = exerciseVariantGroupRepository.findByIdAndCourseIdElseThrow(variantGroup.getId(), course.getId());
        assertThat(loaded.getExercises()).as("members of group %s", variantGroup.getTitle()).extracting(Exercise::getId)
                .containsExactlyInAnyOrder(Arrays.stream(memberIds).boxed().toArray(Long[]::new));
    }
}
