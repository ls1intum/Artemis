package de.tum.cit.aet.artemis.exercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.core.util.RequestUtilService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVariantGroup;
import de.tum.cit.aet.artemis.exercise.dto.CreateExerciseVariantGroupDTO;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseDetailsDTO;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseProblemStatementDTO;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseVariantGroupAssignmentDTO;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseVariantGroupDTO;
import de.tum.cit.aet.artemis.exercise.dto.UpdateExerciseVariantGroupDTO;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseTestRepository;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVariantGroupRepository;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVersionTestRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.fileupload.dto.FileUploadExerciseDTO;
import de.tum.cit.aet.artemis.fileupload.dto.UpdateFileUploadExerciseDTO;
import de.tum.cit.aet.artemis.fileupload.util.FileUploadExerciseUtilService;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.dto.ModelingExerciseResponseDTO;
import de.tum.cit.aet.artemis.modeling.dto.UpdateModelingExerciseDTO;
import de.tum.cit.aet.artemis.modeling.util.ModelingExerciseUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseTimelineUpdateDTO;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.quiz.domain.QuizBatch;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;
import de.tum.cit.aet.artemis.quiz.dto.exercise.QuizExerciseWithQuestionsDTO;
import de.tum.cit.aet.artemis.quiz.util.QuizExerciseFactory;
import de.tum.cit.aet.artemis.quiz.util.QuizExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentBatchTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.dto.TextExerciseResponseDTO;
import de.tum.cit.aet.artemis.text.dto.UpdateTextExerciseDTO;
import de.tum.cit.aet.artemis.text.util.TextExerciseFactory;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

class ExerciseVariantGroupIntegrationTest extends AbstractSpringIntegrationIndependentBatchTest {

    private static final String TEST_PREFIX = "exvargrpinteg";

    /** Title an update request sets alongside the (rejected) timeline change, to prove the rest of the update still lands. */
    private static final String UPDATED_TITLE = "Renamed variant";

    @Autowired
    private RequestUtilService request;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private ExerciseVariantGroupRepository exerciseVariantGroupRepository;

    @Autowired
    private ExerciseTestRepository exerciseRepository;

    @Autowired
    private CourseTestRepository courseRepository;

    @Autowired
    private ExerciseVersionTestRepository exerciseVersionRepository;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ModelingExerciseUtilService modelingExerciseUtilService;

    @Autowired
    private FileUploadExerciseUtilService fileUploadExerciseUtilService;

    @Autowired
    private QuizExerciseUtilService quizExerciseUtilService;

    private Course course;

    private TextExercise exercise;

