package de.tum.cit.aet.artemis.fileupload;

import static de.tum.cit.aet.artemis.core.util.TestResourceUtils.HalfSecond;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertExerciseNotInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertFileUploadExerciseExistsInWeaviate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.test.context.support.WithMockUser;

import tools.jackson.databind.node.ObjectNode;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.assessment.domain.GradingInstruction;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.dto.GradingCriterionDTO;
import de.tum.cit.aet.artemis.assessment.util.GradingCriterionUtil;
import de.tum.cit.aet.artemis.atlas.connector.AtlasMLRequestMockProvider;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.util.ConversationUtilService;
import de.tum.cit.aet.artemis.core.dto.SearchResultPageDTO;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseInformationSharingConfiguration;
import de.tum.cit.aet.artemis.course.dto.CourseForDashboardDTO;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exam.util.InvalidExamExerciseDatesArgumentProvider;
import de.tum.cit.aet.artemis.exam.util.InvalidExamExerciseDatesArgumentProvider.InvalidExamExerciseDateConfiguration;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.domain.TeamAssignmentConfig;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.dto.CreateExerciseVariantGroupDTO;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseVariantGroupAssignmentDTO;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseVariantGroupDTO;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadSubmission;
import de.tum.cit.aet.artemis.fileupload.dto.FileUploadExerciseDTO;
import de.tum.cit.aet.artemis.fileupload.dto.FileUploadExerciseInputDTO;
import de.tum.cit.aet.artemis.fileupload.dto.FileUploadPlagiarismDetectionConfigDTO;
import de.tum.cit.aet.artemis.fileupload.dto.FileUploadTeamAssignmentConfigDTO;
import de.tum.cit.aet.artemis.fileupload.dto.UpdateFileUploadExerciseDTO;
import de.tum.cit.aet.artemis.fileupload.util.FileUploadExerciseFactory;
import de.tum.cit.aet.artemis.globalsearch.service.WeaviateService;
import de.tum.cit.aet.artemis.lecture.dto.CompetencyLinkDTO;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismDetectionConfig;

class FileUploadExerciseIntegrationTest extends AbstractFileUploadIntegrationTest {

    @Autowired
    private ConversationUtilService conversationUtilService;

    @Autowired
    private Optional<AtlasMLRequestMockProvider> atlasMLRequestMockProvider;

    @Autowired
    private FeatureToggleService featureToggleService;

    @Autowired(required = false)
    private WeaviateService weaviateService;

    private static final String TEST_PREFIX = "fileuploaderxercise";

    private static final String OTHER_INSTRUCTOR = TEST_PREFIX + "otherinstructor";

    private FileUploadExercise fileUploadExercise;

    private Course course;

    private Competency competency;

    private Set<GradingCriterion> gradingCriteria;

    private final String creationFilePattern = "png, pdf, jPg , r, DOCX";

    static FileUploadExerciseInputDTO inputDTO(Exercise exercise) {
        FileUploadExercise fileUploadExercise = (FileUploadExercise) exercise;
        var teamConfig = initialized(exercise.getTeamAssignmentConfig()) ? FileUploadTeamAssignmentConfigDTO.of(exercise.getTeamAssignmentConfig()) : null;
        var plagiarismConfig = initialized(exercise.getPlagiarismDetectionConfig()) ? FileUploadPlagiarismDetectionConfigDTO.of(exercise.getPlagiarismDetectionConfig()) : null;
        var criteria = mapGradingCriteria(exercise);
        var competencyLinks = initialized(exercise.getCompetencyLinks()) ? exercise.getCompetencyLinks().stream().map(CompetencyLinkDTO::of).collect(Collectors.toSet()) : null;
        Long courseId = exercise.isCourseExercise() ? exercise.getCourseViaExerciseGroupOrCourseMember().getId() : null;
        Long exerciseGroupId = exercise.isExamExercise() ? exercise.getExerciseGroup().getId() : null;
        return new FileUploadExerciseInputDTO(exercise.getId(), exercise.getTitle(), exercise.getChannelName(), exercise.getShortName(), exercise.getProblemStatement(),
                exercise.getCategories(), exercise.getDifficulty(), exercise.getMaxPoints(), exercise.getBonusPoints(), exercise.getIncludedInOverallScore(), exercise.getMode(),
                teamConfig, exercise.getAllowComplaintsForAutomaticAssessments(), exercise.getPresentationScoreEnabled(), exercise.getSecondCorrectionEnabled(),
                exercise.getGradingInstructions(), exercise.getReleaseDate(), exercise.getStartDate(), exercise.getDueDate(), exercise.getAssessmentDueDate(),
                exercise.getExampleSolutionPublicationDate(), fileUploadExercise.getExampleSolution(), fileUploadExercise.getFilePattern(), courseId, exerciseGroupId, criteria,
                competencyLinks, plagiarismConfig);
    }

    private static boolean initialized(Object association) {
        return association != null && Hibernate.isInitialized(association);
    }

