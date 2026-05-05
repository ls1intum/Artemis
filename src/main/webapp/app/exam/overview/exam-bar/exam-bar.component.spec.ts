import { provideHttpClient } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { ExamBarComponent } from 'app/exam/overview/exam-bar/exam-bar.component';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { AlertService } from 'app/shared/service/alert.service';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import dayjs from 'dayjs/esm';
import { MockProvider } from 'ng-mocks';
import { DialogService } from 'primeng/dynamicdialog';
import { MockResizeObserver } from 'test/helpers/mocks/service/mock-resize-observer';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

describe('ExamBarComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<ExamBarComponent>;
    let comp: ExamBarComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                LocalStorageService,
                SessionStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(AlertService),
                MockProvider(DialogService),
                provideHttpClient(),
            ],
        }).compileComponents();
        // Required because exam bar uses the ResizeObserver for height calculations
        global.ResizeObserver = MockResizeObserver as any;

        fixture = TestBed.createComponent(ExamBarComponent);
        comp = fixture.componentInstance;

        const exam = new Exam();
        exam.title = 'Test Exam';
        const studentExam = new StudentExam();
        studentExam.exercises = [
            {
                id: 0,
                type: ExerciseType.PROGRAMMING,
                studentParticipations: [
                    {
                        submissions: [{ id: 3, isSynced: true } as Submission],
                    } as StudentParticipation,
                ],
            } as Exercise,
            { id: 1, type: ExerciseType.TEXT } as Exercise,
            { id: 2, type: ExerciseType.MODELING } as Exercise,
        ];

        fixture.componentRef.setInput('exam', exam);
        fixture.componentRef.setInput('studentExam', studentExam);
        fixture.componentRef.setInput('endDate', dayjs());
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('trigger emit and call saveExercise when the exam is about to end', () => {
        vi.spyOn(comp, 'saveExercise');
        vi.spyOn(comp.examAboutToEnd, 'emit');

        comp.triggerExamAboutToEnd();

        expect(comp.saveExercise).toHaveBeenCalledOnce();
        expect(comp.examAboutToEnd.emit).toHaveBeenCalledOnce();
    });

    it('should hand in the exam early', () => {
        vi.spyOn(comp.onExamHandInEarly, 'emit');
        vi.spyOn(comp, 'saveExercise');

        comp.handInEarly();
        expect(comp.onExamHandInEarly.emit).toHaveBeenCalledOnce();
    });

    describe('signal-derived properties', () => {
        it('should derive exam properties from inputs', () => {
            const exam = new Exam();
            exam.title = 'Test Exam Title';
            exam.testExam = true;
            const studentExam = new StudentExam();
            studentExam.testRun = true;
            studentExam.exercises = [{ id: 1 } as Exercise];

            fixture.componentRef.setInput('exam', exam);
            fixture.componentRef.setInput('studentExam', studentExam);

            expect(comp.examTitle()).toBe('Test Exam Title');
            expect(comp.exercises()).toEqual([{ id: 1 }]);
            expect(comp.testExam()).toBe(true);
            expect(comp.isTestRun()).toBe(true);
        });

        it('should handle undefined exam title', () => {
            const exam = new Exam();
            exam.title = undefined;
            const studentExam = new StudentExam();

            fixture.componentRef.setInput('exam', exam);
            fixture.componentRef.setInput('studentExam', studentExam);

            expect(comp.examTitle()).toBe('');
        });

        it('should handle undefined exercises', () => {
            const exam = new Exam();
            const studentExam = new StudentExam();
            studentExam.exercises = undefined;

            fixture.componentRef.setInput('exam', exam);
            fixture.componentRef.setInput('studentExam', studentExam);

            expect(comp.exercises()).toEqual([]);
        });
    });

    describe('saveExercise', () => {
        it('should mark submission as submitted for non-programming exercise', () => {
            const submission = { id: 1, submitted: false } as Submission;
            const studentExam = new StudentExam();
            studentExam.exercises = [
                {
                    id: 0,
                    type: ExerciseType.PROGRAMMING,
                    studentParticipations: [{ submissions: [{ id: 3, isSynced: true } as Submission] } as StudentParticipation],
                } as Exercise,
                {
                    id: 1,
                    type: ExerciseType.TEXT,
                    studentParticipations: [{ submissions: [submission] } as StudentParticipation],
                } as Exercise,
                { id: 2, type: ExerciseType.MODELING } as Exercise,
            ];
            fixture.componentRef.setInput('studentExam', studentExam);
            fixture.componentRef.setInput('exerciseIndex', 1);

            comp.saveExercise();

            expect(submission.submitted).toBe(true);
        });

        it('should not mark submission as submitted for programming exercise', () => {
            const submission = { id: 1, submitted: false, isSynced: true } as Submission;
            const studentExam = new StudentExam();
            studentExam.exercises = [
                {
                    id: 0,
                    type: ExerciseType.PROGRAMMING,
                    studentParticipations: [{ submissions: [submission] } as StudentParticipation],
                } as Exercise,
                { id: 1, type: ExerciseType.TEXT } as Exercise,
            ];
            fixture.componentRef.setInput('studentExam', studentExam);
            fixture.componentRef.setInput('exerciseIndex', 0);

            comp.saveExercise();

            expect(submission.submitted).toBe(false);
        });
    });

    describe('onHeightChange', () => {
        it('should emit heightChange event', () => {
            vi.spyOn(comp.heightChange, 'emit');

            comp.onHeightChange(100);

            expect(comp.heightChange.emit).toHaveBeenCalledWith(100);
        });
    });
});
