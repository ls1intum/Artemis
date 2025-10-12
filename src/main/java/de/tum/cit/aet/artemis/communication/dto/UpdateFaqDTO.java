package de.tum.cit.aet.artemis.communication.dto;

import java.util.Set;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.Faq;
import de.tum.cit.aet.artemis.communication.domain.FaqState;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UpdateFaqDTO(long id, long courseId, @NotBlank String questionTitle, @Nullable String questionAnswer, @Nullable Set<String> categories, @NotNull FaqState faqState) {

    /**
     * Converts this DTO to a Faq entity.
     *
     * @return a new Faq entity with the data from this DTO
     */
    public Faq toEntity() {
        Faq faq = new Faq();
        faq.setId(this.id);
        faq.setFaqState(this.faqState);
        faq.setCategories(this.categories);
        faq.setQuestionTitle(this.questionTitle);
        faq.setQuestionAnswer(this.questionAnswer);
        return faq;
    }
}
