package de.tum.cit.aet.artemis.exercise.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.security.websocket.WebsocketSubscription;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopic;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopicAccess;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopicProvider;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketUserTopic;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;

/**
 * The websocket topics of the exercise module.
 */
@Profile(PROFILE_CORE)
@Lazy
@Component
public class ExerciseWebsocketTopics implements WebsocketTopicProvider {

    /**
     * The team members of a participation who are currently online, and who of them is typing.
     */
    public static final WebsocketTopic TEAM_ONLINE_STUDENTS = WebsocketTopic.of("/topic/participations/{participationId}/team",
            WebsocketTopicAccess.custom(ExerciseWebsocketTopics.class, ExerciseWebsocketTopics::isMemberOfParticipation));

    /**
     * The text submission of a team participation, synchronized between the team members.
     */
    public static final WebsocketTopic TEAM_TEXT_SUBMISSIONS = WebsocketTopic.of("/topic/participations/{participationId}/team/text-submissions",
            WebsocketTopicAccess.custom(ExerciseWebsocketTopics.class, ExerciseWebsocketTopics::isMemberOfParticipation));

    /**
     * Patches to the modeling submission of a team participation, synchronized between the team members.
     */
    public static final WebsocketTopic TEAM_MODELING_SUBMISSIONS = WebsocketTopic.of("/topic/participations/{participationId}/team/modeling-submissions",
            WebsocketTopicAccess.custom(ExerciseWebsocketTopics.class, ExerciseWebsocketTopics::isMemberOfParticipation));

    /**
     * Collaborative editing of an exercise: file and problem statement changes of the other editors, new commits, new exercise versions and review threads.
     */
    public static final WebsocketTopic EDITOR_SYNCHRONIZATION = WebsocketTopic.of("/topic/exercises/{exerciseId}/synchronization",
            WebsocketTopicAccess.atLeastEditorInExercise("exerciseId"));

    /**
     * Changes to the user's team assignments.
     */
    public static final WebsocketUserTopic TEAM_ASSIGNMENTS = WebsocketUserTopic.of("/topic/team-assignments");

    private final StudentParticipationRepository studentParticipationRepository;

    public ExerciseWebsocketTopics(StudentParticipationRepository studentParticipationRepository) {
        this.studentParticipationRepository = studentParticipationRepository;
    }

    /**
     * Only the student of a participation, or a member of its team. No administrator override: the topics carry the team's work in progress.
     */
    private boolean isMemberOfParticipation(WebsocketSubscription subscription) {
        return studentParticipationRepository.existsByIdAndParticipatingStudentLogin(subscription.id("participationId"), subscription.login());
    }
}
