package de.tum.in.www1.artemis.repository;

import java.util.List;

import javax.persistence.QueryHint;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Attachment;

/**
 * Spring Data repository for the Attachment entity.
 */
@SuppressWarnings("unused")
@Repository
public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    @Query("select a FROM Attachment a WHERE a.lecture.id =  :#{#lectureId}")
    @QueryHints(value = { @QueryHint(name = "org.hibernate.cacheable", value = "true") })
    List<Attachment> findAllByLectureId(@Param("lectureId") Long lectureId);

}
