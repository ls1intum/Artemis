package de.tum.cit.aet.artemis.programming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.hibernate.collection.spi.PersistentBag;
import org.hibernate.collection.spi.PersistentSet;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVariantGroup;
import de.tum.cit.aet.artemis.exercise.dto.TeamAssignmentConfigDTO;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.LockRepositoryPolicy;
import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.SubmissionPenaltyPolicy;
import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.SubmissionPolicy;
import de.tum.cit.aet.artemis.programming.dto.CreateProgrammingExerciseDTO;
import de.tum.cit.aet.artemis.programming.dto.ImportProgrammingExerciseRequestDTO;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseListItemDTO;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseResponseDTO;
import de.tum.cit.aet.artemis.programming.dto.ResultDTO;
import de.tum.cit.aet.artemis.programming.dto.SubmissionPolicyDTO;

/**
 * Mapping and wire-shape tests for the shared programming DTOs. Deliberately a plain unit test in the module root:
 * a test class under a {@code ..dto..} package breaks the module code-style rules.
 */
class ProgrammingExerciseDtoMappingTest {

    /**
     * A bare mapper, not the Spring-configured one. The real wire contract of these responses is owned by the
     * endpoint tests (SubmissionPolicyIntegrationTest asserts the exact key sets on the real HTTP responses); the
     * mapping tests below only cover the record's own inclusion rules in a fast loop.
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    // --- SubmissionPolicyDTO: inclusion rules and id pass-through --------------------------------------------------

    @Test
    void lockRepositoryPolicyMapsWithoutTheSubtypeOnlyPenaltyValue() throws Exception {
        LockRepositoryPolicy policy = new LockRepositoryPolicy();
        policy.setId(11L);
        policy.setSubmissionLimit(3);
        policy.setActive(false);

        JsonNode json = objectMapper.valueToTree(SubmissionPolicyDTO.of(policy));

        assertThat(json.has("type")).isTrue();
        assertThat(json.get("type").asText()).isEqualTo("lock_repository");
        assertThat(json.get("id").asLong()).isEqualTo(11L);
        assertThat(json.get("submissionLimit").asInt()).isEqualTo(3);
        // NON_EMPTY keeps a false Boolean: only nulls and empty containers are dropped
        assertThat(json.has("active")).isTrue();
        assertThat(json.get("active").asBoolean()).isFalse();
        // exceedingPenalty only exists on the penalty subtype and must stay absent, exactly as today
        assertThat(json.has("exceedingPenalty")).isFalse();
    }

    @Test
    void submissionPenaltyPolicyMapsLimitAndPenalty() throws Exception {
        SubmissionPenaltyPolicy policy = new SubmissionPenaltyPolicy();
        policy.setId(12L);
        policy.setSubmissionLimit(5);
        policy.setExceedingPenalty(2.5);
        policy.setActive(true);

        JsonNode json = objectMapper.valueToTree(SubmissionPolicyDTO.of(policy));

        assertThat(json.get("type").asText()).isEqualTo("submission_penalty");
        assertThat(json.get("submissionLimit").asInt()).isEqualTo(5);
        assertThat(json.get("exceedingPenalty").asDouble()).isEqualTo(2.5);
        assertThat(json.get("active").asBoolean()).isTrue();
    }

    @Test
    void submissionPolicyRoundTripsThroughJacksonForBothTypes() throws Exception {
        LockRepositoryPolicy lockPolicy = new LockRepositoryPolicy();
        lockPolicy.setId(21L);
        lockPolicy.setSubmissionLimit(4);
        lockPolicy.setActive(false);
        SubmissionPenaltyPolicy penaltyPolicy = new SubmissionPenaltyPolicy();
        penaltyPolicy.setId(22L);
        penaltyPolicy.setSubmissionLimit(6);
        penaltyPolicy.setExceedingPenalty(1.5);
        penaltyPolicy.setActive(true);

        SubmissionPolicyDTO lockDto = objectMapper.readValue(objectMapper.writeValueAsString(SubmissionPolicyDTO.of(lockPolicy)), SubmissionPolicyDTO.class);
        SubmissionPolicyDTO penaltyDto = objectMapper.readValue(objectMapper.writeValueAsString(SubmissionPolicyDTO.of(penaltyPolicy)), SubmissionPolicyDTO.class);

        assertThat(lockDto).isEqualTo(new SubmissionPolicyDTO(21L, "lock_repository", 4, null, false));
        assertThat(penaltyDto).isEqualTo(new SubmissionPolicyDTO(22L, "submission_penalty", 6, 1.5, true));
    }

    @Test
    void submissionPolicyToEntityCopiesTheIdThrough() {
        SubmissionPolicy lockPolicy = new SubmissionPolicyDTO(31L, "lock_repository", 3, null, true).toEntity();
        SubmissionPolicy penaltyPolicy = new SubmissionPolicyDTO(32L, "submission_penalty", 7, 4.0, false).toEntity();

        assertThat(lockPolicy).isInstanceOf(LockRepositoryPolicy.class);
        assertThat(lockPolicy.getId()).isEqualTo(31L);
        assertThat(lockPolicy.getSubmissionLimit()).isEqualTo(3);
        assertThat(lockPolicy.isActive()).isTrue();
        assertThat(lockPolicy.getProgrammingExercise()).isNull();

        assertThat(penaltyPolicy).isInstanceOf(SubmissionPenaltyPolicy.class);
        assertThat(penaltyPolicy.getId()).isEqualTo(32L);
        assertThat(((SubmissionPenaltyPolicy) penaltyPolicy).getExceedingPenalty()).isEqualTo(4.0);
        assertThat(penaltyPolicy.isActive()).isFalse();
    }

    @Test
    void submissionPolicyToEntityRejectsAnUnknownType() {
        SubmissionPolicyDTO dto = new SubmissionPolicyDTO(1L, "not_a_policy", 1, null, true);

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(dto::toEntity);
    }

    // --- ProgrammingExerciseResponseDTO: lazy guards and nested graphs ---------------------------------------------

    @Test
    void responseDtoMapsUninitializedLazySlotsToNull() {
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setId(42L);
        exercise.setTitle("Detached exercise");
        exercise.setCategories(uninitializedSet());
        exercise.setCompetencyLinks(uninitializedSet());
        exercise.setStudentParticipations(uninitializedSet());
        exercise.setAuxiliaryRepositories(uninitializedList());

        assertThatCode(() -> ProgrammingExerciseResponseDTO.of(exercise)).doesNotThrowAnyException();

        ProgrammingExerciseResponseDTO dto = ProgrammingExerciseResponseDTO.of(exercise);
        assertThat(dto).isNotNull();
        assertThat(dto.id()).isEqualTo(42L);
        assertThat(dto.type()).isEqualTo("programming");
        assertThat(dto.categories()).isNull();
        assertThat(dto.competencyLinks()).isNull();
        assertThat(dto.studentParticipations()).isNull();
        assertThat(dto.auxiliaryRepositories()).isNull();
        assertThat(dto.course()).isNull();
        assertThat(dto.exerciseGroup()).isNull();
        assertThat(dto.buildConfig()).isNull();
        assertThat(dto.submissionPolicy()).isNull();
        assertThat(dto.templateParticipation()).isNull();
        assertThat(dto.solutionParticipation()).isNull();
        assertThat(dto.gradingInstructionFeedbackUsed()).isNull();
    }

    @Test
    void responseDtoOfCourseExerciseCarriesTheNestedCourse() {
        Course course = new Course();
        course.setId(5L);
        course.setTitle("Software Engineering");
        course.setShortName("SE");
        course.setPresentationScore(2);

        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setId(6L);
        exercise.setTitle("Course exercise");
        exercise.setCategories(Set.of("[\"easy\"]"));
        exercise.setCourse(course);

        ProgrammingExerciseResponseDTO dto = ProgrammingExerciseResponseDTO.of(exercise, true);

        assertThat(dto.exerciseGroup()).isNull();
        assertThat(dto.course()).isNotNull();
        assertThat(dto.course().id()).isEqualTo(5L);
        assertThat(dto.course().title()).isEqualTo("Software Engineering");
        assertThat(dto.course().shortName()).isEqualTo("SE");
        assertThat(dto.course().presentationScore()).isEqualTo(2);
        assertThat(dto.categories()).containsExactly("[\"easy\"]");
        assertThat(dto.gradingInstructionFeedbackUsed()).isTrue();
    }

    /**
     * {@code exerciseVariantGroup} is a LAZY association. An unfetched proxy has to map to {@code null} rather than
     * throw, because the read paths that do not fetch-join it must keep working — and the entity wire omits it there
     * too, so the DTO stays byte-for-byte compatible with what the client used to receive.
     */
    @Test
    void responseDtoMapsTheVariantGroupAndDegradesAnUnfetchedOneToNull() {
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setId(7L);

        assertThat(ProgrammingExerciseResponseDTO.of(exercise).exerciseVariantGroup()).isNull();

        ExerciseVariantGroup group = new ExerciseVariantGroup();
        group.setId(11L);
        group.setTitle("Loop variants");
        group.setMaxPoints(12.0);
        group.setDueDate(ZonedDateTime.now().plusDays(2));
        exercise.setExerciseVariantGroup(group);

        var dto = ProgrammingExerciseResponseDTO.of(exercise).exerciseVariantGroup();
        assertThat(dto).isNotNull();
        assertThat(dto.id()).isEqualTo(11L);
        assertThat(dto.title()).isEqualTo("Loop variants");
        assertThat(dto.maxPoints()).isEqualTo(12.0);
        assertThat(dto.dueDate()).isEqualTo(group.getDueDate());
        // The member collection must never travel: it would drag the whole exercise graph back into the response.
        assertThat(ProgrammingExerciseListItemDTO.of(exercise).exerciseVariantGroup()).isEqualTo(dto);
    }

