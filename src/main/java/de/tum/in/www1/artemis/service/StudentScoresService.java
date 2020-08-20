package de.tum.in.www1.artemis.service;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.repository.StudentScoresRepository;

@Service
public class StudentScoresService {

    private final StudentScoresRepository studentScoresRepository;

    private final UserService userService;

    public StudentScoresService(StudentScoresRepository studentScoresRepository, UserService userService) {
        this.studentScoresRepository = studentScoresRepository;
        this.userService = userService;
    }
}
