import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse, HttpHeaders, HttpResponse } from '@angular/common/http';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { TranslateService } from '@ngx-translate/core';

import { ArtemisTestModule } from '../../test.module';
import { QuizExerciseComponent } from 'app/exercises/quiz/manage/quiz-exercise.component';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { Course } from 'app/entities/course.model';
import { ExerciseFilter } from 'app/entities/exercise-filter.model';
import { AlertService } from 'app/core/util/alert.service';
import dayjs from 'dayjs';

describe('QuizExercise Management Component', () => {
    let comp: QuizExerciseComponent;
    let fixture: ComponentFixture<QuizExerciseComponent>;
    let service: QuizExerciseService;
    let alertService: AlertService;

    const course = { id: 123 } as Course;
    const quizExercise = new QuizExercise(course, undefined);
    quizExercise.id = 456;
    quizExercise.title = 'Quiz Exercise';
    const route = { snapshot: { paramMap: convertToParamMap({ courseId: course.id }) } } as any as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [QuizExerciseComponent],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .overrideTemplate(QuizExerciseComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(QuizExerciseComponent);
        comp = fixture.componentInstance;
        service = fixture.debugElement.injector.get(QuizExerciseService);
        alertService = fixture.debugElement.injector.get(AlertService);

        comp.course = course;
        comp.quizExercises = [quizExercise];
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('Should call loadExercises on init', () => {
        // GIVEN
        const headers = new HttpHeaders().append('link', 'link;link');
        jest.spyOn(service, 'findForCourse').mockReturnValue(
            of(
                new HttpResponse({
                    body: [quizExercise],
                    headers,
                }),
            ),
        );

        // WHEN
        comp.ngOnInit();

        // THEN
        expect(service.findForCourse).toHaveBeenCalledTimes(1);
        expect(comp.quizExercises[0]).toEqual(quizExercise);
    });

    it('Should reset quiz', () => {
        const headers = new HttpHeaders().append('link', 'link;link');
        jest.spyOn(service, 'reset').mockReturnValue(
            of(
                new HttpResponse({
                    body: {},
                    headers,
                }),
            ),
        );
        jest.spyOn(service, 'findForCourse');

        comp.ngOnInit();
        comp.resetQuizExercise(456);
        expect(service.reset).toHaveBeenCalledWith(456);
        expect(service.reset).toHaveBeenCalledTimes(1);
    });

    it('Should open quiz for practice', () => {
        const headers = new HttpHeaders().append('link', 'link;link');
        jest.spyOn(service, 'openForPractice').mockReturnValue(
            of(
                new HttpResponse({
                    body: quizExercise,
                    headers,
                }),
            ),
        );

        comp.ngOnInit();
        comp.openForPractice(456);
        expect(service.openForPractice).toHaveBeenCalledWith(456);
        expect(service.openForPractice).toHaveBeenCalledTimes(1);
    });

    it('Should not open quiz for practice on error', () => {
        const headers = new HttpHeaders().append('link', 'link;link');
        jest.spyOn(service, 'find').mockReturnValue(
            of(
                new HttpResponse({
                    body: quizExercise,
                    headers,
                }),
            ),
        );
        jest.spyOn(service, 'openForPractice').mockReturnValue(throwError(new HttpErrorResponse({ error: 'Forbidden', status: 403 })));
        jest.spyOn(alertService, 'error');

        comp.ngOnInit();
        comp.openForPractice(456);
        expect(service.openForPractice).toHaveBeenCalledWith(456);
        expect(service.openForPractice).toHaveBeenCalledTimes(1);
        expect(alertService.error).toHaveBeenCalledTimes(1);
        expect(service.find).toHaveBeenCalledWith(456);
        expect(service.find).toHaveBeenCalledTimes(1);
    });

    it('Should start quiz', () => {
        const headers = new HttpHeaders().append('link', 'link;link');
        jest.spyOn(service, 'start').mockReturnValue(
            of(
                new HttpResponse({
                    body: quizExercise,
                    headers,
                }),
            ),
        );

        comp.ngOnInit();
        comp.startQuiz(456);
        expect(service.start).toHaveBeenCalledWith(456);
        expect(service.start).toHaveBeenCalledTimes(1);
    });

    it('Should not start quiz on error', () => {
        const headers = new HttpHeaders().append('link', 'link;link');
        jest.spyOn(service, 'find').mockReturnValue(
            of(
                new HttpResponse({
                    body: quizExercise,
                    headers,
                }),
            ),
        );
        jest.spyOn(service, 'start').mockReturnValue(throwError(new HttpErrorResponse({ error: 'Forbidden', status: 403 })));
        jest.spyOn(alertService, 'error');

        comp.ngOnInit();
        comp.startQuiz(456);
        expect(service.start).toHaveBeenCalledWith(456);
        expect(service.start).toHaveBeenCalledTimes(1);
        expect(alertService.error).toHaveBeenCalledTimes(1);
        expect(service.find).toHaveBeenCalledWith(456);
        expect(service.find).toHaveBeenCalledTimes(1);
    });

    it('Should make quiz visible', () => {
        const headers = new HttpHeaders().append('link', 'link;link');
        jest.spyOn(service, 'setVisible').mockReturnValue(
            of(
                new HttpResponse({
                    body: quizExercise,
                    headers,
                }),
            ),
        );

        comp.ngOnInit();
        comp.showQuiz(456);
        expect(service.setVisible).toHaveBeenCalledWith(456);
        expect(service.setVisible).toHaveBeenCalledTimes(1);
    });

    it('Should not make quiz visible on error', () => {
        const headers = new HttpHeaders().append('link', 'link;link');
        jest.spyOn(service, 'find').mockReturnValue(
            of(
                new HttpResponse({
                    body: quizExercise,
                    headers,
                }),
            ),
        );
        jest.spyOn(service, 'setVisible').mockReturnValue(throwError(new HttpErrorResponse({ error: 'Forbidden', status: 403 })));
        jest.spyOn(alertService, 'error');

        comp.ngOnInit();
        comp.showQuiz(456);
        expect(service.setVisible).toHaveBeenCalledWith(456);
        expect(service.setVisible).toHaveBeenCalledTimes(1);
        expect(alertService.error).toHaveBeenCalledTimes(1);
        expect(service.find).toHaveBeenCalledWith(456);
        expect(service.find).toHaveBeenCalledTimes(1);
    });

    it('Should delete quiz', () => {
        const headers = new HttpHeaders().append('link', 'link;link');
        jest.spyOn(service, 'delete').mockReturnValue(
            of(
                new HttpResponse({
                    body: {},
                    headers,
                }),
            ),
        );

        comp.ngOnInit();
        comp.deleteQuizExercise(456);
        expect(service.delete).toHaveBeenCalledWith(456);
        expect(service.delete).toHaveBeenCalledTimes(1);
    });

    it('Should export quiz', () => {
        const headers = new HttpHeaders().append('link', 'link;link');
        jest.spyOn(service, 'find').mockReturnValue(
            of(
                new HttpResponse({
                    body: quizExercise,
                    headers,
                }),
            ),
        );
        jest.spyOn(service, 'exportQuiz');

        comp.ngOnInit();
        comp.exportQuizById(456, true);
        expect(service.find).toHaveBeenCalledWith(456);
        expect(service.find).toHaveBeenCalledTimes(1);
        expect(service.exportQuiz).toHaveBeenCalledWith(undefined, true, 'Quiz Exercise');
        expect(service.exportQuiz).toHaveBeenCalledTimes(1);
    });

    it('Should return quiz is over', () => {
        quizExercise.isPlannedToStart = true;
        quizExercise.releaseDate = dayjs().add(-20, 'seconds');
        quizExercise.duration = 10;
        expect(comp.quizIsOver(quizExercise)).toBe(true);
    });

    it('Should return quiz is not over', () => {
        quizExercise.isPlannedToStart = false;
        quizExercise.releaseDate = dayjs().add(20, 'seconds');
        quizExercise.duration = 10;
        expect(comp.quizIsOver(quizExercise)).toBe(false);
    });

    it('Should return quiz id', () => {
        expect(comp.trackId(0, quizExercise)).toBe(456);
    });

    describe('QuizExercise Search Exercises', () => {
        it('Should show all exercises', () => {
            // WHEN
            comp.exerciseFilter = new ExerciseFilter('Quiz', '', 'quiz');

            // THEN
            expect(comp.quizExercises).toHaveLength(1);
            expect(comp.filteredQuizExercises).toHaveLength(1);
        });

        it('Should show no exercises', () => {
            // WHEN
            comp.exerciseFilter = new ExerciseFilter('Prog', '', 'all');

            // THEN
            expect(comp.quizExercises).toHaveLength(1);
            expect(comp.filteredQuizExercises).toHaveLength(0);
        });
    });
});
