package de.tum.cit.aet.artemis.atlas.service.competency;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyLectureUnitLink;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyImportOptionsDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyWithTailRelationDTO;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyExerciseLinkRepository;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyLectureUnitLinkRepository;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyProgressRepository;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRelationRepository;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRepository;
import de.tum.cit.aet.artemis.atlas.repository.CourseCompetencyRepository;
import de.tum.cit.aet.artemis.atlas.repository.StandardizedCompetencyRepository;
import de.tum.cit.aet.artemis.atlas.service.LearningObjectImportService;
import de.tum.cit.aet.artemis.atlas.service.atlasml.AtlasMLService;
import de.tum.cit.aet.artemis.atlas.service.learningpath.LearningPathService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseService;
import de.tum.cit.aet.artemis.lecture.api.LectureUnitRepositoryApi;
import de.tum.cit.aet.artemis.lecture.domain.ExerciseUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;

/**
 * Service for managing competencies.
 */
@Conditional(AtlasEnabled.class)
@Lazy
@Service
public class CompetencyService extends CourseCompetencyService {

    private final CompetencyRepository competencyRepository;

    private final CompetencyExerciseLinkRepository competencyExerciseLinkRepository;

    public CompetencyService(CompetencyRepository competencyRepository, AuthorizationCheckService authCheckService, CompetencyRelationRepository competencyRelationRepository,
            LearningPathService learningPathService, CompetencyProgressService competencyProgressService, CompetencyProgressRepository competencyProgressRepository,
            Optional<LectureUnitRepositoryApi> lectureUnitRepositoryApi, StandardizedCompetencyRepository standardizedCompetencyRepository,
            CourseCompetencyRepository courseCompetencyRepository, ExerciseService exerciseService, LearningObjectImportService learningObjectImportService,
            CompetencyLectureUnitLinkRepository competencyLectureUnitLinkRepository, CourseRepository courseRepository,
            CompetencyExerciseLinkRepository competencyExerciseLinkRepository, @Lazy AtlasMLService atlasMLService) {
        super(competencyProgressRepository, courseCompetencyRepository, competencyRelationRepository, competencyProgressService, exerciseService, learningPathService,
                authCheckService, standardizedCompetencyRepository, lectureUnitRepositoryApi, learningObjectImportService, courseRepository, competencyLectureUnitLinkRepository,
                atlasMLService);
        this.competencyRepository = competencyRepository;
        this.competencyExerciseLinkRepository = competencyExerciseLinkRepository;
    }

    /**
     * Imports the given competencies and relations into a course
     *
     * @param course        the course to import into
     * @param competencies  the competencies to import
     * @param importOptions the options for importing the competencies
     * @return The set of imported competencies, each also containing the relations it is the tail competency for.
     */
    public Set<CompetencyWithTailRelationDTO> importCompetencies(Course course, Collection<? extends CourseCompetency> competencies, CompetencyImportOptionsDTO importOptions) {
        return importCourseCompetencies(course, competencies, importOptions, Competency::new);
    }

    /**
     * Imports the standardized competencies with the given ids as competencies into a course
     *
     * @param competencyIdsToImport the ids of the standardized competencies to import
     * @param course                the course to import into
     * @return the list of imported competencies
     */
    public List<CourseCompetency> importStandardizedCompetencies(List<Long> competencyIdsToImport, Course course) {
        return super.importStandardizedCompetencies(competencyIdsToImport, course, Competency::new);
    }

    /**
     * Creates a list of new competencies and links them to a course and lecture units.
     *
     * @param competencies the competencies to create
     * @param course       the course to link the competencies to
     * @return the persisted competencies
     */
    public List<Competency> createCompetencies(List<Competency> competencies, Course course) {
        return createCourseCompetencies(competencies, course, Competency::new);
    }

    /**
     * Finds a competency by its id and fetches its lecture units, exercises and progress for the provided user. It also fetches the lecture unit progress for the same user.
     * <p>
     * As Spring Boot 3 doesn't support conditional JOIN FETCH statements, we have to retrieve the data manually.
     *
     * @param competencyId The id of the competency to find
     * @param userId       The id of the user for which to fetch the progress
     * @return The found competency
     */
    public Competency findCompetencyWithExercisesAndLectureUnitsAndProgressForUser(Long competencyId, Long userId) {
        Competency competency = competencyRepository.findByIdWithLectureUnitsAndExercisesElseThrow(competencyId);
        return findProgressAndLectureUnitCompletionsForUser(competency, userId);
    }

    /**
     * Finds competencies within a course and fetch progress for the provided user.
     * <p>
     * As Spring Boot 3 doesn't support conditional JOIN FETCH statements, we have to retrieve the data manually.
     *
     * @param courseId The id of the course for which to fetch the competencies
     * @param userId   The id of the user for which to fetch the progress
     * @return The found competency
     */
    public List<Competency> findCompetenciesWithProgressForUserByCourseId(Long courseId, Long userId) {
        List<Competency> competencies = competencyRepository.findByCourseIdOrderById(courseId);
        return findProgressForCompetenciesAndUser(competencies, userId);
    }

    /**
     * Creates competency links for exercise units of the lecture.
     * <p>
     * As exercise units can not be linked to competencies but only via the exercise itself, we add temporary links to the exercise units.
     * Although they can not be persisted, this makes it easier to display the linked competencies in the client consistently across all lecture unit type.
     *
     * @param lecture the lecture to augment the exercise unit links for
     */
    public void addCompetencyLinksToExerciseUnits(Lecture lecture) {
        List<ExerciseUnit> exerciseUnits = lecture.getLectureUnits().stream().filter(ExerciseUnit.class::isInstance).map(ExerciseUnit.class::cast)
                .filter(unit -> unit.getExercise() != null).toList();

        if (exerciseUnits.isEmpty()) {
            return;
        }

        Set<Long> exerciseIds = exerciseUnits.stream().map(unit -> unit.getExercise().getId()).collect(Collectors.toSet());
        Set<CompetencyExerciseLink> allCompetencyExerciseLinks = competencyExerciseLinkRepository.findByExerciseIdInWithCompetency(exerciseIds);

        Map<Long, List<CompetencyExerciseLink>> linksByExerciseId = allCompetencyExerciseLinks.stream().collect(Collectors.groupingBy(link -> link.getExercise().getId()));

        exerciseUnits.forEach(exerciseUnit -> {
            var exercise = exerciseUnit.getExercise();
            List<CompetencyExerciseLink> competencyExerciseLinksForUnit = linksByExerciseId.getOrDefault(exercise.getId(), List.of());
            Set<CompetencyLectureUnitLink> competencyLectureUnitLinks = competencyExerciseLinksForUnit.stream()
                    .map(link -> new CompetencyLectureUnitLink(link.getCompetency(), exerciseUnit, link.getWeight())).collect(Collectors.toSet());
            competencyExerciseLinksForUnit.forEach(competencyLink -> {
                if (competencyLink.getCompetency() != null && Hibernate.isInitialized(competencyLink.getCompetency())) {
                    competencyLink.getCompetency().setCourse(null); // Avoid sending the course to the client multiple times in the response to save data
                }
            });
            exerciseUnit.setCompetencyLinks(competencyLectureUnitLinks);
        });
    }

    /**
     * Returns a managed reference to the competency with the given id.
     * <p>
     * Uses {@code getReferenceById} to avoid creating detached entities when
     * creating or updating competency links.
     *
     * @param competencyId the id of the competency
     * @return a managed competency reference
     */
    public Competency getReference(Long competencyId) {
        return competencyRepository.getReferenceById(competencyId);
    }
}
