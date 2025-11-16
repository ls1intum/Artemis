package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository.ProgrammingExerciseFetchOptions.AuxiliaryRepositories;
import static de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository.ProgrammingExerciseFetchOptions.GradingCriteria;
import static de.tum.cit.aet.artemis.programming.repository.SolutionProgrammingExerciseParticipationRepository.SolutionParticipationFetchOptions;
import static de.tum.cit.aet.artemis.programming.repository.TemplateProgrammingExerciseParticipationRepository.TemplateParticipationFetchOptions;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.SearchResultPageDTO;
import de.tum.cit.aet.artemis.core.dto.pageablesearch.SearchTermPageableSearchDTO;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.util.PageUtil;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.service.ExerciseSpecificationService;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.repository.AuxiliaryRepositoryRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.TemplateProgrammingExerciseParticipationRepository;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class ProgrammingExerciseService {

    private final ProgrammingExerciseTaskService programmingExerciseTaskService;

    private final ExerciseSpecificationService exerciseSpecificationService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    private final SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    private final ResultRepository resultRepository;

    private final AuxiliaryRepositoryRepository auxiliaryRepositoryRepository;

    public ProgrammingExerciseService(ProgrammingExerciseRepository programmingExerciseRepository,
            TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository,
            SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository, ResultRepository resultRepository,
            AuxiliaryRepositoryRepository auxiliaryRepositoryRepository, ProgrammingExerciseTaskService programmingExerciseTaskService,
            ExerciseSpecificationService exerciseSpecificationService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.templateProgrammingExerciseParticipationRepository = templateProgrammingExerciseParticipationRepository;
        this.solutionProgrammingExerciseParticipationRepository = solutionProgrammingExerciseParticipationRepository;
        this.resultRepository = resultRepository;
        this.auxiliaryRepositoryRepository = auxiliaryRepositoryRepository;
        this.programmingExerciseTaskService = programmingExerciseTaskService;
        this.exerciseSpecificationService = exerciseSpecificationService;
    }

    public static Path getProgrammingLanguageProjectTypePath(ProgrammingLanguage programmingLanguage, ProjectType projectType) {
        return getProgrammingLanguageTemplatePath(programmingLanguage).resolve(projectType.name().toLowerCase());
    }

    public static Path getProgrammingLanguageTemplatePath(ProgrammingLanguage programmingLanguage) {
        return Path.of("templates", programmingLanguage.name().toLowerCase());
    }

    public boolean hasAtLeastOneStudentResult(ProgrammingExercise programmingExercise) {
        // Is true if the exercise is released and has at least one result.
        // We can't use the resultService here due to a circular dependency issue.
        return resultRepository.existsBySubmission_Participation_Exercise_Id(programmingExercise.getId());
    }

    /**
     * Search for all programming exercises fitting a {@link SearchTermPageableSearchDTO search query}. The result is paged,
     * meaning that there is only a predefined portion of the result returned to the user, so that the server doesn't
     * have to send hundreds/thousands of exercises if there are that many in Artemis.
     *
     * @param search         The search query defining the search term and the size of the returned page
     * @param isCourseFilter Whether to search in the courses for exercises
     * @param isExamFilter   Whether to search in the groups for exercises
     * @param user           The user for whom to fetch all available exercises
     * @return A wrapper object containing a list of all found exercises and the total number of pages
     */
    public SearchResultPageDTO<ProgrammingExercise> getAllOnPageWithSize(final SearchTermPageableSearchDTO<String> search, final boolean isCourseFilter, final boolean isExamFilter,
            final User user) {
        if (!isCourseFilter && !isExamFilter) {
            return new SearchResultPageDTO<>(Collections.emptyList(), 0);
        }
        String searchTerm = search.getSearchTerm();
        PageRequest pageable = PageUtil.createDefaultPageRequest(search, PageUtil.ColumnMapping.EXERCISE);
        Specification<ProgrammingExercise> specification = exerciseSpecificationService.getExerciseSearchSpecification(searchTerm, isCourseFilter, isExamFilter, user, pageable);
        return getAllOnPageForSpecification(pageable, specification);
    }

    /**
     * Search for all programming exercises with SCA enabled and with a specific programming language.
     *
     * @param search              The search query defining the search term and the size of the returned page
     * @param isCourseFilter      Whether to search in the courses for exercises
     * @param isExamFilter        Whether to search in the groups for exercises
     * @param user                The user for whom to fetch all available exercises
     * @param programmingLanguage The result will only include exercises in this language
     * @return A wrapper object containing a list of all found exercises and the total number of pages
     */
    public SearchResultPageDTO<ProgrammingExercise> getAllWithSCAOnPageWithSize(SearchTermPageableSearchDTO<String> search, boolean isCourseFilter, boolean isExamFilter,
            ProgrammingLanguage programmingLanguage, User user) {
        if (!isCourseFilter && !isExamFilter) {
            return new SearchResultPageDTO<>(Collections.emptyList(), 0);
        }
        String searchTerm = search.getSearchTerm();
        PageRequest pageable = PageUtil.createDefaultPageRequest(search, PageUtil.ColumnMapping.EXERCISE);
        Specification<ProgrammingExercise> specification = exerciseSpecificationService.getExerciseSearchSpecification(searchTerm, isCourseFilter, isExamFilter, user, pageable);
        specification = specification.and(exerciseSpecificationService.createSCAFilter(programmingLanguage));
        return getAllOnPageForSpecification(pageable, specification);
    }

    private SearchResultPageDTO<ProgrammingExercise> getAllOnPageForSpecification(PageRequest pageable, Specification<ProgrammingExercise> specification) {
        Page<ProgrammingExercise> exercisePage = programmingExerciseRepository.findAll(specification, pageable);
        return new SearchResultPageDTO<>(exercisePage.getContent(), exercisePage.getTotalPages());
    }

    /**
     * Load a programming exercise with eager
     * - auxiliary repositories
     * - template participation with submissions (and results if withSubmissionResults is true)
     * - solution participation with submissions (and results if withSubmissionResults is true)
     * - grading criteria (only if withGradingCriteria is true)
     *
     * @param exerciseId            the ID of the programming exercise to load
     * @param withSubmissionResults a flag indicating whether to include submission results
     * @param withGradingCriteria   a flag indicating whether to include grading criteria
     * @return the loaded programming exercise entity
     */
    public ProgrammingExercise loadProgrammingExercise(long exerciseId, boolean withSubmissionResults, boolean withGradingCriteria) {
        // 1. Load programming exercise, optionally with grading criteria
        final Set<ProgrammingExerciseRepository.ProgrammingExerciseFetchOptions> fetchOptions = withGradingCriteria ? Set.of(GradingCriteria, AuxiliaryRepositories)
                : Set.of(AuxiliaryRepositories);
        var programmingExercise = programmingExerciseRepository.findByIdWithDynamicFetchElseThrow(exerciseId, fetchOptions);

        // 2. Load template and solution participation, either with only submissions or with submissions and results
        final var templateFetchOptions = withSubmissionResults ? Set.of(TemplateParticipationFetchOptions.SubmissionsAndResults)
                : Set.of(TemplateParticipationFetchOptions.Submissions);
        final var templateParticipation = templateProgrammingExerciseParticipationRepository.findByExerciseIdWithDynamicFetchElseThrow(exerciseId, templateFetchOptions);

        final var solutionFetchOptions = withSubmissionResults ? Set.of(SolutionParticipationFetchOptions.SubmissionsAndResults)
                : Set.of(SolutionParticipationFetchOptions.Submissions);
        final var solutionParticipation = solutionProgrammingExerciseParticipationRepository.findByExerciseIdWithDynamicFetchElseThrow(exerciseId, solutionFetchOptions);

        programmingExercise.setSolutionParticipation(solutionParticipation);
        programmingExercise.setTemplateParticipation(templateParticipation);

        programmingExerciseTaskService.replaceTestIdsWithNames(programmingExercise);
        return programmingExercise;
    }

    /**
     * Load a programming exercise, only with eager auxiliary repositories
     *
     * @param exerciseId the ID of the programming exercise to load
     * @return the loaded programming exercise entity
     */
    public ProgrammingExercise loadProgrammingExerciseWithAuxiliaryRepositories(long exerciseId) {
        final Set<ProgrammingExerciseRepository.ProgrammingExerciseFetchOptions> fetchOptions = Set.of(AuxiliaryRepositories);
        return programmingExerciseRepository.findByIdWithDynamicFetchElseThrow(exerciseId, fetchOptions);
    }

    /**
     * Find a programming exercise by its id, with eagerly loaded template and solution participation,
     * including their latest submission with the latest result with feedback and test cases.
     * <p>
     * NOTICE: this method is quite expensive because it loads all feedback and test cases,
     * IMPORTANT: you should generally avoid using this query except you really need all information!!
     *
     * @param programmingExerciseId of the programming exercise.
     * @return The programming exercise related to the given id
     * @throws EntityNotFoundException the programming exercise could not be found.
     */
    @NonNull
    public ProgrammingExercise findByIdWithTemplateAndSolutionParticipationAndAuxiliaryReposAndLatestResultFeedbackTestCasesElseThrow(long programmingExerciseId)
            throws EntityNotFoundException {
        ProgrammingExercise programmingExerciseWithTemplate = programmingExerciseRepository.findWithTemplateParticipationAndLatestSubmissionByIdElseThrow(programmingExerciseId);
        // if there are no submissions we can neither access a submission nor does it make sense to load a result
        if (!programmingExerciseWithTemplate.getTemplateParticipation().getSubmissions().isEmpty()) {
            Optional<Result> latestResultForLatestSubmissionOfTemplate = resultRepository
                    .findLatestResultWithFeedbacksAndTestcasesForSubmission(programmingExerciseWithTemplate.getTemplateParticipation().getSubmissions().iterator().next().getId());
            List<Result> resultsForLatestSubmissionTemplate = new ArrayList<>();
            latestResultForLatestSubmissionOfTemplate.ifPresent(resultsForLatestSubmissionTemplate::add);
            programmingExerciseWithTemplate.getTemplateParticipation().getSubmissions().iterator().next().setResults(resultsForLatestSubmissionTemplate);
        }
        SolutionProgrammingExerciseParticipation solutionParticipationWithLatestSubmission = solutionProgrammingExerciseParticipationRepository
                .findWithLatestSubmissionByExerciseIdElseThrow(programmingExerciseId);

        if (!solutionParticipationWithLatestSubmission.getSubmissions().isEmpty()) {
            Optional<Result> latestResultForLatestSubmissionOfSolution = resultRepository
                    .findLatestResultWithFeedbacksAndTestcasesForSubmission(solutionParticipationWithLatestSubmission.getSubmissions().iterator().next().getId());
            List<Result> resultsForLatestSubmissionSolution = new ArrayList<>();
            latestResultForLatestSubmissionOfSolution.ifPresent(resultsForLatestSubmissionSolution::add);
            solutionParticipationWithLatestSubmission.getSubmissions().iterator().next().setResults(resultsForLatestSubmissionSolution);
        }
        List<AuxiliaryRepository> auxiliaryRepositories = auxiliaryRepositoryRepository.findByProgrammingExerciseId(programmingExerciseId);

        programmingExerciseWithTemplate.setSolutionParticipation(solutionParticipationWithLatestSubmission);
        programmingExerciseWithTemplate.setAuxiliaryRepositories(auxiliaryRepositories);

        return programmingExerciseWithTemplate;
    }

    /**
     * Retrieves all programming exercises for a given course, including their categories, template and solution participations with their latest submissions and results.
     * This method avoids one big and expensive query by splitting the retrieval into multiple smaller queries.
     *
     * @param courseId the course the returned programming exercises belong to.
     * @return all exercises for the given course with only the latest result and latest submission for solution and template each (if present).
     */
    public List<ProgrammingExercise> findByCourseIdWithCategoriesLatestSubmissionResultForTemplateAndSolutionParticipation(long courseId) {
        List<ProgrammingExercise> programmingExercisesWithCategories = programmingExerciseRepository.findAllWithCategoriesByCourseId(courseId);
        if (programmingExercisesWithCategories.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> exerciseIds = programmingExercisesWithCategories.stream().map(ProgrammingExercise::getId).collect(Collectors.toSet());

        Set<SolutionProgrammingExerciseParticipation> solutionParticipationsWithLatestSubmission = solutionProgrammingExerciseParticipationRepository
                .findAllWithLatestSubmissionByExerciseIds(exerciseIds);
        Set<TemplateProgrammingExerciseParticipation> templateParticipationsWithLatestSubmission = templateProgrammingExerciseParticipationRepository
                .findAllWithLatestSubmissionByExerciseIds(exerciseIds);

        Set<Long> solutionSubmissionIds = solutionParticipationsWithLatestSubmission.stream().flatMap(p -> p.getSubmissions().stream().map(DomainObject::getId))
                .collect(Collectors.toSet());
        Set<Long> templateSubmissionIds = templateParticipationsWithLatestSubmission.stream().flatMap(p -> p.getSubmissions().stream().map(DomainObject::getId))
                .collect(Collectors.toSet());

        Map<Long, Result> latestResultsForSolutionSubmissions = resultRepository.findLatestResultsBySubmissionIds(solutionSubmissionIds).stream()
                .collect(Collectors.toMap(result -> result.getSubmission().getId(), result -> result, (r1, r2) -> r1)); // In case of multiple, take first

        Map<Long, Result> latestResultsForTemplateSubmissions = resultRepository.findLatestResultsBySubmissionIds(templateSubmissionIds).stream()
                .collect(Collectors.toMap(result -> result.getSubmission().getId(), result -> result, (r1, r2) -> r1));

        Map<Long, SolutionProgrammingExerciseParticipation> solutionParticipationMap = solutionParticipationsWithLatestSubmission.stream()
                .collect(Collectors.toMap(p -> p.getProgrammingExercise().getId(), p -> p));

        Map<Long, TemplateProgrammingExerciseParticipation> templateParticipationMap = templateParticipationsWithLatestSubmission.stream()
                .collect(Collectors.toMap(p -> p.getProgrammingExercise().getId(), p -> p));

        for (ProgrammingExercise programmingExercise : programmingExercisesWithCategories) {
            TemplateProgrammingExerciseParticipation templateParticipation = templateParticipationMap.get(programmingExercise.getId());
            if (templateParticipation != null) {
                programmingExercise.setTemplateParticipation(templateParticipation);
                connectSubmissionAndResult(latestResultsForTemplateSubmissions, templateParticipation.getSubmissions());
            }
            SolutionProgrammingExerciseParticipation solutionParticipation = solutionParticipationMap.get(programmingExercise.getId());
            if (solutionParticipation != null) {
                programmingExercise.setSolutionParticipation(solutionParticipation);
                connectSubmissionAndResult(latestResultsForSolutionSubmissions, solutionParticipation.getSubmissions());
            }
        }
        return programmingExercisesWithCategories;
    }

    private void connectSubmissionAndResult(Map<Long, Result> latestResultsForSolutionSubmissions, Set<Submission> submissions) {
        if (submissions != null && !submissions.isEmpty()) {
            Submission submission = submissions.iterator().next();
            Result res = latestResultsForSolutionSubmissions.get(submission.getId());
            if (res != null) {
                submission.setResults(Collections.singletonList(res));
            }
        }
    }
}
