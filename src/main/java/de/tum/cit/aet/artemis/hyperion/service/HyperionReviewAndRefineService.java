package de.tum.cit.aet.artemis.hyperion.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_HYPERION;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.NetworkingException;
import de.tum.cit.aet.artemis.hyperion.dto.ConsistencyCheckResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ProblemStatementRewriteResponseDTO;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Service for reviewing and refining programming exercises using the Hyperion service.
 */
@Service
@Lazy
@Profile(PROFILE_HYPERION)
public class HyperionReviewAndRefineService {

    private static final Logger log = LoggerFactory.getLogger(HyperionReviewAndRefineService.class);

    private final ConsistencyCheckService consistencyCheckService;

    private final ProblemStatementRewriteService rewriteService;

    public HyperionReviewAndRefineService(ConsistencyCheckService consistencyCheckService, ProblemStatementRewriteService rewriteService) {
        this.consistencyCheckService = consistencyCheckService;
        this.rewriteService = rewriteService;
    }

    /**
     * Performs a consistency check on a programming exercise using the Hyperion service.
     * Analyzes the relationship between problem statement, template code, solution code, and test cases.
     *
     * @param user     the user requesting the consistency check
     * @param exercise the programming exercise to analyze
     * @return DTO containing identified inconsistencies and their descriptions
     * @throws NetworkingException if communication with Hyperion service fails
     */
    public ConsistencyCheckResponseDTO checkConsistency(User user, ProgrammingExercise exercise) throws NetworkingException {
        log.info("Performing consistency check for exercise {} by user {}", exercise.getId(), user.getLogin());

        return consistencyCheckService.checkConsistency(user, exercise);
    }

    /**
     * Rewrites and improves a problem statement using the Hyperion service.
     *
     * @param user                 the user requesting the problem statement rewrite
     * @param course               the course context (used for logging and tracking)
     * @param problemStatementText the original problem statement text to improve
     * @return DTO containing the improved problem statement text
     * @throws NetworkingException      if communication with the Hyperion service fails
     * @throws IllegalArgumentException if any parameter is null or problemStatementText is empty
     */
    public ProblemStatementRewriteResponseDTO rewriteProblemStatement(User user, Course course, String problemStatementText) throws NetworkingException {
        if (user == null) {
            throw new IllegalArgumentException("User must not be null");
        }
        if (course == null) {
            throw new IllegalArgumentException("Course must not be null");
        }
        if (problemStatementText == null || problemStatementText.trim().isEmpty()) {
            throw new IllegalArgumentException("Problem statement text must not be null or empty");
        }

        log.info("Rewriting problem statement for course {} by user {}", course.getId(), user.getLogin());

        return rewriteService.rewriteProblemStatement(user, course, problemStatementText);
    }

    // Facade retains no internal logic to simplify maintenance going forward.
}
