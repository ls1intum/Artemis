package de.tum.cit.aet.artemis.hyperion.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.api.CompetencyRelationApi;
import de.tum.cit.aet.artemis.atlas.api.CourseCompetencyApi;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyRelation;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.iris.exception.IrisException;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisConnectorException;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisConnectorService;
import de.tum.cit.aet.artemis.lecture.api.LectureUnitApi;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.TextUnit;

/**
 * Resolves selected competencies for a course and builds enriched context for LLM quiz question generation.
 */
@Service
@Lazy
public class HyperionCompetencyContextService {

    private static final Logger log = LoggerFactory.getLogger(HyperionCompetencyContextService.class);

    private static final int LECTURE_SNIPPETS_PER_COMPETENCY = 20;

    // Pyris enforces 20 on the search endpoint
    private static final int PYRIS_SEARCH_LIMIT = 20;

    private final Optional<CourseCompetencyApi> courseCompetencyApi;

    private final Optional<CompetencyRelationApi> competencyRelationApi;

    private final Optional<PyrisConnectorService> pyrisConnectorService;

    private final Optional<LectureUnitApi> lectureUnitApi;

    public HyperionCompetencyContextService(Optional<CourseCompetencyApi> courseCompetencyApi, Optional<CompetencyRelationApi> competencyRelationApi,
            Optional<PyrisConnectorService> pyrisConnectorService, Optional<LectureUnitApi> lectureUnitApi) {
        this.courseCompetencyApi = courseCompetencyApi;
        this.competencyRelationApi = competencyRelationApi;
        this.pyrisConnectorService = pyrisConnectorService;
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
     * @param lectureSnippets relevant lecture content snippets retrieved from Pyris (may be empty)
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

        List<String> lectureSnippets = fetchLectureSnippets(selected, selectedIds);

        return new CompetencyContext(selected, relations, lectureSnippets);
    }

    private List<String> fetchLectureSnippets(List<CourseCompetency> selected, Set<Long> selectedIds) {
        if (competencyRelationApi.isEmpty() || lectureUnitApi.isEmpty() || selectedIds.isEmpty()) {
            return List.of();
        }

        Set<Long> linkedLectureUnitIds = competencyRelationApi.get().findLectureUnitIdsByCompetencyIds(selectedIds);
        if (linkedLectureUnitIds.isEmpty()) {
            return List.of();
        }

        List<LectureUnit> lectureUnits = lectureUnitApi.get().findAllByIds(linkedLectureUnitIds);

        List<String> snippets = new ArrayList<>();
        Set<Long> nonTextUnitIds = new HashSet<>();

        // TextUnits: include content directly from the database.
        for (LectureUnit unit : lectureUnits) {
            if (unit instanceof TextUnit textUnit && textUnit.getContent() != null && !textUnit.getContent().isBlank()) {
                snippets.add("[" + unit.getLecture().getTitle() + " – " + unit.getName() + "]\n" + textUnit.getContent());
            }
            else if (unit.getId() != null) {
                nonTextUnitIds.add(unit.getId());
            }
        }

        // Non-text units (slides etc.): retrieve snippets from Pyris, then filter to linked units only.
        if (!nonTextUnitIds.isEmpty() && pyrisConnectorService.isPresent()) {
            for (CourseCompetency competency : selected) {
                String query = competency.getTitle() + (competency.getDescription() != null ? " " + competency.getDescription() : "");
                try {
                    pyrisConnectorService.get().searchLectures(query, PYRIS_SEARCH_LIMIT).stream().filter(result -> nonTextUnitIds.contains(result.lectureUnit().id()))
                            .limit(LECTURE_SNIPPETS_PER_COMPETENCY).map(result -> "[" + result.lecture().name() + " – " + result.lectureUnit().name() + "]\n" + result.snippet())
                            .forEach(snippets::add);
                }
                catch (PyrisConnectorException | IrisException e) {
                    log.warn("Failed to retrieve lecture snippets from Pyris for competency [{}]: {}", competency.getId(), e.getMessage());
                }
            }
        }

        return snippets.stream().distinct().toList();
    }
}
