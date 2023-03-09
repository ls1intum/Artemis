import {
    ConnectionUpdatedAction,
    ContinuedAfterHandedInEarlyAction,
    EndedExamAction,
    ExamAction,
    ExamActionType,
    HandedInEarlyAction,
    SavedExerciseAction,
    StartedExamAction,
    SwitchedExerciseAction,
} from 'app/entities/exam-user-activity.model';
import { Exercise } from 'app/entities/exercise.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { NgxChartsSingleSeriesDataEntry } from 'app/shared/chart/ngx-charts-datatypes';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import dayjs from 'dayjs/esm';

export const createExamActionBasedOnType = (examActionType: ExamActionType): ExamAction => {
    let examAction: ExamAction;

    switch (examActionType) {
        case ExamActionType.STARTED_EXAM:
            examAction = new StartedExamAction(0);
            break;
        case ExamActionType.ENDED_EXAM:
            examAction = new EndedExamAction();
            break;
        case ExamActionType.HANDED_IN_EARLY:
            examAction = new HandedInEarlyAction();
            break;
        case ExamActionType.CONTINUED_AFTER_HAND_IN_EARLY:
            examAction = new ContinuedAfterHandedInEarlyAction();
            break;
        case ExamActionType.SWITCHED_EXERCISE:
            examAction = new SwitchedExerciseAction(0);
            break;
        case ExamActionType.SAVED_EXERCISE:
            examAction = new SavedExerciseAction(false, 0, 0, false, true);
            break;
        case ExamActionType.CONNECTION_UPDATED:
            examAction = new ConnectionUpdatedAction(false);
            break;
    }
    examAction.timestamp = dayjs();
    examAction.examActivityId = 0;
    return examAction;
};

export function createActions() {
    return Object.keys(ExamActionType).map((action) => createExamActionBasedOnType(ExamActionType[action]));
}

export function createTestExercises(amount: number, startIndex = 0): Exercise[] {
    const exercises = [];
    let index = startIndex;
    while (index - startIndex < amount) {
        const exercise = new TextExercise(undefined, undefined);
        exercise.id = index;
        exercise.title = '' + index;
        exercises.push(exercise);
        index++;
    }
    return exercises;
}

export function createSingleSeriesDataEntriesWithTimestamps(timestamps: dayjs.Dayjs[], artemisDatePipe: ArtemisDatePipe): NgxChartsSingleSeriesDataEntry[] {
    return timestamps.map((timestamp) => ({ name: artemisDatePipe.transform(timestamp, 'time', true), value: 0 } as NgxChartsSingleSeriesDataEntry));
}
