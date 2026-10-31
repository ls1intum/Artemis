package de.tum.cit.aet.artemis.course.service.home;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.course.dto.home.CourseHomeDTO;

/** Explicit allowlist: private activity affects deterministic selection but never enters this prompt. */
@Service
@Lazy
@Profile(PROFILE_CORE)
public class CourseHomeAiPromptBuilderService {

    private final JsonMapper mapper;

    private final String system;

    public CourseHomeAiPromptBuilderService(JsonMapper mapper, @Value("classpath:prompts/course/home-welcome-system.st") Resource resource) throws IOException {
        this.mapper = mapper;
        this.system = resource.getContentAsString(StandardCharsets.UTF_8);
    }

    /**
     * Builds a bounded prompt using published course information and nonprivate action facts.
     *
     * @param home   the current authorized course Home snapshot
     * @param locale the requested message language
     * @return the bounded system and user prompt strings
     */
    public CourseHomeAiClient.Input build(CourseHomeDTO home, String locale) {
        var next = home.nextStep();
        String step = "Choose a manageable learning step";
        if (next != null) {
            step = switch (next.target().kind()) {
                case THREAD -> "Read the course discussion";
                case IRIS_SESSION -> "Continue learning with available support";
                default -> next.target().kind().name() + ": " + clip(next.title(), 160);
            };
        }
        String style = switch (home.welcome().variantIndex()) {
            case 1 -> "Encourage curiosity";
            case 2 -> "Encourage reflection";
            default -> "Encourage a manageable first step";
        };
        Map<String, String> data = new LinkedHashMap<>();
        data.put("language", locale.equals("de") ? "German" : "English");
        data.put("courseTitle", clip(home.course().title(), 250));
        data.put("publishedDescription", clip(home.course().description(), 1200));
        data.put("learningApproach", clip(home.course().studentWelcomeContext(), 2000));
        data.put("situation", home.welcome().welcomeCase().name());
        data.put("nextStep", step);
        data.put("style", style);
        String serialized = mapper.writeValueAsString(data);
        while (system.length() + serialized.length() > 8192) {
            String largest = List.of("publishedDescription", "learningApproach", "courseTitle", "nextStep").stream().max(Comparator.comparingInt(k -> data.get(k).length()))
                    .orElseThrow();
            String value = data.get(largest);
            if (value.isEmpty()) {
                throw new IllegalArgumentException("Home welcome system prompt exceeds its bound");
            }
            data.put(largest, clip(value, value.length() / 2));
            serialized = mapper.writeValueAsString(data);
        }
        return new CourseHomeAiClient.Input(system, serialized);
    }

    private String clip(@Nullable String value, int limit) {
        return value == null ? "" : value.substring(0, Math.min(value.length(), limit));
    }
}
