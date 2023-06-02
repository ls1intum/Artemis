package de.tum.in.www1.artemis.exercise.fileupload;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

}