    @BeforeEach
    void setup() {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 1, 1);
        course = textExerciseUtilService.addEnrolledCourseWithOneReleasedTextExercise("Text", TEST_PREFIX);
        exercise = ExerciseUtilService.getFirstExerciseWithType(course, TextExercise.class);
    }

    private String groupsUrl() {
        return "/api/exercise/courses/" + course.getId() + "/exercise-variant-groups";
    }

    private CreateExerciseVariantGroupDTO sampleCreateDTO() {
        return new CreateExerciseVariantGroupDTO("Loop variants", 100.0, null, null, null, null, null);
    }

    private ExerciseVariantGroupDTO createGroupAsEditor() throws Exception {
        return request.postWithResponseBody(groupsUrl(), sampleCreateDTO(), ExerciseVariantGroupDTO.class, HttpStatus.CREATED);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testCreateExerciseVariantGroup() throws Exception {
        ExerciseVariantGroupDTO created = createGroupAsEditor();

        assertThat(created.id()).isNotNull();
        assertThat(created.title()).isEqualTo("Loop variants");
        assertThat(created.maxPoints()).isEqualTo(100.0);
        // The group is persisted in the owning course.
        assertThat(exerciseVariantGroupRepository.findAllByCourseId(course.getId())).extracting(ExerciseVariantGroup::getId).containsExactly(created.id());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testCourseWithExercisesSerializesVariantGroupForCap() throws Exception {
        ExerciseVariantGroupDTO created = createGroupAsEditor();
        String assignUrl = "/api/exercise/courses/" + course.getId() + "/exercises/" + exercise.getId() + "/variant-group";
        request.put(assignUrl, new ExerciseVariantGroupAssignmentDTO(created.id()), HttpStatus.OK);

        // The course-with-exercises endpoint (used by the scores management view) must serialize each exercise's variant
        // group including its maxPoints, so the client can cap the combined points of a group's variants at that maxPoints.
        // With open-in-view disabled this only works because the fetch graph eagerly loads the association.
        Course loaded = request.get("/api/course/courses/" + course.getId() + "/with-exercises", HttpStatus.OK, Course.class);
        Exercise loadedExercise = loaded.getExercises().stream().filter(candidate -> candidate.getId().equals(exercise.getId())).findFirst().orElseThrow();
        assertThat(loadedExercise.getExerciseVariantGroup()).isNotNull();
        assertThat(loadedExercise.getExerciseVariantGroup().getMaxPoints()).isEqualTo(100.0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testCreateExerciseVariantGroup_negativeMaxPointsBadRequest() throws Exception {
        CreateExerciseVariantGroupDTO negativeMaxPoints = new CreateExerciseVariantGroupDTO("Loop variants", -5.0, null, null, null, null, null);
        request.postWithResponseBody(groupsUrl(), negativeMaxPoints, ExerciseVariantGroupDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testCreateExerciseVariantGroup_titleTooLongBadRequest() throws Exception {
        // The title column is varchar(255), so a longer title must fail validation instead of reaching persistence.
        CreateExerciseVariantGroupDTO tooLongTitle = new CreateExerciseVariantGroupDTO("x".repeat(256), null, null, null, null, null, null);
        request.postWithResponseBody(groupsUrl(), tooLongTitle, ExerciseVariantGroupDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testUpdateExerciseVariantGroup_titleTooLongBadRequest() throws Exception {
        ExerciseVariantGroupDTO created = createGroupAsEditor();
        UpdateExerciseVariantGroupDTO tooLongTitle = new UpdateExerciseVariantGroupDTO(created.id(), "x".repeat(256), null, null, null, null, null, null);
        request.putWithResponseBody(groupsUrl() + "/" + created.id(), tooLongTitle, ExerciseVariantGroupDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testCreateExerciseVariantGroup_exampleSolutionBeforeDueDateAllowed() throws Exception {
        // A group has no IncludedInOverallScore, so (mirroring the NOT_INCLUDED exercise rule) it must accept an example
        // solution publication date before the due date. Each member exercise still re-validates its own timeline.
        ZonedDateTime due = ZonedDateTime.now().plusDays(7).truncatedTo(ChronoUnit.MILLIS);
        ZonedDateTime exampleSolutionBeforeDue = ZonedDateTime.now().plusDays(1).truncatedTo(ChronoUnit.MILLIS);
        CreateExerciseVariantGroupDTO createDTO = new CreateExerciseVariantGroupDTO("Early solution variants", null, null, null, due, null, exampleSolutionBeforeDue);
        request.postWithResponseBody(groupsUrl(), createDTO, ExerciseVariantGroupDTO.class, HttpStatus.CREATED);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCreateExerciseVariantGroup_studentForbidden() throws Exception {
        request.postWithResponseBody(groupsUrl(), sampleCreateDTO(), ExerciseVariantGroupDTO.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testCreateExerciseVariantGroup_tutorForbidden() throws Exception {
        request.postWithResponseBody(groupsUrl(), sampleCreateDTO(), ExerciseVariantGroupDTO.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testUpdateExerciseVariantGroup() throws Exception {
        ExerciseVariantGroupDTO created = createGroupAsEditor();
        UpdateExerciseVariantGroupDTO updateDTO = new UpdateExerciseVariantGroupDTO(created.id(), "Renamed", 50.0, null, null, null, null, null);

        ExerciseVariantGroupDTO updated = request.putWithResponseBody(groupsUrl() + "/" + created.id(), updateDTO, ExerciseVariantGroupDTO.class, HttpStatus.OK);

        assertThat(updated.title()).isEqualTo("Renamed");
        assertThat(updated.maxPoints()).isEqualTo(50.0);
        // The group is still owned by the same course: updating its settings cannot move it (course is immutable).
        assertThat(exerciseVariantGroupRepository.findAllByCourseId(course.getId())).extracting(ExerciseVariantGroup::getId).containsExactly(created.id());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testUpdateExerciseVariantGroup_idMismatchBadRequest() throws Exception {
        ExerciseVariantGroupDTO created = createGroupAsEditor();
        UpdateExerciseVariantGroupDTO mismatched = new UpdateExerciseVariantGroupDTO(created.id() + 1, "Renamed", null, null, null, null, null, null);
        request.putWithResponseBody(groupsUrl() + "/" + created.id(), mismatched, ExerciseVariantGroupDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testGetExerciseVariantGroupsForCourse() throws Exception {
        ExerciseVariantGroupDTO created = createGroupAsEditor();

        List<ExerciseVariantGroupDTO> groups = request.getList(groupsUrl(), HttpStatus.OK, ExerciseVariantGroupDTO.class);

        assertThat(groups).extracting(ExerciseVariantGroupDTO::id).containsExactly(created.id());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testGetExerciseVariantGroup() throws Exception {
        ExerciseVariantGroupDTO created = createGroupAsEditor();

        ExerciseVariantGroupDTO fetched = request.get(groupsUrl() + "/" + created.id(), HttpStatus.OK, ExerciseVariantGroupDTO.class);

        assertThat(fetched.id()).isEqualTo(created.id());
        assertThat(fetched.title()).isEqualTo("Loop variants");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testGetExerciseVariantGroup_unknownNotFound() throws Exception {
        request.get(groupsUrl() + "/" + Long.MAX_VALUE, HttpStatus.NOT_FOUND, ExerciseVariantGroupDTO.class);
    }

    private String problemStatementsUrl(long groupId) {
        return groupsUrl() + "/" + groupId + "/problem-statements";
    }

    /**
     * The group detail page must render every member's preview from a single request. This asserts the batch endpoint
     * returns the problem statements of all members at once, so the client never fans out one detail request per member.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testGetGroupProblemStatements_returnsAllMembersInOneRequest() throws Exception {
        ExerciseVariantGroupDTO created = createGroupAsEditor();
        TextExercise second = addReleasedTextExerciseToCourse("Second variant");
        assignToGroup(exercise.getId(), created.id());
        assignToGroup(second.getId(), created.id());

        List<ExerciseProblemStatementDTO> statements = request.getList(problemStatementsUrl(created.id()), HttpStatus.OK, ExerciseProblemStatementDTO.class);

        assertThat(statements).extracting(ExerciseProblemStatementDTO::exerciseId).containsExactlyInAnyOrder(exercise.getId(), second.getId());
        assertThat(statements).extracting(ExerciseProblemStatementDTO::problemStatement).allMatch("Problem Statement"::equals);
    }

    /** A student may read the previews, but only for members they are allowed to see: an unreleased member is excluded. */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetGroupProblemStatements_excludesUnreleasedMemberForStudent() throws Exception {
        // The released text exercise (visible) and an unreleased one (release date in the future, hidden) share a group.
        ZonedDateTime future = ZonedDateTime.now().plusDays(5).truncatedTo(ChronoUnit.MILLIS);
        TextExercise unreleased = TextExerciseFactory.generateTextExercise(future, future.plusDays(5), future.plusDays(10), course);
        unreleased.setTitle("Unreleased variant");
        unreleased = exerciseRepository.save(unreleased);
        long groupId = createGroupInCourseWithMembers("Loop variants", exercise, unreleased);

        List<ExerciseProblemStatementDTO> statements = request.getList(problemStatementsUrl(groupId), HttpStatus.OK, ExerciseProblemStatementDTO.class);

        assertThat(statements).extracting(ExerciseProblemStatementDTO::exerciseId).containsExactly(exercise.getId());
        assertThat(statements).extracting(ExerciseProblemStatementDTO::problemStatement).containsExactly("Problem Statement");
    }

    /** Adds a second, already-released text exercise (with a problem statement) to the test course. */
    private TextExercise addReleasedTextExerciseToCourse(String title) {
        ZonedDateTime release = ZonedDateTime.now().minusDays(1).truncatedTo(ChronoUnit.MILLIS);
        TextExercise textExercise = TextExerciseFactory.generateTextExercise(release, release.plusDays(7), release.plusDays(14), course);
        textExercise.setTitle(title);
        return exerciseRepository.save(textExercise);
    }

    /**
     * Creates a variant group owned by the test course and assigns the given members to it, entirely via the repositories
     * so it works under a student mock user (which may not call the editor-only create/assign endpoints).
     */
    private long createGroupInCourseWithMembers(String title, Exercise... members) {
        ExerciseVariantGroup group = new ExerciseVariantGroup();
        group.setTitle(title);
        group.setMaxPoints(100.0);
        group = exerciseVariantGroupRepository.save(group);
        // The course owns the unidirectional collection, so attach the group to the course to write its FK.
        Course owningCourse = courseRepository.findWithEagerExerciseVariantGroupsByIdElseThrow(course.getId());
        owningCourse.addExerciseVariantGroup(group);
        courseRepository.save(owningCourse);
        for (Exercise member : members) {
            member.setExerciseVariantGroup(group);
            exerciseRepository.save(member);
        }
        return group.getId();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteExerciseVariantGroup_instructorAllowed() throws Exception {
        ExerciseVariantGroupDTO created = request.postWithResponseBody(groupsUrl(), sampleCreateDTO(), ExerciseVariantGroupDTO.class, HttpStatus.CREATED);

        request.delete(groupsUrl() + "/" + created.id(), HttpStatus.OK);

        assertThat(exerciseVariantGroupRepository.findById(created.id())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteExerciseVariantGroup_withMembersDetachesExercises() throws Exception {
        ExerciseVariantGroupDTO created = request.postWithResponseBody(groupsUrl(), sampleCreateDTO(), ExerciseVariantGroupDTO.class, HttpStatus.CREATED);
        String assignUrl = "/api/exercise/courses/" + course.getId() + "/exercises/" + exercise.getId() + "/variant-group";
        request.put(assignUrl, new ExerciseVariantGroupAssignmentDTO(created.id()), HttpStatus.OK);

        request.delete(groupsUrl() + "/" + created.id(), HttpStatus.OK);

        // The group is gone, but its member exercise survives and is simply ungrouped.
        assertThat(exerciseVariantGroupRepository.findById(created.id())).isEmpty();
        Exercise survivor = exerciseRepository.findByIdElseThrow(exercise.getId());
        assertThat(survivor.getExerciseVariantGroup()).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testDeleteExerciseVariantGroup_editorForbidden() throws Exception {
        ExerciseVariantGroupDTO created = createGroupAsEditor();
        request.delete(groupsUrl() + "/" + created.id(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testAssignAndUnassignExerciseToGroup() throws Exception {
        ExerciseVariantGroupDTO created = createGroupAsEditor();
        String assignUrl = "/api/exercise/courses/" + course.getId() + "/exercises/" + exercise.getId() + "/variant-group";

        request.put(assignUrl, new ExerciseVariantGroupAssignmentDTO(created.id()), HttpStatus.OK);
        assertThat(request.get(groupsUrl() + "/" + created.id(), HttpStatus.OK, ExerciseVariantGroupDTO.class).exerciseIds()).containsExactly(exercise.getId());

        request.put(assignUrl, new ExerciseVariantGroupAssignmentDTO(null), HttpStatus.OK);
        // An empty exerciseIds set is omitted from the response (NON_EMPTY serialization), so it deserializes as null.
        assertThat(request.get(groupsUrl() + "/" + created.id(), HttpStatus.OK, ExerciseVariantGroupDTO.class).exerciseIds()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testMoveExerciseBetweenGroupsInSingleRequest() throws Exception {
        ExerciseVariantGroupDTO group1 = createGroupAsEditor();
        ExerciseVariantGroupDTO group2 = createGroupAsEditor();
        String assignUrl = "/api/exercise/courses/" + course.getId() + "/exercises/" + exercise.getId() + "/variant-group";

        request.put(assignUrl, new ExerciseVariantGroupAssignmentDTO(group1.id()), HttpStatus.OK);
        request.put(assignUrl, new ExerciseVariantGroupAssignmentDTO(group2.id()), HttpStatus.OK);

        assertThat(request.get(groupsUrl() + "/" + group1.id(), HttpStatus.OK, ExerciseVariantGroupDTO.class).exerciseIds()).isNullOrEmpty();
        assertThat(request.get(groupsUrl() + "/" + group2.id(), HttpStatus.OK, ExerciseVariantGroupDTO.class).exerciseIds()).containsExactly(exercise.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testAssigningExerciseAdoptsGroupTimeline() throws Exception {
        ZonedDateTime release = ZonedDateTime.now().plusDays(1).truncatedTo(ChronoUnit.MILLIS);
        // Within the exercise's own (pre-existing) assessment due date so adopting it (see below) stays a valid combination.
        ZonedDateTime due = release.plusHours(12);
        CreateExerciseVariantGroupDTO createDTO = new CreateExerciseVariantGroupDTO("Dated variants", null, release, null, due, null, null);
        ExerciseVariantGroupDTO created = request.postWithResponseBody(groupsUrl(), createDTO, ExerciseVariantGroupDTO.class, HttpStatus.CREATED);
        String assignUrl = "/api/exercise/courses/" + course.getId() + "/exercises/" + exercise.getId() + "/variant-group";
        // Re-fetch instead of using the in-memory field: the DB column's millisecond precision can round it differently.
        ZonedDateTime originalAssessmentDueDate = exerciseRepository.findByIdElseThrow(exercise.getId()).getAssessmentDueDate();

        request.put(assignUrl, new ExerciseVariantGroupAssignmentDTO(created.id()), HttpStatus.OK);

        // release/due are copied from the group; the group defined no assessment due date (and had no other members) so it
        // adopts the exercise's own; neither has a start date, so it stays unset.
        Exercise reloaded = exerciseRepository.findByIdElseThrow(exercise.getId());
        assertThat(reloaded.getReleaseDate().toInstant()).isEqualTo(release.toInstant());
        assertThat(reloaded.getDueDate().toInstant()).isEqualTo(due.toInstant());
        assertThat(reloaded.getStartDate()).isNull();
        assertThat(reloaded.getAssessmentDueDate().toInstant()).isEqualTo(originalAssessmentDueDate.toInstant());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testAssigningExerciseToGroupWithDateAlreadySetDoesNotOverwriteGroup() throws Exception {
        // Within the exercise's own pre-existing release/assessment-due dates so the resulting combination stays valid.
        ZonedDateTime due = ZonedDateTime.now().plusHours(36).truncatedTo(ChronoUnit.MILLIS);
        CreateExerciseVariantGroupDTO createDTO = new CreateExerciseVariantGroupDTO("Dated variants", null, null, null, due, null, null);
        ExerciseVariantGroupDTO created = request.postWithResponseBody(groupsUrl(), createDTO, ExerciseVariantGroupDTO.class, HttpStatus.CREATED);
        String assignUrl = "/api/exercise/courses/" + course.getId() + "/exercises/" + exercise.getId() + "/variant-group";

        request.put(assignUrl, new ExerciseVariantGroupAssignmentDTO(created.id()), HttpStatus.OK);

        // The group already defined a due date, so the joining exercise's own (different) due date must not be adopted:
        // the exercise instead takes on the group's due date.
        ExerciseVariantGroupDTO reloadedGroup = request.get(groupsUrl() + "/" + created.id(), HttpStatus.OK, ExerciseVariantGroupDTO.class);
        assertThat(reloadedGroup.dueDate().toInstant()).isEqualTo(due.toInstant());
        Exercise reloadedExercise = exerciseRepository.findByIdElseThrow(exercise.getId());
        assertThat(reloadedExercise.getDueDate().toInstant()).isEqualTo(due.toInstant());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testUpdatingGroupTimelinePropagatesToMembers() throws Exception {
        ExerciseVariantGroupDTO created = createGroupAsEditor();
        String assignUrl = "/api/exercise/courses/" + course.getId() + "/exercises/" + exercise.getId() + "/variant-group";
        request.put(assignUrl, new ExerciseVariantGroupAssignmentDTO(created.id()), HttpStatus.OK);

        ZonedDateTime due = ZonedDateTime.now().plusDays(10).truncatedTo(ChronoUnit.MILLIS);
        UpdateExerciseVariantGroupDTO updateDTO = new UpdateExerciseVariantGroupDTO(created.id(), "Loop variants", 100.0, null, null, due, null, null);
        request.put(groupsUrl() + "/" + created.id(), updateDTO, HttpStatus.OK);

        // Editing the group's timeline re-syncs every member exercise to the new dates.
        Exercise reloaded = exerciseRepository.findByIdElseThrow(exercise.getId());
        assertThat(reloaded.getDueDate().toInstant()).isEqualTo(due.toInstant());
        assertThat(reloaded.getReleaseDate()).isNull();
    }

    /** A group timeline edit must run the same post-update work a member's own endpoint does (scheduling, versioning, ...). */
    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testUpdatingGroupTimelineRunsMemberPostUpdateSideEffects() throws Exception {
        ExerciseVariantGroupDTO created = createGroupAsEditor();
        String assignUrl = "/api/exercise/courses/" + course.getId() + "/exercises/" + exercise.getId() + "/variant-group";
        request.put(assignUrl, new ExerciseVariantGroupAssignmentDTO(created.id()), HttpStatus.OK);
        // Only interactions caused by the group update itself should count, not those from the assignment above.
        reset(instanceMessageSendService);

        ZonedDateTime due = ZonedDateTime.now().plusDays(10).truncatedTo(ChronoUnit.MILLIS);
        UpdateExerciseVariantGroupDTO updateDTO = new UpdateExerciseVariantGroupDTO(created.id(), "Loop variants", 100.0, null, null, due, null, null);
        request.put(groupsUrl() + "/" + created.id(), updateDTO, HttpStatus.OK);

        // The text member's type-specific scheduler is refreshed, exactly as TextExerciseCreationUpdateResource does.
        verify(instanceMessageSendService).sendTextExerciseSchedule(exercise.getId());
        // A version snapshot is recorded (versioning is synchronous under the test profile).
        assertThat(exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(exercise.getId())).isPresent();
    }

    /** A programming member's build-and-test date is per-exercise, not shared, so a group due-date edit must not overwrite it. */
    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testUpdatingGroupTimelineKeepsProgrammingMemberBuildAndTestDate() throws Exception {
        ZonedDateTime release = ZonedDateTime.now().plusDays(1).truncatedTo(ChronoUnit.MILLIS);
        ZonedDateTime due = release.plusDays(6);
        CreateExerciseVariantGroupDTO createDTO = new CreateExerciseVariantGroupDTO("Programming variants", null, release, null, due, null, null);
        ExerciseVariantGroupDTO created = request.postWithResponseBody(groupsUrl(), createDTO, ExerciseVariantGroupDTO.class, HttpStatus.CREATED);
        ProgrammingExercise programmingExercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course);
        ZonedDateTime buildAndTest = due.plusHours(1);
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(buildAndTest);
        exerciseRepository.save(programmingExercise);
        String assignUrl = "/api/exercise/courses/" + course.getId() + "/exercises/" + programmingExercise.getId() + "/variant-group";
        request.put(assignUrl, new ExerciseVariantGroupAssignmentDTO(created.id()), HttpStatus.OK);

        // The group carries no build-and-test date at all, so it cannot clear the member's.
        ProgrammingExercise reloaded = (ProgrammingExercise) exerciseRepository.findByIdElseThrow(programmingExercise.getId());
        assertThat(reloaded.getExerciseVariantGroup()).isNotNull();
        assertThat(reloaded.getBuildAndTestStudentSubmissionsAfterDueDate()).as("the member keeps its own build-and-test date").isNotNull();
        assertThat(reloaded.getDueDate().toInstant()).as("the shared dates are still propagated").isEqualTo(due.toInstant());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testUpdateGroupToMemberInvalidTimeline_badRequestLeavesGroupDatesUnchanged() throws Exception {
        ZonedDateTime release = ZonedDateTime.now().plusDays(1).truncatedTo(ChronoUnit.MILLIS);
        ZonedDateTime due = ZonedDateTime.now().plusDays(7).truncatedTo(ChronoUnit.MILLIS);
        CreateExerciseVariantGroupDTO createDTO = new CreateExerciseVariantGroupDTO("Loop variants", 100.0, release, null, due, null, null);
        ExerciseVariantGroupDTO created = request.postWithResponseBody(groupsUrl(), createDTO, ExerciseVariantGroupDTO.class, HttpStatus.CREATED);
        String assignUrl = "/api/exercise/courses/" + course.getId() + "/exercises/" + exercise.getId() + "/variant-group";
        request.put(assignUrl, new ExerciseVariantGroupAssignmentDTO(created.id()), HttpStatus.OK);
        // Snapshot the group's stored timeline after the member joined, to prove the rejected update leaves it untouched.
        ExerciseVariantGroupDTO before = request.get(groupsUrl() + "/" + created.id(), HttpStatus.OK, ExerciseVariantGroupDTO.class);

        // Valid at group level, but the member text exercise (INCLUDED_COMPLETELY) requires exampleSolution >= dueDate, so
        // applying it must fail with 400 before anything is saved.
        ZonedDateTime exampleSolutionBeforeDue = ZonedDateTime.now().plusDays(3).truncatedTo(ChronoUnit.MILLIS);
        UpdateExerciseVariantGroupDTO invalidUpdate = new UpdateExerciseVariantGroupDTO(created.id(), "Loop variants", 100.0, release, null, due, null, exampleSolutionBeforeDue);
        request.put(groupsUrl() + "/" + created.id(), invalidUpdate, HttpStatus.BAD_REQUEST);

        // The rejected update changed neither the stored group dates nor the member exercise's dates.
        ExerciseVariantGroupDTO after = request.get(groupsUrl() + "/" + created.id(), HttpStatus.OK, ExerciseVariantGroupDTO.class);
        assertThat(after.releaseDate().toInstant()).isEqualTo(before.releaseDate().toInstant());
        assertThat(after.dueDate().toInstant()).isEqualTo(before.dueDate().toInstant());
        assertThat(after.exampleSolutionPublicationDate()).isNull();
        Exercise reloaded = exerciseRepository.findByIdElseThrow(exercise.getId());
        assertThat(reloaded.getDueDate().toInstant()).isEqualTo(due.toInstant());
        assertThat(reloaded.getExampleSolutionPublicationDate()).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testAssignProgrammingExerciseInvalidTimeline_badRequestLeavesExerciseUngrouped() throws Exception {
        ZonedDateTime release = ZonedDateTime.now().plusDays(1).truncatedTo(ChronoUnit.MILLIS);
        ZonedDateTime exampleSolutionBeforeDue = ZonedDateTime.now().plusDays(3).truncatedTo(ChronoUnit.MILLIS);
        ZonedDateTime due = ZonedDateTime.now().plusDays(7).truncatedTo(ChronoUnit.MILLIS);
        // Valid at group level, but the programming exercise (INCLUDED_COMPLETELY) requires exampleSolution >= dueDate. The
        // rejected assignment must not persist the membership (the programming path used to save before validating).
        CreateExerciseVariantGroupDTO createDTO = new CreateExerciseVariantGroupDTO("Programming variants", null, release, null, due, null, exampleSolutionBeforeDue);
        ExerciseVariantGroupDTO created = request.postWithResponseBody(groupsUrl(), createDTO, ExerciseVariantGroupDTO.class, HttpStatus.CREATED);
        ProgrammingExercise programmingExercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course);
        ProgrammingExercise before = (ProgrammingExercise) exerciseRepository.findByIdElseThrow(programmingExercise.getId());
        String assignUrl = "/api/exercise/courses/" + course.getId() + "/exercises/" + programmingExercise.getId() + "/variant-group";

        request.put(assignUrl, new ExerciseVariantGroupAssignmentDTO(created.id()), HttpStatus.BAD_REQUEST);

        ProgrammingExercise reloaded = (ProgrammingExercise) exerciseRepository.findByIdElseThrow(programmingExercise.getId());
        assertThat(reloaded.getExerciseVariantGroup()).isNull();
        assertThat(zonedToInstant(reloaded.getReleaseDate())).isEqualTo(zonedToInstant(before.getReleaseDate()));
        assertThat(zonedToInstant(reloaded.getDueDate())).isEqualTo(zonedToInstant(before.getDueDate()));
        assertThat(reloaded.getExampleSolutionPublicationDate()).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testAssignExerciseFromAnotherCourseBadRequest() throws Exception {
        ExerciseVariantGroupDTO created = createGroupAsEditor();
        Course otherCourse = textExerciseUtilService.addCourseWithOneReleasedTextExercise("Other");
        TextExercise otherExercise = ExerciseUtilService.getFirstExerciseWithType(otherCourse, TextExercise.class);

        // courseId in the path belongs to the group's course, but the exercise belongs to a different course.
        String assignUrl = "/api/exercise/courses/" + course.getId() + "/exercises/" + otherExercise.getId() + "/variant-group";
        request.put(assignUrl, new ExerciseVariantGroupAssignmentDTO(created.id()), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testAssignExamExerciseToGroup_badRequest() throws Exception {
        ExerciseVariantGroupDTO created = createGroupAsEditor();
        // An exam exercise reports the exam's course, so it would otherwise pass the ownership check; it must be rejected.
        Exam exam = examUtilService.addExamWithExerciseGroup(course, true);
        ExerciseGroup examExerciseGroup = exam.getExerciseGroups().getFirst();
        TextExercise examExercise = exerciseRepository.save(TextExerciseFactory.generateTextExerciseForExam(examExerciseGroup));
        String assignUrl = "/api/exercise/courses/" + course.getId() + "/exercises/" + examExercise.getId() + "/variant-group";

        request.put(assignUrl, new ExerciseVariantGroupAssignmentDTO(created.id()), HttpStatus.BAD_REQUEST);

        assertThat(exerciseRepository.findByIdElseThrow(examExercise.getId()).getExerciseVariantGroup()).isNull();
    }

    /**
     * The LAZY {@code exerciseVariantGroup} serializes an unfetched proxy as {@code null} (Hibernate7Module), so a read path
     * that forgets to fetch it fails silently. These tests assert on the serialized HTTP response — a repository call would
     * hand back a proxy and pass even when the JSON is null.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testExerciseEndpointSerializesVariantGroup() throws Exception {
        ExerciseVariantGroupDTO created = createGroupAsEditor();
        assignToGroup(exercise.getId(), created.id());

        Exercise loaded = request.get("/api/exercise/exercises/" + exercise.getId(), HttpStatus.OK, Exercise.class);

        assertThat(loaded.getExerciseVariantGroup()).isNotNull();
        assertThat(loaded.getExerciseVariantGroup().getMaxPoints()).isEqualTo(100.0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testExerciseDetailsEndpointSerializesVariantGroup() throws Exception {
        createGroupAsEditorFor(exercise.getId());

        ExerciseDetailsDTO details = request.get("/api/exercise/exercises/" + exercise.getId() + "/details", HttpStatus.OK, ExerciseDetailsDTO.class);

        assertThat(details.exercise().getExerciseVariantGroup()).isNotNull();
        assertThat(details.exercise().getExerciseVariantGroup().getMaxPoints()).isEqualTo(100.0);
    }

    /** The quiz edit DTO reads the association (maps title/maxPoints/dates off it), so an unfetched proxy would throw here. */
    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testQuizExerciseEndpointSerializesVariantGroup() throws Exception {
        ExerciseVariantGroupDTO created = createGroupAsEditor();
        QuizExercise quiz = addQuizToCourse(QuizMode.INDIVIDUAL);
        assignToGroup(quiz.getId(), created.id());

        QuizExerciseWithQuestionsDTO loaded = request.get("/api/quiz/quiz-exercises/" + quiz.getId(), HttpStatus.OK, QuizExerciseWithQuestionsDTO.class);

        assertThat(loaded.quizExerciseWithoutQuestionsDTO().exerciseVariantGroup()).isNotNull();
        assertThat(loaded.quizExerciseWithoutQuestionsDTO().exerciseVariantGroup().maxPoints()).isEqualTo(100.0);
    }

    /** The programming edit page serializes the entity, so it carries the association only if fetched. */
    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testProgrammingExerciseEndpointSerializesVariantGroup() throws Exception {
        ProgrammingExercise programmingExercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course);
        createGroupAsEditorFor(programmingExercise.getId());

        ProgrammingExercise loaded = request.get("/api/programming/programming-exercises/" + programmingExercise.getId(), HttpStatus.OK, ProgrammingExercise.class);

        assertVariantGroupPresent(loaded);
    }

    /** The modeling exercise edit page is a DTO, so like the text one it must map the group explicitly. */
    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testModelingExerciseEndpointSerializesVariantGroup() throws Exception {
        ModelingExercise modelingExercise = modelingExerciseUtilService.addModelingExerciseToCourse(course);
        createGroupAsEditorFor(modelingExercise.getId());

        ModelingExerciseResponseDTO loaded = request.get("/api/modeling/modeling-exercises/" + modelingExercise.getId(), HttpStatus.OK, ModelingExerciseResponseDTO.class);

        assertThat(loaded.exerciseVariantGroup()).isNotNull();
        assertThat(loaded.exerciseVariantGroup().maxPoints()).isEqualTo(100.0);
        assertThat(loaded.exerciseVariantGroup().title()).isEqualTo("Loop variants");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testFileUploadExerciseEndpointSerializesVariantGroup() throws Exception {
        ZonedDateTime now = ZonedDateTime.now();
        FileUploadExercise fileUploadExercise = fileUploadExerciseUtilService.addFileUploadExercise(course, now.minusDays(1), now, now.plusDays(7), now.plusDays(14));
        createGroupAsEditorFor(fileUploadExercise.getId());

        FileUploadExerciseDTO loaded = request.get("/api/fileupload/file-upload-exercises/" + fileUploadExercise.getId(), HttpStatus.OK, FileUploadExerciseDTO.class);

        assertThat(loaded.exerciseVariantGroup()).isNotNull();
        assertThat(loaded.exerciseVariantGroup().maxPoints()).isEqualTo(100.0);
        assertThat(loaded.exerciseVariantGroup().title()).isEqualTo("Loop variants");
    }

    /**
     * A student starting a grouped quiz must not blow up: this endpoint loads the quiz without fetching the group, so
     * reading it off the proxy after the session closed used to throw {@code LazyInitializationException} (500). The DTO now
     * maps the instructor-facing group to null for students.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testStartParticipationOnGroupedQuizDoesNotFail() throws Exception {
        QuizExercise quiz = quizInGroup();

        // Status only (polymorphic DTO, no Jackson creator): the point is that this used to be a 500.
        request.postWithoutResponseBody("/api/quiz/quiz-exercises/" + quiz.getId() + "/start-participation", null, HttpStatus.OK);
    }

    /** The scores view caps a group at its maxPoints, so every member must carry its group — asserted here for a quiz too. */
    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testCourseWithExercisesSerializesVariantGroupForQuiz() throws Exception {
        QuizExercise quiz = quizInGroup();

        Course loaded = request.get("/api/course/courses/" + course.getId() + "/with-exercises", HttpStatus.OK, Course.class);
        Exercise loadedQuiz = loaded.getExercises().stream().filter(candidate -> candidate.getId().equals(quiz.getId())).findFirst().orElseThrow();

        assertVariantGroupPresent(loadedQuiz);
    }

    /** Adds an individual-mode quiz in a group, via the repositories (the student-facing test may not create groups). */
    private QuizExercise quizInGroup() {
        // Released in the past, so a student is actually allowed to start it.
        ZonedDateTime release = ZonedDateTime.now().minusDays(1).truncatedTo(ChronoUnit.MILLIS);
        ZonedDateTime due = ZonedDateTime.now().plusDays(7).truncatedTo(ChronoUnit.MILLIS);
        QuizExercise quiz = QuizExerciseFactory.generateQuizExercise(release, due, QuizMode.INDIVIDUAL, course);
        QuizExerciseFactory.addQuestionsToQuizExercise(quiz);
        course.addExercises(quiz);

        ExerciseVariantGroup group = new ExerciseVariantGroup();
        group.setTitle("Loop variants");
        group.setMaxPoints(100.0);
        quiz.setExerciseVariantGroup(exerciseVariantGroupRepository.save(group));
        return exerciseRepository.save(quiz);
    }

    /** Compares dates as instants (PostgreSQL stores timestamps as UTC), tolerating unset dates. */
    private static Instant zonedToInstant(@Nullable ZonedDateTime date) {
        return date != null ? date.toInstant() : null;
    }

    /** The association is LAZY, so an unfetched read path serializes it as null rather than throwing — assert it is really there. */
    private static void assertVariantGroupPresent(Exercise loaded) {
        assertThat(loaded.getExerciseVariantGroup()).isNotNull();
        assertThat(loaded.getExerciseVariantGroup().getMaxPoints()).isEqualTo(100.0);
        assertThat(loaded.getExerciseVariantGroup().getTitle()).isEqualTo("Loop variants");
    }

    /** The text exercise edit page is a DTO, so unlike the entity-serializing pages it must map the group explicitly. */
    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testTextExerciseEndpointSerializesVariantGroup() throws Exception {
        createGroupAsEditorFor(exercise.getId());

        TextExerciseResponseDTO loaded = request.get("/api/text/text-exercises/" + exercise.getId(), HttpStatus.OK, TextExerciseResponseDTO.class);

        assertThat(loaded.exerciseVariantGroup()).isNotNull();
        assertThat(loaded.exerciseVariantGroup().maxPoints()).isEqualTo(100.0);
        assertThat(loaded.exerciseVariantGroup().title()).isEqualTo("Loop variants");
    }

    /** Creates a group and assigns the given exercise to it, returning the created group. */
    private ExerciseVariantGroupDTO createGroupAsEditorFor(long exerciseId) throws Exception {
        ExerciseVariantGroupDTO created = createGroupAsEditor();
        assignToGroup(exerciseId, created.id());
        return created;
    }

    private void assignToGroup(long exerciseId, Long groupId) throws Exception {
        request.put("/api/exercise/courses/" + course.getId() + "/exercises/" + exerciseId + "/variant-group", new ExerciseVariantGroupAssignmentDTO(groupId), HttpStatus.OK);
    }

    /** Adds a quiz exercise with the given mode to the test course (separately from the released text exercise). */
    private QuizExercise addQuizToCourse(QuizMode quizMode) {
        ZonedDateTime release = ZonedDateTime.now().plusDays(1).truncatedTo(ChronoUnit.MILLIS);
        ZonedDateTime due = ZonedDateTime.now().plusDays(7).truncatedTo(ChronoUnit.MILLIS);
        QuizExercise quizExercise = QuizExerciseFactory.generateQuizExercise(release, due, quizMode, course);
        QuizExerciseFactory.addQuestionsToQuizExercise(quizExercise);
        course.addExercises(quizExercise);
        return exerciseRepository.save(quizExercise);
    }

    /**
     * A member update endpoint must not let a stale client move a member off its group timeline. Endpoints where dates are
     * incidental overwrite them from the group (so an unrelated edit self-heals and still succeeds); timeline-only endpoints
     * reject the request instead.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testUpdateTextExerciseCannotChangeGroupTimeline() throws Exception {
        GroupTimeline timeline = createGroupWithTimelineFor(exercise.getId());
        TextExercise toUpdate = (TextExercise) exerciseRepository.findByIdElseThrow(exercise.getId());
        applyDifferentTimelineAndTitle(toUpdate);

        request.putWithResponseBody("/api/text/text-exercises", UpdateTextExerciseDTO.of(toUpdate), TextExerciseResponseDTO.class, HttpStatus.OK);

        assertTimelinePinnedToGroupAndTitleUpdated(exercise.getId(), timeline);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testUpdateModelingExerciseCannotChangeGroupTimeline() throws Exception {
        ModelingExercise modelingExercise = modelingExerciseUtilService.addModelingExerciseToCourse(course);
        GroupTimeline timeline = createGroupWithTimelineFor(modelingExercise.getId());
        ModelingExercise toUpdate = (ModelingExercise) exerciseRepository.findByIdElseThrow(modelingExercise.getId());
        applyDifferentTimelineAndTitle(toUpdate);

        request.putWithResponseBody("/api/modeling/modeling-exercises", UpdateModelingExerciseDTO.of(toUpdate), ModelingExerciseResponseDTO.class, HttpStatus.OK);

        assertTimelinePinnedToGroupAndTitleUpdated(modelingExercise.getId(), timeline);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testUpdateFileUploadExerciseCannotChangeGroupTimeline() throws Exception {
        ZonedDateTime now = ZonedDateTime.now();
        FileUploadExercise fileUploadExercise = fileUploadExerciseUtilService.addFileUploadExercise(course, now.plusDays(1), null, now.plusDays(7), now.plusDays(14));
        GroupTimeline timeline = createGroupWithTimelineFor(fileUploadExercise.getId());
        FileUploadExercise toUpdate = (FileUploadExercise) exerciseRepository.findByIdElseThrow(fileUploadExercise.getId());
        applyDifferentTimelineAndTitle(toUpdate);

        request.putWithResponseBody("/api/fileupload/file-upload-exercises/" + fileUploadExercise.getId(), UpdateFileUploadExerciseDTO.of(toUpdate), FileUploadExercise.class,
                HttpStatus.OK);

        assertTimelinePinnedToGroupAndTitleUpdated(fileUploadExercise.getId(), timeline);
    }

    // The programming and quiz update endpoints are covered from their own test classes instead: a full
    // PUT /programming-exercises needs the ProgrammingLanguageFeatureService that only the LocalCI/LocalVC profiles
    // provide (see ProgrammingExerciseResourceTest), and the multipart quiz update needs the richer quiz fixture built by
    // AbstractQuizExerciseIntegrationTest (see QuizExerciseIntegrationTest).

    /** The timeline-only programming endpoint rejects group members. The guard lives in the resource so the service can still propagate group dates onto members. */
    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testUpdateProgrammingExerciseTimelineForGroupMember_badRequest() throws Exception {
        ProgrammingExercise programmingExercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course);
        GroupTimeline timeline = createGroupWithTimelineFor(programmingExercise.getId());
        ProgrammingExercise member = (ProgrammingExercise) exerciseRepository.findByIdElseThrow(programmingExercise.getId());
        ProgrammingExerciseTimelineUpdateDTO timelineUpdate = new ProgrammingExerciseTimelineUpdateDTO(programmingExercise.getId(), timeline.release().plusDays(2), null,
                timeline.due().plusDays(2), member.getAssessmentType(), timeline.assessmentDue().plusDays(2), null, null);

        request.put("/api/programming/programming-exercises/timeline", timelineUpdate, HttpStatus.BAD_REQUEST);

        assertMemberTimelinePinnedToGroup(programmingExercise.getId(), timeline);
    }

    /** {@code set-visible} writes the release date through a targeted {@code @Modifying} query that bypasses validation, so it needs its own group guard. */
    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testSetVisibleOnGroupedQuiz_badRequest() throws Exception {
        QuizExercise quiz = addQuizToCourse(QuizMode.INDIVIDUAL);
        GroupTimeline timeline = createGroupWithTimelineFor(quiz.getId());

        request.put("/api/quiz/quiz-exercises/" + quiz.getId() + "/set-visible", null, HttpStatus.BAD_REQUEST);

        assertMemberTimelinePinnedToGroup(quiz.getId(), timeline);
    }

    /** The shared timeline a group imposes on its members, for asserting that member updates cannot move it. */
    private record GroupTimeline(Long groupId, ZonedDateTime release, ZonedDateTime due, ZonedDateTime assessmentDue) {
    }

    /** Creates a group that defines every shared date itself (so the timeline doesn't depend on the joining exercise) and assigns the exercise to it. */
    private GroupTimeline createGroupWithTimelineFor(long exerciseId) throws Exception {
        ZonedDateTime release = ZonedDateTime.now().plusDays(1).truncatedTo(ChronoUnit.MILLIS);
        ZonedDateTime due = ZonedDateTime.now().plusDays(7).truncatedTo(ChronoUnit.MILLIS);
        ZonedDateTime assessmentDue = ZonedDateTime.now().plusDays(14).truncatedTo(ChronoUnit.MILLIS);
        CreateExerciseVariantGroupDTO createDTO = new CreateExerciseVariantGroupDTO("Loop variants", 100.0, release, null, due, assessmentDue, null);
        ExerciseVariantGroupDTO created = request.postWithResponseBody(groupsUrl(), createDTO, ExerciseVariantGroupDTO.class, HttpStatus.CREATED);
        assignToGroup(exerciseId, created.id());
        return new GroupTimeline(created.id(), release, due, assessmentDue);
    }

    /** Moves every shared date and renames the exercise, so an update carries both a forbidden and a permitted change. */
    private static void applyDifferentTimelineAndTitle(Exercise exercise) {
        exercise.setTitle(UPDATED_TITLE);
        exercise.setReleaseDate(ZonedDateTime.now().plusDays(2).truncatedTo(ChronoUnit.MILLIS));
        exercise.setDueDate(ZonedDateTime.now().plusDays(20).truncatedTo(ChronoUnit.MILLIS));
        exercise.setAssessmentDueDate(ZonedDateTime.now().plusDays(30).truncatedTo(ChronoUnit.MILLIS));
    }

    /** Asserts the member kept its group's timeline while the rest of the update was applied normally. */
    private void assertTimelinePinnedToGroupAndTitleUpdated(long exerciseId, GroupTimeline timeline) {
        assertMemberTimelinePinnedToGroup(exerciseId, timeline);
        assertThat(exerciseRepository.findByIdElseThrow(exerciseId).getTitle()).isEqualTo(UPDATED_TITLE);
    }

    private void assertMemberTimelinePinnedToGroup(long exerciseId, GroupTimeline timeline) {
        Exercise reloaded = exerciseRepository.findByIdElseThrow(exerciseId);
        assertThat(zonedToInstant(reloaded.getReleaseDate())).isEqualTo(timeline.release().toInstant());
        assertThat(zonedToInstant(reloaded.getDueDate())).isEqualTo(timeline.due().toInstant());
        assertThat(zonedToInstant(reloaded.getAssessmentDueDate())).isEqualTo(timeline.assessmentDue().toInstant());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testAssignSynchronizedQuizToGroup_badRequest() throws Exception {
        ExerciseVariantGroupDTO created = createGroupAsEditor();
        QuizExercise quiz = addQuizToCourse(QuizMode.SYNCHRONIZED);
        String assignUrl = "/api/exercise/courses/" + course.getId() + "/exercises/" + quiz.getId() + "/variant-group";

        request.put(assignUrl, new ExerciseVariantGroupAssignmentDTO(created.id()), HttpStatus.BAD_REQUEST);

        Exercise reloaded = exerciseRepository.findByIdElseThrow(quiz.getId());
        assertThat(reloaded.getExerciseVariantGroup()).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testAssignBatchedQuizToGroup_badRequest() throws Exception {
        ExerciseVariantGroupDTO created = createGroupAsEditor();
        QuizExercise quiz = addQuizToCourse(QuizMode.BATCHED);
        String assignUrl = "/api/exercise/courses/" + course.getId() + "/exercises/" + quiz.getId() + "/variant-group";

        request.put(assignUrl, new ExerciseVariantGroupAssignmentDTO(created.id()), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testAssignIndividualQuizToGroupAdoptsTimeline() throws Exception {
        ZonedDateTime release = ZonedDateTime.now().plusDays(1).truncatedTo(ChronoUnit.MILLIS);
        ZonedDateTime due = ZonedDateTime.now().plusDays(7).truncatedTo(ChronoUnit.MILLIS);
        CreateExerciseVariantGroupDTO createDTO = new CreateExerciseVariantGroupDTO("Quiz variants", null, release, null, due, null, null);
        ExerciseVariantGroupDTO created = request.postWithResponseBody(groupsUrl(), createDTO, ExerciseVariantGroupDTO.class, HttpStatus.CREATED);
        QuizExercise quiz = addQuizToCourse(QuizMode.INDIVIDUAL);
        String assignUrl = "/api/exercise/courses/" + course.getId() + "/exercises/" + quiz.getId() + "/variant-group";

        request.put(assignUrl, new ExerciseVariantGroupAssignmentDTO(created.id()), HttpStatus.OK);

        Exercise reloaded = exerciseRepository.findByIdElseThrow(quiz.getId());
        assertThat(reloaded.getExerciseVariantGroup()).isNotNull();
        assertThat(reloaded.getReleaseDate().toInstant()).isEqualTo(release.toInstant());
        assertThat(reloaded.getDueDate().toInstant()).isEqualTo(due.toInstant());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testAssignIndividualQuizToGroup_exampleSolutionDateBeforeDueDate_badRequest() throws Exception {
        ZonedDateTime release = ZonedDateTime.now().plusDays(1).truncatedTo(ChronoUnit.MILLIS);
        ZonedDateTime exampleSolution = ZonedDateTime.now().plusDays(3).truncatedTo(ChronoUnit.MILLIS);
        ZonedDateTime due = ZonedDateTime.now().plusDays(7).truncatedTo(ChronoUnit.MILLIS);
        // Valid at the group level (exampleSolution >= release), but the quiz's own (default INCLUDED_COMPLETELY) dates
        // require exampleSolution >= dueDate once applied to the member exercise (Exercise#validateBaseDates()).
        CreateExerciseVariantGroupDTO createDTO = new CreateExerciseVariantGroupDTO("Quiz variants", null, release, null, due, null, exampleSolution);
        ExerciseVariantGroupDTO created = request.postWithResponseBody(groupsUrl(), createDTO, ExerciseVariantGroupDTO.class, HttpStatus.CREATED);
        QuizExercise quiz = addQuizToCourse(QuizMode.INDIVIDUAL);
        String assignUrl = "/api/exercise/courses/" + course.getId() + "/exercises/" + quiz.getId() + "/variant-group";

        request.put(assignUrl, new ExerciseVariantGroupAssignmentDTO(created.id()), HttpStatus.BAD_REQUEST);

        Exercise reloaded = exerciseRepository.findByIdElseThrow(quiz.getId());
        assertThat(reloaded.getExerciseVariantGroup()).isNull();
    }

    /**
     * Assigning stamps the group timeline onto the quiz, so a quiz with a started batch must be rejected — the group would
     * otherwise overwrite the dates of a live attempt.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testAssignIndividualQuizWithStartedBatchToGroup_badRequest() throws Exception {
        ExerciseVariantGroupDTO created = createGroupAsEditor();
        QuizExercise quiz = addQuizToCourse(QuizMode.INDIVIDUAL);
        startBatchFor(quiz);
        String assignUrl = "/api/exercise/courses/" + course.getId() + "/exercises/" + quiz.getId() + "/variant-group";

        request.put(assignUrl, new ExerciseVariantGroupAssignmentDTO(created.id()), HttpStatus.BAD_REQUEST);

        assertThat(exerciseRepository.findByIdElseThrow(quiz.getId()).getExerciseVariantGroup()).isNull();
    }

    /** The same guard for a quiz that has already ended: assigning it would move a finished quiz's due date. */
    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testAssignEndedIndividualQuizToGroup_badRequest() throws Exception {
        ExerciseVariantGroupDTO created = createGroupAsEditor();
        QuizExercise quiz = addEndedQuizToCourse();
        String assignUrl = "/api/exercise/courses/" + course.getId() + "/exercises/" + quiz.getId() + "/variant-group";

        request.put(assignUrl, new ExerciseVariantGroupAssignmentDTO(created.id()), HttpStatus.BAD_REQUEST);

        assertThat(exerciseRepository.findByIdElseThrow(quiz.getId()).getExerciseVariantGroup()).isNull();
    }

    /** A date-changing group update must be rejected once a member quiz's batch has started, leaving the member's dates untouched. */
    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testUpdateGroupTimelineWithStartedBatchQuizMember_badRequest() throws Exception {
        QuizExercise quiz = addQuizToCourse(QuizMode.INDIVIDUAL);
        // Assign while still editable so the quiz adopts the group timeline, then start a batch to lock it.
        GroupTimeline timeline = createGroupWithTimelineFor(quiz.getId());
        startBatchFor((QuizExercise) exerciseRepository.findByIdElseThrow(quiz.getId()));

        UpdateExerciseVariantGroupDTO update = new UpdateExerciseVariantGroupDTO(timeline.groupId(), "Loop variants", 100.0, timeline.release(), null, timeline.due().plusDays(2),
                timeline.assessmentDue(), null);
        request.put(groupsUrl() + "/" + timeline.groupId(), update, HttpStatus.BAD_REQUEST);

        assertMemberTimelinePinnedToGroup(quiz.getId(), timeline);
    }

    /** The same group-update guard for an ended member quiz (membership built via repositories, since REST assignment would already reject it). */
    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testUpdateGroupTimelineWithEndedQuizMember_badRequest() throws Exception {
        QuizExercise quiz = addEndedQuizToCourse();
        GroupTimeline timeline = persistEndedQuizInGroup(quiz);

        // A different due date that keeps the group timeline itself valid (release < due <= assessmentDue), so the request
        // only fails on our member-editability guard rather than group-level date validation.
        ZonedDateTime newDue = ZonedDateTime.now().minusHours(12).truncatedTo(ChronoUnit.MILLIS);
        UpdateExerciseVariantGroupDTO update = new UpdateExerciseVariantGroupDTO(timeline.groupId(), "Loop variants", 100.0, timeline.release(), null, newDue,
                timeline.assessmentDue(), null);
        request.put(groupsUrl() + "/" + timeline.groupId(), update, HttpStatus.BAD_REQUEST);

        assertMemberTimelinePinnedToGroup(quiz.getId(), timeline);
    }

    /** A group edit that doesn't touch the timeline (only title/max points) stays a no-op for members, so it's allowed even while a member quiz is live. */
    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testUpdateGroupMetadataOnlyWithLiveQuizMemberAllowed() throws Exception {
        QuizExercise quiz = addQuizToCourse(QuizMode.INDIVIDUAL);
        GroupTimeline timeline = createGroupWithTimelineFor(quiz.getId());
        startBatchFor((QuizExercise) exerciseRepository.findByIdElseThrow(quiz.getId()));

        // Same dates as the group already has, only the title and max points change.
        UpdateExerciseVariantGroupDTO update = new UpdateExerciseVariantGroupDTO(timeline.groupId(), "Renamed while live", 42.0, timeline.release(), null, timeline.due(),
                timeline.assessmentDue(), null);
        ExerciseVariantGroupDTO updated = request.putWithResponseBody(groupsUrl() + "/" + timeline.groupId(), update, ExerciseVariantGroupDTO.class, HttpStatus.OK);

        assertThat(updated.title()).isEqualTo("Renamed while live");
        assertThat(updated.maxPoints()).isEqualTo(42.0);
        // The live quiz member kept its dates untouched.
        assertMemberTimelinePinnedToGroup(quiz.getId(), timeline);
    }

    /** Regression: an editable quiz member (no started batch) still receives a group date change — the guard doesn't over-reject. */
    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testUpdateGroupTimelinePropagatesToEditableQuizMember() throws Exception {
        QuizExercise quiz = addQuizToCourse(QuizMode.INDIVIDUAL);
        GroupTimeline timeline = createGroupWithTimelineFor(quiz.getId());

        ZonedDateTime newDue = timeline.due().plusDays(3);
        UpdateExerciseVariantGroupDTO update = new UpdateExerciseVariantGroupDTO(timeline.groupId(), "Loop variants", 100.0, timeline.release(), null, newDue,
                timeline.assessmentDue(), null);
        request.put(groupsUrl() + "/" + timeline.groupId(), update, HttpStatus.OK);

        assertThat(exerciseRepository.findByIdElseThrow(quiz.getId()).getDueDate().toInstant()).isEqualTo(newDue.toInstant());
    }

    /** Adds an individual-mode quiz whose release and due dates are already in the past, so the quiz has ended. */
    private QuizExercise addEndedQuizToCourse() {
        ZonedDateTime release = ZonedDateTime.now().minusDays(7).truncatedTo(ChronoUnit.MILLIS);
        ZonedDateTime due = ZonedDateTime.now().minusDays(1).truncatedTo(ChronoUnit.MILLIS);
        ZonedDateTime assessmentDue = ZonedDateTime.now().minusHours(1).truncatedTo(ChronoUnit.MILLIS);
        QuizExercise quizExercise = QuizExerciseFactory.generateQuizExercise(release, due, assessmentDue, QuizMode.INDIVIDUAL, course);
        QuizExerciseFactory.addQuestionsToQuizExercise(quizExercise);
        course.addExercises(quizExercise);
        return exerciseRepository.save(quizExercise);
    }

    /** Attaches a batch that started in the past to the given quiz, making it no longer editable. */
    private void startBatchFor(QuizExercise quiz) {
        QuizBatch batch = QuizExerciseFactory.generateQuizBatch(quiz, ZonedDateTime.now().minusSeconds(10).truncatedTo(ChronoUnit.MILLIS));
        quizExerciseUtilService.setQuizBatchExerciseAndSave(batch, quiz);
    }

    /**
     * Puts an ended quiz in a group whose timeline matches the quiz's dates. The group is created via REST (so it's attached
     * to the course), but membership is set via the repository to bypass the REST assignment guard.
     */
    private GroupTimeline persistEndedQuizInGroup(QuizExercise quiz) throws Exception {
        CreateExerciseVariantGroupDTO createDTO = new CreateExerciseVariantGroupDTO("Loop variants", 100.0, quiz.getReleaseDate(), null, quiz.getDueDate(),
                quiz.getAssessmentDueDate(), null);
        ExerciseVariantGroupDTO created = request.postWithResponseBody(groupsUrl(), createDTO, ExerciseVariantGroupDTO.class, HttpStatus.CREATED);
        ExerciseVariantGroup group = exerciseVariantGroupRepository.findByIdAndCourseIdElseThrow(created.id(), course.getId());
        quiz.setExerciseVariantGroup(group);
        QuizExercise saved = exerciseRepository.save(quiz);
        return new GroupTimeline(created.id(), saved.getReleaseDate(), saved.getDueDate(), saved.getAssessmentDueDate());
    }
}
