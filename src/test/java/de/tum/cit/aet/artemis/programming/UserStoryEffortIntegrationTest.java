package de.tum.cit.aet.artemis.programming;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.MilestoneExerciseGroup;
import de.tum.cit.aet.artemis.exercise.repository.MilestoneExerciseGroupRepository;
import de.tum.cit.aet.artemis.programming.domain.MilestoneExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.UserStoryEffort;
import de.tum.cit.aet.artemis.programming.domain.UserStoryExercise;
import de.tum.cit.aet.artemis.programming.dto.UserStoryEffortDTO;
import de.tum.cit.aet.artemis.programming.dto.UserStoryEffortStatusDTO;
import de.tum.cit.aet.artemis.programming.repository.UserStoryEffortRepository;
import de.tum.cit.aet.artemis.programming.service.MilestoneEffortGateService;

/**
 * Covers the effort a student reports for a user story exercise, and the gate that refuses writes to a milestone group's
 * shared repository while a started story is still unestimated.
 */
class UserStoryEffortIntegrationTest extends AbstractProgrammingIntegrationIndependentTest {

    private static final String TEST_PREFIX = "userstoryeffort";

    @Autowired
    private UserStoryEffortRepository userStoryEffortRepository;

    @Autowired
    private MilestoneExerciseGroupRepository milestoneExerciseGroupRepository;

    @Autowired
    private MilestoneEffortGateService milestoneEffortGateService;

    private Course course;

    private MilestoneExercise milestoneExercise;

    private MilestoneExerciseGroup group;

    private UserStoryExercise userStory;

