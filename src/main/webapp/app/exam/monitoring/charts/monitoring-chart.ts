import { GraphColors } from 'app/entities/statistics.model';
import { ExamAction, ExamActionType, SwitchedExerciseAction } from 'app/entities/exam-user-activity.model';
import { groupBy } from 'lodash';

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
const colors = [GraphColors.LIGHT_GREY, GraphColors.GREY, GraphColors.DARK_BLUE, GraphColors.BLUE, GraphColors.LIGHT_BLUE];

/**
 * Returns a color based on the index (modulo)
 * @param index
 */
export function getColor(index: number) {
    return colors[index % colors.length];
}

export function groupActionsByTimestamp(examActions: ExamAction[]) {
    return groupBy(examActions, (action: ExamAction) => {
        return action.timestamp;
    });
}

export function groupActionsByType(examActions: ExamAction[]) {
    return groupBy(examActions, (action: ExamAction) => {
        return action.type;
    });
}

export function groupActionsByActivityId(examActions: ExamAction[]) {
    return groupBy(examActions, (action: ExamAction) => {
        return action.examActivityId;
    });
}

export function getSwitchedExerciseActionsGroupedByActivityId(examActions: ExamAction[]) {
    return groupActionsByActivityId(examActions.filter((action) => action.type === ExamActionType.SWITCHED_EXERCISE));
}

export function getSavedExerciseActionsGroupedByActivityId(examActions: ExamAction[]) {
    return groupActionsByActivityId(examActions.filter((action) => action.type === ExamActionType.SAVED_EXERCISE));
}

export function getCurrentAmountOfStudentsPerExercises(examActions: ExamAction[]): Map<number, number> {
    const exerciseAmountMap: Map<number, number> = new Map();
    const groupedByActivityId = getSwitchedExerciseActionsGroupedByActivityId(examActions);
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
