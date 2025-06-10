package de.tum.cit.aet.artemis.nebula.faq;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_NEBULA;

import java.util.List;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.domain.Faq;
import de.tum.cit.aet.artemis.communication.domain.FaqState;
import de.tum.cit.aet.artemis.communication.repository.FaqRepository;
import faqservice.FAQServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

@Service
@Profile(PROFILE_NEBULA)
public class FaqGrpcService {

    @Value("${nebula.faq.host:host.docker.internal}")
    private String nebulaUrl;

    @Value("${nebula.faq.port:50051}")
    private int faqPort;

    private final FaqRepository faqRepository;

    private FAQServiceGrpc.FAQServiceBlockingStub faqStub;

    public FaqGrpcService(FaqRepository faqRepository) {
        this.faqRepository = faqRepository;
    }

    @PostConstruct
    // This method is called after the service is constructed to initialize the gRPC channel.
    // It sets up the FAQServiceBlockingStub to communicate with the Nebula FAQ service.
    // This has to be done afterward, else the application will not start properly due to the missing configs
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
