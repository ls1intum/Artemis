package de.tum.cit.aet.artemis.lecture.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.core.exception.NoUniqueQueryException;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;

/**
 * API for managing lectures.
 */
@Profile(PROFILE_CORE)
@Controller
@Lazy
public class LectureRepositoryApi extends AbstractLectureApi {

    private final LectureRepository lectureRepository;

    public LectureRepositoryApi(LectureRepository lectureRepository) {
        this.lectureRepository = lectureRepository;
    }

    public Optional<Lecture> findById(Long lectureId) {
        return lectureRepository.findById(lectureId);
    }

    public Lecture findByIdElseThrow(long lectureId) {
        return lectureRepository.findByIdElseThrow(lectureId);
    }

    public List<Lecture> findAllById(Iterable<Long> lectureIds) {
        return lectureRepository.findAllById(lectureIds);
    }

    public Set<Lecture> findAllByCourseId(long courseId) {
        return lectureRepository.findAllByCourseId(courseId);
    }

    public Lecture findByIdWithLectureUnitsElseThrow(Long lectureId) {
        return lectureRepository.findByIdWithLectureUnitsElseThrow(lectureId);
    }

    public Optional<Lecture> findUniqueLecturesByTitleAndCourseIdWithLectureUnitsElseThrow(String title, long courseId) throws NoUniqueQueryException {
        return lectureRepository.findUniqueLecturesByTitleAndCourseIdWithLectureUnitsElseThrow(title, courseId);
    }

    public long countByCourseId(long courseId) {
        return lectureRepository.countByCourse_Id(courseId);
    }

    public String getLectureTitle(long lectureId) {
        return lectureRepository.getLectureTitle(lectureId);
    }

    public void saveAll(Collection<Lecture> lectures) {
        lectureRepository.saveAll(lectures);
    }

    public Set<Lecture> findAllByCourseIdWithEagerLectureUnits(long courseId) {
        return lectureRepository.findAllByCourseIdWithEagerLectureUnits(courseId);
    }

    public Set<Lecture> findAllVisibleByCourseIdWithEagerLectureUnits(long courseId, ZonedDateTime now) {
        return lectureRepository.findAllVisibleByCourseIdWithEagerLectureUnits(courseId, now);
    }
}
