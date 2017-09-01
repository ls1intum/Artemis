package de.tum.in.www1.exerciseapp.repository;

import de.tum.in.www1.exerciseapp.domain.SubmittedAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


/**
 * Spring Data JPA repository for the SubmittedAnswer entity.
 */
@SuppressWarnings("unused")
@Repository
public interface SubmittedAnswerRepository extends JpaRepository<SubmittedAnswer, Long> {

}
