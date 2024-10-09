package de.tum.cit.aet.artemis.atlas.service.competency;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyRelation;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.domain.competency.Prerequisite;
import de.tum.cit.aet.artemis.atlas.domain.competency.StandardizedCompetency;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyImportOptionsDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyRelationDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyWithTailRelationDTO;
import de.tum.cit.aet.artemis.atlas.dto.UpdateCourseCompetencyRelationDTO;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyProgressRepository;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRelationRepository;
import de.tum.cit.aet.artemis.atlas.repository.CourseCompetencyRepository;
import de.tum.cit.aet.artemis.atlas.repository.StandardizedCompetencyRepository;
import de.tum.cit.aet.artemis.atlas.service.LearningObjectImportService;
import de.tum.cit.aet.artemis.atlas.service.learningpath.LearningPathService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.SearchResultPageDTO;
import de.tum.cit.aet.artemis.core.dto.pageablesearch.CompetencyPageableSearchDTO;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.util.PageUtil;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.service.ExerciseService;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitCompletionRepository;
import de.tum.cit.aet.artemis.lecture.service.LectureUnitService;

/**
 * Service for managing competencies.
 */
@Profile(PROFILE_CORE)
@Service
public class CourseCompetencyService {

    private static final String ENTITY_NAME = "courseCompetency";

    protected final CompetencyProgressRepository competencyProgressRepository;

    protected final CourseCompetencyRepository courseCompetencyRepository;

    protected final CompetencyRelationRepository competencyRelationRepository;

    protected final CompetencyProgressService competencyProgressService;

    protected final ExerciseService exerciseService;

    protected final LectureUnitService lectureUnitService;

    protected final LearningPathService learningPathService;

    protected final AuthorizationCheckService authCheckService;

    protected final StandardizedCompetencyRepository standardizedCompetencyRepository;

    protected final LectureUnitCompletionRepository lectureUnitCompletionRepository;

    private final LearningObjectImportService learningObjectImportService;

