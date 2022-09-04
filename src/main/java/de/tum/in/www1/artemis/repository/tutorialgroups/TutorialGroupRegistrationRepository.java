package de.tum.in.www1.artemis.repository.tutorialgroups;

import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroup;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupRegistration;

@Repository
public interface TutorialGroupRegistrationRepository extends JpaRepository<TutorialGroupRegistration, Long> {

    Optional<TutorialGroupRegistration> findTutorialGroupRegistrationByTutorialGroupAndStudent(TutorialGroup tutorialGroup, User student);

    Set<TutorialGroupRegistration> findAllByTutorialGroup(TutorialGroup tutorialGroup);

    void deleteAllByStudent(User student);

}
