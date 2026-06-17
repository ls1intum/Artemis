package de.tum.cit.aet.artemis.exercise;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.RequestUtilService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVariantGroup;
import de.tum.cit.aet.artemis.exercise.dto.CreateExerciseVariantGroupDTO;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseVariantGroupAssignmentDTO;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseVariantGroupDTO;
import de.tum.cit.aet.artemis.exercise.dto.UpdateExerciseVariantGroupDTO;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVariantGroupRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentBatchTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

class ExerciseVariantGroupIntegrationTest extends AbstractSpringIntegrationIndependentBatchTest {

    private static final String TEST_PREFIX = "exvargrpinteg";

    @Autowired
    private RequestUtilService request;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ExerciseVariantGroupRepository exerciseVariantGroupRepository;

    @Autowired
    private ExerciseRepository exerciseRepository;

    private Course course;

    private TextExercise exercise;

    @BeforeEach
    void setup() {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 1, 1);
        course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
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

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteExerciseVariantGroup_instructorAllowed() throws Exception {
        ExerciseVariantGroupDTO created = request.postWithResponseBody(groupsUrl(), sampleCreateDTO(), ExerciseVariantGroupDTO.class, HttpStatus.CREATED);

        request.delete(groupsUrl() + "/" + created.id(), HttpStatus.OK);

        assertThat(exerciseVariantGroupRepository.findById(created.id())).isEmpty();
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
        ZonedDateTime due = ZonedDateTime.now().plusDays(7).truncatedTo(ChronoUnit.MILLIS);
        CreateExerciseVariantGroupDTO createDTO = new CreateExerciseVariantGroupDTO("Dated variants", null, release, null, due, null, null);
        ExerciseVariantGroupDTO created = request.postWithResponseBody(groupsUrl(), createDTO, ExerciseVariantGroupDTO.class, HttpStatus.CREATED);
        String assignUrl = "/api/exercise/courses/" + course.getId() + "/exercises/" + exercise.getId() + "/variant-group";

        request.put(assignUrl, new ExerciseVariantGroupAssignmentDTO(created.id()), HttpStatus.OK);

        // The exercise adopts the group's whole timeline: set dates are copied, unset group dates clear the exercise's own.
        Exercise reloaded = exerciseRepository.findByIdElseThrow(exercise.getId());
        assertThat(reloaded.getReleaseDate().toInstant()).isEqualTo(release.toInstant());
        assertThat(reloaded.getDueDate().toInstant()).isEqualTo(due.toInstant());
        assertThat(reloaded.getStartDate()).isNull();
        assertThat(reloaded.getAssessmentDueDate()).isNull();
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
}
