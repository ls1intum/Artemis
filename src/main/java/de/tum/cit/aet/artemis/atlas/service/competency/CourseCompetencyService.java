package de.tum.cit.aet.artemis.atlas.service.competency;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyLectureUnitLink;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyRelation;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.domain.competency.Prerequisite;
import de.tum.cit.aet.artemis.atlas.domain.competency.StandardizedCompetency;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyContributionDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyImportOptionsDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyRelationDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyWithTailRelationDTO;
import de.tum.cit.aet.artemis.atlas.dto.UpdateCourseCompetencyRelationDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.SaveCompetencyRequestDTO.OperationTypeDTO;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyLectureUnitLinkRepository;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyProgressRepository;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRelationRepository;
import de.tum.cit.aet.artemis.atlas.repository.CourseCompetencyRepository;
import de.tum.cit.aet.artemis.atlas.repository.StandardizedCompetencyRepository;
import de.tum.cit.aet.artemis.atlas.service.LearningObjectImportService;
import de.tum.cit.aet.artemis.atlas.service.atlasml.AtlasMLService;
import de.tum.cit.aet.artemis.atlas.service.learningpath.LearningPathService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.SearchResultPageDTO;
import de.tum.cit.aet.artemis.core.dto.pageablesearch.CompetencyPageableSearchDTO;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.util.PageUtil;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.service.ExerciseService;
import de.tum.cit.aet.artemis.lecture.api.LectureUnitRepositoryApi;
import de.tum.cit.aet.artemis.lecture.config.LectureApiNotPresentException;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;

/**
 * Service for managing competencies.
 */
@Conditional(AtlasEnabled.class)
@Lazy
@Service
public class CourseCompetencyService {

    private static final String ENTITY_NAME = "courseCompetency";

    protected final CompetencyProgressRepository competencyProgressRepository;

    protected final CourseCompetencyRepository courseCompetencyRepository;

    protected final CompetencyRelationRepository competencyRelationRepository;

    protected final CompetencyProgressService competencyProgressService;

    protected final ExerciseService exerciseService;

    protected final LearningPathService learningPathService;

    protected final AuthorizationCheckService authCheckService;

    protected final StandardizedCompetencyRepository standardizedCompetencyRepository;

    private final Optional<LectureUnitRepositoryApi> lectureUnitRepositoryApi;

    private final LearningObjectImportService learningObjectImportService;

    private final CourseRepository courseRepository;

    private final CompetencyLectureUnitLinkRepository lectureUnitLinkRepository;

    private final AtlasMLService atlasMLService;

