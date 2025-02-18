package de.tum.cit.aet.artemis.lti.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LTI;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.glassfish.jersey.uri.UriComponent;
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
import de.tum.cit.aet.artemis.lti.dto.Lti13DeepLinkingResponse;

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

    /**
     * Constructor for LtiDeepLinkingService.
     *
     * @param exerciseRepository The repository for exercises.
     * @param tokenRetriever     The LTI 1.3 token retriever.
     */
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
        // Initialize DeepLinkingResponse
        Lti13DeepLinkingResponse lti13DeepLinkingResponse = Lti13DeepLinkingResponse.from(ltiIdToken, clientRegistrationId);
        // Dynamically populate content items based on the type
        ArrayList<Map<String, Object>> contentItems = switch (type) {
            case EXERCISE -> {
                if (unitIds == null || unitIds.isEmpty()) {
                    throw new BadRequestAlertException("No exercise IDs provided for deep linking", "LTI", "noExerciseIds");
                }
                yield populateExerciseContentItems(String.valueOf(courseId), unitIds);
            }
            case LECTURE -> {
                if (unitIds == null || unitIds.isEmpty()) {
                    throw new BadRequestAlertException("No lecture IDs provided for deep linking", "LTI", "noLectureIds");
                }
                yield populateLectureContentItems(String.valueOf(courseId), unitIds);
            }
            case COMPETENCY -> populateCompetencyContentItems(String.valueOf(courseId));
            case IRIS -> populateIrisContentItems(String.valueOf(courseId));
            case LEARNING_PATH -> populateLearningPathsContentItems(String.valueOf(courseId));
            default -> throw new BadRequestAlertException("Invalid deep linking type provided", "LTI", "invalidType");
        };
        lti13DeepLinkingResponse = lti13DeepLinkingResponse.setContentItems(contentItems);

        // Prepare return url with jwt and id parameters
        return this.buildLtiDeepLinkResponse(clientRegistrationId, lti13DeepLinkingResponse);
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

        // Validate properties are set to create a response
        validateDeepLinkingResponseSettings(returnUrl, jwt, lti13DeepLinkingResponse.deploymentId());

        uriComponentsBuilder.queryParam("jwt", jwt);
        uriComponentsBuilder.queryParam("id", lti13DeepLinkingResponse.deploymentId());
        uriComponentsBuilder.queryParam("deepLinkUri", UriComponent.encode(returnUrl, UriComponent.Type.QUERY_PARAM));

        return uriComponentsBuilder.build().toUriString();

    }

    /**
     * Populate content items for deep linking response.
     *
     * @param courseId    The course ID.
     * @param exerciseIds The set of exercise IDs.
     */
    private ArrayList<Map<String, Object>> populateExerciseContentItems(String courseId, Set<Long> exerciseIds) {
        ArrayList<Map<String, Object>> contentItems = new ArrayList<>();
        for (Long exerciseId : exerciseIds) {
            Map<String, Object> item = setExerciseContentItem(courseId, String.valueOf(exerciseId));
            contentItems.add(item);
        }
        return contentItems;
    }

    /**
     * Populate content items for deep linking response with lectures.
     *
     * @param courseId   The course ID.
     * @param lectureIds The set of lecture IDs.
     */
    private ArrayList<Map<String, Object>> populateLectureContentItems(String courseId, Set<Long> lectureIds) {
        ArrayList<Map<String, Object>> contentItems = new ArrayList<>();
        for (Long lectureId : lectureIds) {
            Map<String, Object> item = setLectureContentItem(courseId, String.valueOf(lectureId));
            contentItems.add(item);

        }
        return contentItems;
    }

    /**
     * Populate content items for deep linking response with competencies.
     *
     * @param courseId The course ID.
     */
    private ArrayList<Map<String, Object>> populateCompetencyContentItems(String courseId) {
        return new ArrayList<>(List.of(setCompetencyContentItem(courseId)));
    }

    /**
     * Populate content items for deep linking response with Iris.
     *
     * @param courseId The course ID.
     */
    private ArrayList<Map<String, Object>> populateIrisContentItems(String courseId) {
        return new ArrayList<>(List.of(setIrisContentItem(courseId)));
    }

    /**
     * Populate content items for deep linking response with learning paths.
     *
     * @param courseId The course ID.
     */
    private ArrayList<Map<String, Object>> populateLearningPathsContentItems(String courseId) {
        return new ArrayList<>(List.of(setLearningPathContentItem(courseId)));
    }

    private Map<String, Object> setExerciseContentItem(String courseId, String exerciseId) {
        Optional<Exercise> exerciseOpt = exerciseRepository.findById(Long.valueOf(exerciseId));
        String launchUrl = String.format(artemisServerUrl + "/courses/%s/exercises/%s", courseId, exerciseId);
        return exerciseOpt.map(exercise -> createExerciseContentItem(exerciseOpt.get(), launchUrl))
                .orElseThrow(() -> new BadRequestAlertException("Exercise not found.", "LTI", "exerciseNotFound"));
    }

    private Map<String, Object> setLectureContentItem(String courseId, String lectureId) {
        Optional<Lecture> lectureOpt = lectureRepository.findById(Long.valueOf(lectureId));
        String launchUrl = String.format(artemisServerUrl + "/courses/%s/lectures/%s", courseId, lectureId);
        return lectureOpt.map(lecture -> createLectureContentItem(lectureOpt.get(), launchUrl))
                .orElseThrow(() -> new BadRequestAlertException("Lecture not found.", "LTI", "lectureNotFound"));
    }

    private Map<String, Object> setCompetencyContentItem(String courseId) {
        Optional<Competency> competencyOpt = courseRepository.findWithEagerCompetenciesAndPrerequisitesById(Long.parseLong(courseId))
                .flatMap(course -> course.getCompetencies().stream().findFirst());
        String launchUrl = String.format(artemisServerUrl + "/courses/%s/competencies", courseId);
        return competencyOpt.map(competency -> createSingleUnitContentItem(launchUrl))
                .orElseThrow(() -> new BadRequestAlertException("No competencies found.", "LTI", "CompetenciesNotFound"));
    }

    private Map<String, Object> setLearningPathContentItem(String courseId) {
        boolean hasLearningPaths = courseRepository.findWithEagerLearningPathsAndLearningPathCompetenciesByIdElseThrow(Long.parseLong(courseId)).getLearningPathsEnabled();
        if (hasLearningPaths) {
            String launchUrl = String.format(artemisServerUrl + "/courses/%s/learning-path", courseId);
            return createSingleUnitContentItem(launchUrl);
        }
        else {
            throw new BadRequestAlertException("No learning paths found.", "LTI", "learningPathsNotFound");
        }
    }

    private Map<String, Object> setIrisContentItem(String courseId) {
        Optional<Course> courseOpt = courseRepository.findById(Long.parseLong(courseId));
        if (courseOpt.isPresent()) {
            if (courseOpt.get().getStudentCourseAnalyticsDashboardEnabled()) {
                String launchUrl = String.format(artemisServerUrl + "/courses/%s/dashboard", courseId);
                return createSingleUnitContentItem(launchUrl);
            }
            else {
                throw new BadRequestAlertException("Course Analytics Dashboard not activated", "LTI", "noCourseAnalyticsDashboard");
            }
        }
        else {
            throw new BadRequestAlertException("No course found.", "LTI", "courseNotFound");
        }
    }

    private Map<String, Object> createExerciseContentItem(Exercise exercise, String url) {

        Map<String, Object> item = new HashMap<>();
        item.put("type", "ltiResourceLink");
        item.put("title", exercise.getTitle());
        item.put("url", url);

        addLineItemIfIncluded(exercise, item);
        return item;
    }

    private Map<String, Object> createLectureContentItem(Lecture lecture, String url) {
        return Map.of("type", "ltiResourceLink", "title", lecture.getTitle(), "url", url);
    }

    private Map<String, Object> createSingleUnitContentItem(String url) {
        return Map.of("type", "ltiResourceLink", "title", "competency", "url", url);
    }

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

    private void addLineItemIfIncluded(Exercise exercise, Map<String, Object> item) {
        if (exercise.getIncludedInOverallScore() != IncludedInOverallScore.NOT_INCLUDED) {
            Map<String, Object> lineItem = new HashMap<>();
            lineItem.put("scoreMaximum", DEFAULT_SCORE_MAXIMUM);
            item.put("lineItem", lineItem);
        }
    }
}
