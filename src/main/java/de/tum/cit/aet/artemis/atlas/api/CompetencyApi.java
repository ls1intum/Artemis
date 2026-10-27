package de.tum.cit.aet.artemis.atlas.api;

import java.util.Collection;
import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.LearningObject;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRepository;
import de.tum.cit.aet.artemis.atlas.service.competency.CompetencyDemoService;
import de.tum.cit.aet.artemis.atlas.service.competency.CompetencyService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;

@Conditional(AtlasEnabled.class)
@Controller
@Lazy
public class CompetencyApi extends AbstractAtlasApi {

    private final CompetencyService competencyService;

    private final CompetencyRepository competencyRepository;

    private final CompetencyDemoService competencyDemoService;

    public CompetencyApi(CompetencyService competencyService, CompetencyRepository competencyRepository, CompetencyDemoService competencyDemoService) {
        this.competencyService = competencyService;
        this.competencyRepository = competencyRepository;
        this.competencyDemoService = competencyDemoService;
    }

    /**
     * Creates the competencies of the demo course with their links and relations, enables its learning paths and updates the progress of the demo students, see
     * {@link CompetencyDemoService#createDemo}.
     *
     * @param course        the demo course.
     * @param architecture  the exercises and lecture units about software architecture.
     * @param algorithms    the exercises and lecture units about algorithms and their complexity.
     * @param modeling      the exercises and lecture units about object-oriented modeling.
     * @param communication the exercises about communicating design decisions.
     * @param students      the demo students, whose progress is updated.
     */
    public void createDemo(Course course, Collection<? extends LearningObject> architecture, Collection<? extends LearningObject> algorithms,
            Collection<? extends LearningObject> modeling, Collection<? extends LearningObject> communication, List<User> students) {
        competencyDemoService.createDemo(course, architecture, algorithms, modeling, communication, students);
    }

    public void addCompetencyLinksToExerciseUnits(Lecture lecture) {
        competencyService.addCompetencyLinksToExerciseUnits(lecture);
    }

    public Competency loadCompetency(Long competencyId) {
        return competencyService.loadCompetency(competencyId);
    }

    public long countByCourseId(long courseId) {
        return competencyRepository.countByCourseId(courseId);
    }
}
