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
import de.tum.cit.aet.artemis.iris.domain.settings.IrisCourseSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisCourseSettingsEntity;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisRateLimitConfiguration;
import de.tum.cit.aet.artemis.iris.dto.IrisCourseSettingsWithRateLimitDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisCourseSettingsRepository;

/**
 * Service entry point for interacting with the new single layer Iris settings model.
 */
@Service
@Profile(PROFILE_IRIS)
@Lazy
public class IrisSettingsService {

    private final IrisCourseSettingsRepository irisCourseSettingsRepository;

    private final CourseRepository courseRepository;

    private final int configuredDefaultRateLimit;

    private final int configuredDefaultTimeframeHours;

    public IrisSettingsService(IrisCourseSettingsRepository irisCourseSettingsRepository, CourseRepository courseRepository,
            @Value("${artemis.iris.ratelimit.default-limit:0}") int configuredDefaultRateLimit,
            @Value("${artemis.iris.ratelimit.default-timeframe-hours:0}") int configuredDefaultTimeframeHours) {
        this.irisCourseSettingsRepository = irisCourseSettingsRepository;
        this.courseRepository = courseRepository;
        this.configuredDefaultRateLimit = configuredDefaultRateLimit;
        this.configuredDefaultTimeframeHours = configuredDefaultTimeframeHours;
    }

    /**
     * Returns the effective settings for the given course.
     *
     * @param course the course
     * @return settings DTO (defaults if no custom settings exist)
     */
    public IrisCourseSettings getSettingsForCourse(Course course) {
        Objects.requireNonNull(course, "course must not be null");
        return getSettingsForCourse(course.getId());
    }

    /**
     * Returns the effective settings for the given course id.
     *
     * @param courseId the course id
     * @return settings DTO (defaults if no custom settings exist)
     */
    public IrisCourseSettings getSettingsForCourse(long courseId) {
        return irisCourseSettingsRepository.findByCourseId(courseId).map(IrisCourseSettingsEntity::getSettings).orElseGet(IrisCourseSettings::defaultSettings);
    }

    /**
     * Returns the Iris settings mapped to a DTO for REST responses.
     *
     * @param courseId the course id
     * @return DTO containing the payload with effective rate limits
     */
    public IrisCourseSettingsWithRateLimitDTO getCourseSettingsWithRateLimit(long courseId) {
        var settings = getSettingsForCourse(courseId);
        var defaults = getApplicationRateLimitDefaults();
        var effective = resolveEffectiveRateLimit(settings, defaults);
        return new IrisCourseSettingsWithRateLimitDTO(courseId, settings, effective, defaults);
    }

    /**
     * Updates (or creates if not present) the Iris settings for a course.
     * <p>
     * Handles null payloads (uses current settings), sanitization, and instructor restrictions.
     *
     * @param courseId the course id
     * @param payload  the new payload (nullable, will use current settings if null)
     * @param isAdmin  whether the requesting user is an admin (admins can change variant and rate limits)
     * @return DTO representing the persisted state
     */
    public IrisCourseSettingsWithRateLimitDTO updateCourseSettings(long courseId, IrisCourseSettings payload, boolean isAdmin) {
        var current = getSettingsForCourse(courseId);
        var request = Objects.requireNonNullElse(payload, current);
        var sanitizedRequest = sanitizePayload(request);
        var sanitizedCurrent = sanitizePayload(current);

        if (!isAdmin) {
            enforceInstructorRestrictions(sanitizedRequest, sanitizedCurrent);
        }

        var entity = irisCourseSettingsRepository.findByCourseId(courseId).orElseGet(() -> {
            var newEntity = new IrisCourseSettingsEntity();
            newEntity.setCourseId(courseId);
            return newEntity;
        });
        entity.setSettings(sanitizedRequest);
        irisCourseSettingsRepository.save(entity);
        var defaults = getApplicationRateLimitDefaults();
        var effective = resolveEffectiveRateLimit(sanitizedRequest, defaults);
        return new IrisCourseSettingsWithRateLimitDTO(courseId, sanitizedRequest, effective, defaults);
    }

    /**
     * Validates that non-admin users are not trying to change restricted settings.
     * Instructors can only modify enabled status and custom instructions; variant and rate limits are admin-only.
     *
     * @param request the requested new settings
     * @param current the current settings
     * @throws AccessForbiddenAlertException if the request attempts to change variant or rate limits
     */
    private void enforceInstructorRestrictions(IrisCourseSettings request, IrisCourseSettings current) {
        if (!Objects.equals(request.variant(), current.variant())) {
            throw new AccessForbiddenAlertException("Only administrators can change the Iris pipeline variant", "IrisSettings", "irisVariantRestricted");
        }

        if (!Objects.equals(request.rateLimit(), current.rateLimit())) {
            throw new AccessForbiddenAlertException("Only administrators can change Iris rate limits", "IrisSettings", "irisRateLimitRestricted");
        }
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
    public IrisCourseSettings getSettingsForCourseOrThrow(long courseId) {
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
    public IrisCourseSettings sanitizePayload(IrisCourseSettings payload) {
        if (payload == null) {
            return IrisCourseSettings.defaultSettings();
        }
        var sanitizedRateLimit = sanitizeRateLimit(payload.rateLimit());
        return IrisCourseSettings.of(payload.enabled(), payload.customInstructions(), payload.variant(), sanitizedRateLimit);
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

    private IrisRateLimitConfiguration resolveEffectiveRateLimit(IrisCourseSettings settings, IrisRateLimitConfiguration defaults) {
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
