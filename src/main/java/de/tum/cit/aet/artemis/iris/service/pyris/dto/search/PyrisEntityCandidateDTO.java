package de.tum.cit.aet.artemis.iris.service.pyris.dto.search;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.globalsearch.dto.SearchableEntityCandidateDTO;

/**
 * One pre-fetched, pre-authorized entity candidate for the Pyris global-search answer pipeline
 * ({@code entityCandidates} on the request). Pyris renders each candidate into a text card and
 * reranks it against lecture content on one shared scale; the access filtering happened in Artemis.
 * Field names match the Pyris {@code EntityCandidateDTO} aliases; dates travel as ISO strings.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisEntityCandidateDTO(String entityType, @Nullable Long entityId, @Nullable Long courseId, @Nullable String courseName, @Nullable String title,
        @Nullable String description, @Nullable String shortName, @Nullable String link, @Nullable String releaseDate, @Nullable String startDate, @Nullable String dueDate,
        @Nullable String endDate, @Nullable String examVisibleDate, @Nullable String examStartDate, @Nullable String examEndDate, @Nullable Double maxPoints,
        @Nullable Long quizDurationSeconds, @Nullable String programmingLanguage, @Nullable String exerciseType, @Nullable String unitType, @Nullable String faqState,
        @Nullable Boolean channelIsPublic) {

    /**
     * Maps a prefetched globalsearch candidate onto the Pyris wire shape.
     *
     * @param candidate the access-filtered candidate from the globalsearch module
     * @return the wire DTO
     */
    public static PyrisEntityCandidateDTO of(SearchableEntityCandidateDTO candidate) {
        return new PyrisEntityCandidateDTO(candidate.entityType(), candidate.entityId(), candidate.courseId(), candidate.courseName(), candidate.title(), candidate.description(),
                candidate.shortName(), candidate.link(), candidate.releaseDate(), candidate.startDate(), candidate.dueDate(), candidate.endDate(), candidate.visibleDate(),
                candidate.examStartDate(), candidate.examEndDate(), candidate.maxPoints(), candidate.quizDurationSeconds(), candidate.programmingLanguage(),
                candidate.exerciseType(), candidate.unitType(), candidate.faqState(), candidate.channelIsPublic());
    }
}
