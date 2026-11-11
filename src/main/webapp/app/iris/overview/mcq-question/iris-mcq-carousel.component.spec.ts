import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { IrisMcqCarouselComponent } from './iris-mcq-carousel.component';
import { McqQuestionData, McqSetData } from 'app/iris/shared/entities/iris-content-type.model';

const sampleQuestions: McqQuestionData[] = [
    {
        question: 'What is 2 + 2?',
        options: [
            { text: '3', correct: false },
            { text: '4', correct: true },
            { text: '5', correct: false },
            { text: '6', correct: false },
        ],
        explanation: '2 + 2 equals 4.',
    },
    {
        question: 'What is 3 * 3?',
        options: [
            { text: '6', correct: false },
            { text: '9', correct: true },
            { text: '12', correct: false },
            { text: '15', correct: false },
        ],
        explanation: '3 * 3 equals 9.',
    },
    {
        question: 'What is 10 / 2?',
        options: [
            { text: '3', correct: false },
            { text: '4', correct: false },
            { text: '5', correct: true },
            { text: '6', correct: false },
        ],
        explanation: '10 / 2 equals 5.',
    },
];

const sampleMcqSet: McqSetData = {
    type: 'mcq-set',
    questions: sampleQuestions,
};

describe('IrisMcqCarouselComponent', () => {
    let fixture: ComponentFixture<IrisMcqCarouselComponent>;
    let component: IrisMcqCarouselComponent;

    const render = (mcqSetData: McqSetData = sampleMcqSet) => {
        fixture.componentRef.setInput('mcqSetData', mcqSetData);
        fixture.detectChanges();
        return fixture.nativeElement as HTMLElement;
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [IrisMcqCarouselComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        });

        fixture = TestBed.createComponent(IrisMcqCarouselComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => vi.restoreAllMocks());

    it('should render the carousel header', () => {
        const el = render();
        expect(el.querySelector('.mcq-carousel-header')).toBeTruthy();
    });

    it('should render the correct number of dots', () => {
        const el = render();
        const dots = el.querySelectorAll('.mcq-carousel-dots .dot');
        expect(dots).toHaveLength(3);
    });

    it('should highlight the first dot as active initially', () => {
        const el = render();
        const dots = el.querySelectorAll('.mcq-carousel-dots .dot');
        expect(dots[0].classList.contains('active')).toBe(true);
        expect(dots[1].classList.contains('active')).toBe(false);
    });

    it('should render the MCQ question component', () => {
        const el = render();
        expect(el.querySelector('jhi-iris-mcq-question')).toBeTruthy();
    });

    it('should navigate to next question', () => {
        render();
        component.nextQuestion();
        fixture.detectChanges();
        expect(component.currentIndex()).toBe(1);
    });

    it('should navigate to previous question', () => {
        render();
        component.nextQuestion();
        component.previousQuestion();
        fixture.detectChanges();
        expect(component.currentIndex()).toBe(0);
    });

    it('should not navigate before first question', () => {
        render();
        component.previousQuestion();
        expect(component.currentIndex()).toBe(0);
    });

    it('should not navigate past last question', () => {
        render();
        component.goToQuestion(2);
        component.nextQuestion();
        expect(component.currentIndex()).toBe(2);
    });

    it('should navigate to question via dot click', () => {
        const el = render();
        const dots = el.querySelectorAll('.mcq-carousel-dots .dot');
        (dots[2] as HTMLButtonElement).click();
        fixture.detectChanges();
        expect(component.currentIndex()).toBe(2);
        expect(dots[2].classList.contains('active')).toBe(true);
    });

    it('should disable previous button on first question', () => {
        const el = render();
        const prevBtn = el.querySelectorAll('.nav-btn')[0] as HTMLButtonElement;
        expect(prevBtn.disabled).toBe(true);
    });

    it('should disable next button on last question', () => {
        render();
        component.goToQuestion(2);
        fixture.detectChanges();
        const el = fixture.nativeElement as HTMLElement;
        const nextBtn = el.querySelectorAll('.nav-btn')[1] as HTMLButtonElement;
        expect(nextBtn.disabled).toBe(true);
    });

    it('should track answers when onAnswerChanged is called', () => {
        render();
        component.onAnswerChanged({ selectedIndex: 1, submitted: true });
        expect(component.answers().get(0)).toEqual({ selectedIndex: 1, submitted: true });
    });

    it('should compute correctCount correctly', () => {
        render();
        // Answer question 0 correctly (index 1 is correct)
        component.onAnswerChanged({ selectedIndex: 1, submitted: true });
        // Go to question 1, answer incorrectly (index 0 is wrong)
        component.goToQuestion(1);
        component.onAnswerChanged({ selectedIndex: 0, submitted: true });
        expect(component.correctCount()).toBe(1);
    });

    it('should compute scorePercentage correctly', () => {
        render();
        // Answer all three questions: 2 correct, 1 wrong
        component.onAnswerChanged({ selectedIndex: 1, submitted: true }); // correct
        component.goToQuestion(1);
        component.onAnswerChanged({ selectedIndex: 1, submitted: true }); // correct
        component.goToQuestion(2);
        component.onAnswerChanged({ selectedIndex: 0, submitted: true }); // wrong
        expect(component.scorePercentage()).toBe(67);
    });

    it('should not show score button until all questions are answered', () => {
        const el = render();
        expect(el.querySelector('.view-score-btn')).toBeFalsy();

        // Answer only 2 of 3
        component.onAnswerChanged({ selectedIndex: 1, submitted: true });
        component.goToQuestion(1);
        component.onAnswerChanged({ selectedIndex: 1, submitted: true });
        fixture.detectChanges();
        expect(el.querySelector('.view-score-btn')).toBeFalsy();
    });

    it('should show score button when all questions are answered', () => {
        const el = render();
        component.onAnswerChanged({ selectedIndex: 1, submitted: true });
        component.goToQuestion(1);
        component.onAnswerChanged({ selectedIndex: 1, submitted: true });
        component.goToQuestion(2);
        component.onAnswerChanged({ selectedIndex: 2, submitted: true });
        fixture.detectChanges();
        expect(el.querySelector('.view-score-btn')).toBeTruthy();
    });

    it('should show score summary when view score is clicked', () => {
        const el = render();
        // Answer all questions
        component.onAnswerChanged({ selectedIndex: 1, submitted: true });
        component.goToQuestion(1);
        component.onAnswerChanged({ selectedIndex: 1, submitted: true });
        component.goToQuestion(2);
        component.onAnswerChanged({ selectedIndex: 2, submitted: true });
        fixture.detectChanges();

        (el.querySelector('.view-score-btn') as HTMLButtonElement).click();
        fixture.detectChanges();

        expect(el.querySelector('.score-summary')).toBeTruthy();
        expect(el.querySelector('.score-fraction')?.textContent).toContain('3/3');
    });

    it('should return to questions when review button is clicked', () => {
        render();
        component.showScore.set(true);
        fixture.detectChanges();
        const el = fixture.nativeElement as HTMLElement;

        (el.querySelector('.review-btn') as HTMLButtonElement).click();
        fixture.detectChanges();

        expect(component.showScore()).toBe(false);
        expect(el.querySelector('.score-summary')).toBeFalsy();
    });

    it('should emit responseSaved when an answer is submitted', () => {
        render();
        const spy = vi.fn();
        component.responseSaved.subscribe(spy);

        component.onAnswerChanged({ selectedIndex: 1, submitted: true });
        expect(spy).toHaveBeenCalledWith({ selectedIndex: 1, submitted: true, questionIndex: 0 });
    });

    it('should not emit responseSaved for unsubmitted selection', () => {
        render();
        const spy = vi.fn();
        component.responseSaved.subscribe(spy);

        component.onAnswerChanged({ selectedIndex: 1, submitted: false });
        expect(spy).not.toHaveBeenCalled();
    });

    it('should restore answers from persisted responses', () => {
        const mcqSetWithResponses: McqSetData = {
            ...sampleMcqSet,
            responses: [
                { questionIndex: 0, selectedIndex: 1, submitted: true },
                { questionIndex: 2, selectedIndex: 2, submitted: true },
            ],
        };
        render(mcqSetWithResponses);

        expect(component.answers().get(0)).toEqual({ selectedIndex: 1, submitted: true });
        expect(component.answers().get(2)).toEqual({ selectedIndex: 2, submitted: true });
        expect(component.answeredCount()).toBe(2);
    });

    it('should mark dots with correct/incorrect classes', () => {
        const el = render();
        // Answer question 0 correctly
        component.onAnswerChanged({ selectedIndex: 1, submitted: true });
        // Answer question 1 incorrectly
        component.goToQuestion(1);
        component.onAnswerChanged({ selectedIndex: 0, submitted: true });
        fixture.detectChanges();

        const dots = el.querySelectorAll('.mcq-carousel-dots .dot');
        expect(dots[0].classList.contains('correct')).toBe(true);
        expect(dots[1].classList.contains('incorrect')).toBe(true);
        expect(dots[2].classList.contains('correct')).toBe(false);
        expect(dots[2].classList.contains('incorrect')).toBe(false);
    });

    it('should give navigation buttons translated accessible names and preserve their disabled boundaries', () => {
        const el = render();
        const [previous, next] = el.querySelectorAll<HTMLButtonElement>('.nav-btn');
        expect(previous.getAttribute('aria-label')).toBe('artemisApp.iris.mcq.previousQuestion');
        expect(next.getAttribute('aria-label')).toBe('artemisApp.iris.mcq.nextQuestion');
        expect(previous.disabled).toBe(true);
        next.click();
        fixture.detectChanges();
        expect(component.currentIndex()).toBe(1);
        expect(previous.disabled).toBe(false);
        next.click();
        fixture.detectChanges();
        expect(component.currentIndex()).toBe(2);
        expect(next.disabled).toBe(true);
        previous.click();
        fixture.detectChanges();
        expect(component.currentIndex()).toBe(1);
    });

    it('should name every question selector with its question number and submitted status', () => {
        const translate = TestBed.inject(TranslateService);
        const instant = vi.spyOn(translate, 'instant');
        const el = render();
        component.onAnswerChanged({ selectedIndex: 1, submitted: true });
        component.goToQuestion(1);
        component.onAnswerChanged({ selectedIndex: 0, submitted: true });
        fixture.detectChanges();
        const dots = el.querySelectorAll<HTMLButtonElement>('.dot');
        expect(dots[0].getAttribute('aria-label')).toBe('artemisApp.iris.mcq.questionCorrect');
        expect(dots[1].getAttribute('aria-label')).toBe('artemisApp.iris.mcq.questionIncorrect');
        expect(dots[2].getAttribute('aria-label')).toBe('artemisApp.iris.mcq.questionUnanswered');
        expect(instant).toHaveBeenCalledWith('artemisApp.iris.mcq.questionCorrect', { number: 1 });
        expect(instant).toHaveBeenCalledWith('artemisApp.iris.mcq.questionIncorrect', { number: 2 });
        expect(instant).toHaveBeenCalledWith('artemisApp.iris.mcq.questionUnanswered', { number: 3 });
        expect(dots[0].textContent).toContain('✓');
        expect(dots[1].textContent).toContain('×');
        expect(dots[0].title).toBe(dots[0].getAttribute('aria-label'));
    });

    it('should expose exactly one current question through aria-current', () => {
        const el = render();
        const dots = el.querySelectorAll<HTMLButtonElement>('.dot');
        expect(dots[0].getAttribute('aria-current')).toBe('step');
        expect(el.querySelectorAll('[aria-current="step"]')).toHaveLength(1);
        dots[2].click();
        fixture.detectChanges();
        expect(dots[0].hasAttribute('aria-current')).toBe(false);
        expect(dots[2].getAttribute('aria-current')).toBe('step');
        expect(el.querySelectorAll('[aria-current="step"]')).toHaveLength(1);
    });

    it('should retain selected and submitted answers through next, previous, score and review actions', () => {
        const el = render();
        const saved = vi.fn();
        component.responseSaved.subscribe(saved);
        const chooseAndSubmit = (index: number) => {
            el.querySelectorAll<HTMLInputElement>('input[type="radio"]')[index].click();
            fixture.detectChanges();
            el.querySelector<HTMLButtonElement>('.mcq-submit')!.click();
            fixture.detectChanges();
        };
        chooseAndSubmit(1);
        el.querySelectorAll<HTMLButtonElement>('.nav-btn')[1].click();
        fixture.detectChanges();
        el.querySelectorAll<HTMLInputElement>('input[type="radio"]')[0].click();
        fixture.detectChanges();
        el.querySelectorAll<HTMLButtonElement>('.nav-btn')[0].click();
        fixture.detectChanges();
        let radios = el.querySelectorAll<HTMLInputElement>('input[type="radio"]');
        expect(radios[1].checked).toBe(true);
        expect(Array.from(radios, (radio) => radio.disabled)).toEqual([true, true, true, true]);
        el.querySelectorAll<HTMLButtonElement>('.nav-btn')[1].click();
        fixture.detectChanges();
        radios = el.querySelectorAll<HTMLInputElement>('input[type="radio"]');
        expect(radios[0].checked).toBe(true);
        expect(radios[0].disabled).toBe(false);
        el.querySelector<HTMLButtonElement>('.mcq-submit')!.click();
        fixture.detectChanges();
        el.querySelectorAll<HTMLButtonElement>('.nav-btn')[1].click();
        fixture.detectChanges();
        chooseAndSubmit(2);
        el.querySelector<HTMLButtonElement>('.view-score-btn')!.click();
        fixture.detectChanges();
        expect(el.querySelector('.score-fraction')?.textContent).toBe('2/3');
        expect(el.querySelector('.score-percentage')?.textContent).toBe('(67%)');
        el.querySelector<HTMLButtonElement>('.review-btn')!.click();
        fixture.detectChanges();
        radios = el.querySelectorAll<HTMLInputElement>('input[type="radio"]');
        expect(radios[2].checked).toBe(true);
        expect(Array.from(radios, (radio) => radio.disabled)).toEqual([true, true, true, true]);
        expect(saved).toHaveBeenCalledTimes(3);
        expect(saved).toHaveBeenNthCalledWith(1, { selectedIndex: 1, submitted: true, questionIndex: 0 });
        expect(saved).toHaveBeenNthCalledWith(2, { selectedIndex: 0, submitted: true, questionIndex: 1 });
        expect(saved).toHaveBeenNthCalledWith(3, { selectedIndex: 2, submitted: true, questionIndex: 2 });
    });

    it('should name score selectors and open the requested persisted answer for review', () => {
        const el = render({
            ...sampleMcqSet,
            responses: [
                { questionIndex: 0, selectedIndex: 1, submitted: true },
                { questionIndex: 1, selectedIndex: 0, submitted: true },
                { questionIndex: 2, selectedIndex: 2, submitted: true },
            ],
        });
        const dots = el.querySelectorAll<HTMLButtonElement>('.score-dots .dot');
        expect(dots[0].getAttribute('aria-label')).toBe('artemisApp.iris.mcq.questionCorrect');
        expect(dots[1].getAttribute('aria-label')).toBe('artemisApp.iris.mcq.questionIncorrect');
        expect(dots[1].textContent).toContain('×');
        dots[1].click();
        fixture.detectChanges();
        expect(component.showScore()).toBe(false);
        expect(component.currentIndex()).toBe(1);
        const radios = el.querySelectorAll<HTMLInputElement>('input[type="radio"]');
        expect(radios[0].checked).toBe(true);
        expect(radios[0].disabled).toBe(true);
        expect(el.querySelector('.mcq-feedback')?.textContent).toContain('artemisApp.iris.mcq.incorrect');
    });

    it('should show the counter and preserve navigation when there are more than ten questions', () => {
        const el = render({ ...sampleMcqSet, questions: Array.from({ length: 11 }, () => sampleQuestions[0]) });
        expect(el.querySelector('.dot')).toBeNull();
        expect(el.querySelector('.mcq-carousel-counter')?.textContent).toBe('1 / 11');
        el.querySelectorAll<HTMLButtonElement>('.nav-btn')[1].click();
        fixture.detectChanges();
        expect(el.querySelector('.mcq-carousel-counter')?.textContent).toBe('2 / 11');
    });
});
