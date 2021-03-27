package de.tum.in.www1.artemis.util;

import static com.google.gson.JsonParser.parseString;
import static org.assertj.core.api.Assertions.*;

import java.net.URL;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.*;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.domain.lecture.*;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.*;
import de.tum.in.www1.artemis.domain.quiz.*;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.AuthoritiesConstants;
import de.tum.in.www1.artemis.service.AssessmentService;
import de.tum.in.www1.artemis.service.ModelingSubmissionService;
import de.tum.in.www1.artemis.service.ParticipationService;
import de.tum.in.www1.artemis.web.rest.dto.PageableSearchDTO;

/** Service responsible for initializing the database with specific testdata for a testscenario */
@Service
public class DatabaseUtilService {

    private static final ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(1);

    private static final ZonedDateTime futureTimestamp = ZonedDateTime.now().plusDays(1);

    private static final ZonedDateTime futureFutureTimestamp = ZonedDateTime.now().plusDays(2);

    private static final Authority userAuthority = new Authority(AuthoritiesConstants.USER);

    private static final Authority tutorAuthority = new Authority(AuthoritiesConstants.TEACHING_ASSISTANT);

    private static final Authority instructorAuthority = new Authority(AuthoritiesConstants.INSTRUCTOR);

    private static final Authority adminAuthority = new Authority(AuthoritiesConstants.ADMIN);

    private static final Set<Authority> studentAuthorities = Set.of(userAuthority);

    private static final Set<Authority> tutorAuthorities = Set.of(userAuthority, tutorAuthority);

    private static final Set<Authority> instructorAuthorities = Set.of(userAuthority, tutorAuthority, instructorAuthority);

    private static final Set<Authority> adminAuthorities = Set.of(userAuthority, tutorAuthority, instructorAuthority, adminAuthority);

    @Autowired
    CourseRepository courseRepo;

    @Autowired
    LectureRepository lectureRepo;

    @Autowired
    ExerciseRepository exerciseRepo;

    @Autowired
    AttachmentRepository attachmentRepo;

    @Autowired
    ProgrammingExerciseTestCaseRepository testCaseRepository;

    @Autowired
    StaticCodeAnalysisCategoryRepository staticCodeAnalysisCategoryRepository;

    @Autowired
    ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    ExerciseHintRepository exerciseHintRepository;

    @Autowired
    UserRepository userRepo;

    @Autowired
    TeamRepository teamRepo;

    @Autowired
    ResultRepository resultRepo;

    @Autowired
    StudentParticipationRepository studentParticipationRepo;

    @Autowired
    ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepo;

    @Autowired
    TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepo;

    @Autowired
    SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepo;

    @Autowired
    ModelingSubmissionRepository modelingSubmissionRepo;

    @Autowired
    TextSubmissionRepository textSubmissionRepo;

    @Autowired
    ParticipationService participationService;

    @Autowired
    TextBlockRepository textBlockRepo;

    @Autowired
    FileUploadSubmissionRepository fileUploadSubmissionRepo;

    @Autowired
    SubmissionRepository submissionRepository;

    @Autowired
    SubmissionVersionRepository submissionVersionRepository;

    @Autowired
    ProgrammingSubmissionRepository programmingSubmissionRepo;

    @Autowired
    FeedbackRepository feedbackRepo;

    @Autowired
    ComplaintRepository complaintRepo;

    @Autowired
    ComplaintResponseRepository complaintResponseRepo;

    @Autowired
    ExampleSubmissionRepository exampleSubmissionRepo;

    @Autowired
    TutorParticipationRepository tutorParticipationRepo;

    @Autowired
    StudentQuestionRepository studentQuestionRepository;

    @Autowired
    ModelingSubmissionService modelSubmissionService;

    @Autowired
    AssessmentService assessmentService;

    @Autowired
    ProgrammingExerciseTestRepository programmingExerciseTestRepository;

    @Autowired
    private AuthorityRepository authorityRepository;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    GroupNotificationRepository groupNotificationRepository;

    @Autowired
    private ExerciseGroupRepository exerciseGroupRepository;

    @Autowired
    private StudentExamRepository studentExamRepository;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    @Autowired
    AttachmentUnitRepository attachmentUnitRepository;

    @Autowired
    AttachmentRepository attachmentRepository;

    @Autowired
    ExerciseUnitRepository exerciseUnitRepository;

    @Autowired
    TextUnitRepository textUnitRepository;

    @Autowired
    VideoUnitRepository videoUnitRepository;

    @Autowired
    private DatabaseCleanupService databaseCleanupService;

    @Value("${info.guided-tour.course-group-students:#{null}}")
    private Optional<String> tutorialGroupStudents;

    @Value("${info.guided-tour.course-group-tutors:#{null}}")
    private Optional<String> tutorialGroupTutors;

    @Value("${info.guided-tour.course-group-instructors:#{null}}")
    private Optional<String> tutorialGroupInstructors;

    public void resetDatabase() {
        databaseCleanupService.clearDatabase();
    }

    // TODO: this should probably be moved into another service
    public void changeUser(String username) {
        User user = getUserByLogin(username);
        List<GrantedAuthority> grantedAuthorities = new ArrayList<>();
        for (Authority authority : user.getAuthorities()) {
            grantedAuthorities.add(new SimpleGrantedAuthority(authority.getName()));
        }
        org.springframework.security.core.userdetails.User securityContextUser = new org.springframework.security.core.userdetails.User(user.getLogin(), user.getPassword(),
                grantedAuthorities);
        Authentication authentication = new UsernamePasswordAuthenticationToken(securityContextUser, securityContextUser.getPassword(), grantedAuthorities);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        TestSecurityContextHolder.setContext(context);
    }

    /**
     * Adds the provided number of students and tutors into the user repository. Students login is a concatenation of the prefix "student" and a number counting from 1 to
     * numberOfStudents Tutors login is a concatenation of the prefix "tutor" and a number counting from 1 to numberOfStudents Tutors are all in the "tutor" group and students in
     * the "tumuser" group
     *
     * @param numberOfStudents    the number of students that will be added to the database
     * @param numberOfTutors      the number of tutors that will be added to the database
     * @param numberOfInstructors the number of instructors that will be added to the database
     */
    public List<User> addUsers(int numberOfStudents, int numberOfTutors, int numberOfInstructors) {

        authorityRepository.saveAll(adminAuthorities);

        List<User> students = ModelFactory.generateActivatedUsers("student", new String[] { "tumuser", "testgroup" }, studentAuthorities, numberOfStudents);
        List<User> tutors = ModelFactory.generateActivatedUsers("tutor", new String[] { "tutor", "testgroup" }, tutorAuthorities, numberOfTutors);
        List<User> instructors = ModelFactory.generateActivatedUsers("instructor", new String[] { "instructor", "testgroup" }, instructorAuthorities, numberOfInstructors);
        User admin = ModelFactory.generateActivatedUser("admin");
        admin.setGroups(Set.of("admin"));
        admin.setAuthorities(adminAuthorities);
        List<User> usersToAdd = new ArrayList<>();
        usersToAdd.addAll(students);
        usersToAdd.addAll(tutors);
        usersToAdd.addAll(instructors);
        usersToAdd.add(admin);
        userRepo.saveAll(usersToAdd);
        assertThat(userRepo.findAll().size()).as("all users are created").isGreaterThanOrEqualTo(numberOfStudents + numberOfTutors + numberOfInstructors + 1);
        assertThat(userRepo.findAll()).as("users are correctly stored").containsAnyOf(usersToAdd.toArray(new User[0]));

        final var users = new ArrayList<>(students);
        users.addAll(tutors);
        users.addAll(instructors);
        users.add(admin);
        return users;
    }

    public List<Team> addTeamsForExercise(Exercise exercise, String shortNamePrefix, String loginPrefix, int numberOfTeams, User owner) {
        List<Team> teams = ModelFactory.generateTeamsForExercise(exercise, shortNamePrefix, loginPrefix, numberOfTeams, owner, null);
        userRepo.saveAll(teams.stream().map(Team::getStudents).flatMap(Collection::stream).collect(Collectors.toList()));
        return teamRepo.saveAll(teams);
    }

    public List<Team> addTeamsForExercise(Exercise exercise, String shortNamePrefix, int numberOfTeams, User owner) {
        return addTeamsForExercise(exercise, shortNamePrefix, "student", numberOfTeams, owner);
    }

    public List<Team> addTeamsForExercise(Exercise exercise, int numberOfTeams, User owner) {
        return addTeamsForExercise(exercise, "team", numberOfTeams, owner);
    }

    public List<Team> addTeamsForExerciseFixedTeamSize(Exercise exercise, int numberOfTeams, User owner, int noOfStudentsPerTeam) {
        List<Team> teams = ModelFactory.generateTeamsForExerciseFixedTeamSize(exercise, "team", "student", numberOfTeams, owner, null, "R", noOfStudentsPerTeam);
        userRepo.saveAll(teams.stream().map(Team::getStudents).flatMap(Collection::stream).collect(Collectors.toList()));
        return teamRepo.saveAll(teams);
    }

    public Team addTeamForExercise(Exercise exercise, User owner) {
        return addTeamsForExercise(exercise, 1, owner).get(0);
    }

    public Result addProgrammingParticipationWithResultForExercise(ProgrammingExercise exercise, String login) {
        var storedParticipation = programmingExerciseStudentParticipationRepo.findByExerciseIdAndStudentLogin(exercise.getId(), login);
        final StudentParticipation studentParticipation;
        if (storedParticipation.isEmpty()) {
            final var user = getUserByLogin(login);
            final var participation = new ProgrammingExerciseStudentParticipation();
            final var buildPlanId = exercise.getProjectKey().toUpperCase() + "-" + login.toUpperCase();
            final var repoName = (exercise.getProjectKey() + "-" + login).toLowerCase();
            participation.setInitializationDate(ZonedDateTime.now());
            participation.setParticipant(user);
            participation.setBuildPlanId(buildPlanId);
            participation.setProgrammingExercise(exercise);
            participation.setInitializationState(InitializationState.INITIALIZED);
            participation.setRepositoryUrl(String.format("http://some.test.url/%s/%s.git", exercise.getCourseViaExerciseGroupOrCourseMember().getShortName(), repoName));
            programmingExerciseStudentParticipationRepo.save(participation);
            storedParticipation = programmingExerciseStudentParticipationRepo.findByExerciseIdAndStudentLogin(exercise.getId(), login);
            assertThat(storedParticipation).isPresent();
            studentParticipation = studentParticipationRepo.findWithEagerLegalSubmissionsAndResultsAssessorsById(storedParticipation.get().getId()).get();
        }
        else {
            studentParticipation = storedParticipation.get();
        }
        return addResultToParticipation(null, null, studentParticipation);
    }

    public void addInstructor(final String instructorGroup, final String instructorName) {
        var instructor = ModelFactory.generateActivatedUsers(instructorName, new String[] { instructorGroup, "testgroup" }, instructorAuthorities, 1).get(0);
        instructor = userRepo.save(instructor);

        assertThat(instructor.getId()).as("Instructor has been created").isNotNull();
    }

    public void addTeachingAssistant(final String taGroup, final String taName) {
        var ta = ModelFactory.generateActivatedUsers(taName, new String[] { taGroup, "testgroup" }, tutorAuthorities, 1).get(0);
        ta = userRepo.save(ta);

        assertThat(ta.getId()).as("Teaching assistant has been created").isNotNull();
    }

