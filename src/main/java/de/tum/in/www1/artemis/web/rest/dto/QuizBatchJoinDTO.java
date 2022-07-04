package de.tum.in.www1.artemis.web.rest.dto;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class QuizBatchJoinDTO {

    @Nullable
    public String password;

    @Nullable
    public Long batchId;

    public QuizBatchJoinDTO() {
        // empty constructor for Jackson
    }

    public QuizBatchJoinDTO(String password) {
        this.password = password;
    }

    public QuizBatchJoinDTO(Long batchId) {
        this.batchId = batchId;
    }

}
