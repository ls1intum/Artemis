package de.tum.cit.aet.artemis.nebula.faq;

import java.util.List;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.domain.Faq;
import de.tum.cit.aet.artemis.communication.domain.FaqState;
import de.tum.cit.aet.artemis.communication.repository.FaqRepository;
import faqservice.FAQServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

@Service
public class FaqGrpcIngestService {

    @Value("${nebula.faq.host:host.docker.internal}")
    private String nebulaUrl;

    @Value("${nebula.faq.port:50051}")
    private int faqPort;

    private final FaqRepository faqRepository;

    private FAQServiceGrpc.FAQServiceBlockingStub faqStub;

    public FaqGrpcIngestService(FaqRepository faqRepository) {
        this.faqRepository = faqRepository;
    }

    @PostConstruct
    public void initGrpcChannel() {
        ManagedChannel channel = ManagedChannelBuilder.forAddress(nebulaUrl, faqPort).usePlaintext().intercept().build();
        this.faqStub = FAQServiceGrpc.newBlockingStub(channel);
    }

    public String ingestAcceptedFaqsToNebula(Long courseId, String inputText) {
        List<Faq> acceptedFaqs = faqRepository.findAllByCourseIdAndFaqState(courseId, FaqState.ACCEPTED).stream().toList();
        List<faqservice.Faq.FAQ> protoFaqs = FaqGrpcMapper.mapToProto(acceptedFaqs);

        faqservice.Faq.FaqRewritingRequest request = faqservice.Faq.FaqRewritingRequest.newBuilder().addAllFaqs(protoFaqs).setInputText(inputText).build();

        faqservice.Faq.FaqRewritingResponse response = faqStub.processInput(request);
        return response.getResult();
    }

}
