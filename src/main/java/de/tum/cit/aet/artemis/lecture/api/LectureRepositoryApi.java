package de.tum.cit.aet.artemis.lecture.api;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.core.dto.CourseContentCount;
import de.tum.cit.aet.artemis.core.exception.NoUniqueQueryException;
import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;

/**
 * API for managing lectures.
 */
@Conditional(LectureEnabled.class)
@Controller
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

    public Set<Lecture> findAllByCourseId(long courseId) {
        return lectureRepository.findAllByCourseId(courseId);
    }

    public Lecture findByIdWithLectureUnitsElseThrow(Long lectureId) {
        return lectureRepository.findByIdWithLectureUnitsElseThrow(lectureId);
    }

    public Optional<Lecture> findUniqueByTitleAndCourseIdWithLectureUnitsElseThrow(String title, long courseId) throws NoUniqueQueryException {
        return lectureRepository.findUniqueByTitleAndCourseIdWithLectureUnitsElseThrow(title, courseId);
    }

    public long countByCourseId(long courseId) {
        return lectureRepository.countByCourse_Id(courseId);
    }

    public Set<CourseContentCount> countVisibleLectures(Set<Long> courseIds, ZonedDateTime now) {
        return lectureRepository.countVisibleLectures(courseIds, now);
    }

    public String getLectureTitle(long lectureId) {
        return lectureRepository.getLectureTitle(lectureId);
    }

    public void saveAll(Collection<Lecture> lectures) {
        lectureRepository.saveAll(lectures);
    }
}
