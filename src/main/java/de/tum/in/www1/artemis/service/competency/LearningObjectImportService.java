package de.tum.cit.det.www1.artemis.service.competency;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.FileUploadExercise;
import de.tum.in.www1.artemis.domain.GradingCriterion;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.competency.CourseCompetency;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.repository.CourseCompetencyRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.FileUploadExerciseRepository;
import de.tum.in.www1.artemis.repository.GradingCriterionRepository;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.repository.LectureUnitRepository;
import de.tum.in.www1.artemis.repository.ModelingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.QuizExerciseRepository;
import de.tum.in.www1.artemis.repository.TextExerciseRepository;
import de.tum.in.www1.artemis.repository.hestia.ProgrammingExerciseTaskRepository;
import de.tum.in.www1.artemis.service.FileUploadExerciseImportService;
import de.tum.in.www1.artemis.service.LectureImportService;
import de.tum.in.www1.artemis.service.LectureUnitImportService;
import de.tum.in.www1.artemis.service.ModelingExerciseImportService;
import de.tum.in.www1.artemis.service.TextExerciseImportService;
import de.tum.in.www1.artemis.service.plagiarism.PlagiarismDetectionConfigHelper;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseImportService;
import de.tum.in.www1.artemis.service.quiz.QuizExerciseImportService;
import de.tum.in.www1.artemis.web.rest.dto.competency.CompetencyImportOptionsDTO;
import de.tum.in.www1.artemis.web.rest.dto.competency.CompetencyWithTailRelationDTO;

/**
 * Service for importing learning objects related to competencies.
 */
@Profile(PROFILE_CORE)
@Service
public class LearningObjectImportService {

    private final ExerciseRepository exerciseRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingExerciseImportService programmingExerciseImportService;

    private final FileUploadExerciseRepository fileUploadExerciseRepository;

    private final FileUploadExerciseImportService fileUploadExerciseImportService;

    private final ModelingExerciseRepository modelingExerciseRepository;

    private final ModelingExerciseImportService modelingExerciseImportService;

    private final TextExerciseRepository textExerciseRepository;

    private final TextExerciseImportService textExerciseImportService;

    private final QuizExerciseRepository quizExerciseRepository;

    private final QuizExerciseImportService quizExerciseImportService;

    private final LectureRepository lectureRepository;

    private final LectureImportService lectureImportService;

    private final LectureUnitRepository lectureUnitRepository;

    private final LectureUnitImportService lectureUnitImportService;

    private final CourseCompetencyRepository courseCompetencyRepository;

    private final ProgrammingExerciseTaskRepository programmingExerciseTaskRepository;

    private final GradingCriterionRepository gradingCriterionRepository;

    public LearningObjectImportService(ExerciseRepository exerciseRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            ProgrammingExerciseImportService programmingExerciseImportService, FileUploadExerciseRepository fileUploadExerciseRepository,
            FileUploadExerciseImportService fileUploadExerciseImportService, ModelingExerciseRepository modelingExerciseRepository,
            ModelingExerciseImportService modelingExerciseImportService, TextExerciseRepository textExerciseRepository, TextExerciseImportService textExerciseImportService,
            QuizExerciseRepository quizExerciseRepository, QuizExerciseImportService quizExerciseImportService, LectureRepository lectureRepository,
            LectureImportService lectureImportService, LectureUnitRepository lectureUnitRepository, LectureUnitImportService lectureUnitImportService,
            CourseCompetencyRepository courseCompetencyRepository, ProgrammingExerciseTaskRepository programmingExerciseTaskRepository,
            GradingCriterionRepository gradingCriterionRepository) {
        this.exerciseRepository = exerciseRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingExerciseImportService = programmingExerciseImportService;
        this.fileUploadExerciseRepository = fileUploadExerciseRepository;
        this.fileUploadExerciseImportService = fileUploadExerciseImportService;
        this.modelingExerciseRepository = modelingExerciseRepository;
        this.modelingExerciseImportService = modelingExerciseImportService;
        this.textExerciseRepository = textExerciseRepository;
        this.textExerciseImportService = textExerciseImportService;
        this.quizExerciseRepository = quizExerciseRepository;
        this.quizExerciseImportService = quizExerciseImportService;
        this.lectureRepository = lectureRepository;
        this.lectureImportService = lectureImportService;
        this.lectureUnitRepository = lectureUnitRepository;
        this.lectureUnitImportService = lectureUnitImportService;
        this.courseCompetencyRepository = courseCompetencyRepository;
        this.programmingExerciseTaskRepository = programmingExerciseTaskRepository;
        this.gradingCriterionRepository = gradingCriterionRepository;
    }

