package de.tum.in.www1.artemis.web.rest.dto.hestia;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ProgrammingExerciseFullGitDiffReportDTO {

    private String templateRepositoryCommitHash;

    private String solutionRepositoryCommitHash;

    private Set<ProgrammingExerciseFullGitDiffEntryDTO> entries;

    public String getTemplateRepositoryCommitHash() {
        return templateRepositoryCommitHash;
    }

    public void setTemplateRepositoryCommitHash(String templateRepositoryCommitHash) {
        this.templateRepositoryCommitHash = templateRepositoryCommitHash;
    }

    public String getSolutionRepositoryCommitHash() {
        return solutionRepositoryCommitHash;
    }

    public void setSolutionRepositoryCommitHash(String solutionRepositoryCommitHash) {
        this.solutionRepositoryCommitHash = solutionRepositoryCommitHash;
    }

    public Set<ProgrammingExerciseFullGitDiffEntryDTO> getEntries() {
        return entries;
    }

    public void setEntries(Set<ProgrammingExerciseFullGitDiffEntryDTO> entries) {
        this.entries = entries;
    }
}
