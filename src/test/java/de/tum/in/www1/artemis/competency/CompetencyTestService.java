package de.tum.in.www1.artemis.competency;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Competency;
import de.tum.in.www1.artemis.domain.Course;

@Service
public class CompetencyTestService {

    public Competency createCompetency(Course course) {
        Competency competency = new Competency();
        competency.setTitle("Example Competency");
        competency.setDescription("Magna pars studiorum, prodita quaerimus.");
        competency.setCourse(course);
        return competencyRepo.save(competency);
    }
}
