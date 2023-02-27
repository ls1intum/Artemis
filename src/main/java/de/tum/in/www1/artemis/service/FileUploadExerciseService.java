package de.tum.in.www1.artemis.service;

import java.util.Collections;

import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.FileUploadExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.FileUploadExerciseRepository;
import de.tum.in.www1.artemis.web.rest.dto.PageableSearchDTO;
import de.tum.in.www1.artemis.web.rest.dto.SearchResultPageDTO;
import de.tum.in.www1.artemis.web.rest.util.PageUtil;

@Service
public class FileUploadExerciseService {

    private final ExerciseSpecificationService exerciseSpecificationService;

    private final FileUploadExerciseRepository fileUploadExerciseRepository;

    public FileUploadExerciseService(ExerciseSpecificationService exerciseSpecificationService, FileUploadExerciseRepository fileUploadExerciseRepository) {
        this.exerciseSpecificationService = exerciseSpecificationService;
        this.fileUploadExerciseRepository = fileUploadExerciseRepository;

    }

    /**
     * Search for all file upload exercises fitting a {@link PageableSearchDTO search query}. The result is paged,
     * meaning that there is only a predefined portion of the result returned to the user, so that the server doesn't
     * have to send hundreds/thousands of exercises if there are that many in Artemis.
     *
     * @param search         The search query defining the search term and the size of the returned page
     * @param isCourseFilter Whether to search in the courses for exercises
     * @param isExamFilter   Whether to search in the groups for exercises
     * @param user           The user for whom to fetch all available exercises
     * @return A wrapper object containing a list of all found exercises and the total number of pages
     */
    public SearchResultPageDTO<FileUploadExercise> getAllOnPageWithSize(final PageableSearchDTO<String> search, final Boolean isCourseFilter, final Boolean isExamFilter,
            final User user) {
        if (!isCourseFilter && !isExamFilter) {
            return new SearchResultPageDTO<>(Collections.emptyList(), 0);
        }
        final var pageable = PageUtil.createExercisePageRequest(search);
        final var searchTerm = search.getSearchTerm();
        Specification<FileUploadExercise> specification = exerciseSpecificationService.getExerciseSearchSpecification(searchTerm, isCourseFilter, isExamFilter, user, pageable);
        Page<FileUploadExercise> exercisePage = fileUploadExerciseRepository.findAll(specification, pageable);
        return new SearchResultPageDTO<>(exercisePage.getContent(), exercisePage.getTotalPages());
    }
}
