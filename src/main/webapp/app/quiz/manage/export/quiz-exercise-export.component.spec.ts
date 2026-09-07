import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
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

class QuizExerciseServiceStub {
    findForCourse = vi.fn();
    find = vi.fn();
    exportQuiz = vi.fn();
}

class CourseManagementServiceStub {
    find = vi.fn();
}

describe('QuizExerciseExportComponent', () => {
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
            ],
        }).compileComponents();

        alertService = TestBed.inject(AlertService);
    });

    /** Creates the component and opens it for the given course (the load runs when `visible` flips true, as at runtime). */
    async function open(courseId: number): Promise<ComponentFixture<QuizExerciseExportComponent>> {
        const fixture = TestBed.createComponent(QuizExerciseExportComponent);
        fixture.componentRef.setInput('courseId', courseId);
        fixture.componentRef.setInput('visible', true);
        fixture.detectChanges();
        await fixture.whenStable();
        return fixture;
    }

    it('should load the course and questions when opened', async () => {
        const course: Course = { id: 42 } as Course;
        const quiz = { id: 7, quizQuestions: [{ id: 1 } as QuizQuestion] } as QuizExercise;
        const quizDetails = { ...quiz, quizQuestions: [{ id: 1 } as QuizQuestion] } as QuizExercise;
        courseService.find.mockReturnValue(of(new ResponseStub(course)));
        quizService.findForCourse.mockReturnValue(of(new ResponseStub([quiz])));
        quizService.find.mockReturnValue(of(new ResponseStub(quizDetails)));

        const fixture = await open(42);

        expect(courseService.find).toHaveBeenCalledWith(42);
        expect(quizService.findForCourse).toHaveBeenCalledWith(42);
        expect(quizService.find).toHaveBeenCalledWith(quiz.id);
        expect(fixture.componentInstance.questions()).toHaveLength(1);
        expect(fixture.componentInstance.questions()[0].exercise?.id).toBe(quiz.id);
    });

    it('should forward export call and close', async () => {
        courseService.find.mockReturnValue(of(new ResponseStub({ id: 42 } as Course)));
        quizService.findForCourse.mockReturnValue(of(new ResponseStub([])));
        const fixture = await open(42);
        fixture.componentInstance.questions.set([{ id: 1 } as QuizQuestion]);

        fixture.componentInstance.exportQuiz();

        expect(quizService.exportQuiz).toHaveBeenCalledWith([{ id: 1 }], false);
        expect(fixture.componentInstance.visible()).toBe(false);
    });

    it('emits back and closes on back', async () => {
        courseService.find.mockReturnValue(of(new ResponseStub({ id: 42 } as Course)));
        quizService.findForCourse.mockReturnValue(of(new ResponseStub([])));
        const fixture = await open(42);
        const backSpy = vi.fn();
        fixture.componentInstance.back.subscribe(backSpy);

        fixture.componentInstance.onBack();

        expect(backSpy).toHaveBeenCalledOnce();
        expect(fixture.componentInstance.visible()).toBe(false);
    });

    it('should handle load errors', async () => {
        courseService.find.mockReturnValue(of(new ResponseStub({ id: 42 } as Course)));
        quizService.findForCourse.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 400, statusText: 'boom' })));

        await open(42);

        expect(alertService.error).toHaveBeenCalledWith('error.http.400');
    });

    it('should stop loading and alert when the course load itself fails', async () => {
        courseService.find.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 400, statusText: 'boom' })));

        const fixture = await open(42);

        expect(alertService.error).toHaveBeenCalledWith('error.http.400');
        expect(fixture.componentInstance.isLoading()).toBe(false);
    });

    it('should stop loading and alert when loading a quiz`s questions fails', async () => {
        courseService.find.mockReturnValue(of(new ResponseStub({ id: 42 } as Course)));
        quizService.findForCourse.mockReturnValue(of(new ResponseStub([{ id: 7 } as QuizExercise])));
        quizService.find.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 400, statusText: 'boom' })));

        const fixture = await open(42);

        expect(alertService.error).toHaveBeenCalledWith('error.http.400');
        expect(fixture.componentInstance.isLoading()).toBe(false);
    });

    it('should show an empty list without loading questions when the course has no quizzes', async () => {
        courseService.find.mockReturnValue(of(new ResponseStub({ id: 42 } as Course)));
        quizService.findForCourse.mockReturnValue(of(new ResponseStub([])));

        const fixture = await open(42);

        expect(fixture.componentInstance.questions()).toEqual([]);
        expect(fixture.componentInstance.isLoading()).toBe(false);
        expect(quizService.find).not.toHaveBeenCalled();
    });
});

class ResponseStub<T> {
    constructor(public body: T) {}
}
