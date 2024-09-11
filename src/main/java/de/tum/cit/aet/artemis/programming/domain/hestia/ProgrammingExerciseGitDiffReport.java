package de.tum.cit.aet.artemis.programming.domain.hestia;

import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * A git-diff report representing a git-diff between the template and solution repositories of a ProgrammingExercise.
 * This is a light version of the git-diff that does not contain the actual code in its entries.
 * The entries rather contain the startLine and the length of the code blocks from both repositories.
 * It can be converted to a full git-diff report with the ProgrammingExerciseGitDiffReportService.
 */
@Entity
@Table(name = "programming_exercise_git_diff_report")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ProgrammingExerciseGitDiffReport extends DomainObject {

    // FetchType.LAZY here, as we always already have the exercise when retrieving the report
    @OneToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties("gitDiffReport")
    private ProgrammingExercise programmingExercise;

    @Column(name = "template_repository_commit_hash")
    private String templateRepositoryCommitHash;

    @Column(name = "solution_repository_commit_hash")
    private String solutionRepositoryCommitHash;

    // Eager fetching is used here, as the git-diff is useless without the change entries
    // Also CascadeType.ALL is used, so we can handle the diff entries easier
    @OneToMany(mappedBy = "gitDiffReport", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JsonIgnoreProperties("gitDiffReport")
    private Set<ProgrammingExerciseGitDiffEntry> entries;

    public ProgrammingExercise getProgrammingExercise() {
        return programmingExercise;
    }

    public void setProgrammingExercise(ProgrammingExercise programmingExercise) {
        this.programmingExercise = programmingExercise;
    }

    public String getTemplateRepositoryCommitHash() {
        return templateRepositoryCommitHash;
    }

    public void setTemplateRepositoryCommitHash(String templateRepositoryCommit) {
        this.templateRepositoryCommitHash = templateRepositoryCommit;
    }

    public String getSolutionRepositoryCommitHash() {
        return solutionRepositoryCommitHash;
    }

    public void setSolutionRepositoryCommitHash(String solutionRepositoryCommit) {
        this.solutionRepositoryCommitHash = solutionRepositoryCommit;
    }

    public Set<ProgrammingExerciseGitDiffEntry> getEntries() {
        return entries;
    }

    public void setEntries(Set<ProgrammingExerciseGitDiffEntry> entries) {
        this.entries = entries;
    }
}
