package de.tum.cit.aet.artemis.lecture.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

import org.hibernate.NonUniqueResultException;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.atlas.dto.metrics.LectureUnitInformationDTO;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnitCompletion;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitCompletionRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitMetricsRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitRepository;

/**
 * API for managing lecture units.
 */
@Profile(PROFILE_CORE)
@Controller
@Lazy
public class LectureUnitRepositoryApi extends AbstractLectureApi {

    private final LectureUnitRepository lectureUnitRepository;

    private final LectureUnitCompletionRepository lectureUnitCompletionRepository;

    private final LectureUnitMetricsRepository lectureUnitMetricsRepository;

    public LectureUnitRepositoryApi(LectureUnitRepository lectureUnitRepository, LectureUnitCompletionRepository lectureUnitCompletionRepository,
            LectureUnitMetricsRepository lectureUnitMetricsRepository) {
        this.lectureUnitRepository = lectureUnitRepository;
        this.lectureUnitCompletionRepository = lectureUnitCompletionRepository;
        this.lectureUnitMetricsRepository = lectureUnitMetricsRepository;
    }

    public Set<LectureUnitCompletion> findByLectureUnitsAndUserId(Collection<? extends LectureUnit> lectureUnits, Long userId) {
        return lectureUnitCompletionRepository.findByLectureUnitsAndUserId(lectureUnits, userId);
    }

    public Set<LectureUnitInformationDTO> findAllNormalLectureUnitInformationByCourseId(long courseId) {
        return lectureUnitMetricsRepository.findAllNormalLectureUnitInformationByCourseId(courseId);
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

    public LectureUnit findByIdElseThrow(long lectureUnitId) {
        return lectureUnitRepository.findByIdElseThrow(lectureUnitId);
    }

    public LectureUnit save(LectureUnit lectureUnit) {
        return lectureUnitRepository.save(lectureUnit);
    }
}
