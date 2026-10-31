package de.tum.cit.aet.artemis.course.service.home;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Feature-specific boundary: never exposes the model, transport or executor as a shared bean. */
public interface CourseHomeAiClient extends AutoCloseable {

    record Input(String systemPrompt, String userPrompt) {

        public Input {
            if (systemPrompt.length() + userPrompt.length() > 8192) {
                throw new IllegalArgumentException("Home welcome prompt exceeds its bound");
            }
        }
    }

    CompletableFuture<Optional<String>> generate(Input input);

    @Override
    void close();
}
