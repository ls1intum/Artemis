package de.tum.cit.aet.artemis.tutorialgroups.test_repository;

import java.util.Optional;

import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSchedule;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupScheduleRepository;

public interface TutorialGroupScheduleTestRepository extends TutorialGroupScheduleRepository {

    Optional<TutorialGroupSchedule> findByTutorialGroupId(Long tutorialGroupId);

}
