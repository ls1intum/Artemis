package de.tum.cit.aet.artemis.atlas.api;

import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.LearningObject;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRepository;
import de.tum.cit.aet.artemis.atlas.service.competency.CompetencyProgressService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participant;

@Controller
@Lazy
@Conditional(AtlasEnabled.class)
public class CompetencyProgressApi extends AbstractAtlasApi {

    private final CompetencyProgressService competencyProgressService;

    private final CompetencyRepository competencyRepository;

    public CompetencyProgressApi(CompetencyProgressService competencyProgressService, CompetencyRepository competencyRepository) {
        this.competencyProgressService = competencyProgressService;
        this.competencyRepository = competencyRepository;
    }

    public void updateProgressByLearningObjectForParticipantAsync(LearningObject learningObject, Participant participant) {
        competencyProgressService.updateProgressByLearningObjectForParticipantAsync(learningObject, participant);
    }

    public void updateProgressByLearningObjectAsync(LearningObject learningObject) {
        competencyProgressService.updateProgressByLearningObjectAsync(learningObject);
    }

    public void updateProgressByCompetencyAsync(CourseCompetency competency) {
        competencyProgressService.updateProgressByCompetencyAsync(competency);
    }

    public void updateProgressForUpdatedLearningObjectAsync(LearningObject originalLearningObject, Optional<LearningObject> updatedLearningObject) {
        competencyProgressService.updateProgressForUpdatedLearningObjectAsync(originalLearningObject, updatedLearningObject);
    }

    public void updateProgressByLearningObjectSync(LearningObject learningObject, Set<User> users) {
        competencyProgressService.updateProgressByLearningObjectSync(learningObject, users);
    }

    public long countByCourse(Course course) {
        return competencyRepository.countByCourse(course);
    }

    public void deleteAll(Set<Competency> competencies) {
        competencyRepository.deleteAll(competencies);
    }
}
