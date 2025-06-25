package de.tum.cit.aet.artemis.nebula.faq;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import de.tum.cit.aet.artemis.communication.domain.Faq;

public class FaqGrpcMapper {

    public static faqservice.Faq.FAQ mapToProto(Faq faq) {
        return faqservice.Faq.FAQ.newBuilder().setQuestionTitle(faq.getQuestionTitle()).setQuestionAnswer(faq.getQuestionAnswer()).build();
    }

    public static List<faqservice.Faq.FAQ> mapToProto(List<Faq> faqs) {
        return faqs.stream().map(FaqGrpcMapper::mapToProto).collect(Collectors.toCollection(ArrayList::new));
    }

}
