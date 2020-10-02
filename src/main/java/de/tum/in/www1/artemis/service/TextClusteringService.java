package de.tum.in.www1.artemis.service;

import de.tum.in.www1.artemis.service.connectors.AtheneService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.exception.NetworkingError;

@Service
@Profile("athene")
public class TextClusteringService {

    private final Logger log = LoggerFactory.getLogger(TextClusteringService.class);

    private final AtheneService atheneService;

    public TextClusteringService(AtheneService atheneService) {
        this.atheneService = atheneService;
    }

    /**
     * Calculates the similarity clusters for a given TextExercise
     * Can Take a long time and should not be called in the main Thread
     * @param exercise the TextExercise
     */
    @Transactional
    public void calculateClusters(TextExercise exercise) {
        log.debug("Start Clustering for Text Exercise \"" + exercise.getTitle() + "\" (#" + exercise.getId() + ").");

        try{
            atheneService.submitJob(exercise);
        }
        catch (NetworkingError networkingError) {
            networkingError.printStackTrace();
        }
    }

}
