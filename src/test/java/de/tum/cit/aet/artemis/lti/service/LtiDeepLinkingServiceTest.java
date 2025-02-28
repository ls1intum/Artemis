package de.tum.cit.aet.artemis.lti.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.anyMap;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseTestRepository;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;
import de.tum.cit.aet.artemis.lti.config.Lti13TokenRetriever;
import de.tum.cit.aet.artemis.lti.domain.OnlineCourseConfiguration;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import uk.ac.ox.ctl.lti13.lti.Claims;

class LtiDeepLinkingServiceTest {

    @Mock
    private CourseTestRepository courseRepository;

    @Mock
    private ExerciseTestRepository exerciseRepository;

    @Mock
    LectureRepository lectureRepository;

    @Mock
    private Lti13TokenRetriever tokenRetriever;

    private LtiDeepLinkingService ltiDeepLinkingService;

    private AutoCloseable closeable;

    private OidcIdToken oidcIdToken;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        oidcIdToken = mock(OidcIdToken.class);
        SecurityContextHolder.clearContext();
        ltiDeepLinkingService = new LtiDeepLinkingService(courseRepository, exerciseRepository, lectureRepository, tokenRetriever);
        ReflectionTestUtils.setField(ltiDeepLinkingService, "artemisServerUrl", "http://artemis.com");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (closeable != null) {
            closeable.close();
        }
    }

    @Test
    void testPerformDeepLinking() throws MalformedURLException, URISyntaxException {
        createMockOidcIdToken();
        when(tokenRetriever.createDeepLinkingJWT(anyString(), anyMap())).thenReturn("test_jwt");

        DeepLinkCourseExercises result = createTestExercisesForDeepLinking();
        String deepLinkResponse = ltiDeepLinkingService.performDeepLinking(oidcIdToken, "test_registration_id", result.courseId(), result.exerciseSet(), DeepLinkingType.EXERCISE);

        assertThat(deepLinkResponse).isNotNull();
        assertThat(deepLinkResponse).contains("test_jwt");
    }

    @Test
    void testEmptyJwtBuildLtiDeepLinkResponse() throws MalformedURLException, URISyntaxException {
        createMockOidcIdToken();
        when(tokenRetriever.createDeepLinkingJWT(anyString(), anyMap())).thenReturn(null);

        DeepLinkCourseExercises result = createTestExercisesForDeepLinking();
        assertThatExceptionOfType(BadRequestAlertException.class)
                .isThrownBy(() -> ltiDeepLinkingService.performDeepLinking(oidcIdToken, "test_registration_id", result.courseId, result.exerciseSet, DeepLinkingType.EXERCISE))
                .withMessage("Deep linking response cannot be created")
                .matches(exception -> "LTI".equals(exception.getEntityName()) && "deepLinkingResponseFailed".equals(exception.getErrorKey()));
    }

    @Test
    void testEmptyReturnUrlBuildLtiDeepLinkResponse() throws JsonProcessingException, MalformedURLException, URISyntaxException {
        createMockOidcIdToken();
        when(tokenRetriever.createDeepLinkingJWT(anyString(), anyMap())).thenReturn("test_jwt");
        ObjectMapper mapper = new ObjectMapper();
        String deepLinkingSettingsAsJsonString = """
                {
                  "deep_link_return_url": "",
                  "accept_types": [
                    "link",
                    "file",
                    "html",
                    "ltiResourceLink",
                    "image"
                  ],
                  "accept_media_types": "image/*,text/html",
                  "accept_presentation_document_targets": [
                    "iframe",
                    "window",
                    "embed"
                  ],
                  "accept_multiple": true,
                  "auto_create": true,
                  "title": "This is the default title",
                  "text": "This is the default text",
                  "data": "csrftoken:c7fbba78-7b75-46e3-9201-11e6d5f36f53"
                }
                """;
        Map<String, Object> deepLinkingSettingsAsMap = mapper.readValue(deepLinkingSettingsAsJsonString, new TypeReference<>() {
        });
        when(oidcIdToken.getClaim(de.tum.cit.aet.artemis.lti.dto.Claims.DEEP_LINKING_SETTINGS)).thenReturn(deepLinkingSettingsAsMap);

        DeepLinkCourseExercises result = createTestExercisesForDeepLinking();
        assertThatExceptionOfType(BadRequestAlertException.class)
                .isThrownBy(() -> ltiDeepLinkingService.performDeepLinking(oidcIdToken, "test_registration_id", result.courseId, result.exerciseSet, DeepLinkingType.EXERCISE))
                .withMessage("Cannot find platform return URL")
                .matches(exception -> "LTI".equals(exception.getEntityName()) && "deepLinkReturnURLEmpty".equals(exception.getErrorKey()));
    }

    @Test
    void testEmptyDeploymentIdBuildLtiDeepLinkResponse() throws MalformedURLException, URISyntaxException {
        createMockOidcIdToken();
        when(tokenRetriever.createDeepLinkingJWT(anyString(), anyMap())).thenReturn("test_jwt");
        when(oidcIdToken.getClaim(de.tum.cit.aet.artemis.lti.dto.Claims.LTI_DEPLOYMENT_ID)).thenReturn(null);

        DeepLinkCourseExercises result = createTestExercisesForDeepLinking();
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> ltiDeepLinkingService.performDeepLinking(oidcIdToken, "test_registration_id", result.courseId, result.exerciseSet, DeepLinkingType.EXERCISE))
                .withMessage("Missing claim: " + Claims.LTI_DEPLOYMENT_ID);
    }

    @Test
    void testMissingCompetencyThrowsException() throws MalformedURLException, URISyntaxException {
        createMockOidcIdToken();
        when(courseRepository.findWithEagerCompetenciesAndPrerequisitesById(anyLong())).thenReturn(Optional.empty());

        assertThatExceptionOfType(BadRequestAlertException.class)
                .isThrownBy(() -> ltiDeepLinkingService.performDeepLinking(oidcIdToken, "test_registration_id", 1L, null, DeepLinkingType.COMPETENCY))
                .withMessage("No competencies found.").matches(exception -> "LTI".equals(exception.getEntityName()) && "CompetenciesNotFound".equals(exception.getErrorKey()));
    }

    @Test
    void testMissingLearningPathThrowsException() throws MalformedURLException, URISyntaxException {
        createMockOidcIdToken();
        Course course = createMockCourse();
        course.setLearningPathsEnabled(false);
        when(courseRepository.findWithEagerLearningPathsAndLearningPathCompetenciesByIdElseThrow(anyLong())).thenReturn(course);

        assertThatExceptionOfType(BadRequestAlertException.class)
                .isThrownBy(() -> ltiDeepLinkingService.performDeepLinking(oidcIdToken, "test_registration_id", 1L, null, DeepLinkingType.LEARNING_PATH))
                .withMessage("No learning paths found.").matches(exception -> "LTI".equals(exception.getEntityName()) && "learningPathsNotFound".equals(exception.getErrorKey()));
    }

    @Test
    void testMissingIrisDashboardThrowsException() throws MalformedURLException, URISyntaxException {
        createMockOidcIdToken();
        Course course = createMockCourse();
        course.setStudentCourseAnalyticsDashboardEnabled(false);
        when(courseRepository.findById(anyLong())).thenReturn(Optional.of(course));

        assertThatExceptionOfType(BadRequestAlertException.class)
                .isThrownBy(() -> ltiDeepLinkingService.performDeepLinking(oidcIdToken, "test_registration_id", 1L, null, DeepLinkingType.IRIS))
                .withMessage("Course Analytics Dashboard not activated")
                .matches(exception -> "LTI".equals(exception.getEntityName()) && "noCourseAnalyticsDashboard".equals(exception.getErrorKey()));
    }

    @Test
    void testPerformDeepLinkingWithUnknownType() throws MalformedURLException, URISyntaxException {
        createMockOidcIdToken();
        DeepLinkCourseExercises result = createTestExercisesForDeepLinking();

        assertThatExceptionOfType(BadRequestAlertException.class)
                .isThrownBy(() -> ltiDeepLinkingService.performDeepLinking(oidcIdToken, "test_registration_id", result.courseId(), result.exerciseSet(), DeepLinkingType.UNKNOWN))
                .withMessage("Invalid deep linking type provided").matches(exception -> "LTI".equals(exception.getEntityName()) && "invalidType".equals(exception.getErrorKey()));
    }

    @Test
    void testPerformDeepLinkingWithLectureType() throws MalformedURLException, URISyntaxException {
        createMockOidcIdToken();
        when(tokenRetriever.createDeepLinkingJWT(anyString(), anyMap())).thenReturn("test_jwt");

        long courseId = 1L;
        long lectureId = 2L;
        Lecture lecture = new Lecture();
        lecture.setId(lectureId);
        lecture.setTitle("Test Lecture");
        when(lectureRepository.findById(lectureId)).thenReturn(Optional.of(lecture));

        Set<Long> lectureIds = new HashSet<>();
        lectureIds.add(lectureId);

        String deepLinkResponse = ltiDeepLinkingService.performDeepLinking(oidcIdToken, "test_registration_id", courseId, lectureIds, DeepLinkingType.LECTURE);

        assertThat(deepLinkResponse).isNotNull();
        assertThat(deepLinkResponse).contains("test_jwt");
    }

    @Test
    void testPerformDeepLinkingWithCompetencyType() throws MalformedURLException, URISyntaxException {
        createMockOidcIdToken();
        when(tokenRetriever.createDeepLinkingJWT(anyString(), anyMap())).thenReturn("test_jwt");

        Course course = createMockCourse();
        Competency competency = new Competency();
        course.setCompetencies(Set.of(competency));
        when(courseRepository.findWithEagerCompetenciesAndPrerequisitesById(course.getId())).thenReturn(Optional.of(course));

        String deepLinkResponse = ltiDeepLinkingService.performDeepLinking(oidcIdToken, "test_registration_id", course.getId(), null, DeepLinkingType.COMPETENCY);

        assertThat(deepLinkResponse).isNotNull();
        assertThat(deepLinkResponse).contains("test_jwt");
    }

    @Test
    void testPerformDeepLinkingWithLearningPathType() throws MalformedURLException, URISyntaxException {
        createMockOidcIdToken();
        when(tokenRetriever.createDeepLinkingJWT(anyString(), anyMap())).thenReturn("test_jwt");

        Course course = createMockCourse();
        course.setLearningPathsEnabled(true);
        when(courseRepository.findWithEagerLearningPathsAndLearningPathCompetenciesByIdElseThrow(course.getId())).thenReturn(course);

        String deepLinkResponse = ltiDeepLinkingService.performDeepLinking(oidcIdToken, "test_registration_id", course.getId(), null, DeepLinkingType.LEARNING_PATH);

        assertThat(deepLinkResponse).isNotNull();
        assertThat(deepLinkResponse).contains("test_jwt");
    }

    @Test
    void testPerformDeepLinkingWithIrisType() throws MalformedURLException, URISyntaxException {
        createMockOidcIdToken();
        when(tokenRetriever.createDeepLinkingJWT(anyString(), anyMap())).thenReturn("test_jwt");

        Course course = createMockCourse();

        course.setStudentCourseAnalyticsDashboardEnabled(true);
        when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));

        String deepLinkResponse = ltiDeepLinkingService.performDeepLinking(oidcIdToken, "test_registration_id", course.getId(), null, DeepLinkingType.IRIS);

        assertThat(deepLinkResponse).isNotNull();
        assertThat(deepLinkResponse).contains("test_jwt");
    }

    @Test
    void testPerformDeepLinkingWithInvalidCourseId() throws MalformedURLException, URISyntaxException {
        createMockOidcIdToken();
        when(courseRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThatExceptionOfType(BadRequestAlertException.class)
                .isThrownBy(() -> ltiDeepLinkingService.performDeepLinking(oidcIdToken, "test_registration_id", 1L, null, DeepLinkingType.IRIS))
                .withMessage("Course Analytics Dashboard not activated")
                .matches(exception -> "LTI".equals(exception.getEntityName()) && "noCourseAnalyticsDashboard".equals(exception.getErrorKey()));
    }

    private void createMockOidcIdToken() throws MalformedURLException, URISyntaxException {
        Map<String, Object> mockSettings = new TreeMap<>();
        mockSettings.put("deep_link_return_url", "test_return_url");
        when(oidcIdToken.getClaim(Claims.DEEP_LINKING_SETTINGS)).thenReturn(mockSettings);
        when(oidcIdToken.getClaim("iss")).thenReturn("http://artemis.com");
        when(oidcIdToken.getClaim("aud")).thenReturn("http://moodle.com");
        when(oidcIdToken.getClaim("exp")).thenReturn("12345");
        when(oidcIdToken.getClaim("iat")).thenReturn("test");
        when(oidcIdToken.getClaim("nonce")).thenReturn("1234-34535-abcbcbd");
        when(oidcIdToken.getIssuer()).thenReturn(new URI("http://artemis.com").toURL());
        when(oidcIdToken.getAudience()).thenReturn(Arrays.asList("http://moodle.com"));
        when(oidcIdToken.getExpiresAt()).thenReturn(Instant.now().plus(2, ChronoUnit.HOURS));
        when(oidcIdToken.getIssuedAt()).thenReturn(Instant.now());
        when(oidcIdToken.getNonce()).thenReturn("1234-34535-abcbcbd");
        when(oidcIdToken.getClaim(Claims.LTI_DEPLOYMENT_ID)).thenReturn("1");
        when(oidcIdToken.getClaimAsString(Claims.LTI_DEPLOYMENT_ID)).thenReturn("1");
    }

    private Exercise createMockExercise(long exerciseId, long courseId) {
        Exercise exercise = new TextExercise();
        exercise.setTitle("test_title");
        exercise.setId(exerciseId);

        Course course = new Course();
        course.setId(courseId);
        course.setOnlineCourseConfiguration(new OnlineCourseConfiguration());
        exercise.setCourse(course);
        return exercise;
    }

    private Course createMockCourse() {
        Course course = new Course();
        course.setId(1L);
        return course;
    }

    private DeepLinkCourseExercises createTestExercisesForDeepLinking() {
        long exerciseId = 3;
        long courseId = 17;
        Exercise exercise = createMockExercise(exerciseId, courseId);
        when(exerciseRepository.findById(exerciseId)).thenReturn(Optional.of(exercise));

        Set<Long> exerciseSet = new HashSet<>();
        exerciseSet.add(exerciseId);
        return new DeepLinkCourseExercises(courseId, exerciseSet);
    }

    private record DeepLinkCourseExercises(long courseId, Set<Long> exerciseSet) {

    }
}