    public CourseCompetencyService(CompetencyProgressRepository competencyProgressRepository, CourseCompetencyRepository courseCompetencyRepository,
            CompetencyRelationRepository competencyRelationRepository, CompetencyProgressService competencyProgressService, ExerciseService exerciseService,
            LectureUnitService lectureUnitService, LearningPathService learningPathService, AuthorizationCheckService authCheckService,
            StandardizedCompetencyRepository standardizedCompetencyRepository, LectureUnitCompletionRepository lectureUnitCompletionRepository,
            LearningObjectImportService learningObjectImportService) {
        this.competencyProgressRepository = competencyProgressRepository;
        this.courseCompetencyRepository = courseCompetencyRepository;
        this.competencyRelationRepository = competencyRelationRepository;
        this.competencyProgressService = competencyProgressService;
        this.exerciseService = exerciseService;
        this.lectureUnitService = lectureUnitService;
        this.learningPathService = learningPathService;
        this.authCheckService = authCheckService;
        this.standardizedCompetencyRepository = standardizedCompetencyRepository;
        this.lectureUnitCompletionRepository = lectureUnitCompletionRepository;
        this.learningObjectImportService = learningObjectImportService;
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
    public CourseCompetency findCompetencyWithExercisesAndLectureUnitsAndProgressForUser(Long competencyId, Long userId) {
        CourseCompetency competency = courseCompetencyRepository.findByIdWithLectureUnitsAndExercisesElseThrow(competencyId);
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
    public List<CourseCompetency> findCourseCompetenciesWithProgressForUserByCourseId(Long courseId, Long userId) {
        List<CourseCompetency> competencies = courseCompetencyRepository.findByCourseIdOrderById(courseId);
        return findProgressForCompetenciesAndUser(competencies, userId);
    }

    /**
     * Updates the type of a course competency relation.
     *
     * @param courseId                          The id of the course for which to fetch the competencies
     * @param courseCompetencyRelationId        The id of the course competency relation to update
     * @param updateCourseCompetencyRelationDTO The DTO containing the new relation type
     *
     */
    public void updateCourseCompetencyRelation(long courseId, long courseCompetencyRelationId, UpdateCourseCompetencyRelationDTO updateCourseCompetencyRelationDTO) {
        var relation = competencyRelationRepository.findByIdElseThrow(courseCompetencyRelationId);
        var course = courseCompetencyRepository.findByIdElseThrow(courseId);
        var headCompetency = relation.getHeadCompetency();
        var tailCompetency = relation.getTailCompetency();

        if (!course.getId().equals(headCompetency.getCourse().getId()) || !course.getId().equals(tailCompetency.getCourse().getId())) {
            throw new BadRequestAlertException("The relation does not belong to the course", ENTITY_NAME, "relationWrongCourse");
        }

        relation.setType(updateCourseCompetencyRelationDTO.newRelationType());
        competencyRelationRepository.save(relation);
    }

    /**
     * Search for all course competencies fitting a {@link CompetencyPageableSearchDTO search query}. The result is paged.
     *
     * @param search The search query defining the search terms and the size of the returned page
     * @param user   The user for whom to fetch the competencies
     * @return A wrapper object containing a list of all found competencies and the total number of pages
     */
    public SearchResultPageDTO<CourseCompetency> getOnPageWithSizeForImport(final CompetencyPageableSearchDTO search, final User user) {
        final var pageable = PageUtil.createDefaultPageRequest(search, PageUtil.ColumnMapping.COMPETENCY);
        final String title = StringUtils.isEmpty(search.getTitle()) ? null : search.getTitle();
        final String description = StringUtils.isEmpty(search.getDescription()) ? null : search.getDescription();
        final String courseTitle = StringUtils.isEmpty(search.getCourseTitle()) ? null : search.getCourseTitle();
        final String semester = StringUtils.isEmpty(search.getSemester()) ? null : search.getSemester();

        final Page<CourseCompetency> competencyPage;
        competencyPage = courseCompetencyRepository.findForImportAndUserHasAccessToCourse(title, description, courseTitle, semester, user.getGroups(),
                authCheckService.isAdmin(user), pageable);
        return new SearchResultPageDTO<>(competencyPage.getContent(), competencyPage.getTotalPages());
    }

    /**
     * Filters out all learning objects that the user should not see because they are not yet released
     *
     * @param competency  The competency to filter the learning objects for
     * @param currentUser The user for whom to filter the learning objects
     */
    public void filterOutLearningObjectsThatUserShouldNotSee(CourseCompetency competency, User currentUser) {
        competency.setLectureUnits(competency.getLectureUnits().stream().filter(lectureUnit -> authCheckService.isAllowedToSeeLectureUnit(lectureUnit, currentUser))
                .peek(lectureUnit -> lectureUnit.setCompleted(lectureUnit.isCompletedFor(currentUser))).collect(Collectors.toSet()));

        Set<Exercise> exercisesUserIsAllowedToSee = exerciseService.filterOutExercisesThatUserShouldNotSee(competency.getExercises(), currentUser);
        Set<Exercise> exercisesWithAllInformationNeeded = exerciseService
                .loadExercisesWithInformationForDashboard(exercisesUserIsAllowedToSee.stream().map(Exercise::getId).collect(Collectors.toSet()), currentUser);
        competency.setExercises(exercisesWithAllInformationNeeded);
    }

    /**
     * Imports the given course competencies and relations into a course
     *
     * @param course             the course to import into
     * @param courseCompetencies the course competencies to import
     * @param importOptions      the import options
     * @return The set of imported course competencies, each also containing the relations it is the tail competency for.
     */
    public Set<CompetencyWithTailRelationDTO> importCourseCompetencies(Course course, Collection<CourseCompetency> courseCompetencies, CompetencyImportOptionsDTO importOptions) {
        var idToImportedCompetency = new HashMap<Long, CompetencyWithTailRelationDTO>();

        for (var courseCompetency : courseCompetencies) {
            CourseCompetency importedCompetency = switch (courseCompetency) {
                case Competency competency -> new Competency(competency);
                case Prerequisite prerequisite -> new Prerequisite(prerequisite);
                default -> throw new IllegalStateException("Unexpected value: " + courseCompetency);
            };
            importedCompetency.setCourse(course);

            importedCompetency = courseCompetencyRepository.save(importedCompetency);
            idToImportedCompetency.put(courseCompetency.getId(), new CompetencyWithTailRelationDTO(importedCompetency, new ArrayList<>()));
        }

        return importCourseCompetencies(course, courseCompetencies, idToImportedCompetency, importOptions);
    }

    /**
     * Imports the given competencies and relations into a course
     *
     * @param course                 the course to import into
     * @param competenciesToImport   the source competencies that were imported
     * @param idToImportedCompetency map of original competency id to imported competency
     * @param importOptions          the import options
     * @return The set of imported competencies, each also containing the relations it is the tail competency for.
     */
    public Set<CompetencyWithTailRelationDTO> importCourseCompetencies(Course course, Collection<? extends CourseCompetency> competenciesToImport,
            Map<Long, CompetencyWithTailRelationDTO> idToImportedCompetency, CompetencyImportOptionsDTO importOptions) {
        if (course.getLearningPathsEnabled()) {
            var importedCompetencies = idToImportedCompetency.values().stream().map(CompetencyWithTailRelationDTO::competency).toList();
            learningPathService.linkCompetenciesToLearningPathsOfCourse(importedCompetencies, course.getId());
        }

        if (importOptions.importRelations()) {
            var originalCompetencyIds = idToImportedCompetency.keySet();
            var relations = competencyRelationRepository.findAllByHeadCompetencyIdInAndTailCompetencyIdIn(originalCompetencyIds, originalCompetencyIds);

            for (var relation : relations) {
                var tailCompetencyDTO = idToImportedCompetency.get(relation.getTailCompetency().getId());
                var headCompetencyDTO = idToImportedCompetency.get(relation.getHeadCompetency().getId());

                CompetencyRelation relationToImport = new CompetencyRelation();
                relationToImport.setType(relation.getType());
                relationToImport.setTailCompetency(tailCompetencyDTO.competency());
                relationToImport.setHeadCompetency(headCompetencyDTO.competency());

                relationToImport = competencyRelationRepository.save(relationToImport);
                tailCompetencyDTO.tailRelations().add(CompetencyRelationDTO.of(relationToImport));
            }
        }

        learningObjectImportService.importRelatedLearningObjects(competenciesToImport, idToImportedCompetency, course, importOptions);

        return new HashSet<>(idToImportedCompetency.values());
    }

    /**
     * Imports the standardized competencies with the given ids as course competencies into a course
     *
     * @param competencyIdsToImport    the ids of the standardized competencies to import
     * @param course                   the course to import into
     * @param courseCompetencySupplier the supplier for creating new course competencies
     * @return the list of imported competencies
     */
    public List<CourseCompetency> importStandardizedCompetencies(List<Long> competencyIdsToImport, Course course, Supplier<CourseCompetency> courseCompetencySupplier) {
        List<StandardizedCompetency> standardizedCompetencies = standardizedCompetencyRepository.findAllById(competencyIdsToImport);

        if (standardizedCompetencies.size() != competencyIdsToImport.size()) {
            throw new EntityNotFoundException("Could not find all standardized competencies to import in the database!");
        }

        List<CourseCompetency> competenciesToCreate = new ArrayList<>();

        for (var standardizedCompetency : standardizedCompetencies) {
            var competency = courseCompetencySupplier.get();
            competency.setTitle(standardizedCompetency.getTitle());
            competency.setDescription(standardizedCompetency.getDescription());
            competency.setTaxonomy(standardizedCompetency.getTaxonomy());
            competency.setMasteryThreshold(Competency.DEFAULT_MASTERY_THRESHOLD);
            competency.setLinkedStandardizedCompetency(standardizedCompetency);
            competency.setCourse(course);

            competenciesToCreate.add(competency);
        }

        List<CourseCompetency> importedCompetencies = courseCompetencyRepository.saveAll(competenciesToCreate);

        if (course.getLearningPathsEnabled()) {
            learningPathService.linkCompetenciesToLearningPathsOfCourse(importedCompetencies, course.getId());
        }

        return importedCompetencies;
    }

    /**
     * Creates a new competency and links it to a course and lecture units.
     * If learning paths are enabled, the competency is also linked to the learning paths of the course.
     *
     * @param competencyToCreate the competency to create
     * @param course             the course to link the competency to
     * @param <C>                the type of the CourseCompetency
     * @return the persisted competency
     */
    public <C extends CourseCompetency> C createCourseCompetency(C competencyToCreate, Course course) {
        competencyToCreate.setCourse(course);

        var persistedCompetency = courseCompetencyRepository.save(competencyToCreate);

        updateLectureUnits(competencyToCreate, persistedCompetency);

        if (course.getLearningPathsEnabled()) {
            learningPathService.linkCompetencyToLearningPathsOfCourse(persistedCompetency, course.getId());
        }

        return persistedCompetency;
    }

    /**
     * Creates a list of new competencies and links them to a course and lecture units.
     *
     * @param competencies       the competencies to create
     * @param course             the course to link the competencies to
     * @param competencyFunction the function that creates new course competencies
     * @param <C>                the type of the CourseCompetency
     * @return the persisted competencies
     */
    public <C extends CourseCompetency> List<C> createCourseCompetencies(List<C> competencies, Course course, Function<CourseCompetency, C> competencyFunction) {
        var createdCompetencies = new ArrayList<C>();

        for (var competency : competencies) {
            var createdCompetency = competencyFunction.apply(competency);
            createdCompetency.setCourse(course);
            createdCompetency = courseCompetencyRepository.save(createdCompetency);

            updateLectureUnits(competency, createdCompetency);

            createdCompetencies.add(createdCompetency);
        }

        if (course.getLearningPathsEnabled()) {
            learningPathService.linkCompetenciesToLearningPathsOfCourse(createdCompetencies, course.getId());
        }

        return createdCompetencies;
    }

    /**
     * Updates a competency with the values of another one. Updates progress if necessary.
     *
     * @param competencyToUpdate the competency to update
     * @param competency         the competency to update with
     * @param <C>                the type of the CourseCompetency
     * @return the updated competency
     */
    public <C extends CourseCompetency> C updateCourseCompetency(C competencyToUpdate, C competency) {
        competencyToUpdate.setTitle(competency.getTitle().trim());
        competencyToUpdate.setDescription(competency.getDescription());
        competencyToUpdate.setSoftDueDate(competency.getSoftDueDate());
        competencyToUpdate.setMasteryThreshold(competency.getMasteryThreshold());
        competencyToUpdate.setTaxonomy(competency.getTaxonomy());
        competencyToUpdate.setOptional(competency.isOptional());
        final var persistedCompetency = courseCompetencyRepository.save(competencyToUpdate);

        // update competency progress if necessary
        if (competency.getLectureUnits().size() != competencyToUpdate.getLectureUnits().size() || !competencyToUpdate.getLectureUnits().containsAll(competency.getLectureUnits())) {
            competencyProgressService.updateProgressByCompetencyAndUsersInCourseAsync(persistedCompetency);
        }

        return persistedCompetency;
    }

    /**
     * Find the competency progress and lecture unit completions for a course competency and user.
     *
     * @param competency The competency to find the lecture unit completions
     * @param userId     The id of the user for which to fetch the progress
     * @param <C>        the type of the CourseCompetency
     * @return The found competency
     */
    public <C extends CourseCompetency> C findProgressAndLectureUnitCompletionsForUser(C competency, Long userId) {
        competencyProgressRepository.findByCompetencyIdAndUserId(competency.getId(), userId).ifPresent(progress -> competency.setUserProgress(Set.of(progress)));
        // collect to map lecture unit id -> this
        var completions = lectureUnitCompletionRepository.findByLectureUnitsAndUserId(competency.getLectureUnits(), userId).stream()
                .collect(Collectors.toMap(completion -> completion.getLectureUnit().getId(), completion -> completion));
        competency.getLectureUnits().forEach(lectureUnit -> {
            if (completions.containsKey(lectureUnit.getId())) {
                lectureUnit.setCompletedUsers(Set.of(completions.get(lectureUnit.getId())));
            }
            else {
                lectureUnit.setCompletedUsers(Collections.emptySet());
            }
        });

        return competency;
    }

    /**
     * Find the progress for a list of competencies and a user.
     *
     * @param competencies The competencies to find the progress
     * @param userId       The id of the user for which to fetch the progress
     * @param <C>          the type of the CourseCompetency
     * @return The found competency
     */
    public <C extends CourseCompetency> List<C> findProgressForCompetenciesAndUser(List<C> competencies, Long userId) {
        var progress = competencyProgressRepository.findByCompetenciesAndUser(competencies, userId).stream()
                .collect(Collectors.toMap(completion -> completion.getCompetency().getId(), completion -> completion));

        competencies.forEach(competency -> {
            if (progress.containsKey(competency.getId())) {
                competency.setUserProgress(Set.of(progress.get(competency.getId())));
            }
            else {
                competency.setUserProgress(Collections.emptySet());
            }
        });

        return competencies;
    }

    /**
     * Deletes a course competency and all its relations.
     *
     * @param courseCompetency the course competency to delete
     * @param course           the course the competency belongs to
     */
    public void deleteCourseCompetency(CourseCompetency courseCompetency, Course course) {
        competencyRelationRepository.deleteAllByCompetencyId(courseCompetency.getId());
        competencyProgressService.deleteProgressForCompetency(courseCompetency.getId());

        exerciseService.removeCompetency(courseCompetency.getExercises(), courseCompetency);
        lectureUnitService.removeCompetency(courseCompetency.getLectureUnits(), courseCompetency);

        if (course.getLearningPathsEnabled()) {
            learningPathService.removeLinkedCompetencyFromLearningPathsOfCourse(courseCompetency, course.getId());
        }

        courseCompetencyRepository.deleteById(courseCompetency.getId());
    }

    /**
     * Checks if the competency is part of the course
     *
     * @param competencyId The id of the competency
     * @param courseId     The id of the course
     * @throws BadRequestAlertException if the competency does not belong to the course
     */
    public void checkIfCompetencyBelongsToCourse(long competencyId, long courseId) {
        if (!courseCompetencyRepository.existsByIdAndCourseId(competencyId, courseId)) {
            throw new BadRequestAlertException("The competency does not belong to the course", ENTITY_NAME, "competencyWrongCourse");
        }
    }

    private void updateLectureUnits(CourseCompetency competency, CourseCompetency createdCompetency) {
        if (!competency.getLectureUnits().isEmpty()) {
            lectureUnitService.linkLectureUnitsToCompetency(createdCompetency, competency.getLectureUnits(), Set.of());
            competencyProgressService.updateProgressByCompetencyAndUsersInCourseAsync(createdCompetency);
        }
    }
}
