package de.tum.cit.aet.artemis.iris.util;

import java.util.ArrayList;
import java.util.List;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.session.IrisCourseChatSession;
import de.tum.cit.aet.artemis.iris.domain.session.IrisLectureChatSession;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;

public class IrisChatSessionFactory {

    private IrisChatSessionFactory() {
        // Prevent instantiation of this utility class
    }

    public static IrisLectureChatSession createLectureSessionWithMessages(Lecture lecture, User user) {
        List<IrisMessage> messages = new ArrayList<>();

        messages.add(IrisMessageFactory.createIrisMessage(IrisMessageSender.LLM));
        messages.add(IrisMessageFactory.createIrisMessage(IrisMessageSender.USER));

        return createLectureSessionWithMessages(lecture, user, messages);
    }

    public static IrisLectureChatSession createLectureSessionWithMessages(Lecture lecture, User user, List<IrisMessage> messages) {
        IrisLectureChatSession session = new IrisLectureChatSession(lecture, user);
        session.setLectureId(lecture.getId());
        session.setMessages(messages);
        messages.forEach(message -> message.setSession(session));
        return session;
    }

    public static IrisCourseChatSession createCourseSessionWithMessages(Course course, User user) {
        List<IrisMessage> messages = new ArrayList<>();

        messages.add(IrisMessageFactory.createIrisMessage(IrisMessageSender.LLM));
        messages.add(IrisMessageFactory.createIrisMessage(IrisMessageSender.USER));

        return createCourseSession(course, user, messages);
    }

    public static IrisCourseChatSession createCourseSession(Course course, User user, List<IrisMessage> messages) {
        IrisCourseChatSession session = new IrisCourseChatSession(course, user);
        session.setCourseId(course.getId());
        messages.forEach(message -> message.setSession(session));
        session.setMessages(messages);
        return session;
    }
}