    /**
     * Imports the related learning objects from the source course competencies into the course to import into and links them to the imported competencies.
     *
     * @param sourceCourseCompetencies The source course competencies to import from.
     * @param idToImportedCompetency   A map from the source competency IDs to the imported competencies.
     * @param courseToImportInto       The course to import the learning objects into.
     * @param importOptions            The import options.
     */
    public void importRelatedLearningObjects(Collection<? extends CourseCompetency> sourceCourseCompetencies, Map<Long, CompetencyWithTailRelationDTO> idToImportedCompetency,
            Course courseToImportInto, CompetencyImportOptionsDTO importOptions) {
        Set<CourseCompetency> importedCourseCompetencies = idToImportedCompetency.values().stream().map(CompetencyWithTailRelationDTO::competency).collect(Collectors.toSet());

        Set<Exercise> importedExercises = new HashSet<>();
        if (importOptions.importExercises()) {
            try {
                importOrLoadExercises(sourceCourseCompetencies, idToImportedCompetency, courseToImportInto, importedExercises);
            }
            catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
        Map<String, Lecture> titleToImportedLectures = new HashMap<>();
        Set<LectureUnit> importedLectureUnits = new HashSet<>();
        if (importOptions.importLectures()) {
            importOrLoadLectureUnits(sourceCourseCompetencies, idToImportedCompetency, courseToImportInto, titleToImportedLectures, importedLectureUnits);
        }
        Set<Lecture> importedLectures = new HashSet<>(titleToImportedLectures.values());

        if (importOptions.referenceDate().isPresent()) {
            setAllDates(importedExercises, importedLectures, importedLectureUnits, importedCourseCompetencies, importOptions.referenceDate().get(), importOptions.isReleaseDate());
        }

        courseCompetencyRepository.saveAll(importedCourseCompetencies);
        exerciseRepository.saveAll(importedExercises);
        lectureRepository.saveAll(importedLectures);
    }

    private void importOrLoadExercises(Collection<? extends CourseCompetency> sourceCourseCompetencies, Map<Long, CompetencyWithTailRelationDTO> idToImportedCompetency,
            Course courseToImportInto, Set<Exercise> importedExercises) throws JsonProcessingException {
        for (CourseCompetency sourceCourseCompetency : sourceCourseCompetencies) {
            for (Exercise sourceExercise : sourceCourseCompetency.getExercises()) {
                Exercise importedExercise = importOrLoadExercise(sourceExercise, courseToImportInto);

                importedExercises.add(importedExercise);

                importedExercise.getCompetencies().add(idToImportedCompetency.get(sourceCourseCompetency.getId()).competency());
                idToImportedCompetency.get(sourceCourseCompetency.getId()).competency().getExercises().add(importedExercise);
            }
        }
    }

    private Exercise importOrLoadExercise(Exercise sourceExercise, Course course) throws JsonProcessingException {
        return switch (sourceExercise) {
            case ProgrammingExercise programmingExercise -> importOrLoadProgrammingExercise(programmingExercise, course);
            case FileUploadExercise fileUploadExercise -> importOrLoadExercise(fileUploadExercise, course, fileUploadExerciseRepository::findWithCompetenciesByTitleAndCourseId,
                    fileUploadExerciseRepository::findWithGradingCriteriaByIdElseThrow, fileUploadExerciseImportService::importFileUploadExercise);
            case ModelingExercise modelingExercise -> importOrLoadExercise(modelingExercise, course, modelingExerciseRepository::findWithCompetenciesByTitleAndCourseId,
                    modelingExerciseRepository::findByIdWithExampleSubmissionsAndResultsAndPlagiarismDetectionConfigElseThrow,
                    modelingExerciseImportService::importModelingExercise);
            case TextExercise textExercise -> importOrLoadExercise(textExercise, course, textExerciseRepository::findWithCompetenciesByTitleAndCourseId,
                    textExerciseRepository::findByIdWithExampleSubmissionsAndResultsAndGradingCriteriaElseThrow, textExerciseImportService::importTextExercise);
            case QuizExercise quizExercise -> importOrLoadExercise(quizExercise, course, quizExerciseRepository::findWithCompetenciesByTitleAndCourseId,
                    quizExerciseRepository::findByIdWithQuestionsAndStatisticsAndCompetenciesAndBatchesAndGradingCriteriaElseThrow, (exercise, templateExercise) -> {
                        try {
                            return quizExerciseImportService.importQuizExercise(exercise, templateExercise, null);
                        }
                        catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
            default -> throw new IllegalStateException("Unexpected value: " + sourceExercise);
        };
    }

    private Exercise importOrLoadProgrammingExercise(ProgrammingExercise programmingExercise, Course course) throws JsonProcessingException {
        Optional<ProgrammingExercise> foundByTitle = programmingExerciseRepository.findWithCompetenciesByTitleAndCourseId(programmingExercise.getTitle(), course.getId());
        Optional<ProgrammingExercise> foundByShortName = programmingExerciseRepository.findByShortNameAndCourseIdWithCompetencies(programmingExercise.getShortName(),
                course.getId());

        if (foundByTitle.isPresent() && foundByShortName.isPresent() && !foundByTitle.get().equals(foundByShortName.get())) {
            throw new IllegalArgumentException("Two programming exercises with the title or short name already exist in the course");
        }

        if (foundByTitle.isPresent()) {
            return foundByTitle.get();
        }
        else if (foundByShortName.isPresent()) {
            return foundByShortName.get();
        }
        else {
            programmingExercise = programmingExerciseRepository.findByIdForImportElseThrow(programmingExercise.getId());
            // Fetching the tasks separately, as putting it in the query above leads to Hibernate duplicating the tasks.
            var templateTasks = programmingExerciseTaskRepository.findByExerciseIdWithTestCases(programmingExercise.getId());
            programmingExercise.setTasks(new ArrayList<>(templateTasks));
            Set<GradingCriterion> gradingCriteria = gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(programmingExercise.getId());
            programmingExercise.setGradingCriteria(gradingCriteria);

            ProgrammingExercise newExercise = programmingExerciseRepository
                    .findByIdWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesAndCompetenciesAndPlagiarismDetectionConfigAndBuildConfigElseThrow(
                            programmingExercise.getId());
            PlagiarismDetectionConfigHelper.createAndSaveDefaultIfNullAndCourseExercise(newExercise, programmingExerciseRepository);
            newExercise.setCourse(course);
            newExercise.forceNewProjectKey();

            clearProgrammingExerciseAttributes(newExercise);

            return programmingExerciseImportService.importProgrammingExercise(programmingExercise, newExercise, false, false, false);
        }
    }

    private void clearProgrammingExerciseAttributes(ProgrammingExercise programmingExercise) {
        programmingExercise.setTasks(null);
        programmingExercise.setExerciseHints(new HashSet<>());
        programmingExercise.setTestCases(new HashSet<>());
        programmingExercise.setStaticCodeAnalysisCategories(new HashSet<>());
        programmingExercise.setTeams(new HashSet<>());
        programmingExercise.setGradingCriteria(new HashSet<>());
        programmingExercise.setStudentParticipations(new HashSet<>());
        programmingExercise.setTutorParticipations(new HashSet<>());
        programmingExercise.setExampleSubmissions(new HashSet<>());
        programmingExercise.setAttachments(new HashSet<>());
        programmingExercise.setPosts(new HashSet<>());
        programmingExercise.setPlagiarismCases(new HashSet<>());
        programmingExercise.setCompetencies(new HashSet<>());
    }

    /**
     * Imports or loads an exercise.
     *
     * @param exercise       The source exercise for the import
     * @param course         The course to import the exercise into
     * @param findFunction   The function to find an existing exercise by title
     * @param loadForImport  The function to load an exercise for import
     * @param importFunction The function to import the exercise
     * @return The imported or loaded exercise
     * @param <E> The type of the exercise
     */
    private <E extends Exercise> Exercise importOrLoadExercise(E exercise, Course course, BiFunction<String, Long, Optional<E>> findFunction, Function<Long, E> loadForImport,
            BiFunction<E, E, E> importFunction) {
        Optional<E> foundByTitle = findFunction.apply(exercise.getTitle(), course.getId());
        if (foundByTitle.isPresent()) {
            return foundByTitle.get();
        }
        else {
            exercise = loadForImport.apply(exercise.getId());
            exercise.setCourse(course);
            exercise.setId(null);
            exercise.setCompetencies(new HashSet<>());

            return importFunction.apply(exercise, exercise);
        }
    }

    /**
     * Imports or loads a lecture unit. If the lecture unit needs to be imported, the lecture is imported or loaded as well.
     *
     * @param sourceCourseCompetencies The source course competencies to import from
     * @param idToImportedCompetency   A map from the source competency IDs to the imported competencies
     * @param courseToImportInto       The course to import the lecture unit into
     * @param titleToImportedLectures  A map from the source lecture titles to the imported lectures
     * @param importedLectureUnits     The set of imported lecture units
     */
    private void importOrLoadLectureUnits(Collection<? extends CourseCompetency> sourceCourseCompetencies, Map<Long, CompetencyWithTailRelationDTO> idToImportedCompetency,
            Course courseToImportInto, Map<String, Lecture> titleToImportedLectures, Set<LectureUnit> importedLectureUnits) {
        for (CourseCompetency sourceCourseCompetency : sourceCourseCompetencies) {
            for (LectureUnit sourceLectureUnit : sourceCourseCompetency.getLectureUnits()) {
                Optional<LectureUnit> foundLectureUnit = lectureUnitRepository.findByNameAndCourseIdWithCompetencies(sourceLectureUnit.getName(), courseToImportInto.getId());

                LectureUnit importedLectureUnit;
                if (foundLectureUnit.isEmpty()) {
                    Lecture sourceLecture = sourceLectureUnit.getLecture();
                    Lecture importedLecture = importOrLoadLecture(sourceLecture, courseToImportInto, titleToImportedLectures);

                    importedLectureUnit = foundLectureUnit.orElseGet(() -> lectureUnitRepository.save(lectureUnitImportService.importLectureUnit(sourceLectureUnit)));

                    importedLecture.getLectureUnits().add(importedLectureUnit);
                    importedLectureUnit.setLecture(importedLecture);
                }
                else {
                    importedLectureUnit = foundLectureUnit.get();
                }

                importedLectureUnits.add(importedLectureUnit);

                importedLectureUnit.getCompetencies().add(idToImportedCompetency.get(sourceCourseCompetency.getId()).competency());
                idToImportedCompetency.get(sourceCourseCompetency.getId()).competency().getLectureUnits().add(importedLectureUnit);
            }
        }
    }

    private Lecture importOrLoadLecture(Lecture sourceLecture, Course courseToImportInto, Map<String, Lecture> titleToImportedLectures) {
        Optional<Lecture> foundLecture = Optional.ofNullable(titleToImportedLectures.get(sourceLecture.getTitle()));
        if (foundLecture.isEmpty()) {
            foundLecture = lectureRepository.findByTitleAndCourseIdWithLectureUnits(sourceLecture.getTitle(), courseToImportInto.getId());
        }
        Lecture importedLecture = foundLecture.orElseGet(() -> lectureImportService.importLecture(sourceLecture, courseToImportInto, false));
        titleToImportedLectures.put(importedLecture.getTitle(), importedLecture);

        return importedLecture;
    }

    private void setAllDates(Set<Exercise> importedExercises, Set<Lecture> importedLectures, Set<LectureUnit> importedLectureUnits,
            Set<CourseCompetency> importedCourseCompetencies, ZonedDateTime referenceDate, boolean isReleaseDate) {
        long timeOffset = determineTimeOffset(importedExercises, importedLectures, importedLectureUnits, importedCourseCompetencies, referenceDate, isReleaseDate);
        if (timeOffset == 0) {
            return;
        }

        importedExercises.forEach(exercise -> setAllExerciseDates(exercise, timeOffset));
        importedLectures.forEach(lecture -> setAllLectureDates(lecture, timeOffset));
        importedLectureUnits.forEach(lectureUnit -> setAllLectureUnitDates(lectureUnit, timeOffset));
        importedCourseCompetencies.forEach(competency -> setAllCompetencyDates(competency, timeOffset));
    }

    /**
     * Finds the earliest relevant time and determines the time offset to apply to the dates of the imported learning objects.
     *
     * @param importedExercises          The imported exercises
     * @param importedLectures           The imported lectures
     * @param importedLectureUnits       The imported lecture units
     * @param importedCourseCompetencies The imported competencies
     * @param referenceDate              The reference date to calculate the offset from
     * @param isReleaseDate              Whether the offset is for the release date or the due date
     * @return The time offset to apply
     */
    private long determineTimeOffset(Set<Exercise> importedExercises, Set<Lecture> importedLectures, Set<LectureUnit> importedLectureUnits,
            Set<CourseCompetency> importedCourseCompetencies, ZonedDateTime referenceDate, boolean isReleaseDate) {
        Optional<ZonedDateTime> earliestTime;

        if (isReleaseDate) {
            Stream<ZonedDateTime> exerciseDates = importedExercises.stream().map(Exercise::getReleaseDate);
            Stream<ZonedDateTime> lectureDates = importedLectures.stream().map(Lecture::getVisibleDate);
            Stream<ZonedDateTime> lectureUnitDates = importedLectureUnits.stream().map(LectureUnit::getReleaseDate);
            earliestTime = Stream.concat(exerciseDates, Stream.concat(lectureDates, lectureUnitDates)).filter(Objects::nonNull).min(Comparator.naturalOrder());
        }
        else {
            Stream<ZonedDateTime> exerciseDates = importedExercises.stream().map(Exercise::getDueDate);
            Stream<ZonedDateTime> lectureDates = importedLectures.stream().map(Lecture::getEndDate);
            Stream<ZonedDateTime> competencyDates = importedCourseCompetencies.stream().map(CourseCompetency::getSoftDueDate);
            earliestTime = Stream.concat(exerciseDates, Stream.concat(lectureDates, competencyDates)).filter(Objects::nonNull).min(Comparator.naturalOrder());
        }

        return earliestTime.map(zonedDateTime -> referenceDate.toEpochSecond() - zonedDateTime.toEpochSecond()).orElse(0L);
    }

    private void setAllExerciseDates(Exercise exercise, long timeOffset) {
        if (exercise.getReleaseDate() != null) {
            exercise.setReleaseDate(exercise.getReleaseDate().plusSeconds(timeOffset));
        }
        if (exercise.getStartDate() != null) {
            exercise.setStartDate(exercise.getStartDate().plusSeconds(timeOffset));
        }
        if (exercise.getDueDate() != null) {
            exercise.setDueDate(exercise.getDueDate().plusSeconds(timeOffset));
        }
        if (exercise.getAssessmentDueDate() != null) {
            exercise.setAssessmentDueDate(exercise.getAssessmentDueDate().plusSeconds(timeOffset));
        }
        if (exercise.getExampleSolutionPublicationDate() != null) {
            exercise.setExampleSolutionPublicationDate(exercise.getExampleSolutionPublicationDate().plusSeconds(timeOffset));
        }

        if (exercise instanceof ProgrammingExercise programmingExercise && programmingExercise.getBuildAndTestStudentSubmissionsAfterDueDate() != null) {
            programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(programmingExercise.getBuildAndTestStudentSubmissionsAfterDueDate().plusSeconds(timeOffset));
        }
    }

    private void setAllLectureDates(Lecture lecture, long timeOffset) {
        if (lecture.getVisibleDate() != null) {
            lecture.setVisibleDate(lecture.getVisibleDate().plusSeconds(timeOffset));
        }
        if (lecture.getStartDate() != null) {
            lecture.setStartDate(lecture.getStartDate().plusSeconds(timeOffset));
        }
        if (lecture.getEndDate() != null) {
            lecture.setEndDate(lecture.getEndDate().plusSeconds(timeOffset));
        }
    }

    private void setAllLectureUnitDates(LectureUnit lectureUnit, long timeOffset) {
        if (lectureUnit.getReleaseDate() != null) {
            lectureUnit.setReleaseDate(lectureUnit.getReleaseDate().plusSeconds(timeOffset));
        }
    }

    private void setAllCompetencyDates(CourseCompetency competency, long timeOffset) {
        if (competency.getSoftDueDate() != null) {
            competency.setSoftDueDate(competency.getSoftDueDate().plusSeconds(timeOffset));
        }
    }
}