    private static List<GradingCriterionDTO> mapGradingCriteria(Exercise exercise) {
        Set<GradingCriterion> criteria = exercise.getGradingCriteria();
        if (!initialized(criteria) || criteria.stream().map(GradingCriterion::getStructuredGradingInstructions).anyMatch(instructions -> !Hibernate.isInitialized(instructions))) {
            return null;
        }
        return criteria.stream().map(GradingCriterionDTO::of).toList();
    }

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 1, 1);
        fileUploadExercise = fileUploadExerciseUtilService.createEnrolledFileUploadExercisesWithCourse(TEST_PREFIX).getFirst();
        course = fileUploadExercise.getCourseViaExerciseGroupOrCourseMember();
        competency = competencyUtilService.createCompetency(course);

        userUtilService.createAndSaveUser(OTHER_INSTRUCTOR);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createFileUploadExerciseFails() throws Exception {
        String filePattern = "Example file pattern";
        fileUploadExercise.setFilePattern(filePattern);
        request.postWithResponseBody("/api/fileupload/file-upload-exercises", inputDTO(fileUploadExercise), FileUploadExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createFileUploadExerciseFailsIfAlreadyCreated() throws Exception {
        String filePattern = "Example file pattern";
        fileUploadExercise.setFilePattern(filePattern);
        fileUploadExercise = fileUploadExerciseRepository.save(fileUploadExercise);
        request.postWithResponseBody("/api/fileupload/file-upload-exercises", inputDTO(fileUploadExercise), FileUploadExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createFileUploadExercise_InvalidInstructor() throws Exception {
        User instructor1 = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        userUtilService.unenrollUserFromCourse(instructor1, course);
        fileUploadExercise.setFilePattern(creationFilePattern);
        gradingCriteria = exerciseUtilService.addGradingInstructionsToExercise(fileUploadExercise);
        request.postWithResponseBody("/api/fileupload/file-upload-exercises", inputDTO(fileUploadExercise), FileUploadExercise.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createFileUploadExerciseFails_AlmostEmptyFilePattern() throws Exception {
        fileUploadExercise.setFilePattern(" ");
        gradingCriteria = exerciseUtilService.addGradingInstructionsToExercise(fileUploadExercise);
        request.postWithResponseBody("/api/fileupload/file-upload-exercises", inputDTO(fileUploadExercise), FileUploadExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createFileUploadExerciseFails_EmptyFilePattern() throws Exception {
        fileUploadExercise.setFilePattern("");
        gradingCriteria = exerciseUtilService.addGradingInstructionsToExercise(fileUploadExercise);
        request.postWithResponseBody("/api/fileupload/file-upload-exercises", inputDTO(fileUploadExercise), FileUploadExercise.class, HttpStatus.BAD_REQUEST);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "exercise-new-fileupload-exerci", "" })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createFileUploadExercise(String channelName) throws Exception {
        courseUtilService.enableMessagingForCourse(course);
        fileUploadExercise.setFilePattern(creationFilePattern);
        fileUploadExercise.setTitle("new fileupload exercise");
        fileUploadExercise.setChannelName(channelName);
        gradingCriteria = exerciseUtilService.addGradingInstructionsToExercise(fileUploadExercise);
        FileUploadExerciseDTO receivedFileUploadExercise = request.postWithResponseBody("/api/fileupload/file-upload-exercises", inputDTO(fileUploadExercise),
                FileUploadExerciseDTO.class, HttpStatus.CREATED);

        Channel channelFromDB = channelRepository.findChannelByExerciseId(receivedFileUploadExercise.id());

        assertThat(receivedFileUploadExercise).isNotNull();
        assertThat(receivedFileUploadExercise.id()).isNotNull();
        assertThat(receivedFileUploadExercise.filePattern()).isEqualTo(creationFilePattern.toLowerCase(Locale.ROOT).replaceAll("\\s+", ""));
        assertThat(receivedFileUploadExercise.course()).as("course was set for normal exercise").isNotNull();
        assertThat(receivedFileUploadExercise.exerciseGroup()).as("exerciseGroup was not set for normal exercise").isNull();
        assertThat(receivedFileUploadExercise.course().id()).as("courseId was set correctly").isEqualTo(course.getId());

        GradingCriterionDTO criterionWithoutTitle = receivedFileUploadExercise.gradingCriteria().stream().filter(criterion -> criterion.title() == null).findFirst().orElseThrow();
        assertThat(criterionWithoutTitle.structuredGradingInstructions()).hasSize(1);
        assertThat(criterionWithoutTitle.structuredGradingInstructions().stream().findFirst().orElseThrow().instructionDescription())
                .isEqualTo("created first instruction with empty criteria for testing");

        assertThat(channelFromDB).isNotNull();
        assertThat(channelFromDB.getName()).isEqualTo("exercise-new-fileupload-exerci");
        assertThat(receivedFileUploadExercise.channelName()).isEqualTo(channelFromDB.getName());

        assertFileUploadExerciseExistsInWeaviate(weaviateService, fileUploadExerciseRepository.findByIdElseThrow(receivedFileUploadExercise.id()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createFileUploadExercise_preservesIdenticalGradingCriteriaFromRawJson() throws Exception {
        fileUploadExercise.setFilePattern(creationFilePattern);
        fileUploadExercise.setTitle("new fileupload exercise");
        fileUploadExercise.setId(null);

        ObjectNode requestBody = request.getObjectMapper().valueToTree(inputDTO(fileUploadExercise));
        ObjectNode criterion = request.getObjectMapper().createObjectNode();
        criterion.put("title", "identical criterion");
        criterion.putArray("structuredGradingInstructions");
        requestBody.putArray("gradingCriteria").add(criterion).add(criterion.deepCopy());

        FileUploadExerciseDTO createdExercise = request.postWithResponseBody("/api/fileupload/file-upload-exercises", requestBody, FileUploadExerciseDTO.class, HttpStatus.CREATED);

        assertThat(createdExercise.gradingCriteria()).hasSize(2);
        assertThat(gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(createdExercise.id())).hasSize(2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createFileUploadExercise_withGradingCriterionId_badRequest() throws Exception {
        fileUploadExercise.setFilePattern(creationFilePattern);
        fileUploadExercise.setTitle("new fileupload exercise");
        fileUploadExercise.setId(null);

        ObjectNode requestBody = request.getObjectMapper().valueToTree(inputDTO(fileUploadExercise));
        ObjectNode criterion = request.getObjectMapper().createObjectNode();
        criterion.put("id", 123L);
        criterion.put("title", "criterion with id");
        requestBody.putArray("gradingCriteria").add(criterion);

        MockHttpServletResponse response = request.postWithoutResponseBody("/api/fileupload/file-upload-exercises", requestBody, HttpStatus.BAD_REQUEST, null);

        assertErrorKey(response, "idExists");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createFileUploadExerciseForExam() throws Exception {
        ExerciseGroup exerciseGroup = examUtilService.addEnrolledExerciseGroupWithExamAndCourse(true, TEST_PREFIX);
        FileUploadExercise fileUploadExercise = FileUploadExerciseFactory.generateFileUploadExerciseForExam(creationFilePattern, exerciseGroup);

        gradingCriteria = exerciseUtilService.addGradingInstructionsToExercise(fileUploadExercise);
        FileUploadExerciseDTO createdFileUploadExercise = request.postWithResponseBody("/api/fileupload/file-upload-exercises", inputDTO(fileUploadExercise),
                FileUploadExerciseDTO.class, HttpStatus.CREATED);

        Channel channelFromDB = channelRepository.findChannelByExerciseId(createdFileUploadExercise.id());
        assertThat(channelFromDB).isNull(); // there should not be any channel for exam exercise

        assertThat(createdFileUploadExercise).isNotNull();
        assertThat(createdFileUploadExercise.id()).isNotNull();
        assertThat(createdFileUploadExercise.filePattern()).isEqualTo(creationFilePattern.toLowerCase(Locale.ROOT).replaceAll("\\s+", ""));
        assertThat(createdFileUploadExercise.course()).as("course was not set for exam exercise").isNull();
        assertThat(createdFileUploadExercise.exerciseGroup()).as("exerciseGroup was set for exam exercise").isNotNull();
        assertThat(createdFileUploadExercise.exerciseGroup().id()).as("exerciseGroupId was set correctly").isEqualTo(exerciseGroup.getId());

        GradingCriterionDTO criterionWithoutTitle = createdFileUploadExercise.gradingCriteria().stream().filter(criterion -> criterion.title() == null).findFirst().orElseThrow();
        assertThat(criterionWithoutTitle.structuredGradingInstructions()).hasSize(1);
        assertThat(criterionWithoutTitle.structuredGradingInstructions().stream().findFirst().orElseThrow().instructionDescription())
                .isEqualTo("created first instruction with empty criteria for testing");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createAndGetTeamFileUploadExercise_preservesTeamConfiguration() throws Exception {
        fileUploadExercise.setTitle("new team file upload exercise");
        fileUploadExercise.setFilePattern(creationFilePattern);
        fileUploadExercise.setMode(ExerciseMode.TEAM);
        TeamAssignmentConfig teamConfig = new TeamAssignmentConfig();
        teamConfig.setMinTeamSize(2);
        teamConfig.setMaxTeamSize(4);
        fileUploadExercise.setTeamAssignmentConfig(teamConfig);
        PlagiarismDetectionConfig plagiarismConfig = PlagiarismDetectionConfig.createDefault();
        plagiarismConfig.setSimilarityThreshold(75);
        fileUploadExercise.setPlagiarismDetectionConfig(plagiarismConfig);

        FileUploadExerciseDTO createdExercise = request.postWithResponseBody("/api/fileupload/file-upload-exercises", inputDTO(fileUploadExercise), FileUploadExerciseDTO.class,
                HttpStatus.CREATED);
        FileUploadExerciseDTO retrievedExercise = request.get("/api/fileupload/file-upload-exercises/" + createdExercise.id(), HttpStatus.OK, FileUploadExerciseDTO.class);

        assertThat(createdExercise.mode()).isEqualTo(ExerciseMode.TEAM);
        assertThat(createdExercise.teamMode()).isTrue();
        assertThat(createdExercise.teamAssignmentConfig().minTeamSize()).isEqualTo(2);
        assertThat(createdExercise.teamAssignmentConfig().maxTeamSize()).isEqualTo(4);
        assertThat(createdExercise.plagiarismDetectionConfig().similarityThreshold()).isEqualTo(75);
        assertThat(retrievedExercise.teamMode()).isTrue();
        assertThat(retrievedExercise.teamAssignmentConfig().minTeamSize()).isEqualTo(2);
        assertThat(retrievedExercise.teamAssignmentConfig().maxTeamSize()).isEqualTo(4);
        assertThat(retrievedExercise.plagiarismDetectionConfig().similarityThreshold()).isEqualTo(75);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createTeamFileUploadExercise_withoutTeamAssignmentConfig_badRequest() throws Exception {
        fileUploadExercise.setId(null);
        fileUploadExercise.setMode(ExerciseMode.TEAM);
        fileUploadExercise.setTeamAssignmentConfig(null);
        fileUploadExercise.setFilePattern(creationFilePattern);

        MockHttpServletResponse response = request.postWithoutResponseBody("/api/fileupload/file-upload-exercises", inputDTO(fileUploadExercise), HttpStatus.BAD_REQUEST, null);

        assertErrorKey(response, "teamAssignmentConfigMissing");
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ArgumentsSource(InvalidExamExerciseDatesArgumentProvider.class)
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createFileUploadExerciseForExam_invalidExercise_dates(InvalidExamExerciseDateConfiguration invalidDates) throws Exception {
        ExerciseGroup exerciseGroup = examUtilService.addExerciseGroupWithExamAndCourse(true);
        FileUploadExercise fileUploadExercise = FileUploadExerciseFactory.generateFileUploadExerciseForExam(creationFilePattern, exerciseGroup);

        request.postWithResponseBody("/api/fileupload/file-upload-exercises", inputDTO(invalidDates.applyTo(fileUploadExercise)), FileUploadExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createFileUploadExercise_setBothCourseAndExerciseGroupOrNeither_badRequest() throws Exception {
        ExerciseGroup exerciseGroup = examUtilService.addExerciseGroupWithExamAndCourse(true);
        FileUploadExercise fileUploadExercise = FileUploadExerciseFactory.generateFileUploadExerciseForExam(creationFilePattern, exerciseGroup);
        fileUploadExercise.setCourse(fileUploadExercise.getCourseViaExerciseGroupOrCourseMember());

        MockHttpServletResponse bothTargetsResponse = request.postWithoutResponseBody("/api/fileupload/file-upload-exercises", inputDTO(fileUploadExercise), HttpStatus.BAD_REQUEST,
                null);
        assertErrorKey(bothTargetsResponse, "eitherCourseOrExerciseGroupSet");

        fileUploadExercise.setCourse(null);
        fileUploadExercise.setExerciseGroup(null);

        MockHttpServletResponse noTargetResponse = request.postWithoutResponseBody("/api/fileupload/file-upload-exercises", inputDTO(fileUploadExercise), HttpStatus.BAD_REQUEST,
                null);
        assertErrorKey(noTargetResponse, "eitherCourseOrExerciseGroupSet");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getFileUploadExercise() throws Exception {
        Course course = fileUploadExerciseUtilService.addEnrolledCourseWithThreeFileUploadExercise(TEST_PREFIX);
        FileUploadExercise fileUploadExercise = ExerciseUtilService.findFileUploadExerciseWithTitle(course.getExercises(), "released");
        course.setTestCourse(true);
        course.setPresentationScore(3);
        course.setCourseInformationSharingConfiguration(CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING);
        course.setAccuracyOfScores(2);
        courseRepository.save(course);
        gradingCriteria = exerciseUtilService.addGradingInstructionsToExercise(fileUploadExercise);
        gradingCriterionRepository.saveAll(gradingCriteria);
        Competency linkedCompetency = competencyUtilService.createCompetency(course);
        competencyExerciseLinkRepository.save(new CompetencyExerciseLink(linkedCompetency, fileUploadExercise, 0.75));

        conversationUtilService.addChannelToExercise(fileUploadExercise);

        FileUploadExerciseDTO receivedFileUploadExercise = assertThatDb(
                () -> request.get("/api/fileupload/file-upload-exercises/" + fileUploadExercise.getId(), HttpStatus.OK, FileUploadExerciseDTO.class)).hasBeenCalledAtMostTimes(10);

        assertThat(fileUploadExercise.getId()).isEqualTo(receivedFileUploadExercise.id());
        assertThat(receivedFileUploadExercise.course()).isNotNull();
        assertThat(receivedFileUploadExercise.course().id()).isEqualTo(course.getId());
        assertThat(receivedFileUploadExercise.course().title()).isEqualTo(course.getTitle());
        assertThat(receivedFileUploadExercise.course().shortName()).isEqualTo(course.getShortName());
        assertThat(receivedFileUploadExercise.course().testCourse()).isTrue();
        assertThat(receivedFileUploadExercise.course().presentationScore()).isEqualTo(3);
        assertThat(receivedFileUploadExercise.course().courseInformationSharingConfiguration()).isEqualTo(CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING);
        assertThat(receivedFileUploadExercise.course().accuracyOfScores()).isEqualTo(2);
        assertThat(receivedFileUploadExercise.gradingCriteria()).isNotEmpty();
        assertThat(receivedFileUploadExercise.competencyLinks()).singleElement().satisfies(link -> {
            assertThat(link.competency().id()).isEqualTo(linkedCompetency.getId());
            assertThat(link.weight()).isEqualTo(0.75);
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getExamFileUploadExercise_asStudent_forbidden() throws Exception {
        getExamFileUploadExercise();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getExamFileUploadExercise_asTutor_forbidden() throws Exception {
        getExamFileUploadExercise();
    }

    private void getExamFileUploadExercise() throws Exception {
        FileUploadExercise fileUploadExercise = fileUploadExerciseUtilService.addEnrolledCourseExamExerciseGroupWithOneFileUploadExercise(TEST_PREFIX, false);
        request.get("/api/fileupload/file-upload-exercises/" + fileUploadExercise.getId(), HttpStatus.FORBIDDEN, FileUploadExercise.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getExamFileUploadExercise_asInstructor() throws Exception {
        FileUploadExercise fileUploadExercise = fileUploadExerciseUtilService.addEnrolledCourseExamExerciseGroupWithOneFileUploadExercise(TEST_PREFIX, false);

        FileUploadExerciseDTO receivedFileUploadExercise = request.get("/api/fileupload/file-upload-exercises/" + fileUploadExercise.getId(), HttpStatus.OK,
                FileUploadExerciseDTO.class);
        assertThat(receivedFileUploadExercise).as("exercise was retrieved").isNotNull();
        assertThat(receivedFileUploadExercise.id()).as("exercise with the right id was retrieved").isEqualTo(fileUploadExercise.getId());
        assertThat(receivedFileUploadExercise.exerciseGroup()).isNotNull();
        assertThat(receivedFileUploadExercise.exerciseGroup().exam()).isNotNull();
        assertThat(receivedFileUploadExercise.exerciseGroup().exam().title()).isEqualTo(fileUploadExercise.getExerciseGroup().getExam().getTitle());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getFileUploadExerciseFails_wrongId() throws Exception {
        fileUploadExerciseUtilService.addEnrolledCourseWithThreeFileUploadExercise(TEST_PREFIX);
        request.get("/api/fileupload/file-upload-exercises/" + 555555, HttpStatus.NOT_FOUND, FileUploadExercise.class);
    }

    @Test
    @WithMockUser(username = OTHER_INSTRUCTOR, roles = "INSTRUCTOR")
    void getExamFileUploadExercise_InstructorNotInGroup() throws Exception {
        Course course = fileUploadExerciseUtilService.addEnrolledCourseWithThreeFileUploadExercise(TEST_PREFIX);
        for (var exercise : course.getExercises()) {
            request.get("/api/fileupload/file-upload-exercises/" + exercise.getId(), HttpStatus.FORBIDDEN, FileUploadExercise.class);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetFileUploadExercise_setGradingInstructionFeedbackUsed() throws Exception {
        Course course = fileUploadExerciseUtilService.addEnrolledCourseWithThreeFileUploadExercise(TEST_PREFIX);
        FileUploadExercise fileUploadExercise = ExerciseUtilService.findFileUploadExerciseWithTitle(course.getExercises(), "released");
        gradingCriteria = exerciseUtilService.addGradingInstructionsToExercise(fileUploadExercise);
        gradingCriterionRepository.saveAll(gradingCriteria);
        Feedback feedback = new Feedback();
        feedback.setGradingInstruction(GradingCriterionUtil.findAnyInstructionWhere(gradingCriteria, instruction -> true).orElseThrow());
        feedbackRepository.save(feedback);

        conversationUtilService.addChannelToExercise(fileUploadExercise);

        FileUploadExerciseDTO receivedFileUploadExercise = request.get("/api/fileupload/file-upload-exercises/" + fileUploadExercise.getId(), HttpStatus.OK,
                FileUploadExerciseDTO.class);

        assertThat(receivedFileUploadExercise.gradingInstructionFeedbackUsed()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteFileUploadExercise_asInstructor() throws Exception {
        Course course = fileUploadExerciseUtilService.addEnrolledCourseWithThreeFileUploadExercise(TEST_PREFIX);
        var exerciseIds = course.getExercises().stream().map(Exercise::getId).toList();
        for (var exercise : course.getExercises()) {
            request.delete("/api/fileupload/file-upload-exercises/" + exercise.getId(), HttpStatus.OK);
        }
        assertThat(exerciseRepository.findByCourseIdWithCategories(course.getId())).isEmpty();
        for (var exerciseId : exerciseIds) {
            assertExerciseNotInWeaviate(weaviateService, exerciseId);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteFileUploadExerciseWithChannel() throws Exception {
        Course course = fileUploadExerciseUtilService.addEnrolledCourseWithFileUploadExercise(TEST_PREFIX);
        FileUploadExercise fileUploadExercise = fileUploadExerciseRepository.findByCourseIdWithCategories(course.getId()).getFirst();
        Channel exerciseChannel = conversationUtilService.addChannelToExercise(fileUploadExercise);

        request.delete("/api/fileupload/file-upload-exercises/" + fileUploadExercise.getId(), HttpStatus.OK);

        Optional<Channel> exerciseChannelAfterDelete = channelRepository.findById(exerciseChannel.getId());
        assertThat(exerciseChannelAfterDelete).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteFileUploadExerciseWithCompetency() throws Exception {
        fileUploadExercise = fileUploadExerciseRepository.save(fileUploadExercise);
        competencyExerciseLinkRepository.save(new CompetencyExerciseLink(competency, fileUploadExercise, 1));
        request.delete("/api/fileupload/file-upload-exercises/" + fileUploadExercise.getId(), HttpStatus.OK);

        verify(competencyProgressApi).updateProgressByCompetencyAsync(eq(competency));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void deleteFileUploadExercise_asStudent() throws Exception {
        Course course = fileUploadExerciseUtilService.addEnrolledCourseWithThreeFileUploadExercise(TEST_PREFIX);
        for (var exercise : course.getExercises()) {
            request.delete("/api/fileupload/file-upload-exercises/" + exercise.getId(), HttpStatus.FORBIDDEN);
        }

        assertThat(exerciseRepository.findByCourseIdWithCategories(course.getId())).hasSize(course.getExercises().size());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteFileUploadExerciseFails_WithWrongId() throws Exception {
        fileUploadExerciseUtilService.addEnrolledCourseWithThreeFileUploadExercise(TEST_PREFIX);
        request.delete("/api/fileupload/file-upload-exercises/" + 5555555, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = OTHER_INSTRUCTOR, roles = "INSTRUCTOR")
    void deleteFileUploadExerciseFails_InstructorNotInGroup() throws Exception {
        Course course = fileUploadExerciseUtilService.addEnrolledCourseWithThreeFileUploadExercise(TEST_PREFIX);
        for (var exercise : course.getExercises()) {
            request.delete("/api/fileupload/file-upload-exercises/" + exercise.getId(), HttpStatus.FORBIDDEN);
        }
        assertThat(exerciseRepository.findByCourseIdWithCategories(course.getId())).hasSize(3);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteExamFileUploadExercise() throws Exception {
        FileUploadExercise fileUploadExercise = fileUploadExerciseUtilService.addEnrolledCourseExamExerciseGroupWithOneFileUploadExercise(TEST_PREFIX, false);
        request.delete("/api/fileupload/file-upload-exercises/" + fileUploadExercise.getId(), HttpStatus.OK);
        assertThat(exerciseRepository.findByCourseIdWithCategories(fileUploadExercise.getCourseViaExerciseGroupOrCourseMember().getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateFileUploadExercise_asInstructor() throws Exception {
        Course course = fileUploadExerciseUtilService.addEnrolledCourseWithThreeFileUploadExercise(TEST_PREFIX);
        Competency newCompetency = competencyUtilService.createCompetency(course);
        assertThat(newCompetency.getCourse().getId()).as("courseId was not updated").isEqualTo(course.getId());
        FileUploadExercise fileUploadExercise = ExerciseUtilService.findFileUploadExerciseWithTitle(course.getExercises(), "released");
        final ZonedDateTime dueDate = ZonedDateTime.now().plusDays(10);
        fileUploadExercise.setDueDate(dueDate);
        fileUploadExercise.setAssessmentDueDate(ZonedDateTime.now().plusDays(11));
        fileUploadExercise.setCompetencyLinks(Set.of(new CompetencyExerciseLink(newCompetency, fileUploadExercise, 1)));

        FileUploadExerciseDTO receivedFileUploadExercise = assertThatDb(
                () -> request.putWithResponseBody("/api/fileupload/file-upload-exercises/" + fileUploadExercise.getId() + "?notificationText=notification",
                        UpdateFileUploadExerciseDTO.of(fileUploadExercise), FileUploadExerciseDTO.class, HttpStatus.OK))
                // Includes the four fixed queries used to reload and enrich the response DTO and one lookup for the new competency link.
                .hasBeenCalledAtMostTimes(52);
        assertThat(receivedFileUploadExercise.dueDate()).isCloseTo(dueDate, HalfSecond());
        assertThat(receivedFileUploadExercise.course()).as("course was set for normal exercise").isNotNull();
        assertThat(receivedFileUploadExercise.exerciseGroup()).as("exerciseGroup was not set for normal exercise").isNull();
        assertThat(receivedFileUploadExercise.course().id()).as("courseId was not updated").isEqualTo(course.getId());
        verify(examLiveEventsService, never()).createAndSendProblemStatementUpdateEvent(any(), any(), any());
        verify(groupNotificationScheduleService, times(1)).checkAndCreateAppropriateNotificationsWhenUpdatingExercise(any(), any(), any(), any());
        verify(competencyProgressApi, timeout(1000).times(1)).updateProgressForUpdatedLearningObjectAsyncWithOriginalCompetencyIds(eq(Set.of()), any());
        assertFileUploadExerciseExistsInWeaviate(weaviateService, fileUploadExerciseRepository.findByIdElseThrow(receivedFileUploadExercise.id()));
    }

    @Test
    @WithMockUser(username = OTHER_INSTRUCTOR, roles = "INSTRUCTOR")
    void updateFileUploadExerciseFails_InstructorNotInGroup() throws Exception {
        Course course = fileUploadExerciseUtilService.addEnrolledCourseWithThreeFileUploadExercise(TEST_PREFIX);
        FileUploadExercise fileUploadExercise = ExerciseUtilService.findFileUploadExerciseWithTitle(course.getExercises(), "released");
        fileUploadExercise.setDueDate(ZonedDateTime.now().plusDays(10));
        fileUploadExercise.setAssessmentDueDate(ZonedDateTime.now().plusDays(11));
        request.putWithResponseBody("/api/fileupload/file-upload-exercises/" + fileUploadExercise.getId(), UpdateFileUploadExerciseDTO.of(fileUploadExercise),
                FileUploadExercise.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateFileUploadExerciseForExam_asInstructor() throws Exception {
        FileUploadExercise fileUploadExercise = fileUploadExerciseUtilService.addEnrolledCourseExamExerciseGroupWithOneFileUploadExercise(TEST_PREFIX, true);
        String newTitle = "New file upload exercise title";
        fileUploadExercise.setTitle(newTitle);
        fileUploadExercise.setProblemStatement("New problem statement");

        FileUploadExerciseDTO updatedFileUploadExercise = request.putWithResponseBody("/api/fileupload/file-upload-exercises/" + fileUploadExercise.getId(),
                UpdateFileUploadExerciseDTO.of(fileUploadExercise), FileUploadExerciseDTO.class, HttpStatus.OK);

        assertThat(updatedFileUploadExercise.title()).isEqualTo(newTitle);
        assertThat(updatedFileUploadExercise.course()).as("course was not set for exam exercise").isNull();
        assertThat(updatedFileUploadExercise.exerciseGroup()).as("exerciseGroup was set for exam exercise").isNotNull();
        assertThat(updatedFileUploadExercise.exerciseGroup().id()).as("exerciseGroupId was not updated").isEqualTo(fileUploadExercise.getExerciseGroup().getId());
        verify(examLiveEventsService, timeout(2000).times(1)).createAndSendProblemStatementUpdateEvent(any(), any());
        verify(groupNotificationScheduleService, never()).checkAndCreateAppropriateNotificationsWhenUpdatingExercise(any(), any(), any(), any());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateFileUploadExerciseForExam_withoutCourseId_asInstructor() throws Exception {
        FileUploadExercise examExercise = fileUploadExerciseUtilService.addEnrolledCourseExamExerciseGroupWithOneFileUploadExercise(TEST_PREFIX, true);
        UpdateFileUploadExerciseDTO originalDTO = UpdateFileUploadExerciseDTO.of(examExercise);
        UpdateFileUploadExerciseDTO dtoWithoutCourseId = createDtoWithTargetIds(originalDTO, null, examExercise.getExerciseGroup().getId());

        FileUploadExerciseDTO updatedExercise = request.putWithResponseBody("/api/fileupload/file-upload-exercises/" + examExercise.getId(), dtoWithoutCourseId,
                FileUploadExerciseDTO.class, HttpStatus.OK);

        assertThat(updatedExercise.exerciseGroup()).isNotNull();
        assertThat(updatedExercise.exerciseGroup().id()).isEqualTo(examExercise.getExerciseGroup().getId());
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateFileUploadExerciseForExam_withChangedTargetIds_badRequest(boolean reEvaluate) throws Exception {
        FileUploadExercise examExercise = fileUploadExerciseUtilService.addEnrolledCourseExamExerciseGroupWithOneFileUploadExercise(TEST_PREFIX, true);
        UpdateFileUploadExerciseDTO originalDTO = UpdateFileUploadExerciseDTO.of(examExercise);
        String endpoint = "/api/fileupload/file-upload-exercises/" + examExercise.getId() + (reEvaluate ? "/re-evaluate?deleteFeedback=false" : "");

        Course otherCourse = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        UpdateFileUploadExerciseDTO dtoWithForeignCourseId = createDtoWithTargetIds(originalDTO, otherCourse.getId(), examExercise.getExerciseGroup().getId());
        MockHttpServletResponse foreignCourseResponse = request.putWithoutResponseBody(endpoint, dtoWithForeignCourseId, HttpStatus.BAD_REQUEST);
        assertErrorKey(foreignCourseResponse, "courseIdInvalid");

        ExerciseGroup otherExerciseGroup = examUtilService.addEnrolledExerciseGroupWithExamAndCourse(true, TEST_PREFIX);
        UpdateFileUploadExerciseDTO dtoWithForeignExerciseGroupId = createDtoWithTargetIds(originalDTO, null, otherExerciseGroup.getId());
        MockHttpServletResponse foreignExerciseGroupResponse = request.putWithoutResponseBody(endpoint, dtoWithForeignExerciseGroupId, HttpStatus.CONFLICT);
        assertErrorKey(foreignExerciseGroupResponse, "exerciseGroupCannotChange");
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ArgumentsSource(InvalidExamExerciseDatesArgumentProvider.class)
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateFileUploadExerciseForExam_invalid_dates(InvalidExamExerciseDateConfiguration dates) throws Exception {
        FileUploadExercise fileUploadExercise = fileUploadExerciseUtilService.addEnrolledCourseExamExerciseGroupWithOneFileUploadExercise(TEST_PREFIX, false);

        request.putWithResponseBody("/api/fileupload/file-upload-exercises/" + fileUploadExercise.getId(), UpdateFileUploadExerciseDTO.of(dates.applyTo(fileUploadExercise)),
                FileUploadExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateFileUploadExercise_setBothCourseAndExerciseGroupOrNeither_badRequest() throws Exception {
        // Test case 1: Exam exercise - send DTO with courseId but without exerciseGroupId
        FileUploadExercise examExercise = fileUploadExerciseUtilService.addCourseExamExerciseGroupWithOneFileUploadExercise(false);
        UpdateFileUploadExerciseDTO originalExamDTO = UpdateFileUploadExerciseDTO.of(examExercise);
        UpdateFileUploadExerciseDTO examDtoWithoutExerciseGroupId = createDtoWithTargetIds(originalExamDTO, originalExamDTO.courseId(), null);
        MockHttpServletResponse missingExerciseGroupResponse = request.putWithoutResponseBody("/api/fileupload/file-upload-exercises/" + examExercise.getId(),
                examDtoWithoutExerciseGroupId, HttpStatus.BAD_REQUEST);
        assertErrorKey(missingExerciseGroupResponse, "exerciseGroupIdMissing");

        // Test case 2: Exam exercise - send DTO with neither courseId nor exerciseGroupId set
        UpdateFileUploadExerciseDTO dtoWithNeitherSet = createDtoWithNeitherCourseNorExerciseGroup(examExercise);
        request.putWithResponseBody("/api/fileupload/file-upload-exercises/" + examExercise.getId(), dtoWithNeitherSet, FileUploadExercise.class, HttpStatus.BAD_REQUEST);

        // Test case 3: Course exercise - send DTO with both courseId and exerciseGroupId set
        Course testCourse = fileUploadExerciseUtilService.addEnrolledCourseWithThreeFileUploadExercise(TEST_PREFIX);
        FileUploadExercise courseExercise = ExerciseUtilService.findFileUploadExerciseWithTitle(testCourse.getExercises(), "released");
        UpdateFileUploadExerciseDTO courseDtoWithBothSet = createDtoWithBothCourseAndExerciseGroup(courseExercise);
        MockHttpServletResponse ambiguousCourseResponse = request.putWithoutResponseBody("/api/fileupload/file-upload-exercises/" + courseExercise.getId(), courseDtoWithBothSet,
                HttpStatus.BAD_REQUEST);
        assertErrorKey(ambiguousCourseResponse, "courseOrExerciseGroupRequired");

        // Test case 4: Course exercise - send DTO with neither courseId nor exerciseGroupId set
        UpdateFileUploadExerciseDTO courseDtoWithNeitherSet = createDtoWithNeitherCourseNorExerciseGroup(courseExercise);
        request.putWithResponseBody("/api/fileupload/file-upload-exercises/" + courseExercise.getId(), courseDtoWithNeitherSet, FileUploadExercise.class, HttpStatus.BAD_REQUEST);
    }

    /**
     * Creates a DTO from the exercise but with both courseId and exerciseGroupId set.
     * This is invalid for course exercises but allowed for exam exercises.
     */
    private UpdateFileUploadExerciseDTO createDtoWithBothCourseAndExerciseGroup(FileUploadExercise exercise) {
        UpdateFileUploadExerciseDTO original = UpdateFileUploadExerciseDTO.of(exercise);
        Long courseId = exercise.getCourseViaExerciseGroupOrCourseMember() != null ? exercise.getCourseViaExerciseGroupOrCourseMember().getId() : 1L;
        Long exerciseGroupId = exercise.getExerciseGroup() != null ? exercise.getExerciseGroup().getId() : 1L;
        return createDtoWithTargetIds(original, courseId, exerciseGroupId);
    }

    /**
     * Creates a DTO from the exercise but with neither courseId nor exerciseGroupId set (invalid).
     */
    private UpdateFileUploadExerciseDTO createDtoWithNeitherCourseNorExerciseGroup(FileUploadExercise exercise) {
        UpdateFileUploadExerciseDTO original = UpdateFileUploadExerciseDTO.of(exercise);
        return createDtoWithTargetIds(original, null, null);
    }

    private UpdateFileUploadExerciseDTO createDtoWithTargetIds(UpdateFileUploadExerciseDTO original, Long courseId, Long exerciseGroupId) {
        return new UpdateFileUploadExerciseDTO(original.id(), original.title(), original.channelName(), original.shortName(), original.problemStatement(), original.categories(),
                original.difficulty(), original.maxPoints(), original.bonusPoints(), original.includedInOverallScore(), original.allowComplaintsForAutomaticAssessments(),
                original.presentationScoreEnabled(), original.secondCorrectionEnabled(), original.gradingInstructions(), original.releaseDate(), original.startDate(),
                original.dueDate(), original.assessmentDueDate(), original.exampleSolutionPublicationDate(), original.exampleSolution(), original.filePattern(), courseId,
                exerciseGroupId, original.gradingCriteria(), original.competencyLinks());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateFileUploadExercise_conversionBetweenCourseAndExamExercise_badRequest() throws Exception {
        FileUploadExercise fileUploadExerciseWithExerciseGroup = fileUploadExerciseUtilService.addEnrolledCourseExamExerciseGroupWithOneFileUploadExercise(TEST_PREFIX, false);

        fileUploadExercise.setCourse(null);
        fileUploadExercise.setExerciseGroup(fileUploadExerciseWithExerciseGroup.getExerciseGroup());
        fileUploadExercise = fileUploadExerciseRepository.saveAndFlush(fileUploadExercise);

        fileUploadExerciseWithExerciseGroup.setCourse(course);
        fileUploadExerciseWithExerciseGroup.setExerciseGroup(null);

        request.putWithResponseBody("/api/fileupload/file-upload-exercises/" + fileUploadExercise.getId(), UpdateFileUploadExerciseDTO.of(fileUploadExercise),
                FileUploadExercise.class, HttpStatus.BAD_REQUEST);
        request.putWithResponseBody("/api/fileupload/file-upload-exercises/" + fileUploadExerciseWithExerciseGroup.getId(),
                UpdateFileUploadExerciseDTO.of(fileUploadExerciseWithExerciseGroup), FileUploadExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateModelingExerciseDueDate() throws Exception {
        fileUploadExercise = fileUploadExerciseRepository.save(fileUploadExercise);

        final ZonedDateTime individualDueDate = ZonedDateTime.now().plusHours(20);

        {
            final FileUploadSubmission submission1 = ParticipationFactory.generateFileUploadSubmission(true);
            fileUploadExerciseUtilService.addFileUploadSubmission(fileUploadExercise, submission1, TEST_PREFIX + "student1");
            final FileUploadSubmission submission2 = ParticipationFactory.generateFileUploadSubmission(true);
            fileUploadExerciseUtilService.addFileUploadSubmission(fileUploadExercise, submission2, TEST_PREFIX + "student2");

            final var participations = new ArrayList<>(studentParticipationRepository.findByExerciseId(fileUploadExercise.getId()));
            assertThat(participations).hasSize(2);
            participations.getFirst().setIndividualDueDate(ZonedDateTime.now().plusHours(2));
            participations.get(1).setIndividualDueDate(individualDueDate);
            studentParticipationRepository.saveAll(participations);
        }

        fileUploadExercise.setDueDate(ZonedDateTime.now().plusHours(12));
        FileUploadExerciseDTO updatedExercise = request.putWithResponseBody("/api/fileupload/file-upload-exercises/" + fileUploadExercise.getId(),
                UpdateFileUploadExerciseDTO.of(fileUploadExercise), FileUploadExerciseDTO.class, HttpStatus.OK);
        assertThat(updatedExercise.id()).isEqualTo(fileUploadExercise.getId());

        {
            final var participations = studentParticipationRepository.findByExerciseId(fileUploadExercise.getId());
            final var withNoIndividualDueDate = participations.stream().filter(participation -> participation.getIndividualDueDate() == null).toList();
            assertThat(withNoIndividualDueDate).hasSize(1);

            final var withIndividualDueDate = participations.stream().filter(participation -> participation.getIndividualDueDate() != null).toList();
            assertThat(withIndividualDueDate).hasSize(1);
            assertThat(withIndividualDueDate.getFirst().getIndividualDueDate()).isCloseTo(individualDueDate, HalfSecond());
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getAllFileUploadExercisesForCourse_asInstructor() throws Exception {
        var course = fileUploadExerciseUtilService.addEnrolledCourseWithThreeFileUploadExercise(TEST_PREFIX);
        FileUploadExercise teamExercise = (FileUploadExercise) course.getExercises().iterator().next();
        teamExercise.setMode(ExerciseMode.TEAM);
        TeamAssignmentConfig teamAssignmentConfig = new TeamAssignmentConfig();
        teamAssignmentConfig.setMinTeamSize(2);
        teamAssignmentConfig.setMaxTeamSize(4);
        teamExercise.setTeamAssignmentConfig(teamAssignmentConfig);
        fileUploadExerciseRepository.save(teamExercise);
        List<FileUploadExerciseDTO> receivedFileUploadExercises = request.getList("/api/fileupload/courses/" + course.getId() + "/file-upload-exercises", HttpStatus.OK,
                FileUploadExerciseDTO.class);

        // this seems to be a flaky test, based on the execution order, the following line has a problem with authentication, this should fix it
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        assertThat(receivedFileUploadExercises).hasSize(course.getExercises().size());
        assertThat(receivedFileUploadExercises).allSatisfy(exercise -> assertThat(exercise.course()).isNull());
        assertThat(receivedFileUploadExercises).filteredOn(exercise -> exercise.id().equals(teamExercise.getId())).singleElement().satisfies(exercise -> {
            assertThat(exercise.mode()).isEqualTo(ExerciseMode.TEAM);
            assertThat(exercise.teamMode()).isTrue();
            assertThat(exercise.teamAssignmentConfig()).isNull();
        });
    }

    @Test
    @WithMockUser(username = OTHER_INSTRUCTOR, roles = "INSTRUCTOR")
    void getAllFileUploadExercisesForCourseFails_InstructorNotInGroup() throws Exception {
        var course = fileUploadExerciseUtilService.addEnrolledCourseWithThreeFileUploadExercise(TEST_PREFIX);
        request.getList("/api/fileupload/courses/" + course.getId() + "/file-upload-exercises", HttpStatus.FORBIDDEN, FileUploadExercise.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getAllFileUploadExercisesForCourse_asStudent() throws Exception {
        var course = fileUploadExerciseUtilService.addEnrolledCourseWithThreeFileUploadExercise(TEST_PREFIX);
        request.getList("/api/fileupload/courses/" + course.getId() + "/file-upload-exercises", HttpStatus.FORBIDDEN, FileUploadExercise.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testReEvaluateAndUpdateFileUploadExercise() throws Exception {
        Course course = fileUploadExerciseUtilService.addEnrolledCourseWithThreeFileUploadExercise(TEST_PREFIX);
        FileUploadExercise fileUploadExercise = ExerciseUtilService.findFileUploadExerciseWithTitle(course.getExercises(), "released");
        Set<GradingCriterion> gradingCriteria = exerciseUtilService.addGradingInstructionsToExercise(fileUploadExercise);
        gradingCriterionRepository.saveAll(gradingCriteria);

        StudentParticipation participation = participationUtilService.addAssessmentWithFeedbackWithGradingInstructionsForExercise(fileUploadExercise, TEST_PREFIX + "instructor1");

        // change grading instruction score
        Set<GradingInstruction> usedInstructions = participation.getSubmissions().stream().flatMap(submission -> submission.getResults().stream())
                .flatMap(result -> result.getFeedbacks().stream()).flatMap(feedback -> Optional.ofNullable(feedback.getGradingInstruction()).stream())
                .collect(Collectors.toUnmodifiableSet());
        assertThat(usedInstructions).hasSize(1);
        GradingInstruction usedInstruction = usedInstructions.stream().findAny().orElseThrow();
        usedInstruction.setCredits(3);
        fileUploadExercise.setGradingCriteria(gradingCriteria);

        FileUploadExerciseDTO updatedFileUploadExerciseDTO = assertThatDb(
                () -> request.putWithResponseBody("/api/fileupload/file-upload-exercises/" + fileUploadExercise.getId() + "/re-evaluate" + "?deleteFeedback=false",
                        UpdateFileUploadExerciseDTO.of(fileUploadExercise), FileUploadExerciseDTO.class, HttpStatus.OK))
                // Includes the four fixed queries used to reload and enrich the response DTO. The ceiling is the number
                // this flow actually performs; it guards against new N+1 queries rather than describing an optimum.
                .hasBeenCalledAtMostTimes(53);
        FileUploadExercise updatedFileUploadExercise = fileUploadExerciseRepository.findByIdElseThrow(updatedFileUploadExerciseDTO.id());
        List<Result> updatedResults = participationUtilService.getResultsForExercise(updatedFileUploadExercise);
        assertThat(GradingCriterionUtil.findAnyInstructionWhere(gradingCriteria, instruction -> instruction.getId().equals(usedInstruction.getId())).orElseThrow().getCredits())
                .isEqualTo(3);
        assertThat(updatedResults.getFirst().getScore()).isEqualTo(60);
        assertThat(updatedResults.getFirst().getFeedbacks().iterator().next().getCredits()).isEqualTo(3);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateFileUploadExercise_withExplicitEmptyCollections_preservesResponseContext(boolean reEvaluate) throws Exception {
        Course testCourse = fileUploadExerciseUtilService.addEnrolledCourseWithThreeFileUploadExercise(TEST_PREFIX);
        FileUploadExercise exercise = ExerciseUtilService.findFileUploadExerciseWithTitle(testCourse.getExercises(), "released");
        gradingCriterionRepository.saveAll(exerciseUtilService.addGradingInstructionsToExercise(exercise));
        Competency linkedCompetency = competencyUtilService.createCompetency(testCourse);
        competencyExerciseLinkRepository.save(new CompetencyExerciseLink(linkedCompetency, exercise, 1));

        ExerciseVariantGroupDTO variantGroup = request.postWithResponseBody("/api/exercise/courses/" + testCourse.getId() + "/exercise-variant-groups",
                new CreateExerciseVariantGroupDTO("File upload variants", exercise.getMaxPoints(), null, null, null, null, null), ExerciseVariantGroupDTO.class,
                HttpStatus.CREATED);
        request.put("/api/exercise/courses/" + testCourse.getId() + "/exercises/" + exercise.getId() + "/variant-group", new ExerciseVariantGroupAssignmentDTO(variantGroup.id()),
                HttpStatus.OK);

        Channel channel = conversationUtilService.addChannelToExercise(exercise);
        exercise.setChannelName(channel.getName());
        ObjectNode updateBody = request.getObjectMapper().valueToTree(UpdateFileUploadExerciseDTO.of(exercise));
        updateBody.putArray("gradingCriteria");
        updateBody.putArray("competencyLinks");
        String endpoint = "/api/fileupload/file-upload-exercises/" + exercise.getId() + (reEvaluate ? "/re-evaluate?deleteFeedback=false" : "");

        FileUploadExerciseDTO response = request.putWithResponseBody(endpoint, updateBody, FileUploadExerciseDTO.class, HttpStatus.OK);

        assertThat(response.channelName()).isEqualTo(channel.getName());
        assertThat(response.exerciseVariantGroup()).isNotNull();
        assertThat(response.exerciseVariantGroup().id()).isEqualTo(variantGroup.id());
        assertThat(response.gradingCriteria()).isNullOrEmpty();
        assertThat(response.competencyLinks()).isNullOrEmpty();
        assertThat(gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(exercise.getId())).isEmpty();
        assertThat(competencyExerciseLinkRepository.findByExerciseIdWithCompetency(exercise.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateFileUploadExercise_withOmittedGradingCriteria_preservesExistingCriteria() throws Exception {
        Course testCourse = fileUploadExerciseUtilService.addEnrolledCourseWithThreeFileUploadExercise(TEST_PREFIX);
        FileUploadExercise exercise = ExerciseUtilService.findFileUploadExerciseWithTitle(testCourse.getExercises(), "released");
        Set<GradingCriterion> existingCriteria = exerciseUtilService.addGradingInstructionsToExercise(exercise);
        gradingCriterionRepository.saveAll(existingCriteria);

        ObjectNode updateBody = request.getObjectMapper().valueToTree(UpdateFileUploadExerciseDTO.of(exercise));
        updateBody.remove("gradingCriteria");

        FileUploadExerciseDTO response = request.putWithResponseBody("/api/fileupload/file-upload-exercises/" + exercise.getId(), updateBody, FileUploadExerciseDTO.class,
                HttpStatus.OK);

        assertThat(response.gradingCriteria()).hasSameSizeAs(existingCriteria);
        assertThat(gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(exercise.getId())).hasSameSizeAs(existingCriteria);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testReEvaluateAndUpdateFileUploadExercise_shouldDeleteFeedbacks() throws Exception {
        Course course = fileUploadExerciseUtilService.addEnrolledCourseWithThreeFileUploadExercise(TEST_PREFIX);
        FileUploadExercise fileUploadExercise = ExerciseUtilService.findFileUploadExerciseWithTitle(course.getExercises(), "released");
        Set<GradingCriterion> gradingCriteria = exerciseUtilService.addGradingInstructionsToExercise(fileUploadExercise);
        gradingCriterionRepository.saveAll(gradingCriteria);

        participationUtilService.addAssessmentWithFeedbackWithGradingInstructionsForExercise(fileUploadExercise, TEST_PREFIX + "instructor1");

        // remove instruction which is associated with feedbacks
        gradingCriteria.removeIf(criterion -> criterion.getTitle() == null);
        fileUploadExercise.setGradingCriteria(gradingCriteria);

        FileUploadExerciseDTO updatedFileUploadExerciseDTO = request.putWithResponseBody(
                "/api/fileupload/file-upload-exercises/" + fileUploadExercise.getId() + "/re-evaluate" + "?deleteFeedback=true", UpdateFileUploadExerciseDTO.of(fileUploadExercise),
                FileUploadExerciseDTO.class, HttpStatus.OK);
        FileUploadExercise updatedFileUploadExercise = fileUploadExerciseRepository.findByIdElseThrow(updatedFileUploadExerciseDTO.id());
        List<Result> updatedResults = participationUtilService.getResultsForExercise(updatedFileUploadExercise);
        assertThat(updatedFileUploadExerciseDTO.gradingCriteria()).hasSize(2);
        assertThat(updatedResults.getFirst().getScore()).isZero();
        assertThat(updatedResults.getFirst().getFeedbacks()).isEmpty();

    }

    @Test
    @WithMockUser(username = OTHER_INSTRUCTOR, roles = "INSTRUCTOR")
    void testReEvaluateAndUpdateFileUploadExercise_isNotAtLeastInstructorInCourse_forbidden() throws Exception {
        Course course = fileUploadExerciseUtilService.addEnrolledCourseWithThreeFileUploadExercise(TEST_PREFIX);
        FileUploadExercise fileUploadExercise = ExerciseUtilService.findFileUploadExerciseWithTitle(course.getExercises(), "released");

        request.putWithResponseBody("/api/fileupload/file-upload-exercises/" + fileUploadExercise.getId() + "/re-evaluate", UpdateFileUploadExerciseDTO.of(fileUploadExercise),
                FileUploadExercise.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testReEvaluateAndUpdateFileUploadExercise_isNotSameGivenExerciseIdInRequestBody_conflict() throws Exception {
        Course course = fileUploadExerciseUtilService.addEnrolledCourseWithThreeFileUploadExercise(TEST_PREFIX);
        FileUploadExercise fileUploadExercise = ExerciseUtilService.findFileUploadExerciseWithTitle(course.getExercises(), "released");
        FileUploadExercise fileUploadExerciseToBeConflicted = fileUploadExerciseRepository.findByIdElseThrow(fileUploadExercise.getId());
        fileUploadExerciseToBeConflicted.setId(123456789L);
        request.putWithResponseBody("/api/fileupload/file-upload-exercises/" + fileUploadExercise.getId() + "/re-evaluate",
                UpdateFileUploadExerciseDTO.of(fileUploadExerciseToBeConflicted), FileUploadExercise.class, HttpStatus.CONFLICT);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testReEvaluateAndUpdateFileUploadExercise_notFound() throws Exception {
        Course course = fileUploadExerciseUtilService.addEnrolledCourseWithThreeFileUploadExercise(TEST_PREFIX);
        FileUploadExercise fileUploadExercise = ExerciseUtilService.findFileUploadExerciseWithTitle(course.getExercises(), "released");

        // Create a DTO with matching non-existent ID (to pass ID match validation, then fail with NOT_FOUND)
        long nonExistentId = 123456789L;
        UpdateFileUploadExerciseDTO dtoWithNonExistentId = createDtoWithCustomId(fileUploadExercise, nonExistentId);

        request.putWithResponseBody("/api/fileupload/file-upload-exercises/" + nonExistentId + "/re-evaluate", dtoWithNonExistentId, FileUploadExercise.class,
                HttpStatus.NOT_FOUND);
    }

    /**
     * Creates a DTO from the exercise but with a custom ID.
     */
    private UpdateFileUploadExerciseDTO createDtoWithCustomId(FileUploadExercise exercise, long customId) {
        UpdateFileUploadExerciseDTO original = UpdateFileUploadExerciseDTO.of(exercise);
        return new UpdateFileUploadExerciseDTO(customId, original.title(), original.channelName(), original.shortName(), original.problemStatement(), original.categories(),
                original.difficulty(), original.maxPoints(), original.bonusPoints(), original.includedInOverallScore(), original.allowComplaintsForAutomaticAssessments(),
                original.presentationScoreEnabled(), original.secondCorrectionEnabled(), original.gradingInstructions(), original.releaseDate(), original.startDate(),
                original.dueDate(), original.assessmentDueDate(), original.exampleSolutionPublicationDate(), original.exampleSolution(), original.filePattern(),
                original.courseId(), original.exerciseGroupId(), original.gradingCriteria(), original.competencyLinks());
    }

    private void assertErrorKey(MockHttpServletResponse response, String expectedErrorKey) throws Exception {
        assertThat(request.getObjectMapper().readTree(response.getContentAsString()).path("errorKey").asString()).isEqualTo(expectedErrorKey);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createFileUploadExercise_setInvalidExampleSolutionPublicationDate_badRequest() throws Exception {
        final var baseTime = ZonedDateTime.now();
        final Course course = fileUploadExerciseUtilService.addEnrolledCourseWithFileUploadExercise(TEST_PREFIX);
        FileUploadExercise fileUploadExercise = fileUploadExerciseRepository.findByCourseIdWithCategories(course.getId()).getFirst();
        fileUploadExercise.setId(null);
        fileUploadExercise.setAssessmentDueDate(null);
        fileUploadExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);

        fileUploadExercise.setReleaseDate(baseTime.plusHours(1));
        fileUploadExercise.setDueDate(baseTime.plusHours(3));
        fileUploadExercise.setExampleSolutionPublicationDate(baseTime.plusHours(2));

        request.postWithResponseBody("/api/fileupload/file-upload-exercises", inputDTO(fileUploadExercise), FileUploadExercise.class, HttpStatus.BAD_REQUEST);

        fileUploadExercise.setReleaseDate(baseTime.plusHours(3));
        fileUploadExercise.setDueDate(null);
        fileUploadExercise.setExampleSolutionPublicationDate(baseTime.plusHours(2));

        request.postWithResponseBody("/api/fileupload/file-upload-exercises", inputDTO(fileUploadExercise), FileUploadExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createFileUploadExercise_setValidExampleSolutionPublicationDate() throws Exception {
        final var baseTime = ZonedDateTime.now();
        final Course course = fileUploadExerciseUtilService.addEnrolledCourseWithFileUploadExercise(TEST_PREFIX);
        FileUploadExercise fileUploadExercise = fileUploadExerciseRepository.findByCourseIdWithCategories(course.getId()).getFirst();
        fileUploadExercise.setId(null);
        fileUploadExercise.setAssessmentDueDate(null);
        fileUploadExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);

        fileUploadExercise.setReleaseDate(baseTime.plusHours(1));
        fileUploadExercise.setDueDate(baseTime.plusHours(2));
        var exampleSolutionPublicationDate = baseTime.plusHours(3);
        fileUploadExercise.setExampleSolutionPublicationDate(exampleSolutionPublicationDate);

        fileUploadExercise.setChannelName("test-" + UUID.randomUUID().toString().substring(0, 4));
        var result = request.postWithResponseBody("/api/fileupload/file-upload-exercises", inputDTO(fileUploadExercise), FileUploadExerciseDTO.class, HttpStatus.CREATED);
        // The response DTO is reloaded from the database, which stores timestamps with millisecond precision, so the
        // sub-millisecond part of the in-memory value does not survive the round trip.
        assertThat(result.exampleSolutionPublicationDate()).isCloseTo(exampleSolutionPublicationDate, within(1, ChronoUnit.MILLIS));

        fileUploadExercise.setIncludedInOverallScore(IncludedInOverallScore.NOT_INCLUDED);
        fileUploadExercise.setReleaseDate(baseTime.plusHours(1));
        fileUploadExercise.setDueDate(baseTime.plusHours(3));
        exampleSolutionPublicationDate = baseTime.plusHours(2);
        fileUploadExercise.setExampleSolutionPublicationDate(exampleSolutionPublicationDate);
        fileUploadExercise.setChannelName("test" + UUID.randomUUID().toString().substring(0, 8));
        result = request.postWithResponseBody("/api/fileupload/file-upload-exercises", inputDTO(fileUploadExercise), FileUploadExerciseDTO.class, HttpStatus.CREATED);
        assertThat(result.exampleSolutionPublicationDate()).isCloseTo(exampleSolutionPublicationDate, within(1, ChronoUnit.MILLIS));

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
            var create = new FileUploadExercise();
            create.setCourse(course);
            create.setTitle("AtlasML FileUpload Create");
            create.setFilePattern("pdf, png");
            create.setMaxPoints(10.0);
            create.setChannelName("atlasml-fileupload-create");
            request.postWithResponseBody("/api/fileupload/file-upload-exercises", inputDTO(create), FileUploadExerciseDTO.class, HttpStatus.CREATED);

            // Update
            FileUploadExercise persisted = fileUploadExerciseRepository.findByCourseIdWithCategories(course.getId()).getFirst();
            persisted.setTitle("AtlasML FileUpload Update");
            request.putWithResponseBody("/api/fileupload/file-upload-exercises/" + persisted.getId() + "?notificationText=x", UpdateFileUploadExerciseDTO.of(persisted),
                    FileUploadExerciseDTO.class, HttpStatus.OK);

            // Delete
            request.delete("/api/fileupload/file-upload-exercises/" + persisted.getId(), HttpStatus.OK);
        }
        finally {
            featureToggleService.disableFeature(Feature.AtlasML);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createFileUploadExercise_invalidPlagiarismDetectionConfig_badRequest() throws Exception {
        courseUtilService.enableMessagingForCourse(course);
        fileUploadExercise.setId(null);
        fileUploadExercise.setTitle("new fileupload exercise with invalid config");
        fileUploadExercise.setFilePattern(creationFilePattern);
        fileUploadExercise.setChannelName("test-channel");

        var config = new PlagiarismDetectionConfig();
        config.setSimilarityThreshold(-1); // invalid: below 0
        config.setMinimumScore(50);
        config.setMinimumSize(50);
        config.setContinuousPlagiarismControlPlagiarismCaseStudentResponsePeriod(7);
        fileUploadExercise.setPlagiarismDetectionConfig(config);

        request.postWithResponseBody("/api/fileupload/file-upload-exercises", inputDTO(fileUploadExercise), FileUploadExercise.class, HttpStatus.BAD_REQUEST);

        // Test invalid minimumScore
        config.setSimilarityThreshold(50);
        config.setMinimumScore(101); // invalid: above 100
        request.postWithResponseBody("/api/fileupload/file-upload-exercises", inputDTO(fileUploadExercise), FileUploadExercise.class, HttpStatus.BAD_REQUEST);

        // Test invalid minimumSize
        config.setMinimumScore(50);
        config.setMinimumSize(-1); // invalid: negative
        request.postWithResponseBody("/api/fileupload/file-upload-exercises", inputDTO(fileUploadExercise), FileUploadExercise.class, HttpStatus.BAD_REQUEST);

        // Test invalid response period
        config.setMinimumSize(50);
        config.setContinuousPlagiarismControlPlagiarismCaseStudentResponsePeriod(5); // invalid: below 7
        request.postWithResponseBody("/api/fileupload/file-upload-exercises", inputDTO(fileUploadExercise), FileUploadExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateFileUploadExercise_invalidPlagiarismDetectionConfig_badRequest() throws Exception {
        Course course = fileUploadExerciseUtilService.addEnrolledCourseWithThreeFileUploadExercise(TEST_PREFIX);
        FileUploadExercise fileUploadExercise = ExerciseUtilService.findFileUploadExerciseWithTitle(course.getExercises(), "released");

        var config = new PlagiarismDetectionConfig();
        config.setSimilarityThreshold(101); // invalid: above 100
        config.setMinimumScore(50);
        config.setMinimumSize(50);
        config.setContinuousPlagiarismControlPlagiarismCaseStudentResponsePeriod(7);
        fileUploadExercise.setPlagiarismDetectionConfig(config);

        request.putWithResponseBody("/api/fileupload/file-upload-exercises/" + fileUploadExercise.getId(), fileUploadExercise, FileUploadExercise.class, HttpStatus.BAD_REQUEST);

        // Test invalid response period upper bound
        config.setSimilarityThreshold(50);
        config.setContinuousPlagiarismControlPlagiarismCaseStudentResponsePeriod(32); // invalid: above 31
        request.putWithResponseBody("/api/fileupload/file-upload-exercises/" + fileUploadExercise.getId(), fileUploadExercise, FileUploadExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importFileUploadExercise_invalidPlagiarismDetectionConfig_badRequest() throws Exception {
        Course course = fileUploadExerciseUtilService.addEnrolledCourseWithFileUploadExercise(TEST_PREFIX);
        Exercise expectedFileUploadExercise = course.getExercises().stream().findFirst().orElseThrow();
        Course course2 = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        expectedFileUploadExercise.setCourse(course2);
        expectedFileUploadExercise.setChannelName("test" + UUID.randomUUID().toString().substring(0, 8));

        var config = new PlagiarismDetectionConfig();
        config.setSimilarityThreshold(50);
        config.setMinimumScore(-5); // invalid: negative
        config.setMinimumSize(50);
        config.setContinuousPlagiarismControlPlagiarismCaseStudentResponsePeriod(7);
        expectedFileUploadExercise.setPlagiarismDetectionConfig(config);

        var sourceExerciseId = expectedFileUploadExercise.getId();
        request.postWithResponseBody("/api/fileupload/file-upload-exercises/import?sourceId=" + sourceExerciseId, inputDTO(expectedFileUploadExercise), FileUploadExercise.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetFileUploadExercise_asStudent_exampleSolutionVisibility() throws Exception {
        testGetFileUploadExercise_exampleSolutionVisibility(true, TEST_PREFIX + "student1");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testImportFileUploadExerciseFromCourseToCourseAsEditorSuccess() throws Exception {
        Course course = fileUploadExerciseUtilService.addEnrolledCourseWithFileUploadExercise(TEST_PREFIX);
        FileUploadExercise expectedFileUploadExercise = (FileUploadExercise) course.getExercises().stream().findFirst().orElseThrow();
        Course course2 = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        courseUtilService.enableMessagingForCourse(course2);
        expectedFileUploadExercise.setCourse(course2);
        expectedFileUploadExercise.setMode(ExerciseMode.TEAM);
        TeamAssignmentConfig teamConfig = new TeamAssignmentConfig();
        teamConfig.setMinTeamSize(2);
        teamConfig.setMaxTeamSize(4);
        expectedFileUploadExercise.setTeamAssignmentConfig(teamConfig);
        String uniqueChannelName = "test" + UUID.randomUUID().toString().substring(0, 8);
        expectedFileUploadExercise.setChannelName(uniqueChannelName);
        fileUploadExercise.setCompetencyLinks(Set.of(new CompetencyExerciseLink(competency, fileUploadExercise, 1)));

        var sourceExerciseId = expectedFileUploadExercise.getId();
        FileUploadExerciseDTO importedFileUploadExerciseDTO = request.postWithResponseBody("/api/fileupload/file-upload-exercises/import?sourceId=" + sourceExerciseId,
                inputDTO(expectedFileUploadExercise), FileUploadExerciseDTO.class, HttpStatus.CREATED);
        FileUploadExercise importedFileUploadExercise = fileUploadExerciseRepository.findByIdElseThrow(importedFileUploadExerciseDTO.id());
        FileUploadExerciseDTO expectedFileUploadExerciseDTO = FileUploadExerciseDTO.of(expectedFileUploadExercise);
        // File upload exercises are always assessed manually
        assertThat(importedFileUploadExerciseDTO.assessmentType()).isEqualTo(AssessmentType.MANUAL);
        assertThat(importedFileUploadExerciseDTO.mode()).isEqualTo(ExerciseMode.TEAM);
        assertThat(importedFileUploadExerciseDTO.teamMode()).isTrue();
        assertThat(importedFileUploadExerciseDTO.teamAssignmentConfig().minTeamSize()).isEqualTo(2);
        assertThat(importedFileUploadExerciseDTO.teamAssignmentConfig().maxTeamSize()).isEqualTo(4);
        assertThat(importedFileUploadExerciseDTO).usingRecursiveComparison().ignoringFields("id", "teamAssignmentConfig.id", "shortName", "releaseDate", "dueDate",
                "assessmentDueDate", "exampleSolutionPublicationDate", "competencyLinks", "assessmentType", "channelName", "gradingCriteria")
                .isEqualTo(expectedFileUploadExerciseDTO);
        assertThat(importedFileUploadExerciseDTO.gradingCriteria()).isNullOrEmpty();
        Channel channelFromDB = channelRepository.findChannelByExerciseId(importedFileUploadExercise.getId());
        assertThat(channelFromDB).isNotNull();
        assertThat(channelFromDB.getName()).isEqualTo(uniqueChannelName);
        verify(competencyProgressApi).updateProgressByLearningObjectAsync(eq(importedFileUploadExercise));
        assertFileUploadExerciseExistsInWeaviate(weaviateService, importedFileUploadExercise);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importFileUploadExerciseFromCourseToExam_forcesIndividualMode() throws Exception {
        Course sourceCourse = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        FileUploadExercise sourceExercise = FileUploadExerciseFactory.generateFileUploadExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(7),
                ZonedDateTime.now().plusDays(14), creationFilePattern, sourceCourse);
        sourceExercise = fileUploadExerciseRepository.save(sourceExercise);
        ExerciseGroup targetExerciseGroup = examUtilService.addEnrolledExerciseGroupWithExamAndCourse(true, TEST_PREFIX);

        sourceExercise.setCourse(null);
        sourceExercise.setExerciseGroup(targetExerciseGroup);
        sourceExercise.setReleaseDate(null);
        sourceExercise.setDueDate(null);
        sourceExercise.setAssessmentDueDate(null);
        sourceExercise.setMode(ExerciseMode.TEAM);
        TeamAssignmentConfig teamAssignmentConfig = new TeamAssignmentConfig();
        teamAssignmentConfig.setMinTeamSize(2);
        teamAssignmentConfig.setMaxTeamSize(4);
        sourceExercise.setTeamAssignmentConfig(teamAssignmentConfig);

        FileUploadExerciseDTO importedExercise = request.postWithResponseBody("/api/fileupload/file-upload-exercises/import?sourceId=" + sourceExercise.getId(),
                inputDTO(sourceExercise), FileUploadExerciseDTO.class, HttpStatus.CREATED);

        assertThat(importedExercise.course()).isNull();
        assertThat(importedExercise.exerciseGroup()).isNotNull();
        assertThat(importedExercise.exerciseGroup().id()).isEqualTo(targetExerciseGroup.getId());
        assertThat(importedExercise.mode()).isEqualTo(ExerciseMode.INDIVIDUAL);
        assertThat(importedExercise.teamMode()).isFalse();
        assertThat(importedExercise.teamAssignmentConfig()).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testImportFileUploadExerciseFromCourseToCourseNegativeCourseIdBadRequest() throws Exception {
        Course course = fileUploadExerciseUtilService.addEnrolledCourseWithFileUploadExercise(TEST_PREFIX);
        Exercise expectedFileUploadExercise = course.getExercises().stream().findFirst().orElseThrow();
        Course course2 = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        expectedFileUploadExercise.setCourse(course2);
        request.postWithResponseBody("/api/fileupload/file-upload-exercises/import?sourceId=" + -1, inputDTO(expectedFileUploadExercise), FileUploadExercise.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testImportFileUploadExerciseCourseNotSetBadRequest() throws Exception {
        Course course = fileUploadExerciseUtilService.addEnrolledCourseWithFileUploadExercise(TEST_PREFIX);
        Exercise expectedFileUploadExercise = course.getExercises().stream().findFirst().orElseThrow();
        expectedFileUploadExercise.setCourse(null);
        request.postWithResponseBody("/api/fileupload/file-upload-exercises/import?sourceId=" + expectedFileUploadExercise.getId(), inputDTO(expectedFileUploadExercise),
                FileUploadExercise.class, HttpStatus.BAD_REQUEST);

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testGetAllExercisesOnPageAsEditorSuccess() throws Exception {
        final Course course = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        final var now = ZonedDateTime.now();
        FileUploadExercise exercise = FileUploadExerciseFactory.generateFileUploadExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), "pdf", course);
        String title = TEST_PREFIX + "testGetAllExercisesOnPageAsEditorSuccess";
        exercise.setTitle(title);
        exercise = fileUploadExerciseRepository.save(exercise);
        final var searchTerm = pageableSearchUtilService.configureSearch(exercise.getTitle());
        SearchResultPageDTO<FileUploadExerciseDTO> result = request.getSearchResult("/api/fileupload/file-upload-exercises", HttpStatus.OK, FileUploadExerciseDTO.class,
                pageableSearchUtilService.searchMapping(searchTerm));
        assertThat(result.getResultsOnPage()).hasSize(1);
        assertThat(result.getNumberOfPages()).isEqualTo(1);
        assertThat(result.getResultsOnPage().getFirst().course()).isNotNull();
        assertThat(result.getResultsOnPage().getFirst().course().title()).isEqualTo(course.getTitle());

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void searchExamFileUploadExercise_returnsExamContext() throws Exception {
        ExerciseGroup exerciseGroup = examUtilService.addEnrolledExerciseGroupWithExamAndCourse(true, TEST_PREFIX);
        FileUploadExercise exercise = FileUploadExerciseFactory.generateFileUploadExerciseForExam(creationFilePattern, exerciseGroup);
        String title = TEST_PREFIX + "searchExamFileUploadExercise";
        exercise.setTitle(title);
        fileUploadExerciseRepository.save(exercise);

        var searchTerm = pageableSearchUtilService.configureSearch(title);
        SearchResultPageDTO<FileUploadExerciseDTO> result = request.getSearchResult("/api/fileupload/file-upload-exercises", HttpStatus.OK, FileUploadExerciseDTO.class,
                pageableSearchUtilService.searchMapping(searchTerm));

        assertThat(result.getResultsOnPage()).singleElement().satisfies(responseExercise -> {
            assertThat(responseExercise.course()).isNull();
            assertThat(responseExercise.exerciseGroup()).isNotNull();
            assertThat(responseExercise.exerciseGroup().id()).isEqualTo(exerciseGroup.getId());
            assertThat(responseExercise.exerciseGroup().exam()).isNotNull();
            assertThat(responseExercise.exerciseGroup().exam().id()).isEqualTo(exerciseGroup.getExam().getId());
            assertThat(responseExercise.exerciseGroup().exam().title()).isEqualTo(exerciseGroup.getExam().getTitle());
            assertThat(responseExercise.exerciseGroup().exam().course()).isNotNull();
            assertThat(responseExercise.exerciseGroup().exam().course().id()).isEqualTo(exerciseGroup.getExam().getCourse().getId());
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "ta1", roles = "TA")
    void testImportFileUploadExerciseAsTeachingAssistantFails() throws Exception {
        Course course = fileUploadExerciseUtilService.addEnrolledCourseWithFileUploadExercise(TEST_PREFIX);
        Exercise expectedFileUploadExercise = course.getExercises().stream().findFirst().orElseThrow();
        var sourceExerciseId = expectedFileUploadExercise.getId();
        request.postWithResponseBody("/api/fileupload/file-upload-exercises/import?sourceId=" + sourceExerciseId, inputDTO(expectedFileUploadExercise), FileUploadExercise.class,
                HttpStatus.FORBIDDEN);

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testExamExerciseNotIncludedInScoreReturnsBadRequest() throws Exception {
        FileUploadExercise fileUploadExercise = fileUploadExerciseUtilService.addEnrolledCourseExamExerciseGroupWithOneFileUploadExercise(TEST_PREFIX, false);
        fileUploadExercise.setIncludedInOverallScore(IncludedInOverallScore.NOT_INCLUDED);
        request.postWithResponseBody("/api/fileupload/file-upload-exercises/import?sourceId=" + fileUploadExercise.getId(), inputDTO(fileUploadExercise), FileUploadExercise.class,
                HttpStatus.BAD_REQUEST);

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetFileUploadExercise_asInstructor_exampleSolutionVisibility() throws Exception {
        testGetFileUploadExercise_exampleSolutionVisibility(false, TEST_PREFIX + "instructor1");
    }

    private void testGetFileUploadExercise_exampleSolutionVisibility(boolean isStudent, String username) throws Exception {
        Course course = fileUploadExerciseUtilService.addEnrolledCourseWithThreeFileUploadExercise(TEST_PREFIX);
        final FileUploadExercise fileUploadExercise = fileUploadExerciseRepository.findByCourseIdWithCategories(course.getId()).getFirst();

        // Utility function to avoid duplication
        Function<Course, FileUploadExercise> fileUploadExerciseGetter = c -> (FileUploadExercise) c.getExercises().stream()
                .filter(e -> e.getId().equals(fileUploadExercise.getId())).findAny().orElseThrow();

        fileUploadExercise.setExampleSolution("Sample<br>solution");

        if (isStudent) {
            participationUtilService.createAndSaveParticipationForExercise(fileUploadExercise, username);
        }

        // Test example solution publication date not set.
        fileUploadExercise.setExampleSolutionPublicationDate(null);
        fileUploadExerciseRepository.save(fileUploadExercise);

        CourseForDashboardDTO courseForDashboard = request.get("/api/course/courses/" + fileUploadExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/for-dashboard",
                HttpStatus.OK, CourseForDashboardDTO.class);
        course = courseForDashboard.course();
        FileUploadExercise fileUploadExerciseFromApi = fileUploadExerciseGetter.apply(course);

        if (isStudent) {
            assertThat(fileUploadExerciseFromApi.getExampleSolution()).isNull();
        }
        else {
            assertThat(fileUploadExerciseFromApi.getExampleSolution()).isEqualTo(fileUploadExercise.getExampleSolution());
        }

        // Test example solution publication date in the past.
        fileUploadExercise.setExampleSolutionPublicationDate(ZonedDateTime.now().minusHours(1));
        fileUploadExerciseRepository.save(fileUploadExercise);

        courseForDashboard = request.get("/api/course/courses/" + fileUploadExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/for-dashboard", HttpStatus.OK,
                CourseForDashboardDTO.class);
        course = courseForDashboard.course();
        fileUploadExerciseFromApi = fileUploadExerciseGetter.apply(course);

        assertThat(fileUploadExerciseFromApi.getExampleSolution()).isEqualTo(fileUploadExercise.getExampleSolution());

        // Test example solution publication date in the future.
        fileUploadExercise.setExampleSolutionPublicationDate(ZonedDateTime.now().plusHours(1));
        fileUploadExerciseRepository.save(fileUploadExercise);

        courseForDashboard = request.get("/api/course/courses/" + fileUploadExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/for-dashboard", HttpStatus.OK,
                CourseForDashboardDTO.class);
        course = courseForDashboard.course();
        fileUploadExerciseFromApi = fileUploadExerciseGetter.apply(course);

        if (isStudent) {
            assertThat(fileUploadExerciseFromApi.getExampleSolution()).isNull();
        }
        else {
            assertThat(fileUploadExerciseFromApi.getExampleSolution()).isEqualTo(fileUploadExercise.getExampleSolution());
        }
    }
}
