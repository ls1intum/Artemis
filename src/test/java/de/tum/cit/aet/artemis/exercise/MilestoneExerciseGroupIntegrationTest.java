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

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.dto.CourseExercisesForOverviewDTO;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVariantGroup;
import de.tum.cit.aet.artemis.exercise.domain.MilestoneExerciseGroup;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseOverviewDTO;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseVariantGroupDTO;
import de.tum.cit.aet.artemis.exercise.dto.MilestoneExerciseGroupDTO;
import de.tum.cit.aet.artemis.exercise.dto.MilestoneStatusDTO;
import de.tum.cit.aet.artemis.exercise.dto.UpdateMilestoneExerciseGroupDTO;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVariantGroupRepository;
import de.tum.cit.aet.artemis.exercise.repository.MilestoneExerciseGroupRepository;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationIndependentTest;
import de.tum.cit.aet.artemis.programming.domain.MilestoneExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.UserStoryExercise;

/**
 * Covers {@link MilestoneExerciseGroup} and its dedicated repository/routes, alongside ordinary
 * {@link ExerciseVariantGroup}s in the same course.
 * <p>
 * The two types share one table under a discriminator, so the point of these tests is that neither type's queries ever
 * see the other's rows, and that a milestone group's anchor exercise is actually fetched - its timeline is read through
 * that anchor, so an unfetched one silently reads as "no dates".
 * <p>
 * The milestone group is built directly through the repository rather than through the create endpoint: provisioning a
 * real {@link MilestoneExercise} would drag in the VCS/CI test infrastructure, and nothing here depends on it.
 */
class MilestoneExerciseGroupIntegrationTest extends AbstractProgrammingIntegrationIndependentTest {

    private static final String TEST_PREFIX = "milestonegrpinteg";

    @Autowired
    private MilestoneExerciseGroupRepository milestoneExerciseGroupRepository;

    @Autowired
    private ExerciseVariantGroupRepository exerciseVariantGroupRepository;

    private Course course;

    private MilestoneExercise milestoneExercise;

    private MilestoneExerciseGroup milestoneGroup;

