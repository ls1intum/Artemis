import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { Participation } from 'app/entities/participation/participation.model';
import { Result } from 'app/entities/result.model';
import { ResultTemplateStatus } from 'app/exercises/shared/result/result.utils';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { hasExerciseDueDatePassed } from 'app/exercises/shared/exercise/exercise.utils';
import dayjs from 'dayjs/esm';
import { ExerciseCacheService } from 'app/exercises/shared/exercise/exercise-cache.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';

export type FeedbackComponentPreparedParams = {
    exercise: Exercise | undefined;
    result: Result;
    exerciseType?: ExerciseType;
    showScoreChart?: boolean;
    messageKey?: string;
    latestDueDate?: dayjs.Dayjs;
    showMissingAutomaticFeedbackInformation?: boolean;
};

/**
 * Prepares the parameters for the feedback component {@link FeedbackComponent}
 */
export function prepareFeedbackComponentParameters(
    exercise: Exercise | undefined,
    result: Result,
    participation: Participation,
    templateStatus: ResultTemplateStatus,
    latestDueDate: dayjs.Dayjs | undefined,
    exerciseService: ExerciseCacheService | ExerciseService,
) {
    const preparedParameters: FeedbackComponentPreparedParams = {
        exercise: exercise,
        result: result,
    };

    if (!result.participation) {
        result.participation = participation;
    }

    if (exercise) {
        preparedParameters.exerciseType = exercise.type!;
        preparedParameters.showScoreChart = true;
    }

    if (templateStatus === ResultTemplateStatus.MISSING) {
        preparedParameters.messageKey = 'artemisApp.result.notLatestSubmission';
    }

    if (result?.assessmentType === AssessmentType.AUTOMATIC && exercise?.type === ExerciseType.PROGRAMMING && hasExerciseDueDatePassed(exercise, participation)) {
        determineShowMissingAutomaticFeedbackInformation(latestDueDate, exerciseService, preparedParameters, exercise);
    }

    return preparedParameters;
}

/**
 * Determines if some information about testcases could still be hidden because of later individual due dates
 */
function determineShowMissingAutomaticFeedbackInformation(
    latestDueDate: dayjs.Dayjs | undefined,
    exerciseService: ExerciseCacheService | ExerciseService,
    preparedParameters: FeedbackComponentPreparedParams,
    exercise: Exercise,
) {
    if (latestDueDate) {
        setShowMissingAutomaticFeedbackInformation(latestDueDate, preparedParameters);
    } else {
        exerciseService.getLatestDueDate(exercise!.id!).subscribe((latestDueDate) => {
            if (latestDueDate) {
                setShowMissingAutomaticFeedbackInformation(latestDueDate, preparedParameters);
            }
        });
    }
}

function setShowMissingAutomaticFeedbackInformation(latestDueDate: dayjs.Dayjs, preparedParameters: FeedbackComponentPreparedParams) {
    preparedParameters.latestDueDate = latestDueDate;
    preparedParameters.showMissingAutomaticFeedbackInformation = dayjs().isBefore(latestDueDate);
}
