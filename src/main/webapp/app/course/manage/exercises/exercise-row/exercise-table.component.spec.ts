import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TranslateService } from '@ngx-translate/core';
import dayjs from 'dayjs/esm';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ExerciseTableComponent } from 'app/course/manage/exercises/exercise-row/exercise-table.component';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { CourseExerciseGroup } from 'app/exercise/shared/entities/exercise/course-exercise-group.model';

describe('ExerciseTableComponent', () => {
    setupTestBed({ zoneless: true });
    let component: ExerciseTableComponent;
    let fixture: ComponentFixture<ExerciseTableComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExerciseTableComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(ExerciseTableComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('courseId', 1);
    });

    describe('sortedExercises', () => {
        it('sorts undated exercises last regardless of ascending/descending direction', () => {
            const dated1: Exercise = { id: 1, title: 'Dated early', type: ExerciseType.TEXT, dueDate: dayjs('2026-01-05T10:00:00Z') } as Exercise;
            const dated2: Exercise = { id: 2, title: 'Dated late', type: ExerciseType.TEXT, dueDate: dayjs('2026-01-10T10:00:00Z') } as Exercise;
            const undated: Exercise = { id: 3, title: 'Undated', type: ExerciseType.TEXT } as Exercise;
            fixture.componentRef.setInput('exercises', [dated2, undated, dated1]);

            component.sortBy('dueDate');
            expect(component.sortedExercises().map((e) => e.id)).toEqual([1, 2, 3]);

            component.sortBy('dueDate');
            expect(component.sortedExercises().map((e) => e.id)).toEqual([2, 1, 3]);
        });
    });

    describe('owningGroupForExercise', () => {
        it('does not cross-match different draft exercises that both have an undefined id', () => {
            const draftA: Exercise = { title: 'Draft A', type: ExerciseType.TEXT } as Exercise;
            const draftB: Exercise = { title: 'Draft B', type: ExerciseType.TEXT } as Exercise;
            const groupA = { id: 1, exercises: [draftA] } as CourseExerciseGroup;
            const groupB = { id: 2, exercises: [draftB] } as CourseExerciseGroup;
            fixture.componentRef.setInput('exercises', []);
            fixture.componentRef.setInput('groups', [groupA, groupB]);

            expect(component.owningGroupForExercise(draftA)).toBe(groupA);
            expect(component.owningGroupForExercise(draftB)).toBe(groupB);
        });

        it('still matches saved exercises by id', () => {
            const saved: Exercise = { id: 42, title: 'Saved', type: ExerciseType.TEXT } as Exercise;
            const group = { id: 1, exercises: [saved] } as CourseExerciseGroup;
            fixture.componentRef.setInput('exercises', []);
            fixture.componentRef.setInput('groups', [group]);

            expect(component.owningGroupForExercise({ id: 42, title: 'Saved (different object)', type: ExerciseType.TEXT } as Exercise)).toBe(group);
        });
    });
});