    private String studentLogin;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 0, 1);
        studentLogin = TEST_PREFIX + "student1";
        course = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);

        milestoneExercise = new MilestoneExercise();
        milestoneExercise.setTitle("Milestone");
        milestoneExercise.setShortName("ms" + TEST_PREFIX);
        milestoneExercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        milestoneExercise.setCourse(course);
        milestoneExercise.setMaxPoints(0.0);
        milestoneExercise.setDueDate(ZonedDateTime.now().plusDays(7));
        milestoneExercise.generateAndSetProjectKey();
        milestoneExercise = (MilestoneExercise) programmingExerciseRepository.save(milestoneExercise);

        group = new MilestoneExerciseGroup();
        group.setTitle("Sprint 1");
        group.setMilestoneExercise(milestoneExercise);
        group = milestoneExerciseGroupRepository.save(group);

        course = courseRepository.findWithEagerExerciseVariantGroupsByIdElseThrow(course.getId());
        course.addExerciseVariantGroup(group);
        courseRepository.save(course);

        userStory = createUserStory("us1", ZonedDateTime.now().plusDays(7));
    }

    private UserStoryExercise createUserStory(String shortName, ZonedDateTime dueDate) {
        UserStoryExercise exercise = new UserStoryExercise();
        exercise.setTitle("User story " + shortName);
        exercise.setShortName(shortName + TEST_PREFIX);
        exercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        exercise.setCourse(course);
        exercise.setMaxPoints(2.0);
        exercise.setDueDate(dueDate);
        exercise.setExerciseVariantGroup(group);
        exercise.generateAndSetProjectKey();
        return (UserStoryExercise) programmingExerciseRepository.save(exercise);
    }

    private String effortUrl(long exerciseId) {
        return "/api/programming/user-story-exercises/" + exerciseId + "/effort";
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void reportingEffortCreatesThenUpdatesASingleRow() throws Exception {
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(userStory, studentLogin);

        UserStoryEffortDTO created = request.putWithResponseBody(effortUrl(userStory.getId()), new UserStoryEffortDTO(3.5, null), UserStoryEffortDTO.class, HttpStatus.OK);
        assertThat(created.estimatedEffort()).isEqualTo(3.5);
        assertThat(created.actualEffort()).isNull();

        Long rowIdAfterFirstReport = userStoryEffortRepository.findByParticipationId(participation.getId()).orElseThrow().getId();

        UserStoryEffortDTO updated = request.putWithResponseBody(effortUrl(userStory.getId()), new UserStoryEffortDTO(3.5, 5.0), UserStoryEffortDTO.class, HttpStatus.OK);
        assertThat(updated.actualEffort()).isEqualTo(5.0);
        // Upsert, not insert: reporting again must update the same row rather than leave a second one behind.
        assertThat(userStoryEffortRepository.findByParticipationId(participation.getId()).orElseThrow().getId()).isEqualTo(rowIdAfterFirstReport);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void readingEffortReturnsAnEmptyPairBeforeAnythingIsReported() throws Exception {
        participationUtilService.addStudentParticipationForProgrammingExercise(userStory, studentLogin);

        UserStoryEffortDTO effort = request.get(effortUrl(userStory.getId()), HttpStatus.OK, UserStoryEffortDTO.class);

        assertThat(effort.estimatedEffort()).isNull();
        assertThat(effort.actualEffort()).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void reportingIsRejectedWithoutAParticipation() throws Exception {
        request.putWithResponseBody(effortUrl(userStory.getId()), new UserStoryEffortDTO(1.0, null), UserStoryEffortDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void reportingIsRejectedForANonUserStoryExercise() throws Exception {
        request.putWithResponseBody(effortUrl(milestoneExercise.getId()), new UserStoryEffortDTO(1.0, null), UserStoryEffortDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void outOfRangeValuesAreRejected() throws Exception {
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(userStory, studentLogin);

        request.putWithResponseBody(effortUrl(userStory.getId()), new UserStoryEffortDTO(-1.0, null), UserStoryEffortDTO.class, HttpStatus.BAD_REQUEST);
        request.putWithResponseBody(effortUrl(userStory.getId()), new UserStoryEffortDTO(null, 10_000.0), UserStoryEffortDTO.class, HttpStatus.BAD_REQUEST);
        // Scoped to this participation: the suite shares a database, so other tests' rows are in the table too.
        assertThat(userStoryEffortRepository.findByParticipationId(participation.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void reportingIsRejectedAfterTheDueDate() throws Exception {
        UserStoryExercise pastStory = createUserStory("us2", ZonedDateTime.now().minusDays(1));
        participationUtilService.addStudentParticipationForProgrammingExercise(pastStory, studentLogin);

        request.putWithResponseBody(effortUrl(pastStory.getId()), new UserStoryEffortDTO(1.0, null), UserStoryEffortDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void anIndividualDueDateExtensionStillAllowsReporting() throws Exception {
        UserStoryExercise pastStory = createUserStory("us3", ZonedDateTime.now().minusDays(1));
        ProgrammingExerciseStudentParticipation participation = participationUtilService.addStudentParticipationForProgrammingExercise(pastStory, studentLogin);
        participation.setIndividualDueDate(ZonedDateTime.now().plusDays(3));
        participationRepository.save(participation);

        request.putWithResponseBody(effortUrl(pastStory.getId()), new UserStoryEffortDTO(1.0, null), UserStoryEffortDTO.class, HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void theCourseLookupReportsEveryStartedStory() throws Exception {
        participationUtilService.addStudentParticipationForProgrammingExercise(userStory, studentLogin);
        UserStoryExercise otherStory = createUserStory("us4", ZonedDateTime.now().plusDays(7));
        participationUtilService.addStudentParticipationForProgrammingExercise(otherStory, studentLogin);
        request.putWithResponseBody(effortUrl(userStory.getId()), new UserStoryEffortDTO(2.0, null), UserStoryEffortDTO.class, HttpStatus.OK);

        List<UserStoryEffortStatusDTO> statuses = request.getList("/api/programming/courses/" + course.getId() + "/user-story-efforts", HttpStatus.OK,
                UserStoryEffortStatusDTO.class);

        assertThat(statuses).extracting(UserStoryEffortStatusDTO::exerciseId).containsExactlyInAnyOrder(userStory.getId(), otherStory.getId());
        assertThat(statuses).filteredOn(status -> status.exerciseId().equals(userStory.getId())).singleElement().extracting(UserStoryEffortStatusDTO::estimatedEffort)
                .isEqualTo(2.0);
        // The story with no estimate is what the overview marks.
        assertThat(statuses).filteredOn(status -> status.exerciseId().equals(otherStory.getId())).singleElement().extracting(UserStoryEffortStatusDTO::estimatedEffort).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void aTutorCanReadTheEffortOnAParticipationTheyAssess() throws Exception {
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(userStory, studentLogin);
        userStoryEffortRepository.save(effortFor(participation, 4.0, 6.0));

        UserStoryEffortDTO effort = request.get("/api/programming/participations/" + participation.getId() + "/user-story-effort", HttpStatus.OK, UserStoryEffortDTO.class);

        assertThat(effort.estimatedEffort()).isEqualTo(4.0);
        assertThat(effort.actualEffort()).isEqualTo(6.0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void anotherStudentCannotReadTheEffortOnAParticipation() throws Exception {
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(userStory, studentLogin);
        userStoryEffortRepository.save(effortFor(participation, 4.0, null));

        request.get("/api/programming/participations/" + participation.getId() + "/user-story-effort", HttpStatus.FORBIDDEN, UserStoryEffortDTO.class);
    }

    private UserStoryEffort effortFor(ProgrammingExerciseStudentParticipation participation, Double estimated, Double actual) {
        UserStoryEffort effort = new UserStoryEffort();
        effort.setParticipation(participation);
        effort.setEstimatedEffort(estimated);
        effort.setActualEffort(actual);
        return effort;
    }

    @Test
    void theGateBlocksAStartedStoryWithoutAnEstimate() {
        participationUtilService.addStudentParticipationForProgrammingExercise(userStory, studentLogin);
        var milestoneParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(milestoneExercise, studentLogin);
        var student = userUtilService.getUserByLogin(studentLogin);

        List<String> blocking = milestoneEffortGateService.findStoriesBlockingWrite(milestoneExercise, milestoneParticipation, student);

        assertThat(blocking).containsExactly(userStory.getTitle());
        assertThat(milestoneEffortGateService.buildRejectionMessage(blocking)).contains(userStory.getTitle());
    }

    @Test
    void theGateIgnoresAStoryTheStudentHasNotStarted() {
        var milestoneParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(milestoneExercise, studentLogin);
        var student = userUtilService.getUserByLogin(studentLogin);

        // No participation in the story, so there is nowhere to record an estimate - it must not block the push.
        assertThat(milestoneEffortGateService.findStoriesBlockingWrite(milestoneExercise, milestoneParticipation, student)).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void theGateClearsOnceTheEstimateIsReported() throws Exception {
        participationUtilService.addStudentParticipationForProgrammingExercise(userStory, studentLogin);
        var milestoneParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(milestoneExercise, studentLogin);
        var student = userUtilService.getUserByLogin(studentLogin);
        assertThat(milestoneEffortGateService.findStoriesBlockingWrite(milestoneExercise, milestoneParticipation, student)).isNotEmpty();

        request.putWithResponseBody(effortUrl(userStory.getId()), new UserStoryEffortDTO(2.0, null), UserStoryEffortDTO.class, HttpStatus.OK);

        assertThat(milestoneEffortGateService.findStoriesBlockingWrite(milestoneExercise, milestoneParticipation, student)).isEmpty();
    }

    @Test
    void theGateNeverBlocksTeachingStaff() {
        participationUtilService.addStudentParticipationForProgrammingExercise(userStory, studentLogin);
        var milestoneParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(milestoneExercise, studentLogin);
        var tutor = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");

        assertThat(milestoneEffortGateService.findStoriesBlockingWrite(milestoneExercise, milestoneParticipation, tutor)).isEmpty();
    }

    @Test
    void theGateIgnoresExercisesThatAreNotMilestones() {
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(userStory, studentLogin);
        var student = userUtilService.getUserByLogin(studentLogin);

        // The gate only guards a milestone group's shared repository; a story's own repository is not one.
        assertThat(milestoneEffortGateService.findStoriesBlockingWrite(userStory, participation, student)).isEmpty();
    }
}
