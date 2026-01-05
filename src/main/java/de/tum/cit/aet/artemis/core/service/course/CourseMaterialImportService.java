package de.tum.cit.aet.artemis.core.service.course;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.repository.GradingCriterionRepository;
import de.tum.cit.aet.artemis.atlas.api.CompetencyImportApi;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyImportOptionsDTO;
import de.tum.cit.aet.artemis.communication.service.FaqImportService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.CourseMaterialImportOptionsDTO;
import de.tum.cit.aet.artemis.core.dto.CourseMaterialImportResultDTO;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.exam.api.ExamImportApi;
import de.tum.cit.aet.artemis.exam.api.ExamRepositoryApi;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.fileupload.api.FileUploadImportApi;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.lecture.api.LectureImportApi;
import de.tum.cit.aet.artemis.lecture.api.LectureRepositoryApi;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.modeling.api.ModelingExerciseImportApi;
import de.tum.cit.aet.artemis.modeling.api.ModelingRepositoryApi;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTaskRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseImportService;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository;
import de.tum.cit.aet.artemis.quiz.service.QuizExerciseImportService;
import de.tum.cit.aet.artemis.text.api.TextExerciseImportApi;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.tutorialgroup.api.TutorialGroupImportApi;

/**
 * Service for importing course material from one course to another.
 * This orchestrates the import of exercises, lectures, exams, competencies,
 * tutorial groups, and FAQs using existing import services.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class CourseMaterialImportService {

    private static final Logger log = LoggerFactory.getLogger(CourseMaterialImportService.class);

    private final CourseRepository courseRepository;

    private final ExerciseRepository exerciseRepository;

    private final Optional<LectureRepositoryApi> lectureRepositoryApi;

    private final Optional<ExamRepositoryApi> examRepositoryApi;

    // Exercise import services
    private final ProgrammingExerciseImportService programmingExerciseImportService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingExerciseTaskRepository programmingExerciseTaskRepository;

    private final GradingCriterionRepository gradingCriterionRepository;

    private final QuizExerciseImportService quizExerciseImportService;

    private final QuizExerciseRepository quizExerciseRepository;

    private final Optional<ModelingExerciseImportApi> modelingExerciseImportApi;

    private final Optional<ModelingRepositoryApi> modelingRepositoryApi;

    private final Optional<TextExerciseImportApi> textExerciseImportApi;

    private final Optional<FileUploadImportApi> fileUploadImportApi;

    // Other import services
    private final Optional<LectureImportApi> lectureImportApi;

    private final Optional<ExamImportApi> examImportApi;

    private final Optional<CompetencyImportApi> competencyImportApi;

    private final Optional<TutorialGroupImportApi> tutorialGroupImportApi;

    private final FaqImportService faqImportService;

    public CourseMaterialImportService(CourseRepository courseRepository, ExerciseRepository exerciseRepository, Optional<LectureRepositoryApi> lectureRepositoryApi,
            Optional<ExamRepositoryApi> examRepositoryApi, ProgrammingExerciseImportService programmingExerciseImportService,
            ProgrammingExerciseRepository programmingExerciseRepository, ProgrammingExerciseTaskRepository programmingExerciseTaskRepository,
            GradingCriterionRepository gradingCriterionRepository, QuizExerciseImportService quizExerciseImportService, QuizExerciseRepository quizExerciseRepository,
            Optional<ModelingExerciseImportApi> modelingExerciseImportApi, Optional<ModelingRepositoryApi> modelingRepositoryApi,
            Optional<TextExerciseImportApi> textExerciseImportApi, Optional<FileUploadImportApi> fileUploadImportApi, Optional<LectureImportApi> lectureImportApi,
            Optional<ExamImportApi> examImportApi, Optional<CompetencyImportApi> competencyImportApi, Optional<TutorialGroupImportApi> tutorialGroupImportApi,
            FaqImportService faqImportService) {
        this.courseRepository = courseRepository;
        this.exerciseRepository = exerciseRepository;
        this.lectureRepositoryApi = lectureRepositoryApi;
        this.examRepositoryApi = examRepositoryApi;
        this.programmingExerciseImportService = programmingExerciseImportService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingExerciseTaskRepository = programmingExerciseTaskRepository;
        this.gradingCriterionRepository = gradingCriterionRepository;
        this.quizExerciseImportService = quizExerciseImportService;
        this.quizExerciseRepository = quizExerciseRepository;
        this.modelingExerciseImportApi = modelingExerciseImportApi;
        this.modelingRepositoryApi = modelingRepositoryApi;
        this.textExerciseImportApi = textExerciseImportApi;
        this.fileUploadImportApi = fileUploadImportApi;
        this.lectureImportApi = lectureImportApi;
        this.examImportApi = examImportApi;
        this.competencyImportApi = competencyImportApi;
        this.tutorialGroupImportApi = tutorialGroupImportApi;
        this.faqImportService = faqImportService;
    }

    /**
     * Import course material from one course to another based on the provided options.
     *
     * @param targetCourseId the ID of the course to import to
     * @param options        the import options specifying what to import
     * @param requestingUser the user requesting the import
     * @return the result of the import operation
     */
    public CourseMaterialImportResultDTO importCourseMaterial(long targetCourseId, CourseMaterialImportOptionsDTO options, User requestingUser) {
        log.info("Starting course material import from course {} to course {}", options.sourceCourseId(), targetCourseId);

        Course targetCourse = courseRepository.findByIdElseThrow(targetCourseId);
        Course sourceCourse = courseRepository.findByIdElseThrow(options.sourceCourseId());

        List<String> errors = new ArrayList<>();
        int exercisesImported = 0;
        int lecturesImported = 0;
        int examsImported = 0;
        int competenciesImported = 0;
        int tutorialGroupsImported = 0;
        int faqsImported = 0;

        // 1. Import Exercises
        if (options.importExercises()) {
            try {
                exercisesImported = importExercises(options.sourceCourseId(), targetCourse, errors);
                log.info("Imported {} exercises to course {}", exercisesImported, targetCourseId);
            }
            catch (Exception e) {
                log.error("Failed to import exercises", e);
                errors.add("Failed to import exercises: " + e.getMessage());
            }
        }

        // 2. Import Lectures
        if (options.importLectures()) {
            try {
                lecturesImported = importLectures(options.sourceCourseId(), targetCourse, errors);
                log.info("Imported {} lectures to course {}", lecturesImported, targetCourseId);
            }
            catch (Exception e) {
                log.error("Failed to import lectures", e);
                errors.add("Failed to import lectures: " + e.getMessage());
            }
        }

        // 3. Import Exams
        if (options.importExams()) {
            try {
                examsImported = importExams(options.sourceCourseId(), targetCourseId, errors);
                log.info("Imported {} exams to course {}", examsImported, targetCourseId);
            }
            catch (Exception e) {
                log.error("Failed to import exams", e);
                errors.add("Failed to import exams: " + e.getMessage());
            }
        }

        // 4. Import Competencies
        if (options.importCompetencies()) {
            try {
                competenciesImported = importCompetencies(options.sourceCourseId(), targetCourse, errors);
                log.info("Imported {} competencies to course {}", competenciesImported, targetCourseId);
            }
            catch (Exception e) {
                log.error("Failed to import competencies", e);
                errors.add("Failed to import competencies: " + e.getMessage());
            }
        }

        // 5. Import Tutorial Groups
        if (options.importTutorialGroups()) {
            try {
                tutorialGroupsImported = importTutorialGroups(options.sourceCourseId(), targetCourse, requestingUser, errors);
                log.info("Imported {} tutorial groups to course {}", tutorialGroupsImported, targetCourseId);
            }
            catch (Exception e) {
                log.error("Failed to import tutorial groups", e);
                errors.add("Failed to import tutorial groups: " + e.getMessage());
            }
        }

        // 6. Import FAQs
        if (options.importFaqs()) {
            try {
                faqsImported = importFaqs(options.sourceCourseId(), targetCourse, errors);
                log.info("Imported {} FAQs to course {}", faqsImported, targetCourseId);
            }
            catch (Exception e) {
                log.error("Failed to import FAQs", e);
                errors.add("Failed to import FAQs: " + e.getMessage());
            }
        }

        log.info("Course material import completed. Exercises: {}, Lectures: {}, Exams: {}, Competencies: {}, TutorialGroups: {}, FAQs: {}, Errors: {}", exercisesImported,
                lecturesImported, examsImported, competenciesImported, tutorialGroupsImported, faqsImported, errors.size());

        return new CourseMaterialImportResultDTO(exercisesImported, lecturesImported, examsImported, competenciesImported, tutorialGroupsImported, faqsImported, errors);
    }

    /**
     * Import all course exercises (not exam exercises) from the source course to the target course.
     */
    private int importExercises(long sourceCourseId, Course targetCourse, List<String> errors) {
        Set<Exercise> sourceExercises = exerciseRepository.findByCourseIdWithCategories(sourceCourseId);
        int imported = 0;

        for (Exercise exercise : sourceExercises) {
            // Skip exercises that belong to an exam (exerciseGroup is not null)
            if (exercise.isExamExercise()) {
                continue;
            }

            try {
                Optional<? extends Exercise> importedExercise = importSingleExercise(exercise, targetCourse);
                if (importedExercise.isPresent()) {
                    imported++;
                }
            }
            catch (Exception e) {
                log.error("Failed to import exercise {}: {}", exercise.getTitle(), e.getMessage());
                errors.add("Failed to import exercise '" + exercise.getTitle() + "': " + e.getMessage());
            }
        }

        return imported;
    }

    /**
     * Import a single exercise based on its type.
     */
    private Optional<? extends Exercise> importSingleExercise(Exercise exercise, Course targetCourse) throws Exception {
        return switch (exercise.getExerciseType()) {
            case PROGRAMMING -> importProgrammingExercise((ProgrammingExercise) exercise, targetCourse);
            case QUIZ -> importQuizExercise((QuizExercise) exercise, targetCourse);
            case MODELING -> importModelingExercise((ModelingExercise) exercise, targetCourse);
            case TEXT -> importTextExercise((TextExercise) exercise, targetCourse);
            case FILE_UPLOAD -> importFileUploadExercise((FileUploadExercise) exercise, targetCourse);
        };
    }

    private Optional<ProgrammingExercise> importProgrammingExercise(ProgrammingExercise exercise, Course targetCourse) {
        var optionalOriginal = programmingExerciseRepository
                .findByIdWithEagerTestCasesStaticCodeAnalysisCategoriesHintsAndTemplateAndSolutionParticipationsAndAuxReposAndBuildConfig(exercise.getId());
        if (optionalOriginal.isEmpty()) {
            return Optional.empty();
        }

        var originalExercise = optionalOriginal.get();
        var templateTasks = programmingExerciseTaskRepository.findByExerciseIdWithTestCases(originalExercise.getId());
        originalExercise.setTasks(new ArrayList<>(templateTasks));

        var gradingCriteria = gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(originalExercise.getId());
        originalExercise.setGradingCriteria(gradingCriteria);

        // Create new exercise for the target course
        ProgrammingExercise newExercise = new ProgrammingExercise();
        newExercise.setCourse(targetCourse);
        newExercise.setTitle(originalExercise.getTitle());
        newExercise.forceNewProjectKey();

        try {
            return Optional.of(programmingExerciseImportService.importProgrammingExercise(originalExercise, newExercise, false, false, false));
        }
        catch (Exception e) {
            log.error("Failed to import programming exercise: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<QuizExercise> importQuizExercise(QuizExercise exercise, Course targetCourse) {
        var optionalOriginal = quizExerciseRepository.findById(exercise.getId());
        if (optionalOriginal.isEmpty()) {
            return Optional.empty();
        }

        QuizExercise newExercise = new QuizExercise();
        newExercise.setCourse(targetCourse);
        newExercise.setTitle(optionalOriginal.get().getTitle());

        try {
            return Optional.of(quizExerciseImportService.importQuizExercise(optionalOriginal.get(), newExercise, null));
        }
        catch (Exception e) {
            log.error("Failed to import quiz exercise: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<ModelingExercise> importModelingExercise(ModelingExercise exercise, Course targetCourse) {
        if (modelingRepositoryApi.isEmpty() || modelingExerciseImportApi.isEmpty()) {
            return Optional.empty();
        }
        var optionalOriginal = modelingRepositoryApi.get().findByIdWithExampleSubmissionsAndResultsAndGradingCriteria(exercise.getId());
        if (optionalOriginal.isEmpty()) {
            return Optional.empty();
        }

        ModelingExercise newExercise = new ModelingExercise();
        newExercise.setCourse(targetCourse);
        newExercise.setTitle(optionalOriginal.get().getTitle());

        return modelingExerciseImportApi.get().importModelingExercise(exercise.getId(), newExercise);
    }

    private Optional<TextExercise> importTextExercise(TextExercise exercise, Course targetCourse) {
        if (textExerciseImportApi.isEmpty()) {
            return Optional.empty();
        }

        TextExercise newExercise = new TextExercise();
        newExercise.setCourse(targetCourse);
        newExercise.setTitle(exercise.getTitle());

        return textExerciseImportApi.get().importTextExercise(exercise.getId(), newExercise);
    }

    private Optional<FileUploadExercise> importFileUploadExercise(FileUploadExercise exercise, Course targetCourse) {
        if (fileUploadImportApi.isEmpty()) {
            return Optional.empty();
        }

        FileUploadExercise newExercise = new FileUploadExercise();
        newExercise.setCourse(targetCourse);
        newExercise.setTitle(exercise.getTitle());

        return fileUploadImportApi.get().importFileUploadExercise(exercise.getId(), newExercise);
    }

    /**
     * Import all lectures from the source course to the target course.
     */
    private int importLectures(long sourceCourseId, Course targetCourse, List<String> errors) {
        if (lectureRepositoryApi.isEmpty() || lectureImportApi.isEmpty()) {
            errors.add("Lecture import service not available");
            return 0;
        }

        Set<Lecture> sourceLectures = lectureRepositoryApi.get().findAllByCourseIdWithAttachmentsAndLectureUnits(sourceCourseId);
        int imported = 0;

        for (Lecture lecture : sourceLectures) {
            try {
                lectureImportApi.get().importLecture(lecture, targetCourse, true);
                imported++;
            }
            catch (Exception e) {
                log.error("Failed to import lecture {}: {}", lecture.getTitle(), e.getMessage());
                errors.add("Failed to import lecture '" + lecture.getTitle() + "': " + e.getMessage());
            }
        }

        return imported;
    }

    /**
     * Import all exams from the source course to the target course.
     */
    private int importExams(long sourceCourseId, long targetCourseId, List<String> errors) {
        if (examImportApi.isEmpty() || examRepositoryApi.isEmpty()) {
            errors.add("Exam import service not available");
            return 0;
        }

        List<Exam> sourceExams = examRepositoryApi.get().findByCourseIdWithExerciseGroupsAndExercises(sourceCourseId);
        int imported = 0;

        for (Exam exam : sourceExams) {
            try {
                examImportApi.get().importExamWithExercises(exam, targetCourseId);
                imported++;
            }
            catch (Exception e) {
                log.error("Failed to import exam {}: {}", exam.getTitle(), e.getMessage());
                errors.add("Failed to import exam '" + exam.getTitle() + "': " + e.getMessage());
            }
        }

        return imported;
    }

    /**
     * Import all competencies from the source course to the target course.
     */
    private int importCompetencies(long sourceCourseId, Course targetCourse, List<String> errors) {
        if (competencyImportApi.isEmpty()) {
            errors.add("Competency service not available");
            return 0;
        }

        try {
            var competencies = competencyImportApi.get().findAllForCourseWithExercisesAndLectureUnitsAndLecturesAndAttachments(sourceCourseId);
            var importOptions = new CompetencyImportOptionsDTO(null, Optional.of(sourceCourseId), false, false, false, Optional.empty(), false);
            var imported = competencyImportApi.get().importCourseCompetencies(targetCourse, competencies, importOptions);
            return imported.size();
        }
        catch (Exception e) {
            log.error("Failed to import competencies: {}", e.getMessage());
            errors.add("Failed to import competencies: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Import all tutorial groups from the source course to the target course.
     */
    private int importTutorialGroups(long sourceCourseId, Course targetCourse, User requestingUser, List<String> errors) {
        if (tutorialGroupImportApi.isEmpty()) {
            errors.add("Tutorial group import service not available");
            return 0;
        }

        try {
            var imported = tutorialGroupImportApi.get().importTutorialGroups(sourceCourseId, targetCourse, requestingUser);
            return imported.size();
        }
        catch (Exception e) {
            log.error("Failed to import tutorial groups: {}", e.getMessage());
            errors.add("Failed to import tutorial groups: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Import all FAQs from the source course to the target course.
     */
    private int importFaqs(long sourceCourseId, Course targetCourse, List<String> errors) {
        try {
            var imported = faqImportService.importFaqs(sourceCourseId, targetCourse);
            return imported.size();
        }
        catch (Exception e) {
            log.error("Failed to import FAQs: {}", e.getMessage());
            errors.add("Failed to import FAQs: " + e.getMessage());
            return 0;
        }
    }
}
