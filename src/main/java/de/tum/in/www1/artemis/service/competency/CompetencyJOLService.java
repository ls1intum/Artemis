package de.tum.in.www1.artemis.service.competency;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.competency.CompetencyJOL;
import de.tum.in.www1.artemis.repository.CompetencyRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.competency.CompetencyJOLRepository;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

/**
 * Service Implementation for managing CompetencyJOL.
 */
@Profile(PROFILE_CORE)
@Service
public class CompetencyJOLService {

    private final String ENTITY_NAME = "CompetencyJOL";

    private final CompetencyJOLRepository competencyJOLRepository;

    private final CompetencyRepository competencyRepository;

    private final UserRepository userRepository;

    public CompetencyJOLService(CompetencyJOLRepository competencyJOLRepository, CompetencyRepository competencyRepository, UserRepository userRepository) {
        this.competencyJOLRepository = competencyJOLRepository;
        this.competencyRepository = competencyRepository;
        this.userRepository = userRepository;
    }

    private static boolean isJolValueValid(int jolValue) {
        return 1 <= jolValue && jolValue <= 5;
    }

    /**
     * Set the judgement of learning value for a user and a competency.
     *
     * @param competencyId the id of the competency
     * @param userId       the id of the user
     * @param jolValue     the judgement of learning value
     */
    public void setJudgementOfLearning(long competencyId, long userId, int jolValue) {
        if (!isJolValueValid(jolValue)) {
            throw new BadRequestAlertException("Invalid judgement of learning value", ENTITY_NAME, "invalidJolValue");
        }

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

    /**
     * Create a new CompetencyJOL.
     *
     * @param competencyId the id of the competency
     * @param userId       the id of the user
     * @param jolValue     the judgement of learning value
     * @return the created CompetencyJOL (not persisted)
     */
    public CompetencyJOL createCompetencyJOL(long competencyId, long userId, int jolValue) {
        final var jol = new CompetencyJOL();
        jol.setCompetency(competencyRepository.findById(competencyId).orElseThrow());
        jol.setUser(userRepository.findById(userId).orElseThrow());
        jol.setValue(jolValue);
        return jol;
    }
}
