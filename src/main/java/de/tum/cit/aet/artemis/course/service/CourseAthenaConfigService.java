package de.tum.cit.aet.artemis.course.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseAthenaConfig;
import de.tum.cit.aet.artemis.course.repository.CourseAthenaConfigRepository;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class CourseAthenaConfigService {

    private final CourseAthenaConfigRepository courseAthenaConfigRepository;

    public CourseAthenaConfigService(CourseAthenaConfigRepository courseAthenaConfigRepository) {
        this.courseAthenaConfigRepository = courseAthenaConfigRepository;
    }

    /**
     * Upserts or deletes the {@link CourseAthenaConfig} row for the given course.
     * If both flags are {@code false} the row is deleted (if present); otherwise it is created or updated.
     *
     * @param course           the course whose Athena config should be updated
     * @param formativeEnabled whether formative (preliminary) Athena feedback is enabled
     * @param gradingEnabled   whether graded Athena feedback suggestions are enabled
     */
    public void updateConfig(Course course, boolean formativeEnabled, boolean gradingEnabled) {
        Optional<CourseAthenaConfig> existing = courseAthenaConfigRepository.findByCourseId(course.getId());
        if (!formativeEnabled && !gradingEnabled) {
            existing.ifPresent(courseAthenaConfigRepository::delete);
        }
        else {
            CourseAthenaConfig config = existing.orElseGet(() -> {
                CourseAthenaConfig c = new CourseAthenaConfig();
                c.setCourse(course);
                return c;
            });
            config.setFormativeEnabled(formativeEnabled);
            config.setGradingEnabled(gradingEnabled);
            courseAthenaConfigRepository.save(config);
        }
    }

    /**
     * Reads the {@link CourseAthenaConfig} for the given course and sets the {@code @Transient}
     * {@code athenaFormativeEnabled} and {@code athenaGradingEnabled} fields on the course object
     * so they are included in JSON serialization.
     *
     * @param course the course whose transient Athena fields should be populated
     */
    public void stampAthenaConfig(Course course) {
        courseAthenaConfigRepository.findByCourseId(course.getId()).ifPresentOrElse(config -> {
            course.setAthenaFormativeEnabled(config.isFormativeEnabled());
            course.setAthenaGradingEnabled(config.isGradingEnabled());
        }, () -> {
            course.setAthenaFormativeEnabled(false);
            course.setAthenaGradingEnabled(false);
        });
    }

    public boolean isGradingEnabled(long courseId) {
        return courseAthenaConfigRepository.findByCourseId(courseId).map(CourseAthenaConfig::isGradingEnabled).orElse(false);
    }

    public boolean isFormativeEnabled(long courseId) {
        return courseAthenaConfigRepository.findByCourseId(courseId).map(CourseAthenaConfig::isFormativeEnabled).orElse(false);
    }
}
