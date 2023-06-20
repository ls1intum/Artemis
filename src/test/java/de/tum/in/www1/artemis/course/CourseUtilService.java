package de.tum.in.www1.artemis.course;

import static de.tum.in.www1.artemis.participation.ParticipationFactory.generateResult;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.assessment.ComplaintUtilService;
import de.tum.in.www1.artemis.competency.CompetencyUtilService;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.*;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.participation.TutorParticipation;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.domain.quiz.QuizSubmission;
import de.tum.in.www1.artemis.exam.ExamUtilService;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.exercise.fileuploadexercise.FileUploadExerciseFactory;
import de.tum.in.www1.artemis.exercise.fileuploadexercise.FileUploadExerciseUtilService;
import de.tum.in.www1.artemis.exercise.modelingexercise.ModelingExerciseFactory;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseFactory;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseUtilService;
import de.tum.in.www1.artemis.exercise.quizexercise.QuizExerciseFactory;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseFactory;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseUtilService;
import de.tum.in.www1.artemis.lecture.LectureFactory;
import de.tum.in.www1.artemis.lecture.LectureUtilService;
import de.tum.in.www1.artemis.organization.OrganizationUtilService;
import de.tum.in.www1.artemis.participation.ParticipationFactory;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.ModelingSubmissionService;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.util.FileUtils;

/**
 * Service responsible for initializing the database with specific testdata related to courses for use in integration tests.
 */
@Service
public class CourseUtilService {

    private static final ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(1);

    private static final ZonedDateTime futureTimestamp = ZonedDateTime.now().plusDays(1);

    private static final ZonedDateTime futureFutureTimestamp = ZonedDateTime.now().plusDays(2);

    @Autowired
    private CourseRepository courseRepo;

    @Autowired
    private LectureRepository lectureRepo;

    @Autowired
    private AttachmentRepository attachmentRepo;

    @Autowired
    private ExerciseRepository exerciseRepo;

    @Autowired
    private TutorParticipationRepository tutorParticipationRepo;

    @Autowired
    private ExampleSubmissionRepository exampleSubmissionRepo;

    @Autowired
    private StudentParticipationRepository studentParticipationRepo;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private ResultRepository resultRepo;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private ModelingSubmissionRepository modelingSubmissionRepo;

    @Autowired
    private TextSubmissionRepository textSubmissionRepo;

    @Autowired
    private FileUploadSubmissionRepository fileUploadSubmissionRepo;

    @Autowired
    private ExerciseGroupRepository exerciseGroupRepository;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private OrganizationUtilService organizationTestService;

    @Autowired
    private LectureUtilService lectureUtilService;

    @Autowired
    private CompetencyUtilService competencyUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ModelingSubmissionService modelSubmissionService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private FileUploadExerciseUtilService fileUploadExerciseUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private ComplaintUtilService complaintUtilService;

    public Course createCourse() {
        return createCourse(null);
    }

