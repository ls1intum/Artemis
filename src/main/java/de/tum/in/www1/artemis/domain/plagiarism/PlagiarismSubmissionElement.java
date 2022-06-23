package de.tum.in.www1.artemis.domain.plagiarism;

import javax.persistence.*;

import org.hibernate.annotations.DiscriminatorOptions;

import de.tum.in.www1.artemis.domain.DomainObject;

@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "discriminator", discriminatorType = DiscriminatorType.STRING)
@DiscriminatorValue("PSE")
@DiscriminatorOptions(force = true)
@Table(name = "plagiarism_submission_element")
public class PlagiarismSubmissionElement extends DomainObject {

    @ManyToOne
    private PlagiarismSubmission<?> plagiarismSubmission;

    public PlagiarismSubmission<?> getPlagiarismSubmission() {
        return plagiarismSubmission;
    }

    public void setPlagiarismSubmission(PlagiarismSubmission<?> plagiarismSubmission) {
        this.plagiarismSubmission = plagiarismSubmission;
    }
}
