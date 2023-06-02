package de.tum.in.www1.artemis.fileupload;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.FileUploadExercise;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.repository.FileUploadExerciseRepository;
import de.tum.in.www1.artemis.util.ModelFactory;

@Service
public class FileUploadService {

    private static final ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(1);

    private static final ZonedDateTime futureTimestamp = ZonedDateTime.now().plusDays(1);

    @Autowired
    private FileUploadExerciseRepository fileUploadExerciseRepository;

    @Autowired
    private CourseRepository courseRepo;

    @Autowired
    private ExamRepository examRepo;

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

        FileUploadExercise fileUploadExercise = ModelFactory.generateFileUploadExercise(releaseDate, dueDate, dueDate.plusHours(1), filePattern, course);
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

        return ModelFactory.generateFileUploadExerciseForExam(filePattern, exerciseGroup);
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

}
