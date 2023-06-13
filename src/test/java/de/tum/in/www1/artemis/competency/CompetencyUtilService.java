package de.tum.in.www1.artemis.competency;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Competency;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.repository.CompetencyRepository;

@Service
public class CompetencyUtilService {

    @Autowired
    private CompetencyRepository competencyRepo;

    public Competency createCompetency(Course course) {
        Competency competency = new Competency();
        competency.setTitle("Example Competency");
        competency.setDescription("Magna pars studiorum, prodita quaerimus.");
        competency.setCourse(course);
        return competencyRepo.save(competency);
    }
}
