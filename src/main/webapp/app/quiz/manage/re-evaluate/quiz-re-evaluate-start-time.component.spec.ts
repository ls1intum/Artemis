import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { MockComponent, MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import dayjs from 'dayjs/esm';
import { DialogService } from 'primeng/dynamicdialog';
import { TumAetUiDatePickerComponent } from '@tumaet/ui-angular';
import { QuizReEvaluateComponent } from 'app/quiz/manage/re-evaluate/quiz-re-evaluate.component';
import { ReEvaluateMultipleChoiceQuestionComponent } from 'app/quiz/manage/re-evaluate/multiple-choice-question/re-evaluate-multiple-choice-question.component';
import { ReEvaluateDragAndDropQuestionComponent } from 'app/quiz/manage/re-evaluate/drag-and-drop-question/re-evaluate-drag-and-drop-question.component';
import { ReEvaluateShortAnswerQuestionComponent } from 'app/quiz/manage/re-evaluate/short-answer-question/re-evaluate-short-answer-question.component';
import { IncludedInOverallScorePickerComponent } from 'app/exercise/included-in-overall-score-picker/included-in-overall-score-picker.component';
import { QuizExerciseService } from 'app/quiz/manage/service/quiz-exercise.service';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { Course } from 'app/course/shared/entities/course.model';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'src/test/javascript/spec/helpers/mocks/service/mock-account.service';
import { MockRouter } from 'src/test/javascript/spec/helpers/mocks/mock-router';
import { MockTranslateService } from 'src/test/javascript/spec/helpers/mocks/service/mock-translate.service';

/** Renders the real template: only the start time field is of interest, the question editors are replaced. */
describe('QuizReEvaluateComponent start time', () => {
    let fixture: ComponentFixture<QuizReEvaluateComponent>;

    beforeEach(async () => {
        const quizExercise = new QuizExercise({ id: 123 } as Course, undefined);
        quizExercise.id = 456;
        quizExercise.title = 'MyQuiz';
        quizExercise.releaseDate = dayjs('2030-06-02T12:00:00');
        quizExercise.quizQuestions = [];

        await TestBed.configureTestingModule({
            providers: [
                MockProvider(DialogService),
                MockProvider(QuizExerciseService, { find: () => of(new HttpResponse({ body: quizExercise })) }),
                { provide: ActivatedRoute, useValue: { params: of({ exerciseId: 456 }) } },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useClass: MockRouter },
                { provide: AccountService, useClass: MockAccountService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .overrideComponent(QuizReEvaluateComponent, {
                remove: {
                    imports: [
                        IncludedInOverallScorePickerComponent,
                        ReEvaluateDragAndDropQuestionComponent,
                        ReEvaluateShortAnswerQuestionComponent,
                        ReEvaluateMultipleChoiceQuestionComponent,
                    ],
                },
                add: {
                    imports: [
                        MockComponent(IncludedInOverallScorePickerComponent),
                        MockComponent(ReEvaluateDragAndDropQuestionComponent),
                        MockComponent(ReEvaluateShortAnswerQuestionComponent),
                        MockComponent(ReEvaluateMultipleChoiceQuestionComponent),
                    ],
                },
            })
            .compileComponents();
        fixture = TestBed.createComponent(QuizReEvaluateComponent);
        fixture.componentInstance.ngOnInit();
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
    });

    it('should show the start time disabled and labelled by the translation key', () => {
        const picker = fixture.debugElement.query(By.directive(TumAetUiDatePickerComponent)).componentInstance as TumAetUiDatePickerComponent;

        expect(picker.disabled()).toBe(true);
        // the picker translates the label itself, so it must be given the key and not an already translated text
        expect(picker.labelName()).toBe('artemisApp.quizExercise.startTime');
        expect(picker.value()!.isSame(dayjs('2030-06-02T12:00:00'))).toBe(true);
    });
});
