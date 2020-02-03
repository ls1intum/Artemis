package de.tum.in.www1.artemis.service;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.repository.TeamRepository;

@Service
public class TeamService {

    private TeamRepository teamRepository;

    private final AuthorizationCheckService authCheckService;

    public TeamService(TeamRepository teamRepository, AuthorizationCheckService authCheckService) {
        this.teamRepository = teamRepository;
        this.authCheckService = authCheckService;
    }

}
