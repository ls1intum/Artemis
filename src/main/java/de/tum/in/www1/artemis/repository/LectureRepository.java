package de.tum.in.www1.artemis.repository;

import java.util.List;
import java.util.Optional;

import javax.persistence.QueryHint;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Lecture;

/**
 * Spring Data repository for the Lecture entity.
 */
@SuppressWarnings("unused")
@Repository
public interface LectureRepository extends JpaRepository<Lecture, Long> {

    @Query("select l FROM Lecture l WHERE l.course.id =  :#{#courseId}")
    @QueryHints(value = { @QueryHint(name = "org.hibernate.cacheable", value = "true") })
    List<Lecture> findAllByCourseId(@Param("courseId") Long courseId);

    @QueryHints(value = { @QueryHint(name = "org.hibernate.cacheable", value = "true") })
    Optional<Lecture> findById(@Param("id") Long id);

}
