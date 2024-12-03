package de.tum.cit.aet.artemis.core.config.migration.entries;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_ATLAS;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.atlas.api.CompetencyProgressApi;
import de.tum.cit.aet.artemis.core.config.migration.MigrationEntry;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.exception.ApiNotPresentException;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;

public class MigrationEntry20240614_140000 extends MigrationEntry {

    private static final Logger log = LoggerFactory.getLogger(MigrationEntry20240614_140000.class);

    private final CourseRepository courseRepository;

    private final Optional<CompetencyProgressApi> competencyProgressApi;

    public MigrationEntry20240614_140000(CourseRepository courseRepository, Optional<CompetencyProgressApi> competencyProgressApi) {
        this.courseRepository = courseRepository;
        this.competencyProgressApi = competencyProgressApi;
    }

    @Override
    public void execute() {
        List<Course> activeCourses = courseRepository.findAllActiveWithoutTestCourses(ZonedDateTime.now());

        log.info("Updating competency progress for {} active courses", activeCourses.size());
        var api = competencyProgressApi.orElseThrow(() -> new ApiNotPresentException("competencyProgressApi", PROFILE_ATLAS));
        api.updateProgressForCoursesAsync(activeCourses);
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
