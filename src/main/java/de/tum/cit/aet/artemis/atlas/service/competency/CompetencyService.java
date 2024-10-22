package de.tum.cit.aet.artemis.atlas.service.competency;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyImportOptionsDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyWithTailRelationDTO;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyProgressRepository;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRelationRepository;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRepository;
import de.tum.cit.aet.artemis.atlas.repository.CourseCompetencyRepository;
import de.tum.cit.aet.artemis.atlas.repository.StandardizedCompetencyRepository;
import de.tum.cit.aet.artemis.atlas.service.LearningObjectImportService;
import de.tum.cit.aet.artemis.atlas.service.learningpath.LearningPathService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseService;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitCompletionRepository;
import de.tum.cit.aet.artemis.lecture.service.LectureUnitService;

/**
 * Service for managing competencies.
 */
@Profile(PROFILE_CORE)
@Service
public class CompetencyService extends CourseCompetencyService {

    private final CompetencyRepository competencyRepository;

    public CompetencyService(CompetencyRepository competencyRepository, AuthorizationCheckService authCheckService, CompetencyRelationRepository competencyRelationRepository,
            LearningPathService learningPathService, CompetencyProgressService competencyProgressService, LectureUnitService lectureUnitService,
            CompetencyProgressRepository competencyProgressRepository, LectureUnitCompletionRepository lectureUnitCompletionRepository,
            StandardizedCompetencyRepository standardizedCompetencyRepository, CourseCompetencyRepository courseCompetencyRepository, ExerciseService exerciseService,
            LearningObjectImportService learningObjectImportService) {
        super(competencyProgressRepository, courseCompetencyRepository, competencyRelationRepository, competencyProgressService, exerciseService, lectureUnitService,
                learningPathService, authCheckService, standardizedCompetencyRepository, lectureUnitCompletionRepository, learningObjectImportService);
        this.competencyRepository = competencyRepository;
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
        var idToImportedCompetency = new HashMap<Long, CompetencyWithTailRelationDTO>();

        for (var competency : competencies) {
            Competency importedCompetency = new Competency(competency);
            importedCompetency.setCourse(course);

            importedCompetency = competencyRepository.save(importedCompetency);
            idToImportedCompetency.put(competency.getId(), new CompetencyWithTailRelationDTO(importedCompetency, new ArrayList<>()));
        }

        return importCourseCompetencies(course, competencies, idToImportedCompetency, importOptions);
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
     * Creates a new competency and links it to a course and lecture units.
     *
     * @param competency the competency to create
     * @param course     the course to link the competency to
     * @return the persisted competency
     */
    public Competency createCompetency(CourseCompetency competency, Course course) {
        Competency competencyToCreate = new Competency(competency);
        return createCourseCompetency(competencyToCreate, course);
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
}
