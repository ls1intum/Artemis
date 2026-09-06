package de.tum.cit.aet.artemis.atlas.api;

import java.util.Collection;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyLectureUnitLink;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy;
import de.tum.cit.aet.artemis.atlas.factories.CompetencyFactory;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyLectureUnitLinkRepository;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRepository;
import de.tum.cit.aet.artemis.atlas.service.competency.CompetencyService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;

@Conditional(AtlasEnabled.class)
@Controller
@Lazy
public class CompetencyApi extends AbstractAtlasApi {

    /**
     * Title of the demo competency. Used as the idempotency key of {@link #createDemo(Course, Collection)} together with the course, so it must stay stable.
     */
    private static final String DEMO_COMPETENCY_TITLE = "Demo Competency";

    private static final Logger log = LoggerFactory.getLogger(CompetencyApi.class);

    private final CompetencyService competencyService;

    private final CompetencyRepository competencyRepository;

    private final CompetencyLectureUnitLinkRepository competencyLectureUnitLinkRepository;

    public CompetencyApi(CompetencyService competencyService, CompetencyRepository competencyRepository, CompetencyLectureUnitLinkRepository competencyLectureUnitLinkRepository) {
        this.competencyService = competencyService;
        this.competencyRepository = competencyRepository;
        this.competencyLectureUnitLinkRepository = competencyLectureUnitLinkRepository;
    }

    /**
     * Creates the demo competency in the given course if it does not exist yet and links it to the given lecture units.
     * <p>
     * Competency and links are checked independently, so a competency that exists but lost its link is relinked on the next startup.
     *
     * @param course       the demo course the competency belongs to.
     * @param lectureUnits the lecture units the competency is linked to.
     */
    public void createDemo(Course course, Collection<LectureUnit> lectureUnits) {
        Competency competency = competencyRepository.findAllByCourseId(course.getId()).stream().filter(existing -> DEMO_COMPETENCY_TITLE.equals(existing.getTitle())).findFirst()
                .orElseGet(() -> {
                    Competency newCompetency = CompetencyFactory.generateCompetency(DEMO_COMPETENCY_TITLE, "Demo competency seeded on startup by the 'demo' profile.",
                            CompetencyTaxonomy.UNDERSTAND, 50, course);
                    Competency savedCompetency = competencyRepository.save(newCompetency);
                    log.info("Created demo competency '{}' with id {}", DEMO_COMPETENCY_TITLE, savedCompetency.getId());
                    return savedCompetency;
                });

        Set<Long> alreadyLinkedUnitIds = competencyLectureUnitLinkRepository.findLectureUnitIdsByCompetencyIds(Set.of(competency.getId()));
        for (LectureUnit lectureUnit : lectureUnits) {
            if (!alreadyLinkedUnitIds.contains(lectureUnit.getId())) {
                competencyLectureUnitLinkRepository.save(new CompetencyLectureUnitLink(competency, lectureUnit, 1));
                log.info("Linked demo competency '{}' to lecture unit {}", DEMO_COMPETENCY_TITLE, lectureUnit.getId());
            }
        }
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
