package de.tum.in.www1.artemis.competency;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.competency.Prerequisite;
import de.tum.in.www1.artemis.repository.PrerequisiteRepository;
import de.tum.in.www1.artemis.web.rest.dto.competency.PrerequisiteRequestDTO;

/**
 * Service responsible for initializing the database with specific test data related to prerequisites for use in integration tests.
 */
@Service
public class PrerequisiteUtilService {

    @Autowired
    private PrerequisiteRepository prerequisiteRepository;

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

    /**
     * Creates a PrerequisiteRequestDTO from a prerequisite
     *
     * @param prerequisite the prerequisite to convert
     * @return the created PrerequisiteRequestDTO
     */
    public PrerequisiteRequestDTO prerequisiteToRequestDTO(Prerequisite prerequisite) {
        return new PrerequisiteRequestDTO(prerequisite.getTitle(), prerequisite.getDescription(), prerequisite.getTaxonomy(), prerequisite.getSoftDueDate(),
                prerequisite.getMasteryThreshold(), prerequisite.isOptional());
    }
}
