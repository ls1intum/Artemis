import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
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
        const options = el.querySelectorAll<HTMLInputElement>('.mcq-option-input');
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
        const options = el.querySelectorAll<HTMLInputElement>('.mcq-option-input');
        options[1].click();
        fixture.detectChanges();
        expect(component.selectedIndex()).toBe(1);
        expect(options[1].nextElementSibling!.classList.contains('selected')).toBe(true);
    });

    it('should disable submit button when no option is selected', () => {
        const el = render();
        const submitButton = el.querySelector('.mcq-submit') as HTMLButtonElement;
        expect(submitButton.disabled).toBe(true);
    });

    it('should enable submit button after selecting an option', () => {
        const el = render();
        const options = el.querySelectorAll<HTMLInputElement>('.mcq-option-input');
        options[0].click();
        fixture.detectChanges();
        const submitButton = el.querySelector('.mcq-submit') as HTMLButtonElement;
        expect(submitButton.disabled).toBe(false);
    });

    it('should show correct feedback when correct answer is selected', () => {
        const el = render();
        const options = el.querySelectorAll<HTMLInputElement>('.mcq-option-input');
        options[1].click(); // correct answer
        fixture.detectChanges();
        (el.querySelector('.mcq-submit') as HTMLButtonElement).click();
        fixture.detectChanges();

        expect(component.submitted()).toBe(true);
        expect(component.correctAnswer()).toBe(true);
        expect(el.querySelector('tumaet-ui-message')?.getAttribute('data-severity')).toBe('success');
        expect(el.querySelector('[role="status"]')?.textContent).toContain('artemisApp.iris.mcq.correct');
    });

    it('should show incorrect feedback when wrong answer is selected', () => {
        const el = render();
        const options = el.querySelectorAll<HTMLInputElement>('.mcq-option-input');
        options[0].click(); // incorrect answer
        fixture.detectChanges();
        (el.querySelector('.mcq-submit') as HTMLButtonElement).click();
        fixture.detectChanges();

        expect(component.submitted()).toBe(true);
        expect(component.correctAnswer()).toBe(false);
        expect(el.querySelector('tumaet-ui-message')?.getAttribute('data-severity')).toBe('error');
        expect(el.querySelector('[role="alert"]')?.textContent).toContain('artemisApp.iris.mcq.incorrect');
    });

    it('should show explanation after submission', () => {
        const el = render();
        const options = el.querySelectorAll<HTMLInputElement>('.mcq-option-input');
        options[1].click();
        fixture.detectChanges();
        (el.querySelector('.mcq-submit') as HTMLButtonElement).click();
        fixture.detectChanges();

        const explanation = el.querySelector('.mcq-explanation');
        expect(explanation).toBeTruthy();
        expect(explanation?.textContent).toContain('2 + 2 equals 4.');
    });

    it('should hide submit button after submission', () => {
        const el = render();
        const options = el.querySelectorAll<HTMLInputElement>('.mcq-option-input');
        options[1].click();
        fixture.detectChanges();
        (el.querySelector('.mcq-submit') as HTMLButtonElement).click();
        fixture.detectChanges();

        expect(el.querySelector('.mcq-submit')).toBeFalsy();
    });

    it('should highlight correct option with correct class after submission', () => {
        const el = render();
        const options = el.querySelectorAll<HTMLInputElement>('.mcq-option-input');
        options[0].click(); // wrong answer
        fixture.detectChanges();
        (el.querySelector('.mcq-submit') as HTMLButtonElement).click();
        fixture.detectChanges();

        expect(options[0].nextElementSibling!.classList.contains('incorrect')).toBe(true);
        expect(options[1].nextElementSibling!.classList.contains('correct')).toBe(true);
    });

    it('should disable all options after submission', () => {
        const el = render();
        const options = el.querySelectorAll<HTMLInputElement>('.mcq-option-input');
        options[1].click();
        fixture.detectChanges();
        (el.querySelector('.mcq-submit') as HTMLButtonElement).click();
        fixture.detectChanges();

        options.forEach((option) => {
            expect(option.disabled).toBe(true);
        });
    });

    it('should not change selection after submission', () => {
        const el = render();
        const options = el.querySelectorAll<HTMLInputElement>('.mcq-option-input');
        options[1].click();
        fixture.detectChanges();
        (el.querySelector('.mcq-submit') as HTMLButtonElement).click();
        fixture.detectChanges();

        component.selectOption(0);
        expect(component.selectedIndex()).toBe(1);
    });

    it('should emit answerChanged on option select', () => {
        const el = render();
        const spy = vi.fn();
        component.answerChanged.subscribe(spy);

        const options = el.querySelectorAll<HTMLInputElement>('.mcq-option-input');
        options[2].click();
        fixture.detectChanges();

        expect(spy).toHaveBeenCalledWith({ selectedIndex: 2, submitted: false });
    });

    it('should emit answerChanged on submit', () => {
        const el = render();
        const spy = vi.fn();
        component.answerChanged.subscribe(spy);

        const options = el.querySelectorAll<HTMLInputElement>('.mcq-option-input');
        options[1].click();
        fixture.detectChanges();
        (el.querySelector('.mcq-submit') as HTMLButtonElement).click();
        fixture.detectChanges();

        expect(spy).toHaveBeenCalledWith({ selectedIndex: 1, submitted: true });
    });

    it('should restore state from initialSelectedIndex and initialSubmitted', () => {
        fixture.componentRef.setInput('mcqData', sampleMcq);
        fixture.componentRef.setInput('initialSelectedIndex', 2);
        fixture.componentRef.setInput('initialSubmitted', true);
        fixture.detectChanges();

        expect(component.selectedIndex()).toBe(2);
        expect(component.submitted()).toBe(true);
    });

    it('should restore state from persisted response in mcqData', () => {
        const mcqWithResponse: McqData = {
            ...sampleMcq,
            response: { selectedIndex: 0, submitted: true },
        };
        render(mcqWithResponse);

        expect(component.selectedIndex()).toBe(0);
        expect(component.submitted()).toBe(true);
    });

    it('should reset submitted state when initialSubmitted changes to false', () => {
        fixture.componentRef.setInput('mcqData', sampleMcq);
        fixture.componentRef.setInput('initialSelectedIndex', 1);
        fixture.componentRef.setInput('initialSubmitted', true);
        fixture.detectChanges();

        expect(component.submitted()).toBe(true);
        expect(component.selectedIndex()).toBe(1);

        // Simulate navigating to an unanswered question in the carousel
        fixture.componentRef.setInput('initialSelectedIndex', undefined);
        fixture.componentRef.setInput('initialSubmitted', false);
        fixture.detectChanges();

        expect(component.submitted()).toBe(false);
        expect(component.selectedIndex()).toBeUndefined();
    });

    it('should reset selectedIndex when initialSelectedIndex changes to undefined', () => {
        fixture.componentRef.setInput('mcqData', sampleMcq);
        fixture.componentRef.setInput('initialSelectedIndex', 2);
        fixture.componentRef.setInput('initialSubmitted', false);
        fixture.detectChanges();

        expect(component.selectedIndex()).toBe(2);

        fixture.componentRef.setInput('initialSelectedIndex', undefined);
        fixture.detectChanges();

        expect(component.selectedIndex()).toBeUndefined();
    });

    it('should show submit button and hide feedback after resetting from submitted state', () => {
        fixture.componentRef.setInput('mcqData', sampleMcq);
        fixture.componentRef.setInput('initialSelectedIndex', 1);
        fixture.componentRef.setInput('initialSubmitted', true);
        fixture.detectChanges();

        const el = fixture.nativeElement as HTMLElement;
        expect(el.querySelector('.mcq-submit')).toBeFalsy();
        expect(el.querySelector('.mcq-feedback')).toBeTruthy();

        // Navigate to unanswered question
        fixture.componentRef.setInput('initialSelectedIndex', undefined);
        fixture.componentRef.setInput('initialSubmitted', false);
        fixture.detectChanges();

        expect(el.querySelector('.mcq-submit')).toBeTruthy();
        expect(el.querySelector('.mcq-feedback')).toBeFalsy();
        expect(el.querySelector('.mcq-explanation')).toBeFalsy();
    });

    it('should allow interaction after resetting from submitted state', () => {
        fixture.componentRef.setInput('mcqData', sampleMcq);
        fixture.componentRef.setInput('initialSelectedIndex', 1);
        fixture.componentRef.setInput('initialSubmitted', true);
        fixture.detectChanges();

        // Navigate to unanswered question
        fixture.componentRef.setInput('initialSelectedIndex', undefined);
        fixture.componentRef.setInput('initialSubmitted', false);
        fixture.detectChanges();

        // Should be able to select and submit
        const el = fixture.nativeElement as HTMLElement;
        const options = el.querySelectorAll<HTMLInputElement>('.mcq-option-input');
        options.forEach((option) => {
            expect(option.disabled).toBe(false);
        });

        options[0].click();
        fixture.detectChanges();

        expect(component.selectedIndex()).toBe(0);
        expect(component.submitted()).toBe(false);
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

    it('should report no correct answer when no option is selected', () => {
        render();
        expect(component.correctAnswer()).toBe(false);
    });

    it('should expose native radios in a uniquely named group labelled by the question', () => {
        const el = render();
        const group = el.querySelector('[role="radiogroup"]')!;
        const radios = el.querySelectorAll<HTMLInputElement>('input[type="radio"]');
        expect(group.getAttribute('aria-labelledby')).toBe(component.questionLabelId);
        expect(el.querySelector(`#${component.questionLabelId}`)?.textContent).toBe(sampleMcq.question);
        expect(new Set(Array.from(radios, (radio) => radio.name))).toEqual(new Set([component.questionLabelId]));
        expect(radios[1].labels?.[0].textContent).toContain('B');
        expect(radios[1].labels?.[0].textContent).toContain('4');
    });

    it('should leave the tab stop to the native radio group and check the clicked radio', () => {
        const el = render();
        const radios = el.querySelectorAll<HTMLInputElement>('input[type="radio"]');
        expect(Array.from(radios, (radio) => radio.hasAttribute('tabindex'))).toEqual([false, false, false, false]);
        radios[2].click();
        fixture.detectChanges();
        expect(Array.from(radios, (radio) => radio.checked)).toEqual([false, false, true, false]);
    });

    it.each(['ArrowRight', 'ArrowDown', 'ArrowLeft', 'ArrowUp'])('should leave %s navigation to the native radio group', (key) => {
        const el = render();
        const radio = el.querySelector<HTMLInputElement>('input[type="radio"]')!;
        radio.focus();
        // JSDOM does not implement native keyboard default actions. Verify that the application
        // keeps the native, same-name group and does not cancel the browser's arrow-key handling.
        const event = new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true });
        expect(radio.dispatchEvent(event)).toBe(true);
        expect(event.defaultPrevented).toBe(false);
        expect(document.activeElement).toBe(radio);
    });

    it('should focus the feedback and remove submitted answers from the tab order', async () => {
        const el = render();
        const radios = el.querySelectorAll<HTMLInputElement>('input[type="radio"]');
        radios[1].click();
        fixture.detectChanges();
        const submit = el.querySelector<HTMLButtonElement>('.mcq-submit')!;
        submit.focus();
        submit.click();
        fixture.detectChanges();
        await fixture.whenStable();
        expect(document.activeElement).toBe(el.querySelector('.mcq-feedback'));
        expect(Array.from(radios, (radio) => radio.disabled)).toEqual([true, true, true, true]);
        radios[0].click();
        fixture.detectChanges();
        expect(radios[1].checked).toBe(true);
        expect(component.selectedIndex()).toBe(1);
    });

    it('should restore checked and disabled native radios without moving focus', async () => {
        const el = render({ ...sampleMcq, response: { selectedIndex: 2, submitted: true } });
        await fixture.whenStable();
        const radios = el.querySelectorAll<HTMLInputElement>('input[type="radio"]');
        expect(Array.from(radios, (radio) => radio.checked)).toEqual([false, false, true, false]);
        expect(Array.from(radios, (radio) => radio.disabled)).toEqual([true, true, true, true]);
        expect(document.activeElement).not.toBe(el.querySelector('.mcq-feedback'));
    });

    it('should expose correct and incorrect option status as text as well as colour', () => {
        const el = render();
        el.querySelector<HTMLInputElement>('input[type="radio"]')!.click();
        fixture.detectChanges();
        el.querySelector<HTMLButtonElement>('.mcq-submit')!.click();
        fixture.detectChanges();
        const options = el.querySelectorAll('.mcq-option');
        expect(options[0].textContent).toContain('×');
        expect(options[0].textContent).toContain('artemisApp.iris.mcq.incorrect');
        expect(options[1].textContent).toContain('✓');
        expect(options[1].textContent).toContain('artemisApp.iris.mcq.correct');
    });

    it('should retain cleaned option text and letter labels', () => {
        const el = render({
            ...sampleMcq,
            options: [
                { text: 'A. First answer', correct: true },
                { text: 'A valid answer', correct: false },
            ],
        });
        expect(Array.from(el.querySelectorAll('.mcq-option-text'), (option) => option.textContent)).toEqual(['First answer', 'A valid answer']);
        expect(Array.from(el.querySelectorAll('.mcq-option-label'), (option) => option.textContent)).toEqual(['A', 'B']);
    });
});
