import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { of } from 'rxjs';
import { HttpHeaders, HttpResponse, provideHttpClient } from '@angular/common/http';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { QuizExerciseComponent } from 'app/quiz/manage/exercise/quiz-exercise.component';
import { QuizExerciseService } from 'app/quiz/manage/service/quiz-exercise.service';
import { QuizBatch, QuizExercise, QuizStatus } from 'app/quiz/shared/entities/quiz-exercise.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ExerciseFilter } from 'app/exercise/shared/entities/exercise/exercise-filter.model';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { EventManager } from 'app/shared/service/event-manager.service';
import { MockProvider } from 'ng-mocks';

describe('QuizExercise Management Component', () => {
    let comp: QuizExerciseComponent;
    let fixture: ComponentFixture<QuizExerciseComponent>;
    let quizExerciseService: QuizExerciseService;
    let accountService: AccountService;

    const course = { id: 123 } as Course;
    const quizExercise = new QuizExercise(course, undefined);
    quizExercise.id = 456;
    quizExercise.title = 'Quiz Exercise';
    const quizBatch = new QuizBatch();
    quizBatch.id = 567;
    const route = { snapshot: { paramMap: convertToParamMap({ courseId: course.id }) } } as any as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                LocalStorageService,
                SessionStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(EventManager),
            ],
        })
            .overrideTemplate(QuizExerciseComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(QuizExerciseComponent);
        comp = fixture.componentInstance;
        quizExerciseService = TestBed.inject(QuizExerciseService);
        accountService = TestBed.inject(AccountService);

        comp.course = course;
        comp.quizExercises.set([quizExercise]);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should call loadExercises on init', () => {
        // GIVEN
        const headers = new HttpHeaders().append('link', 'link;link');
        jest.spyOn(quizExerciseService, 'findForCourse').mockReturnValue(
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
        expect(quizExerciseService.findForCourse).toHaveBeenCalledOnce();
        expect(comp.quizExercises()[0]).toEqual(quizExercise);
    });

    it('should delete multiple quizzes', () => {
        const headers = new HttpHeaders().append('link', 'link;link');
        jest.spyOn(quizExerciseService, 'delete').mockReturnValue(
            of(
                new HttpResponse({
                    body: {},
                    headers,
                }),
            ),
        );

        comp.ngOnInit();
        // @ts-ignore
        comp.deleteMultipleExercises([{ id: 1 }, { id: 2 }, { id: 3 }] as QuizExercise[], comp.quizExerciseService);
        expect(quizExerciseService.delete).toHaveBeenCalledTimes(3);
    });
    it('should return quiz id', () => {
        expect(comp.trackId(0, quizExercise)).toBe(456);
    });

    describe('QuizExercise Search Exercises', () => {
        it('should show all exercises', () => {
            // WHEN
            comp.exerciseFilter = new ExerciseFilter('Quiz', '', 'quiz');

            // THEN
            expect(comp.quizExercises()).toHaveLength(1);
            expect(comp.filteredQuizExercises).toHaveLength(1);
        });

        it('should show no exercises', () => {
            // WHEN
            comp.exerciseFilter = new ExerciseFilter('Prog', '', 'all');

            // THEN
            expect(comp.quizExercises()).toHaveLength(1);
            expect(comp.filteredQuizExercises).toHaveLength(0);
        });
    });

    it('should have working selection', () => {
        // WHEN
        comp.toggleExercise(quizExercise);

        // THEN
        expect(comp.selectedExercises[0]).toContainEntry(['id', quizExercise.id]);
        expect(comp.allChecked).toEqual(comp.selectedExercises.length === comp.quizExercises().length);
    });

    it('should load one', () => {
        const findExerciseSpy = jest.spyOn(quizExerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: quizExercise })));
        jest.spyOn(accountService, 'isAtLeastTutorInCourse').mockReturnValue(true);
        jest.spyOn(accountService, 'isAtLeastEditorInCourse').mockReturnValue(true);
        jest.spyOn(accountService, 'isAtLeastInstructorInCourse').mockReturnValue(true);
        jest.spyOn(quizExerciseService, 'getStatus').mockReturnValue(QuizStatus.VISIBLE);
        comp.loadOne(quizExercise.id!);
        expect(findExerciseSpy).toHaveBeenCalledOnce();
        expect(comp.quizExercises()).toHaveLength(1);
        expect(comp.quizExercises()[0].isAtLeastEditor).toBeTruthy();
    });

    it('should correctly calculate isEditable when loadExercises is called and isQuizEditable returns false', () => {
        const headers = new HttpHeaders().append('link', 'link;link');
        jest.spyOn(quizExerciseService, 'findForCourse').mockReturnValue(
            of(
                new HttpResponse({
                    body: [quizExercise],
                    headers,
                }),
            ),
        );

        jest.spyOn(quizExerciseService, 'getStatus').mockReturnValue(QuizStatus.ACTIVE);

        // WHEN
        comp.ngOnInit();

        // THEN
        expect(quizExerciseService.findForCourse).toHaveBeenCalledOnce();
        expect(comp.quizExercises()[0]).toEqual(quizExercise);
        expect(comp.quizExercises()[0].isEditable).toBeFalse();
    });
});
