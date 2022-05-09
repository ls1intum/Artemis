package de.tum.in.www1.artemis.domain.exam.monitoring.actions;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

import de.tum.in.www1.artemis.domain.exam.monitoring.ExamAction;

/**
 * This action indicates whether a student wants to hand in early or not.
 */
@Entity
@DiscriminatorValue("HANDED_IN_EARLY")
public class HandedInEarlyAction extends ExamAction {
}
