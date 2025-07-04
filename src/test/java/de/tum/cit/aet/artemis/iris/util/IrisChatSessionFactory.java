package de.tum.cit.aet.artemis.iris.util;

import java.util.ArrayList;
import java.util.List;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.domain.session.IrisCourseChatSession;
import de.tum.cit.aet.artemis.iris.domain.session.IrisLectureChatSession;
import de.tum.cit.aet.artemis.iris.domain.session.IrisProgrammingExerciseChatSession;
import de.tum.cit.aet.artemis.iris.domain.session.IrisTextExerciseChatSession;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

public class IrisChatSessionFactory {

    private IrisChatSessionFactory() {
        // Prevent instantiation of this utility class
    }

    public static IrisLectureChatSession createLectureSessionForUser(Lecture lecture, User user) {
        return new IrisLectureChatSession(lecture, user);
    }

    public static IrisCourseChatSession createCourseChatSessionForUser(Course course, User user) {
        return new IrisCourseChatSession(course, user);
    }

    public static IrisTextExerciseChatSession createTextExerciseChatSessionForUser(TextExercise textExercise, User user) {
        return new IrisTextExerciseChatSession(textExercise, user);
    }

    public static IrisProgrammingExerciseChatSession createProgrammingExerciseChatSessionForUser(ProgrammingExercise programmingExercise, User user) {
        return new IrisProgrammingExerciseChatSession(programmingExercise, user);
    }

    public static <T extends IrisChatSession> T createSessionWithMessages(T session, Long id) {
        List<IrisMessage> messages = new ArrayList<>();
        messages.add(IrisMessageFactory.createIrisMessage(IrisMessageSender.LLM));
        messages.add(IrisMessageFactory.createIrisMessage(IrisMessageSender.USER));
        messages.forEach(message -> message.setSession(session));

        session.setMessages(messages);

        return session;
    }

    public static IrisLectureChatSession createLectureSessionForUserWithMessages(Lecture lecture, User user) {
        IrisLectureChatSession chatSession = createLectureSessionForUser(lecture, user);
        chatSession.setLectureId(lecture.getId());
        return createSessionWithMessages(chatSession, lecture.getId());
    }

    public static IrisCourseChatSession createCourseSessionForUserWithMessages(Course course, User user) {
        IrisCourseChatSession chatSession = createCourseChatSessionForUser(course, user);
        chatSession.setCourseId(course.getId());
        return createSessionWithMessages(chatSession, course.getId());
    }

    public static IrisTextExerciseChatSession createTextExerciseSessionForUserWithMessages(TextExercise textExercise, User user) {
        IrisTextExerciseChatSession chatSession = createTextExerciseChatSessionForUser(textExercise, user);
        chatSession.setExerciseId(textExercise.getId());
        return createSessionWithMessages(chatSession, textExercise.getId());
    }

    public static IrisProgrammingExerciseChatSession createProgrammingExerciseChatSessionForUserWithMessages(ProgrammingExercise programmingExercise, User user) {
        IrisProgrammingExerciseChatSession chatSession = createProgrammingExerciseChatSessionForUser(programmingExercise, user);
        chatSession.setExerciseId(programmingExercise.getId());
        return createSessionWithMessages(chatSession, programmingExercise.getId());
    }
}
