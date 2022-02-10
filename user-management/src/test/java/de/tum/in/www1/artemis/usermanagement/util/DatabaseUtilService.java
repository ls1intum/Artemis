package de.tum.in.www1.artemis.usermanagement.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.*;

import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.DifficultyLevel;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseMode;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.ProjectType;
import de.tum.in.www1.artemis.util.DatabaseCleanupService;
import de.tum.in.www1.artemis.util.ModelFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Authority;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.AuthorityRepository;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.Role;

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
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private AuthorityRepository authorityRepository;

    @Autowired
    private DatabaseCleanupService databaseCleanupService;

    @Value("${info.guided-tour.course-group-students:#{null}}")
    private Optional<String> tutorialGroupStudents;

    @Value("${info.guided-tour.course-group-tutors:#{null}}")
    private Optional<String> tutorialGroupTutors;

    @Value("${info.guided-tour.course-group-editors:#{null}}")
    private Optional<String> tutorialGroupEditors;

    @Value("${info.guided-tour.course-group-instructors:#{null}}")
    private Optional<String> tutorialGroupInstructors;

    public void resetDatabase() {
        databaseCleanupService.clearDatabase();
    }

    /**
     * Adds the provided number of students and tutors into the user repository. Students login is a concatenation of the prefix "student" and a number counting from 1 to
     * numberOfStudents Tutors login is a concatenation of the prefix "tutor" and a number counting from 1 to numberOfStudents Tutors are all in the "tutor" group and students in
     * the "tumuser" group
     *
     * @param numberOfStudents    the number of students that will be added to the database
     * @param numberOfTutors      the number of tutors that will be added to the database
     * @param numberOfEditors     the number of editors that will be added to the database
     * @param numberOfInstructors the number of instructors that will be added to the database
     */
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

    public ProgrammingExercise addProgrammingExerciseToCourse(Course course, boolean enableStaticCodeAnalysis) {
        return addProgrammingExerciseToCourse(course, enableStaticCodeAnalysis, ProgrammingLanguage.JAVA);
    }

    public ProgrammingExercise addProgrammingExerciseToCourse(Course course, boolean enableStaticCodeAnalysis,
            ProgrammingLanguage programmingLanguage) {
        var programmingExercise = (ProgrammingExercise) new ProgrammingExercise().course(course);
        populateProgrammingExercise(programmingExercise, "TSTEXC", "Programming", enableStaticCodeAnalysis, programmingLanguage);
        programmingExercise.setPresentationScoreEnabled(course.getPresentationScore() != 0);

        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        course.addExercises(programmingExercise);

        assertThat(programmingExercise.getPresentationScoreEnabled()).as("presentation score is enabled").isTrue();

        return programmingExercise;
    }

    private void populateProgrammingExercise(ProgrammingExercise programmingExercise, String shortName, String title, boolean enableStaticCodeAnalysis,
            ProgrammingLanguage programmingLanguage) {
        programmingExercise.setProgrammingLanguage(programmingLanguage);
        programmingExercise.setShortName(shortName);
        programmingExercise.generateAndSetProjectKey();
        programmingExercise.setReleaseDate(ZonedDateTime.now().plusDays(1));
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().plusDays(5));
        programmingExercise.setBonusPoints(0D);
        programmingExercise.setPublishBuildPlanUrl(true);
        programmingExercise.setMaxPoints(42.0);
        programmingExercise.setDifficulty(DifficultyLevel.EASY);
        programmingExercise.setMode(ExerciseMode.INDIVIDUAL);
        programmingExercise.setProblemStatement("Lorem Ipsum");
        programmingExercise.setAssessmentType(AssessmentType.AUTOMATIC);
        programmingExercise.setGradingInstructions("Lorem Ipsum");
        programmingExercise.setTitle(title);
        if (programmingLanguage == ProgrammingLanguage.JAVA) {
            programmingExercise.setProjectType(ProjectType.ECLIPSE);
        }
        else if (programmingLanguage == ProgrammingLanguage.SWIFT) {
            programmingExercise.setProjectType(ProjectType.PLAIN);
        }
        else {
            programmingExercise.setProjectType(null);
        }
        programmingExercise.setAllowOnlineEditor(true);
        programmingExercise.setStaticCodeAnalysisEnabled(enableStaticCodeAnalysis);
        if (enableStaticCodeAnalysis) {
            programmingExercise.setMaxStaticCodeAnalysisPenalty(40);
        }
        // Note: no separators are allowed for Swift package names
        if (programmingLanguage == ProgrammingLanguage.SWIFT) {
            programmingExercise.setPackageName("swiftTest");
        }
        else {
            programmingExercise.setPackageName("de.test");
        }
        programmingExercise.setDueDate(ZonedDateTime.now().plusDays(2));
        programmingExercise.setAssessmentDueDate(ZonedDateTime.now().plusDays(3));
        programmingExercise.setCategories(new HashSet<>(Set.of("cat1", "cat2")));
        programmingExercise.setTestRepositoryUrl("http://nadnasidni.tum/scm/" + programmingExercise.getProjectKey() + "/" + programmingExercise.getProjectKey() + "-tests.git");
        programmingExercise.setShowTestNamesToStudents(false);
    }

    /**
     * @return An empty course
     */
    public Course addEmptyCourse() {
        Course course = ModelFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        courseRepo.save(course);
        assertThat(courseRepo.findById(course.getId())).as("empty course is initialized").isPresent();
        return course;
    }

     /**
     * @return A tutorial course with the names specified in application-dev or application-prod
     */
     public Course addTutorialCourse() {
         Course course = ModelFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), tutorialGroupStudents.get(), tutorialGroupTutors.get(),
         tutorialGroupEditors.get(), tutorialGroupInstructors.get());
         courseRepo.save(course);
         assertThat(courseRepo.findById(course.getId())).as("tutorial course is initialized").isPresent();
         return course;
     }
}
