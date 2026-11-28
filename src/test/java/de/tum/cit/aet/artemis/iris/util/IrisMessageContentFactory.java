package de.tum.cit.aet.artemis.iris.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import de.tum.cit.aet.artemis.iris.domain.message.IrisJsonMessageContent;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageContent;
import de.tum.cit.aet.artemis.iris.domain.message.IrisSolutionChoice;
import de.tum.cit.aet.artemis.iris.domain.message.IrisTextMessageContent;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode;
import de.tum.cit.aet.artemis.iris.dto.IrisSolutionOfferDTO;

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

    /**
     * Creates the structured content of an assistant message offering the given choices, as Pyris sends it.
     *
     * @param chatMode the chat mode the offer is bound to
     * @param entityId the entity the offer is bound to
     * @param choices  the offered choices in order
     * @return the offer content
     */
    public static IrisJsonMessageContent solutionOffer(IrisChatMode chatMode, long entityId, List<IrisSolutionChoice> choices) {
        return IrisJsonMessageContent.of(new IrisSolutionOfferDTO(IrisSolutionOfferDTO.TYPE, chatMode, entityId, choices));
    }
}
