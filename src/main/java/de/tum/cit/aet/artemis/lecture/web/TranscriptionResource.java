package de.tum.cit.aet.artemis.lecture.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.Transcription;
import de.tum.cit.aet.artemis.lecture.domain.TranscriptionSegment;
import de.tum.cit.aet.artemis.lecture.dto.TranscriptionDTO;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitRepository;
import de.tum.cit.aet.artemis.lecture.repository.TranscriptionRepository;

@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class TranscriptionResource {

    private static final Logger log = LoggerFactory.getLogger(TranscriptionResource.class);

    private final TranscriptionRepository transcriptionRepository;

    private final LectureRepository lectureRepository;

    private final LectureUnitRepository lectureUnitRepository;

    public TranscriptionResource(TranscriptionRepository transcriptionRepository, LectureRepository lectureRepository, LectureUnitRepository lectureUnitRepository) {
        this.transcriptionRepository = transcriptionRepository;
        this.lectureRepository = lectureRepository;
        this.lectureUnitRepository = lectureUnitRepository;
    }

    /**
     * POST /transcription : Create a new transcription.
     *
     * @param transcriptionDTO the transcription object to create
     * @return the ResponseEntity with status 201 (Created) and with body the new transcription, or with status 400 (Bad Request) if the transcription has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping(value = "transcription")
    @EnforceAtLeastEditor
    public ResponseEntity<Transcription> createTranscription(@RequestBody TranscriptionDTO transcriptionDTO) throws URISyntaxException {
        Lecture lecture = lectureRepository.findById(transcriptionDTO.lectureId()).orElseThrow(() -> new EntityNotFoundException("no lecture found for this id"));

        List<TranscriptionSegment> segments = transcriptionDTO.segments().stream().map(segment -> {
            LectureUnit lectureUnit = lectureUnitRepository.findById(segment.lectureUnitId()).orElseThrow(() -> new EntityNotFoundException("no lecture unit found for this id"));
            return new TranscriptionSegment(segment.startTime(), segment.endTime(), segment.text(), lectureUnit, segment.slideNumber());
        }).toList();

        Transcription transcription = new Transcription(lecture, transcriptionDTO.language(), segments);
        transcription.setId(null);

        Transcription result = transcriptionRepository.save(transcription);

        return ResponseEntity.created(new URI("/api/transcriptions/" + result.getId())).body(result);
    }
}
