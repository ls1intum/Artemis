package de.tum.in.www1.artemis.exercise.fileupload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.*;

import javax.validation.constraints.NotNull;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.util.ModelFactory;

@Service
public class FileUploadTestService {

    private static final ZonedDateTime PAST_TIMESTAMP = ZonedDateTime.now().minusDays(1);

    private static final ZonedDateTime FUTURE_TIMESTAMP = ZonedDateTime.now().plusDays(1);

    @Autowired
    private FileUploadExerciseRepository fileUploadExerciseRepository;

    @Autowired
    private CourseRepository courseRepo;

    @Autowired
    private ExamRepository examRepo;

    @Autowired
    private GradingCriterionRepository gradingCriterionRepository;

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private StudentParticipationRepository studentParticipationRepository;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private FileUploadSubmissionRepository fileUploadSubmissionRepository;

    @Autowired
    private ResultRepository resultRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    /**
     * creates and saves a file upload exercise in the repository
     *
     * @param releaseDate release date of the exercise
     * @param dueDate     due date of the exercise
     * @param filePattern acceptable file patterns
     * @return created file upload exercise
     */
    public FileUploadExercise createAndAndSaveFileUploadExercise(ZonedDateTime releaseDate, ZonedDateTime dueDate, String filePattern) {
        FileUploadExercise fileUploadExercise = generateFileUploadExercise(releaseDate, dueDate, filePattern);
        fileUploadExerciseRepository.save(fileUploadExercise);

        return fileUploadExercise;
    }

