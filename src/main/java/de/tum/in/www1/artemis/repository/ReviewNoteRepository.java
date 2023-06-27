package de.tum.in.www1.artemis.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.ReviewNote;

public interface ReviewNoteRepository extends JpaRepository<ReviewNote, Long> {

    default ReviewNote setReviewNoteForResult(final String note, final Result result) {
        ReviewNote reviewNote = Optional.of(result.getReviewNote()).orElseGet(ReviewNote::new);
        reviewNote.setCreator(result.getAssessor());
        reviewNote.setNote(note);
        return save(reviewNote);
    }
}
