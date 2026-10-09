import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { of } from 'rxjs';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ArtemisNavigationUtilService } from 'app/foundation/util/navigation.utils';
import { MultipleChoiceVisualQuestionComponent } from 'app/quiz/shared/questions/multiple-choice-question/visual-question/multiple-choice-visual-question.component';
import { MultipleChoiceQuestion } from 'app/quiz/shared/entities/multiple-choice-question.model';
import { AnswerOption } from 'app/quiz/shared/entities/answer-option.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';

describe('QuizVisualEditorComponent', () => {
    let fixture: ComponentFixture<MultipleChoiceVisualQuestionComponent>;
    let comp: MultipleChoiceVisualQuestionComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [MultipleChoiceVisualQuestionComponent, MockModule(NgbTooltipModule), FaIconComponent, MockPipe(ArtemisTranslatePipe)],
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
        }).compileComponents();

        fixture = TestBed.createComponent(MultipleChoiceVisualQuestionComponent);
        comp = fixture.componentInstance;

        fixture.componentRef.setInput('question', new MultipleChoiceQuestion());
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('keeps the current correct answer enabled while other single-choice answers are unavailable', () => {
        const correct = { isCorrect: true } as AnswerOption;
        const incorrect = { isCorrect: false } as AnswerOption;
        fixture.componentRef.setInput('question', { singleChoice: true, answerOptions: [correct, incorrect] });
        fixture.detectChanges();
        const [selected, other] = Array.from(fixture.nativeElement.querySelectorAll('.visual-answer')) as HTMLElement[];
        const changed = vi.spyOn(comp.questionChanged, 'emit');
        expect(selected.getAttribute('role')).toBe('button');
        expect(selected.tabIndex).toBe(0);
        expect(other.hasAttribute('role')).toBe(false);
        expect(other.tabIndex).toBe(-1);
        for (const [type, key] of [
            ['keydown', 'Enter'],
            ['keydown', ' '],
            ['keyup', ' '],
        ]) {
            const event = new KeyboardEvent(type, { key, bubbles: true, cancelable: true });
            other.dispatchEvent(event);
            expect(event.defaultPrevented).toBe(false);
        }
        expect(changed).not.toHaveBeenCalled();
        selected.dispatchEvent(new KeyboardEvent('keyup', { key: ' ', bubbles: true }));
        fixture.detectChanges();
        expect(correct.isCorrect).toBe(false);
        expect(other.getAttribute('role')).toBe('button');
        expect(other.tabIndex).toBe(0);
        other.dispatchEvent(new KeyboardEvent('keyup', { key: ' ', bubbles: true }));
        expect(incorrect.isCorrect).toBe(true);
        expect(changed).toHaveBeenCalledTimes(2);
    });

    it('parse the given question properly to markdown', () => {
        fixture.detectChanges();

        comp.question().text = 'Hallo';
        comp.question().hint = 'Hint';
        comp.question().explanation = 'Exp';

        const answerOption = new AnswerOption();
        answerOption.text = 'Answer';
        answerOption.hint = 'H2';
        answerOption.explanation = 'Exp2';
        answerOption.isCorrect = true;
        comp.question().answerOptions = [answerOption];

        const markdown = comp.parseQuestion();
        const expected = 'Hallo\n\t[hint] Hint\n\t[exp] Exp\n\n[correct] Answer\n\t[hint] H2\n\t[exp] Exp2';

        expect(markdown).toBe(expected);
    });

    it('delete an answer option', () => {
        fixture.detectChanges();

        const answerOption = new AnswerOption();
        const answerOption2 = new AnswerOption();
        comp.question().answerOptions = [answerOption, answerOption2];
        expect(comp.question().answerOptions).toHaveLength(2);

        comp.deleteAnswer(0);

        expect(comp.question().answerOptions).toHaveLength(1);
    });

    it('toggle the isCorrect state', () => {
        fixture.detectChanges();

        const answerOption = new AnswerOption();
        answerOption.text = 'Answer';
        answerOption.isCorrect = true;
        comp.question().answerOptions = [answerOption];

        expect(answerOption.isCorrect).toBe(true);

        comp.toggleIsCorrect(answerOption);

        expect(answerOption.isCorrect).toBe(false);
    });

    it('toggles correctness only once after repeated Space keydown events', () => {
        const answerOption = new AnswerOption();
        answerOption.text = 'Answer';
        answerOption.isCorrect = false;
        comp.question().answerOptions = [answerOption];
        fixture.detectChanges();

        const correctnessButton = fixture.nativeElement.querySelector('.visual-answer') as HTMLElement;
        correctnessButton.dispatchEvent(new KeyboardEvent('keydown', { key: ' ', bubbles: true, cancelable: true }));
        correctnessButton.dispatchEvent(new KeyboardEvent('keydown', { key: ' ', bubbles: true, cancelable: true, repeat: true }));
        expect(answerOption.isCorrect).toBe(false);

        correctnessButton.dispatchEvent(new KeyboardEvent('keyup', { key: ' ', bubbles: true }));
        fixture.detectChanges();
        expect(answerOption.isCorrect).toBe(true);
        expect(correctnessButton.getAttribute('aria-pressed')).toBe('true');
    });

    it('does not toggle the if single mode and already has correct answer', () => {
        fixture.detectChanges();

        comp.question().singleChoice = true;

        const answerOption = new AnswerOption();
        answerOption.text = 'Answer';
        answerOption.isCorrect = true;

        const answerOption2 = new AnswerOption();
        comp.question().answerOptions = [answerOption, answerOption2];

        expect(answerOption2.isCorrect).toBe(false);

        comp.toggleIsCorrect(answerOption2);

        expect(answerOption2.isCorrect).toBe(false);
    });

    it('add a new answer option', () => {
        fixture.detectChanges();

        expect(comp.question().answerOptions).toBeUndefined();

        comp.addNewAnswer();

        expect(comp.question().answerOptions).toHaveLength(1);
    });
});
