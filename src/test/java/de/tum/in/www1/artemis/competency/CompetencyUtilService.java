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

    private Competency createCompetency(Course course, String suffix) {
        Competency competency = new Competency();
        competency.setTitle("Example Competency" + suffix);
        competency.setDescription("Magna pars studiorum, prodita quaerimus.");
        competency.setCourse(course);
        return competencyRepo.save(competency);
    }

    public Competency createCompetency(Course course) {
        return createCompetency(course, "");
    }

    public Competency[] createCompetencies(Course course, int numberOfCompetencies) {
        Competency[] competencies = new Competency[numberOfCompetencies];
        for (int i = 0; i < competencies.length; i++) {
            competencies[i] = createCompetency(course, "" + i);
        }
        return competencies;
    }
}
