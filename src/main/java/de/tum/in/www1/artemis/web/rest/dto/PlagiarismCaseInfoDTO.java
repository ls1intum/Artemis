package de.tum.in.www1.artemis.web.rest.dto;

import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismVerdict;

/**
 * A DTO with a subset of Plagiarism Case fields for displaying relevant info to a student.
 */
public class PlagiarismCaseInfoDTO {

    private Long id;

    private PlagiarismVerdict verdict;

    public PlagiarismCaseInfoDTO(Long id, PlagiarismVerdict verdict) {
        this.id = id;
        this.verdict = verdict;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public PlagiarismVerdict getVerdict() {
        return verdict;
    }

    public void setVerdict(PlagiarismVerdict verdict) {
        this.verdict = verdict;
    }
}
