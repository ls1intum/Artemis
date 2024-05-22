package de.tum.in.www1.artemis.service.competency;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.competency.CompetencyJOL;
import de.tum.in.www1.artemis.repository.CompetencyRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.competency.CompetencyJOLRepository;

/**
 * Service Implementation for managing CompetencyJOL.
 */
@Profile(PROFILE_CORE)
@Service
public class CompetencyJOLService {

    private final CompetencyJOLRepository competencyJOLRepository;

    private final CompetencyRepository competencyRepository;

    private final UserRepository userRepository;

    public CompetencyJOLService(CompetencyJOLRepository competencyJOLRepository, CompetencyRepository competencyRepository, UserRepository userRepository) {
        this.competencyJOLRepository = competencyJOLRepository;
        this.competencyRepository = competencyRepository;
        this.userRepository = userRepository;
    }

    public void setJudgementOfLearning(long competencyId, long userId, int jolValue) {
        final var competencyJOL = competencyJOLRepository.findByCompetencyIdAndUserId(competencyId, userId);

        // If the competencyJOL already exists, update the value
        if (competencyJOL.isPresent()) {
            competencyJOL.get().setValue(jolValue);
            competencyJOLRepository.save(competencyJOL.get());
            return;
        }

        // If the competencyJOL does not exist, create a new one
        final var jol = createCompetencyJOL(competencyId, userId, jolValue);
        competencyJOLRepository.save(jol);
    }

    public CompetencyJOL createCompetencyJOL(long competencyId, long userId, int jolValue) {
        final var jol = new CompetencyJOL();
        jol.setCompetency(competencyRepository.findById(competencyId).orElseThrow());
        jol.setUser(userRepository.findById(userId).orElseThrow());
        jol.setValue(jolValue);
        return jol;
    }
}
