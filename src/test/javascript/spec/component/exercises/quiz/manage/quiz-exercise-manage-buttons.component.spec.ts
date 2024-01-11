import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { TranslateService } from '@ngx-translate/core';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import { QuizBatch, QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { Course } from 'app/entities/course.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { QuizExerciseManageButtonsComponent } from 'app/exercises/quiz/manage/quiz-exercise-manage-buttons.component';
import { MockSyncStorage } from '../../../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../../../test.module';

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
            imports: [ArtemisTestModule],
            declarations: [QuizExerciseManageButtonsComponent],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .overrideTemplate(QuizExerciseManageButtonsComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(QuizExerciseManageButtonsComponent);
        comp = fixture.componentInstance;
        quizExerciseService = fixture.debugElement.injector.get(QuizExerciseService);
        exerciseService = fixture.debugElement.injector.get(ExerciseService);
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
        comp.quizExercise = quizExercise;
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

        comp.quizExercise = quizExercise;
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

        comp.quizExercise = quizExercise;
        comp.ngOnInit();
        comp.exportQuizExercise(true);
        expect(quizExerciseService.exportQuiz).toHaveBeenCalledWith([], true, 'Quiz Exercise');
        expect(quizExerciseService.exportQuiz).toHaveBeenCalledOnce();
    });
});
