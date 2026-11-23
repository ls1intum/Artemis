package de.tum.cit.aet.artemis.lecture.test_repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentVideoUnitRepository;

@Lazy
@Repository
@Primary
public interface AttachmentVideoUnitTestRepository extends AttachmentVideoUnitRepository {

    @EntityGraph(type = LOAD, attributePaths = "competencyLinks")
    AttachmentVideoUnit findOneWithCompetencyLinksById(long attachmentVideoUnitId);

    @Transactional // ok because of modifying query
    @Modifying
    @Query("UPDATE AttachmentVideoUnit unit SET unit.videoSource = :videoSource WHERE unit.id = :attachmentVideoUnitId")
    void overwriteVideoSource(@Param("attachmentVideoUnitId") long attachmentVideoUnitId, @Param("videoSource") String videoSource);

    /**
     * Saves a unit including its video URL, which a save does not write for a unit that exists already (see AttachmentVideoUnit#videoSource).
     *
     * @param unit the unit
     * @return the saved unit
     */
    default AttachmentVideoUnit saveWithVideoSource(AttachmentVideoUnit unit) {
        AttachmentVideoUnit saved = save(unit);
        overwriteVideoSource(saved.getId(), unit.getVideoSource());
        saved.setVideoSource(unit.getVideoSource());
        return saved;
    }
}