    private ExerciseVariantGroup variantGroup;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);
        course = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);

        milestoneExercise = new MilestoneExercise();
        milestoneExercise.setTitle("Milestone");
        milestoneExercise.setShortName("milestone" + TEST_PREFIX);
        milestoneExercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        milestoneExercise.setCourse(course);
        milestoneExercise.setMaxPoints(0.0);
        // Truncated to milliseconds: PostgreSQL stores no finer precision, so an untruncated value would not survive the
        // round-trip the assertions compare against.
        milestoneExercise.setReleaseDate(ZonedDateTime.now().minusDays(1).truncatedTo(ChronoUnit.MILLIS));
        milestoneExercise.setDueDate(ZonedDateTime.now().plusDays(7).truncatedTo(ChronoUnit.MILLIS));
        milestoneExercise.generateAndSetProjectKey();
        milestoneExercise = (MilestoneExercise) programmingExerciseRepository.save(milestoneExercise);

        milestoneGroup = new MilestoneExerciseGroup();
        milestoneGroup.setTitle("Sprint 1");
        milestoneGroup.setMilestoneExercise(milestoneExercise);
        milestoneGroup = milestoneExerciseGroupRepository.save(milestoneGroup);

        variantGroup = new ExerciseVariantGroup();
        variantGroup.setTitle("Loop variants");
        variantGroup.setMaxPoints(100.0);
        variantGroup = exerciseVariantGroupRepository.save(variantGroup);

        course = courseRepository.findWithEagerExerciseVariantGroupsByIdElseThrow(course.getId());
        course.addExerciseVariantGroup(milestoneGroup);
        course.addExerciseVariantGroup(variantGroup);
        courseRepository.save(course);
    }

    private String variantGroupsUrl() {
        return "/api/exercise/courses/" + course.getId() + "/exercise-variant-groups";
    }

    private String milestoneGroupsUrl() {
        return "/api/exercise/courses/" + course.getId() + "/milestone-exercise-groups";
    }

    @Test
    void milestoneAndVariantGroupsAreStoredUnderTheirOwnDiscriminator() {
        assertThat(milestoneExerciseGroupRepository.findAllByCourseId(course.getId())).extracting(ExerciseVariantGroup::getId).containsExactly(milestoneGroup.getId());
        // Each repository sees only its own type: the two share a table, and the variant query cannot fetch a milestone
        // group's anchor without a TREAT that would drop every other group from the result.
        assertThat(exerciseVariantGroupRepository.findAllByCourseId(course.getId())).extracting(ExerciseVariantGroup::getId).containsExactly(variantGroup.getId());
        assertThat(exerciseVariantGroupRepository.findByIdAndCourseId(milestoneGroup.getId(), course.getId())).isEmpty();
    }

    @Test
    void milestoneGroupLookupsRejectAVariantGroup() {
        assertThat(milestoneExerciseGroupRepository.findByIdAndCourseId(milestoneGroup.getId(), course.getId())).isPresent();
        assertThat(milestoneExerciseGroupRepository.findByIdAndCourseId(variantGroup.getId(), course.getId())).isEmpty();
        assertThat(milestoneExerciseGroupRepository.findByIdAndCourseIdWithoutExercises(variantGroup.getId(), course.getId())).isEmpty();
    }

    @Test
    void milestoneGroupLookupsAreScopedToTheCourse() {
        Course otherCourse = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX + "other");
        assertThat(milestoneExerciseGroupRepository.findByIdAndCourseId(milestoneGroup.getId(), otherCourse.getId())).isEmpty();
        assertThat(milestoneExerciseGroupRepository.findAllByCourseId(otherCourse.getId())).isEmpty();
    }

    @Test
    void theAnchorExerciseIsFetchedSoTheGroupCanReportItsTimeline() {
        MilestoneExerciseGroup loaded = milestoneExerciseGroupRepository.findByIdAndCourseIdElseThrow(milestoneGroup.getId(), course.getId());

        assertThat(loaded.getMilestoneExercise()).isNotNull();
        assertThat(loaded.getMilestoneExercise().getId()).isEqualTo(milestoneExercise.getId());
        // Read through the group, which delegates to the anchor - null here would mean the anchor was left a proxy.
        assertThat(loaded.getDueDate()).isNotNull();
        assertThat(loaded.getDueDate().toInstant()).isEqualTo(milestoneExercise.getDueDate().toInstant());
    }

    @Test
    void theAnchorExerciseIdIsResolvableWithoutLoadingIt() {
        assertThat(milestoneExerciseGroupRepository.findMilestoneExerciseIdByGroupId(milestoneGroup.getId())).contains(milestoneExercise.getId());
        assertThat(milestoneExerciseGroupRepository.countExercisesByGroupId(milestoneGroup.getId())).isZero();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void milestoneGroupEndpointReturnsOnlyMilestoneGroupsWithTheirAnchor() throws Exception {
        List<MilestoneExerciseGroupDTO> groups = request.getList(milestoneGroupsUrl(), HttpStatus.OK, MilestoneExerciseGroupDTO.class);

        assertThat(groups).hasSize(1);
        MilestoneExerciseGroupDTO group = groups.getFirst();
        assertThat(group.id()).isEqualTo(milestoneGroup.getId());
        assertThat(group.title()).isEqualTo("Sprint 1");
        assertThat(group.milestoneExerciseId()).isEqualTo(milestoneExercise.getId());
        assertThat(group.dueDate()).isNotNull();
        assertThat(group.dueDate().toInstant()).isEqualTo(milestoneExercise.getDueDate().toInstant());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void singleGroupEndpointRejectsAVariantGroup() throws Exception {
        request.get(milestoneGroupsUrl() + "/" + milestoneGroup.getId(), HttpStatus.OK, MilestoneExerciseGroupDTO.class);
        request.get(milestoneGroupsUrl() + "/" + variantGroup.getId(), HttpStatus.NOT_FOUND, MilestoneExerciseGroupDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void updatingAMilestoneGroupWritesTheTimelineToItsAnchorExercise() throws Exception {
        ZonedDateTime newDueDate = ZonedDateTime.now().plusDays(21).truncatedTo(ChronoUnit.MILLIS);
        UpdateMilestoneExerciseGroupDTO updateDTO = new UpdateMilestoneExerciseGroupDTO(milestoneGroup.getId(), "Sprint 1 renamed", milestoneExercise.getReleaseDate(), null,
                newDueDate, null, null);

        MilestoneExerciseGroupDTO updated = request.putWithResponseBody(milestoneGroupsUrl() + "/" + milestoneGroup.getId(), updateDTO, MilestoneExerciseGroupDTO.class,
                HttpStatus.OK);

        assertThat(updated.title()).isEqualTo("Sprint 1 renamed");
        assertThat(updated.dueDate().toInstant()).isEqualTo(newDueDate.toInstant());
        // The milestone group stores no dates of its own - they live on the anchor exercise.
        assertThat(programmingExerciseRepository.findByIdElseThrow(milestoneExercise.getId()).getDueDate().toInstant()).isEqualTo(newDueDate.toInstant());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void updatingWithAMismatchedIdIsRejected() throws Exception {
        UpdateMilestoneExerciseGroupDTO mismatched = new UpdateMilestoneExerciseGroupDTO(milestoneGroup.getId() + 1, "Renamed", null, null, null, null, null);

        request.putWithResponseBody(milestoneGroupsUrl() + "/" + milestoneGroup.getId(), mismatched, MilestoneExerciseGroupDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void variantGroupEndpointsStillServeVariantGroupsUnchanged() throws Exception {
        ExerciseVariantGroupDTO fetched = request.get(variantGroupsUrl() + "/" + variantGroup.getId(), HttpStatus.OK, ExerciseVariantGroupDTO.class);

        assertThat(fetched.id()).isEqualTo(variantGroup.getId());
        assertThat(fetched.maxPoints()).isEqualTo(100.0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void aStudentMayNotReadMilestoneGroups() throws Exception {
        request.getList(milestoneGroupsUrl(), HttpStatus.FORBIDDEN, MilestoneExerciseGroupDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void anEditorMayNotDeleteAMilestoneGroup() throws Exception {
        request.delete(milestoneGroupsUrl() + "/" + milestoneGroup.getId(), HttpStatus.FORBIDDEN);

        assertThat(milestoneExerciseGroupRepository.findByIdAndCourseId(milestoneGroup.getId(), course.getId())).isPresent();
    }

    /**
     * The anchor exercise is never part of any exercise listing, so this endpoint is the only way the student group view
     * learns its id - and therefore what the group's "Start exercise" action addresses.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void milestoneStatusNamesTheAnchorExerciseForAStudentWhoHasNotStartedIt() throws Exception {
        MilestoneStatusDTO status = request.get(milestoneGroupsUrl() + "/" + milestoneGroup.getId() + "/milestone-status", HttpStatus.OK, MilestoneStatusDTO.class);

        assertThat(status.milestoneExerciseId()).isEqualTo(milestoneExercise.getId());
        assertThat(status.started()).isFalse();
        assertThat(status.participationId()).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void milestoneStatusRejectsAVariantGroup() throws Exception {
        request.get(milestoneGroupsUrl() + "/" + variantGroup.getId() + "/milestone-status", HttpStatus.NOT_FOUND, MilestoneStatusDTO.class);
    }

    /**
     * The course overview is what the student group view builds its groups from, so a milestone group's members have to
     * carry the anchor exercise id along with the discriminator - otherwise the view knows the group is a milestone but
     * not which exercise its actions address.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void theCourseOverviewNamesTheAnchorExerciseOnAMilestoneGroupMember() throws Exception {
        UserStoryExercise member = new UserStoryExercise();
        member.setTitle("User story");
        member.setShortName("userstory" + TEST_PREFIX);
        member.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        member.setCourse(course);
        member.setMaxPoints(10.0);
        member.setReleaseDate(ZonedDateTime.now().minusDays(1).truncatedTo(ChronoUnit.MILLIS));
        member.setExerciseVariantGroup(milestoneGroup);
        member.generateAndSetProjectKey();
        programmingExerciseRepository.save(member);

        var overview = request.get("/api/course/courses/" + course.getId() + "/exercises-for-overview", HttpStatus.OK, CourseExercisesForOverviewDTO.class);

        // The anchor itself is never listed: MilestoneExercise.isVisibleToStudents() is always false.
        assertThat(overview.exercises()).extracting(ExerciseOverviewDTO::id).doesNotContain(milestoneExercise.getId());
        assertThat(overview.exercises()).filteredOn(exercise -> exercise.id().equals(member.getId())).singleElement().satisfies(exercise -> {
            assertThat(exercise.exerciseVariantGroup()).isNotNull();
            assertThat(exercise.exerciseVariantGroup().type()).isEqualTo("milestone");
            assertThat(exercise.exerciseVariantGroup().milestoneExerciseId()).isEqualTo(milestoneExercise.getId());
        });
    }
}
