package de.tum.in.www1.artemis.exercise.fileuploadexercise;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.course.CourseFactory;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.exam.ExamUtilService;
import de.tum.in.www1.artemis.participation.ParticipationFactory;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.user.UserUtilService;

/**
 * Service responsible for initializing the database with specific testdata related to file upload exercises for use in integration tests.
 */
@Service
public class FileUploadExerciseUtilService {

    private static final ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(1);

    private static final ZonedDateTime futureTimestamp = ZonedDateTime.now().plusDays(1);

    private static final ZonedDateTime futureFutureTimestamp = ZonedDateTime.now().plusDays(2);

    @Autowired
    private ExerciseRepository exerciseRepo;

    @Autowired
    private CourseRepository courseRepo;

    @Autowired
    private FileUploadSubmissionRepository fileUploadSubmissionRepo;

    @Autowired
    private StudentParticipationRepository studentParticipationRepo;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private ResultRepository resultRepo;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private UserUtilService userUtilService;

    /**
     * Creates and saves a new Course and an Exam with one mandatory FileUploadExercise.
     *
     * @return The created FileUploadExercise
     */
    public FileUploadExercise addCourseExamExerciseGroupWithOneFileUploadExercise() {
        ExerciseGroup exerciseGroup = examUtilService.addExerciseGroupWithExamAndCourse(true);
        FileUploadExercise fileUploadExercise = FileUploadExerciseFactory.generateFileUploadExerciseForExam("pdf", exerciseGroup);
        return exerciseRepo.save(fileUploadExercise);
    }

    /**
     * Creates and saves a new Course with one released, one finished, and one assessed FileUploadExercise. Does not save the exercises.
     *
     * @return A List containing the created FileUploadExercises
     */
    public List<FileUploadExercise> createFileUploadExercisesWithCourse() {
        Course course = CourseFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        course = courseRepo.save(course);

        FileUploadExercise releasedFileUploadExercise = FileUploadExerciseFactory.generateFileUploadExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, "png,pdf",
                course);
        releasedFileUploadExercise.setTitle("released");
        FileUploadExercise finishedFileUploadExercise = FileUploadExerciseFactory.generateFileUploadExercise(pastTimestamp, pastTimestamp, futureFutureTimestamp, "png,pdf",
                course);
        finishedFileUploadExercise.setTitle("finished");
        FileUploadExercise assessedFileUploadExercise = FileUploadExerciseFactory.generateFileUploadExercise(pastTimestamp, pastTimestamp, pastTimestamp, "png,pdf", course);
        assessedFileUploadExercise.setTitle("assessed");

