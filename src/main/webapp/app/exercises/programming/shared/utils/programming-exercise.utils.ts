// See: https://github.com/ls1intum/Artemis/commit/c842a8995f9f837b010d1ddfa3ebe00df7652011
// We changed the notification plugin to also send information about successful tests (previously only failed tests).
// In some cases it needs to be checked explicitly wether a result is legacy or not.
// The date used is the date of the merge: 2019-05-10T22:12:28Z.
import { Result } from 'app/entities/result.model';
import dayjs from 'dayjs/esm';
import { Participation, ParticipationType } from 'app/entities/participation/participation.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { SubmissionType } from 'app/entities/submission.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { AssessmentType } from 'app/entities/assessment-type.model';

const BAMBOO_RESULT_LEGACY_TIMESTAMP = 1557526348000;

export const isLegacyResult = (result: Result) => {
    if (result.completionDate) {
        return result.completionDate.valueOf() < BAMBOO_RESULT_LEGACY_TIMESTAMP;
    } else {
        return false;
    }
};

export const createBuildPlanUrl = (template: string, projectKey: string, buildPlanId: string): string | undefined => {
    if (template && projectKey && buildPlanId) {
        return template.replace('{buildPlanId}', buildPlanId).replace('{projectKey}', projectKey);
    }
};

export const createCommitUrl = (
    template: string | undefined,
    projectKey: string | undefined,
    participation: Participation | undefined,
    submission: ProgrammingSubmission | undefined,
): string | undefined => {
    const projectKeyLowerCase = projectKey?.toLowerCase();
    let repoSlugPostfix: string | undefined = undefined;
    if (participation?.type === ParticipationType.PROGRAMMING) {
        const studentParticipation = participation as ProgrammingExerciseStudentParticipation;
        if (studentParticipation.repositoryUrl) {
            repoSlugPostfix = studentParticipation.participantIdentifier;
        }
    } else if (participation?.type === ParticipationType.TEMPLATE) {
        // In case of a test submisson, we need to use the test repository
        repoSlugPostfix = submission?.type === SubmissionType.TEST ? 'tests' : 'exercise';
    } else if (participation?.type === ParticipationType.SOLUTION) {
        // In case of a test submisson, we need to use the test repository
        repoSlugPostfix = submission?.type === SubmissionType.TEST ? 'tests' : 'solution';
    }
    if (repoSlugPostfix && template && projectKeyLowerCase) {
        return template
            .replace('{projectKey}', projectKeyLowerCase)
            .replace('{repoSlug}', projectKeyLowerCase + '-' + repoSlugPostfix)
            .replace('{commitHash}', submission?.commitHash ?? '');
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
    // If not a dayjs date already, try to convert it (e.g. when it is a string).
    if (resultCompletionDate && !dayjs.isDayjs(resultCompletionDate)) {
        resultCompletionDate = dayjs(resultCompletionDate);
    }
    // When the result completionDate is invalid, we have to return here (edge case, every result should have a valid completionDate).
    if (!resultCompletionDate.isValid()) {
        return true;
    }
    // If an exercise's assessment type is not automatic the last result is supposed to be manually assessed
    if (programmingExercise.assessmentType !== AssessmentType.AUTOMATIC) {
        // either the semi-automatic result is not yet available as last result (then it is preliminary), or it is already available (then it still can be changed)
        if (programmingExercise.assessmentDueDate) {
            return dayjs().isBefore(dayjs(programmingExercise.assessmentDueDate));
        }
        // in case the assessment due date is not set, the assessment type of the latest result is checked. If it is automatic the result is still preliminary.
        return latestResult.assessmentType === AssessmentType.AUTOMATIC;
    }
    // When the due date for the automatic building and testing is available but not reached, the result is preliminary
    if (programmingExercise.buildAndTestStudentSubmissionsAfterDueDate) {
        return resultCompletionDate.isBefore(dayjs(programmingExercise.buildAndTestStudentSubmissionsAfterDueDate));
    }
    return false;
};

export const isProgrammingExerciseStudentParticipation = (participation: Participation) => {
    return participation && participation.type === ParticipationType.PROGRAMMING;
};

export const isProgrammingExerciseParticipation = (participation: Participation | undefined): boolean => {
    return (participation?.type && [ParticipationType.PROGRAMMING, ParticipationType.TEMPLATE, ParticipationType.SOLUTION].includes(participation.type)) || false;
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
    if (!dayjs.isDayjs(referenceDate)) {
        referenceDate = dayjs(referenceDate);
    }
    return referenceDate.isBefore(dayjs());
};
