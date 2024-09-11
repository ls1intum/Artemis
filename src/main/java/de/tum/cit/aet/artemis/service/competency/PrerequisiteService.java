package de.tum.cit.aet.artemis.service.competency;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.domain.competency.Prerequisite;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyProgressRepository;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRelationRepository;
import de.tum.cit.aet.artemis.atlas.repository.CourseCompetencyRepository;
import de.tum.cit.aet.artemis.atlas.repository.PrerequisiteRepository;
import de.tum.cit.aet.artemis.atlas.repository.competency.StandardizedCompetencyRepository;
import de.tum.cit.aet.artemis.domain.Course;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitCompletionRepository;
import de.tum.cit.aet.artemis.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.service.ExerciseService;
import de.tum.cit.aet.artemis.service.LectureUnitService;
import de.tum.cit.aet.artemis.service.learningpath.LearningPathService;
import de.tum.cit.aet.artemis.web.rest.dto.competency.CompetencyWithTailRelationDTO;

/**
 * Service for managing prerequisites.
 */
@Profile(PROFILE_CORE)
@Service
public class PrerequisiteService extends CourseCompetencyService {

    private final PrerequisiteRepository prerequisiteRepository;

    public PrerequisiteService(PrerequisiteRepository prerequisiteRepository, AuthorizationCheckService authCheckService, CompetencyRelationRepository competencyRelationRepository,
            LearningPathService learningPathService, CompetencyProgressService competencyProgressService, LectureUnitService lectureUnitService,
            CompetencyProgressRepository competencyProgressRepository, LectureUnitCompletionRepository lectureUnitCompletionRepository,
            StandardizedCompetencyRepository standardizedCompetencyRepository, CourseCompetencyRepository courseCompetencyRepository, ExerciseService exerciseService) {
        super(competencyProgressRepository, courseCompetencyRepository, competencyRelationRepository, competencyProgressService, exerciseService, lectureUnitService,
                learningPathService, authCheckService, standardizedCompetencyRepository, lectureUnitCompletionRepository);
        this.prerequisiteRepository = prerequisiteRepository;
    }

    /**
     * Imports the given prerequisites and relations into a course
     *
     * @param course        the course to import into
     * @param prerequisites the prerequisites to import
     * @return The set of imported prerequisites, each also containing the relations for which it is the tail prerequisite for.
     */
    public Set<CompetencyWithTailRelationDTO> importPrerequisitesAndRelations(Course course, Collection<? extends CourseCompetency> prerequisites) {
        var idToImportedPrerequisite = new HashMap<Long, CompetencyWithTailRelationDTO>();

        for (var prerequisite : prerequisites) {
            Prerequisite importedPrerequisite = new Prerequisite(prerequisite);
            importedPrerequisite.setCourse(course);

            importedPrerequisite = prerequisiteRepository.save(importedPrerequisite);
            idToImportedPrerequisite.put(prerequisite.getId(), new CompetencyWithTailRelationDTO(importedPrerequisite, new ArrayList<>()));
        }

        return importCourseCompetenciesAndRelations(course, idToImportedPrerequisite);
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
     * Imports the given course prerequisites into a course
     *
     * @param course        the course to import into
     * @param prerequisites the course prerequisites to import
     * @return The list of imported prerequisites
     */
    public Set<CompetencyWithTailRelationDTO> importPrerequisites(Course course, Collection<? extends CourseCompetency> prerequisites) {
        return importCourseCompetencies(course, prerequisites, Prerequisite::new);
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
