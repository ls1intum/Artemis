package de.tum.cit.aet.artemis.iris.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageContent;
import de.tum.cit.aet.artemis.iris.domain.message.IrisTextMessageContent;

public class IrisMessageContentFactory {

    /**
     * This is a utility class that should not be instantiated, which is why the constructor is private.
     */
    private IrisMessageContentFactory() {
    }

    public static List<IrisMessageContent> createIrisMessageContents() {
        return createIrisMessageContents(3);
    }

    private static IrisMessageContent createMockTextContent() {
        String[] adjectives = { "happy", "sad", "angry", "funny", "silly", "crazy", "beautiful", "smart" };
        String[] nouns = { "dog", "cat", "house", "car", "book", "computer", "phone", "shoe" };

        ThreadLocalRandom randomGenerator = ThreadLocalRandom.current();
        String randomAdjective = adjectives[randomGenerator.nextInt(adjectives.length)];
        String randomNoun = nouns[randomGenerator.nextInt(nouns.length)];

        String randomizedText = "The " + randomAdjective + " " + randomNoun + " jumped over the lazy dog.";
        return new IrisTextMessageContent(randomizedText);
    }

    public static List<IrisMessageContent> createIrisMessageContents(int numberOfMessages) {
        List<IrisMessageContent> messageContents = new ArrayList<>();
        for (int i = 0; i < numberOfMessages; i++) {
            messageContents.add(createMockTextContent());
        }
        return messageContents;
    }
}
