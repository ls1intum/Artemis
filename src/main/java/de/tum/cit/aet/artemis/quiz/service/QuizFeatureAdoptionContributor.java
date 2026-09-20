package de.tum.cit.aet.artemis.quiz.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.service.featureusage.FeatureAdoptionContributor;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureAdoptionEntry;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;
import de.tum.cit.aet.artemis.quiz.repository.QuizExerciseAdoptionRepository;

/**
 * Reports how quizzes are configured, one entry per quiz mode.
 */
@Profile(PROFILE_CORE)
@Component
@Lazy
public class QuizFeatureAdoptionContributor implements FeatureAdoptionContributor {

    private static final String MODULE = "quiz";

    private final QuizExerciseAdoptionRepository adoptionRepository;

    public QuizFeatureAdoptionContributor(QuizExerciseAdoptionRepository adoptionRepository) {
        this.adoptionRepository = adoptionRepository;
    }

    @Override
    public List<FeatureAdoptionEntry> collectAdoption() {
        long total = adoptionRepository.count();
        // Iterating the enum rather than listing the modes means a new mode is reported without touching this class.
        return Arrays.stream(QuizMode.values())
                .map(quizMode -> new FeatureAdoptionEntry(MODULE, "mode/" + quizMode.name().toLowerCase(Locale.ROOT), adoptionRepository.countByQuizMode(quizMode), total))
                .toList();
    }
}
