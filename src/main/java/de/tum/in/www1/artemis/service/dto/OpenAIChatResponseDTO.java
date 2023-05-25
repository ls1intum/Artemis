package de.tum.in.www1.artemis.service.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A DTO representing a response from the OpenAI Chat Completions API.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class OpenAIChatResponseDTO {

    @JsonProperty("id")
    private String id;

    /**
     * The type of JSON object returned. We expect this to be "chat.completion" (what this DTO represents).
     */
    @JsonProperty("object")
    private String object;

    /**
     * The timestamp when the response was created.
     */
    @JsonProperty("created")
    private Long created;

    /**
     * The ID of the model used to generate the response, e.g. "gpt-35-turbo".
     */
    @JsonProperty("model")
    private String model;

    /**
     * A summary of the amount of tokens used by the API request.
     */
    @JsonProperty("usage")
    private Usage usage;

    /**
     * A list of generated responses to the conversation. Typically, there is only one choice.
     */
    @JsonProperty("choices")
    private List<Choice> choices;

    public OpenAIChatResponseDTO() {
    }

    // To create instances for unit tests
    public OpenAIChatResponseDTO(String id, String object, Long created, String model, Usage usage, List<Choice> choices) {
        this.id = id;
        this.object = object;
        this.created = created;
        this.model = model;
        this.usage = usage;
        this.choices = choices;
    }

    public String getId() {
        return id;
    }

    public String getObject() {
        return object;
    }

    public Long getCreated() {
        return created;
    }

    public String getModel() {
        return model;
    }

    public Usage getUsage() {
        return usage;
    }

    public List<Choice> getChoices() {
        return choices;
    }

    /**
     * A summary of the amount of tokens used by the API request.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class Usage {

        /**
         * The number of tokens used reading the prompt.
         */
        @JsonProperty("prompt_tokens")
        private Integer promptTokens;

        /**
         * The number of tokens used generating the response.
         */
        @JsonProperty("completion_tokens")
        private Integer completionTokens;

        /**
         * The total number of tokens used, prompt + response.
         */
        @JsonProperty("total_tokens")
        private Integer totalTokens;

        public Usage() {
        }

        // To create instances for unit tests
        public Usage(Integer promptTokens, Integer completionTokens, Integer totalTokens) {
            this.promptTokens = promptTokens;
            this.completionTokens = completionTokens;
            this.totalTokens = totalTokens;
        }

        public Integer getPromptTokens() {
            return promptTokens;
        }

        public Integer getCompletionTokens() {
            return completionTokens;
        }

        public Integer getTotalTokens() {
            return totalTokens;
        }

        @Override
        public String toString() {
            return "Usage{" + "promptTokens=" + promptTokens + ", completionTokens=" + completionTokens + ", totalTokens=" + totalTokens + '}';
        }
    }

    /**
     * One of potentially many generated responses with some metadata.
     * Typically, we request only one choice from the API.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class Choice {

        @JsonProperty("message")
        private Message message;

        /**
         * The index of the choice in the list of choices.
         */
        @JsonProperty("index")
        private Integer index;

        /**
         * The reason why the AI stopped generating text. This can be one of the following:
         * <ul>
         * <li><b>stop</b>: The model reached a natural stopping point or one of the configured stop sequences.</li>
         * <li><b>length</b>: The response reached the maximum number of tokens specified in the request.</li>
         * <li><b>content_filter</b>: The response contained a banned word, and the content filter was enabled.</li>
         * </ul>
         */
        @JsonProperty("finish_reason")
        private String finishReason;

        public Choice() {
        }

        // To create instances for unit tests
        public Choice(Message message, Integer index, String finishReason) {
            this.message = message;
            this.index = index;
            this.finishReason = finishReason;
        }

        public Message getMessage() {
            return message;
        }

        public Integer getIndex() {
            return index;
        }

        public String getFinishReason() {
            return finishReason;
        }

        @Override
        public String toString() {
            return "Choice{" + "message=" + message + ", index=" + index + ", finishReason='" + finishReason + '\'' + '}';
        }
    }

    /**
     * A message generated by the AI.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class Message {

        /**
         * The role of the AI that generated the response, e.g. "assistant".
         */
        @JsonProperty("role")
        private String role;

        /**
         * The actual content of the AI's response, e.g. "Hello, how are you?".
         */
        @JsonProperty("content")
        private String content;

        public Message() {
        }

        // To create instances for unit tests
        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() {
            return role;
        }

        public String getContent() {
            return content;
        }

        @Override
        public String toString() {
            return "Message{" + "role='" + role + '\'' + ", content='" + content + '\'' + '}';
        }
    }

    @Override
    public String toString() {
        return "OpenAIChatResponseDTO{" + "id='" + id + '\'' + ", object='" + object + '\'' + ", created=" + created + ", model='" + model + '\'' + ", usage=" + usage
                + ", choices=" + choices + '}';
    }

}
