package de.tum.in.www1.artemis.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.SortingOrder;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.repository.ModelingExerciseRepository;
import de.tum.in.www1.artemis.web.rest.dto.PageableSearchDTO;
import de.tum.in.www1.artemis.web.rest.dto.SearchResultPageDTO;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
public class ModelingExerciseService {

    private final Logger log = LoggerFactory.getLogger(ModelingExerciseService.class);

    private final ModelingExerciseRepository modelingExerciseRepository;

    private final AuthorizationCheckService authCheckService;

    public ModelingExerciseService(ModelingExerciseRepository modelingExerciseRepository, AuthorizationCheckService authCheckService) {
        this.modelingExerciseRepository = modelingExerciseRepository;
        this.authCheckService = authCheckService;
    }

    /**
     * Get one modeling exercise by id.
     *
     * @param exerciseId the id of the entity
     * @return the entity
     */
    public ModelingExercise findOne(Long exerciseId) {
        log.debug("Request to get Modeling Exercise : {}", exerciseId);
        return modelingExerciseRepository.findById(exerciseId).orElseThrow(() -> new EntityNotFoundException("Exercise with id: \"" + exerciseId + "\" does not exist"));
    }

    /**
     * Search for all modeling exercises fitting a {@link PageableSearchDTO search query}. The result is paged,
     * meaning that there is only a predefined portion of the result returned to the user, so that the server doesn't
     * have to send hundreds/thousands of exercises if there are that many in Artemis.
     *
     * @param search The search query defining the search term and the size of the returned page
     * @param user The user for whom to fetch all available exercises
     * @return A wrapper object containing a list of all found exercises and the total number of pages
     */
    public SearchResultPageDTO<ModelingExercise> getAllOnPageWithSize(final PageableSearchDTO<String> search, final User user) {
        var sorting = Sort.by(Exercise.ExerciseSearchColumn.valueOf(search.getSortedColumn()).getMappedColumnName());
        sorting = search.getSortingOrder() == SortingOrder.ASCENDING ? sorting.ascending() : sorting.descending();
        final var sorted = PageRequest.of(search.getPage() - 1, search.getPageSize(), sorting);
        final var searchTerm = search.getSearchTerm();
        final Page<ModelingExercise> exercisePage;
        if (authCheckService.isAdmin(user)) {
            exercisePage = modelingExerciseRepository
                    .findByTitleIgnoreCaseContainingOrCourse_TitleIgnoreCaseContainingOrExerciseGroup_Exam_TitleIgnoreCaseContainingOrExerciseGroup_Exam_Course_TitleIgnoreCaseContaining(
                            searchTerm, searchTerm, searchTerm, searchTerm, sorted);
        }
        else {
            exercisePage = modelingExerciseRepository.findByTitleInExerciseOrCourseAndUserHasAccessToCourse(searchTerm, searchTerm, user.getGroups(), sorted);
        }
        return new SearchResultPageDTO<>(exercisePage.getContent(), exercisePage.getTotalPages());
    }
}
