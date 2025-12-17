package de.tum.cit.aet.artemis.iris.service.settings;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenAlertException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.iris.domain.settings.CourseIrisSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisCourseSettingsDTO;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisRateLimitConfiguration;
import de.tum.cit.aet.artemis.iris.dto.IrisCourseSettingsWithRateLimitDTO;
import de.tum.cit.aet.artemis.iris.repository.CourseIrisSettingsRepository;

/**
 * Service entry point for interacting with the new single layer Iris settings model.
 */
@Service
@Profile(PROFILE_IRIS)
@Lazy
public class IrisSettingsService {

    private final CourseIrisSettingsRepository courseIrisSettingsRepository;

    private final CourseRepository courseRepository;

    private final int configuredDefaultRateLimit;

    private final int configuredDefaultTimeframeHours;

    public IrisSettingsService(CourseIrisSettingsRepository courseIrisSettingsRepository, CourseRepository courseRepository,
            @Value("${artemis.iris.ratelimit.default-limit:0}") int configuredDefaultRateLimit,
            @Value("${artemis.iris.ratelimit.default-timeframe-hours:0}") int configuredDefaultTimeframeHours) {
        this.courseIrisSettingsRepository = courseIrisSettingsRepository;
        this.courseRepository = courseRepository;
        this.configuredDefaultRateLimit = configuredDefaultRateLimit;
        this.configuredDefaultTimeframeHours = configuredDefaultTimeframeHours;
    }

    /**
     * Retrieves the Iris course settings or creates a default entry if missing.
     *
     * @param courseId the owning course id
     * @return managed entity containing the settings
     * @throws EntityNotFoundException if the course does not exist
     */
    public CourseIrisSettings getOrCreateCourseSettings(long courseId) {
        return courseIrisSettingsRepository.findByCourseId(courseId).orElseGet(() -> {
            // Validate course exists before creating settings to provide a clear error message
            courseRepository.findByIdElseThrow(courseId);
            var entity = new CourseIrisSettings();
            entity.setCourseId(courseId);
            entity.setSettings(IrisCourseSettingsDTO.defaultSettings());
            return courseIrisSettingsRepository.save(entity);
        });
    }

    /**
     * Returns the effective settings for the given course.
     *
     * @param course the course
     * @return sanitized payload
     */
    public IrisCourseSettingsDTO getSettingsForCourse(Course course) {
        Objects.requireNonNull(course, "course must not be null");
        return getOrCreateCourseSettings(course.getId()).getSettings();
    }

    /**
     * Returns the effective settings for the given course id.
     *
     * @param courseId the course id
     * @return sanitized payload
     */
    public IrisCourseSettingsDTO getSettingsForCourse(long courseId) {
        return getOrCreateCourseSettings(courseId).getSettings();
    }

    /**
     * Returns the Iris settings mapped to a DTO for REST responses.
     *
     * @param courseId the course id
     * @return DTO containing the payload
     */
    public IrisCourseSettingsWithRateLimitDTO getCourseSettingsWithRateLimit(long courseId) {
        var entity = getOrCreateCourseSettings(courseId);
        var settings = entity.getSettings();
        var defaults = getApplicationRateLimitDefaults();
        var effective = resolveEffectiveRateLimit(settings, defaults);
        return IrisCourseSettingsWithRateLimitDTO.fromEntity(entity, effective, defaults);
    }

    /**
     * Updates (or creates) the Iris settings for a course.
     *
     * @param courseId the course id
     * @param payload  the new payload
     * @return DTO representing the persisted state
     */
    public IrisCourseSettingsWithRateLimitDTO updateCourseSettings(long courseId, IrisCourseSettingsDTO payload) {
        var entity = getOrCreateCourseSettings(courseId);
        var sanitized = sanitizePayload(payload);
        entity.setSettings(sanitized);
        var saved = courseIrisSettingsRepository.save(entity);
        var defaults = getApplicationRateLimitDefaults();
        var effective = resolveEffectiveRateLimit(sanitized, defaults);
        return IrisCourseSettingsWithRateLimitDTO.fromEntity(saved, effective, defaults);
    }

    /**
     * Checks whether Iris is enabled for the given course id.
     *
     * @param courseId the course id
     * @return {@code true} if enabled
     */
    public boolean isEnabledForCourse(long courseId) {
        return getSettingsForCourse(courseId).enabled();
    }

