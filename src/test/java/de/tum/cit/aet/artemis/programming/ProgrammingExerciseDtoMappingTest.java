package de.tum.cit.aet.artemis.programming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.hibernate.Hibernate;
import org.hibernate.collection.spi.PersistentBag;
import org.hibernate.collection.spi.PersistentSet;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.proxy.LazyInitializer;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.assessment.domain.GradingInstruction;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.domain.Visibility;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVariantGroup;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.dto.TeamAssignmentConfigDTO;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCaseType;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.LockRepositoryPolicy;
import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.SubmissionPenaltyPolicy;
import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.SubmissionPolicy;
import de.tum.cit.aet.artemis.programming.dto.CreateProgrammingExerciseDTO;
import de.tum.cit.aet.artemis.programming.dto.ImportProgrammingExerciseRequestDTO;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseListItemDTO;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseResponseDTO;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingParticipationLatestResultDTO;
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

        // A missing guard would throw here: every lazy slot above reports itself as not initialized.
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
        // The entity always serialized its transient flag, so the single-argument overload carries the default too.
        assertThat(dto.gradingInstructionFeedbackUsed()).isFalse();
    }

    @Test
    void responseDtoOfCourseExerciseCarriesTheNestedCourse() {
        Course course = new Course();
        course.setId(5L);
        course.setTitle("Software Engineering");
        course.setShortName("SE");
        course.setPresentationScore(2);
        course.setColor("#691b0b");
        course.setCourseIcon("/api/core/files/course/icons/5/icon.png");
        course.setEnrollmentConfirmationMessage("Welcome to the course");
        course.setCourseArchivePath("Course-Archive-5.zip");
        course.setMaxPoints(120);
        course.setTimeZone("Europe/Berlin");
        course.setCourseInformationSharingMessagingCodeOfConduct("Be excellent to each other");

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
        // The optional course columns the entity put on the wire whenever an instructor had set them.
        assertThat(dto.course().color()).isEqualTo("#691b0b");
        assertThat(dto.course().courseIcon()).isEqualTo("/api/core/files/course/icons/5/icon.png");
        assertThat(dto.course().enrollmentConfirmationMessage()).isEqualTo("Welcome to the course");
        assertThat(dto.course().courseArchivePath()).isEqualTo("Course-Archive-5.zip");
        assertThat(dto.course().maxPoints()).isEqualTo(120);
        assertThat(dto.course().timeZone()).isEqualTo("Europe/Berlin");
        assertThat(dto.course().courseInformationSharingMessagingCodeOfConduct()).isEqualTo("Be excellent to each other");
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
        exam.setVisibleDate(ZonedDateTime.now().minusDays(1));
        exam.setStartDate(ZonedDateTime.now());
        exam.setEndDate(ZonedDateTime.now().plusHours(2));
        exam.setWorkingTime(7200);
        exam.setExamMaxPoints(37);
        exam.setExaminer("Prof. Krusche");
        exam.setModuleNumber("IN0006");
        exam.setCourse(course);

        ExerciseGroup exerciseGroup = new ExerciseGroup();
        exerciseGroup.setId(4L);
        exerciseGroup.setTitle("Group 1");
        exerciseGroup.setIsMandatory(true);
        exerciseGroup.setExam(exam);

        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setId(10L);
        exercise.setTitle("Exam exercise");
        exercise.setExerciseGroup(exerciseGroup);

        ProgrammingExerciseResponseDTO dto = ProgrammingExerciseResponseDTO.of(exercise);

        assertThat(dto.course()).isNull();
        assertThat(dto.exerciseGroup()).isNotNull();
        assertThat(dto.exerciseGroup().id()).isEqualTo(4L);
        assertThat(dto.exerciseGroup().title()).isEqualTo("Group 1");
        assertThat(dto.exerciseGroup().isMandatory()).isTrue();
        assertThat(dto.exerciseGroup().exam()).isNotNull();
        assertThat(dto.exerciseGroup().exam().id()).isEqualTo(3L);
        assertThat(dto.exerciseGroup().exam().title()).isEqualTo("Endterm");
        assertThat(dto.exerciseGroup().exam().testExam()).isFalse();
        assertThat(dto.exerciseGroup().exam().numberOfCorrectionRoundsInExam()).isEqualTo(2);
        assertThat(dto.exerciseGroup().exam().exampleSolutionPublicationDate()).isEqualTo(exam.getExampleSolutionPublicationDate());
        // The conduction dates and the exam metadata the IntelliJ plugin sees on the SCORPIO route.
        assertThat(dto.exerciseGroup().exam().visibleDate()).isEqualTo(exam.getVisibleDate());
        assertThat(dto.exerciseGroup().exam().startDate()).isEqualTo(exam.getStartDate());
        assertThat(dto.exerciseGroup().exam().endDate()).isEqualTo(exam.getEndDate());
        assertThat(dto.exerciseGroup().exam().workingTime()).isEqualTo(7200);
        assertThat(dto.exerciseGroup().exam().examMaxPoints()).isEqualTo(37);
        assertThat(dto.exerciseGroup().exam().examiner()).isEqualTo("Prof. Krusche");
        assertThat(dto.exerciseGroup().exam().moduleNumber()).isEqualTo("IN0006");
        assertThat(dto.exerciseGroup().exam().course()).isNotNull();
        assertThat(dto.exerciseGroup().exam().course().id()).isEqualTo(9L);
        assertThat(dto.exerciseGroup().exam().course().title()).isEqualTo("Exam course");
    }

    /**
     * Not every read path fetch-joins the whole exam chain, so each level has to degrade on its own. A detached
     * exercise whose group is still a proxy must map to no group at all rather than to a blank one.
     */
    @Test
    void responseDtoOmitsTheExerciseGroupWhenTheGroupIsStillAProxy() {
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setId(10L);
        exercise.setExerciseGroup(uninitializedProxy(ExerciseGroup.class));

        ProgrammingExerciseResponseDTO dto = ProgrammingExerciseResponseDTO.of(exercise);

        assertThat(dto.exerciseGroup()).isNull();
    }

    /**
     * The group is loaded but its exam is not: the group reference still has to reach the client, without the exam.
     * Touching {@code exam.getTitle()} here would force a lazy load outside a session.
     */
    @Test
    void responseDtoOmitsTheExamWhenOnlyTheExerciseGroupIsLoaded() {
        ExerciseGroup exerciseGroup = new ExerciseGroup();
        exerciseGroup.setId(4L);
        exerciseGroup.setTitle("Group 1");
        exerciseGroup.setExam(uninitializedProxy(Exam.class));

        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setId(10L);
        exercise.setExerciseGroup(exerciseGroup);

        ProgrammingExerciseResponseDTO dto = ProgrammingExerciseResponseDTO.of(exercise);

        assertThat(dto.exerciseGroup()).isNotNull();
        assertThat(dto.exerciseGroup().id()).isEqualTo(4L);
        // The group's own scalars survive the missing exam; only the exam sub-object drops out.
        assertThat(dto.exerciseGroup().title()).isEqualTo("Group 1");
        assertThat(dto.exerciseGroup().isMandatory()).isTrue();
        assertThat(dto.exerciseGroup().exam()).isNull();
    }

    /**
     * One level further down: the exam is loaded, its course is not. The exam fields the client reads must survive,
     * only the course drops out.
     */
    @Test
    void responseDtoOmitsTheExamCourseWhenTheCourseIsStillAProxy() {
        Exam exam = new Exam();
        exam.setId(3L);
        exam.setTitle("Endterm");
        exam.setNumberOfCorrectionRoundsInExam(2);
        exam.setCourse(uninitializedProxy(Course.class));

        ExerciseGroup exerciseGroup = new ExerciseGroup();
        exerciseGroup.setId(4L);
        exerciseGroup.setExam(exam);

        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setId(10L);
        exercise.setExerciseGroup(exerciseGroup);

        ProgrammingExerciseResponseDTO dto = ProgrammingExerciseResponseDTO.of(exercise);

        assertThat(dto.exerciseGroup()).isNotNull();
        assertThat(dto.exerciseGroup().exam()).isNotNull();
        assertThat(dto.exerciseGroup().exam().id()).isEqualTo(3L);
        assertThat(dto.exerciseGroup().exam().title()).isEqualTo("Endterm");
        assertThat(dto.exerciseGroup().exam().numberOfCorrectionRoundsInExam()).isEqualTo(2);
        assertThat(dto.exerciseGroup().exam().course()).isNull();
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

    // --- ProgrammingParticipationLatestResultDTO (the SCORPIO latest-result route) --------------------------------

    @Test
    void latestResultFeedbackCarriesTheGradingInstructionAndTheWholeTestCase() {
        GradingInstruction instruction = new GradingInstruction();
        instruction.setId(7L);
        instruction.setCredits(1.5);
        instruction.setGradingScale("good");
        instruction.setInstructionDescription("description");
        instruction.setFeedback("proposed feedback");
        instruction.setUsageCount(2);

        ProgrammingExerciseTestCase testCase = new ProgrammingExerciseTestCase();
        testCase.setId(8L);
        testCase.setTestName("test1");
        testCase.setWeight(3.0);
        testCase.setBonusMultiplier(2.0);
        testCase.setBonusPoints(1.0);
        testCase.setActive(true);
        testCase.setVisibility(Visibility.ALWAYS);

        Feedback feedback = new Feedback();
        feedback.setId(9L);
        feedback.setType(FeedbackType.MANUAL);
        feedback.setGradingInstruction(instruction);
        feedback.setTestCase(testCase);

        var dto = ProgrammingParticipationLatestResultDTO.FeedbackRefDTO.of(feedback);

        assertThat(dto.gradingInstruction()).isNotNull();
        assertThat(dto.gradingInstruction().credits()).isEqualTo(1.5);
        assertThat(dto.gradingInstruction().gradingScale()).isEqualTo("good");
        assertThat(dto.gradingInstruction().usageCount()).isEqualTo(2);
        assertThat(dto.testCase()).isNotNull();
        // The shared ResultDTO.TestCaseDTO stops at id and testName; this route kept the rest of the entity payload.
        assertThat(dto.testCase().weight()).isEqualTo(3.0);
        assertThat(dto.testCase().bonusMultiplier()).isEqualTo(2.0);
        assertThat(dto.testCase().bonusPoints()).isEqualTo(1.0);
        assertThat(dto.testCase().active()).isTrue();
        assertThat(dto.testCase().visibility()).isEqualTo(Visibility.ALWAYS);
        assertThat(dto.testCase().type()).isEqualTo(ProgrammingExerciseTestCaseType.DEFAULT);
    }

    @Test
    void latestResultFeedbackGuardsUninitializedAssociations() {
        Feedback feedback = new Feedback();
        feedback.setId(1L);
        feedback.setTestCase(uninitializedProxy(ProgrammingExerciseTestCase.class));
        feedback.setGradingInstruction(uninitializedProxy(GradingInstruction.class));

        var dto = ProgrammingParticipationLatestResultDTO.FeedbackRefDTO.of(feedback);

        assertThat(dto.testCase()).isNull();
        assertThat(dto.gradingInstruction()).isNull();
    }

    @Test
    void latestResultParticipationCarriesTheRepositoryAndParticipantFields() {
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setId(4L);
        User student = new User();
        student.setId(11L);
        student.setLogin("student1");
        student.setFirstName("First");
        student.setLastName("Last");

        ProgrammingExerciseStudentParticipation participation = new ProgrammingExerciseStudentParticipation();
        participation.setId(3L);
        participation.setExercise(exercise);
        participation.setParticipant(student);
        participation.setInitializationState(InitializationState.INITIALIZED);
        participation.setInitializationDate(ZonedDateTime.now().minusDays(1));
        participation.setIndividualDueDate(ZonedDateTime.now().plusDays(1));
        participation.setTestRun(true);
        participation.setPresentationScore(42.0);
        participation.setRepositoryUri("http://user@artemis.local/git/PROJ/proj-student1.git");
        participation.setBuildPlanId("PROJ-STUDENT1");
        participation.setBranch("main");

        var dto = ProgrammingParticipationLatestResultDTO.ParticipationRefDTO.of(participation);

        assertThat(dto.type()).isEqualTo("programming");
        assertThat(dto.initializationState()).isEqualTo(InitializationState.INITIALIZED);
        assertThat(dto.initializationDate()).isNotNull();
        assertThat(dto.individualDueDate()).isNotNull();
        assertThat(dto.testRun()).isTrue();
        assertThat(dto.presentationScore()).isEqualTo(42.0);
        assertThat(dto.repositoryUri()).isEqualTo("http://user@artemis.local/git/PROJ/proj-student1.git");
        assertThat(dto.buildPlanId()).isEqualTo("PROJ-STUDENT1");
        assertThat(dto.branch()).isEqualTo("main");
        // The entity computed this one by stripping the user info from the authority.
        assertThat(dto.userIndependentRepositoryUri()).isEqualTo("http://artemis.local/git/PROJ/proj-student1.git");
        assertThat(dto.participantIdentifier()).isEqualTo("student1");
        assertThat(dto.participantName()).isEqualTo("First Last");
        assertThat(dto.student()).isNotNull();
        assertThat(dto.student().login()).isEqualTo("student1");
        assertThat(dto.team()).isNull();
        // A student participation serialized the exercise under `exercise`, so the other slot stays empty.
        assertThat(dto.exercise()).isNotNull();
        assertThat(dto.programmingExercise()).isNull();
    }

    @Test
    void latestResultParticipationPutsATemplateExerciseInTheProgrammingExerciseSlot() {
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setId(4L);
        TemplateProgrammingExerciseParticipation participation = new TemplateProgrammingExerciseParticipation();
        participation.setId(5L);
        participation.setProgrammingExercise(exercise);

        var dto = ProgrammingParticipationLatestResultDTO.ParticipationRefDTO.of(participation);

        assertThat(dto.type()).isEqualTo("template");
        // A template participation @JsonIgnores getExercise() and serialized programmingExercise instead.
        assertThat(dto.exercise()).isNull();
        assertThat(dto.programmingExercise()).isNotNull();
        assertThat(dto.programmingExercise().id()).isEqualTo(4L);
        assertThat(dto.student()).isNull();
        assertThat(dto.branch()).isNull();
    }

    @Test
    void latestResultTeamParticipationGuardsAnUninitializedStudentCollection() {
        Team team = new Team();
        team.setId(6L);
        team.setName("Team 1");
        team.setShortName("t1");
        team.setStudents(uninitializedSet());

        StudentParticipation participation = new StudentParticipation();
        participation.setId(7L);
        participation.setParticipant(team);

        var dto = ProgrammingParticipationLatestResultDTO.ParticipationRefDTO.of(participation);

        assertThat(dto.student()).isNull();
        assertThat(dto.team()).isNotNull();
        assertThat(dto.team().shortName()).isEqualTo("t1");
        assertThat(dto.team().participantIdentifier()).isEqualTo("t1");
        assertThat(dto.team().students()).isNull();
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

    /**
     * An entity proxy that reports itself as not initialized, the state every lazy to-one relation is in on a
     * detached entity. A mapper that reads through it instead of guarding produces a blank sub-object, which the
     * assertions above catch.
     *
     * @param type the entity class to proxy
     * @return the proxy
     */
    private static <T> T uninitializedProxy(Class<T> type) {
        T proxy = mock(type, withSettings().extraInterfaces(HibernateProxy.class));
        LazyInitializer lazyInitializer = mock(LazyInitializer.class);
        when(lazyInitializer.isUninitialized()).thenReturn(true);
        // Hibernate reaches the lazy initializer over the default asHibernateProxy(), which a mock would answer with
        // null, so both steps of that lookup have to be stubbed.
        doReturn(proxy).when((HibernateProxy) proxy).asHibernateProxy();
        doReturn(lazyInitializer).when((HibernateProxy) proxy).getHibernateLazyInitializer();
        assertThat(Hibernate.isInitialized(proxy)).isFalse();
        return proxy;
    }
}
