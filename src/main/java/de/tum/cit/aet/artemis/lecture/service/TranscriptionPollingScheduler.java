package de.tum.cit.aet.artemis.lecture.service;

import java.util.List;
import java.util.Map;

import jakarta.transaction.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;
import de.tum.cit.aet.artemis.lecture.domain.TranscriptionStatus;
import de.tum.cit.aet.artemis.lecture.dto.LectureTranscriptionDTO;
import de.tum.cit.aet.artemis.lecture.repository.LectureTranscriptionRepository;

@Component
public class TranscriptionPollingScheduler {

    private static final Logger log = LoggerFactory.getLogger(TranscriptionPollingScheduler.class);

    private final LectureTranscriptionRepository transcriptionRepository;

    private final LectureTranscriptionService transcriptionService;

    private final ObjectMapper objectMapper;

    private final RestClient restClient;

    public TranscriptionPollingScheduler(LectureTranscriptionRepository transcriptionRepository, LectureTranscriptionService transcriptionService, ObjectMapper objectMapper,
            RestClient.Builder restClientBuilder, @Value("${artemis.nebula.base-url}") String nebulaBaseUrl, @Value("${artemis.nebula.secret-token}") String nebulaSecretToken) {
        this.transcriptionRepository = transcriptionRepository;
        this.transcriptionService = transcriptionService;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder.baseUrl(nebulaBaseUrl).defaultHeader("Authorization", nebulaSecretToken).build();
    }

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void pollPendingTranscriptions() {
        List<LectureTranscription> pendingTranscriptions = transcriptionRepository.findAll().stream()
                .filter(t -> t.getJobId() != null && t.getTranscriptionStatus() == TranscriptionStatus.PENDING).toList();

        for (LectureTranscription t : pendingTranscriptions) {
            try {
                String jobId = t.getJobId();
                Map<String, Object> response = restClient.get().uri("/transcribe/status/" + jobId).retrieve().body(new ParameterizedTypeReference<>() {
                });

                String status = (String) response.get("status");

                if ("done".equals(status)) {
                    LectureTranscriptionDTO dto = objectMapper.convertValue(response, LectureTranscriptionDTO.class);
                    transcriptionService.saveFinalTranscriptionResult(t.getJobId(), dto);
                    log.info("✅ Transcription completed and saved for jobId={}", jobId);
                }
                else if ("error".equals(status)) {
                    t.setTranscriptionStatus(TranscriptionStatus.FAILED);
                    transcriptionRepository.save(t);
                    log.warn("❌ Nebula reported error for job {}: {}", jobId, response.get("error"));
                }
                else {
                    log.info("⏳ Transcription still processing for job {}", jobId);
                }
            }
            catch (Exception e) {
                log.error("❌ Error polling transcription job {}: {}", t.getJobId(), e.getMessage(), e);
            }
        }
    }
}
