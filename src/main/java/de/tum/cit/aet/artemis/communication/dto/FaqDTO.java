package de.tum.cit.aet.artemis.communication.dto;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.Faq;
import de.tum.cit.aet.artemis.communication.domain.FaqState;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FaqDTO(Long id, String questionTitle, String questionAnswer, Set<String> categories, FaqState faqState) {

    public FaqDTO(Faq faq) {
        this(faq.getId(), faq.getQuestionTitle(), faq.getQuestionAnswer(), faq.getCategories(), faq.getFaqState());
    }

}
