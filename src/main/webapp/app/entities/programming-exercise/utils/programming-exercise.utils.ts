// See: https://github.com/ls1intum/Artemis/commit/c842a8995f9f837b010d1ddfa3ebe00df7652011
// We changed the notification plugin to also send information about successful tests (previously only failed tests).
// In some cases it needs to be checked explicitly wether a result is legacy or not.
// The date used is the date of the merge: 2019-05-10T22:12:28Z.
import { Result } from 'app/entities/result';
import { ProgrammingExercise, programmingExerciseRoute } from 'app/entities/programming-exercise';
import * as moment from 'moment';
import { Participation, ParticipationType, StudentParticipation } from 'app/entities/participation';
import { ExerciseType } from 'app/entities/exercise';

const BAMBOO_RESULT_LEGACY_TIMESTAMP = 1557526348000;

export const isLegacyResult = (result: Result) => {
    return result.completionDate!.valueOf() < BAMBOO_RESULT_LEGACY_TIMESTAMP;
};

export const hasBuildAndTestAfterDueDatePassed = (programmingExercise: ProgrammingExercise) => {
    return (
        !programmingExercise.buildAndTestStudentSubmissionsAfterDueDate ||
        (programmingExercise.buildAndTestStudentSubmissionsAfterDueDate && moment(programmingExercise.buildAndTestStudentSubmissionsAfterDueDate).isBefore(moment.now()))
    );
};

export const isProgrammingExerciseStudentParticipation = (participation: Participation) => {
    return participation.type && participation.type === ParticipationType.PROGRAMMING;
};