    @Test
    void responseDtoOfExamExerciseCarriesTheExerciseGroupExamCourseChain() {
        Course course = new Course();
        course.setId(9L);
        course.setTitle("Exam course");

        Exam exam = new Exam();
        exam.setId(3L);
        exam.setTitle("Endterm");
        exam.setTestExam(false);
        exam.setNumberOfCorrectionRoundsInExam(2);
        exam.setExampleSolutionPublicationDate(ZonedDateTime.now().plusDays(1));
        exam.setCourse(course);

        ExerciseGroup exerciseGroup = new ExerciseGroup();
        exerciseGroup.setId(4L);
        exerciseGroup.setExam(exam);

        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setId(10L);
        exercise.setTitle("Exam exercise");
        exercise.setExerciseGroup(exerciseGroup);

        ProgrammingExerciseResponseDTO dto = ProgrammingExerciseResponseDTO.of(exercise);

        assertThat(dto.course()).isNull();
        assertThat(dto.exerciseGroup()).isNotNull();
        assertThat(dto.exerciseGroup().id()).isEqualTo(4L);
        assertThat(dto.exerciseGroup().exam()).isNotNull();
        assertThat(dto.exerciseGroup().exam().id()).isEqualTo(3L);
        assertThat(dto.exerciseGroup().exam().title()).isEqualTo("Endterm");
        assertThat(dto.exerciseGroup().exam().testExam()).isFalse();
        assertThat(dto.exerciseGroup().exam().numberOfCorrectionRoundsInExam()).isEqualTo(2);
        assertThat(dto.exerciseGroup().exam().exampleSolutionPublicationDate()).isEqualTo(exam.getExampleSolutionPublicationDate());
        assertThat(dto.exerciseGroup().exam().course()).isNotNull();
        assertThat(dto.exerciseGroup().exam().course().id()).isEqualTo(9L);
        assertThat(dto.exerciseGroup().exam().course().title()).isEqualTo("Exam course");
    }

