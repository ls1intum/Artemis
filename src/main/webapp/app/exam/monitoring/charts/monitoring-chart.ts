import { GraphColors } from 'app/entities/statistics.model';
import { ExamAction, ExamActionType, SavedExerciseAction, SwitchedExerciseAction } from 'app/entities/exam-user-activity.model';
import { groupBy } from 'lodash';
import { Exam } from 'app/entities/exam.model';
import { NgxChartsEntry } from 'app/shared/chart/ngx-charts-datatypes';
import { Color } from '@swimlane/ngx-charts';
import dayjs from 'dayjs/esm';

// Collection of colors used for the exam monitoring
const colors = [GraphColors.LIGHT_BLUE, GraphColors.LIGHT_GREY, GraphColors.BLUE, GraphColors.GREY, GraphColors.DARK_BLUE];

/**
 * Returns a color based on the index (modulo).
 * @param index
 * @return selected color based on index
 */
export function getColor(index: number) {
    return colors[index % colors.length];
}

/**
 * Groups actions by ceiled timestamp or timestamp.
 * @param examActions array of actions
 * @return grouped actions
 */
export function groupActionsByTimestamp(examActions: ExamAction[]) {
    return groupBy(examActions, (action: ExamAction) => {
        return action.ceiledTimestamp ?? action.timestamp;
    });
}

/**
 * Groups actions by type.
 * @param examActions array of actions
 * @return grouped actions
 */
export function groupActionsByType(examActions: ExamAction[]) {
    return groupBy(examActions, (action: ExamAction) => {
        return action.type;
    });
}

/**
 * Groups actions by activity id.
 * @param examActions array of actions
 * @return grouped actions
 */
export function groupActionsByActivityId(examActions: ExamAction[]) {
    return groupBy(examActions, (action: ExamAction) => {
        return action.examActivityId;
    });
}

/**
 * Get the last action grouped by activity id.
 * @param examActions array of actions
 * @return filtered and grouped actions
 */
export function getLastActionGroupedByActivityId(examActions: ExamAction[]) {
    const activityActionMap: Map<number, ExamAction> = new Map();
    for (const [key, actions] of Object.entries(groupActionsByActivityId(examActions))) {
        activityActionMap.set(Number(key), (actions.sort((a, b) => (a.timestamp?.isAfter(b.timestamp) ? 1 : -1)) as ExamAction[]).last()!);
    }
    return activityActionMap;
}

/**
 * Get actions filtered for SavedExerciseAction and grouped by activity id.
 * @param examActions array of actions
 * @return filtered and grouped actions
 */
export function getSavedExerciseActionsGroupedByActivityId(examActions: ExamAction[]) {
    return groupActionsByActivityId(examActions.filter((action) => action.type === ExamActionType.SAVED_EXERCISE));
}

/**
 * Get actions filtered for SwitchedExerciseAction and grouped by activity id.
 * @param examActions array of actions
 * @return filtered and grouped actions
 */
export function getSwitchedExerciseActionsGroupedByActivityId(examActions: ExamAction[]) {
    return groupActionsByActivityId(examActions.filter((action) => action.type === ExamActionType.SWITCHED_EXERCISE));
}

/**
 * Returns the current exercise of student.
 * @param examActions array of actions
 * @return current exercise of student as map
 */
export function getCurrentExercisePerStudent(examActions: ExamAction[]) {
    const currentExercisePerStudent: Map<number, number | undefined> = new Map();
    const groupedByActivityId = getLastActionGroupedByActivityId(examActions);
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    for (const [_, action] of groupedByActivityId) {
        let typedAction = undefined;
        if (action.type === ExamActionType.SWITCHED_EXERCISE) {
            typedAction = action as SwitchedExerciseAction;
        } else if (action.type === ExamActionType.SAVED_EXERCISE) {
            typedAction = action as SavedExerciseAction;
        }
        if (typedAction && typedAction.exerciseId !== undefined) {
            currentExercisePerStudent.set(typedAction.examActivityId!, typedAction.exerciseId);
        } else {
            currentExercisePerStudent.set(action.examActivityId!, undefined);
        }
    }
    return currentExercisePerStudent;
}

/**
 * Updates the current exercise of student.
 * @param examAction received action
 * @param currentExercisePerStudent current exercise per student
 */
export function updateCurrentExerciseOfStudent(examAction: ExamAction, currentExercisePerStudent: Map<number, number | undefined>) {
    let typedAction = undefined;
    if (examAction.type === ExamActionType.SWITCHED_EXERCISE) {
        typedAction = examAction as SwitchedExerciseAction;
    } else if (examAction.type === ExamActionType.SAVED_EXERCISE) {
        typedAction = examAction as SavedExerciseAction;
    }
    if (typedAction && typedAction.exerciseId !== undefined) {
        currentExercisePerStudent.set(typedAction.examActivityId!, typedAction.exerciseId);
    } else {
        currentExercisePerStudent.set(examAction.examActivityId!, undefined);
    }
}

/**
 * Converts the current exercise per student map to amount of students per exercise
 * @param currentExercisePerStudent current exercise of student as map
 * @return students per exercise as map
 */
export function convertCurrentExercisePerStudentMapToNumberOfStudentsPerExerciseMap(currentExercisePerStudent: Map<number, number | undefined>) {
    const numberOfStudentsPerExercise: Map<number, number> = new Map();
    for (const exercise of currentExercisePerStudent.values()) {
        if (exercise !== undefined) {
            numberOfStudentsPerExercise.set(exercise, (numberOfStudentsPerExercise.get(exercise) ?? 0) + 1);
        }
    }
    return numberOfStudentsPerExercise;
}

/**
 * Converts the exercise map to ngx data and insert the bar color accordingly.
 * @param exam the current exam
 * @param exerciseAmountMap amount of students per exercise as map
 * @param ngxData data of the chart
 * @param ngxColor color of the chart
 */
export function insertNgxDataAndColorForExerciseMap(exam: Exam | undefined, exerciseAmountMap: Map<number, number>, ngxData: NgxChartsEntry[], ngxColor: Color) {
    exam?.exerciseGroups?.forEach((group, index) => {
        group.exercises?.forEach((exercise) => {
            ngxData.push({ name: exercise.title ?? '', value: exerciseAmountMap.get(exercise.id!) ?? 0 });
            ngxColor.domain.push(getColor(index));
        });
    });
}

/**
 * Method to round the provided timestamp to the specified seconds
 * @param timestamp timestamp to round
 * @param seconds specified gap
 */
export function ceilDayjsSeconds(timestamp: dayjs.Dayjs, seconds: number) {
    return timestamp.add(seconds - (timestamp.get('seconds') % seconds), 'seconds').startOf('seconds');
}
