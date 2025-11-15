package de.tum.cit.aet.artemis.lecture.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.lecture.domain.ExerciseUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.repository.ExerciseUnitRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitRepository;
import de.tum.cit.aet.artemis.lecture.service.LectureUnitImportService;
import de.tum.cit.aet.artemis.lecture.service.LectureUnitService;

/**
 * API for managing lecture/exercise units.
 */
@Profile(PROFILE_CORE)
@Controller
@Lazy
public class LectureUnitApi extends AbstractLectureApi {

    private final LectureUnitService lectureUnitService;

    private final LectureUnitImportService lectureUnitImportService;

    private final LectureUnitRepository lectureUnitRepository;

    private final ExerciseUnitRepository exerciseUnitRepository;

    public LectureUnitApi(LectureUnitService lectureUnitService, LectureUnitRepository lectureUnitRepository, LectureUnitImportService lectureUnitImportService,
            ExerciseUnitRepository exerciseUnitRepository) {
        this.lectureUnitService = lectureUnitService;
        this.lectureUnitRepository = lectureUnitRepository;
        this.lectureUnitImportService = lectureUnitImportService;
        this.exerciseUnitRepository = exerciseUnitRepository;
    }

    public void setCompletedForAllLectureUnits(List<? extends LectureUnit> lectureUnits, @NotNull User user, boolean completed) {
        lectureUnitService.setCompletedForAllLectureUnits(lectureUnits, user, completed);
    }

    public LectureUnit save(LectureUnit lectureUnit) {
        return lectureUnitRepository.save(lectureUnit);
    }

    public void removeLectureUnitFromExercise(long exerciseId) {
        List<ExerciseUnit> exerciseUnits = this.exerciseUnitRepository.findByIdWithCompetenciesBidirectional(exerciseId);
        for (ExerciseUnit exerciseUnit : exerciseUnits) {
            lectureUnitService.removeLectureUnit(exerciseUnit);
        }
    }

    public LectureUnit importLectureUnit(LectureUnit sourceLectureUnit, Lecture newLecture) {
        return lectureUnitImportService.importLectureUnit(sourceLectureUnit, newLecture);
    }
}
