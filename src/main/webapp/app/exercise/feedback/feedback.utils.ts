import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { ResultTemplateStatus } from 'app/exercise/result/result.utils';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { hasExerciseDueDatePassed } from 'app/exercise/util/exercise.utils';
import dayjs from 'dayjs/esm';
import { ExerciseCacheService } from 'app/exercise/services/exercise-cache.service';
import { ExerciseService } from 'app/exercise/services/exercise.service';

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
