package de.tum.cit.aet.artemis.iris.util;

import java.util.ArrayList;
import java.util.List;

import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageContent;
import de.tum.cit.aet.artemis.iris.domain.message.IrisTextMessageContent;

public class IrisMessageContentFactory {

    private IrisMessageContentFactory() {
        // Prevent instantiation of this utility class
    }

    public static List<IrisMessageContent> createIrisMessageContents() {
        return createIrisMessageContents(3);
    }

    public static List<IrisMessageContent> createIrisMessageContents(int numberOfMessages) {
        List<IrisMessageContent> messageContents = new ArrayList<>();
        for (int i = 0; i < numberOfMessages; i++) {
            messageContents.add(createIrisMessageContent("Text content " + i));
        }
        return messageContents;
    }

    public static IrisMessageContent createIrisMessageContent(String textContent) {
        IrisTextMessageContent messageContent = new IrisTextMessageContent();
        messageContent.setTextContent(textContent);
        return messageContent;
    }
}
