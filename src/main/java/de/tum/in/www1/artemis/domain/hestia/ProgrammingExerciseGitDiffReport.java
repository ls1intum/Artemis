package de.tum.in.www1.artemis.domain.hestia;

import java.util.Set;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;

/**
 * A TODO
 */
@Entity
@Table(name = "programming_exercise_git_diff_report")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ProgrammingExerciseGitDiffReport extends DomainObject {

    @OneToOne
    private ProgrammingExercise programmingExercise;

    @Column(name = "template_repository_commit_hash")
    private String templateRepositoryCommitHash;

    @Column(name = "solution_repository_commit_hash")
    private String solutionRepositoryCommitHash;

    // Eager fetching is used here, as the git-diff is useless without the change entries
    // Also CascadeType.ALL is used, so we can handle diffEntries easier
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
