import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
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
    setupTestBed({ zoneless: true });

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

    it('should render the carousel header', () => {
        const el = render();
        expect(el.querySelector('.carousel-header')).toBeTruthy();
    });

    it('should render the correct number of dots', () => {
        const el = render();
        const dots = el.querySelectorAll('.carousel-dots .dot');
        expect(dots).toHaveLength(3);
    });

    it('should highlight the first dot as active initially', () => {
        const el = render();
        const dots = el.querySelectorAll('.carousel-dots .dot');
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
        const dots = el.querySelectorAll('.carousel-dots .dot');
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

        const dots = el.querySelectorAll('.carousel-dots .dot');
        expect(dots[0].classList.contains('correct')).toBe(true);
        expect(dots[1].classList.contains('incorrect')).toBe(true);
        expect(dots[2].classList.contains('correct')).toBe(false);
        expect(dots[2].classList.contains('incorrect')).toBe(false);
    });
});
