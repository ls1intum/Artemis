package de.tum.cit.aet.artemis.fileupload.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.TITLE_NAME_PATTERN;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
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
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.fileupload.dto.UpdateFileUploadExercisesDTO;
import de.tum.cit.aet.artemis.fileupload.repository.FileUploadExerciseRepository;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class FileUploadExerciseService {

    private final ExerciseSpecificationService exerciseSpecificationService;

    private final FileUploadExerciseRepository fileUploadExerciseRepository;

    private static final String ENTITY_NAME = "FileUploadExercise";

    public FileUploadExerciseService(ExerciseSpecificationService exerciseSpecificationService, FileUploadExerciseRepository fileUploadExerciseRepository) {
        this.exerciseSpecificationService = exerciseSpecificationService;
        this.fileUploadExerciseRepository = fileUploadExerciseRepository;

    }

    /**
     * Search for all file upload exercises fitting a {@link SearchTermPageableSearchDTO search query}. The result is paged,
     * meaning that there is only a predefined portion of the result returned to the user, so that the server doesn't
     * have to send hundreds/thousands of exercises if there are that many in Artemis.
     *
     * @param search         The search query defining the search term and the size of the returned page
     * @param isCourseFilter Whether to search in the courses for exercises
     * @param isExamFilter   Whether to search in the groups for exercises
     * @param user           The user for whom to fetch all available exercises
     * @return A wrapper object containing a list of all found exercises and the total number of pages
     */
    public SearchResultPageDTO<FileUploadExercise> getAllOnPageWithSize(final SearchTermPageableSearchDTO<String> search, final Boolean isCourseFilter, final Boolean isExamFilter,
            final User user) {
        if (!isCourseFilter && !isExamFilter) {
            return new SearchResultPageDTO<>(Collections.emptyList(), 0);
        }
        final var pageable = PageUtil.createDefaultPageRequest(search, PageUtil.ColumnMapping.EXERCISE);
        final var searchTerm = search.getSearchTerm();
        Specification<FileUploadExercise> specification = exerciseSpecificationService.getExerciseSearchSpecification(searchTerm, isCourseFilter, isExamFilter, user, pageable);
        Page<FileUploadExercise> exercisePage = fileUploadExerciseRepository.findAll(specification, pageable);
        return new SearchResultPageDTO<>(exercisePage.getContent(), exercisePage.getTotalPages());
    }

    /**
     * Applies new UpdateFileUploadExercisesDTO's data to the given exercise, mutating it in place.
     * <p>
     * This method follows PUT semantics:
     * <ul>
     * <li>All fields in the DTO represent the new state.</li>
     * <li>Required attributes (e.g. title) are validated here and must not be {@code null} or blank.</li>
     * <li>Nullable attributes are explicitly overwritten, i.e. {@code null} means "clear existing value".</li>
     * <li>Collections (grading criteria, competency links) are fully replaced; {@code null} or empty means "remove all".</li>
     * </ul>
     *
     * @param updateFileUploadExercisesDTO the DTO containing the updated state for the exercise
     * @param exercise                     the exercise to update (will be mutated)
     * @return the same {@link FileUploadExercise} instance after applying the updates
     * @throws BadRequestAlertException if required fields are missing/invalid or a competency from the DTO
     *                                      does not belong to the exercise's course or otherwise violates domain constraints
     */
    public FileUploadExercise updateFileUploadExercise(UpdateFileUploadExercisesDTO updateFileUploadExercisesDTO, FileUploadExercise exercise) {
        if (updateFileUploadExercisesDTO == null) {
            throw new BadRequestAlertException("No fileUpload exercise was provided.", ENTITY_NAME, "isNull");
        }

        if (updateFileUploadExercisesDTO.title() == null || updateFileUploadExercisesDTO.title().isBlank() || updateFileUploadExercisesDTO.title().length() < 3) {
            throw new BadRequestAlertException("The title is not set or is too short.", ENTITY_NAME, "fileUploadExerciseTitleInvalid");
        }
        Matcher titleMatcher = TITLE_NAME_PATTERN.matcher(updateFileUploadExercisesDTO.title());
        if (!titleMatcher.matches()) {
            throw new BadRequestAlertException("The title is invalid.", ENTITY_NAME, "titleInvalid");
        }
        exercise.setTitle(updateFileUploadExercisesDTO.title());

        exercise.setShortName(updateFileUploadExercisesDTO.shortName());
        // problemStatement: null → empty string
        String newProblemStatement = updateFileUploadExercisesDTO.problemStatement() == null ? "" : updateFileUploadExercisesDTO.problemStatement();
        exercise.setProblemStatement(newProblemStatement);

        exercise.setChannelName(updateFileUploadExercisesDTO.channelName());
        exercise.setCategories(updateFileUploadExercisesDTO.categories());
        exercise.setDifficulty(updateFileUploadExercisesDTO.difficulty());

        exercise.setMaxPoints(updateFileUploadExercisesDTO.maxPoints());
        exercise.setBonusPoints(updateFileUploadExercisesDTO.bonusPoints());
        exercise.setIncludedInOverallScore(updateFileUploadExercisesDTO.includedInOverallScore());

        exercise.setReleaseDate(updateFileUploadExercisesDTO.releaseDate());
        exercise.setStartDate(updateFileUploadExercisesDTO.startDate());
        exercise.setDueDate(updateFileUploadExercisesDTO.dueDate());
        exercise.setAssessmentDueDate(updateFileUploadExercisesDTO.assessmentDueDate());
        exercise.setExampleSolutionPublicationDate(updateFileUploadExercisesDTO.exampleSolutionPublicationDate());

        exercise.setAllowComplaintsForAutomaticAssessments(updateFileUploadExercisesDTO.allowComplaintsForAutomaticAssessments());
        exercise.setAllowFeedbackRequests(updateFileUploadExercisesDTO.allowFeedbackRequests());
        exercise.setPresentationScoreEnabled(updateFileUploadExercisesDTO.presentationScoreEnabled());
        exercise.setSecondCorrectionEnabled(updateFileUploadExercisesDTO.secondCorrectionEnabled());
        exercise.setFeedbackSuggestionModule(updateFileUploadExercisesDTO.feedbackSuggestionModule());
        exercise.setGradingInstructions(updateFileUploadExercisesDTO.gradingInstructions());

        exercise.setExampleSolution(updateFileUploadExercisesDTO.exampleSolution());
        exercise.setFilePattern(updateFileUploadExercisesDTO.filePattern());

        // validates general settings: points, dates
        exercise.validateGeneralSettings();

        if (updateFileUploadExercisesDTO.gradingCriteria() == null || updateFileUploadExercisesDTO.gradingCriteria().isEmpty()) {
            Set<GradingCriterion> existingCriteria = exercise.getGradingCriteria();
            if (existingCriteria != null && Hibernate.isInitialized(existingCriteria)) {
                existingCriteria.clear();
            }
        }
        else {
            Set<GradingCriterion> managedCriteria = exercise.getGradingCriteria();
            if (managedCriteria == null) {
                managedCriteria = new HashSet<>();
                exercise.setGradingCriteria(managedCriteria);
            }

            Map<Long, GradingCriterion> existingById = managedCriteria.stream().filter(gc -> gc.getId() != null).collect(Collectors.toMap(GradingCriterion::getId, gc -> gc));

            Set<GradingCriterion> updatedCriteria = updateFileUploadExercisesDTO.gradingCriteria().stream().map(dto -> {
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

            managedCriteria.clear();
            managedCriteria.addAll(updatedCriteria);
        }

        if (updateFileUploadExercisesDTO.competencyLinks() == null || updateFileUploadExercisesDTO.competencyLinks().isEmpty()) {
            Set<CompetencyExerciseLink> existingLinks = exercise.getCompetencyLinks();
            if (existingLinks != null && Hibernate.isInitialized(existingLinks)) {
                existingLinks.clear();
            }
        }
        else {
            Set<CompetencyExerciseLink> managedLinks = exercise.getCompetencyLinks();
            if (managedLinks == null) {
                managedLinks = new HashSet<>();
                exercise.setCompetencyLinks(managedLinks);
            }

            Map<Long, CompetencyExerciseLink> existingByCompetencyId = managedLinks.stream().filter(link -> link.getCompetency() != null && link.getCompetency().getId() != null)
                    .collect(Collectors.toMap(link -> link.getCompetency().getId(), link -> link));

            Long exerciseCourseId = exercise.getCourseViaExerciseGroupOrCourseMember() != null ? exercise.getCourseViaExerciseGroupOrCourseMember().getId() : null;

            Set<CompetencyExerciseLink> updatedLinks = new HashSet<>();

            for (var dto : updateFileUploadExercisesDTO.competencyLinks()) {
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

                updatedLinks.add(link);
            }
            managedLinks.clear();
            managedLinks.addAll(updatedLinks);
        }
        return exercise;
    }
}
