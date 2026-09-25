package de.tum.cit.aet.artemis.course.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.service.featureusage.FeatureAdoptionContributor;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureAdoptionEntry;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;
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
        return List.of(new FeatureAdoptionEntry(MODULE, "communication", UserFeature.MESSAGING, withCommunication, total),
                new FeatureAdoptionEntry(MODULE, "learning-paths", UserFeature.LEARNING_PATHS, adoptionRepository.countWithLearningPaths(), total),
                new FeatureAdoptionEntry(MODULE, "self-enrollment", UserFeature.COURSE_ENROLLMENT, adoptionRepository.countWithEnrollment(), total),
                new FeatureAdoptionEntry(MODULE, "online-course", UserFeature.LTI, adoptionRepository.countOnlineCourses(), total),
                new FeatureAdoptionEntry(MODULE, "athena-feedback", UserFeature.ATHENA_FEEDBACK_SUGGESTIONS, adoptionRepository.countWithAthenaFeedbackEnabled(), total),
                new FeatureAdoptionEntry(MODULE, "test-course", UserFeature.COURSE_SETTINGS, adoptionRepository.countTestCourses(), total));
    }
}
