package de.tum.in.www1.artemis.domain.exam.monitoring.actions;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

import de.tum.in.www1.artemis.domain.exam.monitoring.ExamAction;

/**
 * This action indicates whether a student has continued after visiting the handed in early page or not.
 */
@Entity
@DiscriminatorValue("CONTINUED_AFTER_HAND_IN_EARLY")
public class ContinuedAfterHandedInEarlyAction extends ExamAction {
}
