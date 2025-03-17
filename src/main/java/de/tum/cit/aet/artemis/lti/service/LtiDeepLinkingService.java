package de.tum.cit.aet.artemis.lti.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LTI;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
            case LECTURE -> populateLectureContentItems(String.valueOf(courseId), unitIds);
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
     * Populate content items for deep linking response with lectures.
     */
    private List<LtiContentItem> populateLectureContentItems(String courseId, Set<Long> lectureIds) {
        validateUnitIds(lectureIds, DeepLinkingType.LECTURE);
        return lectureIds.stream().map(lectureId -> setLectureContentItem(courseId, String.valueOf(lectureId))).toList();
    }

    /**
     * Populate content items for deep linking response with competencies.
     */
    List<LtiContentItem> populateCompetencyContentItems(String courseId) {
        Optional<Competency> competencyOpt = courseRepository.findWithEagerCompetenciesAndPrerequisitesById(Long.parseLong(courseId))
                .flatMap(course -> course.getCompetencies().stream().findFirst());
        String launchUrl = buildContentUrl(courseId, "competencies");
        return List.of(competencyOpt.map(competency -> createSingleUnitContentItem(launchUrl))
                .orElseThrow(() -> new BadRequestAlertException("No competencies found.", "LTI", "CompetenciesNotFound")));
    }

    /**
     * Populate content items for deep linking response with Iris.
     */
    List<LtiContentItem> populateIrisContentItems(String courseId) {
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
    List<LtiContentItem> populateLearningPathsContentItems(String courseId) {
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
     * Set a content item for a lecture.
     */
    private LtiContentItem setLectureContentItem(String courseId, String lectureId) {
        String launchUrl = buildContentUrl(courseId, "lectures", lectureId);
        return lectureRepository.findById(Long.valueOf(lectureId)).map(lecture -> createLectureContentItem(lecture, launchUrl))
                .orElseThrow(() -> new BadRequestAlertException("Lecture not found.", "LTI", "lectureNotFound"));
    }

    /**
     * Create a content item for an exercise.
     */
    LtiContentItem createExerciseContentItem(Exercise exercise, String url) {
        LineItem lineItem = exercise.getIncludedInOverallScore() != IncludedInOverallScore.NOT_INCLUDED ? new LineItem(DEFAULT_SCORE_MAXIMUM) : null;
        return new LtiContentItem("ltiResourceLink", exercise.getTitle(), url, lineItem);
    }

    /**
     * Create a content item for a lecture.
     */
    private LtiContentItem createLectureContentItem(Lecture lecture, String url) {
        return new LtiContentItem("ltiResourceLink", lecture.getTitle(), url, null);
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
    String buildContentUrl(String courseId, String resourceType, String resourceId) {
        return String.format("%s/courses/%s/%s/%s", artemisServerUrl, courseId, resourceType, resourceId);
    }

    String buildContentUrl(String courseId, String resourceType) {
        return String.format("%s/courses/%s/%s", artemisServerUrl, courseId, resourceType);
    }

    /**
     * Validate deep linking response settings.
     */
    void validateDeepLinkingResponseSettings(String returnURL, String jwt, String deploymentId) {
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
