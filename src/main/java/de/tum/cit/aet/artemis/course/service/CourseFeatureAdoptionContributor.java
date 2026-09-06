package de.tum.cit.aet.artemis.course.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.service.featureusage.FeatureAdoptionContributor;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureAdoptionEntry;
import de.tum.cit.aet.artemis.course.domain.CourseInformationSharingConfiguration;
import de.tum.cit.aet.artemis.course.repository.CourseAdoptionRepository;

/**
 * Reports how widely the optional features of a course are switched on.
 */
@Profile(PROFILE_CORE)
@Component
@Lazy
public class CourseFeatureAdoptionContributor implements FeatureAdoptionContributor {

    private static final String MODULE = "course";

    private final CourseAdoptionRepository adoptionRepository;

    public CourseFeatureAdoptionContributor(CourseAdoptionRepository adoptionRepository) {
        this.adoptionRepository = adoptionRepository;
    }

    @Override
    public List<FeatureAdoptionEntry> collectAdoption() {
        long total = adoptionRepository.count();
        long withCommunication = adoptionRepository.countWithCommunication(CourseInformationSharingConfiguration.DISABLED);
        return List.of(new FeatureAdoptionEntry(MODULE, "communication", withCommunication, total),
                new FeatureAdoptionEntry(MODULE, "learning-paths", adoptionRepository.countWithLearningPaths(), total),
                new FeatureAdoptionEntry(MODULE, "self-enrollment", adoptionRepository.countWithEnrollment(), total),
                new FeatureAdoptionEntry(MODULE, "online-course", adoptionRepository.countOnlineCourses(), total),
                new FeatureAdoptionEntry(MODULE, "athena-feedback", adoptionRepository.countWithAthenaFeedbackEnabled(), total),
                new FeatureAdoptionEntry(MODULE, "test-course", adoptionRepository.countTestCourses(), total));
    }
}
