package de.tum.in.www1.artemis.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.TextExerciseRepository;
import de.tum.in.www1.artemis.web.rest.dto.PageableSearchDTO;
import de.tum.in.www1.artemis.web.rest.dto.SearchResultPageDTO;
import de.tum.in.www1.artemis.web.rest.util.PageUtil;

@Service
public class TextExerciseService {

    private final Logger log = LoggerFactory.getLogger(TextExerciseService.class);

    private final TextExerciseRepository textExerciseRepository;

    private final AuthorizationCheckService authCheckService;

    public TextExerciseService(TextExerciseRepository textExerciseRepository, AuthorizationCheckService authCheckService) {
        this.textExerciseRepository = textExerciseRepository;
        this.authCheckService = authCheckService;
    }

    /**
     * Search for all text exercises fitting a {@link PageableSearchDTO search query}. The result is paged,
     * meaning that there is only a predefined portion of the result returned to the user, so that the server doesn't
     * have to send hundreds/thousands of exercises if there are that many in Artemis.
     *
     * @param search The search query defining the search term and the size of the returned page
     * @param user The user for whom to fetch all available exercises
     * @return A wrapper object containing a list of all found exercises and the total number of pages
     */
    public SearchResultPageDTO<TextExercise> getAllOnPageWithSize(final PageableSearchDTO<String> search, final User user) {
        final var pageable = PageUtil.createExercisePageRequest(search);
        final var searchTerm = search.getSearchTerm();
        final Page<TextExercise> exercisePage;
        if (authCheckService.isAdmin(user)) {
            exercisePage = textExerciseRepository
                    .findByTitleIgnoreCaseContainingOrCourse_TitleIgnoreCaseContainingOrExerciseGroup_Exam_TitleIgnoreCaseContainingOrExerciseGroup_Exam_Course_TitleIgnoreCaseContaining(
                            searchTerm, searchTerm, searchTerm, searchTerm, pageable);
        }
        else {
            exercisePage = textExerciseRepository.findByTitleInExerciseOrCourseAndUserHasAccessToCourse(searchTerm, searchTerm, user.getGroups(), pageable);
        }
        return new SearchResultPageDTO<>(exercisePage.getContent(), exercisePage.getTotalPages());
    }
}
