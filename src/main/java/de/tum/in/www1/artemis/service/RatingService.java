package de.tum.in.www1.artemis.service;

import java.util.Optional;

import javax.transaction.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Rating;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.repository.RatingRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;

/**
 * Service Implementation for managing {@link de.tum.in.www1.artemis.domain.Rating}.
 */
@Service
public class RatingService {

    private final Logger log = LoggerFactory.getLogger(ResultService.class);

    private final RatingRepository ratingRepository;

    private final ResultRepository resultRepository;

    public RatingService(RatingRepository ratingRepository, ResultRepository resultRepository) {
        this.ratingRepository = ratingRepository;
        this.resultRepository = resultRepository;
    }

    public Optional<Rating> findRatingByResultId(Long resultId) {
        return ratingRepository.findRatingByResultId(resultId);
    }

    @Transactional
    public Rating saveRating(Rating rating) {
        Rating serverRating = new Rating();
        serverRating.setRating(rating.getRating());
        Result result = resultRepository.getOne(rating.getResult().getId());
        serverRating.setResult(result);
        log.debug("Rating: ", serverRating);
        return ratingRepository.save(serverRating);
    }

    @Transactional
    public Rating updateRating(Rating rating) {
        Rating update = this.ratingRepository.getOne(rating.getId());
        update.setRating(rating.getRating());
        return update;
    }

}