    public Lecture createCourseWithLecture(boolean saveLecture) {
        Course course = ModelFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), "tumuser", "tutor", "instructor");

        Lecture lecture = new Lecture();
        lecture.setDescription("Test Lecture");
        lecture.setCourse(course);
        courseRepo.save(course);
        if (saveLecture) {
            lectureRepo.save(lecture);
        }
        return lecture;
    }

    public Course createCourse() {
        Course course = ModelFactory.generateCourse(null, pastTimestamp, futureTimestamp, new HashSet<>(), "tumuser", "tutor", "instructor");
        return courseRepo.save(course);
    }

    public TextExercise createIndividualTextExercise(Course course, ZonedDateTime pastTimestamp, ZonedDateTime futureTimestamp, ZonedDateTime futureFutureTimestamp) {
        TextExercise textExercise = ModelFactory.generateTextExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, course);
        textExercise.setMaxPoints(10.0);
        textExercise.setBonusPoints(0.0);
        return exerciseRepo.save(textExercise);
    }

    public Team createTeam(Set<User> students, User owner, Exercise exercise) {
        Team team = new Team();
        for (User student : students) {
            team.addStudents(student);
        }
        team.setOwner(owner);
        team.setShortName("team1");
        team.setName("team1");
        team.setExercise(exercise);
        return teamRepo.saveAndFlush(team);
    }

    public TextExercise createTeamTextExercise(Course course, ZonedDateTime pastTimestamp, ZonedDateTime futureTimestamp, ZonedDateTime futureFutureTimestamp) {
        TextExercise teamTextExercise = ModelFactory.generateTextExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, course);
        teamTextExercise.setMaxPoints(10.0);
        teamTextExercise.setBonusPoints(0.0);
        teamTextExercise.setMode(ExerciseMode.TEAM);
        return exerciseRepo.save(teamTextExercise);
    }

    public Result createParticipationSubmissionAndResult(Long idOfExercise, Participant participant, Double pointsOfExercise, Double bonusPointsOfExercise, long scoreAwarded,
            boolean rated) {
        Exercise exercise = exerciseRepo.findById(idOfExercise).get();

        if (!exercise.getMaxPoints().equals(pointsOfExercise)) {
            exercise.setMaxPoints(pointsOfExercise);
        }
        if (!exercise.getBonusPoints().equals(bonusPointsOfExercise)) {
            exercise.setBonusPoints(bonusPointsOfExercise);
        }
        exercise = exerciseRepo.saveAndFlush(exercise);

        StudentParticipation studentParticipation = participationService.startExercise(exercise, participant, false);

        return createSubmissionAndResult(studentParticipation, scoreAwarded, rated);
    }

    public Result createSubmissionAndResult(StudentParticipation studentParticipation, long scoreAwarded, boolean rated) {
        Exercise exercise = studentParticipation.getExercise();
        Submission submission;
        if (exercise instanceof ProgrammingExercise) {
            submission = new ProgrammingSubmission();
        }
        else if (exercise instanceof ModelingExercise) {
            submission = new ModelingSubmission();
        }
        else if (exercise instanceof TextExercise) {
            submission = new TextSubmission();
        }
        else if (exercise instanceof FileUploadExercise) {
            submission = new FileUploadSubmission();
        }
        else if (exercise instanceof QuizExercise) {
            submission = new QuizSubmission();
        }
        else {
            throw new RuntimeException("Unsupported exercise type: " + exercise);
        }

        submission.setType(SubmissionType.MANUAL);
        submission.setParticipation(studentParticipation);
        submission = submissionRepository.saveAndFlush(submission);

        Result result = ModelFactory.generateResult(rated, scoreAwarded);
        result.setParticipation(studentParticipation);
        result.setSubmission(submission);
        result.completionDate(ZonedDateTime.now());
        submission.addResult(result);
        submission = submissionRepository.saveAndFlush(submission);
        submission.getResults().get(0);
        return submission.getResults().get(0);
    }

    public Course createCourseWithExamAndExerciseGroupAndExercises(User user, ZonedDateTime visible, ZonedDateTime start, ZonedDateTime end) {
        Course course = createCourse();
        Exam exam = addExam(course, user, visible, start, end);
        course.addExam(exam);
        addExerciseGroupsAndExercisesToExam(exam, false);
        return courseRepo.save(course);
    }

    public Course createCourseWithExamAndExerciseGroupAndExercises(User user) {
        Course course = createCourse();
        Exam exam = addExam(course, user, ZonedDateTime.now().minusMinutes(1), ZonedDateTime.now(), ZonedDateTime.now().plusMinutes(1));
        course.addExam(exam);
        addExerciseGroupsAndExercisesToExam(exam, false);
        return courseRepo.save(course);
    }

    public List<Course> createCoursesWithExercisesAndLecturesAndLectureUnits(boolean withParticiptions) throws Exception {
        List<Course> courses = this.createCoursesWithExercisesAndLectures(withParticiptions);
        Course course1 = this.courseRepo.findWithEagerExercisesAndLecturesById(courses.get(0).getId());
        Lecture lecture1 = course1.getLectures().stream().findFirst().get();
        TextExercise textExercise = textExerciseRepository.findByCourseId(course1.getId()).stream().findFirst().get();
        VideoUnit videoUnit = createVideoUnit();
        TextUnit textUnit = createTextUnit();
        AttachmentUnit attachmentUnit = createAttachmentUnit();
        ExerciseUnit exerciseUnit = createExerciseUnit(textExercise);
        addLectureUnitsToLecture(lecture1, Set.of(videoUnit, textUnit, attachmentUnit, exerciseUnit));
        return courses;
    }

    public Lecture addLectureUnitsToLecture(Lecture lecture, Set<LectureUnit> lectureUnits) {
        Lecture l = lectureRepo.findByIdWithStudentQuestionsAndLectureUnitsAndLearningGoals(lecture.getId()).get();
        for (LectureUnit lectureUnit : lectureUnits) {
            l.addLectureUnit(lectureUnit);
        }
        return lectureRepo.save(l);
    }

    public ExerciseUnit createExerciseUnit(Exercise exercise) {
        ExerciseUnit exerciseUnit = new ExerciseUnit();
        exerciseUnit.setExercise(exercise);
        return exerciseUnitRepository.save(exerciseUnit);
    }

    public AttachmentUnit createAttachmentUnit() {
        Attachment attachmentOfAttachmentUnit = new Attachment().attachmentType(AttachmentType.FILE).link("files/temp/example.txt").name("example");
        AttachmentUnit attachmentUnit = new AttachmentUnit();
        attachmentUnit.setDescription("Lorem Ipsum");
        attachmentUnit = attachmentUnitRepository.save(attachmentUnit);
        attachmentOfAttachmentUnit.setAttachmentUnit(attachmentUnit);
        attachmentOfAttachmentUnit = attachmentRepository.save(attachmentOfAttachmentUnit);
        attachmentUnit.setAttachment(attachmentOfAttachmentUnit);
        return attachmentUnitRepository.save(attachmentUnit);
    }

    public TextUnit createTextUnit() {
        TextUnit textUnit = new TextUnit();
        textUnit.setContent("Lorem Ipsum");
        return textUnitRepository.save(textUnit);
    }

    public VideoUnit createVideoUnit() {
        VideoUnit videoUnit = new VideoUnit();
        videoUnit.setDescription("Lorem Ipsum");
        videoUnit.setSource("Some URL");
        return videoUnitRepository.save(videoUnit);
    }

    public List<Course> createCoursesWithExercisesAndLectures(boolean withParticipations) throws Exception {
        ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(5);
        ZonedDateTime futureTimestamp = ZonedDateTime.now().plusDays(5);
        ZonedDateTime futureFutureTimestamp = ZonedDateTime.now().plusDays(8);

        Course course1 = ModelFactory.generateCourse(null, pastTimestamp, futureTimestamp, new HashSet<>(), "tumuser", "tutor", "instructor");
        Course course2 = ModelFactory.generateCourse(null, ZonedDateTime.now().minusDays(8), pastTimestamp, new HashSet<>(), "tumuser", "tutor", "instructor");

        ModelingExercise modelingExercise = ModelFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.ClassDiagram, course1);
        modelingExercise.setGradingInstructions("some grading instructions");
        addGradingInstructionsToExercise(modelingExercise);
        modelingExercise.getCategories().add("Modeling");
        course1.addExercises(modelingExercise);

        TextExercise textExercise = ModelFactory.generateTextExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, course1);
        textExercise.setGradingInstructions("some grading instructions");
        addGradingInstructionsToExercise(textExercise);
        textExercise.getCategories().add("Text");
        course1.addExercises(textExercise);

        FileUploadExercise fileUploadExercise = ModelFactory.generateFileUploadExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, "png", course1);
        fileUploadExercise.setGradingInstructions("some grading instructions");
        fileUploadExercise.setSampleSolution("Sample Solution");
        addGradingInstructionsToExercise(fileUploadExercise);
        fileUploadExercise.getCategories().add("File");
        course1.addExercises(fileUploadExercise);

        ProgrammingExercise programmingExercise = ModelFactory.generateProgrammingExercise(pastTimestamp, futureTimestamp, course1);
        programmingExercise.setGradingInstructions("some grading instructions");
        addGradingInstructionsToExercise(programmingExercise);
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
        fileUploadExercise = exerciseRepo.save(fileUploadExercise);
        programmingExercise = exerciseRepo.save(programmingExercise);
        quizExercise = exerciseRepo.save(quizExercise);

        if (withParticipations) {

            // create 5 tutor participations and 5 example submissions and connect all of them (to test the many-to-many relationship)
            var tutorParticipations = new ArrayList<TutorParticipation>();
            for (int i = 1; i < 6; i++) {
                var tutorParticipation = new TutorParticipation().tutor(getUserByLogin("tutor" + i));
                tutorParticipationRepo.save(tutorParticipation);
                tutorParticipations.add(tutorParticipation);
            }

            for (int i = 0; i < 5; i++) {
                String validModel = FileUtils.loadFileFromResources("test-data/model-submission/model.54727.json");
                var exampleSubmission = addExampleSubmission(generateExampleSubmission(validModel, modelingExercise, true));
                exampleSubmission.assessmentExplanation("exp");
                for (var tutorParticipation : tutorParticipations) {
                    exampleSubmission.addTutorParticipations(tutorParticipation);
                }
                exampleSubmissionRepo.save(exampleSubmission);
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

    public List<StudentQuestion> createCourseWithExerciseAndStudentQuestions() {
        ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(5);
        ZonedDateTime futureTimestamp = ZonedDateTime.now().plusDays(5);
        ZonedDateTime futureFutureTimestamp = ZonedDateTime.now().plusDays(8);

        Course course1 = createCourse();

        TextExercise textExercise = ModelFactory.generateTextExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, course1);
        textExercise.setGradingInstructions("some grading instructions");
        addGradingInstructionsToExercise(textExercise);
        textExercise.getCategories().add("Text");
        course1.addExercises(textExercise);

        courseRepo.save(course1);
        textExercise = exerciseRepo.save(textExercise);

        List<StudentQuestion> studentQuestions = new ArrayList<>();
        StudentQuestion studentQuestion1 = new StudentQuestion();
        studentQuestion1.setExercise(textExercise);
        studentQuestion1.setQuestionText("Test Student Question 1");
        studentQuestion1.setVisibleForStudents(true);
        studentQuestion1.setAuthor(getUserByLogin("student1"));
        studentQuestionRepository.save(studentQuestion1);
        studentQuestions.add(studentQuestion1);

        StudentQuestion studentQuestion2 = new StudentQuestion();
        studentQuestion2.setExercise(textExercise);
        studentQuestion2.setQuestionText("Test Student Question 2");
        studentQuestion2.setVisibleForStudents(true);
        studentQuestion2.setAuthor(getUserByLogin("student2"));
        studentQuestionRepository.save(studentQuestion2);
        studentQuestions.add(studentQuestion2);

        return studentQuestions;
    }

    public List<StudentQuestion> createCourseWithExerciseAndLectureAndStudentQuestions() {
        ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(5);
        ZonedDateTime futureTimestamp = ZonedDateTime.now().plusDays(5);
        ZonedDateTime futureFutureTimestamp = ZonedDateTime.now().plusDays(8);

        Course course1 = ModelFactory.generateCourse(null, pastTimestamp, futureTimestamp, new HashSet<>(), "tumuser", "tutor", "instructor");

        TextExercise textExercise = ModelFactory.generateTextExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, course1);
        textExercise.setGradingInstructions("some grading instructions");
        addGradingInstructionsToExercise(textExercise);
        textExercise.getCategories().add("Text");
        course1.addExercises(textExercise);

        Lecture lecture = ModelFactory.generateLecture(pastTimestamp, futureFutureTimestamp, course1);
        lecture.setDescription("a test lecture");
        course1.addLectures(lecture);

        courseRepo.save(course1);
        textExercise = exerciseRepo.save(textExercise);
        lecture = lectureRepo.save(lecture);

        List<StudentQuestion> studentQuestions = new ArrayList<>();
        StudentQuestion studentQuestion1 = new StudentQuestion();
        studentQuestion1.setExercise(textExercise);
        studentQuestion1.setQuestionText("Test Student Question 1");
        studentQuestion1.setVisibleForStudents(true);
        studentQuestion1.setAuthor(getUserByLogin("student1"));
        studentQuestionRepository.save(studentQuestion1);
        studentQuestions.add(studentQuestion1);

        StudentQuestion studentQuestion2 = new StudentQuestion();
        studentQuestion2.setExercise(textExercise);
        studentQuestion2.setQuestionText("Test Student Question 2");
        studentQuestion2.setVisibleForStudents(true);
        studentQuestion2.setAuthor(getUserByLogin("student2"));
        studentQuestionRepository.save(studentQuestion2);
        studentQuestions.add(studentQuestion2);

        StudentQuestion studentQuestion3 = new StudentQuestion();
        studentQuestion3.setLecture(lecture);
        studentQuestion3.setQuestionText("Test Student Question 3");
        studentQuestion3.setVisibleForStudents(true);
        studentQuestion3.setAuthor(getUserByLogin("student1"));
        studentQuestionRepository.save(studentQuestion3);
        studentQuestions.add(studentQuestion3);

        StudentQuestion studentQuestion4 = new StudentQuestion();
        studentQuestion4.setLecture(lecture);
        studentQuestion4.setQuestionText("Test Student Question 4");
        studentQuestion4.setVisibleForStudents(true);
        studentQuestion4.setAuthor(getUserByLogin("student2"));
        studentQuestionRepository.save(studentQuestion4);
        studentQuestions.add(studentQuestion2);

        return studentQuestions;
    }

    public Course createCourseWithAllExerciseTypesAndParticipationsAndSubmissionsAndResults(boolean hasAssessmentDueDatePassed) {
        Course course = ModelFactory.generateCourse(null, pastTimestamp, futureTimestamp, new HashSet<>(), "tumuser", "tutor", "instructor");

        ModelingExercise modelingExercise = ModelFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.ClassDiagram, course);
        TextExercise textExercise = ModelFactory.generateTextExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, course);
        FileUploadExercise fileUploadExercise = ModelFactory.generateFileUploadExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, "png", course);
        ProgrammingExercise programmingExercise = ModelFactory.generateProgrammingExercise(pastTimestamp, futureTimestamp, course);
        QuizExercise quizExercise = ModelFactory.generateQuizExercise(pastTimestamp, futureTimestamp, course);

        // Set assessment due dates
        if (hasAssessmentDueDatePassed) {
            modelingExercise.setAssessmentDueDate(ZonedDateTime.now().minusMinutes(10L));
            textExercise.setAssessmentDueDate(ZonedDateTime.now().minusMinutes(10L));
            fileUploadExercise.setAssessmentDueDate(ZonedDateTime.now().minusMinutes(10L));
            programmingExercise.setAssessmentDueDate(ZonedDateTime.now().minusMinutes(10L));
            quizExercise.setAssessmentDueDate(ZonedDateTime.now().minusMinutes(10L));
        }
        else {
            modelingExercise.setAssessmentDueDate(ZonedDateTime.now().plusMinutes(10L));
            textExercise.setAssessmentDueDate(ZonedDateTime.now().plusMinutes(10L));
            fileUploadExercise.setAssessmentDueDate(ZonedDateTime.now().plusMinutes(10L));
            programmingExercise.setAssessmentDueDate(ZonedDateTime.now().plusMinutes(10L));
            quizExercise.setAssessmentDueDate(ZonedDateTime.now().plusMinutes(10L));
        }

        // Add exercises to course
        course.addExercises(modelingExercise);
        course.addExercises(textExercise);
        course.addExercises(fileUploadExercise);
        course.addExercises(programmingExercise);
        course.addExercises(quizExercise);

        // Save course and exercises to database
        Course courseSaved = courseRepo.save(course);
        modelingExercise = exerciseRepo.save(modelingExercise);
        textExercise = exerciseRepo.save(textExercise);
        fileUploadExercise = exerciseRepo.save(fileUploadExercise);
        programmingExercise = exerciseRepo.save(programmingExercise);
        quizExercise = exerciseRepo.save(quizExercise);

        // Get user and setup participations
        User user = (userRepo.findOneByLogin("student1")).get();
        StudentParticipation participationModeling = ModelFactory.generateStudentParticipation(InitializationState.FINISHED, modelingExercise, user);
        StudentParticipation participationText = ModelFactory.generateStudentParticipation(InitializationState.FINISHED, textExercise, user);
        StudentParticipation participationFileUpload = ModelFactory.generateStudentParticipation(InitializationState.FINISHED, fileUploadExercise, user);
        StudentParticipation participationQuiz = ModelFactory.generateStudentParticipation(InitializationState.FINISHED, quizExercise, user);
        StudentParticipation participationProgramming = ModelFactory.generateStudentParticipation(InitializationState.INITIALIZED, programmingExercise, user);

        // Save participations
        participationModeling = studentParticipationRepo.save(participationModeling);
        participationText = studentParticipationRepo.save(participationText);
        participationFileUpload = studentParticipationRepo.save(participationFileUpload);
        participationQuiz = studentParticipationRepo.save(participationQuiz);
        participationProgramming = studentParticipationRepo.save(participationProgramming);

        // Setup results
        Result resultModeling = ModelFactory.generateResult(true, 10D);
        resultModeling.setAssessmentType(AssessmentType.MANUAL);
        resultModeling.setCompletionDate(ZonedDateTime.now());

        Result resultText = ModelFactory.generateResult(true, 12D);
        resultText.setAssessmentType(AssessmentType.MANUAL);
        resultText.setCompletionDate(ZonedDateTime.now());

        Result resultFileUpload = ModelFactory.generateResult(true, 0D);
        resultFileUpload.setAssessmentType(AssessmentType.MANUAL);
        resultFileUpload.setCompletionDate(ZonedDateTime.now());

        Result resultQuiz = ModelFactory.generateResult(true, 0D);
        resultQuiz.setAssessmentType(AssessmentType.AUTOMATIC);
        resultQuiz.setCompletionDate(ZonedDateTime.now());

        Result resultProgramming = ModelFactory.generateResult(true, 20D);
        resultProgramming.setAssessmentType(AssessmentType.AUTOMATIC);
        resultProgramming.setCompletionDate(ZonedDateTime.now());

        // Connect participations to results and vice versa
        resultModeling.setParticipation(participationModeling);
        resultText.setParticipation(participationText);
        resultFileUpload.setParticipation(participationFileUpload);
        resultQuiz.setParticipation(participationQuiz);
        resultProgramming.setParticipation(participationProgramming);

        participationModeling.addResult(resultModeling);
        participationText.addResult(resultText);
        participationFileUpload.addResult(resultFileUpload);
        participationQuiz.addResult(resultQuiz);
        participationProgramming.addResult(resultProgramming);

        // Save results and participations
        resultModeling = resultRepo.save(resultModeling);
        resultText = resultRepo.save(resultText);
        resultFileUpload = resultRepo.save(resultFileUpload);
        resultQuiz = resultRepo.save(resultQuiz);
        resultProgramming = resultRepo.save(resultProgramming);

        participationModeling = studentParticipationRepo.save(participationModeling);
        participationText = studentParticipationRepo.save(participationText);
        participationFileUpload = studentParticipationRepo.save(participationFileUpload);
        participationQuiz = studentParticipationRepo.save(participationQuiz);
        participationProgramming = studentParticipationRepo.save(participationProgramming);

        // Connect exercises with participations
        modelingExercise.addParticipation(participationModeling);
        textExercise.addParticipation(participationText);
        fileUploadExercise.addParticipation(participationFileUpload);
        quizExercise.addParticipation(participationQuiz);
        programmingExercise.addParticipation(participationProgramming);

        // Setup submissions and connect with participations
        ModelingSubmission modelingSubmission = ModelFactory.generateModelingSubmission("model1", true);
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("text of text submission", Language.ENGLISH, true);
        FileUploadSubmission fileUploadSubmission = ModelFactory.generateFileUploadSubmission(true);
        QuizSubmission quizSubmission = ModelFactory.generateQuizSubmission(true);
        ProgrammingSubmission programmingSubmission = ModelFactory.generateProgrammingSubmission(true);

        // Save submissions
        modelingSubmission = submissionRepository.save(modelingSubmission);
        textSubmission = submissionRepository.save(textSubmission);
        fileUploadSubmission = submissionRepository.save(fileUploadSubmission);
        quizSubmission = submissionRepository.save(quizSubmission);
        programmingSubmission = submissionRepository.save(programmingSubmission);

        modelingSubmission.setParticipation(participationModeling);
        modelingSubmission.addResult(resultModeling);
        textSubmission.setParticipation(participationText);
        textSubmission.addResult(resultText);
        fileUploadSubmission.setParticipation(participationFileUpload);
        fileUploadSubmission.addResult(resultFileUpload);
        quizSubmission.setParticipation(participationQuiz);
        quizSubmission.addResult(resultQuiz);
        programmingSubmission.setParticipation(participationProgramming);
        programmingSubmission.addResult(resultProgramming);

        // Save submissions
        modelingSubmission = submissionRepository.save(modelingSubmission);
        textSubmission = submissionRepository.save(textSubmission);
        fileUploadSubmission = submissionRepository.save(fileUploadSubmission);
        quizSubmission = submissionRepository.save(quizSubmission);
        programmingSubmission = submissionRepository.save(programmingSubmission);

        // Save exercises
        exerciseRepo.save(modelingExercise);
        exerciseRepo.save(textExercise);
        exerciseRepo.save(fileUploadExercise);
        exerciseRepo.save(programmingExercise);
        exerciseRepo.save(quizExercise);

        // Connect participations with submissions
        participationModeling.setSubmissions(Set.of(modelingSubmission));
        participationText.setSubmissions(Set.of(textSubmission));
        participationFileUpload.setSubmissions(Set.of(fileUploadSubmission));
        participationQuiz.setSubmissions(Set.of(quizSubmission));
        participationProgramming.setSubmissions(Set.of(programmingSubmission));

        // Save participations
        studentParticipationRepo.save(participationModeling);
        studentParticipationRepo.save(participationText);
        studentParticipationRepo.save(participationFileUpload);
        studentParticipationRepo.save(participationQuiz);
        studentParticipationRepo.save(participationProgramming);

        return courseSaved;
    }

    public StudentExam setupTestRunForExamWithExerciseGroupsForInstructor(Exam exam, User instructor, List<ExerciseGroup> exerciseGroupsWithExercises) {
        List<Exercise> exercises = new ArrayList<>();
        exerciseGroupsWithExercises.forEach(exerciseGroup -> exercises.add(exerciseGroup.getExercises().iterator().next()));
        var testRun = generateTestRunForInstructor(exam, instructor, exercises);
        return studentExamRepository.save(testRun);
    }

    public StudentExam generateTestRunForInstructor(Exam exam, User instructor, List<Exercise> exercises) {
        var testRun = ModelFactory.generateExamTestRun(exam);
        testRun.setUser(instructor);
        examRepository.findWithExerciseGroupsAndExercisesById(exam.getId()).get();
        for (final var exercise : exercises) {
            testRun.addExercise(exercise);
            assertThat(exercise.isExamExercise()).isTrue();
            Submission submission;
            if (exercise instanceof ModelingExercise) {
                submission = addModelingSubmission((ModelingExercise) exercise, ModelFactory.generateModelingSubmission("", false), instructor.getLogin());
            }
            else if (exercise instanceof TextExercise) {
                submission = saveTextSubmission((TextExercise) exercise, ModelFactory.generateTextSubmission("", null, false), instructor.getLogin());
            }
            else {
                submission = new ProgrammingSubmission().submitted(true);
                addProgrammingSubmission((ProgrammingExercise) exercise, (ProgrammingSubmission) submission, instructor.getLogin());
                submission = submissionRepository.save(submission);
            }
            var studentParticipation = (StudentParticipation) submission.getParticipation();
            studentParticipation.setTestRun(true);
            studentParticipationRepo.save(studentParticipation);
        }
        return testRun;
    }

    public Exam setupExamWithExerciseGroupsExercisesRegisteredStudents(Course course) {
        var exam = ModelFactory.generateExam(course);
        exam.setNumberOfExercisesInExam(4);
        exam.setRandomizeExerciseOrder(true);
        exam.setStartDate(ZonedDateTime.now().plusHours(2));
        exam.setEndDate(ZonedDateTime.now().plusHours(4));
        exam.setMaxPoints(20);
        exam = examRepository.save(exam);

        // add exercise groups: 3 mandatory, 2 optional
        ModelFactory.generateExerciseGroup(true, exam);
        ModelFactory.generateExerciseGroup(true, exam);
        ModelFactory.generateExerciseGroup(true, exam);
        ModelFactory.generateExerciseGroup(false, exam);
        ModelFactory.generateExerciseGroup(false, exam);
        exam = examRepository.save(exam);

        // TODO: also add other exercise types

        // add exercises
        var exercise1a = ModelFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(0));
        var exercise1b = ModelFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(0));
        var exercise1c = ModelFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(0));
        exerciseRepo.saveAll(List.of(exercise1a, exercise1b, exercise1c));

        var exercise2a = ModelFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(1));
        var exercise2b = ModelFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(1));
        var exercise2c = ModelFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(1));
        exerciseRepo.saveAll(List.of(exercise2a, exercise2b, exercise2c));

        var exercise3a = ModelFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(2));
        var exercise3b = ModelFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(2));
        var exercise3c = ModelFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(2));
        exerciseRepo.saveAll(List.of(exercise3a, exercise3b, exercise3c));

        var exercise4a = ModelFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(3));
        var exercise4b = ModelFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(3));
        var exercise4c = ModelFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(3));
        exerciseRepo.saveAll(List.of(exercise4a, exercise4b, exercise4c));

        var exercise5a = ModelFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(4));
        var exercise5b = ModelFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(4));
        var exercise5c = ModelFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(4));
        exerciseRepo.saveAll(List.of(exercise5a, exercise5b, exercise5c));

        // register user
        var student1 = getUserByLogin("student1");
        var student2 = getUserByLogin("student2");
        var student3 = getUserByLogin("student3");
        var student4 = getUserByLogin("student4");
        var registeredUsers = Set.of(student1, student2, student3, student4);

        exam.setRegisteredUsers(registeredUsers);
        exam = examRepository.save(exam);
        return exam;
    }

    public Exam addExam(Course course) {
        Exam exam = ModelFactory.generateExam(course);
        examRepository.save(exam);
        return exam;
    }

    public Exam addExam(Course course, User user, ZonedDateTime visibleDate, ZonedDateTime startDate, ZonedDateTime endDate) {
        Exam exam = ModelFactory.generateExam(course);
        exam.addRegisteredUser(user);
        exam.setVisibleDate(visibleDate);
        exam.setStartDate(startDate);
        exam.setEndDate(endDate);
        exam.setNumberOfCorrectionRoundsInExam(1);
        examRepository.save(exam);
        return exam;
    }

    public Exam addExamWithExerciseGroup(Course course, boolean mandatory) {
        Exam exam = ModelFactory.generateExam(course);
        ModelFactory.generateExerciseGroup(mandatory, exam);
        examRepository.save(exam);
        return exam;
    }

    public Exam addExam(Course course, ZonedDateTime visibleDate, ZonedDateTime startDate, ZonedDateTime endDate) {
        Exam exam = ModelFactory.generateExam(course);
        exam.setVisibleDate(visibleDate);
        exam.setStartDate(startDate);
        exam.setEndDate(endDate);
        exam.setGracePeriod(180);
        examRepository.save(exam);
        return exam;
    }

    public Exam addExam(Course course, ZonedDateTime visibleDate, ZonedDateTime startDate, ZonedDateTime endDate, ZonedDateTime publishResultDate) {
        Exam exam = ModelFactory.generateExam(course);
        exam.setVisibleDate(visibleDate);
        exam.setStartDate(startDate);
        exam.setEndDate(endDate);
        exam.setPublishResultsDate(publishResultDate);
        exam.setGracePeriod(180);
        examRepository.save(exam);
        return exam;
    }

    public Exam addActiveExamWithRegisteredUser(Course course, User user) {
        Exam exam = ModelFactory.generateExam(course);
        exam.setStartDate(ZonedDateTime.now().minusHours(1));
        exam.setEndDate(ZonedDateTime.now().plusHours(1));
        exam.addRegisteredUser(user);
        examRepository.save(exam);
        var studentExam = new StudentExam();
        studentExam.setExam(exam);
        studentExam.setTestRun(false);
        studentExam.setUser(user);
        studentExam.setWorkingTime((int) Duration.between(exam.getStartDate(), exam.getEndDate()).toSeconds());
        studentExamRepository.save(studentExam);
        return exam;
    }

    public StudentExam addStudentExam(Exam exam) {
        StudentExam studentExam = ModelFactory.generateStudentExam(exam);
        studentExamRepository.save(studentExam);
        return studentExam;
    }

    public Exam addExerciseGroupsAndExercisesToExam(Exam exam, boolean withProgrammingExercise) {
        ModelFactory.generateExerciseGroup(true, exam); // text
        ModelFactory.generateExerciseGroup(true, exam); // quiz
        ModelFactory.generateExerciseGroup(true, exam); // file upload
        ModelFactory.generateExerciseGroup(true, exam); // modeling
        ModelFactory.generateExerciseGroup(true, exam); // bonus text
        ModelFactory.generateExerciseGroup(true, exam); // not included text
        exam.setNumberOfExercisesInExam(6);
        exam.setMaxPoints(24);
        exam = examRepository.save(exam);
        // NOTE: we have to reassign, otherwise we get problems, because the objects have changed
        var exerciseGroup0 = exam.getExerciseGroups().get(0);
        var exerciseGroup1 = exam.getExerciseGroups().get(1);
        var exerciseGroup2 = exam.getExerciseGroups().get(2);
        var exerciseGroup3 = exam.getExerciseGroups().get(3);
        var exerciseGroup4 = exam.getExerciseGroups().get(4);
        var exerciseGroup5 = exam.getExerciseGroups().get(5);

        TextExercise textExercise1 = ModelFactory.generateTextExerciseForExam(exerciseGroup0);
        TextExercise textExercise2 = ModelFactory.generateTextExerciseForExam(exerciseGroup0);
        exerciseGroup0.setExercises(Set.of(textExercise1, textExercise2));
        exerciseRepo.save(textExercise1);
        exerciseRepo.save(textExercise2);

        QuizExercise quizExercise1 = createQuizForExam(exerciseGroup1);
        QuizExercise quizExercise2 = createQuizForExam(exerciseGroup1);
        exerciseGroup1.setExercises(Set.of(quizExercise1, quizExercise2));
        exerciseRepo.save(quizExercise1);
        exerciseRepo.save(quizExercise2);

        FileUploadExercise fileUploadExercise1 = ModelFactory.generateFileUploadExerciseForExam("pdf", exerciseGroup2);
        FileUploadExercise fileUploadExercise2 = ModelFactory.generateFileUploadExerciseForExam("pdf", exerciseGroup2);
        exerciseGroup2.setExercises(Set.of(fileUploadExercise1, fileUploadExercise2));
        exerciseRepo.save(fileUploadExercise1);
        exerciseRepo.save(fileUploadExercise2);

        ModelingExercise modelingExercise1 = ModelFactory.generateModelingExerciseForExam(DiagramType.ClassDiagram, exerciseGroup3);
        ModelingExercise modelingExercise2 = ModelFactory.generateModelingExerciseForExam(DiagramType.ClassDiagram, exerciseGroup3);
        exerciseGroup3.setExercises(Set.of(modelingExercise1, modelingExercise2));
        exerciseRepo.save(modelingExercise1);
        exerciseRepo.save(modelingExercise2);

        TextExercise bonusTextExercise = ModelFactory.generateTextExerciseForExam(exerciseGroup4);
        bonusTextExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_AS_BONUS);
        exerciseGroup4.setExercises(Set.of(bonusTextExercise));
        exerciseRepo.save(bonusTextExercise);

        TextExercise notIncludedTextExercise = ModelFactory.generateTextExerciseForExam(exerciseGroup5);
        notIncludedTextExercise.setIncludedInOverallScore(IncludedInOverallScore.NOT_INCLUDED);
        exerciseGroup5.setExercises(Set.of(notIncludedTextExercise));
        exerciseRepo.save(notIncludedTextExercise);

        if (withProgrammingExercise) {
            ModelFactory.generateExerciseGroup(true, exam); // programming
            exam.setNumberOfExercisesInExam(7);
            exam.setMaxPoints(29);
            exam = examRepository.save(exam);
            var exerciseGroup6 = exam.getExerciseGroups().get(6);
            // Programming exercises need a proper setup for 'prepare exam start' to work
            ProgrammingExercise programmingExercise1 = ModelFactory.generateProgrammingExerciseForExam(exerciseGroup6);
            exerciseRepo.save(programmingExercise1);
            addTemplateParticipationForProgrammingExercise(programmingExercise1);
            addSolutionParticipationForProgrammingExercise(programmingExercise1);

            exerciseGroup6.setExercises(Set.of(programmingExercise1));
        }

        return exam;
    }

    public Exam addTextModelingProgrammingExercisesToExam(Exam initialExam, boolean withProgrammingExercise, boolean withQuizExercise) {
        ModelFactory.generateExerciseGroup(true, initialExam); // text
        ModelFactory.generateExerciseGroup(true, initialExam); // modeling
        initialExam.setNumberOfExercisesInExam(2);
        var exam = examRepository.save(initialExam);
        // NOTE: we have to reassign, otherwise we get problems, because the objects have changed
        var exerciseGroup0 = exam.getExerciseGroups().get(0);
        var exerciseGroup1 = exam.getExerciseGroups().get(1);

        TextExercise textExercise1 = ModelFactory.generateTextExerciseForExam(exerciseGroup0);
        TextExercise textExercise2 = ModelFactory.generateTextExerciseForExam(exerciseGroup0);
        exerciseGroup0.setExercises(Set.of(textExercise1, textExercise2));
        exerciseRepo.save(textExercise1);
        exerciseRepo.save(textExercise2);

        ModelingExercise modelingExercise1 = ModelFactory.generateModelingExerciseForExam(DiagramType.ClassDiagram, exerciseGroup1);
        ModelingExercise modelingExercise2 = ModelFactory.generateModelingExerciseForExam(DiagramType.ClassDiagram, exerciseGroup1);
        exerciseGroup1.setExercises(Set.of(modelingExercise1, modelingExercise2));
        exerciseRepo.save(modelingExercise1);
        exerciseRepo.save(modelingExercise2);

        if (withProgrammingExercise) {
            ModelFactory.generateExerciseGroup(true, exam); // programming
            exam.setNumberOfExercisesInExam(3);
            exam = examRepository.save(exam);
            var exerciseGroup2 = exam.getExerciseGroups().get(2);
            // Programming exercises need a proper setup for 'prepare exam start' to work
            ProgrammingExercise programmingExercise1 = ModelFactory.generateProgrammingExerciseForExam(exerciseGroup2);
            exerciseRepo.save(programmingExercise1);
            addTemplateParticipationForProgrammingExercise(programmingExercise1);
            addSolutionParticipationForProgrammingExercise(programmingExercise1);
            exerciseGroup2.setExercises(Set.of(programmingExercise1));
        }

        if (withQuizExercise) {
            ModelFactory.generateExerciseGroup(true, exam); // modeling
            exam.setNumberOfExercisesInExam(3 + (withProgrammingExercise ? 1 : 0));
            exam = examRepository.save(exam);
            var exerciseGroup3 = exam.getExerciseGroups().get(2 + (withProgrammingExercise ? 1 : 0));
            // Programming exercises need a proper setup for 'prepare exam start' to work
            QuizExercise quizExercise = createQuizForExam(exerciseGroup3);
            exerciseRepo.save(quizExercise);
            exerciseGroup3.setExercises(Set.of(quizExercise));
        }
        return exam;
    }

    /**
     * Stores participation of the user with the given login for the given exercise
     *
     * @param exercise the exercise for which the participation will be created
     * @param login    login of the user
     * @return eagerly loaded representation of the participation object stored in the database
     */
    public StudentParticipation createAndSaveParticipationForExercise(Exercise exercise, String login) {
        Optional<StudentParticipation> storedParticipation = studentParticipationRepo.findWithEagerLegalSubmissionsByExerciseIdAndStudentLogin(exercise.getId(), login);
        if (storedParticipation.isEmpty()) {
            User user = getUserByLogin(login);
            StudentParticipation participation = new StudentParticipation();
            participation.setInitializationDate(ZonedDateTime.now());
            participation.setParticipant(user);
            participation.setExercise(exercise);
            studentParticipationRepo.save(participation);
            storedParticipation = studentParticipationRepo.findWithEagerLegalSubmissionsByExerciseIdAndStudentLogin(exercise.getId(), login);
            assertThat(storedParticipation).isPresent();
        }
        return studentParticipationRepo.findWithEagerLegalSubmissionsAndResultsAssessorsById(storedParticipation.get().getId()).get();
    }

    /**
     * Stores participation of the team with the given id for the given exercise
     *
     * @param exercise the exercise for which the participation will be created
     * @param teamId   id of the team
     * @return eagerly loaded representation of the participation object stored in the database
     */
    public StudentParticipation addTeamParticipationForExercise(Exercise exercise, long teamId) {
        Optional<StudentParticipation> storedParticipation = studentParticipationRepo.findWithEagerLegalSubmissionsByExerciseIdAndTeamId(exercise.getId(), teamId);
        if (storedParticipation.isEmpty()) {
            Team team = teamRepo.findById(teamId).orElseThrow();
            StudentParticipation participation = new StudentParticipation();
            participation.setInitializationDate(ZonedDateTime.now());
            participation.setParticipant(team);
            participation.setExercise(exercise);
            studentParticipationRepo.save(participation);
            storedParticipation = studentParticipationRepo.findWithEagerLegalSubmissionsByExerciseIdAndTeamId(exercise.getId(), teamId);
            assertThat(storedParticipation).isPresent();
        }
        return studentParticipationRepo.findWithEagerLegalSubmissionsAndResultsAssessorsById(storedParticipation.get().getId()).get();
    }

    public ProgrammingExerciseStudentParticipation addStudentParticipationForProgrammingExercise(ProgrammingExercise exercise, String login) {

        final var existingParticipation = programmingExerciseStudentParticipationRepo.findByExerciseIdAndStudentLogin(exercise.getId(), login);
        if (existingParticipation.isPresent()) {
            return existingParticipation.get();
        }
        ProgrammingExerciseStudentParticipation participation = configureIndividualParticipation(exercise, login);
        final var repoName = (exercise.getProjectKey() + "-" + login).toLowerCase();
        participation.setRepositoryUrl(String.format("http://some.test.url/scm/%s/%s.git", exercise.getProjectKey(), repoName));
        participation = programmingExerciseStudentParticipationRepo.save(participation);

        return (ProgrammingExerciseStudentParticipation) studentParticipationRepo.findWithEagerLegalSubmissionsAndResultsAssessorsById(participation.getId()).get();
    }

    public ProgrammingExerciseStudentParticipation addTeamParticipationForProgrammingExercise(ProgrammingExercise exercise, Team team) {

        final var existingParticipation = programmingExerciseStudentParticipationRepo.findByExerciseIdAndTeamId(exercise.getId(), team.getId());
        if (existingParticipation.isPresent()) {
            return existingParticipation.get();
        }
        ProgrammingExerciseStudentParticipation participation = configureTeamParticipation(exercise, team);
        final var repoName = (exercise.getProjectKey() + "-" + team.getShortName()).toLowerCase();
        participation.setRepositoryUrl(String.format("http://some.test.url/scm/%s/%s.git", exercise.getProjectKey(), repoName));
        participation = programmingExerciseStudentParticipationRepo.save(participation);

        return (ProgrammingExerciseStudentParticipation) studentParticipationRepo.findWithEagerLegalSubmissionsAndResultsAssessorsById(participation.getId()).get();
    }

    public ProgrammingExerciseStudentParticipation addStudentParticipationForProgrammingExerciseForLocalRepo(ProgrammingExercise exercise, String login, URL localRepoPath) {
        final var existingParticipation = programmingExerciseStudentParticipationRepo.findByExerciseIdAndStudentLogin(exercise.getId(), login);
        if (existingParticipation.isPresent()) {
            return existingParticipation.get();
        }
        ProgrammingExerciseStudentParticipation participation = configureIndividualParticipation(exercise, login);
        final var repoName = (exercise.getProjectKey() + "-" + login).toLowerCase();
        participation.setRepositoryUrl(String.format(localRepoPath.toString() + "%s/%s.git", exercise.getProjectKey(), repoName));
        participation = programmingExerciseStudentParticipationRepo.save(participation);

        return (ProgrammingExerciseStudentParticipation) studentParticipationRepo.findWithEagerLegalSubmissionsAndResultsAssessorsById(participation.getId()).get();
    }

    private ProgrammingExerciseStudentParticipation configureIndividualParticipation(ProgrammingExercise exercise, String login) {
        final var user = getUserByLogin(login);
        var participation = new ProgrammingExerciseStudentParticipation();
        final var buildPlanId = exercise.getProjectKey().toUpperCase() + "-" + login.toUpperCase();
        participation.setInitializationDate(ZonedDateTime.now());
        participation.setParticipant(user);
        participation.setBuildPlanId(buildPlanId);
        participation.setProgrammingExercise(exercise);
        participation.setInitializationState(InitializationState.INITIALIZED);
        return participation;
    }

    private ProgrammingExerciseStudentParticipation configureTeamParticipation(ProgrammingExercise exercise, Team team) {
        var participation = new ProgrammingExerciseStudentParticipation();
        final var buildPlanId = exercise.getProjectKey().toUpperCase() + "-" + team.getShortName().toUpperCase();
        participation.setInitializationDate(ZonedDateTime.now());
        participation.setParticipant(team);
        participation.setBuildPlanId(buildPlanId);
        participation.setProgrammingExercise(exercise);
        participation.setInitializationState(InitializationState.INITIALIZED);
        return participation;
    }

    public ProgrammingExercise addTemplateParticipationForProgrammingExercise(ProgrammingExercise exercise) {
        final var repoName = exercise.generateRepositoryName(RepositoryType.TEMPLATE);
        TemplateProgrammingExerciseParticipation participation = new TemplateProgrammingExerciseParticipation();
        participation.setProgrammingExercise(exercise);
        participation.setBuildPlanId(exercise.generateBuildPlanId(BuildPlanType.TEMPLATE));
        participation.setRepositoryUrl(String.format("http://some.test.url/scm/%s/%s.git", exercise.getProjectKey(), repoName));
        participation.setInitializationState(InitializationState.INITIALIZED);
        templateProgrammingExerciseParticipationRepo.save(participation);
        exercise.setTemplateParticipation(participation);
        return programmingExerciseRepository.save(exercise);
    }

    public ProgrammingExercise addSolutionParticipationForProgrammingExercise(ProgrammingExercise exercise) {
        final var repoName = exercise.generateRepositoryName(RepositoryType.SOLUTION);
        SolutionProgrammingExerciseParticipation participation = new SolutionProgrammingExerciseParticipation();
        participation.setProgrammingExercise(exercise);
        participation.setBuildPlanId(exercise.generateBuildPlanId(BuildPlanType.SOLUTION));
        participation.setRepositoryUrl(String.format("http://some.test.url/scm/%s/%s.git", exercise.getProjectKey(), repoName));
        participation.setInitializationState(InitializationState.INITIALIZED);
        solutionProgrammingExerciseParticipationRepo.save(participation);
        exercise.setSolutionParticipation(participation);
        return programmingExerciseRepository.save(exercise);
    }

    public Result addResultToParticipation(AssessmentType assessmentType, ZonedDateTime completionDate, Participation participation) {
        Result result = new Result().participation(participation).resultString("x of y passed").successful(false).rated(true).score(100D).assessmentType(assessmentType)
                .completionDate(completionDate);
        return resultRepo.save(result);
    }

    public Result addResultToParticipation(AssessmentType assessmentType, ZonedDateTime completionDate, Participation participation, String resultString, String assessorLogin,
            List<Feedback> feedbacks) {
        Result result = new Result().participation(participation).resultString(resultString).assessmentType(assessmentType).completionDate(completionDate).feedbacks(feedbacks);
        result.setAssessor(getUserByLogin(assessorLogin));
        return resultRepo.save(result);
    }

    public Result addResultToParticipation(Participation participation, Submission submission) {
        Result result = new Result().participation(participation).resultString("x of y passed").successful(false).score(100D);
        result = resultRepo.save(result);
        result.setSubmission(submission);
        submission.addResult(result);
        submission.setParticipation(participation);
        submissionRepository.save(submission);
        return result;
    }

    public Result addSampleFeedbackToResults(Result result) {
        Feedback feedback1 = feedbackRepo.save(new Feedback().detailText("detail1"));
        Feedback feedback2 = feedbackRepo.save(new Feedback().detailText("detail2"));
        List<Feedback> feedbacks = new ArrayList<>();
        feedbacks.add(feedback1);
        feedbacks.add(feedback2);
        result.addFeedbacks(feedbacks);
        return resultRepo.save(result);
    }

    public Result addSampleStaticCodeAnalysisFeedbackToResults(Result result) {
        List<Feedback> feedback = ModelFactory.generateStaticCodeAnalysisFeedbackList(5);
        feedback.addAll(ModelFactory.generateFeedback());
        feedback = feedbackRepo.saveAll(feedback);
        result.addFeedbacks(feedback);
        return resultRepo.save(result);
    }

    public Result addFeedbackToResult(Feedback feedback, Result result) {
        feedbackRepo.save(feedback);
        result.addFeedback(feedback);
        return resultRepo.save(result);
    }

    public Result addFeedbackToResults(Result result) {
        List<Feedback> feedback = ModelFactory.generateStaticCodeAnalysisFeedbackList(5);
        feedback.addAll(ModelFactory.generateFeedback());
        feedback = feedbackRepo.saveAll(feedback);
        result.addFeedbacks(feedback);
        return resultRepo.save(result);
    }

    public Submission addResultToSubmission(final Submission submission, AssessmentType assessmentType, User user, String resultString, Double score, boolean rated,
            ZonedDateTime completionDate) {
        Result result = new Result().participation(submission.getParticipation()).assessmentType(assessmentType).resultString(resultString).score(score).rated(rated)
                .completionDate(completionDate);
        result.setAssessor(user);
        result = resultRepo.save(result);
        result.setSubmission(submission);
        submission.addResult(result);
        var savedSubmission = submissionRepository.save(submission);
        return submissionRepository.findWithEagerResultsAndAssessorById(savedSubmission.getId()).orElseThrow();
    }

    public Submission addResultToSubmission(Submission submission, AssessmentType assessmentType) {
        return addResultToSubmission(submission, assessmentType, null, "x of y passed", 100D, true, null);
    }

    public Submission addResultToSubmission(Submission submission, AssessmentType assessmentType, User user) {
        return addResultToSubmission(submission, assessmentType, user, "x of y passed", 100D, true, ZonedDateTime.now());
    }

    public Submission addResultToSubmission(Submission submission, AssessmentType assessmentType, User user, Double score, boolean rated) {
        return addResultToSubmission(submission, assessmentType, user, "x of y passed", score, rated, ZonedDateTime.now());
    }

    public Exercise addMaxScoreAndBonusPointsToExercise(Exercise exercise) {
        exercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);
        exercise.setMaxPoints(100.0);
        exercise.setBonusPoints(10.0);
        return exerciseRepo.save(exercise);
    }

    public List<GradingCriterion> addGradingInstructionsToExercise(Exercise exercise) {
        GradingCriterion emptyCriterion = ModelFactory.generateGradingCriterion(null);
        List<GradingInstruction> instructionWithNoCriteria = ModelFactory.generateGradingInstructions(emptyCriterion, 1, 0);
        instructionWithNoCriteria.get(0).setCredits(1);
        instructionWithNoCriteria.get(0).setUsageCount(0);
        emptyCriterion.setExercise(exercise);
        emptyCriterion.setStructuredGradingInstructions(instructionWithNoCriteria);

        GradingCriterion testCriterion = ModelFactory.generateGradingCriterion("test title");
        List<GradingInstruction> instructions = ModelFactory.generateGradingInstructions(testCriterion, 3, 1);
        testCriterion.setStructuredGradingInstructions(instructions);

        GradingCriterion testCriterion2 = ModelFactory.generateGradingCriterion("test title2");
        List<GradingInstruction> instructionsWithBigLimit = ModelFactory.generateGradingInstructions(testCriterion2, 1, 4);
        testCriterion2.setStructuredGradingInstructions(instructionsWithBigLimit);

        testCriterion.setExercise(exercise);
        var criteria = new ArrayList<GradingCriterion>();
        criteria.add(emptyCriterion);
        criteria.add(testCriterion);
        criteria.add(testCriterion2);
        exercise.setGradingCriteria(criteria);
        return exercise.getGradingCriteria();
    }

    /**
     * @param title The title of the to be added modeling exercise
     * @return A course with one specified modeling exercise
     */
    public Course addCourseWithOneModelingExercise(String title) {
        long currentCourseRepoSize = courseRepo.count();
        long currentExerciseRepoSize = exerciseRepo.count();
        Course course = ModelFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), "tumuser", "tutor", "instructor");
        ModelingExercise modelingExercise = ModelFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.ClassDiagram, course);
        modelingExercise.setTitle(title);
        course.addExercises(modelingExercise);
        course.setMaxComplaintTimeDays(14);
        course = courseRepo.save(course);
        modelingExercise = exerciseRepo.save(modelingExercise);
        assertThat(exerciseRepo.count()).as("one exercise got stored").isEqualTo(currentExerciseRepoSize + 1L);
        assertThat(courseRepo.count()).as("a course got stored").isEqualTo(currentCourseRepoSize + 1L);
        assertThat(course.getExercises()).as("course contains the exercise").containsExactlyInAnyOrder(modelingExercise);
        assertThat(modelingExercise.getPresentationScoreEnabled()).as("presentation score is enabled").isTrue();
        return course;
    }

    public Course addCourseWithOneModelingExercise() {
        return addCourseWithOneModelingExercise("ClassDiagram");
    }

    /**
     * @param title The title of the to be added text exercise
     * @return A course with one specified text exercise
     */
    public Course addCourseWithOneReleasedTextExercise(String title) {
        Course course = ModelFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), "tumuser", "tutor", "instructor");
        TextExercise textExercise = ModelFactory.generateTextExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, course);
        textExercise.setTitle(title);
        course.addExercises(textExercise);
        final var exercisesNrBefore = exerciseRepo.count();
        final var courseNrBefore = courseRepo.count();
        courseRepo.save(course);
        exerciseRepo.save(textExercise);
        assertThat(exercisesNrBefore + 1).as("one exercise got stored").isEqualTo(exerciseRepo.count());
        assertThat(courseNrBefore + 1).as("a course got stored").isEqualTo(courseRepo.count());
        assertThat(courseRepo.findWithEagerExercisesById(course.getId()).getExercises()).as("course contains the exercise").contains(textExercise);
        assertThat(textExercise.getPresentationScoreEnabled()).as("presentation score is enabled").isTrue();

        return course;
    }

    public Course addCourseWithOneReleasedTextExercise() {
        return addCourseWithOneReleasedTextExercise("Text");
    }

    public ProgrammingExercise addCourseExamExerciseGroupWithOneProgrammingExerciseAndTestCases() {
        ExerciseGroup exerciseGroup = addExerciseGroupWithExamAndCourse(true);
        ProgrammingExercise programmingExercise = new ProgrammingExercise();
        programmingExercise.setExerciseGroup(exerciseGroup);
        populateProgrammingExercise(programmingExercise, "TESTEXFOREXAM", "Testtitle", false);

        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        programmingExercise = addSolutionParticipationForProgrammingExercise(programmingExercise);
        programmingExercise = addTemplateParticipationForProgrammingExercise(programmingExercise);

        addTestCasesToProgrammingExercise(programmingExercise);
        return programmingExercise;
    }

    public ProgrammingSubmission createProgrammingSubmission(Participation participation, boolean buildFailed, String commitHash) {
        ProgrammingSubmission programmingSubmission = ModelFactory.generateProgrammingSubmission(true);
        programmingSubmission.setBuildFailed(buildFailed);
        programmingSubmission.type(SubmissionType.MANUAL).submissionDate(ZonedDateTime.now());
        programmingSubmission.setCommitHash(commitHash);
        programmingSubmission.setParticipation(participation);
        return submissionRepository.save(programmingSubmission);
    }

    public ProgrammingSubmission createProgrammingSubmission(Participation participation, boolean buildFailed) {
        return createProgrammingSubmission(participation, buildFailed, TestConstants.COMMIT_HASH_STRING);
    }

    public TextExercise addCourseExamExerciseGroupWithOneTextExercise() {
        ExerciseGroup exerciseGroup = addExerciseGroupWithExamAndCourse(true);
        TextExercise textExercise = ModelFactory.generateTextExerciseForExam(exerciseGroup);
        final var exercisesNrBefore = exerciseRepo.count();
        exerciseRepo.save(textExercise);
        assertThat(exercisesNrBefore + 1).as("one exercise got stored").isEqualTo(exerciseRepo.count());
        return textExercise;
    }

    public TextExercise addCourseExamWithReviewDatesExerciseGroupWithOneTextExercise() {
        ExerciseGroup exerciseGroup = addExerciseGroupWithExamWithReviewDatesAndCourse(true);
        TextExercise textExercise = ModelFactory.generateTextExerciseForExam(exerciseGroup);
        final var exercisesNrBefore = exerciseRepo.count();
        exerciseRepo.save(textExercise);
        assertThat(exercisesNrBefore + 1).as("one exercise got stored").isEqualTo(exerciseRepo.count());
        return textExercise;
    }

    public FileUploadExercise addCourseExamExerciseGroupWithOneFileUploadExercise() {
        ExerciseGroup exerciseGroup = addExerciseGroupWithExamAndCourse(true);
        FileUploadExercise fileUploadExercise = ModelFactory.generateFileUploadExerciseForExam("pdf", exerciseGroup);
        final var exercisesNrBefore = exerciseRepo.count();
        exerciseRepo.save(fileUploadExercise);
        assertThat(exercisesNrBefore + 1).as("one exercise got stored").isEqualTo(exerciseRepo.count());
        return fileUploadExercise;
    }

    public ExerciseGroup addExerciseGroupWithExamAndCourse(boolean mandatory) {
        Course course = ModelFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), "tumuser", "tutor", "instructor");
        Exam exam = ModelFactory.generateExam(course);
        ExerciseGroup exerciseGroup = ModelFactory.generateExerciseGroup(mandatory, exam);
        final var courseNrBefore = courseRepo.count();
        final var examNrBefore = examRepository.count();
        final var exerciseGroupNrBefore = exerciseGroupRepository.count();

        courseRepo.save(course);
        examRepository.save(exam);

        assertThat(courseNrBefore + 1).as("a course got stored").isEqualTo(courseRepo.count());
        assertThat(examNrBefore + 1).as("an exam got stored").isEqualTo(examRepository.count());
        assertThat(exerciseGroupNrBefore + 1).as("an exerciseGroup got stored").isEqualTo(exerciseGroupRepository.count());

        Optional<Course> optionalCourse = courseRepo.findById(course.getId());
        assertThat(optionalCourse).as("course can be retrieved").isPresent();
        Course courseDB = optionalCourse.get();

        Optional<Exam> optionalExam = examRepository.findById(exam.getId());
        assertThat(optionalCourse).as("exam can be retrieved").isPresent();
        Exam examDB = optionalExam.get();

        Optional<ExerciseGroup> optionalExerciseGroup = exerciseGroupRepository.findById(exerciseGroup.getId());
        assertThat(optionalExerciseGroup).as("exerciseGroup can be retrieved").isPresent();
        ExerciseGroup exerciseGroupDB = optionalExerciseGroup.get();

        assertThat(examDB.getCourse().getId()).as("exam and course are linked correctly").isEqualTo(courseDB.getId());
        assertThat(exerciseGroupDB.getExam().getId()).as("exerciseGroup and exam are linked correctly").isEqualTo(examDB.getId());

        return exerciseGroup;
    }

    public ExerciseGroup addExerciseGroupWithExamWithReviewDatesAndCourse(boolean mandatory) {
        Course course = ModelFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), "tumuser", "tutor", "instructor");
        Exam exam = ModelFactory.generateExamWithStudentReviewDates(course);
        ExerciseGroup exerciseGroup = ModelFactory.generateExerciseGroup(mandatory, exam);
        final var courseNrBefore = courseRepo.count();
        final var examNrBefore = examRepository.count();
        final var exerciseGroupNrBefore = exerciseGroupRepository.count();

        courseRepo.save(course);
        examRepository.save(exam);

        assertThat(courseNrBefore + 1).as("a course got stored").isEqualTo(courseRepo.count());
        assertThat(examNrBefore + 1).as("an exam got stored").isEqualTo(examRepository.count());
        assertThat(exerciseGroupNrBefore + 1).as("an exerciseGroup got stored").isEqualTo(exerciseGroupRepository.count());

        Optional<Course> optionalCourse = courseRepo.findById(course.getId());
        assertThat(optionalCourse).as("course can be retrieved").isPresent();
        Course courseDB = optionalCourse.get();

        Optional<Exam> optionalExam = examRepository.findById(exam.getId());
        assertThat(optionalCourse).as("exam can be retrieved").isPresent();
        Exam examDB = optionalExam.get();

        Optional<ExerciseGroup> optionalExerciseGroup = exerciseGroupRepository.findById(exerciseGroup.getId());
        assertThat(optionalExerciseGroup).as("exerciseGroup can be retrieved").isPresent();
        ExerciseGroup exerciseGroupDB = optionalExerciseGroup.get();

        assertThat(examDB.getCourse().getId()).as("exam and course are linked correctly").isEqualTo(courseDB.getId());
        assertThat(exerciseGroupDB.getExam().getId()).as("exerciseGroup and exam are linked correctly").isEqualTo(examDB.getId());

        return exerciseGroup;
    }

    public Course addCourseWithOneFinishedTextExercise() {
        long numberOfCourses = courseRepo.count();
        long numberOfExercises = exerciseRepo.count();

        Course course = ModelFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), "tumuser", "tutor", "instructor");
        TextExercise finishedTextExercise = ModelFactory.generateTextExercise(pastTimestamp, pastTimestamp.plusHours(12), pastTimestamp.plusHours(24), course);
        finishedTextExercise.setTitle("Finished");
        course.addExercises(finishedTextExercise);
        courseRepo.save(course);
        exerciseRepo.save(finishedTextExercise);
        List<Course> courseRepoContent = courseRepo.findAllActiveWithEagerExercisesAndLectures(ZonedDateTime.now());
        List<Exercise> exerciseRepoContent = exerciseRepo.findAll();
        assertThat(exerciseRepoContent.size()).as("one exercise got stored").isEqualTo(numberOfExercises + 1);
        assertThat(courseRepoContent.size()).as("a course got stored").isEqualTo(numberOfCourses + 1);
        return course;
    }

    public Course addCourseWithDifferentModelingExercises() {
        Course course = ModelFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), "tumuser", "tutor", "instructor");
        ModelingExercise classExercise = ModelFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.ClassDiagram, course);
        classExercise.setTitle("ClassDiagram");
        course.addExercises(classExercise);

        ModelingExercise activityExercise = ModelFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.ActivityDiagram, course);
        activityExercise.setTitle("ActivityDiagram");
        course.addExercises(activityExercise);

        ModelingExercise objectExercise = ModelFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.ObjectDiagram, course);
        objectExercise.setTitle("ObjectDiagram");
        course.addExercises(objectExercise);

        ModelingExercise useCaseExercise = ModelFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.UseCaseDiagram, course);
        useCaseExercise.setTitle("UseCaseDiagram");
        course.addExercises(useCaseExercise);

        ModelingExercise finishedExercise = ModelFactory.generateModelingExercise(pastTimestamp, pastTimestamp, futureTimestamp, DiagramType.ClassDiagram, course);
        finishedExercise.setTitle("finished");
        course.addExercises(finishedExercise);

        course = courseRepo.save(course);
        exerciseRepo.save(classExercise);
        exerciseRepo.save(activityExercise);
        exerciseRepo.save(objectExercise);
        exerciseRepo.save(useCaseExercise);
        exerciseRepo.save(finishedExercise);
        Course storedCourse = courseRepo.findWithEagerExercisesAndLecturesById(course.getId());
        Set<Exercise> exercises = storedCourse.getExercises();
        assertThat(exercises.size()).as("five exercises got stored").isEqualTo(5);
        assertThat(exercises).as("Contains all exercises").containsExactlyInAnyOrder(course.getExercises().toArray(new Exercise[] {}));
        return course;
    }

    public Course addCourseWithOneProgrammingExercise() {
        return addCourseWithOneProgrammingExercise(false);
    }

    public Course addCourseWithOneProgrammingExercise(boolean enableStaticCodeAnalysis) {
        return addCourseWithOneProgrammingExercise(enableStaticCodeAnalysis, ProgrammingLanguage.JAVA);
    }

    public Course addCourseWithOneProgrammingExercise(boolean enableStaticCodeAnalysis, ProgrammingLanguage programmingLanguage) {
        var course = ModelFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), "tumuser", "tutor", "instructor");
        course = courseRepo.save(course);
        var programmingExercise = addProgrammingExerciseToCourse(course, enableStaticCodeAnalysis, programmingLanguage);
        assertThat(programmingExercise.getPresentationScoreEnabled()).as("presentation score is enabled").isTrue();
        return courseRepo.findWithEagerExercisesAndLecturesById(course.getId());
    }

    public ProgrammingExercise addProgrammingExerciseToCourse(Course course, boolean enableStaticCodeAnalysis) {
        return addProgrammingExerciseToCourse(course, enableStaticCodeAnalysis, ProgrammingLanguage.JAVA);
    }

    public ProgrammingExercise addProgrammingExerciseToCourse(Course course, boolean enableStaticCodeAnalysis, ProgrammingLanguage programmingLanguage) {
        var programmingExercise = (ProgrammingExercise) new ProgrammingExercise().course(course);
        populateProgrammingExercise(programmingExercise, "TSTEXC", "Programming", enableStaticCodeAnalysis, programmingLanguage);
        programmingExercise.setPresentationScoreEnabled(course.getPresentationScore() != 0);

        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        course.addExercises(programmingExercise);
        programmingExercise = addSolutionParticipationForProgrammingExercise(programmingExercise);
        programmingExercise = addTemplateParticipationForProgrammingExercise(programmingExercise);

        assertThat(programmingExercise.getPresentationScoreEnabled()).as("presentation score is enabled").isTrue();

        return programmingExercise;
    }

    /**
     * @param programmingExerciseTitle The name of programming exercise
     * @return A course with named exercise
     */
    public Course addCourseWithNamedProgrammingExercise(String programmingExerciseTitle) {
        var course = ModelFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), "tumuser", "tutor", "instructor");
        course = courseRepo.save(course);

        var programmingExercise = (ProgrammingExercise) new ProgrammingExercise().course(course);
        populateProgrammingExercise(programmingExercise, "TSTEXC", programmingExerciseTitle, false);
        programmingExercise.setPresentationScoreEnabled(course.getPresentationScore() != 0);

        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        course.addExercises(programmingExercise);
        programmingExercise = addSolutionParticipationForProgrammingExercise(programmingExercise);
        programmingExercise = addTemplateParticipationForProgrammingExercise(programmingExercise);

        assertThat(programmingExercise.getPresentationScoreEnabled()).as("presentation score is enabled").isTrue();

        return courseRepo.findWithEagerExercisesAndLecturesById(course.getId());
    }

    private void populateProgrammingExercise(ProgrammingExercise programmingExercise, String shortName, String title, boolean enableStaticCodeAnalysis) {
        populateProgrammingExercise(programmingExercise, shortName, title, enableStaticCodeAnalysis, ProgrammingLanguage.JAVA);
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
     * @return A empty course
     */
    public Course addEmptyCourse() {
        Course course = ModelFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), "tumuser", "tutor", "instructor");
        courseRepo.save(course);
        assertThat(courseRepo.findById(course.getId())).as("empty course is initialized").isPresent();
        return course;
    }

    /**
     * @return A tutorial course with the names specified in application-dev or application-prod
     */
    public Course addTutorialCourse() {
        Course course = ModelFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), tutorialGroupStudents.get(), tutorialGroupTutors.get(),
                tutorialGroupInstructors.get());
        courseRepo.save(course);
        assertThat(courseRepo.findById(course.getId())).as("tutorial course is initialized").isPresent();
        return course;
    }

    /**
     * @param title The title reflect the genre of exercise that will be added to the course
     * @return A course that is added in other group different from the group that created in beforeEach() method
     */
    public Course addCourseInOtherInstructionGroupAndExercise(String title) {
        Course course = ModelFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), "tumuser", "tutor", "other-instructors");
        if ("Programming".equals(title)) {
            course = courseRepo.save(course);

            var programmingExercise = (ProgrammingExercise) new ProgrammingExercise().course(course);
            populateProgrammingExercise(programmingExercise, "TSTEXC", "Programming", false);
            programmingExercise.setPresentationScoreEnabled(course.getPresentationScore() != 0);

            programmingExercise = programmingExerciseRepository.save(programmingExercise);
            course.addExercises(programmingExercise);
            programmingExercise = addSolutionParticipationForProgrammingExercise(programmingExercise);
            programmingExercise = addTemplateParticipationForProgrammingExercise(programmingExercise);

            assertThat(programmingExercise.getPresentationScoreEnabled()).as("presentation score is enabled").isTrue();
        }
        else if ("Text".equals(title)) {
            TextExercise textExercise = ModelFactory.generateTextExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, course);
            textExercise.setTitle("Text");
            course.addExercises(textExercise);
            courseRepo.save(course);
            exerciseRepo.save(textExercise);
        }
        else if ("ClassDiagram".equals(title)) {
            ModelingExercise modelingExercise = ModelFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.ClassDiagram, course);
            modelingExercise.setTitle("ClassDiagram");
            course.addExercises(modelingExercise);
            courseRepo.save(course);
            exerciseRepo.save(modelingExercise);
        }
        else {
            return course;
        }

        return course;
    }

    public Course addCourseWithOneProgrammingExerciseAndSpecificTestCases() {
        Course course = addCourseWithOneProgrammingExercise();
        ProgrammingExercise programmingExercise = findProgrammingExerciseWithTitle(course.getExercises(), "Programming");

        List<ProgrammingExerciseTestCase> testCases = new ArrayList<>();
        testCases.add(new ProgrammingExerciseTestCase().testName("testClass[BubbleSort]").weight(1.0).active(true).exercise(programmingExercise).bonusMultiplier(1D).bonusPoints(0D)
                .afterDueDate(false));
        testCases.add(new ProgrammingExerciseTestCase().testName("testMethods[Context]").weight(2.0).active(true).exercise(programmingExercise).bonusMultiplier(1D).bonusPoints(0D)
                .afterDueDate(false));
        testCases.add(new ProgrammingExerciseTestCase().testName("testMethods[Policy]").weight(3.0).active(true).exercise(programmingExercise).bonusMultiplier(1D).bonusPoints(0D)
                .afterDueDate(false));
        testCaseRepository.saveAll(testCases);

        List<ProgrammingExerciseTestCase> tests = new ArrayList<>(testCaseRepository.findByExerciseId(programmingExercise.getId()));
        assertThat(tests).as("test case is initialized").hasSize(3);

        return courseRepo.findById(course.getId()).get();
    }

    public ProgrammingExercise addCourseWithOneProgrammingExerciseAndStaticCodeAnalysisCategories() {
        return addCourseWithOneProgrammingExerciseAndStaticCodeAnalysisCategories(ProgrammingLanguage.JAVA);
    }

    public ProgrammingExercise addCourseWithOneProgrammingExerciseAndStaticCodeAnalysisCategories(ProgrammingLanguage programmingLanguage) {
        Course course = addCourseWithOneProgrammingExercise(true, programmingLanguage);
        ProgrammingExercise programmingExercise = findProgrammingExerciseWithTitle(course.getExercises(), "Programming");
        programmingExercise = programmingExerciseRepository.save(programmingExercise);

        addStaticCodeAnalysisCategoriesToProgrammingExercise(programmingExercise);

        return programmingExercise;
    }

    public void addStaticCodeAnalysisCategoriesToProgrammingExercise(ProgrammingExercise programmingExercise) {
        programmingExercise.setStaticCodeAnalysisEnabled(true);
        programmingExerciseRepository.save(programmingExercise);
        var category1 = ModelFactory.generateStaticCodeAnalysisCategory(programmingExercise, "Bad Practice", CategoryState.GRADED, 3D, 10D);
        var category2 = ModelFactory.generateStaticCodeAnalysisCategory(programmingExercise, "Code Style", CategoryState.GRADED, 5D, 10D);
        var category3 = ModelFactory.generateStaticCodeAnalysisCategory(programmingExercise, "Miscellaneous", CategoryState.INACTIVE, 2D, 10D);
        var category4 = ModelFactory.generateStaticCodeAnalysisCategory(programmingExercise, "Potential Bugs", CategoryState.FEEDBACK, 5D, 20D);
        var categories = staticCodeAnalysisCategoryRepository.saveAll(List.of(category1, category2, category3, category4));
        programmingExercise.setStaticCodeAnalysisCategories(new HashSet<>(categories));
    }

    public Course addCourseWithOneProgrammingExerciseAndTestCases() {
        Course course = addCourseWithOneProgrammingExercise();
        ProgrammingExercise programmingExercise = findProgrammingExerciseWithTitle(course.getExercises(), "Programming");
        addTestCasesToProgrammingExercise(programmingExercise);
        return courseRepo.findById(course.getId()).get();
    }

    /**
     * @param programmingExerciseTitle The title of the programming exercise
     * @return A Course with named programming exercise and test cases
     */
    public Course addCourseWithNamedProgrammingExerciseAndTestCases(String programmingExerciseTitle) {
        Course course = addCourseWithNamedProgrammingExercise(programmingExerciseTitle);
        ProgrammingExercise programmingExercise = findProgrammingExerciseWithTitle(course.getExercises(), programmingExerciseTitle);

        addTestCasesToProgrammingExercise(programmingExercise);

        return courseRepo.findById(course.getId()).get();
    }

    public void addTestCasesToProgrammingExercise(ProgrammingExercise programmingExercise) {
        List<ProgrammingExerciseTestCase> testCases = new ArrayList<>();
        testCases.add(
                new ProgrammingExerciseTestCase().testName("test1").weight(1.0).active(true).exercise(programmingExercise).afterDueDate(false).bonusMultiplier(1D).bonusPoints(0D));
        testCases.add(new ProgrammingExerciseTestCase().testName("test2").weight(2.0).active(false).exercise(programmingExercise).afterDueDate(false).bonusMultiplier(1D)
                .bonusPoints(0D));
        testCases.add(
                new ProgrammingExerciseTestCase().testName("test3").weight(3.0).active(true).exercise(programmingExercise).afterDueDate(true).bonusMultiplier(1D).bonusPoints(0D));
        testCaseRepository.saveAll(testCases);

        List<ProgrammingExerciseTestCase> tests = new ArrayList<>(testCaseRepository.findByExerciseId(programmingExercise.getId()));
        assertThat(tests).as("test case is initialized").hasSize(3);
    }

    public Course addCourseWithModelingAndTextExercise() {
        Course course = ModelFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), "tumuser", "tutor", "instructor");
        ModelingExercise modelingExercise = ModelFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.ClassDiagram, course);
        modelingExercise.setTitle("Modeling");
        course.addExercises(modelingExercise);
        TextExercise textExercise = ModelFactory.generateTextExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, course);
        textExercise.setTitle("Text");
        course.addExercises(textExercise);
        course = courseRepo.save(course);
        exerciseRepo.save(modelingExercise);
        exerciseRepo.save(textExercise);
        return course;
    }

    public Course addCourseWithModelingAndTextAndFileUploadExercise() {
        Course course = ModelFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), "tumuser", "tutor", "instructor");

        ModelingExercise modelingExercise = ModelFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.ClassDiagram, course);
        modelingExercise.setTitle("Modeling");
        course.addExercises(modelingExercise);

        TextExercise textExercise = ModelFactory.generateTextExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, course);
        textExercise.setTitle("Text");
        course.addExercises(textExercise);

        FileUploadExercise fileUploadExercise = ModelFactory.generateFileUploadExercise(pastTimestamp, pastTimestamp, futureFutureTimestamp, "png,pdf", course);
        fileUploadExercise.setTitle("FileUpload");
        course.addExercises(fileUploadExercise);

        course = courseRepo.save(course);
        exerciseRepo.save(modelingExercise);
        exerciseRepo.save(textExercise);
        exerciseRepo.save(fileUploadExercise);
        return course;
    }

    public List<FileUploadExercise> createFileUploadExercisesWithCourse() {
        Course course = ModelFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), "tumuser", "tutor", "instructor");
        int courseSizeBefore = courseRepo.findAllActiveWithEagerExercisesAndLectures(ZonedDateTime.now()).size();
        courseRepo.save(course);
        List<Course> courseRepoContent = courseRepo.findAllActiveWithEagerExercisesAndLectures(ZonedDateTime.now());
        assertThat(courseRepoContent.size()).as("a course got stored").isEqualTo(courseSizeBefore + 1);

        FileUploadExercise releasedFileUploadExercise = ModelFactory.generateFileUploadExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, "png,pdf", course);
        releasedFileUploadExercise.setTitle("released");
        FileUploadExercise finishedFileUploadExercise = ModelFactory.generateFileUploadExercise(pastTimestamp, pastTimestamp, futureFutureTimestamp, "png,pdf", course);
        finishedFileUploadExercise.setTitle("finished");
        FileUploadExercise assessedFileUploadExercise = ModelFactory.generateFileUploadExercise(pastTimestamp, pastTimestamp, pastTimestamp, "png,pdf", course);
        assessedFileUploadExercise.setTitle("assessed");

        var fileUploadExercises = new ArrayList<FileUploadExercise>();
        fileUploadExercises.add(releasedFileUploadExercise);
        fileUploadExercises.add(finishedFileUploadExercise);
        fileUploadExercises.add(assessedFileUploadExercise);
        return fileUploadExercises;
    }

    public Course addCourseWithThreeFileUploadExercise() {
        var fileUploadExercises = createFileUploadExercisesWithCourse();
        exerciseRepo.saveAll(fileUploadExercises);
        List<Course> courseRepoContent = courseRepo.findAllActiveWithEagerExercisesAndLectures(ZonedDateTime.now());
        List<Exercise> exerciseRepoContent = exerciseRepo.findAll();
        assertThat(exerciseRepoContent.size()).as("one exercise got stored").isEqualTo(3);
        assertThat(courseRepoContent.size()).as("a course got stored").isEqualTo(1);
        assertThat(courseRepoContent.get(0).getExercises()).as("course contains the exercises").containsExactlyInAnyOrder(exerciseRepoContent.toArray(new Exercise[] {}));
        return courseRepoContent.get(0);
    }

    /** With this method we can generate a course. We can specify the number of exercises. To not only test one type, this method generates modeling, file-upload and text exercises in a cyclic manner.
     *
     * @param numberOfExercises             - number of generated exercises. E.g. if you set it to 4, 2 modeling exercises, one text and one file-upload exercise will be generated. (thats why there is the %3 check)
     * @param numberOfSubmissionPerExercise - for each exercise this number of submissions will be generated. E.g. if you have 2 exercises, and set this to 4, in total 8 submissions will be created.
     * @param numberOfAssessments           - generates the assessments for a submission of an exercise. Example from abobe, 2 exrecises, 4 submissions each. If you set numberOfAssessments to 2, for each exercise 2 assessmetns will be created.
     *                                          In total there will be 4 assessments then. (by two different tutors, as each exercise is assesssed by an individual tutor. There are 4 tutors that create assessments)
     * @param numberOfComplaints            - generates the complaints for assessments, in the same way as results are created.
     * @param typeComplaint                 - true: complaintType==COMPLAINT | false: complaintType==MORE_FEEDBACK
     * @param numberComplaintResponses      - generates responses for the complaint/feedback request (as above)
     * @param validModel                    - model for the modeling submission
     *
     * @return                              - the generated course
     */
    public Course addCourseWithExercisesAndSubmissions(int numberOfExercises, int numberOfSubmissionPerExercise, int numberOfAssessments, int numberOfComplaints,
            boolean typeComplaint, int numberComplaintResponses, String validModel) {
        Course course = ModelFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), "tumuser", "tutor", "instructor");
        var tutors = userRepo.getTutors(course);
        for (int i = 0; i < numberOfExercises; i++) {
            var currentUser = tutors.get(i % 4);

            if ((i % 3) == 0) {
                ModelingExercise modelingExercise = ModelFactory.generateModelingExercise(pastTimestamp, pastTimestamp, futureTimestamp, DiagramType.ClassDiagram, course);
                modelingExercise.setTitle("Modeling" + i);
                course.addExercises(modelingExercise);
                course = courseRepo.save(course);
                exerciseRepo.save(modelingExercise);
                for (int j = 1; j <= numberOfSubmissionPerExercise; j++) {
                    StudentParticipation participation = createAndSaveParticipationForExercise(modelingExercise, "student" + j);
                    ModelingSubmission submission = ModelFactory.generateModelingSubmission(validModel, true);
                    modelSubmissionService.save(submission, modelingExercise, "student" + j);
                    studentParticipationRepo.save(participation);
                    if (numberOfAssessments >= j) {
                        Result result = generateResult(submission, currentUser);
                        submission.addResult(result);
                        participation.addResult(result);
                        studentParticipationRepo.save(participation);
                        modelingSubmissionRepo.save(submission);
                        generateComplaintAndResponses(j, numberOfComplaints, numberComplaintResponses, typeComplaint, result, currentUser);
                    }
                }

            }
            else if ((i % 3) == 1) {
                TextExercise textExercise = ModelFactory.generateTextExercise(pastTimestamp, pastTimestamp, futureTimestamp, course);
                textExercise.setTitle("Text" + i);
                course.addExercises(textExercise);
                course = courseRepo.save(course);
                exerciseRepo.save(textExercise);
                for (int j = 1; j <= numberOfSubmissionPerExercise; j++) {
                    TextSubmission submission = ModelFactory.generateTextSubmission("submissionText", Language.ENGLISH, true);
                    submission = saveTextSubmission(textExercise, submission, "student" + j);
                    if (numberOfAssessments >= j) {
                        Result result = generateResult(submission, currentUser);
                        submission.addResult(result);
                        submission = (TextSubmission) saveResultInParticipation(submission, result);
                        textSubmissionRepo.save(submission);
                        generateComplaintAndResponses(j, numberOfComplaints, numberComplaintResponses, typeComplaint, result, currentUser);
                    }
                }
            }
            else if ((i % 3) == 2) {
                FileUploadExercise fileUploadExercise = ModelFactory.generateFileUploadExercise(pastTimestamp, pastTimestamp, futureTimestamp, "png,pdf", course);
                fileUploadExercise.setTitle("FileUpload" + i);
                course.addExercises(fileUploadExercise);
                course = courseRepo.save(course);
                exerciseRepo.save(fileUploadExercise);
                for (int j = 1; j <= numberOfSubmissionPerExercise; j++) {
                    FileUploadSubmission submission = ModelFactory.generateFileUploadSubmissionWithFile(true, "path/to/file.pdf");
                    saveFileUploadSubmission(fileUploadExercise, submission, "student" + j);
                    if (numberOfAssessments >= j) {
                        Result result = generateResult(submission, currentUser);
                        submission = (FileUploadSubmission) saveResultInParticipation(submission, result);
                        fileUploadSubmissionRepo.save(submission);
                        generateComplaintAndResponses(j, numberOfComplaints, numberComplaintResponses, typeComplaint, result, currentUser);
                    }
                }
            }
        }
        course = courseRepo.save(course);
        return course;
    }

    private Submission saveResultInParticipation(Submission submission, Result result) {
        submission.addResult(result);
        StudentParticipation participation = (StudentParticipation) submission.getParticipation();
        participation.addResult(result);
        studentParticipationRepo.save(participation);
        return submission;
    }

    private Result generateResult(Submission submission, User currentUser) {
        Result result = new Result();
        result = resultRepo.save(result);
        result.setSubmission(submission);
        result.completionDate(pastTimestamp);
        result.resultString("3 of 10 points");
        result.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        result.setAssessor(currentUser);
        return result;
    }

    private void generateComplaintAndResponses(int j, int numberOfComplaints, int numberComplaintResponses, boolean typeComplaint, Result result, User currentUser) {
        result = resultRepo.save(result);
        if (numberOfComplaints >= j) {
            Complaint complaint = typeComplaint ? new Complaint().complaintType(ComplaintType.COMPLAINT) : new Complaint().complaintType(ComplaintType.MORE_FEEDBACK);
            complaint.setResult(result);
            complaint = complaintRepo.save(complaint);
            if (numberComplaintResponses >= j) {
                ComplaintResponse complaintResponse = createInitialEmptyResponse(typeComplaint ? "tutor5" : currentUser.getLogin(), complaint);
                complaintResponse.getComplaint().setAccepted(true);
                complaintResponse.setResponseText(typeComplaint ? "Accepted" : "SomeMoreFeedback");
                complaintResponseRepo.save(complaintResponse);
                complaint.setComplaintResponse(complaintResponse);
                complaintRepo.save(complaint);
            }
        }
    }

    /**
     * Stores for the given model a submission of the user and initiates the corresponding Result
     *
     * @param exercise exercise the submission belongs to
     * @param model    ModelingSubmission json as string contained in the submission
     * @param login    of the user the submission belongs to
     * @return submission stored in the modelingSubmissionRepository
     */
    public ModelingSubmission addModelingSubmissionWithEmptyResult(ModelingExercise exercise, String model, String login) {
        StudentParticipation participation = createAndSaveParticipationForExercise(exercise, login);
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(model, true);
        submission = modelSubmissionService.save(submission, exercise, login);
        Result result = new Result();
        result = resultRepo.save(result);
        result.setSubmission(submission);
        submission.addResult(result);
        participation.addResult(result);
        studentParticipationRepo.save(participation);
        modelingSubmissionRepo.save(submission);
        resultRepo.save(result);
        return submission;
    }

    public ModelingSubmission addModelingSubmission(ModelingExercise exercise, ModelingSubmission submission, String login) {
        StudentParticipation participation = createAndSaveParticipationForExercise(exercise, login);
        participation.addSubmission(submission);
        submission.setParticipation(participation);
        modelingSubmissionRepo.save(submission);
        studentParticipationRepo.save(participation);
        return submission;
    }

    public ModelingSubmission addModelingTeamSubmission(ModelingExercise exercise, ModelingSubmission submission, Team team) {
        StudentParticipation participation = addTeamParticipationForExercise(exercise, team.getId());
        participation.addSubmission(submission);
        submission.setParticipation(participation);
        modelingSubmissionRepo.save(submission);
        studentParticipationRepo.save(participation);
        return submission;
    }

    public ProgrammingSubmission addProgrammingSubmission(ProgrammingExercise exercise, ProgrammingSubmission submission, String login) {
        StudentParticipation participation = addStudentParticipationForProgrammingExercise(exercise, login);
        submission.setParticipation(participation);
        submission = programmingSubmissionRepo.save(submission);
        return submission;
    }

    /**
     * Add a submission with a result to the given programming exercise. The submission will be assigned to the corresponding participation of the given login (if exists or create a new participation).
     * The method will make sure that all necessary entities are connected.
     *
     * @param exercise for which to create the submission/participation/result combination.
     * @param submission to use for adding to the exercise/participation/result.
     * @param login of the user to identify the corresponding student participation.
     * @return the updated programming submission that is linked to all related entities.
     */
    public ProgrammingSubmission addProgrammingSubmissionWithResult(ProgrammingExercise exercise, ProgrammingSubmission submission, String login) {
        StudentParticipation participation = addStudentParticipationForProgrammingExercise(exercise, login);
        submission = programmingSubmissionRepo.save(submission);
        Result result = resultRepo.save(new Result().participation(participation));
        participation.addSubmission(submission);
        submission.setParticipation(participation);
        submission.addResult(result);
        submission = programmingSubmissionRepo.save(submission);
        result.setSubmission(submission);
        result = resultRepo.save(result);
        participation.addResult(result);
        studentParticipationRepo.save(participation);
        return submission;
    }

    public ProgrammingSubmission addProgrammingSubmissionWithResultAndAssessor(ProgrammingExercise exercise, ProgrammingSubmission submission, String login, String assessorLogin,
            AssessmentType assessmentType, boolean hasCompletionDate) {
        StudentParticipation participation = createAndSaveParticipationForExercise(exercise, login);
        Result result = new Result();
        result.setAssessor(getUserByLogin(assessorLogin));
        result.setAssessmentType(assessmentType);
        result.setScore(50D);
        if (hasCompletionDate) {
            result.setCompletionDate(ZonedDateTime.now());
        }

        studentParticipationRepo.save(participation);
        programmingSubmissionRepo.save(submission);

        submission.setParticipation(participation);
        result.setParticipation(participation);

        result = resultRepo.save(result);
        result.setSubmission(submission);
        submission.addResult(result);
        // Manual results are always rated and have a resultString which is defined in the client
        if (assessmentType.equals(AssessmentType.SEMI_AUTOMATIC)) {
            result.rated(true);
            result.resultString("1 of 13 passed, 1 issue, 5 of 10 points");
        }
        submission = programmingSubmissionRepo.save(submission);
        return submission;
    }

    public ProgrammingSubmission addProgrammingSubmissionToResultAndParticipation(Result result, StudentParticipation participation, String commitHash) {
        ProgrammingSubmission submission = createProgrammingSubmission(participation, false);
        submission.addResult(result);
        submission.setCommitHash(commitHash);
        resultRepo.save(result);
        result.setSubmission(submission);
        participation.addSubmission(submission);
        studentParticipationRepo.save(participation);
        return submissionRepository.save(submission);
    }

    public Submission addSubmission(Exercise exercise, Submission submission, String login) {
        StudentParticipation participation = createAndSaveParticipationForExercise(exercise, login);
        participation.addSubmission(submission);
        submission.setParticipation(participation);
        submissionRepository.save(submission);
        studentParticipationRepo.save(participation);
        return submission;
    }

    public Submission addSubmission(StudentParticipation participation, Submission submission) {
        participation.addSubmission(submission);
        submission.setParticipation(participation);
        submissionRepository.save(submission);
        studentParticipationRepo.save(participation);
        return submission;
    }

    public ModelingSubmission addModelingSubmissionWithResultAndAssessor(ModelingExercise exercise, ModelingSubmission submission, String login, String assessorLogin) {

        StudentParticipation participation = createAndSaveParticipationForExercise(exercise, login);
        participation.addSubmission(submission);
        submission = modelingSubmissionRepo.save(submission);

        Result result = new Result();

        result.setAssessor(getUserByLogin(assessorLogin));
        result.setAssessmentType(AssessmentType.MANUAL);
        result = resultRepo.save(result);
        submission = modelingSubmissionRepo.save(submission);
        studentParticipationRepo.save(participation);
        result = resultRepo.save(result);

        result.setSubmission(submission);
        submission.setParticipation(participation);
        submission.addResult(result);
        submission.getParticipation().addResult(result);
        submission = modelingSubmissionRepo.save(submission);
        studentParticipationRepo.save(participation);
        return submission;
    }

    public Submission addModelingSubmissionWithFinishedResultAndAssessor(ModelingExercise exercise, ModelingSubmission submission, String login, String assessorLogin) {
        StudentParticipation participation = createAndSaveParticipationForExercise(exercise, login);
        return addSubmissionWithFinishedResultsWithAssessor(participation, exercise, submission, login, assessorLogin);
    }

    public Submission addSubmissionWithTwoFinishedResultsWithAssessor(Exercise exercise, Submission submission, String login, String assessorLogin) {
        StudentParticipation participation = createAndSaveParticipationForExercise(exercise, login);
        submission = addSubmissionWithFinishedResultsWithAssessor(participation, exercise, submission, login, assessorLogin);
        submission = addSubmissionWithFinishedResultsWithAssessor(participation, exercise, submission, login, assessorLogin);
        return submission;
    }

    public Submission addSubmissionWithFinishedResultsWithAssessor(StudentParticipation participation, Exercise exercise, Submission submission, String login,
            String assessorLogin) {
        participation.addSubmission(submission);
        submission = saveSubmissionToRepo(submission);
        Result result = new Result();
        result.setAssessor(getUserByLogin(assessorLogin));
        result.setCompletionDate(ZonedDateTime.now());
        result = resultRepo.save(result);
        result.setSubmission(submission);
        submission.setParticipation(participation);
        submission.addResult(result);
        submission.getParticipation().addResult(result);
        submission = saveSubmissionToRepo(submission);
        result = resultRepo.save(result);
        studentParticipationRepo.save(participation);
        return submission;
    }

    private Submission saveSubmissionToRepo(Submission submission) {
        if (submission instanceof ModelingSubmission) {
            return modelingSubmissionRepo.save((ModelingSubmission) submission);
        }
        else if (submission instanceof TextSubmission) {
            return textSubmissionRepo.save((TextSubmission) submission);
        }
        else if (submission instanceof ProgrammingSubmission) {
            return programmingSubmissionRepo.save((ProgrammingSubmission) submission);
        }
        return null;
    }

    public FileUploadSubmission addFileUploadSubmission(FileUploadExercise fileUploadExercise, FileUploadSubmission fileUploadSubmission, String login) {
        StudentParticipation participation = createAndSaveParticipationForExercise(fileUploadExercise, login);
        participation.addSubmission(fileUploadSubmission);
        fileUploadSubmission.setParticipation(participation);
        fileUploadSubmissionRepo.save(fileUploadSubmission);
        studentParticipationRepo.save(participation);
        return fileUploadSubmission;
    }

    public FileUploadSubmission saveFileUploadSubmissionWithResultAndAssessorFeedback(FileUploadExercise exercise, FileUploadSubmission fileUploadSubmission, String login,
            String assessorLogin, List<Feedback> feedbacks) {
        StudentParticipation participation = createAndSaveParticipationForExercise(exercise, login);

        submissionRepository.save(fileUploadSubmission);

        participation.addSubmission(fileUploadSubmission);
        Result result = new Result();
        result.setAssessor(getUserByLogin(assessorLogin));
        result.setScore(100D);
        if (exercise.getReleaseDate() != null) {
            result.setCompletionDate(exercise.getReleaseDate());
        }
        else { // exam exercises do not have a release date
            result.setCompletionDate(ZonedDateTime.now());
        }
        result.setFeedbacks(feedbacks);
        result = resultRepo.save(result);
        result.setSubmission(fileUploadSubmission);
        fileUploadSubmission.setParticipation(participation);
        fileUploadSubmission.addResult(result);
        fileUploadSubmission.getParticipation().addResult(result);
        fileUploadSubmission = fileUploadSubmissionRepo.save(fileUploadSubmission);
        studentParticipationRepo.save(participation);
        return fileUploadSubmission;
    }

    public FileUploadSubmission saveFileUploadSubmissionWithResultAndAssessor(FileUploadExercise fileUploadExercise, FileUploadSubmission fileUploadSubmission, String login,
            String assessorLogin) {
        return saveFileUploadSubmissionWithResultAndAssessorFeedback(fileUploadExercise, fileUploadSubmission, login, assessorLogin, new ArrayList<>());
    }

    public FileUploadSubmission saveFileUploadSubmission(FileUploadExercise exercise, FileUploadSubmission submission, String login) {
        StudentParticipation participation = createAndSaveParticipationForExercise(exercise, login);
        participation.addSubmission(submission);
        submission.setParticipation(participation);
        submission = fileUploadSubmissionRepo.save(submission);
        return submission;
    }

    public TextSubmission saveTextSubmission(TextExercise exercise, TextSubmission submission, String login) {
        StudentParticipation participation = createAndSaveParticipationForExercise(exercise, login);
        participation.addSubmission(submission);
        submission.setParticipation(participation);
        submission = textSubmissionRepo.save(submission);
        return submission;
    }

    private TextSubmission saveTextSubmissionWithResultAndAssessor(TextExercise exercise, TextSubmission submission, String studentLogin, Long teamId, String assessorLogin) {
        StudentParticipation participation = Optional.ofNullable(studentLogin).map(login -> createAndSaveParticipationForExercise(exercise, login))
                .orElseGet(() -> addTeamParticipationForExercise(exercise, teamId));

        submissionRepository.save(submission);

        participation.addSubmission(submission);
        Result result = new Result();
        result.setAssessor(getUserByLogin(assessorLogin));
        result.setScore(100D);
        if (exercise.getReleaseDate() != null) {
            result.setCompletionDate(exercise.getReleaseDate());
        }
        else { // exam exercises do not have a release date
            result.setCompletionDate(ZonedDateTime.now());
        }
        result = resultRepo.save(result);
        result.setSubmission(submission);
        submission.setParticipation(participation);
        submission.addResult(result);
        submission.getParticipation().addResult(result);
        submission = textSubmissionRepo.save(submission);
        result = resultRepo.save(result);
        studentParticipationRepo.save(participation);

        submission = textSubmissionRepo.save(submission);
        return submission;
    }

    public TextSubmission saveTextSubmissionWithResultAndAssessor(TextExercise exercise, TextSubmission submission, String login, String assessorLogin) {
        return saveTextSubmissionWithResultAndAssessor(exercise, submission, login, null, assessorLogin);
    }

    public TextSubmission saveTextSubmissionWithResultAndAssessor(TextExercise exercise, TextSubmission submission, long teamId, String assessorLogin) {
        return saveTextSubmissionWithResultAndAssessor(exercise, submission, null, teamId, assessorLogin);
    }

    public TextSubmission addTextSubmissionWithResultAndAssessorAndFeedbacks(TextExercise exercise, TextSubmission submission, String studentLogin, String assessorLogin,
            List<Feedback> feedbacks) {
        submission = saveTextSubmissionWithResultAndAssessor(exercise, submission, studentLogin, null, assessorLogin);
        Result result = submission.getLatestResult();
        for (Feedback feedback : feedbacks) {
            // Important note to prevent 'JpaSystemException: null index column for collection':
            // 1) save the child entity (without connection to the parent entity) and make sure to re-assign the return value
            feedback = feedbackRepo.save(feedback);
            // this also invokes feedback.setResult(result)
            // Important note to prevent 'JpaSystemException: null index column for collection':
            // 2) connect child and parent entity
            result.addFeedback(feedback);
        }
        // this automatically saves the feedback because of the CascadeType.All annotation
        // Important note to prevent 'JpaSystemException: null index column for collection':
        // 3) save the parent entity and make sure to re-assign the return value
        result = resultRepo.save(result);

        return submission;
    }

    public TextSubmission addAndSaveTextBlocksToTextSubmission(Set<TextBlock> blocks, TextSubmission submission) {
        blocks.forEach(block -> {
            block.setSubmission(submission);
            block.setTextFromSubmission();
            block.computeId();
        });
        submission.setBlocks(blocks);
        textBlockRepo.saveAll(blocks);
        return textSubmissionRepo.save(submission);
    }

    public ModelingSubmission addModelingSubmissionFromResources(ModelingExercise exercise, String path, String login) throws Exception {
        String model = FileUtils.loadFileFromResources(path);
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(model, true);
        submission = addModelingSubmission(exercise, submission, login);
        checkModelingSubmissionCorrectlyStored(submission.getId(), model);
        return submission;
    }

    public void checkModelingSubmissionCorrectlyStored(Long submissionId, String sentModel) {
        Optional<ModelingSubmission> modelingSubmission = modelingSubmissionRepo.findById(submissionId);
        assertThat(modelingSubmission).as("submission correctly stored").isPresent();
        checkModelsAreEqual(modelingSubmission.get().getModel(), sentModel);
    }

    public void checkModelsAreEqual(String storedModel, String sentModel) {
        JsonObject sentModelObject = parseString(sentModel).getAsJsonObject();
        JsonObject storedModelObject = parseString(storedModel).getAsJsonObject();
        assertThat(storedModelObject).as("model correctly stored").isEqualTo(sentModelObject);
    }

    public Result addModelingAssessmentForSubmission(ModelingExercise exercise, ModelingSubmission submission, String path, String login, boolean submit) throws Exception {
        List<Feedback> feedbackList = loadAssessmentFomResources(path);
        Result result = assessmentService.saveManualAssessment(submission, feedbackList, null);
        result.setParticipation(submission.getParticipation().results(null));
        result.setAssessor(getUserByLogin(login));
        resultRepo.save(result);
        if (submit) {
            assessmentService.submitManualAssessment(result.getId(), exercise, submission.getSubmissionDate());
        }
        return resultRepo.findWithEagerSubmissionAndFeedbackAndAssessorById(result.getId()).get();
    }

    public ExampleSubmission addExampleSubmission(ExampleSubmission exampleSubmission) {
        Submission submission;
        if (exampleSubmission.getSubmission() instanceof ModelingSubmission) {
            submission = modelingSubmissionRepo.save((ModelingSubmission) exampleSubmission.getSubmission());
        }
        else {
            submission = textSubmissionRepo.save((TextSubmission) exampleSubmission.getSubmission());
        }
        exampleSubmission.setSubmission(submission);
        return exampleSubmissionRepo.save(exampleSubmission);
    }

    public ComplaintResponse createInitialEmptyResponse(String loginOfTutor, Complaint complaint) {
        ComplaintResponse complaintResponse = new ComplaintResponse();
        complaintResponse.setComplaint(complaint);
        User tutor = userRepo.findOneByLogin(loginOfTutor).get();
        complaintResponse.setReviewer(tutor);
        complaintResponse = complaintResponseRepo.saveAndFlush(complaintResponse);
        return complaintResponse;
    }

    public List<Feedback> loadAssessmentFomResources(String path) throws Exception {
        String fileContent = FileUtils.loadFileFromResources(path);
        return mapper.readValue(fileContent, mapper.getTypeFactory().constructCollectionType(List.class, Feedback.class));
    }

    public User getUserByLogin(String login) {
        return userRepo.findOneWithAuthoritiesByLogin(login).orElseThrow(() -> new IllegalArgumentException("Provided login " + login + " does not exist in database"));
    }

    public void updateExerciseDueDate(long exerciseId, ZonedDateTime newDueDate) {
        Exercise exercise = exerciseRepo.findById(exerciseId).orElseThrow(() -> new IllegalArgumentException("Exercise with given ID " + exerciseId + " could not be found"));
        exercise.setDueDate(newDueDate);
        if (exercise instanceof ProgrammingExercise) {
            ((ProgrammingExercise) exercise).setBuildAndTestStudentSubmissionsAfterDueDate(newDueDate);
        }
        exerciseRepo.save(exercise);
    }

    public void updateAssessmentDueDate(long exerciseId, ZonedDateTime newDueDate) {
        Exercise exercise = exerciseRepo.findById(exerciseId).orElseThrow(() -> new IllegalArgumentException("Exercise with given ID " + exerciseId + " could not be found"));
        exercise.setAssessmentDueDate(newDueDate);
        exerciseRepo.save(exercise);
    }

    public void updateResultCompletionDate(long resultId, ZonedDateTime newCompletionDate) {
        Result result = resultRepo.findById(resultId).orElseThrow(() -> new IllegalArgumentException("Result with given ID " + resultId + " could not be found"));
        result.setCompletionDate(newCompletionDate);
        resultRepo.save(result);
    }

    public void addComplaints(String studentLogin, Participation participation, int numberOfComplaints, ComplaintType complaintType) {
        for (int i = 0; i < numberOfComplaints; i++) {
            Result dummyResult = new Result().participation(participation);
            dummyResult = resultRepo.save(dummyResult);
            Complaint complaint = new Complaint().participant(getUserByLogin(studentLogin)).result(dummyResult).complaintType(complaintType);
            complaintRepo.save(complaint);
        }
    }

    public void addTeamComplaints(Team team, Participation participation, int numberOfComplaints, ComplaintType complaintType) {
        for (int i = 0; i < numberOfComplaints; i++) {
            Result dummyResult = new Result().participation(participation);
            dummyResult = resultRepo.save(dummyResult);
            Complaint complaint = new Complaint().participant(team).result(dummyResult).complaintType(complaintType);
            complaintRepo.save(complaint);
        }
    }

    public Set<ExerciseHint> addHintsToExercise(Exercise exercise) {
        ExerciseHint exerciseHint1 = new ExerciseHint().exercise(exercise).title("title 1").content("content 1");
        ExerciseHint exerciseHint2 = new ExerciseHint().exercise(exercise).title("title 2").content("content 2");
        ExerciseHint exerciseHint3 = new ExerciseHint().exercise(exercise).title("title 3").content("content 3");
        Set<ExerciseHint> hints = new HashSet<>();
        hints.add(exerciseHint1);
        hints.add(exerciseHint2);
        hints.add(exerciseHint3);
        exercise.setExerciseHints(hints);
        exerciseHintRepository.saveAll(hints);

        return hints;
    }

    public ProgrammingExercise loadProgrammingExerciseWithEagerReferences(ProgrammingExercise lazyExercise) {
        return programmingExerciseTestRepository.findOneWithEagerEverything(lazyExercise.getId());
    }

    public <T extends Exercise> T addHintsToProblemStatement(T exercise) {
        final var statement = exercise.getProblemStatement() == null ? "" : exercise.getProblemStatement();
        final var hintsInStatement = exercise.getExerciseHints().stream().map(ExerciseHint::getId).map(Object::toString).collect(Collectors.joining(", ", "{", "}"));
        exercise.setProblemStatement(statement + hintsInStatement);
        return exerciseRepo.save(exercise);
    }

    /**
     * Generates an example submission for a given model and exercise
     * @param modelOrText given uml model for the example submission
     * @param exercise exercise for which the example submission is created
     * @param flagAsExampleSubmission true if the submission is an example submission
     * @return  created example submission
     */
    public ExampleSubmission generateExampleSubmission(String modelOrText, Exercise exercise, boolean flagAsExampleSubmission) {
        return generateExampleSubmission(modelOrText, exercise, flagAsExampleSubmission, false);
    }

    /**
     * Generates an example submission for a given model and exercise
     * @param modelOrText given uml model for the example submission
     * @param exercise exercise for which the example submission is created
     * @param flagAsExampleSubmission true if the submission is an example submission
     * @param usedForTutorial true if the example submission is used for tutorial
     * @return  created example submission
     */
    public ExampleSubmission generateExampleSubmission(String modelOrText, Exercise exercise, boolean flagAsExampleSubmission, boolean usedForTutorial) {
        Submission submission;
        if (exercise instanceof ModelingExercise) {
            submission = ModelFactory.generateModelingSubmission(modelOrText, false);
        }
        else {
            submission = ModelFactory.generateTextSubmission(modelOrText, Language.ENGLISH, false);
        }
        submission.setExampleSubmission(flagAsExampleSubmission);
        return ModelFactory.generateExampleSubmission(submission, exercise, usedForTutorial);
    }

    /**
     * Generates a submitted answer for a given question.
     * @param question given question, the answer is for
     * @param correct boolean whether the answer should be correct or not
     * @return created SubmittedAnswer
     */
    public SubmittedAnswer generateSubmittedAnswerFor(QuizQuestion question, boolean correct) {
        if (question instanceof MultipleChoiceQuestion) {
            var submittedAnswer = new MultipleChoiceSubmittedAnswer();
            submittedAnswer.setQuizQuestion(question);

            for (var answerOption : ((MultipleChoiceQuestion) question).getAnswerOptions()) {
                if (answerOption.isIsCorrect().equals(correct)) {
                    submittedAnswer.addSelectedOptions(answerOption);
                }
            }
            return submittedAnswer;
        }
        else if (question instanceof DragAndDropQuestion) {
            var submittedAnswer = new DragAndDropSubmittedAnswer();
            submittedAnswer.setQuizQuestion(question);

            DragItem dragItem1 = ((DragAndDropQuestion) question).getDragItems().get(0);
            dragItem1.setQuestion((DragAndDropQuestion) question);
            System.out.println(dragItem1.toString());
            DragItem dragItem2 = ((DragAndDropQuestion) question).getDragItems().get(1);
            dragItem2.setQuestion((DragAndDropQuestion) question);
            System.out.println(dragItem2.toString());
            DragItem dragItem3 = ((DragAndDropQuestion) question).getDragItems().get(2);
            dragItem3.setQuestion((DragAndDropQuestion) question);
            System.out.println(dragItem3.toString());

            DropLocation dropLocation1 = ((DragAndDropQuestion) question).getDropLocations().get(0);
            dropLocation1.setQuestion((DragAndDropQuestion) question);
            System.out.println(dropLocation1.toString());
            DropLocation dropLocation2 = ((DragAndDropQuestion) question).getDropLocations().get(1);
            dropLocation2.setQuestion((DragAndDropQuestion) question);
            System.out.println(dropLocation2.toString());
            DropLocation dropLocation3 = ((DragAndDropQuestion) question).getDropLocations().get(2);
            dropLocation3.setQuestion((DragAndDropQuestion) question);
            System.out.println(dropLocation3.toString());

            if (correct) {
                submittedAnswer.addMappings(new DragAndDropMapping().dragItem(dragItem1).dropLocation(dropLocation1));
                submittedAnswer.addMappings(new DragAndDropMapping().dragItem(dragItem2).dropLocation(dropLocation2));
                submittedAnswer.addMappings(new DragAndDropMapping().dragItem(dragItem3).dropLocation(dropLocation3));
            }
            else {
                submittedAnswer.addMappings(new DragAndDropMapping().dragItem(dragItem2).dropLocation(dropLocation3));
                submittedAnswer.addMappings(new DragAndDropMapping().dragItem(dragItem1).dropLocation(dropLocation2));
                submittedAnswer.addMappings(new DragAndDropMapping().dragItem(dragItem3).dropLocation(dropLocation1));
            }

            return submittedAnswer;
        }
        else if (question instanceof ShortAnswerQuestion) {
            var submittedAnswer = new ShortAnswerSubmittedAnswer();
            submittedAnswer.setQuizQuestion(question);

            for (var spot : ((ShortAnswerQuestion) question).getSpots()) {
                ShortAnswerSubmittedText submittedText = new ShortAnswerSubmittedText();
                submittedText.setSpot(spot);
                var correctText = ((ShortAnswerQuestion) question).getCorrectSolutionForSpot(spot).iterator().next().getText();
                if (correct) {
                    submittedText.setText(correctText);
                }
                else {
                    submittedText.setText(correctText.toUpperCase());
                }
                submittedAnswer.addSubmittedTexts(submittedText);
                // also invoke remove once
                submittedAnswer.removeSubmittedTexts(submittedText);
                submittedAnswer.addSubmittedTexts(submittedText);
            }
            return submittedAnswer;
        }
        return null;
    }

    public SubmittedAnswer generateSubmittedAnswerForQuizWithCorrectAndFalseAnswers(QuizQuestion question) {
        if (question instanceof MultipleChoiceQuestion) {
            var submittedAnswer = new MultipleChoiceSubmittedAnswer();
            submittedAnswer.setQuizQuestion(question);

            for (var answerOption : ((MultipleChoiceQuestion) question).getAnswerOptions()) {
                submittedAnswer.addSelectedOptions(answerOption);
            }
            return submittedAnswer;
        }
        else if (question instanceof DragAndDropQuestion) {
            var submittedAnswer = new DragAndDropSubmittedAnswer();
            submittedAnswer.setQuizQuestion(question);

            DragItem dragItem1 = ((DragAndDropQuestion) question).getDragItems().get(0);
            dragItem1.setQuestion((DragAndDropQuestion) question);
            System.out.println(dragItem1.toString());
            DragItem dragItem2 = ((DragAndDropQuestion) question).getDragItems().get(1);
            dragItem2.setQuestion((DragAndDropQuestion) question);
            System.out.println(dragItem2.toString());
            DragItem dragItem3 = ((DragAndDropQuestion) question).getDragItems().get(2);
            dragItem3.setQuestion((DragAndDropQuestion) question);
            System.out.println(dragItem3.toString());

            DropLocation dropLocation1 = ((DragAndDropQuestion) question).getDropLocations().get(0);
            dropLocation1.setQuestion((DragAndDropQuestion) question);
            System.out.println(dropLocation1.toString());
            DropLocation dropLocation2 = ((DragAndDropQuestion) question).getDropLocations().get(1);
            dropLocation2.setQuestion((DragAndDropQuestion) question);
            System.out.println(dropLocation2.toString());
            DropLocation dropLocation3 = ((DragAndDropQuestion) question).getDropLocations().get(2);
            dropLocation3.setQuestion((DragAndDropQuestion) question);
            System.out.println(dropLocation3.toString());

            submittedAnswer.addMappings(new DragAndDropMapping().dragItem(dragItem1).dropLocation(dropLocation1));
            submittedAnswer.addMappings(new DragAndDropMapping().dragItem(dragItem2).dropLocation(dropLocation3));
            submittedAnswer.addMappings(new DragAndDropMapping().dragItem(dragItem3).dropLocation(dropLocation2));

            return submittedAnswer;
        }
        else if (question instanceof ShortAnswerQuestion) {
            var submittedAnswer = new ShortAnswerSubmittedAnswer();
            submittedAnswer.setQuizQuestion(question);

            for (var spot : ((ShortAnswerQuestion) question).getSpots()) {
                ShortAnswerSubmittedText submittedText = new ShortAnswerSubmittedText();
                submittedText.setSpot(spot);
                var correctText = ((ShortAnswerQuestion) question).getCorrectSolutionForSpot(spot).iterator().next().getText();
                if (spot.getSpotNr() == 2) {
                    submittedText.setText(correctText);
                }
                else {
                    submittedText.setText("wrong submitted text");
                }
                submittedAnswer.addSubmittedTexts(submittedText);
                // also invoke remove once
                submittedAnswer.removeSubmittedTexts(submittedText);
                submittedAnswer.addSubmittedTexts(submittedText);
            }
            return submittedAnswer;
        }
        return null;
    }

    @NotNull
    public QuizExercise createQuiz(Course course, ZonedDateTime releaseDate, ZonedDateTime dueDate) {
        QuizExercise quizExercise = ModelFactory.generateQuizExercise(releaseDate, dueDate, course);
        quizExercise.addQuestions(createMultipleChoiceQuestion());
        quizExercise.addQuestions(createDragAndDropQuestion());
        quizExercise.addQuestions(createShortAnswerQuestion());
        quizExercise.setMaxPoints(quizExercise.getOverallQuizPoints());
        quizExercise.setGradingInstructions(null);
        return quizExercise;
    }

    @NotNull
    public QuizExercise createQuizForExam(ExerciseGroup exerciseGroup) {
        QuizExercise quizExercise = ModelFactory.generateQuizExerciseForExam(exerciseGroup);
        quizExercise.addQuestions(createMultipleChoiceQuestion());
        quizExercise.addQuestions(createDragAndDropQuestion());
        quizExercise.addQuestions(createShortAnswerQuestion());
        quizExercise.setMaxPoints(quizExercise.getOverallQuizPoints());
        quizExercise.setGradingInstructions(null);
        return quizExercise;
    }

    @NotNull
    public ShortAnswerQuestion createShortAnswerQuestion() {
        ShortAnswerQuestion sa = (ShortAnswerQuestion) new ShortAnswerQuestion().title("SA").score(2).text("This is a long answer text");
        sa.setScoringType(ScoringType.PROPORTIONAL_WITHOUT_PENALTY);
        // TODO: we should test different values here
        sa.setMatchLetterCase(true);
        sa.setSimilarityValue(100);

        var shortAnswerSpot1 = new ShortAnswerSpot().spotNr(0).width(1);
        shortAnswerSpot1.setTempID(generateTempId());
        var shortAnswerSpot2 = new ShortAnswerSpot().spotNr(2).width(2);
        shortAnswerSpot2.setTempID(generateTempId());
        sa.getSpots().add(shortAnswerSpot1);
        sa.getSpots().add(shortAnswerSpot2);

        var shortAnswerSolution1 = new ShortAnswerSolution().text("is");
        shortAnswerSolution1.setTempID(generateTempId());
        var shortAnswerSolution2 = new ShortAnswerSolution().text("long");
        shortAnswerSolution2.setTempID(generateTempId());
        sa.addSolution(shortAnswerSolution1);
        // also invoke remove once
        sa.removeSolution(shortAnswerSolution1);
        sa.addSolution(shortAnswerSolution1);
        sa.addSolution(shortAnswerSolution2);

        var mapping1 = new ShortAnswerMapping().spot(sa.getSpots().get(0)).solution(sa.getSolutions().get(0));
        shortAnswerSolution1.addMappings(mapping1);
        shortAnswerSpot1.addMappings(mapping1);
        // also invoke remove once
        shortAnswerSolution1.removeMappings(mapping1);
        shortAnswerSpot1.removeMappings(mapping1);
        shortAnswerSolution1.addMappings(mapping1);
        shortAnswerSpot1.addMappings(mapping1);
        assertThat(shortAnswerSolution1.getMappings()).isNotEmpty();
        assertThat(shortAnswerSpot1.getMappings()).isNotEmpty();
        System.out.println(shortAnswerSolution1.toString());
        System.out.println(shortAnswerSpot1.toString());

        var mapping2 = new ShortAnswerMapping().spot(sa.getSpots().get(1)).solution(sa.getSolutions().get(1));
        sa.addCorrectMapping(mapping1);
        assertThat(sa).isEqualTo(mapping1.getQuestion());
        sa.removeCorrectMapping(mapping1);
        sa.addCorrectMapping(mapping1);
        sa.addCorrectMapping(mapping2);
        sa.setExplanation("Explanation");
        sa.setRandomizeOrder(true);
        // invoke some util methods
        System.out.println("ShortAnswer: " + sa.toString());
        System.out.println("ShortAnswer.hashCode: " + sa.hashCode());
        sa.copyQuestionId();
        return sa;
    }

    @NotNull
    public DragAndDropQuestion createDragAndDropQuestion() {
        DragAndDropQuestion dnd = (DragAndDropQuestion) new DragAndDropQuestion().title("DnD").score(3).text("Q2");
        dnd.setScoringType(ScoringType.PROPORTIONAL_WITH_PENALTY);

        var dropLocation1 = new DropLocation().posX(10d).posY(10d).height(10d).width(10d);
        dropLocation1.setTempID(generateTempId());
        var dropLocation2 = new DropLocation().posX(20d).posY(20d).height(10d).width(10d);
        dropLocation2.setTempID(generateTempId());
        var dropLocation3 = new DropLocation().posX(30d).posY(30d).height(10d).width(10d);
        dropLocation3.setTempID(generateTempId());
        dnd.addDropLocation(dropLocation1);
        // also invoke remove once
        dnd.removeDropLocation(dropLocation1);
        dnd.addDropLocation(dropLocation1);
        dnd.addDropLocation(dropLocation2);
        dnd.addDropLocation(dropLocation3);

        var dragItem1 = new DragItem().text("D1");
        dragItem1.setTempID(generateTempId());
        var dragItem2 = new DragItem().text("D2");
        dragItem2.setTempID(generateTempId());
        var dragItem3 = new DragItem().text("D3");
        dragItem3.setTempID(generateTempId());
        dnd.addDragItem(dragItem1);
        assertThat(dragItem1.getQuestion()).isEqualTo(dnd);
        // also invoke remove once
        dnd.removeDragItem(dragItem1);
        dnd.addDragItem(dragItem1);
        dnd.addDragItem(dragItem2);
        dnd.addDragItem(dragItem3);

        var mapping1 = new DragAndDropMapping().dragItem(dragItem1).dropLocation(dropLocation1);
        dragItem1.addMappings(mapping1);
        // also invoke remove
        dragItem1.removeMappings(mapping1);
        dragItem1.addMappings(mapping1);
        assertThat(dragItem1.getMappings()).isNotEmpty();

        dnd.addCorrectMapping(mapping1);
        dnd.removeCorrectMapping(mapping1);
        dnd.addCorrectMapping(mapping1);
        var mapping2 = new DragAndDropMapping().dragItem(dragItem2).dropLocation(dropLocation2);
        dnd.addCorrectMapping(mapping2);
        var mapping3 = new DragAndDropMapping().dragItem(dragItem3).dropLocation(dropLocation3);
        dnd.addCorrectMapping(mapping3);
        dnd.setExplanation("Explanation");
        // invoke some util methods
        System.out.println("DnD: " + dnd.toString());
        System.out.println("DnD.hashCode: " + dnd.hashCode());
        dnd.copyQuestionId();
        return dnd;
    }

    public Long generateTempId() {
        return ThreadLocalRandom.current().nextLong(Long.MAX_VALUE);
    }

    @NotNull
    public MultipleChoiceQuestion createMultipleChoiceQuestion() {
        MultipleChoiceQuestion mc = (MultipleChoiceQuestion) new MultipleChoiceQuestion().title("MC").score(4).text("Q1");
        mc.setScoringType(ScoringType.ALL_OR_NOTHING);
        mc.getAnswerOptions().add(new AnswerOption().text("A").hint("H1").explanation("E1").isCorrect(true));
        mc.getAnswerOptions().add(new AnswerOption().text("B").hint("H2").explanation("E2").isCorrect(false));
        mc.setExplanation("Explanation");
        // invoke some util methods
        System.out.println("MC: " + mc.toString());
        System.out.println("MC.hashCode: " + mc.hashCode());
        mc.copyQuestionId();
        return mc;
    }

    /**
     * Generate submissions for a student for an exercise. Results are mixed.
     * @param quizExercise QuizExercise the submissions are for (we assume 3 questions here)
     * @param studentID ID of the student
     * @param submitted Boolean if it is submitted or not
     * @param submissionDate Submission date
     */
    public QuizSubmission generateSubmissionForThreeQuestions(QuizExercise quizExercise, int studentID, boolean submitted, ZonedDateTime submissionDate) {
        QuizSubmission quizSubmission = new QuizSubmission();
        QuizQuestion quizQuestion1 = quizExercise.getQuizQuestions().get(0);
        QuizQuestion quizQuestion2 = quizExercise.getQuizQuestions().get(1);
        QuizQuestion quizQuestion3 = quizExercise.getQuizQuestions().get(2);
        quizSubmission.addSubmittedAnswers(generateSubmittedAnswerFor(quizQuestion1, studentID % 2 == 0));
        quizSubmission.addSubmittedAnswers(generateSubmittedAnswerFor(quizQuestion2, studentID % 3 == 0));
        quizSubmission.addSubmittedAnswers(generateSubmittedAnswerFor(quizQuestion3, studentID % 4 == 0));
        quizSubmission.submitted(submitted);
        quizSubmission.submissionDate(submissionDate);

        return quizSubmission;
    }

    /**
     * Generate a submission with all or none options of a MultipleChoiceQuestion selected, if there is one in the exercise
     * @param quizExercise Exercise the submission is for
     * @param submitted Boolean whether it is submitted or not
     * @param submissionDate Submission date
     * @param selectEverything Boolean whether every answer option should be selected or none
     */
    public QuizSubmission generateSpecialSubmissionWithResult(QuizExercise quizExercise, boolean submitted, ZonedDateTime submissionDate, boolean selectEverything) {
        QuizSubmission quizSubmission = new QuizSubmission();

        for (QuizQuestion question : quizExercise.getQuizQuestions()) {
            if (question instanceof MultipleChoiceQuestion) {
                var submittedAnswer = new MultipleChoiceSubmittedAnswer();
                submittedAnswer.setQuizQuestion(question);
                if (selectEverything) {
                    for (var answerOption : ((MultipleChoiceQuestion) question).getAnswerOptions()) {
                        submittedAnswer.addSelectedOptions(answerOption);
                    }
                }
                quizSubmission.addSubmittedAnswers(submittedAnswer);

            }
            else {
                quizSubmission.addSubmittedAnswers(generateSubmittedAnswerFor(question, false));
            }
        }
        quizSubmission.submitted(submitted);
        quizSubmission.submissionDate(submissionDate);

        return quizSubmission;
    }

    // TODO: find some generic solution for the following duplicated code

    @NotNull
    public FileUploadExercise findFileUploadExerciseWithTitle(Collection<Exercise> exercises, String title) {
        Optional<Exercise> exercise = exercises.stream().filter(e -> e.getTitle().equals(title)).findFirst();
        if (exercise.isEmpty()) {
            fail("Could not find file upload exercise with title " + title);
        }
        else {
            if (exercise.get() instanceof FileUploadExercise) {
                return (FileUploadExercise) exercise.get();
            }
        }
        fail("Could not find file upload exercise with title " + title);
        // just to prevent compiler warnings, we have failed anyway here
        return new FileUploadExercise();
    }

    @NotNull
    public ModelingExercise findModelingExerciseWithTitle(Collection<Exercise> exercises, String title) {
        Optional<Exercise> exercise = exercises.stream().filter(e -> e.getTitle().equals(title)).findFirst();
        if (exercise.isEmpty()) {
            fail("Could not find modeling exercise with title " + title);
        }
        else {
            if (exercise.get() instanceof ModelingExercise) {
                return (ModelingExercise) exercise.get();
            }
        }
        fail("Could not find modeling exercise with title " + title);
        // just to prevent compiler warnings, we have failed anyway here
        return new ModelingExercise();
    }

    @NotNull
    public TextExercise findTextExerciseWithTitle(Collection<Exercise> exercises, String title) {
        Optional<Exercise> exercise = exercises.stream().filter(e -> e.getTitle().equals(title)).findFirst();
        if (exercise.isEmpty()) {
            fail("Could not find text exercise with title " + title);
        }
        else {
            if (exercise.get() instanceof TextExercise) {
                return (TextExercise) exercise.get();
            }
        }
        fail("Could not find text exercise with title " + title);
        // just to prevent compiler warnings, we have failed anyway here
        return new TextExercise();
    }

    @NotNull
    public ProgrammingExercise findProgrammingExerciseWithTitle(Collection<Exercise> exercises, String title) {
        Optional<Exercise> exercise = exercises.stream().filter(e -> e.getTitle().equals(title)).findFirst();
        if (exercise.isEmpty()) {
            fail("Could not find programming exercise with title " + title);
        }
        else {
            if (exercise.get() instanceof ProgrammingExercise) {
                return (ProgrammingExercise) exercise.get();
            }
        }
        fail("Could not find programming exercise with title " + title);
        // just to prevent compiler warnings, we have failed anyway here
        return new ProgrammingExercise();
    }

    public PageableSearchDTO<String> configureSearch(String searchTerm) {
        final var search = new PageableSearchDTO<String>();
        search.setPage(1);
        search.setPageSize(10);
        search.setSearchTerm(searchTerm);
        search.setSortedColumn(Exercise.ExerciseSearchColumn.ID.name());
        if ("".equals(searchTerm)) {
            search.setSortingOrder(SortingOrder.ASCENDING);
        }
        else {
            search.setSortingOrder(SortingOrder.DESCENDING);
        }
        return search;
    }

    public LinkedMultiValueMap<String, String> exerciseSearchMapping(PageableSearchDTO<String> search) {
        final var mapType = new TypeToken<Map<String, String>>() {
        }.getType();
        final var gson = new Gson();
        final Map<String, String> params = new Gson().fromJson(gson.toJson(search), mapType);
        final var paramMap = new LinkedMultiValueMap<String, String>();
        params.forEach(paramMap::add);
        return paramMap;
    }

    public void checkFeedbackCorrectlyStored(List<Feedback> sentFeedback, List<Feedback> storedFeedback, FeedbackType feedbackType) {
        assertThat(sentFeedback.size()).as("contains the same amount of feedback").isEqualTo(storedFeedback.size());
        Result storedFeedbackResult = new Result();
        Result sentFeedbackResult = new Result();
        storedFeedbackResult.setFeedbacks(storedFeedback);
        sentFeedbackResult.setFeedbacks(sentFeedback);

        double calculatedTotalPoints = resultRepo.calculateTotalPoints(storedFeedback);
        double totalPoints = resultRepo.constrainToRange(calculatedTotalPoints, 20.0);
        storedFeedbackResult.setScore(totalPoints, 20.0);
        storedFeedbackResult.setResultString(totalPoints, 20.0);

        double calculatedTotalPoints2 = resultRepo.calculateTotalPoints(sentFeedback);
        double totalPoints2 = resultRepo.constrainToRange(calculatedTotalPoints2, 20.0);
        sentFeedbackResult.setScore(totalPoints2, 20.0);
        sentFeedbackResult.setResultString(totalPoints2, 20.0);

        assertThat(storedFeedbackResult.getScore()).as("stored feedback evaluates to the same score as sent feedback").isEqualTo(sentFeedbackResult.getScore());
        storedFeedback.forEach(feedback -> {
            assertThat(feedback.getType()).as("type has been set correctly").isEqualTo(feedbackType);
        });
    }

    public Course createCourseWithProgrammingExerciseAndIllegalAndLegalSubmissions() {
        Course course = ModelFactory.generateCourse(null, pastTimestamp, futureTimestamp, new HashSet<>(), "tumuser", "tutor", "instructor");
        course = courseRepo.save(course);
        ProgrammingExercise programmingExercise = ModelFactory.generateProgrammingExercise(null, null, course);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        var student1 = userRepo.findOneByLogin("student1").orElseThrow();
        var student2 = userRepo.findOneByLogin("student2").orElseThrow();
        ProgrammingExerciseStudentParticipation programmingExerciseStudentParticipation1 = ModelFactory
                .generateProgrammingExerciseStudentParticipation(InitializationState.INITIALIZED, programmingExercise, student1);
        ProgrammingExerciseStudentParticipation programmingExerciseStudentParticipation2 = ModelFactory
                .generateProgrammingExerciseStudentParticipation(InitializationState.INITIALIZED, programmingExercise, student2);
        programmingExerciseStudentParticipation1 = programmingExerciseStudentParticipationRepo.save(programmingExerciseStudentParticipation1);
        programmingExerciseStudentParticipation2 = programmingExerciseStudentParticipationRepo.save(programmingExerciseStudentParticipation2);
        ProgrammingSubmission programmingSubmission1 = ModelFactory.generateProgrammingSubmission(true);
        programmingSubmissionRepo.save(programmingSubmission1);
        programmingSubmission1.setType(SubmissionType.MANUAL);
        ProgrammingSubmission programmingSubmission2 = ModelFactory.generateProgrammingSubmission(true);
        programmingSubmission2.setType(SubmissionType.ILLEGAL);
        programmingSubmissionRepo.save(programmingSubmission2);
        programmingExerciseStudentParticipation1.addSubmission(programmingSubmission1);
        programmingExerciseStudentParticipation2.addSubmission(programmingSubmission2);
        programmingSubmission1.setParticipation(programmingExerciseStudentParticipation1);
        programmingSubmission2.setParticipation(programmingExerciseStudentParticipation2);
        programmingSubmissionRepo.save(programmingSubmission2);
        programmingSubmissionRepo.save(programmingSubmission1);
        programmingExerciseStudentParticipationRepo.save(programmingExerciseStudentParticipation1);
        programmingExerciseStudentParticipationRepo.save(programmingExerciseStudentParticipation2);
        return course;
    }
}
