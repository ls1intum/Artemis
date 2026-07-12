package de.tum.cit.aet.artemis.lecture.repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.domain.IrisLectureUnitSyncState;

@Conditional(LectureEnabled.class)
@Lazy
@Repository
public interface IrisLectureUnitSyncStateRepository extends ArtemisJpaRepository<IrisLectureUnitSyncState, Long> {

    Optional<IrisLectureUnitSyncState> findByLectureUnitId(Long lectureUnitId);

    List<IrisLectureUnitSyncState> findTop50ByStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(List<String> statuses, ZonedDateTime now);
}
