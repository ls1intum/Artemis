package de.tum.in.www1.artemis.lecture.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.*;

import de.tum.in.www1.artemis.domain.Attachment;
import de.tum.in.www1.artemis.domain.Authority;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.FileUploadExercise;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.DiagramType;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.participation.TutorParticipation;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.repository.AttachmentRepository;
import de.tum.in.www1.artemis.repository.AuthorityRepository;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.repository.SubmissionRepository;
import de.tum.in.www1.artemis.repository.TutorParticipationRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;

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
    private AttachmentRepository attachmentRepo;

    @Autowired
    private ExerciseRepository exerciseRepo;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private StudentParticipationRepository studentParticipationRepo;

    @Autowired
    private TutorParticipationRepository tutorParticipationRepo;

    @Autowired
    private ResultRepository resultRepo;

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

    public List<Course> createCoursesWithExercisesAndLectures(boolean withParticipations) {
        ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(5);
        ZonedDateTime futureTimestamp = ZonedDateTime.now().plusDays(5);
        ZonedDateTime futureFutureTimestamp = ZonedDateTime.now().plusDays(8);

        Course course1 = ModelFactory.generateCourse(null, pastTimestamp, futureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        Course course2 = ModelFactory.generateCourse(null, ZonedDateTime.now().minusDays(8), pastTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");

        ModelingExercise modelingExercise = ModelFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.ClassDiagram, course1);
        modelingExercise.setGradingInstructions("some grading instructions");
        modelingExercise.getCategories().add("Modeling");
        course1.addExercises(modelingExercise);

        de.tum.in.www1.artemis.domain.TextExercise textExercise = ModelFactory.generateTextExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, course1);
        textExercise.setGradingInstructions("some grading instructions");
        textExercise.getCategories().add("Text");
        course1.addExercises(textExercise);

        FileUploadExercise fileUploadExercise = ModelFactory.generateFileUploadExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, "png", course1);
        fileUploadExercise.setGradingInstructions("some grading instructions");
        fileUploadExercise.getCategories().add("File");
        course1.addExercises(fileUploadExercise);

        ProgrammingExercise programmingExercise = ModelFactory.generateProgrammingExercise(pastTimestamp, futureTimestamp, course1);
        programmingExercise.setGradingInstructions("some grading instructions");
        programmingExercise.getCategories().add("Programming");
        course1.addExercises(programmingExercise);

        QuizExercise quizExercise = ModelFactory.generateQuizExercise(pastTimestamp, futureTimestamp, course1);
        programmingExercise.getCategories().add("Quiz");
        course1.addExercises(quizExercise);

        Lecture lecture1 = ModelFactory.generateLecture(pastTimestamp, futureFutureTimestamp, course1);
        Attachment attachment1 = ModelFactory.generateAttachment(pastTimestamp, lecture1);
        lecture1.addAttachments(attachment1);
        course1.addLectures(lecture1);

        Lecture lecture2 = ModelFactory.generateLecture(pastTimestamp, futureFutureTimestamp, course1);
        Attachment attachment2 = ModelFactory.generateAttachment(pastTimestamp, lecture2);
        lecture2.addAttachments(attachment2);
        course1.addLectures(lecture2);

        course1 = courseRepo.save(course1);
        course2 = courseRepo.save(course2);

        lectureRepo.save(lecture1);
        lectureRepo.save(lecture2);

        attachmentRepo.save(attachment1);
        attachmentRepo.save(attachment2);

        modelingExercise = exerciseRepo.save(modelingExercise);
        textExercise = exerciseRepo.save(textExercise);
        exerciseRepo.save(fileUploadExercise);
        exerciseRepo.save(programmingExercise);
        exerciseRepo.save(quizExercise);

        if (withParticipations) {

            // create 5 tutor participations and 5 example submissions and connect all of them (to test the many-to-many relationship)
            var tutorParticipations = new ArrayList<TutorParticipation>();
            for (int i = 1; i < 6; i++) {
                var tutorParticipation = new TutorParticipation().tutor(getUserByLogin("tutor" + i));
                tutorParticipationRepo.save(tutorParticipation);
                tutorParticipations.add(tutorParticipation);
            }

            User user = (userRepo.findOneByLogin("student1")).get();
            StudentParticipation participation1 = ModelFactory.generateStudentParticipation(InitializationState.INITIALIZED, modelingExercise, user);
            StudentParticipation participation2 = ModelFactory.generateStudentParticipation(InitializationState.FINISHED, textExercise, user);
            StudentParticipation participation3 = ModelFactory.generateStudentParticipation(InitializationState.UNINITIALIZED, modelingExercise, user);

            Submission modelingSubmission1 = ModelFactory.generateModelingSubmission("model1", true);
            Submission modelingSubmission2 = ModelFactory.generateModelingSubmission("model2", true);
            Submission textSubmission = ModelFactory.generateTextSubmission("text", Language.ENGLISH, true);

            Result result1 = ModelFactory.generateResult(true, 10D);
            Result result2 = ModelFactory.generateResult(true, 12D);
            Result result3 = ModelFactory.generateResult(false, 0D);

            participation1 = studentParticipationRepo.save(participation1);
            participation2 = studentParticipationRepo.save(participation2);
            participation3 = studentParticipationRepo.save(participation3);

            submissionRepository.save(modelingSubmission1);
            submissionRepository.save(modelingSubmission2);
            submissionRepository.save(textSubmission);

            modelingSubmission1.setParticipation(participation1);
            textSubmission.setParticipation(participation2);
            modelingSubmission2.setParticipation(participation3);

            result1.setParticipation(participation1);
            result2.setParticipation(participation3);
            result3.setParticipation(participation2);

            result1 = resultRepo.save(result1);
            result2 = resultRepo.save(result2);
            result3 = resultRepo.save(result3);

            result1.setSubmission(modelingSubmission1);
            result2.setSubmission(modelingSubmission2);
            result3.setSubmission(textSubmission);

            modelingSubmission1.addResult(result1);
            modelingSubmission2.addResult(result2);
            textSubmission.addResult(result3);

            submissionRepository.save(modelingSubmission1);
            submissionRepository.save(modelingSubmission2);
            submissionRepository.save(textSubmission);
        }

        return Arrays.asList(course1, course2);
    }

    public User getUserByLogin(String login) {
        return userRepo.findOneWithAuthoritiesByLogin(login).orElseThrow(() -> new IllegalArgumentException("Provided login " + login + " does not exist in database"));
    }

    public void resetDatabase() {
        databaseCleanupService.clearDatabase();
    }

}
