package de.tum.cit.aet.artemis.lti.service;

import static de.tum.cit.aet.artemis.core.config.Constants.LTI_ENABLED_PROPERTY_NAME;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.lecture.api.LectureRepositoryApi;
import de.tum.cit.aet.artemis.lecture.config.LectureApiNotPresentException;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lti.config.Lti13TokenRetriever;
import de.tum.cit.aet.artemis.lti.dto.LineItem;
import de.tum.cit.aet.artemis.lti.dto.Lti13DeepLinkingResponse;
import de.tum.cit.aet.artemis.lti.dto.LtiContentItem;

/**
 * Service for handling LTI deep linking functionality.
 * This includes building and returning appropriate LTI launch URLs
 * for various Artemis content types such as exercises, lectures, competencies, etc.
 */
@Lazy
@Service
@ConditionalOnProperty(value = LTI_ENABLED_PROPERTY_NAME, havingValue = "true")
public class LtiDeepLinkingService {

    @Value("${server.url}")
    private String artemisServerUrl;

    private static final double DEFAULT_SCORE_MAXIMUM = 100D;

    private final CourseRepository courseRepository;

    private final ExerciseRepository exerciseRepository;

    private final Optional<LectureRepositoryApi> lectureRepositoryApi;

    private final Lti13TokenRetriever tokenRetriever;

    public LtiDeepLinkingService(CourseRepository courseRepository, ExerciseRepository exerciseRepository, Optional<LectureRepositoryApi> lectureRepositoryApi,
            Lti13TokenRetriever tokenRetriever) {
        this.courseRepository = courseRepository;
        this.exerciseRepository = exerciseRepository;
        this.lectureRepositoryApi = lectureRepositoryApi;
        this.tokenRetriever = tokenRetriever;
    }

    /**
     * Constructs an LTI Deep Linking response URL with JWT for the specified course and exercise.
     *
     * @param ltiIdToken           OIDC ID token with the user's authentication claims.
     * @param clientRegistrationId Client registration ID for the LTI tool.
     * @param courseId             ID of the course for deep linking.
     * @param unitIds              Set of IDs of the exercises/lectures for deep linking.
     * @param type                 The type of deep linking (exercise, lecture, competency, iris, learning path).
     * @return Constructed deep linking response URL.
     * @throws BadRequestAlertException if there are issues with the OIDC ID token claims.
     */
    public String performDeepLinking(OidcIdToken ltiIdToken, String clientRegistrationId, long courseId, Set<Long> unitIds, DeepLinkingType type) {
        Lti13DeepLinkingResponse lti13DeepLinkingResponse = Lti13DeepLinkingResponse.from(ltiIdToken, clientRegistrationId);

        List<LtiContentItem> contentItems = switch (type) {
            case EXERCISE -> populateExerciseContentItems(courseId, unitIds);
            case GROUPED_EXERCISE -> List.of(populateGroupedExerciseContentItem(courseId, unitIds));
            case LECTURE -> populateLectureContentItems(courseId, unitIds);
            case GROUPED_LECTURE -> List.of(populateGroupedLectureContentItems(courseId, unitIds));
            case COMPETENCY -> populateCompetencyContentItems(courseId);
            case IRIS -> populateIrisContentItems(courseId);
            case LEARNING_PATH -> populateLearningPathsContentItems(courseId);
        };

        List<Map<String, Object>> contentItemsMap = contentItems.stream().map(LtiContentItem::toMap).toList();

        lti13DeepLinkingResponse = lti13DeepLinkingResponse.setContentItems(contentItemsMap);
        return buildLtiDeepLinkResponse(clientRegistrationId, lti13DeepLinkingResponse);
    }

    /**
     * Creates the deep linking launch URL that includes encoded JWT and parameters required by the LTI platform.
     *
     * @param clientRegistrationId     Registration ID of the LTI client.
     * @param lti13DeepLinkingResponse Object holding the LTI claims and return URL.
     * @return Final URL to be sent back to the LTI platform for launching.
     */
    private String buildLtiDeepLinkResponse(String clientRegistrationId, Lti13DeepLinkingResponse lti13DeepLinkingResponse) {
        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromUriString(this.artemisServerUrl + "/lti/select-content");

        String jwt = tokenRetriever.createDeepLinkingJWT(clientRegistrationId, lti13DeepLinkingResponse.getClaims());
        String returnUrl = lti13DeepLinkingResponse.returnUrl();

        validateDeepLinkingResponseSettings(returnUrl, jwt, lti13DeepLinkingResponse.deploymentId());

        uriComponentsBuilder.queryParam("jwt", jwt);
        uriComponentsBuilder.queryParam("id", lti13DeepLinkingResponse.deploymentId());
        uriComponentsBuilder.queryParam("deepLinkUri", URLEncoder.encode(returnUrl, StandardCharsets.UTF_8));

        return uriComponentsBuilder.build().toUriString();
    }