    public Course createCourse(Long id) {
        Course course = CourseFactory.generateCourse(id, pastTimestamp, futureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        return courseRepo.save(course);
    }

    public Course createCourseWithMessagingEnabled() {
        Course course = CourseFactory.generateCourse(null, pastTimestamp, futureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor", true);
        return courseRepo.save(course);
    }

    public Course createCourseWithOrganizations(String name, String shortName, String url, String description, String logoUrl, String emailPattern) {
        Course course = createCourse();
        Set<Organization> organizations = new HashSet<>();
        Organization organization = organizationTestService.createOrganization(name, shortName, url, description, logoUrl, emailPattern);
        organizations.add(organization);
        course.setOrganizations(organizations);
        course.setEnrollmentEnabled(true);
        return courseRepo.save(course);
    }

    public Course createCourseWithOrganizations() {
        return createCourseWithOrganizations("organization1", "org1", "org.org", "This is organization1", null, "^.*@matching.*$");
    }

    public List<Course> createCoursesWithExercisesAndLecturesAndLectureUnitsAndCompetencies(String userPrefix, boolean withParticipations, boolean withFiles,
            int numberOfTutorParticipations) throws Exception {
        List<Course> courses = lectureUtilService.createCoursesWithExercisesAndLecturesAndLectureUnits(userPrefix, withParticipations, withFiles, numberOfTutorParticipations);
        return courses.stream().peek(course -> {
            List<Lecture> lectures = new ArrayList<>(course.getLectures());
            lectures.replaceAll(lecture -> lectureUtilService.addCompetencyToLectureUnits(lecture, Set.of(competencyUtilService.createCompetency(course))));
            course.setLectures(new HashSet<>(lectures));
        }).toList();
    }

    public List<Course> createCoursesWithExercisesAndLectures(String prefix, boolean withParticipations, int numberOfTutorParticipations) throws Exception {
        return createCoursesWithExercisesAndLectures(prefix, withParticipations, false, numberOfTutorParticipations);
    }

    public List<Course> createCoursesWithExercisesAndLectures(String prefix, boolean withParticipations, boolean withFiles, int numberOfTutorParticipations) throws Exception {
        ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(5);
        ZonedDateTime futureTimestamp = ZonedDateTime.now().plusDays(5);
        ZonedDateTime futureFutureTimestamp = ZonedDateTime.now().plusDays(8);

        Course course1 = CourseFactory.generateCourse(null, pastTimestamp, futureTimestamp, new HashSet<>(), prefix + "tumuser", prefix + "tutor", prefix + "editor",
                prefix + "instructor");
        Course course2 = CourseFactory.generateCourse(null, ZonedDateTime.now().minusDays(8), pastTimestamp, new HashSet<>(), prefix + "tumuser", prefix + "tutor",
                prefix + "editor", prefix + "instructor");

        ModelingExercise modelingExercise = ModelingExerciseFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.ClassDiagram,
                course1);
        modelingExercise.setGradingInstructions("some grading instructions");
        modelingExercise.setExampleSolutionModel("Example solution model");
        modelingExercise.setExampleSolutionExplanation("Example Solution");
        exerciseUtilService.addGradingInstructionsToExercise(modelingExercise);
        modelingExercise.getCategories().add("Modeling");
        course1.addExercises(modelingExercise);

        TextExercise textExercise = TextExerciseFactory.generateTextExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, course1);
        textExercise.setGradingInstructions("some grading instructions");
        textExercise.setExampleSolution("Example Solution");
        exerciseUtilService.addGradingInstructionsToExercise(textExercise);
        textExercise.getCategories().add("Text");
        course1.addExercises(textExercise);

        FileUploadExercise fileUploadExercise = FileUploadExerciseFactory.generateFileUploadExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, "png", course1);
        fileUploadExercise.setGradingInstructions("some grading instructions");
        fileUploadExercise.setExampleSolution("Example Solution");
        exerciseUtilService.addGradingInstructionsToExercise(fileUploadExercise);
        fileUploadExercise.getCategories().add("File");
        course1.addExercises(fileUploadExercise);

        ProgrammingExercise programmingExercise = ProgrammingExerciseFactory.generateProgrammingExercise(pastTimestamp, futureTimestamp, course1);
        programmingExercise.setGradingInstructions("some grading instructions");
        exerciseUtilService.addGradingInstructionsToExercise(programmingExercise);
        programmingExercise.getCategories().add("Programming");
        course1.addExercises(programmingExercise);

        QuizExercise quizExercise = QuizExerciseFactory.generateQuizExercise(pastTimestamp, futureTimestamp, QuizMode.SYNCHRONIZED, course1);
        programmingExercise.getCategories().add("Quiz");
        course1.addExercises(quizExercise);

        Lecture lecture1 = LectureFactory.generateLecture(pastTimestamp, futureFutureTimestamp, course1);
        Attachment attachment1 = withFiles ? LectureFactory.generateAttachmentWithFile(pastTimestamp) : LectureFactory.generateAttachment(pastTimestamp);
        attachment1.setLecture(lecture1);
        lecture1.addAttachments(attachment1);
        course1.addLectures(lecture1);

        Lecture lecture2 = LectureFactory.generateLecture(pastTimestamp, futureFutureTimestamp, course1);
        Attachment attachment2 = withFiles ? LectureFactory.generateAttachmentWithFile(pastTimestamp) : LectureFactory.generateAttachment(pastTimestamp);
        attachment2.setLecture(lecture2);
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
            Set<TutorParticipation> tutorParticipations = new HashSet<>();
            for (int i = 1; i < numberOfTutorParticipations + 1; i++) {
                var tutorParticipation = new TutorParticipation().tutor(userUtilService.getUserByLogin(prefix + "tutor" + i)).status(TutorParticipationStatus.NOT_PARTICIPATED)
                        .assessedExercise(modelingExercise);
                tutorParticipationRepo.save(tutorParticipation);
                tutorParticipations.add(tutorParticipation);
            }

