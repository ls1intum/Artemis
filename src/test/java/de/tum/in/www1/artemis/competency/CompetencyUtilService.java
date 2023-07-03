package de.tum.in.www1.artemis.competency;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.competency.Competency;
import de.tum.in.www1.artemis.repository.CompetencyRepository;

/**
 * Service responsible for initializing the database with specific testdata related to competencies for use in integration tests.
 */
@Service
public class CompetencyUtilService {

    @Autowired
    private CompetencyRepository competencyRepo;

    /**
     * Creates competency and links it to the course. The title of the competency will hold the specified suffix.
     *
     * @param course course the competency will be linked to
     * @param suffix the suffix that will be included in the title
     * @return the persisted competency
     */
    private Competency createCompetency(Course course, String suffix) {
        Competency competency = new Competency();
        competency.setTitle("Example Competency" + suffix);
        competency.setDescription("Magna pars studiorum, prodita quaerimus.");
        competency.setCourse(course);
        return competencyRepo.save(competency);
    }

    /**
     * Creates competency and links it to the course.
     *
     * @param course course the competency will be linked to
     * @return the persisted competency
     */
    public Competency createCompetency(Course course) {
        return createCompetency(course, "");
    }

    /**
     * Creates multiple competencies and links them to the course
     *
     * @param course               course the competencies will be linked to
     * @param numberOfCompetencies number of competencies to create
     * @return array of the persisted competencies
     */
    public Competency[] createCompetencies(Course course, int numberOfCompetencies) {
        Competency[] competencies = new Competency[numberOfCompetencies];
        for (int i = 0; i < competencies.length; i++) {
            competencies[i] = createCompetency(course, "" + i);
        }
        return competencies;
    }
}
