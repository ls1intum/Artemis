import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { of } from 'rxjs';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { MockProvider, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';
import { MultipleChoiceVisualQuestionComponent } from 'app/exercises/quiz/shared/questions/multiple-choice-question/multiple-choice-visual-question.component';
import { MultipleChoiceQuestion } from 'app/entities/quiz/multiple-choice-question.model';
import { AnswerOption } from 'app/entities/quiz/answer-option.model';

describe('QuizVisualEditorComponent', () => {
    let fixture: ComponentFixture<MultipleChoiceVisualQuestionComponent>;
    let comp: MultipleChoiceVisualQuestionComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [MultipleChoiceVisualQuestionComponent, MockPipe(ArtemisTranslatePipe)],
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

    it('should initialize', fakeAsync(() => {
        fixture.detectChanges();
        expect(comp).not.toBeNull();
    }));

    it('parse the given question properly to markdown', fakeAsync(() => {
        fixture.detectChanges();
        expect(comp).not.toBeNull();

        comp.question.text = 'Hallo';
        comp.question.hint = 'Hint';

        const answerOption = new AnswerOption();
        answerOption.text = 'Answer';
        answerOption.isCorrect = true;
        comp.question.answerOptions = [answerOption];

        const markdown = comp.parseQuestion();
        const expected = 'Hallo\n\t[hint] Hint\n\n[correct] Answer';

        expect(markdown).toBe(expected);
    }));

    it('delete an answer option', fakeAsync(() => {
        fixture.detectChanges();
        expect(comp).not.toBeNull();

        const answerOption = new AnswerOption();
        comp.question.answerOptions = [answerOption];
        expect(comp.question.answerOptions).toHaveLength(1);

        comp.deleteAnswer(0);

        expect(comp.question.answerOptions).toBeEmpty();
    }));

    it('toggle the isCorrect state', fakeAsync(() => {
        fixture.detectChanges();
        expect(comp).not.toBeNull();

        const answerOption = new AnswerOption();
        answerOption.text = 'Answer';
        answerOption.isCorrect = true;
        comp.question.answerOptions = [answerOption];

        expect(answerOption.isCorrect).toBeTrue();

        comp.toggleIsCorrect(answerOption);

        expect(answerOption.isCorrect).toBeFalse();
    }));

    it('add a new answer option', fakeAsync(() => {
        fixture.detectChanges();
        expect(comp).not.toBeNull();

        expect(comp.question.answerOptions).toBeUndefined();

        comp.newOption.text = 'Answer';
        comp.newOption.isCorrect = true;

        comp.addNewAnswer();

        expect(comp.question.answerOptions).toHaveLength(1);
        expect(comp.question.answerOptions![0].text).toBe('Answer');
    }));
});
