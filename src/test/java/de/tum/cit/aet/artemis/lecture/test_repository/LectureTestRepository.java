package de.tum.cit.aet.artemis.lecture.test_repository;

import java.util.Optional;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;

@Repository
@Primary
public interface LectureTestRepository extends LectureRepository {

    @NotNull
    default Lecture findByIdWithAttachmentsAndLectureUnitsAndCompletionsElseThrow(long lectureId) {
        return getValueElseThrow(findByIdWithAttachmentsAndLectureUnitsAndCompletions(lectureId), lectureId);
    }

    @Query("""
            SELECT DISTINCT lecture
            FROM Lecture lecture
              LEFT JOIN FETCH lecture.attachments
              LEFT JOIN FETCH lecture.lectureUnits lu
              LEFT JOIN FETCH lu.completedUsers cu
            WHERE lecture.id = :lectureId
            """)
    Optional<Lecture> findByIdWithAttachmentsAndLectureUnitsAndCompletions(@Param("lectureId") long lectureId);
}
