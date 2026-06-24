package de.tum.cit.aet.artemis.text;

import static de.tum.cit.aet.artemis.core.util.TestResourceUtils.HalfSecond;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertExerciseExistsInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertExerciseNotInWeaviate;
import static de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismStatus.CONFIRMED;
import static de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismStatus.DENIED;
import static de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismStatus.NONE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.ExampleSubmission;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.assessment.domain.GradingInstruction;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.FeedbackRepository;
import de.tum.cit.aet.artemis.assessment.repository.GradingCriterionRepository;
import de.tum.cit.aet.artemis.assessment.test_repository.ExampleSubmissionTestRepository;
import de.tum.cit.aet.artemis.assessment.util.GradingCriterionUtil;
import de.tum.cit.aet.artemis.atlas.competency.util.CompetencyUtilService;
import de.tum.cit.aet.artemis.atlas.connector.AtlasMLRequestMockProvider;
import de.tum.cit.aet.artemis.atlas.domain.LearningObject;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.communication.util.ConversationUtilService;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.core.dto.SortingOrder;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import de.tum.cit.aet.artemis.core.util.PageUtil;
import de.tum.cit.aet.artemis.core.util.PageableSearchUtilService;
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
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.globalsearch.service.WeaviateService;
import de.tum.cit.aet.artemis.lecture.dto.CompetencyLinkDTO;
import de.tum.cit.aet.artemis.plagiarism.PlagiarismUtilService;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismComparison;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismDetectionConfig;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismResult;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismStatus;
import de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismComparisonStatusDTO;
import de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismResultDTO;
import de.tum.cit.aet.artemis.plagiarism.repository.PlagiarismComparisonRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextBlock;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.dto.ImportTextExerciseDTO;
import de.tum.cit.aet.artemis.text.dto.TextExerciseListItemDTO;
import de.tum.cit.aet.artemis.text.dto.TextExerciseResponseDTO;
import de.tum.cit.aet.artemis.text.dto.TextParticipationDTO;
import de.tum.cit.aet.artemis.text.dto.TextSubmissionAssessmentDTO;
import de.tum.cit.aet.artemis.text.dto.TextSubmissionRequestDTO;
import de.tum.cit.aet.artemis.text.dto.TextSubmissionResponseDTO;
import de.tum.cit.aet.artemis.text.dto.UpdateTextExerciseDTO;
import de.tum.cit.aet.artemis.text.repository.TextExerciseRepository;
import de.tum.cit.aet.artemis.text.test_repository.TextSubmissionTestRepository;
import de.tum.cit.aet.artemis.text.util.TextExerciseFactory;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

class TextExerciseIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "textexerciseintegration";

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private TextSubmissionTestRepository textSubmissionRepository;

    @Autowired
    private ExampleSubmissionTestRepository exampleSubmissionRepo;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private PlagiarismComparisonRepository plagiarismComparisonRepository;

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private GradingCriterionRepository gradingCriterionRepository;

    @Autowired
    private StudentParticipationTestRepository studentParticipationRepository;

    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private PageableSearchUtilService pageableSearchUtilService;

    @Autowired
    private PlagiarismUtilService plagiarismUtilService;

    @Autowired
    private CompetencyUtilService competencyUtilService;

    @Autowired
    private ConversationUtilService conversationUtilService;

    @Autowired
    private Optional<AtlasMLRequestMockProvider> atlasMLRequestMockProvider;

    @Autowired
    private FeatureToggleService featureToggleService;

    @Autowired(required = false)
    private WeaviateService weaviateService;

    private Course course;

    private TextExercise textExercise;

    private Competency competency;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 0, 1);
        userUtilService.addInstructor("other-instructors", TEST_PREFIX + "instructorother1");
        course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).getFirst();
        competency = competencyUtilService.createCompetency(course);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void submitEnglishTextExercise() throws Exception {
        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("This Submission is written in English", Language.ENGLISH, false);
        request.postWithResponseBody("/api/exercise/exercises/" + textExercise.getId() + "/participations", null, Participation.class);
        TextSubmissionRequestDTO submissionRequest = new TextSubmissionRequestDTO(textSubmission.getId(), textSubmission.getText(), textSubmission.getLanguage(),
                textSubmission.isSubmitted());
        TextSubmissionResponseDTO submissionResponse = request.postWithResponseBody("/api/text/exercises/" + textExercise.getId() + "/text-submissions", submissionRequest,
                TextSubmissionResponseDTO.class);

        Optional<TextSubmission> result = textSubmissionRepository.findById(submissionResponse.id());
        assertThat(result).isPresent();
        result.ifPresent(submission -> assertThat(submission.getLanguage()).isEqualTo(Language.ENGLISH));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteTextExerciseWithSubmissionWithTextBlocks() throws Exception {
        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("Lorem Ipsum Foo Bar", Language.ENGLISH, true);
        textSubmission = textExerciseUtilService.saveTextSubmission(textExercise, textSubmission, TEST_PREFIX + "student1");
        int submissionCount = 5;
        int submissionSize = 4;
        var textBlocks = TextExerciseFactory.generateTextBlocks(submissionCount * submissionSize);
        textExerciseUtilService.addAndSaveTextBlocksToTextSubmission(textBlocks, textSubmission);

        request.delete("/api/text/text-exercises/" + textExercise.getId(), HttpStatus.OK);
        assertThat(textExerciseRepository.findById(textExercise.getId())).as("text exercise was deleted").isEmpty();
        assertExerciseNotInWeaviate(weaviateService, textExercise.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteTextExerciseWithChannel() throws Exception {
        Course course = courseUtilService.createCourse();
        ZonedDateTime now = ZonedDateTime.now();
        TextExercise textExercise = textExerciseUtilService.createIndividualTextExercise(course, now, now, now);
        Channel exerciseChannel = conversationUtilService.addChannelToExercise(textExercise);

        request.delete("/api/text/text-exercises/" + textExercise.getId(), HttpStatus.OK);

        Optional<Channel> exerciseChannelAfterDelete = channelRepository.findById(exerciseChannel.getId());
        assertThat(exerciseChannelAfterDelete).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteTextExerciseWithCompetency() throws Exception {
        textExercise.setCompetencyLinks(Set.of(new CompetencyExerciseLink(competency, textExercise, 1)));
        textExerciseRepository.save(textExercise);

        request.delete("/api/text/text-exercises/" + textExercise.getId(), HttpStatus.OK);

        verify(competencyProgressApi).updateProgressByCompetencyAsync(eq(competency));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteExamTextExercise() throws Exception {
        TextExercise textExercise = examUtilService.addCourseExamExerciseGroupWithOneTextExercise();

        request.delete("/api/text/text-exercises/" + textExercise.getId(), HttpStatus.OK);
        assertThat(textExerciseRepository.findById(textExercise.getId())).isNotPresent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteTextExercise_notFound() throws Exception {
        TextExercise textExercise = new TextExercise();
        textExercise.setId(114213211L);

        request.delete("/api/text/text-exercises/" + textExercise.getId(), HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteTextExercise_isNotAtLeastInstructorInCourse_forbidden() throws Exception {
        course.setInstructorGroupName("test");
        courseRepository.save(course);

        request.delete("/api/text/text-exercises/" + textExercise.getId(), HttpStatus.FORBIDDEN);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "exercise-new-text-exercise", "" })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createTextExercise(String channelName) throws Exception {
        courseUtilService.enableMessagingForCourse(course);

        String title = "New Text Exercise";
        DifficultyLevel difficulty = DifficultyLevel.HARD;

        textExercise.setId(null);
        textExercise.setTitle(title);
        textExercise.setDifficulty(difficulty);
        textExercise.setChannelName(channelName);
        TextExerciseResponseDTO newTextExercise = request.postWithResponseBody("/api/text/text-exercises", UpdateTextExerciseDTO.of(textExercise), TextExerciseResponseDTO.class,
                HttpStatus.CREATED);

        Channel channel = channelRepository.findChannelByExerciseId(newTextExercise.id());

        assertThat(newTextExercise.title()).as("text exercise title was correctly set").isEqualTo(title);
        assertThat(newTextExercise.difficulty()).as("text exercise difficulty was correctly set").isEqualTo(difficulty);
        assertThat(newTextExercise.assessmentType()).as("assessment type defaults to MANUAL on create (the create DTO does not carry it)").isEqualTo(AssessmentType.MANUAL);
        assertThat(newTextExercise.courseId()).as("course was set for normal exercise").isNotNull();
        assertThat(newTextExercise.exerciseGroupId()).as("exerciseGroup was not set for normal exercise").isNull();
        assertThat(newTextExercise.courseId()).as("courseId was set correctly").isEqualTo(course.getId());
        assertThat(channel).as("channel was created").isNotNull();
        assertThat(channel.getName()).as("channel name was set correctly").isEqualTo("exercise-new-text-exercise");
        assertExerciseExistsInWeaviate(weaviateService, textExerciseRepository.findById(newTextExercise.id()).orElseThrow());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createTeamTextExercise() throws Exception {
        courseUtilService.enableMessagingForCourse(course);
        textExercise.setId(null);
        textExercise.setTitle("New Team Text Exercise");
        textExercise.setChannelName("exercise-new-team-text");
        textExercise.setMode(ExerciseMode.TEAM);
        TeamAssignmentConfig teamAssignmentConfig = new TeamAssignmentConfig();
        teamAssignmentConfig.setExercise(textExercise);
        teamAssignmentConfig.setMinTeamSize(2);
        teamAssignmentConfig.setMaxTeamSize(5);
        textExercise.setTeamAssignmentConfig(teamAssignmentConfig);

        TextExerciseResponseDTO response = request.postWithResponseBody("/api/text/text-exercises", UpdateTextExerciseDTO.of(textExercise), TextExerciseResponseDTO.class,
                HttpStatus.CREATED);

        assertThat(response.mode()).as("team mode was persisted on the response").isEqualTo(ExerciseMode.TEAM);
        assertThat(response.teamMode()).as("teamMode is exposed so the client activates the team UI for a team exercise").isTrue();
        assertThat(response.teamAssignmentConfig()).as("team assignment config is present on the response").isNotNull();
        assertThat(response.teamAssignmentConfig().minTeamSize()).as("min team size was persisted").isEqualTo(2);
        assertThat(response.teamAssignmentConfig().maxTeamSize()).as("max team size was persisted").isEqualTo(5);

        TextExercise reloaded = textExerciseRepository.findWithEagerTeamAssignmentConfigAndCategoriesAndCompetenciesById(response.id()).orElseThrow();
        assertThat(reloaded.getMode()).as("team mode was persisted on the entity").isEqualTo(ExerciseMode.TEAM);
        assertThat(reloaded.getTeamAssignmentConfig()).as("team assignment config was persisted on the entity").isNotNull();
        assertThat(reloaded.getTeamAssignmentConfig().getMinTeamSize()).as("min team size was persisted on the entity").isEqualTo(2);
        assertThat(reloaded.getTeamAssignmentConfig().getMaxTeamSize()).as("max team size was persisted on the entity").isEqualTo(5);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createTextExercise_setExerciseTitleNull_badRequest() throws Exception {
        TextExercise textExercise = new TextExercise();

        request.postWithResponseBody("/api/text/text-exercises", UpdateTextExerciseDTO.of(textExercise), TextExerciseResponseDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createTextExercise_setAssessmentDueDateWithoutExerciseDueDate_badRequest() throws Exception {
        textExercise.setId(null);
        textExercise.setDueDate(null);

        request.postWithResponseBody("/api/text/text-exercises", UpdateTextExerciseDTO.of(textExercise), TextExerciseResponseDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createTextExercise_isNotAtLeastInstructorInCourse_forbidden() throws Exception {
        course.setInstructorGroupName("test");
        courseRepository.save(course);
        textExercise.setId(null);

        request.postWithResponseBody("/api/text/text-exercises", UpdateTextExerciseDTO.of(textExercise), TextExerciseResponseDTO.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createTextExerciseForExam() throws Exception {
        ExerciseGroup exerciseGroup = examUtilService.addExerciseGroupWithExamAndCourse(true);
        TextExercise textExercise = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup);

        String title = "New Exam Text Exercise";
        DifficultyLevel difficulty = DifficultyLevel.HARD;
        textExercise.setTitle(title);
        textExercise.setDifficulty(difficulty);
        textExercise.setChannelName("new-exam-text-exercise");
        TextExerciseResponseDTO newTextExercise = request.postWithResponseBody("/api/text/text-exercises", UpdateTextExerciseDTO.of(textExercise), TextExerciseResponseDTO.class,
                HttpStatus.CREATED);
        Channel channel = channelRepository.findChannelByExerciseId(newTextExercise.id());
        assertThat(channel).isNull(); // there should not be any channel for exam exercise

        assertThat(newTextExercise.title()).as("text exercise title was correctly set").isEqualTo(title);
        assertThat(newTextExercise.difficulty()).as("text exercise difficulty was correctly set").isEqualTo(difficulty);
        assertThat(newTextExercise.courseId()).as("course was not set for exam exercise").isNull();
        assertThat(newTextExercise.exerciseGroupId()).as("exerciseGroup was set for exam exercise").isNotNull();
        assertThat(newTextExercise.exerciseGroupId()).as("exerciseGroupId was set correctly").isEqualTo(exerciseGroup.getId());
        assertThat(newTextExercise.exerciseGroup()).as("nested exerciseGroup is exposed so the student/exam editor detects exam mode").isNotNull();
        assertThat(newTextExercise.exerciseGroup().id()).as("nested exerciseGroup id matches").isEqualTo(exerciseGroup.getId());
        assertThat(newTextExercise.exerciseGroup().exam()).as("nested exam reference is exposed for the publish-results-date check").isNotNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createTextExerciseForExam_datesSet() throws Exception {
        ExerciseGroup exerciseGroup = examUtilService.addExerciseGroupWithExamAndCourse(true);
        TextExercise textExercise = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup);
        ZonedDateTime someMoment = ZonedDateTime.of(2000, 6, 15, 0, 0, 0, 0, ZoneId.of("Z"));
        String title = "New Exam Text Exercise";
        DifficultyLevel difficulty = DifficultyLevel.HARD;
        textExercise.setTitle(title);
        textExercise.setDifficulty(difficulty);
        textExercise.setDueDate(someMoment);
        request.postWithResponseBody("/api/text/text-exercises", UpdateTextExerciseDTO.of(textExercise), TextExerciseResponseDTO.class, HttpStatus.BAD_REQUEST);
        assertThat(exerciseGroup.getExercises()).doesNotContain(textExercise);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void atlasML_isCalledOnCreateUpdateAndDelete() throws Exception {
        var provider = atlasMLRequestMockProvider.orElseThrow(() -> new IllegalStateException("AtlasMLRequestMockProvider must be available for AtlasML tests"));
        featureToggleService.enableFeature(Feature.AtlasML);
        try {
            provider.reset();
            provider.enableMockingOfRequests();
            provider.mockSaveCompetenciesAny();
            // Create
            courseUtilService.enableMessagingForCourse(course);
            textExercise.setId(null);
            textExercise.setTitle("AtlasML Create");
            textExercise.setChannelName("atlasml-create");
            request.postWithResponseBody("/api/text/text-exercises", UpdateTextExerciseDTO.of(textExercise), TextExerciseResponseDTO.class, HttpStatus.CREATED);

            // Update
            textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).getFirst();
            textExercise.setTitle("AtlasML Update");
            request.putWithResponseBody("/api/text/text-exercises", de.tum.cit.aet.artemis.text.dto.UpdateTextExerciseDTO.of(textExercise), TextExerciseResponseDTO.class,
                    HttpStatus.OK);

            // Delete
            request.delete("/api/text/text-exercises/" + textExercise.getId(), HttpStatus.OK);
        }
        finally {
            featureToggleService.disableFeature(Feature.AtlasML);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createTextExercise_invalidPlagiarismDetectionConfig_badRequest() throws Exception {
        courseUtilService.enableMessagingForCourse(course);
        textExercise.setId(null);
        textExercise.setTitle("Text exercise with invalid config");
        textExercise.setChannelName("test-text-channel");

        var config = new PlagiarismDetectionConfig();
        config.setSimilarityThreshold(-1); // invalid: below 0
        config.setMinimumScore(50);
        config.setMinimumSize(50);
        config.setContinuousPlagiarismControlPlagiarismCaseStudentResponsePeriod(7);
        textExercise.setPlagiarismDetectionConfig(config);

        // FIXME-DTO: UpdateTextExerciseDTO does not carry plagiarismDetectionConfig, so the invalid config is never sent
        // to the server and the previously asserted BAD_REQUEST validation on create can no longer be exercised through
        // this boundary. Creation now succeeds with the server's default/stored config (mirrors
        // updateTextExercise_invalidPlagiarismDetectionConfig_doesNotAffectUpdate). If create-time plagiarism config
        // validation must remain reachable from the client, the create DTO needs a plagiarismDetectionConfig field.
        request.postWithResponseBody("/api/text/text-exercises", UpdateTextExerciseDTO.of(textExercise), TextExerciseResponseDTO.class, HttpStatus.CREATED);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateTextExercise_invalidPlagiarismDetectionConfig_doesNotAffectUpdate() throws Exception {
        // With the DTO approach, PlagiarismDetectionConfig is not included in the UpdateTextExerciseDTO.
        // Invalid config set on the local object is never sent to the server, so the update succeeds.
        Course course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).getFirst();

        var config = new PlagiarismDetectionConfig();
        config.setSimilarityThreshold(101); // invalid: above 100
        config.setMinimumScore(50);
        config.setMinimumSize(50);
        config.setContinuousPlagiarismControlPlagiarismCaseStudentResponsePeriod(7);
        textExercise.setPlagiarismDetectionConfig(config);

        // The DTO does not include plagiarism config, so the server validates the stored (valid) config
        request.putWithResponseBody("/api/text/text-exercises", de.tum.cit.aet.artemis.text.dto.UpdateTextExerciseDTO.of(textExercise), TextExerciseResponseDTO.class,
                HttpStatus.OK);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ArgumentsSource(InvalidExamExerciseDatesArgumentProvider.class)
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createTextExerciseForExam_invalidExercise_dates(InvalidExamExerciseDateConfiguration invalidDates) throws Exception {
        ExerciseGroup exerciseGroup = examUtilService.addExerciseGroupWithExamAndCourse(true);
        TextExercise textExercise = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup);
        request.postWithResponseBody("/api/text/text-exercises", UpdateTextExerciseDTO.of(invalidDates.applyTo(textExercise)), TextExerciseResponseDTO.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createTextExercise_setCourseAndExerciseGroup_badRequest() throws Exception {
        ExerciseGroup exerciseGroup = examUtilService.addExerciseGroupWithExamAndCourse(true);
        TextExercise textExercise = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup);
        textExercise.setCourse(exerciseGroup.getExam().getCourse());
        request.postWithResponseBody("/api/text/text-exercises", UpdateTextExerciseDTO.of(textExercise), TextExerciseResponseDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createTextExercise_setNeitherCourseAndExerciseGroup_badRequest() throws Exception {
        TextExercise textExercise = TextExerciseFactory.generateTextExerciseForExam(null);

        request.postWithResponseBody("/api/text/text-exercises", UpdateTextExerciseDTO.of(textExercise), TextExerciseResponseDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateTextExercise_InvalidMaxScore() throws Exception {
        textExercise.setMaxPoints(0.0);
        request.putWithResponseBody("/api/text/text-exercises", de.tum.cit.aet.artemis.text.dto.UpdateTextExerciseDTO.of(textExercise), TextExerciseResponseDTO.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateTextExercise_withUnicodeLettersInTitle_succeeds() throws Exception {
        // Regression test for "Lärche": editing an exercise to a title containing umlauts / other Unicode letters must
        // succeed. The shared title validation (Exercise#validateTitle, also used by the programming edit path that the
        // bug was reported on) previously rejected such titles with an ASCII-only pattern.
        textExercise.setTitle("Lärche Übung");
        TextExercise updated = request.putWithResponseBody("/api/text/text-exercises", de.tum.cit.aet.artemis.text.dto.UpdateTextExerciseDTO.of(textExercise), TextExercise.class,
                HttpStatus.OK);
        assertThat(updated.getTitle()).isEqualTo("Lärche Übung");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateTextExercise_IncludedAsBonusInvalidBonusPoints() throws Exception {
        textExercise.setMaxPoints(10.0);
        textExercise.setBonusPoints(1.0);
        textExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_AS_BONUS);
        request.putWithResponseBody("/api/text/text-exercises", de.tum.cit.aet.artemis.text.dto.UpdateTextExerciseDTO.of(textExercise), TextExerciseResponseDTO.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateTextExercise_NotIncludedInvalidBonusPoints() throws Exception {
        textExercise.setMaxPoints(10.0);
        textExercise.setBonusPoints(1.0);
        textExercise.setIncludedInOverallScore(IncludedInOverallScore.NOT_INCLUDED);
        request.putWithResponseBody("/api/text/text-exercises", de.tum.cit.aet.artemis.text.dto.UpdateTextExerciseDTO.of(textExercise), TextExerciseResponseDTO.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateTextExercise_WithStructuredGradingInstructions() throws Exception {
        GradingCriterion criterion = new GradingCriterion();
        criterion.setTitle("Test");

        GradingInstruction gradingInstruction = new GradingInstruction();
        gradingInstruction.setCredits(2);
        gradingInstruction.setGradingScale("Good");
        gradingInstruction.setInstructionDescription("Use this Feedback to test functionality");
        gradingInstruction.setFeedback("This is a test!");
        gradingInstruction.setUsageCount(5);

        criterion.addStructuredGradingInstruction(gradingInstruction);
        textExercise.setGradingCriteria(Set.of(criterion));
        TextExerciseResponseDTO actualExerciseDto = request.putWithResponseBody("/api/text/text-exercises", de.tum.cit.aet.artemis.text.dto.UpdateTextExerciseDTO.of(textExercise),
                TextExerciseResponseDTO.class, HttpStatus.OK);

        // The response DTO carries only GradingCriterionDTOs (no exercise back-reference / criterion-instruction linkage),
        // so reload the persisted grading criteria (with eager structured instructions) to assert the wiring.
        Set<GradingCriterion> persistedCriteria = gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(actualExerciseDto.id());

        assertThat(persistedCriteria).hasSize(1);
        GradingCriterion testCriterion = persistedCriteria.iterator().next();
        assertThat(testCriterion.getTitle()).isEqualTo("Test");
        assertThat(testCriterion.getStructuredGradingInstructions()).usingRecursiveFieldByFieldElementComparatorIgnoringFields("id", "gradingCriterion", "feedbacks")
                .containsExactly(gradingInstruction);
        assertThat(testCriterion.getExercise().getId()).isEqualTo(actualExerciseDto.id());
        assertThat(testCriterion.getStructuredGradingInstructions()).allMatch(instruction -> instruction.getGradingCriterion().getId().equals(testCriterion.getId()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateTextExercise() throws Exception {
        textExercise = textExerciseRepository.findByIdWithExampleSubmissionsAndResultsElseThrow(textExercise.getId());

        // update certain attributes of text exercise
        String title = "Updated Text Exercise";
        DifficultyLevel difficulty = DifficultyLevel.HARD;
        textExercise.setTitle(title);
        textExercise.setDifficulty(difficulty);

        // update problem statement
        textExercise.setProblemStatement("New problem statement");

        // add example submission to exercise
        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("Lorem Ipsum Foo Bar", Language.ENGLISH, true);
        textSubmissionRepository.save(textSubmission);
        ExampleSubmission exampleSubmission = new ExampleSubmission();
        exampleSubmission.setSubmission(textSubmission);
        exampleSubmission.setExercise(textExercise);
        exampleSubmissionRepo.save(exampleSubmission);
        textExercise.addExampleSubmission(exampleSubmission);
        textExercise.setCompetencyLinks(Set.of(new CompetencyExerciseLink(competency, textExercise, 1)));

        TextExerciseResponseDTO updatedTextExercise = request.putWithResponseBody("/api/text/text-exercises",
                de.tum.cit.aet.artemis.text.dto.UpdateTextExerciseDTO.of(textExercise), TextExerciseResponseDTO.class, HttpStatus.OK);

        assertThat(updatedTextExercise.title()).as("text exercise title was correctly updated").isEqualTo(title);
        assertThat(updatedTextExercise.difficulty()).as("text exercise difficulty was correctly updated").isEqualTo(difficulty);
        assertThat(updatedTextExercise.courseId()).as("course was set for normal exercise").isNotNull();
        assertThat(updatedTextExercise.exerciseGroupId()).as("exerciseGroup was not set for normal exercise").isNull();
        assertThat(updatedTextExercise.courseId()).as("courseId was not updated").isEqualTo(course.getId());
        verify(examLiveEventsService, never()).createAndSendProblemStatementUpdateEvent(any(), any(), any());
        verify(groupNotificationScheduleService, timeout(2000).times(1)).checkAndCreateAppropriateNotificationsWhenUpdatingExercise(any(), any(), any(), any());
        verify(competencyProgressApi, timeout(1000).times(1)).updateProgressForUpdatedLearningObjectAsyncWithOriginalCompetencyIds(eq(Set.of()), any());
        assertExerciseExistsInWeaviate(weaviateService, textExerciseRepository.findById(updatedTextExercise.id()).orElseThrow());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateTextExerciseDueDate() throws Exception {
        final ZonedDateTime individualDueDate = ZonedDateTime.now().plusHours(20);

        {
            final TextSubmission submission1 = ParticipationFactory.generateTextSubmission("Lorem Ipsum Foo Bar", Language.ENGLISH, true);
            textExerciseUtilService.saveTextSubmission(textExercise, submission1, TEST_PREFIX + "student1");
            final TextSubmission submission2 = ParticipationFactory.generateTextSubmission("Lorem Ipsum Foo Bar", Language.ENGLISH, true);
            textExerciseUtilService.saveTextSubmission(textExercise, submission2, TEST_PREFIX + "student2");

            final var participations = new ArrayList<>(studentParticipationRepository.findByExerciseId(textExercise.getId()));
            assertThat(participations).hasSize(2);
            participations.getFirst().setIndividualDueDate(ZonedDateTime.now().plusHours(2));
            participations.get(1).setIndividualDueDate(individualDueDate);
            studentParticipationRepository.saveAll(participations);
        }

        textExercise.setDueDate(ZonedDateTime.now().plusHours(12));
        request.put("/api/text/text-exercises", de.tum.cit.aet.artemis.text.dto.UpdateTextExerciseDTO.of(textExercise), HttpStatus.OK);

        {
            final var participations = studentParticipationRepository.findByExerciseId(textExercise.getId());
            final var withNoIndividualDueDate = participations.stream().filter(participation -> participation.getIndividualDueDate() == null).toList();
            assertThat(withNoIndividualDueDate).hasSize(1);

            final var withIndividualDueDate = participations.stream().filter(participation -> participation.getIndividualDueDate() != null).toList();
            assertThat(withIndividualDueDate).hasSize(1);
            assertThat(withIndividualDueDate.getFirst().getIndividualDueDate()).isCloseTo(individualDueDate, HalfSecond());
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateTextExercise_setExerciseIdNull_created() throws Exception {
        textExercise.setId(null);
        textExercise.setChannelName("test" + UUID.randomUUID().toString().substring(0, 8));
        request.putWithResponseBody("/api/text/text-exercises", de.tum.cit.aet.artemis.text.dto.UpdateTextExerciseDTO.of(textExercise), TextExerciseResponseDTO.class,
                HttpStatus.CREATED);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateTextExercise_setExerciseIdNullWithMalformedCompetencyLink_badRequest() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UpdateTextExerciseDTO malformedCreateDto = new UpdateTextExerciseDTO(null, "text-create-" + suffix, "channel-" + suffix, "short-" + suffix,
                textExercise.getProblemStatement(), textExercise.getCategories(), textExercise.getDifficulty(), textExercise.getMaxPoints(), textExercise.getBonusPoints(),
                textExercise.getIncludedInOverallScore(), textExercise.getAllowComplaintsForAutomaticAssessments(), textExercise.getAllowFeedbackRequests(),
                textExercise.getPresentationScoreEnabled(), textExercise.getSecondCorrectionEnabled(), textExercise.getFeedbackSuggestionModule(),
                textExercise.getGradingInstructions(), textExercise.getReleaseDate(), textExercise.getStartDate(), textExercise.getDueDate(), textExercise.getAssessmentDueDate(),
                textExercise.getExampleSolutionPublicationDate(), textExercise.getExampleSolution(), course.getId(), null, null, null, null,
                Set.of(new CompetencyLinkDTO(null, 1.0)));

        request.putWithResponseBody("/api/text/text-exercises", malformedCreateDto, TextExerciseResponseDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateTextExercise_withMalformedCompetencyLink_badRequest() throws Exception {
        UpdateTextExerciseDTO malformedUpdateDto = new UpdateTextExerciseDTO(textExercise.getId(), textExercise.getTitle(), textExercise.getChannelName(),
                textExercise.getShortName(), textExercise.getProblemStatement(), textExercise.getCategories(), textExercise.getDifficulty(), textExercise.getMaxPoints(),
                textExercise.getBonusPoints(), textExercise.getIncludedInOverallScore(), textExercise.getAllowComplaintsForAutomaticAssessments(),
                textExercise.getAllowFeedbackRequests(), textExercise.getPresentationScoreEnabled(), textExercise.getSecondCorrectionEnabled(),
                textExercise.getFeedbackSuggestionModule(), textExercise.getGradingInstructions(), textExercise.getReleaseDate(), textExercise.getStartDate(),
                textExercise.getDueDate(), textExercise.getAssessmentDueDate(), textExercise.getExampleSolutionPublicationDate(), textExercise.getExampleSolution(), course.getId(),
                null, null, null, null, Set.of(new CompetencyLinkDTO(null, 1.0)));

        request.putWithResponseBody("/api/text/text-exercises", malformedUpdateDto, TextExerciseResponseDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateTextExercise_withCompetencyFromDifferentCourse_badRequest() throws Exception {
        Course otherCourse = courseUtilService.addEmptyCourse();
        Competency foreignCompetency = competencyUtilService.createCompetency(otherCourse);

        textExercise.setCompetencyLinks(Set.of(new CompetencyExerciseLink(foreignCompetency, textExercise, 1)));

        request.putWithResponseBody("/api/text/text-exercises", UpdateTextExerciseDTO.of(textExercise), TextExerciseResponseDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateTextExercise_usesOriginalCompetenciesForProgressUpdate() throws Exception {
        Competency replacementCompetency = competencyUtilService.createCompetency(course);
        textExercise.setCompetencyLinks(Set.of(new CompetencyExerciseLink(competency, textExercise, 1)));
        textExerciseRepository.saveAndFlush(textExercise);

        AtomicReference<Set<Long>> originalCompetencyIds = new AtomicReference<>();
        AtomicReference<Set<Long>> updatedCompetencyIds = new AtomicReference<>();
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Set<Long> originalIds = invocation.getArgument(0);
            LearningObject updatedLearningObject = invocation.getArgument(1);

            originalCompetencyIds.set(Set.copyOf(originalIds));
            updatedCompetencyIds.set(updatedLearningObject.getCompetencyLinks().stream().map(link -> link.getCompetency().getId()).collect(Collectors.toSet()));
            return null;
        }).when(competencyProgressApi).updateProgressForUpdatedLearningObjectAsyncWithOriginalCompetencyIds(any(), any());

        textExercise.setCompetencyLinks(Set.of(new CompetencyExerciseLink(replacementCompetency, textExercise, 1)));

        request.putWithResponseBody("/api/text/text-exercises", UpdateTextExerciseDTO.of(textExercise), TextExerciseResponseDTO.class, HttpStatus.OK);

        assertThat(originalCompetencyIds.get()).containsExactly(competency.getId());
        assertThat(updatedCompetencyIds.get()).containsExactly(replacementCompetency.getId());
        verify(competencyProgressApi, timeout(1000).times(1)).updateProgressForUpdatedLearningObjectAsyncWithOriginalCompetencyIds(any(), any());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateTextExercise_updatingCourseId_asInstructor() throws Exception {
        // Create a text exercise.
        TextExercise existingTextExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).getFirst();

        // Create a new course with different id.
        Course newCourse = courseUtilService.createCourse();

        // Assign new course to the text exercise.
        existingTextExercise.setCourse(newCourse);

        // Text exercise update with the new course should fail.
        TextExerciseResponseDTO returnedTextExercise = request.putWithResponseBody("/api/text/text-exercises",
                de.tum.cit.aet.artemis.text.dto.UpdateTextExerciseDTO.of(existingTextExercise), TextExerciseResponseDTO.class, HttpStatus.CONFLICT);
        assertThat(returnedTextExercise).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateTextExercise_isNotAtLeastInstructorInCourse_forbidden() throws Exception {
        course.setInstructorGroupName("test");
        courseRepository.save(course);

        request.putWithResponseBody("/api/text/text-exercises", de.tum.cit.aet.artemis.text.dto.UpdateTextExerciseDTO.of(textExercise), TextExerciseResponseDTO.class,
                HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateTextExerciseForExam() throws Exception {
        ExerciseGroup exerciseGroup = examUtilService.addExerciseGroupWithExamAndCourse(true, true);
        TextExercise textExercise = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup);
        textExerciseRepository.save(textExercise);

        // Update certain attributes of text exercise
        String updateTitle = "After";
        DifficultyLevel updateDifficulty = DifficultyLevel.HARD;
        textExercise.setTitle(updateTitle);
        textExercise.setDifficulty(updateDifficulty);

        // update problem statement
        textExercise.setProblemStatement("New problem statement");

        TextExerciseResponseDTO updatedTextExercise = request.putWithResponseBody("/api/text/text-exercises",
                de.tum.cit.aet.artemis.text.dto.UpdateTextExerciseDTO.of(textExercise), TextExerciseResponseDTO.class, HttpStatus.OK);

        assertThat(updatedTextExercise.title()).as("text exercise title was correctly updated").isEqualTo(updateTitle);
        assertThat(updatedTextExercise.difficulty()).as("text exercise difficulty was correctly updated").isEqualTo(updateDifficulty);
        assertThat(updatedTextExercise.courseId()).as("course was not set for exam exercise").isNull();
        assertThat(updatedTextExercise.exerciseGroupId()).as("exerciseGroup was set for exam exercise").isNotNull();
        assertThat(updatedTextExercise.exerciseGroupId()).as("exerciseGroupId was not updated").isEqualTo(exerciseGroup.getId());
        verify(examLiveEventsService, timeout(2000).times(1)).createAndSendProblemStatementUpdateEvent(any(), any());
        verify(groupNotificationScheduleService, never()).checkAndCreateAppropriateNotificationsWhenUpdatingExercise(any(), any(), any(), any());
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ArgumentsSource(InvalidExamExerciseDatesArgumentProvider.class)
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateTextExerciseForExam_invalidExercise_dates(InvalidExamExerciseDateConfiguration invalidDates) throws Exception {
        ExerciseGroup exerciseGroup = examUtilService.addExerciseGroupWithExamAndCourse(true);
        TextExercise textExercise = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup);
        textExerciseRepository.save(textExercise);

        request.putWithResponseBody("/api/text/text-exercises", de.tum.cit.aet.artemis.text.dto.UpdateTextExerciseDTO.of(invalidDates.applyTo(textExercise)),
                TextExerciseResponseDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateTextExercise_setCourseAndExerciseGroup_conflict() throws Exception {
        ExerciseGroup exerciseGroup = examUtilService.addExerciseGroupWithExamAndCourse(true);
        textExercise.setExerciseGroup(exerciseGroup);

        // With DTO approach, setting an exercise group from a different course results in CONFLICT
        // because the courseId from the DTO doesn't match the stored exercise's courseId
        request.putWithResponseBody("/api/text/text-exercises", de.tum.cit.aet.artemis.text.dto.UpdateTextExerciseDTO.of(textExercise), TextExerciseResponseDTO.class,
                HttpStatus.CONFLICT);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateTextExercise_setNeitherCourseAndExerciseGroup_badRequest() throws Exception {
        textExercise.setCourse(null);

        request.putWithResponseBody("/api/text/text-exercises", de.tum.cit.aet.artemis.text.dto.UpdateTextExerciseDTO.of(textExercise), TextExerciseResponseDTO.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateTextExercise_convertFromCourseToExamExercise_conflict() throws Exception {
        ExerciseGroup exerciseGroup = examUtilService.addExerciseGroupWithExamAndCourse(true);

        textExercise.setExerciseGroup(exerciseGroup);

        // With DTO approach, converting from course to exam exercise through a different course results in CONFLICT
        request.putWithResponseBody("/api/text/text-exercises", de.tum.cit.aet.artemis.text.dto.UpdateTextExerciseDTO.of(textExercise), TextExerciseResponseDTO.class,
                HttpStatus.CONFLICT);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateTextExercise_convertFromExamToCourseExercise_badRequest() throws Exception {
        ExerciseGroup exerciseGroup = examUtilService.addExerciseGroupWithExamAndCourse(true);
        TextExercise textExercise = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup);
        textExerciseRepository.save(textExercise);

        textExercise.setExerciseGroup(null);

        request.putWithResponseBody("/api/text/text-exercises", de.tum.cit.aet.artemis.text.dto.UpdateTextExerciseDTO.of(textExercise), TextExerciseResponseDTO.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importTextExerciseFromCourseToCourse() throws Exception {
        var now = ZonedDateTime.now();
        Course course1 = courseUtilService.addEmptyCourse();
        Course course2 = courseUtilService.addEmptyCourse();
        courseUtilService.enableMessagingForCourse(course2);
        TextExercise textExercise = TextExerciseFactory.generateTextExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), course1);
        textExerciseRepository.save(textExercise);
        textExercise.setCourse(course2);
        textExercise.setChannelName("testchannel" + textExercise.getId());
        textExercise.setCompetencyLinks(Set.of(new CompetencyExerciseLink(competency, textExercise, 1)));
        textExercise.getCompetencyLinks().forEach(link -> link.getCompetency().setCourse(null));

        var newTextExerciseDto = request.postWithResponseBody("/api/text/text-exercises/import?sourceExerciseId=" + textExercise.getId(), ImportTextExerciseDTO.of(textExercise),
                TextExerciseResponseDTO.class, HttpStatus.CREATED);
        TextExercise newTextExercise = textExerciseRepository.findById(newTextExerciseDto.id()).orElseThrow();
        // The import DTO does not carry assessmentType; without setting it explicitly the new exercise would be
        // persisted with assessmentType == null instead of the MANUAL mode the old entity payload preserved.
        assertThat(newTextExercise.getAssessmentType()).as("imported text exercise keeps the MANUAL assessment type").isEqualTo(AssessmentType.MANUAL);
        Channel channel = channelRepository.findChannelByExerciseId(newTextExercise.getId());
        assertThat(channel).isNotNull();
        verify(competencyProgressApi).updateProgressByLearningObjectAsync(eq(newTextExercise));
        assertExerciseExistsInWeaviate(weaviateService, newTextExercise);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importTextExerciseOmittingModeAndScoreKeepsEntityDefaults() throws Exception {
        var now = ZonedDateTime.now();
        Course course1 = courseUtilService.addEmptyCourse();
        Course course2 = courseUtilService.addEmptyCourse();
        courseUtilService.enableMessagingForCourse(course2);
        TextExercise textExercise = TextExerciseFactory.generateTextExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), course1);
        textExerciseRepository.save(textExercise);
        textExercise.setCourse(course2);
        textExercise.setChannelName("testchannel-defaults-" + textExercise.getId());

        // Simulate an older/API client that omits mode and includedInOverallScore in the import payload. The old entity
        // request body kept the entity defaults (INDIVIDUAL, INCLUDED_COMPLETELY) for omitted fields; the DTO mapper must
        // not overwrite them with null (null mode breaks the non-null column, null score fails validateGeneralSettings).
        var src = ImportTextExerciseDTO.of(textExercise);
        var dto = new ImportTextExerciseDTO(src.id(), src.title(), src.channelName(), src.shortName(), src.problemStatement(), src.categories(), src.difficulty(), null,
                src.maxPoints(), src.bonusPoints(), null, src.allowComplaintsForAutomaticAssessments(), src.allowFeedbackRequests(), src.presentationScoreEnabled(),
                src.secondCorrectionEnabled(), src.feedbackSuggestionModule(), src.gradingInstructions(), src.releaseDate(), src.startDate(), src.dueDate(),
                src.assessmentDueDate(), src.exampleSolutionPublicationDate(), src.exampleSolution(), src.courseId(), src.exerciseGroupId(), src.teamAssignmentConfig(),
                src.plagiarismDetectionConfig(), src.gradingCriteria(), src.competencyLinks());

        var newTextExerciseDto = request.postWithResponseBody("/api/text/text-exercises/import?sourceExerciseId=" + textExercise.getId(), dto, TextExerciseResponseDTO.class,
                HttpStatus.CREATED);
        TextExercise newTextExercise = textExerciseRepository.findById(newTextExerciseDto.id()).orElseThrow();
        assertThat(newTextExercise.getMode()).as("omitted mode keeps the INDIVIDUAL entity default instead of null").isEqualTo(ExerciseMode.INDIVIDUAL);
        assertThat(newTextExercise.getIncludedInOverallScore()).as("omitted includedInOverallScore keeps the INCLUDED_COMPLETELY entity default")
                .isEqualTo(IncludedInOverallScore.INCLUDED_COMPLETELY);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importTextExerciseWithExampleSubmissionFromCourseToCourse() throws Exception {
        var now = ZonedDateTime.now();
        Course course1 = courseUtilService.addEmptyCourse();
        TextExercise textExercise = TextExerciseFactory.generateTextExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), course1);
        textExercise = textExerciseRepository.save(textExercise);
        textExercise.setChannelName("testchannel" + textExercise.getId());
        // Create example submission
        var exampleSubmission = participationUtilService.generateExampleSubmission("Lorem Ipsum", textExercise, true);
        exampleSubmission = participationUtilService.addExampleSubmission(exampleSubmission);

        var automaticTextBlock = TextExerciseFactory.generateTextBlock(1, 4, "orem");
        automaticTextBlock.automatic();

        var manualTextBlock = TextExerciseFactory.generateTextBlock(1, 3, "ore");
        manualTextBlock.manual();

        textExerciseUtilService.addAndSaveTextBlocksToTextSubmission(Set.of(manualTextBlock, automaticTextBlock), (TextSubmission) exampleSubmission.getSubmission());

        participationUtilService.addResultToSubmission(exampleSubmission.getSubmission(), AssessmentType.MANUAL, textExercise.getId());
        // TextExerciseResponseDTO does not carry example submissions, so reload the imported exercise (with eager example
        // submissions, results and text blocks) to assert the copied submission content.
        TextExerciseResponseDTO newTextExerciseDto = request.postWithResponseBody("/api/text/text-exercises/import?sourceExerciseId=" + textExercise.getId(),
                ImportTextExerciseDTO.of(textExercise), TextExerciseResponseDTO.class, HttpStatus.CREATED);
        TextExercise newTextExercise = textExerciseRepository.findByIdWithExampleSubmissionsAndResultsAndGradingCriteriaElseThrow(newTextExerciseDto.id());
        assertThat(newTextExercise.getExampleSubmissions()).hasSize(1);
        ExampleSubmission newExampleSubmission = newTextExercise.getExampleSubmissions().iterator().next();
        var textBlocks = ((TextSubmission) newExampleSubmission.getSubmission()).getBlocks();
        assertThat(textBlocks).hasSize(2);

        TextBlock manualTextBlockFromImport = textBlocks.stream().filter(tb -> tb.getText().equals(manualTextBlock.getText())).findFirst().orElseThrow();
        assertThat(manualTextBlockFromImport.getId()).isNotEqualTo(manualTextBlock.getId());
        assertTextBlocksHaveSameContent(manualTextBlock, manualTextBlockFromImport);

        TextBlock automaticTextBlockFromImport = textBlocks.stream().filter(tb -> tb.getText().equals(automaticTextBlock.getText())).findFirst().orElseThrow();
        assertThat(automaticTextBlockFromImport.getId()).isNotEqualTo(automaticTextBlock.getId());
        assertTextBlocksHaveSameContent(automaticTextBlock, automaticTextBlockFromImport);
    }

    private static void assertTextBlocksHaveSameContent(TextBlock manualTextBlock, TextBlock manualTextBlockFromImport) {
        assertThat(manualTextBlockFromImport.getType()).isEqualTo(manualTextBlock.getType());
        assertThat(manualTextBlockFromImport.getStartIndex()).isEqualTo(manualTextBlock.getStartIndex());
        assertThat(manualTextBlockFromImport.getEndIndex()).isEqualTo(manualTextBlock.getEndIndex());
        assertThat(manualTextBlockFromImport.getText()).isEqualTo(manualTextBlock.getText());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importTextExerciseFromCourseToExam() throws Exception {
        var now = ZonedDateTime.now();
        Course course1 = courseUtilService.addEmptyCourse();
        ExerciseGroup exerciseGroup1 = examUtilService.addExerciseGroupWithExamAndCourse(true);
        TextExercise textExercise = TextExerciseFactory.generateTextExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), course1);
        textExerciseRepository.save(textExercise);
        textExercise.setCourse(null);
        textExercise.setDueDate(null);
        textExercise.setAssessmentDueDate(null);
        textExercise.setReleaseDate(null);
        textExercise.setExerciseGroup(exerciseGroup1);

        var newTextExercise = request.postWithResponseBody("/api/text/text-exercises/import?sourceExerciseId=" + textExercise.getId(), ImportTextExerciseDTO.of(textExercise),
                TextExerciseResponseDTO.class, HttpStatus.CREATED);

        // There should not be created a channel for the imported exam exercise
        Channel channel = channelRepository.findChannelByExerciseId(newTextExercise.id());
        assertThat(channel).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "TA")
    void importTextExerciseFromCourseToExam_forbidden() throws Exception {
        var now = ZonedDateTime.now();
        Course course1 = courseUtilService.addEmptyCourse();
        ExerciseGroup exerciseGroup1 = examUtilService.addExerciseGroupWithExamAndCourse(true);
        TextExercise textExercise = TextExerciseFactory.generateTextExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), course1);
        textExerciseRepository.save(textExercise);
        textExercise.setCourse(null);
        textExercise.setExerciseGroup(exerciseGroup1);

        request.postWithResponseBody("/api/text/text-exercises/import?sourceExerciseId=" + textExercise.getId(), ImportTextExerciseDTO.of(textExercise),
                TextExerciseResponseDTO.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importTextExerciseFromExamToCourse() throws Exception {
        ExerciseGroup exerciseGroup1 = examUtilService.addExerciseGroupWithExamAndCourse(true);
        TextExercise textExercise = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup1);
        Course course1 = courseUtilService.addEmptyCourse();
        textExerciseRepository.save(textExercise);
        textExercise.setCourse(course1);
        textExercise.setExerciseGroup(null);
        textExercise.setChannelName("test" + textExercise.getId());
        request.postWithResponseBody("/api/text/text-exercises/import?sourceExerciseId=" + textExercise.getId(), ImportTextExerciseDTO.of(textExercise),
                TextExerciseResponseDTO.class, HttpStatus.CREATED);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "TA")
    void importTextExerciseFromExamToCourse_forbidden() throws Exception {
        ExerciseGroup exerciseGroup1 = examUtilService.addExerciseGroupWithExamAndCourse(true);
        TextExercise textExercise = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup1);
        Course course1 = courseUtilService.addEmptyCourse();
        textExerciseRepository.save(textExercise);
        textExercise.setCourse(course1);
        textExercise.setExerciseGroup(null);

        request.postWithResponseBody("/api/text/text-exercises/import?sourceExerciseId=" + textExercise.getId(), ImportTextExerciseDTO.of(textExercise),
                TextExerciseResponseDTO.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importTextExerciseFromExamToExam() throws Exception {
        ExerciseGroup exerciseGroup1 = examUtilService.addExerciseGroupWithExamAndCourse(true);
        ExerciseGroup exerciseGroup2 = examUtilService.addExerciseGroupWithExamAndCourse(true);
        TextExercise textExercise = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup1);
        textExerciseRepository.save(textExercise);
        textExercise.setExerciseGroup(exerciseGroup2);

        request.postWithResponseBody("/api/text/text-exercises/import?sourceExerciseId=" + textExercise.getId(), ImportTextExerciseDTO.of(textExercise),
                TextExerciseResponseDTO.class, HttpStatus.CREATED);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importTextExerciseFromCourseToCourse_badRequest() throws Exception {
        var now = ZonedDateTime.now();
        Course course1 = courseUtilService.addEmptyCourse();
        TextExercise textExercise = TextExerciseFactory.generateTextExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), course1);
        textExerciseRepository.save(textExercise);
        textExercise.setCourse(null);

        request.postWithResponseBody("/api/text/text-exercises/import?sourceExerciseId=" + textExercise.getId(), ImportTextExerciseDTO.of(textExercise),
                TextExerciseResponseDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importTextExerciseFromCourseToCourse_exampleSolutionPublicationDate() throws Exception {
        var now = ZonedDateTime.now();
        Course course1 = courseUtilService.addEmptyCourse();
        Course course2 = courseUtilService.addEmptyCourse();
        TextExercise textExercise = TextExerciseFactory.generateTextExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), course1);

        textExercise.setExampleSolutionPublicationDate(ZonedDateTime.now());

        textExercise = textExerciseRepository.save(textExercise);
        textExercise.setCourse(course2);
        textExercise.setChannelName("test-" + textExercise.getId());
        TextExerciseResponseDTO newTextExercise = request.postWithResponseBody("/api/text/text-exercises/import?sourceExerciseId=" + textExercise.getId(),
                ImportTextExerciseDTO.of(textExercise), TextExerciseResponseDTO.class, HttpStatus.CREATED);
        assertThat(newTextExercise.exampleSolutionPublicationDate()).as("text example solution publication date was correctly set to null in the response").isNull();

        TextExercise newTextExerciseFromDatabase = textExerciseRepository.findById(newTextExercise.id()).orElseThrow();
        assertThat(newTextExerciseFromDatabase.getExampleSolutionPublicationDate()).as("text example solution publication date was correctly set to null in the database").isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getAllTextExercisesForCourse() throws Exception {
        List<TextExerciseListItemDTO> textExercises = request.getList("/api/text/courses/" + course.getId() + "/text-exercises", HttpStatus.OK, TextExerciseListItemDTO.class);

        assertThat(textExercises).as("text exercises for course were retrieved").hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getAllTextExercisesForCourse_listItemCarriesScoringAndModeFields() throws Exception {
        // Regression guard: the course management text-exercise table renders bonus points, the included-in-score
        // badge, the presentation-score column, and the Teams action (teamMode). The list DTO must keep these scalars;
        // dropping them left the table blank. Use non-default values so they are serialized (not dropped by NON_EMPTY).
        textExercise.setBonusPoints(7.0);
        textExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_AS_BONUS);
        textExercise.setPresentationScoreEnabled(true);
        textExercise.setMode(ExerciseMode.TEAM);
        textExerciseRepository.save(textExercise);

        List<TextExerciseListItemDTO> textExercises = request.getList("/api/text/courses/" + course.getId() + "/text-exercises", HttpStatus.OK, TextExerciseListItemDTO.class);

        assertThat(textExercises).hasSize(1);
        TextExerciseListItemDTO dto = textExercises.getFirst();
        assertThat(dto.maxPoints()).as("maxPoints is exposed").isEqualTo(textExercise.getMaxPoints());
        assertThat(dto.bonusPoints()).as("bonusPoints is exposed for the table indicator").isEqualTo(7.0);
        assertThat(dto.includedInOverallScore()).as("includedInOverallScore is exposed for the table badge").isEqualTo(IncludedInOverallScore.INCLUDED_AS_BONUS);
        assertThat(dto.presentationScoreEnabled()).as("presentationScoreEnabled is exposed for the table column").isTrue();
        assertThat(dto.teamMode()).as("teamMode is exposed so the Teams action renders for team exercises").isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getAllTextExercisesForCourse_isNotAtLeastTeachingAssistantInCourse_forbidden() throws Exception {
        course.setTeachingAssistantGroupName("test");
        courseRepository.save(course);

        request.getList("/api/text/courses/" + course.getId() + "/text-exercises", HttpStatus.FORBIDDEN, TextExerciseListItemDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getTextExercise_notFound() throws Exception {
        TextExercise textExercise = new TextExercise();
        textExercise.setId(114213211L);

        request.get("/api/text/text-exercises/" + textExercise.getId(), HttpStatus.NOT_FOUND, TextExerciseResponseDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getTextExerciseAsTutor() throws Exception {
        Channel channel = new Channel();
        channel.setIsPublic(true);
        channel.setIsAnnouncementChannel(false);
        channel.setIsArchived(false);
        channel.setName("testchannel-" + UUID.randomUUID().toString().substring(0, 8));
        channel.setExercise(textExercise);
        channelRepository.save(channel);
        TextExerciseResponseDTO textExerciseServer = request.get("/api/text/text-exercises/" + textExercise.getId(), HttpStatus.OK, TextExerciseResponseDTO.class);

        assertThat(textExerciseServer).as("text exercise was retrieved").isNotNull();
        // The single GET must carry a nested course projection: the client reads exercise.course to render links and,
        // crucially, the course group names so it can compute access rights (account.service.setAccessRightsForCourse).
        // Dropping it crashed the example-submissions page with "Cannot set properties of undefined (setting 'isAtLeastTutor')".
        assertThat(textExerciseServer.course()).as("nested course is present for a course exercise").isNotNull();
        assertThat(textExerciseServer.course().id()).as("nested course carries its id").isEqualTo(course.getId());
        assertThat(textExerciseServer.course().teachingAssistantGroupName()).as("nested course carries the TA group name used for access rights")
                .isEqualTo(course.getTeachingAssistantGroupName());
        assertThat(textExerciseServer.course().instructorGroupName()).as("nested course carries the instructor group name used for access rights")
                .isEqualTo(course.getInstructorGroupName());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getExamTextExerciseCarriesNestedExamCourse() throws Exception {
        ExerciseGroup exerciseGroup = examUtilService.addExerciseGroupWithExamAndCourse(true);
        TextExercise examTextExercise = textExerciseRepository.save(TextExerciseFactory.generateTextExerciseForExam(exerciseGroup));
        var exam = exerciseGroup.getExam();
        Course examCourse = exam.getCourse();

        TextExerciseResponseDTO textExerciseServer = request.get("/api/text/text-exercises/" + examTextExercise.getId(), HttpStatus.OK, TextExerciseResponseDTO.class);

        assertThat(textExerciseServer).as("exam text exercise was retrieved").isNotNull();
        assertThat(textExerciseServer.exerciseGroup()).as("nested exerciseGroup is exposed for an exam exercise").isNotNull();
        var examRef = textExerciseServer.exerciseGroup().exam();
        assertThat(examRef).as("nested exam reference is exposed").isNotNull();
        // For exam exercises the client resolves the course via exercise.exerciseGroup.exam.course (top-level course is
        // null). It needs the course group names there to compute access rights (account.service.setAccessRightsForCourse);
        // dropping it loses course context and access rights on the exam exercise management screens.
        assertThat(examRef.course()).as("nested exam course is present for an exam exercise").isNotNull();
        assertThat(examRef.course().id()).as("nested exam course carries its id").isEqualTo(examCourse.getId());
        assertThat(examRef.course().teachingAssistantGroupName()).as("nested exam course carries the TA group name used for access rights")
                .isEqualTo(examCourse.getTeachingAssistantGroupName());
        assertThat(examRef.course().instructorGroupName()).as("nested exam course carries the instructor group name used for access rights")
                .isEqualTo(examCourse.getInstructorGroupName());
        // The unchanged Angular views also read exam.title (detail-page exam link), exam.testExam (gates feedback-
        // suggestion options) and exam.numberOfCorrectionRoundsInExam (assessment controls); they must survive the DTO.
        assertThat(examRef.id()).as("nested exam id").isEqualTo(exam.getId());
        assertThat(examRef.title()).as("nested exam title for the detail-page exam link").isEqualTo(exam.getTitle());
        assertThat(examRef.testExam()).as("nested exam test-exam flag gating feedback-suggestion options").isEqualTo(exam.isTestExam());
        assertThat(examRef.numberOfCorrectionRoundsInExam()).as("nested exam correction-round count for the assessment controls")
                .isEqualTo(exam.getNumberOfCorrectionRoundsInExam());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getTextExerciseWithExampleSubmissions() throws Exception {
        var exampleSubmission = participationUtilService.generateExampleSubmission("Lorem Ipsum", textExercise, true);
        exampleSubmission = participationUtilService.addExampleSubmission(exampleSubmission);
        participationUtilService.addResultToSubmission(exampleSubmission.getSubmission(), AssessmentType.MANUAL, textExercise.getId());
        final long exampleSubmissionId = exampleSubmission.getId();

        TextExerciseResponseDTO textExerciseServer = request.get("/api/text/text-exercises/" + textExercise.getId(), HttpStatus.OK, TextExerciseResponseDTO.class);

        assertThat(textExerciseServer).as("text exercise was retrieved").isNotNull();
        assertThat(textExerciseServer.exampleSubmissions()).as("example submissions are present in the single GET").isNotNull();
        assertThat(textExerciseServer.exampleSubmissions()).as("the created example submission is returned").anySatisfy(dto -> assertThat(dto.id()).isEqualTo(exampleSubmissionId));
        assertThat(textExerciseServer.exampleSubmissions()).as("the returned example submission carries its submission").filteredOn(dto -> dto.id().equals(exampleSubmissionId))
                .first().satisfies(dto -> assertThat(dto.submission()).isNotNull());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getExamTextExerciseAsTutor_forbidden() throws Exception {
        ExerciseGroup exerciseGroup = examUtilService.addExerciseGroupWithExamAndCourse(true);
        TextExercise textExercise = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup);
        textExerciseRepository.save(textExercise);

        request.get("/api/text/text-exercises/" + textExercise.getId(), HttpStatus.FORBIDDEN, TextExerciseResponseDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getExamTextExerciseAsInstructor() throws Exception {
        ExerciseGroup exerciseGroup = examUtilService.addExerciseGroupWithExamAndCourse(true);
        TextExercise textExercise = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup);
        textExerciseRepository.save(textExercise);

        TextExerciseResponseDTO textExerciseServer = request.get("/api/text/text-exercises/" + textExercise.getId(), HttpStatus.OK, TextExerciseResponseDTO.class);
        assertThat(textExerciseServer).as("text exercise was retrieved").isNotNull();
        assertThat(textExerciseServer.id()).as("Text exercise with the right id was retrieved").isEqualTo(textExercise.getId());
        // Exam exercises resolve their course client-side via the exercise group, mirroring the original entity where
        // exercise.course was only populated for course exercises; the nested course projection stays null here.
        assertThat(textExerciseServer.course()).as("nested course is not set for an exam exercise").isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getTextExercise_isNotAtleastTeachingAssistantInCourse_forbidden() throws Exception {
        course.setTeachingAssistantGroupName("test");
        courseRepository.save(course);
        request.get("/api/text/text-exercises/" + textExercise.getId(), HttpStatus.FORBIDDEN, TextExerciseResponseDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetTextExercise_setGradingInstructionFeedbackUsed() throws Exception {
        Channel channel = new Channel();
        channel.setName("testchannel-" + UUID.randomUUID().toString().substring(0, 8));
        channel.setIsPublic(true);
        channel.setIsAnnouncementChannel(false);
        channel.setIsArchived(false);
        channel.setExercise(textExercise);
        channelRepository.save(channel);
        Set<GradingCriterion> gradingCriteria = exerciseUtilService.addGradingInstructionsToExercise(textExercise);
        gradingCriterionRepository.saveAll(gradingCriteria);
        Feedback feedback = new Feedback();
        feedback.setGradingInstruction(GradingCriterionUtil.findAnyInstructionWhere(gradingCriteria, instruction -> true).orElseThrow());
        feedbackRepository.save(feedback);

        TextExerciseResponseDTO receivedTextExercise = request.get("/api/text/text-exercises/" + textExercise.getId(), HttpStatus.OK, TextExerciseResponseDTO.class);

        assertThat(receivedTextExercise.gradingInstructionFeedbackUsed()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructorother1", roles = "INSTRUCTOR")
    void testInstructorGetsOnlyResultsFromOwningCourses() throws Exception {
        final var search = pageableSearchUtilService.configureSearch("");
        final var result = request.getSearchResult("/api/text/text-exercises", HttpStatus.OK, TextExerciseListItemDTO.class, pageableSearchUtilService.searchMapping(search));
        assertThat(result.getResultsOnPage()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testInstructorGetResultsFromOwningCoursesNotEmpty() throws Exception {
        String courseBaseTitle1 = "testInstructorGetResultsFromOwningCoursesNotEmpty 1";
        String courseBaseTitle2 = "testInstructorGetResultsFromOwningCoursesNotEmpty 2";

        textExerciseUtilService.addCourseWithOneReleasedTextExercise(courseBaseTitle1);
        textExerciseUtilService.addCourseWithOneReleasedTextExercise(courseBaseTitle2 + "Bachelor");
        textExerciseUtilService.addCourseWithOneReleasedTextExercise(courseBaseTitle2 + "Master");

        final var searchText = pageableSearchUtilService.configureSearch(courseBaseTitle1);
        final var resultText = request.getSearchResult("/api/text/text-exercises", HttpStatus.OK, TextExerciseListItemDTO.class,
                pageableSearchUtilService.searchMapping(searchText));
        assertThat(resultText.getResultsOnPage()).hasSize(1);

        final var searchEssay = pageableSearchUtilService.configureSearch(courseBaseTitle2);
        final var resultEssay = request.getSearchResult("/api/text/text-exercises", HttpStatus.OK, TextExerciseListItemDTO.class,
                pageableSearchUtilService.searchMapping(searchEssay));
        assertThat(resultEssay.getResultsOnPage()).hasSize(2);

        final var searchNon = pageableSearchUtilService.configureSearch("No course has this name");
        final var resultNon = request.getSearchResult("/api/text/text-exercises", HttpStatus.OK, TextExerciseListItemDTO.class, pageableSearchUtilService.searchMapping(searchNon));
        assertThat(resultNon.getResultsOnPage()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void searchExamTextExerciseCarriesNestedExerciseGroupForImportMarker() throws Exception {
        ExerciseGroup exerciseGroup = examUtilService.addExerciseGroupWithExamAndCourse(true);
        TextExercise examTextExercise = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup);
        examTextExercise.setTitle("searchExamMarker-" + UUID.randomUUID().toString().substring(0, 8));
        examTextExercise = textExerciseRepository.save(examTextExercise);

        final var search = pageableSearchUtilService.configureSearch(examTextExercise.getTitle());
        final var result = request.getSearchResult("/api/text/text-exercises", HttpStatus.OK, TextExerciseListItemDTO.class, pageableSearchUtilService.searchMapping(search));

        final long exerciseId = examTextExercise.getId();
        TextExerciseListItemDTO found = result.getResultsOnPage().stream().filter(e -> e.id().equals(exerciseId)).findFirst().orElseThrow();
        // The cross-course import search table shows the exam-question marker via @if (exercise.exerciseGroup); the list
        // DTO must carry a nested exerciseGroup for exam exercises or that marker disappears.
        assertThat(found.exerciseGroup()).as("exam text exercise carries a nested exerciseGroup so the import search marks it as an exam question").isNotNull();
        assertThat(found.examId()).as("flat examId is still present alongside the nested group").isEqualTo(exerciseGroup.getExam().getId());
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

    private void testCourseAndExamFilters(String courseTitle) throws Exception {
        textExerciseUtilService.addCourseWithOneReleasedTextExercise(courseTitle);
        examUtilService.addCourseExamExerciseGroupWithOneTextExercise(courseTitle + "-Morpork");
        // The search endpoint now returns TextExerciseListItemDTO. We cannot reuse
        // ExerciseIntegrationTestService.testCourseAndExamFilters because it deserializes into the polymorphic Exercise
        // entity and navigates exerciseGroup.getExam() (NPE on the DTO). Replicate the same coverage against the DTO.
        testCourseAndExamFiltersForTextDto("/api/text/text-exercises", courseTitle);
    }

    private void testCourseAndExamFiltersForTextDto(String apiPath, String searchTerm) throws Exception {
        var search = pageableSearchUtilService.configureSearch(searchTerm);

        // no filter explicitly set -> should default to all filters active and show both exercises
        final var resultWithoutFiltersSet = request.getSearchResult(apiPath, HttpStatus.OK, TextExerciseListItemDTO.class, pageableSearchUtilService.searchMapping(search));
        assertThat(resultWithoutFiltersSet.getResultsOnPage()).hasSize(2);

        // both filter explicitly set -> should show both exercises
        final var courseAndExamFilterParams = pageableSearchUtilService.searchMapping(search);
        courseAndExamFilterParams.add("isCourseFilter", "true");
        courseAndExamFilterParams.add("isExamFilter", "true");
        final var resultWithCourseAndExamFiltersActive = request.getSearchResult(apiPath, HttpStatus.OK, TextExerciseListItemDTO.class, courseAndExamFilterParams);
        assertThat(resultWithCourseAndExamFiltersActive.getResultsOnPage()).hasSize(2);

        // both filter explicitly deactivated -> should show no exercises
        final var allFiltersInactiveParams = pageableSearchUtilService.searchMapping(search);
        allFiltersInactiveParams.add("isCourseFilter", "false");
        allFiltersInactiveParams.add("isExamFilter", "false");
        final var resultWithNoFiltersActive = request.getSearchResult(apiPath, HttpStatus.OK, TextExerciseListItemDTO.class, allFiltersInactiveParams);
        assertThat(resultWithNoFiltersActive.getResultsOnPage()).isEmpty();

        // only course filter set -> should show only the course exercise
        final var courseFilterParams = pageableSearchUtilService.searchMapping(search);
        courseFilterParams.add("isCourseFilter", "true");
        courseFilterParams.add("isExamFilter", "false");
        final var resultWithOnlyCoursesFilterActive = request.getSearchResult(apiPath, HttpStatus.OK, TextExerciseListItemDTO.class, courseFilterParams);
        assertThat(resultWithOnlyCoursesFilterActive.getResultsOnPage()).hasSize(1);
        assertThat(resultWithOnlyCoursesFilterActive.getResultsOnPage().getFirst().title()).isEqualTo(searchTerm);

        // only exam filter set -> should show only the exam exercise
        final var examFilterParams = pageableSearchUtilService.searchMapping(search);
        examFilterParams.add("isCourseFilter", "false");
        examFilterParams.add("isExamFilter", "true");
        final var resultWithOnlyExamFilterActive = request.getSearchResult(apiPath, HttpStatus.OK, TextExerciseListItemDTO.class, examFilterParams);
        assertThat(resultWithOnlyExamFilterActive.getResultsOnPage()).hasSize(1);
        assertThat(resultWithOnlyExamFilterActive.getResultsOnPage().getFirst().title()).isEqualTo(searchTerm + "-Morpork");

        var columnNameMap = PageUtil.ColumnMapping.EXERCISE.getColumnNameMap();
        for (var sort : columnNameMap.keySet()) {
            if (sort.equals("PROGRAMMING_LANGUAGE")) {
                continue; // not applicable to text exercises
            }
            for (var order : List.of(SortingOrder.ASCENDING, SortingOrder.DESCENDING)) {
                search = pageableSearchUtilService.configureSearch(searchTerm);
                search.setSortedColumn(sort);
                search.setSortingOrder(order);
                var params = pageableSearchUtilService.searchMapping(search);

                // COURSE_TITLE / EXAM_TITLE navigations only make sense for one exercise category, mirroring the shared
                // helper. With the filter applied each yields a single result, so sorting holds trivially; we still assert
                // the endpoint returns the sorted page and (for EXAM_TITLE) compare via the DTO's examTitle().
                if (sort.equals("EXAM_TITLE")) {
                    params.add("isCourseFilter", "false");
                }
                else if (sort.equals("COURSE_TITLE")) {
                    params.add("isExamFilter", "false");
                }

                var result = request.getSearchResult(apiPath, HttpStatus.OK, TextExerciseListItemDTO.class, params);

                Comparator<TextExerciseListItemDTO> comparator = getExpectedTextDtoComparator(sort);
                if (order == SortingOrder.DESCENDING) {
                    comparator = comparator.reversed();
                }
                assertThat(result.getResultsOnPage()).as("Sorting by " + sort + " " + order).isSortedAccordingTo(comparator);
            }
        }
    }

    private Comparator<TextExerciseListItemDTO> getExpectedTextDtoComparator(String sort) {
        return switch (sort) {
            case "ID" -> Comparator.comparing(TextExerciseListItemDTO::id);
            case "TITLE" -> Comparator.comparing(TextExerciseListItemDTO::title);
            // FIXME-DTO: TextExerciseListItemDTO carries courseId() but not the course title, so COURSE_TITLE sorting
            // cannot be verified by value through this boundary. The course-filtered page contains a single exercise,
            // so any comparator is trivially satisfied; fall back to id ordering to keep the assertion meaningful.
            case "COURSE_TITLE" -> Comparator.comparing(TextExerciseListItemDTO::id);
            case "EXAM_TITLE" -> Comparator.comparing(TextExerciseListItemDTO::examTitle);
            default -> throw new IllegalStateException("Unexpected value: " + sort);
        };
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testInstructorSearchTermMatchesId() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        testSearchTermMatchesId();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testAdminSearchTermMatchesId() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        testSearchTermMatchesId();
    }

    private void testSearchTermMatchesId() throws Exception {
        final Course course = courseUtilService.addEmptyCourse();
        final var now = ZonedDateTime.now();
        TextExercise exercise = TextExerciseFactory.generateTextExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), course);
        exercise.setTitle("LoremIpsum");
        exercise = textExerciseRepository.save(exercise);
        var exerciseId = exercise.getId();

        final var searchTerm = pageableSearchUtilService.configureSearch(exerciseId.toString());
        final var searchResult = request.getSearchResult("/api/text/text-exercises", HttpStatus.OK, TextExerciseListItemDTO.class,
                pageableSearchUtilService.searchMapping(searchTerm));
        assertThat(searchResult.getResultsOnPage().stream().filter(result -> result.id() == exerciseId.longValue())).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructorother1", roles = "INSTRUCTOR")
    void testInstructorGetsOnlyResultsFromOwningExams() throws Exception {
        examUtilService.addCourseExamExerciseGroupWithOneTextExercise();
        final var search = pageableSearchUtilService.configureSearch("");
        final var result = request.getSearchResult("/api/text/text-exercises", HttpStatus.OK, TextExerciseListItemDTO.class, pageableSearchUtilService.searchMapping(search));
        assertThat(result.getResultsOnPage()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testInstructorGetResultsFromOwningExamsNotEmpty() throws Exception {
        String exerciseBaseTitle1 = "testInstructorGetResultsFromOwningExamsNotEmpty 1";
        String exerciseBaseTitle2 = "testInstructorGetResultsFromOwningExamsNotEmpty 2";

        examUtilService.addCourseExamExerciseGroupWithOneTextExercise(exerciseBaseTitle1);
        examUtilService.addCourseExamExerciseGroupWithOneTextExercise(exerciseBaseTitle2 + "Bachelor");
        examUtilService.addCourseExamExerciseGroupWithOneTextExercise(exerciseBaseTitle2 + "Master");

        final var searchText = pageableSearchUtilService.configureSearch(exerciseBaseTitle1);
        final var resultText = request.getSearchResult("/api/text/text-exercises", HttpStatus.OK, TextExerciseListItemDTO.class,
                pageableSearchUtilService.searchMapping(searchText));
        assertThat(resultText.getResultsOnPage()).hasSize(1);

        final var searchEssay = pageableSearchUtilService.configureSearch(exerciseBaseTitle2);
        final var resultEssay = request.getSearchResult("/api/text/text-exercises", HttpStatus.OK, TextExerciseListItemDTO.class,
                pageableSearchUtilService.searchMapping(searchEssay));
        assertThat(resultEssay.getResultsOnPage()).hasSize(2);

        final var searchNon = pageableSearchUtilService.configureSearch("No exam has this name");
        final var resultNon = request.getSearchResult("/api/text/text-exercises", HttpStatus.OK, TextExerciseListItemDTO.class, pageableSearchUtilService.searchMapping(searchNon));
        assertThat(resultNon.getResultsOnPage()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testAdminGetsResultsFromAllCourses() throws Exception {
        String courseTitle = "testAdminGetsResultsFromAllCourses";

        textExerciseUtilService.addCourseWithOneReleasedTextExercise(courseTitle);
        Course otherInstructorsCourse = textExerciseUtilService.addCourseWithOneReleasedTextExercise(courseTitle);
        otherInstructorsCourse.setInstructorGroupName("other-instructors");
        courseRepository.save(otherInstructorsCourse);

        final var search = pageableSearchUtilService.configureSearch(courseTitle);
        final var result = request.getSearchResult("/api/text/text-exercises", HttpStatus.OK, TextExerciseListItemDTO.class, pageableSearchUtilService.searchMapping(search));
        assertThat(result.getResultsOnPage()).hasSize(2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportTextExercise_team_modeChange() throws Exception {
        var now = ZonedDateTime.now();
        Course course1 = courseUtilService.addEmptyCourse();
        Course course2 = courseUtilService.addEmptyCourse();
        TextExercise sourceExercise = TextExerciseFactory.generateTextExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), course1);
        sourceExercise = textExerciseRepository.save(sourceExercise);

        var exerciseToBeImported = new TextExercise();
        exerciseToBeImported.setMode(ExerciseMode.TEAM);

        var teamAssignmentConfig = new TeamAssignmentConfig();
        teamAssignmentConfig.setExercise(exerciseToBeImported);
        teamAssignmentConfig.setMinTeamSize(1);
        teamAssignmentConfig.setMaxTeamSize(10);
        exerciseToBeImported.setTeamAssignmentConfig(teamAssignmentConfig);
        exerciseToBeImported.setCourse(course2);
        exerciseToBeImported.setMaxPoints(1.0);
        exerciseToBeImported.setChannelName("test-" + UUID.randomUUID().toString().substring(0, 3));

        TextExerciseResponseDTO importedDto = request.postWithResponseBody("/api/text/text-exercises/import?sourceExerciseId=" + sourceExercise.getId(),
                ImportTextExerciseDTO.of(exerciseToBeImported), TextExerciseResponseDTO.class, HttpStatus.CREATED);
        // Reload the imported exercise (with eager team assignment config) to assert team-mode wiring not on the response DTO.
        TextExercise importedExercise = textExerciseRepository.findForVersioningById(importedDto.id()).orElseThrow();

        assertThat(importedExercise.getCourseViaExerciseGroupOrCourseMember().getId()).isEqualTo(course2.getId());
        assertThat(importedExercise.getMode()).isEqualTo(ExerciseMode.TEAM);
        assertThat(importedExercise.getTeamAssignmentConfig().getMinTeamSize()).isEqualTo(teamAssignmentConfig.getMinTeamSize());
        assertThat(importedExercise.getTeamAssignmentConfig().getMaxTeamSize()).isEqualTo(teamAssignmentConfig.getMaxTeamSize());
        assertThat(teamRepository.findAllByExerciseIdWithEagerStudents(importedExercise, null)).isEmpty();

        sourceExercise = textExerciseRepository.findById(sourceExercise.getId()).orElseThrow();
        assertThat(sourceExercise.getCourseViaExerciseGroupOrCourseMember().getId()).isEqualTo(course1.getId());
        assertThat(sourceExercise.getMode()).isEqualTo(ExerciseMode.INDIVIDUAL);
        assertThat(sourceExercise.getTeamAssignmentConfig()).isNull();
        assertThat(teamRepository.findAllByExerciseIdWithEagerStudents(sourceExercise, null)).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportTextExercise_individual_modeChange() throws Exception {
        var now = ZonedDateTime.now();
        Course course1 = courseUtilService.addEmptyCourse();
        Course course2 = courseUtilService.addEmptyCourse();
        TextExercise sourceExercise = TextExerciseFactory.generateTextExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), course1);
        sourceExercise.setMode(ExerciseMode.TEAM);
        var teamAssignmentConfig = new TeamAssignmentConfig();
        teamAssignmentConfig.setExercise(sourceExercise);
        teamAssignmentConfig.setMinTeamSize(1);
        teamAssignmentConfig.setMaxTeamSize(10);
        sourceExercise.setTeamAssignmentConfig(teamAssignmentConfig);
        sourceExercise.setCourse(course1);

        sourceExercise = textExerciseRepository.save(sourceExercise);
        var team = new Team();
        team.setShortName("testImportTextExercise_individual_modeChange");
        teamRepository.save(sourceExercise, team);

        var exerciseToBeImported = new TextExercise();
        exerciseToBeImported.setMode(ExerciseMode.INDIVIDUAL);
        exerciseToBeImported.setCourse(course2);
        exerciseToBeImported.setMaxPoints(1.0);
        exerciseToBeImported.setChannelName("test-" + UUID.randomUUID().toString().substring(0, 3));

        TextExerciseResponseDTO importedDto = request.postWithResponseBody("/api/text/text-exercises/import?sourceExerciseId=" + sourceExercise.getId(),
                ImportTextExerciseDTO.of(exerciseToBeImported), TextExerciseResponseDTO.class, HttpStatus.CREATED);
        // Reload the imported exercise (with eager team assignment config) to assert individual-mode wiring not on the response DTO.
        TextExercise importedExercise = textExerciseRepository.findForVersioningById(importedDto.id()).orElseThrow();

        assertThat(importedExercise.getCourseViaExerciseGroupOrCourseMember().getId()).isEqualTo(course2.getId());
        assertThat(importedExercise.getMode()).isEqualTo(ExerciseMode.INDIVIDUAL);
        assertThat(importedExercise.getTeamAssignmentConfig()).isNull();
        assertThat(teamRepository.findAllByExerciseIdWithEagerStudents(importedExercise, null)).isEmpty();

        sourceExercise = textExerciseRepository.findById(sourceExercise.getId()).orElseThrow();
        assertThat(sourceExercise.getCourseViaExerciseGroupOrCourseMember().getId()).isEqualTo(course1.getId());
        assertThat(sourceExercise.getMode()).isEqualTo(ExerciseMode.TEAM);
        assertThat(teamRepository.findAllByExerciseIdWithEagerStudents(sourceExercise, null)).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCheckPlagiarismIdenticalLongTexts() throws Exception {
        var longText = """
                Lorem ipsum dolor sit amet, consectetur adipiscing elit.
                Aenean vitae vestibulum metus.
                Cras id fringilla tellus, sed maximus mi.
                Class aptent taciti sociosqu ad litora torquent per conubia nostra, per inceptos himenaeos.
                Aenean non nulla non ipsum posuere lacinia vel id magna.
                Vestibulum ante ipsum primis in faucibus orci luctus et ultrices posuere cubilia curae; Nulla facilisi.
                Sed in urna vitae est tempus pulvinar.
                Nulla vel lacinia purus, sollicitudin congue libero.
                Nulla maximus finibus sapien vel venenatis.
                Proin a lacus massa. Vivamus nulla libero, commodo nec nibh consectetur, aliquam gravida mauris.
                Etiam condimentum sem id purus feugiat molestie.
                Donec malesuada eu diam sed viverra.
                Morbi interdum massa non purus consequat, quis aliquam quam lacinia.
                Suspendisse sem risus, varius et fermentum sed, cursus in nunc.
                Ut malesuada nulla quam, sed condimentum tellus laoreet vel.
                Ut id leo lobortis velit sollicitudin laoreet.
                Duis quis orci ac est placerat lacinia sit amet ut ipsum.
                Quisque a sapien mollis, tempor est sit amet, volutpat est.
                Cras molestie maximus nisi a porta. Nullam efficitur id odio at posuere.
                Duis id feugiat massa. Duis vitae ultrices velit.
                Aenean congue vestibulum ligula, nec eleifend nulla vestibulum nec.
                Praesent eu convallis neque. Nulla facilisi. Suspendisse mattis nisl ac.
                """;

        textExerciseUtilService.createSubmissionForTextExercise(textExercise, userUtilService.getUserByLogin(TEST_PREFIX + "student1"), longText);
        textExerciseUtilService.createSubmissionForTextExercise(textExercise, userUtilService.getUserByLogin(TEST_PREFIX + "student2"), longText);

        var path = "/api/text/text-exercises/" + textExercise.getId() + "/check-plagiarism";
        var result = request.get(path, HttpStatus.OK, PlagiarismResultDTO.class, plagiarismUtilService.getDefaultPlagiarismOptions());
        assertThat(result.plagiarismResult().getComparisons()).hasSize(1);
        assertThat(result.plagiarismResult().getExercise().getId()).isEqualTo(textExercise.getId());
        var plagiarismResult = result.plagiarismResult();

        PlagiarismComparison comparison = plagiarismResult.getComparisons().iterator().next();
        // Both submissions compared consist of 4 words (= 4 tokens). JPlag seems to be off by 1
        // when counting the length of a match. This is why it calculates a similarity of 3/4 = 75%
        // instead of 4/4 = 100% (5 words ==> 80%, 100 words ==> 99%, etc.). Therefore, we use a rather
        // high offset here to compensate this issue.
        // TODO: Reduce the offset once this issue is fixed in JPlag
        assertThat(comparison.getSimilarity()).isEqualTo(100.0, Offset.offset(1.0));
        assertThat(comparison.getStatus()).isEqualTo(PlagiarismStatus.NONE);
        assertThat(comparison.getMatches()).hasSize(1);

        // verify plagiarism result stats
        var stats = result.plagiarismResultStats();
        assertThat(stats.numberOfDetectedSubmissions()).isEqualTo(2);
        assertThat(stats.averageSimilarity()).isEqualTo(100.0, Offset.offset(1.0));
        assertThat(stats.maximalSimilarity()).isEqualTo(100.0, Offset.offset(1.0));

        var plagiarismStatusDto = new PlagiarismComparisonStatusDTO(CONFIRMED);
        request.put("/api/plagiarism/courses/" + course.getId() + "/plagiarism-comparisons/" + comparison.getId() + "/status", plagiarismStatusDto, HttpStatus.OK);
        assertThat(plagiarismComparisonRepository.findByIdWithSubmissionsStudentsElseThrow(comparison.getId()).getStatus()).isEqualTo(PlagiarismStatus.CONFIRMED);

        plagiarismStatusDto = new PlagiarismComparisonStatusDTO(DENIED);
        request.put("/api/plagiarism/courses/" + course.getId() + "/plagiarism-comparisons/" + comparison.getId() + "/status", plagiarismStatusDto, HttpStatus.OK);
        assertThat(plagiarismComparisonRepository.findByIdWithSubmissionsStudentsElseThrow(comparison.getId()).getStatus()).isEqualTo(DENIED);

        plagiarismStatusDto = new PlagiarismComparisonStatusDTO(NONE);
        request.put("/api/plagiarism/courses/" + course.getId() + "/plagiarism-comparisons/" + comparison.getId() + "/status", plagiarismStatusDto, HttpStatus.OK);
        assertThat(plagiarismComparisonRepository.findByIdWithSubmissionsStudentsElseThrow(comparison.getId()).getStatus()).isEqualTo(PlagiarismStatus.NONE);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCheckPlagiarismIdenticalShortTexts() throws Exception {
        var shortText = "Lorem Ipsum Foo Bar";
        textExerciseUtilService.createSubmissionForTextExercise(textExercise, userUtilService.getUserByLogin(TEST_PREFIX + "student1"), shortText);
        textExerciseUtilService.createSubmissionForTextExercise(textExercise, userUtilService.getUserByLogin(TEST_PREFIX + "student2"), shortText);

        var path = "/api/text/text-exercises/" + textExercise.getId() + "/check-plagiarism";
        request.get(path, HttpStatus.BAD_REQUEST, PlagiarismResult.class, plagiarismUtilService.getPlagiarismOptions(50, 0, 5));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCheckPlagiarismNoSubmissions() throws Exception {
        var path = "/api/text/text-exercises/" + textExercise.getId() + "/check-plagiarism";
        request.get(path, HttpStatus.BAD_REQUEST, PlagiarismResult.class, plagiarismUtilService.getDefaultPlagiarismOptions());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCheckPlagiarism_isNotAtLeastInstructorInCourse_forbidden() throws Exception {
        course.setInstructorGroupName("test");
        courseRepository.save(course);
        request.get("/api/text/text-exercises/" + textExercise.getId() + "/check-plagiarism", HttpStatus.FORBIDDEN, PlagiarismResult.class,
                plagiarismUtilService.getDefaultPlagiarismOptions());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetPlagiarismResult() throws Exception {
        PlagiarismResult expectedResult = textExerciseUtilService.createPlagiarismResultForExercise(textExercise);

        var result = request.get("/api/text/text-exercises/" + textExercise.getId() + "/plagiarism-result", HttpStatus.OK, PlagiarismResultDTO.class);
        assertThat(result.plagiarismResult().getId()).isEqualTo(expectedResult.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetPlagiarismResultWithoutResult() throws Exception {
        var result = request.get("/api/text/text-exercises/" + textExercise.getId() + "/plagiarism-result", HttpStatus.OK, String.class);
        assertThat(result).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetPlagiarismResultWithoutExercise() throws Exception {
        PlagiarismResult result = request.get("/api/text/text-exercises/" + 10000000 + "/plagiarism-result", HttpStatus.NOT_FOUND, PlagiarismResult.class);
        assertThat(result).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetPlagiarismResult_isNotAtLeastInstructorInCourse_forbidden() throws Exception {
        course.setInstructorGroupName("test");
        courseRepository.save(course);
        request.get("/api/text/text-exercises/" + textExercise.getId() + "/plagiarism-result", HttpStatus.FORBIDDEN, String.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testReEvaluateAndUpdateTextExercise() throws Exception {
        Set<GradingCriterion> gradingCriteria = exerciseUtilService.addGradingInstructionsToExercise(textExercise);
        gradingCriterionRepository.saveAll(gradingCriteria);

        participationUtilService.addAssessmentWithFeedbackWithGradingInstructionsForExercise(textExercise, TEST_PREFIX + "instructor1");

        // change grading instruction score
        GradingCriterion toUpdate = GradingCriterionUtil.findAnyWhere(gradingCriteria, criterion -> !criterion.getStructuredGradingInstructions().isEmpty()).orElseThrow();
        toUpdate.getStructuredGradingInstructions().stream().findFirst().orElseThrow().setCredits(3);
        gradingCriteria.removeIf(criterion -> criterion != toUpdate);
        textExercise.setGradingCriteria(gradingCriteria);

        TextExerciseResponseDTO updatedTextExerciseDto = request.putWithResponseBody("/api/text/text-exercises/" + textExercise.getId() + "/re-evaluate" + "?deleteFeedback=false",
                de.tum.cit.aet.artemis.text.dto.UpdateTextExerciseDTO.of(textExercise), TextExerciseResponseDTO.class, HttpStatus.OK);
        TextExercise updatedTextExercise = textExerciseRepository.findById(updatedTextExerciseDto.id()).orElseThrow();
        Set<GradingCriterion> updatedCriteria = gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(updatedTextExercise.getId());
        List<Result> updatedResults = participationUtilService.getResultsForExercise(updatedTextExercise);
        assertThat(GradingCriterionUtil.findAnyInstructionWhere(updatedCriteria, instruction -> instruction.getCredits() == 3)).isPresent();
        assertThat(updatedResults.getFirst().getScore()).isEqualTo(60);
        assertThat(updatedResults.getFirst().getFeedbacks()).extracting(Feedback::getCredits).containsExactly(3.0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testReEvaluateAndUpdateTextExerciseWithExampleSubmission() throws Exception {
        Set<GradingCriterion> gradingCriteria = exerciseUtilService.addGradingInstructionsToExercise(textExercise);
        gradingCriterionRepository.saveAll(gradingCriteria);
        textExercise.setGradingCriteria(gradingCriteria);

        // Create example submission
        Set<ExampleSubmission> exampleSubmissionSet = new HashSet<>();
        var exampleSubmission = participationUtilService.generateExampleSubmission("text", textExercise, true);
        exampleSubmission = participationUtilService.addExampleSubmission(exampleSubmission);
        TextSubmission textSubmission = (TextSubmission) participationUtilService.addResultToSubmission(exampleSubmission.getSubmission(), AssessmentType.MANUAL,
                textExercise.getId());
        textSubmission.setExampleSubmission(true);
        Result result = textSubmission.getLatestResult();
        result.setExampleResult(true);
        textSubmission.addResult(result);
        textSubmissionRepository.save(textSubmission);
        exampleSubmissionSet.add(exampleSubmission);
        textExercise.setExampleSubmissions(exampleSubmissionSet);

        request.putWithResponseBody("/api/text/text-exercises/" + textExercise.getId() + "/re-evaluate" + "?deleteFeedback=false",
                de.tum.cit.aet.artemis.text.dto.UpdateTextExerciseDTO.of(textExercise), TextExerciseResponseDTO.class, HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testReEvaluateAndUpdateTextExercise_shouldDeleteFeedbacks() throws Exception {
        Set<GradingCriterion> gradingCriteria = exerciseUtilService.addGradingInstructionsToExercise(textExercise);
        gradingCriterionRepository.saveAll(gradingCriteria);

        participationUtilService.addAssessmentWithFeedbackWithGradingInstructionsForExercise(textExercise, TEST_PREFIX + "instructor1");

        // remove instruction which is associated with feedbacks
        gradingCriteria.removeIf(criterion -> criterion.getTitle() == null);
        textExercise.setGradingCriteria(gradingCriteria);

        TextExerciseResponseDTO updatedTextExerciseDto = request.putWithResponseBody("/api/text/text-exercises/" + textExercise.getId() + "/re-evaluate" + "?deleteFeedback=true",
                de.tum.cit.aet.artemis.text.dto.UpdateTextExerciseDTO.of(textExercise), TextExerciseResponseDTO.class, HttpStatus.OK);
        TextExercise updatedTextExercise = textExerciseRepository.findById(updatedTextExerciseDto.id()).orElseThrow();
        Set<GradingCriterion> updatedCriteria = gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(updatedTextExercise.getId());
        List<Result> updatedResults = participationUtilService.getResultsForExercise(updatedTextExercise);
        assertThat(updatedCriteria).hasSize(2);
        assertThat(updatedResults.getFirst().getScore()).isZero();
        assertThat(updatedResults.getFirst().getFeedbacks()).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testReEvaluateAndUpdateTextExercise_isNotAtLeastInstructorInCourse_forbidden() throws Exception {
        course.setInstructorGroupName("test");
        courseRepository.save(course);

        request.putWithResponseBody("/api/text/text-exercises/" + textExercise.getId() + "/re-evaluate", de.tum.cit.aet.artemis.text.dto.UpdateTextExerciseDTO.of(textExercise),
                TextExerciseResponseDTO.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testReEvaluateAndUpdateTextExercise_isNotSameGivenExerciseIdInRequestBody_conflict() throws Exception {
        TextExercise textExerciseToBeConflicted = textExerciseRepository.findByCourseIdWithCategories(course.getId()).getFirst();
        textExerciseToBeConflicted.setId(123456789L);

        request.putWithResponseBody("/api/text/text-exercises/" + textExercise.getId() + "/re-evaluate",
                de.tum.cit.aet.artemis.text.dto.UpdateTextExerciseDTO.of(textExerciseToBeConflicted), TextExerciseResponseDTO.class, HttpStatus.CONFLICT);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testReEvaluateAndUpdateTextExercise_notFound() throws Exception {
        request.putWithResponseBody("/api/text/text-exercises/" + 123456789 + "/re-evaluate", de.tum.cit.aet.artemis.text.dto.UpdateTextExerciseDTO.of(textExercise),
                TextExerciseResponseDTO.class, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createTextExercise_setInvalidExampleSolutionPublicationDate_badRequest() throws Exception {
        final var baseTime = ZonedDateTime.now();
        textExercise.setId(null);
        textExercise.setAssessmentDueDate(null);
        textExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);

        textExercise.setReleaseDate(baseTime.plusHours(1));
        textExercise.setDueDate(baseTime.plusHours(3));
        textExercise.setExampleSolutionPublicationDate(baseTime.plusHours(2));

        request.postWithResponseBody("/api/text/text-exercises", UpdateTextExerciseDTO.of(textExercise), TextExerciseResponseDTO.class, HttpStatus.BAD_REQUEST);

        textExercise.setReleaseDate(baseTime.plusHours(3));
        textExercise.setDueDate(null);
        textExercise.setExampleSolutionPublicationDate(baseTime.plusHours(2));

        request.postWithResponseBody("/api/text/text-exercises", UpdateTextExerciseDTO.of(textExercise), TextExerciseResponseDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createTextExercise_setValidExampleSolutionPublicationDate() throws Exception {
        final var baseTime = ZonedDateTime.now();
        textExercise.setId(null);
        textExercise.setAssessmentDueDate(null);
        textExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);

        textExercise.setReleaseDate(baseTime.plusHours(1));
        textExercise.setDueDate(baseTime.plusHours(2));
        var exampleSolutionPublicationDate = baseTime.plusHours(3);
        textExercise.setExampleSolutionPublicationDate(exampleSolutionPublicationDate);
        textExercise.setChannelName("test");

        var result = request.postWithResponseBody("/api/text/text-exercises", UpdateTextExerciseDTO.of(textExercise), TextExerciseResponseDTO.class, HttpStatus.CREATED);
        assertThat(result.exampleSolutionPublicationDate()).isEqualTo(exampleSolutionPublicationDate);

        textExercise.setIncludedInOverallScore(IncludedInOverallScore.NOT_INCLUDED);
        textExercise.setReleaseDate(baseTime.plusHours(1));
        textExercise.setDueDate(baseTime.plusHours(3));
        exampleSolutionPublicationDate = baseTime.plusHours(2);
        textExercise.setExampleSolutionPublicationDate(exampleSolutionPublicationDate);
        textExercise.setChannelName("test" + UUID.randomUUID().toString().substring(0, 8));
        result = request.postWithResponseBody("/api/text/text-exercises", UpdateTextExerciseDTO.of(textExercise), TextExerciseResponseDTO.class, HttpStatus.CREATED);
        assertThat(result.exampleSolutionPublicationDate()).isEqualTo(exampleSolutionPublicationDate);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetTextExercise_asStudent_exampleSolutionVisibility() throws Exception {
        testGetTextExercise_exampleSolutionVisibility(true, TEST_PREFIX + "student1");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetTextExercise_asInstructor_exampleSolutionVisibility() throws Exception {
        testGetTextExercise_exampleSolutionVisibility(false, "instructor1");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportTextExercise_setGradingInstructionForCopiedFeedback() throws Exception {
        var now = ZonedDateTime.now();
        Course course1 = courseUtilService.addEmptyCourse();
        Course course2 = courseUtilService.addEmptyCourse();

        TextExercise textExercise = TextExerciseFactory.generateTextExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), course1);
        textExercise = textExerciseRepository.save(textExercise);
        Set<GradingCriterion> gradingCriteria = exerciseUtilService.addGradingInstructionsToExercise(textExercise);
        gradingCriterionRepository.saveAll(gradingCriteria);
        GradingInstruction gradingInstruction = GradingCriterionUtil.findAnyInstructionWhere(gradingCriteria, instruction -> instruction.getFeedback() != null).orElseThrow();

        // Create example submission
        var exampleSubmission = participationUtilService.generateExampleSubmission("text", textExercise, true);
        exampleSubmission = participationUtilService.addExampleSubmission(exampleSubmission);
        participationUtilService.addResultToSubmission(exampleSubmission.getSubmission(), AssessmentType.MANUAL, textExercise.getId());
        var submission = textSubmissionRepository.findByIdWithEagerResultsAndFeedbackAndTextBlocksElseThrow(exampleSubmission.getSubmission().getId());

        Feedback feedback = ParticipationFactory.generateFeedback().getFirst();
        feedback.setGradingInstruction(gradingInstruction);
        participationUtilService.addFeedbackToResult(feedback, Objects.requireNonNull(submission.getLatestResult()));

        textExercise.setCourse(course2);
        textExercise.setChannelName("test" + UUID.randomUUID().toString().substring(0, 8));
        var importedTextExercise = request.postWithResponseBody("/api/text/text-exercises/import?sourceExerciseId=" + textExercise.getId(), ImportTextExerciseDTO.of(textExercise),
                TextExerciseResponseDTO.class, HttpStatus.CREATED);

        assertThat(textExerciseRepository.findById(importedTextExercise.id())).isPresent();

        // The response DTO no longer carries example submissions/results/feedback, so assert the copied grading instruction
        // on the reloaded entity instead of on the response payload.
        // FIXME-DTO: the previous response-based assertion that the copied GradingInstruction.gradingCriterion is null
        // (added "to avoid infinite recursion when serializing to JSON") no longer applies, since TextExerciseResponseDTO
        // does not serialize example submissions/feedback/grading instructions at all.
        var importedExampleSubmission = textExerciseRepository.findWithExampleSubmissionsAndResultsById(importedTextExercise.id()).orElseThrow().getExampleSubmissions().stream()
                .findFirst().orElseThrow();
        var importedFeedbacks = importedExampleSubmission.getSubmission().getLatestResult().getFeedbacks();
        assertThat(importedFeedbacks).hasSize(1);
        GradingInstruction importedFeedbackGradingInstruction = importedFeedbacks.iterator().next().getGradingInstruction();
        assertThat(importedFeedbackGradingInstruction).isNotNull();

        // Copy and original should have the same data but not the same ids.
        assertThat(importedFeedbackGradingInstruction.getId()).isNotEqualTo(gradingInstruction.getId());
        assertThat(importedFeedbackGradingInstruction.getFeedback()).isEqualTo(gradingInstruction.getFeedback());
        assertThat(importedFeedbackGradingInstruction.getGradingScale()).isEqualTo(gradingInstruction.getGradingScale());
        assertThat(importedFeedbackGradingInstruction.getInstructionDescription()).isEqualTo(gradingInstruction.getInstructionDescription());
        assertThat(importedFeedbackGradingInstruction.getCredits()).isEqualTo(gradingInstruction.getCredits());
        assertThat(importedFeedbackGradingInstruction.getUsageCount()).isEqualTo(gradingInstruction.getUsageCount());

        var importedTextExerciseFromDB = textExerciseRepository.findWithExampleSubmissionsAndResultsById(importedTextExercise.id()).orElseThrow();
        var importedFeedbacksFromDb = importedTextExerciseFromDB.getExampleSubmissions().stream().findFirst().orElseThrow().getSubmission().getLatestResult().getFeedbacks();
        assertThat(importedFeedbacksFromDb).hasSize(1);
        var importedFeedbackGradingInstructionFromDb = importedFeedbacksFromDb.iterator().next().getGradingInstruction();

        assertThat(importedFeedbackGradingInstructionFromDb.getGradingCriterion().getId()).isNotEqualTo(gradingInstruction.getGradingCriterion().getId());

    }

    private void testGetTextExercise_exampleSolutionVisibility(boolean isStudent, String username) throws Exception {
        // Utility function to avoid duplication
        Function<Course, TextExercise> textExerciseGetter = c -> (TextExercise) c.getExercises().stream().filter(e -> e.getId().equals(textExercise.getId())).findAny()
                .orElseThrow();

        textExercise.setExampleSolution("Sample<br>solution");

        if (isStudent) {
            participationUtilService.createAndSaveParticipationForExercise(textExercise, username);
        }

        // Test example solution publication date not set.
        textExercise.setExampleSolutionPublicationDate(null);
        textExerciseRepository.save(textExercise);

        CourseForDashboardDTO courseForDashboard = request.get("/api/course/courses/" + textExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/for-dashboard",
                HttpStatus.OK, CourseForDashboardDTO.class);
        course = courseForDashboard.course();
        TextExercise textExerciseFromApi = textExerciseGetter.apply(course);

        if (isStudent) {
            assertThat(textExerciseFromApi.getExampleSolution()).isNull();
        }
        else {
            assertThat(textExerciseFromApi.getExampleSolution()).isEqualTo(textExercise.getExampleSolution());
        }

        // Test example solution publication date in the past.
        textExercise.setExampleSolutionPublicationDate(ZonedDateTime.now().minusHours(1));
        textExerciseRepository.save(textExercise);

        courseForDashboard = request.get("/api/course/courses/" + textExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/for-dashboard", HttpStatus.OK,
                CourseForDashboardDTO.class);
        course = courseForDashboard.course();
        textExerciseFromApi = textExerciseGetter.apply(course);

        assertThat(textExerciseFromApi.getExampleSolution()).isEqualTo(textExercise.getExampleSolution());

        // Test example solution publication date in the future.
        textExercise.setExampleSolutionPublicationDate(ZonedDateTime.now().plusHours(1));
        textExerciseRepository.save(textExercise);

        courseForDashboard = request.get("/api/course/courses/" + textExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/for-dashboard", HttpStatus.OK,
                CourseForDashboardDTO.class);
        course = courseForDashboard.course();
        textExerciseFromApi = textExerciseGetter.apply(course);

        if (isStudent) {
            assertThat(textExerciseFromApi.getExampleSolution()).isNull();
        }
        else {
            assertThat(textExerciseFromApi.getExampleSolution()).isEqualTo(textExercise.getExampleSolution());
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getTextEditorDataWithNewlyCreatedSubmissionAsStudentWhenNoSubmissionsExist() throws Exception {
        // create a participation with no submissions
        Participation participation = participationUtilService.createAndSaveParticipationForExercise(textExercise, TEST_PREFIX + "student1");
        assertThat(participation.getSubmissions()).isEmpty();

        TextParticipationDTO result = request.get("/api/text/text-editor/" + participation.getId(), HttpStatus.OK, TextParticipationDTO.class);

        // the endpoint should have created a new, unsubmitted submission and return it as part of the participation
        assertThat(result.submissions()).hasSize(1);
        TextSubmissionAssessmentDTO submission = result.submissions().getLast();
        assertThat(submission.submitted()).isFalse();
    }
}
