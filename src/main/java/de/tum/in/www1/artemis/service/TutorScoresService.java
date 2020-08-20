package de.tum.in.www1.artemis.service;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.repository.TutorScoresRepository;

@Service
public class TutorScoresService {

    private final TutorScoresRepository tutorScoresRepository;

    private final UserService userService;

    public TutorScoresService(TutorScoresRepository tutorScoresRepository, UserService userService) {
        this.tutorScoresRepository = tutorScoresRepository;
        this.userService = userService;
    }
}
