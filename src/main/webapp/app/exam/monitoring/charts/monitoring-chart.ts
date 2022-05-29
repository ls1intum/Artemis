import { GraphColors } from 'app/entities/statistics.model';
import { ExamAction, ExamActionType, SwitchedExerciseAction } from 'app/entities/exam-user-activity.model';
import { groupBy } from 'lodash';
import { Exam } from 'app/entities/exam.model';
import { NgxChartsSingleSeriesDataEntry } from 'app/shared/chart/ngx-charts-datatypes';
import { Color } from '@swimlane/ngx-charts';

// Chart content
export class ChartData {
    name: string;
    value: any;

    constructor(name: string, value: any) {
        this.name = name;
        this.value = value;
    }
}

export class ChartSeriesData {
    name: string;
    series: ChartData[];

    constructor(name: string, series: ChartData[]) {
        this.name = name;
        this.series = series;
    }
}

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
 * Groups actions by timestamp.
 * @param examActions array of actions
 * @return grouped actions
 */
export function groupActionsByTimestamp(examActions: ExamAction[]) {
    return groupBy(examActions, (action: ExamAction) => {
        return action.timestamp;
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
 * Get actions filtered for SwitchedExerciseAction and grouped by activity id.
 * @param examActions array of actions
 * @return filtered and grouped actions
 */
export function getSwitchedExerciseActionsGroupedByActivityId(examActions: ExamAction[]) {
    return groupActionsByActivityId(examActions.filter((action) => action.type === ExamActionType.SWITCHED_EXERCISE));
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
 * Returns the current amount of students per exercise.
 * @param examActions array of actions
 * @return amount of students per exercise as map
 */
export function getCurrentAmountOfStudentsPerExercises(examActions: ExamAction[]): Map<number, number> {
    const exerciseAmountMap: Map<number, number> = new Map();
    const groupedByActivityId = getSwitchedExerciseActionsGroupedByActivityId(examActions);
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    for (const [_, value] of Object.entries(groupedByActivityId)) {
        const sortedByTimestamp = value.sort((a, b) => (a.timestamp?.isAfter(b.timestamp) ? 1 : -1));
        if (sortedByTimestamp.length === 0) {
            continue;
        }
        const last = sortedByTimestamp.last()! as SwitchedExerciseAction;
        exerciseAmountMap.set(last.exerciseId!, (exerciseAmountMap.get(last.exerciseId!) ?? 0) + 1);
    }
    return exerciseAmountMap;
}

/**
 * Converts the exercise map to ngx data and insert the bar color accordingly.
 * @param exam the current exam
 * @param exerciseAmountMap amount of students per exercise as map
 * @param ngxData data of the chart
 * @param ngxColor color of the chart
 */
export function insertNgxDataAndColorForExerciseMap(exam: Exam, exerciseAmountMap: Map<number, number>, ngxData: NgxChartsSingleSeriesDataEntry[], ngxColor: Color) {
    exam.exerciseGroups!.forEach((group, index) => {
        group.exercises!.forEach((exercise) => {
            ngxData.push(new ChartData(exercise.title ?? '', exerciseAmountMap.get(exercise.id!) ?? 0));
            ngxColor.domain.push(getColor(index));
        });
    });
}
