// See: https://github.com/ls1intum/Artemis/commit/c842a8995f9f837b010d1ddfa3ebe00df7652011
// We changed the notification plugin to also send information about successful tests (previously only failed tests).
// In some cases it needs to be checked explicitly wether a result is legacy or not.
// The date used is the date of the merge: 2019-05-10T22:12:28Z.
import { Result } from 'app/entities/result';
import { ProgrammingExercise, programmingExerciseRoute } from 'app/entities/programming-exercise';
import * as moment from 'moment';
import { Participation, ParticipationType, StudentParticipation } from 'app/entities/participation';
import { ExerciseType } from 'app/entities/exercise';
import { ProgrammingSubmission } from 'app/entities/programming-submission';

const BAMBOO_RESULT_LEGACY_TIMESTAMP = 1557526348000;

export const isLegacyResult = (result: Result) => {
    return result.completionDate!.valueOf() < BAMBOO_RESULT_LEGACY_TIMESTAMP;
};

/**
 * A result is preliminary if:
 * - The programming exercise buildAndTestAfterDueDate is set
 * - The submission date of the result is before the buildAndTestAfterDueDate
 *
 * Note: We check some error cases in this method as a null value for the given parameters, because the clients using this method might unwillingly provide them (result component).
 * TODO: Remove the null checks when the result component is refactored.
 *
 * @param result Result with attached Submission - if submission is null, method will return false.
 * @param programmingExercise ProgrammingExercise
 */
export const isResultPreliminary = (result: Result | null, programmingExercise: ProgrammingExercise | null) => {
    if (!programmingExercise || !result) {
        return false;
    }
    const { submission } = result;
    if (!submission || !submission.submissionDate) {
        return false;
    }
    return (
        !!programmingExercise.buildAndTestStudentSubmissionsAfterDueDate &&
        submission.submissionDate.isBefore(moment(programmingExercise.buildAndTestStudentSubmissionsAfterDueDate))
    );
};

export const isProgrammingExerciseStudentParticipation = (participation: Participation) => {
    return participation && participation.type === ParticipationType.PROGRAMMING;
};
