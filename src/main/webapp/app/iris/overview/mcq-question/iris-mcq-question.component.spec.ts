import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { IrisMcqQuestionComponent } from './iris-mcq-question.component';
import { McqData } from 'app/iris/shared/entities/iris-content-type.model';

const sampleMcq: McqData = {
    type: 'mcq',
    question: 'What is 2 + 2?',
    options: [
        { text: '3', correct: false },
        { text: '4', correct: true },
        { text: '5', correct: false },
        { text: '6', correct: false },
    ],
    explanation: '2 + 2 equals 4.',
};

describe('IrisMcqQuestionComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<IrisMcqQuestionComponent>;
    let component: IrisMcqQuestionComponent;

    const render = (mcqData: McqData = sampleMcq) => {
        fixture.componentRef.setInput('mcqData', mcqData);
        fixture.detectChanges();
        return fixture.nativeElement as HTMLElement;
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [IrisMcqQuestionComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        });

        fixture = TestBed.createComponent(IrisMcqQuestionComponent);
        component = fixture.componentInstance;
    });

    it('should render the question text', () => {
        const el = render();
        expect(el.querySelector('.mcq-question')?.textContent).toContain('What is 2 + 2?');
    });

    it('should render four options', () => {
        const el = render();
        const options = el.querySelectorAll('.mcq-option');
        expect(options).toHaveLength(4);
    });

    it('should render option labels A through D', () => {
        const el = render();
        const labels = el.querySelectorAll('.mcq-option-label');
        expect(labels[0].textContent).toBe('A');
        expect(labels[1].textContent).toBe('B');
        expect(labels[2].textContent).toBe('C');
        expect(labels[3].textContent).toBe('D');
    });

    it('should render the submit button initially', () => {
        const el = render();
        expect(el.querySelector('.mcq-submit')).toBeTruthy();
    });

    it('should not show feedback before submission', () => {
        const el = render();
        expect(el.querySelector('.mcq-feedback')).toBeFalsy();
        expect(el.querySelector('.mcq-explanation')).toBeFalsy();
    });

    it('should select an option on click', () => {
        const el = render();
        const options = el.querySelectorAll('.mcq-option');
        (options[1] as HTMLButtonElement).click();
        fixture.detectChanges();
        expect(component.selectedIndex()).toBe(1);
        expect(options[1].classList.contains('selected')).toBe(true);
    });

    it('should disable submit button when no option is selected', () => {
        const el = render();
        const submitButton = el.querySelector('.mcq-submit') as HTMLButtonElement;
        expect(submitButton.disabled).toBe(true);
    });

    it('should enable submit button after selecting an option', () => {
        const el = render();
        const options = el.querySelectorAll('.mcq-option');
        (options[0] as HTMLButtonElement).click();
        fixture.detectChanges();
        const submitButton = el.querySelector('.mcq-submit') as HTMLButtonElement;
        expect(submitButton.disabled).toBe(false);
    });

    it('should show correct feedback when correct answer is selected', () => {
        const el = render();
        const options = el.querySelectorAll('.mcq-option');
        (options[1] as HTMLButtonElement).click(); // correct answer
        fixture.detectChanges();
        (el.querySelector('.mcq-submit') as HTMLButtonElement).click();
        fixture.detectChanges();

        expect(component.submitted()).toBe(true);
        expect(component.isCorrectAnswer()).toBe(true);
        expect(el.querySelector('.mcq-feedback-correct')).toBeTruthy();
        expect(el.querySelector('.mcq-feedback-incorrect')).toBeFalsy();
    });

    it('should show incorrect feedback when wrong answer is selected', () => {
        const el = render();
        const options = el.querySelectorAll('.mcq-option');
        (options[0] as HTMLButtonElement).click(); // incorrect answer
        fixture.detectChanges();
        (el.querySelector('.mcq-submit') as HTMLButtonElement).click();
        fixture.detectChanges();

        expect(component.submitted()).toBe(true);
        expect(component.isCorrectAnswer()).toBe(false);
        expect(el.querySelector('.mcq-feedback-incorrect')).toBeTruthy();
        expect(el.querySelector('.mcq-feedback-correct')).toBeFalsy();
    });

    it('should show explanation after submission', () => {
        const el = render();
        const options = el.querySelectorAll('.mcq-option');
        (options[1] as HTMLButtonElement).click();
        fixture.detectChanges();
        (el.querySelector('.mcq-submit') as HTMLButtonElement).click();
        fixture.detectChanges();

        const explanation = el.querySelector('.mcq-explanation');
        expect(explanation).toBeTruthy();
        expect(explanation?.textContent).toContain('2 + 2 equals 4.');
    });

    it('should hide submit button after submission', () => {
        const el = render();
        const options = el.querySelectorAll('.mcq-option');
        (options[1] as HTMLButtonElement).click();
        fixture.detectChanges();
        (el.querySelector('.mcq-submit') as HTMLButtonElement).click();
        fixture.detectChanges();

        expect(el.querySelector('.mcq-submit')).toBeFalsy();
    });

    it('should highlight correct option with correct class after submission', () => {
        const el = render();
        const options = el.querySelectorAll('.mcq-option');
        (options[0] as HTMLButtonElement).click(); // wrong answer
        fixture.detectChanges();
        (el.querySelector('.mcq-submit') as HTMLButtonElement).click();
        fixture.detectChanges();

        expect(options[0].classList.contains('incorrect')).toBe(true);
        expect(options[1].classList.contains('correct')).toBe(true);
    });

    it('should disable all options after submission', () => {
        const el = render();
        const options = el.querySelectorAll('.mcq-option');
        (options[1] as HTMLButtonElement).click();
        fixture.detectChanges();
        (el.querySelector('.mcq-submit') as HTMLButtonElement).click();
        fixture.detectChanges();

        options.forEach((option) => {
            expect((option as HTMLButtonElement).disabled).toBe(true);
        });
    });

    it('should not change selection after submission', () => {
        const el = render();
        const options = el.querySelectorAll('.mcq-option');
        (options[1] as HTMLButtonElement).click();
        fixture.detectChanges();
        (el.querySelector('.mcq-submit') as HTMLButtonElement).click();
        fixture.detectChanges();

        component.selectOption(0);
        expect(component.selectedIndex()).toBe(1);
    });

    it('should not submit when no option is selected', () => {
        render();
        component.submit();
        expect(component.submitted()).toBe(false);
    });

    it('should not submit again after already submitted', () => {
        render();
        component.selectOption(1);
        component.submit();
        expect(component.submitted()).toBe(true);
        // second submit should be a no-op
        component.submit();
        expect(component.submitted()).toBe(true);
    });

    it('should return false for isCorrectAnswer when no option is selected', () => {
        render();
        expect(component.isCorrectAnswer()).toBe(false);
    });
});
