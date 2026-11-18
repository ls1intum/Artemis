package de.tum.cit.aet.artemis.plagiarism.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismCase;

/**
 * Lightweight, cycle-free projection of {@link PlagiarismCase} for embedding inside {@code PostResponseDTO},
 * which previously serialized {@code Post.plagiarismCase} via {@code @JsonIncludeProperties({"id"})}.
 * <p>
 * Only the id is exposed, to match that prior wire shape exactly — the messaging client reads only
 * {@code post.plagiarismCase.id}. Exposing further fields would both leak more than before and force extra
 * association loads in {@link #from(PlagiarismCase)}.
 *
 * @param id the plagiarism case id
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PlagiarismCaseRefDTO(Long id) {

    /**
     * Build a {@link PlagiarismCaseRefDTO} from a {@link PlagiarismCase} entity. Returns {@code null}
     * if the input is {@code null} so callers can map nullable references without a guard.
     *
     * @param plagiarismCase the entity to project, may be {@code null}
     * @return the projected reference, or {@code null} when input is {@code null}
     */
    public static @Nullable PlagiarismCaseRefDTO from(@Nullable PlagiarismCase plagiarismCase) {
        if (plagiarismCase == null) {
            return null;
        }
        return new PlagiarismCaseRefDTO(plagiarismCase.getId());
    }
}