    /**
     * Checks whether Iris is enabled for the given course.
     *
     * @param course the course entity
     * @return {@code true} if enabled
     */
    public boolean isEnabledForCourse(Course course) {
        Objects.requireNonNull(course, "course must not be null");
        return isEnabledForCourse(course.getId());
    }

    /**
     * Ensures Iris is enabled for the supplied course, otherwise throws an access exception.
     *
     * @param course the course
     */
    public void ensureEnabledForCourseOrElseThrow(Course course) {
        if (!isEnabledForCourse(course)) {
            throw new AccessForbiddenAlertException("Iris is disabled for course " + course.getId(), "Iris", "iris.course_disabled");
        }
    }

    /**
     * Shortcut to fetch a course by id and return the settings.
     *
     * @param courseId the course id
     * @return the Iris settings for the course
     */
    public IrisCourseSettingsDTO getSettingsForCourseOrThrow(long courseId) {
        courseRepository.findByIdElseThrow(courseId);
        return getSettingsForCourse(courseId);
    }

    /**
     * Returns the application-level default rate limit configuration.
     * <p>
     * Interprets the configured values as follows:
     * - (-1, -1) = unlimited (returns null, null)
     * - Not set / both 0 with old config = unlimited (returns null, null)
     * - Otherwise: requests < 0 → 0 (blocking), timeframe < 1 → 1 (minimum 1 hour)
     *
     * @return the default rate limit configuration from application properties
     */
    public IrisRateLimitConfiguration getApplicationRateLimitDefaults() {
        // (-1, -1) means unlimited
        if (configuredDefaultRateLimit == -1 && configuredDefaultTimeframeHours == -1) {
            return new IrisRateLimitConfiguration(null, null);
        }

        // Sanitize: requests < 0 → 0 (blocking), timeframe < 1 → 1 (minimum)
        int requests = Math.max(configuredDefaultRateLimit, 0);
        int timeframe = Math.max(configuredDefaultTimeframeHours, 1);

        // If both end up as 0/1 due to old config with 0,0, treat as unlimited
        if (requests == 0 && timeframe == 1 && configuredDefaultRateLimit == 0 && configuredDefaultTimeframeHours == 0) {
            return new IrisRateLimitConfiguration(null, null);
        }

        return new IrisRateLimitConfiguration(requests, timeframe);
    }

    /**
     * Sanitizes a payload before it is used for permission checks or persistence.
     *
     * @param payload incoming payload
     * @return sanitized payload
     */
    public IrisCourseSettingsDTO sanitizePayload(IrisCourseSettingsDTO payload) {
        if (payload == null) {
            return IrisCourseSettingsDTO.defaultSettings();
        }
        var sanitizedRateLimit = sanitizeRateLimit(payload.rateLimit());
        return IrisCourseSettingsDTO.of(payload.enabled(), payload.customInstructions(), payload.variant(), sanitizedRateLimit);
    }

    private IrisRateLimitConfiguration sanitizeRateLimit(IrisRateLimitConfiguration rateLimit) {
        if (rateLimit == null) {
            return null; // null = use defaults, non-null = explicit override
        }

        boolean hasRequests = rateLimit.requests() != null;
        boolean hasTimeframe = rateLimit.timeframeHours() != null;

        // Both empty = use defaults (return null)
        if (!hasRequests && !hasTimeframe) {
            return null;
        }

        // One filled, one empty = invalid
        if (hasRequests != hasTimeframe) {
            throw new BadRequestAlertException("Both rate limit fields must be filled or both must be empty", "IrisSettings", "irisRateLimitBothRequired");
        }

        // Both filled - validate values
        // Note: 0 requests means "no requests allowed" (blocking)
        if (rateLimit.requests() < 0) {
            throw new BadRequestAlertException("Rate limit requests must be 0 or greater", "IrisSettings", "irisRateLimitRequestsInvalid");
        }

        if (rateLimit.timeframeHours() <= 0) {
            throw new BadRequestAlertException("Rate limit timeframe must be greater than 0", "IrisSettings", "irisRateLimitTimeframeInvalid");
        }

        return rateLimit;
    }

    private IrisRateLimitConfiguration resolveEffectiveRateLimit(IrisCourseSettingsDTO settings, IrisRateLimitConfiguration defaults) {
        Objects.requireNonNull(settings, "settings must not be null");
        defaults = Objects.requireNonNullElse(defaults, IrisRateLimitConfiguration.empty());

        // null rateLimit = no override, use defaults
        if (settings.rateLimit() == null) {
            return defaults;
        }

        // Non-null rateLimit = explicit override (null values inside = unlimited)
        return settings.rateLimit();
    }
}