            for (int i = 1; i < numberOfTutorParticipations + 1; i++) {
                String validModel = FileUtils.loadFileFromResources("test-data/model-submission/model.54727.json");
                var exampleSubmission = participationUtilService.addExampleSubmission(participationUtilService.generateExampleSubmission(validModel, modelingExercise, true));
                exampleSubmission.assessmentExplanation("exp");
                exampleSubmission.setTutorParticipations(tutorParticipations);
                exampleSubmissionRepo.save(exampleSubmission);
            }

            User user = userUtilService.getUserByLogin(prefix + "student1");
            StudentParticipation participation1 = ParticipationFactory.generateStudentParticipation(InitializationState.INITIALIZED, modelingExercise, user);
            StudentParticipation participation2 = ParticipationFactory.generateStudentParticipation(InitializationState.FINISHED, textExercise, user);
            StudentParticipation participation3 = ParticipationFactory.generateStudentParticipation(InitializationState.UNINITIALIZED, modelingExercise, user);
            StudentParticipation participation4 = ParticipationFactory.generateProgrammingExerciseStudentParticipation(InitializationState.FINISHED, programmingExercise, user);
            StudentParticipation participation5 = ParticipationFactory.generateProgrammingExerciseStudentParticipation(InitializationState.INITIALIZED, programmingExercise, user);
            participation5.setTestRun(true);

            Submission modelingSubmission1 = ParticipationFactory.generateModelingSubmission("model1", true);
            Submission modelingSubmission2 = ParticipationFactory.generateModelingSubmission("model2", true);
            Submission textSubmission = ParticipationFactory.generateTextSubmission("text", Language.ENGLISH, true);
            Submission programmingSubmission1 = ParticipationFactory.generateProgrammingSubmission(true, "1234", SubmissionType.MANUAL);
            Submission programmingSubmission2 = ParticipationFactory.generateProgrammingSubmission(true, "5678", SubmissionType.MANUAL);

            Result result1 = generateResult(true, 10D);
            Result result2 = generateResult(true, 12D);
            Result result3 = generateResult(false, 0D);
            Result result4 = generateResult(true, 12D);
            Result result5 = generateResult(false, 42D);

            participation1 = studentParticipationRepo.save(participation1);
            participation2 = studentParticipationRepo.save(participation2);
            participation3 = studentParticipationRepo.save(participation3);
            participation4 = studentParticipationRepo.save(participation4);
            participation5 = studentParticipationRepo.save(participation5);

            submissionRepository.save(modelingSubmission1);
            submissionRepository.save(modelingSubmission2);
            submissionRepository.save(textSubmission);
            submissionRepository.save(programmingSubmission1);
            submissionRepository.save(programmingSubmission2);

            modelingSubmission1.setParticipation(participation1);
            textSubmission.setParticipation(participation2);
            modelingSubmission2.setParticipation(participation3);
            programmingSubmission1.setParticipation(participation4);
            programmingSubmission2.setParticipation(participation5);

            result1.setParticipation(participation1);
            result2.setParticipation(participation3);
            result3.setParticipation(participation2);
            result4.setParticipation(participation4);
            result5.setParticipation(participation5);

            result1 = resultRepo.save(result1);
            result2 = resultRepo.save(result2);
            result3 = resultRepo.save(result3);
            result4 = resultRepo.save(result4);
            result5 = resultRepo.save(result5);

            result1.setSubmission(modelingSubmission1);
            result2.setSubmission(modelingSubmission2);
            result3.setSubmission(textSubmission);
            result4.setSubmission(programmingSubmission1);
            result5.setSubmission(programmingSubmission2);

            modelingSubmission1.addResult(result1);
            modelingSubmission2.addResult(result2);
            textSubmission.addResult(result3);
            programmingSubmission1.addResult(result4);
            programmingSubmission2.addResult(result5);

