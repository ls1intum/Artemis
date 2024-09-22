package de.tum.cit.aet.artemis.atlas.api;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.validation.constraints.NotNull;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.atlas.domain.LearningObject;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRepository;
import de.tum.cit.aet.artemis.atlas.service.competency.CompetencyProgressService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participant;

@Controller
public class CompetencyProgressApi extends AbstractAtlasApi {

    private final Optional<CompetencyProgressService> optionalCompetencyProgressService;

    private final Optional<CompetencyRepository> optionalCompetencyRepository;

    public CompetencyProgressApi(Environment environment, Optional<CompetencyProgressService> optionalCompetencyProgressService,
            Optional<CompetencyRepository> optionalCompetencyRepository) {
        super(environment);
        this.optionalCompetencyProgressService = optionalCompetencyProgressService;
        this.optionalCompetencyRepository = optionalCompetencyRepository;
    }

    public void updateProgressByLearningObjectForParticipantAsync(LearningObject learningObject, @NotNull Participant participant) {
        optionalCompetencyProgressService.ifPresent(service -> updateProgressByLearningObjectForParticipantAsync(learningObject, participant));
    }

    public void updateProgressByLearningObjectAsync(LearningObject learningObject) {
        optionalCompetencyProgressService.ifPresent(service -> updateProgressByLearningObjectAsync(learningObject));
    }

    public void updateProgressByCompetencyAsync(CourseCompetency competency) {
        optionalCompetencyProgressService.ifPresent(service -> service.updateProgressByCompetencyAsync(competency));
    }

    public void updateProgressForUpdatedLearningObjectAsync(LearningObject originalLearningObject, Optional<LearningObject> updatedLearningObject) {
        optionalCompetencyProgressService.ifPresent(service -> updateProgressForUpdatedLearningObjectAsync(originalLearningObject, updatedLearningObject));
    }

    public void updateProgressByLearningObjectSync(LearningObject learningObject, Set<User> users) {
        optionalCompetencyProgressService.ifPresent(service -> service.updateProgressByLearningObjectSync(learningObject, users));
    }

    public void updateProgressForCourse(List<Course> activeCourses) {
        CompetencyProgressService competencyProgressService = getOrThrow(optionalCompetencyProgressService);
        CompetencyRepository competencyRepository = getOrThrow(optionalCompetencyRepository);

        activeCourses.forEach(course -> {
            List<Competency> competencies = competencyRepository.findByCourseIdOrderById(course.getId());
            // Asynchronously update the progress for each competency
            competencies.forEach(competencyProgressService::updateProgressByCompetencyAsync);
        });
    }
}
