package de.tum.in.www1.artemis.config.javaDataChange.entries;

import java.util.List;

import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.config.javaDataChange.JavaDataChangeEntry;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.repository.CourseRepository;

@Component
public class ChangeEntry1 extends JavaDataChangeEntry {

    private final CourseRepository courseRepository;

    public ChangeEntry1(CourseRepository courseRepository) {
        this.courseRepository = courseRepository;
    }

    @Override
    public String author() {
        return "julian-christl";
    }

    @Override
    public void execute() {
        List<Course> courses = courseRepository.findAll();
        courseRepository.saveAllAndFlush(courses);
    }
}
