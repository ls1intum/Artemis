package de.tum.in.www1.artemis.config.migration.entries;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.in.www1.artemis.config.migration.MigrationEntry;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.competency.LearningPathsConfiguration;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.competency.LearningPathsConfigurationRepository;

public class MigrationEntry20240720_163000 extends MigrationEntry {

    private static final Logger log = LoggerFactory.getLogger(MigrationEntry20240720_163000.class);

    private final CourseRepository courseRepository;

    private final LearningPathsConfigurationRepository learningPathsConfigurationRepository;

    public MigrationEntry20240720_163000(CourseRepository courseRepository, LearningPathsConfigurationRepository learningPathsConfigurationRepository) {
        this.courseRepository = courseRepository;
        this.learningPathsConfigurationRepository = learningPathsConfigurationRepository;
    }

    @Override
    public void execute() {
        List<Course> courses = courseRepository.findAllWithLearningPathsEnabled();
        List<LearningPathsConfiguration> learningPathsConfigurations = new ArrayList<>();

        log.info("Adding learning paths configuration for {} courses", courses.size());

        courses.forEach(course -> {
            var learningPathsConfiguration = new LearningPathsConfiguration();
            learningPathsConfigurations.add(learningPathsConfiguration);
            course.setLearningPathsConfiguration(learningPathsConfiguration);
        });

        learningPathsConfigurationRepository.saveAll(learningPathsConfigurations);
        courseRepository.saveAll(courses);
    }

    @Override
    public String author() {
        return "stoehrj";
    }

    @Override
    public String date() {
        return "20240720_163000";
    }
}
