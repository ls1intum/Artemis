import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ActivatedRoute } from '@angular/router';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { QuizExerciseExportComponent } from 'app/quiz/manage/export/quiz-exercise-export.component';
import { QuizExerciseService } from 'app/quiz/manage/service/quiz-exercise.service';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { Course } from 'app/course/shared/entities/course.model';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { QuizQuestion } from 'app/quiz/shared/entities/quiz-question.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { TranslateService, TranslateStore } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { QUIZ_EXPORT_BACK } from 'app/quiz/manage/export/quiz-exercise-export.component';

class QuizExerciseServiceStub {
    findForCourse = vi.fn();
    find = vi.fn();
    exportQuiz = vi.fn();
}

class CourseManagementServiceStub {
    find = vi.fn();
}

describe('QuizExerciseExportComponent', () => {
    setupTestBed({ zoneless: true });

    let quizService: QuizExerciseServiceStub;
    let courseService: CourseManagementServiceStub;
    let alertService: AlertService;

    beforeEach(async () => {
        quizService = new QuizExerciseServiceStub();
        courseService = new CourseManagementServiceStub();

        await TestBed.configureTestingModule({
            imports: [QuizExerciseExportComponent],
            providers: [
                { provide: QuizExerciseService, useValue: quizService },
                { provide: CourseManagementService, useValue: courseService },
                { provide: AlertService, useValue: { error: vi.fn(), success: vi.fn(), addAlert: vi.fn() } as any },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: TranslateStore, useValue: {} },
                {
                    provide: ActivatedRoute,
                    useValue: { params: of({ courseId: 42 }) },
                },
            ],
        }).compileComponents();

        alertService = TestBed.inject(AlertService);
    });

    it('should load course and questions on init', async () => {
        const course: Course = { id: 42 } as Course;
        const quiz = { id: 7, quizQuestions: [{ id: 1 } as QuizQuestion] } as QuizExercise;
        const quizDetails = { ...quiz, quizQuestions: [{ id: 1 } as QuizQuestion] } as QuizExercise;
        courseService.find.mockReturnValue(of(new ResponseStub(course)));
        quizService.findForCourse.mockReturnValue(of(new ResponseStub([quiz])));
        quizService.find.mockReturnValue(of(new ResponseStub(quizDetails)));

        const fixture = TestBed.createComponent(QuizExerciseExportComponent);
        fixture.detectChanges();
        await fixture.whenStable();

        expect(courseService.find).toHaveBeenCalledWith(42);
        expect(quizService.findForCourse).toHaveBeenCalledWith(42);
        expect(quizService.find).toHaveBeenCalledWith(quiz.id);
        expect(fixture.componentInstance.questions()).toHaveLength(1);
        expect(fixture.componentInstance.questions()[0].exercise?.id).toBe(quiz.id);
    });

    it('should forward export call', () => {
        const fixture = TestBed.createComponent(QuizExerciseExportComponent);
        fixture.componentInstance.questions.set([{ id: 1 } as QuizQuestion]);

        fixture.componentInstance.exportQuiz();

        expect(quizService.exportQuiz).toHaveBeenCalledWith([{ id: 1 }], false);
    });

    it('should handle load errors', async () => {
        courseService.find.mockReturnValue(of(new ResponseStub({ id: 42 } as Course)));
        quizService.findForCourse.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 400, statusText: 'boom' })));

        const fixture = TestBed.createComponent(QuizExerciseExportComponent);
        fixture.detectChanges();
        await fixture.whenStable();

        expect(alertService.error).toHaveBeenCalledWith('error.http.400');
    });

    it('should stop loading and alert when the course load itself fails', async () => {
        courseService.find.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 400, statusText: 'boom' })));

        const fixture = TestBed.createComponent(QuizExerciseExportComponent);
        fixture.detectChanges();
        await fixture.whenStable();

        expect(alertService.error).toHaveBeenCalledWith('error.http.400');
        expect(fixture.componentInstance.isLoading()).toBe(false);
    });

    it('should stop loading and alert when loading a quiz`s questions fails', async () => {
        courseService.find.mockReturnValue(of(new ResponseStub({ id: 42 } as Course)));
        quizService.findForCourse.mockReturnValue(of(new ResponseStub([{ id: 7 } as QuizExercise])));
        quizService.find.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 400, statusText: 'boom' })));

        const fixture = TestBed.createComponent(QuizExerciseExportComponent);
        fixture.detectChanges();
        await fixture.whenStable();

        expect(alertService.error).toHaveBeenCalledWith('error.http.400');
        expect(fixture.componentInstance.isLoading()).toBe(false);
    });

    it('should show an empty list without loading questions when the course has no quizzes', async () => {
        courseService.find.mockReturnValue(of(new ResponseStub({ id: 42 } as Course)));
        quizService.findForCourse.mockReturnValue(of(new ResponseStub([])));

        const fixture = TestBed.createComponent(QuizExerciseExportComponent);
        fixture.detectChanges();
        await fixture.whenStable();

        expect(fixture.componentInstance.questions()).toEqual([]);
        expect(fixture.componentInstance.isLoading()).toBe(false);
        expect(quizService.find).not.toHaveBeenCalled();
    });

    describe('as a dialog', () => {
        let dialogRef: { close: ReturnType<typeof vi.fn> };

        beforeEach(async () => {
            dialogRef = { close: vi.fn() };
            // The outer beforeEach already instantiated the module (TestBed.inject), so rebuild it with the dialog providers.
            TestBed.resetTestingModule();
            await TestBed.configureTestingModule({
                imports: [QuizExerciseExportComponent],
                providers: [
                    { provide: QuizExerciseService, useValue: quizService },
                    { provide: CourseManagementService, useValue: courseService },
                    { provide: AlertService, useValue: { error: vi.fn(), success: vi.fn(), addAlert: vi.fn() } as any },
                    { provide: TranslateService, useClass: MockTranslateService },
                    { provide: TranslateStore, useValue: {} },
                    { provide: ActivatedRoute, useValue: { params: of({ courseId: 42 }) } },
                    { provide: DynamicDialogRef, useValue: dialogRef },
                    { provide: DynamicDialogConfig, useValue: { data: { courseId: 99 } } },
                ],
            }).compileComponents();
        });

        it('takes the course id from the dialog data instead of the route', async () => {
            courseService.find.mockReturnValue(of(new ResponseStub({ id: 99 } as Course)));
            quizService.findForCourse.mockReturnValue(of(new ResponseStub([])));

            const fixture = TestBed.createComponent(QuizExerciseExportComponent);
            fixture.detectChanges();
            await fixture.whenStable();

            expect(fixture.componentInstance.isDialog).toBe(true);
            expect(courseService.find).toHaveBeenCalledWith(99);
        });

        it('closes the dialog after triggering the export', () => {
            const fixture = TestBed.createComponent(QuizExerciseExportComponent);
            fixture.componentInstance.questions.set([]);

            fixture.componentInstance.exportQuiz();

            expect(dialogRef.close).toHaveBeenCalledWith();
        });

        it('closes with the back sentinel on back', () => {
            const fixture = TestBed.createComponent(QuizExerciseExportComponent);

            fixture.componentInstance.back();

            expect(dialogRef.close).toHaveBeenCalledWith(QUIZ_EXPORT_BACK);
        });
    });
});

class ResponseStub<T> {
    constructor(public body: T) {}
}
