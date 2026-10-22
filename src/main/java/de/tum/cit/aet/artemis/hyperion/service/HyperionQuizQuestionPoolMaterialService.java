package de.tum.cit.aet.artemis.hyperion.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.api.CompetencyRelationApi;
import de.tum.cit.aet.artemis.core.exception.ApiConditionNotPresentException;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.lecture.api.LectureTranscriptionsRepositoryApi;
import de.tum.cit.aet.artemis.lecture.api.LectureUnitApi;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscriptionSegment;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.TextUnit;

/**
 * Supplies the lecture material that quiz question pool items are grounded in.
 * <p>
 * A lecture unit is groundable when it carries text this service can read: the content of a text unit, the text of an attachment
 * video unit's stored PDF, or that unit's transcript. {@link #extractText} returns that text cut to
 * {@code artemis.hyperion.quiz-pool.max-material-characters}; a PDF that cannot be read contributes nothing and is logged.
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class HyperionQuizQuestionPoolMaterialService {

    private static final Logger log = LoggerFactory.getLogger(HyperionQuizQuestionPoolMaterialService.class);

    private final Optional<CompetencyRelationApi> competencyRelationApi;

    private final Optional<LectureUnitApi> lectureUnitApi;

    private final Optional<LectureTranscriptionsRepositoryApi> transcriptionsApi;

    private final int maxMaterialCharacters;

    public HyperionQuizQuestionPoolMaterialService(Optional<CompetencyRelationApi> competencyRelationApi, Optional<LectureUnitApi> lectureUnitApi,
            Optional<LectureTranscriptionsRepositoryApi> transcriptionsApi, @Value("${artemis.hyperion.quiz-pool.max-material-characters:60000}") int maxMaterialCharacters) {
        this.competencyRelationApi = competencyRelationApi;
        this.lectureUnitApi = lectureUnitApi;
        this.transcriptionsApi = transcriptionsApi;
        if (maxMaterialCharacters < 1) {
            throw new IllegalArgumentException("artemis.hyperion.quiz-pool.max-material-characters must be at least 1, got " + maxMaterialCharacters);
        }
        this.maxMaterialCharacters = maxMaterialCharacters;
    }

    /**
     * Finds the groundable lecture units linked to each competency.
     *
     * @param competencyIds the competencies, in the order they appear in the result
     * @return the groundable units of each competency ordered by id; a competency without one is absent
     */
    public Map<Long, List<LectureUnit>> findGroundableUnits(Collection<Long> competencyIds) {
        CompetencyRelationApi relationApi = competencyRelationApi.orElseThrow(() -> new ApiConditionNotPresentException(CompetencyRelationApi.class, "artemis.atlas.enabled"));
        Map<Long, Set<Long>> unitIdsByCompetency = new LinkedHashMap<>();
        for (Long competencyId : competencyIds) {
            unitIdsByCompetency.put(competencyId, relationApi.findLectureUnitIdsByCompetencyIds(Set.of(competencyId)));
        }
        Map<Long, LectureUnit> units = findUnits(unitIdsByCompetency.values().stream().flatMap(Set::stream).collect(Collectors.toSet()));
        Map<Long, List<LectureUnit>> result = new LinkedHashMap<>();
        for (Map.Entry<Long, Set<Long>> entry : unitIdsByCompetency.entrySet()) {
            List<LectureUnit> groundable = entry.getValue().stream().sorted().map(units::get).filter(unit -> unit != null && isGroundable(unit)).toList();
            if (!groundable.isEmpty()) {
                result.put(entry.getKey(), groundable);
            }
        }
        return result;
    }

    /**
     * Loads lecture units with their lectures.
     *
     * @param ids the units to load
     * @return the units that exist, by id
     */
    public Map<Long, LectureUnit> findUnits(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        LectureUnitApi api = lectureUnitApi.orElseThrow(() -> new ApiConditionNotPresentException(LectureUnitApi.class, "artemis.lecture.enabled"));
        Map<Long, LectureUnit> units = new HashMap<>();
        for (LectureUnit unit : api.findAllByIds(ids)) {
            units.put(unit.getId(), unit);
        }
        return units;
    }

    /**
     * Returns whether the unit carries material questions can be grounded in.
     *
     * @param unit the unit
     * @return {@code true} for a text unit with content and for an attachment video unit with a stored PDF or a transcript
     */
    public boolean isGroundable(LectureUnit unit) {
        if (unit instanceof TextUnit textUnit) {
            return textUnit.getContent() != null && !textUnit.getContent().isBlank();
        }
        if (unit instanceof AttachmentVideoUnit videoUnit) {
            return hasStoredPdf(videoUnit) || transcript(videoUnit).isPresent();
        }
        return false;
    }

    /**
     * Extracts the unit's text.
     *
     * @param unit the unit
     * @return the text cut to the configured budget, or an empty string when the unit has none this service can read
     */
    public String extractText(LectureUnit unit) {
        String text;
        if (unit instanceof TextUnit textUnit) {
            text = textUnit.getContent() == null ? "" : textUnit.getContent();
        }
        else if (unit instanceof AttachmentVideoUnit videoUnit) {
            text = videoUnitText(videoUnit);
        }
        else {
            text = "";
        }
        return truncate(text.strip(), maxMaterialCharacters);
    }

    private String videoUnitText(AttachmentVideoUnit unit) {
        List<String> parts = new ArrayList<>();
        if (hasStoredPdf(unit)) {
            unit.getAttachment().fileLocation().ifPresent(location -> {
                try {
                    parts.add(readPdfText(location.path()));
                }
                catch (IOException e) {
                    log.warn("Cannot read the PDF of lecture unit [{}] for the quiz question pool", unit.getId(), e);
                }
            });
        }
        transcript(unit).ifPresent(transcription -> parts.add(transcriptText(transcription)));
        return parts.stream().filter(part -> !part.isBlank()).collect(Collectors.joining("\n\n"));
    }

    private static boolean hasStoredPdf(AttachmentVideoUnit unit) {
        Attachment attachment = unit.getAttachment();
        return attachment != null && attachment.isStoredPdf();
    }

    private Optional<LectureTranscription> transcript(AttachmentVideoUnit unit) {
        if (unit.getId() == null) {
            return Optional.empty();
        }
        return transcriptionsApi.flatMap(api -> api.findByLectureUnit_Id(unit.getId()))
                .filter(transcription -> transcription.getSegments() != null && !transcription.getSegments().isEmpty());
    }

    private static String transcriptText(LectureTranscription transcription) {
        return transcription.getSegments().stream().map(LectureTranscriptionSegment::text).filter(text -> text != null && !text.isBlank()).collect(Collectors.joining("\n"));
    }

    /**
     * Reads the text of every page of a PDF, in page order.
     *
     * @param file the PDF
     * @return the text
     * @throws IOException when the file cannot be read or is not a PDF
     */
    static String readPdfText(Path file) throws IOException {
        try (PDDocument document = Loader.loadPDF(file.toFile())) {
            return new PDFTextStripper().getText(document);
        }
    }

    static String truncate(String text, int maxCharacters) {
        return text.length() <= maxCharacters ? text : text.substring(0, maxCharacters);
    }
}
