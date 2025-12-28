import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { QuizExerciseService } from 'app/quiz/manage/service/quiz-exercise.service';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { MultipleChoiceQuestion } from 'app/quiz/shared/entities/multiple-choice-question.model';
import { DragAndDropQuestion } from 'app/quiz/shared/entities/drag-and-drop-question.model';
import { QuizReEvaluateWarningComponent } from 'app/quiz/manage/re-evaluate/warning/quiz-re-evaluate-warning.component';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { MockProvider } from 'ng-mocks';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';

describe('QuizExercise Re-evaluate Warning Component', () => {
    setupTestBed({ zoneless: true });

    let comp: QuizReEvaluateWarningComponent;
    let fixture: ComponentFixture<QuizReEvaluateWarningComponent>;
    let quizService: QuizExerciseService;

    const course = { id: 123 } as Course;
    const quizExercise = new QuizExercise(course, undefined);
    quizExercise.id = 456;
    quizExercise.title = 'MyQuiz';

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                MockProvider(NgbActiveModal),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                provideHttpClient(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(QuizReEvaluateWarningComponent);
        comp = fixture.componentInstance;
        quizService = TestBed.inject(QuizExerciseService);
        const quizQuestion1 = new MultipleChoiceQuestion();
        const quizQuestion2 = new DragAndDropQuestion();
        quizExercise.quizQuestions = [quizQuestion1, quizQuestion2];

        // initial value
        comp.quizExercise = quizExercise;
        vi.spyOn(quizService, 'find').mockReturnValue(of(new HttpResponse({ body: quizExercise })));
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize quiz exercise', () => {
        comp.ngOnInit();
        expect(comp.backUpQuiz).toEqual(quizExercise);
    });
});
