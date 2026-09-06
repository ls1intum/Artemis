package de.tum.cit.aet.artemis.modeling;

import static de.tum.cit.aet.artemis.core.util.TestResourceUtils.HalfSecond;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertExerciseNotInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertModelingExerciseExistsInWeaviate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.ExampleSubmission;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.assessment.domain.GradingInstruction;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.domain.TutorParticipation;
import de.tum.cit.aet.artemis.assessment.repository.FeedbackRepository;
import de.tum.cit.aet.artemis.assessment.repository.GradingCriterionRepository;
import de.tum.cit.aet.artemis.assessment.test_repository.TutorParticipationTestRepository;
import de.tum.cit.aet.artemis.assessment.util.GradingCriterionUtil;
import de.tum.cit.aet.artemis.atlas.competency.util.CompetencyUtilService;
import de.tum.cit.aet.artemis.atlas.connector.AtlasMLRequestMockProvider;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.communication.util.ConversationUtilService;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import de.tum.cit.aet.artemis.core.util.PageableSearchUtilService;
import de.tum.cit.aet.artemis.core.util.TestResourceUtils;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.dto.CourseForDashboardDTO;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exam.util.InvalidExamExerciseDatesArgumentProvider;
import de.tum.cit.aet.artemis.exam.util.InvalidExamExerciseDatesArgumentProvider.InvalidExamExerciseDateConfiguration;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.TeamAssignmentConfig;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.SubmissionTestRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseIntegrationTestService;
import de.tum.cit.aet.artemis.modeling.domain.DiagramType;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.modeling.dto.ImportModelingExerciseDTO;
import de.tum.cit.aet.artemis.modeling.dto.ModelingExerciseListItemDTO;
import de.tum.cit.aet.artemis.modeling.dto.ModelingExerciseResponseDTO;
import de.tum.cit.aet.artemis.modeling.dto.UpdateModelingExerciseDTO;
import de.tum.cit.aet.artemis.modeling.test_repository.ModelingExerciseTestRepository;
import de.tum.cit.aet.artemis.modeling.util.ModelingExerciseFactory;
import de.tum.cit.aet.artemis.modeling.util.ModelingExerciseUtilService;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismDetectionConfig;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorParticipationStatus;

class ModelingExerciseIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "modelingexerciseintegration";

    /** Tutor/instructor not enrolled in the test course; exercises the wrong-course branches. */
    private static final String OTHER_PREFIX = "modelingexerciseother";

    @Autowired
    private ModelingExerciseUtilService modelingExerciseUtilService;

    @Autowired
    private ModelingExerciseTestRepository modelingExerciseTestRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private SubmissionTestRepository submissionRepository;

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private GradingCriterionRepository gradingCriterionRepository;

    @Autowired
    private StudentParticipationTestRepository studentParticipationRepository;

    @Autowired
    private ExerciseIntegrationTestService exerciseIntegrationTestService;

    @Autowired
    private TutorParticipationTestRepository tutorParticipationRepository;

    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private PageableSearchUtilService pageableSearchUtilService;

    @Autowired
    private CompetencyUtilService competencyUtilService;

    @Autowired
    private ConversationUtilService conversationUtilService;

    @Autowired
    private Optional<AtlasMLRequestMockProvider> atlasMLRequestMockProvider;

    @Autowired
    private FeatureToggleService featureToggleService;

    @Autowired(required = false)
    private de.tum.cit.aet.artemis.globalsearch.service.WeaviateService weaviateService;

    private ModelingExercise classExercise;

    private Set<GradingCriterion> gradingCriteria;

    private Competency competency;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 0, 1);
        userUtilService.addUsers(OTHER_PREFIX, 0, 1, 0, 1); // outsider tutor, instructor — never enrolled in course

        Course course = modelingExerciseUtilService.addEnrolledCourseWithOneModelingExercise("ClassDiagram", TEST_PREFIX);
        classExercise = (ModelingExercise) course.getExercises().iterator().next();

        competency = competencyUtilService.createCompetency(course);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "user1", roles = "USER")
    void testGetModelingExercise_asStudent_Forbidden() throws Exception {
        request.get("/api/modeling/modeling-exercises/" + classExercise.getId(), HttpStatus.FORBIDDEN, ModelingExercise.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetModelingExercise_asTA() throws Exception {
        conversationUtilService.addChannelToExercise(classExercise);

        ModelingExerciseResponseDTO receivedModelingExercise = request.get("/api/modeling/modeling-exercises/" + classExercise.getId(), HttpStatus.OK,
                ModelingExerciseResponseDTO.class);
        assertThat(receivedModelingExercise.id()).isNotNull();
    }

    @Test
    @WithMockUser(username = OTHER_PREFIX + "tutor1", roles = "TA")
    void testGetModelingExercise_tutorNotInCourse() throws Exception {
        request.get("/api/modeling/modeling-exercises/" + classExercise.getId(), HttpStatus.FORBIDDEN, ModelingExercise.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetModelingExercise_setGradingInstructionFeedbackUsed() throws Exception {
        gradingCriteria = exerciseUtilService.addGradingInstructionsToExercise(classExercise);
        gradingCriterionRepository.saveAll(gradingCriteria);
        Feedback feedback = new Feedback();
        feedback.setGradingInstruction(GradingCriterionUtil.findAnyInstructionWhere(gradingCriteria, instruction -> true).orElseThrow());
        feedbackRepository.save(feedback);

        conversationUtilService.addChannelToExercise(classExercise);

        ModelingExerciseResponseDTO receivedModelingExercise = request.get("/api/modeling/modeling-exercises/" + classExercise.getId(), HttpStatus.OK,
                ModelingExerciseResponseDTO.class);

        assertThat(receivedModelingExercise.gradingInstructionFeedbackUsed()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetModelingExerciseForCourse_asTA() throws Exception {
        request.get("/api/modeling/courses/" + classExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/modeling-exercises", HttpStatus.OK, List.class);
    }

    @Test
    @WithMockUser(username = OTHER_PREFIX + "tutor1", roles = "TA")
    void testGetModelingExerciseForCourse_tutorNotInCourse() throws Exception {
        request.get("/api/modeling/courses/" + classExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/modeling-exercises", HttpStatus.FORBIDDEN, List.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "exercise-new-modeling-exercise", "" })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateModelingExercise_asInstructor(String channelName) throws Exception {
        ModelingExercise modelingExercise = ModelingExerciseFactory.createModelingExercise(classExercise.getCourseViaExerciseGroupOrCourseMember().getId());
        courseUtilService.enableMessagingForCourse(modelingExercise.getCourseViaExerciseGroupOrCourseMember());
        gradingCriteria = exerciseUtilService.addGradingInstructionsToExercise(modelingExercise);
        modelingExercise.setTitle("new modeling exercise");
        modelingExercise.setChannelName(channelName);
        ModelingExerciseResponseDTO receivedModelingExercise = request.postWithResponseBody("/api/modeling/modeling-exercises", UpdateModelingExerciseDTO.of(modelingExercise),
                ModelingExerciseResponseDTO.class, HttpStatus.CREATED);
        Channel channelFromDB = channelRepository.findChannelByExerciseId(receivedModelingExercise.id());

        assertThat(receivedModelingExercise.gradingCriteria()).hasSize(3);
        assertThat(receivedModelingExercise.gradingCriteria().stream().map(criterion -> criterion.structuredGradingInstructions().size())).containsExactlyInAnyOrder(1, 1, 3);
        assertThat(channelFromDB).isNotNull();
        assertThat(channelFromDB.getName()).isEqualTo("exercise-new-modeling-exercise");

        assertModelingExerciseExistsInWeaviate(weaviateService, modelingExerciseTestRepository.findById(receivedModelingExercise.id()).orElseThrow());

        modelingExercise = ModelingExerciseFactory.createModelingExercise(classExercise.getCourseViaExerciseGroupOrCourseMember().getId(), 1L);
        request.post("/api/modeling/modeling-exercises", UpdateModelingExerciseDTO.of(modelingExercise), HttpStatus.BAD_REQUEST);

        modelingExercise = ModelingExerciseFactory.createModelingExercise(-1L);
        request.post("/api/modeling/modeling-exercises", UpdateModelingExerciseDTO.of(modelingExercise), HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = OTHER_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateModelingExercise_instructorNotInCourse() throws Exception {
        ModelingExercise modelingExercise = ModelingExerciseFactory.createModelingExercise(classExercise.getCourseViaExerciseGroupOrCourseMember().getId());
        request.post("/api/modeling/modeling-exercises", UpdateModelingExerciseDTO.of(modelingExercise), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateModelingExercise_asInstructor() throws Exception {
        ModelingExercise modelingExercise = ModelingExerciseFactory.createModelingExercise(classExercise.getCourseViaExerciseGroupOrCourseMember().getId());
        courseUtilService.enableMessagingForCourse(modelingExercise.getCourseViaExerciseGroupOrCourseMember());
        modelingExercise.setChannelName("testchannel-" + UUID.randomUUID().toString().substring(0, 8));
        // Create exercise first without competency links (competency is set up in @BeforeEach but may not
        // be visible to the POST handler due to Zonky per-test database isolation)
        ModelingExerciseResponseDTO createdModelingExercise = request.postWithResponseBody("/api/modeling/modeling-exercises", UpdateModelingExerciseDTO.of(modelingExercise),
                ModelingExerciseResponseDTO.class, HttpStatus.CREATED);
        gradingCriteria = exerciseUtilService.addGradingInstructionsToExercise(modelingExercise);

        // Build the update request from the source entity (with the persisted id and the new grading criteria).
        modelingExercise.setId(createdModelingExercise.id());
        modelingExercise.setGradingCriteria(gradingCriteria);

        var params = new LinkedMultiValueMap<String, String>();
        var notificationText = "notified!";
        params.add("notificationText", notificationText);
        UpdateModelingExerciseDTO updateModelingExerciseDTO = UpdateModelingExerciseDTO.of(modelingExercise);
        ModelingExerciseResponseDTO returnedModelingExercise = request.putWithResponseBodyAndParams("/api/modeling/modeling-exercises", updateModelingExerciseDTO,
                ModelingExerciseResponseDTO.class, HttpStatus.OK, params);
        assertThat(returnedModelingExercise.gradingCriteria()).hasSameSizeAs(gradingCriteria);
        verify(groupNotificationService).notifyStudentAndEditorAndInstructorGroupAboutExerciseUpdate(any());
        verify(examLiveEventsService, never()).createAndSendProblemStatementUpdateEvent(any(), eq(notificationText));
        verify(competencyProgressApi, timeout(1000).times(1)).updateProgressForUpdatedLearningObjectAsyncWithOriginalCompetencyIds(eq(Set.of()), any());

        assertModelingExerciseExistsInWeaviate(weaviateService, modelingExerciseTestRepository.findById(returnedModelingExercise.id()).orElseThrow());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateModelingExerciseWrongCourseId_asInstructor() throws Exception {
        // use an arbitrary course id that was not yet stored on the server to get a bad request in the PUT call
        ModelingExercise modelingExercise = ModelingExerciseFactory.createModelingExercise(Long.MAX_VALUE, classExercise.getId());
        UpdateModelingExerciseDTO updateModelingExerciseDTO = UpdateModelingExerciseDTO.of(modelingExercise);
        request.put("/api/modeling/modeling-exercises", updateModelingExerciseDTO, HttpStatus.CONFLICT);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateModelingExerciseForExam_asInstructor() throws Exception {
        ExerciseGroup exerciseGroup = examUtilService.addEnrolledExerciseGroupWithExamAndCourse(true, true, TEST_PREFIX);
        ModelingExercise modelingExercise = ModelingExerciseFactory.generateModelingExerciseForExam(DiagramType.ClassDiagram, exerciseGroup);
        modelingExerciseTestRepository.save(modelingExercise);

        modelingExercise.setProblemStatement("New problem statement");
        var params = new LinkedMultiValueMap<String, String>();
        var notificationText = "notified!";
        params.add("notificationText", notificationText);
        UpdateModelingExerciseDTO updateModelingExerciseDTO = UpdateModelingExerciseDTO.of(modelingExercise);
        ModelingExerciseResponseDTO returnedModelingExercise = request.putWithResponseBodyAndParams("/api/modeling/modeling-exercises", updateModelingExerciseDTO,
                ModelingExerciseResponseDTO.class, HttpStatus.OK, params);

        assertThat(returnedModelingExercise.exerciseGroupId()).isNotNull();
        verify(groupNotificationService, never()).notifyStudentAndEditorAndInstructorGroupAboutExerciseUpdate(any());
        verify(examLiveEventsService, times(1)).createAndSendProblemStatementUpdateEvent(any(), eq(notificationText));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateModelingExercise_updatingCourseId_conflict() throws Exception {
        // Create a modeling exercise.
        ModelingExercise createdModelingExercise = classExercise;

        // Create a new course with different id.
        Course newCourse = courseUtilService.createCourse();

        // Assign new course to the modeling exercise.
        createdModelingExercise.setCourse(newCourse);

        UpdateModelingExerciseDTO updateModelingExerciseDTO = UpdateModelingExerciseDTO.of(createdModelingExercise);
        // Modeling exercise update with the new course should fail.
        ModelingExerciseResponseDTO returnedModelingExercise = request.putWithResponseBody("/api/modeling/modeling-exercises", updateModelingExerciseDTO,
                ModelingExerciseResponseDTO.class, HttpStatus.CONFLICT);
        assertThat(returnedModelingExercise).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateModelingExerciseCriteria_asInstructor() throws Exception {
        ModelingExercise modelingExercise = ModelingExerciseFactory.createModelingExercise(classExercise.getCourseViaExerciseGroupOrCourseMember().getId());
        gradingCriteria = exerciseUtilService.addGradingInstructionsToExercise(modelingExercise);
        var currentCriteriaSize = modelingExercise.getGradingCriteria().size();
        var newCriteria = new GradingCriterion();
        newCriteria.setTitle("new");
        newCriteria.setExercise(modelingExercise);
        modelingExercise.getGradingCriteria().add(newCriteria);
        modelingExercise.setChannelName("testchannel-" + UUID.randomUUID().toString().substring(0, 8));
        ModelingExerciseResponseDTO createdModelingExercise = request.postWithResponseBody("/api/modeling/modeling-exercises", UpdateModelingExerciseDTO.of(modelingExercise),
                ModelingExerciseResponseDTO.class, HttpStatus.CREATED);
        assertThat(createdModelingExercise.gradingCriteria()).hasSize(currentCriteriaSize + 1);

        modelingExercise.getGradingCriteria().stream().findFirst().orElseThrow().setTitle("UPDATED");
        modelingExercise.setChannelName("testchannelname-" + UUID.randomUUID().toString().substring(0, 8));

        createdModelingExercise = request.postWithResponseBody("/api/modeling/modeling-exercises", UpdateModelingExerciseDTO.of(modelingExercise),
                ModelingExerciseResponseDTO.class, HttpStatus.CREATED);
        assertThat(createdModelingExercise.gradingCriteria()).anyMatch(criterion -> "UPDATED".equals(criterion.title()));

        // If the grading criteria are deleted then their instructions should also be deleted
        modelingExercise.setGradingCriteria(null);
        modelingExercise.setChannelName("testchannelname-" + UUID.randomUUID().toString().substring(0, 8));

        createdModelingExercise = request.postWithResponseBody("/api/modeling/modeling-exercises", UpdateModelingExerciseDTO.of(modelingExercise),
                ModelingExerciseResponseDTO.class, HttpStatus.CREATED);
        assertThat(createdModelingExercise.gradingCriteria()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateModelingExerciseInstructions_asInstructor() throws Exception {
        ModelingExercise modelingExercise = ModelingExerciseFactory.createModelingExercise(classExercise.getCourseViaExerciseGroupOrCourseMember().getId());
        gradingCriteria = exerciseUtilService.addGradingInstructionsToExercise(modelingExercise);

        GradingCriterion criterionToUpdate = modelingExercise.getGradingCriteria().stream().findAny().orElseThrow();
        var currentInstructionsSize = criterionToUpdate.getStructuredGradingInstructions().size();
        var newInstruction = new GradingInstruction();
        newInstruction.setInstructionDescription("New Instruction");

        criterionToUpdate.addStructuredGradingInstruction(newInstruction);
        modelingExercise.setChannelName("testchannel-" + UUID.randomUUID().toString().substring(0, 8));
        ModelingExerciseResponseDTO createdModelingExercise = request.postWithResponseBody("/api/modeling/modeling-exercises", UpdateModelingExerciseDTO.of(modelingExercise),
                ModelingExerciseResponseDTO.class, HttpStatus.CREATED);
        assertThat(findCriterionByTitle(createdModelingExercise, criterionToUpdate.getTitle()).structuredGradingInstructions()).hasSize(currentInstructionsSize + 1);

        criterionToUpdate.getStructuredGradingInstructions().stream().findFirst().orElseThrow().setInstructionDescription("UPDATE");
        modelingExercise.setChannelName("testchannelname-" + UUID.randomUUID().toString().substring(0, 8));

        createdModelingExercise = request.postWithResponseBody("/api/modeling/modeling-exercises", UpdateModelingExerciseDTO.of(modelingExercise),
                ModelingExerciseResponseDTO.class, HttpStatus.CREATED);
        assertThat(findCriterionByTitle(createdModelingExercise, criterionToUpdate.getTitle()).structuredGradingInstructions())
                .anyMatch(instruction -> "UPDATE".equals(instruction.instructionDescription()));

        criterionToUpdate.setStructuredGradingInstructions(null);
        modelingExercise.setChannelName("testchannelname-" + UUID.randomUUID().toString().substring(0, 8));
        createdModelingExercise = request.postWithResponseBody("/api/modeling/modeling-exercises", UpdateModelingExerciseDTO.of(modelingExercise),
                ModelingExerciseResponseDTO.class, HttpStatus.CREATED);
        assertThat(createdModelingExercise.gradingCriteria()).isNotEmpty();
        assertThat(findCriterionByTitle(createdModelingExercise, criterionToUpdate.getTitle()).structuredGradingInstructions()).isNullOrEmpty();
    }

    private static de.tum.cit.aet.artemis.assessment.dto.GradingCriterionDTO findCriterionByTitle(ModelingExerciseResponseDTO exercise, String title) {
        return exercise.gradingCriteria().stream().filter(criterion -> Objects.equals(criterion.title(), title)).findFirst().orElseThrow();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ArgumentsSource(InvalidExamExerciseDatesArgumentProvider.class)
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateModelingExerciseForExam_invalidExercise_dates(InvalidExamExerciseDateConfiguration invalidDates) throws Exception {
        ExerciseGroup exerciseGroup = examUtilService.addEnrolledExerciseGroupWithExamAndCourse(true, TEST_PREFIX);
        ModelingExercise modelingExercise = ModelingExerciseFactory.generateModelingExerciseForExam(DiagramType.ClassDiagram, exerciseGroup);
        modelingExerciseTestRepository.save(modelingExercise);

        request.postWithResponseBody("/api/modeling/modeling-exercises", UpdateModelingExerciseDTO.of(invalidDates.applyTo(modelingExercise)), ModelingExerciseResponseDTO.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateModelingExerciseDueDate() throws Exception {
        final ZonedDateTime individualDueDate = ZonedDateTime.now().plusHours(20);

        {
            final ModelingSubmission submission1 = ParticipationFactory.generateModelingSubmission("model1", true);
            modelingExerciseUtilService.addModelingSubmission(classExercise, submission1, TEST_PREFIX + "student1");
            final ModelingSubmission submission2 = ParticipationFactory.generateModelingSubmission("model2", false);
            modelingExerciseUtilService.addModelingSubmission(classExercise, submission2, TEST_PREFIX + "student2");

            final var participations = new ArrayList<>(studentParticipationRepository.findByExerciseId(classExercise.getId()));
            assertThat(participations).hasSize(2);
            participations.getFirst().setIndividualDueDate(ZonedDateTime.now().plusHours(2));
            participations.get(1).setIndividualDueDate(individualDueDate);
            studentParticipationRepository.saveAll(participations);
        }

        classExercise.setDueDate(ZonedDateTime.now().plusHours(12));
        UpdateModelingExerciseDTO updateModelingExerciseDTO = UpdateModelingExerciseDTO.of(classExercise);
        request.put("/api/modeling/modeling-exercises", updateModelingExerciseDTO, HttpStatus.OK);

        {
            final var participations = studentParticipationRepository.findByExerciseId(classExercise.getId());
            final var withNoIndividualDueDate = participations.stream().filter(participation -> participation.getIndividualDueDate() == null).toList();
            assertThat(withNoIndividualDueDate).hasSize(1);

            final var withIndividualDueDate = participations.stream().filter(participation -> participation.getIndividualDueDate() != null).toList();
            assertThat(withIndividualDueDate).hasSize(1);
            assertThat(withIndividualDueDate.getFirst().getIndividualDueDate()).isCloseTo(individualDueDate, HalfSecond());
        }
    }

    @Test
    @WithMockUser(username = OTHER_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateModelingExercise_instructorNotInCourse() throws Exception {
        request.put("/api/modeling/modeling-exercises", UpdateModelingExerciseDTO.of(classExercise), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteModelingExercise_asInstructor() throws Exception {
        long exerciseId = classExercise.getId();
        request.delete("/api/modeling/modeling-exercises/" + exerciseId, HttpStatus.OK);
        assertThat(modelingExerciseTestRepository.findById(exerciseId)).as("exercise was deleted").isEmpty();
        assertExerciseNotInWeaviate(weaviateService, exerciseId);
        request.delete("/api/modeling/modeling-exercises/" + exerciseId, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteModelingExerciseWithCompetency() throws Exception {
        classExercise.setCompetencyLinks(Set.of(new CompetencyExerciseLink(competency, classExercise, 1)));
        modelingExerciseTestRepository.save(classExercise);

        request.delete("/api/modeling/modeling-exercises/" + classExercise.getId(), HttpStatus.OK);

        verify(competencyProgressApi).updateProgressByCompetencyAsync(eq(competency));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteModelingExerciseWithChannel() throws Exception {
        Course course = modelingExerciseUtilService.addEnrolledCourseWithOneModelingExercise("ClassDiagram", TEST_PREFIX);
        ModelingExercise modelingExercise = modelingExerciseTestRepository.findByCourseIdWithCategories(course.getId()).getFirst();
        Channel exerciseChannel = conversationUtilService.addChannelToExercise(modelingExercise);

        request.delete("/api/modeling/modeling-exercises/" + modelingExercise.getId(), HttpStatus.OK);

        Optional<Channel> exerciseChannelAfterDelete = channelRepository.findById(exerciseChannel.getId());
        assertThat(exerciseChannelAfterDelete).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteModelingExerciseWithTutorParticipations() throws Exception {
        TutorParticipation tutorParticipation = new TutorParticipation().tutor(userUtilService.getUserByLogin(TEST_PREFIX + "tutor1"))
                .status(TutorParticipationStatus.REVIEWED_INSTRUCTIONS).assessedExercise(classExercise);

        String validModel = TestResourceUtils.loadFileFromResources("test-data/model-submission/model.54727.json");
        ExampleSubmission exampleSubmission = participationUtilService.generateExampleSubmission(validModel, classExercise, true);
        exampleSubmission.addTutorParticipations(tutorParticipation);
        participationUtilService.addExampleSubmission(exampleSubmission);
        tutorParticipationRepository.save(tutorParticipation);

        assertThat(tutorParticipationRepository.findByAssessedExercise(classExercise)).isNotEmpty();

        request.delete("/api/modeling/modeling-exercises/" + classExercise.getId(), HttpStatus.OK);

        assertThat(modelingExerciseTestRepository.findById(classExercise.getId())).as("exercise was deleted").isEmpty();
        assertThat(tutorParticipationRepository.findByAssessedExercise(classExercise)).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testDeleteModelingExercise_asTutor_Forbidden() throws Exception {
        request.delete("/api/modeling/modeling-exercises/" + classExercise.getId(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = OTHER_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteModelingExercise_notInstructorInCourse() throws Exception {
        request.delete("/api/modeling/modeling-exercises/" + classExercise.getId(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importModelingExercise_standaloneImportHonorsEditedFieldsAndResetsDates() throws Exception {
        // Regression test: the standalone (course-to-course) import must persist the fields the user edited in the
        // import form (problem statement, example solution) and keep the dates the client cleared, rather than falling
        // back to the source exercise's values.
        var now = ZonedDateTime.now();
        Course source = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        Course target = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        ModelingExercise sourceExercise = ModelingExerciseFactory.generateModelingExercise(now.minusDays(10), now.minusDays(8), now.minusDays(6), DiagramType.ClassDiagram, source);
        sourceExercise.setProblemStatement("SOURCE PROBLEM STATEMENT");
        sourceExercise.setMaxPoints(42.0);
        modelingExerciseTestRepository.save(sourceExercise);

        // Emulate the client edit form: edited problem statement and example solution, cleared dates (resetForImport), target course.
        ModelingExercise body = modelingExerciseTestRepository.findByIdElseThrow(sourceExercise.getId());
        body.setProblemStatement("EDITED PROBLEM STATEMENT");
        body.setExampleSolutionExplanation("EDITED EXAMPLE SOLUTION");
        body.setReleaseDate(null);
        body.setStartDate(null);
        body.setDueDate(null);
        body.setAssessmentDueDate(null);
        body.setCourse(target);
        body.setChannelName("edited-import-" + UUID.randomUUID().toString().substring(0, 8));

        // The import endpoint consumes the flat ImportModelingExerciseDTO (matching the migrated Angular client), so the
        // edited entity is mapped to the DTO shape the client would send.
        var importedDto = request.postWithResponseBody("/api/modeling/modeling-exercises/import?sourceExerciseId=" + sourceExercise.getId(), ImportModelingExerciseDTO.of(body),
                ModelingExerciseResponseDTO.class, HttpStatus.CREATED);
        ModelingExercise imported = modelingExerciseTestRepository.findByIdElseThrow(importedDto.id());

        assertThat(imported.getProblemStatement()).as("edited problem statement should survive the standalone import").isEqualTo("EDITED PROBLEM STATEMENT");
        assertThat(imported.getExampleSolutionExplanation()).as("edited example solution should survive the standalone import").isEqualTo("EDITED EXAMPLE SOLUTION");
        assertThat(imported.getMaxPoints()).as("points should survive the standalone import").isEqualTo(42.0);
        assertThat(imported.getReleaseDate()).as("cleared release date should stay cleared").isNull();
        assertThat(imported.getDueDate()).as("cleared due date should stay cleared").isNull();
        assertThat(imported.getAssessmentDueDate()).as("cleared assessment due date should stay cleared").isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importModelingExerciseFromCourseToCourse() throws Exception {
        var now = ZonedDateTime.now();
        Course course1 = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        Course course2 = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        courseUtilService.enableMessagingForCourse(course2);
        ModelingExercise modelingExerciseToImport = ModelingExerciseFactory.generateModelingExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1),
                DiagramType.ClassDiagram, course1);
        modelingExerciseTestRepository.save(modelingExerciseToImport);
        modelingExerciseToImport.setCourse(course2);
        String uniqueChannelName = "channel-" + UUID.randomUUID().toString().substring(0, 8);
        modelingExerciseToImport.setChannelName(uniqueChannelName);
        modelingExerciseToImport.getCompetencyLinks().add(new CompetencyExerciseLink(competency, modelingExerciseToImport, 1));
        modelingExerciseToImport.getCompetencyLinks().forEach(link -> link.getCompetency().setCourse(null));

        var importedExerciseDto = request.postWithResponseBody("/api/modeling/modeling-exercises/import?sourceExerciseId=" + modelingExerciseToImport.getId(),
                ImportModelingExerciseDTO.of(modelingExerciseToImport), ModelingExerciseResponseDTO.class, HttpStatus.CREATED);
        ModelingExercise importedExercise = modelingExerciseTestRepository.findById(importedExerciseDto.id()).orElseThrow();
        // The import DTO does not carry assessmentType; without setting it explicitly the new exercise would be persisted
        // with assessmentType == null instead of the MANUAL mode the old entity payload preserved.
        assertThat(importedExercise.getAssessmentType()).as("imported modeling exercise keeps the MANUAL assessment type").isEqualTo(AssessmentType.MANUAL);
        assertThat(importedExercise.getDiagramType()).as("imported modeling exercise keeps the diagram type").isEqualTo(modelingExerciseToImport.getDiagramType());
        Channel channelFromDB = channelRepository.findChannelByExerciseId(importedExercise.getId());
        assertThat(channelFromDB).isNotNull();
        assertThat(channelFromDB.getName()).isEqualTo(uniqueChannelName);
        verify(competencyProgressApi).updateProgressByLearningObjectAsync(eq(importedExercise));

        assertModelingExerciseExistsInWeaviate(weaviateService, importedExercise);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importModelingExercisePreservesTheGradingCriteriaTheClientSubmits() throws Exception {
        // The import form is pre-filled from the source, so the client posts the source's grading criteria back. They must
        // be deep-copied onto the imported exercise (new entities, same titles).
        ModelingExercise source = createSourceExerciseWithGradingCriteria();
        Set<GradingCriterion> sourceCriteria = gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(source.getId());
        assertThat(sourceCriteria).as("precondition: the source has grading criteria").isNotEmpty();

        ModelingExercise body = importBodyFor(source);
        body.setGradingCriteria(sourceCriteria);

        var importedExercise = request.postWithResponseBody("/api/modeling/modeling-exercises/import?sourceExerciseId=" + source.getId(), ImportModelingExerciseDTO.of(body),
                ModelingExerciseResponseDTO.class, HttpStatus.CREATED);

        Set<GradingCriterion> importedCriteria = gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(importedExercise.id());
        assertThat(importedCriteria).hasSameSizeAs(sourceCriteria);
        assertThat(importedCriteria).extracting(GradingCriterion::getTitle).containsExactlyInAnyOrderElementsOf(sourceCriteria.stream().map(GradingCriterion::getTitle).toList());
        assertThat(importedCriteria).extracting(GradingCriterion::getId).doesNotContainAnyElementsOf(sourceCriteria.stream().map(GradingCriterion::getId).toList());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importModelingExerciseWithoutGradingCriteriaImportsNone() throws Exception {
        // An instructor who deletes every grading criterion in the import form posts an empty collection. That is the
        // caller's own content and must win over the source, instead of the source's criteria being silently restored.
        ModelingExercise source = createSourceExerciseWithGradingCriteria();
        Set<GradingCriterion> sourceCriteria = gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(source.getId());
        assertThat(sourceCriteria).as("precondition: the source has grading criteria").isNotEmpty();

        ModelingExercise body = importBodyFor(source);
        body.setGradingCriteria(new HashSet<>());

        var importedExercise = request.postWithResponseBody("/api/modeling/modeling-exercises/import?sourceExerciseId=" + source.getId(), ImportModelingExerciseDTO.of(body),
                ModelingExerciseResponseDTO.class, HttpStatus.CREATED);

        assertThat(gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(importedExercise.id())).isEmpty();
        assertThat(gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(source.getId())).as("the source keeps its own criteria").hasSameSizeAs(sourceCriteria);
    }

    private ModelingExercise createSourceExerciseWithGradingCriteria() {
        var now = ZonedDateTime.now();
        Course sourceCourse = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        ModelingExercise source = modelingExerciseTestRepository
                .save(ModelingExerciseFactory.generateModelingExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), DiagramType.ClassDiagram, sourceCourse));
        exerciseUtilService.addGradingInstructionsToExercise(source);
        return modelingExerciseTestRepository.save(source);
    }

    /**
     * Builds the request body of a standalone import: a copy of the source pointing at a fresh target course, mirroring
     * what the client posts from the (pre-filled) import form.
     */
    private ModelingExercise importBodyFor(ModelingExercise source) {
        Course targetCourse = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        courseUtilService.enableMessagingForCourse(targetCourse);
        ModelingExercise body = ModelingExerciseFactory.generateModelingExercise(source.getReleaseDate(), source.getDueDate(), source.getAssessmentDueDate(),
                source.getDiagramType(), targetCourse);
        body.setChannelName("channel-" + UUID.randomUUID().toString().substring(0, 8));
        return body;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importModelingExerciseWithCompetencyLinkOfTheTargetCourse() throws Exception {
        var now = ZonedDateTime.now();
        Course course1 = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        Course course2 = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        courseUtilService.enableMessagingForCourse(course2);
        // The competency belongs to the TARGET course, so the link really is created for the imported exercise (a link to a
        // competency of another course is skipped, which is why importModelingExerciseFromCourseToCourse cannot reach this
        // code). Creating the link forces a second save of a detached exercise, and the import has to keep working on the
        // instance that save returned - otherwise the link's derived id stays unset and the resource's follow-up save
        // fails with a duplicate-key error.
        Competency targetCompetency = competencyUtilService.createCompetency(course2);

        ModelingExercise exerciseToImport = ModelingExerciseFactory.generateModelingExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), DiagramType.ClassDiagram,
                course1);
        modelingExerciseTestRepository.save(exerciseToImport);
        long sourceExerciseId = exerciseToImport.getId();
        exerciseToImport.setCourse(course2);
        String uniqueChannelName = "channel-" + UUID.randomUUID().toString().substring(0, 8);
        exerciseToImport.setChannelName(uniqueChannelName);
        exerciseToImport.setCompetencyLinks(new HashSet<>(Set.of(new CompetencyExerciseLink(targetCompetency, exerciseToImport, 1))));

        var importedExercise = request.postWithResponseBody("/api/modeling/modeling-exercises/import?sourceExerciseId=" + sourceExerciseId,
                ImportModelingExerciseDTO.of(exerciseToImport), ModelingExerciseResponseDTO.class, HttpStatus.CREATED);

        assertThat(importedExercise.id()).isNotEqualTo(sourceExerciseId);
        ModelingExercise reloaded = modelingExerciseTestRepository.findWithCompetencyLinksByIdElseThrow(importedExercise.id());
        assertThat(reloaded.getCompetencyLinks()).hasSize(1);
        assertThat(reloaded.getCompetencyLinks().iterator().next().getCompetency().getId()).isEqualTo(targetCompetency.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importModelingExerciseWithExampleSubmissionFromCourseToCourse() throws Exception {
        var now = ZonedDateTime.now();
        Course course1 = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        Course course2 = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);

        ModelingExercise modelingExercise = ModelingExerciseFactory.generateModelingExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), DiagramType.ClassDiagram,
                course1);
        modelingExercise = modelingExerciseTestRepository.save(modelingExercise);
        exerciseUtilService.addGradingInstructionsToExercise(modelingExercise);

        // Create example submission
        var exampleSubmission = participationUtilService.generateExampleSubmission("model", modelingExercise, true);
        exampleSubmission = participationUtilService.addExampleSubmission(exampleSubmission);
        participationUtilService.addResultToSubmission(exampleSubmission.getSubmission(), AssessmentType.MANUAL, modelingExercise.getId());
        var submission = submissionRepository.findWithEagerResultAndFeedbackAndAssessmentNoteById(exampleSubmission.getSubmission().getId()).orElseThrow();
        participationUtilService.addFeedbackToResult(ParticipationFactory.generateFeedback().stream().findFirst().orElseThrow(),
                Objects.requireNonNull(submission.getLatestResult()));
        modelingExercise.setChannelName("testchannel-" + UUID.randomUUID().toString().substring(0, 8));

        modelingExercise.setCourse(course2);
        var importedModelingExerciseDto = request.postWithResponseBody("/api/modeling/modeling-exercises/import?sourceExerciseId=" + modelingExercise.getId(),
                ImportModelingExerciseDTO.of(modelingExercise), ModelingExerciseResponseDTO.class, HttpStatus.CREATED);

        assertThat(modelingExerciseTestRepository.findById(importedModelingExerciseDto.id())).isPresent();
        var importedModelingExercise = modelingExerciseTestRepository.findByIdWithExampleSubmissionsAndResultsAndGradingCriteria(modelingExercise.getId()).orElseThrow();
        var importedExampleSubmission = importedModelingExercise.getExampleSubmissions().stream().findFirst().orElseThrow();
        assertThat(importedExampleSubmission.getId()).isEqualTo(exampleSubmission.getId());
        assertThat(importedExampleSubmission.getSubmission().getLatestResult()).isEqualTo(submission.getLatestResult());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importModelingExerciseFromCourseToExam() throws Exception {
        var now = ZonedDateTime.now();
        Course course1 = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        ExerciseGroup exerciseGroup1 = examUtilService.addEnrolledExerciseGroupWithExamAndCourse(true, TEST_PREFIX);
        ModelingExercise modelingExercise = ModelingExerciseFactory.generateModelingExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), DiagramType.ClassDiagram,
                course1);
        modelingExercise.setReleaseDate(null);
        modelingExercise.setDueDate(null);
        modelingExercise.setAssessmentDueDate(null);
        modelingExerciseTestRepository.save(modelingExercise);
        modelingExercise.setCourse(null);
        modelingExercise.setExerciseGroup(exerciseGroup1);
        // Link a competency of the exam's course so the import runs its second, merge-based save: that merge resolves
        // the non-cascaded exerciseGroup to an uninitialized proxy, which is exactly the state the response mapping
        // must survive for the exerciseGroup/exam/course assertions below to mean anything.
        Competency examCourseCompetency = competencyUtilService.createCompetency(exerciseGroup1.getExam().getCourse());
        modelingExercise.setCompetencyLinks(Set.of(new CompetencyExerciseLink(examCourseCompetency, modelingExercise, 1)));

        var importedExercise = request.postWithResponseBody("/api/modeling/modeling-exercises/import?sourceExerciseId=" + modelingExercise.getId(),
                ImportModelingExerciseDTO.of(modelingExercise), ModelingExerciseResponseDTO.class, HttpStatus.CREATED);

        Channel channelFromDB = channelRepository.findChannelByExerciseId(importedExercise.id());
        assertThat(channelFromDB).isNull();

        // The import response must carry the same exerciseGroup/exam/course wiring as create/update/GET, so the
        // management detail page can build the "Exam" link instead of navigating to /course-management/undefined/...
        assertThat(importedExercise.exerciseGroup()).isNotNull();
        assertThat(importedExercise.exerciseGroup().exam()).as("exam was set correctly").isNotNull();
        assertThat(importedExercise.exerciseGroup().exam().id()).isEqualTo(exerciseGroup1.getExam().getId());
        assertThat(importedExercise.exerciseGroup().exam().course()).as("exam course was set correctly").isNotNull();
        assertThat(importedExercise.exerciseGroup().exam().course().id()).isEqualTo(exerciseGroup1.getExam().getCourse().getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "TA")
    void importModelingExerciseFromCourseToExam_forbidden() throws Exception {
        var now = ZonedDateTime.now();
        Course course1 = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        ExerciseGroup exerciseGroup1 = examUtilService.addExerciseGroupWithExamAndCourse(true);
        ModelingExercise modelingExercise = ModelingExerciseFactory.generateModelingExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), DiagramType.ClassDiagram,
                course1);
        modelingExerciseTestRepository.save(modelingExercise);
        modelingExercise.setCourse(null);
        modelingExercise.setExerciseGroup(exerciseGroup1);

        request.postWithResponseBody("/api/modeling/modeling-exercises/import?sourceExerciseId=" + modelingExercise.getId(), ImportModelingExerciseDTO.of(modelingExercise),
                ModelingExerciseResponseDTO.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importModelingExerciseFromExamToCourse() throws Exception {
        ExerciseGroup exerciseGroup1 = examUtilService.addEnrolledExerciseGroupWithExamAndCourse(true, TEST_PREFIX);
        ModelingExercise modelingExercise = ModelingExerciseFactory.generateModelingExerciseForExam(DiagramType.ClassDiagram, exerciseGroup1);
        Course course1 = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        modelingExerciseTestRepository.save(modelingExercise);
        modelingExercise.setCourse(course1);
        modelingExercise.setExerciseGroup(null);
        modelingExercise.setChannelName("testchannel-" + UUID.randomUUID().toString().substring(0, 8));
        request.postWithResponseBody("/api/modeling/modeling-exercises/import?sourceExerciseId=" + modelingExercise.getId(), ImportModelingExerciseDTO.of(modelingExercise),
                ModelingExerciseResponseDTO.class, HttpStatus.CREATED);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "TA")
    void importModelingExerciseFromExamToCourse_forbidden() throws Exception {
        ExerciseGroup exerciseGroup1 = examUtilService.addExerciseGroupWithExamAndCourse(true);
        ModelingExercise modelingExercise = ModelingExerciseFactory.generateModelingExerciseForExam(DiagramType.ClassDiagram, exerciseGroup1);
        Course course1 = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        modelingExerciseTestRepository.save(modelingExercise);
        modelingExercise.setCourse(course1);
        modelingExercise.setExerciseGroup(null);

        request.postWithResponseBody("/api/modeling/modeling-exercises/import?sourceExerciseId=" + modelingExercise.getId(), ImportModelingExerciseDTO.of(modelingExercise),
                ModelingExerciseResponseDTO.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importModelingExerciseFromExamToExam() throws Exception {
        ExerciseGroup exerciseGroup1 = examUtilService.addEnrolledExerciseGroupWithExamAndCourse(true, TEST_PREFIX);
        ExerciseGroup exerciseGroup2 = examUtilService.addEnrolledExerciseGroupWithExamAndCourse(true, TEST_PREFIX);
        ModelingExercise modelingExercise = ModelingExerciseFactory.generateModelingExerciseForExam(DiagramType.ClassDiagram, exerciseGroup1);
        modelingExerciseTestRepository.save(modelingExercise);
        modelingExercise.setExerciseGroup(exerciseGroup2);

        request.postWithResponseBody("/api/modeling/modeling-exercises/import?sourceExerciseId=" + modelingExercise.getId(), ImportModelingExerciseDTO.of(modelingExercise),
                ModelingExerciseResponseDTO.class, HttpStatus.CREATED);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importModelingExerciseFromCourseToCourse_badRequest() throws Exception {
        var now = ZonedDateTime.now();
        Course course1 = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        ModelingExercise modelingExercise = ModelingExerciseFactory.generateModelingExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), DiagramType.ClassDiagram,
                course1);
        modelingExerciseTestRepository.save(modelingExercise);
        modelingExercise.setCourse(null);

        request.postWithResponseBody("/api/modeling/modeling-exercises/import?sourceExerciseId=" + modelingExercise.getId(), ImportModelingExerciseDTO.of(modelingExercise),
                ModelingExerciseResponseDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importModelingExerciseFromCourseToCourse_exampleSolutionPublicationDate() throws Exception {
        var now = ZonedDateTime.now();
        Course course1 = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        Course course2 = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        ModelingExercise modelingExercise = ModelingExerciseFactory.generateModelingExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), DiagramType.ClassDiagram,
                course1);

        modelingExercise.setExampleSolutionPublicationDate(ZonedDateTime.now());

        modelingExerciseTestRepository.save(modelingExercise);
        modelingExercise.setCourse(course2);
        modelingExercise.setChannelName("testchannel-" + UUID.randomUUID().toString().substring(0, 8));

        ModelingExerciseResponseDTO newModelingExercise = request.postWithResponseBody("/api/modeling/modeling-exercises/import?sourceExerciseId=" + modelingExercise.getId(),
                ImportModelingExerciseDTO.of(modelingExercise), ModelingExerciseResponseDTO.class, HttpStatus.CREATED);
        assertThat(newModelingExercise.exampleSolutionPublicationDate()).as("modeling example solution publication date was correctly set to null in the response").isNull();

        ModelingExercise newModelingExerciseFromDatabase = modelingExerciseTestRepository.findById(newModelingExercise.id()).orElseThrow();
        assertThat(newModelingExerciseFromDatabase.getExampleSolutionPublicationDate()).as("modeling example solution publication date was correctly set to null in the database")
                .isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createModelingExerciseForExam() throws Exception {
        ExerciseGroup exerciseGroup = examUtilService.addEnrolledExerciseGroupWithExamAndCourse(true, TEST_PREFIX);
        ModelingExercise modelingExercise = ModelingExerciseFactory.generateModelingExerciseForExam(DiagramType.ClassDiagram, exerciseGroup);

        String title = "New Exam Modeling Exercise";
        DifficultyLevel difficulty = DifficultyLevel.HARD;
        modelingExercise.setTitle(title);
        modelingExercise.setDifficulty(difficulty);

        ModelingExerciseResponseDTO newModelingExercise = request.postWithResponseBody("/api/modeling/modeling-exercises", UpdateModelingExerciseDTO.of(modelingExercise),
                ModelingExerciseResponseDTO.class, HttpStatus.CREATED);
        Channel channelFromDB = channelRepository.findChannelByExerciseId(newModelingExercise.id());

        assertThat(channelFromDB).isNull(); // there should not be any channel for exam exercise

        assertThat(newModelingExercise.title()).as("modeling exercise title was correctly set").isEqualTo(title);
        assertThat(newModelingExercise.difficulty()).as("modeling exercise difficulty was correctly set").isEqualTo(difficulty);
        assertThat(newModelingExercise.course()).as("course was not set for exam exercise").isNull();
        assertThat(newModelingExercise.exerciseGroup()).as("exerciseGroup was set for exam exercise").isNotNull();
        assertThat(newModelingExercise.exerciseGroup().id()).as("exerciseGroupId was set correctly").isEqualTo(exerciseGroup.getId());
        assertThat(newModelingExercise.exerciseGroup().exam()).as("exam was set correctly").isNotNull();
        assertThat(newModelingExercise.exerciseGroup().exam().course()).as("exam course was set correctly").isNotNull();
        assertThat(newModelingExercise.exerciseGroup().exam().course().id()).as("exam course id was set correctly").isEqualTo(exerciseGroup.getExam().getCourse().getId());
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ArgumentsSource(InvalidExamExerciseDatesArgumentProvider.class)
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createModelingExerciseForExam_invalidExercise_dates(InvalidExamExerciseDateConfiguration invalidDates) throws Exception {
        ExerciseGroup exerciseGroup = examUtilService.addEnrolledExerciseGroupWithExamAndCourse(true, TEST_PREFIX);
        ModelingExercise modelingExercise = ModelingExerciseFactory.generateModelingExerciseForExam(DiagramType.ClassDiagram, exerciseGroup);

        request.postWithResponseBody("/api/modeling/modeling-exercises", UpdateModelingExerciseDTO.of(invalidDates.applyTo(modelingExercise)), ModelingExerciseResponseDTO.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createModelingExercise_setCourseAndExerciseGroup_badRequest() throws Exception {
        ExerciseGroup exerciseGroup = examUtilService.addEnrolledExerciseGroupWithExamAndCourse(true, TEST_PREFIX);
        ModelingExercise modelingExercise = ModelingExerciseFactory.generateModelingExerciseForExam(DiagramType.ClassDiagram, exerciseGroup);
        modelingExercise.setCourse(exerciseGroup.getExam().getCourse());
        request.postWithResponseBody("/api/modeling/modeling-exercises", UpdateModelingExerciseDTO.of(modelingExercise), ModelingExerciseResponseDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createModelingExercise_setNeitherCourseAndExerciseGroup_badRequest() throws Exception {
        ModelingExercise modelingExercise = ModelingExerciseFactory.generateModelingExerciseForExam(DiagramType.ClassDiagram, null);
        request.postWithResponseBody("/api/modeling/modeling-exercises", UpdateModelingExerciseDTO.of(modelingExercise), ModelingExerciseResponseDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createModelingExercise_InvalidMaxScore() throws Exception {
        ModelingExercise modelingExercise = ModelingExerciseFactory.createModelingExercise(classExercise.getCourseViaExerciseGroupOrCourseMember().getId());
        modelingExercise.setMaxPoints(0.0);
        request.postWithResponseBody("/api/modeling/modeling-exercises", UpdateModelingExerciseDTO.of(modelingExercise), ModelingExerciseResponseDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createModelingExercise_IncludedAsBonusInvalidBonusPoints() throws Exception {
        ModelingExercise modelingExercise = ModelingExerciseFactory.createModelingExercise(classExercise.getCourseViaExerciseGroupOrCourseMember().getId());
        modelingExercise.setMaxPoints(10.0);
        modelingExercise.setBonusPoints(1.0);
        modelingExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_AS_BONUS);
        request.postWithResponseBody("/api/modeling/modeling-exercises", UpdateModelingExerciseDTO.of(modelingExercise), ModelingExerciseResponseDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createModelingExercise_NotIncludedInvalidBonusPoints() throws Exception {
        ModelingExercise modelingExercise = ModelingExerciseFactory.createModelingExercise(classExercise.getCourseViaExerciseGroupOrCourseMember().getId());
        modelingExercise.setMaxPoints(10.0);
        modelingExercise.setBonusPoints(1.0);
        modelingExercise.setIncludedInOverallScore(IncludedInOverallScore.NOT_INCLUDED);
        request.postWithResponseBody("/api/modeling/modeling-exercises", UpdateModelingExerciseDTO.of(modelingExercise), ModelingExerciseResponseDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = OTHER_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testInstructorGetsOnlyResultsFromOwningCourses() throws Exception {
        final var search = pageableSearchUtilService.configureSearch("");
        final var result = request.getSearchResult("/api/modeling/modeling-exercises", HttpStatus.OK, ModelingExerciseListItemDTO.class,
                pageableSearchUtilService.searchMapping(search));
        assertThat(result.getResultsOnPage()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testInstructorSearchTermMatchesTitle() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        testSearchTermMatchesTitle(TEST_PREFIX + "testInstructorSearchTermMatchesTitle");
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testAdminSearchTermMatchesTitle() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        testSearchTermMatchesTitle(TEST_PREFIX + "testAdminSearchTermMatchesTitle");
    }

    private void testSearchTermMatchesTitle(String exerciseTitle) throws Exception {
        final Course course = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        final var now = ZonedDateTime.now();
        ModelingExercise exercise = ModelingExerciseFactory.generateModelingExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), DiagramType.ClassDiagram, course);
        exercise.setTitle(exerciseTitle);
        exercise = modelingExerciseTestRepository.save(exercise);

        final var searchTerm = pageableSearchUtilService.configureSearch(exercise.getTitle());
        final var searchResult = request.getSearchResult("/api/modeling/modeling-exercises", HttpStatus.OK, ModelingExerciseListItemDTO.class,
                pageableSearchUtilService.searchMapping(searchTerm));
        assertThat(searchResult.getResultsOnPage()).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testInstructorGetsResultsFromOwningCoursesNotEmpty() throws Exception {
        final String titleExtension = "testInstructorGetsResultsFromOwningCoursesNotEmpty";
        modelingExerciseUtilService.addEnrolledCourseWithOneModelingExercise("ClassDiagram" + titleExtension, TEST_PREFIX);
        modelingExerciseUtilService.addEnrolledCourseWithOneModelingExercise("Activity Diagram" + titleExtension, TEST_PREFIX);
        final var searchClassDiagram = pageableSearchUtilService.configureSearch("ClassDiagram" + titleExtension);
        final var resultClassDiagram = request.getSearchResult("/api/modeling/modeling-exercises", HttpStatus.OK, ModelingExerciseListItemDTO.class,
                pageableSearchUtilService.searchMapping(searchClassDiagram));
        assertThat(resultClassDiagram.getResultsOnPage()).hasSize(1);

        final var searchActivityDiagram = pageableSearchUtilService.configureSearch("Activity Diagram" + titleExtension);
        final var resultActivityDiagram = request.getSearchResult("/api/modeling/modeling-exercises", HttpStatus.OK, ModelingExerciseListItemDTO.class,
                pageableSearchUtilService.searchMapping(searchActivityDiagram));
        assertThat(resultActivityDiagram.getResultsOnPage()).hasSize(1);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testAdminGetsResultsFromAllCourses() throws Exception {
        String searchTerm = "ClassDiagram testAdminGetsResultsFromAllCourses";
        final var search = pageableSearchUtilService.configureSearch(searchTerm);
        final var oldResult = request.getSearchResult("/api/modeling/modeling-exercises", HttpStatus.OK, ModelingExerciseListItemDTO.class,
                pageableSearchUtilService.searchMapping(search));
        courseUtilService.addCourseWithExercise(searchTerm);
        final var result = request.getSearchResult("/api/modeling/modeling-exercises", HttpStatus.OK, ModelingExerciseListItemDTO.class,
                pageableSearchUtilService.searchMapping(search));
        assertThat(result.getResultsOnPage()).hasSize(oldResult.getResultsOnPage().size() + 1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCourseAndExamFiltersAsInstructor() throws Exception {
        testCourseAndExamFilters("testCourseAndExamFiltersAsInstructor");
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testCourseAndExamFiltersAsAdmin() throws Exception {
        testCourseAndExamFilters("testCourseAndExamFiltersAsAdmin");
    }

    private void testCourseAndExamFilters(String title) throws Exception {
        Course course = modelingExerciseUtilService.addEnrolledCourseWithOneModelingExercise(title, TEST_PREFIX);
        ModelingExercise courseExercise = (ModelingExercise) course.getExercises().iterator().next();
        ModelingExercise examExercise = examUtilService.addEnrolledCourseExamExerciseGroupWithOneModelingExercise(title + "-Morpork", TEST_PREFIX);
        exerciseIntegrationTestService.testCourseAndExamFilters("/api/modeling/modeling-exercises", title);

        var search = pageableSearchUtilService.configureSearch(title);
        var result = request.getSearchResult("/api/modeling/modeling-exercises", HttpStatus.OK, ModelingExerciseListItemDTO.class, pageableSearchUtilService.searchMapping(search));
        assertThat(result.getResultsOnPage()).hasSize(2);

        ModelingExerciseListItemDTO courseResult = result.getResultsOnPage().stream().filter(exercise -> exercise.id().equals(courseExercise.getId())).findFirst().orElseThrow();
        assertThat(courseResult.courseId()).as("course id is present for course exercises").isEqualTo(course.getId());
        assertThat(courseResult.course()).as("source course is present for course exercises").isNotNull();
        assertThat(courseResult.course().id()).isEqualTo(course.getId());
        assertThat(courseResult.course().title()).isEqualTo(course.getTitle());
        assertThat(courseResult.exerciseGroup()).as("course exercises have no exam marker").isNull();

        ModelingExerciseListItemDTO examResult = result.getResultsOnPage().stream().filter(exercise -> exercise.id().equals(examExercise.getId())).findFirst().orElseThrow();
        assertThat(examResult.courseId()).as("exam exercises use the nested source course").isNull();
        assertThat(examResult.course()).as("source course is present for exam exercises").isNotNull();
        assertThat(examResult.course().id()).isEqualTo(examExercise.getCourseViaExerciseGroupOrCourseMember().getId());
        assertThat(examResult.course().title()).isEqualTo(examExercise.getCourseViaExerciseGroupOrCourseMember().getTitle());
        assertThat(examResult.examId()).isEqualTo(examExercise.getExam().getId());
        assertThat(examResult.exerciseGroup()).as("exam marker is present").isNotNull();
        assertThat(examResult.exerciseGroup().id()).isEqualTo(examExercise.getExerciseGroup().getId());
        assertThat(examResult.exerciseGroup().exam()).as("exam reference is present").isNotNull();
        assertThat(examResult.exerciseGroup().exam().id()).isEqualTo(examExercise.getExam().getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImport_team_modeChange() throws Exception {
        var now = ZonedDateTime.now();
        Course course1 = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        Course course2 = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        ModelingExercise sourceExercise = ModelingExerciseFactory.generateModelingExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), DiagramType.ClassDiagram,
                course1);
        sourceExercise.setMode(ExerciseMode.INDIVIDUAL);
        sourceExercise = modelingExerciseTestRepository.save(sourceExercise);

        var exerciseToBeImported = ModelingExerciseFactory.generateModelingExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), DiagramType.ClassDiagram, course2);
        exerciseToBeImported.setMode(ExerciseMode.TEAM);
        exerciseToBeImported.setCourse(course2);

        var teamAssignmentConfig = new TeamAssignmentConfig();
        teamAssignmentConfig.setExercise(exerciseToBeImported);
        teamAssignmentConfig.setMinTeamSize(1);
        teamAssignmentConfig.setMaxTeamSize(10);
        exerciseToBeImported.setTeamAssignmentConfig(teamAssignmentConfig);
        exerciseToBeImported.setChannelName("testchannel-" + UUID.randomUUID().toString().substring(0, 8));
        ModelingExerciseResponseDTO importedDto = request.postWithResponseBody("/api/modeling/modeling-exercises/import?sourceExerciseId=" + sourceExercise.getId(),
                ImportModelingExerciseDTO.of(exerciseToBeImported), ModelingExerciseResponseDTO.class, HttpStatus.CREATED);

        assertThat(importedDto.courseId()).isEqualTo(course2.getId());
        assertThat(importedDto.mode()).isEqualTo(ExerciseMode.TEAM);
        assertThat(importedDto.teamAssignmentConfig().minTeamSize()).isEqualTo(teamAssignmentConfig.getMinTeamSize());
        assertThat(importedDto.teamAssignmentConfig().maxTeamSize()).isEqualTo(teamAssignmentConfig.getMaxTeamSize());
        assertThat(teamRepository.findAllByExerciseIdWithEagerStudents(modelingExerciseTestRepository.findById(importedDto.id()).orElseThrow(), null)).isEmpty();

        sourceExercise = modelingExerciseTestRepository.findById(sourceExercise.getId()).orElseThrow();
        assertThat(sourceExercise.getCourseViaExerciseGroupOrCourseMember().getId()).isEqualTo(course1.getId());
        assertThat(sourceExercise.getMode()).isEqualTo(ExerciseMode.INDIVIDUAL);
        assertThat(teamRepository.findAllByExerciseIdWithEagerStudents(sourceExercise, null)).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImport_individual_modeChange() throws Exception {
        var now = ZonedDateTime.now();
        Course course1 = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        Course course2 = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        ModelingExercise sourceExercise = ModelingExerciseFactory.generateModelingExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), DiagramType.ClassDiagram,
                course1);
        sourceExercise.setMode(ExerciseMode.TEAM);
        var teamAssignmentConfig = new TeamAssignmentConfig();
        teamAssignmentConfig.setExercise(sourceExercise);
        teamAssignmentConfig.setMinTeamSize(1);
        teamAssignmentConfig.setMaxTeamSize(10);
        sourceExercise.setTeamAssignmentConfig(teamAssignmentConfig);
        sourceExercise.setCourse(course1);

        sourceExercise = modelingExerciseTestRepository.save(sourceExercise);
        var team = new Team();
        team.setShortName(TEST_PREFIX + "testImport_individual_modeChange");
        teamRepository.save(sourceExercise, team);

        var exerciseToBeImported = ModelingExerciseFactory.generateModelingExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), DiagramType.ClassDiagram, course2);
        exerciseToBeImported.setMode(ExerciseMode.INDIVIDUAL);
        exerciseToBeImported.setCourse(course2);
        exerciseToBeImported.setChannelName("channelName-" + UUID.randomUUID().toString().substring(0, 8));
        ModelingExerciseResponseDTO importedDto = request.postWithResponseBody("/api/modeling/modeling-exercises/import?sourceExerciseId=" + sourceExercise.getId(),
                ImportModelingExerciseDTO.of(exerciseToBeImported), ModelingExerciseResponseDTO.class, HttpStatus.CREATED);

        assertThat(importedDto.courseId()).isEqualTo(course2.getId());
        assertThat(importedDto.mode()).isEqualTo(ExerciseMode.INDIVIDUAL);
        assertThat(importedDto.teamAssignmentConfig()).isNull();
        assertThat(teamRepository.findAllByExerciseIdWithEagerStudents(modelingExerciseTestRepository.findById(importedDto.id()).orElseThrow(), null)).isEmpty();

        sourceExercise = modelingExerciseTestRepository.findById(sourceExercise.getId()).orElseThrow();
        assertThat(sourceExercise.getCourseViaExerciseGroupOrCourseMember().getId()).isEqualTo(course1.getId());
        assertThat(sourceExercise.getMode()).isEqualTo(ExerciseMode.TEAM);
        assertThat(teamRepository.findAllByExerciseIdWithEagerStudents(sourceExercise, null)).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testReEvaluateAndUpdateModelingExercise() throws Exception {
        Set<GradingCriterion> gradingCriteria = exerciseUtilService.addGradingInstructionsToExercise(classExercise);
        gradingCriterionRepository.saveAll(gradingCriteria);

        participationUtilService.addAssessmentWithFeedbackWithGradingInstructionsForExercise(classExercise, TEST_PREFIX + "instructor1");

        // change grading instruction score
        GradingCriterion toUpdate = GradingCriterionUtil.findAnyWhere(gradingCriteria, criterion -> !criterion.getStructuredGradingInstructions().isEmpty()).orElseThrow();
        toUpdate.getStructuredGradingInstructions().stream().findFirst().orElseThrow().setCredits(3);
        gradingCriteria.removeIf(criterion -> criterion != toUpdate);
        classExercise.setGradingCriteria(gradingCriteria);

        UpdateModelingExerciseDTO updateModelingExerciseDTO = UpdateModelingExerciseDTO.of(classExercise);
        ModelingExerciseResponseDTO updatedModelingExercise = request.putWithResponseBody(
                "/api/modeling/modeling-exercises/" + classExercise.getId() + "/re-evaluate" + "?deleteFeedback=false", updateModelingExerciseDTO,
                ModelingExerciseResponseDTO.class, HttpStatus.OK);
        ModelingExercise updatedExerciseEntity = modelingExerciseTestRepository.findById(updatedModelingExercise.id()).orElseThrow();
        List<Result> updatedResults = participationUtilService.getResultsForExercise(updatedExerciseEntity);
        assertThat(updatedModelingExercise.gradingCriteria().stream().flatMap(criterion -> criterion.structuredGradingInstructions().stream())
                .anyMatch(instruction -> instruction.credits() == 3)).isTrue();
        assertThat(updatedResults.getFirst().getScore()).isEqualTo(60);
        assertThat(updatedResults.getFirst().getFeedbacks().iterator().next().getCredits()).isEqualTo(3);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testReEvaluateAndUpdateModelingExercise_shouldDeleteFeedbacks() throws Exception {
        Set<GradingCriterion> gradingCriteria = exerciseUtilService.addGradingInstructionsToExercise(classExercise);
        gradingCriterionRepository.saveAll(gradingCriteria);

        participationUtilService.addAssessmentWithFeedbackWithGradingInstructionsForExercise(classExercise, TEST_PREFIX + "instructor1");

        // remove instruction which is associated with feedbacks
        gradingCriteria.removeIf(criterion -> criterion.getTitle() == null);
        classExercise.setGradingCriteria(gradingCriteria);

        UpdateModelingExerciseDTO updateModelingExerciseDTO = UpdateModelingExerciseDTO.of(classExercise);
        ModelingExerciseResponseDTO updatedModelingExercise = request.putWithResponseBody(
                "/api/modeling/modeling-exercises/" + classExercise.getId() + "/re-evaluate" + "?deleteFeedback=true", updateModelingExerciseDTO, ModelingExerciseResponseDTO.class,
                HttpStatus.OK);
        ModelingExercise updatedExerciseEntity = modelingExerciseTestRepository.findById(updatedModelingExercise.id()).orElseThrow();
        List<Result> updatedResults = participationUtilService.getResultsForExercise(updatedExerciseEntity);
        assertThat(updatedModelingExercise.gradingCriteria()).hasSize(2);
        assertThat(updatedResults.getFirst().getScore()).isZero();
        assertThat(updatedResults.getFirst().getFeedbacks()).isEmpty();
    }

    @Test
    @WithMockUser(username = OTHER_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testReEvaluateAndUpdateModelingExercise_isNotAtLeastInstructorInCourse_forbidden() throws Exception {
        Course course = modelingExerciseUtilService.addEnrolledCourseWithOneModelingExercise("ClassDiagram", TEST_PREFIX);
        classExercise = (ModelingExercise) course.getExercises().iterator().next();
        request.put("/api/modeling/modeling-exercises/" + classExercise.getId() + "/re-evaluate", UpdateModelingExerciseDTO.of(classExercise), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testReEvaluateAndUpdateModelingExercise_isNotSameGivenExerciseIdInRequestBody_conflict() throws Exception {
        ModelingExercise modelingExerciseToBeConflicted = modelingExerciseTestRepository.findByIdElseThrow(classExercise.getId());
        modelingExerciseToBeConflicted.setId(123456789L);

        UpdateModelingExerciseDTO dto = UpdateModelingExerciseDTO.of(modelingExerciseToBeConflicted);
        request.put("/api/modeling/modeling-exercises/" + classExercise.getId() + "/re-evaluate", dto, HttpStatus.CONFLICT);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testReEvaluateAndUpdateModelingExercise_notFound() throws Exception {
        request.put("/api/modeling/modeling-exercises/" + 123456789 + "/re-evaluate", UpdateModelingExerciseDTO.of(classExercise), HttpStatus.NOT_FOUND);
    }

    /**
     * Persists {@link #classExercise} with one competency link and two categories, then builds the update request body the
     * exact way the Angular client does: a JSON object whose {@code competencyLinks} and {@code categories} are explicit
     * arrays.
     * <p>
     * Building the body as a JSON tree is essential: {@code UpdateModelingExerciseDTO} uses bare {@code @JsonInclude}, whose
     * default is {@code ALWAYS}. Serializing the DTO directly therefore mirrors only the values produced by
     * {@code .of(entity)}; it does not let the test choose whether a field is omitted, explicitly {@code null}, or an empty
     * array. A {@code JsonNode} body serializes verbatim, so the returned {@link ObjectNode} lets the test control those exact
     * wire values and send the collections as the real client does (empty or populated), exercising the mutate/clear path.
     *
     * @return the client-shaped request body, pre-populated from the persisted exercise (competencyLinks and categories non-empty)
     */
    private ObjectNode persistPopulatedExerciseAndBuildClientBody() {
        classExercise.setCategories(new HashSet<>(Set.of("uml", "diagrams")));
        classExercise.setCompetencyLinks(new HashSet<>(Set.of(new CompetencyExerciseLink(competency, classExercise, 1))));
        modelingExerciseTestRepository.save(classExercise);
        return (ObjectNode) request.getObjectMapper().valueToTree(UpdateModelingExerciseDTO.of(classExercise));
    }

    /**
     * Reproduces the real client PUT update with EXPLICIT empty {@code competencyLinks} and {@code categories} arrays on an
     * exercise that currently HAS a competency link and categories. This is the path that a {@code .of(entity)}-based test
     * cannot reach because that shortcut mirrors the entity's current collections instead of letting the test control the
     * exact omitted, {@code null}, or empty wire values. The competency-link clear runs on the eagerly-fetched (populated)
     * collection; the categories are replaced via the setter. Both must succeed with 200 and the
     * collections must be empty when reloaded from a fresh session.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateModelingExercise_withExplicitEmptyClientCollections_clearsPopulatedCollections() throws Exception {
        ObjectNode body = persistPopulatedExerciseAndBuildClientBody();
        body.set("competencyLinks", request.getObjectMapper().createArrayNode());
        body.set("categories", request.getObjectMapper().createArrayNode());

        request.putWithResponseBody("/api/modeling/modeling-exercises", body, ModelingExerciseResponseDTO.class, HttpStatus.OK);

        ModelingExercise reloaded = modelingExerciseTestRepository.findWithEagerExampleSubmissionsAndCompetenciesByIdElseThrow(classExercise.getId());
        assertThat(reloaded.getCompetencyLinks()).isEmpty();
        assertThat(reloaded.getCategories()).isEmpty();
    }

    /**
     * Same explicit-empty-collections client body as the update case, but against the re-evaluate endpoint — the exact
     * scenario that produced a LazyInitializationException 500 in the sibling text-exercise PR. Must return 200 and clear the
     * populated collections.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testReEvaluateModelingExercise_withExplicitEmptyClientCollections_returnsOk() throws Exception {
        ObjectNode body = persistPopulatedExerciseAndBuildClientBody();
        body.set("competencyLinks", request.getObjectMapper().createArrayNode());
        body.set("categories", request.getObjectMapper().createArrayNode());

        request.putWithResponseBody("/api/modeling/modeling-exercises/" + classExercise.getId() + "/re-evaluate?deleteFeedback=false", body, ModelingExerciseResponseDTO.class,
                HttpStatus.OK);

        ModelingExercise reloaded = modelingExerciseTestRepository.findWithEagerExampleSubmissionsAndCompetenciesByIdElseThrow(classExercise.getId());
        assertThat(reloaded.getCompetencyLinks()).isEmpty();
        assertThat(reloaded.getCategories()).isEmpty();
    }

    /**
     * Positive counterpart: the client sends EXPLICIT non-empty {@code competencyLinks} (weight changed) and {@code categories}
     * arrays. Proves the raw-JSON path both persists the new categories and re-applies the competency link (not just that it
     * clears), so the empty-array assertions above are meaningful.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateModelingExercise_withExplicitNonEmptyClientCollections_persists() throws Exception {
        ObjectNode body = persistPopulatedExerciseAndBuildClientBody();
        // Re-send the existing competency link with a changed weight (matched by competency id → no re-lookup needed).
        ((ObjectNode) ((ArrayNode) body.get("competencyLinks")).get(0)).put("weight", 2.0);
        // Replace the categories with a different, explicit non-empty array.
        ArrayNode categories = request.getObjectMapper().createArrayNode();
        categories.add("alpha");
        categories.add("beta");
        body.set("categories", categories);

        request.putWithResponseBody("/api/modeling/modeling-exercises", body, ModelingExerciseResponseDTO.class, HttpStatus.OK);

        ModelingExercise reloaded = modelingExerciseTestRepository.findWithEagerExampleSubmissionsAndCompetenciesByIdElseThrow(classExercise.getId());
        assertThat(reloaded.getCategories()).containsExactlyInAnyOrder("alpha", "beta");
        assertThat(reloaded.getCompetencyLinks()).hasSize(1);
        CompetencyExerciseLink persistedLink = reloaded.getCompetencyLinks().iterator().next();
        assertThat(persistedLink.getCompetency().getId()).isEqualTo(competency.getId());
        assertThat(persistedLink.getWeight()).isEqualTo(2.0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createModelingExercise_setInvalidExampleSolutionPublicationDate_badRequest() throws Exception {
        final var baseTime = ZonedDateTime.now();
        final Course course = modelingExerciseUtilService.addEnrolledCourseWithOneModelingExercise("ClassDiagram", TEST_PREFIX);
        ModelingExercise modelingExercise = modelingExerciseTestRepository.findByCourseIdWithCategories(course.getId()).getFirst();
        modelingExercise.setId(null);
        modelingExercise.setAssessmentDueDate(null);
        modelingExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);

        modelingExercise.setReleaseDate(baseTime.plusHours(1));
        modelingExercise.setDueDate(baseTime.plusHours(3));
        modelingExercise.setExampleSolutionPublicationDate(baseTime.plusHours(2));

        request.postWithResponseBody("/api/modeling/modeling-exercises", UpdateModelingExerciseDTO.of(modelingExercise), ModelingExerciseResponseDTO.class, HttpStatus.BAD_REQUEST);

        modelingExercise.setReleaseDate(baseTime.plusHours(3));
        modelingExercise.setDueDate(null);
        modelingExercise.setExampleSolutionPublicationDate(baseTime.plusHours(2));

        request.postWithResponseBody("/api/modeling/modeling-exercises", UpdateModelingExerciseDTO.of(modelingExercise), ModelingExerciseResponseDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createModelingExercise_setValidExampleSolutionPublicationDate() throws Exception {
        final var baseTime = ZonedDateTime.now();
        final Course course = modelingExerciseUtilService.addEnrolledCourseWithOneModelingExercise("ClassDiagram", TEST_PREFIX);
        ModelingExercise modelingExercise = modelingExerciseTestRepository.findByCourseIdWithCategories(course.getId()).getFirst();
        modelingExercise.setId(null);
        modelingExercise.setAssessmentDueDate(null);
        modelingExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);

        modelingExercise.setReleaseDate(baseTime.plusHours(1));
        modelingExercise.setDueDate(baseTime.plusHours(2));
        var exampleSolutionPublicationDate = baseTime.plusHours(3);
        modelingExercise.setExampleSolutionPublicationDate(exampleSolutionPublicationDate);
        modelingExercise.setChannelName("testchannelname-" + UUID.randomUUID().toString().substring(0, 8));
        var result = request.postWithResponseBody("/api/modeling/modeling-exercises", UpdateModelingExerciseDTO.of(modelingExercise), ModelingExerciseResponseDTO.class,
                HttpStatus.CREATED);
        assertThat(result.exampleSolutionPublicationDate()).isEqualTo(exampleSolutionPublicationDate);

        modelingExercise.setIncludedInOverallScore(IncludedInOverallScore.NOT_INCLUDED);
        modelingExercise.setReleaseDate(baseTime.plusHours(1));
        modelingExercise.setDueDate(baseTime.plusHours(3));
        exampleSolutionPublicationDate = baseTime.plusHours(2);
        modelingExercise.setExampleSolutionPublicationDate(exampleSolutionPublicationDate);
        modelingExercise.setChannelName("testchannelname-" + UUID.randomUUID().toString().substring(0, 8));

        result = request.postWithResponseBody("/api/modeling/modeling-exercises", UpdateModelingExerciseDTO.of(modelingExercise), ModelingExerciseResponseDTO.class,
                HttpStatus.CREATED);
        assertThat(result.exampleSolutionPublicationDate()).isEqualTo(exampleSolutionPublicationDate);

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createModelingExercise_invalidPlagiarismDetectionConfig_badRequest() throws Exception {
        ModelingExercise modelingExercise = ModelingExerciseFactory.createModelingExercise(classExercise.getCourseViaExerciseGroupOrCourseMember().getId());
        modelingExercise.setTitle("Exercise with invalid plagiarism config");
        modelingExercise.setChannelName("test-modeling-channel");

        var config = new PlagiarismDetectionConfig();
        config.setSimilarityThreshold(-1); // invalid: below 0
        config.setMinimumScore(50);
        config.setMinimumSize(50);
        config.setContinuousPlagiarismControlPlagiarismCaseStudentResponsePeriod(7);
        modelingExercise.setPlagiarismDetectionConfig(config);

        request.postWithResponseBody("/api/modeling/modeling-exercises", UpdateModelingExerciseDTO.of(modelingExercise), ModelingExerciseResponseDTO.class, HttpStatus.BAD_REQUEST);

        config.setSimilarityThreshold(50);
        config.setMinimumScore(101); // invalid: above 100
        request.postWithResponseBody("/api/modeling/modeling-exercises", UpdateModelingExerciseDTO.of(modelingExercise), ModelingExerciseResponseDTO.class, HttpStatus.BAD_REQUEST);

        config.setMinimumScore(50);
        config.setMinimumSize(-1); // invalid: negative
        request.postWithResponseBody("/api/modeling/modeling-exercises", UpdateModelingExerciseDTO.of(modelingExercise), ModelingExerciseResponseDTO.class, HttpStatus.BAD_REQUEST);

        config.setMinimumSize(50);
        config.setContinuousPlagiarismControlPlagiarismCaseStudentResponsePeriod(32); // invalid: above 31
        request.postWithResponseBody("/api/modeling/modeling-exercises", UpdateModelingExerciseDTO.of(modelingExercise), ModelingExerciseResponseDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateModelingExercise_invalidPlagiarismDetectionConfig_badRequest() throws Exception {
        Course course = modelingExerciseUtilService.addEnrolledCourseWithOneModelingExercise("ClassDiagram", TEST_PREFIX);
        ModelingExercise modelingExercise = (ModelingExercise) course.getExercises().iterator().next();

        var config = new PlagiarismDetectionConfig();
        config.setSimilarityThreshold(101); // invalid: above 100
        config.setMinimumScore(50);
        config.setMinimumSize(50);
        config.setContinuousPlagiarismControlPlagiarismCaseStudentResponsePeriod(7);
        modelingExercise.setPlagiarismDetectionConfig(config);

        request.putWithResponseBody("/api/modeling/modeling-exercises", UpdateModelingExerciseDTO.of(modelingExercise), ModelingExerciseResponseDTO.class, HttpStatus.BAD_REQUEST);

        config.setSimilarityThreshold(50);
        config.setContinuousPlagiarismControlPlagiarismCaseStudentResponsePeriod(6); // invalid: below 7
        request.putWithResponseBody("/api/modeling/modeling-exercises", UpdateModelingExerciseDTO.of(modelingExercise), ModelingExerciseResponseDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getModelingExercise_carriesStoredPlagiarismDetectionConfig_andEchoedUpdateKeepsIt() throws Exception {
        // plagiarismDetectionConfig is LAZY and open-in-view is off, so the detail endpoint has to fetch it. If it does
        // not, the response omits the stored config, the edit form falls back to its defaults, and the next save from
        // that form silently overwrites the instructor's settings. Every value here is deliberately non-default.
        var storedConfig = new PlagiarismDetectionConfig();
        storedConfig.setContinuousPlagiarismControlEnabled(true);
        storedConfig.setContinuousPlagiarismControlPostDueDateChecksEnabled(true);
        storedConfig.setContinuousPlagiarismControlPlagiarismCaseStudentResponsePeriod(9);
        storedConfig.setSimilarityThreshold(42);
        storedConfig.setMinimumScore(13);
        storedConfig.setMinimumSize(7);
        classExercise.setPlagiarismDetectionConfig(storedConfig);
        classExercise = modelingExerciseTestRepository.save(classExercise);

        // The graph fetches example submissions through the nested exampleSubmissions.submission.results path rather
        // than listing them separately, so pin that they still reach the response.
        String validModel = TestResourceUtils.loadFileFromResources("test-data/model-submission/model.54727.json");
        participationUtilService.addExampleSubmission(participationUtilService.generateExampleSubmission(validModel, classExercise, true));

        var fetched = request.get("/api/modeling/modeling-exercises/" + classExercise.getId(), HttpStatus.OK, ModelingExerciseResponseDTO.class);
        assertThat(fetched.exampleSubmissions()).as("the nested graph path must still load example submissions").hasSize(1);
        assertThat(fetched.plagiarismDetectionConfig()).as("the detail response must carry the stored plagiarism config").isNotNull();
        assertThat(fetched.plagiarismDetectionConfig().similarityThreshold()).isEqualTo(42);
        assertThat(fetched.plagiarismDetectionConfig().minimumScore()).isEqualTo(13);
        assertThat(fetched.plagiarismDetectionConfig().minimumSize()).isEqualTo(7);
        assertThat(fetched.plagiarismDetectionConfig().continuousPlagiarismControlEnabled()).isTrue();
        assertThat(fetched.plagiarismDetectionConfig().continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod()).isEqualTo(9);

        // Echo the fetched config back the way the edit form does, changing something unrelated.
        var echoed = UpdateModelingExerciseDTO.of(classExercise);
        var body = (ObjectNode) request.getObjectMapper().valueToTree(echoed);
        body.set("plagiarismDetectionConfig", request.getObjectMapper().valueToTree(fetched.plagiarismDetectionConfig()));
        body.put("title", "Echoed update");
        request.putWithResponseBody("/api/modeling/modeling-exercises", body, ModelingExerciseResponseDTO.class, HttpStatus.OK);

        var persisted = modelingExerciseTestRepository.findForVersioningById(classExercise.getId()).orElseThrow();
        assertThat(persisted.getPlagiarismDetectionConfig().getSimilarityThreshold()).isEqualTo(42);
        assertThat(persisted.getPlagiarismDetectionConfig().getMinimumScore()).isEqualTo(13);
        assertThat(persisted.getPlagiarismDetectionConfig().getMinimumSize()).isEqualTo(7);
        assertThat(persisted.getPlagiarismDetectionConfig().isContinuousPlagiarismControlEnabled()).isTrue();
        assertThat(persisted.getPlagiarismDetectionConfig().getContinuousPlagiarismControlPlagiarismCaseStudentResponsePeriod()).isEqualTo(9);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void reEvaluateModelingExercise_invalidPlagiarismDetectionConfig_badRequest() throws Exception {
        var validConfig = PlagiarismDetectionConfig.createDefault();
        classExercise.setPlagiarismDetectionConfig(validConfig);
        modelingExerciseTestRepository.save(classExercise);

        ObjectNode body = (ObjectNode) request.getObjectMapper().valueToTree(UpdateModelingExerciseDTO.of(classExercise));
        ((ObjectNode) body.get("plagiarismDetectionConfig")).put("continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod", 6);

        request.putWithResponseBody("/api/modeling/modeling-exercises/" + classExercise.getId() + "/re-evaluate?deleteFeedback=false", body, ModelingExerciseResponseDTO.class,
                HttpStatus.BAD_REQUEST);

        ModelingExercise persisted = modelingExerciseTestRepository.findForVersioningById(classExercise.getId()).orElseThrow();
        assertThat(persisted.getPlagiarismDetectionConfig().getContinuousPlagiarismControlPlagiarismCaseStudentResponsePeriod()).isEqualTo(7);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createModelingExercise_courseExercise_persistsDefaultPlagiarismDetectionConfig() throws Exception {
        // The create path fills the default for course exercises when the request omits the config, so it is not persisted
        // as null. Pin that here.
        ModelingExercise modelingExercise = ModelingExerciseFactory.createModelingExercise(classExercise.getCourseViaExerciseGroupOrCourseMember().getId());
        modelingExercise.setTitle("Course exercise with default plagiarism config");
        modelingExercise.setChannelName("test-modeling-channel-" + UUID.randomUUID().toString().substring(0, 8));

        ModelingExerciseResponseDTO created = request.postWithResponseBody("/api/modeling/modeling-exercises", UpdateModelingExerciseDTO.of(modelingExercise),
                ModelingExerciseResponseDTO.class, HttpStatus.CREATED);

        // Reload through a fresh persistence context that eagerly fetches the (lazy) plagiarism config.
        ModelingExercise reloaded = modelingExerciseTestRepository.findForVersioningById(created.id()).orElseThrow();
        assertThat(reloaded.getPlagiarismDetectionConfig()).as("course exercise create persists a non-null default plagiarism config").isNotNull();
        assertThat(reloaded.getPlagiarismDetectionConfig()).usingRecursiveComparison().ignoringFields("id").isEqualTo(PlagiarismDetectionConfig.createDefault());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createAndUpdateModelingExercise_persistsProvidedPlagiarismDetectionConfig() throws Exception {
        ModelingExercise exercise = ModelingExerciseFactory.createModelingExercise(classExercise.getCourseViaExerciseGroupOrCourseMember().getId());
        exercise.setTitle("Exercise with custom plagiarism config");
        exercise.setChannelName("test-modeling-channel-" + UUID.randomUUID().toString().substring(0, 8));
        PlagiarismDetectionConfig createConfig = PlagiarismDetectionConfig.createDefault();
        createConfig.setSimilarityThreshold(42);
        exercise.setPlagiarismDetectionConfig(createConfig);

        ModelingExerciseResponseDTO created = request.postWithResponseBody("/api/modeling/modeling-exercises", UpdateModelingExerciseDTO.of(exercise),
                ModelingExerciseResponseDTO.class, HttpStatus.CREATED);
        ModelingExercise reloaded = modelingExerciseTestRepository.findForVersioningById(created.id()).orElseThrow();
        assertThat(reloaded.getPlagiarismDetectionConfig().getSimilarityThreshold()).isEqualTo(42);
        Long originalConfigId = reloaded.getPlagiarismDetectionConfig().getId();

        ObjectNode updateBody = (ObjectNode) request.getObjectMapper().valueToTree(UpdateModelingExerciseDTO.of(reloaded));
        ((ObjectNode) updateBody.get("plagiarismDetectionConfig")).put("similarityThreshold", 73);
        request.putWithResponseBody("/api/modeling/modeling-exercises", updateBody, ModelingExerciseResponseDTO.class, HttpStatus.OK);

        // Reload through a fresh persistence context (not the in-memory managed object) and pin that the PUT merged
        // into the existing plagiarism config row instead of orphan-deleting it and inserting a new one: the config's
        // OneToOne is cascade=ALL, orphanRemoval=true, so dropping the id on write-back silently swaps the row's PK.
        ModelingExercise updated = modelingExerciseTestRepository.findForVersioningById(created.id()).orElseThrow();
        assertThat(updated.getPlagiarismDetectionConfig().getSimilarityThreshold()).isEqualTo(73);
        assertThat(updated.getPlagiarismDetectionConfig().getId()).as("PUT must keep the plagiarism config row id stable").isEqualTo(originalConfigId);

        // A client-sent config id must be ignored: the server re-attaches the exercise's own stored row id, so a
        // tampered id can neither adopt another exercise's config row nor swap this exercise's row.
        ObjectNode tamperedBody = (ObjectNode) request.getObjectMapper().valueToTree(UpdateModelingExerciseDTO.of(updated));
        ((ObjectNode) tamperedBody.get("plagiarismDetectionConfig")).put("id", originalConfigId + 12345L);
        ((ObjectNode) tamperedBody.get("plagiarismDetectionConfig")).put("similarityThreshold", 81);
        request.putWithResponseBody("/api/modeling/modeling-exercises", tamperedBody, ModelingExerciseResponseDTO.class, HttpStatus.OK);

        ModelingExercise afterTamper = modelingExerciseTestRepository.findForVersioningById(created.id()).orElseThrow();
        assertThat(afterTamper.getPlagiarismDetectionConfig().getSimilarityThreshold()).isEqualTo(81);
        assertThat(afterTamper.getPlagiarismDetectionConfig().getId()).as("a tampered client-sent config id must not replace the exercise's own row").isEqualTo(originalConfigId);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createModelingExercise_omittedIncludedInOverallScore_keepsEntityDefault() throws Exception {
        ModelingExercise exercise = ModelingExerciseFactory.createModelingExercise(classExercise.getCourseViaExerciseGroupOrCourseMember().getId());
        exercise.setTitle("Exercise without inclusion field");
        exercise.setChannelName("test-modeling-channel-" + UUID.randomUUID().toString().substring(0, 8));
        exercise.setIncludedInOverallScore(null);

        ModelingExerciseResponseDTO created = request.postWithResponseBody("/api/modeling/modeling-exercises", UpdateModelingExerciseDTO.of(exercise),
                ModelingExerciseResponseDTO.class, HttpStatus.CREATED);

        assertThat(created.includedInOverallScore()).isEqualTo(IncludedInOverallScore.INCLUDED_COMPLETELY);
        assertThat(modelingExerciseTestRepository.findById(created.id()).orElseThrow().getIncludedInOverallScore()).isEqualTo(IncludedInOverallScore.INCLUDED_COMPLETELY);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateModelingExercise_doesNotChangeDiagramType() throws Exception {
        ObjectNode updateBody = (ObjectNode) request.getObjectMapper().valueToTree(UpdateModelingExerciseDTO.of(classExercise));
        updateBody.put("diagramType", DiagramType.ActivityDiagram.name());

        ModelingExerciseResponseDTO updated = request.putWithResponseBody("/api/modeling/modeling-exercises", updateBody, ModelingExerciseResponseDTO.class, HttpStatus.OK);

        assertThat(updated.diagramType()).isEqualTo(DiagramType.ClassDiagram);
        assertThat(modelingExerciseTestRepository.findById(classExercise.getId()).orElseThrow().getDiagramType()).isEqualTo(DiagramType.ClassDiagram);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importModelingExerciseIntoCourse_persistsPlagiarismDetectionConfigFromDto() throws Exception {
        var now = ZonedDateTime.now();
        Course course1 = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        Course course2 = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        ModelingExercise sourceExercise = ModelingExerciseFactory.generateModelingExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), DiagramType.ClassDiagram,
                course1);
        // Carry a non-default (valid) plagiarism config on the source so the import DTO transports it and we can assert it is persisted.
        var config = new PlagiarismDetectionConfig();
        config.setContinuousPlagiarismControlEnabled(true);
        config.setContinuousPlagiarismControlPostDueDateChecksEnabled(true);
        config.setContinuousPlagiarismControlPlagiarismCaseStudentResponsePeriod(9);
        config.setSimilarityThreshold(42);
        config.setMinimumScore(13);
        config.setMinimumSize(7);
        sourceExercise.setPlagiarismDetectionConfig(config);
        modelingExerciseTestRepository.save(sourceExercise);

        sourceExercise.setCourse(course2);
        sourceExercise.setChannelName("channel-" + UUID.randomUUID().toString().substring(0, 8));

        var importedDto = request.postWithResponseBody("/api/modeling/modeling-exercises/import?sourceExerciseId=" + sourceExercise.getId(),
                ImportModelingExerciseDTO.of(sourceExercise), ModelingExerciseResponseDTO.class, HttpStatus.CREATED);

        // Reload through a fresh persistence context that eagerly fetches the (lazy) plagiarism config.
        ModelingExercise reloaded = modelingExerciseTestRepository.findForVersioningById(importedDto.id()).orElseThrow();
        assertThat(reloaded.getPlagiarismDetectionConfig()).as("import persists the plagiarism config carried on the DTO").isNotNull();
        assertThat(reloaded.getPlagiarismDetectionConfig()).usingRecursiveComparison().ignoringFields("id").isEqualTo(config);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void atlasML_isCalledOnCreateUpdateAndDelete() throws Exception {
        var provider = atlasMLRequestMockProvider.orElseThrow(() -> new IllegalStateException("AtlasMLRequestMockProvider must be available for AtlasML tests"));
        featureToggleService.enableFeature(Feature.AtlasML);
        try {
            provider.enableMockingOfRequests();
            provider.mockSaveCompetenciesAny();
            // Create
            var modelingExercise = ModelingExerciseFactory.createModelingExercise(classExercise.getCourseViaExerciseGroupOrCourseMember().getId());
            modelingExercise.setTitle("AtlasML Create");
            request.postWithResponseBody("/api/modeling/modeling-exercises", UpdateModelingExerciseDTO.of(modelingExercise), ModelingExerciseResponseDTO.class, HttpStatus.CREATED);

            // Update
            var created = modelingExerciseTestRepository.findByCourseIdWithCategories(classExercise.getCourseViaExerciseGroupOrCourseMember().getId()).getFirst();
            created.setTitle("AtlasML Update");
            request.putWithResponseBody("/api/modeling/modeling-exercises", UpdateModelingExerciseDTO.of(created), ModelingExerciseResponseDTO.class, HttpStatus.OK);

            // Delete
            request.delete("/api/modeling/modeling-exercises/" + created.getId(), HttpStatus.OK);
        }
        finally {
            featureToggleService.disableFeature(Feature.AtlasML);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetModelingExercise_asStudent_exampleSolutionVisibility() throws Exception {
        testGetModelingExercise_exampleSolutionVisibility(true, TEST_PREFIX + "student1");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetModelingExercise_asInstructor_exampleSolutionVisibility() throws Exception {
        testGetModelingExercise_exampleSolutionVisibility(false, TEST_PREFIX + "instructor1");
    }

    private void testGetModelingExercise_exampleSolutionVisibility(boolean isStudent, String username) throws Exception {
        // Utility function to avoid duplication
        Function<Course, ModelingExercise> modelingExerciseGetter = c -> (ModelingExercise) c.getExercises().stream().filter(e -> e.getId().equals(classExercise.getId())).findAny()
                .orElseThrow();

        classExercise.setExampleSolutionModel("<Sample solution model>");
        classExercise.setExampleSolutionExplanation("<Sample solution explanation>");

        if (isStudent) {
            participationUtilService.createAndSaveParticipationForExercise(classExercise, username);
        }

        // Test example solution publication date not set.
        classExercise.setExampleSolutionPublicationDate(null);
        modelingExerciseTestRepository.save(classExercise);

        CourseForDashboardDTO courseForDashboard = request.get("/api/course/courses/" + classExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/for-dashboard",
                HttpStatus.OK, CourseForDashboardDTO.class);
        Course course = courseForDashboard.course();
        ModelingExercise modelingExercise = modelingExerciseGetter.apply(course);

        if (isStudent) {
            assertThat(modelingExercise.getExampleSolutionModel()).isNull();
            assertThat(modelingExercise.getExampleSolutionExplanation()).isNull();
        }
        else {
            assertThat(modelingExercise.getExampleSolutionModel()).isEqualTo(classExercise.getExampleSolutionModel());
            assertThat(modelingExercise.getExampleSolutionExplanation()).isEqualTo(classExercise.getExampleSolutionExplanation());
        }

        // Test example solution publication date in the past.
        classExercise.setExampleSolutionPublicationDate(ZonedDateTime.now().minusHours(1));
        modelingExerciseTestRepository.save(classExercise);

        courseForDashboard = request.get("/api/course/courses/" + classExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/for-dashboard", HttpStatus.OK,
                CourseForDashboardDTO.class);
        course = courseForDashboard.course();
        modelingExercise = modelingExerciseGetter.apply(course);

        assertThat(modelingExercise.getExampleSolutionModel()).isEqualTo(classExercise.getExampleSolutionModel());
        assertThat(modelingExercise.getExampleSolutionExplanation()).isEqualTo(classExercise.getExampleSolutionExplanation());

        // Test example solution publication date in the future.
        classExercise.setExampleSolutionPublicationDate(ZonedDateTime.now().plusHours(1));
        modelingExerciseTestRepository.save(classExercise);

        courseForDashboard = request.get("/api/course/courses/" + classExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/for-dashboard", HttpStatus.OK,
                CourseForDashboardDTO.class);
        course = courseForDashboard.course();
        modelingExercise = modelingExerciseGetter.apply(course);

        if (isStudent) {
            assertThat(modelingExercise.getExampleSolutionModel()).isNull();
            assertThat(modelingExercise.getExampleSolutionExplanation()).isNull();
        }
        else {
            assertThat(modelingExercise.getExampleSolutionModel()).isEqualTo(classExercise.getExampleSolutionModel());
            assertThat(modelingExercise.getExampleSolutionExplanation()).isEqualTo(classExercise.getExampleSolutionExplanation());
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportModelingExercise_setGradingInstructionForCopiedFeedback() throws Exception {
        var now = ZonedDateTime.now();
        Course course1 = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        Course course2 = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);

        ModelingExercise modelingExercise = ModelingExerciseFactory.generateModelingExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), DiagramType.ClassDiagram,
                course1);
        modelingExercise = modelingExerciseTestRepository.save(modelingExercise);
        Set<GradingCriterion> gradingCriteria = exerciseUtilService.addGradingInstructionsToExercise(modelingExercise);
        gradingCriterionRepository.saveAll(gradingCriteria);
        GradingInstruction gradingInstruction = GradingCriterionUtil.findAnyInstructionWhere(gradingCriteria, instruction -> instruction.getFeedback() != null).orElseThrow();

        // Create example submission
        var exampleSubmission = participationUtilService.generateExampleSubmission("model", modelingExercise, true);
        exampleSubmission = participationUtilService.addExampleSubmission(exampleSubmission);
        participationUtilService.addResultToSubmission(exampleSubmission.getSubmission(), AssessmentType.MANUAL, modelingExercise.getId());
        var submission = submissionRepository.findWithEagerResultAndFeedbackAndAssessmentNoteById(exampleSubmission.getSubmission().getId()).orElseThrow();

        Feedback feedback = ParticipationFactory.generateFeedback().getFirst();
        feedback.setGradingInstruction(gradingInstruction);
        participationUtilService.addFeedbackToResult(feedback, Objects.requireNonNull(submission.getLatestResult()));
        modelingExercise.setChannelName("testchannel-" + UUID.randomUUID().toString().substring(0, 8));
        modelingExercise.setCourse(course2);
        var importedModelingExercise = request.postWithResponseBody("/api/modeling/modeling-exercises/import?sourceExerciseId=" + modelingExercise.getId(),
                ImportModelingExerciseDTO.of(modelingExercise), ModelingExerciseResponseDTO.class, HttpStatus.CREATED);

        assertThat(modelingExerciseTestRepository.findById(importedModelingExercise.id())).isPresent();

        var importedModelingExerciseFromDb = modelingExerciseTestRepository.findByIdWithExampleSubmissionsAndResultsAndGradingCriteria(importedModelingExercise.id()).orElseThrow();
        var importedExampleSubmission = importedModelingExerciseFromDb.getExampleSubmissions().stream().findFirst().orElseThrow();
        GradingInstruction importedFeedbackGradingInstruction = importedExampleSubmission.getSubmission().getLatestResult().getFeedbacks().iterator().next()
                .getGradingInstruction();
        assertThat(importedFeedbackGradingInstruction).isNotNull();

        // Copy and original should have the same data but not the same ids.
        assertThat(importedFeedbackGradingInstruction.getId()).isNotEqualTo(gradingInstruction.getId());
        assertThat(importedFeedbackGradingInstruction.getFeedback()).isEqualTo(gradingInstruction.getFeedback());
        assertThat(importedFeedbackGradingInstruction.getGradingScale()).isEqualTo(gradingInstruction.getGradingScale());
        assertThat(importedFeedbackGradingInstruction.getInstructionDescription()).isEqualTo(gradingInstruction.getInstructionDescription());
        assertThat(importedFeedbackGradingInstruction.getCredits()).isEqualTo(gradingInstruction.getCredits());
        assertThat(importedFeedbackGradingInstruction.getUsageCount()).isEqualTo(gradingInstruction.getUsageCount());

        assertThat(importedFeedbackGradingInstruction.getGradingCriterion().getId()).isNotEqualTo(gradingInstruction.getGradingCriterion().getId());

    }

    /**
     * Wire-contract pin: the single-GET of an exam modeling exercise must carry the nested exercise-group/exam/course so
     * the unchanged client can resolve the exam course (management access rights) and rebuild an exam-edit request from
     * the flattened response. The flattened response must additionally let a client-shaped exam-edit PUT (courseId absent,
     * exerciseGroupId present) succeed with 200 — the marquee 400 "courseOrExerciseGroupMissing" regression.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetAndUpdateExamModelingExercise_carriesExamGroupAndAcceptsFlattenedEdit() throws Exception {
        ExerciseGroup exerciseGroup = examUtilService.addEnrolledExerciseGroupWithExamAndCourse(true, true, TEST_PREFIX);
        ModelingExercise modelingExercise = ModelingExerciseFactory.generateModelingExerciseForExam(DiagramType.ClassDiagram, exerciseGroup);
        modelingExercise = modelingExerciseTestRepository.save(modelingExercise);

        ModelingExerciseResponseDTO response = request.get("/api/modeling/modeling-exercises/" + modelingExercise.getId(), HttpStatus.OK, ModelingExerciseResponseDTO.class);

        // Exam single-GET carries exerciseGroup.exam.course.id and keeps the course/exerciseGroup flattening exclusive.
        assertThat(response.exerciseGroupId()).isEqualTo(exerciseGroup.getId());
        assertThat(response.courseId()).isNull();
        assertThat(response.exerciseGroup()).isNotNull();
        assertThat(response.exerciseGroup().exam()).isNotNull();
        assertThat(response.exerciseGroup().exam().course()).isNotNull();
        assertThat(response.exerciseGroup().exam().course().id()).isEqualTo(exerciseGroup.getExam().getCourse().getId());

        // Build the exam-edit request the way the client does (from the flattened response): courseId absent, exerciseGroupId present.
        UpdateModelingExerciseDTO editDto = new UpdateModelingExerciseDTO(response.id(), response.title(), response.channelName(), response.shortName(),
                response.problemStatement(), response.categories(), response.difficulty(), response.maxPoints(), response.bonusPoints(), response.includedInOverallScore(),
                response.allowComplaintsForAutomaticAssessments(), response.presentationScoreEnabled(), response.secondCorrectionEnabled(), response.gradingInstructions(),
                response.releaseDate(), response.startDate(), response.dueDate(), response.assessmentDueDate(), response.exampleSolutionPublicationDate(), response.diagramType(),
                response.exampleSolutionModel(), response.exampleSolutionExplanation(), response.courseId(), response.exerciseGroupId(), response.mode(),
                response.teamAssignmentConfig(), response.plagiarismDetectionConfig(), response.gradingCriteria(), response.competencyLinks());

        ModelingExerciseResponseDTO updated = request.putWithResponseBody("/api/modeling/modeling-exercises", editDto, ModelingExerciseResponseDTO.class, HttpStatus.OK);

        // The PUT response must carry the same exerciseGroup.exam.course wiring as the GET above.
        assertThat(updated.exerciseGroup()).isNotNull();
        assertThat(updated.exerciseGroup().exam()).isNotNull();
        assertThat(updated.exerciseGroup().exam().course()).isNotNull();
        assertThat(updated.exerciseGroup().exam().course().id()).isEqualTo(exerciseGroup.getExam().getCourse().getId());
    }

    /**
     * Wire-contract pin: the course modeling-exercise list DTO must carry the scalars the course-management table binds
     * (bonusPoints, includedInOverallScore, presentationScoreEnabled, teamMode) and the nested course title.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetModelingExercisesForCourse_listDtoCarriesTableScalars() throws Exception {
        classExercise.setBonusPoints(5.0);
        classExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);
        classExercise.setPresentationScoreEnabled(true);
        modelingExerciseTestRepository.save(classExercise);

        List<ModelingExerciseListItemDTO> exercises = request.getList(
                "/api/modeling/courses/" + classExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/modeling-exercises", HttpStatus.OK,
                ModelingExerciseListItemDTO.class);
        ModelingExerciseListItemDTO listItem = exercises.stream().filter(exercise -> exercise.id().equals(classExercise.getId())).findFirst().orElseThrow();

        assertThat(listItem.bonusPoints()).isEqualTo(5.0);
        assertThat(listItem.includedInOverallScore()).isEqualTo(IncludedInOverallScore.INCLUDED_COMPLETELY);
        assertThat(listItem.presentationScoreEnabled()).isTrue();
        assertThat(listItem.teamMode()).isFalse();
        assertThat(listItem.course()).isNotNull();
        assertThat(listItem.course().title()).isEqualTo(classExercise.getCourseViaExerciseGroupOrCourseMember().getTitle());
    }
}
