package de.tum.cit.aet.artemis.atlas.api;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.LearningObject;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyProgressRepository;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRepository;
import de.tum.cit.aet.artemis.atlas.service.competency.CompetencyProgressService;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.export.CompetencyProgressExportDTO;
import de.tum.cit.aet.artemis.core.dto.export.UserCompetencyProgressExportDTO;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participant;

@Controller
@Conditional(AtlasEnabled.class)
@Lazy
public class CompetencyProgressApi extends AbstractAtlasApi {

    private final CompetencyProgressService competencyProgressService;

    private final CompetencyRepository competencyRepository;

    private final CompetencyProgressRepository competencyProgressRepository;

    public CompetencyProgressApi(CompetencyProgressService competencyProgressService, CompetencyRepository competencyRepository,
            CompetencyProgressRepository competencyProgressRepository) {
        this.competencyProgressService = competencyProgressService;
        this.competencyRepository = competencyRepository;
        this.competencyProgressRepository = competencyProgressRepository;
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

    public void updateProgressForUpdatedLearningObjectAsyncWithOriginalCompetencyIds(Set<Long> originalCompetencyIds, LearningObject updatedLearningObject) {
        competencyProgressService.updateProgressForUpdatedLearningObjectAsyncWithOriginalCompetencyIds(originalCompetencyIds, updatedLearningObject);
    }

    public void updateProgressByLearningObjectSync(LearningObject learningObject, Set<User> users) {
        competencyProgressService.updateProgressByLearningObjectSync(learningObject, users);
    }

    public long countCompetencyProgressByCourseId(long courseId) {
        return competencyProgressRepository.countByCourseId(courseId);
    }

    public void deleteAllByCourseId(long courseId) {
        competencyRepository.deleteAllByCourseId(courseId);
    }

    /**
     * Find all competency progress records for a course for export.
     *
     * @param courseId the id of the course
     * @return list of competency progress export DTOs
     */
    public List<CompetencyProgressExportDTO> findAllForExportByCourseId(long courseId) {
        return competencyProgressRepository.findAllForExportByCourseId(courseId);
    }

    /**
     * Find all competency progress records for a user for GDPR data export.
     *
     * @param userId the id of the user
     * @return list of user competency progress export DTOs with course information
     */
    public List<UserCompetencyProgressExportDTO> findAllForExportByUserId(long userId) {
        return competencyProgressRepository.findAllForExportByUserId(userId);
    }
}
