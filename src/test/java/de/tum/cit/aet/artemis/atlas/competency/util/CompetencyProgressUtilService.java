package de.tum.cit.aet.artemis.atlas.competency.util;

import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyProgress;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.test_repository.CompetencyProgressTestRepository;
import de.tum.cit.aet.artemis.core.domain.User;

/**
 * Service responsible for initializing the database with specific testdata related to competency progress for use in integration tests.
 */
@Lazy
@Service
@Profile(SPRING_PROFILE_TEST)
@Conditional(AtlasEnabled.class)
public class CompetencyProgressUtilService {

    @Autowired
    private CompetencyProgressTestRepository competencyProgressRepository;

    /**
     * Creates Competency progress for given competency and user.
     *
     * @param competency the competency the progress is associated to
     * @param user       the user for whom the progress should be created
     * @param progress   the progress that should be stored
     * @param confidence the confidence level that should be stored
     * @return the persisted competency progress entity
     */
    public CompetencyProgress createCompetencyProgress(CourseCompetency competency, User user, double progress, double confidence) {
        CompetencyProgress competencyProgress = new CompetencyProgress();
        competencyProgress.setCompetency(competency);
        competencyProgress.setUser(user);
        competencyProgress.setProgress(progress);
        competencyProgress.setConfidence(confidence);
        return competencyProgressRepository.save(competencyProgress);
    }

}