    // --- Request DTOs: write-side defaults the entity does not provide ---------------------------------------------

    /**
     * Variant group membership is owned by {@code PUT api/exercise/courses/{courseId}/exercises/{exerciseId}/variant-group},
     * which rejects exam exercises, checks the exercise's course against the path course and adopts the group's shared
     * timeline. The create and import request records therefore do not bind {@code exerciseVariantGroup} at all, so a
     * body that carries one cannot smuggle a member past those guards. The programming update path is closed the same
     * way, since {@code UpdateProgrammingExerciseDTO} has no such component either.
     */
    @Test
    void createAndImportRequestsIgnoreAVariantGroupInTheBody() throws Exception {
        String body = """
                {"title":"Smuggled","shortName":"SMG","exerciseVariantGroup":{"id":42,"title":"Loop variants"}}""";

        CreateProgrammingExerciseDTO createRequest = objectMapper.readValue(body, CreateProgrammingExerciseDTO.class);
        ImportProgrammingExerciseRequestDTO importRequest = objectMapper.readValue(body, ImportProgrammingExerciseRequestDTO.class);

        assertThat(createRequest.title()).isEqualTo("Smuggled");
        assertThat(importRequest.title()).isEqualTo("Smuggled");
        assertThat(createRequest.toEntity().getExerciseVariantGroup()).isNull();
        assertThat(importRequest.toEntity().getExerciseVariantGroup()).isNull();
    }

