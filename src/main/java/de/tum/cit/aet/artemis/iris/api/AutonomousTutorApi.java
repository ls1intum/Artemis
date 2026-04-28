package de.tum.cit.aet.artemis.iris.api;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.conversation.Conversation;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.service.AutonomousTutorForwardingService;

@Conditional(IrisEnabled.class)
@Controller
@Lazy
public class AutonomousTutorApi extends AbstractIrisApi {

    private final AutonomousTutorForwardingService autonomousTutorForwardingService;

    public AutonomousTutorApi(AutonomousTutorForwardingService autonomousTutorForwardingService) {
        this.autonomousTutorForwardingService = autonomousTutorForwardingService;
    }

    public void onNewMessage(Post post, Conversation conversation, Course course) {
        autonomousTutorForwardingService.onNewMessage(post, conversation, course);
    }

    public void onNewAnswerMessage(AnswerPost answerPost, Post parentPost, Conversation conversation, Course course) {
        autonomousTutorForwardingService.onNewAnswerMessage(answerPost, parentPost, conversation, course);
    }
}
