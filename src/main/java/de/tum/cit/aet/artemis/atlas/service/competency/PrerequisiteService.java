package de.tum.cit.aet.artemis.atlas.service.competency;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.domain.competency.Prerequisite;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyImportOptionsDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyWithTailRelationDTO;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyLectureUnitLinkRepository;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyProgressRepository;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRelationRepository;
import de.tum.cit.aet.artemis.atlas.repository.CourseCompetencyRepository;
import de.tum.cit.aet.artemis.atlas.repository.PrerequisiteRepository;
import de.tum.cit.aet.artemis.atlas.repository.StandardizedCompetencyRepository;
import de.tum.cit.aet.artemis.atlas.service.LearningObjectImportService;
import de.tum.cit.aet.artemis.atlas.service.learningpath.LearningPathService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseService;
import de.tum.cit.aet.artemis.lecture.api.LectureUnitRepositoryApi;

/**
 * Service for managing prerequisites.
 */
@Conditional(AtlasEnabled.class)
@Lazy
@Service
public class PrerequisiteService extends CourseCompetencyService {

    private final PrerequisiteRepository prerequisiteRepository;

    public PrerequisiteService(PrerequisiteRepository prerequisiteRepository, AuthorizationCheckService authCheckService, CompetencyRelationRepository competencyRelationRepository,
            LearningPathService learningPathService, CompetencyProgressService competencyProgressService, CompetencyProgressRepository competencyProgressRepository,
            Optional<LectureUnitRepositoryApi> lectureUnitRepositoryApi, StandardizedCompetencyRepository standardizedCompetencyRepository,
            CourseCompetencyRepository courseCompetencyRepository, ExerciseService exerciseService, LearningObjectImportService learningObjectImportService,
            CompetencyLectureUnitLinkRepository competencyLectureUnitLinkRepository, CourseRepository courseRepository) {
        super(competencyProgressRepository, courseCompetencyRepository, competencyRelationRepository, competencyProgressService, exerciseService, learningPathService,
                authCheckService, standardizedCompetencyRepository, lectureUnitRepositoryApi, learningObjectImportService, courseRepository, competencyLectureUnitLinkRepository);
        this.prerequisiteRepository = prerequisiteRepository;
    }

    /**
     * Imports the given prerequisites and relations into a course
     *
     * @param course        the course to import into
     * @param prerequisites the prerequisites to import
     * @param importOptions the options for importing the prerequisites
     * @return The set of imported prerequisites, each also containing the relations for which it is the tail prerequisite for.
     */
    public Set<CompetencyWithTailRelationDTO> importPrerequisites(Course course, Collection<? extends CourseCompetency> prerequisites, CompetencyImportOptionsDTO importOptions) {
        return importCourseCompetencies(course, prerequisites, importOptions, Prerequisite::new);
    }

    /**
     * Imports the standardized prerequisites with the given ids as prerequisites into a course
     *
     * @param prerequisiteIdsToImport the ids of the standardized prerequisites to import
     * @param course                  the course to import into
     * @return the list of imported prerequisites
     */
    public List<CourseCompetency> importStandardizedPrerequisites(List<Long> prerequisiteIdsToImport, Course course) {
        return super.importStandardizedCompetencies(prerequisiteIdsToImport, course, Prerequisite::new);
    }

    /**
     * Creates a new prerequisite and links it to a course and lecture units.
     *
     * @param prerequisite the prerequisite to create
     * @param course       the course to link the prerequisite to
     * @return the persisted prerequisite
     */
    public Prerequisite createPrerequisite(CourseCompetency prerequisite, Course course) {
        Prerequisite prerequisiteToCreate = new Prerequisite(prerequisite);
        return createCourseCompetency(prerequisiteToCreate, course);
    }

    /**
     * Creates a list of new prerequisites and links them to a course and lecture units.
     *
     * @param prerequisites the prerequisites to create
     * @param course        the course to link the prerequisites to
     * @return the persisted prerequisites
     */
    public List<Prerequisite> createPrerequisites(List<Prerequisite> prerequisites, Course course) {
        return createCourseCompetencies(prerequisites, course, Prerequisite::new);
    }

    /**
     * Finds a prerequisite by its id and fetches its lecture units, exercises and progress for the provided user. It also fetches the lecture unit progress for the same user.
     * <p>
     * As Spring Boot 3 doesn't support conditional JOIN FETCH statements, we have to retrieve the data manually.
     *
     * @param prerequisiteId The id of the prerequisite to find
     * @param userId         The id of the user for which to fetch the progress
     * @return The found prerequisite
     */
    public Prerequisite findPrerequisiteWithExercisesAndLectureUnitsAndProgressForUser(Long prerequisiteId, Long userId) {
        Prerequisite prerequisite = prerequisiteRepository.findByIdWithLectureUnitsAndExercisesElseThrow(prerequisiteId);
        return findProgressAndLectureUnitCompletionsForUser(prerequisite, userId);
    }

    /**
     * Finds prerequisites within a course and fetch progress for the provided user.
     * <p>
     * As Spring Boot 3 doesn't support conditional JOIN FETCH statements, we have to retrieve the data manually.
     *
     * @param courseId The id of the course for which to fetch the prerequisites
     * @param userId   The id of the user for which to fetch the progress
     * @return The found prerequisite
     */
    public List<Prerequisite> findPrerequisitesWithProgressForUserByCourseId(Long courseId, Long userId) {
        List<Prerequisite> prerequisites = prerequisiteRepository.findByCourseIdOrderById(courseId);
        return findProgressForCompetenciesAndUser(prerequisites, userId);
    }
}