    /**
     * Maps each exercise ID to an individual LTI content item.
     */
    private List<LtiContentItem> populateExerciseContentItems(long courseId, Set<Long> exerciseIds) {
        validateUnitIds(exerciseIds, DeepLinkingType.EXERCISE);
        return exerciseIds.stream().map(exerciseId -> setExerciseContentItem(courseId, String.valueOf(exerciseId))).toList();
    }

    /**
     * Groups a set of exercises into one content item.
     */
    private LtiContentItem populateGroupedExerciseContentItem(long courseId, Set<Long> exerciseIds) {
        validateUnitIds(exerciseIds, DeepLinkingType.GROUPED_EXERCISE);
        return setGroupedExerciseContentItem(courseId, exerciseIds);
    }

    /**
     * Maps each lecture ID to an individual LTI content item.
     */
    private List<LtiContentItem> populateLectureContentItems(long courseId, Set<Long> lectureIds) {
        validateUnitIds(lectureIds, DeepLinkingType.LECTURE);
        return lectureIds.stream().map(lectureId -> setLectureContentItem(courseId, String.valueOf(lectureId))).toList();
    }

    /**
     * Groups a set of lectures into one content item.
     */
    private LtiContentItem populateGroupedLectureContentItems(long courseId, Set<Long> lectureIds) {
        validateUnitIds(lectureIds, DeepLinkingType.GROUPED_LECTURE);
        return setGroupedLectureContentItem(courseId, lectureIds);
    }

    /**
     * Prepares a content item pointing to the first available competency in the course.
     */
    private List<LtiContentItem> populateCompetencyContentItems(long courseId) {
        Optional<Competency> competencyOpt = courseRepository.findWithEagerCompetenciesAndPrerequisitesById(courseId)
                .flatMap(course -> course.getCompetencies().stream().findFirst());
        String launchUrl = buildContentUrl(courseId, "competencies");
        return List.of(competencyOpt.map(competency -> createSingleUnitContentItem(launchUrl))
                .orElseThrow(() -> new BadRequestAlertException("No competencies found.", "LTI", "CompetenciesNotFound")));
    }

    /**
     * Prepares a content item for launching the Iris analytics dashboard.
     */
    private List<LtiContentItem> populateIrisContentItems(long courseId) {
        Optional<Course> courseOpt = courseRepository.findById(courseId);
        if (courseOpt.isPresent() && courseOpt.get().getStudentCourseAnalyticsDashboardEnabled()) {
            String launchUrl = buildContentUrl(courseId, "dashboard");
            return List.of(createSingleUnitContentItem(launchUrl));
        }
        else {
            throw new BadRequestAlertException("Course Analytics Dashboard not activated", "LTI", "noCourseAnalyticsDashboard");
        }
    }

    /**
     * Prepares a content item pointing to the learning path of the course.
     */
    private List<LtiContentItem> populateLearningPathsContentItems(long courseId) {
        boolean hasLearningPaths = courseRepository.findByIdElseThrow(courseId).getLearningPathsEnabled();
        if (hasLearningPaths) {
            String launchUrl = buildContentUrl(courseId, "learning-path");
            return List.of(createSingleUnitContentItem(launchUrl));
        }
        else {
            throw new BadRequestAlertException("No learning paths found.", "LTI", "learningPathsNotFound");
        }
    }

    /**
     * Create a content item for a specific exercise.
     */
    private LtiContentItem setExerciseContentItem(long courseId, String exerciseId) {
        Optional<Exercise> exerciseOpt = exerciseRepository.findById(Long.valueOf(exerciseId));
        String launchUrl = buildContentUrl(courseId, "exercises", exerciseId);
        return exerciseOpt.map(exercise -> createExerciseContentItem(exercise, launchUrl))
                .orElseThrow(() -> new BadRequestAlertException("Exercise not found.", "LTI", "exerciseNotFound"));
    }

    /**
     * Create a content item for a group of exercises.
     */
    private LtiContentItem setGroupedExerciseContentItem(long courseId, Set<Long> exerciseIds) {

        List<Exercise> exercises = exerciseRepository.findAllById(exerciseIds);

        if (exercises.isEmpty()) {
            throw new BadRequestAlertException("No exercises found.", "LTI", "exercisesNotFound");
        }
        String launchUrl = buildGroupedResourceUrl(courseId, exercises.stream().map(Exercise::getId).collect(Collectors.toSet()), "exercises", "exerciseIDs", "noExerciseIds");
        return createGroupedExerciseContentItem(exercises, launchUrl);
    }

