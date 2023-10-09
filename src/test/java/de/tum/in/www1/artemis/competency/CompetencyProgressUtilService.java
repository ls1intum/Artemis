package de.tum.in.www1.artemis.competency;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.competency.Competency;
import de.tum.in.www1.artemis.domain.competency.CompetencyProgress;
import de.tum.in.www1.artemis.repository.CompetencyProgressRepository;

/**
 * Service responsible for initializing the database with specific testdata related to competency progress for use in integration tests.
 */
@Service
public class CompetencyProgressUtilService {

    @Autowired
    private CompetencyProgressRepository competencyProgressRepository;

    /**
     * Creates Competency progress for given competency and user.
     *
     * @param competency the competency the progress is associated to
     * @param user       the user for whom the progress should be created
     * @param progress   the progress that should be stored
     * @param confidence the confidence level that should be stored
     * @return the persisted competency progress entity
     */
    public CompetencyProgress createCompetencyProgress(Competency competency, User user, double progress, double confidence) {
        CompetencyProgress competencyProgress = new CompetencyProgress();
        competencyProgress.setCompetency(competency);
        competencyProgress.setUser(user);
        competencyProgress.setProgress(progress);
        competencyProgress.setConfidence(confidence);
        return competencyProgressRepository.save(competencyProgress);
    }

}
