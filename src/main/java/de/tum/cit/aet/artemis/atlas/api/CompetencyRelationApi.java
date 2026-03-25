package de.tum.cit.aet.artemis.atlas.api;

import java.util.List;
import java.util.stream.StreamSupport;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyLectureUnitLink;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyExerciseLinkRepository;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyLectureUnitLinkRepository;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRelationRepository;

@Controller
@Conditional(AtlasEnabled.class)
@Lazy
public class CompetencyRelationApi extends AbstractAtlasApi {

    private final CompetencyRelationRepository competencyRelationRepository;

    private final CompetencyExerciseLinkRepository competencyExerciseLinkRepository;

    private final CompetencyLectureUnitLinkRepository lectureUnitLinkRepository;

    private final CompetencyRepositoryApi competencyRepositoryApi;

    public CompetencyRelationApi(CompetencyRelationRepository competencyRelationRepository, CompetencyExerciseLinkRepository competencyExerciseLinkRepository,
            CompetencyLectureUnitLinkRepository lectureUnitLinkRepository, CompetencyRepositoryApi competencyRepositoryApi) {
        this.competencyRelationRepository = competencyRelationRepository;
        this.competencyExerciseLinkRepository = competencyExerciseLinkRepository;
        this.lectureUnitLinkRepository = lectureUnitLinkRepository;
        this.competencyRepositoryApi = competencyRepositoryApi;
    }

    public void deleteAllByCourseId(Long courseId) {
        competencyRelationRepository.deleteAllByCourseId(courseId);
    }

    public void deleteAllExerciseLinks(Iterable<CompetencyExerciseLink> competencyExerciseLinks) {
        competencyExerciseLinkRepository.deleteAll(competencyExerciseLinks);
    }

    /**
     * Saves all exercise links, resolving detached competency references to managed entities.
     * <p>
     * Uses individual lookups via {@link CompetencyRepositoryApi#findCompetencyOrPrerequisiteByIdElseThrow}
     * instead of {@code findAllById} to avoid Hibernate 6.6+ polymorphic query issues with
     * the abstract {@code CourseCompetency} SINGLE_TABLE base class.
     *
     * @param competencyExerciseLinks the links to save
     * @return the saved links
     */
    public List<CompetencyExerciseLink> saveAllExerciseLinks(Iterable<CompetencyExerciseLink> competencyExerciseLinks) {
        List<CompetencyExerciseLink> links = StreamSupport.stream(competencyExerciseLinks.spliterator(), false).toList();

        // Load each competency individually as a managed entity.
        // Using findAllById on the abstract CourseCompetency (SINGLE_TABLE inheritance)
        // can return empty results under Hibernate 6.6+ due to polymorphic query issues.
        links.forEach(link -> {
            CourseCompetency managedCompetency = competencyRepositoryApi.findCompetencyOrPrerequisiteByIdElseThrow(link.getCompetency().getId());
            link.setCompetency(managedCompetency);
        });

        return competencyExerciseLinkRepository.saveAll(links);
    }

    public List<CompetencyLectureUnitLink> saveAllLectureUnitLinks(Iterable<CompetencyLectureUnitLink> lectureUnitLinks) {
        return lectureUnitLinkRepository.saveAll(lectureUnitLinks);
    }

    public void deleteAllLectureUnitLinksByLectureId(Long lectureId) {
        lectureUnitLinkRepository.deleteAllByLectureId(lectureId);
    }
}
