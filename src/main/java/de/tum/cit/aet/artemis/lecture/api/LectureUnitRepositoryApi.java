package de.tum.cit.aet.artemis.lecture.api;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.hibernate.NonUniqueResultException;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnitCompletion;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitCompletionRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitRepository;

/**
 * API for managing lecture units.
 */
@Conditional(LectureEnabled.class)
@Controller
@Lazy
public class LectureUnitRepositoryApi extends AbstractLectureApi {

    private final LectureUnitRepository lectureUnitRepository;

    private final LectureUnitCompletionRepository lectureUnitCompletionRepository;

    public LectureUnitRepositoryApi(LectureUnitRepository lectureUnitRepository, LectureUnitCompletionRepository lectureUnitCompletionRepository) {
        this.lectureUnitRepository = lectureUnitRepository;
        this.lectureUnitCompletionRepository = lectureUnitCompletionRepository;
    }

    public Set<LectureUnitCompletion> findByLectureUnitsAndUserId(Collection<? extends LectureUnit> lectureUnits, Long userId) {
        return lectureUnitCompletionRepository.findByLectureUnitsAndUserId(lectureUnits, userId);
    }

    public Set<User> findCompletedUsersForLectureUnit(LectureUnit lectureUnit) {
        return lectureUnitCompletionRepository.findCompletedUsersForLectureUnit(lectureUnit);
    }

    public Optional<LectureUnit> findByNameAndLectureTitleAndCourseIdWithCompetencies(String name, String lectureTitle, long courseId) throws NonUniqueResultException {
        return lectureUnitRepository.findByNameAndLectureTitleAndCourseIdWithCompetencies(name, lectureTitle, courseId);
    }

    /**
     * Walks the ids expected to be indexed, one page at a time, for the reconcile passes.
     *
     * @param afterId the id the previous page stopped at
     * @param limit   the page size
     * @return the next indexable lecture unit ids in ascending order
     */
    public List<Long> findIndexableUnitIdsAfter(long afterId, int limit) {
        return lectureUnitRepository.findIndexableUnitIdsAfter(afterId, PageRequest.ofSize(limit));
    }

    public LectureUnit findByIdElseThrow(long lectureUnitId) {
        return lectureUnitRepository.findByIdElseThrow(lectureUnitId);
    }

    public List<LectureUnit> findAllByIdsWithLecture(Collection<Long> ids) {
        return lectureUnitRepository.findAllByIdsWithLecture(ids);
    }

    public LectureUnit save(LectureUnit lectureUnit) {
        return lectureUnitRepository.save(lectureUnit);
    }
}