        var fileUploadExercises = new ArrayList<FileUploadExercise>();
        fileUploadExercises.add(releasedFileUploadExercise);
        fileUploadExercises.add(finishedFileUploadExercise);
        fileUploadExercises.add(assessedFileUploadExercise);
        return fileUploadExercises;
    }

    /**
     * Creates and saves a new Course with one released, one finished, and one assessed FileUploadExercise.
     *
     * @return The created Course
     */
    public Course addCourseWithThreeFileUploadExercise() {
        var fileUploadExercises = createFileUploadExercisesWithCourse();
        assertThat(fileUploadExercises).as("created three exercises").hasSize(3);
        exerciseRepo.saveAll(fileUploadExercises);
        long courseId = fileUploadExercises.get(0).getCourseViaExerciseGroupOrCourseMember().getId();
        Course course = courseRepo.findByIdWithEagerExercisesElseThrow(courseId);
        List<Exercise> exercises = exerciseRepo.findAllExercisesByCourseId(courseId).stream().toList();
        assertThat(exercises).as("three exercises got stored").hasSize(3);
        assertThat(course.getExercises()).as("course contains the exercises").containsExactlyInAnyOrder(exercises.toArray(new Exercise[] {}));
        return course;
    }

    /**
     * Creates and saves a new Course with one released, one finished, one assessed, and one no-due-date FileUploadExercise.
     *
     * @return The created Course
     */
    public Course addCourseWithFourFileUploadExercise() {
        Course course = CourseFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        course = courseRepo.save(course);

        FileUploadExercise releasedFileUploadExercise = FileUploadExerciseFactory.generateFileUploadExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, "png,pdf",
                course);
        releasedFileUploadExercise.setTitle("released");
        FileUploadExercise finishedFileUploadExercise = FileUploadExerciseFactory.generateFileUploadExercise(pastTimestamp, pastTimestamp, futureFutureTimestamp, "png,pdf",
                course);
        finishedFileUploadExercise.setTitle("finished");
        FileUploadExercise assessedFileUploadExercise = FileUploadExerciseFactory.generateFileUploadExercise(pastTimestamp, pastTimestamp, pastTimestamp, "png,pdf", course);
        assessedFileUploadExercise.setTitle("assessed");
        FileUploadExercise noDueDateFileUploadExercise = FileUploadExerciseFactory.generateFileUploadExercise(pastTimestamp, null, pastTimestamp, "png,pdf", course);
        noDueDateFileUploadExercise.setTitle("noDueDate");

        var fileUploadExercises = new ArrayList<FileUploadExercise>();
        fileUploadExercises.add(releasedFileUploadExercise);
        fileUploadExercises.add(finishedFileUploadExercise);
        fileUploadExercises.add(assessedFileUploadExercise);
        fileUploadExercises.add(noDueDateFileUploadExercise);
        exerciseRepo.saveAll(fileUploadExercises);

        return courseRepo.findByIdWithEagerExercisesElseThrow(course.getId());
    }

    /**
     * Creates and saves a new Course with one assessed FileUploadExercise.
     *
     * @return The created Course
     */
    public Course addCourseWithFileUploadExercise() {
        Course course = CourseFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        FileUploadExercise assessedFileUploadExercise = FileUploadExerciseFactory.generateFileUploadExercise(pastTimestamp, pastTimestamp, pastTimestamp, "png,pdf", course);
        assessedFileUploadExercise.setTitle("assessed");
        course.addExercises(assessedFileUploadExercise);
        courseRepo.save(course);
        exerciseRepo.save(assessedFileUploadExercise);
        return course;
    }

    /**
     * Creates and saves a StudentParticipation for the given FileUploadExercise, the FileUploadSubmission, and login.
     *
     * @param fileUploadExercise   The FileUploadExercise the StudentParticipation should belong to
     * @param fileUploadSubmission The FileUploadSubmission the StudentParticipation should belong to
     * @param login                The login of the user the StudentParticipation should belong to
     * @return The updated FileUploadSubmission
     */
    public FileUploadSubmission addFileUploadSubmission(FileUploadExercise fileUploadExercise, FileUploadSubmission fileUploadSubmission, String login) {
        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(fileUploadExercise, login);
        participation.addSubmission(fileUploadSubmission);
        fileUploadSubmission.setParticipation(participation);
        fileUploadSubmissionRepo.save(fileUploadSubmission);
        studentParticipationRepo.save(participation);
        return fileUploadSubmission;
    }

    /**
     * Creates and saves a StudentParticipation for the given FileUploadExercise, the FileUploadSubmission, and login. Also creates and saves a Result for the
     * StudentParticipation given the assessorLogin and List of Feedbacks.
     *
     * @param exercise             The FileUploadExercise the StudentParticipation should belong to
     * @param fileUploadSubmission The FileUploadSubmission the StudentParticipation should belong to
     * @param login                The login of the user the StudentParticipation should belong to
     * @param assessorLogin        The login of the assessor the Result should belong to
     * @param feedbacks            The List of Feedbacks the Result should contain
     * @return The updated FileUploadSubmission
     */
    public FileUploadSubmission saveFileUploadSubmissionWithResultAndAssessorFeedback(FileUploadExercise exercise, FileUploadSubmission fileUploadSubmission, String login,
            String assessorLogin, List<Feedback> feedbacks) {
        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(exercise, login);

        submissionRepository.save(fileUploadSubmission);

        participation.addSubmission(fileUploadSubmission);
        Result result = new Result();
        result.setAssessor(userUtilService.getUserByLogin(assessorLogin));
        result.setScore(100D);
        result.setParticipation(participation);
        if (exercise.getReleaseDate() != null) {
            result.setCompletionDate(exercise.getReleaseDate());
        }
        else { // exam exercises do not have a release date
            result.setCompletionDate(ZonedDateTime.now());
        }
        result.setFeedbacks(feedbacks);
        result = resultRepo.save(result);
        for (Feedback feedback : feedbacks) {
            feedback.setResult(result);
        }
        result = resultRepo.save(result);
        result.setSubmission(fileUploadSubmission);
        fileUploadSubmission.setParticipation(participation);
        fileUploadSubmission.addResult(result);
        fileUploadSubmission.getParticipation().addResult(result);
        fileUploadSubmission = fileUploadSubmissionRepo.save(fileUploadSubmission);
        studentParticipationRepo.save(participation);
        return fileUploadSubmission;
    }

    /**
     * Creates and saves a StudentParticipation for the given FileUploadExercise, the FileUploadSubmission, and login. Also creates and saves a Result for the
     * StudentParticipation given the assessorLogin.
     *
     * @param fileUploadExercise   The FileUploadExercise the StudentParticipation should belong to
     * @param fileUploadSubmission The FileUploadSubmission the StudentParticipation should belong to
     * @param login                The login of the user the StudentParticipation should belong to
     * @param assessorLogin        The login of the assessor the Result should belong to
     * @return The updated FileUploadSubmission
     */
    public FileUploadSubmission saveFileUploadSubmissionWithResultAndAssessor(FileUploadExercise fileUploadExercise, FileUploadSubmission fileUploadSubmission, String login,
            String assessorLogin) {
        return saveFileUploadSubmissionWithResultAndAssessorFeedback(fileUploadExercise, fileUploadSubmission, login, assessorLogin, new ArrayList<>());
    }

    /**
     * Creates and saves a StudentParticipation for the given FileUploadExercise, the FileUploadSubmission, and login.
     *
     * @param exercise   The FileUploadExercise the StudentParticipation should belong to
     * @param submission The FileUploadSubmission the StudentParticipation should belong to
     * @param login      The login of the user the StudentParticipation should belong to
     * @return The updated FileUploadSubmission
     */
    public FileUploadSubmission saveFileUploadSubmission(FileUploadExercise exercise, FileUploadSubmission submission, String login) {
        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(exercise, login);
        participation.addSubmission(submission);
        submission.setParticipation(participation);
        fileUploadSubmissionRepo.save(submission);
        return submission;
    }

    /**
     * Creates and saves a FileUploadSubmission for the given FileUploadExercise and loginPrefix. Also creates an empty file for the submission.
     *
     * @param loginPrefix        The loginPrefix of the user the FileUploadSubmission should belong to (the loginPrefix will be suffixed with "student1")
     * @param fileUploadExercise The FileUploadExercise the FileUploadSubmission should belong to
     * @param filename           The name of the file to create
     * @throws IOException If creating the file fails
     */
    public void createFileUploadSubmissionWithFile(String loginPrefix, FileUploadExercise fileUploadExercise, String filename) throws IOException {
        var fileUploadSubmission = ParticipationFactory.generateFileUploadSubmission(true);
        fileUploadSubmission = addFileUploadSubmission(fileUploadExercise, fileUploadSubmission, loginPrefix + "student1");

        // Create a dummy file
        var uploadedFileDir = Path.of("./").resolve(FileUploadSubmission.buildFilePath(fileUploadExercise.getId(), fileUploadSubmission.getId()));
        var uploadedFilePath = Path.of(uploadedFileDir.toString(), filename);
        if (!Files.exists(uploadedFilePath)) {
            Files.createDirectories(uploadedFileDir);
            Files.createFile(uploadedFilePath);
        }
        fileUploadSubmission.setFilePath(uploadedFilePath.toString());
        fileUploadSubmissionRepo.save(fileUploadSubmission);
    }
}
