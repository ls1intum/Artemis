package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.service.featureusage.FeatureAdoptionContributor;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureAdoptionEntry;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseAdoptionRepository;

/**
 * Reports how widely the optional features of programming exercises are switched on.
 * <p>
 * This is the module the whole analysis was motivated by: programming exercises have many sub features and no way to tell,
 * so far, which of them instructors actually turn on.
 */
@Profile(PROFILE_CORE)
@Component
@Lazy
public class ProgrammingFeatureAdoptionContributor implements FeatureAdoptionContributor {

    private static final String MODULE = "programming";

    private final ProgrammingExerciseAdoptionRepository adoptionRepository;

    public ProgrammingFeatureAdoptionContributor(ProgrammingExerciseAdoptionRepository adoptionRepository) {
        this.adoptionRepository = adoptionRepository;
    }

    @Override
    public List<FeatureAdoptionEntry> collectAdoption() {
        long total = adoptionRepository.count();
        return List.of(new FeatureAdoptionEntry(MODULE, "static-code-analysis", adoptionRepository.countWithStaticCodeAnalysis(), total),
                new FeatureAdoptionEntry(MODULE, "online-editor", adoptionRepository.countWithOnlineEditor(), total),
                new FeatureAdoptionEntry(MODULE, "offline-ide", adoptionRepository.countWithOfflineIde(), total),
                new FeatureAdoptionEntry(MODULE, "online-ide", adoptionRepository.countWithOnlineIde(), total),
                new FeatureAdoptionEntry(MODULE, "released-tests", adoptionRepository.countWithReleasedTests(), total),
                new FeatureAdoptionEntry(MODULE, "submission-policy", adoptionRepository.countWithSubmissionPolicy(), total),
                new FeatureAdoptionEntry(MODULE, "auxiliary-repositories", adoptionRepository.countWithAuxiliaryRepositories(), total));
    }
}
