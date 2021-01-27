// See: https://github.com/ls1intum/Artemis/commit/c842a8995f9f837b010d1ddfa3ebe00df7652011
// We changed the notification plugin to also send information about successful tests (previously only failed tests).
// In some cases it needs to be checked explicitly wether a result is legacy or not.
// The date used is the date of the merge: 2019-05-10T22:12:28Z.
import { Result } from 'app/entities/result.model';
import * as moment from 'moment';
import { isMoment } from 'moment';
import { Participation, ParticipationType } from 'app/entities/participation/participation.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { AssessmentType } from 'app/entities/assessment-type.model';

const BAMBOO_RESULT_LEGACY_TIMESTAMP = 1557526348000;

export const isLegacyResult = (result: Result) => {
    if (result.completionDate) {
        return result.completionDate.valueOf() < BAMBOO_RESULT_LEGACY_TIMESTAMP;
    } else {
        return false;
    }
};

/**
 * A result is preliminary if:
 * - The programming exercise buildAndTestAfterDueDate is set
 * - The submission date of the result / result completionDate is before the buildAndTestAfterDueDate
 *
 * Note: We check some error cases in this method as a null value for the given parameters, because the clients using this method might unwillingly provide them (result component).
 *
 * @param latestResult Result with attached Submission - if submission is null, method will use the result completionDate as a reference.
 * @param programmingExercise ProgrammingExercise
 */
export const isResultPreliminary = (latestResult: Result, programmingExercise?: ProgrammingExercise) => {
    if (!programmingExercise) {
        return false;
    }
    let resultCompletionDate = latestResult.completionDate;
    // We use the result completion date
    if (!resultCompletionDate) {
        // in the unlikely case the completion date is not set yet (this should not happen), it is preliminary
        return true;
    }
    // If not a moment date already, try to convert it (e.g. when it is a string).
    if (resultCompletionDate && !isMoment(resultCompletionDate)) {
        resultCompletionDate = moment(resultCompletionDate);
    }
    // When the result completionDate would be null, we have to return here (edge case, every result should have a completionDate).
    if (!resultCompletionDate || !resultCompletionDate.isValid()) {
        return true;
    }
    if (programmingExercise.assessmentType !== AssessmentType.AUTOMATIC && programmingExercise.assessmentDueDate) {
        // either the semi-automatic result is not yet available as last result (then it is preliminary), or it is already available, then it still can be changed)
        return moment().isBefore(moment(programmingExercise.assessmentDueDate));
    }
    if (programmingExercise.assessmentType !== AssessmentType.AUTOMATIC && !programmingExercise.assessmentDueDate) {
        return latestResult.assessmentType === AssessmentType.AUTOMATIC;
    }
    if (programmingExercise.buildAndTestStudentSubmissionsAfterDueDate) {
        return resultCompletionDate.isBefore(moment(programmingExercise.buildAndTestStudentSubmissionsAfterDueDate));
    }
    return false;
};

export const isProgrammingExerciseStudentParticipation = (participation: Participation) => {
    return participation && participation.type === ParticipationType.PROGRAMMING;
};

/**
 * The deadline has passed if:
 * - The dueDate is set and the buildAndTestAfterDueDate is not set and the dueDate has passed.
 * - The dueDate is set and the buildAndTestAfterDueDate is set and the buildAndTestAfterDueDate has passed.
 *
 * @param exercise
 */
export const hasDeadlinePassed = (exercise: ProgrammingExercise) => {
    // If there is no due date, the due date can't pass.
    if (!exercise.dueDate && !exercise.buildAndTestStudentSubmissionsAfterDueDate) {
        return false;
    }
    // The first priority is the buildAndTestAfterDueDate if it is set.
    let referenceDate = exercise.buildAndTestStudentSubmissionsAfterDueDate || exercise.dueDate!;
    if (!isMoment(referenceDate)) {
        referenceDate = moment(referenceDate);
    }
    return referenceDate.isBefore(moment());
};
