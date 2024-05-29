package de.tum.in.www1.artemis.service.competency;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;
import static java.util.stream.Collectors.toMap;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.function.Function;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.competency.CompetencyJol;
import de.tum.in.www1.artemis.repository.CompetencyProgressRepository;
import de.tum.in.www1.artemis.repository.CompetencyRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.competency.CompetencyJolRepository;
import de.tum.in.www1.artemis.web.rest.dto.competency.CompetencyJolDTO;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

/**
 * Service Implementation for managing CompetencyJol.
 */
@Profile(PROFILE_CORE)
@Service
public class CompetencyJolService {

    private final String ENTITY_NAME = "CompetencyJol";

    private final CompetencyJolRepository competencyJolRepository;

    private final CompetencyRepository competencyRepository;

    private final CompetencyProgressRepository competencyProgressRepository;

    private final UserRepository userRepository;

    public CompetencyJolService(CompetencyJolRepository competencyJolRepository, CompetencyRepository competencyRepository,
            CompetencyProgressRepository competencyProgressRepository, UserRepository userRepository) {
        this.competencyJolRepository = competencyJolRepository;
        this.competencyRepository = competencyRepository;
        this.competencyProgressRepository = competencyProgressRepository;
        this.userRepository = userRepository;
    }

    /**
     * Check if a judgement of learning value is valid.
     * <p>
     * A judgement of learning value is valid if it is an integer between 1 and 5.
     *
     * @param jolValue the judgement of learning value
     * @return true if the value is valid, false otherwise
     */
    private static boolean isJolValueValid(int jolValue) {
        return 1 <= jolValue && jolValue <= 5;
    }

    /**
     * Set the judgement of learning value for a user and a competency.
     *
     * @param competencyId the id of the competency
     * @param userId       the id of the user
     * @param jolValue     the judgement of learning value
     */
    public void setJudgementOfLearning(long competencyId, long userId, short jolValue) {
        if (!isJolValueValid(jolValue)) {
            throw new BadRequestAlertException("Invalid judgement of learning value", ENTITY_NAME, "invalidJolValue");
        }

        final var jol = createCompetencyJol(competencyId, userId, jolValue, ZonedDateTime.now());
        competencyJolRepository.save(jol);
    }

    /**
     * Create a new CompetencyJOL.
     *
     * @param competencyId  the id of the competency
     * @param userId        the id of the user
     * @param jolValue      the judgement of learning value
     * @param judgementTime the time of the judgement
     * @return the created CompetencyJol (not persisted)
     */
    public CompetencyJol createCompetencyJol(long competencyId, long userId, short jolValue, ZonedDateTime judgementTime) {
        final var jol = new CompetencyJol();
        jol.setCompetency(competencyRepository.findById(competencyId).orElseThrow());
        jol.setUser(userRepository.findById(userId).orElseThrow());
        jol.setValue(jolValue);
        jol.setJudgementTime(judgementTime);
        final var progress = competencyProgressRepository.findByCompetencyIdAndUserId(competencyId, userId).orElseThrow();
        jol.setCompetencyProgress(progress.getProgress());
        jol.setCompetencyConfidence(progress.getConfidence());
        return jol;
    }

    /**
     * Get a users latest judgement of learning for all competencies of a course.
     *
     * @param userId   the id of the user
     * @param courseId the id of the course
     * @return a map from competency id to judgement of learning
     */
    public Map<Long, CompetencyJolDTO> getLatestJudgementOfLearningForUserByCourseId(long userId, long courseId) {
        return competencyJolRepository.findLatestJolValuesForUserByCourseId(userId, courseId).stream().collect(toMap(CompetencyJolDTO::competencyId, Function.identity()));
    }
}
