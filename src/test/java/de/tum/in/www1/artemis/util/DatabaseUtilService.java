package de.tum.in.www1.artemis.util;

import java.io.File;
import java.io.*;
import java.nio.file.*;
import java.time.ZonedDateTime;
import java.util.*;
import de.tum.in.www1.artemis.service.ModelingAssessmentService;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ResourceUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.*;
import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.ModelingSubmissionService;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Service responsible for initializing the database with specific testdata for a testscenario
 */
@Service
public class DatabaseUtilService {
    private static ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(1);
    private static ZonedDateTime futureTimestamp = ZonedDateTime.now().plusDays(1);
    private static ZonedDateTime futureFututreTimestamp = ZonedDateTime.now().plusDays(2);
    @Autowired
    CourseRepository courseRepo;
    @Autowired
    ExerciseRepository exerciseRepo;
    @Autowired
    UserRepository userRepo;
    @Autowired
    ResultRepository resultRepo;
    @Autowired
    ParticipationRepository participationRepo;
    @Autowired
    ModelingSubmissionRepository modelingSubmissionRepo;
    @Autowired
    FeedbackRepository feedbackRepo;
    @Autowired
    ModelingSubmissionService modelSubmissionService;
    @Autowired
    ModelingAssessmentService modelingAssessmentService;
    @Autowired
    ObjectMapper mapper;


    public void resetDatabase() {
        feedbackRepo.deleteAll();
        resultRepo.deleteAll();
        modelingSubmissionRepo.deleteAll();
        participationRepo.deleteAll();
        exerciseRepo.deleteAll();
        courseRepo.deleteAll();
        userRepo.deleteAll();
        assertThat(courseRepo.findAll()).as("course data has been cleared").isEmpty();
        assertThat(exerciseRepo.findAll()).as("exercise data has been cleared").isEmpty();
        assertThat(userRepo.findAll()).as("user data has been cleared").isEmpty();
    }


    public void resetFileStorage() throws IOException {
        Path path = Paths.get(Constants.FILEPATH_COMPASS + File.separator);
        FileUtils.cleanDirectory(new File(path.toUri()));
    }


    /**
     * Adds the provided number of students and tutors into the user repository. Students login is a
     * concatenation of the prefix "student" and a number counting from 1 to numberOfStudents Tutors
     * login is a concatenation of the prefix "tutor" and a number counting from 1 to numberOfStudents
     * Tutors are all in the "tutor" group and students in the "tumuser" group
     *
     * @param numberOfStudents
     * @param numberOfTutors
     */
    public void addUsers(int numberOfStudents, int numberOfTutors) {
        LinkedList<User> students =
            ModelFactory.generateActivatedUsers("student", new String[]{"tumuser"}, numberOfStudents);
        LinkedList<User> tutors =
            ModelFactory.generateActivatedUsers("tutor", new String[]{"tutor"}, numberOfTutors);
        LinkedList<User> usersToAdd = new LinkedList<>();
        usersToAdd.addAll(students);
        usersToAdd.addAll(tutors);
        userRepo.saveAll(usersToAdd);
        assertThat(userRepo.findAll().size())
            .as("all users are created")
            .isEqualTo(numberOfStudents + numberOfTutors);
        assertThat(userRepo.findAll())
            .as("users are correctly stored")
            .containsAnyOf(usersToAdd.toArray(new User[0]));
    }


    /**
     * Stores participation of the user with the given login for the given exercise
     *
     * @param exercise
     * @param login    login of the user
     * @return eagerly loaded representation of the participation object stored in the database
     */
    public Participation addParticipationForExercise(Exercise exercise, String login) {
        Optional<Participation> storedParticipation = participationRepo.findOneByExerciseIdAndStudentLogin(exercise.getId(), login);
        if (storedParticipation.isPresent()) {
            return storedParticipation.get();
        }
        User user =
            userRepo
                .findOneByLogin(login)
                .orElseThrow(
                    () -> new IllegalArgumentException("Provided login does not exist in database"));
        Participation participation = new Participation();
        participation.setStudent(user);
        participation.setExercise(exercise);
        participationRepo.save(participation);
        storedParticipation = participationRepo.findOneByExerciseIdAndStudentLogin(exercise.getId(), login);
        assertThat(storedParticipation).isPresent();
        return participationRepo
            .findByIdWithEagerSubmissionsAndEagerResultsAndEagerAssessors(storedParticipation.get().getId())
            .get();
    }


