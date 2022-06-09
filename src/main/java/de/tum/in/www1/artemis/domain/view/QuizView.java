package de.tum.in.www1.artemis.domain.view;

/**
 * NOTE: This class provides inner classes for use with @JsonView
 *
 * JsonView can be used to serialize only select properties of an entity to JSON before sending it to the client. Mark attributes in the domain classes
 *           with @JsonView(QuizView.xxx.class) to include that attribute in the xxx view. Attributes without annotations are not serialized by default, if a view is used. This
 *           means, we have to annotate all properties we want to serialize (white-listing) To use it in a REST response, you have to return a MappingJacksonValue object instead of
 *           the domain object itself. (Alternatively ResponseEntity<MappingJacksonValue>) create it like this (replace 'QuizView.During' with the view you want to use): Object
 *           yourDomainObject = ...; MappingJacksonValue payload = new MappingJacksonValue(yourDomainObject); Class view = QuizView.During.class;
 *           payload.setSerializationView(view); return payload; To use it in a WebSocket message, make it write the value as bytes and send those directly: - use
 *           messagingTemplate.send() instead of messagingTemplate.convertAndSend() - objectMapper should be the mapper used by the injected MappingJackson2HttpMessageConverter
 *           Object yourDomainObject = ...; Class view = QuizView.During.class; byte[] payload = objectMapper.writerWithView(view).writeValueAsBytes(yourDomainObject);
 *           messagingTemplate.send("/topic/some/path/", MessageBuilder.withPayload(payload).build());
 */
public class QuizView {

    /**
     * This view is to be used for sending data to students before a quiz has started (e.g., no questions and statistics in a quiz)
     */
    public static class Before {
    }

    /**
     * This view is to be used for sending data to students during a quiz (e.g., no explanations and correctMappings / isCorrect values in questions, no statistics in quiz, no
     * scores in participation) It extends Before because everything that is visible before a quiz will still be visible during the quiz
     */
    public static class During extends Before {
    }

    /**
     * This view is to be used for sending data to students after a quiz has ended (e.g., no statistics in quiz if statistics have not been released) It extends During because
     * everything that is visible during a quiz will still be visible when the quiz has ended
     */
    public static class After extends During {
    }
}
