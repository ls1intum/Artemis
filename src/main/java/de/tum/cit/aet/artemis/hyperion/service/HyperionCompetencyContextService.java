package de.tum.cit.aet.artemis.hyperion.service;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.api.CompetencyRelationApi;
import de.tum.cit.aet.artemis.atlas.api.CourseCompetencyApi;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyRelation;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.core.FilePathType;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.lecture.api.LectureUnitApi;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentType;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.TextUnit;

/**
 * Resolves selected competencies for a course and builds enriched context for LLM quiz question generation.
 */
@Service
@Lazy
public class HyperionCompetencyContextService {

    private static final Logger log = LoggerFactory.getLogger(HyperionCompetencyContextService.class);

    private static final int MAX_TOTAL_CHARS = 50_000;

    private final Optional<CourseCompetencyApi> courseCompetencyApi;

    private final Optional<CompetencyRelationApi> competencyRelationApi;

    private final Optional<LectureUnitApi> lectureUnitApi;

    public HyperionCompetencyContextService(Optional<CourseCompetencyApi> courseCompetencyApi, Optional<CompetencyRelationApi> competencyRelationApi,
            Optional<LectureUnitApi> lectureUnitApi) {
        this.courseCompetencyApi = courseCompetencyApi;
        this.competencyRelationApi = competencyRelationApi;
        this.lectureUnitApi = lectureUnitApi;
    }

    /**
     * Returns whether competency-graph mode is available (Atlas module must be enabled).
     */
    public boolean isAvailable() {
        return courseCompetencyApi.isPresent();
    }

    /**
     * Holds enriched context about a set of selected competencies derived from the graph.
     *
     * @param competencies    the competencies in the selected set
     * @param relations       relations involving the selected competencies (ASSUMES / EXTENDS / MATCHES)
     * @param lectureSnippets relevant lecture content snippets extracted from linked units (may be empty)
     */
    public record CompetencyContext(List<CourseCompetency> competencies, Set<CompetencyRelation> relations, List<String> lectureSnippets) {
    }

    /**
     * Resolves the selected competencies and builds enriched context for the LLM prompt.
     *
     * @param courseId      the course to load competencies from
     * @param competencyIds the IDs of the selected competencies
     * @return enriched context for the LLM prompt
     */
    public CompetencyContext computeContext(long courseId, List<Long> competencyIds) {
        CourseCompetencyApi api = courseCompetencyApi
                .orElseThrow(() -> new BadRequestAlertException("Competency-graph mode requires the Atlas module", "QuizQuestionGeneration", "atlasNotEnabled"));
        List<CourseCompetency> allCompetencies = new ArrayList<>(api.findAllForCourse(courseId));

        Map<Long, CourseCompetency> competencyById = new HashMap<>();
        for (CourseCompetency c : allCompetencies) {
            if (c.getId() != null) {
                competencyById.put(c.getId(), c);
            }
        }

        List<CourseCompetency> selected = competencyIds.stream().map(competencyById::get).filter(Objects::nonNull).toList();

        Set<Long> selectedIds = selected.stream().map(CourseCompetency::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<CompetencyRelation> relations = competencyRelationApi.map(relApi -> relApi.findRelationsInvolvingCompetencies(courseId, selectedIds)).orElse(Set.of());

        List<String> lectureSnippets = fetchLectureSnippets(selectedIds);

        return new CompetencyContext(selected, relations, lectureSnippets);
    }

    private List<String> fetchLectureSnippets(Set<Long> selectedIds) {
        if (competencyRelationApi.isEmpty() || lectureUnitApi.isEmpty() || selectedIds.isEmpty()) {
            return List.of();
        }

        Set<Long> linkedLectureUnitIds = competencyRelationApi.get().findLectureUnitIdsByCompetencyIds(selectedIds);
        if (linkedLectureUnitIds.isEmpty()) {
            return List.of();
        }

        List<LectureUnit> lectureUnits = lectureUnitApi.get().findAllByIds(linkedLectureUnitIds);

        // Extract all texts upfront so we can redistribute unused budget from short units to long ones.
        record UnitText(LectureUnit unit, String text) {
        }
        List<UnitText> extracted = new ArrayList<>();
        for (LectureUnit unit : lectureUnits) {
            String text = extractUnitText(unit);
            if (text != null && !text.isBlank()) {
                extracted.add(new UnitText(unit, text));
            }
        }
        if (extracted.isEmpty()) {
            return List.of();
        }

        // Two-pass fair allocation: equal share first, then redistribute leftover from short units.
        int equalShare = MAX_TOTAL_CHARS / extracted.size();
        int leftover = 0;
        for (UnitText ut : extracted) {
            leftover += Math.max(0, equalShare - ut.text().length());
        }
        int oversizeCount = (int) extracted.stream().filter(ut -> ut.text().length() > equalShare).count();
        int bonus = oversizeCount > 0 ? leftover / oversizeCount : 0;

        List<String> snippets = new ArrayList<>();
        for (UnitText ut : extracted) {
            int budget = ut.text().length() <= equalShare ? equalShare : equalShare + bonus;
            String truncated = ut.text().length() > budget ? ut.text().substring(0, budget) + "…[truncated]" : ut.text();
            snippets.add("[" + ut.unit().getLecture().getTitle() + " – " + ut.unit().getName() + "]\n" + truncated);
        }

        return snippets.stream().distinct().toList();
    }

    private String extractUnitText(LectureUnit unit) {
        if (unit instanceof TextUnit textUnit) {
            return textUnit.getContent();
        }
        if (unit instanceof AttachmentVideoUnit avu && avu.getAttachment() != null && avu.getAttachment().getAttachmentType() == AttachmentType.FILE
                && avu.getAttachment().getLink() != null && avu.getAttachment().getLink().endsWith(".pdf")) {
            return extractPdfText(avu.getAttachment().getLink());
        }
        return null;
    }

    private String extractPdfText(String attachmentLink) {
        try {
            Path path = FilePathConverter.fileSystemPathForExternalUri(URI.create(attachmentLink), FilePathType.ATTACHMENT_UNIT);
            byte[] fileBytes = Files.readAllBytes(path);
            try (PDDocument doc = Loader.loadPDF(fileBytes)) {
                return new PDFTextStripper().getText(doc);
            }
        }
        catch (IOException e) {
            log.warn("Could not extract text from PDF attachment [{}]: {}", attachmentLink, e.getMessage());
            return null;
        }
    }
}
