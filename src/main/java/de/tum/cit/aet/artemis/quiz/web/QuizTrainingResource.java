package de.tum.cit.aet.artemis.quiz.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.quiz.dto.LeaderboardEntryDTO;
import de.tum.cit.aet.artemis.quiz.service.QuizTrainingLeaderboardService;

/**
 * REST controller for managing QuizTraining.
 */
@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/quiz/")
public class QuizTrainingResource {

    private final QuizTrainingLeaderboardService quizTrainingLeaderboardService;

    private final UserRepository userRepository;

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authCheckService;

    private static final Logger log = LoggerFactory.getLogger(QuizTrainingResource.class);

    public QuizTrainingResource(QuizTrainingLeaderboardService quizTrainingLeaderboardService, UserRepository userRepository, CourseRepository courseRepository,
            AuthorizationCheckService authCheckService) {
        this.quizTrainingLeaderboardService = quizTrainingLeaderboardService;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.authCheckService = authCheckService;
    }

    @GetMapping("courses/{courseId}/training/leaderboard")
    @EnforceAtLeastStudent
    public ResponseEntity<List<LeaderboardEntryDTO>> getQuizTrainingLeaderboard(@PathVariable Long courseId) {
        log.info("REST request to get quiz questions for course with id : {}", courseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        Course course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);
        List<LeaderboardEntryDTO> leaderboard = quizTrainingLeaderboardService.getLeaderboard(user.getId(), courseId);
        return ResponseEntity.ok(leaderboard);
    }
}