    /**
     * creates and a file upload exercise in the repository
     *
     * @param releaseDate release date of the exercise
     * @param dueDate     due date of the exercise
     * @param filePattern file pattern of the exercise
     * @return created file upload exercise
     */
    public FileUploadExercise generateFileUploadExercise(ZonedDateTime releaseDate, ZonedDateTime dueDate, String filePattern) {
        Course course = ModelFactory.generateCourse(null, null, null, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");

        FileUploadExercise fileUploadExercise = FileUploadTestFactory.generateFileUploadExercise(releaseDate, dueDate, dueDate.plusHours(1), filePattern, course);
        course.setExercises(Set.of(fileUploadExercise));
        courseRepo.save(course);

        return fileUploadExercise;
    }

    /**
     * creates an active (start date in the past, due date in the future) file upload exercise and saves it in the repository
     *
     * @param filePattern file pattern of the exercise
     * @return created file upload exercise
     */
    public FileUploadExercise createAndSaveActiveFileUploadExercise(String filePattern) {
        return createAndAndSaveFileUploadExercise(PAST_TIMESTAMP, FUTURE_TIMESTAMP, filePattern);
    }

    /**
     * creates an active (start date in the past, due date in the future) file upload exercise
     *
     * @param filePattern file pattern of the exercise
     * @return created file upload exercise
     */
    public FileUploadExercise generateActiveFileUploadExercise(String filePattern) {
        return generateFileUploadExercise(PAST_TIMESTAMP, FUTURE_TIMESTAMP, filePattern);
    }

    /**
     * creates an exam file upload exercise and saves it to the repository
     *
     * @param startDate   start date of the exam
     * @param endDate     end date of the exam
     * @param filePattern pattern of the file upload exercise
     * @return the exam created file upload exercise
     */
    public FileUploadExercise createAndSaveExamFileUploadExercise(ZonedDateTime startDate, ZonedDateTime endDate, String filePattern) {
        FileUploadExercise fileUploadExercise = generateExamFileUploadExercise(startDate, endDate, filePattern);
        fileUploadExerciseRepository.save(fileUploadExercise);

        return fileUploadExercise;
    }

    /**
     * creates an exam file upload exercise
     *
     * @param startDate   start date of the exam
     * @param endDate     end date of the exam
     * @param filePattern pattern of the file upload exercise
     * @return the created exam file upload exercise
     */
    public FileUploadExercise generateExamFileUploadExercise(ZonedDateTime startDate, ZonedDateTime endDate, String filePattern) {
        Course course = ModelFactory.generateCourse(null, null, null, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");

        Exam exam = ModelFactory.generateExam(course, startDate.minusMinutes(5), startDate, endDate, false);
        ExerciseGroup exerciseGroup = ModelFactory.generateExerciseGroup(true, exam);

        courseRepo.save(course);
        examRepo.save(exam);

        return FileUploadTestFactory.generateFileUploadExerciseForExam(filePattern, exerciseGroup);
    }

    /**
     * creates an active (start date in the past, due date in the future) exam file upload exercise and saves it in the repository
     *
     * @param filePattern file pattern of the exercise
     * @return created exam file upload exercise
     */
    public FileUploadExercise generateExamActiveFileUploadExercise(String filePattern) {
        return generateExamFileUploadExercise(PAST_TIMESTAMP, FUTURE_TIMESTAMP, filePattern);
    }

    /**
     * creates an active (start date in the past, due date in the future) file upload exercise
     *
     * @param filePattern file pattern of the exercise
     * @return created file upload exercise
     */
    public FileUploadExercise createAndSaveExamActiveFileUploadExercise(String filePattern) {
        return createAndSaveExamFileUploadExercise(PAST_TIMESTAMP, FUTURE_TIMESTAMP, filePattern);
    }

    /**
     * finds file upload exercise in the repository based on the passed id
     *
     * @param id id of the exercise
     * @return the exercise with the given id or null
     */
    public FileUploadExercise findFileUploadExercise(Long id) {
        return fileUploadExerciseRepository.findById(id).orElse(null);
    }

    /**
     * finds participations of an exercise based on its id
     *
     * @param id the id of the exercise
     * @return participations found in the studentParticipationRepository
     */
    public Set<StudentParticipation> findParticipationsOfExercise(Long id) {
        return studentParticipationRepository.findByExerciseId(id);
    }

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

    /**
     * sets the example publication date and saves the file upload exercise
     *
     * @param fileUploadExercise      exercise of which the date is to be changed
     * @param solutionPublicationDate new date
     */
    public void setExampleSolutionPublicationDateAndSave(FileUploadExercise fileUploadExercise, ZonedDateTime solutionPublicationDate) {
        fileUploadExercise.setExampleSolutionPublicationDate(solutionPublicationDate);
        fileUploadExerciseRepository.save(fileUploadExercise);
    }

    /**
     * creates feedback, sets its grading instructions and saves it to the repository
     *
     * @param gradingInstruction the grading instruction of the feedback
     */
    public void createAndSaveFeedback(GradingInstruction gradingInstruction) {
        Feedback feedback = new Feedback();
        feedback.setGradingInstruction(gradingInstruction);
        feedbackRepository.save(feedback);
    }

    /**
     * Stores participation of the user with the given login for the given exercise
     *
     * @param exercise the exercise for which the participation will be created
     * @param login    login of the user
     * @return eagerly loaded representation of the participation object stored in the database
     */
    public StudentParticipation createAndSaveParticipationForExercise(Exercise exercise, String login) {
        Optional<StudentParticipation> storedParticipation = studentParticipationRepository.findWithEagerLegalSubmissionsByExerciseIdAndStudentLoginAndTestRun(exercise.getId(),
                login, false);
        if (storedParticipation.isEmpty()) {
            User user = getUserByLogin(login);
            StudentParticipation participation = new StudentParticipation();
            participation.setInitializationDate(ZonedDateTime.now());
            participation.setParticipant(user);
            participation.setExercise(exercise);
            studentParticipationRepository.save(participation);
            storedParticipation = studentParticipationRepository.findWithEagerLegalSubmissionsByExerciseIdAndStudentLoginAndTestRun(exercise.getId(), login, false);
            assertThat(storedParticipation).isPresent();
        }
        return storedParticipation.get();
    }

    public FileUploadSubmission addFileUploadSubmissionAndParticipation(FileUploadExercise fileUploadExercise, FileUploadSubmission fileUploadSubmission, String login) {
        StudentParticipation participation = createAndSaveParticipationForExercise(fileUploadExercise, login);
        participation.addSubmission(fileUploadSubmission);
        fileUploadSubmission.setParticipation(participation);
        fileUploadSubmissionRepository.save(fileUploadSubmission);
        studentParticipationRepository.save(participation);
        return fileUploadSubmission;
    }

    public void createFileUploadSubmissionWithFile(String loginPrefix, FileUploadExercise fileUploadExercise, String filename) throws IOException {
        var fileUploadSubmission = FileUploadTestFactory.generateFileUploadSubmission(true);
        fileUploadSubmission = addFileUploadSubmissionAndParticipation(fileUploadExercise, fileUploadSubmission, loginPrefix + "student1");

        // Create a dummy file
        var uploadedFileDir = Path.of("./", FileUploadSubmission.buildFilePath(fileUploadExercise.getId(), fileUploadSubmission.getId()));
        var uploadedFilePath = Path.of(uploadedFileDir.toString(), filename);
        if (!Files.exists(uploadedFilePath)) {
            Files.createDirectories(uploadedFileDir);
            Files.createFile(uploadedFilePath);
        }
        fileUploadSubmission.setFilePath(uploadedFilePath.toString());
        fileUploadSubmissionRepository.save(fileUploadSubmission);
    }

    public Course addCourseWithFourFileUploadExercise() {
        Course course = ModelFactory.generateCourse(null, PAST_TIMESTAMP, FUTURE_TIMESTAMP.plusHours(1), new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        courseRepo.save(course);

        var fileUploadExercises = createFourFileUploadExercisesWithCourseWithCustomUserGroupAssignment(course);
        fileUploadExerciseRepository.saveAll(fileUploadExercises);
        course.setExercises(new HashSet<>(fileUploadExercises));

        return course;
    }

    public Set<FileUploadExercise> createFourFileUploadExercisesWithCourseWithCustomUserGroupAssignment(Course course) {
        FileUploadExercise releasedFileUploadExercise = FileUploadTestFactory.generateFileUploadExercise(PAST_TIMESTAMP, FUTURE_TIMESTAMP, FUTURE_TIMESTAMP.plusHours(1), "png,pdf",
                course);
        releasedFileUploadExercise.setTitle("released");
        FileUploadExercise finishedFileUploadExercise = FileUploadTestFactory.generateFileUploadExercise(PAST_TIMESTAMP, PAST_TIMESTAMP, FUTURE_TIMESTAMP.plusHours(1), "png,pdf",
                course);
        finishedFileUploadExercise.setTitle("finished");
        FileUploadExercise assessedFileUploadExercise = FileUploadTestFactory.generateFileUploadExercise(PAST_TIMESTAMP, PAST_TIMESTAMP, PAST_TIMESTAMP, "png,pdf", course);
        assessedFileUploadExercise.setTitle("assessed");
        FileUploadExercise noDueDateFileUploadExercise = FileUploadTestFactory.generateFileUploadExercise(PAST_TIMESTAMP, null, PAST_TIMESTAMP, "png,pdf", course);
        noDueDateFileUploadExercise.setTitle("noDueDate");

        var fileUploadExercises = new HashSet<FileUploadExercise>();
        fileUploadExercises.add(releasedFileUploadExercise);
        fileUploadExercises.add(finishedFileUploadExercise);
        fileUploadExercises.add(assessedFileUploadExercise);
        fileUploadExercises.add(noDueDateFileUploadExercise);
        return fileUploadExercises;
    }

    public FileUploadSubmission saveFileUploadSubmissionWithResultAndAssessorFeedback(FileUploadExercise exercise, FileUploadSubmission fileUploadSubmission, String login,
            String assessorLogin, List<Feedback> feedbacks) {
        StudentParticipation participation = createAndSaveParticipationForExercise(exercise, login);

        submissionRepository.save(fileUploadSubmission);

        participation.addSubmission(fileUploadSubmission);
        Result result = new Result();
        result.setAssessor(getUserByLogin(assessorLogin));
        result.setScore(100D);
        result.setParticipation(participation);
        if (exercise.getReleaseDate() != null) {
            result.setCompletionDate(exercise.getReleaseDate());
        }
        else { // exam exercises do not have a release date
            result.setCompletionDate(ZonedDateTime.now());
        }
        result.setFeedbacks(feedbacks);
        result = resultRepository.save(result);
        for (Feedback feedback : feedbacks) {
            feedback.setResult(result);
        }
        result = resultRepository.save(result);
        result.setSubmission(fileUploadSubmission);
        fileUploadSubmission.setParticipation(participation);
        fileUploadSubmission.addResult(result);
        fileUploadSubmission.getParticipation().addResult(result);
        fileUploadSubmission = fileUploadSubmissionRepository.save(fileUploadSubmission);
        studentParticipationRepository.save(participation);
        return fileUploadSubmission;
    }

    public FileUploadSubmission saveFileUploadSubmissionWithResultAndAssessor(FileUploadExercise fileUploadExercise, FileUploadSubmission fileUploadSubmission, String login,
            String assessorLogin) {
        return saveFileUploadSubmissionWithResultAndAssessorFeedback(fileUploadExercise, fileUploadSubmission, login, assessorLogin, new ArrayList<>());
    }

    // these two methods could be put into the ExerciseTestService

    /**
     * sets individual due dates of participations and saves them in the repository
     *
     * @param id       the id of the exercise, used to access participation
     * @param dueDates new due dates that get set
     */
    public void setIndividualDueDate(Long id, List<ZonedDateTime> dueDates) {
        var participations = new ArrayList<>(studentParticipationRepository.findByExerciseId(id));

        int min = Math.min(participations.size(), dueDates.size());
        for (int i = 0; i < min; i++) {
            participations.get(i).setIndividualDueDate(dueDates.get(i));
        }

        studentParticipationRepository.saveAll(participations);
    }

    public List<GradingCriterion> addGradingInstructionsToExercise(Exercise exercise, boolean save) {
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

        if (save) {
            gradingCriterionRepository.saveAll(criteria);
        }

        return exercise.getGradingCriteria();
    }

    // these should probably be in another TestService

    /**
     * changes the instructor group name of the passed course to a new group name
     *
     * @param course    course of which the group name should be changed
     * @param groupName new group name
     */
    public void changeInstructorGroupName(Course course, String groupName) {
        course.setInstructorGroupName(groupName);
        courseRepo.save(course);
    }

    public User getUserByLogin(String login) {
        // we convert to lowercase for convenience, because logins have to be lower case
        return userRepo.findOneWithGroupsAndAuthoritiesByLogin(login.toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("Provided login " + login + " does not exist in database"));
    }

}