    /**
     * Create a content item for a specific lecture.
     */
    private LtiContentItem setLectureContentItem(long courseId, String lectureId) {
        LectureRepositoryApi api = lectureRepositoryApi.orElseThrow(() -> new LectureApiNotPresentException(LectureRepositoryApi.class));
        String launchUrl = buildContentUrl(courseId, "lectures", lectureId);
        return api.findById(Long.valueOf(lectureId)).map(lecture -> createLectureContentItem(lecture, launchUrl))
                .orElseThrow(() -> new BadRequestAlertException("Lecture not found.", "LTI", "lectureNotFound"));
    }

    /**
     * Create a content item for a group of lectures.
     */
    private LtiContentItem setGroupedLectureContentItem(long courseId, Set<Long> lectureIds) {

        List<Lecture> lectures = lectureRepositoryApi.map(api -> api.findAllById(lectureIds)).orElse(List.of());

        if (lectures.isEmpty()) {
            throw new BadRequestAlertException("No lectures found.", "LTI", "lecturesNotFound");
        }
        String launchUrl = buildGroupedResourceUrl(courseId, lectures.stream().map(Lecture::getId).collect(Collectors.toSet()), "lectures", "lectureIDs", "noLectureIds");
        return createGroupedLectureContentItem(launchUrl);
    }

    private LtiContentItem createExerciseContentItem(Exercise exercise, String url) {
        LineItem lineItem = exercise.getIncludedInOverallScore() != IncludedInOverallScore.NOT_INCLUDED ? new LineItem(DEFAULT_SCORE_MAXIMUM) : null;
        return new LtiContentItem("ltiResourceLink", exercise.getTitle(), url, lineItem);
    }

    private LtiContentItem createGroupedExerciseContentItem(List<Exercise> exercises, String url) {
        LineItem lineItem = exercises.stream().anyMatch(exercise -> exercise.getIncludedInOverallScore() != IncludedInOverallScore.NOT_INCLUDED)
                ? new LineItem(DEFAULT_SCORE_MAXIMUM)
                : null;
        return new LtiContentItem("ltiResourceLink", "Grouped Exercises", url, lineItem);
    }

    private LtiContentItem createLectureContentItem(Lecture lecture, String url) {
        return new LtiContentItem("ltiResourceLink", lecture.getTitle(), url, null);
    }

    private LtiContentItem createGroupedLectureContentItem(String url) {
        return new LtiContentItem("ltiResourceLink", "Grouped Lectures", url, null);
    }

    /**
     * Create a content item for a single unit (e.g., competency, learning path, Iris).
     */
    private LtiContentItem createSingleUnitContentItem(String url) {
        return new LtiContentItem("ltiResourceLink", "ltiContentItem", url, null);
    }

    /**
     * Validate that unit IDs are provided for deep linking.
     */
    private void validateUnitIds(Set<Long> unitIds, DeepLinkingType type) {
        if (unitIds == null || unitIds.isEmpty()) {
            throw new BadRequestAlertException("No " + type.name().toLowerCase() + " IDs provided for deep linking", "LTI", "no" + type.name() + "Ids");
        }
    }

    /**
     * Build a content URL for deep linking.
     */
    private String buildContentUrl(long courseId, String resourceType, String resourceId) {
        return String.format("%s/courses/%s/%s/%s", artemisServerUrl, courseId, resourceType, resourceId);
    }

    private String buildContentUrl(long courseId, String resourceType) {
        return String.format("%s/courses/%s/%s", artemisServerUrl, courseId, resourceType);
    }

    private String buildGroupedResourceUrl(long courseId, Set<Long> ids, String pathSegment, String queryParamKey, String alertKey) {
        long smallestId = ids.stream().min(Long::compareTo).orElseThrow(() -> new BadRequestAlertException("No IDs provided", "LTI", alertKey));

        String baseUrl = String.format("%s/courses/%s/%s/%d", artemisServerUrl, courseId, pathSegment, smallestId);
        String joinedIds = ids.stream().map(String::valueOf).collect(Collectors.joining(","));

        return UriComponentsBuilder.fromUriString(baseUrl).queryParam("isMultiLaunch", true).queryParam(queryParamKey, joinedIds).toUriString();
    }

    /**
     * Validate deep linking response settings.
     */
    private void validateDeepLinkingResponseSettings(String returnURL, String jwt, String deploymentId) {
        if (isEmptyString(jwt)) {
            throw new BadRequestAlertException("Deep linking response cannot be created", "LTI", "deepLinkingResponseFailed");
        }
        if (isEmptyString(returnURL)) {
            throw new BadRequestAlertException("Cannot find platform return URL", "LTI", "deepLinkReturnURLEmpty");
        }
        if (isEmptyString(deploymentId)) {
            throw new BadRequestAlertException("Platform deployment id cannot be empty", "LTI", "deploymentIdEmpty");
        }
    }

    private boolean isEmptyString(String string) {
        return string == null || string.isEmpty();
    }
}
