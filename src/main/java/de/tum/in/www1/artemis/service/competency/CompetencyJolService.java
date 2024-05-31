package de.tum.in.www1.artemis.service.competency;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;
import static java.util.stream.Collectors.toMap;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.competency.CompetencyJol;
import de.tum.in.www1.artemis.domain.competency.CompetencyProgress;
import de.tum.in.www1.artemis.repository.CompetencyProgressRepository;
import de.tum.in.www1.artemis.repository.CompetencyRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.competency.CompetencyJolRepository;
import de.tum.in.www1.artemis.service.iris.session.IrisCourseChatSessionService;
import de.tum.in.www1.artemis.web.rest.dto.competency.CompetencyJolDTO;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

/**
 * Service Implementation for managing CompetencyJol.
 */
@Profile(PROFILE_CORE)
@Service
public class CompetencyJolService {

    private static final Logger log = LoggerFactory.getLogger(CompetencyJolService.class);

    private final String ENTITY_NAME = "CompetencyJol";

    private final CompetencyJolRepository competencyJolRepository;

    private final CompetencyRepository competencyRepository;

    private final CompetencyProgressRepository competencyProgressRepository;

    private final UserRepository userRepository;

    private final IrisCourseChatSessionService irisCourseChatSessionService;

    public CompetencyJolService(CompetencyJolRepository competencyJolRepository, CompetencyRepository competencyRepository,
            CompetencyProgressRepository competencyProgressRepository, UserRepository userRepository, IrisCourseChatSessionService irisCourseChatSessionService) {
        this.competencyJolRepository = competencyJolRepository;
        this.competencyRepository = competencyRepository;
        this.competencyProgressRepository = competencyProgressRepository;
        this.userRepository = userRepository;
        this.irisCourseChatSessionService = irisCourseChatSessionService;
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

        // Inform Iris so it can send a message to the user
        try {
            if (userId % 3 > 0) { // HD3-GROUPS: Iris groups are 1 & 2
                irisCourseChatSessionService.onJudgementOfLearningSet(jol);
            }
        }
        catch (Exception e) {
            log.warn("Something went wrong while sending the judgement of learning to Iris", e);
        }
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
