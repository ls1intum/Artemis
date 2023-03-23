import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { of } from 'rxjs';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { MockComponent, MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';
import { MultipleChoiceVisualQuestionComponent } from 'app/exercises/quiz/shared/questions/multiple-choice-question/multiple-choice-visual-question.component';
import { MultipleChoiceQuestion } from 'app/entities/quiz/multiple-choice-question.model';
import { AnswerOption } from 'app/entities/quiz/answer-option.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgModel } from '@angular/forms';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';

describe('QuizVisualEditorComponent', () => {
    let fixture: ComponentFixture<MultipleChoiceVisualQuestionComponent>;
    let comp: MultipleChoiceVisualQuestionComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MockModule(NgbTooltipModule)],
            declarations: [MultipleChoiceVisualQuestionComponent, MockPipe(ArtemisTranslatePipe), MockComponent(FaIconComponent), MockDirective(NgModel)],
            providers: [
                MockProvider(ArtemisNavigationUtilService),
                MockProvider(CourseManagementService),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useClass: MockRouter },
                {
                    provide: ActivatedRoute,
                    useValue: { queryParams: of({}) },
                },
            ],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(MultipleChoiceVisualQuestionComponent);
                comp = fixture.componentInstance;

                comp.question = new MultipleChoiceQuestion();
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('parse the given question properly to markdown', fakeAsync(() => {
        fixture.detectChanges();

        comp.question.text = 'Hallo';
        comp.question.hint = 'Hint';
        comp.question.explanation = 'Exp';

        const answerOption = new AnswerOption();
        answerOption.text = 'Answer';
        answerOption.hint = 'H2';
        answerOption.explanation = 'Exp2';
        answerOption.isCorrect = true;
        comp.question.answerOptions = [answerOption];

        const markdown = comp.parseQuestion();
        const expected = 'Hallo\n\t[hint] Hint\n\t[exp] Exp\n\n[correct] Answer\n\t[hint] H2\n\t[exp] Exp2';

        expect(markdown).toBe(expected);
    }));

    it('delete an answer option', fakeAsync(() => {
        fixture.detectChanges();

        const answerOption = new AnswerOption();
        const answerOption2 = new AnswerOption();
        comp.question.answerOptions = [answerOption, answerOption2];
        expect(comp.question.answerOptions).toHaveLength(2);

        comp.deleteAnswer(0);

        expect(comp.question.answerOptions).toHaveLength(1);
    }));

    it('toggle the isCorrect state', fakeAsync(() => {
        fixture.detectChanges();

        const answerOption = new AnswerOption();
        answerOption.text = 'Answer';
        answerOption.isCorrect = true;
        comp.question.answerOptions = [answerOption];

        expect(answerOption.isCorrect).toBeTrue();

        comp.toggleIsCorrect(answerOption);

        expect(answerOption.isCorrect).toBeFalse();
    }));

    it('does not toggle the if single mode and already has correct answer', fakeAsync(() => {
        fixture.detectChanges();

        comp.question.singleChoice = true;

        const answerOption = new AnswerOption();
        answerOption.text = 'Answer';
        answerOption.isCorrect = true;

        const answerOption2 = new AnswerOption();
        comp.question.answerOptions = [answerOption, answerOption2];

        expect(answerOption2.isCorrect).toBeFalse();

        comp.toggleIsCorrect(answerOption2);

        expect(answerOption2.isCorrect).toBeFalse();
    }));

    it('add a new answer option', fakeAsync(() => {
        fixture.detectChanges();

        expect(comp.question.answerOptions).toBeUndefined();

        comp.addNewAnswer();

        expect(comp.question.answerOptions).toHaveLength(1);
    }));
});
