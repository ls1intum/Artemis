package de.tum.in.www1.artemis.repository.tutorialgroups;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupFreeDay;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Repository
public interface TutorialGroupFreeDayRepository extends JpaRepository<TutorialGroupFreeDay, Long> {

    default TutorialGroupFreeDay findByIdElseThrow(Long tutorialGroupFreeDayId) {
        return findById(tutorialGroupFreeDayId).orElseThrow(() -> new EntityNotFoundException("TutorialGroupFreeDay", tutorialGroupFreeDayId));
    }

}
