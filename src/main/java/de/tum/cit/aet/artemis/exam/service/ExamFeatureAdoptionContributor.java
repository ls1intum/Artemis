package de.tum.cit.aet.artemis.exam.service;

import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.service.featureusage.FeatureAdoptionContributor;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureAdoptionEntry;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.repository.ExamAdoptionRepository;

/**
 * Reports how widely the optional features of exams are switched on.
 * <p>
 * Conditional on the exam module, so a deployment that runs without exams contributes nothing rather than reporting zeros
 * that would read as "exams exist but nobody uses them".
 */
@Conditional(ExamEnabled.class)
@Component
@Lazy
public class ExamFeatureAdoptionContributor implements FeatureAdoptionContributor {

    private static final String MODULE = "exam";

    private final ExamAdoptionRepository adoptionRepository;

    public ExamFeatureAdoptionContributor(ExamAdoptionRepository adoptionRepository) {
        this.adoptionRepository = adoptionRepository;
    }

    @Override
    public List<FeatureAdoptionEntry> collectAdoption() {
        long total = adoptionRepository.count();
        return List.of(new FeatureAdoptionEntry(MODULE, "test-exam", adoptionRepository.countTestExams(), total),
                new FeatureAdoptionEntry(MODULE, "attendance-check", adoptionRepository.countWithAttendanceCheck(), total));
    }
}
