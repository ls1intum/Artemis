import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { MockComponent } from 'ng-mocks';
import { beforeEach, describe, expect, it } from 'vitest';

import { QuizQuestionPreviewComponent } from 'app/quiz/manage/quiz-question-preview/quiz-question-preview.component';
import { MultipleChoiceQuestionComponent } from 'app/quiz/shared/questions/multiple-choice-question/multiple-choice-question.component';
import { DragAndDropQuestionComponent } from 'app/quiz/shared/questions/drag-and-drop-question/drag-and-drop-question.component';
import { ShortAnswerQuestionComponent } from 'app/quiz/shared/questions/short-answer-question/short-answer-question.component';
import { MultipleChoiceQuestion } from 'app/quiz/shared/entities/multiple-choice-question.model';
import { DragAndDropQuestion } from 'app/quiz/shared/entities/drag-and-drop-question.model';
import { ShortAnswerQuestion } from 'app/quiz/shared/entities/short-answer-question.model';
import { QuizQuestion } from 'app/quiz/shared/entities/quiz-question.model';

describe('QuizQuestionPreviewComponent', () => {
    let fixture: ComponentFixture<QuizQuestionPreviewComponent>;

    const renderedQuestionTags = () => {
        const tags = ['jhi-multiple-choice-question', 'jhi-drag-and-drop-question', 'jhi-short-answer-question'];
        return tags.filter((tag) => fixture.debugElement.query(By.css(tag)) !== null);
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [QuizQuestionPreviewComponent],
        })
            // The question components render markdown and images and need their own providers; this spec only checks which one is chosen.
            .overrideComponent(QuizQuestionPreviewComponent, {
                remove: { imports: [MultipleChoiceQuestionComponent, DragAndDropQuestionComponent, ShortAnswerQuestionComponent] },
                add: { imports: [MockComponent(MultipleChoiceQuestionComponent), MockComponent(DragAndDropQuestionComponent), MockComponent(ShortAnswerQuestionComponent)] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(QuizQuestionPreviewComponent);
    });

    it.each([
        ['multiple choice', () => new MultipleChoiceQuestion(), 'jhi-multiple-choice-question'],
        ['drag and drop', () => new DragAndDropQuestion(), 'jhi-drag-and-drop-question'],
        ['short answer', () => new ShortAnswerQuestion(), 'jhi-short-answer-question'],
    ])('should render exactly the component of a %s question', (_name: string, createQuestion: () => QuizQuestion, expectedTag: string) => {
        fixture.componentRef.setInput('question', createQuestion());
        fixture.detectChanges();

        expect(renderedQuestionTags()).toEqual([expectedTag]);
    });

    it('should render no question component for an unknown question type', () => {
        const question = new MultipleChoiceQuestion();
        question.type = undefined;
        fixture.componentRef.setInput('question', question);
        fixture.detectChanges();

        expect(renderedQuestionTags()).toEqual([]);
        expect(fixture.nativeElement.querySelector('[data-testid="quiz-question-preview"]')).not.toBeNull();
    });

    it('should pass the question and its one-based position to the question component', () => {
        const question = new MultipleChoiceQuestion();
        question.title = 'Question title';
        fixture.componentRef.setInput('question', question);
        fixture.componentRef.setInput('questionIndex', 3);
        fixture.detectChanges();

        const child = fixture.debugElement.query(By.directive(MockComponent(MultipleChoiceQuestionComponent)));
        const instance = child.componentInstance as MultipleChoiceQuestionComponent;
        expect(instance.question()).toBe(question);
        expect(instance.questionIndex()).toBe(3);
        expect(instance.selectedAnswerOptions()).toEqual([]);
    });
});