    public CourseCompetencyService(CompetencyProgressRepository competencyProgressRepository, CourseCompetencyRepository courseCompetencyRepository,
            CompetencyRelationRepository competencyRelationRepository, CompetencyProgressService competencyProgressService, ExerciseService exerciseService,
            LearningPathService learningPathService, AuthorizationCheckService authCheckService, StandardizedCompetencyRepository standardizedCompetencyRepository,
            Optional<LectureUnitRepositoryApi> lectureUnitRepositoryApi, LearningObjectImportService learningObjectImportService, CourseRepository courseRepository,
            CompetencyLectureUnitLinkRepository lectureUnitLinkRepository, @Lazy AtlasMLService atlasMLService) {
        this.competencyProgressRepository = competencyProgressRepository;
        this.courseCompetencyRepository = courseCompetencyRepository;
        this.competencyRelationRepository = competencyRelationRepository;
        this.competencyProgressService = competencyProgressService;
        this.exerciseService = exerciseService;
        this.learningPathService = learningPathService;
        this.authCheckService = authCheckService;
        this.standardizedCompetencyRepository = standardizedCompetencyRepository;
        this.lectureUnitRepositoryApi = lectureUnitRepositoryApi;
        this.learningObjectImportService = learningObjectImportService;
        this.courseRepository = courseRepository;
        this.lectureUnitLinkRepository = lectureUnitLinkRepository;
        this.atlasMLService = atlasMLService;
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
     * @param filter   Whether to filter out competencies that are not linked to any learning objects
     * @return The found competency
     */
    public List<CourseCompetency> findCourseCompetenciesWithProgressForUserByCourseId(long courseId, long userId, boolean filter) {
        List<CourseCompetency> competencies;
        if (filter) {
            competencies = courseCompetencyRepository.findByCourseIdAndLinkedToLearningObjectOrderById(courseId);
        }
        else {
            competencies = courseCompetencyRepository.findByCourseIdOrderById(courseId);
        }
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
        var course = courseRepository.findByIdElseThrow(courseId);
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
        competency.setLectureUnitLinks(competency.getLectureUnitLinks().stream()
                .filter(lectureUnitLink -> authCheckService.isAllowedToSeeLectureUnit(lectureUnitLink.getLectureUnit(), currentUser))
                .peek(lectureUnitLink -> lectureUnitLink.getLectureUnit().setCompleted(lectureUnitLink.getLectureUnit().isCompletedFor(currentUser))).collect(Collectors.toSet()));

        Set<Exercise> exercises = competency.getExerciseLinks().stream().map(CompetencyExerciseLink::getExercise).collect(Collectors.toSet());
        Set<Long> exerciseIdsUserIsAllowedToSee = exerciseService.filterOutExercisesThatUserShouldNotSee(exercises, currentUser).stream().map(Exercise::getId)
                .collect(Collectors.toSet());
        Set<Exercise> exercisesWithAllInformationNeeded = exerciseService.loadExercisesWithInformationForDashboard(exerciseIdsUserIsAllowedToSee, currentUser);

        Set<CompetencyExerciseLink> exerciseLinksWithAllInformation = competency.getExerciseLinks().stream()
                .filter(exerciseLink -> exerciseIdsUserIsAllowedToSee.contains(exerciseLink.getExercise().getId())).peek(exerciseLink -> {
                    Optional<Exercise> exerciseWithAllInformationNeeded = exercisesWithAllInformationNeeded.stream()
                            .filter(exercise -> exercise.getId().equals(exerciseLink.getExercise().getId())).findFirst();
                    exerciseWithAllInformationNeeded.ifPresent(exerciseLink::setExercise);
                }).collect(Collectors.toSet());

        competency.setExerciseLinks(exerciseLinksWithAllInformation);
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
        Function<CourseCompetency, CourseCompetency> createNewCourseCompetency = courseCompetency -> switch (courseCompetency) {
            case Competency competency -> new Competency(competency);
            case Prerequisite prerequisite -> new Prerequisite(prerequisite);
            default -> throw new IllegalStateException("Unexpected value: " + courseCompetency);
        };

        return importCourseCompetencies(course, courseCompetencies, importOptions, createNewCourseCompetency);
    }

    /**
     * Imports the given competencies and relations into a course
     *
     * @param course                    the course to import into
     * @param competenciesToImport      the source competencies that were imported
     * @param importOptions             the import options
     * @param createNewCourseCompetency the function that creates new course competencies
     * @return The set of imported competencies, each also containing the relations it is the tail competency for.
     */
    public Set<CompetencyWithTailRelationDTO> importCourseCompetencies(Course course, Collection<? extends CourseCompetency> competenciesToImport,
            CompetencyImportOptionsDTO importOptions, Function<CourseCompetency, CourseCompetency> createNewCourseCompetency) {
        var idToImportedCompetency = new HashMap<Long, CompetencyWithTailRelationDTO>();

        Set<CourseCompetency> competenciesInCourse = courseCompetencyRepository.findAllForCourse(course.getId());

        for (var courseCompetency : competenciesToImport) {
            Optional<CourseCompetency> existingCompetency = competenciesInCourse.stream().filter(competency -> competency.getTitle().equals(courseCompetency.getTitle()))
                    .filter(competency -> competency.getType().equals(courseCompetency.getType())).findFirst();
            CourseCompetency importedCompetency = existingCompetency.orElse(createNewCourseCompetency.apply(courseCompetency));
            importedCompetency.setCourse(course);
            idToImportedCompetency.put(courseCompetency.getId(), new CompetencyWithTailRelationDTO(importedCompetency, new ArrayList<>()));
        }
        courseCompetencyRepository.saveAll(idToImportedCompetency.values().stream().map(CompetencyWithTailRelationDTO::competency).toList());

        // Save imported competencies to AtlasML (always using list-based API)
        List<Competency> allCompetenciesForAtlas = new ArrayList<>();

        for (CompetencyWithTailRelationDTO competencyDTO : idToImportedCompetency.values()) {
            CourseCompetency importedCompetency = competencyDTO.competency();
            if (importedCompetency instanceof Competency competency) {
                allCompetenciesForAtlas.add(competency);
            }
            else {
                Competency converted = new Competency(importedCompetency);
                converted.setId(importedCompetency.getId());
                allCompetenciesForAtlas.add(converted);
            }
        }

        if (!allCompetenciesForAtlas.isEmpty()) {
            atlasMLService.saveCompetencies(allCompetenciesForAtlas, OperationTypeDTO.UPDATE);
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

        return importedCompetencies;
    }

    /**
     * Creates a new competency and links it to a course and lecture units.
     *
     * @param competencyToCreate the competency to create
     * @param course             the course to link the competency to
     * @param <C>                the type of the CourseCompetency
     * @return the persisted competency
     */
    public <C extends CourseCompetency> C createCourseCompetency(C competencyToCreate, Course course) {
        competencyToCreate.setCourse(course);
        var persistedCompetency = courseCompetencyRepository.save(competencyToCreate);

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

            createdCompetencies.add(createdCompetency);
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

        return courseCompetencyRepository.save(competencyToUpdate);
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
        Set<LectureUnit> lectureUnits = competency.getLectureUnitLinks().stream().map(CompetencyLectureUnitLink::getLectureUnit).collect(Collectors.toSet());
        // collect to map lecture unit id -> this
        LectureUnitRepositoryApi api = lectureUnitRepositoryApi.orElseThrow(() -> new LectureApiNotPresentException(LectureUnitRepositoryApi.class));
        var completions = api.findByLectureUnitsAndUserId(lectureUnits, userId).stream()
                .collect(Collectors.toMap(completion -> completion.getLectureUnit().getId(), completion -> completion));
        lectureUnits.forEach(lectureUnit -> {
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

        exerciseService.removeCompetency(courseCompetency.getExerciseLinks(), courseCompetency);
        removeCompetencyLectureUnitLinks(courseCompetency.getLectureUnitLinks(), courseCompetency);

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

    private void removeCompetencyLectureUnitLinks(Set<CompetencyLectureUnitLink> lectureUnitLinks, CourseCompetency competency) {
        lectureUnitLinkRepository.deleteAll(lectureUnitLinks);
        competency.getLectureUnitLinks().removeAll(lectureUnitLinks);
    }

    /**
     * Get the competency contributions for a user in an exercise.
     *
     * @param exerciseId The id of the exercise
     * @param userId     The id of the user
     * @return The list of competency contributions
     */
    public List<CompetencyContributionDTO> getCompetencyContributionsForExercise(long exerciseId, long userId) {
        final var competencies = courseCompetencyRepository.findAllByExerciseIdWithExerciseLinks(exerciseId);
        return competencies.stream().map(competency -> {
            final var progress = competencyProgressRepository.findByCompetencyIdAndUserId(competency.getId(), userId);
            final var mastery = progress.map(CompetencyProgressService::getMastery).orElse(0.0);
            return new CompetencyContributionDTO(competency.getId(), competency.getTitle(),
                    competency.getExerciseLinks().stream().findFirst().map(CompetencyExerciseLink::getWeight).orElse(0.0), mastery);
        }).toList();
    }

    /**
     * Get the competency contributions for a user in a lecture unit.
     *
     * @param lectureUnitId The id of the lecture unit
     * @param userId        The id of the user
     * @return The list of competency contributions
     */
    public List<CompetencyContributionDTO> getCompetencyContributionsForLectureUnit(long lectureUnitId, long userId) {
        final var competencies = courseCompetencyRepository.findAllByLectureUnitIdWithLectureUnitLinks(lectureUnitId);
        return competencies.stream().map(competency -> {
            final var progress = competencyProgressRepository.findByCompetencyIdAndUserId(competency.getId(), userId);
            final var master = progress.map(CompetencyProgressService::getMastery).orElse(0.0);
            return new CompetencyContributionDTO(competency.getId(), competency.getTitle(),
                    competency.getLectureUnitLinks().stream().findFirst().map(CompetencyLectureUnitLink::getWeight).orElse(0.0), master);
        }).toList();
    }

    /**
     * Finds course competencies by their ids and course id.
     *
     * @param ids      the ids of the course competencies
     * @param courseId the id of the course
     * @return the set of found course competencies
     */
    public Set<CourseCompetency> findCourseCompetenciesByIdsAndCourseId(Set<Long> ids, Long courseId) {
        return courseCompetencyRepository.findByIdInAndCourseId(ids, courseId);
    }
}
