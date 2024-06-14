package de.tum.in.www1.artemis.config.migration.entries;

import java.time.ZonedDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.in.www1.artemis.config.migration.MigrationEntry;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.competency.Competency;
import de.tum.in.www1.artemis.repository.CompetencyRepository;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.service.competency.CompetencyProgressService;

public class MigrationEntry20240614_140000 extends MigrationEntry {

    private static final Logger log = LoggerFactory.getLogger(MigrationEntry20240614_140000.class);

    private final CourseRepository courseRepository;

    private final CompetencyRepository competencyRepository;

    private final CompetencyProgressService competencyProgressService;

    public MigrationEntry20240614_140000(CourseRepository courseRepository, CompetencyRepository competencyRepository, CompetencyProgressService competencyProgressService) {
        this.courseRepository = courseRepository;
        this.competencyRepository = competencyRepository;
        this.competencyProgressService = competencyProgressService;
    }

    @Override
    public void execute() {
        List<Course> activeCourses = courseRepository.findAllActiveWithoutTestCourses(ZonedDateTime.now());

        log.info("Updating competency progress for {} active courses", activeCourses.size());

        activeCourses.forEach(course -> {
            List<Competency> competencies = competencyRepository.findByCourseId(course.getId());
            // Asynchronously update the progress for each competency
            competencies.forEach(competencyProgressService::updateProgressByCompetencyAsync);
        });
    }

    @Override
    public String author() {
        return "stoehrj";
    }

    @Override
    public String date() {
        return "20240614_140000";
    }
}
