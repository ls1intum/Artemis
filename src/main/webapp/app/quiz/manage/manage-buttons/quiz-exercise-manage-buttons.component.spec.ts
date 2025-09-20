import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { of } from 'rxjs';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { QuizExerciseService } from 'app/quiz/manage/service/quiz-exercise.service';
import { QuizBatch, QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { QuizExerciseManageButtonsComponent } from 'app/quiz/manage/manage-buttons/quiz-exercise-manage-buttons.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';

describe('QuizExercise Management Buttons Component', () => {
    let comp: QuizExerciseManageButtonsComponent;
    let fixture: ComponentFixture<QuizExerciseManageButtonsComponent>;
    let quizExerciseService: QuizExerciseService;
    let exerciseService: ExerciseService;

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
                { provide: AccountService, useClass: MockAccountService },
                provideHttpClient(),
            ],
        })
            .overrideTemplate(QuizExerciseManageButtonsComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(QuizExerciseManageButtonsComponent);
        comp = fixture.componentInstance;
        quizExerciseService = TestBed.inject(QuizExerciseService);
        exerciseService = TestBed.inject(ExerciseService);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should reset quiz', () => {
        jest.spyOn(exerciseService, 'reset').mockReturnValue(
            of(
                new HttpResponse({
                    body: undefined,
                }),
            ),
        );
        fixture.componentRef.setInput('quizExercise', quizExercise);
        comp.ngOnInit();
        comp.resetQuizExercise();
        expect(exerciseService.reset).toHaveBeenCalledWith(456);
        expect(exerciseService.reset).toHaveBeenCalledOnce();
    });

    it('should delete quiz', () => {
        jest.spyOn(quizExerciseService, 'delete').mockReturnValue(
            of(
                new HttpResponse({
                    body: {},
                }),
            ),
        );

        fixture.componentRef.setInput('quizExercise', quizExercise);
        comp.ngOnInit();
        comp.deleteQuizExercise();
        expect(quizExerciseService.delete).toHaveBeenCalledWith(456);
        expect(quizExerciseService.delete).toHaveBeenCalledOnce();
    });

    it('should export quiz', () => {
        jest.spyOn(quizExerciseService, 'find').mockReturnValue(
            of(
                new HttpResponse({
                    body: quizExercise,
                }),
            ),
        );
        jest.spyOn(quizExerciseService, 'exportQuiz');

        fixture.componentRef.setInput('quizExercise', quizExercise);
        comp.ngOnInit();
        comp.exportQuizExercise(true);
        expect(quizExerciseService.exportQuiz).toHaveBeenCalledWith([], true, 'Quiz Exercise');
        expect(quizExerciseService.exportQuiz).toHaveBeenCalledOnce();
    });
});
