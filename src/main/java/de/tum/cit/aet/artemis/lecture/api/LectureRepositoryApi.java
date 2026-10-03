package de.tum.cit.aet.artemis.lecture.api;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.core.exception.NoUniqueQueryException;
import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;

/**
 * API for managing lectures.
 */
@Conditional(LectureEnabled.class)
@Controller
@Lazy
public class LectureRepositoryApi extends AbstractLectureApi {

    private final LectureRepository lectureRepository;

    public LectureRepositoryApi(LectureRepository lectureRepository) {
        this.lectureRepository = lectureRepository;
    }

    /**
     * Checks which of the given ids exists, for the pass that removes index rows with no backing entity.
     *
     * @param entityIds the ids to check
     * @return the subset that exists
     */
    public Set<Long> findExistingLectureIds(Collection<Long> entityIds) {
        return lectureRepository.findExistingLectureIds(entityIds);
    }

    /**
     * Walks the ids expected to be indexed, one page at a time, for the reconcile passes.
     *
     * @param afterId the id the previous page stopped at
     * @param limit   the page size
     * @return the next lecture ids in ascending order
     */
    public List<Long> findLectureIdsAfter(long afterId, int limit) {
        return lectureRepository.findLectureIdsAfter(afterId, PageRequest.ofSize(limit));
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

    public Optional<Lecture> findUniqueByTitleAndCourseIdWithLectureUnitsElseThrow(String title, long courseId) throws NoUniqueQueryException {
        return lectureRepository.findUniqueByTitleAndCourseIdWithLectureUnitsElseThrow(title, courseId);
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

    public Set<Lecture> findAllByCourseIdWithAttachmentsAndLectureUnits(long courseId) {
        return lectureRepository.findAllByCourseIdWithAttachmentsAndLectureUnits(courseId);
    }
}
