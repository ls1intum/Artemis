package de.tum.cit.aet.artemis.lti.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LTI;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
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
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;
import de.tum.cit.aet.artemis.lti.config.Lti13TokenRetriever;
import de.tum.cit.aet.artemis.lti.dto.LineItem;
import de.tum.cit.aet.artemis.lti.dto.Lti13DeepLinkingResponse;
import de.tum.cit.aet.artemis.lti.dto.LtiContentItem;

/**
 * Service for handling LTI deep linking functionality.
 */
@Service
@Profile(PROFILE_LTI)
public class LtiDeepLinkingService {

    @Value("${server.url}")
    private String artemisServerUrl;

    private static final double DEFAULT_SCORE_MAXIMUM = 100D;

    private final CourseRepository courseRepository;

    private final ExerciseRepository exerciseRepository;

    private final LectureRepository lectureRepository;

    private final Lti13TokenRetriever tokenRetriever;

    public LtiDeepLinkingService(CourseRepository courseRepository, ExerciseRepository exerciseRepository, LectureRepository lectureRepository,
            Lti13TokenRetriever tokenRetriever) {
        this.courseRepository = courseRepository;
        this.exerciseRepository = exerciseRepository;
        this.lectureRepository = lectureRepository;
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
    public String performDeepLinking(OidcIdToken ltiIdToken, String clientRegistrationId, Long courseId, Set<Long> unitIds, DeepLinkingType type) {
        Lti13DeepLinkingResponse lti13DeepLinkingResponse = Lti13DeepLinkingResponse.from(ltiIdToken, clientRegistrationId);

        List<LtiContentItem> contentItems = switch (type) {
            case EXERCISE -> populateExerciseContentItems(String.valueOf(courseId), unitIds);
            case GROUPED_EXERCISE -> List.of(populateGroupedExerciseContentItem(String.valueOf(courseId), unitIds));
            case LECTURE -> populateLectureContentItems(String.valueOf(courseId), unitIds);
            case GROUPED_LECTURE -> List.of(populateGroupedLectureContentItems(String.valueOf(courseId), unitIds));
            case COMPETENCY -> populateCompetencyContentItems(String.valueOf(courseId));
            case IRIS -> populateIrisContentItems(String.valueOf(courseId));
            case LEARNING_PATH -> populateLearningPathsContentItems(String.valueOf(courseId));
        };

        List<Map<String, Object>> contentItemsMap = contentItems.stream().map(LtiContentItem::toMap).toList();

        lti13DeepLinkingResponse = lti13DeepLinkingResponse.setContentItems(contentItemsMap);
        return buildLtiDeepLinkResponse(clientRegistrationId, lti13DeepLinkingResponse);
    }

    /**
     * Build an LTI deep linking response URL.
     *
     * @return The LTI deep link response URL.
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
     * Populate content items for deep linking response with exercises.
     */
    private List<LtiContentItem> populateExerciseContentItems(String courseId, Set<Long> exerciseIds) {
        validateUnitIds(exerciseIds, DeepLinkingType.EXERCISE);
        return exerciseIds.stream().map(exerciseId -> setExerciseContentItem(courseId, String.valueOf(exerciseId))).toList();
    }

    /**
     * Populate a content item for deep linking response with grouped exercises.
     */
    private LtiContentItem populateGroupedExerciseContentItem(String courseId, Set<Long> exerciseIds) {
        validateUnitIds(exerciseIds, DeepLinkingType.GROUPED_EXERCISE);
        return setGroupedExerciseContentItem(courseId, exerciseIds);
    }

    /**
     * Populate content items for deep linking response with lectures.
     */
    private List<LtiContentItem> populateLectureContentItems(String courseId, Set<Long> lectureIds) {
        validateUnitIds(lectureIds, DeepLinkingType.LECTURE);
        return lectureIds.stream().map(lectureId -> setLectureContentItem(courseId, String.valueOf(lectureId))).toList();
    }

    /**
     * Populate a content item for deep linking response with grouped lectures.
     */
    private LtiContentItem populateGroupedLectureContentItems(String courseId, Set<Long> lectureIds) {
        validateUnitIds(lectureIds, DeepLinkingType.GROUPED_LECTURE);
        return setGroupedLectureContentItem(courseId, lectureIds);
    }

    /**
     * Populate content items for deep linking response with competencies.
     */
    private List<LtiContentItem> populateCompetencyContentItems(String courseId) {
        Optional<Competency> competencyOpt = courseRepository.findWithEagerCompetenciesAndPrerequisitesById(Long.parseLong(courseId))
                .flatMap(course -> course.getCompetencies().stream().findFirst());
        String launchUrl = buildContentUrl(courseId, "competencies");
        return List.of(competencyOpt.map(competency -> createSingleUnitContentItem(launchUrl))
                .orElseThrow(() -> new BadRequestAlertException("No competencies found.", "LTI", "CompetenciesNotFound")));
    }

    /**
     * Populate content items for deep linking response with Iris.
     */
    private List<LtiContentItem> populateIrisContentItems(String courseId) {
        Optional<Course> courseOpt = courseRepository.findById(Long.parseLong(courseId));
        if (courseOpt.isPresent() && courseOpt.get().getStudentCourseAnalyticsDashboardEnabled()) {
            String launchUrl = buildContentUrl(courseId, "dashboard");
            return List.of(createSingleUnitContentItem(launchUrl));
        }
        else {
            throw new BadRequestAlertException("Course Analytics Dashboard not activated", "LTI", "noCourseAnalyticsDashboard");
        }
    }

    /**
     * Populate content items for deep linking response with learning paths.
     */
    private List<LtiContentItem> populateLearningPathsContentItems(String courseId) {
        boolean hasLearningPaths = courseRepository.findWithEagerLearningPathsAndLearningPathCompetenciesByIdElseThrow(Long.parseLong(courseId)).getLearningPathsEnabled();
        if (hasLearningPaths) {
            String launchUrl = buildContentUrl(courseId, "learning-path");
            return List.of(createSingleUnitContentItem(launchUrl));
        }
        else {
            throw new BadRequestAlertException("No learning paths found.", "LTI", "learningPathsNotFound");
        }
    }

    /**
     * Set a content item for an exercise.
     */
    private LtiContentItem setExerciseContentItem(String courseId, String exerciseId) {
        Optional<Exercise> exerciseOpt = exerciseRepository.findById(Long.valueOf(exerciseId));
        String launchUrl = buildContentUrl(courseId, "exercises", exerciseId);
        return exerciseOpt.map(exercise -> createExerciseContentItem(exercise, launchUrl))
                .orElseThrow(() -> new BadRequestAlertException("Exercise not found.", "LTI", "exerciseNotFound"));
    }

    /**
     * Set a content item for a grouped exercise.
     */
    private LtiContentItem setGroupedExerciseContentItem(String courseId, Set<Long> exerciseIds) {
        List<Exercise> exercises = new ArrayList<>();

        for (Long exerciseId : exerciseIds) {
            Optional<Exercise> exerciseOpt = exerciseRepository.findById(exerciseId);
            exerciseOpt.ifPresent(exercises::add);
        }
        if (exercises.isEmpty()) {
            throw new BadRequestAlertException("No exercises found.", "LTI", "exercisesNotFound");
        }
        String launchUrl = buildContentUrl(courseId, "groupedExercises", exercises.stream().map(Exercise::getId).map(String::valueOf).collect(Collectors.joining(",")));
        return createGroupedExerciseContentItem(exercises, launchUrl);
    }

    /**
     * Set a content item for a lecture.
     */
    private LtiContentItem setLectureContentItem(String courseId, String lectureId) {
        String launchUrl = buildContentUrl(courseId, "lectures", lectureId);
        return lectureRepository.findById(Long.valueOf(lectureId)).map(lecture -> createLectureContentItem(lecture, launchUrl))
                .orElseThrow(() -> new BadRequestAlertException("Lecture not found.", "LTI", "lectureNotFound"));
    }

    /**
     * Set a content item for a grouped lecture.
     */
    private LtiContentItem setGroupedLectureContentItem(String courseId, Set<Long> lectureIds) {
        List<Lecture> lectures = new ArrayList<>();

        for (Long lectureId : lectureIds) {
            Optional<Lecture> lectureOpt = lectureRepository.findById(lectureId);
            lectureOpt.ifPresent(lectures::add);
        }
        if (lectures.isEmpty()) {
            throw new BadRequestAlertException("No lectures found.", "LTI", "lecturesNotFound");
        }
        String launchUrl = buildContentUrl(courseId, "groupedLectures", lectures.stream().map(Lecture::getId).map(String::valueOf).collect(Collectors.joining(",")));
        return createGroupedLectureContentItem(launchUrl);
    }

    /**
     * Create a content item for an exercise.
     */
    private LtiContentItem createExerciseContentItem(Exercise exercise, String url) {
        LineItem lineItem = exercise.getIncludedInOverallScore() != IncludedInOverallScore.NOT_INCLUDED ? new LineItem(DEFAULT_SCORE_MAXIMUM) : null;
        return new LtiContentItem("ltiResourceLink", exercise.getTitle(), url, lineItem);
    }

    /**
     * Create a content item for grouped exercises.
     */
    private LtiContentItem createGroupedExerciseContentItem(List<Exercise> exercises, String url) {
        LineItem lineItem = exercises.stream().anyMatch(exercise -> exercise.getIncludedInOverallScore() != IncludedInOverallScore.NOT_INCLUDED)
                ? new LineItem(DEFAULT_SCORE_MAXIMUM)
                : null;
        return new LtiContentItem("ltiResourceLink", "Grouped Exercises", url, lineItem);
    }

    /**
     * Create a content item for a lecture.
     */
    private LtiContentItem createLectureContentItem(Lecture lecture, String url) {
        return new LtiContentItem("ltiResourceLink", lecture.getTitle(), url, null);
    }

    /**
     * Create a content item for grouped lectures.
     */
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
    private String buildContentUrl(String courseId, String resourceType, String resourceId) {
        if ("groupedExercises".equals(resourceType)) {
            List<Long> exerciseIds = Arrays.stream(resourceId.split(",")).map(String::trim).map(Long::valueOf).toList();

            // Take the smallest exercise ID for the base URL to establish it as "starting" exercise for LTI view
            Long smallestExerciseId = exerciseIds.stream().min(Long::compareTo).orElseThrow(() -> new BadRequestAlertException("No exercise IDs provided", "LTI", "noExerciseIds"));

            String baseUrl = String.format("%s/courses/%s/exercises/%d", artemisServerUrl, courseId, smallestExerciseId);

            // Include all exercise IDs in the query parameter for sidebar content
            UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(baseUrl).queryParam("isMultiLaunch", true).queryParam("exerciseIDs", resourceId);

            return uriBuilder.toUriString();
        }
        else if ("groupedLectures".equals(resourceType)) {
            List<Long> lectureIds = Arrays.stream(resourceId.split(",")).map(String::trim).map(Long::valueOf).toList();

            Long smallestLectureId = lectureIds.stream().min(Long::compareTo).orElseThrow(() -> new BadRequestAlertException("No lecture IDs provided", "LTI", "noLectureIds"));

            String baseUrl = String.format("%s/courses/%s/lectures/%d", artemisServerUrl, courseId, smallestLectureId);
            UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(baseUrl).queryParam("isMultiLaunch", true).queryParam("lectureIDs", resourceId);

            return uriBuilder.toUriString();
        }
        else {
            return String.format("%s/courses/%s/%s/%s", artemisServerUrl, courseId, resourceType, resourceId);
        }
    }

    private String buildContentUrl(String courseId, String resourceType) {
        return String.format("%s/courses/%s/%s", artemisServerUrl, courseId, resourceType);
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
