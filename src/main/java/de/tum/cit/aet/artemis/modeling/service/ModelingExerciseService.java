package de.tum.cit.aet.artemis.modeling.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.SearchResultPageDTO;
import de.tum.cit.aet.artemis.core.dto.pageablesearch.SearchTermPageableSearchDTO;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.util.PageUtil;
import de.tum.cit.aet.artemis.exercise.service.ExerciseSpecificationService;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.dto.UpdateModelingExerciseDTO;
import de.tum.cit.aet.artemis.modeling.repository.ModelingExerciseRepository;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class ModelingExerciseService {

    private final ModelingExerciseRepository modelingExerciseRepository;

    private final ExerciseSpecificationService exerciseSpecificationService;

    private static final String ENTITY_NAME = "CourseCompetency";

    public ModelingExerciseService(ModelingExerciseRepository modelingExerciseRepository, ExerciseSpecificationService exerciseSpecificationService) {
        this.modelingExerciseRepository = modelingExerciseRepository;
        this.exerciseSpecificationService = exerciseSpecificationService;
    }

    /**
     * Search for all modeling exercises fitting a {@link SearchTermPageableSearchDTO search query}. The result is paged,
     * meaning that there is only a predefined portion of the result returned to the user, so that the server doesn't
     * have to send hundreds/thousands of exercises if there are that many in Artemis.
     *
     * @param search         The search query defining the search term and the size of the returned page
     * @param isCourseFilter Whether to search in the courses for exercises
     * @param isExamFilter   Whether to search in the groups for exercises
     * @param user           The user for whom to fetch all available exercises
     * @return A wrapper object containing a list of all found exercises and the total number of pages
     */
    public SearchResultPageDTO<ModelingExercise> getAllOnPageWithSize(final SearchTermPageableSearchDTO<String> search, final boolean isCourseFilter, final boolean isExamFilter,
            final User user) {
        if (!isCourseFilter && !isExamFilter) {
            return new SearchResultPageDTO<>(Collections.emptyList(), 0);
        }
        final var pageable = PageUtil.createDefaultPageRequest(search, PageUtil.ColumnMapping.EXERCISE);
        final var searchTerm = search.getSearchTerm();
        Specification<ModelingExercise> specification = exerciseSpecificationService.getExerciseSearchSpecification(searchTerm, isCourseFilter, isExamFilter, user, pageable);
        Page<ModelingExercise> exercisePage = modelingExerciseRepository.findAll(specification, pageable);
        return new SearchResultPageDTO<>(exercisePage.getContent(), exercisePage.getTotalPages());
    }

    /**
     * Applies new updateModelingExerciseDTO's data to the given exercise, mutating it in place.
     *
     * @param exercise the exercise to update (will be mutated)
     * @return the same exercise instance after applying updates
     */
    public ModelingExercise updateModelingExercise(UpdateModelingExerciseDTO updateModelingExerciseDTO, ModelingExercise exercise) {
        if (updateModelingExerciseDTO.title() != null) {
            exercise.setTitle(updateModelingExerciseDTO.title());
        }
        if (updateModelingExerciseDTO.channelName() != null) {
            exercise.setChannelName(updateModelingExerciseDTO.channelName());
        }
        if (updateModelingExerciseDTO.shortName() != null) {
            exercise.setShortName(updateModelingExerciseDTO.shortName());
        }
        if (updateModelingExerciseDTO.problemStatement() != null) {
            exercise.setProblemStatement(updateModelingExerciseDTO.problemStatement());
        }
        if (updateModelingExerciseDTO.categories() != null) {
            exercise.setCategories(updateModelingExerciseDTO.categories());
        }
        if (updateModelingExerciseDTO.difficulty() != null) {
            exercise.setDifficulty(updateModelingExerciseDTO.difficulty());
        }
        if (updateModelingExerciseDTO.maxPoints() != null) {
            exercise.setMaxPoints(updateModelingExerciseDTO.maxPoints());
        }
        if (updateModelingExerciseDTO.bonusPoints() != null) {
            exercise.setBonusPoints(updateModelingExerciseDTO.bonusPoints());
        }
        if (updateModelingExerciseDTO.includedInOverallScore() != null) {
            exercise.setIncludedInOverallScore(updateModelingExerciseDTO.includedInOverallScore());
        }
        if (updateModelingExerciseDTO.allowFeedbackRequests() != null) {
            exercise.setAllowFeedbackRequests(updateModelingExerciseDTO.allowFeedbackRequests());
        }
        if (updateModelingExerciseDTO.gradingInstructions() != null) {
            exercise.setGradingInstructions(updateModelingExerciseDTO.gradingInstructions());
        }
        exercise.setReleaseDate(updateModelingExerciseDTO.releaseDate());
        exercise.setStartDate(updateModelingExerciseDTO.startDate());
        exercise.setDueDate(updateModelingExerciseDTO.dueDate());
        exercise.setAssessmentDueDate(updateModelingExerciseDTO.assessmentDueDate());
        exercise.setExampleSolutionPublicationDate(updateModelingExerciseDTO.exampleSolutionPublicationDate());
        if (updateModelingExerciseDTO.exampleSolutionModel() != null) {
            exercise.setExampleSolutionModel(updateModelingExerciseDTO.exampleSolutionModel());
        }
        if (updateModelingExerciseDTO.exampleSolutionExplanation() != null) {
            exercise.setExampleSolutionExplanation(updateModelingExerciseDTO.exampleSolutionExplanation());
        }
        if (updateModelingExerciseDTO.gradingCriteria() != null) {
            Set<GradingCriterion> existingCriteria = exercise.getGradingCriteria();
            var existingById = (existingCriteria != null && Hibernate.isInitialized(existingCriteria))
                    ? existingCriteria.stream().filter(gc -> gc.getId() != null).collect(Collectors.toMap(GradingCriterion::getId, gc -> gc))
                    : Map.<Long, GradingCriterion>of();

            Set<GradingCriterion> updatedCriteria = updateModelingExerciseDTO.gradingCriteria().stream().map(dto -> {
                GradingCriterion criterion = dto.id() != null ? existingById.get(dto.id()) : null;
                if (criterion == null) {
                    criterion = dto.toEntity();
                    criterion.setExercise(exercise);
                }
                else {
                    dto.applyTo(criterion);
                }
                return criterion;
            }).collect(Collectors.toSet());

            exercise.setGradingCriteria(updatedCriteria);
        }
        if (updateModelingExerciseDTO.competencyLinks() != null) {
            Set<CompetencyExerciseLink> existingLinks = exercise.getCompetencyLinks();
            if (existingLinks == null || !Hibernate.isInitialized(existingLinks)) {
                existingLinks = Set.of();
            }

            var existingByCompetencyId = existingLinks.stream().filter(link -> link.getCompetency() != null && link.getCompetency().getId() != null)
                    .collect(Collectors.toMap(link -> link.getCompetency().getId(), link -> link));

            Set<CompetencyExerciseLink> updatedLinks = updateModelingExerciseDTO.competencyLinks().stream().map(dto -> {
                Long exerciseCourseId = exercise.getCourseViaExerciseGroupOrCourseMember() != null ? exercise.getCourseViaExerciseGroupOrCourseMember().getId() : null;
                if (exerciseCourseId != null && dto.courseId() != null && !Objects.equals(exerciseCourseId, dto.courseId())) {
                    throw new BadRequestAlertException("The competency does not belong to the exercise's course.", ENTITY_NAME, "wrongCourse");
                }

                var competencyDto = dto.courseCompetencyDTO();
                CompetencyExerciseLink link = existingByCompetencyId.get(competencyDto.id());
                if (link == null) {
                    Competency competency = new Competency();
                    competency.setId(competencyDto.id());
                    competency.setCourse(exercise.getCourseViaExerciseGroupOrCourseMember());
                    competency.setDescription(competencyDto.description());
                    competency.setTitle(competencyDto.title());
                    competency.setTaxonomy(competencyDto.taxonomy());
                    link = new CompetencyExerciseLink(competency, exercise, dto.weight());
                }
                else {
                    link.setWeight(dto.weight());
                }
                return link;
            }).collect(Collectors.toSet());
            exercise.setCompetencyLinks(updatedLinks);
        }
        return exercise;
    }
}