    @Test
    void createRequestToEntityPreservesTheFieldsWithoutEntityDefaults() {
        CreateProgrammingExerciseDTO dto = new CreateProgrammingExerciseDTO(null, "New exercise", "NEW", "new-exercise", "de.tum.in", "problem", "instructions",
                Set.of("[\"cat\"]"), null, ExerciseMode.TEAM, new TeamAssignmentConfigDTO(null, 2, 4), null, null, null, null, null, null, null, null, null,
                AssessmentType.SEMI_AUTOMATIC, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        ProgrammingExercise exercise = dto.toEntity();

        assertThat(exercise.getAssessmentType()).isEqualTo(AssessmentType.SEMI_AUTOMATIC);
        assertThat(exercise.getMode()).isEqualTo(ExerciseMode.TEAM);
        assertThat(exercise.isTeamMode()).isTrue();
        assertThat(exercise.getTeamAssignmentConfig()).isNotNull();
        assertThat(exercise.getTeamAssignmentConfig().getMinTeamSize()).isEqualTo(2);
        assertThat(exercise.getTeamAssignmentConfig().getMaxTeamSize()).isEqualTo(4);
        assertThat(exercise.getCategories()).containsExactly("[\"cat\"]");
        assertThat(exercise.getBuildConfig()).isNull();
        assertThat(exercise.getSubmissionPolicy()).isNull();
    }

    @Test
    void createRequestToEntityKeepsAClientSuppliedIdSoTheIdExistsCheckStillFires() {
        CreateProgrammingExerciseDTO dto = new CreateProgrammingExerciseDTO(4711L, "New exercise", "NEW", null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null);

        assertThat(dto.toEntity().getId()).isEqualTo(4711L);
    }

    @Test
    void importRequestToEntityPreservesFieldsAndMapsNullCollectionsToEmpty() {
        ImportProgrammingExerciseRequestDTO dto = new ImportProgrammingExerciseRequestDTO(77L, "Imported", "IMP", null, "de.tum.in", "problem", null, null, null, ExerciseMode.TEAM,
                new TeamAssignmentConfigDTO(null, 3, 5), null, null, null, null, null, null, null, null, null, AssessmentType.MANUAL, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        ProgrammingExercise exercise = dto.toEntity();

        assertThat(exercise.getId()).isEqualTo(77L);
        assertThat(exercise.getAssessmentType()).isEqualTo(AssessmentType.MANUAL);
        assertThat(exercise.getMode()).isEqualTo(ExerciseMode.TEAM);
        assertThat(exercise.getTeamAssignmentConfig()).isNotNull();
        assertThat(exercise.getTeamAssignmentConfig().getMaxTeamSize()).isEqualTo(5);
        assertThat(exercise.getCategories()).isEmpty();
        assertThat(exercise.getGradingCriteria()).isEmpty();
        assertThat(exercise.getAuxiliaryRepositories()).isEmpty();
        assertThat(exercise.getCompetencyLinks()).isEmpty();
    }

    /**
     * {@code Exercise.presentationScoreEnabled} is an initialized {@code false} entity default that the previous
     * entity request binding left alone for an absent JSON key. Both request mappers must guard the assignment, or a
     * body without the key persists {@code null} where it used to persist {@code false}.
     */
    @Test
    void requestToEntityKeepsThePresentationScoreEnabledDefaultWhenTheKeyIsAbsent() {
        CreateProgrammingExerciseDTO createDto = new CreateProgrammingExerciseDTO(null, "New exercise", "NEW", null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null);
        ImportProgrammingExerciseRequestDTO importDto = new ImportProgrammingExerciseRequestDTO(null, "Imported", "IMP", null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null);

        assertThat(createDto.toEntity().getPresentationScoreEnabled()).isFalse();
        assertThat(importDto.toEntity().getPresentationScoreEnabled()).isFalse();
        assertThat(createDto.presentationScoreEnabled()).isNull();
    }

    @Test
    void importRequestToEntityKeepsTheCategorySetOfEncodedStrings() {
        ImportProgrammingExerciseRequestDTO dto = new ImportProgrammingExerciseRequestDTO(null, "Imported", "IMP", null, null, null, null, Set.of("[\"a\"]", "[\"b\"]"), null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null);

        assertThat(dto.toEntity().getCategories()).containsExactlyInAnyOrder("[\"a\"]", "[\"b\"]");
    }

    // --- ResultDTO.ofNested ---------------------------------------------------------------------------------------

    @Test
    void ofNestedOmitsSubmissionAndParticipationAndToleratesANullSubmission() {
        Feedback feedback = new Feedback();
        feedback.setId(2L);
        feedback.setText("test1");
        feedback.setDetailText("passed");
        feedback.setCredits(1.0);

        Result result = new Result();
        result.setId(1L);
        result.setScore(100.0);
        result.setSubmission(null);

        ResultDTO dto = ResultDTO.ofNested(result, List.of(feedback));

        assertThat(dto.id()).isEqualTo(1L);
        assertThat(dto.score()).isEqualTo(100.0);
        assertThat(dto.submission()).isNull();
        assertThat(dto.participation()).isNull();
        assertThat(dto.feedbacks()).hasSize(1);
        assertThat(dto.feedbacks().getFirst().id()).isEqualTo(2L);
        assertThat(dto.feedbacks().getFirst().text()).isEqualTo("test1");
    }

    @Test
    void ofNestedAcceptsANullFeedbackCollection() {
        Result result = new Result();
        result.setId(3L);

        ResultDTO dto = ResultDTO.ofNested(result, null);

        assertThat(dto.id()).isEqualTo(3L);
        assertThat(dto.feedbacks()).isNull();
    }

    /**
     * A Hibernate collection that reports itself as not initialized, the state every lazy relation is in on a detached
     * entity. Touching it would throw, so a mapper that forgets its guard fails loudly.
     */
    @SuppressWarnings("unchecked")
    private static <T> Set<T> uninitializedSet() {
        PersistentSet<T> set = mock(PersistentSet.class);
        when(set.wasInitialized()).thenReturn(false);
        return set;
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> uninitializedList() {
        PersistentBag<T> bag = mock(PersistentBag.class);
        when(bag.wasInitialized()).thenReturn(false);
        return bag;
    }
}
