package de.tum.cit.aet.artemis.lecture.repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.IrisLectureUnitSyncState;

@Conditional(LectureEnabled.class)
@Lazy
@Repository
public interface IrisLectureUnitSyncStateRepository extends ArtemisJpaRepository<IrisLectureUnitSyncState, Long> {

    Optional<IrisLectureUnitSyncState> findByLectureUnitId(Long lectureUnitId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT attachmentVideoUnit FROM AttachmentVideoUnit attachmentVideoUnit WHERE attachmentVideoUnit.id = :lectureUnitId")
    Optional<AttachmentVideoUnit> findAttachmentVideoUnitForUpdateById(@Param("lectureUnitId") long lectureUnitId);

    List<IrisLectureUnitSyncState> findTop50ByStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(List<String> statuses, ZonedDateTime now);

    /**
     * Atomically creates or updates the dirty synchronization state while holding a lock on the owning lecture unit. Locking the always-existing parent row also serializes
     * concurrent first writes when no synchronization-state row exists yet.
     *
     * @param lectureUnitId  the attachment video unit id
     * @param metadataHash   the new metadata hash, or null if metadata did not change
     * @param visibilityHash the new visibility hash, or null if visibility did not change
     * @param nextRetryAt    the earliest retry time
     * @return the persisted dirty state
     */
    @Transactional
    default IrisLectureUnitSyncState markDirty(long lectureUnitId, String metadataHash, String visibilityHash, ZonedDateTime nextRetryAt) {
        if (findAttachmentVideoUnitForUpdateById(lectureUnitId).isEmpty()) {
            throw new EntityNotFoundException("AttachmentVideoUnit", lectureUnitId);
        }

        IrisLectureUnitSyncState state = findByLectureUnitId(lectureUnitId).orElseGet(() -> {
            IrisLectureUnitSyncState newState = new IrisLectureUnitSyncState();
            newState.setLectureUnitId(lectureUnitId);
            return newState;
        });
        if (metadataHash != null) {
            state.setMetadataHash(metadataHash);
        }
        if (visibilityHash != null) {
            state.setVisibilityHash(visibilityHash);
        }
        state.setStatus(IrisLectureUnitSyncState.STATUS_DIRTY);
        state.setNextRetryAt(nextRetryAt);
        return save(state);
    }
}
