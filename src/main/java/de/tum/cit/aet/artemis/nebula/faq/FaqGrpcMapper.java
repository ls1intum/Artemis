package de.tum.cit.aet.artemis.nebula.faq;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import de.tum.cit.aet.artemis.communication.domain.Faq;

public class FaqGrpcMapper {

    public static de.tum.cit.aet.artemis.nebula.Faq.FAQ mapToProto(Faq faq) {
        return de.tum.cit.aet.artemis.nebula.Faq.FAQ.newBuilder().setQuestionTitle(faq.getQuestionTitle()).setQuestionAnswer(faq.getQuestionAnswer()).build();
    }

    public static List<de.tum.cit.aet.artemis.nebula.Faq.FAQ> mapToProto(List<Faq> faqs) {
        return faqs.stream().map(FaqGrpcMapper::mapToProto).collect(Collectors.toCollection(ArrayList::new));
    }

}
