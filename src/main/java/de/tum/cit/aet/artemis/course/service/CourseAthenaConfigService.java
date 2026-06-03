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
