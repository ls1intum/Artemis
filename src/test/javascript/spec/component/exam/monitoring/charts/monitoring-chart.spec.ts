import {
    getColor,
    getCurrentExercisePerStudent,
    getSavedExerciseActionsGroupedByActivityId,
    groupActionsByActivityId,
    groupActionsByTimestamp,
    groupActionsByType,
    insertNgxDataAndColorForExerciseMap,
    updateCurrentExerciseOfStudent,
} from 'app/exam/monitoring/charts/monitoring-chart';
import { GraphColors } from 'app/entities/statistics.model';
import dayjs from 'dayjs/esm';
import { EndedExamAction, ExamAction, ExamActionType, SwitchedExerciseAction } from 'app/entities/exam-user-activity.model';
import { Exam } from 'app/entities/exam.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { NgxChartsSingleSeriesDataEntry } from 'app/shared/chart/ngx-charts-datatypes';
import { Color, ScaleType } from '@swimlane/ngx-charts';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { createActions, createExamActionBasedOnType } from '../exam-monitoring-helper';

describe('Monitoring charts helper methods', () => {
    // Get color
    it('should return a color', () => {
        const colors = [GraphColors.LIGHT_BLUE, GraphColors.LIGHT_GREY, GraphColors.BLUE, GraphColors.GREY, GraphColors.DARK_BLUE];
        colors.forEach((color, index) => {
            expect(getColor(index)).toEqual(color);
        });
        expect(getColor(colors.length)).toEqual(colors[0]);
    });

    // Group actions by timestamp
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

    // Group actions by type
    it('should group actions by type', () => {
        const actions = createActions();
        const grouped = groupActionsByType(actions);
        actions.forEach((action) => expect(grouped[action.type]).toEqual([action]));
    });

    // Group actions by activity id
    it('should group actions by activity id', () => {
        const actions = createActions().map((action, index) => {
            action.examActivityId = index;
            return action;
        });
        const grouped = groupActionsByActivityId(actions);
        actions.forEach((action) => expect(grouped[action.examActivityId!]).toEqual([action]));
    });

    // Filter actions and group by activity id
    it('should filter actions for SavedExerciseAction and group actions by activity id', () => {
        const actions = createActions().map((action) => {
            action.examActivityId = 0;
            return action;
        });
        const grouped = getSavedExerciseActionsGroupedByActivityId(actions);
        expect(grouped[0]).toEqual(actions.filter((action) => action.type === ExamActionType.SAVED_EXERCISE));
    });

    // Get current exercise of students
    it('should get current exercise per student - 0', () => {
        const currentExercisePerStudent = getCurrentExercisePerStudent(new Map());
        expect(currentExercisePerStudent).toEqual(new Map());
    });

    it('should get current exercise of student - 1', () => {
        const lastActionPerStudent = new Map();
        const action = createExamActionBasedOnType(ExamActionType.SWITCHED_EXERCISE);
        action.examActivityId = 0;
        lastActionPerStudent.set(action.examActivityId, action);
        const currentExercisePerStudent = getCurrentExercisePerStudent(lastActionPerStudent);
        const expectedMap = new Map();
        expectedMap.set(0, 0);
        expect(currentExercisePerStudent).toEqual(expectedMap);
    });

    it('should get current exercise of student - ignore multiple switches', () => {
        const lastActionPerStudent = new Map();
        const action1 = new SwitchedExerciseAction(1);
        action1.examActivityId = 0;
        action1.timestamp = dayjs();
        lastActionPerStudent.set(action1.examActivityId, action1);
        const action2 = new SwitchedExerciseAction(1);
        action2.examActivityId = 0;
        action2.timestamp = dayjs().add(1, 'hour');
        lastActionPerStudent.set(action2.examActivityId, action2);
        const currentExercisePerStudent = getCurrentExercisePerStudent(lastActionPerStudent);
        const expectedMap = new Map();
        expectedMap.set(0, 1);
        expect(currentExercisePerStudent).toEqual(expectedMap);
    });

    it.each`
        input                                                             | expect
        ${[new SwitchedExerciseAction(1), new SwitchedExerciseAction(2)]} | ${[1, 2]}
        ${[new EndedExamAction(), new SwitchedExerciseAction(2)]}         | ${[undefined, 2]}
    `('should get current exercise of students - multiple values', (param: { input: ExamAction[]; expect: (number | undefined)[] }) => {
        const lastActionPerStudent = new Map();
        const action1 = param.input[0];
        action1.examActivityId = 1;
        lastActionPerStudent.set(action1.examActivityId, action1);
        const action2 = param.input[1];
        action2.examActivityId = 2;
        lastActionPerStudent.set(action2.examActivityId, action2);
        const currentExercisePerStudent = getCurrentExercisePerStudent(lastActionPerStudent);
        const expectedMap = new Map();
        expectedMap.set(1, param.expect[0]);
        expectedMap.set(2, param.expect[1]);
        expect(currentExercisePerStudent).toEqual(expectedMap);
    });

    // Update current exercise of students
    it.each`
        input                            | expect
        ${new SwitchedExerciseAction(1)} | ${1}
        ${new EndedExamAction()}         | ${undefined}
    `('should update the current exercise of student', (param: { input: ExamAction; expect: number | undefined }) => {
        const action = param.input;
        action.examActivityId = 0;
        const currentExercisePerStudent = new Map<number, number | undefined>();
        updateCurrentExerciseOfStudent(param.input, currentExercisePerStudent);
        const expectedMap = new Map();
        expectedMap.set(0, param.expect);
        expect(currentExercisePerStudent).toEqual(expectedMap);
    });

    // Insert data and color into chart
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
        expect(ngxData).toEqual([
            { name: exercise1.title, value: 1 },
            { name: exercise2.title, value: 1 },
        ]);
        expect(ngxColor.domain).toEqual([getColor(0), getColor(0)]);
    });
});
