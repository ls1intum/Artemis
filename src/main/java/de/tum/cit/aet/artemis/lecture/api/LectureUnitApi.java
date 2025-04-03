package de.tum.cit.aet.artemis.lecture.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.validation.constraints.NotNull;

import org.hibernate.NonUniqueResultException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.atlas.dto.metrics.LectureUnitInformationDTO;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.lecture.domain.ExerciseUnit;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnitCompletion;
import de.tum.cit.aet.artemis.lecture.repository.ExerciseUnitRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitCompletionRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitMetricsRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitRepository;
import de.tum.cit.aet.artemis.lecture.service.LectureUnitImportService;
import de.tum.cit.aet.artemis.lecture.service.LectureUnitService;

/**
 * API for managing lecture/exercise units.
 */
@Profile(PROFILE_CORE)
@Controller
public class LectureUnitApi extends AbstractLectureApi {

    private final LectureUnitService lectureUnitService;

    private final LectureUnitImportService lectureUnitImportService;

    private final LectureUnitRepository lectureUnitRepository;

    private final LectureUnitCompletionRepository lectureUnitCompletionRepository;

    private final LectureUnitMetricsRepository lectureUnitMetricsRepository;

    private final ExerciseUnitRepository exerciseUnitRepository;

    public LectureUnitApi(LectureUnitService lectureUnitService, LectureUnitImportService lectureUnitImportService, LectureUnitRepository lectureUnitRepository,
            LectureUnitCompletionRepository lectureUnitCompletionRepository, LectureUnitMetricsRepository lectureUnitMetricsRepository,
            ExerciseUnitRepository exerciseUnitRepository) {
        this.lectureUnitService = lectureUnitService;
        this.lectureUnitImportService = lectureUnitImportService;
        this.lectureUnitRepository = lectureUnitRepository;
        this.lectureUnitCompletionRepository = lectureUnitCompletionRepository;
        this.lectureUnitMetricsRepository = lectureUnitMetricsRepository;
        this.exerciseUnitRepository = exerciseUnitRepository;
    }

    public void setCompletedForAllLectureUnits(List<? extends LectureUnit> lectureUnits, @NotNull User user, boolean completed) {
        lectureUnitService.setCompletedForAllLectureUnits(lectureUnits, user, completed);
    }

    public Set<LectureUnitCompletion> findByLectureUnitsAndUserId(Collection<? extends LectureUnit> lectureUnits, Long userId) {
        return lectureUnitCompletionRepository.findByLectureUnitsAndUserId(lectureUnits, userId);
    }

    public Set<LectureUnitInformationDTO> findAllLectureUnitInformationByCourseId(long courseId) {
        return lectureUnitMetricsRepository.findAllLectureUnitInformationByCourseId(courseId);
    }

    public Set<Long> findAllCompletedLectureUnitIdsForUserByLectureUnitIds(long userId, Set<Long> lectureUnitIds) {
        return lectureUnitMetricsRepository.findAllCompletedLectureUnitIdsForUserByLectureUnitIds(userId, lectureUnitIds);
    }

    public Set<User> findCompletedUsersForLectureUnit(LectureUnit lectureUnit) {
        return lectureUnitCompletionRepository.findCompletedUsersForLectureUnit(lectureUnit);
    }

    public Optional<LectureUnit> findByNameAndLectureTitleAndCourseIdWithCompetencies(String name, String lectureTitle, long courseId) throws NonUniqueResultException {
        return lectureUnitRepository.findByNameAndLectureTitleAndCourseIdWithCompetencies(name, lectureTitle, courseId);
    }

    public LectureUnit save(LectureUnit lectureUnit) {
        return lectureUnitRepository.save(lectureUnit);
    }

    public LectureUnit importLectureUnit(LectureUnit sourceLectureUnit) {
        return lectureUnitImportService.importLectureUnit(sourceLectureUnit);
    }

    public void removeLectureUnitFromExercise(long exerciseId) {
        List<ExerciseUnit> exerciseUnits = this.exerciseUnitRepository.findByIdWithCompetenciesBidirectional(exerciseId);
        for (ExerciseUnit exerciseUnit : exerciseUnits) {
            lectureUnitService.removeLectureUnit(exerciseUnit);
        }
    }
}
