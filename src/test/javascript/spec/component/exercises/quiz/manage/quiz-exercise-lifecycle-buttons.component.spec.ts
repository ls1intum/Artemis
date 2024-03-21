import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { TranslateService } from '@ngx-translate/core';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import { QuizBatch, QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { Course } from 'app/entities/course.model';
import { MockSyncStorage } from '../../../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../../../test.module';
import { QuizExerciseLifecycleButtonsComponent } from 'app/exercises/quiz/manage/quiz-exercise-lifecycle-buttons.component';
import { AlertService } from 'app/core/util/alert.service';
import { MockAlertService } from '../../../../helpers/mocks/service/mock-alert.service';

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
            imports: [ArtemisTestModule],
            declarations: [QuizExerciseLifecycleButtonsComponent],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AlertService, useClass: MockAlertService },
            ],
        })
            .overrideTemplate(QuizExerciseLifecycleButtonsComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(QuizExerciseLifecycleButtonsComponent);
        comp = fixture.componentInstance;
        quizExerciseService = fixture.debugElement.injector.get(QuizExerciseService);
        alertService = fixture.debugElement.injector.get(AlertService);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should open quiz for practice', () => {
        jest.spyOn(quizExerciseService, 'openForPractice').mockReturnValue(
            of(
                new HttpResponse({
                    body: quizExercise,
                }),
            ),
        );

        comp.quizExercise = quizExercise;
        comp.openForPractice();
        expect(quizExerciseService.openForPractice).toHaveBeenCalledWith(456);
        expect(quizExerciseService.openForPractice).toHaveBeenCalledOnce();
    });

    it('should not open quiz for practice on error', () => {
        jest.spyOn(quizExerciseService, 'openForPractice').mockReturnValue(throwError(() => new HttpErrorResponse({ error: 'Forbidden', status: 403 })));
        jest.spyOn(alertService, 'error');

        comp.quizExercise = quizExercise;
        comp.openForPractice();
        expect(quizExerciseService.openForPractice).toHaveBeenCalledWith(456);
        expect(quizExerciseService.openForPractice).toHaveBeenCalledOnce();
        expect(alertService.error).toHaveBeenCalledOnce();
    });

    it('should start quiz', () => {
        jest.spyOn(quizExerciseService, 'start').mockReturnValue(
            of(
                new HttpResponse({
                    body: quizExercise,
                }),
            ),
        );

        comp.quizExercise = quizExercise;
        comp.startQuiz();
        expect(quizExerciseService.start).toHaveBeenCalledWith(456);
        expect(quizExerciseService.start).toHaveBeenCalledOnce();
    });

    it('should not start quiz on error', () => {
        jest.spyOn(quizExerciseService, 'start').mockReturnValue(throwError(() => new HttpErrorResponse({ error: 'Forbidden', status: 403 })));
        jest.spyOn(alertService, 'error');

        comp.quizExercise = quizExercise;
        comp.startQuiz();
        expect(quizExerciseService.start).toHaveBeenCalledWith(456);
        expect(quizExerciseService.start).toHaveBeenCalledOnce();
        expect(alertService.error).toHaveBeenCalledOnce();
    });

    it('should end quiz', () => {
        jest.spyOn(quizExerciseService, 'end').mockReturnValue(
            of(
                new HttpResponse({
                    body: quizExercise,
                }),
            ),
        );

        comp.quizExercise = quizExercise;
        comp.endQuiz();
        expect(quizExerciseService.end).toHaveBeenCalledWith(456);
        expect(quizExerciseService.end).toHaveBeenCalledOnce();
    });

    it('should add quiz batch', () => {
        jest.spyOn(quizExerciseService, 'addBatch').mockReturnValue(
            of(
                new HttpResponse({
                    body: quizBatch,
                }),
            ),
        );

        comp.quizExercise = quizExercise;
        comp.addBatch();
        expect(quizExerciseService.addBatch).toHaveBeenCalledWith(456);
        expect(quizExerciseService.addBatch).toHaveBeenCalledOnce();
    });

    it('should start quiz batch', () => {
        jest.spyOn(quizExerciseService, 'startBatch').mockReturnValue(
            of(
                new HttpResponse({
                    body: quizBatch,
                }),
            ),
        );

        comp.quizExercise = quizExercise;
        comp.startBatch(567);
        expect(quizExerciseService.startBatch).toHaveBeenCalledWith(567);
        expect(quizExerciseService.startBatch).toHaveBeenCalledOnce();
    });

    it('should make quiz visible', () => {
        jest.spyOn(quizExerciseService, 'setVisible').mockReturnValue(
            of(
                new HttpResponse({
                    body: quizExercise,
                }),
            ),
        );

        comp.quizExercise = quizExercise;
        comp.showQuiz();
        expect(quizExerciseService.setVisible).toHaveBeenCalledWith(456);
        expect(quizExerciseService.setVisible).toHaveBeenCalledOnce();
    });

    it('should not make quiz visible on error', () => {
        jest.spyOn(quizExerciseService, 'find').mockReturnValue(
            of(
                new HttpResponse({
                    body: quizExercise,
                }),
            ),
        );
        jest.spyOn(quizExerciseService, 'setVisible').mockReturnValue(throwError(() => new HttpErrorResponse({ error: 'Forbidden', status: 403 })));
        jest.spyOn(alertService, 'error');

        comp.quizExercise = quizExercise;
        comp.showQuiz();
        expect(quizExerciseService.setVisible).toHaveBeenCalledWith(456);
        expect(quizExerciseService.setVisible).toHaveBeenCalledOnce();
        expect(alertService.error).toHaveBeenCalledOnce();
    });
});
