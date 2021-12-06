package de.tum.in.www1.artemis.config.migration.entries;

import java.util.List;

import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.config.migration.MigrationEntry;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.repository.CourseRepository;

@Component
public class MigrationEntry20211127_120000 extends MigrationEntry {

    private final CourseRepository courseRepository;

    public MigrationEntry20211127_120000(CourseRepository courseRepository) {
        this.courseRepository = courseRepository;
    }

    @Override
    public String author() {
        return /* "julian-christl" */null;
    }

    /**
     * Format YYYYMMDD-HHmmss
     *
     * @return Current time in given format
     */
    @Override
    public String date() {
        return "20211127-120000";
    }

    @Override
    public void execute() {
        List<Course> courses = courseRepository.findAll();
        courseRepository.saveAllAndFlush(courses);
    }
}
