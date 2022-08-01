package de.tum.in.www1.artemis.service;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.repository.TutorialGroupRepository;

@Service
public class TutorialGroupService {

    private final TutorialGroupRepository tutorialGroupRepository;

    public TutorialGroupService(TutorialGroupRepository tutorialGroupRepository) {
        this.tutorialGroupRepository = tutorialGroupRepository;
    }

}
