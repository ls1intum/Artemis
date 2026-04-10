package de.tum.cit.aet.artemis.iris.util;

import java.util.ArrayList;
import java.util.List;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

public class IrisChatSessionFactory {

    /**
     * This is a utility class that should not be instantiated, which is why the constructor is private.
     */
    private IrisChatSessionFactory() {
    }

    public static IrisChatSession createLectureSessionForUser(Lecture lecture, User user) {
        return new IrisChatSession(lecture, user);
    }

    public static IrisChatSession createCourseChatSessionForUser(Course course, User user) {
        return new IrisChatSession(course, user);
    }

    public static IrisChatSession createTextExerciseChatSessionForUser(TextExercise textExercise, User user) {
        return new IrisChatSession(textExercise, user, IrisChatMode.TEXT_EXERCISE_CHAT);
    }

    public static IrisChatSession createProgrammingExerciseChatSessionForUser(ProgrammingExercise programmingExercise, User user) {
        return new IrisChatSession(programmingExercise, user, IrisChatMode.PROGRAMMING_EXERCISE_CHAT);
    }

    public static <T extends IrisChatSession> T createSessionWithMessages(T session) {
        List<IrisMessage> messages = new ArrayList<>();
        messages.add(IrisMessageFactory.createIrisMessage(IrisMessageSender.LLM));
        messages.add(IrisMessageFactory.createIrisMessage(IrisMessageSender.USER));
        messages.forEach(message -> message.setSession(session));

        session.setMessages(messages);

        return session;
    }

    public static IrisChatSession createLectureSessionForUserWithMessages(Lecture lecture, User user) {
        return createSessionWithMessages(createLectureSessionForUser(lecture, user));
    }

    public static IrisChatSession createCourseSessionForUserWithMessages(Course course, User user) {
        return createSessionWithMessages(createCourseChatSessionForUser(course, user));
    }

    public static IrisChatSession createTextExerciseSessionForUserWithMessages(TextExercise textExercise, User user) {
        return createSessionWithMessages(createTextExerciseChatSessionForUser(textExercise, user));
    }

    public static IrisChatSession createProgrammingExerciseChatSessionForUserWithMessages(ProgrammingExercise programmingExercise, User user) {
        return createSessionWithMessages(createProgrammingExerciseChatSessionForUser(programmingExercise, user));
    }
}
