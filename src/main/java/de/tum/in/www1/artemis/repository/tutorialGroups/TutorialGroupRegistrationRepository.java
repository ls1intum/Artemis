package de.tum.in.www1.artemis.repository.tutorialGroups;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.tutorialGroups.TutorialGroup;
import de.tum.in.www1.artemis.domain.tutorialGroups.TutorialGroupRegistration;

@Repository
public interface TutorialGroupRegistrationRepository extends JpaRepository<TutorialGroupRegistration, Long> {

    Optional<TutorialGroupRegistration> findTutorialGroupRegistrationByTutorialGroupAndStudent(TutorialGroup tutorialGroup, User student);
}
