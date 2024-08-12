package de.tum.cit.det.www1.artemis.service.competency;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
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
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.repository.LectureUnitRepository;
import de.tum.in.www1.artemis.repository.ModelingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.QuizExerciseRepository;
import de.tum.in.www1.artemis.repository.TextExerciseRepository;
import de.tum.in.www1.artemis.service.FileUploadExerciseImportService;
import de.tum.in.www1.artemis.service.LectureImportService;
import de.tum.in.www1.artemis.service.LectureUnitImportService;
import de.tum.in.www1.artemis.service.ModelingExerciseImportService;
import de.tum.in.www1.artemis.service.TextExerciseImportService;
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

    public LearningObjectImportService(ExerciseRepository exerciseRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            ProgrammingExerciseImportService programmingExerciseImportService, FileUploadExerciseRepository fileUploadExerciseRepository,
            FileUploadExerciseImportService fileUploadExerciseImportService, ModelingExerciseRepository modelingExerciseRepository,
            ModelingExerciseImportService modelingExerciseImportService, TextExerciseRepository textExerciseRepository, TextExerciseImportService textExerciseImportService,
            QuizExerciseRepository quizExerciseRepository, QuizExerciseImportService quizExerciseImportService, LectureRepository lectureRepository,
            LectureImportService lectureImportService, LectureUnitRepository lectureUnitRepository, LectureUnitImportService lectureUnitImportService,
            CourseCompetencyRepository courseCompetencyRepository) {
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
    }

    /**
     * Imports the related learning objects from the source course competencies into the course to import into and links them to the imported competencies.
     *
     * @param sourceCourseCompetencies The source course competencies to import from.
     * @param idToImportedCompetency   A map from the source competency IDs to the imported competencies.
     * @param courseToImportInto       The course to import the learning objects into.
     * @param importOptions            The import options.
     */
    public void importRelatedLearningObjects(Set<CourseCompetency> sourceCourseCompetencies, Map<Long, CompetencyWithTailRelationDTO> idToImportedCompetency,
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
        Set<Lecture> importedLectures = new HashSet<>();
        Set<LectureUnit> importedLectureUnits = new HashSet<>();
        if (importOptions.importLectures()) {
            importOrLoadLectureUnits(sourceCourseCompetencies, idToImportedCompetency, courseToImportInto, importedLectures, importedLectureUnits);
        }

        if (importOptions.referenceDate().isPresent()) {
            setAllDates(importedExercises, importedLectures, importedLectureUnits, importedCourseCompetencies, importOptions.referenceDate().get(), importOptions.isReleaseDate());
        }

        if (!importedExercises.isEmpty()) {
            exerciseRepository.saveAll(importedExercises);
        }
        if (!importedLectures.isEmpty()) {
            lectureRepository.saveAll(importedLectures);
        }
        if (!importedLectureUnits.isEmpty()) {
            lectureUnitRepository.saveAll(importedLectureUnits);
        }
        courseCompetencyRepository.saveAll(importedCourseCompetencies);
    }

    private void importOrLoadExercises(Set<CourseCompetency> sourceCourseCompetencies, Map<Long, CompetencyWithTailRelationDTO> idToImportedCompetency, Course courseToImportInto,
            Set<Exercise> importedExercises) throws JsonProcessingException {
        for (CourseCompetency sourceCourseCompetency : sourceCourseCompetencies) {
            for (Exercise sourceExercise : sourceCourseCompetency.getExercises()) {
                Exercise importedExercise = importOrLoadExercise(sourceExercise, courseToImportInto);

                importedExercise.getCompetencies().add(idToImportedCompetency.get(sourceCourseCompetency.getId()).competency());
                idToImportedCompetency.get(sourceCourseCompetency.getId()).competency().getExercises().add(importedExercise);

                importedExercises.add(importedExercise);
            }
        }
    }

    private Exercise importOrLoadExercise(Exercise sourceExercise, Course course) throws JsonProcessingException {
        return switch (sourceExercise) {
            case ProgrammingExercise programmingExercise -> importOrLoadProgrammingExercise(programmingExercise, course);
            case FileUploadExercise fileUploadExercise -> importOrLoadExercise(fileUploadExercise, course.getId(), fileUploadExerciseRepository::findByTitleAndCourseId,
                    exercise -> fileUploadExerciseImportService.importFileUploadExercise(exercise, new FileUploadExercise()));
            case ModelingExercise modelingExercise -> importOrLoadExercise(modelingExercise, course.getId(), modelingExerciseRepository::findByTitleAndCourseId,
                    exercise -> modelingExerciseImportService.importModelingExercise(exercise, new ModelingExercise()));
            case TextExercise textExercise -> importOrLoadExercise(textExercise, course.getId(), textExerciseRepository::findByTitleAndCourseId,
                    exercise -> textExerciseImportService.importTextExercise(exercise, new TextExercise()));
            case QuizExercise quizExercise -> importOrLoadExercise(quizExercise, course.getId(), quizExerciseRepository::findByTitleAndCourseId, exercise -> {
                try {
                    return quizExerciseImportService.importQuizExercise(exercise, new QuizExercise(), null);
                }
                catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            default -> throw new IllegalStateException("Unexpected value: " + sourceExercise);
        };
    }

    private Exercise importOrLoadProgrammingExercise(ProgrammingExercise programmingExercise, Course course) throws JsonProcessingException {
        Optional<ProgrammingExercise> foundByTitle = programmingExerciseRepository.findByTitleAndCourseId(programmingExercise.getTitle(), course.getId());
        Optional<ProgrammingExercise> foundByShortName = programmingExerciseRepository.findByShortNameAndCourseId(programmingExercise.getShortName(), course.getId());

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
            return programmingExerciseImportService.importProgrammingExercise(programmingExercise, new ProgrammingExercise(), false, false, false);
        }
    }

    private <E extends Exercise> Exercise importOrLoadExercise(E exercise, long courseId, BiFunction<String, Long, Optional<E>> findFunction, Function<E, E> importFunction) {
        Optional<E> foundByTitle = findFunction.apply(exercise.getTitle(), courseId);
        return foundByTitle.orElse(importFunction.apply(exercise));
    }

    private void importOrLoadLectureUnits(Set<CourseCompetency> sourceCourseCompetencies, Map<Long, CompetencyWithTailRelationDTO> idToImportedCompetency,
            Course courseToImportInto, Set<Lecture> importedLectures, Set<LectureUnit> importedLectureUnits) {
        for (CourseCompetency sourceCourseCompetency : sourceCourseCompetencies) {
            for (LectureUnit sourceLectureUnit : sourceCourseCompetency.getLectureUnits()) {
                Lecture sourceLecture = sourceLectureUnit.getLecture();

                Optional<Lecture> foundLecture = lectureRepository.findByTitleAndCourseId(sourceLecture.getTitle(), courseToImportInto.getId());
                Lecture importedLecture = foundLecture.orElseGet(() -> lectureImportService.importLecture(sourceLecture, courseToImportInto));
                importedLectures.add(importedLecture);

                Optional<LectureUnit> foundLectureUnit = lectureUnitRepository.findByNameAndCourseId(sourceLectureUnit.getName(), courseToImportInto.getId());
                LectureUnit importedLectureUnit = foundLectureUnit.orElseGet(() -> lectureUnitImportService.importLectureUnit(sourceLectureUnit, importedLecture));
                importedLectureUnits.add(importedLectureUnit);

                importedLectureUnit.getCompetencies().add(idToImportedCompetency.get(sourceCourseCompetency.getId()).competency());
                idToImportedCompetency.get(sourceCourseCompetency.getId()).competency().getLectureUnits().add(importedLectureUnit);
            }
        }
    }

    private void setAllDates(Set<Exercise> importedExercises, Set<Lecture> importedLectures, Set<LectureUnit> importedLectureUnits,
            Set<CourseCompetency> importedCourseCompetencies, ZonedDateTime referenceDate, boolean isReleaseDate) {
        long timeOffset = determineTimeOffset(importedExercises, importedLectures, importedLectureUnits, importedCourseCompetencies, referenceDate, isReleaseDate);
        if (timeOffset == 0) {
            return;
        }

        for (Exercise exercise : importedExercises) {
            setAllExerciseDates(exercise, timeOffset);
        }
        for (Lecture lecture : importedLectures) {
            setAllLectureDates(lecture, timeOffset);
        }
        for (LectureUnit lectureUnit : importedLectureUnits) {
            setAllLectureUnitDates(lectureUnit, timeOffset);
        }
        for (CourseCompetency competency : importedCourseCompetencies) {
            setAllCompetencyDates(competency, timeOffset);
        }
    }

    private long determineTimeOffset(Set<Exercise> importedExercises, Set<Lecture> importedLectures, Set<LectureUnit> importedLectureUnits,
            Set<CourseCompetency> importedCourseCompetencies, ZonedDateTime referenceDate, boolean isReleaseDate) {
        Optional<ZonedDateTime> earliestTime;

        if (isReleaseDate) {
            Stream<ZonedDateTime> exerciseDates = importedExercises.stream().map(Exercise::getReleaseDate);
            Stream<ZonedDateTime> lectureDates = importedLectures.stream().map(Lecture::getVisibleDate);
            Stream<ZonedDateTime> lectureUnitDates = importedLectureUnits.stream().map(LectureUnit::getReleaseDate);
            earliestTime = Stream.concat(exerciseDates, Stream.concat(lectureDates, lectureUnitDates)).min(Comparator.naturalOrder());
        }
        else {
            Stream<ZonedDateTime> exerciseDates = importedExercises.stream().map(Exercise::getDueDate);
            Stream<ZonedDateTime> lectureDates = importedLectures.stream().map(Lecture::getEndDate);
            Stream<ZonedDateTime> competencyDates = importedCourseCompetencies.stream().map(CourseCompetency::getSoftDueDate);
            earliestTime = Stream.concat(exerciseDates, Stream.concat(lectureDates, competencyDates)).min(Comparator.naturalOrder());
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
