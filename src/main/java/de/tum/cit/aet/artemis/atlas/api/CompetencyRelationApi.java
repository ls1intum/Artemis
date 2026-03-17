package de.tum.cit.aet.artemis.atlas.api;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
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
import de.tum.cit.aet.artemis.atlas.repository.CourseCompetencyRepository;

@Controller
@Conditional(AtlasEnabled.class)
@Lazy
public class CompetencyRelationApi extends AbstractAtlasApi {

    private final CompetencyRelationRepository competencyRelationRepository;

    private final CompetencyExerciseLinkRepository competencyExerciseLinkRepository;

    private final CompetencyLectureUnitLinkRepository lectureUnitLinkRepository;

    private final CourseCompetencyRepository courseCompetencyRepository;

    public CompetencyRelationApi(CompetencyRelationRepository competencyRelationRepository, CompetencyExerciseLinkRepository competencyExerciseLinkRepository,
            CompetencyLectureUnitLinkRepository lectureUnitLinkRepository, CourseCompetencyRepository courseCompetencyRepository) {
        this.competencyRelationRepository = competencyRelationRepository;
        this.competencyExerciseLinkRepository = competencyExerciseLinkRepository;
        this.lectureUnitLinkRepository = lectureUnitLinkRepository;
        this.courseCompetencyRepository = courseCompetencyRepository;
    }

    /**
     * Deletes all competency relations for the given course.
     *
     * @param courseId the id of the course
     */
    public void deleteAllByCourseId(Long courseId) {
        competencyRelationRepository.deleteAllByCourseId(courseId);
    }

    /**
     * Deletes the given exercise links.
     *
     * @param competencyExerciseLinks the links to delete
     */
    public void deleteAllExerciseLinks(Iterable<CompetencyExerciseLink> competencyExerciseLinks) {
        competencyExerciseLinkRepository.deleteAll(competencyExerciseLinks);
    }

    /**
     * Finds a competency or prerequisite by id or throws an EntityNotFoundException.
     *
     * @param competencyId the id of the competency
     * @return the found competency
     */
    public CourseCompetency findCompetencyByIdElseThrow(long competencyId) {
        return courseCompetencyRepository.findByIdElseThrow(competencyId);
    }

    /**
     * Finds all competencies by their ids.
     *
     * @param ids the ids of the competencies
     * @return the found competencies
     */
    public List<CourseCompetency> findAllCompetenciesById(Iterable<Long> ids) {
        return courseCompetencyRepository.findAllById(ids);
    }

    /**
     * Saves all exercise links, ensuring referenced competencies are managed entities.
     *
     * @param competencyExerciseLinks the links to save
     * @return the saved links
     */
    public List<CompetencyExerciseLink> saveAllExerciseLinks(Iterable<CompetencyExerciseLink> competencyExerciseLinks) {
        List<CompetencyExerciseLink> links = StreamSupport.stream(competencyExerciseLinks.spliterator(), false).toList();

        // Load all competencies from database to ensure they are managed entities
        // Hibernate 6.6+ requires all referenced entities to be managed during persist/merge
        Set<Long> competencyIds = links.stream().map(link -> link.getCompetency().getId()).collect(Collectors.toSet());
        Map<Long, CourseCompetency> competencyMap = courseCompetencyRepository.findAllById(competencyIds).stream()
                .collect(Collectors.toMap(CourseCompetency::getId, Function.identity()));

        // Replace detached competencies with managed entities
        links.forEach(link -> {
            CourseCompetency managedCompetency = competencyMap.get(link.getCompetency().getId());
            if (managedCompetency != null) {
                link.setCompetency(managedCompetency);
            }
        });

        return competencyExerciseLinkRepository.saveAll(links);
    }

    /**
     * Saves all lecture unit links.
     *
     * @param lectureUnitLinks the links to save
     * @return the saved links
     */
    public List<CompetencyLectureUnitLink> saveAllLectureUnitLinks(Iterable<CompetencyLectureUnitLink> lectureUnitLinks) {
        return lectureUnitLinkRepository.saveAll(lectureUnitLinks);
    }

    /**
     * Deletes all lecture unit links for the given lecture.
     *
     * @param lectureId the id of the lecture
     */
    public void deleteAllLectureUnitLinksByLectureId(Long lectureId) {
        lectureUnitLinkRepository.deleteAllByLectureId(lectureId);
    }
}
