package de.tum.cit.aet.artemis.plagiarism.domain;

import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "discriminator", discriminatorType = DiscriminatorType.STRING)
@DiscriminatorValue("PSE")
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
