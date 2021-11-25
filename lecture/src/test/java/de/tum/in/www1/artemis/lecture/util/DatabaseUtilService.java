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

    private static final ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(1);

    private static final ZonedDateTime futureFutureTimestamp = ZonedDateTime.now().plusDays(2);

    private static final Authority userAuthority = new Authority(Role.STUDENT.getAuthority());

    private static final Authority tutorAuthority = new Authority(Role.TEACHING_ASSISTANT.getAuthority());

    private static final Authority editorAuthority = new Authority(Role.EDITOR.getAuthority());

    private static final Authority instructorAuthority = new Authority(Role.INSTRUCTOR.getAuthority());

    private static final Authority adminAuthority = new Authority(Role.ADMIN.getAuthority());

    private static final Set<Authority> studentAuthorities = Set.of(userAuthority);

    private static final Set<Authority> tutorAuthorities = Set.of(userAuthority, tutorAuthority);

    private static final Set<Authority> editorAuthorities = Set.of(userAuthority, tutorAuthority, editorAuthority);

    private static final Set<Authority> instructorAuthorities = Set.of(userAuthority, tutorAuthority, editorAuthority, instructorAuthority);

    private static final Set<Authority> adminAuthorities = Set.of(userAuthority, tutorAuthority, editorAuthority, instructorAuthority, adminAuthority);

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

    public List<User> addUsers(int numberOfStudents, int numberOfTutors, int numberOfEditors, int numberOfInstructors) {

        authorityRepository.saveAll(adminAuthorities);

        List<User> students = ModelFactory.generateActivatedUsers("student", new String[] { "tumuser", "testgroup" }, studentAuthorities, numberOfStudents);
        List<User> tutors = ModelFactory.generateActivatedUsers("tutor", new String[] { "tutor", "testgroup" }, tutorAuthorities, numberOfTutors);
        List<User> editors = ModelFactory.generateActivatedUsers("editor", new String[] { "editor", "testgroup" }, editorAuthorities, numberOfEditors);
        List<User> instructors = ModelFactory.generateActivatedUsers("instructor", new String[] { "instructor", "testgroup" }, instructorAuthorities, numberOfInstructors);
        User admin = ModelFactory.generateActivatedUser("admin");
        admin.setGroups(Set.of("admin"));
        admin.setAuthorities(adminAuthorities);
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

    public Lecture createCourseWithLecture(boolean saveLecture) {
        Course course = ModelFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");

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
