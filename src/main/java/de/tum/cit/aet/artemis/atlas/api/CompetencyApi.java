package de.tum.cit.aet.artemis.atlas.api;

import java.util.Collection;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRepository;
import de.tum.cit.aet.artemis.atlas.service.competency.CompetencyDemoService;
import de.tum.cit.aet.artemis.atlas.service.competency.CompetencyService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;

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

    public void createDemo(Course course, Collection<LectureUnit> lectureUnits) {
        competencyDemoService.createDemo(course, lectureUnits);
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
