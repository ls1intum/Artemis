package de.tum.in.www1.artemis.course;

import java.util.HashSet;
import java.util.Set;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Organization;
import de.tum.in.www1.artemis.util.ModelFactory;

@Service
public class CourseTestService {

    public Course createCourse() {
        return createCourse(null);
    }

    public Course createCourse(Long id) {
        Course course = ModelFactory.generateCourse(id, pastTimestamp, futureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        return courseRepo.save(course);
    }

    public Course createCourseWithOrganizations(String name, String shortName, String url, String description, String logoUrl, String emailPattern) {
        Course course = createCourse();
        Set<Organization> organizations = new HashSet<>();
        Organization organization = createOrganization(name, shortName, url, description, logoUrl, emailPattern);
        organizations.add(organization);
        course.setOrganizations(organizations);
        return courseRepo.save(course);
    }

    public Course createCourseWithOrganizations() {
        return createCourseWithOrganizations("organization1", "org1", "org.org", "This is organization1", null, "^.*@matching.*$");
    }
}
