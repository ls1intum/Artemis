import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { Course } from 'app/course/shared/entities/course.model';
import { CreateTestRunModalComponent } from 'app/exam/manage/test-runs/create-test-run-modal/create-test-run-modal.component';
import dayjs from 'dayjs/esm';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { CreateTestRunDTO } from 'app/exam/manage/test-runs/create-test-run-dto.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

describe('Create Test Run Modal Component', () => {
    let comp: CreateTestRunModalComponent;
    let fixture: ComponentFixture<CreateTestRunModalComponent>;

    const course = { id: 1 } as Course;
    const exercise = { id: 1, title: 'exampleExercise', type: ExerciseType.TEXT } as Exercise;
    const exerciseGroup1 = { id: 1, exercises: [exercise], title: 'exampleExerciseGroup' } as ExerciseGroup;
    const exerciseGroup2 = { id: 2 } as ExerciseGroup;
    let exam: Exam;

    function createComponent() {
        fixture = TestBed.createComponent(CreateTestRunModalComponent);
        comp = fixture.componentInstance;
        fixture.componentRef.setInput('exam', exam);
    }

    beforeEach(() => {
        exam = { id: 1, course, started: true, startDate: dayjs(), endDate: dayjs().add(20, 'seconds'), exerciseGroups: [exerciseGroup1] } as Exam;
        TestBed.configureTestingModule({
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();
        createComponent();
    });

    afterEach(() => {
        vi.restoreAllMocks();
        document.querySelectorAll('.cdk-overlay-container').forEach((container) => container.replaceChildren());
    });

    describe('onInit', () => {
        it('should initialise the working time form', () => {
            comp.ngOnInit();
            expect(comp.workingTimeForm).toBeDefined();
            expect(comp.exam()).toEqual(exam);
        });
    });

    describe('creating test run workflow', () => {
        it('should create a new test run, emit it and close the dialog', () => {
            const emitted: CreateTestRunDTO[] = [];
            comp.testRunCreate.subscribe((testRun) => emitted.push(testRun));
            fixture.detectChanges();
            comp.workingTimeForm.controls['minutes'].setValue(30);
            comp.workingTimeForm.controls['seconds'].setValue(0);

            const exerciseRow = document.querySelector<HTMLElement>('#exercise-1');
            expect(exerciseRow).not.toBeNull();
            exerciseRow!.click();
            fixture.detectChanges();
            expect(comp.testRunConfiguration().get(1)).toEqual(exercise);
            expect(comp.exerciseGroups()).toHaveLength(1);
            expect(comp.testRunConfigured()).toBe(true);

            const createTestRunButton = document.querySelector<HTMLButtonElement>('#createTestRunButton');
            expect(createTestRunButton).not.toBeNull();
            createTestRunButton!.click();

            expect(emitted).toHaveLength(1);
            expect(emitted[0].examId).toBe(exam.id);
            expect(emitted[0].exerciseIds).toEqual([exercise.id]);
            expect(emitted[0].workingTime).toBe(1800);
            expect(comp.visible()).toBe(false);
        });

        it('should close the dialog without emitting when cancelled', () => {
            const emitted: CreateTestRunDTO[] = [];
            comp.testRunCreate.subscribe((testRun) => emitted.push(testRun));
            fixture.detectChanges();

            comp.cancel();

            expect(comp.visible()).toBe(false);
            expect(emitted).toHaveLength(0);
        });
    });

    describe('Ignore Exercise groups', () => {
        it('should ignore exercise groups with no exercises', () => {
            exam.exerciseGroups = [exerciseGroup1, exerciseGroup2];
            createComponent();
            fixture.detectChanges();
            expect(comp.exerciseGroups()).toHaveLength(1);
            expect(comp.exerciseGroups()[0]).toBe(exerciseGroup1);
        });
    });

    describe('Exercise Selection', () => {
        it('should select the only exercise of a group automatically', () => {
            fixture.detectChanges();
            expect(comp.isSelected(exercise, exerciseGroup1)).toBe(true);
            expect(comp.testRunConfigured()).toBe(true);
        });

        it('should highlight the exercise when pressed', () => {
            const other = { id: 2, title: 'other', type: ExerciseType.TEXT } as Exercise;
            const group = { id: 3, exercises: [exercise, other], title: 'two exercises' } as ExerciseGroup;
            exam.exerciseGroups = [group];
            createComponent();
            fixture.detectChanges();
            expect(comp.testRunConfigured()).toBe(false);

            comp.onSelectExercise(other, group);

            expect(comp.isSelected(other, group)).toBe(true);
            expect(comp.isSelected(exercise, group)).toBe(false);
            expect(comp.testRunConfigured()).toBe(true);
        });
    });
    it('should select exercises through a native button and announce the selected state', async () => {
        const other = { id: 2, title: 'other', type: ExerciseType.TEXT } as Exercise;
        const group = { id: 3, exercises: [exercise, other], title: 'two exercises' } as ExerciseGroup;
        fixture.destroy();
        exam.exerciseGroups = [group];
        createComponent();
        comp.visible.set(true);
        fixture.detectChanges();
        await fixture.whenStable();

        const button = document.querySelector<HTMLButtonElement>('[data-testid="test-run-select-exercise-2"]')!;
        expect(button.tagName).toBe('BUTTON');
        expect(button.type).toBe('button');
        expect(button.getAttribute('aria-pressed')).toBe('false');
        button.click();
        fixture.detectChanges();
        await fixture.whenStable();

        expect(comp.isSelected(other, group)).toBe(true);
        expect(button.getAttribute('aria-pressed')).toBe('true');
    });
});
