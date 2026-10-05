package de.tum.cit.aet.artemis.exercise.service;

import de.tum.cit.aet.artemis.exercise.domain.Submission;

/**
 * A saved submission together with the participation the response reports for it.
 * <p>
 * The submit path resolves its participation as a projection and never loads it as an entity, so the participation
 * cannot be read back off the saved submission - its association there is only the foreign key. The mapped
 * participation travels alongside instead, so the resource can answer without another read.
 *
 * @param submission    the submission as it was written
 * @param participation the participation the response reports
 * @param <S>           the submission type
 * @param <P>           the participation DTO the module's response reports
 */
public record SavedSubmission<S extends Submission, P>(S submission, P participation) {
}