            submissionRepository.save(modelingSubmission1);
            submissionRepository.save(modelingSubmission2);
            submissionRepository.save(textSubmission);
            submissionRepository.save(programmingSubmission1);
            submissionRepository.save(programmingSubmission2);
        }

        return Arrays.asList(course1, course2);
    }

    public List<Course> createMultipleCoursesWithAllExercisesAndLectures(String userPrefix, int numberOfCoursesWithExercisesAndLectures, int numberOfTutorParticipations)
            throws Exception {
        List<Course> courses = new ArrayList<>();
        for (int i = 0; i < numberOfCoursesWithExercisesAndLectures; i++) {
            var coursesWithLectures = lectureUtilService.createCoursesWithExercisesAndLecturesAndLectureUnits(userPrefix, true, true, numberOfTutorParticipations);
            courses.addAll(coursesWithLectures);
        }
        return courses;
    }

    public Course createCourseWithAllExerciseTypesAndParticipationsAndSubmissionsAndResults(String userPrefix, boolean hasAssessmentDueDatePassed) {
        var assessmentTimestamp = hasAssessmentDueDatePassed ? ZonedDateTime.now().minusMinutes(10L) : ZonedDateTime.now().plusMinutes(10L);
        Course course = CourseFactory.generateCourse(null, pastTimestamp, futureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");

        ModelingExercise modelingExercise = ModelingExerciseFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.ClassDiagram,
                course);
        TextExercise textExercise = TextExerciseFactory.generateTextExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, course);
        FileUploadExercise fileUploadExercise = FileUploadExerciseFactory.generateFileUploadExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, "png", course);
        ProgrammingExercise programmingExercise = ProgrammingExerciseFactory.generateProgrammingExercise(pastTimestamp, futureTimestamp, course);
        QuizExercise quizExercise = QuizExerciseFactory.generateQuizExercise(pastTimestamp, assessmentTimestamp, QuizMode.SYNCHRONIZED, course);

        // Set assessment due dates
        modelingExercise.setAssessmentDueDate(assessmentTimestamp);
        textExercise.setAssessmentDueDate(assessmentTimestamp);
        fileUploadExercise.setAssessmentDueDate(assessmentTimestamp);
        programmingExercise.setAssessmentDueDate(assessmentTimestamp);

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
        User user = (userRepo.findOneByLogin(userPrefix + "student1")).get();
        StudentParticipation participationModeling = ParticipationFactory.generateStudentParticipation(InitializationState.FINISHED, modelingExercise, user);
        StudentParticipation participationText = ParticipationFactory.generateStudentParticipation(InitializationState.FINISHED, textExercise, user);
        StudentParticipation participationFileUpload = ParticipationFactory.generateStudentParticipation(InitializationState.FINISHED, fileUploadExercise, user);
        StudentParticipation participationQuiz = ParticipationFactory.generateStudentParticipation(InitializationState.FINISHED, quizExercise, user);
        StudentParticipation participationProgramming = ParticipationFactory.generateStudentParticipation(InitializationState.INITIALIZED, programmingExercise, user);

        // Save participations
        participationModeling = studentParticipationRepo.save(participationModeling);
        participationText = studentParticipationRepo.save(participationText);
        participationFileUpload = studentParticipationRepo.save(participationFileUpload);
        participationQuiz = studentParticipationRepo.save(participationQuiz);
        participationProgramming = studentParticipationRepo.save(participationProgramming);

        // Setup results
        Result resultModeling = generateResult(true, 10D);
        resultModeling.setAssessmentType(AssessmentType.MANUAL);
        resultModeling.setCompletionDate(ZonedDateTime.now());

        Result resultText = generateResult(true, 12D);
        resultText.setAssessmentType(AssessmentType.MANUAL);
        resultText.setCompletionDate(ZonedDateTime.now());

        Result resultFileUpload = generateResult(true, 0D);
        resultFileUpload.setAssessmentType(AssessmentType.MANUAL);
        resultFileUpload.setCompletionDate(ZonedDateTime.now());

        Result resultQuiz = generateResult(true, 0D);
        resultQuiz.setAssessmentType(AssessmentType.AUTOMATIC);
        resultQuiz.setCompletionDate(ZonedDateTime.now());

        Result resultProgramming = generateResult(true, 20D);
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
        ModelingSubmission modelingSubmission = ParticipationFactory.generateModelingSubmission("model1", true);
        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("text of text submission", Language.ENGLISH, true);
        FileUploadSubmission fileUploadSubmission = ParticipationFactory.generateFileUploadSubmission(true);
        QuizSubmission quizSubmission = ParticipationFactory.generateQuizSubmission(true);
        ProgrammingSubmission programmingSubmission = ParticipationFactory.generateProgrammingSubmission(true);

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

    public OnlineCourseConfiguration addOnlineCourseConfigurationToCourse(Course course) {
        OnlineCourseConfiguration onlineCourseConfiguration = new OnlineCourseConfiguration();
        onlineCourseConfiguration.setLtiKey("artemis_lti_key");
        onlineCourseConfiguration.setLtiSecret("fake-secret");
        onlineCourseConfiguration.setUserPrefix("prefix");
        onlineCourseConfiguration.setRegistrationId(course.getId().toString());
        onlineCourseConfiguration.setCourse(course);
        course.setOnlineCourseConfiguration(onlineCourseConfiguration);
        courseRepo.save(course);
        return onlineCourseConfiguration;
    }

    public Course addEmptyCourse(String studentGroupName, String taGroupName, String editorGroupName, String instructorGroupName) {
        Course course = CourseFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), studentGroupName, taGroupName, editorGroupName,
                instructorGroupName);
        courseRepo.save(course);
        assertThat(courseRepo.findById(course.getId())).as("empty course is initialized").isPresent();
        return course;
    }

    /**
     * @return An empty course
     */
    public Course addEmptyCourse() {
        return addEmptyCourse("tumuser", "tutor", "editor", "instructor");
    }

    /**
     * @param title The title reflect the genre of exercise that will be added to the course
     */
    public Course addCourseInOtherInstructionGroupAndExercise(String title) {
        Course course = CourseFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "other-instructors");
        if ("Programming".equals(title)) {
            course = courseRepo.save(course);

            var programmingExercise = (ProgrammingExercise) new ProgrammingExercise().course(course);
            ProgrammingExerciseFactory.populateProgrammingExercise(programmingExercise, "TSTEXC", "Programming", false);
            programmingExercise.setPresentationScoreEnabled(course.getPresentationScore() != 0);

            programmingExercise = programmingExerciseRepository.save(programmingExercise);
            course.addExercises(programmingExercise);
            programmingExercise = programmingExerciseUtilService.addSolutionParticipationForProgrammingExercise(programmingExercise);
            programmingExercise = programmingExerciseUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);

            assertThat(programmingExercise.getPresentationScoreEnabled()).as("presentation score is enabled").isTrue();
        }
        else if ("Text".equals(title)) {
            TextExercise textExercise = TextExerciseFactory.generateTextExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, course);
            textExercise.setTitle("Text");
            course.addExercises(textExercise);
            courseRepo.save(course);
            exerciseRepo.save(textExercise);
        }
        else if (title.startsWith("ClassDiagram")) {
            ModelingExercise modelingExercise = ModelingExerciseFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.ClassDiagram,
                    course);
            modelingExercise.setTitle(title);
            course.addExercises(modelingExercise);
            courseRepo.save(course);
            exerciseRepo.save(modelingExercise);
        }

        return course;
    }

    public Course addCourseWithModelingAndTextExercise() {
        Course course = CourseFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        ModelingExercise modelingExercise = ModelingExerciseFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.ClassDiagram,
                course);
        modelingExercise.setTitle("Modeling");
        course.addExercises(modelingExercise);
        TextExercise textExercise = TextExerciseFactory.generateTextExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, course);
        textExercise.setTitle("Text");
        course.addExercises(textExercise);
        course = courseRepo.save(course);
        exerciseRepo.save(modelingExercise);
        exerciseRepo.save(textExercise);
        return course;
    }

    public Course addCourseWithModelingAndTextAndFileUploadExercise() {
        Course course = CourseFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");

        ModelingExercise modelingExercise = ModelingExerciseFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.ClassDiagram,
                course);
        modelingExercise.setTitle("Modeling");
        course.addExercises(modelingExercise);

        TextExercise textExercise = TextExerciseFactory.generateTextExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, course);
        textExercise.setTitle("Text");
        course.addExercises(textExercise);

        FileUploadExercise fileUploadExercise = FileUploadExerciseFactory.generateFileUploadExercise(pastTimestamp, pastTimestamp, futureFutureTimestamp, "png,pdf", course);
        fileUploadExercise.setTitle("FileUpload");
        course.addExercises(fileUploadExercise);

        course = courseRepo.save(course);
        exerciseRepo.save(modelingExercise);
        exerciseRepo.save(textExercise);
        exerciseRepo.save(fileUploadExercise);
        return course;
    }

    /**
     * With this method we can generate a course. We can specify the number of exercises. To not only test one type, this method generates modeling, file-upload and text
     * exercises in a cyclic manner.
     *
     * @param numberOfExercises             number of generated exercises. E.g. if you set it to 4, 2 modeling exercises, one text and one file-upload exercise will be generated.
     *                                          (thats why there is the %3 check)
     * @param numberOfSubmissionPerExercise for each exercise this number of submissions will be generated. E.g. if you have 2 exercises, and set this to 4, in total 8
     *                                          submissions will be created.
     * @param numberOfAssessments           generates the assessments for a submission of an exercise. Example from abobe, 2 exrecises, 4 submissions each. If you set
     *                                          numberOfAssessments to 2, for each exercise 2 assessmetns will be created. In total there will be 4 assessments then. (by two
     *                                          different tutors, as each exercise is assessed by an individual tutor. There are 4 tutors that create assessments)
     * @param numberOfComplaints            generates the complaints for assessments, in the same way as results are created.
     * @param typeComplaint                 true: complaintType==COMPLAINT | false: complaintType==MORE_FEEDBACK
     * @param numberComplaintResponses      generates responses for the complaint/feedback request (as above)
     * @param validModel                    model for the modeling submission
     * @return - the generated course
     */
    public Course addCourseWithExercisesAndSubmissions(String userPrefix, String suffix, int numberOfExercises, int numberOfSubmissionPerExercise, int numberOfAssessments,
            int numberOfComplaints, boolean typeComplaint, int numberComplaintResponses, String validModel) {
        Course course = CourseFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), userPrefix + "student" + suffix, userPrefix + "tutor" + suffix,
                userPrefix + "editor" + suffix, userPrefix + "instructor" + suffix);
        var tutors = userRepo.getTutors(course).stream().sorted(Comparator.comparing(User::getId)).toList();
        for (int i = 0; i < numberOfExercises; i++) {
            var currentUser = tutors.get(i % 4);

            if ((i % 3) == 0) {
                ModelingExercise modelingExercise = ModelingExerciseFactory.generateModelingExercise(pastTimestamp, pastTimestamp.plusHours(1), futureTimestamp,
                        DiagramType.ClassDiagram, course);
                modelingExercise.setTitle("Modeling" + i);
                course.addExercises(modelingExercise);
                course = courseRepo.save(course);
                exerciseRepo.save(modelingExercise);
                for (int j = 1; j <= numberOfSubmissionPerExercise; j++) {
                    StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(modelingExercise, userPrefix + "student" + j);
                    ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
                    var user = userUtilService.getUserByLogin(userPrefix + "student" + j);
                    modelSubmissionService.handleModelingSubmission(submission, modelingExercise, user);
                    studentParticipationRepo.save(participation);
                    if (numberOfAssessments >= j) {
                        Result result = participationUtilService.generateResultWithScore(submission, currentUser, 3.0);
                        submission.addResult(result);
                        participation.addResult(result);
                        studentParticipationRepo.save(participation);
                        modelingSubmissionRepo.save(submission);
                        complaintUtilService.generateComplaintAndResponses(userPrefix, j, numberOfComplaints, numberComplaintResponses, typeComplaint, result, currentUser);
                    }
                }

            }
            else if ((i % 3) == 1) {
                TextExercise textExercise = TextExerciseFactory.generateTextExercise(pastTimestamp, pastTimestamp.plusHours(1), futureTimestamp, course);
                textExercise.setTitle("Text" + i);
                course.addExercises(textExercise);
                course = courseRepo.save(course);
                exerciseRepo.save(textExercise);
                for (int j = 1; j <= numberOfSubmissionPerExercise; j++) {
                    TextSubmission submission = ParticipationFactory.generateTextSubmission("submissionText", Language.ENGLISH, true);
                    submission = textExerciseUtilService.saveTextSubmission(textExercise, submission, userPrefix + "student" + j);
                    if (numberOfAssessments >= j) {
                        Result result = participationUtilService.generateResultWithScore(submission, currentUser, 3.0);
                        submission.addResult(result);
                        participationUtilService.saveResultInParticipation(submission, result);
                        textSubmissionRepo.save(submission);
                        complaintUtilService.generateComplaintAndResponses(userPrefix, j, numberOfComplaints, numberComplaintResponses, typeComplaint, result, currentUser);
                    }
                }
            }
            else { // i.e. (i % 3) == 2
                FileUploadExercise fileUploadExercise = FileUploadExerciseFactory.generateFileUploadExercise(pastTimestamp, pastTimestamp.plusHours(1), futureTimestamp, "png,pdf",
                        course);
                fileUploadExercise.setTitle("FileUpload" + i);
                course.addExercises(fileUploadExercise);
                course = courseRepo.save(course);
                exerciseRepo.save(fileUploadExercise);
                for (int j = 1; j <= numberOfSubmissionPerExercise; j++) {
                    FileUploadSubmission submission = ParticipationFactory.generateFileUploadSubmissionWithFile(true, "path/to/file.pdf");
                    fileUploadExerciseUtilService.saveFileUploadSubmission(fileUploadExercise, submission, userPrefix + "student" + j);
                    if (numberOfAssessments >= j) {
                        Result result = participationUtilService.generateResultWithScore(submission, currentUser, 3.0);
                        participationUtilService.saveResultInParticipation(submission, result);
                        fileUploadSubmissionRepo.save(submission);
                        complaintUtilService.generateComplaintAndResponses(userPrefix, j, numberOfComplaints, numberComplaintResponses, typeComplaint, result, currentUser);
                    }
                }
            }
        }
        course = courseRepo.save(course);
        return course;
    }

    /**
     * Creates a new course that gets saved in the Course repository.
     *
     * @param id        the id of the course
     * @param startDate start date of the course
     * @param endDate   end date of the course
     * @param exercises exercises of the course
     * @return course that was created
     */
    public Course createAndSaveCourse(Long id, ZonedDateTime startDate, ZonedDateTime endDate, Set<Exercise> exercises) {
        Course course = CourseFactory.generateCourse(id, startDate, endDate, exercises, "tumuser", "tutor", "editor", "instructor");
        courseRepo.save(course);

        return course;
    }

    public Course createCourseWithTestModelingAndFileUploadExercisesAndSubmissions(String loginPrefix) throws Exception {
        Course course = addCourseWithModelingAndTextAndFileUploadExercise();
        course.setEndDate(ZonedDateTime.now().minusMinutes(5));
        course = courseRepo.save(course);

        var fileUploadExercise = exerciseUtilService.findFileUploadExerciseWithTitle(course.getExercises(), "FileUpload");
        fileUploadExerciseUtilService.createFileUploadSubmissionWithFile(loginPrefix, fileUploadExercise, "uploaded-file.png");

        var textExercise = exerciseUtilService.findTextExerciseWithTitle(course.getExercises(), "Text");
        var textSubmission = ParticipationFactory.generateTextSubmission("example text", Language.ENGLISH, true);
        textExerciseUtilService.saveTextSubmission(textExercise, textSubmission, loginPrefix + "student1");

        var modelingExercise = exerciseUtilService.findModelingExerciseWithTitle(course.getExercises(), "Modeling");
        participationUtilService.createAndSaveParticipationForExercise(modelingExercise, loginPrefix + "student1");
        String emptyActivityModel = FileUtils.loadFileFromResources("test-data/model-submission/empty-activity-diagram.json");
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(emptyActivityModel, true);
        participationUtilService.addSubmission(modelingExercise, submission, loginPrefix + "student1");

        return course;
    }

    public Course createCourseWithExamAndExercises(String loginPrefix) throws IOException {
        var course = addEmptyCourse();

        // Create a file upload exercise with a dummy submission file
        var exerciseGroup1 = exerciseGroupRepository.save(new ExerciseGroup());
        var fileUploadExercise = FileUploadExerciseFactory.generateFileUploadExerciseForExam(".png", exerciseGroup1);
        fileUploadExercise = exerciseRepo.save(fileUploadExercise);
        fileUploadExerciseUtilService.createFileUploadSubmissionWithFile(loginPrefix, fileUploadExercise, "uploaded-file.png");
        exerciseGroup1.addExercise(fileUploadExercise);
        exerciseGroup1 = exerciseGroupRepository.save(exerciseGroup1);

        // Create a text exercise with a dummy submission file
        var exerciseGroup2 = exerciseGroupRepository.save(new ExerciseGroup());
        var textExercise = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup2);
        textExercise = exerciseRepo.save(textExercise);
        var textSubmission = ParticipationFactory.generateTextSubmission("example text", Language.ENGLISH, true);
        textExerciseUtilService.saveTextSubmission(textExercise, textSubmission, loginPrefix + "student1");
        exerciseGroup2.addExercise(textExercise);
        exerciseGroup2 = exerciseGroupRepository.save(exerciseGroup2);

        // Create a modeling exercise with a dummy submission file
        var exerciseGroup3 = exerciseGroupRepository.save(new ExerciseGroup());
        var modelingExercise = ModelingExerciseFactory.generateModelingExerciseForExam(DiagramType.ClassDiagram, exerciseGroup2);
        modelingExercise = exerciseRepo.save(modelingExercise);
        String emptyActivityModel = FileUtils.loadFileFromResources("test-data/model-submission/empty-activity-diagram.json");
        var modelingSubmission = ParticipationFactory.generateModelingSubmission(emptyActivityModel, true);
        participationUtilService.addSubmission(modelingExercise, modelingSubmission, loginPrefix + "student1");
        exerciseGroup3.addExercise(modelingExercise);
        exerciseGroupRepository.save(exerciseGroup3);

        Exam exam = examUtilService.addExam(course);
        exam.setEndDate(ZonedDateTime.now().minusMinutes(5));
        exam.addExerciseGroup(exerciseGroup1);
        exam.addExerciseGroup(exerciseGroup2);
        examRepository.save(exam);

        return course;
    }

    public Course saveCourse(Course course) {
        return courseRepo.save(course);
    }

    public void enableMessagingForCourse(Course course) {
        CourseInformationSharingConfiguration currentConfig = course.getCourseInformationSharingConfiguration();
        if (currentConfig == CourseInformationSharingConfiguration.DISABLED) {
            course.setCourseInformationSharingConfiguration(CourseInformationSharingConfiguration.MESSAGING_ONLY);
            courseRepo.save(course);
        }
        else if (currentConfig == CourseInformationSharingConfiguration.COMMUNICATION_ONLY) {
            course.setCourseInformationSharingConfiguration(CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING);
            courseRepo.save(course);
        }
    }

    public Course createCourseWithTextExerciseAndTutor(String login) {
        Course course = this.createCourse();
        TextExercise textExercise = textExerciseUtilService.createIndividualTextExercise(course, pastTimestamp, pastTimestamp, pastTimestamp);
        StudentParticipation participation = ParticipationFactory.generateStudentParticipationWithoutUser(InitializationState.INITIALIZED, textExercise);
        studentParticipationRepo.save(participation);
        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("some text", Language.ENGLISH, true);
        textSubmission.setParticipation(participation);
        textSubmissionRepo.saveAndFlush(textSubmission);
        course.addExercises(textExercise);
        User user = userUtilService.createAndSaveUser(login);
        user.setGroups(Set.of(course.getTeachingAssistantGroupName()));
        userRepo.save(user);
        return course;
    }

    public Course createCourseWithInstructorAndTextExercise(String userPrefix) {
        Course course = this.createCourse();
        TextExercise textExercise = textExerciseUtilService.createIndividualTextExercise(course, pastTimestamp, pastTimestamp, pastTimestamp);
        StudentParticipation participation = ParticipationFactory.generateStudentParticipationWithoutUser(InitializationState.INITIALIZED, textExercise);
        studentParticipationRepo.save(participation);
        course.addExercises(textExercise);
        userUtilService.addUsers(userPrefix, 0, 0, 0, 1);
        return course;
    }

    /**
     * Update the max complaint text limit of the course.
     *
     * @param course             course which is updated
     * @param complaintTextLimit new complaint text limit
     * @return updated course
     */
    public Course updateCourseComplaintTextLimit(Course course, int complaintTextLimit) {
        course.setMaxComplaintTextLimit(complaintTextLimit);
        assertThat(course.getMaxComplaintTextLimit()).as("course contains the correct complaint text limit").isEqualTo(complaintTextLimit);
        return courseRepo.save(course);
    }

    /**
     * Update the max complaint response text limit of the course.
     *
     * @param course                     course which is updated
     * @param complaintResponseTextLimit new complaint response text limit
     * @return updated course
     */
    public Course updateCourseComplaintResponseTextLimit(Course course, int complaintResponseTextLimit) {
        course.setMaxComplaintResponseTextLimit(complaintResponseTextLimit);
        assertThat(course.getMaxComplaintResponseTextLimit()).as("course contains the correct complaint response text limit").isEqualTo(complaintResponseTextLimit);
        return courseRepo.save(course);
    }

    public void updateCourseGroups(String userPrefix, List<Course> courses, String suffix) {
        courses.forEach(course -> updateCourseGroups(userPrefix, course, suffix));
    }

    public void updateCourseGroups(String userPrefix, Course course, String suffix) {
        course.setStudentGroupName(userPrefix + "student" + suffix);
        course.setTeachingAssistantGroupName(userPrefix + "tutor" + suffix);
        course.setEditorGroupName(userPrefix + "editor" + suffix);
        course.setInstructorGroupName(userPrefix + "instructor" + suffix);
        courseRepo.save(course);
    }
}
