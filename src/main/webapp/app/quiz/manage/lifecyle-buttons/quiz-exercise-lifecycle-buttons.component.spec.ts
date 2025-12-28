import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { QuizExerciseService } from 'app/quiz/manage/service/quiz-exercise.service';
import { QuizBatch, QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { QuizExerciseLifecycleButtonsComponent } from 'app/quiz/manage/lifecyle-buttons/quiz-exercise-lifecycle-buttons.component';
import { AlertService } from 'app/shared/service/alert.service';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';

describe('QuizExercise Lifecycle Buttons Component', () => {
    let comp: QuizExerciseLifecycleButtonsComponent;
    let fixture: ComponentFixture<QuizExerciseLifecycleButtonsComponent>;
    let quizExerciseService: QuizExerciseService;
    let alertService: AlertService;

    const course = { id: 123 } as Course;
    const quizExercise = new QuizExercise(course, undefined);
    quizExercise.id = 456;
    quizExercise.title = 'Quiz Exercise';
    quizExercise.quizQuestions = [];
    const quizBatch = new QuizBatch();
    quizBatch.id = 567;
    const route = { snapshot: { paramMap: convertToParamMap({ courseId: course.id }) } } as any as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                { provide: ActivatedRoute, useValue: route },
                LocalStorageService,
                SessionStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AlertService, useClass: MockAlertService },
                { provide: AccountService, useClass: MockAccountService },
                provideHttpClient(),
            ],
        })
            .overrideTemplate(QuizExerciseLifecycleButtonsComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(QuizExerciseLifecycleButtonsComponent);
        comp = fixture.componentInstance;
        quizExerciseService = TestBed.inject(QuizExerciseService);
        alertService = TestBed.inject(AlertService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should open quiz for practice', () => {
        vi.spyOn(quizExerciseService, 'openForPractice').mockReturnValue(
            of(
                new HttpResponse({
                    body: quizExercise,
                }),
            ),
        );

        fixture.componentRef.setInput('quizExercise', quizExercise);
        comp.openForPractice();
        expect(quizExerciseService.openForPractice).toHaveBeenCalledWith(456);
        expect(quizExerciseService.openForPractice).toHaveBeenCalledOnce();
    });

    it('should not open quiz for practice on error', () => {
        vi.spyOn(quizExerciseService, 'openForPractice').mockReturnValue(throwError(() => new HttpErrorResponse({ error: 'Forbidden', status: 403 })));
        vi.spyOn(alertService, 'error');

        fixture.componentRef.setInput('quizExercise', quizExercise);
        comp.openForPractice();
        expect(quizExerciseService.openForPractice).toHaveBeenCalledWith(456);
        expect(quizExerciseService.openForPractice).toHaveBeenCalledOnce();
        expect(alertService.error).toHaveBeenCalledOnce();
    });

    it('should start quiz', () => {
        vi.spyOn(quizExerciseService, 'start').mockReturnValue(
            of(
                new HttpResponse({
                    body: quizExercise,
                }),
            ),
        );

        fixture.componentRef.setInput('quizExercise', quizExercise);
        comp.startQuiz();
        expect(quizExerciseService.start).toHaveBeenCalledWith(456);
        expect(quizExerciseService.start).toHaveBeenCalledOnce();
    });

    it('should not start quiz on error', () => {
        vi.spyOn(quizExerciseService, 'start').mockReturnValue(throwError(() => new HttpErrorResponse({ error: 'Forbidden', status: 403 })));
        vi.spyOn(alertService, 'error');

        fixture.componentRef.setInput('quizExercise', quizExercise);
        comp.startQuiz();
        expect(quizExerciseService.start).toHaveBeenCalledWith(456);
        expect(quizExerciseService.start).toHaveBeenCalledOnce();
        expect(alertService.error).toHaveBeenCalledOnce();
    });

    it('should end quiz', () => {
        vi.spyOn(quizExerciseService, 'end').mockReturnValue(
            of(
                new HttpResponse({
                    body: quizExercise,
                }),
            ),
        );

        fixture.componentRef.setInput('quizExercise', quizExercise);
        comp.endQuiz();
        expect(quizExerciseService.end).toHaveBeenCalledWith(456);
        expect(quizExerciseService.end).toHaveBeenCalledOnce();
    });

    it('should add quiz batch', () => {
        vi.spyOn(quizExerciseService, 'addBatch').mockReturnValue(
            of(
                new HttpResponse({
                    body: quizBatch,
                }),
            ),
        );

        fixture.componentRef.setInput('quizExercise', quizExercise);
        comp.addBatch();
        expect(quizExerciseService.addBatch).toHaveBeenCalledWith(456);
        expect(quizExerciseService.addBatch).toHaveBeenCalledOnce();
    });

    it('should start quiz batch', () => {
        vi.spyOn(quizExerciseService, 'startBatch').mockReturnValue(
            of(
                new HttpResponse({
                    body: quizBatch,
                }),
            ),
        );

        fixture.componentRef.setInput('quizExercise', quizExercise);
        comp.startBatch(567);
        expect(quizExerciseService.startBatch).toHaveBeenCalledWith(567);
        expect(quizExerciseService.startBatch).toHaveBeenCalledOnce();
    });

    it('should make quiz visible', () => {
        vi.spyOn(quizExerciseService, 'setVisible').mockReturnValue(
            of(
                new HttpResponse({
                    body: quizExercise,
                }),
            ),
        );

        fixture.componentRef.setInput('quizExercise', quizExercise);
        comp.showQuiz();
        expect(quizExerciseService.setVisible).toHaveBeenCalledWith(456);
        expect(quizExerciseService.setVisible).toHaveBeenCalledOnce();
    });

    it('should not make quiz visible on error', () => {
        vi.spyOn(quizExerciseService, 'find').mockReturnValue(
            of(
                new HttpResponse({
                    body: quizExercise,
                }),
            ),
        );
        vi.spyOn(quizExerciseService, 'setVisible').mockReturnValue(throwError(() => new HttpErrorResponse({ error: 'Forbidden', status: 403 })));
        vi.spyOn(alertService, 'error');

        fixture.componentRef.setInput('quizExercise', quizExercise);
        comp.showQuiz();
        expect(quizExerciseService.setVisible).toHaveBeenCalledWith(456);
        expect(quizExerciseService.setVisible).toHaveBeenCalledOnce();
        expect(alertService.error).toHaveBeenCalledOnce();
    });
});
