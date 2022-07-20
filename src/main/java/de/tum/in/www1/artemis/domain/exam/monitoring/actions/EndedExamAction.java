package de.tum.in.www1.artemis.domain.exam.monitoring.actions;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

import de.tum.in.www1.artemis.domain.exam.monitoring.ExamAction;

/**
 * This action indicates when a student has ended his exam.
 */
@Entity
@DiscriminatorValue("ENDED_EXAM")
public class EndedExamAction extends ExamAction {
}
