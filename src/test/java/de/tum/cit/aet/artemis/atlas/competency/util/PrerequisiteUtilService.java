package de.tum.cit.aet.artemis.atlas.competency.util;

import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.competency.Prerequisite;
import de.tum.cit.aet.artemis.atlas.test_repository.PrerequisiteTestRepository;
import de.tum.cit.aet.artemis.core.domain.Course;

/**
 * Service responsible for initializing the database with specific test data related to prerequisites for use in integration tests.
 */
@Lazy
@Service
@Profile(SPRING_PROFILE_TEST)
@Conditional(AtlasEnabled.class)
public class PrerequisiteUtilService {

    @Autowired
    private PrerequisiteTestRepository prerequisiteRepository;

    /**
     * Creates and saves a Prerequisite competency for the given Course.
     *
     * @param course The Course the Prerequisite belongs to
     * @return The created Prerequisite
     */
    public Prerequisite createPrerequisite(Course course) {
        Prerequisite prerequisite = new Prerequisite();
        prerequisite.setTitle("Example Prerequisite");
        prerequisite.setDescription("Magna pars studiorum, prodita quaerimus.");
        prerequisite.setCourse(course);
        prerequisite.setMasteryThreshold(42);
        return prerequisiteRepository.save(prerequisite);
    }

    /**
     * Creates and saves a Prerequisite competency for the given Course.
     *
     * @param course The Course the Prerequisite belongs to
     * @param suffix The suffix that will be added to the title of the Prerequisite
     * @return The created Prerequisite
     */
    private Prerequisite createPrerequisite(Course course, String suffix) {
        Prerequisite prerequisite = new Prerequisite();
        prerequisite.setTitle("Example Prerequisite" + suffix);
        prerequisite.setDescription("Magna pars studiorum, prodita quaerimus.");
        prerequisite.setCourse(course);
        return prerequisiteRepository.save(prerequisite);
    }

    /**
     * Creates and saves the given number of Prerequisites for the given Course.
     *
     * @param course                The Course the Prerequisites belong to
     * @param numberOfPrerequisites The number of Prerequisites to create
     * @return A list of the created Prerequisites
     */
    public List<Prerequisite> createPrerequisites(Course course, int numberOfPrerequisites) {
        var prerequisites = new ArrayList<Prerequisite>();
        for (int i = 0; i < numberOfPrerequisites; i++) {
            prerequisites.add(createPrerequisite(course, String.valueOf(i)));
        }
        return prerequisites;
    }
}
