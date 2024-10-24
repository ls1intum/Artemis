package de.tum.cit.aet.artemis.atlas.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Profile;
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

@Profile(PROFILE_CORE)
@Controller
public class CompetencyProgressApi extends AbstractAtlasApi {

    private final Optional<CompetencyProgressService> competencyProgressService;

    private final Optional<CompetencyRepository> competencyRepository;

    public CompetencyProgressApi(Environment environment, Optional<CompetencyProgressService> competencyProgressService, Optional<CompetencyRepository> competencyRepository) {
        super(environment);
        this.competencyProgressService = competencyProgressService;
        this.competencyRepository = competencyRepository;
    }

    public void updateProgressByLearningObjectForParticipantAsync(LearningObject learningObject, @NotNull Participant participant) {
        competencyProgressService.ifPresent(service -> service.updateProgressByLearningObjectForParticipantAsync(learningObject, participant));
    }

    public void updateProgressByLearningObjectAsync(LearningObject learningObject) {
        competencyProgressService.ifPresent(service -> service.updateProgressByLearningObjectAsync(learningObject));
    }

    public void updateProgressByCompetencyAsync(CourseCompetency competency) {
        competencyProgressService.ifPresent(service -> service.updateProgressByCompetencyAsync(competency));
    }

    public void updateProgressForUpdatedLearningObjectAsync(LearningObject originalLearningObject, Optional<LearningObject> updatedLearningObject) {
        competencyProgressService.ifPresent(service -> service.updateProgressForUpdatedLearningObjectAsync(originalLearningObject, updatedLearningObject));
    }

    public void updateProgressByLearningObjectSync(LearningObject learningObject, Set<User> users) {
        competencyProgressService.ifPresent(service -> service.updateProgressByLearningObjectSync(learningObject, users));
    }

    /**
     * Updates the progress for all competencies of the given courses.
     *
     * @param activeCourses the active courses
     */
    public void updateProgressForCourses(List<Course> activeCourses) {
        if (!isActive()) {
            return;
        }

        CompetencyProgressService competencyProgressService = getOrThrow(this.competencyProgressService);
        CompetencyRepository competencyRepository = getOrThrow(this.competencyRepository);

        activeCourses.forEach(course -> {
            List<Competency> competencies = competencyRepository.findByCourseIdOrderById(course.getId());
            // Asynchronously update the progress for each competency
            competencies.forEach(competencyProgressService::updateProgressByCompetencyAsync);
        });
    }
}