    public void addCourseWithModelingExercise() {
        Course course =
            ModelFactory.generateCourse(
                null,
                pastTimestamp,
                futureFututreTimestamp,
                new HashSet<>(),
                "tumuser",
                "tutor",
                "tutor");
        ModelingExercise exercise =
            ModelFactory.generateModelingExercise(
                pastTimestamp, futureTimestamp, futureFututreTimestamp, course);
        course.addExercises(exercise);
        courseRepo.save(course);
        exerciseRepo.save(exercise);
        List<Course> courseRepoContent = courseRepo.findAllActiveWithEagerExercises();
        List<Exercise> exerciseRepoContent = exerciseRepo.findAll();
        assertThat(exerciseRepoContent.size()).as("a exercise got stored").isEqualTo(1);
        assertThat(courseRepoContent.size()).as("a course got stored").isEqualTo(1);
        assertThat(courseRepoContent.get(0).getExercises().size())
            .as("Course contains exercise")
            .isEqualTo(1);
        assertThat(courseRepoContent.get(0).getExercises().contains(exerciseRepoContent.get(0)))
            .as("course contains the right exercise")
            .isTrue();
    }


    /**
     * Stores for the given model a submission of the user and initiates the corresponding Result
     *
     * @param exercise exercise the submission belongs to
     * @param model    ModelingSubmission json as string contained in the submission
     * @param login    of the user the submission belongs to
     * @return submission stored in the modelingSubmissionRepository
     */
    public ModelingSubmission addModelingSubmission(
        ModelingExercise exercise, String model, String login) {
        Participation participation = addParticipationForExercise(exercise, login);
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(model, true);
        submission = modelSubmissionService.save(submission, exercise, participation);
        Result result = new Result();
        result.setSubmission(submission);
        submission.setResult(result);
        submission.getParticipation().addResult(result);
        resultRepo.save(result);
        modelingSubmissionRepo.save(submission);
        return submission;
    }


    @Transactional
    public ModelingSubmission addModelingSubmission(
        ModelingExercise exercise, ModelingSubmission submission, String login) {
        Participation participation = addParticipationForExercise(exercise, login);
        participation.addSubmissions(submission);
        Result result = new Result();
        result.setSubmission(submission);
        submission.setParticipation(participation);
        submission.setResult(result);
        submission.getParticipation().addResult(result);
        modelingSubmissionRepo.save(submission);
        resultRepo.save(result);
        participationRepo.save(participation);
        return submission;
    }


    public ModelingSubmission addModelingSubmissionFromResources(ModelingExercise exercise, String path, String login) throws Exception {
        String model = loadFileFromResources(path);
        ModelingSubmission submission = addModelingSubmission(exercise, model, login);
        checkSubmissionCorrectlyStored(submission.getId(), model);
        return submission;
    }


    public void checkSubmissionCorrectlyStored(Long submissionId, String sentModel) throws Exception {
        String storedModel = modelingSubmissionRepo.findById(submissionId).get().getModel();
        JsonParser parser = new JsonParser();
        JsonObject sentModelObject = parser.parse(sentModel).getAsJsonObject();
        JsonObject storedModelObject = parser.parse(storedModel).getAsJsonObject();
        assertThat(storedModelObject).as("model correctly stored").isEqualTo(sentModelObject);
    }


    public Result addModelingAssessmentForSubmission(ModelingExercise exercise, ModelingSubmission submission,
                                                    String path, String login) throws Exception {
        List<Feedback> assessment = loadAssessmentFomResources(path);
        Result result = modelingAssessmentService.saveManualAssessment(submission, assessment);
        result = modelingAssessmentService.submitManualAssessment(result, exercise);
        return result;
    }


    /**
     * @param path path relative to the test resources folder
     * @return string representation of given file
     * @throws Exception
     */
    public String loadFileFromResources(String path) throws Exception {
        java.io.File file = ResourceUtils.getFile("classpath:" + path);
        StringBuilder builder = new StringBuilder();
        Files.lines(file.toPath()).forEach(builder::append);
        assertThat(builder.toString()).as("model has been correctly read from file").isNotEqualTo("");
        return builder.toString();
    }


    public List<Feedback> loadAssessmentFomResources(String path) throws Exception {
        String fileContent = loadFileFromResources(path);
        List<Feedback> modelingAssessment = mapper.readValue(
            fileContent,
            mapper.getTypeFactory().constructCollectionType(List.class, Feedback.class)
        );
        return modelingAssessment;
    }
}
