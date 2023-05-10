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

    @JsonProperty("object")
    private String object;

    @JsonProperty("created")
    private Long created;

    @JsonProperty("model")
    private String model;

    @JsonProperty("usage")
    private Usage usage;

    @JsonProperty("choices")
    private List<Choice> choices;

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

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class Usage {

        @JsonProperty("prompt_tokens")
        private Integer promptTokens;

        @JsonProperty("completion_tokens")
        private Integer completionTokens;

        @JsonProperty("total_tokens")
        private Integer totalTokens;

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

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class Choice {

        @JsonProperty("message")
        private Message message;

        @JsonProperty("index")
        private Integer index;

        @JsonProperty("finish_reason")
        private String finishReason;

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

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class Message {

        @JsonProperty("role")
        private String role;

        @JsonProperty("content")
        private String content;

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
