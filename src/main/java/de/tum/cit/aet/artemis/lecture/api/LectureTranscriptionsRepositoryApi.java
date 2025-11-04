package de.tum.cit.aet.artemis.lecture.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;
import de.tum.cit.aet.artemis.lecture.domain.TranscriptionStatus;
import de.tum.cit.aet.artemis.lecture.repository.LectureTranscriptionRepository;

/**
 * API for managing lecture transcriptions.
 */
@Profile(PROFILE_CORE)
@Lazy
@Controller
public class LectureTranscriptionsRepositoryApi extends AbstractLectureApi {

    private final LectureTranscriptionRepository lectureTranscriptionRepository;

    public LectureTranscriptionsRepositoryApi(LectureTranscriptionRepository lectureTranscriptionRepository) {
        this.lectureTranscriptionRepository = lectureTranscriptionRepository;
    }

    public Optional<LectureTranscription> findByLectureUnit_Id(Long lectureUnitId) {
        return lectureTranscriptionRepository.findByLectureUnit_Id(lectureUnitId);
    }

    public Optional<LectureTranscription> findByJobId(String jobId) {
        return lectureTranscriptionRepository.findByJobId(jobId);
    }

    public LectureTranscription save(LectureTranscription lectureTranscription) {
        return lectureTranscriptionRepository.save(lectureTranscription);
    }

    public void deleteById(Long id) {
        lectureTranscriptionRepository.deleteById(id);
    }

    public void flush() {
        lectureTranscriptionRepository.flush();
    }

    public List<LectureTranscription> findByTranscriptionStatusAndJobIdIsNotNull(TranscriptionStatus status) {
        return lectureTranscriptionRepository.findByTranscriptionStatusAndJobIdIsNotNull(status);
    }

}
