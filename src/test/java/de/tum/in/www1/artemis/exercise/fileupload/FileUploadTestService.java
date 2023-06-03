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

    private static final ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(1);

    private static final ZonedDateTime futureTimestamp = ZonedDateTime.now().plusDays(1);

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
        FileUploadExercise fileUploadExercise = createFileUploadExercise(releaseDate, dueDate, filePattern);
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
    public FileUploadExercise createFileUploadExercise(ZonedDateTime releaseDate, ZonedDateTime dueDate, String filePattern) {
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
        return createAndAndSaveFileUploadExercise(pastTimestamp, futureTimestamp, filePattern);
    }

    /**
     * creates an active (start date in the past, due date in the future) file upload exercise
     *
     * @param filePattern file pattern of the exercise
     * @return created file upload exercise
     */
    public FileUploadExercise createActiveFileUploadExercise(String filePattern) {
        return createFileUploadExercise(pastTimestamp, futureTimestamp, filePattern);
    }

    // TODO: should this be in some other service? course service?
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

    // todo: javadoc
    public FileUploadExercise findFileUploadExercise(Long id) {
        return fileUploadExerciseRepository.findById(id).orElse(null);
    }

    // todo: javadoc
    public FileUploadExercise createAndSaveExamFileUploadExercise(ZonedDateTime startDate, ZonedDateTime endDate, String filePattern) {
        FileUploadExercise fileUploadExercise = createExamFileUploadExercise(startDate, endDate, filePattern);
        fileUploadExerciseRepository.save(fileUploadExercise);

        return fileUploadExercise;
    }

    // todo: javddoc
    public FileUploadExercise createExamFileUploadExercise(ZonedDateTime startDate, ZonedDateTime endDate, String filePattern) {
        Course course = ModelFactory.generateCourse(null, null, null, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");

        Exam exam = ModelFactory.generateExam(course, startDate.minusMinutes(5), startDate, endDate, false);
        ExerciseGroup exerciseGroup = ModelFactory.generateExerciseGroup(true, exam);

        courseRepo.save(course);
        examRepo.save(exam);

        return FileUploadTestFactory.generateFileUploadExerciseForExam(filePattern, exerciseGroup);
    }

    public FileUploadExercise createExamActiveFileUploadExercise(String filePattern) {
        return createExamFileUploadExercise(pastTimestamp, futureTimestamp, filePattern);
    }

    public FileUploadExercise createAndSaveExamActiveFileUploadExercise(String filePattern) {
        return createAndSaveExamFileUploadExercise(pastTimestamp, futureTimestamp, filePattern);
    }

    public void setExampleSolutionPublicationDateAndSave(FileUploadExercise fileUploadExercise, ZonedDateTime solutionPublicationDate) {
        fileUploadExercise.setExampleSolutionPublicationDate(solutionPublicationDate);
        fileUploadExerciseRepository.save(fileUploadExercise);
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

    // todo javadoc
    public Feedback createAndSaveFeedback(GradingInstruction gradingInstruction) {
        Feedback feedback = new Feedback();
        feedback.setGradingInstruction(gradingInstruction);
        feedbackRepository.save(feedback);
        return feedback;
    }

    public ArrayList<StudentParticipation> setIndividualDueDate(FileUploadExercise fileUploadExercise, ArrayList<ZonedDateTime> dueDates) {
        var participations = new ArrayList<>(studentParticipationRepository.findByExerciseId(fileUploadExercise.getId()));

        int min = Math.min(participations.size(), dueDates.size());
        for (int i = 0; i < min; i++) {
            participations.get(i).setIndividualDueDate(dueDates.get(i));
        }

        studentParticipationRepository.saveAll(participations);
        return participations;
    }

    public Set<StudentParticipation> getParticipationsOfExercise(FileUploadExercise fileUploadExercise) {
        return studentParticipationRepository.findByExerciseId(fileUploadExercise.getId());
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
        return studentParticipationRepository.findWithEagerLegalSubmissionsAndResultsAssessorsById(storedParticipation.get().getId()).get();
    }

    // todo: and participation
    public FileUploadSubmission addFileUploadSubmission(FileUploadExercise fileUploadExercise, FileUploadSubmission fileUploadSubmission, String login) {
        StudentParticipation participation = createAndSaveParticipationForExercise(fileUploadExercise, login);
        participation.addSubmission(fileUploadSubmission);
        fileUploadSubmission.setParticipation(participation);
        fileUploadSubmissionRepository.save(fileUploadSubmission);
        studentParticipationRepository.save(participation);
        return fileUploadSubmission;
    }

    public FileUploadSubmission saveFileUploadSubmission(FileUploadExercise exercise, FileUploadSubmission submission, String login) {
        StudentParticipation participation = createAndSaveParticipationForExercise(exercise, login);
        participation.addSubmission(submission);
        submission.setParticipation(participation);
        fileUploadSubmissionRepository.save(submission);
        return submission;
    }

    public void createFileUploadSubmissionWithFile(String loginPrefix, FileUploadExercise fileUploadExercise, String filename) throws IOException {
        var fileUploadSubmission = FileUploadTestFactory.generateFileUploadSubmission(true);
        fileUploadSubmission = addFileUploadSubmission(fileUploadExercise, fileUploadSubmission, loginPrefix + "student1");

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

    // todo: put into different Service!!
    public User getUserByLogin(String login) {
        // we convert to lowercase for convenience, because logins have to be lower case
        return userRepo.findOneWithGroupsAndAuthoritiesByLogin(login.toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("Provided login " + login + " does not exist in database"));
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

    public Course addCourseWithFourFileUploadExercise() {
        Course course = ModelFactory.generateCourse(null, pastTimestamp, futureTimestamp.plusHours(1), new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        courseRepo.save(course);

        var fileUploadExercises = createFourFileUploadExercisesWithCourseWithCustomUserGroupAssignment(course);
        fileUploadExerciseRepository.saveAll(fileUploadExercises);
        course.setExercises(new HashSet<>(fileUploadExercises));

        return course;
    }

    public Set<FileUploadExercise> createFourFileUploadExercisesWithCourseWithCustomUserGroupAssignment(Course course) {
        FileUploadExercise releasedFileUploadExercise = FileUploadTestFactory.generateFileUploadExercise(pastTimestamp, futureTimestamp, futureTimestamp.plusHours(1), "png,pdf",
                course);
        releasedFileUploadExercise.setTitle("released");
        FileUploadExercise finishedFileUploadExercise = FileUploadTestFactory.generateFileUploadExercise(pastTimestamp, pastTimestamp, futureTimestamp.plusHours(1), "png,pdf",
                course);
        finishedFileUploadExercise.setTitle("finished");
        FileUploadExercise assessedFileUploadExercise = FileUploadTestFactory.generateFileUploadExercise(pastTimestamp, pastTimestamp, pastTimestamp, "png,pdf", course);
        assessedFileUploadExercise.setTitle("assessed");
        FileUploadExercise noDueDateFileUploadExercise = FileUploadTestFactory.generateFileUploadExercise(pastTimestamp, null, pastTimestamp, "png,pdf", course);
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

}
