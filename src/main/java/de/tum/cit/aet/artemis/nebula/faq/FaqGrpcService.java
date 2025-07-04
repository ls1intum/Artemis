package de.tum.cit.aet.artemis.nebula.faq;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.domain.Faq;
import de.tum.cit.aet.artemis.communication.domain.FaqState;
import de.tum.cit.aet.artemis.communication.repository.FaqRepository;
import de.tum.cit.aet.artemis.nebula.FAQServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

@Service
public class FaqGrpcService {

    private static final Logger log = LoggerFactory.getLogger(FaqGrpcService.class);

    @Value("${nebula.url:host.docker.internal}")
    private String nebulaUrl;

    @Value("${nebula.port:50051}")
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
        ManagedChannel channel = ManagedChannelBuilder.forAddress(nebulaUrl, faqPort).usePlaintext().build();
        this.faqStub = FAQServiceGrpc.newBlockingStub(channel);
    }

    public String rewriteFaqInNebula(Long courseId, String inputText) {
        List<Faq> acceptedFaqs = faqRepository.findAllByCourseIdAndFaqState(courseId, FaqState.ACCEPTED).stream().toList();
        List<de.tum.cit.aet.artemis.nebula.Faq.FAQ> protoFaqs = FaqGrpcMapper.mapToProto(acceptedFaqs);

        de.tum.cit.aet.artemis.nebula.Faq.FaqRewritingRequest request = de.tum.cit.aet.artemis.nebula.Faq.FaqRewritingRequest.newBuilder().addAllFaqs(protoFaqs)
                .setInputText(inputText).build();

        de.tum.cit.aet.artemis.nebula.Faq.FaqRewritingResponse response = faqStub.rewriteFAQ(request);
        return response.getResult();
    }

    public String rewriteFaqInNebulaStream(Long courseId) {
        List<Faq> acceptedFaqs = faqRepository.findAllByCourseIdAndFaqState(courseId, FaqState.ACCEPTED).stream().toList();
        List<de.tum.cit.aet.artemis.nebula.Faq.FAQ> protoFaqs = FaqGrpcMapper.mapToProto(acceptedFaqs);

        de.tum.cit.aet.artemis.nebula.Faq.FaqRewritingRequest request = de.tum.cit.aet.artemis.nebula.Faq.FaqRewritingRequest.newBuilder().addAllFaqs(protoFaqs).build();

        log.info("🔁 Sending {} accepted FAQs to Nebula for streaming rewrite", protoFaqs.size());

        Iterator<de.tum.cit.aet.artemis.nebula.Faq.FaqRewriteStatusUpdate> responseIterator = faqStub.withDeadlineAfter(60, TimeUnit.SECONDS).rewriteFAQStream(request);

        String finalResult = "";
        while (responseIterator.hasNext()) {
            var update = responseIterator.next();

            log.debug("📡 Received update: {}% - {}", update.getProgressPercent(), update.getStatusMessage());

            if (update.getDone()) {
                finalResult = update.getFinalResult();
                log.info("✅ Final result received from Nebula stream");
            }
        }

        log.debug("🎯 Final rewritten result length: {} characters", finalResult.length());
        return finalResult;
    }

}
