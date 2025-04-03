package de.tum.cit.aet.artemis.atlas.api;

import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyLectureUnitLink;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyExerciseLinkRepository;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyLectureUnitLinkRepository;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRelationRepository;

@Controller
@Conditional(AtlasEnabled.class)
public class CompetencyRelationApi extends AbstractAtlasApi {

    private final CompetencyRelationRepository competencyRelationRepository;

    private final CompetencyExerciseLinkRepository competencyExerciseLinkRepository;

    private final CompetencyLectureUnitLinkRepository lectureUnitLinkRepository;

    public CompetencyRelationApi(CompetencyRelationRepository competencyRelationRepository, CompetencyExerciseLinkRepository competencyExerciseLinkRepository,
            CompetencyLectureUnitLinkRepository lectureUnitLinkRepository) {
        this.competencyRelationRepository = competencyRelationRepository;
        this.competencyExerciseLinkRepository = competencyExerciseLinkRepository;
        this.lectureUnitLinkRepository = lectureUnitLinkRepository;
    }

    public void deleteAllByCourseId(Long courseId) {
        competencyRelationRepository.deleteAllByCourseId(courseId);
    }

    public void deleteAllExerciseLinks(Iterable<CompetencyExerciseLink> competencyExerciseLinks) {
        competencyExerciseLinkRepository.deleteAll(competencyExerciseLinks);
    }

    public List<CompetencyExerciseLink> saveAllExerciseLinks(Iterable<CompetencyExerciseLink> competencyExerciseLinks) {
        return competencyExerciseLinkRepository.saveAll(competencyExerciseLinks);
    }

    public List<CompetencyLectureUnitLink> saveAllLectureUnitLinks(Iterable<CompetencyLectureUnitLink> lectureUnitLinks) {
        return lectureUnitLinkRepository.saveAll(lectureUnitLinks);
    }

    public void deleteAllLectureUnitLinks(Iterable<CompetencyLectureUnitLink> lectureUnitLinks) {
        lectureUnitLinkRepository.deleteAll(lectureUnitLinks);
    }

    public void deleteAllLectureUnitLinksByLectureId(Long lectureId) {
        lectureUnitLinkRepository.deleteAllByLectureId(lectureId);
    }
}
