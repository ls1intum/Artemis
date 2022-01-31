package de.tum.in.www1.artemis.lecture.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.domain.Authority;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.AuthorityRepository;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.util.DatabaseCleanupService;
import de.tum.in.www1.artemis.util.ModelFactory;
import org.springframework.stereotype.Service;

/**
 * Service responsible for initializing the database with specific testdata for a testscenario
 */
@Service
public class DatabaseUtilService {

    private static final ZonedDateTime PAST_TIMESTAMP = ZonedDateTime.now().minusDays(1);

    private static final ZonedDateTime FUTURE_FUTURE_TIMESTAMP = ZonedDateTime.now().plusDays(2);

    private static final Authority USER_AUTHORITY = new Authority(Role.STUDENT.getAuthority());

    private static final Authority TUTOR_AUTHORITY = new Authority(Role.TEACHING_ASSISTANT.getAuthority());

    private static final Authority EDITOR_AUTHORITY = new Authority(Role.EDITOR.getAuthority());

    private static final Authority INSTRUCTOR_AUTHORITY = new Authority(Role.INSTRUCTOR.getAuthority());

    private static final Authority ADMIN_AUTHORITY = new Authority(Role.ADMIN.getAuthority());

    private static final Set<Authority> STUDENT_AUTHORITIES = Set.of(USER_AUTHORITY);

    private static final Set<Authority> TUTOR_AUTHORITIES = Set.of(USER_AUTHORITY, TUTOR_AUTHORITY);

    private static final Set<Authority> EDITOR_AUTHORITIES = Set.of(USER_AUTHORITY, TUTOR_AUTHORITY, EDITOR_AUTHORITY);

    private static final Set<Authority> INSTRUCTOR_AUTHORITIES = Set.of(USER_AUTHORITY, TUTOR_AUTHORITY, EDITOR_AUTHORITY, INSTRUCTOR_AUTHORITY);

    private static final Set<Authority> ADMIN_AUTHORITIES = Set.of(USER_AUTHORITY, TUTOR_AUTHORITY, EDITOR_AUTHORITY, INSTRUCTOR_AUTHORITY, ADMIN_AUTHORITY);

    @Autowired
    private CourseRepository courseRepo;

    @Autowired
    private LectureRepository lectureRepo;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private AuthorityRepository authorityRepository;

    @Autowired
    private DatabaseCleanupService databaseCleanupService;

    /**
     * Add users of all roles.
     *
     * @return the list of the newly created users
     */
    public List<User> addUsers(int numberOfStudents, int numberOfTutors, int numberOfEditors, int numberOfInstructors) {

        authorityRepository.saveAll(ADMIN_AUTHORITIES);

        List<User> students = ModelFactory.generateActivatedUsers("student", new String[] { "tumuser", "testgroup" }, STUDENT_AUTHORITIES, numberOfStudents);
        List<User> tutors = ModelFactory.generateActivatedUsers("tutor", new String[] { "tutor", "testgroup" }, TUTOR_AUTHORITIES, numberOfTutors);
        List<User> editors = ModelFactory.generateActivatedUsers("editor", new String[] { "editor", "testgroup" }, EDITOR_AUTHORITIES, numberOfEditors);
        List<User> instructors = ModelFactory.generateActivatedUsers("instructor", new String[] { "instructor", "testgroup" }, INSTRUCTOR_AUTHORITIES, numberOfInstructors);
        User admin = ModelFactory.generateActivatedUser("admin");
        admin.setGroups(Set.of("admin"));
        admin.setAuthorities(ADMIN_AUTHORITIES);
        List<User> usersToAdd = new ArrayList<>();
        usersToAdd.addAll(students);
        usersToAdd.addAll(tutors);
        usersToAdd.addAll(editors);
        usersToAdd.addAll(instructors);
        usersToAdd.add(admin);
        userRepo.saveAll(usersToAdd);
        assertThat(userRepo.findAll().size()).as("all users are created").isGreaterThanOrEqualTo(numberOfStudents + numberOfTutors + numberOfEditors + numberOfInstructors + 1);
        assertThat(userRepo.findAll()).as("users are correctly stored").containsAnyOf(usersToAdd.toArray(new User[0]));

        final var users = new ArrayList<>(students);
        users.addAll(tutors);
        users.addAll(editors);
        users.addAll(instructors);
        users.add(admin);
        return users;
    }

    /**
     * Create a course and create a lecture assigned to the course.
     *
     * @return the created lecture
     */
    public Lecture createCourseWithLecture(boolean saveLecture) {
        Course course = ModelFactory.generateCourse(null, PAST_TIMESTAMP, FUTURE_FUTURE_TIMESTAMP, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");

        Lecture lecture = new Lecture();
        lecture.setDescription("Test Lecture");
        lecture.setCourse(course);
        courseRepo.save(course);
        if (saveLecture) {
            lectureRepo.save(lecture);
        }
        return lecture;
    }

    public void resetDatabase() {
        databaseCleanupService.clearDatabase();
    }

}
