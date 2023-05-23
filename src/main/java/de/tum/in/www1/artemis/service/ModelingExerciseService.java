package de.tum.in.www1.artemis.service;

import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.modeling.ModelCluster;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.repository.ModelClusterRepository;
import de.tum.in.www1.artemis.repository.ModelElementRepository;
import de.tum.in.www1.artemis.repository.ModelingExerciseRepository;
import de.tum.in.www1.artemis.service.messaging.InstanceMessageSendService;
import de.tum.in.www1.artemis.web.rest.dto.PageableSearchDTO;
import de.tum.in.www1.artemis.web.rest.dto.SearchResultPageDTO;
import de.tum.in.www1.artemis.web.rest.util.PageUtil;

@Service
public class ModelingExerciseService {

    private final Logger log = LoggerFactory.getLogger(ModelingExerciseService.class);

    private final ModelingExerciseRepository modelingExerciseRepository;

    private final InstanceMessageSendService instanceMessageSendService;

    private final ModelClusterRepository modelClusterRepository;

    private final ModelElementRepository modelElementRepository;

    private final ExerciseSpecificationService exerciseSpecificationService;

    public ModelingExerciseService(ModelingExerciseRepository modelingExerciseRepository, InstanceMessageSendService instanceMessageSendService,
            ModelClusterRepository modelClusterRepository, ModelElementRepository modelElementRepository, ExerciseSpecificationService exerciseSpecificationService) {
        this.modelingExerciseRepository = modelingExerciseRepository;
        this.instanceMessageSendService = instanceMessageSendService;
        this.modelClusterRepository = modelClusterRepository;
        this.modelElementRepository = modelElementRepository;
        this.exerciseSpecificationService = exerciseSpecificationService;
    }

    /**
     * Search for all modeling exercises fitting a {@link PageableSearchDTO search query}. The result is paged,
     * meaning that there is only a predefined portion of the result returned to the user, so that the server doesn't
     * have to send hundreds/thousands of exercises if there are that many in Artemis.
     *
     * @param search         The search query defining the search term and the size of the returned page
     * @param isCourseFilter Whether to search in the courses for exercises
     * @param isExamFilter   Whether to search in the groups for exercises
     * @param user           The user for whom to fetch all available exercises
     * @return A wrapper object containing a list of all found exercises and the total number of pages
     */
    public SearchResultPageDTO<ModelingExercise> getAllOnPageWithSize(final PageableSearchDTO<String> search, final boolean isCourseFilter, final boolean isExamFilter,
            final User user) {
        if (!isCourseFilter && !isExamFilter) {
            return new SearchResultPageDTO<>(Collections.emptyList(), 0);
        }
        final var pageable = PageUtil.createExercisePageRequest(search);
        final var searchTerm = search.getSearchTerm();
        Specification<ModelingExercise> specification = exerciseSpecificationService.getExerciseSearchSpecification(searchTerm, isCourseFilter, isExamFilter, user, pageable);
        Page<ModelingExercise> exercisePage = modelingExerciseRepository.findAll(specification, pageable);
        return new SearchResultPageDTO<>(exercisePage.getContent(), exercisePage.getTotalPages());
    }

    public void scheduleOperations(Long modelingExerciseId) {
        instanceMessageSendService.sendModelingExerciseSchedule(modelingExerciseId);
    }

    public void cancelScheduledOperations(Long modelingExerciseId) {
        instanceMessageSendService.sendModelingExerciseScheduleCancel(modelingExerciseId);
    }

    /**
     * Delete clusters and elements of a modeling exercise
     *
     * @param modelingExercise modeling exercise clusters and elements belong to
     */
    public void deleteClustersAndElements(ModelingExercise modelingExercise) {
        List<ModelCluster> clustersToDelete = modelClusterRepository.findAllByExerciseIdWithEagerElements(modelingExercise.getId());

        for (ModelCluster cluster : clustersToDelete) {
            modelElementRepository.deleteAll(cluster.getModelElements());
            modelClusterRepository.deleteById(cluster.getId());
        }
    }
}
