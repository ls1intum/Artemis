package de.tum.in.www1.artemis.service.competency;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.competency.CompetencyJol;
import de.tum.in.www1.artemis.domain.competency.CompetencyProgress;
import de.tum.in.www1.artemis.repository.CompetencyProgressRepository;
import de.tum.in.www1.artemis.repository.CompetencyRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.competency.CompetencyJolRepository;
import de.tum.in.www1.artemis.web.rest.dto.competency.CompetencyJolDTO;
import de.tum.in.www1.artemis.web.rest.dto.competency.CompetencyJolPairDTO;
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

        final var competencyProgress = competencyProgressRepository.findByCompetencyIdAndUserId(competencyId, userId);
        final var jol = createCompetencyJol(competencyId, userId, jolValue, ZonedDateTime.now(), competencyProgress);
        competencyJolRepository.save(jol);
    }

    /**
     * Create a new CompetencyJOL.
     * <p>
     * If no competency progress is provided, the progress and confidence level are set to 0.
     *
     * @param competencyId       the id of the competency
     * @param userId             the id of the user
     * @param jolValue           the judgement of learning value
     * @param judgementTime      the time of the judgement
     * @param competencyProgress the progress and confidence level of the competency for the user at the current time
     * @return the created CompetencyJol (not persisted)
     */
    public CompetencyJol createCompetencyJol(long competencyId, long userId, short jolValue, ZonedDateTime judgementTime, Optional<CompetencyProgress> competencyProgress) {

        final var jol = new CompetencyJol();
        jol.setCompetency(competencyRepository.findById(competencyId).orElseThrow());
        jol.setUser(userRepository.findById(userId).orElseThrow());
        jol.setValue(jolValue);
        jol.setJudgementTime(judgementTime);
        jol.setCompetencyProgress(competencyProgress.map(CompetencyProgress::getProgress).orElse(0.0));
        jol.setCompetencyConfidence(competencyProgress.map(CompetencyProgress::getConfidence).orElse(0.0));
        return jol;
    }

    /**
     * Get the current and prior judgement of learning for a user and a competency.
     *
     * @param userId       the id of the user
     * @param competencyId the id of the competency
     * @return the current and prior judgement of learning
     */
    public CompetencyJolPairDTO getLatestJudgementOfLearningPairForUserByCompetencyId(long userId, long competencyId) {
        final var currentJol = competencyJolRepository.findLatestByCompetencyIdAndUserId(competencyId, userId);
        if (currentJol.isEmpty()) {
            return new CompetencyJolPairDTO(null, null);
        }
        final var priorJol = competencyJolRepository.findLatestByCompetencyIdAndUserIdExceptJolId(competencyId, userId, currentJol.get().getId());
        return CompetencyJolPairDTO.of(currentJol.get(), priorJol.orElse(null));
    }

    /**
     * Get a users latest judgement of learning for all competencies of a course.
     *
     * @param userId   the id of the user
     * @param courseId the id of the course
     * @return a map from competency id to current and prior judgement of learning
     */
    public Map<Long, CompetencyJolPairDTO> getLatestJudgementOfLearningForUserByCourseId(long userId, long courseId) {
        final var currentJols = competencyJolRepository.findLatestJolValuesForUserByCourseId(userId, courseId).stream()
                .collect(toMap(CompetencyJolDTO::competencyId, Function.identity()));
        final var currentJolIds = currentJols.values().stream().map(CompetencyJolDTO::id).collect(toSet());
        final var priorJols = competencyJolRepository.findLatestJolValuesForUserByCourseIdExcludeJolIds(userId, courseId, currentJolIds).stream()
                .collect(toMap(CompetencyJolDTO::competencyId, Function.identity()));
        return currentJols.keySet().stream().collect(toMap(competencyId -> currentJols.get(competencyId).competencyId(),
                competencyId -> new CompetencyJolPairDTO(currentJols.get(competencyId), priorJols.get(competencyId))));
    }
}
