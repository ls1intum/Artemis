import {
    ChartData,
    getColor,
    getCurrentAmountOfStudentsPerExercises,
    getSavedExerciseActionsGroupedByActivityId,
    getSwitchedExerciseActionsGroupedByActivityId,
    groupActionsByActivityId,
    groupActionsByTimestamp,
    groupActionsByType,
    insertNgxDataAndColorForExerciseMap,
} from 'app/exam/monitoring/charts/monitoring-chart';
import { GraphColors } from 'app/entities/statistics.model';
import dayjs from 'dayjs/esm';
import { ExamActionType, SwitchedExerciseAction } from 'app/entities/exam-user-activity.model';
import { Exam } from 'app/entities/exam.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { NgxChartsSingleSeriesDataEntry } from 'app/shared/chart/ngx-charts-datatypes';
import { Color, ScaleType } from '@swimlane/ngx-charts';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { createActions } from '../exam-monitoring-helper';

describe('Monitoring charts helper methods', () => {
    it('should return a color', () => {
        const colors = [GraphColors.LIGHT_BLUE, GraphColors.LIGHT_GREY, GraphColors.BLUE, GraphColors.GREY, GraphColors.DARK_BLUE];
        colors.forEach((color, index) => {
            expect(getColor(index)).toEqual(color);
        });
        expect(getColor(colors.length)).toEqual(colors[0]);
    });

    it('should group actions by timestamp in one group', () => {
        const actions = createActions();
        const now = dayjs();
        actions.map((action) => (action.timestamp = now));
        const grouped = groupActionsByTimestamp(actions);
        expect(grouped[now.toString()]).toHaveLength(actions.length);
    });

    it('should group actions by timestamp in multiple groups', () => {
        const now = dayjs();
        const later = dayjs().add(1, 'hour');
        const actions = createActions().map((action) => {
            action.timestamp = now;
            return action;
        });
        actions[0].timestamp = later;
        const grouped = groupActionsByTimestamp(actions);
        expect(grouped[now.toString()]).toHaveLength(actions.length - 1);
        expect(grouped[later.toString()]).toHaveLength(1);
    });

    it('should group actions by type', () => {
        const actions = createActions();
        const grouped = groupActionsByType(actions);
        actions.forEach((action) => expect(grouped[action.type]).toEqual([action]));
    });

    it('should group actions by activity id', () => {
        const actions = createActions().map((action, index) => {
            action.examActivityId = index;
            return action;
        });
        const grouped = groupActionsByActivityId(actions);
        actions.forEach((action) => expect(grouped[action.examActivityId!]).toEqual([action]));
    });

    it('should filter actions for SwitchedExerciseAction and group actions by activity id', () => {
        const actions = createActions().map((action) => {
            action.examActivityId = 0;
            return action;
        });
        const grouped = getSwitchedExerciseActionsGroupedByActivityId(actions);
        expect(grouped[0]).toEqual(actions.filter((action) => action.type === ExamActionType.SWITCHED_EXERCISE));
    });

    it('should filter actions for SavedExerciseAction and group actions by activity id', () => {
        const actions = createActions().map((action) => {
            action.examActivityId = 0;
            return action;
        });
        const grouped = getSavedExerciseActionsGroupedByActivityId(actions);
        expect(grouped[0]).toEqual(actions.filter((action) => action.type === ExamActionType.SAVED_EXERCISE));
    });

    it('should get current amount of students per exercise - 0', () => {
        const amount = getCurrentAmountOfStudentsPerExercises([]);
        expect(amount).toEqual(new Map());
    });

    it('should get current amount of students per exercise - 1', () => {
        const amount = getCurrentAmountOfStudentsPerExercises(createActions());
        const expectedMap = new Map();
        expectedMap.set(0, 1);
        expect(amount).toEqual(expectedMap);
    });

    it('should get current amount of students per exercise - ignore multiple switches', () => {
        const action1 = new SwitchedExerciseAction(1);
        action1.timestamp = dayjs();
        const action2 = new SwitchedExerciseAction(1);
        action2.timestamp = dayjs().add(1, 'hour');
        const amount = getCurrentAmountOfStudentsPerExercises([action1, action2]);
        const expectedMap = new Map();
        expectedMap.set(1, 1);
        expect(amount).toEqual(expectedMap);
    });

    it('should get current amount of students per exercise - multiple values', () => {
        const action1 = new SwitchedExerciseAction(1);
        action1.examActivityId = 1;
        const action2 = new SwitchedExerciseAction(2);
        action2.examActivityId = 2;
        const amount = getCurrentAmountOfStudentsPerExercises([action1, action2]);
        const expectedMap = new Map();
        expectedMap.set(1, 1);
        expectedMap.set(2, 1);
        expect(amount).toEqual(expectedMap);
    });

    it('should insert chart data and color for exercise map', () => {
        const exam = new Exam();
        const exercise1 = new TextExercise(undefined, undefined);
        exercise1.id = 1;
        exercise1.title = '1';
        const exercise2 = new TextExercise(undefined, undefined);
        exercise2.id = 2;
        exercise2.title = '2';
        const exerciseGroup = new ExerciseGroup();
        exerciseGroup.exercises = [exercise1, exercise2];
        exam.exerciseGroups = [exerciseGroup];

        const exerciseAmountMap = new Map();
        exerciseAmountMap.set(1, 1);
        exerciseAmountMap.set(2, 1);

        const ngxData: NgxChartsSingleSeriesDataEntry[] = [];
        const ngxColor: Color = { name: 'exercise groups', selectable: true, group: ScaleType.Ordinal, domain: [] } as Color;

        insertNgxDataAndColorForExerciseMap(exam, exerciseAmountMap, ngxData, ngxColor);
        expect(ngxData).toEqual([new ChartData(exercise1.title, 1), new ChartData(exercise2.title, 1)]);
        expect(ngxColor.domain).toEqual([getColor(0), getColor(0)]);
    });
});
